package com.example.auto

import com.example.auto.fakes.ManualMockReprocessor

class BatchProcessingSpec extends SomeBaseTest {

    // Test 1: Sukces za pierwszym razem
    def "should succeed on the first attempt"() {
        given: "a reprocessor configured to succeed immediately"
        def autoId = "AUTO-SUCCESS"

        // 1. Tworzymy nasz RĘCZNY mock
        def manualMock = new ManualMockReprocessor()

        // 2. Mówimy mu, co ma zwracać
        manualMock.resultsToReturn = [true]

        // 3. Tworzymy obiekt testowany
        def batchService = new BatchReprocessingService(manualMock)

        when: "processing is triggered"
        boolean finalResult = batchService.processWithRetries(autoId)

        then: "it succeeds"
        finalResult == true

        and: "it was called only once"
        manualMock.invocationCount == 1
    }

    // Test 2: Porażka po wszystkich próbach
    def "should fail after max retries"() {
        given: "a reprocessor configured to always fail"
        def autoId = "AUTO-ALWAYS-FAIL"

        def manualMock = new ManualMockReprocessor()
        manualMock.resultsToReturn = [false, false, false]

        def batchService = new BatchReprocessingService(manualMock)
        batchService.maxRetries = 3

        when: "processing is triggered"
        boolean finalResult = batchService.processWithRetries(autoId)

        then: "it fails"
        finalResult == false

        and: "it was called 3 times"
        manualMock.invocationCount == 3
    }

    // Test 3: Twój oryginalny test - sukces za trzecim razem
    def "should succeed on the third attempt"() {
        given: "a reprocessor configured to fail twice, then succeed"
        def autoId = "AUTO-RETRY-SUCCESS"

        def manualMock = new ManualMockReprocessor()
        manualMock.resultsToReturn = [false, false, true]

        def batchService = new BatchReprocessingService(manualMock)
        batchService.maxRetries = 3

        when: "processing is triggered"
        boolean finalResult = batchService.processWithRetries(autoId)

        then: "it succeeds"
        finalResult == true

        and: "it was called 3 times"
        manualMock.invocationCount == 3
    }
}