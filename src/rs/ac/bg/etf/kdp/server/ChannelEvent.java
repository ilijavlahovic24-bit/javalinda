package rs.ac.bg.etf.kdp.server;

import java.util.concurrent.CompletableFuture;

import rs.ac.bg.etf.kdp.protocol.FailoverAction;
import rs.ac.bg.etf.kdp.protocol.JobFinished;

/**
 * Dogadjaj koji neka pozadinska komponenta (HeartbeatMonitor, worker
 * result handler) ubacuje u red konekcije koja pripada datom poslu - nit
 * koja "poseduje" tu konekciju (handleJobSubmission petlja) ih uzima
 * jedan po jedan i salje na socket ka klijentu.
 */
public class ChannelEvent {

    public enum Type { WORKER_FAILURE, JOB_FINISHED }

    private final Type type;
    private final int failedWorkerId;
    private final CompletableFuture<FailoverAction> decisionFuture;
    private final JobFinished jobFinished;

    private ChannelEvent(Type type, int failedWorkerId,
                         CompletableFuture<FailoverAction> decisionFuture, JobFinished jobFinished) {
        this.type = type;
        this.failedWorkerId = failedWorkerId;
        this.decisionFuture = decisionFuture;
        this.jobFinished = jobFinished;
    }

    public static ChannelEvent workerFailure(int failedWorkerId,
                                             CompletableFuture<FailoverAction> decisionFuture) {
        return new ChannelEvent(Type.WORKER_FAILURE, failedWorkerId, decisionFuture, null);
    }

    public static ChannelEvent jobFinished(JobFinished jobFinished) {
        return new ChannelEvent(Type.JOB_FINISHED, -1, null, jobFinished);
    }

    public Type getType() {
        return type;
    }

    public int getFailedWorkerId() {
        return failedWorkerId;
    }

    public CompletableFuture<FailoverAction> getDecisionFuture() {
        return decisionFuture;
    }

    public JobFinished getJobFinished() {
        return jobFinished;
    }
}