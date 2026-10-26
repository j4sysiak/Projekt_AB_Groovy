package com.example.auto.fakes
import com.example.auto.domain.Coordinates
import com.example.auto.IOrbitalMechanics

class ManualMockOrbitalMechanics implements IOrbitalMechanics {
    Coordinates positionToReturn = new Coordinates(latitude: 0, longitude: 0)

    @Override
    Coordinates getCurrentPosition() {
        return positionToReturn
    }
}