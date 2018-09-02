package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.ArisModuleException;
import edu.rpi.aris.assign.ModuleService;
import edu.rpi.aris.assign.Problem;
import edu.rpi.aris.assign.ProblemConverter;
import edu.rpi.aris.assign.spi.ArisModule;

import java.io.*;

public abstract class ProblemMessage<T extends ArisModule> extends DataMessage {

    private final String moduleName;
    private transient Problem<T> problem;

    ProblemMessage(String moduleName, Problem<T> problem) {
        this.moduleName = moduleName;
        this.problem = problem;
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
                throw new ArisModuleException("No module for name: " + moduleName);
            ProblemConverter<T> converter = module.getProblemConverter();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            converter.convertProblem(problem, baos, false);
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
        ArisModule<T> module = ModuleService.getService().getModule(moduleName);
        if (module == null)
            throw new ArisModuleException("No module for name: " + moduleName);
        ProblemConverter<T> converter = module.getProblemConverter();
        byte[] data = new byte[size];
        if (size != in.read(data))
            throw new IOException("Failed to read all data from stream");
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        problem = converter.loadProblem(bais, false);
        bais.close();
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
}
