package com.example.auto.fakes
import com.example.auto.domain.Coordinates
import com.example.auto.IPlatzControlRadio

class ManualMockPlatzControlRadio implements IPlatzControlRadio {
    Coordinates positionToReturn = new Coordinates(latitude: 0, longitude: 0)

    @Override
    Coordinates pingAuto(String autoIdId) {
        return positionToReturn
    }
}