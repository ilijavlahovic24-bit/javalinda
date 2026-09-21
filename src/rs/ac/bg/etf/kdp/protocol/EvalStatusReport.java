package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;

/** Radna stanica -> Server: eval() pod-proces je zavrsio (uspesno, neuspesno, ili prekinut zbog timeout-a). */
public class EvalStatusReport implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long jobSetId;
    private final String taskName;
    private final boolean success;
    private final String errorMessage;
    private final boolean timedOut;

    public EvalStatusReport(long jobSetId, String taskName, boolean success,
                            String errorMessage, boolean timedOut) {
        this.jobSetId = jobSetId;
        this.taskName = taskName;
        this.success = success;
        this.errorMessage = errorMessage;
        this.timedOut = timedOut;
    }

    public long getJobSetId() {
        return jobSetId;
    }

    public String getTaskName() {
        return taskName;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isTimedOut() {
        return timedOut;
    }
}