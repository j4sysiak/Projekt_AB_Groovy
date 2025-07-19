package com.example.sat.fakes

import com.example.sat.TokenCache
import groovy.transform.Canonical

// Używamy @Canonical, aby Groovy automatycznie wygenerował nam
// konstruktor, który pozwoli ustawić pole 'tokenToReturn'.
@Canonical
class ManualMockTokenCache implements TokenCache {

    // DODAJEMY BRAKUJĄCE POLE
    // Ustawiamy domyślną wartość, którą można nadpisać w teście.
    String tokenToReturn = "DEFAULT-FAKE-TOKEN"

    @Override
    String getToken() {
        return tokenToReturn
    }
}