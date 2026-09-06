package rs.ac.bg.etf.kdp.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import rs.ac.bg.etf.kdp.protocol.WorkerRegistration;
import rs.ac.bg.etf.kdp.util.SimpleLogger;

/**
 * Thread-safe registar radnih stanica. Izbor stanice je round-robin
 * (order lista prati redosled prijave) - VISE NE PROVERAVA slobodan
 * kapacitet, stanica se uvek smatra dostupnom dok god je prijavljena
 * (videti WorkerDispatchServer - kapacitet vise nije ogranicavajuci
 * faktor na strani stanice).
 */
public class WorkerRegistry {

    private final Map<Integer, WorkerHandle> workers = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Integer> order = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextWorkerId = new AtomicInteger(1);
    private final AtomicInteger roundRobinCursor = new AtomicInteger(0);
    private final SimpleLogger logger;

    public WorkerRegistry(SimpleLogger logger) {
        this.logger = logger;
    }

    public WorkerHandle register(WorkerRegistration registration) {
        int workerId = nextWorkerId.getAndIncrement();
        WorkerHandle handle = new WorkerHandle(workerId, registration.getDispatchHost(),
                registration.getDispatchPort(), registration.getCapacity(),
                registration.getOs(), registration.getJavaVersion());
        workers.put(workerId, handle);
        order.add(workerId);
        logger.log("WorkerRegistry", "Stanica " + workerId + " prijavljena ("
                + registration.getOs() + ", Java " + registration.getJavaVersion()
                + "), kapacitet " + registration.getCapacity());
        return handle;
    }

    public void unregister(int workerId) {
        workers.remove(workerId);
        order.remove(Integer.valueOf(workerId));
        logger.log("WorkerRegistry", "Stanica " + workerId + " uklonjena");
    }

    public void heartbeat(int workerId, int freeSlots) {
        WorkerHandle handle = workers.get(workerId);
        if (handle != null) {
            handle.touch(freeSlots);
        }
    }

    public WorkerHandle get(int workerId) {
        return workers.get(workerId);
    }

    public Iterable<WorkerHandle> all() {
        return workers.values();
    }

    /**
     * Round-robin izbor - ciklicno prolazi kroz stanice po redosledu
     * prijave. Ne proverava slobodan kapacitet (stanica se uvek prihvata
     * kao dostupna dok god je prijavljena). Ako je neka stanica u
     * medjuvremenu uklonjena (race sa unregister()), preskace je i
     * probava sledecu, najvise onoliko puta koliko trenutno ima stanica.
     */
    public WorkerHandle nextWorker() {
        int size = order.size();
        if (size == 0) {
            return null;
        }
        for (int attempt = 0; attempt < size; attempt++) {
            int idx = Math.floorMod(roundRobinCursor.getAndIncrement(), size);
            if (idx >= order.size()) {
                continue; // velicina se promenila u medjuvremenu
            }
            Integer id = order.get(idx);
            WorkerHandle handle = workers.get(id);
            if (handle != null) {
                return handle;
            }
        }
        return null;
    }

    /** Isto kao nextWorker(), ali preskace datu stanicu (koristi HeartbeatMonitor pri redispatch-u). */
    public WorkerHandle nextWorkerExcept(int excludeWorkerId) {
        int size = order.size();
        if (size == 0) {
            return null;
        }
        for (int attempt = 0; attempt < size; attempt++) {
            int idx = Math.floorMod(roundRobinCursor.getAndIncrement(), size);
            if (idx >= order.size()) {
                continue;
            }
            Integer id = order.get(idx);
            if (id == excludeWorkerId) {
                continue;
            }
            WorkerHandle handle = workers.get(id);
            if (handle != null) {
                return handle;
            }
        }
        return null;
    }
}