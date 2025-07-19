package com.example.sat.dto

import groovy.transform.Canonical

//To jest wynik pracy planisty – gotowy, zweryfikowany plan do wysłania na satelitę.
@Canonical
class MissionPlan {
    String missionId
    String status // PENDING_UPLOAD, UPLOADED, EXECUTED
    long estimatedExecutionTime
    List<Map> commandSequence // Lista komend z parametrami
}