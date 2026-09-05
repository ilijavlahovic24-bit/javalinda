package rs.ac.bg.etf.kdp.worker;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rs.ac.bg.etf.kdp.protocol.JobDispatch;
import rs.ac.bg.etf.kdp.protocol.JobResultReport;
import rs.ac.bg.etf.kdp.util.SimpleLogger;

/**
 * Izvrsava glavni posao (JobDispatch) kao poseban JVM proces. Za razliku
 * od EvalProcessRunner-a, OVDE SE CEKA zavrsetak procesa (Process.waitFor())
 * jer moramo da prikupimo izlazne fajlove i prijavimo rezultat serveru.
 */
public class JobProcessRunner {

    private final String lindaLibJarPath;
    private final SimpleLogger logger;

    public JobProcessRunner(String lindaLibJarPath, SimpleLogger logger) {
        this.lindaLibJarPath = lindaLibJarPath;
        this.logger = logger;
    }

    /** @param expectedOutputFiles imena fajlova koje treba pokupiti sa diska posle zavrsetka procesa */
    public JobResultReport run(JobDispatch dispatch, String[] expectedOutputFiles) {
        File workDir = null;
        try {
            workDir = Files.createTempDirectory("kdp-job-" + dispatch.getJobId()).toFile();

            File jarFile = new File(workDir, "job.jar");
            Files.write(jarFile.toPath(), dispatch.getJarBytes());

            for (Map.Entry<String, byte[]> entry : dispatch.getInputFiles().entrySet()) {
                Files.write(new File(workDir, entry.getKey()).toPath(), entry.getValue());
            }

            String classpath = jarFile.getAbsolutePath() + File.pathSeparator + lindaLibJarPath;

            List<String> command = new ArrayList<>();
            command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
            command.add("-Dkdp.tuplespace.host=" + dispatch.getTupleSpaceHost());
            command.add("-Dkdp.tuplespace.port=" + dispatch.getTupleSpacePort());
            command.add("-Dkdp.jobSetId=" + dispatch.getJobId());
            command.add("-Dkdp.jobJar=" + jarFile.getAbsolutePath());
            command.add("-cp");
            command.add(classpath);
            command.add(dispatch.getMainClassName());
            for (String arg : dispatch.getProgramArgs()) {
                command.add(arg);
            }

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workDir);
            builder.redirectErrorStream(true);
            builder.redirectOutput(new File(workDir, "stdout.log"));

            logger.log("JobProcessRunner", "Pokrecem posao " + dispatch.getJobId()
                    + " (" + dispatch.getMainClassName() + ")");

            Process process = builder.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                return new JobResultReport(dispatch.getJobId(), false,
                        "Proces zavrsio sa exit kodom " + exitCode, null);
            }

            Map<String, byte[]> outputFiles = new HashMap<>();
            for (String fileName : expectedOutputFiles) {
                File outputFile = new File(workDir, fileName);
                if (outputFile.exists()) {
                    outputFiles.put(fileName, Files.readAllBytes(outputFile.toPath()));
                } else {
                    logger.log("JobProcessRunner", "Upozorenje: ocekivan izlazni fajl '"
                            + fileName + "' ne postoji za posao " + dispatch.getJobId());
                }
            }

            logger.log("JobProcessRunner", "Posao " + dispatch.getJobId() + " zavrsen (DONE)");
            return new JobResultReport(dispatch.getJobId(), true, null, outputFiles);

        } catch (Exception e) {
            logger.log("JobProcessRunner", "Posao " + dispatch.getJobId() + " FAILED: " + e);
            return new JobResultReport(dispatch.getJobId(), false, String.valueOf(e.getMessage()), null);
        } finally {

        }
    }
}