package com.example.sat.fakes
import com.example.sat.IPassPredictor

class ManualMockPassPredictor implements IPassPredictor {
    boolean isWindowOpen = true
    @Override boolean isCommunicationWindowOpen() { return isWindowOpen }
}