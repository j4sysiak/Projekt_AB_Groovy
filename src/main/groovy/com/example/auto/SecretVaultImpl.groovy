package com.example.auto

import com.example.auto.exceptions.SecretRetrievalException

// Prosta symulacja magazynu sekretów, która przechowuje dane w pamięci
class SecretVaultImpl implements SecretVault {
    private final String vaultAddress

    // W prawdziwym świecie te sekrety byłyby w bezpiecznym, zdalnym systemie.
    // Tutaj symulujemy je za pomocą mapy.
    private final Map<String, String> secrets = [
            "alpha-system/api-key": "ALPHA-VALID-API-KEY",
            "beta-system/api-key": "BETA-SECRET-KEY-99",
            "secret-name": "ALPHA-VALID-API-KEY" // <-- Dodaj tę linię
    ]

    SecretVaultImpl(String vaultAddress) {
        this.vaultAddress = vaultAddress
        println "VAULT: Initializing connection to secret vault at ${vaultAddress}"
    }

    @Override
    String getSecret(String secretName) throws SecretRetrievalException {
        println "VAULT: Attempting to retrieve secret: '${secretName}'"
        def secret = secrets[secretName]
        if (secret == null) {
            throw new SecretRetrievalException("Secret '${secretName}' not found in vault.")
        }
        return secret
    }
}