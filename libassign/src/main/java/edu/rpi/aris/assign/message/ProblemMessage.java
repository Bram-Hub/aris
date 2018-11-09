package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.spi.ArisModule;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.sql.Connection;

public abstract class ProblemMessage<T extends ArisModule> extends DataMessage {

    private static final long MAX_FILE_SIZE = ServerCallbacks.getInstance().getMaxSubmissionSize();

    private final String moduleName;
    private final boolean isProblemSolution;
    private transient boolean tooLarge = false;
    private transient Problem<T> problem;

    ProblemMessage(@NotNull String moduleName, Problem<T> problem, boolean isProblemSolution, @NotNull Perm perm, boolean customPermCheck) {
        super(perm, customPermCheck);
        this.moduleName = moduleName;
        this.problem = problem;
        this.isProblemSolution = isProblemSolution;
    }

    ProblemMessage(@NotNull String moduleName, Problem<T> problem, boolean isProblemSolution, @NotNull Perm perm) {
        super(perm);
        this.moduleName = moduleName;
        this.problem = problem;
        this.isProblemSolution = isProblemSolution;
    }

    @Override
    public void sendData(DataOutputStream out) throws Exception {
        try {
            if (problem == null) {
                out.writeInt(-1);
                return;
            }
            ArisModule<T> module = ModuleService.getService().getModule(moduleName);
            if (module == null)
                throw new ArisException("No module for name: " + moduleName);
            ProblemConverter<T> converter = module.getProblemConverter();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            converter.convertProblem(problem, baos, isProblemSolution);
            baos.close();
            int size = baos.size();
            out.writeInt(size);
            out.write(baos.toByteArray());
        } finally {
            out.flush();
        }
    }

    @Override
    public void receiveData(DataInputStream in) throws Exception {
        int size = in.readInt();
        if (size == -1)
            return;
        if (MAX_FILE_SIZE > 0 && size > MAX_FILE_SIZE) {
            tooLarge = true;
            return;
        }
        ArisModule<T> module = ModuleService.getService().getModule(moduleName);
        if (module == null)
            throw new ArisException("No module for name: " + moduleName);
        ProblemConverter<T> converter = module.getProblemConverter();
        BoundedInputStream bis = new BoundedInputStream(new CloseShieldInputStream(in), size);
        problem = converter.loadProblem(bis, isProblemSolution);
    }

    public String getModuleName() {
        return moduleName;
    }

    public Problem<T> getProblem() {
        return problem;
    }

    public void setProblem(Problem<T> problem) {
        this.problem = problem;
    }

    @Override
    public boolean checkValid() {
        return problem == null || moduleName != null;
    }

    @Override
    public final @Nullable ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception {
        if (tooLarge)
            return ErrorType.FILE_TOO_LARGE;
        return processProblemMessage(connection, user, permissions);
    }

    abstract @Nullable ErrorType processProblemMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception;

}
