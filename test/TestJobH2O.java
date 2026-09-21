package test;

import java.io.Serializable;
import rs.ac.bg.etf.kdp.linda.LindaSocketClient;

/**
 * Sinhronizacija H i O niti tako da se uvek formira molekul od tacno 2H+1O.
 * Uproscenje: jedan "spajac" (Bonder) serializuje formiranje molekula -
 * dovoljno za demonstraciju, bez potrebe za slozenijom atomicnom proverom.
 */
public class TestJobH2O {
    private static final int MOLECULES = 5;

    public static void main(String[] args) throws Exception {
        try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
            linda.eval("bonder", new BonderRunnable());
            for (int i = 0; i < MOLECULES * 2; i++) linda.eval("hydrogen-" + i, new HydrogenRunnable(i));
            for (int i = 0; i < MOLECULES; i++) linda.eval("oxygen-" + i, new OxygenRunnable(i));

            int total = 1 + MOLECULES * 2 + MOLECULES;
            for (int i = 0; i < total; i++) {
                linda.in(new String[] { "done" });
            }
            java.nio.file.Files.write(java.nio.file.Paths.get("output.txt"),
                    ("formirano " + MOLECULES + " molekula H2O").getBytes());
        }
        System.out.println("[H2O] pokrenuto i zavrseno");
    }

    private static class BonderRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                for (int i = 0; i < MOLECULES; i++) {
                    linda.in(new String[] { "waitingH" });
                    linda.in(new String[] { "waitingH" });
                    linda.in(new String[] { "waitingO" });
                    linda.out(new String[] { "releaseH" });
                    linda.out(new String[] { "releaseH" });
                    linda.out(new String[] { "releaseO" });
                    System.out.println("[Bonder] formiran molekul #" + i);
                    linda.out(new String[] { "done" });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class HydrogenRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;

        HydrogenRunnable(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                linda.out(new String[] { "waitingH" });
                linda.in(new String[] { "releaseH" });
                System.out.println("[H " + id + "] vezan u molekul");
                linda.out(new String[] { "done" });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class OxygenRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;

        OxygenRunnable(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                linda.out(new String[] { "waitingO" });
                linda.in(new String[] { "releaseO" });
                System.out.println("[O " + id + "] vezan u molekul");
                linda.out(new String[] { "done" });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}