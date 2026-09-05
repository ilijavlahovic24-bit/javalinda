package rs.ac.bg.etf.kdp.worker;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;

import rs.ac.bg.etf.kdp.linda.EvalTask;

/**
 * Pokrece se kao poseban JVM proces sa classpath-om koji sadrzi jar posla.
 * Argumenti: <putanjaDoSerijalizovanogRunnable-a>
 * Sistemske propertije (kdp.tuplespace.host/port, kdp.jobSetId, kdp.jobJar)
 * su vec postavljene pri pokretanju procesa - ako Runnable unutar run()
 * napravi LindaSocketClient.fromSystemProperties(), automatski ce se
 * povezati na isti prostor torki.
 */
public class EvalRunnerMain {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Upotreba: EvalRunnerMain <putanjaDoRunnableBajtova>");
            System.exit(1);
        }
        byte[] runnableBytes = Files.readAllBytes(Paths.get(args[0]));
        Runnable runnable = EvalTask.deserialize(runnableBytes,
                EvalRunnerMain.class.getClassLoader());
        runnable.run();
    }
}