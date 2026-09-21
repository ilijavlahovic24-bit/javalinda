package rs.ac.bg.etf.kdp.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import rs.ac.bg.etf.kdp.protocol.JobStatus;
import rs.ac.bg.etf.kdp.util.SimpleLogger;

public class JobRegistry {

    private final Map<Long, JobRecord> jobs = new ConcurrentHashMap<>();
    private final AtomicLong nextJobId = new AtomicLong(1);
    private final SimpleLogger logger;

    public JobRegistry(SimpleLogger logger) {
        this.logger = logger;
    }

    public JobRecord submit(byte[] jarBytes, String mainClassName, String[] programArgs,
                            Map<String, byte[]> inputFiles, String[] outputFileNames) {
        long jobId = nextJobId.getAndIncrement();
        JobRecord record = new JobRecord(jobId, jarBytes, mainClassName, programArgs,
                inputFiles, outputFileNames);
        jobs.put(jobId, record);
        logger.log("JobRegistry", "Posao " + jobId + " primljen (READY): " + mainClassName);
        return record;
    }

    public JobRecord get(long jobId) {
        return jobs.get(jobId);
    }

    public void updateStatus(long jobId, JobStatus status) {
        JobRecord record = jobs.get(jobId);
        if (record == null) {
            logger.log("JobRegistry", "Upozorenje: nepoznat jobId " + jobId
                    + " pri promeni statusa u " + status);
            return;
        }
        record.setStatus(status);
        logger.log("JobRegistry", "Posao " + jobId + " -> " + status
                + (record.getAssignedWorkerId() != null
                ? " (stanica " + record.getAssignedWorkerId() + ")"
                : ""));
    }

    public void assignWorker(long jobId, int workerId) {
        JobRecord record = jobs.get(jobId);
        if (record != null) {
            record.setAssignedWorkerId(workerId);
            updateStatus(jobId, JobStatus.SCHEDULED);
        }
    }

    public void fail(long jobId, String reason) {
        JobRecord record = jobs.get(jobId);
        if (record != null) {
            record.setFailureMessage(reason);
        }
        updateStatus(jobId, JobStatus.FAILED);
        logger.log("JobRegistry", "Posao " + jobId + " FAILED: " + reason);
    }

    public void completeWithResult(long jobId, Map<String, byte[]> outputFiles) {
        JobRecord record = jobs.get(jobId);
        if (record != null) {
            record.setOutputFiles(outputFiles);
        }
        updateStatus(jobId, JobStatus.DONE);
    }

    public Iterable<JobRecord> byAssignedWorker(int workerId) {
        java.util.List<JobRecord> result = new java.util.ArrayList<>();
        for (JobRecord record : jobs.values()) {
            if (workerId == (record.getAssignedWorkerId() == null ? -1 : record.getAssignedWorkerId())
                    && (record.getStatus() == JobStatus.RUNNING
                    || record.getStatus() == JobStatus.SCHEDULED)) {
                result.add(record);
            }
        }
        return result;
    }

    public Iterable<JobRecord> all() {
        return jobs.values();
    }

    /** Poziva EvalDispatcherImpl kad uspesno posalje eval() zadatak radnoj stanici. */
    public void evalDispatched(long jobId) {
        JobRecord record = jobs.get(jobId);
        if (record != null) {
            int n = record.incrementPendingEval();
            logger.log("JobRegistry", "Posao " + jobId + " - eval() zadatak zakazan, na cekanju: " + n);
        }
    }

    /**
     * Poziva WorkerRegistrationServer kad stigne EvalStatusReport. Ako je
     * glavni proces vec zavrsio A ovo je bio poslednji preostali eval() -
     * vraca "spreman" JobFinished dogadjaj da se konacno posalje klijentu;
     * inace vraca null (jos se ceka).
     */
    public rs.ac.bg.etf.kdp.protocol.JobFinished evalFinished(long jobId, String taskName,
                                                              boolean success, String errorMessage, boolean timedOut) {
        JobRecord record = jobs.get(jobId);
        if (record == null) {
            return null;
        }
        int remaining = record.decrementPendingEval();
        String outcome = timedOut ? "TIMEOUT (moguca mrtva blokada)" : (success ? "OK" : "GRESKA: " + errorMessage);
        logger.log("JobRegistry", "Posao " + jobId + " - eval() '" + taskName + "' zavrsen (" + outcome
                + "), jos na cekanju: " + remaining);

        if (remaining == 0 && record.isMainProcessDone()) {
            return record.getPendingFinishEvent();
        }
        return null;
    }

    /**
     * Poziva se kad glavni proces javi rezultat. Ako ima jos eval() procesa
     * na cekanju, dogadjaj se CUVA i NE salje odmah - vraca false (pozivalac
     * ne sme jos da javi klijentu). Ako nema cekajucih, vraca true (moze
     * odmah da se posalje).
     */
    public boolean markMainDoneAndCheckReady(long jobId, rs.ac.bg.etf.kdp.protocol.JobFinished finishEvent) {
        JobRecord record = jobs.get(jobId);
        if (record == null) {
            return true;
        }
        record.markMainProcessDone(finishEvent);
        int pending = record.getPendingEvalCount();
        if (pending > 0) {
            logger.log("JobRegistry", "Posao " + jobId + " - glavni proces zavrsen, ali ceka se jos "
                    + pending + " eval() pod-procesa pre nego sto se javi klijentu");
            return false;
        }
        return true;
    }
}