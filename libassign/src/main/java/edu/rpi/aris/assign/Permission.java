package edu.rpi.aris.assign;

public class Permission {

    private int rollId;
    private final Perm perm;

    public Permission(Perm perm, int rollId) {
        this.perm = perm;
        this.rollId = rollId;
    }

    public String getName() {
        return perm.name();
    }

    public String getDescription() {
        return perm.description;
    }

    public int getRollId() {
        return rollId;
    }

    public Perm getPerm() {
        return perm;
    }

    public void setRoleId(int id) {
        rollId = id;
    }
}
