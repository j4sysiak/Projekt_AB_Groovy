package com.example.sat.fakes
import com.example.sat.ProcessingQueue

class ManualMockProcessingQueue implements ProcessingQueue {
    List<String> publishedJobs = []
    List<String> jobsToConsume = []
    int consumeInvocations = 0

    @Override
    void publishJob(String satelliteId) { publishedJobs.add(satelliteId) }
    @Override
    String consumeJob() {
        consumeInvocations++
        return jobsToConsume.isEmpty() ? null : jobsToConsume.remove(0)
    }
}