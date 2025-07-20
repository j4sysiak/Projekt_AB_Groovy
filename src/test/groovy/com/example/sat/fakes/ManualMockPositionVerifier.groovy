package com.example.sat.fakes
import com.example.sat.IPositionVerifier
import com.example.sat.exceptions.PositionMismatchException

class ManualMockPositionVerifier implements IPositionVerifier {
    boolean isPositionOk = true
    @Override void verifyPosition(String satelliteId) {
        if (!isPositionOk) throw new PositionMismatchException("Forced mismatch")
    }
}