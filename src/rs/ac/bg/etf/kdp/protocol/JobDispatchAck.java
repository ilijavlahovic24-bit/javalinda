package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;

public class JobDispatchAck implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean accepted;
    private final String errorMessage;

    public JobDispatchAck(boolean accepted, String errorMessage) {
        this.accepted = accepted;
        this.errorMessage = errorMessage;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}