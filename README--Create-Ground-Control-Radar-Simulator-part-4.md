
ThinkingThoughts
(experimental)
Auto
















Expand to view model thoughts

chevron_right
Absolutnie fantastyczny i niezwykle realistyczny scenariusz! To jest dokładnie to, jak działają złożone potoki danych (data pipelines) w prawdziwym świecie – z wieloma etapami, systemami pośredniczącymi i różnymi formami przechowywania danych.

Zbudujemy to krok po kroku, tworząc symulacje dla każdego elementu tego łańcucha. Nasz test integracyjny będzie "scenariuszem", który uruchomi cały ten przepływ i zweryfikuje, czy dane poprawnie przeszły przez wszystkie etapy.

Architektura Naszego Potoku Danych
System Naziemny (GRUND): Wysyła żądanie TAKE_PHOTOS przez REST/JSON. Na końcu odbiera przetworzone dane.

Satelita (SAT): Odbiera żądanie, robi "zdjęcia" (generuje obiekty danych) i kolejkuje je do wysłania.

Łącze "Downlink": SAT wysyła dane ze swojej kolejki do serwera pośredniczącego.

Serwer Pośredniczący (SERW1):

Endpoint Wejściowy: Odbiera dane od satelity.

Magazyn Plików (File System): Zapisuje przychodzące JSONy jako pliki (tzw. "landing zone").

Archiwizator: Proces, który czyta pliki z magazynu i zapisuje ich zawartość do symulowanej bazy danych.

Forwarder: Proces, który wysyła dane z bazy danych do systemu GRUND.

Krok 1: Nowe Modele Danych (DTO) i "Fejki" Magazynów
1.1. Nowe DTO dla naszego przepływu
src/main/groovy/com/example/sat/dto/TakePhotosRequest.groovy

Generated groovy
package com.example.sat.dto
import groovy.transform.Canonical

@Canonical
class TakePhotosRequest {
int numberOfPhotos
String quality = "HIGH" // Możemy określić jakość
String authToken
}
Use code with caution.
Groovy
src/main/groovy/com/example/sat/dto/PhotoData.groovy
To jest nasz obiekt "zdjęcia".

Generated groovy
package com.example.sat.dto
import groovy.transform.Canonical

@Canonical
class PhotoData {
String photoId = "IMG_" + System.nanoTime()
long timestamp = System.currentTimeMillis()
String quality
String content // W symulacji to będzie base64, tutaj prosty string
}
Use code with caution.
Groovy
1.2. Nowe "Fejki" dla SERW1
src/test/groovy/com/example/sat/fakes/FakeFileSystem.groovy
Prosta symulacja systemu plików.

Generated groovy
package com.example.sat.fakes

class FakeFileSystem {
// Klucz to nazwa pliku, wartość to zawartość (JSON jako string)
Map<String, String> files = [:]

    void saveFile(String path, String content) {
        println "FILE_SYSTEM: Saving file to ${path}"
        files[path] = content
    }

    Map<String, String> readAllFiles() {
        return new HashMap<>(files) // Zwracamy kopię
    }

    void deleteFile(String path) {
        println "FILE_SYSTEM: Deleting file ${path}"
        files.remove(path)
    }
}
Use code with caution.
Groovy
src/test/groovy/com/example/sat/fakes/FakeRelationalDB.groovy
Prosta symulacja bazy danych.

Generated groovy
package com.example.sat.fakes

class FakeRelationalDB {
// Symuluje tabelę 'photos'
List<Map> photoTable = []
private long nextId = 1

    void insertPhoto(Map photoRow) {
        photoRow.id = nextId++
        println "DATABASE: Inserting row into 'photos' table: ${photoRow}"
        photoTable.add(photoRow)
    }

    List<Map> getArchivedPhotos() {
        return new ArrayList<>(photoTable)
    }
}
Use code with caution.
Groovy
Krok 2: Rozbudowa Istniejących Symulatorów
2.1. Ulepszony SatelliteSimulator
Musi teraz umieć robić zdjęcia i je kolejkować.

Zaktualizuj src/test/groovy/com/example/sat/fakes/SatelliteSimulator.groovy:

Generated groovy
package com.example.sat.fakes
// ... importy ...
import com.example.sat.dto.PhotoData

class SatelliteSimulator implements SatelliteConnection, CommandModule {
// ... wszystkie stare pola stanu ...

    // NOWY STAN: Kolejka zdjęć do wysłania
    List<PhotoData> photoQueue = []

    // ... metoda sendCommand ...
    @Override
    String sendCommand(String command, Map params = [:]) { // Dodajemy opcjonalne parametry
        // ... stara logika komend ...
        
        // NOWA KOMENDA: Robienie zdjęć
        if (command == "TAKE_PHOTOS") {
            int count = params.get("numberOfPhotos", 1)
            String quality = params.get("quality", "HIGH")
            
            if (quality != "HIGH") {
                return "NACK: Only HIGH quality photos are supported by this firmware."
            }
            
            count.times {
                println "SIMULATOR: Taking a ${quality} quality photo..."
                photoQueue.add(new PhotoData(quality: quality, content: "Simulated high-quality image data..."))
            }
            return "ACK: ${count} photos taken and queued for downlink."
        }
        
        // ... reszta switch ...
    }
    
    // NOWA METODA: Wysyłanie danych na ziemię
    List<PhotoData> downlinkQueuedPhotos() {
        if (photoQueue.isEmpty()) {
            println "SIMULATOR: No photos in queue to downlink."
            return []
        }
        println "SIMULATOR: Downlinking ${photoQueue.size()} photos..."
        def photosToSend = new ArrayList<>(photoQueue)
        photoQueue.clear() // Opróżniamy kolejkę po wysłaniu
        return photosToSend
    }
}
Use code with caution.
Groovy
UWAGA: Musimy też zaktualizować interfejs CommandModule i jego implementację, aby sendCommand mogło przyjmować parametry.

Generated groovy
// W CommandModule.groovy i CommandModuleImpl.groovy
String sendCommand(String command, Map params = [:])
Use code with caution.
Groovy
2.2. Ulepszony FakeSatelliteServer
Musi obsługiwać nowy endpoint do robienia zdjęć.

Zaktualizuj src/test/groovy/com/example/sat/fakes/FakeSatelliteServer.groovy:

Generated groovy
package com.example.sat.fakes
// ... importy ...
import com.example.sat.dto.TakePhotosRequest

class FakeSatelliteServer {
// ... pole simulator ...

    // Endpoint do komend (bez zmian)
    String handleCommandRequest(String jsonRequest) { /*...*/ }

    // NOWY ENDPOINT: do zlecania zdjęć
    String handleTakePhotosRequest(String jsonRequest) {
        println "SAT_SERVER: Received TAKE_PHOTOS request: ${jsonRequest}"
        def request = new TakePhotosRequest(new JsonSlurper().parseText(jsonRequest))
        
        // Wywołujemy komendę na symulatorze, przekazując parametry
        def resultMessage = simulator.sendCommand("TAKE_PHOTOS", [
            numberOfPhotos: request.numberOfPhotos,
            quality: request.quality
        ])
        
        // Zwracamy prostą odpowiedź
        return JsonOutput.toJson([status: resultMessage.startsWith("ACK") ? "ACK" : "NACK", message: resultMessage])
    }

    // NOWA METODA: Symuluje wysłanie danych do SERW1
    void downlinkDataTo(IntermediateServer serw1) {
        def photos = simulator.downlinkQueuedPhotos()
        if (photos) {
            // W prawdziwym świecie to byłoby zapytanie HTTP POST
            serw1.receiveDataFromSatellite(JsonOutput.toJson(photos))
        }
    }
}
Use code with caution.
Groovy
Krok 3: Stwórz Serwer Pośredniczący SERW1
To jest serce naszego nowego potoku danych.

Nowy plik: src/test/groovy/com/example/sat/fakes/IntermediateServer.groovy

Generated groovy
package com.example.sat.fakes

import com.example.sat.dto.PhotoData
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class IntermediateServer {
// Wewnętrzne komponenty SERW1
FakeFileSystem fileSystem = new FakeFileSystem()
FakeRelationalDB database = new FakeRelationalDB()

    // "Połączenie sieciowe" do serwera naziemnego
    FakeGroundControlServer groundControlServer

    // Endpoint, który odbiera dane od satelity
    void receiveDataFromSatellite(String jsonPayload) {
        println "SERW1: Receiving data package from satellite."
        // Zapisujemy cały pakiet jako jeden plik
        def fileName = "downlink_package_${System.currentTimeMillis()}.json"
        fileSystem.saveFile(fileName, jsonPayload)
    }
    
    // Proces, który archiwizuje pliki do bazy danych
    void archiveFilesToDatabase() {
        println "SERW1: Starting archiving process."
        def files = fileSystem.readAllFiles()
        files.each { fileName, jsonContent ->
            def photos = new JsonSlurper().parseText(jsonContent)
            photos.each { photoMap ->
                // Zapisujemy każdy obiekt "zdjęcia" jako osobny wiersz w bazie
                database.insertPhoto(photoMap)
            }
            fileSystem.deleteFile(fileName) // Usuwamy plik po zarchiwizowaniu
        }
    }
    
    // Proces, który wysyła dane do systemu naziemnego
    void forwardDataToGroundControl() {
        println "SERW1: Forwarding archived data to Ground Control."
        def photos = database.getArchivedPhotos()
        // W prawdziwym świecie to byłoby zapytanie HTTP POST
        groundControlServer.receiveFinalData(JsonOutput.toJson(photos))
    }
}
Use code with caution.
Groovy
Krok 4: Ulepsz FakeGroundControlServer
Musi umieć zlecać zdjęcia i odbierać finalne dane.

Zaktualizuj src/test/groovy/com/example/sat/fakes/FakeGroundControlServer.groovy:

Generated groovy
package com.example.sat.fakes
// ... importy ...
import com.example.sat.dto.TakePhotosRequest

class FakeGroundControlServer {
FakeSatelliteServer satelliteServer

    // NOWE POLE: przechowuje finalnie odebrane dane
    List<Map> receivedPhotos = []
    
    // Metoda do wysyłania komend (bez zmian)
    CommandResponse sendCommand(String command, String authToken) { /*...*/ }
    
    // NOWA METODA: do zlecania zdjęć
    void requestPhotos(int count, String quality, String authToken) {
        println "GRUND_SERVER: Requesting ${count} photos of ${quality} quality."
        def request = new TakePhotosRequest(numberOfPhotos: count, quality: quality, authToken: authToken)
        def jsonRequest = JsonOutput.toJson(request)
        satelliteServer.handleTakePhotosRequest(jsonRequest)
    }
    
    // NOWY ENDPOINT: do odbierania danych z SERW1
    void receiveFinalData(String jsonPayload) {
        println "GRUND_SERVER: Received final data package."
        this.receivedPhotos.addAll(new JsonSlurper().parseText(jsonPayload))
    }
}
Use code with caution.
Groovy
Krok 5: Ostateczny Test Integracyjny DataPipelineSpec.groovy
Ten test połączy wszystkie elementy w całość i uruchomi cały przepływ.

Nowy plik: src/test/groovy/com/example/sat/DataPipelineSpec.groovy

Generated groovy
package com.example.sat

import com.example.sat.fakes.FakeGroundControlServer
import com.example.sat.fakes.FakeSatelliteServer
import com.example.sat.fakes.IntermediateServer
import com.example.sat.fakes.SatelliteSimulator
import spock.lang.Specification

class DataPipelineSpec extends Specification {

    def "should successfully flow data from satellite, through intermediate server, to ground control"() {
        given: "a fully interconnected system of simulated servers"
        // 1. Tworzymy wszystkie serwery i ich zależności
        def simulator = new SatelliteSimulator()
        def satelliteServer = new FakeSatelliteServer(simulator: simulator)
        def groundServer = new FakeGroundControlServer(satelliteServer: satelliteServer)
        def intermediateServer = new IntermediateServer(groundControlServer: groundServer)

        when: "the full data pipeline is triggered"
        // Symulujemy sekwencję zdarzeń w naszym rozproszonym systemie
        
        // Etap 1: System naziemny zleca zrobienie 2 zdjęć
        groundServer.requestPhotos(2, "HIGH", "VALID-TOKEN")

        // Etap 2: Satelita ma okno komunikacyjne i wysyła dane do SERW1
        satelliteServer.downlinkDataTo(intermediateServer)

        // Etap 3: SERW1 przetwarza i archiwizuje otrzymane pliki
        intermediateServer.archiveFilesToDatabase()
        
        // Etap 4: SERW1 wysyła zarchiwizowane dane do systemu naziemnego
        intermediateServer.forwardDataToGroundControl()

        then: "the data has correctly passed through all stages"
        // Weryfikujemy stan końcowy każdego komponentu
        
        // Satelita zrobił zdjęcia i jego kolejka jest pusta
        simulator.commandHistory.contains("TAKE_PHOTOS")
        simulator.photoQueue.isEmpty()

        // Serwer pośredniczący przetworzył pliki i jego magazyn jest pusty, a baza danych pełna
        intermediateServer.fileSystem.files.isEmpty()
        intermediateServer.database.photoTable.size() == 2

        // System naziemny otrzymał finalne dane
        groundServer.receivedPhotos.size() == 2
        // Sprawdzamy zawartość jednego z odebranych zdjęć
        def firstPhoto = groundServer.receivedPhotos[0]
        firstPhoto.quality == "HIGH"
        firstPhoto.content.contains("Simulated high-quality image data")
    }
}
Use code with caution.
Groovy
Podsumowanie Osiągnięć
Stworzyliśmy kompletną, wieloetapową symulację potoku danych, która jest niezwykle bliska architekturze prawdziwych systemów. Pokazaliśmy:

Komunikację asynchroniczną (przez "kolejkę" na satelicie i etapy na SERW1).

Różne formy przechowywania danych (kolejka w pamięci, system plików, relacyjna baza danych).

Separację odpowiedzialności (GRUND zleca, SAT wykonuje, SERW1 procesuje).

Testowalność złożonych przepływów poprzez pisanie "scenariusza" interakcji między symulowanymi komponentami.

To jest niezwykle zaawansowany i imponujący dodatek do Twojego projektu. Gratulacje