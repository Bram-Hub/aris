package edu.rpi.aris.assign.server;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.Update;
import edu.rpi.aris.assign.UserType;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
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
import java.net.ServerSocket;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class AssignServer implements Runnable {

    private static final ServerConfig config;
    private static final char[] KEYSTORE_PASSWORD = "ARIS_SERVER".toCharArray();
    private static final String KEYSTORE_FILENAME = "server.keystore";

    private static final Logger logger = LogManager.getLogger(AssignServer.class);

    static {
        ServerConfig cfg = null;
        try {
            cfg = ServerConfig.getInstance();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
        config = cfg;
    }

    private static final File KEYSTORE_FILE = new File(config != null ? config.getStorageDir() : null, KEYSTORE_FILENAME);
    private static final File SELF_SIGNED_CERT = new File(config != null ? config.getStorageDir() : null, "self-signed-cert.pem");

    static {
        if (Security.getProvider("BC") == null)
            Security.addProvider(new BouncyCastleProvider());
        if (Security.getProvider("BCJSSE") == null)
            Security.addProvider(new BouncyCastleJsseProvider());
        System.setProperty("jdk.tls.ephemeralDHKeySize", "2048");
    }

    private final int port;
    //    private final File caCertificate;
//    private final File privateKey;
    private final Update update = new Update(Update.Stream.SERVER, new File(System.getProperty("java.io.tmpdir"), "aris-update"));
    private boolean selfSign, stopServer, shutdown;
    private DatabaseManager dbManager;
    private Timer certExpireTimer = null;
    private ServerSocket serverSocket;
    private ReentrantLock serverLock = new ReentrantLock(true);
    private HashSet<ClientHandler> clients = new HashSet<>();
    private Thread serverThread = null;
    private final Thread shutdownHook = new Thread(this::shutdown, "AssignServer Shutdown Hook");

    public AssignServer(int port, File caCertificate, File privateKey) throws FileNotFoundException {
        logger.info("Preparing server");
        this.port = port;
        stopServer = false;
        shutdown = false;
        if (caCertificate != null && privateKey != null) {
            config.setCaFile(caCertificate);
            config.setKeyFile(privateKey);
        }
        if (config.getCaFile() == null || config.getKeyFile() == null) {
            logger.warn("CA certificate and key not specified");
            logger.warn("Running server in self signing mode");
            logger.warn("Running the server in this mode is potentially insecure and not recommended");
            selfSign = true;
        } else {
            selfSign = false;
            if (!config.getCaFile().exists())
                throw new FileNotFoundException("ca certificate \"" + config.getCaFile().getPath() + "\" does not exist");
            if (!config.getKeyFile().exists())
                throw new FileNotFoundException("private key \"" + config.getKeyFile().getPath() + "\" does not exist");
        }
        try {
            dbManager = new DatabaseManager(config.getDbHost(), config.getDbPort(), config.getDbName(), config.getDbUser(), config.getDbPass());
        } catch (IOException | SQLException e) {
            RuntimeException e1 = new RuntimeException("Failed to open sql database", e);
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e1, true);
        }
        Timer updateTimer = new Timer();
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 4);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        if (calendar.before(Calendar.getInstance()))
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                update.checkUpdate();
            }
        }, calendar.getTime(), 1000 * 60 * 60 * 24); // run every 24 hours
        logger.info("Update check scheduled for " + NetUtil.DATE_FORMAT.format(calendar.getTime()));
        logger.info("AssignServer preparation complete");
    }

    @Override
    public void run() {
        try {
            if (!serverLock.tryLock(10, TimeUnit.SECONDS))
                return;
        } catch (InterruptedException e) {
            logger.error("An error occurred while attempting to acquire the server lock", e);
            return;
        }
        try {
            serverThread = Thread.currentThread();
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            logger.info("Starting AssignServer on port " + port + (port == 9001 ? " (IT'S OVER 9000!!!)" : ""));
            serverSocket = getServerSocketFactory().createServerSocket(port);
            ExecutorService threadPool = Executors.newCachedThreadPool(new ThreadFactory() {

                private int count = 0;

                @Override
                public Thread newThread(Runnable r) {
                    int i;
                    synchronized (this) {
                        i = ++count;
                    }
                    return new Thread(r, "ClientHandler-ThreadPool-" + i);
                }
            });
            logger.info("AssignServer Started");
            logger.info("Waiting for connections");
            //noinspection InfiniteLoopStatement
            while (true) {
                ClientHandler client = new ClientHandler((SSLSocket) serverSocket.accept(), dbManager) {
                    @Override
                    public void onDisconnect(ClientHandler clientHandler) {
                        clients.remove(clientHandler);
                    }
                };
                clients.add(client);
                threadPool.execute(client);
            }
        } catch (IOException e) {
            if (!stopServer)
                logger.fatal("An error occurred with the Aris ServerSocket", e);
        } catch (Throwable e) {
            logger.fatal("A fatal error occurred in the main server loop", e);
            shutdown = true;
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
            System.exit(1);
        } finally {
            if (!shutdown) {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
                serverThread = null;
            }
            logger.info("ServerSocket closed");
            serverLock.unlock();
        }
    }

    private void shutdown() {
        logger.info("Stopping server");
        stopServer = true;
        shutdown = true;
        try {
            serverSocket.close();
            if (serverThread != null)
                serverThread.join();
        } catch (IOException | InterruptedException e) {
            logger.error("Error stopping server", e);
        }
        logger.info("Disconnecting clients");
        for (ClientHandler client : clients)
            client.disconnect();
        logger.info("AssignServer shutdown");
        LogManager.shutdown();
    }

    private SSLServerSocketFactory getServerSocketFactory() {
        SSLServerSocketFactory socketFactory = null;
        try {
            // set up key manager to do server authentication
            SSLContext context;
            KeyManagerFactory keyManagerFactory;
            KeyStore keyStore;

            context = SSLContext.getInstance("TLSv1.2");

            keyManagerFactory = KeyManagerFactory.getInstance("X.509");
            logger.info("Preparing server certificate");
            try {
                keyStore = getKeyStore();
            } catch (Throwable e) {
                throw new RuntimeException("Failed to get certificate keystore", e);
            }
            if (keyStore == null)
                throw new NullPointerException("Failed to get certificate keystore");
            Certificate[] certChain = keyStore.getCertificateChain("aris_server");
            X509Certificate cert = (X509Certificate) certChain[0];
            Date expireDate = cert.getNotAfter();
            if (expireDate.before(new Date())) {
                if (selfSign && KEYSTORE_FILE.delete() && SELF_SIGNED_CERT.delete()) {
                    logger.warn("The server's self-signed certificate has expired.");
                    logger.warn("A new certificate will now be generated");
                    logger.warn("Please forward the newly generated certificate to any clients that would like to connect");
                    return getServerSocketFactory();
                } else if (selfSign) {
                    logger.error("Failed to automatically regenerate self-signed certificate");
                    logger.error("Please delete the following file then restart the server");
                    logger.error(KEYSTORE_FILE.getCanonicalPath());
                    logger.error(SELF_SIGNED_CERT.getCanonicalPath());
                    throw new CertificateException("Failed to automatically regenerate self-signed certificate");
                } else {
                    throw new CertificateException("AssignServer's provided certificate is expired");
                }
            }
            if (certExpireTimer != null)
                certExpireTimer.cancel();
            certExpireTimer = new Timer("Certificate expiration Timer", true);
            certExpireTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        logger.info("AssignServer's certificate has expired.");
                        logger.info("AssignServer will now reload to read any new available certificates");
                        stopServer = true;
                        serverSocket.close();
                        new Thread(this, "ServerSocket-Listen").start();
                    } catch (IOException e) {
                        logger.error("Failed to stop server while attempting to restart on expired certificate", e);
                    }
                }
            }, expireDate);
            keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD);
            context.init(keyManagerFactory.getKeyManagers(), null, null);
            socketFactory = context.getServerSocketFactory();
        } catch (Exception e) {
            logger.error("Failed to get socket factory", e);
        }
        return socketFactory;
    }

    private KeyStore getKeyStore() {
        if (selfSign) {
            KeyStore ks = null;
            if (KEYSTORE_FILE.exists()) {
                try {
                    logger.info("Loading stored self-signed certificate");
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
                    logger.info("Generating self-signed certificate");
                    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
                    keyPairGenerator.initialize(4096, new SecureRandom());
                    KeyPair kp = keyPairGenerator.genKeyPair();

                    if (config.getDomain() == null)
                        throw new RuntimeException("Domain must be set if the server is running in self signing mode");

                    X500Principal principal = new X500Principal("CN=" + config.getDomain());
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
                } catch (NoSuchAlgorithmException | NoSuchProviderException | CertificateException | OperatorCreationException | IOException | KeyStoreException e) {
                    e.printStackTrace();
                }
            }
            if (ks != null && !SELF_SIGNED_CERT.exists()) {
                try {
                    logger.info("Exporting self-signed certificate");
                    if (!SELF_SIGNED_CERT.getParentFile().exists())
                        if (!SELF_SIGNED_CERT.getParentFile().mkdirs())
                            throw new IOException("Failed to export self signed certificate");
                    if (!SELF_SIGNED_CERT.createNewFile())
                        throw new IOException("Failed to export self signed certificate");
                    KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) ks.getEntry("aris_server", new KeyStore.PasswordProtection(KEYSTORE_PASSWORD));
                    X509Certificate cert = (X509Certificate) entry.getCertificateChain()[0];
                    JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(SELF_SIGNED_CERT));
                    JcaX509CertificateHolder holder = new JcaX509CertificateHolder(cert);
                    pemWriter.writeObject(holder);
                    pemWriter.close();
                    logger.info("Exported self signed certificate");
                } catch (Throwable e) {
                    logger.error("An error occurred while attempting to export the self-signed certificate", e);
                    if (SELF_SIGNED_CERT.exists())
                        //noinspection ResultOfMethodCallIgnored
                        SELF_SIGNED_CERT.delete();
                }
            }
            try {
                logger.info("The server's self signed certificate can be found at " + SELF_SIGNED_CERT.getCanonicalPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
            return ks;
        } else {
            try {
                logger.info("Loading provided certificate");
                PEMParser parser = new PEMParser(new FileReader(config.getCaFile()));
                ArrayList<X509Certificate> certs = new ArrayList<>();
                Object objCert;
                while ((objCert = parser.readObject()) != null) {
                    if (!(objCert instanceof X509CertificateHolder))
                        throw new IOException("Provided certificate file does not contain an X509 encoded certificate");
                    X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate((X509CertificateHolder) objCert);
                    certs.add(cert);
                }
                parser = new PEMParser(new FileReader(config.getKeyFile()));
                Object objKey = parser.readObject();
                if (!(objKey instanceof PrivateKeyInfo))
                    throw new IOException("Provided private key file does not contain a private key");
                PrivateKey key = new JcaPEMKeyConverter().setProvider("BC").getPrivateKey((PrivateKeyInfo) objKey);
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(null);
                X509Certificate[] certArr = new X509Certificate[certs.size()];
                certArr = certs.toArray(certArr);
                keyStore.setKeyEntry("aris_server", key, KEYSTORE_PASSWORD, certArr);
                return keyStore;
            } catch (CertificateException | IOException | KeyStoreException | NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public synchronized boolean addUser(String username, String pass, UserType userType, boolean forceReset) throws SQLException {
        Pair<String, String> result = dbManager.createUser(username, pass, userType, forceReset);
        return result != null && result.getRight().equals(NetUtil.OK);
    }

    public synchronized boolean checkUpdate() {
        if (update.checkUpdate() && update.update()) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
            shutdown();
            update.exit();
            return true;
        }
        return false;
    }

    public DatabaseManager getDbManager() {
        return dbManager;
    }

}
