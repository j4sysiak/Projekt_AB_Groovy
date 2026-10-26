package com.example.auto

interface ProcessingQueue {
    void publishJob(String autoId)
    String consumeJob()
}