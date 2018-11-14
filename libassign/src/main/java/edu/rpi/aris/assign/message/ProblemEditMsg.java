package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.spi.ArisModule;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class ProblemEditMsg<T extends ArisModule> extends ProblemMessage<T> {

    private final int pid;
    private final String name;

    public ProblemEditMsg(int pid, String name) {
        this(pid, name, null, null);
    }

    public ProblemEditMsg(int pid, String moduleName, Problem<T> problem) {
        this(pid, null, moduleName, problem);
    }

    public ProblemEditMsg(int pid, String name, String moduleName, Problem<T> problem) {
        super(moduleName, problem, false, Perm.PROBLEM_EDIT);
        this.pid = pid;
        this.name = name;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private ProblemEditMsg() {
        this(0, null);
    }

    @Nullable
    @Override
    public ErrorType processProblemMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception {
        if (name != null) {
            try (PreparedStatement updateName = connection.prepareStatement("UPDATE problem SET name = ? WHERE id = ?;")) {
                updateName.setString(1, name);
                updateName.setInt(2, pid);
                updateName.executeUpdate();
            }
        }
        if (getProblem() != null) {
            ArisModule<T> module = ModuleService.getService().getModule(getModuleName());
            ProblemConverter<T> converter = module.getProblemConverter();
            try (PreparedStatement updateData = connection.prepareStatement("UPDATE problem SET data = ?, problem_hash = ? WHERE id = ?;");
                 PipedInputStream pis = new PipedInputStream();
                 PipedOutputStream pos = new PipedOutputStream(pis);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                LibAssign.convertProblem(pos, getProblem(), converter, false);

                IOUtils.copy(pis, baos);
                MessageDigest digest = MessageDigest.getInstance("MD5");
                String hash = DatatypeConverter.printHexBinary(digest.digest(baos.toByteArray())).toLowerCase();

                updateData.setBytes(1, baos.toByteArray());
                updateData.setString(2, hash);
                updateData.setInt(3, pid);
                updateData.executeUpdate();
            }
        }
        return null;
    }

    @NotNull
    @Override
    public MessageType getMessageType() {
        return MessageType.EDIT_PROBLEM;
    }

    @Override
    public boolean checkValid() {
        return pid > 0 && super.checkValid();
    }

    public int getPid() {
        return pid;
    }

}
