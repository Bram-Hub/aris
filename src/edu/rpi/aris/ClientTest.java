package edu.rpi.aris;

import edu.rpi.aris.net.client.Client;

import java.io.IOException;

public class ClientTest {

    public static void main(String[] args) throws IOException {
        Client client = new Client("k0d3r.me", 9000, false, System.out::println);
        client.connect();
        String line;
        while ((line = Main.SYSTEM_IN.readLine()) != null) {
            if (line.equalsIgnoreCase("ping")) {
                if (client.ping()) {
                    System.out.println("Pong received");
                } else {
                    System.out.println("Ping failed");
                }
            } else if (line.equalsIgnoreCase("exit")) {
                client.disconnect();
                break;
            } else
                client.sendMessage(line);
        }
    }
}
