package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.net.MessageConnection;
import rs.ac.bg.etf.kdp.protocol.EvalAck;
import rs.ac.bg.etf.kdp.protocol.EvalRequest;
import rs.ac.bg.etf.kdp.util.SimpleLogger;

public class EvalDispatcherImpl implements EvalDispatcher {

    private final WorkerRegistry workerRegistry;
    private final JobRegistry jobRegistry; // NOVO
    private final SimpleLogger logger;

    public EvalDispatcherImpl(WorkerRegistry workerRegistry, JobRegistry jobRegistry,
                              SimpleLogger logger) {
        this.workerRegistry = workerRegistry;
        this.jobRegistry = jobRegistry;
        this.logger = logger;
    }

    @Override
    public EvalAck dispatch(EvalRequest request) {
        WorkerHandle handle = workerRegistry.nextWorker();
        if (handle == null) {
            return new EvalAck(false, "Nijedna radna stanica trenutno nije prijavljena");
        }

        try (MessageConnection connection = MessageConnection.connectTo(
                handle.getDispatchHost(), handle.getDispatchPort())) {
            connection.send(request);
            Object response = connection.receive();
            if (!(response instanceof EvalAck)) {
                return new EvalAck(false, "Neocekivan odgovor od stanice " + handle.getWorkerId());
            }
            EvalAck ack = (EvalAck) response;
            if (ack.isScheduled()) {
                jobRegistry.evalDispatched(request.getJobSetId()); // NOVO
            }
            logger.log("EvalDispatcher", "eval() zadatak '" + request.getName()
                    + "' (job set " + request.getJobSetId() + ") -> stanica "
                    + handle.getWorkerId() + " (round-robin)");
            return ack;
        } catch (Exception e) {
            logger.log("EvalDispatcher", "Slanje eval() zadatka stanici " + handle.getWorkerId()
                    + " nije uspelo: " + e);
            return new EvalAck(false, "Komunikacija sa stanicom " + handle.getWorkerId()
                    + " nije uspela: " + e.getMessage());
        }
    }
}