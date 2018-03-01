package edu.rpi.aris.proof;

import edu.rpi.aris.gui.Aris;
import edu.rpi.aris.gui.ConfigurationManager;
import edu.rpi.aris.gui.Proof;
import javafx.collections.ObservableList;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SaveManager {

    public static final String FILE_EXTENSION = "aprf";

    private static DocumentBuilder documentBuilder;
    private static Transformer transformer;
    private static MessageDigest hash;
    private static Pattern hashPattern = Pattern.compile("(?<=<hash>).+(?=</hash>)");
    private static FileChooser.ExtensionFilter extensionFilter = new FileChooser.ExtensionFilter("Aris Proof File", FILE_EXTENSION);

    static {
        try {
            documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            transformer = TransformerFactory.newInstance().newTransformer();
            hash = MessageDigest.getInstance("SHA-256");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        } catch (ParserConfigurationException | TransformerConfigurationException | NoSuchAlgorithmException e) {
            Aris.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
    }

    public static File showSaveDialog(Window parent, String defaultFileName) throws IOException {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(ConfigurationManager.getConfigManager().getSaveDirectory());
        fileChooser.setSelectedExtensionFilter(extensionFilter);
        fileChooser.setTitle("Save Proof");
        fileChooser.setInitialFileName(defaultFileName);
        File f = fileChooser.showSaveDialog(parent);
        if (f != null) {
            f = f.getCanonicalFile();
            ConfigurationManager.getConfigManager().setSaveDirectory(f.getParentFile());
            if (!f.getName().toLowerCase().endsWith("." + FILE_EXTENSION))
                f = new File(f.getParent(), f.getName() + "." + FILE_EXTENSION);
        }
        return f;
    }

    public static synchronized boolean saveProof(Proof proof, File file) throws TransformerException, IOException {
        if (proof == null || file == null)
            return false;
        Document doc = documentBuilder.newDocument();
        Element root = doc.createElement("aris");
        doc.appendChild(root);

        Element version = doc.createElement("version");
        version.appendChild(doc.createTextNode(Aris.VERSION));
        root.appendChild(version);

        Element prf = doc.createElement("proof");
        root.appendChild(prf);

        Element premises = doc.createElement("premises");
        prf.appendChild(premises);

        Element allLines = doc.createElement("lines");
        prf.appendChild(allLines);

        ObservableList<Proof.Line> lines = proof.getLines();
        for (int i = 0; i < lines.size(); ++i) {
            Proof.Line l = lines.get(i);
            if (i < proof.numPremises().get())
                premises.appendChild(createLineElement(l, doc));
            else
                allLines.appendChild(createLineElement(l, doc));
        }

        Element goals = doc.createElement("goals");
        prf.appendChild(goals);

        for (Proof.Goal g : proof.getGoals()) {
            Element goal = doc.createElement("goal");
            goal.setAttribute("num", String.valueOf(g.goalNumProperty().get()));
            goal.appendChild(doc.createTextNode(g.goalStringProperty().get()));
            goals.appendChild(goal);
        }

        DOMSource src = new DOMSource(doc);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        transformer.transform(src, result);
        String xml = writer.toString();
        xml += "<hash>" + computeHash(xml) + "</hash>";
        hash.reset();
        FileWriter fileWriter = new FileWriter(file);
        fileWriter.write(xml);
        fileWriter.close();
        return true;
    }

    public static Proof loadFile(File file) throws IOException, TransformerException {
        Proof p = new Proof();
        String xml = IOUtils.toString(new FileReader(file));

        Matcher matcher = hashPattern.matcher(xml);
        if (matcher.find()) {
            String hash = matcher.group(0);
            xml = matcher.replaceAll("");
            xml = xml.replaceAll("<hash></hash>", "");
            if (!verifyHash(xml, hash)) {
                System.err.println("Invalid hash");
                // TODO: show invalid hash warning
            }
        } else {
            // TODO: show no hash warning
        }
        StreamSource src = new StreamSource(new StringReader(xml));
        DOMResult result = new DOMResult();

        transformer.transform(src, result);

        if (!(result.getNode() instanceof Document))
            return null;
        Document doc = (Document) result.getNode();

        NodeList list = doc.getElementsByTagName("aris");
        if (list.getLength() != 1 || !(list.item(0) instanceof Element))
            throw new IOException("Invalid file format");
        Element root = (Element) list.item(0);

        Element proof = getElementByTag(root, "proof");

        Element premises = getElementByTag(proof, "premises");

        ArrayList<Element> premiseList = getElementsByTag(premises, "line");
        if (premiseList.size() == 0)
            throw new IOException("Invalid file format");
        for (int i = 0; i < premiseList.size(); ++i) {
            Element e = premiseList.get(i);
            String numStr = e.getAttribute("num");
            int num = -1;
            try {
                num = Integer.parseInt(numStr);
            } catch (NumberFormatException ignored) {
            }
            if (num != i)
                throw new IOException("Invalid file format");
            Proof.Line premise = p.addPremise();
            premise.expressionStringProperty().set(e.getTextContent());
        }

        Element lines = getElementByTag(proof, "lines");

        ArrayList<Element> lineList = getElementsByTag(lines, "line");

        int numPremises = p.numPremises().get();

        for (int i = 0; i < premiseList.size(); ++i) {
            Element e = lineList.get(i);
            String numStr = e.getAttribute("num");
            String indentStr = e.getAttribute("indent");
            String assumptionStr = e.getAttribute("assumption");
            int num = -1;
            int indent = -1;
            boolean assumption = false;
            try {
                num = Integer.parseInt(numStr);
                indent = Integer.parseInt(indentStr);
                assumption = Boolean.parseBoolean(assumptionStr);
            } catch (NumberFormatException ignored) {
            }

            if (num != i + numPremises || num == -1 || indent == -1)
                throw new IOException("Invalid file format");
        }

        return p;
    }

    private static String computeHash(String xml) {
        return Base64.getEncoder().encodeToString(hash.digest(xml.getBytes()));
    }

    private static boolean verifyHash(String xml, String hash) {
        return computeHash(xml).equals(hash);
    }

    private static Element createLineElement(Proof.Line line, Document doc) {
        Element e = doc.createElement("line");

        e.setAttribute("num", String.valueOf(line.lineNumberProperty().get()));
        e.setAttribute("indent", String.valueOf(line.subProofLevelProperty().get()));
        e.setAttribute("assumption", String.valueOf(line.isAssumption()));

        Element expr = doc.createElement("expr");
        expr.appendChild(doc.createTextNode(line.expressionStringProperty().get()));
        e.appendChild(expr);

        Element rule = doc.createElement("rule");
        rule.appendChild(doc.createTextNode(line.selectedRuleProperty().get() == null ? "" : line.selectedRuleProperty().get().name()));
        e.appendChild(rule);

        return e;

    }

    private static Element getElementByTag(Element parent, String tag) throws IOException {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() != 1 || !(list.item(0) instanceof Element))
            throw new IOException("Invalid file format");
        return (Element) list.item(0);
    }

    private static ArrayList<Element> getElementsByTag(Element parent, String tag) throws IOException {
        NodeList list = parent.getElementsByTagName(tag);
        ArrayList<Element> elements = new ArrayList<>();
        for (int i = 0; i < list.getLength(); ++i) {
            if (!(list.item(i) instanceof Element))
                throw new IOException("Invalid file format");
            elements.add((Element) list.item(i));
        }
        return elements;
    }

    public static void main(String[] args) throws IOException, TransformerException {
        loadFile(new File(System.getProperty("user.home"), "test.aprf"));
    }

    private void checkAttributesPresent(Collection<Element> elements, String attrName) throws IOException {
        for (Element e : elements)
            if (e.getAttribute(attrName).length() == 0)
                throw new IOException("Invalid file format");
    }

}
