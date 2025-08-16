package com.example.sat.http

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// ZMIANA: Interfejs INotificationClient jest teraz zaimplementowany tutaj
// To sprawia, że ta klasa jest w pełni zgodna z NotificationService
class SimpleHttpClient implements ISatelliteApiClient, INotificationClient {

    // ZMIANA: Usunięto 'final', aby umożliwić istnienie pustego konstruktora,
    // który jest potrzebny do wysyłania webhooków.
    private String serverUrl

    // Pusty konstruktor dla NotificationService
    SimpleHttpClient() {
        this.serverUrl = null
    }

    // Główny konstruktor dla komunikacji z konkretnym serwerem
    SimpleHttpClient(String serverUrl) {
        this.serverUrl = serverUrl
    }

    // Implementacja metody z interfejsu INotificationClient
    @Override
    void send(String url, Object payload) {
        // callbackUrl to oczywiście "http://.../webhook".
        // Po prostu opakowuje sendGenericPost, aby spełnić kontrakt interfejsu
        sendGenericPost(url, payload)
    }

    // Implementacja metody z interfejsu ISatelliteApiClient
    @Override
    Map uploadMissionPlan(Object plan) {
        // Używa poprawnego endpointu do wysyłania planu misji
        return sendGenericPost("/mission/photo-collection", plan)
    }

    // === METODY PUBLICZNE ===

    Map getData(String path) {
        return sendRequest(path, "GET")
    }

    Map sendGenericPost(String urlOrPath, Object body) {
        return sendRequest(urlOrPath, "POST", body)
    }


    // === PRYWATNA METODA WYKONAWCZA ===

    private Map sendRequest(String urlOrPath, String method, Object body = null) {
        HttpURLConnection connection
        try {
            URL targetUrl

            if (urlOrPath.startsWith("http")) {
                targetUrl = new URL(urlOrPath)
            } else {
                if (this.serverUrl == null) {
                    throw new IllegalStateException("Cannot make a relative request without a serverUrl configured.")
                }
                targetUrl = new URL(this.serverUrl + urlOrPath)
            }

            println "HTTP_CLIENT: Sending ${method} to ${targetUrl}"

            connection = targetUrl.openConnection()
            connection.requestMethod = method
            connection.setRequestProperty("Content-Type", "application/json")

            if (method == "POST" || method == "PUT" || method == "PATCH") {
                connection.doOutput = true
                if (body != null) {
                    String jsonBody = JsonOutput.toJson(body)
                    connection.outputStream.write(jsonBody.bytes)
                }
            }

            def responseCode = connection.responseCode
            def responseStream = (responseCode < 400) ? connection.inputStream : connection.errorStream
            def responseBody = responseStream ? responseStream.getText("UTF-8") : ""

            Object responseJson
            if (responseBody && (responseBody.trim().startsWith("{") || responseBody.trim().startsWith("["))) {
                responseJson = new JsonSlurper().parseText(responseBody)
            } else {
                responseJson = [raw: responseBody]
            }

            return [statusCode: responseCode, json: responseJson]
        } catch (Exception e) {
            println "HTTP_CLIENT_ERROR: Failed to send request: ${e.message}"
            return [statusCode: 500, json: [error: "Client Exception", message: e.message]]
        } finally {
            connection?.disconnect()
        }
    }
}