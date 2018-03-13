package edu.rpi.aris.net.server;

import edu.rpi.aris.Main;
import edu.rpi.aris.net.NetUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLSocket;
import java.io.*;

public class ClientHandler implements Runnable {

    private static final Logger logger = LogManager.getLogger(ClientHandler.class);

    private final SSLSocket socket;
    private String clientName, clientVersion;
    private DataInputStream in;
    private DataOutputStream out;

    public ClientHandler(SSLSocket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            clientName = socket.getInetAddress().getHostName();
            logger.info("[" + clientName + "] Incoming connection from " + socket.getInetAddress().toString());
            socket.setUseClientMode(false);
            socket.setNeedClientAuth(false);
            socket.addHandshakeCompletedListener(handshakeCompletedEvent -> {
                logger.info("[" + clientName + "] Handshake complete");
                synchronized (socket) {
                    socket.notify();
                }
            });
            logger.info("[" + clientName + "] Starting handshake");
            socket.startHandshake();
            synchronized (socket) {
                try {
                    socket.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            logger.info("[" + clientName + "] Connection successful");
            logger.info("[" + clientName + "] Waiting for client version");
            clientVersion = in.readUTF();
            logger.info("[" + clientName + "] Version: " + clientVersion);
            if (!checkVersion())
                return;
            messageWatch();
        } catch (Throwable e) {
            logger.error("[" + clientName + "] Socket error", e);
        } finally {
            disconnect();
        }
    }

    private boolean checkVersion() {
        String[] split = clientVersion.split(" ");
        if (split.length != 2) {
            logger.error("[" + clientName + "] Invalid client version string: " + clientVersion);
            return false;
        }
        if (!split[0].equals(NetUtil.ARIS_NAME)) {
            logger.error("[" + clientName + "] Invalid client program name: " + split[0]);
            return false;
        }
        if (NetUtil.versionCompare(Main.VERSION, split[1]) < 0) {
            logger.warn("[" + clientName + "] Client's version is newer than server");
            logger.warn("[" + clientName + "] This may or may not cause problems");
        }
        return true;
    }

    private void messageWatch() {
        while (true) {
            try {
                String msg = in.readUTF();
            } catch (IOException ignored) {
                // ignored so we don't print an exception whenever we disconnects
            }
        }
    }

    public void disconnect() {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                logger.error("[" + clientName + "] Failed to close input stream", e);
            }
        }
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                logger.error("[" + clientName + "] Failed to close output stream", e);
            }
        }
        try {
            socket.close();
        } catch (IOException e) {
            logger.error("[" + clientName + "] Failed to close socket");
        }
        logger.info("[" + clientName + "] Disconnected");
    }

}
