package rs.ac.bg.etf.kdp.worker;

import rs.ac.bg.etf.kdp.net.MessageConnection;
import rs.ac.bg.etf.kdp.net.MessageServer;
import rs.ac.bg.etf.kdp.protocol.EvalAck;
import rs.ac.bg.etf.kdp.protocol.EvalRequest;
import rs.ac.bg.etf.kdp.protocol.JobDispatch;
import rs.ac.bg.etf.kdp.protocol.JobDispatchAck;
import rs.ac.bg.etf.kdp.util.SimpleLogger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Socket servis koji radna stanica izlaze za dolazne pozive od servera -
 * ovo je "dispatchHost/dispatchPort" iz WorkerRegistration (Faza 5).
 * Prima JobDispatch (glavni posao) i EvalRequest (eval() zadatak) na
 * istoj listenici, razlikuje po tipu poruke.
 */



public class WorkerDispatchServer {

    private final MessageServer messageServer;
    private final JobProcessRunner jobRunner;
    private final EvalProcessRunner evalRunner;
    private final SimpleLogger logger;
    private final int capacity;
    private final AtomicInteger activeJobs = new AtomicInteger(0);
    private final ExecutorService jobPool;
    private JobResultReporter resultReporter;

    public interface JobResultReporter {
        void report(rs.ac.bg.etf.kdp.protocol.JobResultReport report);
    }

    public WorkerDispatchServer(int port, int capacity, JobProcessRunner jobRunner,
                                EvalProcessRunner evalRunner, SimpleLogger logger) {
        this.capacity = capacity;
        this.jobRunner = jobRunner;
        this.evalRunner = evalRunner;
        this.logger = logger;
        this.jobPool = Executors.newFixedThreadPool(Math.max(1, capacity));
        this.messageServer = new MessageServer(port, this::handleConnection);
    }

    public void setResultReporter(JobResultReporter resultReporter) {
        this.resultReporter = resultReporter;
    }

    public void start() {
        new Thread(messageServer, "worker-dispatch-server").start();
    }

    public void stop() {
        messageServer.stop();
        jobPool.shutdownNow();
    }

    public int getPort() {
        return messageServer.getPort();
    }

    public int getFreeSlots() {
        return Math.max(0, capacity - activeJobs.get());
    }

    private void handleConnection(MessageConnection connection) throws Exception {
        Object message = connection.receive();
        if (message instanceof JobDispatch) {
            handleJobDispatch(connection, (JobDispatch) message);
        } else if (message instanceof EvalRequest) {
            handleEvalRequest(connection, (EvalRequest) message);
        } else {
            logger.log("WorkerDispatchServer", "Nepoznata poruka: " + message);
        }
    }

    private void handleJobDispatch(MessageConnection connection, JobDispatch dispatch)
            throws Exception {
        if (getFreeSlots() <= 0) {
            connection.send(new JobDispatchAck(false, "Nema slobodnog kapaciteta"));
            return;
        }
        connection.send(new JobDispatchAck(true, null));
        activeJobs.incrementAndGet();
        jobPool.submit(() -> {
            try {
                // NOVO: dispatch.getOutputFileNames() umesto fiksnog spiska
                rs.ac.bg.etf.kdp.protocol.JobResultReport report =
                        jobRunner.run(dispatch, dispatch.getOutputFileNames());
                if (resultReporter != null) {
                    resultReporter.report(report);
                }
            } finally {
                activeJobs.decrementAndGet();
            }
        });
    }

    private void handleEvalRequest(MessageConnection connection, EvalRequest request)
            throws Exception {
        if (getFreeSlots() <= 0) {
            connection.send(new EvalAck(false, "Nema slobodnog kapaciteta za eval()"));
            return;
        }
        try {
            evalRunner.run(request);
            connection.send(new EvalAck(true, null));
        } catch (Exception e) {
            connection.send(new EvalAck(false, "Pokretanje eval() procesa nije uspelo: " + e));
        }
    }
}