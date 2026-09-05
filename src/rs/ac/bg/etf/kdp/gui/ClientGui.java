package rs.ac.bg.etf.kdp.gui;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import rs.ac.bg.etf.kdp.net.MessageConnection;
import rs.ac.bg.etf.kdp.protocol.*;

/**
 * Forma za zadavanje posla + prikaz statusa + dijalog za failover odluku.
 * Mrezna komunikacija se radi u pozadinskoj niti (SwingWorker) da ne
 * blokira EDT dok konekcija ceka WorkerFailureNotice/JobFinished.
 */
public class ClientGui extends JFrame {

    private final JTextField hostField = new JTextField("localhost", 10);
    private final JTextField portField = new JTextField("6000", 6);
    private final JTextField jarField = new JTextField(20);
    private final JTextField mainClassField = new JTextField(20);
    private final JTextField outputFilesField = new JTextField("output.txt", 20);
    private final JTextField argsField = new JTextField(20);
    private final DefaultListModel<String> inputFilesModel = new DefaultListModel<>();
    private final Map<String, File> inputFilesByName = new HashMap<>();
    private final JTextArea logArea = new JTextArea(10, 50);
    private final JButton submitButton = new JButton("Posalji posao");

    public ClientGui() {
        super("KDP Klijent");
        setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        addRow(form, c, 0, "Server host:port", pack(hostField, portField));
        addRow(form, c, 1, "Jar posla", pack(jarField, browseButton(jarField, false)));
        addRow(form, c, 2, "Glavna klasa", mainClassField);
        addRow(form, c, 3, "Argumenti (razdvojeni razmakom)", argsField);
        addRow(form, c, 4, "Izlazni fajlovi (do 6, zarezom)", outputFilesField);

        JPanel inputPanel = new JPanel(new BorderLayout());
        JList<String> inputList = new JList<>(inputFilesModel);
        inputPanel.add(new JScrollPane(inputList), BorderLayout.CENTER);
        JButton addInputButton = new JButton("Dodaj ulazni fajl (max 6)");
        addInputButton.addActionListener(e -> addInputFile());
        inputPanel.add(addInputButton, BorderLayout.SOUTH);
        addRow(form, c, 5, "Ulazni fajlovi", inputPanel);

        submitButton.addActionListener(e -> submit());
        add(form, BorderLayout.NORTH);
        add(submitButton, BorderLayout.CENTER);

        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
    }

    private void addRow(JPanel form, GridBagConstraints c, int row, String label, Component field) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(field, c);
    }

    private JPanel pack(Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (Component comp : components) {
            panel.add(comp);
        }
        return panel;
    }

    private JButton browseButton(JTextField target, boolean directory) {
        JButton button = new JButton("...");
        button.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                target.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        return button;
    }

    private void addInputFile() {
        if (inputFilesModel.size() >= 6) {
            JOptionPane.showMessageDialog(this, "Najvise 6 ulaznih fajlova je dozvoljeno.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        String nameOnWorker = JOptionPane.showInputDialog(this,
                "Ime pod kojim ce fajl biti dostupan na radnoj stanici:", file.getName());
        if (nameOnWorker == null || nameOnWorker.isBlank()) {
            return;
        }
        inputFilesByName.put(nameOnWorker, file);
        inputFilesModel.addElement(nameOnWorker + " <- " + file.getAbsolutePath());
    }

    private void submit() {
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Neispravan port.");
            return;
        }
        String jarPath = jarField.getText().trim();
        String mainClassName = mainClassField.getText().trim();
        String[] outputFileNames = outputFilesField.getText().trim().split(",");
        String[] programArgs = argsField.getText().trim().isEmpty()
                ? new String[0] : argsField.getText().trim().split("\\s+");

        if (jarPath.isEmpty() || mainClassName.isEmpty() || outputFileNames.length == 0) {
            JOptionPane.showMessageDialog(this, "Jar, glavna klasa i bar jedan izlazni fajl su obavezni.");
            return;
        }
        if (outputFileNames.length > 6) {
            JOptionPane.showMessageDialog(this, "Najvise 6 izlaznih fajlova je dozvoljeno.");
            return;
        }

        submitButton.setEnabled(false);
        log("Saljem posao...");

        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                byte[] jarBytes = Files.readAllBytes(new File(jarPath).toPath());
                Map<String, byte[]> inputFiles = new HashMap<>();
                for (Map.Entry<String, File> entry : inputFilesByName.entrySet()) {
                    inputFiles.put(entry.getKey(), Files.readAllBytes(entry.getValue().toPath()));
                }

                JobSubmission submission = new JobSubmission(jarBytes, mainClassName, programArgs,
                        inputFiles, outputFileNames);

                try (MessageConnection connection = MessageConnection.connectTo(host, port)) {
                    connection.send(submission);

                    Object ackObj = connection.receive();
                    if (!(ackObj instanceof JobSubmissionAck)) {
                        publish("Neocekivan odgovor: " + ackObj);
                        return null;
                    }
                    JobSubmissionAck ack = (JobSubmissionAck) ackObj;
                    if (ack.getErrorMessage() != null) {
                        publish("Greska: " + ack.getErrorMessage());
                        return null;
                    }
                    long jobId = ack.getJobId();
                    publish("Posao poslat, jobId = " + jobId);

                    while (true) {
                        Object message = connection.receive();
                        if (message instanceof WorkerFailureNotice) {
                            WorkerFailureNotice notice = (WorkerFailureNotice) message;
                            FailoverAction action = askUserForDecisionOnEdt(notice);
                            connection.send(new WorkerFailureDecision(jobId, action));
                            publish("Odluka poslata: " + action);
                        } else if (message instanceof JobFinished) {
                            handleFinished((JobFinished) message);
                            break;
                        }
                    }
                }
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String chunk : chunks) {
                    log(chunk);
                }
            }

            @Override
            protected void done() {
                submitButton.setEnabled(true);
                try {
                    get();
                } catch (Exception e) {
                    log("Greska: " + e.getMessage());
                }
            }
        }.execute();
    }

    /** Poziva se iz pozadinske niti - mora blokirati dok korisnik ne odgovori na EDT-u. */
    private FailoverAction askUserForDecisionOnEdt(WorkerFailureNotice notice) throws Exception {
        final FailoverAction[] result = new FailoverAction[1];
        SwingUtilities.invokeAndWait(() -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Radna stanica " + notice.getFailedWorkerId()
                            + " je prestala da odgovara usred izvrsavanja posla "
                            + notice.getJobId() + ".\nProslediti drugoj slobodnoj stanici?",
                    "Pad radne stanice", JOptionPane.YES_NO_OPTION);
            result[0] = choice == JOptionPane.YES_OPTION
                    ? FailoverAction.RETRY_OTHER_WORKER : FailoverAction.ABORT;
        });
        return result[0];
    }

    private void handleFinished(JobFinished finished) throws Exception {
        log("Status: " + finished.getStatus());
        if (finished.getErrorMessage() != null) {
            log("Poruka: " + finished.getErrorMessage());
        }
        if (finished.getStatus() == JobStatus.DONE && finished.getOutputFiles() != null) {
            for (Map.Entry<String, byte[]> entry : finished.getOutputFiles().entrySet()) {
                Files.write(new File(entry.getKey()).toPath(), entry.getValue());
                log("Sacuvan izlazni fajl: " + entry.getKey());
            }
        }
    }

    private void log(String message) {
        logArea.append(message + "\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientGui().setVisible(true));
    }
}