package edu.rpi.aris.assign;

public class Permission {

    private final String name;
    private final int rollId;

    public Permission(String name, int rollId) {
        this.name = name;
        this.rollId = rollId;
    }

    public String getName() {
        return name;
    }

    public int getRollId() {
        return rollId;
    }
}
