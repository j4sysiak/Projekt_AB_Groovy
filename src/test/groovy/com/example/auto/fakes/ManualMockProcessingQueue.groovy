package com.example.auto.fakes
import com.example.auto.ProcessingQueue

class ManualMockProcessingQueue implements ProcessingQueue {
    List<String> publishedJobs = []
    List<String> jobsToConsume = []
    int consumeInvocations = 0

    @Override
    void publishJob(String autoIdId) { publishedJobs.add(autoIdId) }
    @Override
    String consumeJob() {
        consumeInvocations++
        return jobsToConsume.isEmpty() ? null : jobsToConsume.remove(0)
    }
}