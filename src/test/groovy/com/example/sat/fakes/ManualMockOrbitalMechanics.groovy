package com.example.sat.fakes
import com.example.sat.domain.Coordinates
import com.example.sat.IOrbitalMechanics

class ManualMockOrbitalMechanics implements IOrbitalMechanics {
    Coordinates positionToReturn = new Coordinates(latitude: 0, longitude: 0)

    @Override
    Coordinates getCurrentPosition() {
        return positionToReturn
    }
}