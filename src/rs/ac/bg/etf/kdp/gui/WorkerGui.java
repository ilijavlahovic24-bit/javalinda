package rs.ac.bg.etf.kdp.gui;

import javax.swing.*;
import java.awt.*;

import rs.ac.bg.etf.kdp.worker.WorkerDispatchServer;

/** Prost status prikaz - kapacitet/zauzetost. Worker MORA raditi i bez ovoga (--no-gui default). */
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

        Timer refreshTimer = new Timer(1000, e -> {
            int free = dispatchServer.getFreeSlots();
            statusLabel.setText("Slobodno: " + free + " / " + capacity);
        });
        refreshTimer.start();
    }
}