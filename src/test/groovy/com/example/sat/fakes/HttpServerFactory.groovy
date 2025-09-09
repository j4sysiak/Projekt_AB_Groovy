package com.example.sat.fakes

import com.example.sat.server.IHttpServer

/**
 * Fabryka, która tworzy testowe implementacje interfejsu IHttpServer.
 * Ta klasa "żyje" tylko w środowisku testowym.
 */
class HttpServerFactory {
    /**
     * Tworzy i zwraca nową instancję serwera HTTP do użytku w testach.
     * @return Obiekt implementujący IHttpServer.
     */
    static IHttpServer create() {
        // Zwracamy naszą testową implementację opartą na SparkJava
        return new FakeSparkServer()
    }
}
