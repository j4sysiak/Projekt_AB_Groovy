package com.example.sat.http

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class SimpleHttpClient implements ISatelliteApiClient {
    private final String serverUrl

    SimpleHttpClient(String serverUrl) {
        this.serverUrl = serverUrl
    }

    // Implementacja metody z interfejsu
    @Override
    Map uploadMissionPlan(Object plan) {
        return sendGenericPost("/mission-plan", plan)
    }

    private Map sendRequest(String path, String method, String authToken = null, Object body = null) {
        def url = new URL("${serverUrl}${path}")
        HttpURLConnection connection = (HttpURLConnection) url.openConnection()
        connection.setRequestMethod(method)
        connection.setDoOutput(true)

        if (authToken) {
            connection.setRequestProperty("Authorization", "Bearer ${authToken}")
        }

        if (body != null) {
            connection.setRequestProperty("Content-Type", "application/json")
            // JsonOutput.toJson() potrafi poprawnie zserializować zarówno Mapy, jak i Listy.
            def jsonBody = JsonOutput.toJson(body)
            connection.getOutputStream().write(jsonBody.getBytes("UTF-8"))
        }

        def responseCode = connection.getResponseCode()
        def responseBody = (responseCode < 400) ? connection.getInputStream().getText("UTF-8") : connection.getErrorStream().getText("UTF-8")

        Object responseJson = [:]
        if (responseBody && (responseBody.trim().startsWith("{") || responseBody.trim().startsWith("["))) {
            responseJson = new JsonSlurper().parseText(responseBody)
        } else {
            responseJson = [raw: responseBody]
        }

        return [statusCode: responseCode, json: responseJson]
    }

    // Metody publiczne nie wymagają zmian, bo już przekazują
    // odpowiednie typy do (teraz elastycznej) metody sendRequest.
    Map connect(String authToken) {
        return sendRequest("/connect", "POST", authToken)
    }

    Map disconnect() {
        return sendRequest("/disconnect", "POST")
    }

    Map sendCommand(String command, Map params = [:]) {
        return sendRequest("/command", "POST", null, [command: command, params: params])
    }

    Map getData(String path) {
        return sendRequest(path, "GET")
    }

    Map sendGenericPost(String path, Object body) {
        return sendRequest(path, "POST", null, body)
    }

    // === NOWA, DEDYKOWANA METODA ===
    // Jest bardziej opisowa i nie psuje istniejących wywołań.
    Map sendAuthenticatedPost(String path, Object body, String authToken) {
        return sendRequest(path, "POST", authToken, body)
    }
}