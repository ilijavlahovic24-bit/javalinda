package test;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;

import rs.ac.bg.etf.kdp.linda.LindaSocketClient;

/**
 * park torka: [free, priority, passed, waitingEnter, waitingExit]
 * Izuzetak iz specifikacije: "Ako nema automobila iz smera koji ima
 * prioritet, automobili koji nemaju prioritetni smer smeju da prolaze
 * kroz rampu" - realizovano preko waitingEnter/waitingExit brojaca.
 */
public class TestJobParking {
    private static final int N = 3;
    private static final int K = 2;

    public static void main(String[] args) throws Exception {
        try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
            linda.out(new String[] { "park", String.valueOf(N), "exit", "0", "0", "0" });
            linda.out(new String[] { "ramp" });

            for (int i = 0; i < 4; i++) linda.eval("enter-" + i, new EnterRunnable(i));
            for (int i = 0; i < 2; i++) linda.eval("exit-" + i, new ExitRunnable(i));

            for (int i = 0; i < 6; i++) {
                linda.in(new String[] { "done" });
            }
            Files.write(Paths.get("output.txt"), ("4 ulaza i 2 izlaza kroz rampu zavrseno").getBytes());
        }
        System.out.println("[Parking] pokrenuto i zavrseno");
    }

    private static class EnterRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;

        EnterRunnable(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                // prijava: "cekam da udjem" - povecaj waitingEnter (indeks 4)
                String[] reg = new String[] { "park", null, null, null, null, null };
                linda.in(reg);
                reg[4] = String.valueOf(Integer.parseInt(reg[4]) + 1);
                linda.out(reg);

                while (true) {
                    String[] s = new String[] { "park", null, null, null, null, null };
                    linda.in(s);
                    int free = Integer.parseInt(s[1]);
                    String priority = s[2];
                    int passed = Integer.parseInt(s[3]);
                    int waitingEnter = Integer.parseInt(s[4]);
                    int waitingExit = Integer.parseInt(s[5]);

                    boolean canEnter = free > 0 && (priority.equals("enter") || waitingExit == 0);

                    if (!canEnter) {
                        linda.out(s);
                        Thread.sleep(150);
                        continue;
                    }

                    free--;
                    passed++;
                    waitingEnter--;
                    if (passed == K) {
                        passed = 0;
                        priority = "exit";
                    }
                    linda.out(new String[] { "park", String.valueOf(free), priority,
                            String.valueOf(passed), String.valueOf(waitingEnter), String.valueOf(waitingExit) });
                    break;
                }

                linda.in(new String[] { "ramp" });
                System.out.println("[Auto " + id + "] ulazi kroz rampu");
                Thread.sleep(100);
                linda.out(new String[] { "ramp" });

                System.out.println("[Auto " + id + "] parkiran");
                linda.out(new String[] { "done" });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class ExitRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;

        ExitRunnable(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                Thread.sleep(300);

                // prijava: "cekam da izadjem" - povecaj waitingExit (indeks 5)
                String[] reg = new String[] { "park", null, null, null, null, null };
                linda.in(reg);
                reg[5] = String.valueOf(Integer.parseInt(reg[5]) + 1);
                linda.out(reg);

                while (true) {
                    String[] s = new String[] { "park", null, null, null, null, null };
                    linda.in(s);
                    int free = Integer.parseInt(s[1]);
                    String priority = s[2];
                    int passed = Integer.parseInt(s[3]);
                    int waitingEnter = Integer.parseInt(s[4]);
                    int waitingExit = Integer.parseInt(s[5]);

                    boolean canExit = priority.equals("exit") || waitingEnter == 0;

                    if (!canExit) {
                        linda.out(s);
                        Thread.sleep(150);
                        continue;
                    }

                    free++;
                    passed++;
                    waitingExit--;
                    if (passed == K) {
                        passed = 0;
                        priority = "enter";
                    }
                    linda.out(new String[] { "park", String.valueOf(free), priority,
                            String.valueOf(passed), String.valueOf(waitingEnter), String.valueOf(waitingExit) });
                    break;
                }

                linda.in(new String[] { "ramp" });
                System.out.println("[Auto " + id + "] izlazi kroz rampu (placa)");
                Thread.sleep(100);
                linda.out(new String[] { "ramp" });

                System.out.println("[Auto " + id + "] napustio parking");
                linda.out(new String[] { "done" });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}