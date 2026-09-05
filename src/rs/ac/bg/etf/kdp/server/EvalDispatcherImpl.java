package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.net.MessageConnection;
import rs.ac.bg.etf.kdp.protocol.EvalAck;
import rs.ac.bg.etf.kdp.protocol.EvalRequest;
import rs.ac.bg.etf.kdp.util.SimpleLogger;

/**
 * Bira slobodnu radnu stanicu preko WorkerRegistry-ja i salje joj
 * EvalRequest preko nove konekcije ka njenom dispatch host/port-u
 * (server je ovde klijent, stanica je server - videti napomenu na vrhu
 * odgovora u kom je ovo uvedeno). Ne drzi trajnu konekciju - otvara,
 * salje, ceka odgovor, zatvara. Jednostavnije od pool-ovanja konekcija za
 * sada; ako se eval() pokaze cestim, razmotriti keširanje konekcija
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
        WorkerHandle handle = workerRegistry.findFreeWorker();
        if (handle == null) {
            return new EvalAck(false, "Nijedna radna stanica trenutno nema slobodan kapacitet");
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
                    + handle.getWorkerId());
            return (EvalAck) response;
        } catch (Exception e) {
            logger.log("EvalDispatcher", "Slanje eval() zadatka stanici " + handle.getWorkerId()
                    + " nije uspelo: " + e);
            return new EvalAck(false, "Komunikacija sa stanicom " + handle.getWorkerId()
                    + " nije uspela: " + e.getMessage());
        }
    }
}