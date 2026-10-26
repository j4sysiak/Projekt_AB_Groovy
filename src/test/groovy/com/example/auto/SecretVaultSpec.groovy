package com.example.auto

import com.example.auto.exceptions.SecretRetrievalException
import spock.lang.Specification

class SecretVaultSpec extends Specification {

    def vault = new SecretVaultImpl("https://test-vault.com")

    def "should return correct secret for a valid name"() {
        when: "a known secret is requested"
        def secret = vault.getSecret("alpha-system/api-key")

        then: "the correct secret value is returned"
        secret == "ALPHA-VALID-API-KEY"
    }

    def "should throw exception for an unknown secret name"() {
        when: "an unknown secret is requested"
        vault.getSecret("non-existent/secret")

        then: "a SecretRetrievalException is thrown"
        thrown(SecretRetrievalException)
    }
}