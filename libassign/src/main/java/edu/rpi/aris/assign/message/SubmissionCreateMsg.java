package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.spi.ArisModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.ZonedDateTime;

public class SubmissionCreateMsg<T extends ArisModule> extends ProblemMessage<T> {

    private final int cid, aid, pid;
    private int sid;
    private ZonedDateTime submittedOn;
    private GradingStatus status;
    private String statusStr;

    public SubmissionCreateMsg(int cid, int aid, int pid, String moduleName, Problem<T> problem) {
        super(moduleName, problem, true, Perm.SUBMISSION_CREATE);
        this.cid = cid;
        this.aid = aid;
        this.pid = pid;
    }

    private SubmissionCreateMsg() {
        this(0, 0, 0, null, null);
    }

    @Nullable
    @Override
    public ErrorType processProblemMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception {
        if (getProblem() != null) {
            ArisModule<T> module = ModuleService.getService().getModule(getModuleName());
            ProblemConverter<T> converter = module.getProblemConverter();
            try (PreparedStatement insertSubmission = connection.prepareStatement("INSERT INTO submission (class_id, assignment_id, user_id, problem_id, data, time, short_status, status, grade) VALUES (?, ?, ?, ?, ?, now(), ?, ?, 0) RETURNING id, time;");
                 PipedInputStream pis = new PipedInputStream();
                 PipedOutputStream pos = new PipedOutputStream(pis)) {
                insertSubmission.setInt(1, cid);
                insertSubmission.setInt(2, aid);
                insertSubmission.setInt(3, user.uid);
                insertSubmission.setInt(4, pid);
                converter.convertProblem(getProblem(), pos, true);
                pos.close();
                insertSubmission.setBinaryStream(5, pis);
                status = GradingStatus.GRADING;
                statusStr = "Grading";
                insertSubmission.setString(6, status.name());
                insertSubmission.setString(7, statusStr);
                try (ResultSet rs = insertSubmission.executeQuery()) {
                    if (rs.next()) {
                        sid = rs.getInt(1);
                        submittedOn = NetUtil.localToUTC(rs.getTimestamp(2).toLocalDateTime());
                        connection.commit();
                        ServerCallbacks.getInstance().scheduleForGrading(sid);
                    }
                }
            }
        }
        return null;
    }

    @NotNull
    @Override
    public MessageType getMessageType() {
        return MessageType.CREATE_SUBMISSION;
    }

    public int getSid() {
        return sid;
    }

    public ZonedDateTime getSubmittedOn() {
        return submittedOn;
    }

    public GradingStatus getStatus() {
        return status;
    }

    public String getStatusStr() {
        return statusStr;
    }
}
