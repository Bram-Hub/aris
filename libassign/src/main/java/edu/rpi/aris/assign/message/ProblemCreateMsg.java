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
import java.sql.ResultSet;

public class ProblemCreateMsg<T extends ArisModule> extends ProblemMessage<T> {

    private final String name;
    private int pid;

    public ProblemCreateMsg(String name, @NotNull String moduleName, @NotNull Problem<T> problem) {
        super(moduleName, problem, false, Perm.PROBLEM_CREATE);
        this.name = name;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    @SuppressWarnings("ConstantConditions")
    private ProblemCreateMsg() {
        this(null, null, null);
    }

    @Nullable
    @Override
    public ErrorType processProblemMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception {
        ArisModule<T> module = ModuleService.getService().getModule(getModuleName());
        if (module == null)
            return ErrorType.MISSING_MODULE;
        ProblemConverter<T> converter = module.getProblemConverter();
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO problem (name, data, created_by, created_on, module_name, problem_hash) VALUES (?, ?, (SELECT username FROM users WHERE id = ? LIMIT 1), now(), ?, ?) RETURNING id");
             PipedInputStream pis = new PipedInputStream();
             PipedOutputStream pos = new PipedOutputStream(pis);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            converter.convertProblem(getProblem(), pos, false);
            pos.close();
            IOUtils.copy(pis, baos);
            MessageDigest digest = MessageDigest.getInstance("MD5");
            String hash = DatatypeConverter.printHexBinary(digest.digest(baos.toByteArray())).toLowerCase();

            statement.setString(1, name);
            statement.setBytes(2, baos.toByteArray());

            statement.setInt(3, user.uid);
            statement.setString(4, getModuleName());
            statement.setString(5, hash);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next())
                    pid = rs.getInt(1);
            }
        }
        setProblem(null);
        return null;
    }

    @NotNull
    @Override
    public MessageType getMessageType() {
        return MessageType.CREATE_PROBLEM;
    }

    @Override
    public boolean checkValid() {
        return name != null && super.checkValid();
    }

    public int getPid() {
        return pid;
    }

    public String getName() {
        return name;
    }

}
