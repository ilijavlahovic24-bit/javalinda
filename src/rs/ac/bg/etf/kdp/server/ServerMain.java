package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.gui.ServerGui;
import rs.ac.bg.etf.kdp.util.SimpleLogger;

public class ServerMain {

    public static void main(String[] args) throws Exception {
        int registrationPort = args.length > 0 ? Integer.parseInt(args[0]) : 6000;
        int tupleSpacePort = args.length > 1 ? Integer.parseInt(args[1]) : 6001;
        long heartbeatIntervalMs = args.length > 2 ? Long.parseLong(args[2]) : 3000L;
        String tupleSpaceHost = args.length > 3 ? args[3] : "localhost";
        boolean gui = java.util.Arrays.asList(args).contains("--gui");

        SimpleLogger logger = new SimpleLogger();
        JobRegistry jobRegistry = new JobRegistry(logger);
        WorkerRegistry workerRegistry = new WorkerRegistry(logger);
        ClientChannelRegistry channelRegistry = new ClientChannelRegistry();

        WorkerRegistrationServer registrationServer = new WorkerRegistrationServer(
                registrationPort, workerRegistry, jobRegistry, channelRegistry,
                tupleSpaceHost, tupleSpacePort, logger);
        registrationServer.start();

        EvalDispatcherImpl evalDispatcher = new EvalDispatcherImpl(workerRegistry, logger);
        TupleSpaceServer tupleSpaceServer =
                new TupleSpaceServer(tupleSpacePort, logger, evalDispatcher);
        tupleSpaceServer.start();

        HeartbeatMonitor heartbeatMonitor = new HeartbeatMonitor(workerRegistry, jobRegistry,
                channelRegistry, tupleSpaceHost, tupleSpacePort, logger, heartbeatIntervalMs);
        Thread heartbeatThread = new Thread(heartbeatMonitor, "heartbeat-monitor");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();

        logger.log("ServerMain", "Registracija na portu " + registrationPort
                + ", prostor torki na portu " + tupleSpacePort
                + ", heartbeat interval " + heartbeatIntervalMs + "ms");

        if (gui) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                ServerGui serverGui = new ServerGui(jobRegistry, workerRegistry);
                serverGui.setVisible(true);
            });
        }

        Thread.currentThread().join();
    }
}