package com.example.auto.fakes

import com.example.auto.TokenCache
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