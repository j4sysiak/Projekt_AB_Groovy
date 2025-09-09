package com.example.sat.server

/**
 * Czysty kontrakt (interfejs) dla serwera HTTP.
 * Definiuje, CO serwer musi potrafić, ale nie JAK to robi.
 * Nie zawiera żadnej logiki ani metod statycznych.
 */
interface IHttpServer {
    void port(int port)
    void get(String path, Closure handler)
    void post(String path, Closure handler)
    void stop()
    void awaitInitialization()
    int getPort()
}