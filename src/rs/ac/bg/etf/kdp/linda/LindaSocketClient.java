package rs.ac.bg.etf.kdp.linda;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import rs.ac.bg.etf.kdp.net.MessageConnection;
import rs.ac.bg.etf.kdp.protocol.EvalAck;
import rs.ac.bg.etf.kdp.protocol.EvalRequest;
import rs.ac.bg.etf.kdp.protocol.TupleMessage;
import rs.ac.bg.etf.kdp.protocol.TupleResponse;
import rs.ac.bg.etf.kdp.server.TupleSpaceServer.JobSetHandshake;

public class LindaSocketClient implements Linda, AutoCloseable {
    private static final long serialVersionUID = 1L;

    private final MessageConnection connection;
    private final long jobSetId;
    private final byte[] ownJarBytes; // za eval() - moze biti null ako se ne moze pronaci

    public LindaSocketClient(String host, int port, long jobSetId) throws IOException {
        this(host, port, jobSetId, null);
    }

    public LindaSocketClient(String host, int port, long jobSetId, byte[] ownJarBytes)
            throws IOException {
        this.jobSetId = jobSetId;
        this.ownJarBytes = ownJarBytes;
        this.connection = MessageConnection.connectTo(host, port);
        this.connection.send(new JobSetHandshake(jobSetId));
    }

    /**
     * Konvencija za pokrenut posao/eval proces: worker (JobProcessRunner /
     * EvalProcessRunner) postavlja sistemske propertije kdp.tuplespace.host,
     * kdp.tuplespace.port, kdp.jobSetId, kdp.jobJar pri pokretanju procesa -
     * ovaj konstruktor ih cita umesto da ih pozivalac rucno prosledjuje.
     */
    public static LindaSocketClient fromSystemProperties() throws IOException {
        String host = System.getProperty("kdp.tuplespace.host");
        int port = Integer.parseInt(System.getProperty("kdp.tuplespace.port"));
        long jobSetId = Long.parseLong(System.getProperty("kdp.jobSetId"));
        String jarPath = System.getProperty("kdp.jobJar");

        byte[] jarBytes = null;
        if (jarPath != null) {
            try {
                jarBytes = Files.readAllBytes(Paths.get(jarPath));
            } catch (IOException e) {
                // eval() ce raditi bez jarBytes samo ako ciljna stanica vec
                // ima klasu ucitanu iz ranijeg poziva - ne prekidamo ovde,
                // samo eval() moze kasnije pući na ciljnoj stanici
            }
        }
        return new LindaSocketClient(host, port, jobSetId, jarBytes);
    }

    @Override
    public void out(String[] tuple) {
        TupleMatcher.requireNoNulls(tuple);
        send(new TupleMessage(TupleMessage.Op.OUT, tuple));
    }

    @Override
    public void in(String[] tuple) {
        TupleResponse response = send(new TupleMessage(TupleMessage.Op.IN, tuple));
        copyInto(tuple, response.getTuple());
    }

    @Override
    public boolean inp(String[] tuple) {
        TupleResponse response = send(new TupleMessage(TupleMessage.Op.INP, tuple));
        if (response.isFound()) {
            copyInto(tuple, response.getTuple());
        }
        return response.isFound();
    }

    @Override
    public void rd(String[] tuple) {
        TupleResponse response = send(new TupleMessage(TupleMessage.Op.RD, tuple));
        copyInto(tuple, response.getTuple());
    }

    @Override
    public boolean rdp(String[] tuple) {
        TupleResponse response = send(new TupleMessage(TupleMessage.Op.RDP, tuple));
        if (response.isFound()) {
            copyInto(tuple, response.getTuple());
        }
        return response.isFound();
    }

    @Override
    public synchronized void eval(String name, Runnable thread) {
        byte[] runnableBytes = EvalTask.serialize(thread);
        try {
            connection.send(new EvalRequest(jobSetId, name, runnableBytes, ownJarBytes));
            Object response = connection.receive();
            if (!(response instanceof EvalAck)) {
                throw new LindaRuntimeException("Neocekivan odgovor na eval(): " + response);
            }
            EvalAck ack = (EvalAck) response;
            if (!ack.isScheduled()) {
                throw new LindaRuntimeException("eval() nije zakazan: " + ack.getErrorMessage());
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new LindaRuntimeException("eval() komunikacija nije uspela", e);
        }
    }

    private synchronized TupleResponse send(TupleMessage message) {
        try {
            connection.send(message);
            Object response = connection.receive();
            if (!(response instanceof TupleResponse)) {
                throw new LindaRuntimeException("Neocekivan odgovor sa servera: " + response);
            }
            return (TupleResponse) response;
        } catch (IOException | ClassNotFoundException e) {
            throw new LindaRuntimeException("Komunikacija sa TupleSpaceServer-om nije uspela", e);
        }
    }

    private void copyInto(String[] target, String[] source) {
        System.arraycopy(source, 0, target, 0, target.length);
    }

    public void close() {
        connection.close();
    }
}