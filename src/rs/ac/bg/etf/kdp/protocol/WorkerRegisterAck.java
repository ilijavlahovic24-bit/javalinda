package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;

public class WorkerRegisterAck implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int workerId;

    public WorkerRegisterAck(int workerId) {
        this.workerId = workerId;
    }

    public int getWorkerId() {
        return workerId;
    }
}