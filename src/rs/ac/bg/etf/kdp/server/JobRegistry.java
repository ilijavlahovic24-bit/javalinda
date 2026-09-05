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
}