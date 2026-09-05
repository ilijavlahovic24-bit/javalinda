package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;
import java.util.Map;

/** Server -> Klijent (na OTVORENOJ konekciji posla): terminalna poruka - posle nje se konekcija zatvara. */
public class JobFinished implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long jobId;
    private final JobStatus status; // DONE, FAILED, ili ABORTED
    private final String errorMessage;
    private final Map<String, byte[]> outputFiles; // popunjeno samo za DONE

    public JobFinished(long jobId, JobStatus status, String errorMessage,
                       Map<String, byte[]> outputFiles) {
        this.jobId = jobId;
        this.status = status;
        this.errorMessage = errorMessage;
        this.outputFiles = outputFiles;
    }

    public long getJobId() {
        return jobId;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Map<String, byte[]> getOutputFiles() {
        return outputFiles;
    }
}