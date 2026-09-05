package rs.ac.bg.etf.kdp.net;

import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generican server-socket listener: prima konekcije u petlji, svaku
 * obradjuje u posebnoj niti preko datog ConnectionHandler-a. Greska u
 * jednoj konekciji (malformisan sadrzaj, prekid) NE obara listener za
 * ostale konekcije - isti obrazac kao WorkerEventListener iz ranijeg
 * (RMI-doba) socket koda, sada opsti gradivni blok za server/worker/
 * TupleSpaceServer.
 */
public class MessageServer implements Runnable {

    private final int port;
    private final ConnectionHandler handler;
    private final ExecutorService connectionPool = Executors.newCachedThreadPool();
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    public MessageServer(int port, ConnectionHandler handler) {
        this.port = port;
        this.handler = handler;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            while (running) {
                Socket client = serverSocket.accept();
                connectionPool.submit(() -> handleConnection(client));
            }
        } catch (IOException e) {
            if (running) {
                e.printStackTrace();
            }
            // running == false znaci da je ovo ocekivan efekat stop()-a
        }
    }

    private void handleConnection(Socket client) {
        try (MessageConnection connection = new MessageConnection(client)) {
            handler.handle(connection);
        } catch (EOFException | SocketException e) {
            // druga strana zatvorila konekciju - normalan zavrsetak
        } catch (Exception e) {
            System.err.println("MessageServer: greska u konekciji sa "
                    + client.getRemoteSocketAddress() + ": " + e);
        }
    }

    /** Blokira dok server ne pocne da oslušukuje - koristno za testove/sinhronizaciju startupa. */
    public int getPort() {
        if (serverSocket == null) {
            throw new IllegalStateException("Server jos nije startovan");
        }
        return serverSocket.getLocalPort();
    }

    public void stop() {
        running = false;
        connectionPool.shutdownNow();
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }
}