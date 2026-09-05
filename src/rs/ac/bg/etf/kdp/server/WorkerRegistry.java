package rs.ac.bg.etf.kdp.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import rs.ac.bg.etf.kdp.protocol.WorkerRegistration;
import rs.ac.bg.etf.kdp.util.SimpleLogger;

/**
 * Thread-safe registar radnih stanica.
 * "Slobodan kapacitet" se ovde NE proverava uzivo pozivom ka stanici- koristi se poslednja vrednost prijavljena
 * kroz heartbeat (lastReportedFreeSlots).
 * To znaci da findFreeWorker()moze povremeno birati stanicu koja je u medjuvremenu postala zauzeta -
 * prihvatljivo za sada, otvoreno pitanje ako se pokaze problematicnim.
 */
public class WorkerRegistry {

    private final Map<Integer, WorkerHandle> workers = new ConcurrentHashMap<>();
    private final AtomicInteger nextWorkerId = new AtomicInteger(1);
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
        logger.log("WorkerRegistry", "Stanica " + workerId + " prijavljena ("
                + registration.getOs() + ", Java " + registration.getJavaVersion()
                + "), kapacitet " + registration.getCapacity());
        return handle;
    }

    public void unregister(int workerId) {
        workers.remove(workerId);
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

    public WorkerHandle findFreeWorker() {
        for (WorkerHandle handle : workers.values()) {
            if (handle.getLastReportedFreeSlots() > 0) {
                return handle;
            }
        }
        return null;
    }

    /** Isto kao findFreeWorker() ali izbegava datu stanicu - koristi EvalDispatcherImpl da ne saljemo eval nazad istoj stanici bez razloga (nije obavezno, samo bolja raspodela). */
    public WorkerHandle findFreeWorkerExcept(int excludeWorkerId) {
        for (WorkerHandle handle : workers.values()) {
            if (handle.getWorkerId() != excludeWorkerId && handle.getLastReportedFreeSlots() > 0) {
                return handle;
            }
        }
        return findFreeWorker();
    }
}