package rs.ac.bg.etf.kdp.linda;

import rs.ac.bg.etf.kdp.protocol.EvalAck;
import rs.ac.bg.etf.kdp.server.TupleSpaceServer;
import rs.ac.bg.etf.kdp.util.SimpleLogger;

/**
 * Unit test: pokrece TupleSpaceServer u istom procesu (loopback), pa dva
 * LindaSocketClient-a (simulirajuci dva "posla"/procesa koji dele isti
 * jobSetId) rade out()/in() jedan naspram drugog preko pravih soketa.
 */
public class LindaSocketClientTest {

    public static void main(String[] args) throws Exception {
        SimpleLogger logger = new SimpleLogger();
        TupleSpaceServer server = new TupleSpaceServer(0, logger,
                request -> new EvalAck(false, "eval() dispatcher jos nije povezan (Faza 5)"));
        server.start();
        int port = waitForPort(server);

        testBasicOutIn(port);
        testWildcardBlockingIn(port);
        testNonBlockingVariants(port);

        server.stop();
        System.out.println("Svi testovi prosli.");
    }

    private static void testBasicOutIn(int port) throws Exception {
        try (LindaSocketClient client = new LindaSocketClient("localhost", port, 1)) {
            client.out(new String[] { "cmd", "42" });
            String[] template = new String[] { "cmd", null };
            client.in(template);
            assertEquals("42", template[1], "in() treba da popuni wildcard polje");
        }
    }

    private static void testWildcardBlockingIn(int port) throws Exception {
        final String[] template = new String[] { "cmd", null };
        final boolean[] done = { false };

        Thread waiter = new Thread(() -> {
            try (LindaSocketClient client = new LindaSocketClient("localhost", port, 2)) {
                client.in(template);
                done[0] = true;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        waiter.start();

        Thread.sleep(300);
        if (done[0]) {
            throw new AssertionError("in() nije trebalo da se zavrsi pre out()-a");
        }

        try (LindaSocketClient outClient = new LindaSocketClient("localhost", port, 2)) {
            outClient.out(new String[] { "cmd", "77" });
        }
        waiter.join(3000);

        assertEquals(true, done[0], "in() se nije odblokirao posle out()-a");
        assertEquals("77", template[1], "in() nije popunio ispravnu vrednost");
    }

    private static void testNonBlockingVariants(int port) throws Exception {
        try (LindaSocketClient client = new LindaSocketClient("localhost", port, 3)) {
            String[] template = new String[] { "x", null };

            boolean foundBeforeOut = client.inp(template);
            assertEquals(false, foundBeforeOut, "inp() ne sme naci nista u praznom prostoru");

            client.out(new String[] { "x", "1" });
            boolean foundAfterOut = client.rdp(template);
            assertEquals(true, foundAfterOut, "rdp() treba da nadje torku posle out()-a");

            boolean removed = client.inp(template);
            assertEquals(true, removed, "inp() treba da nadje istu torku");
        }
    }

    private static int waitForPort(TupleSpaceServer server) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            try {
                return server.getPort();
            } catch (IllegalStateException e) {
                Thread.sleep(20);
            }
        }
        throw new IllegalStateException("Server se nije pokrenuo na vreme");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " (ocekivano=" + expected + ", dobijeno=" + actual + ")");
        }
    }
}