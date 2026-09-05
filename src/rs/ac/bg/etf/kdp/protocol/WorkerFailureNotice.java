package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;

/** Server -> Klijent (na OTVORENOJ konekciji posla): radna stanica koja je izvrsavala posao je prestala da odgovara. */
public class WorkerFailureNotice implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long jobId;
    private final int failedWorkerId;

    public WorkerFailureNotice(long jobId, int failedWorkerId) {
        this.jobId = jobId;
        this.failedWorkerId = failedWorkerId;
    }

    public long getJobId() {
        return jobId;
    }

    public int getFailedWorkerId() {
        return failedWorkerId;
    }
}