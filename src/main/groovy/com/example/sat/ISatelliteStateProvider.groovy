package com.example.sat

import com.example.sat.domain.SatelliteState


// UWAGA: Potrzebujemy tego nowego, prostego interfejsu i "fejka" (ManualMockSatelliteStateProvider),
// aby planista mógł pytać o stan satelity.
interface ISatelliteStateProvider {
    SatelliteState getCurrentState()
}