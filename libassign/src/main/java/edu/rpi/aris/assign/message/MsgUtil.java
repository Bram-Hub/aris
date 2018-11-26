package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.GradingStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;

public class MsgUtil {

    private static Logger logger = LogManager.getLogger(MsgUtil.class);

    public static class AssignmentData {

        public final String name, assignedBy;
        public final int id;
        public final HashSet<Integer> problems = new HashSet<>();
        public final ZonedDateTime dueDateUTC;

        AssignmentData(String name, String assignedBy, ZonedDateTime dueDateUTC, int id, Collection<Integer> problems) {
            this.name = name;
            this.assignedBy = assignedBy;
            this.dueDateUTC = dueDateUTC;
            this.id = id;
            this.problems.addAll(problems);
        }

        // DO NOT REMOVE!! Default constructor is required for gson deserialization
        private AssignmentData() {
            name = null;
            assignedBy = null;
            id = 0;
            dueDateUTC = null;
        }

        public boolean checkValid() {
            return name != null && assignedBy != null && id > 0 && dueDateUTC != null;
        }

    }

    public static class SubmissionInfo {

        public final int uid;
        public final int sid;
        public final int pid;
        public final int cid;
        public final int aid;
        public final GradingStatus status;
        public final double grade;
        public final String statusStr;
        public final ZonedDateTime submissionTime;

        SubmissionInfo(int uid, int sid, int pid, int cid, int aid, double grade, GradingStatus status, String statusStr, ZonedDateTime submissionTime) {
            this.uid = uid;
            this.sid = sid;
            this.pid = pid;
            this.cid = cid;
            this.aid = aid;
            this.status = status;
            this.grade = grade;
            this.statusStr = statusStr;
            this.submissionTime = submissionTime;
        }

        // DO NOT REMOVE!! Default constructor is required for gson deserialization
        private SubmissionInfo() {
            this.uid = 0;
            this.sid = 0;
            this.pid = 0;
            this.cid = 0;
            this.aid = 0;
            this.grade = 0;
            this.status = null;
            this.statusStr = null;
            this.submissionTime = null;
        }

        public boolean checkValid() {
            return uid > 0 && sid > 0 && pid > 0 && cid > 0 && aid > 0 && status != null && statusStr != null && submissionTime != null;
        }

    }

    public static class ProblemInfo {

        public final int pid;
        public final String name;
        public final String createdBy;
        public final String moduleName;
        public final ZonedDateTime createdDateUTC;
        public final String problemHash;

        ProblemInfo(int pid, String name, String createdBy, ZonedDateTime createdDateUTC, String moduleName, String problemHash) {
            this.pid = pid;
            this.name = name;
            this.createdBy = createdBy;
            this.createdDateUTC = createdDateUTC;
            this.moduleName = moduleName;
            this.problemHash = problemHash;
        }

        // DO NOT REMOVE!! Default constructor is required for gson deserialization
        private ProblemInfo() {
            this.pid = 0;
            this.name = null;
            this.createdBy = null;
            this.createdDateUTC = null;
            this.moduleName = null;
            this.problemHash = null;
        }

        public boolean checkValid() {
            return pid > 0 && name != null && createdBy != null && createdDateUTC != null && moduleName != null && problemHash != null;
        }

    }

    public static class UserInfo implements Comparable<UserInfo> {

        public final int uid;
        @NotNull
        public final String username;
        @NotNull
        public final String fullName;
        public final int defaultRole;

        public UserInfo(int uid, @NotNull String username, @NotNull String fullName, int defaultRole) {
            this.uid = uid;
            this.username = username;
            this.fullName = fullName;
            this.defaultRole = defaultRole;
        }

        private UserInfo() {
            uid = 0;
            username = "";
            fullName = "";
            defaultRole = 0;
        }

        @Contract(value = "null -> false", pure = true)
        @Override
        public boolean equals(Object obj) {
            return obj instanceof UserInfo && uid == ((UserInfo) obj).uid;
        }

        @Override
        public int hashCode() {
            return uid;
        }

        @Override
        public int compareTo(@NotNull MsgUtil.UserInfo o) {
            return username.compareTo(o.username);
        }
    }

}
