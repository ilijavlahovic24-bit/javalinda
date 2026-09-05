package rs.ac.bg.etf.kdp.server;

import java.util.Map;

import rs.ac.bg.etf.kdp.protocol.JobStatus;

public class JobRecord {

    private final long jobId;
    private final byte[] jarBytes;
    private final String mainClassName;
    private final String[] programArgs;
    private final Map<String, byte[]> inputFiles;
    private final String[] outputFileNames;
    private volatile JobStatus status;
    private final long submittedAt;
    private volatile long finishedAt;
    private volatile Integer assignedWorkerId;
    private volatile String failureMessage;
    private volatile Map<String, byte[]> outputFiles;

    public JobRecord(long jobId, byte[] jarBytes, String mainClassName, String[] programArgs,
                     Map<String, byte[]> inputFiles, String[] outputFileNames) {
        this.jobId = jobId;
        this.jarBytes = jarBytes;
        this.mainClassName = mainClassName;
        this.programArgs = programArgs;
        this.inputFiles = inputFiles;
        this.outputFileNames = outputFileNames;
        this.status = JobStatus.READY;
        this.submittedAt = System.currentTimeMillis();
    }

    public long getJobId() {
        return jobId;
    }

    public byte[] getJarBytes() {
        return jarBytes;
    }

    public String getMainClassName() {
        return mainClassName;
    }

    public String[] getProgramArgs() {
        return programArgs;
    }

    public Map<String, byte[]> getInputFiles() {
        return inputFiles;
    }

    public String[] getOutputFileNames() {
        return outputFileNames;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
        if (status == JobStatus.DONE || status == JobStatus.FAILED
                || status == JobStatus.ABORTED) {
            this.finishedAt = System.currentTimeMillis();
        }
    }

    public long getSubmittedAt() {
        return submittedAt;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public Integer getAssignedWorkerId() {
        return assignedWorkerId;
    }

    public void setAssignedWorkerId(Integer assignedWorkerId) {
        this.assignedWorkerId = assignedWorkerId;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public Map<String, byte[]> getOutputFiles() {
        return outputFiles;
    }

    public void setOutputFiles(Map<String, byte[]> outputFiles) {
        this.outputFiles = outputFiles;
    }
}