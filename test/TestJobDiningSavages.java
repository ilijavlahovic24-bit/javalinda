package test;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;

import rs.ac.bg.etf.kdp.linda.LindaSocketClient;

/** Divljaci jedu iz zajedicnkog lonca; kad je lonac prazan, kuvar ga ponovo napuni. */
public class TestJobDiningSavages {
    private static final int POT_SIZE = 3;
    private static final int MEALS_PER_SAVAGE = 4;

    public static void main(String[] args) throws Exception {
        try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
            linda.out(new String[] { "pot", "0" });

            linda.eval("cook", new CookRunnable());
            for (int i = 0; i < 3; i++) linda.eval("savage-" + i, new SavageRunnable(i));

            for (int i = 0; i < 3; i++) { // NAMERNO samo 3, ne 4 - kuvar se ne ceka lokalno
                linda.in(new String[] { "done" });
            }
            Files.write(Paths.get("output.txt"),
                    ("svi divljaci (3) su se najeli - kuvar ostaje aktivan u pozadini").getBytes());
        }
        System.out.println("[DiningSavages] pokrenuto i zavrseno (kuvar i dalje radi - server ceka na njegov timeout)");
    }

    private static class CookRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                while (true) {
                    linda.in(new String[] { "hungry" });
                    linda.out(new String[] { "pot", String.valueOf(POT_SIZE) });
                    linda.out(new String[] { "full" });
                    System.out.println("[Kuvar] napunio lonac");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class SavageRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;

        SavageRunnable(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                for (int meal = 0; meal < MEALS_PER_SAVAGE; meal++) {
                    String[] potT = new String[] { "pot", null };
                    linda.in(potT);
                    int s = Integer.parseInt(potT[1]);

                    if (s == 0) {
                        linda.out(new String[] { "hungry" });
                        linda.in(new String[] { "full" });
                        String[] potT2 = new String[] { "pot", null };
                        linda.in(potT2);
                        s = Integer.parseInt(potT2[1]);
                    }

                    linda.out(new String[] { "pot", String.valueOf(s - 1) });
                    System.out.println("[Divljak " + id + "] jede, obrok " + meal);
                }
                linda.out(new String[] { "done" }); // DODATO
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}