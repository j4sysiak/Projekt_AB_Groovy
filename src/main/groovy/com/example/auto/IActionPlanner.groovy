package com.example.auto

import com.example.auto.dto.PhotoCollectionAction
import com.example.auto.dto.ActionRequest
import com.example.auto.exceptions.ActionPlanningException

interface IActionPlanner {
    // ZMIANA: Metoda zwraca teraz nasz ujednolicony model misji
    PhotoCollectionAction createPlan(ActionRequest request) throws ActionPlanningException
}