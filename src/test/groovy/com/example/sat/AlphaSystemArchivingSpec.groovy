package com.example.sat

import com.example.sat.fakes.InMemoryFileRepository

class AlphaSystemArchivingSpec extends SomeBaseTest {

    def setup() {
        // Tworzymy prawdziwy obiekt repozytorium i ustawiamy go WŁASNORĘCZNIE
        // zanim zawołamy metodę z klasy bazowej.
        repository = new InMemoryFileRepository()

        // Teraz wołamy setupSystem. On zobaczy, że 'repository' już jest ustawione
        // i nie stworzy dla niego mocka.
        setupSystem("ALPHA")
    }

    def "should correctly archive old data using file-based repository"() {
        given: "some data is already saved in the repository"
        repository.saveReprocessedData("SAT-1", "SHORT_DATA") // to zostanie zarchiwizowane
        repository.saveReprocessedData("SAT-2", "VERY_LONG_DATA_THAT_WILL_BE_KEPT")

        when: "the archiving process is triggered"
        // Rzutujemy, aby dostać się do metody specyficznej dla tej implementacji.
        // To jest teraz bezpieczne, bo wiemy, że to jest prawdziwy obiekt.
        ((InMemoryFileRepository)repository).archiveOldData(20)

        then: "short data is moved to archive and long data is kept"
        !repository.wasDataSaved("SAT-1")
        repository.wasDataSaved("SAT-2")
        ((InMemoryFileRepository)repository).archivedData.size() == 1
        ((InMemoryFileRepository)repository).archivedData[0] == "SHORT_DATA"
    }
}