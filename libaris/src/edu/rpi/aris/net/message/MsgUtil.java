package edu.rpi.aris.net.message;

import edu.rpi.aris.net.GradingStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

public class MsgUtil {

    private static Logger logger = LogManager.getLogger(MsgUtil.class);

    public static class AssignmentData {

        public final String name, assignedBy;
        public final int id;
        private final String zdtString;
        private transient ZonedDateTime dueDateUTC;

        AssignmentData(String name, String assignedBy, ZonedDateTime dueDateUTC, int id) {
            this.name = name;
            this.assignedBy = assignedBy;
            this.dueDateUTC = dueDateUTC;
            this.id = id;
            zdtString = dueDateUTC.toString();
        }

        // DO NOT REMOVE!! Default constructor is required for gson deserialization
        private AssignmentData() {
            name = null;
            assignedBy = null;
            id = 0;
            dueDateUTC = null;
            zdtString = null;
        }

        public ZonedDateTime getDueDate() {
            try {
                if (dueDateUTC == null && zdtString != null)
                    dueDateUTC = ZonedDateTime.parse(zdtString);
            } catch (DateTimeParseException e) {
                logger.error(e);
            }
            return dueDateUTC;
        }

        public boolean checkValid() {
            if (dueDateUTC == null && zdtString != null)
                try {
                    dueDateUTC = ZonedDateTime.parse(zdtString);
                } catch (DateTimeParseException e) {
                    logger.error(e);
                    return false;
                }
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
        public final String statusStr;
        private final String zdtString;
        private transient ZonedDateTime submissionTime;

        SubmissionInfo(int uid, int sid, int pid, int cid, int aid, GradingStatus status, String statusStr, ZonedDateTime submissionTime) {
            this.uid = uid;
            this.sid = sid;
            this.pid = pid;
            this.cid = cid;
            this.aid = aid;
            this.status = status;
            this.statusStr = statusStr;
            this.submissionTime = submissionTime;
            zdtString = submissionTime.toString();
        }

        // DO NOT REMOVE!! Default constructor is required for gson deserialization
        private SubmissionInfo() {
            this.uid = 0;
            this.sid = 0;
            this.pid = 0;
            this.cid = 0;
            this.aid = 0;
            this.status = null;
            this.statusStr = null;
            this.submissionTime = null;
            zdtString = null;
        }

        public ZonedDateTime getSubmissionTime() {
            try {
                if (submissionTime == null && zdtString != null)
                    submissionTime = ZonedDateTime.parse(zdtString);
            } catch (DateTimeParseException e) {
                logger.error(e);
            }
            return submissionTime;
        }

        public boolean checkValid() {
            if (submissionTime == null && zdtString != null)
                try {
                    submissionTime = ZonedDateTime.parse(zdtString);
                } catch (DateTimeParseException e) {
                    logger.error(e);
                    return false;
                }
            return uid > 0 && sid > 0 && pid > 0 && cid > 0 && aid > 0 && status != null && statusStr != null && submissionTime != null;
        }

    }

    public static class ProofInfo {

        public final int pid;
        public final String name;
        public final String createdBy;
        private final String zdtString;
        private transient ZonedDateTime createdDateUTC;

        ProofInfo(int pid, String name, String createdBy, ZonedDateTime createdDateUTC) {
            this.pid = pid;
            this.name = name;
            this.createdBy = createdBy;
            this.createdDateUTC = createdDateUTC;
            zdtString = createdDateUTC.toString();
        }

        // DO NOT REMOVE!! Default constructor is required for gson deserialization
        private ProofInfo() {
            this.pid = 0;
            this.name = null;
            this.createdBy = null;
            this.createdDateUTC = null;
            zdtString = null;
        }

        public ZonedDateTime getCreationTime() {
            try {
                if (createdDateUTC == null && zdtString != null)
                    createdDateUTC = ZonedDateTime.parse(zdtString);
            } catch (DateTimeParseException e) {
                logger.error(e);
            }
            return createdDateUTC;
        }

        public boolean checkValid() {
            if (createdDateUTC == null && zdtString != null)
                try {
                    createdDateUTC = ZonedDateTime.parse(zdtString);
                } catch (DateTimeParseException e) {
                    logger.error(e);
                    return false;
                }
            return pid > 0 && name != null && createdBy != null && createdDateUTC != null;
        }

    }
}
