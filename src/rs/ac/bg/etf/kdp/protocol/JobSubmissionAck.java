package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;

public class JobSubmissionAck implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long jobId;
    private final String errorMessage; // popunjeno ako slanje nije prihvaceno

    public JobSubmissionAck(long jobId, String errorMessage) {
        this.jobId = jobId;
        this.errorMessage = errorMessage;
    }

    public long getJobId() {
        return jobId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}