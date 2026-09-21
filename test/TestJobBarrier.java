package test;

import java.io.Serializable;
import rs.ac.bg.etf.kdp.linda.LindaSocketClient;

/** N procesa mora da stigne do tacke sinhronizacije pre nego sto bilo ko nastavi dalje. */
public class TestJobBarrier {
    private static final int N = 4;

    public static void main(String[] args) throws Exception {
        try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
            for (int round = 0; round < 3; round++) {
                linda.out(new String[] { "barrier_count", String.valueOf(round), "0" });
            }
            for (int i = 0; i < N; i++) {
                linda.eval("participant-" + i, new ParticipantRunnable(i));
            }
            for (int i = 0; i < N; i++) {
                linda.in(new String[] { "done" });
            }
            java.nio.file.Files.write(java.nio.file.Paths.get("output.txt"),
                    ("svi ucesnici prosli sve barijere").getBytes());
        }
        System.out.println("[Barrier] pokrenuto i zavrseno");
    }

    private static class ParticipantRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;

        ParticipantRunnable(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                for (int round = 0; round < 3; round++) {
                    System.out.println("[Ucesnik " + id + "] pre barijere, runda " + round);

                    String[] cnt = new String[] { "barrier_count", String.valueOf(round), null };
                    linda.in(cnt);
                    int n = Integer.parseInt(cnt[2]) + 1;
                    linda.out(new String[] { "barrier_count", String.valueOf(round), String.valueOf(n) });

                    if (n == N) {
                        for (int i = 0; i < N; i++) {
                            linda.out(new String[] { "barrier_release", String.valueOf(round) });
                        }
                    }
                    linda.in(new String[] { "barrier_release", String.valueOf(round) });

                    System.out.println("[Ucesnik " + id + "] posle barijere, runda " + round);
                }
                linda.out(new String[] { "done" });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}