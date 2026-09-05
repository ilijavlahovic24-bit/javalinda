package rs.ac.bg.etf.kdp.net;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;

/**
 * Omotac oko Socket-a za razmenu Serializable poruka. Jedna instanca po
 * konekciji, koristi je i server strana (po konekciji od klijenta/radne
 * stanice) i klijent strana (LindaSocketClient, worker, client paket).
 *
 * ObjectOutputStream.reset() se poziva posle svakog slanja da bi se
 * izbeglo rastuce keširanje objekata na dugotrajnoj konekciji (isti
 * obrazac kao u WorkerConnectionPool iz ranijeg RMI/socket koda).
 */
public class MessageConnection implements AutoCloseable {

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    public MessageConnection(Socket socket) throws IOException {
        this.socket = socket;
        // ObjectOutputStream MORA biti napravljen pre ObjectInputStream-a
        // na OBE strane (header handshake) - ako se ovo zameni na
        // jednoj strani, blokira se na konstruktoru.
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    public static MessageConnection connectTo(String host, int port) throws IOException {
        return new MessageConnection(new Socket(host, port));
    }

    public synchronized void send(Serializable message) throws IOException {
        out.writeObject(message);
        out.flush();
        out.reset();
    }

    /** Blokira dok ne stigne sledeca poruka. Baca EOFException ako je druga strana zatvorila konekciju. */
    public Serializable receive() throws IOException, ClassNotFoundException {
        Object obj = in.readObject();
        return (Serializable) obj;
    }

    public boolean isClosed() {
        return socket.isClosed();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}