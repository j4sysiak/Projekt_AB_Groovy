// src/test/groovy/com/example/sat/fakes/FakeSparkServer.groovy
package com.example.sat.fakes

import com.example.sat.server.IHttpServer
import spark.Service // Ten import jest OK, bo jesteśmy w `src/test`

/**
 * Testowa implementacja interfejsu IHttpServer.
 * "Pod spodem" używa prawdziwego serwera SparkJava.
 */
class FakeSparkServer implements IHttpServer {

    // Prywatne pole, które trzyma prawdziwą instancję Sparka
    private Service http = Service.ignite()

    @Override
    void port(int port) {
        http.port(port)
    }

    @Override
    void get(String path, Closure handler) {
        // Spark oczekuje handlera z dwoma argumentami (req, res),
        // więc opakowujemy nasze proste closure
        http.get(path, { req, res -> handler.call(req, res) })
    }

    @Override
    void post(String path, Closure handler) {
        http.post(path, { req, res -> handler.call(req, res) })
    }

    @Override
    void stop() {
        http.stop()
        http.awaitStop()
    }

    @Override
    void awaitInitialization() {
        http.awaitInitialization()
    }

    @Override
    int getPort() {
        return http.port()
    }
}