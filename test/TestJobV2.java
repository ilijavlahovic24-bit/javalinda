package test;

import java.io.Serializable;

import rs.ac.bg.etf.kdp.linda.Linda;
import rs.ac.bg.etf.kdp.linda.LindaSocketClient;

/**
 * Test posao za NOVU (socket) arhitekturu - obican Java proces sa
 * standardnim main()-om, ne LindaJob konvencija (ta je bila RMI-specificna).
 * Povezuje se na Linda preko sistemskih propertija koje worker postavlja
 * pri pokretanju procesa (LindaSocketClient.fromSystemProperties()).
 */
public class TestJobV2 {

    public static void main(String[] args) throws Exception {
        System.out.println("[TestJobV2] pocinjem, args=" + java.util.Arrays.toString(args));

        try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
            linda.out(new String[] { "greeting", "hello-from-main" });
            System.out.println("[TestJobV2] ubacio torku 'greeting'");

            linda.eval("echo-eval", new EchoRunnable());
            System.out.println("[TestJobV2] pozvao eval(), cekam potvrdu...");

            String[] confirmation = new String[] { "eval-done", null };
            linda.in(confirmation);
            System.out.println("[TestJobV2] eval potvrdjen, vrednost=" + confirmation[1]);
        }

        // Ono sto worker ocekuje kao izlazni fajl - napisi ga u trenutni
        // radni direktorijum (JobProcessRunner postavlja workDir kao
        // radni direktorijum procesa preko builder.directory(workDir))
        java.nio.file.Files.write(java.nio.file.Paths.get("output.txt"),
                "posao zavrsen uspesno".getBytes());

        System.out.println("[TestJobV2] gotovo");
    }

    /**
     * Runnable za eval() - MORA biti Serializable. Sam pravi SOPSTVENU
     * LindaSocketClient konekciju u run() (fromSystemProperties() radi i
     * ovde jer worker/EvalProcessRunner postavlja iste sistemske propertije
     * i pomocnom eval() procesu).
     */
    private static class EchoRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                String[] template = new String[] { "greeting", null };
                linda.in(template);
                System.out.println("[EchoRunnable] procitao torku 'greeting' = " + template[1]);

                linda.out(new String[] { "eval-done", "1" });
                System.out.println("[EchoRunnable] ubacio potvrdnu torku 'eval-done'");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}