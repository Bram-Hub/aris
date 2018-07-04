package edu.rpi.aris.assign.client;

import edu.rpi.aris.LibAris;
import edu.rpi.aris.Main;
import edu.rpi.aris.assign.MessageCommunication;
import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.message.ErrorMsg;
import edu.rpi.aris.assign.message.Message;
import edu.rpi.aris.gui.GuiConfig;
import edu.rpi.aris.net.NetUtil;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Pair;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.security.*;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class Client implements MessageCommunication {

    private static final char[] KEYSTORE_PASSWORD = "ARIS_CLIENT".toCharArray();
    private static final File KEYSTORE_FILE = new File(GuiConfig.CLIENT_CONFIG_DIR, "client.keystore");
    private static final File SERVER_KEYSTORE_FILE = new File(GuiConfig.CLIENT_CONFIG_DIR, "imported.keystore");
    private static Logger logger = LogManager.getLogger(Client.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new BouncyCastleJsseProvider());
        System.setProperty("jdk.tls.ephemeralDHKeySize", "2048");
    }

    private ReentrantLock connectionLock = new ReentrantLock(true);
    private SSLSocketFactory socketFactory = null;
    private SSLSocket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String errorString = null;
    private ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;
    private SimpleObjectProperty<ConnectionStatus> connectionStatusProperty = new SimpleObjectProperty<>(connectionStatus);
    private boolean allowInsecure = false;

    private static KeyStore getKeyStore() {
        KeyStore ks = null;
        if (KEYSTORE_FILE.exists()) {
            try {
                ks = KeyStore.getInstance("JKS");
                FileInputStream fis = new FileInputStream(KEYSTORE_FILE);
                ks.load(fis, KEYSTORE_PASSWORD);
                fis.close();
                X509Certificate cert = (X509Certificate) ks.getCertificateChain("aris_client")[0];
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.MONTH, 1);
                cert.checkValidity(cal.getTime());
            } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
                e.printStackTrace();
                ks = null;
            }
        }
        if (ks == null) {
            try {
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
                keyPairGenerator.initialize(4096, new SecureRandom());
                KeyPair kp = keyPairGenerator.genKeyPair();

                X500Principal principal = new X500Principal("CN=" + InetAddress.getLocalHost().getHostName());
                BigInteger serialNum = new BigInteger(Long.toString(System.currentTimeMillis()));

                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.YEAR, 1);

                String sigAlg = "SHA512WithRSA";

                ContentSigner contentSigner = new JcaContentSignerBuilder(sigAlg).build(kp.getPrivate());

                JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(principal, serialNum, new Date(), cal.getTime(), principal, kp.getPublic());

                X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(contentSigner));

                ks = KeyStore.getInstance("JKS");
                ks.load(null);
                ks.setKeyEntry("aris_client", kp.getPrivate(), KEYSTORE_PASSWORD, new X509Certificate[]{cert});

                if (!KEYSTORE_FILE.exists()) {
                    if (!KEYSTORE_FILE.getParentFile().exists())
                        if (!KEYSTORE_FILE.getParentFile().mkdirs())
                            throw new IOException("Failed to create configuration directory");
                    if (!KEYSTORE_FILE.createNewFile())
                        throw new IOException("Failed to create keystore file");
                }

                FileOutputStream fos = new FileOutputStream(KEYSTORE_FILE);
                ks.store(fos, KEYSTORE_PASSWORD);
                fos.close();
            } catch (NoSuchAlgorithmException | NoSuchProviderException | CertificateException | OperatorCreationException | IOException | KeyStoreException e) {
                e.printStackTrace();
            }
        }
        return ks;
    }

    public static String checkError(String msg) throws IOException {
        if (msg == null)
            throw new IOException("Server did not send a response");
        if (msg.startsWith(NetUtil.ERROR) || msg.startsWith(NetUtil.INVALID) || msg.startsWith(NetUtil.UNAUTHORIZED))
            throw new IOException(msg);
        return msg;
    }

    public static String[] checkSplit(String str, int len) {
        String[] split = str.split("\\|");
        if (split.length < len) {
            String[] newSplit = Arrays.copyOf(split, len);
            for (int i = split.length; i < len; ++i)
                newSplit[i] = "";
            return newSplit;
        }
        return split;
    }

    public synchronized boolean importSelfSignedCertificate(File certFile) {
        try {
            PEMParser parser = new PEMParser(new FileReader(certFile));
            X509CertificateHolder certificateHolder = (X509CertificateHolder) parser.readObject();
            JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
            X509Certificate cert = converter.getCertificate(certificateHolder);
            X500Name name = certificateHolder.getSubject();
            RDN cn = name.getRDNs(BCStyle.CN)[0];
            String dn = IETFUtils.valueToString(cn.getFirst().getValue());
            KeyStore ks = KeyStore.getInstance("JKS");
            if (SERVER_KEYSTORE_FILE.exists()) {
                FileInputStream fis = new FileInputStream(SERVER_KEYSTORE_FILE);
                ks.load(fis, KEYSTORE_PASSWORD);
                fis.close();
            } else {
                ks.load(null);
            }
            ks.setCertificateEntry(dn.toLowerCase(), cert);
            if (!SERVER_KEYSTORE_FILE.getParentFile().exists())
                if (!SERVER_KEYSTORE_FILE.getParentFile().mkdirs())
                    throw new IOException("Failed to create keystore file");
            if (!SERVER_KEYSTORE_FILE.exists())
                if (!SERVER_KEYSTORE_FILE.createNewFile())
                    throw new IOException("Failed to create keystore file");
            FileOutputStream fos = new FileOutputStream(SERVER_KEYSTORE_FILE);
            ks.store(fos, KEYSTORE_PASSWORD);
            fos.close();
            return true;
        } catch (IOException | CertificateException | KeyStoreException | NoSuchAlgorithmException e) {
            logger.error("Failed to import given certificate", e);
            return false;
        } finally {
            socketFactory = null;
        }
    }

    public synchronized void removeSelfSignedCertificate(String commonName) {
        commonName = commonName.toLowerCase();
        if (SERVER_KEYSTORE_FILE.exists()) {
            try {
                KeyStore ks = KeyStore.getInstance("JKS");
                try (FileInputStream fis = new FileInputStream(SERVER_KEYSTORE_FILE)) {
                    ks.load(fis, KEYSTORE_PASSWORD);
                }
                ks.deleteEntry(commonName);
                if (ks.aliases().hasMoreElements()) {
                    try (FileOutputStream fos = new FileOutputStream(SERVER_KEYSTORE_FILE)) {
                        ks.store(fos, KEYSTORE_PASSWORD);
                    }
                } else
                    //noinspection ResultOfMethodCallIgnored
                    SERVER_KEYSTORE_FILE.delete();
                socketFactory = null;
            } catch (IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException e) {
                logger.error("Failed to remove imported certificate", e);
            }
        }
    }

    public ArrayList<X509Certificate> getSelfSignedCertificates() {
        ArrayList<X509Certificate> certs = new ArrayList<>();
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            if (SERVER_KEYSTORE_FILE.exists()) {
                try (FileInputStream fis = new FileInputStream(SERVER_KEYSTORE_FILE)) {
                    ks.load(fis, KEYSTORE_PASSWORD);
                }
                Enumeration<String> aliases = ks.aliases();
                while (aliases.hasMoreElements()) {
                    String a = aliases.nextElement();
                    if (ks.isCertificateEntry(a)) {
                        X509Certificate cert = (X509Certificate) ks.getCertificate(a);
                        certs.add(cert);
                    }
                }
            }
        } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException e) {
            logger.error("Error loading saved server certificates", e);
        }
        return certs;
    }

    public synchronized void setAllowInsecure(boolean allowInsecure) {
        this.allowInsecure = allowInsecure;
        if (allowInsecure) {
            logger.warn("WARNING! The allow-insecure flag has been set. This allows the client to connect to potentially insecure servers and is not recommended");
            logger.warn("Please consider removing this flag and instead importing the self-signed certificates for any servers you wish to connect to");
        }
        if (connectionStatus != ConnectionStatus.DISCONNECTED)
            disconnect();
        socket = null;
        socketFactory = null;
    }

    private TrustManager[] getTrustManagers() {
        try {
            CustomTrustManager trustManager = new CustomTrustManager();
            return new TrustManager[]{trustManager};
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            e.printStackTrace();
        }
        return null;
    }

    private SSLSocketFactory getSocketFactory() {
        if (socketFactory == null)
            try {
                SSLContext context = SSLContext.getInstance("TLSv1.2");
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("X.509");
                keyManagerFactory.init(getKeyStore(), KEYSTORE_PASSWORD);

                context.init(keyManagerFactory.getKeyManagers(), getTrustManagers(), null);

                socketFactory = context.getSocketFactory();
            } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException | KeyManagementException e) {
                e.printStackTrace();
            }
        return socketFactory;
    }

    private void setConnectionStatus(ConnectionStatus status) {
        connectionStatus = status;
        try {
            Platform.runLater(() -> connectionStatusProperty.set(status));
        } catch (IllegalStateException e) {
            connectionStatusProperty.set(status);
        }
    }

    private synchronized void setupConnection(String user, String pass, boolean isAccessToken) throws IOException {
        String serverAddress = GuiConfig.serverAddress.get();
        if (serverAddress == null)
            throw new IOException("Server address not specified");
        int port = GuiConfig.serverPort.get();
        if (port <= 0 || port > 65535)
            throw new IOException("Invalid port specified");
        setConnectionStatus(ConnectionStatus.CONNECTING);
        socket = (SSLSocket) getSocketFactory().createSocket(serverAddress, port);
        socket.setSoTimeout(NetUtil.SOCKET_TIMEOUT);
        final Object sync = new Object();
        socket.addHandshakeCompletedListener(listener -> {
            try {
                X509Certificate cert = (X509Certificate) listener.getPeerCertificates()[0];
                cert.checkValidity();
                X500Name name = new JcaX509CertificateHolder(cert).getSubject();
                RDN cn = name.getRDNs(BCStyle.CN)[0];
                if (!IETFUtils.valueToString(cn.getFirst().getValue()).equalsIgnoreCase(serverAddress))
                    throw new CertificateException("Server's certificate common name does not match server address");
                errorString = null;
            } catch (SSLPeerUnverifiedException | CertificateException e) {
                logger.error("An error occurred while attempting to validate server's certificate", e);
                errorString = e.getMessage();
                setConnectionStatus(ConnectionStatus.ERROR);
            }
            synchronized (sync) {
                sync.notify();
            }
        });
        try {
            setConnectionStatus(ConnectionStatus.HANDSHAKING);
            socket.startHandshake();
            synchronized (sync) {
                try {
                    sync.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (SSLHandshakeException e) {
            logger.error("SSL Handshake failed", e);
            Throwable cause = e.getCause();
            if (cause instanceof CertificateException) {
                setConnectionStatus(ConnectionStatus.CERTIFICATE_WARNING);
            } else {
                setConnectionStatus(ConnectionStatus.ERROR);
                errorString = e.getMessage();
            }
        }
        switch (connectionStatus) {
            case ERROR:
                socket.close();
                socket = null;
                setConnectionStatus(ConnectionStatus.DISCONNECTED);
                throw new IOException(errorString);
            case CERTIFICATE_WARNING:
                socket.close();
                socket = null;
                setConnectionStatus(ConnectionStatus.DISCONNECTED);
                if (showCertWarning())
                    setupConnection(user, pass, isAccessToken);
                else
                    throw new IOException("Failed to verify remote server");
                break;
            case HANDSHAKING:
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                out = new DataOutputStream(socket.getOutputStream());
                setConnectionStatus(ConnectionStatus.CONNECTED);
                doAuth(user, pass, isAccessToken);
                System.out.println("Connected");
                break;
            default:
                socket.close();
                socket = null;
                setConnectionStatus(ConnectionStatus.DISCONNECTED);
                throw new IOException("Client socket is in an unexpected state");
        }
    }

    private synchronized void doAuth(String user, String pass, boolean isAccessToken) throws IOException {
        sendMessage(NetUtil.ARIS_NAME + " " + LibAris.VERSION);
        String version = in.readUTF();
        if (version.equals(NetUtil.INVALID_VERSION))
            throw new IOException("Invalid client version");
        String[] versionSplit = version.split(" ");
        if (versionSplit.length != 2 || !versionSplit[0].equals(NetUtil.ARIS_NAME))
            throw new IOException("Invalid server version: " + version);
        sendMessage(NetUtil.VERSION_OK);
        sendMessage(NetUtil.AUTH + "|" + (isAccessToken ? NetUtil.AUTH_ACCESS_TOKEN : NetUtil.AUTH_PASS) + "|" + URLEncoder.encode(user, "UTF-8") + "|" + URLEncoder.encode(pass, "UTF-8"));
        String res = in.readUTF();
        switch (res) {
            case NetUtil.AUTH_BAN:
                throw new IOException("Authentication failed. Your ip address has been temporarily banned");
            case NetUtil.AUTH_FAIL:
                if (isAccessToken)
                    throw new IOException(NetUtil.AUTH_FAIL);
                else
                    throw new IOException("Invalid credentials");
            case NetUtil.AUTH_ERR:
                throw new IOException("The server encountered an error while attempting to authenticate this connection");
            default:
                if (res.startsWith(NetUtil.AUTH_OK)) {
                    String accessToken = res.replaceFirst(NetUtil.AUTH_OK + " ", "");
                    GuiConfig.getConfigManager().setAccessToken(URLDecoder.decode(accessToken, "UTF-8"));
                    Platform.runLater(() -> GuiConfig.getConfigManager().username.set(user));
                } else
                    throw new IOException(res);
        }
    }

    public void connect() throws IOException {
        connectionLock.lock();
        try {
            if (!ping()) {
                disconnect(false);
                String server = GuiConfig.serverAddress.get();
                if (server == null || server.length() == 0) {
                    Pair<String, Integer> info = getServerAddress(null);
                    if (info == null)
                        return;
                    GuiConfig.serverAddress.set(info.getKey());
                    GuiConfig.serverPort.set(info.getValue());
                }
                Triple<String, String, Boolean> credentials = getCredentials();
                boolean isAccessToken = credentials.getRight();
                try {
                    setupConnection(credentials.getLeft(), credentials.getMiddle(), credentials.getRight());
                } catch (IOException e) {
                    if (isAccessToken && e.getMessage().equals(NetUtil.AUTH_FAIL)) {
                        GuiConfig.getConfigManager().setAccessToken(null);
                        connect();
                        if (connectionLock.isHeldByCurrentThread())
                            connectionLock.unlock();
                    } else
                        throw e;
                }
            }
        } catch (IOException e) {
            disconnect();
            throw e;
        }
    }

    public <T extends Message> void processMessage(T message, ResponseHandler<T> responseHandler) {
        new Thread(() -> {
            try {
                connect();
                //noinspection unchecked
                T reply = (T) message.sendAndGet(this);
                if (responseHandler == null)
                    return;
                if (reply == null) {
                    responseHandler.onError();
                    return;
                }
                responseHandler.response(reply);
            } catch (IOException e) {
                logger.error("Error sending message", e);
            } finally {
                disconnect();
            }
        }).start();
    }

    private Pair<String, Integer> getServerAddress(String lastAddress) throws IOException {
        String address = lastAddress;
        switch (Main.getMode()) {
            case GUI:
                final AtomicReference<String> atomicAddress = new AtomicReference<>(address);
                Platform.runLater(() -> {
                    Dialog<String> dialog = new Dialog<>();
                    dialog.setTitle("Set server");
                    dialog.setHeaderText((atomicAddress.get() == null ? "" : "Invalid address!\n") + "Please enter the address of the submission server");
                    HBox box = new HBox();
                    box.setAlignment(Pos.CENTER_LEFT);
                    TextField textField = new TextField(atomicAddress.get() == null ? "" : atomicAddress.get());
                    HBox.setHgrow(textField, Priority.ALWAYS);
                    box.getChildren().addAll(new Label("Server Address: "), textField);
                    dialog.getDialogPane().setContent(box);
                    dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
                    dialog.setResultConverter(param -> param == ButtonType.OK ? textField.getText() : null);
                    textField.requestFocus();
                    Optional<String> result = dialog.showAndWait();
                    if (result.isPresent())
                        atomicAddress.set(result.get());
                    else
                        atomicAddress.set(null);
                    synchronized (atomicAddress) {
                        atomicAddress.notify();
                    }
                });
                synchronized (atomicAddress) {
                    try {
                        atomicAddress.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                address = atomicAddress.get();
                break;
            case CMD:
                if (address != null)
                    System.out.println("Invalid address!");
                System.out.print("Enter the server to connect to: ");
                address = Main.readLine();
                break;
        }
        if (address == null)
            return null;
        String fullAddress = address;
        int port = NetUtil.DEFAULT_PORT;
        boolean valid = true;
        if (address.contains(":")) {
            if (StringUtils.countMatches(address, ':') != 1)
                valid = false;
            else {
                String[] split = address.split(":");
                address = split[0];
                try {
                    port = Integer.parseInt(split[1]);
                } catch (NumberFormatException e) {
                    valid = false;
                }
            }
        }
        if (valid)
            try {
                //noinspection ResultOfMethodCallIgnored
                InetAddress.getByName(address);
            } catch (UnknownHostException e) {
                valid = false;
            }
        if (!valid)
            return getServerAddress(fullAddress);
        return new Pair<>(address, port);
    }

    private Triple<String, String, Boolean> getCredentials() throws IOException {
        String user = GuiConfig.getConfigManager().username.get();
        String pass = GuiConfig.getConfigManager().getAccessToken();
        boolean isAccessToken = user != null && pass != null;
        if (!isAccessToken) {
            Platform.runLater(() -> GuiConfig.getConfigManager().username.set(null));
            switch (Main.getMode()) {
                case GUI:
                    final AtomicReference<Optional<Pair<String, String>>> result = new AtomicReference<>(null);
                    Platform.runLater(() -> {
                        Dialog<Pair<String, String>> dialog = new Dialog<>();
                        dialog.setTitle("Login");
                        dialog.setHeaderText("Login to " + GuiConfig.serverAddress.get());
                        ButtonType loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
                        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);
                        GridPane grid = new GridPane();
                        grid.setVgap(10);
                        grid.setHgap(10);
                        grid.setPadding(new Insets(20));
                        TextField username = new TextField();
                        username.setPromptText("Username");
                        PasswordField password = new PasswordField();
                        password.setPromptText("Password");
                        grid.add(new Label("Username:"), 0, 0);
                        grid.add(username, 1, 0);
                        grid.add(new Label("Password:"), 0, 1);
                        grid.add(password, 1, 1);
                        Node loginButton = dialog.getDialogPane().lookupButton(loginButtonType);
                        loginButton.disableProperty().bind(Bindings.or(Bindings.length(username.textProperty()).isEqualTo(0), Bindings.length(password.textProperty()).isEqualTo(0)));
                        dialog.getDialogPane().setContent(grid);
                        dialog.setResultConverter(buttonType -> new Pair<>(username.getText(), password.getText()));
                        username.requestFocus();
                        result.set(dialog.showAndWait());
                        synchronized (result) {
                            result.notify();
                        }
                    });
                    synchronized (result) {
                        try {
                            result.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if (result.get() != null && result.get().isPresent()) {
                        user = result.get().get().getKey();
                        pass = result.get().get().getValue();
                    } else
                        throw new IOException("CANCEL");
                    break;
                case CMD:
                    System.out.print("Please enter username: ");
                    user = Main.readLine();
                    System.out.print("Please enter password: ");
                    char[] passChar = Main.readPassword();
                    pass = new String(passChar);
                    break;
            }
        }
        return new ImmutableTriple<>(user, pass, isAccessToken);
    }

    public synchronized void disconnect() {
        disconnect(true);
    }

    private synchronized void disconnect(boolean releaseLock) {
        if (!connectionLock.isHeldByCurrentThread())
            return;
        try {
            try {
                if (in != null)
                    in.close();
                if (out != null)
                    out.close();
            } catch (IOException e) {
                logger.error("Failed to close stream", e);
            }
            try {
                if (socket != null)
                    socket.close();
            } catch (IOException e) {
                logger.error("Failed to close socket", e);
            }
        } finally {
            in = null;
            out = null;
            socket = null;
            setConnectionStatus(ConnectionStatus.DISCONNECTED);
            if (releaseLock && connectionLock.isHeldByCurrentThread())
                connectionLock.unlock();
        }
    }

    public synchronized boolean ping() throws IOException {
        if (socket != null && out != null && in != null) {
            out.writeUTF("PING");
            out.flush();
            try {
                return in.readUTF().equals("PONG");
            } catch (IOException ignored) {
            }
        }
        return false;
    }

    @Override
    public synchronized void sendMessage(String msg) throws IOException {
        if (connectionStatus == ConnectionStatus.CONNECTED && socket != null && out != null) {
            try {
                out.writeUTF(msg);
                out.flush();
            } catch (IOException e) {
                disconnect();
                throw e;
            }
        } else
            throw new IOException("Not connected to server");
    }

    @Override
    public synchronized String readMessage() throws IOException {
        try {
            return in.readUTF();
        } catch (IOException e) {
            disconnect();
            throw e;
        }
    }

    @Override
    public void handleErrorMsg(ErrorMsg msg) {
        // TODO: implement
        throw new RuntimeException("Not implemented: \n" + msg);
    }

    private boolean showCertWarning() {
        switch (Main.getMode()) {
            case CMD:
                System.out.println("Aris was unable to verify the server's identity");
                System.out.println("This could be due to the server using a self signed certificate or due to a third party attempting to intercept this connection");
                System.out.println("If you would like to connect to this server anyway either import the server's certificate with the --add-cert flag or run aris with the --allow-insecure flag");
                break;
            case GUI:
                final AtomicBoolean allowInsecure = new AtomicBoolean(false);
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Insecure Connection Warning");
                    alert.setHeaderText("Aris was unable to verify the authenticity of the server.");
                    alert.setContentText("If you would like to securely connect to the server please press \"Cancel\" then import " +
                            "the server's certificate file. If you would like to continue with an insecure connection anyway click \"Connect Anyway\"");
                    ButtonType cont = new ButtonType("Connect Anyway (INSECURE)");
                    alert.getButtonTypes().setAll(ButtonType.CANCEL, cont);
                    ((Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
                    alert.getDialogPane().getScene().getWindow().sizeToScene();
                    alert.getDialogPane().setPrefHeight(200);
                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent() && result.get() == cont)
                        allowInsecure.set(true);
                    synchronized (allowInsecure) {
                        allowInsecure.notify();
                    }
                });
                synchronized (allowInsecure) {
                    try {
                        allowInsecure.wait();
                    } catch (InterruptedException e) {
                        logger.error("Error while waiting for user response", e);
                    }
                }
                if (allowInsecure.get()) {
                    setAllowInsecure(true);
                    return true;
                }
                break;
        }
        return false;
    }

    public SimpleObjectProperty<ConnectionStatus> getConnectionStatusProperty() {
        return connectionStatusProperty;
    }

    public enum ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        HANDSHAKING,
        CERTIFICATE_WARNING,
        ERROR
    }

    private class CustomTrustManager implements X509TrustManager {

        private X509TrustManager manager, managerSS;

        CustomTrustManager() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
            if (SERVER_KEYSTORE_FILE.exists()) {
                KeyStore ks = KeyStore.getInstance("JKS");
                ks.load(null);
                KeyStore importedKeyStore = KeyStore.getInstance("JKS");
                FileInputStream fis = new FileInputStream(SERVER_KEYSTORE_FILE);
                importedKeyStore.load(fis, KEYSTORE_PASSWORD);
                Enumeration<String> aliases = importedKeyStore.aliases();
                while (aliases.hasMoreElements()) {
                    String a = aliases.nextElement();
                    if (importedKeyStore.isCertificateEntry(a))
                        ks.setCertificateEntry(a, importedKeyStore.getCertificate(a));
                }
                TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                factory.init(ks);
                factory.getTrustManagers();
                for (TrustManager m : factory.getTrustManagers())
                    if (m instanceof X509TrustManager)
                        managerSS = (X509TrustManager) m;
            }

            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            factory.getTrustManagers();
            for (TrustManager m : factory.getTrustManagers()) {
                if (m instanceof X509TrustManager)
                    manager = (X509TrustManager) m;
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            manager.checkClientTrusted(x509Certificates, s);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            try {
                try {
                    manager.checkServerTrusted(x509Certificates, s);
                } catch (CertificateException e) {
                    if (managerSS != null) {
                        try {
                            managerSS.checkServerTrusted(x509Certificates, s);
                        } catch (CertificateException e1) {
                            throw e;
                        }
                    } else {
                        throw e;
                    }
                }
            } catch (CertificateException e) {
                if (!allowInsecure || !(e.getCause() instanceof CertPathBuilderException))
                    throw e;
                else {
                    logger.warn("WARNING! The server's certificate chain is not trusted but the connection will continue due to the allow-insecure flag being set");
                    logger.warn("Using the client in this mode is not recommended. Instead you should add the server's self signed certificate to the trusted certificate store");
                }
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return manager.getAcceptedIssuers();
        }
    }

}
