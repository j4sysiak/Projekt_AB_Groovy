package com.example.auto.fakes

import com.example.auto.IAutoStateProvider
import com.example.auto.domain.AutoState

class ManualMockAutoStateProvider implements IAutoStateProvider {
    AutoState stateToReturn = new AutoState(batteryLevel: 90.0, dataStorageAvailable: 2048)

    @Override
    AutoState getCurrentState() {
        return stateToReturn
    }
}