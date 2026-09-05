package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;

/** Server -> pozivalac: potvrda da je eval() zakazan (fire-and-forget, ne ceka zavrsetak). */
public class EvalAck implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean scheduled;
    private final String errorMessage; // popunjeno ako scheduled == false

    public EvalAck(boolean scheduled, String errorMessage) {
        this.scheduled = scheduled;
        this.errorMessage = errorMessage;
    }

    public boolean isScheduled() {
        return scheduled;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}