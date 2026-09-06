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
 * Prima JobDispatch i EvalRequest poruke. VISE NE PROVERAVA slobodan
 * kapacitet - svaki posao/eval se odmah prihvata (JobDispatchAck/EvalAck
 * uvek uspesan cim stigne, osim ako samo pokretanje procesa baci
 * izuzetak). jobPool je CachedThreadPool - nema gornje granice broja niti,
 * svaki posao dobija sopstvenu nit odmah.
 */
public class WorkerDispatchServer {

    private final MessageServer messageServer;
    private final JobProcessRunner jobRunner;
    private final EvalProcessRunner evalRunner;
    private final SimpleLogger logger;
    private final AtomicInteger activeJobs = new AtomicInteger(0);
    private final ExecutorService jobPool;
    private JobResultReporter resultReporter;

    public interface JobResultReporter {
        void report(rs.ac.bg.etf.kdp.protocol.JobResultReport report);
    }

    public WorkerDispatchServer(int port, int capacity, JobProcessRunner jobRunner,
                                EvalProcessRunner evalRunner, SimpleLogger logger) {
        this.jobRunner = jobRunner;
        this.evalRunner = evalRunner;
        this.logger = logger;
        // NOVO: CachedThreadPool umesto FixedThreadPool(capacity) - nema
        // vise gornje granice broja istovremenih poslova/eval zadataka na
        // ovoj stanici; 'capacity' parametar se i dalje prihvata radi
        // kompatibilnosti poziva i informativnog prikaza u WorkerRegistration,
        // ali se ovde vise ne koristi za ogranicavanje.
        this.jobPool = Executors.newCachedThreadPool();
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

    /** NOVO: broj trenutno aktivnih poslova/eval zadataka - zamenjuje raniji getFreeSlots(). */
    public int getActiveJobs() {
        return activeJobs.get();
    }

    /** I dalje se salje u Heartbeat porukama - server ga vise ne koristi za odluku o izboru stanice (round-robin), ali se cuva radi buduce upotrebe/dijagnostike. */
    public int getFreeSlots() {
        return Math.max(0, Integer.MAX_VALUE - activeJobs.get());
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
        // NOVO: nema vise provere slobodnog kapaciteta - uvek prihvata
        connection.send(new JobDispatchAck(true, null));
        activeJobs.incrementAndGet();
        jobPool.submit(() -> {
            try {
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
        // NOVO: nema vise provere slobodnog kapaciteta - uvek pokusava
        try {
            evalRunner.run(request);
            connection.send(new EvalAck(true, null));
        } catch (Exception e) {
            connection.send(new EvalAck(false, "Pokretanje eval() procesa nije uspelo: " + e));
        }
    }
}