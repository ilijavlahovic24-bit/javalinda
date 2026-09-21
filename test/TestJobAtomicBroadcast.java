package test;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;

import rs.ac.bg.etf.kdp.linda.LindaSocketClient;

public class TestJobAtomicBroadcast {
    private static final int MESSAGES = 5;
    private static final int RECEIVERS = 3;

    public static void main(String[] args) throws Exception {
        try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
            linda.eval("broadcaster", new BroadcasterRunnable());
            for (int i = 0; i < RECEIVERS; i++) linda.eval("receiver-" + i, new ReceiverRunnable(i));

            int total = 1 + RECEIVERS;
            for (int i = 0; i < total; i++) {
                linda.in(new String[] { "done" });
            }
            Files.write(Paths.get("output.txt"),
                    (MESSAGES + " poruka isporuceno svim (" + RECEIVERS + ") primaocima istim redosledom").getBytes());
        }
        System.out.println("[AtomicBroadcast] pokrenuto i zavrseno");
    }

    private static class BroadcasterRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                for (int seq = 0; seq < MESSAGES; seq++) {
                    linda.out(new String[] { "msg", String.valueOf(seq), "poruka-" + seq });
                    System.out.println("[Broadcaster] poslao poruku " + seq);
                }
                linda.out(new String[] { "done" }); // DODATO
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class ReceiverRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;

        ReceiverRunnable(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                for (int seq = 0; seq < MESSAGES; seq++) {
                    String[] msg = new String[] { "msg", String.valueOf(seq), null };
                    linda.rd(msg);
                    System.out.println("[Primalac " + id + "] primio poruku " + seq + ": " + msg[2]);
                }
                linda.out(new String[] { "done" }); // DODATO
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}