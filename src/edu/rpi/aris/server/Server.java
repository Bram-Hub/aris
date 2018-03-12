package edu.rpi.aris.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.ServerSocket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static final char[] KEYSTORE_PASSWORD = "ARIS_SERVER".toCharArray();

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

    public Server(int port, File caCertificate, File privateKey) throws FileNotFoundException {
        this.port = port;
        this.caCertificate = caCertificate;
        this.privateKey = privateKey;
        if (caCertificate == null || privateKey == null)
            throw new NullPointerException("caCertificate and privateKey cannot be null");
        if (!caCertificate.exists())
            throw new FileNotFoundException("ca certificate \"" + caCertificate.getPath() + "\" does not exist");
        if (!privateKey.exists())
            throw new FileNotFoundException("private key \"" + privateKey.getPath() + "\" does not exist");
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
            keyStore.setKeyEntry("aris", key, KEYSTORE_PASSWORD, certArr);
            System.out.println("Done key store");
            return keyStore;
        } catch (CertificateException | IOException | KeyStoreException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

}
