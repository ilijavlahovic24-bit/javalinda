package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;
import java.util.Map;

/** Radna stanica -> Server: rezultat zavrsenog posla, saljе se na posebnoj konekciji ka serveru. */
public class JobResultReport implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long jobId;
    private final boolean success;
    private final String errorMessage;
    private final Map<String, byte[]> outputFiles; // ime -> sadrzaj, najvise 6

    public JobResultReport(long jobId, boolean success, String errorMessage,
                           Map<String, byte[]> outputFiles) {
        this.jobId = jobId;
        this.success = success;
        this.errorMessage = errorMessage;
        this.outputFiles = outputFiles;
    }

    public long getJobId() {
        return jobId;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Map<String, byte[]> getOutputFiles() {
        return outputFiles;
    }
}