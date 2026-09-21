package test;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;

import rs.ac.bg.etf.kdp.linda.LindaSocketClient;

public class TestJobBearHoneybee {
    private static final int N = 3; // broj pcela
    private static final int H = 5; // kapacitet kosnice
    private static final int BEE_ROUNDS = 2;
    // NOVO: koliko PUTA kosnica moze da se napuni do kraja, garantovano
    // datim parametrima - N * BEE_ROUNDS / H, zaokruzeno na dole (ovde
    // 3*2/5 = 1). Medved cita TACNO ovoliko "bear_wake" signala, ne
    // fiksnih BEE_ROUNDS - inace ceka signal koji nikad ne stize.
    private static final int BEAR_ROUNDS = (N * BEE_ROUNDS) / H;

    public static void main(String[] args) throws Exception {
        if (BEAR_ROUNDS == 0) {
            throw new IllegalStateException("N * BEE_ROUNDS mora biti >= H da bi se kosnica bar jednom napunila");
        }
        try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
            linda.out(new String[] { "honey", "0" });
            linda.eval("bear", new BearRunnable());
            for (int i = 0; i < N; i++) linda.eval("bee-" + i, new BeeRunnable(i));

            int total = 1 + N;
            for (int i = 0; i < total; i++) {
                linda.in(new String[] { "done" });
            }
            Files.write(Paths.get("output.txt"),
                    ("medved i " + N + " pcele zavrsili - kosnica napunjena " + BEAR_ROUNDS + " put(a)").getBytes());
        }
        System.out.println("[BearHoneybee] pokrenuto i zavrseno");
    }

    private static class BeeRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;

        BeeRunnable(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                for (int round = 0; round < BEE_ROUNDS; round++) {
                    Thread.sleep(50);

                    String[] honeyT = new String[] { "honey", null };
                    linda.in(honeyT);
                    int count = Integer.parseInt(honeyT[1]) + 1;

                    if (count == H) {
                        linda.out(new String[] { "bear_wake" });
                        System.out.println("[Pcela " + id + "] napunila kosnicu, budi medveda");
                    } else {
                        linda.out(new String[] { "honey", String.valueOf(count) });
                        System.out.println("[Pcela " + id + "] ubacila med, kosnica ima " + count);
                    }
                }
                linda.out(new String[] { "done" });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class BearRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                for (int round = 0; round < BEAR_ROUNDS; round++) { // PROMENJENO: BEAR_ROUNDS, ne BEE_ROUNDS
                    linda.in(new String[] { "bear_wake" });
                    System.out.println("[Medved] jede med");
                    Thread.sleep(100);
                    linda.out(new String[] { "honey", "0" });
                }
                linda.out(new String[] { "done" });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}