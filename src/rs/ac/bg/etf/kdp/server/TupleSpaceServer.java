package rs.ac.bg.etf.kdp.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import rs.ac.bg.etf.kdp.linda.TupleSpaceImpl;
import rs.ac.bg.etf.kdp.net.MessageConnection;
import rs.ac.bg.etf.kdp.net.MessageServer;
import rs.ac.bg.etf.kdp.protocol.EvalRequest;
import rs.ac.bg.etf.kdp.protocol.TupleMessage;
import rs.ac.bg.etf.kdp.protocol.TupleResponse;
import rs.ac.bg.etf.kdp.util.SimpleLogger;

public class TupleSpaceServer {

    public static class JobSetHandshake implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private final long jobSetId;

        public JobSetHandshake(long jobSetId) {
            this.jobSetId = jobSetId;
        }

        public long getJobSetId() {
            return jobSetId;
        }
    }

    private final Map<Long, TupleSpaceImpl> spaces = new ConcurrentHashMap<>();
    private final SimpleLogger logger;
    private final MessageServer messageServer;
    private final EvalDispatcher evalDispatcher;

    public TupleSpaceServer(int port, SimpleLogger logger, EvalDispatcher evalDispatcher) {
        this.logger = logger;
        this.evalDispatcher = evalDispatcher;
        this.messageServer = new MessageServer(port, this::handleConnection);
    }

    public void start() {
        new Thread(messageServer, "tuple-space-server").start();
    }

    public void stop() {
        messageServer.stop();
    }

    public int getPort() {
        return messageServer.getPort();
    }

    public void createSpace(long jobSetId) {
        spaces.computeIfAbsent(jobSetId, id -> new TupleSpaceImpl());
    }

    public void removeSpace(long jobSetId) {
        spaces.remove(jobSetId);
    }

    private void handleConnection(MessageConnection connection) throws Exception {
        Object first = connection.receive();
        if (!(first instanceof JobSetHandshake)) {
            logger.log("TupleSpaceServer", "Konekcija bez ispravnog handshake-a, zatvaram");
            return;
        }
        long jobSetId = ((JobSetHandshake) first).getJobSetId();
        TupleSpaceImpl space = spaces.computeIfAbsent(jobSetId, id -> new TupleSpaceImpl());

        while (!connection.isClosed()) {
            Object message = connection.receive();
            if (message instanceof TupleMessage) {
                connection.send(execute(space, (TupleMessage) message));
            } else if (message instanceof EvalRequest) {
                // NOVO (Faza 4): eval() ne dira TupleSpaceImpl direktno - samo se
                // prosledjuje dalje preko EvalDispatcher-a (ka WorkerRegistry-ju u Fazi 5)
                connection.send(evalDispatcher.dispatch((EvalRequest) message));
            } else {
                logger.log("TupleSpaceServer", "Neocekivana poruka na job set " + jobSetId
                        + " konekciji: " + message);
            }
        }
    }

    private TupleResponse execute(TupleSpaceImpl space, TupleMessage msg) {
        String[] tuple = msg.getTuple();
        switch (msg.getOperation()) {
            case OUT:
                space.out(tuple);
                return new TupleResponse(true, null);
            case IN: {
                String[] template = tuple.clone();
                space.in(template);
                return new TupleResponse(true, template);
            }
            case INP: {
                String[] template = tuple.clone();
                boolean found = space.inp(template);
                return new TupleResponse(found, template);
            }
            case RD: {
                String[] template = tuple.clone();
                space.rd(template);
                return new TupleResponse(true, template);
            }
            case RDP: {
                String[] template = tuple.clone();
                boolean found = space.rdp(template);
                return new TupleResponse(found, template);
            }
            default:
                throw new IllegalStateException("Nepoznata operacija: " + msg.getOperation());
        }
    }
}