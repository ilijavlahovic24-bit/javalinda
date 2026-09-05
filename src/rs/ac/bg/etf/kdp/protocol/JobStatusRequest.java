package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;

public class JobStatusRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long jobId;

    public JobStatusRequest(long jobId) {
        this.jobId = jobId;
    }

    public long getJobId() {
        return jobId;
    }
}