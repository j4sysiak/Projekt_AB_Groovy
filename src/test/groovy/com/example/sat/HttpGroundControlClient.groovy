package com.example.sat.fakes

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class HttpGroundControlClient {
    private final String serverUrl

    HttpGroundControlClient(String serverUrl) {
        this.serverUrl = serverUrl
    }

    private Map sendRequest(String path, String method, String authToken = null, Map body = null) {
        def url = new URL("${serverUrl}${path}")
        HttpURLConnection connection = (HttpURLConnection) url.openConnection()
        connection.setRequestMethod(method)
        connection.setDoOutput(true)

        if (authToken) {
            connection.setRequestProperty("Authorization", "Bearer ${authToken}")
        }

        if (body) {
            connection.setRequestProperty("Content-Type", "application/json")
            def jsonBody = JsonOutput.toJson(body)
            connection.getOutputStream().write(jsonBody.getBytes("UTF-8"))
        }

        def responseCode = connection.getResponseCode()
        def responseBody = (responseCode < 400) ? connection.getInputStream().getText("UTF-8") : connection.getErrorStream().getText("UTF-8")

        // === POPRAWKA: Wracamy do poprawnej nazwy metody 'parseText' ===
        Object responseJson = [:]
        if (responseBody && (responseBody.trim().startsWith("{") || responseBody.trim().startsWith("["))) {
            responseJson = new JsonSlurper().parseText(responseBody) // <-- POPRAWNA METODA
        } else {
            responseJson = [raw: responseBody]
        }

        return [statusCode: responseCode, json: responseJson]
    }

    // Wszystkie poniższe metody są już poprawne i nie wymagają zmian
    Map connect(String authToken) {
        return sendRequest("/connect", "POST", authToken)
    }

    Map disconnect() {
        return sendRequest("/disconnect", "POST")
    }

    Map sendCommand(String command, Map params = [:]) {
        return sendRequest("/command", "POST", null, [command: command, params: params])
    }

    Map downlinkData() {
        return sendRequest("/downlink", "GET")
    }
}