package com.example.sat

// Prosty obiekt do przechowywania wyniku analizy
class AnalysisResult {
    boolean anomalyDetected
    String anomalyType // np. "HURRICANE", "VOLCANIC_ASH", "NONE"
    String details
}