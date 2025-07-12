package com.example.sat.fakes
import com.example.sat.JobRepository

class ManualMockJobRepository implements JobRepository {
    List<String> jobsToFind = []
    List<String> successJobs = []
    List<String> failedJobs = []
    int findInvocations = 0

    @Override
    List<String> findPendingJobs() {
        findInvocations++
        return jobsToFind
    }
    @Override
    void markJobAsSuccess(String satelliteId) { successJobs.add(satelliteId) }
    @Override
    void markJobAsFailed(String satelliteId) { failedJobs.add(satelliteId) }
}