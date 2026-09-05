package rs.ac.bg.etf.kdp.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import rs.ac.bg.etf.kdp.net.MessageConnection;
import rs.ac.bg.etf.kdp.protocol.FailoverAction;
import rs.ac.bg.etf.kdp.protocol.JobFinished;
import rs.ac.bg.etf.kdp.protocol.JobStatus;
import rs.ac.bg.etf.kdp.protocol.JobStatusRequest;
import rs.ac.bg.etf.kdp.protocol.JobStatusResponse;
import rs.ac.bg.etf.kdp.protocol.JobSubmission;
import rs.ac.bg.etf.kdp.protocol.JobSubmissionAck;
import rs.ac.bg.etf.kdp.protocol.WorkerFailureDecision;
import rs.ac.bg.etf.kdp.protocol.WorkerFailureNotice;

public class ClientMain {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        switch (args[0]) {
            case "submit":
                submitFromArgs(args);
                break;
            case "submit-file":
                submitFromFile(args);
                break;
            case "status":
                status(args);
                break;
            default:
                printUsage();
                System.exit(1);
        }
    }

    private static void submitFromArgs(String[] args) throws Exception {
        if (args.length < 6) {
            printUsage();
            System.exit(1);
            return;
        }
        String serverHost = args[1];
        int serverPort = Integer.parseInt(args[2]);
        String jarPath = args[3];
        String mainClassName = args[4];
        String[] outputFileNames = args[5].split(",");

        if (outputFileNames.length > 6) {
            System.err.println("Najvise 6 izlaznih fajlova je dozvoljeno.");
            System.exit(1);
        }

        Map<String, byte[]> inputFiles = new HashMap<>();
        java.util.List<String> programArgs = new java.util.ArrayList<>();

        int i = 6;
        while (i < args.length && "--in".equals(args[i])) {
            String[] pair = args[i + 1].split("=", 2);
            if (inputFiles.size() >= 6) {
                System.err.println("Najvise 6 ulaznih fajlova je dozvoljeno.");
                System.exit(1);
            }
            inputFiles.put(pair[0], Files.readAllBytes(Paths.get(pair[1])));
            i += 2;
        }
        if (i < args.length && "--".equals(args[i])) {
            i++;
            while (i < args.length) {
                programArgs.add(args[i]);
                i++;
            }
        }

        byte[] jarBytes = Files.readAllBytes(Paths.get(jarPath));
        submitAndWait(serverHost, serverPort, jarBytes, mainClassName,
                programArgs.toArray(new String[0]), inputFiles, outputFileNames);
    }

    private static void submitFromFile(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Upotreba: submit-file <host> <port> <konfiguracionaDatoteka>");
            System.exit(1);
            return;
        }
        String serverHost = args[1];
        int serverPort = Integer.parseInt(args[2]);
        Path configPath = Paths.get(args[3]);

        JobConfigFile config = JobConfigFile.parse(configPath);

        byte[] jarBytes = Files.readAllBytes(Paths.get(config.getJarPath()));
        Map<String, byte[]> inputFiles = new HashMap<>();
        for (Map.Entry<String, String> entry : config.getInputFiles().entrySet()) {
            inputFiles.put(entry.getKey(), Files.readAllBytes(Paths.get(entry.getValue())));
        }

        submitAndWait(serverHost, serverPort, jarBytes, config.getMainClassName(),
                config.getProgramArgs(), inputFiles, config.getOutputFileNames());
    }

    /** Deljena logika za oba nacina unosa parametara - koristi je i JobConfigFile i rucni argumenti. */
    static void submitAndWait(String serverHost, int serverPort, byte[] jarBytes,
                              String mainClassName, String[] programArgs, Map<String, byte[]> inputFiles,
                              String[] outputFileNames) throws Exception {
        JobSubmission submission = new JobSubmission(jarBytes, mainClassName, programArgs,
                inputFiles, outputFileNames);

        try (MessageConnection connection = MessageConnection.connectTo(serverHost, serverPort)) {
            connection.send(submission);

            Object ackObj = connection.receive();
            if (!(ackObj instanceof JobSubmissionAck)) {
                System.err.println("Neocekivan odgovor: " + ackObj);
                return;
            }
            JobSubmissionAck ack = (JobSubmissionAck) ackObj;
            if (ack.getErrorMessage() != null) {
                System.err.println("Greska: " + ack.getErrorMessage());
                return;
            }
            long jobId = ack.getJobId();
            System.out.println("Posao poslat, jobId = " + jobId
                    + " (ova konekcija ostaje otvorena dok posao ne zavrsi)");

            while (true) {
                Object message = connection.receive();
                if (message instanceof WorkerFailureNotice) {
                    WorkerFailureNotice notice = (WorkerFailureNotice) message;
                    FailoverAction action = askUserForDecision(notice);
                    connection.send(new WorkerFailureDecision(jobId, action));
                    System.out.println("Odluka poslata: " + action);
                } else if (message instanceof JobFinished) {
                    handleFinished((JobFinished) message);
                    break;
                } else {
                    System.err.println("Neocekivana poruka: " + message);
                }
            }
        }
    }

    private static FailoverAction askUserForDecision(WorkerFailureNotice notice) {
        System.out.println();
        System.out.println("!!! Radna stanica " + notice.getFailedWorkerId()
                + " je prestala da odgovara usred izvrsavanja posla " + notice.getJobId() + " !!!");
        System.out.print("Prosledi drugoj stanici (p) ili prekini posao (x)? ");
        Scanner scanner = new Scanner(System.in);
        String answer = scanner.nextLine().trim().toLowerCase();
        return "p".equals(answer) ? FailoverAction.RETRY_OTHER_WORKER : FailoverAction.ABORT;
    }

    private static void handleFinished(JobFinished finished) throws Exception {
        System.out.println("Status: " + finished.getStatus());
        if (finished.getErrorMessage() != null) {
            System.out.println("Poruka: " + finished.getErrorMessage());
        }
        if (finished.getStatus() == JobStatus.DONE && finished.getOutputFiles() != null) {
            for (Map.Entry<String, byte[]> entry : finished.getOutputFiles().entrySet()) {
                Files.write(Paths.get(entry.getKey()), entry.getValue());
                System.out.println("Sacuvan izlazni fajl: " + entry.getKey());
            }
        }
    }

    private static void status(String[] args) throws Exception {
        if (args.length < 4) {
            printUsage();
            System.exit(1);
            return;
        }
        String serverHost = args[1];
        int serverPort = Integer.parseInt(args[2]);
        long jobId = Long.parseLong(args[3]);

        try (MessageConnection connection = MessageConnection.connectTo(serverHost, serverPort)) {
            connection.send(new JobStatusRequest(jobId));
            Object response = connection.receive();
            if (!(response instanceof JobStatusResponse)) {
                System.err.println("Neocekivan odgovor: " + response);
                return;
            }
            JobStatusResponse status = (JobStatusResponse) response;
            System.out.println("Status: " + status.getStatus());
            if (status.getErrorMessage() != null) {
                System.out.println("Poruka: " + status.getErrorMessage());
            }
            if (status.getStatus() == JobStatus.DONE && status.getOutputFiles() != null) {
                for (Map.Entry<String, byte[]> entry : status.getOutputFiles().entrySet()) {
                    Files.write(Paths.get(entry.getKey()), entry.getValue());
                    System.out.println("Sacuvan izlazni fajl: " + entry.getKey());
                }
            }
        }
    }

    private static void printUsage() {
        System.err.println("Upotreba:");
        System.err.println("  submit <host> <port> <jar> <mainClass> <out1[,out2,...]> [--in ime=putanja ...] [-- args...]");
        System.err.println("  submit-file <host> <port> <konfiguracionaDatoteka>");
        System.err.println("  status <host> <port> <jobId>");
    }
}