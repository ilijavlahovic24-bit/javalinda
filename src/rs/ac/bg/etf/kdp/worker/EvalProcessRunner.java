package rs.ac.bg.etf.kdp.worker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import rs.ac.bg.etf.kdp.protocol.EvalRequest;
import rs.ac.bg.etf.kdp.protocol.EvalStatusReport;
import rs.ac.bg.etf.kdp.util.SimpleLogger;

/**
 * Pokrece eval() zadatak kao poseban proces i SADA GA PRATI (za razliku od
 * ranije verzije koja je bila cisto fire-and-forget bez ikakvog nadzora).
 * Ako proces ne zavrsi u okviru timeoutMs, tretira se kao verovatna mrtva
 * blokada - prisilno se prekida (Process.destroyForcibly()) i prijavljuje
 * kao neuspeh sa timedOut=true. Rezultat (uspeh/neuspeh/timeout) se salje
 * nazad serveru preko EvalResultReporter callback-a.
 */
public class EvalProcessRunner {

    public interface EvalResultReporter {
        void report(EvalStatusReport report);
    }

    private static final long DEFAULT_TIMEOUT_MS = 30_000; // podesivo po potrebi

    private final String lindaLibJarPath;
    private final String tupleSpaceHost;
    private final int tupleSpacePort;
    private final SimpleLogger logger;
    private final long timeoutMs;
    private final ExecutorService monitorPool = Executors.newCachedThreadPool();
    private EvalResultReporter resultReporter;

    public EvalProcessRunner(String lindaLibJarPath, String tupleSpaceHost, int tupleSpacePort,
                             SimpleLogger logger) {
        this(lindaLibJarPath, tupleSpaceHost, tupleSpacePort, logger, DEFAULT_TIMEOUT_MS);
    }

    public EvalProcessRunner(String lindaLibJarPath, String tupleSpaceHost, int tupleSpacePort,
                             SimpleLogger logger, long timeoutMs) {
        this.lindaLibJarPath = lindaLibJarPath;
        this.tupleSpaceHost = tupleSpaceHost;
        this.tupleSpacePort = tupleSpacePort;
        this.logger = logger;
        this.timeoutMs = timeoutMs;
    }

    public void setResultReporter(EvalResultReporter resultReporter) {
        this.resultReporter = resultReporter;
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
        Process process = builder.start();

        logger.log("EvalProcessRunner", "eval() zadatak '" + request.getName()
                + "' (job set " + request.getJobSetId() + ") pokrenut, prati se sa timeout-om "
                + timeoutMs + "ms");

        // NOVO: pracenje procesa sa timeout-om u posebnoj niti - ne blokira
        // handleEvalRequest() koji vec vraca EvalAck odmah (eval() ostaje
        // fire-and-forget iz perspektive POZIVAOCA, ali sad IMAMO nadzor)
        monitorPool.submit(() -> monitorProcess(process, request));
    }

    private void monitorProcess(Process process, EvalRequest request) {
        try {
            boolean finishedInTime = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            EvalStatusReport report;

            if (!finishedInTime) {
                process.destroyForcibly();
                logger.log("EvalProcessRunner", "eval() zadatak '" + request.getName()
                        + "' (job set " + request.getJobSetId() + ") NIJE zavrsio u "
                        + timeoutMs + "ms - MOGUCA MRTVA BLOKADA, prisilno prekinut");
                report = new EvalStatusReport(request.getJobSetId(), request.getName(),
                        false, "Proces prisilno prekinut posle " + timeoutMs
                        + "ms - verovatna mrtva blokada (deadlock)", true);
            } else {
                int exitCode = process.exitValue();
                boolean success = exitCode == 0;
                logger.log("EvalProcessRunner", "eval() zadatak '" + request.getName()
                        + "' (job set " + request.getJobSetId() + ") zavrsen, exit kod " + exitCode);
                report = new EvalStatusReport(request.getJobSetId(), request.getName(),
                        success, success ? null : "Proces zavrsio sa exit kodom " + exitCode, false);
            }

            if (resultReporter != null) {
                resultReporter.report(report);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}