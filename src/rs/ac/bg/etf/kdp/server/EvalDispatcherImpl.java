package rs.ac.bg.etf.kdp.server;

import java.rmi.RemoteException;

import rs.ac.bg.etf.kdp.net.MessageConnection;
import rs.ac.bg.etf.kdp.protocol.EvalAck;
import rs.ac.bg.etf.kdp.protocol.EvalRequest;
import rs.ac.bg.etf.kdp.util.SimpleLogger;

/**
 * Bira sledecu stanicu round-robin principom (WorkerRegistry.nextWorker())
 * i salje joj EvalRequest preko NOVE konekcije ka njenom dispatch
 * host/port-u. Ne drzi trajnu konekciju - otvara, salje, ceka odgovor,
 * zatvara.
 */
public class EvalDispatcherImpl implements EvalDispatcher {

    private final WorkerRegistry workerRegistry;
    private final SimpleLogger logger;

    public EvalDispatcherImpl(WorkerRegistry workerRegistry, SimpleLogger logger) {
        this.workerRegistry = workerRegistry;
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
            logger.log("EvalDispatcher", "eval() zadatak '" + request.getName()
                    + "' (job set " + request.getJobSetId() + ") -> stanica "
                    + handle.getWorkerId() + " (round-robin)");
            return (EvalAck) response;
        } catch (Exception e) {
            logger.log("EvalDispatcher", "Slanje eval() zadatka stanici " + handle.getWorkerId()
                    + " nije uspelo: " + e);
            return new EvalAck(false, "Komunikacija sa stanicom " + handle.getWorkerId()
                    + " nije uspela: " + e.getMessage());
        }
    }
}