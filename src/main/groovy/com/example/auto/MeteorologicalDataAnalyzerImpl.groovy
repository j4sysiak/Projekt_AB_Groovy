package com.example.auto

import com.example.auto.domain.AnalysisResult

class MeteorologicalDataAnalyzerImpl implements IDataAnalyzer {
    // Prosta logika: szukamy słów kluczowych w danych tekstowych
    @Override
    AnalysisResult analyze(String rawData) {
        println "ANALYZER: Analyzing data chunk of size ${rawData.length()}..."

        if (rawData.contains("HURRICANE")) {
            println "ANALYZER: Anomaly detected! Type: HURRICANE"
            return new AnalysisResult(
                    anomalyDetected: true,
                    anomalyType: "HURRICANE",
                    details: "Hurricane detected in the provided  imagery."
            )
        }

        println "ANALYZER: No anomalies detected."
        return new AnalysisResult(anomalyDetected: false, anomalyType: "NONE", details: "Clear weather.")
    }
}