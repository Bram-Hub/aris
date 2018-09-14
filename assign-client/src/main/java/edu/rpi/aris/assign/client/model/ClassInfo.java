package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.ServerRole;

import java.util.Objects;

public class ClassInfo implements Comparable<ClassInfo> {

    private final int classId;
    private final String className;
    private final ServerRole userRole;

    public ClassInfo(int classId, String className, ServerRole userRole) {
        Objects.requireNonNull(className);
        this.classId = classId;
        this.className = className;
        this.userRole = userRole;
    }

    public int getClassId() {
        return classId;
    }

    public String getClassName() {
        return className;
    }

    public ServerRole getUserRole() {
        return userRole;
    }

    @Override
    public int hashCode() {
        return Objects.hash(classId, className);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ClassInfo))
            return false;
        ClassInfo info = (ClassInfo) obj;
        return classId == info.classId && className.equals(info.className);
    }

    @Override
    public int compareTo(ClassInfo o) {
        return className.compareTo(o.className);
    }

}
