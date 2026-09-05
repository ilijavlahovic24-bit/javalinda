package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;

public class Heartbeat implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int workerId;
    private final int freeSlots;

    public Heartbeat(int workerId, int freeSlots) {
        this.workerId = workerId;
        this.freeSlots = freeSlots;
    }

    public int getWorkerId() {
        return workerId;
    }

    public int getFreeSlots() {
        return freeSlots;
    }
}