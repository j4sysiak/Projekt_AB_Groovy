package com.example.sat

import com.example.sat.fakes.FakeFileSystem
import com.example.sat.fakes.FakeRelationalDB
import com.example.sat.fakes.HttpServerFactory
import com.example.sat.http.SimpleHttpClient
import com.example.sat.server.IntermediateServerApp
import spock.lang.Specification

class IntermediateServerStateSpec extends Specification {

    IntermediateServerApp intermediateServer
    SimpleHttpClient client

    def setup() {
        def fs = new FakeFileSystem()
        def db = new FakeRelationalDB()
        def http = HttpServerFactory.create()

        intermediateServer = new IntermediateServerApp(http, fs, db)
        intermediateServer.start(0)

        client = new SimpleHttpClient("http://localhost:${intermediateServer.getPort()}")
    }

    def cleanup() {
        intermediateServer?.stop()
    }

    def "should correctly increment file count after receiving data"() {
        expect: "server initially has zero files"
        intermediateServer.getFileSystemFileCount() == 0

        when: "client sends a data package to the ingest endpoint"
        def response = client.sendGenericPost("/ingest", '[{"photoId":"TEST-001"}]')

        then: "operation is successful"
        response.statusCode == 202

        and: "the file count on the server instance is now 1"
        intermediateServer.getFileSystemFileCount() == 1
    }
}