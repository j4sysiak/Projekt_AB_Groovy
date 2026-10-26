package com.example.auto.fakes
import com.example.auto.IPositionVerifier
import com.example.auto.exceptions.PositionMismatchException

class ManualMockPositionVerifier implements IPositionVerifier {
    boolean isPositionOk = true
    @Override void verifyPosition(String autoIdId) {
        if (!isPositionOk) throw new PositionMismatchException("Forced mismatch")
    }
}