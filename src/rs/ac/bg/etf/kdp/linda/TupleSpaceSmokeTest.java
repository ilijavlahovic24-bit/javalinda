package rs.ac.bg.etf.kdp.linda;

/**
 * Rucni "smoke test" bez JUnit zavisnosti - pokrenuti sa
 * `java rs.ac.bg.etf.kdp.linda.TupleSpaceSmokeTest` posle kompajliranja
 */
public class TupleSpaceSmokeTest {

    public static void main(String[] args) throws Exception {
        testBasicOutIn();
        testWildcardBlockingIn();
        testNonBlockingVariants();
        System.out.println("Svi testovi prosli.");
    }

    private static void testBasicOutIn() {
        TupleSpaceImpl space = new TupleSpaceImpl();
        space.out(new String[] { "cmd", "42" });
        String[] template = new String[] { "cmd", null };
        space.in(template);
        assertEquals("42", template[1], "in() treba da popuni wildcard polje");
        assertEquals(0, space.size(), "in() treba da ukloni torku iz prostora");
    }

    private static void testWildcardBlockingIn() throws InterruptedException {
        TupleSpaceImpl space = new TupleSpaceImpl();
        final String[] template = new String[] { "cmd", null };
        final boolean[] done = { false };

        Thread waiter = new Thread(() -> {
            space.in(template);
            done[0] = true;
        });
        waiter.start();

        Thread.sleep(200); // waiter mora da bude blokiran u ovom trenutku
        if (done[0]) {
            throw new AssertionError("in() nije trebalo da se zavrsi pre out()-a");
        }

        space.out(new String[] { "cmd", "77" });
        waiter.join(2000);

        assertEquals(true, done[0], "in() se nije odblokirao posle out()-a");
        assertEquals("77", template[1], "in() nije popunio ispravnu vrednost");
    }

    private static void testNonBlockingVariants() {
        TupleSpaceImpl space = new TupleSpaceImpl();
        String[] template = new String[] { "x", null };

        boolean foundBeforeOut = space.inp(template);
        assertEquals(false, foundBeforeOut, "inp() ne sme naci nista u praznom prostoru");

        space.out(new String[] { "x", "1" });
        boolean foundAfterOut = space.rdp(template);
        assertEquals(true, foundAfterOut, "rdp() treba da nadje torku posle out()-a");
        assertEquals(1, space.size(), "rdp() ne sme ukloniti torku");

        boolean removed = space.inp(template);
        assertEquals(true, removed, "inp() treba da nadje istu torku");
        assertEquals(0, space.size(), "inp() treba da ukloni torku");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " (ocekivano=" + expected + ", dobijeno=" + actual + ")");
        }
    }
}