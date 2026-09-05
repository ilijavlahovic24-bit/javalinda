package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;
import java.util.Map;

public class JobStatusResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final JobStatus status;
    private final String errorMessage;
    private final Map<String, byte[]> outputFiles; // popunjeno samo kad je status DONE

    public JobStatusResponse(JobStatus status, String errorMessage,
                             Map<String, byte[]> outputFiles) {
        this.status = status;
        this.errorMessage = errorMessage;
        this.outputFiles = outputFiles;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Map<String, byte[]> getOutputFiles() {
        return outputFiles;
    }
}