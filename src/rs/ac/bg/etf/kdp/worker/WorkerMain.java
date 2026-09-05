package rs.ac.bg.etf.kdp.worker;

import rs.ac.bg.etf.kdp.net.MessageConnection;
import rs.ac.bg.etf.kdp.protocol.Heartbeat;
import rs.ac.bg.etf.kdp.protocol.HeartbeatAck;
import rs.ac.bg.etf.kdp.protocol.JobResultAck;
import rs.ac.bg.etf.kdp.protocol.WorkerRegisterAck;
import rs.ac.bg.etf.kdp.protocol.WorkerRegistration;
import rs.ac.bg.etf.kdp.util.SimpleLogger;

/**
 * Argumenti: [serverHost] [serverRegistrationPort] [tupleSpaceHost]
 *            [tupleSpacePort] [dispatchPort] [capacity] [lindaLibJarPath]
 *            [ownHost] [--gui]
 * Podrazumevano: localhost 6000 localhost 6001 0(nasumican) 4 linda-lib.jar
 *                (auto-detektovana adresa)
 */
public class WorkerMain {

    public static void main(String[] args) throws Exception {
        String serverHost = args.length > 0 ? args[0] : "localhost";
        int registrationPort = args.length > 1 ? Integer.parseInt(args[1]) : 6000;
        String tupleSpaceHost = args.length > 2 ? args[2] : "localhost";
        int tupleSpacePort = args.length > 3 ? Integer.parseInt(args[3]) : 6001;
        int dispatchPort = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        int capacity = args.length > 5 ? Integer.parseInt(args[5]) : 4;
        String lindaLibJarPath = args.length > 6 ? args[6] : "linda-lib.jar";
        lindaLibJarPath = new java.io.File(lindaLibJarPath).getAbsolutePath();

        // sopstvena adresa koju server treba da koristi da se poveze nazad ka ovoj stanici (JobDispatch/EvalRequest).
        // Ako se ne zada eksplicitno, pokusava auto-detekciju - na masinama sa vise mreznih interfejsa auto-detekcija
        // moze pogoditi pogresan interfejs, pa je na vise racunara uvek bolje zadati rucno
        String ownHost = args.length > 7 ? args[7]
                : java.net.InetAddress.getLocalHost().getHostAddress();

        SimpleLogger logger = new SimpleLogger();

        JobProcessRunner jobRunner = new JobProcessRunner(lindaLibJarPath, logger);
        EvalProcessRunner evalRunner =
                new EvalProcessRunner(lindaLibJarPath, tupleSpaceHost, tupleSpacePort, logger);

        // pravi spisak izlaznih fajlova dolazi iz
        // JobDispatch-a, ne fiksno ovde - videti napomenu u WorkerDispatchServer

        //String[] expectedOutputFiles = { "output.txt" };

        WorkerDispatchServer dispatchServer =
                new WorkerDispatchServer(dispatchPort, capacity, jobRunner, evalRunner, logger);
        dispatchServer.start();
        int actualDispatchPort = waitForPort(dispatchServer);

        MessageConnection registrationConnection =
                MessageConnection.connectTo(serverHost, registrationPort);
        registrationConnection.send(new WorkerRegistration(capacity, ownHost,
                actualDispatchPort, System.getProperty("os.name"),
                System.getProperty("java.version")));

        Object ackObj = registrationConnection.receive();
        if (!(ackObj instanceof WorkerRegisterAck)) {
            throw new IllegalStateException("Server nije potvrdio registraciju: " + ackObj);
        }
        int workerId = ((WorkerRegisterAck) ackObj).getWorkerId();
        logger.log("WorkerMain", "Prijavljen kao stanica " + workerId
                + ", dispatch port " + actualDispatchPort);

        dispatchServer.setResultReporter(report -> {
            try (MessageConnection resultConnection =
                         MessageConnection.connectTo(serverHost, registrationPort)) {
                resultConnection.send(report);
                resultConnection.receive(); // JobResultAck, ignorisemo sadrzaj
            } catch (Exception e) {
                logger.log("WorkerMain", "Slanje rezultata posla " + report.getJobId()
                        + " serveru nije uspelo: " + e);
            }
        });

        boolean gui = java.util.Arrays.asList(args).contains("--gui");
        if (gui) {
            int finalWorkerId = workerId;
            javax.swing.SwingUtilities.invokeLater(() -> {
                rs.ac.bg.etf.kdp.gui.WorkerGui workerGui =
                        new rs.ac.bg.etf.kdp.gui.WorkerGui(dispatchServer, capacity, finalWorkerId);
                workerGui.setVisible(true);
            });
        }

        Thread heartbeatThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2000);
                    registrationConnection.send(new Heartbeat(workerId, dispatchServer.getFreeSlots()));
                    Object response = registrationConnection.receive();
                    if (!(response instanceof HeartbeatAck)) {
                        logger.log("WorkerMain", "Neocekivan odgovor na heartbeat: " + response);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    logger.log("WorkerMain", "Heartbeat nije uspeo: " + e);
                    return;
                }
            }
        }, "heartbeat-sender");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            registrationConnection.close();
            dispatchServer.stop();
        }));

        Thread.currentThread().join();
    }

    private static int waitForPort(WorkerDispatchServer server) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            try {
                return server.getPort();
            } catch (IllegalStateException e) {
                Thread.sleep(20);
            }
        }
        throw new IllegalStateException("Dispatch server se nije pokrenuo na vreme");
    }
}