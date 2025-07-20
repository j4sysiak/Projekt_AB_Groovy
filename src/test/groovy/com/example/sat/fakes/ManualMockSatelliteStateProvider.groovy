package com.example.sat.fakes

import com.example.sat.ISatelliteStateProvider
import com.example.sat.domain.SatelliteState

class ManualMockSatelliteStateProvider implements ISatelliteStateProvider {
    SatelliteState stateToReturn = new SatelliteState(batteryLevel: 90.0, dataStorageAvailable: 2048)

    @Override
    SatelliteState getCurrentState() {
        return stateToReturn
    }
}