package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;
import java.util.Map;

public class JobDispatch implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long jobId;
    private final byte[] jarBytes;
    private final String mainClassName;
    private final String[] programArgs;
    private final Map<String, byte[]> inputFiles;
    private final String[] outputFileNames; // dolazi od klijenta, ne fiksno na workeru
    private final String tupleSpaceHost;
    private final int tupleSpacePort;

    public JobDispatch(long jobId, byte[] jarBytes, String mainClassName, String[] programArgs,
                       Map<String, byte[]> inputFiles, String[] outputFileNames,
                       String tupleSpaceHost, int tupleSpacePort) {
        this.jobId = jobId;
        this.jarBytes = jarBytes;
        this.mainClassName = mainClassName;
        this.programArgs = programArgs;
        this.inputFiles = inputFiles;
        this.outputFileNames = outputFileNames;
        this.tupleSpaceHost = tupleSpaceHost;
        this.tupleSpacePort = tupleSpacePort;
    }

    public long getJobId() {
        return jobId;
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

    public String getTupleSpaceHost() {
        return tupleSpaceHost;
    }

    public int getTupleSpacePort() {
        return tupleSpacePort;
    }
}