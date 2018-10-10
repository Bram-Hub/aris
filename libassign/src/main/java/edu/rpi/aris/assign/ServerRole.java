package edu.rpi.aris.assign;

public class ServerRole {

    private final int id;
    private final String name;
    private final int rollRank;

    public ServerRole(int id, String name, int rollRank) {
        this.id = id;
        this.name = name;
        this.rollRank = rollRank;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getRollRank() {
        return rollRank;
    }
}
