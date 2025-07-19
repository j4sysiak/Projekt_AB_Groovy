package com.example.sat


// UWAGA: Potrzebujemy tego nowego, prostego interfejsu i "fejka" (ManualMockSatelliteStateProvider),
// aby planista mógł pytać o stan satelity.
interface ISatelliteStateProvider {
    SatelliteState getCurrentState()
}