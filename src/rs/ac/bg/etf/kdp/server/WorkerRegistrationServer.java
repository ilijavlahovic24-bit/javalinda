package rs.ac.bg.etf.kdp.server;

import java.util.Map;

import rs.ac.bg.etf.kdp.net.MessageConnection;
import rs.ac.bg.etf.kdp.net.MessageServer;
import rs.ac.bg.etf.kdp.protocol.FailoverAction;
import rs.ac.bg.etf.kdp.protocol.Heartbeat;
import rs.ac.bg.etf.kdp.protocol.HeartbeatAck;
import rs.ac.bg.etf.kdp.protocol.JobDispatch;
import rs.ac.bg.etf.kdp.protocol.JobDispatchAck;
import rs.ac.bg.etf.kdp.protocol.JobFinished;
import rs.ac.bg.etf.kdp.protocol.JobResultAck;
import rs.ac.bg.etf.kdp.protocol.JobResultReport;
import rs.ac.bg.etf.kdp.protocol.JobStatus;
import rs.ac.bg.etf.kdp.protocol.JobStatusRequest;
import rs.ac.bg.etf.kdp.protocol.JobStatusResponse;
import rs.ac.bg.etf.kdp.protocol.JobSubmission;
import rs.ac.bg.etf.kdp.protocol.JobSubmissionAck;
import rs.ac.bg.etf.kdp.protocol.WorkerFailureDecision;
import rs.ac.bg.etf.kdp.protocol.WorkerFailureNotice;
import rs.ac.bg.etf.kdp.protocol.WorkerRegisterAck;
import rs.ac.bg.etf.kdp.protocol.WorkerRegistration;
import rs.ac.bg.etf.kdp.util.SimpleLogger;

public class WorkerRegistrationServer {

    private final WorkerRegistry workerRegistry;
    private final JobRegistry jobRegistry;
    private final ClientChannelRegistry channelRegistry;
    private final String tupleSpaceHost;
    private final int tupleSpacePort;
    private final SimpleLogger logger;
    private final MessageServer messageServer;

    public WorkerRegistrationServer(int port, WorkerRegistry workerRegistry,
                                    JobRegistry jobRegistry, ClientChannelRegistry channelRegistry,
                                    String tupleSpaceHost, int tupleSpacePort, SimpleLogger logger) {
        this.workerRegistry = workerRegistry;
        this.jobRegistry = jobRegistry;
        this.channelRegistry = channelRegistry;
        this.tupleSpaceHost = tupleSpaceHost;
        this.tupleSpacePort = tupleSpacePort;
        this.logger = logger;
        this.messageServer = new MessageServer(port, this::handleConnection);
    }

    public void start() {
        new Thread(messageServer, "worker-registration-server").start();
    }

    public void stop() {
        messageServer.stop();
    }

    public int getPort() {
        return messageServer.getPort();
    }

    private void handleConnection(MessageConnection connection) throws Exception {
        Object first = connection.receive();

        if (first instanceof JobResultReport) {
            handleJobResult((JobResultReport) first);
            connection.send(new JobResultAck()); // NOVO - worker ceka ovo na svojoj konekciji
        } else if (first instanceof JobSubmission) {
            handleJobSubmission(connection, (JobSubmission) first);
        } else if (first instanceof JobStatusRequest) {
            handleJobStatusRequest(connection, (JobStatusRequest) first);
        } else if (first instanceof WorkerRegistration) {
            handleWorkerRegistration(connection, (WorkerRegistration) first);
        } else {
            logger.log("WorkerRegistrationServer", "Nepoznata poruka, zatvaram: " + first);
        }
    }

    private void handleWorkerRegistration(MessageConnection connection,
                                          WorkerRegistration registration) throws Exception {
        WorkerHandle handle = workerRegistry.register(registration);
        connection.send(new WorkerRegisterAck(handle.getWorkerId()));

        try {
            while (!connection.isClosed()) {
                Object message = connection.receive();
                if (message instanceof Heartbeat) {
                    Heartbeat hb = (Heartbeat) message;
                    workerRegistry.heartbeat(hb.getWorkerId(), hb.getFreeSlots());
                    connection.send(new HeartbeatAck());
                }
            }
        } finally {
            workerRegistry.unregister(handle.getWorkerId());
        }
    }

    /** worker javlja rezultat - NE saljemo nista direktno njemu; guramo JOB_FINISHED dogadjaj ka klijentskoj konekciji tog posla. */
    private void handleJobResult(JobResultReport report) {
        JobStatus finalStatus;
        if (report.isSuccess()) {
            jobRegistry.completeWithResult(report.getJobId(), report.getOutputFiles());
            finalStatus = JobStatus.DONE;
        } else {
            jobRegistry.fail(report.getJobId(), report.getErrorMessage());
            finalStatus = JobStatus.FAILED;
        }
        channelRegistry.publish(report.getJobId(), ChannelEvent.jobFinished(
                new JobFinished(report.getJobId(), finalStatus, report.getErrorMessage(),
                        report.getOutputFiles())));
    }

    /**
     *  worker i dalje ocekuje JobResultAck kao odgovor na SVOJU
     * konekciju (WorkerMain.resultReporter salje pa ceka receive()) - ali
     * ta konekcija je ODVOJENA od klijentske. Server ovde treba da odgovori
     * workeru na NJEGOVOJ konekciji, ne klijentovoj - to se desava u
     * handleConnection() koji je pozvao ovu metodu, pa dodajemo send() tamo
     * gde imamo pristup workerovoj connection referenci.
     */

    private void handleJobSubmission(MessageConnection connection, JobSubmission submission)
            throws Exception {
        JobRecord record = jobRegistry.submit(submission.getJarBytes(), submission.getMainClassName(),
                submission.getProgramArgs(), submission.getInputFiles(),
                submission.getOutputFileNames());
        long jobId = record.getJobId();

        java.util.concurrent.BlockingQueue<ChannelEvent> events = channelRegistry.register(jobId);

        WorkerHandle handle = workerRegistry.findFreeWorker();
        if (handle == null) {
            jobRegistry.fail(jobId, "Nijedna radna stanica trenutno nema slobodan kapacitet");
            connection.send(new JobSubmissionAck(jobId, null));
            connection.send(new JobFinished(jobId, JobStatus.FAILED, record.getFailureMessage(), null));
            channelRegistry.unregister(jobId);
            return;
        }

        connection.send(new JobSubmissionAck(jobId, null));

        JobDispatch dispatch = new JobDispatch(jobId, submission.getJarBytes(),
                submission.getMainClassName(), submission.getProgramArgs(),
                submission.getInputFiles(), submission.getOutputFileNames(),
                tupleSpaceHost, tupleSpacePort);

        boolean dispatched = dispatchToWorker(handle, dispatch, jobId);
        if (!dispatched) {
            connection.send(new JobFinished(jobId, JobStatus.FAILED, record.getFailureMessage(), null));
            channelRegistry.unregister(jobId);
            return;
        }

        // Glavna petlja konekcije: cekaj dogadjaje (WORKER_FAILURE, JOB_FINISHED),
        // salji ih klijentu, za WORKER_FAILURE ocekuj i procitaj odluku.
        try {
            while (true) {
                ChannelEvent event = events.take();
                if (event.getType() == ChannelEvent.Type.JOB_FINISHED) {
                    connection.send(event.getJobFinished());
                    break;
                }
                // WORKER_FAILURE
                connection.send(new WorkerFailureNotice(jobId, event.getFailedWorkerId()));
                Object response;
                try {
                    response = connection.receive();
                } catch (Exception e) {
                    // klijent diskonektovan dok smo cekali odluku
                    event.getDecisionFuture().completeExceptionally(e);
                    break;
                }
                if (response instanceof WorkerFailureDecision) {
                    event.getDecisionFuture().complete(((WorkerFailureDecision) response).getAction());
                } else {
                    event.getDecisionFuture().complete(FailoverAction.ABORT);
                }
            }
        } finally {
            channelRegistry.unregister(jobId);
        }
    }

    private boolean dispatchToWorker(WorkerHandle handle, JobDispatch dispatch, long jobId) {
        try (MessageConnection workerConnection = MessageConnection.connectTo(
                handle.getDispatchHost(), handle.getDispatchPort())) {
            workerConnection.send(dispatch);
            Object ack = workerConnection.receive();
            if (ack instanceof JobDispatchAck && ((JobDispatchAck) ack).isAccepted()) {
                jobRegistry.assignWorker(jobId, handle.getWorkerId());
                jobRegistry.updateStatus(jobId, JobStatus.RUNNING);
                return true;
            }
            String reason = ack instanceof JobDispatchAck
                    ? ((JobDispatchAck) ack).getErrorMessage() : String.valueOf(ack);
            jobRegistry.fail(jobId, "Stanica " + handle.getWorkerId() + " odbila posao: " + reason);
            return false;
        } catch (Exception e) {
            jobRegistry.fail(jobId, "Slanje posla stanici " + handle.getWorkerId()
                    + " nije uspelo: " + e.getMessage());
            return false;
        }
    }

    private void handleJobStatusRequest(MessageConnection connection, JobStatusRequest request)
            throws Exception {
        JobRecord record = jobRegistry.get(request.getJobId());
        if (record == null) {
            connection.send(new JobStatusResponse(null, "Nepoznat jobId " + request.getJobId(), null));
            return;
        }
        Map<String, byte[]> outputFiles = record.getStatus() == JobStatus.DONE
                ? record.getOutputFiles() : null;
        connection.send(new JobStatusResponse(record.getStatus(), record.getFailureMessage(),
                outputFiles));
    }
}