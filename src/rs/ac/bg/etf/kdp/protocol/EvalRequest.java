package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;

public class EvalRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long jobSetId;
    private final String name;
    private final byte[] runnableBytes;
    private final byte[] jarBytes; // NOVO - jar posla, da ciljna stanica moze da ucita klasu Runnable-a

    public EvalRequest(long jobSetId, String name, byte[] runnableBytes, byte[] jarBytes) {
        this.jobSetId = jobSetId;
        this.name = name;
        this.runnableBytes = runnableBytes;
        this.jarBytes = jarBytes;
    }

    public long getJobSetId() {
        return jobSetId;
    }

    public String getName() {
        return name;
    }

    public byte[] getRunnableBytes() {
        return runnableBytes;
    }

    public byte[] getJarBytes() {
        return jarBytes;
    }
}