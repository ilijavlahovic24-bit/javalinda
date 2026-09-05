package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;

public enum FailoverAction implements Serializable {
    RETRY_OTHER_WORKER,
    ABORT
}