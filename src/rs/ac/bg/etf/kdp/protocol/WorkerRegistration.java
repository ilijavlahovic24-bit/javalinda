package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;

/**
 * Radna stanica -> Server, prva poruka na registracionoj konekciji.
 * dispatchHost/dispatchPort su adresa na kojoj OVA stanica sluša za
 * EvalRequest/JobDispatch pozive od servera (server se konektuje NA NJU
 * kad treba da joj nesto posalje - videti napomenu u development-plan-
 * linda.md o smeru konekcija u socket modelu).
 */
public class WorkerRegistration implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int capacity;
    private final String dispatchHost;
    private final int dispatchPort;
    private final String os;
    private final String javaVersion;

    public WorkerRegistration(int capacity, String dispatchHost, int dispatchPort,
                              String os, String javaVersion) {
        this.capacity = capacity;
        this.dispatchHost = dispatchHost;
        this.dispatchPort = dispatchPort;
        this.os = os;
        this.javaVersion = javaVersion;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getDispatchHost() {
        return dispatchHost;
    }

    public int getDispatchPort() {
        return dispatchPort;
    }

    public String getOs() {
        return os;
    }

    public String getJavaVersion() {
        return javaVersion;
    }
}