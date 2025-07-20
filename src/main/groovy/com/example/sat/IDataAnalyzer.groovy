package com.example.sat

import com.example.sat.domain.AnalysisResult

// Interfejs dla modułu analizującego dane
interface IDataAnalyzer {
    AnalysisResult analyze(String rawData)
}