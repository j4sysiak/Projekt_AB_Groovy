package com.example.auto.fakes
import com.example.auto.JobRepository

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
    void markJobAsSuccess(String autoIdId) { successJobs.add(autoIdId) }
    @Override
    void markJobAsFailed(String autoIdId) { failedJobs.add(autoIdId) }
}