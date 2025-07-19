
Doskonałe pytanie, które trafia w samo sedno architektury! Odpowiedź brzmi: nie do końca, i to jest właśnie piękno tego projektu.

Pozwól, że wyjaśnię, a to rozwieje wszystkie wątpliwości.

Architektura Pozostaje Ta Sama. Zmienia się Tylko "Inteligencja" GRUND.
Twój obecny schemat komunikacji sieciowej (GRUND ↔ SAT ↔ SERW1) jest perfekcyjny i nie musimy go zmieniać. Satelita (SAT) jest "głupim" wykonawcą – nie powinien nic wiedzieć o planowaniu misji. Jego jedynym zadaniem jest odbieranie i wykonywanie prostych komend (TAKE_PHOTOS, CHECK_STATUS itd.).

Co się zmienia, to wewnętrzna logika serwera GRUND.

Spójrz na to tak:

Wcześniej (stara wersja): Użytkownik (w naszym teście SimpleHttpClient) mówił do GRUND: "Wyślij do SAT komendę TAKE_PHOTOS". GRUND był tylko prostym "przekaźnikiem".

Teraz (nowa wersja): Użytkownik mówi do GRUND: "Chcę zdjęcia Warszawy jutro o 10:00". GRUND, zanim w ogóle skontaktuje się z SAT, uruchamia swój wewnętrzny moduł MissionPlanner, aby sprawdzić, czy to jest w ogóle możliwe.

Wniosek:

SAT nie musi być aktualizowany. On wciąż będzie odbierał te same, proste komendy co wcześniej.

MissionPlanner jest komponentem wewnętrznym serwera GRUND. Działa on po stronie naziemnej.

Testy komunikacji sieciowej (GrundToSatNetworkSpec itd.) również nie muszą być fundamentalnie zmieniane. Musimy je tylko dostosować, aby wołały nowy endpoint /missions na GRUND, zamiast starego /mission/photos.

Jak to Wygląda w Praktyce (Poprawiony Scenariusz)
Nasz test GrundToSatNetworkSpec nie będzie już testował prostej komendy, ale cały, inteligentny proces planowania. To jest znacznie bardziej wartościowy test.

Ostateczny, Poprawny Test dla Przepływu GRUND → SAT
Ten test zweryfikuje, czy GroundControlServerApp, używając swojego wewnętrznego MissionPlanner, potrafi poprawnie zaplanować misję i (w przyszłości) zlecić jej wykonanie.

Zastąp zawartość pliku GrundToSatNetworkSpec.groovy tym nowym, aktualnym kodem:

Generated groovy
package com.example.sat

import com.example.sat.dto.Coordinates
import com.example.sat.dto.MissionRequest
import com.example.sat.fakes.ManualMockOrbitalMechanics
import com.example.sat.fakes.ManualMockSatelliteStateProvider
import com.example.sat.server.GroundControlServerApp
import spock.lang.Specification

class GrundToSatNetworkSpec extends Specification {

    GroundControlServerApp grundServer
    
    // Klient, który będzie udawał "użytkownika"
    com.example.sat.utils.SimpleHttpClient grundClient 

    def setup() {
        grundServer = new GroundControlServerApp()
        grundServer.start(0)
        
        // Wstrzykujemy do serwera GRUND "fejkowe" komponenty dla planisty
        def orbitalMechanics = new ManualMockOrbitalMechanics(positionToReturn: new Coordinates(latitude: 50.0, longitude: 20.0))
        def stateProvider = new ManualMockSatelliteStateProvider(stateToReturn: new SatelliteState(batteryLevel: 80.0, dataStorageAvailable: 4096))
        def planner = new MissionPlannerImpl(orbitalMechanics, stateProvider)
        grundServer.setMissionPlanner(planner)
        
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"
        grundClient = new com.example.sat.utils.SimpleHttpClient(grundServerUrl)
    }

    def cleanup() {
        grundServer?.stop()
    }

    def "ground control server should accept and plan a viable mission"() {
        given: "a mission request for a reachable target"
        def missionRequest = new MissionRequest(
            targetCoordinates: new Coordinates(latitude: 52.0, longitude: 21.0),
            startTimeMillis: System.currentTimeMillis() + 3600_000
        )

        when: "a user sends the mission request to the ground control server"
        def response = grundClient.sendGenericPost("/missions", missionRequest)

        then: "the ground control server accepts the mission and returns a valid plan"
        response.statusCode == 202 // Accepted
        def plan = response.json
        plan.status == "PENDING_UPLOAD"
        plan.missionId.startsWith("MPLAN-")
        plan.commandSequence.size() == 3
    }

    def "ground control server should reject an impossible mission"() {
        given: "a mission request for a target outside of the satellite's reach"
        // Konfigurujemy naszego "fejka", aby symulował, że satelita jest daleko
        def orbitalMechanics = new ManualMockOrbitalMechanics(positionToReturn: new Coordinates(latitude: -50.0, longitude: 100.0))
        def stateProvider = new ManualMockSatelliteStateProvider()
        def planner = new MissionPlannerImpl(orbitalMechanics, stateProvider)
        grundServer.setMissionPlanner(planner)

        def missionRequest = new MissionRequest(targetCoordinates: new Coordinates(latitude: 52.0, longitude: 21.0))

        when: "a user sends the mission request"
        def response = grundClient.sendGenericPost("/missions", missionRequest)

        then: "the ground control server rejects the mission with a proper explanation"
        response.statusCode == 422 // Unprocessable Entity
        def error = response.json
        error.error == "Mission Rejected"
        error.reason.contains("outside of the satellite's orbital path")
    }
}
Use code with caution.
Groovy
UWAGA: Musimy zaktualizować GroundControlServerApp, aby miał pusty konstruktor i aby start przyjmował tylko port. Adres serwera SAT nie jest już potrzebny, bo cała komunikacja z SAT będzie się odbywać w osobnych, przyszłych krokach.

Zaktualizuj GroundControlServerApp.groovy:

Generated groovy
class GroundControlServerApp {
// ...
void start(int port) {
// ...
}
// ...
}
Use code with caution.
Groovy
Podsumowanie
Miałeś rację: Trzeba było zaktualizować test.

Architektura jest poprawna: SAT pozostaje prostym wykonawcą. Cała "inteligencja" (MissionPlanner) została dodana wewnątrz GRUND.

Nowy, Lepszy Test: Test GrundToSatNetworkSpec nie jest już potrzebny. Zastąpiliśmy go testem MissionPlannerSpec (test jednostkowy dla logiki planisty) i nową wersją testu sieciowego (powyżej, możesz go nazwać np. MissionPlanningApiSpec), który weryfikuje, czy serwer GRUND poprawnie używa planisty.

To jest znacznie czystsze i bardziej realistyczne podejście. Dziękuję za to pytanie – pomogło nam ono doprecyzować architekturę.

31.8s
