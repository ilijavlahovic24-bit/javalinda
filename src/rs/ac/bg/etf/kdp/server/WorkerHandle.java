package rs.ac.bg.etf.kdp.server;

/** Stanje jedne prijavljene radne stanice, kako ga vidi server. */
public class WorkerHandle {

    private final int workerId;
    private final String dispatchHost;
    private final int dispatchPort;
    private final int capacity;
    private final String os;
    private final String javaVersion;
    private volatile int lastReportedFreeSlots;
    private volatile long lastHeartbeatAt;

    public WorkerHandle(int workerId, String dispatchHost, int dispatchPort, int capacity,
                        String os, String javaVersion) {
        this.workerId = workerId;
        this.dispatchHost = dispatchHost;
        this.dispatchPort = dispatchPort;
        this.capacity = capacity;
        this.os = os;
        this.javaVersion = javaVersion;
        this.lastReportedFreeSlots = capacity;
        this.lastHeartbeatAt = System.currentTimeMillis();
    }

    public int getWorkerId() {
        return workerId;
    }

    public String getDispatchHost() {
        return dispatchHost;
    }

    public int getDispatchPort() {
        return dispatchPort;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getOs() {
        return os;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public int getLastReportedFreeSlots() {
        return lastReportedFreeSlots;
    }

    public long getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void touch(int freeSlots) {
        this.lastReportedFreeSlots = freeSlots;
        this.lastHeartbeatAt = System.currentTimeMillis();
    }
}