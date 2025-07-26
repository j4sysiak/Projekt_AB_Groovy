package com.example.sat

import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.MissionRequest
import com.example.sat.exceptions.MissionPlanningException

interface IMissionPlanner {
    // ZMIANA: Metoda zwraca teraz nasz ujednolicony model misji
    PhotoCollectionManifest createPlan(MissionRequest request) throws MissionPlanningException
}