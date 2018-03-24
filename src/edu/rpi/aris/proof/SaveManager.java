package edu.rpi.aris.proof;

import edu.rpi.aris.gui.GuiConfig;
import edu.rpi.aris.Main;
import edu.rpi.aris.gui.Proof;
import edu.rpi.aris.rules.RuleList;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Pair;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
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
import java.util.*;
import java.util.stream.Collectors;

public class SaveManager {

    @SuppressWarnings("SpellCheckingInspection")
    public static final String FILE_EXTENSION = "bram";

    private static DocumentBuilder documentBuilder;
    private static Transformer transformer;
    private static MessageDigest hash;
    private static FileChooser.ExtensionFilter extensionFilter = new FileChooser.ExtensionFilter("Bram Proof File (." + FILE_EXTENSION + ")", "*." + FILE_EXTENSION);
    private static FileChooser.ExtensionFilter allFiles = new FileChooser.ExtensionFilter("All Files", "*");

    static {
        try {
            documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            transformer = TransformerFactory.newInstance().newTransformer();
            hash = MessageDigest.getInstance("SHA-256");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        } catch (ParserConfigurationException | TransformerConfigurationException | NoSuchAlgorithmException e) {
            Main.instance.showExceptionError(Thread.currentThread(), e, true);
        }
    }

    public static File showSaveDialog(Window parent, String defaultFileName) throws IOException {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(GuiConfig.getConfigManager().getSaveDirectory());
        fileChooser.getExtensionFilters().add(extensionFilter);
        fileChooser.getExtensionFilters().add(allFiles);
        fileChooser.setSelectedExtensionFilter(extensionFilter);
        fileChooser.setTitle("Save Proof");
        fileChooser.setInitialFileName(defaultFileName);
        File f = fileChooser.showSaveDialog(parent);
        if (f != null) {
            f = f.getCanonicalFile();
            GuiConfig.getConfigManager().setSaveDirectory(f.getParentFile());
        }
        return f;
    }

    public static File showOpenDialog(Window parent) throws IOException {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(GuiConfig.getConfigManager().getSaveDirectory());
        fileChooser.getExtensionFilters().add(extensionFilter);
        fileChooser.getExtensionFilters().add(allFiles);
        fileChooser.setSelectedExtensionFilter(extensionFilter);
        fileChooser.setTitle("Open Proof");
        File f = fileChooser.showOpenDialog(parent);
        if (f != null) {
            f = f.getCanonicalFile();
            GuiConfig.getConfigManager().setSaveDirectory(f.getParentFile());
            if (!f.getName().toLowerCase().endsWith("." + FILE_EXTENSION))
                f = new File(f.getParent(), f.getName() + "." + FILE_EXTENSION);
        }
        return f;
    }

    public static synchronized boolean saveProof(Proof proof, File file) throws IOException, TransformerException {
        if (proof == null || file == null)
            return false;
        if (!file.exists())
            if (!file.createNewFile())
                throw new IOException("Failed to save proof");
        return saveProof(proof, new FileOutputStream(file));
    }

    public static synchronized boolean saveProof(Proof proof, OutputStream out) throws TransformerException {
        if (proof == null || out == null)
            return false;
        Document doc = documentBuilder.newDocument();
        Element root = doc.createElement("bram");
        doc.appendChild(root);

        Element program = doc.createElement("program");
        program.appendChild(doc.createTextNode(Main.NAME));
        root.appendChild(program);

        Element version = doc.createElement("version");
        version.appendChild(doc.createTextNode(Main.VERSION));
        root.appendChild(version);

        ArrayList<Element> proofElements = new ArrayList<>();

        createProofElement(proof, 0, doc, root, proofElements);

        Element baseProof = proofElements.get(0);

        for (Proof.Goal g : proof.getGoals()) {
            Element goal = doc.createElement("goal");
            Element sen = doc.createElement("sen");
            g.buildExpression();
            sen.appendChild(doc.createTextNode(g.getExpression() == null ? "" : g.getExpression().toString()));
            goal.appendChild(sen);
            Element raw = doc.createElement("raw");
            raw.appendChild(doc.createTextNode(g.goalStringProperty().get()));
            goal.appendChild(raw);
            baseProof.appendChild(goal);
        }

        Element metadata = doc.createElement("metadata");

        for (String author : proof.getAuthors()) {
            Element a = doc.createElement("author");
            a.appendChild(doc.createTextNode(author));
            metadata.appendChild(a);
        }

        root.insertBefore(metadata, baseProof);

        DOMSource src = new DOMSource(doc);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        transformer.transform(src, result);

        String hash = computeHash(writer.toString(), proof.getAuthors());
        Element hashElement = doc.createElement("hash");
        hashElement.appendChild(doc.createTextNode(hash));
        metadata.appendChild(hashElement);

        result = new StreamResult(out);
        transformer.transform(src, result);

        return true;
    }

    private static synchronized Pair<Integer, Integer> createProofElement(Proof proof, int lineNum, Document doc, Element root, ArrayList<Element> proofElements) {
        Element prf = doc.createElement("proof");
        int id = proofElements.size();
        prf.setAttribute("id", String.valueOf(id));
        root.appendChild(prf);
        proofElements.add(prf);
        int indent = proof.getLines().get(lineNum).subProofLevelProperty().get();
        int allowedAssumptions = lineNum == 0 ? proof.numPremises().get() : 1;
        for (int i = lineNum; i < proof.getLines().size(); ++i) {
            Proof.Line line = proof.getLines().get(i);
            if (line.subProofLevelProperty().get() < indent || (line.subProofLevelProperty().get() == indent && allowedAssumptions == 0 && line.isAssumption()))
                break;
            if (line.isAssumption()) {
                if (allowedAssumptions == 0) {
                    Element step = doc.createElement("step");
                    step.setAttribute("linenum", String.valueOf(line.lineNumberProperty().get()));
                    Element rule = doc.createElement("rule");
                    rule.appendChild(doc.createTextNode("SUBPROOF"));
                    step.appendChild(rule);
                    Pair<Integer, Integer> subproofResult = createProofElement(proof, line.lineNumberProperty().get(), doc, root, proofElements);
                    Element premise = doc.createElement("premise");
                    premise.appendChild(doc.createTextNode(String.valueOf(subproofResult.getKey())));
                    step.appendChild(premise);
                    prf.appendChild(step);
                    lineNum = subproofResult.getValue();
                    i = lineNum - 1;
                } else {
                    --allowedAssumptions;
                    Element assumption = doc.createElement("assumption");
                    assumption.setAttribute("linenum", String.valueOf(line.lineNumberProperty().get()));
                    Element sen = doc.createElement("sen");
                    line.buildExpression();
                    sen.appendChild(doc.createTextNode(line.getExpression() == null ? "" : line.getExpression().toString()));
                    assumption.appendChild(sen);
                    Element raw = doc.createElement("raw");
                    raw.appendChild(doc.createTextNode(line.expressionStringProperty().get()));
                    assumption.appendChild(raw);
                    prf.appendChild(assumption);
                    ++lineNum;
                }
            } else {
                allowedAssumptions = 0;
                Element step = doc.createElement("step");
                step.setAttribute("linenum", String.valueOf(line.lineNumberProperty().get()));
                Element sen = doc.createElement("sen");
                line.buildExpression();
                sen.appendChild(doc.createTextNode(line.getExpression() == null ? "" : line.getExpression().toString()));
                step.appendChild(sen);
                Element raw = doc.createElement("raw");
                raw.appendChild(doc.createTextNode(line.expressionStringProperty().get()));
                step.appendChild(raw);
                Element rule = doc.createElement("rule");
                rule.appendChild(doc.createTextNode(line.selectedRuleProperty().get() == null ? "" : line.selectedRuleProperty().get().name()));
                step.appendChild(rule);
                for (Proof.Line p : line.getPremises()) {
                    Element premise = doc.createElement("premise");
                    premise.appendChild(doc.createTextNode(String.valueOf(p.lineNumberProperty().get())));
                    step.appendChild(premise);
                }
                prf.appendChild(step);
                ++lineNum;
            }
        }
        return new Pair<>(id, lineNum);
    }

    public static synchronized Proof loadProof(File file) throws IOException, TransformerException {
        if (file == null || !file.exists())
            return null;
        return loadProof(new FileInputStream(file), file.getName());
    }

    public static synchronized Proof loadProof(InputStream in, String name) throws TransformerException, IOException {
        StreamSource src = new StreamSource(in);
        DOMResult result = new DOMResult();
        transformer.transform(src, result);

        if (!(result.getNode() instanceof Document))
            return null;
        Document doc = (Document) result.getNode();

        NodeList list = doc.getElementsByTagName("bram");
        if (list.getLength() != 1 || !(list.item(0) instanceof Element))
            throw new IOException("Invalid file format");
        Element root = (Element) list.item(0);

        Element program = getElementByTag(root, "program");
        Element version = getElementByTag(root, "version");
        boolean isArisFile = program.getTextContent().equals(Main.NAME);
        Proof proof;
        HashSet<String> authors = new HashSet<>();
        if (!isArisFile) {
            switch (Main.getMode()) {
                case GUI:
                    Alert noAris = new Alert(Alert.AlertType.CONFIRMATION);
                    noAris.setTitle("Not Aris File");
                    noAris.setHeaderText("Not Aris File");
                    noAris.setContentText("The given file \"" + name + "\" was written by " + program.getTextContent() + " version " + version.getTextContent() + "\n" +
                            "Aris may still be able to read this file with varying success\n" +
                            "Would you like to attempt to load this file?");
                    Optional<ButtonType> option = noAris.showAndWait();
                    if (!option.isPresent() || option.get() != ButtonType.YES)
                        return null;
                    break;
                case CMD:
                    System.out.println("The given file \"" + name + "\" was written by " + program.getTextContent() + " version " + version.getTextContent());
                    System.out.println("Aris may still be able to read this file with varying success");
                    System.out.println("Would you like to attempt to load this file? (Y/n)");
                    String response = Main.readLine();
                    if (response.equalsIgnoreCase("n") || response.equalsIgnoreCase("no"))
                        return null;
                    break;
                case SERVER:
                    System.out.println("The given file \"" + name + "\" was written by " + program.getTextContent() + " version " + version.getTextContent());
                    System.out.println("Aris will attempt to read the file anyway");
                    break;
            }
            authors.add("UNKNOWN");
            proof = new Proof(authors);
        } else {
            Element metadata = getElementByTag(root, "metadata");
            try {
                Element hashElement = getElementByTag(metadata, "hash");
                authors.addAll(getElementsByTag(metadata, "author").stream().map(Node::getTextContent).collect(Collectors.toList()));
                metadata.removeChild(hashElement);
                DOMSource s = new DOMSource(doc);
                StringWriter w = new StringWriter();
                StreamResult r = new StreamResult(w);
                transformer.transform(s, r);
                String xml = w.toString().replaceAll("\n[\t\\s\f\r\\x0B]*\n", "\n");
                if (!verifyHash(xml, hashElement.getTextContent(), authors)) {
                    switch (Main.getMode()) {
                        case GUI:
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("File integrity check failed");
                            alert.setHeaderText("File integrity check failed");
                            alert.setContentText("This file may be corrupted or may have been tampered with.\n" +
                                    "If this file successfully loads the author will be marked as UNKNOWN.\n" +
                                    "This will show up if this file is submitted and may affect your grade.");
                            alert.getDialogPane().setPrefWidth(500);
                            alert.showAndWait();
                            break;
                        case CMD:
                            System.out.println("File integrity check failed for " + name);
                            System.out.println("This file may be corrupted or may have been tampered with.");
                            System.out.println("If this file successfully loads the author will be marked as UNKNOWN");
                            System.out.println("This will show up if this file is submitted and may affect your grade");
                            System.out.println("Press enter to confirm");
                            Main.readLine();
                            break;
                        case SERVER:
                            System.out.println("File integrity check failed for " + name);
                            System.out.println("The system will still attempt to load the file and will mark the author as UNKNOWN");
                            break;
                    }
                    authors.clear();
                    authors.add("UNKNOWN");
                }
            } catch (IOException ignored) {
                authors.add("UNKNOWN");
            }
            proof = new Proof(authors);
            ArrayList<Element> proofElements = getElementsByTag(root, "proof");
            if (proofElements.size() == 0)
                throw new IOException("Missing main proof element");
            proofElements.sort((e1, e2) -> {
                try {
                    int i1 = Integer.valueOf(e1.getAttribute("id"));
                    int i2 = Integer.valueOf(e2.getAttribute("id"));
                    return Integer.compare(i1, i2);
                } catch (NumberFormatException e) {
                    return 0;
                }
            });
            for (int i = 0; i < proofElements.size(); ++i) {
                Element e = proofElements.get(i);
                int id;
                try {
                    id = Integer.valueOf(e.getAttribute("id"));
                } catch (NumberFormatException e1) {
                    throw new IOException("Invalid id tag in proof element");
                }
                if (id != i)
                    throw new IOException("Non sequential id tag found in proof element");
            }
            readProofElement(proof, proofElements, 0, 0, 0);
            Element baseProof = proofElements.get(0);
            ArrayList<Element> goals = getElementsByTag(baseProof, "goal");
            for (Element g : goals) {
                String raw;
                try {
                    raw = getElementByTag(g, "raw").getTextContent();
                } catch (IOException e) {
                    String sen = getElementByTag(g, "sen").getTextContent();
                    try {
                        raw = new Expression(sen).toLogicString();
                    } catch (ExpressionParseException e1) {
                        throw new IOException("Invalid sentence in goal element");
                    }
                }
                Proof.Goal goal = proof.addGoal(proof.getGoals().size());
                goal.goalStringProperty().set(raw);
            }
        }
        return proof;
    }

    private static synchronized int readProofElement(Proof proof, ArrayList<Element> proofElements, int elementId, int indent, int lineNum) throws IOException {
        Element element = proofElements.get(elementId);
        ArrayList<Element> assumptions = getElementsByTag(element, "assumption");
        assumptions.sort((e1, e2) -> {
            try {
                int i1 = Integer.valueOf(e1.getAttribute("linenum"));
                int i2 = Integer.valueOf(e2.getAttribute("linenum"));
                return Integer.compare(i1, i2);
            } catch (NumberFormatException e) {
                return 0;
            }
        });
        for (Element assumption : assumptions) {
            int num;
            try {
                num = Integer.parseInt(assumption.getAttribute("linenum"));
            } catch (NumberFormatException e) {
                throw new IOException("Invalid assumptions linenum tag");
            }
            if (num != lineNum)
                throw new IOException("Non sequential linenum tags in file");
            String raw;
            try {
                raw = getElementByTag(assumption, "raw").getTextContent();
            } catch (IOException e) {
                String sen = getElementByTag(assumption, "sen").getTextContent();
                try {
                    raw = new Expression(sen).toLogicString();
                } catch (ExpressionParseException e1) {
                    throw new IOException("Invalid sentence in proof element " + elementId);
                }
            }
            Proof.Line line = indent == 0 ? proof.addPremise() : proof.addLine(lineNum, true, indent);
            line.expressionStringProperty().set(raw);
            ++lineNum;
        }
        ArrayList<Element> steps = getElementsByTag(element, "step");
        steps.sort((e1, e2) -> {
            try {
                int i1 = Integer.valueOf(e1.getAttribute("linenum"));
                int i2 = Integer.valueOf(e2.getAttribute("linenum"));
                return Integer.compare(i1, i2);
            } catch (NumberFormatException e) {
                return 0;
            }
        });
        for (Element step : steps) {
            int num;
            try {
                num = Integer.parseInt(step.getAttribute("linenum"));
            } catch (NumberFormatException e) {
                throw new IOException("Invalid step linenum tag");
            }
            if (num != lineNum)
                throw new IOException("Non sequential linenum tags in file");
            String ruleStr = getElementByTag(step, "rule").getTextContent().toUpperCase();
            if (ruleStr.equals("SUBPROOF")) {
                int id;
                try {
                    id = Integer.valueOf(getElementByTag(step, "premise").getTextContent());
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid proof id in subproof step");
                }
                if (id <= elementId || id >= proofElements.size())
                    throw new IOException("Invalid proof id in subproof step");
                lineNum = readProofElement(proof, proofElements, id, indent + 1, lineNum);
            } else {
                String raw;
                try {
                    raw = getElementByTag(step, "raw").getTextContent();
                } catch (IOException e) {
                    String sen = getElementByTag(step, "sen").getTextContent();
                    try {
                        raw = new Expression(sen).toLogicString();
                    } catch (ExpressionParseException e1) {
                        throw new IOException("Invalid sentence in proof element " + elementId);
                    }
                }
                Proof.Line line = proof.addLine(lineNum, false, indent);
                line.expressionStringProperty().set(raw);
                RuleList rule = null;
                try {
                    rule = RuleList.valueOf(ruleStr);
                } catch (IllegalArgumentException ignored) {
                }
                line.selectedRuleProperty().set(rule);
                ArrayList<Element> premises = getElementsByTag(step, "premise");
                for (Element p : premises) {
                    int pid;
                    try {
                        pid = Integer.valueOf(p.getTextContent());
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid premise id in step");
                    }
                    if (pid < 0 || pid >= lineNum)
                        throw new IOException("Invalid premise id in step");
                    proof.setPremise(lineNum, proof.getLines().get(pid), true);
                }
                ++lineNum;
            }
        }
        return lineNum;
    }

    private static synchronized String computeHash(String xml, Collection<String> authorCollection) {
        ArrayList<String> authors = new ArrayList<>(authorCollection);
        Collections.sort(authors);
        String hashStr = Base64.getEncoder().encodeToString(hash.digest((xml + StringUtils.join(authors, "")).getBytes()));
        hash.reset();
        return hashStr;
    }

    private static boolean verifyHash(String xml, String hash, Collection<String> authors) {
        return computeHash(xml, authors).equals(hash);
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
        //noinspection SpellCheckingInspection
        loadProof(new File(System.getProperty("user.home"), "test.aprf"));
    }

}
