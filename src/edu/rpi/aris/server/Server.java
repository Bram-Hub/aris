package edu.rpi.aris.server;

import edu.rpi.aris.gui.ConfigurationManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.security.auth.x500.X500Principal;
import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static final char[] KEYSTORE_PASSWORD = "ARIS_SERVER".toCharArray();
    private static final String KEYSTORE_FILENAME = "server.keystore";
    private static final File KEYSTORE_FILE = new File(ConfigurationManager.CONFIG_DIR, KEYSTORE_FILENAME);

    static {
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new BouncyCastleJsseProvider());
        System.setProperty("jdk.tls.ephemeralDHKeySize", "4096");
    }

    private final int port;
    private final File caCertificate;
    private final File privateKey;
    private Logger logger = LogManager.getLogger(Server.class);
    private ServerSocket serverSocket;
    private boolean selfSign;

    public Server(int port, File caCertificate, File privateKey) throws FileNotFoundException {
        this.port = port;
        this.caCertificate = caCertificate;
        this.privateKey = privateKey;
        if (caCertificate == null || privateKey == null) {
            logger.warn("CA certificate and key not specified");
            logger.warn("Running server in self signing mode");
            logger.warn("Running the server in this mode is potentially insecure and not recommended");
            selfSign = true;
        } else {
            selfSign = false;
            if (!caCertificate.exists())
                throw new FileNotFoundException("ca certificate \"" + caCertificate.getPath() + "\" does not exist");
            if (!privateKey.exists())
                throw new FileNotFoundException("private key \"" + privateKey.getPath() + "\" does not exist");
        }
        System.out.println("End create server");
    }

    public void run() {
        try {
            System.out.println("Start run");
            serverSocket = getServerSocketFactory().createServerSocket(port);
            ExecutorService threadPool = Executors.newCachedThreadPool();
            System.out.println("Waiting for connection");
            while (true) {
                SSLSocket socket = (SSLSocket) serverSocket.accept();
                String clientName = socket.getInetAddress().getHostName();
                System.out.println("Incoming connection from: " + clientName);
                threadPool.execute(() -> {
                    try {
                        socket.setUseClientMode(false);
                        socket.setNeedClientAuth(false);
                        socket.addHandshakeCompletedListener(handshakeCompletedEvent -> {
                            System.out.println("[" + clientName + "] Handshake success");
                            try (DataInputStream in = new DataInputStream(socket.getInputStream());
                                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                                String line;
                                while (true) {
                                    line = in.readUTF();
                                    System.out.println("[" + clientName + "] Message Received: " + line);
                                    if (line.equalsIgnoreCase("ping"))
                                        out.writeUTF("PONG");
                                    else
                                        out.writeUTF(line);
                                    out.flush();
                                }
                            } catch (IOException ignored) {
                                //Ignored so we don't throw an exception every time a client disconnects
                            }
                            try {
                                socket.close();
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                            System.out.println("[" + clientName + "] Disconnected");
                        });
                        System.out.println("[" + clientName + "] Start handshake");
                        socket.startHandshake();
                    } catch (Throwable e) {
                        logger.error("Socket error", e);
                        try {
                            socket.close();
                        } catch (IOException e1) {
                            logger.error("Error closing socket", e);
                        }
                    }
                });
            }
        } catch (IOException e) {
            logger.fatal("Failed to start Aris Server", e);
        }
    }

    private SSLServerSocketFactory getServerSocketFactory() {
        System.out.println("Get socket factory");
        SSLServerSocketFactory socketFactory = null;
        try {
            // set up key manager to do server authentication
            SSLContext context;
            KeyManagerFactory keyManagerFactory;
            KeyStore keyStore;

            context = SSLContext.getInstance("TLSv1.2");

            keyManagerFactory = KeyManagerFactory.getInstance("X.509");
            keyStore = getKeyStore();
            System.out.println("Init key factory");
            keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD);
            System.out.println("Init context");
            context.init(keyManagerFactory.getKeyManagers(), null, null);

            System.out.println("Get socket factory");
            socketFactory = context.getServerSocketFactory();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Done socket factory");
        return socketFactory;
    }

    private KeyStore getKeyStore() {
        System.out.println("Get key store");
        if (selfSign) {
            KeyStore ks = null;
            if (KEYSTORE_FILE.exists()) {
                try {
                    ks = KeyStore.getInstance("JKS");
                    FileInputStream fis = new FileInputStream(KEYSTORE_FILE);
                    ks.load(fis, KEYSTORE_PASSWORD);
                    fis.close();
                } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException e) {
                    logger.error("Failed to load keystore", e);
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
                    ks.setKeyEntry("aris_server", kp.getPrivate(), KEYSTORE_PASSWORD, new X509Certificate[]{cert});

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

                    return ks;
                } catch (NoSuchAlgorithmException | NoSuchProviderException | CertificateException | OperatorCreationException | IOException | KeyStoreException e) {
                    e.printStackTrace();
                }
            }
        } else {
            try {
                PEMParser parser = new PEMParser(new FileReader(caCertificate));
                ArrayList<X509Certificate> certs = new ArrayList<>();
                Object objCert;
                while ((objCert = parser.readObject()) != null) {
                    if (!(objCert instanceof X509CertificateHolder))
                        throw new IOException("Provided certificate file does not contain an X509 encoded certificate");
                    X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate((X509CertificateHolder) objCert);
                    certs.add(cert);
                }
                parser = new PEMParser(new FileReader(privateKey));
                Object objKey = parser.readObject();
                if (!(objKey instanceof PrivateKeyInfo))
                    throw new IOException("Provided private key file does not contain a private key");
                PrivateKey key = new JcaPEMKeyConverter().setProvider("BC").getPrivateKey((PrivateKeyInfo) objKey);
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(null);
                X509Certificate[] certArr = new X509Certificate[certs.size()];
                certArr = certs.toArray(certArr);
                keyStore.setKeyEntry("aris_server", key, KEYSTORE_PASSWORD, certArr);
                System.out.println("Done key store");
                return keyStore;
            } catch (CertificateException | IOException | KeyStoreException | NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

}
