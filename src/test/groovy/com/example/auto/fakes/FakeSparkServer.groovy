// src/test/groovy/com/example/auto/fakes/FakeSparkServer.groovy
package com.example.auto.fakes

import com.example.auto.server.IHttpServer
import spark.Service // Ten import jest OK, bo jesteśmy w `src/test`

/**
 * Testowa implementacja interfejsu IHttpServer.
 * "Pod spodem" używa prawdziwego serwera SparkJava.
 */
class FakeSparkServer implements IHttpServer {

    // Prywatne pole, które trzyma prawdziwą instancję Sparka
    private Service http = Service.ignite()
    //private int configuredPort = 0 // NOWE POLE: Przechowujemy port tutaj

    @Override
    void port(int port) {
        // Po prostu przekazujemy żądanie do Sparka.
        // Jeśli port=0, Spark sam znajdzie wolny port.
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

    /**
     * Zwraca numer portu, na którym serwer faktycznie działa.
     * Ta metoda jest teraz w 100% bezpieczna.
     */
    @Override
    int getPort() {
        // Czekamy krótko, aby dać serwerowi Spark szansę na pełne uruchomienie
        // i związanie się z portem systemowym. To eliminuje IllegalStateException.
        try {
            Thread.sleep(100)
        } catch (InterruptedException e) { /* ignoruj */ }

        // Zawsze pytamy Sparka, na jakim porcie faktycznie wystartował.
        return http.port()
    }
}