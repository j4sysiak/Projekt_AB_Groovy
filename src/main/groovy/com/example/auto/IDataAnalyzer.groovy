package com.example.auto

import com.example.auto.domain.AnalysisResult

// Interfejs dla modułu analizującego dane
interface IDataAnalyzer {
    AnalysisResult analyze(String rawData)
}