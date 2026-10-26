package com.example.auto.fakes
import com.example.auto.IPassPredictor

class ManualMockPassPredictor implements IPassPredictor {
    boolean isWindowOpen = true
    @Override boolean isCommunicationWindowOpen() { return isWindowOpen }
}