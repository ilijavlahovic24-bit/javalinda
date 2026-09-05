package test;

import java.io.Serializable;

public class TestJob implements LindaJob {

    public TestJob() {
    }

    @Override
    public void run(Linda linda, String[] args) {
        System.out.println("[TestJob] pocinjem, args=" + java.util.Arrays.toString(args));

        linda.out(new String[] { "greeting", "hello-from-main" });
        System.out.println("[TestJob] ubacio torku 'greeting'");

        linda.eval("echo-eval", new EchoRunnable(linda));
        System.out.println("[TestJob] pozvao eval(), cekam potvrdu...");

        String[] confirmation = new String[] { "eval-done", null };
        linda.in(confirmation);
        System.out.println("[TestJob] eval potvrdjen, vrednost=" + confirmation[1]);

        System.out.println("[TestJob] gotovo");
    }

    private static class EchoRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;

        private final Linda linda;

        EchoRunnable(Linda linda) {
            this.linda = linda;
        }

        @Override
        public void run() {
            String[] template = new String[] { "greeting", null };
            linda.in(template);
            System.out.println("[EchoRunnable] procitao torku 'greeting' = " + template[1]
                    + " (izvrsava se na worker niti, moguce na drugoj stanici)");

            linda.out(new String[] { "eval-done", "1" });
            System.out.println("[EchoRunnable] ubacio potvrdnu torku 'eval-done'");
        }
    }
}