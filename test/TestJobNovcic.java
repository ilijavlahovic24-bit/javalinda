package test;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

import rs.ac.bg.etf.kdp.linda.LindaSocketClient;

/**
 * "Problem izbora" - tri igraca nezavisno bacaju novcic (0/1). Ako tacno
 * jedan ima drugaciji rezultat od preostala dva, taj je izabran. Ako su
 * sva tri ista, runda se ponavlja. Izvor: RI4DRS vezbe iz Linde,
 * rti.etf.bg.ac.rs/rti/ri4drs/literatura/vezbe/09_Linda.pdf, str. 35-38.
 *
 * Sinhronizacija runda po rundi je implicitna: svaki igrac cita (rd, ne
 * konzumira) tudje bacanje za TEKUCU rundu preko blokirajuceg poziva -
 * ako drugi igrac jos nije bacio za tu rundu, poziv prosto ceka, cime se
 * igraci prirodno drze u koraku bez posebne barijere.
 */
public class TestJobNovcic {

    public static void main(String[] args) throws Exception {
        System.out.println("[TestJobNovcic] pokrecem tri igraca");

        try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
            linda.eval("player-0", new PlayerRunnable(0));
            linda.eval("player-1", new PlayerRunnable(1));
            linda.eval("player-2", new PlayerRunnable(2));

            String[] template = new String[] { "winner", null, null };
            linda.in(template);
            int winnerId = Integer.parseInt(template[1]);
            int round = Integer.parseInt(template[2]);

            System.out.println("[TestJobNovcic] izabran igrac " + winnerId
                    + " u rundi " + round);

            Files.write(Paths.get("output.txt"),
                    ("izabran igrac " + winnerId + " u rundi " + round).getBytes());
        }

        System.out.println("[TestJobNovcic] gotovo");
    }

    private static class PlayerRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;

        private final int id;

        PlayerRunnable(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            Random random = new Random();
            int other1 = (id + 1) % 3;
            int other2 = (id + 2) % 3;

            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                int round = 0;
                while (true) {
                    int myCoin = random.nextInt(2);
                    linda.out(new String[] { "coin", String.valueOf(id),
                            String.valueOf(round), String.valueOf(myCoin) });

                    String[] t1 = new String[] { "coin", String.valueOf(other1),
                            String.valueOf(round), null };
                    linda.rd(t1);
                    int coin1 = Integer.parseInt(t1[3]);

                    String[] t2 = new String[] { "coin", String.valueOf(other2),
                            String.valueOf(round), null };
                    linda.rd(t2);
                    int coin2 = Integer.parseInt(t2[3]);

                    boolean allSame = (myCoin == coin1) && (myCoin == coin2);
                    if (!allSame) {
                        boolean isWinner = (myCoin != coin1) && (myCoin != coin2);
                        if (isWinner) {
                            linda.out(new String[] { "winner", String.valueOf(id),
                                    String.valueOf(round) });
                        }
                        System.out.println("[Player " + id + "] runda " + round
                                + " zavrsena - ja sam pobednik: " + isWinner);
                        return;
                    }

                    System.out.println("[Player " + id + "] runda " + round
                            + " - svi isti (" + myCoin + "), ponavljam");
                    round++;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}