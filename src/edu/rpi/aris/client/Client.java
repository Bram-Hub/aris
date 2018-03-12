package edu.rpi.aris.client;

import edu.rpi.aris.Main;
import edu.rpi.aris.gui.ConfigurationManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import sun.security.validator.ValidatorException;

import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;

public class Client {

    private static final char[] KEYSTORE_PASSWORD = "ARIS_CLIENT".toCharArray();
    private static final String KEYSTORE_FILENAME = "client.keystore";
    private static final File KEYSTORE_FILE = new File(ConfigurationManager.CONFIG_DIR, KEYSTORE_FILENAME);
    private static Logger logger = LogManager.getLogger(Client.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new BouncyCastleJsseProvider());
        System.setProperty("jdk.tls.ephemeralDHKeySize", "4096");
    }

    private SSLSocketFactory socketFactory = null;
    private SSLSocket socket;
    private String serverAddress;
    private int port;
    private DataInputStream in;
    private DataOutputStream out;
    private String errorString = null;
    private ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;
    private X509Certificate selfSignedCert = null;
    private CustomTrustManager trustManager;
    private MessageReceivedListener messageListener;
    private boolean ping = false;

    public Client(String domainName, int port, MessageReceivedListener messageListener) {
        serverAddress = domainName;
        this.port = port;
        this.messageListener = messageListener;
    }

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

    private TrustManager[] getTrustManagers() {
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(null);
            ks.setCertificateEntry("self_signed", selfSignedCert);

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(ks);
            trustManager = new CustomTrustManager(selfSignedCert);
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

    private synchronized void setupConnection() throws IOException {
        connectionStatus = ConnectionStatus.CONNECTING;
        socket = (SSLSocket) getSocketFactory().createSocket(serverAddress, port);
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
                connectionStatus = ConnectionStatus.ERROR;
            }
            synchronized (Client.this) {
                Client.this.notify();
            }
        });
        try {
            connectionStatus = ConnectionStatus.HANDSHAKING;
            socket.startHandshake();
            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (SSLHandshakeException e) {
            logger.error("SSL Handshake failed", e);
            Throwable cause = e.getCause();
            if (cause instanceof ValidatorException) {
                connectionStatus = ConnectionStatus.CERTIFICATE_WARNING;
            } else {
                connectionStatus = ConnectionStatus.ERROR;
                errorString = e.getMessage();
            }
        }
        switch (connectionStatus) {
            case ERROR:
                socket.close();
                socket = null;
                connectionStatus = ConnectionStatus.DISCONNECTED;
                throw new IOException(errorString);
            case CERTIFICATE_WARNING:
                if (showCertWarning()) {
                    selfSignedCert = trustManager.getLastCertificate();
                    socketFactory = null;
                }
                socket.close();
                socket = null;
                connectionStatus = ConnectionStatus.DISCONNECTED;
                if (selfSignedCert != null)
                    setupConnection();
                else
                    throw new IOException("Failed to verify remote server");
                break;
            case HANDSHAKING:
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                out = new DataOutputStream(socket.getOutputStream());
                connectionStatus = ConnectionStatus.CONNECTED;
                System.out.println("Connected");
                new Thread(this::messageWatch).start();
                break;
            default:
                socket.close();
                socket = null;
                connectionStatus = ConnectionStatus.DISCONNECTED;
                throw new IOException("Client socket is in an unexpected state");
        }
    }

    public synchronized void connect() throws IOException {
        if (!ping()) {
            disconnect();
            setupConnection();
        }
    }

    public synchronized void disconnect() {
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
        socket = null;
    }

    public synchronized boolean ping() throws IOException {
        if (socket != null) {
            ping = false;
            out.writeUTF("PING");
            out.flush();
            synchronized (this) {
                try {
                    wait(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return ping;
        }
        return false;
    }

    public synchronized void sendMessage(String msg) throws IOException {
        if (connectionStatus == ConnectionStatus.CONNECTED && socket != null && out != null) {
            out.writeUTF(msg);
            out.flush();
        } else
            throw new IOException("Not connected to server");
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private void messageWatch() {
        try {
            while (true) {
                String message = in.readUTF();
                if (message.equals("PONG")) {
                    ping = true;
                    synchronized (this) {
                        notify();
                    }
                } else
                    messageListener.messageReceived(message);
            }
        } catch (IOException e) {
            disconnect();
        }
    }

    private boolean showCertWarning() {
        switch (Main.getMode()) {
            case CMD:
                System.out.println("Aris was unable to verify the server's identity");
                System.out.println("This could be due to the server running in self signed mode or because someone is attempting to hijack the connection");
                System.out.print("Would you like to continue this INSECURE connection? (y/N) ");
                try {
                    if (Main.SYSTEM_IN.readLine().equalsIgnoreCase("y"))
                        return true;
                } catch (IOException e) {
                    logger.error("Error reading from stdin", e);
                }
                break;
            case GUI:
                //TODO
                break;
            case SERVER:
                logger.fatal("The client is being used while Aris is running in server mode");
                logger.fatal("This shouldn't happen");
                logger.fatal("Program will exit");
                System.exit(1);
                break;
        }
        return false;
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

        private X509TrustManager manager;
        private X509Certificate lastCert;

        public CustomTrustManager(X509Certificate selfSignedCert) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
            KeyStore ks = null;
            if (selfSignedCert != null) {
                ks = KeyStore.getInstance("JKS");
                ks.load(null);
                ks.setCertificateEntry("self_signed", selfSignedCert);
            }
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(ks);
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
            lastCert = x509Certificates[0];
            manager.checkServerTrusted(x509Certificates, s);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return manager.getAcceptedIssuers();
        }

        X509Certificate getLastCertificate() {
            return lastCert;
        }
    }

}
