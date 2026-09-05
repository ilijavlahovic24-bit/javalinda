package test;

import java.nio.file.Files;
import java.nio.file.Paths;

import rs.ac.bg.etf.kdp.linda.LindaSocketClient;

public class TestJobLong {

    public static void main(String[] args) throws Exception {
        int totalSeconds = args.length > 0 ? Integer.parseInt(args[0]) : 60;

        System.out.println("[TestJobLong] pocinjem, trajanje = " + totalSeconds + "s");

        try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
            linda.out(new String[] { "status", "pokrenut" });

            int elapsed = 0;
            int step = 5; // ispisi napredak na svakih 5s
            while (elapsed < totalSeconds) {
                int sleepNow = Math.min(step, totalSeconds - elapsed);
                Thread.sleep(sleepNow * 1000L);
                elapsed += sleepNow;
                System.out.println("[TestJobLong] proteklo " + elapsed + "/" + totalSeconds
                        + "s - jos uvek radim (ovo je dobar trenutak da ugasis stanicu za failover test)");

                // osvezi torku sa proteklim vremenom - korisno ako zelis da
                // eksterno proveris "koliko je stiglo da odradi" pre pada
                linda.out(new String[] { "progress", String.valueOf(elapsed) });
            }
        }

        Files.write(Paths.get("output.txt"),
                ("posao zavrsen posle " + totalSeconds + "s").getBytes());

        System.out.println("[TestJobLong] gotovo");
    }
}