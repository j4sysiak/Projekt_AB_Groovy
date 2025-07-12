package com.example.sat.fakes
import com.example.sat.AuthService

class ManualMockAuthService implements AuthService {
    String tokenToReturn = "DEFAULT-MOCK-TOKEN"
    int invocationCount = 0

    @Override
    String getAuthToken(String apiKey) {
        invocationCount++
        return tokenToReturn
    }
}