package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;

/** Klijent -> Server: odgovor na WorkerFailureNotice. */
public class WorkerFailureDecision implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long jobId;
    private final FailoverAction action;

    public WorkerFailureDecision(long jobId, FailoverAction action) {
        this.jobId = jobId;
        this.action = action;
    }

    public long getJobId() {
        return jobId;
    }

    public FailoverAction getAction() {
        return action;
    }
}