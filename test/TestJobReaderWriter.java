package test;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;

import rs.ac.bg.etf.kdp.linda.LindaSocketClient;

/**
 * Citaoci-pisci, uz uslov da proces koji je ranije stigao ranije i pocinje
 * (FIFO red) - RI4DRS vezbe iz Linde, str. 30-34.
 */
public class TestJobReaderWriter {
    private static final int NUM_READERS = 3;
    private static final int NUM_WRITERS = 2;

    public static void main(String[] args) throws Exception {
        try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
            linda.out(new String[] { "id", "0" });
            linda.out(new String[] { "ok_to_work", "0" });
            linda.out(new String[] { "readers_num", "0" });

            for (int i = 0; i < NUM_READERS; i++) linda.eval("reader-" + i, new ReaderRunnable());
            for (int i = 0; i < NUM_WRITERS; i++) linda.eval("writer-" + i, new WriterRunnable());

            // NOVO: sacekaj da svi citaoci/pisci zavrse pre nego sto glavni
            // proces ispise izlazni fajl - inace main() zavrsi odmah, dok
            // eval() procesi jos rade (eval je fire-and-forget), i nikad
            // nista ne bi bilo upisano u output.txt
            int totalParticipants = NUM_READERS + NUM_WRITERS;
            for (int i = 0; i < totalParticipants; i++) {
                linda.in(new String[] { "done" });
            }

            Files.write(Paths.get("output.txt"),
                    ("zavrseno - " + NUM_READERS + " citalaca, " + NUM_WRITERS + " pisaca").getBytes());
        }
        System.out.println("[ReaderWriter] pokrenuto i zavrseno");
    }

    private static class ReaderRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                for (int round = 0; round < 5; round++) {
                    String[] idT = new String[] { "id", null };
                    linda.in(idT);
                    int id = Integer.parseInt(idT[1]);
                    linda.out(new String[] { "id", String.valueOf(id + 1) });

                    linda.in(new String[] { "ok_to_work", String.valueOf(id) });

                    String[] numT = new String[] { "readers_num", null };
                    linda.in(numT);
                    int n = Integer.parseInt(numT[1]);
                    linda.out(new String[] { "readers_num", String.valueOf(n + 1) });

                    linda.out(new String[] { "ok_to_work", String.valueOf(id + 1) });

                    System.out.println("[Reader] cita, runda " + round);

                    String[] numT2 = new String[] { "readers_num", null };
                    linda.in(numT2);
                    int n2 = Integer.parseInt(numT2[1]);
                    linda.out(new String[] { "readers_num", String.valueOf(n2 - 1) });
                }
                linda.out(new String[] { "done" }); // NOVO
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class WriterRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                for (int round = 0; round < 5; round++) {
                    String[] idT = new String[] { "id", null };
                    linda.in(idT);
                    int id = Integer.parseInt(idT[1]);
                    linda.out(new String[] { "id", String.valueOf(id + 1) });

                    linda.in(new String[] { "ok_to_work", String.valueOf(id) });
                    linda.rd(new String[] { "readers_num", "0" });

                    System.out.println("[Writer] pise, runda " + round);

                    linda.out(new String[] { "ok_to_work", String.valueOf(id + 1) });
                }
                linda.out(new String[] { "done" }); // NOVO
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}