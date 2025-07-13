package com.example.sat.fakes
import com.example.sat.Coordinates
import com.example.sat.IGroundControlRadar

class ManualMockGroundControlRadar implements IGroundControlRadar {
    Coordinates positionToReturn = new Coordinates(latitude: 0, longitude: 0)

    @Override
    Coordinates pingSatellite(String satelliteId) {
        return positionToReturn
    }
}