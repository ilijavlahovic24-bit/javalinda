package rs.ac.bg.etf.kdp.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import rs.ac.bg.etf.kdp.net.MessageConnection;
import rs.ac.bg.etf.kdp.protocol.FailoverAction;
import rs.ac.bg.etf.kdp.protocol.JobDispatch;
import rs.ac.bg.etf.kdp.protocol.JobDispatchAck;
import rs.ac.bg.etf.kdp.util.SimpleLogger;

/**
 * Pozadinska nit: na svakih intervalMs proverava sve prijavljene stanice;
 * ako neka nije javila heartbeat u poslednja dva intervala, proglasava je
 * mrtvom i uklanja iz WorkerRegistry-ja.
 */
public class HeartbeatMonitor implements Runnable {

    private static final long DECISION_TIMEOUT_MS = 15000; // koliko cekamo klijentovu odluku

    private final WorkerRegistry workerRegistry;
    private final JobRegistry jobRegistry;
    private final ClientChannelRegistry channelRegistry;
    private final String tupleSpaceHost;
    private final int tupleSpacePort;
    private final SimpleLogger logger;
    private final long intervalMs;
    private final ExecutorService failureHandlers = Executors.newCachedThreadPool();
    private volatile boolean running = true;

    public HeartbeatMonitor(WorkerRegistry workerRegistry, JobRegistry jobRegistry,
                            ClientChannelRegistry channelRegistry, String tupleSpaceHost, int tupleSpacePort,
                            SimpleLogger logger, long intervalMs) {
        this.workerRegistry = workerRegistry;
        this.jobRegistry = jobRegistry;
        this.channelRegistry = channelRegistry;
        this.tupleSpaceHost = tupleSpaceHost;
        this.tupleSpacePort = tupleSpacePort;
        this.logger = logger;
        this.intervalMs = intervalMs;
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            checkWorkers();
        }
    }

    private void checkWorkers() {
        long deadline = System.currentTimeMillis() - (2 * intervalMs);
        List<Integer> dead = new ArrayList<>();
        for (WorkerHandle handle : workerRegistry.all()) {
            if (handle.getLastHeartbeatAt() < deadline) {
                dead.add(handle.getWorkerId());
            }
        }
        for (Integer workerId : dead) {
            logger.log("HeartbeatMonitor", "Stanica " + workerId
                    + " nije javila heartbeat - proglasavam je mrtvom");
            workerRegistry.unregister(workerId);

            for (JobRecord job : jobRegistry.byAssignedWorker(workerId)) {
                failureHandlers.submit(() -> handleJobAffectedByFailure(job, workerId));
            }
        }
    }

    private void handleJobAffectedByFailure(JobRecord job, int failedWorkerId) {
        CompletableFuture<FailoverAction> decisionFuture = new CompletableFuture<>();
        boolean delivered = channelRegistry.publish(job.getJobId(),
                ChannelEvent.workerFailure(failedWorkerId, decisionFuture));

        if (!delivered) {
            // klijent vec diskonektovan / kanal ne postoji vise - spec:
            // "Ukoliko korisnik nije dostupan, prekida se izvrsavanje celog posla"
            jobRegistry.updateStatus(job.getJobId(), rs.ac.bg.etf.kdp.protocol.JobStatus.ABORTED);
            logger.log("HeartbeatMonitor", "Posao " + job.getJobId()
                    + " ABORTED - klijent nije dostupan za odluku o padu stanice " + failedWorkerId);
            return;
        }

        FailoverAction action;
        try {
            action = decisionFuture.get(DECISION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            action = FailoverAction.ABORT;
            logger.log("HeartbeatMonitor", "Posao " + job.getJobId()
                    + " - klijent nije odgovorio na vreme, tretiram kao ABORT");
        } catch (Exception e) {
            action = FailoverAction.ABORT;
            logger.log("HeartbeatMonitor", "Posao " + job.getJobId()
                    + " - greska pri cekanju odluke, tretiram kao ABORT: " + e);
        }

        if (action == FailoverAction.ABORT) {
            jobRegistry.updateStatus(job.getJobId(), rs.ac.bg.etf.kdp.protocol.JobStatus.ABORTED);
            channelRegistry.publish(job.getJobId(), ChannelEvent.jobFinished(
                    new rs.ac.bg.etf.kdp.protocol.JobFinished(job.getJobId(),
                            rs.ac.bg.etf.kdp.protocol.JobStatus.ABORTED, null, null)));
            return;
        }

        redispatch(job, failedWorkerId);
    }

    private void redispatch(JobRecord job, int failedWorkerId) {
        WorkerHandle newHandle = workerRegistry.findFreeWorkerExcept(failedWorkerId);
        if (newHandle == null) {
            jobRegistry.fail(job.getJobId(), "Nijedna druga radna stanica trenutno nema slobodan kapacitet");
            channelRegistry.publish(job.getJobId(), ChannelEvent.jobFinished(
                    new rs.ac.bg.etf.kdp.protocol.JobFinished(job.getJobId(),
                            rs.ac.bg.etf.kdp.protocol.JobStatus.FAILED,
                            job.getFailureMessage(), null)));
            return;
        }

        JobDispatch dispatch = new JobDispatch(job.getJobId(), job.getJarBytes(),
                job.getMainClassName(), job.getProgramArgs(), job.getInputFiles(),
                job.getOutputFileNames(), tupleSpaceHost, tupleSpacePort);

        try (MessageConnection connection = MessageConnection.connectTo(
                newHandle.getDispatchHost(), newHandle.getDispatchPort())) {
            connection.send(dispatch);
            Object ack = connection.receive();
            if (ack instanceof JobDispatchAck && ((JobDispatchAck) ack).isAccepted()) {
                jobRegistry.assignWorker(job.getJobId(), newHandle.getWorkerId());
                jobRegistry.updateStatus(job.getJobId(), rs.ac.bg.etf.kdp.protocol.JobStatus.RUNNING);
                logger.log("HeartbeatMonitor", "Posao " + job.getJobId()
                        + " prosledjen stanici " + newHandle.getWorkerId()
                        + " posle pada stanice " + failedWorkerId);
            } else {
                failRedispatch(job, newHandle, ack);
            }
        } catch (Exception e) {
            failRedispatch(job, newHandle, e);
        }
    }

    private void failRedispatch(JobRecord job, WorkerHandle newHandle, Object reason) {
        jobRegistry.fail(job.getJobId(), "Prosledjivanje stanici " + newHandle.getWorkerId()
                + " nije uspelo: " + reason);
        channelRegistry.publish(job.getJobId(), ChannelEvent.jobFinished(
                new rs.ac.bg.etf.kdp.protocol.JobFinished(job.getJobId(),
                        rs.ac.bg.etf.kdp.protocol.JobStatus.FAILED, job.getFailureMessage(), null)));
    }

    public void stop() {
        running = false;
        failureHandlers.shutdownNow();
    }
}