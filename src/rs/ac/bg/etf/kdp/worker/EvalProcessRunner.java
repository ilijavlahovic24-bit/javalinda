package rs.ac.bg.etf.kdp.worker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import rs.ac.bg.etf.kdp.protocol.EvalRequest;
import rs.ac.bg.etf.kdp.util.SimpleLogger;

/**
 * Obradjuje EvalRequest: zapisuje jar (ako je prosledjen) i serijalizovani
 * Runnable na disk, pokrece EvalRunnerMain u posebnom procesu sa jarom na
 * classpath-u. eval() je fire-and-forget (ne ceka zavrsetak procesa).
 */
public class EvalProcessRunner {

    private final String lindaLibJarPath;
    private final String tupleSpaceHost;
    private final int tupleSpacePort;
    private final SimpleLogger logger;

    public EvalProcessRunner(String lindaLibJarPath, String tupleSpaceHost, int tupleSpacePort,
                             SimpleLogger logger) {
        this.lindaLibJarPath = lindaLibJarPath;
        this.tupleSpaceHost = tupleSpaceHost;
        this.tupleSpacePort = tupleSpacePort;
        this.logger = logger;
    }

    public void run(EvalRequest request) throws IOException {
        File workDir = Files.createTempDirectory("kdp-eval-" + request.getJobSetId()).toFile();

        File runnableFile = new File(workDir, "runnable.bin");
        Files.write(runnableFile.toPath(), request.getRunnableBytes());

        String classpath = lindaLibJarPath;
        if (request.getJarBytes() != null) {
            File jarFile = new File(workDir, "job.jar");
            Files.write(jarFile.toPath(), request.getJarBytes());
            classpath = jarFile.getAbsolutePath() + File.pathSeparator + lindaLibJarPath;
        }

        List<String> command = new ArrayList<>();
        command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        command.add("-Dkdp.tuplespace.host=" + tupleSpaceHost);
        command.add("-Dkdp.tuplespace.port=" + tupleSpacePort);
        command.add("-Dkdp.jobSetId=" + request.getJobSetId());
        command.add("-cp");
        command.add(classpath);
        command.add("rs.ac.bg.etf.kdp.worker.EvalRunnerMain");
        command.add(runnableFile.getAbsolutePath());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.redirectOutput(new File(workDir, "eval.log"));
        builder.start(); // fire-and-forget - ne cekamo Process.waitFor()

        logger.log("EvalProcessRunner", "eval() zadatak '" + request.getName()
                + "' (job set " + request.getJobSetId() + ") pokrenut kao poseban proces");
    }
}