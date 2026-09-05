package rs.ac.bg.etf.kdp.net;

import java.io.Serializable;

/**
 * Rucni smoke test za MessageServer/MessageConnection - server u niti
 * unutar istog procesa (loopback), klijent u main niti. Ne testira nista
 * specificno za Linda/posao - samo da osnovni request/response ciklus
 * preko soketa radi ispravno pre nego sto se na njemu gradi bilo sta
 * drugo (TupleSpaceServer, WorkerRegistry protokol, itd.).
 */
public class SocketSmokeTest {

    private static class Ping implements Serializable {
        private static final long serialVersionUID = 1L;
        final String text;
        Ping(String text) {
            this.text = text;
        }
    }

    private static class Pong implements Serializable {
        private static final long serialVersionUID = 1L;
        final String text;
        Pong(String text) {
            this.text = text;
        }
    }

    public static void main(String[] args) throws Exception {
        MessageServer server = new MessageServer(0, connection -> {
            // port 0 = OS dodeljuje slobodan port
            Serializable received = connection.receive();
            if (received instanceof Ping) {
                String text = ((Ping) received).text;
                connection.send(new Pong("echo:" + text));
            }
        });

        Thread serverThread = new Thread(server, "smoke-test-server");
        serverThread.start();

        // saceka da server stvarno pocne da oslušukuje (getPort() baca dok se ne startuje)
        int port = waitForPort(server);

        try (MessageConnection client = MessageConnection.connectTo("localhost", port)) {
            client.send(new Ping("hello"));
            Serializable response = client.receive();

            if (!(response instanceof Pong)) {
                throw new AssertionError("Ocekivao Pong, dobio: " + response);
            }
            String text = ((Pong) response).text;
            if (!"echo:hello".equals(text)) {
                throw new AssertionError("Ocekivao 'echo:hello', dobio: " + text);
            }
        }

        server.stop();
        System.out.println("Svi testovi prosli.");
    }

    private static int waitForPort(MessageServer server) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            try {
                return server.getPort();
            } catch (IllegalStateException e) {
                Thread.sleep(20);
            }
        }
        throw new IllegalStateException("Server se nije pokrenuo na vreme");
    }
}