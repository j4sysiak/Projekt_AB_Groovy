package com.example.sat.server

import com.example.sat.fakes.SatelliteSimulator
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spark.Spark

class SatelliteSimulatorServer {
    private final SatelliteSimulator simulator

    SatelliteSimulatorServer(SatelliteSimulator simulator) {
        this.simulator = simulator
    }

    void start(int port) {
        println "SERVER: Starting Satellite Simulator Server on port ${port}..."

        // === POPRAWNA SKŁADNIA ===
        Spark.port(port) // Używamy jawnego odwołania do klasy Spark
        // ==========================

        // POST /connect - ZMIANA: Zwracamy JSON
        Spark.post("/connect") { req, res ->
            res.type("application/json")
            def authToken = req.headers("Authorization")?.replace("Bearer ", "")
            try {
                simulator.connect(authToken)
                res.status(200)
                return JsonOutput.toJson([status: "OK", message: "Connected"])
            } catch (Exception e) {
                res.status(400)
                return JsonOutput.toJson([status: "ERROR", message: e.message])
            }
        }

        // POST /command
        Spark.post("/command") { req, res ->
            res.type("application/json")
            try {
                def request = new JsonSlurper().parseText(req.body())
                def responseMessage = simulator.sendCommand(request.command, request.params ?: [:])

                def response = [
                        status: responseMessage.startsWith("ACK") ? "ACK" : "NACK",
                        message: responseMessage,
                        batteryLevel: simulator.batteryLevel
                ]
                return JsonOutput.toJson(response)
            } catch (Exception e) {
                res.status(500) // Internal Server Error
                return JsonOutput.toJson([status: "ERROR", message: e.message])
            }
        }

        // GET /downlink
        Spark.get("/downlink") { req, res ->
            res.type("application/json")
            def photos = simulator.downlinkQueuedPhotos()
            return JsonOutput.toJson(photos)
        }

        // POST /disconnect - ZMIANA: Zwracamy JSON
        Spark.post("/disconnect") { req, res ->
            res.type("application/json")
            simulator.disconnect()
            res.status(200)
            return JsonOutput.toJson([status: "OK", message: "Disconnected"])
        }

        Spark.awaitInitialization()
        println "SERVER: Satellite Simulator Server started on port ${Spark.port()}."
    }

    void stop() {
        println "SERVER: Stopping Satellite Simulator Server..."
        Spark.stop()
        Spark.awaitStop()
        println "SERVER: Server stopped."
    }
}