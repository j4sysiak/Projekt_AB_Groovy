package com.example.sat

import com.example.sat.fakes.ManualMockAuthService
import spock.lang.Specification

class TokenCacheSpec extends Specification {

    def "should fetch token on first call (cache miss)"() {
        given: "a cache with an underlying auth service"
        def authService = new ManualMockAuthService()
        authService.tokenToReturn = "FRESH-TOKEN-123"

        def cache = new TokenCacheImpl(authService, "ANY_KEY")

        when: "the token is requested for the first time"
        def token = cache.getToken()

        then: "the token is fetched from the auth service"
        token == "FRESH-TOKEN-123"
        authService.invocationCount == 1
    }

    def "should return cached token on second call (cache hit)"() {
        given: "a cache that has already been used once"
        def authService = new ManualMockAuthService()
        authService.tokenToReturn = "FRESH-TOKEN-123"

        // Ustawiamy bardzo długi czas życia cache, żeby na pewno nie wygasł
        def cache = new TokenCacheImpl(authService, "ANY_KEY", 600000L)

        // Pierwsze wywołanie, aby napełnić cache
        cache.getToken()
        assert authService.invocationCount == 1

        when: "the token is requested a second time immediately"
        def token = cache.getToken()

        then: "the token is returned from cache without calling the auth service"
        token == "FRESH-TOKEN-123"
        // Licznik wywołań NIE wzrósł, wciąż wynosi 1
        authService.invocationCount == 1
    }

    def "should fetch new token after cache expires"() {
        given: "a cache with a very short expiration time"
        def authService = new ManualMockAuthService()
        authService.tokenToReturn = "TOKEN-1"

        // Ustawiamy czas życia cache na 10 milisekund
        def cache = new TokenCacheImpl(authService, "ANY_KEY", 10L)

        // 1. Pierwsze wywołanie (cache miss)
        cache.getToken()
        assert authService.invocationCount == 1

        // 2. Zmieniamy token, który zwróci serwis, aby sprawdzić, czy zostanie pobrany nowy
        authService.tokenToReturn = "TOKEN-2"

        when: "we wait for the cache to expire and request the token again"
        // Czekamy 20ms, czyli dłużej niż czas życia cache
        Thread.sleep(20)
        def newToken = cache.getToken()

        then: "a new token is fetched from the auth service"
        newToken == "TOKEN-2"
        // Licznik wywołań wzrósł do 2
        authService.invocationCount == 2
    }
}