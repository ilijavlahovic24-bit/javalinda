package rs.ac.bg.etf.kdp.gui;

import javax.swing.*;
import java.awt.*;

import rs.ac.bg.etf.kdp.worker.WorkerDispatchServer;

/** Prost status prikaz. Worker MORA raditi i bez ovoga (--no-gui default). */
public class WorkerGui extends JFrame {

    public WorkerGui(WorkerDispatchServer dispatchServer, int capacity, int workerId) {
        super("KDP Radna stanica #" + workerId);

        JLabel statusLabel = new JLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(18f));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        setLayout(new BorderLayout());
        add(statusLabel, BorderLayout.CENTER);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(400, 150);

        // NOVO: prikazuje broj aktivnih poslova umesto "slobodno/kapacitet"
        // - kapacitet vise ne ogranicava prijem poslova, pa "slobodno mesto"
        // vise nije smisleno kao koncept
        Timer refreshTimer = new Timer(1000, e -> {
            int active = dispatchServer.getActiveJobs();
            statusLabel.setText("Aktivnih poslova: " + active);
        });
        refreshTimer.start();
    }
}