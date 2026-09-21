package test;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;

import rs.ac.bg.etf.kdp.linda.LindaSocketClient;

public class TestJobHungryBirds {
    private static final int N = 3;
    // NOVO: F=2 umesto 4 - mora da vazi da (N * ROUNDS) bude tacan
    // visekratnik od F pomnozeno brojem POTREBNIH dodatnih (ne-besplatnih)
    // signala = NUM_PARENTS*ROUNDS - 1. Ovde: 3*2=6 potrosnji, treba
    // 2*2-1=3 dodatna signala, F=6/3=2.
    private static final int F = 2;
    private static final int ROUNDS = 2;

    public static void main(String[] args) throws Exception {
        try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
            linda.out(new String[] { "parent_baton" });
            linda.out(new String[] { "hunt_mutex" });

            for (int i = 0; i < 2; i++) linda.eval("parent-" + i, new ParentBirdRunnable(i));
            for (int i = 0; i < N; i++) linda.eval("child-" + i, new ChildBirdRunnable(i));

            int total = 2 + N;
            for (int i = 0; i < total; i++) {
                linda.in(new String[] { "done" });
            }
            Files.write(Paths.get("output.txt"),
                    ("2 roditelja i " + N + " pilica zavrsili " + ROUNDS + " rundi hranjenja").getBytes());
        }
        System.out.println("[HungryBirds] pokrenuto i zavrseno");
    }

    private static class ChildBirdRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;

        ChildBirdRunnable(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                for (int round = 0; round < ROUNDS; round++) {
                    String[] potT = new String[] { "pot", null };
                    linda.in(potT);
                    int pot = Integer.parseInt(potT[1]) - 1;

                    if (pot == 0) {
                        linda.out(new String[] { "parent_baton" });
                        System.out.println("[Pile " + id + "] pojelo poslednjeg crva, budi roditelja");
                    } else {
                        linda.out(new String[] { "pot", String.valueOf(pot) });
                        System.out.println("[Pile " + id + "] jede, u posudi ostalo " + pot);
                    }
                    Thread.sleep(80);
                }
                linda.out(new String[] { "done" });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class ParentBirdRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;

        ParentBirdRunnable(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                for (int round = 0; round < ROUNDS; round++) {
                    linda.in(new String[] { "parent_baton" });
                    linda.in(new String[] { "hunt_mutex" });
                    System.out.println("[Roditelj " + id + "] lovi za decu");
                    Thread.sleep(150);
                    linda.out(new String[] { "pot", String.valueOf(F) });
                    linda.out(new String[] { "hunt_mutex" });

                    linda.in(new String[] { "hunt_mutex" });
                    System.out.println("[Roditelj " + id + "] lovi za sebe");
                    Thread.sleep(100);
                    linda.out(new String[] { "hunt_mutex" });
                }
                linda.out(new String[] { "done" });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}