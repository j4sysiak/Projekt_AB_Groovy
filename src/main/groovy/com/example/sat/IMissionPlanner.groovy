package com.example.sat

import com.example.sat.dto.MissionPlan
import com.example.sat.dto.MissionRequest
import com.example.sat.exceptions.MissionPlanningException

interface IMissionPlanner {
    // Próbuje stworzyć plan misji na podstawie zlecenia.
    // Rzuca wyjątkiem, jeśli misja jest niemożliwa do wykonania.
    MissionPlan createPlan(MissionRequest request) throws MissionPlanningException
}