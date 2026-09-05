package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;
import java.util.Map;

/**
 * Klijent -> Server: zahtev za obradu posla. inputFiles i outputFileNames
 * su ogranicena na najvise 6 elemenata svaka (spec zahtev) - provera je u
 * ClientMain pre slanja, server je ne ponavlja
 */
public class JobSubmission implements Serializable {
    private static final long serialVersionUID = 1L;

    private final byte[] jarBytes;
    private final String mainClassName;
    private final String[] programArgs;
    private final Map<String, byte[]> inputFiles;
    private final String[] outputFileNames;

    public JobSubmission(byte[] jarBytes, String mainClassName, String[] programArgs,
                         Map<String, byte[]> inputFiles, String[] outputFileNames) {
        this.jarBytes = jarBytes;
        this.mainClassName = mainClassName;
        this.programArgs = programArgs;
        this.inputFiles = inputFiles;
        this.outputFileNames = outputFileNames;
    }

    public byte[] getJarBytes() {
        return jarBytes;
    }

    public String getMainClassName() {
        return mainClassName;
    }

    public String[] getProgramArgs() {
        return programArgs;
    }

    public Map<String, byte[]> getInputFiles() {
        return inputFiles;
    }

    public String[] getOutputFileNames() {
        return outputFileNames;
    }
}