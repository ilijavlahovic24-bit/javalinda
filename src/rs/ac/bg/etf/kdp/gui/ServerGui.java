package rs.ac.bg.etf.kdp.gui;

import javax.swing.*;
import java.awt.*;
import javax.swing.table.DefaultTableModel;

import rs.ac.bg.etf.kdp.server.JobRecord;
import rs.ac.bg.etf.kdp.server.JobRegistry;
import rs.ac.bg.etf.kdp.server.WorkerHandle;
import rs.ac.bg.etf.kdp.server.WorkerRegistry;

/**
 * Read-only prikaz stanja servera - pollinguje JobRegistry/WorkerRegistry
 * preko Swing Timer-a (nema push mehanizma iz servera ka GUI-ju, dovoljno
 * za potrebe demonstracije).
 */
public class ServerGui extends JFrame {

    private final JobRegistry jobRegistry;
    private final WorkerRegistry workerRegistry;
    private final DefaultTableModel jobsModel;
    private final DefaultTableModel workersModel;

    public ServerGui(JobRegistry jobRegistry, WorkerRegistry workerRegistry) {
        super("KDP Server");
        this.jobRegistry = jobRegistry;
        this.workerRegistry = workerRegistry;

        jobsModel = new DefaultTableModel(
                new Object[] { "ID", "Klasa", "Status", "Stanica", "Poslat", "Zavrsen" }, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        workersModel = new DefaultTableModel(
                new Object[] { "ID", "OS", "Java", "Kapacitet", "Poslednji heartbeat" }, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        setLayout(new GridLayout(2, 1));
        add(wrapInPanel("Poslovi", new JTable(jobsModel)));
        add(wrapInPanel("Radne stanice", new JTable(workersModel)));

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(800, 600);

        Timer refreshTimer = new Timer(1000, e -> refresh());
        refreshTimer.start();
    }

    private JPanel wrapInPanel(String title, JTable table) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private void refresh() {
        jobsModel.setRowCount(0);
        for (JobRecord job : jobRegistry.all()) {
            jobsModel.addRow(new Object[] {
                    job.getJobId(), job.getMainClassName(), job.getStatus(),
                    job.getAssignedWorkerId() == null ? "-" : job.getAssignedWorkerId(),
                    new java.util.Date(job.getSubmittedAt()),
                    job.getFinishedAt() == 0 ? "-" : new java.util.Date(job.getFinishedAt())
            });
        }

        workersModel.setRowCount(0);
        for (WorkerHandle worker : workerRegistry.all()) {
            workersModel.addRow(new Object[] {
                    worker.getWorkerId(), worker.getOs(), worker.getJavaVersion(),
                    worker.getCapacity(), new java.util.Date(worker.getLastHeartbeatAt())
            });
        }
    }
}