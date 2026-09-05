package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;

public enum JobStatus implements Serializable {
    READY,
    SCHEDULED,
    RUNNING,
    DONE,
    FAILED,
    ABORTED
}