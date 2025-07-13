package com.example.sat.fakes
import com.example.sat.TokenCache

class ManualMockTokenCache implements TokenCache {
    @Override String getToken() { return "VALID-FAKE-TOKEN" }
}