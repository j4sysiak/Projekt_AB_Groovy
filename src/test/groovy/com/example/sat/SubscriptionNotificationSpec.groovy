// src/test/groovy/com/example/sat/SubscriptionNotificationSpec.groovy
package com.example.sat

import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.PhotoTask
import com.example.sat.dto.PhotoTaskStatus
import com.example.sat.dto.SubscriptionRequest
import com.example.sat.http.SimpleHttpClient
import com.example.sat.server.GroundControlServerApp
import groovy.json.JsonSlurper
import okhttp3.mockwebserver.MockResponse      // <-- NOWY IMPORT
import okhttp3.mockwebserver.MockWebServer      // <-- NOWY IMPORT
import spock.lang.Shared
import spock.lang.Specification
import java.util.concurrent.TimeUnit           // <-- NOWY IMPORT

class SubscriptionNotificationSpec extends Specification {

    @Shared GroundControlServerApp grundServer
    @Shared SimpleHttpClient grundClient

    // ZMIANA: Zamiast drugiej instancji naszej aplikacji, używamy dedykowanego MockWebServer
    @Shared MockWebServer webhookReceiverServer


    def setupSpec() {
        // 1. Uruchom serwer GRUND
        grundServer = new GroundControlServerApp()
        grundServer.start(0, "") // Nie potrzebuje satelity
        grundClient = new SimpleHttpClient("http://localhost:${grundServer.getPort()}")

        // 2. Stwórz i uruchom MockWebServer
        webhookReceiverServer = new MockWebServer()
        webhookReceiverServer.start() // Uruchomi się na losowym, wolnym porcie
    }

    def cleanupSpec() {
        grundServer?.stop()
        // ZMIANA: Zamykamy MockWebServer za pomocą jego metody shutdown()
        webhookReceiverServer?.shutdown()
    }

    def cleanup() {
        grundServer.resetState()
        // Nie musimy już czyścić listy, bo MockWebServer sam zarządza stanem
    }

    def "should receive a notification when a subscribed mission is completed"() {
        given: "a mission is planned and a subscription is created"
        // "Programujemy" odpowiedź naszego fałszywego (mockowego ) serwera.
        // Mówimy mu: "Na następne żądanie, które otrzymasz, odpowiedz statusem 200 OK".
        webhookReceiverServer.enqueue(new MockResponse().setResponseCode(200).setBody("Notification received"))

        // Przygotowanie danych w grundServer - bez zmian
        def testTask = new PhotoTask(taskId: 'T1', status: PhotoTaskStatus.PENDING)
        def missionManifest = new PhotoCollectionManifest(
                missionId: 'M-123',
                tasks: [testTask],
                status: "UPLOADED"
        )
        grundServer.missionManifests['M-123'] = missionManifest

        // Tworzymy subskrypcję, podając adres URL naszego MockWebServer
        def subRequest = new SubscriptionRequest(
                missionId: 'M-123',
                eventType: 'MISSION_COMPLETED',
                // ZMIANA: Używamy metody .url(), aby uzyskać pełny adres MockWebServer
                callbackUrl: webhookReceiverServer.url("/webhook-endpoint").toString()
        )
        grundClient.sendGenericPost("/subscriptions", subRequest)

        when: "the final data for the mission arrives at ground control"
        // Dane testowe - bez zmian, ale upewnijmy się, że są kompletne
        def finalPhotoData = [[
                                      missionId: 'M-123',
                                      photoId: 'T1',
                                      content: 'Test content',
                                      timestamp: System.currentTimeMillis(),
                                      quality: 99
                              ]]
        grundClient.sendGenericPost("/data/ingest", finalPhotoData)

        then: "the webhook receiver gets a correct POST request"
        // ZMIANA: Zamiast Awaitility, używamy wbudowanej metody MockWebServer,
        // która czeka na żądanie (z timeoutem dla bezpieczeństwa).
        def receivedRequest = webhookReceiverServer.takeRequest(5, TimeUnit.SECONDS)

        and: "the received request has correct properties"
        receivedRequest != null // Upewniamy się, że żądanie w ogóle dotarło
        receivedRequest.method == "POST"
        receivedRequest.path == "/webhook-endpoint"

        def notification = new JsonSlurper().parseText(receivedRequest.getBody().readUtf8())
        notification.eventType == "MISSION_COMPLETED"
        notification.data.missionId == "M-123"
        notification.data.photoCount == 1
    }
}


/*
Historia, którą opowiada ten test
Cel testu: Chcemy się upewnić, że jeśli zamówimy prenumeratę powiadomień o ukończeniu misji, to gdy misja faktycznie się zakończy, nasza redakcja (grundServer) wyśle nam listonosza (webhook) z tą informacją.

Krok 1: given: - Przygotowanie sceny
W tej części przygotowujemy wszystkich aktorów i rekwizyty.

  1. webhookReceiverServer.enqueue(new MockResponse()...)
     Co to robi? - To jest najważniejsza nowa część. MockWebServer działa jak skrzynka na listy. To polecenie mówi: "Przygotuj tę skrzynkę. Gdy następnym razem listonosz wrzuci do niej list, skrzynka ma po prostu odpowiedzieć 'OK, odebrałem' (setResponseCode(200))." Programujemy z góry reakcję naszej skrzynki.
     Analogia: - Ustawiamy automatyczną odpowiedź na naszej skrzynce pocztowej.

  2. def missionManifest = new PhotoCollectionManifest(...) i grundServer.missionManifests['M-123'] = ...
     Co to robi? - Informujemy naszą redakcję (grundServer), że istnieje misja (wydanie gazety) o numerze M-123 i że będzie ona kompletna, gdy będzie miała 1 artykuł (zadanie).
     Analogia: - Redakcja tworzy w swoim systemie plan nowego wydania gazety.

  3. def subRequest = new SubscriptionRequest(...)
     Co to robi? - Tworzymy "formularz zamówienia prenumeraty". Mówimy w nim:
                                  missionId: 'M-123' (Chcę info o tej konkretnej gazecie)
                                  eventType: 'MISSION_COMPLETED' (Dajcie znać, jak już cała gazeta będzie gotowa)
                                  callbackUrl: webhookReceiverServer.url(...) (Oto adres mojej skrzynki na listy, na którą macie wysłać powiadomienie). webhookReceiverServer.url(...) pobiera dynamiczny adres, na którym działa nasza fałszywa skrzynka.
     Analogia: - Wypełniamy formularz prenumeraty, podając nasz adres.

  4. grundClient.sendGenericPost("/subscriptions", subRequest)
     Co to robi? - Wysyłamy nasz formularz prenumeraty do redakcji.
     Analogia: - Wrzucamy wypełniony formularz do skrzynki pocztowej i wysyłamy go do redakcji.

   Stan po given::
   Redakcja (grundServer) wie o misji M-123 i ma zapisane w swojej bazie, że my (pod adresem naszej skrzynki MockWebServer)
   chcemy dostać powiadomienie, gdy ta misja się zakończy.


Krok 2: when: - Główna akcja
Tutaj dzieje się to, co ma wywołać nasze powiadomienie.

  1. def finalPhotoData = [...]
     Co to robi? - Tworzymy "ostatni artykuł" potrzebny do ukończenia gazety (misji).
     Analogia: -  Dziennikarz kończy pisać ostatni tekst.

  2. grundClient.sendGenericPost("/data/ingest", finalPhotoData)
     Co to robi? Wysyłamy ten ostatni artykuł do redakcji (grundServer).
     Analogia: Dziennikarz wysyła gotowy tekst do redakcji.
               W tym momencie redakcja patrzy na swoje notatki i mówi: "Super, mamy już wszystkie artykuły do gazety M-123!
               Czas powiadomić prenumeratorów!".
               W tle uruchamia się NotificationService.
     Stan po when:: Redakcja (grundServer) odebrała ostatnie dane, uznała misję za zakończoną i wysłała listonosza (webhooka) z powiadomieniem na adres, który podaliśmy w prenumeracie.


Krok 3: then: i and: - Sprawdzenie rezultatów
Teraz sprawdzamy, czy wszystko zadziałało jak należy.

    1. def receivedRequest = webhookReceiverServer.takeRequest(5, TimeUnit.SECONDS)
       Co to robi? - To jest kluczowy moment. Idziemy do naszej skrzynki na listy (MockWebServer) i mówimy:
       "Poczekaj maksymalnie 5 sekund i daj mi pierwszy list, który do ciebie wpadnie".
       Ta metoda jest blokująca – test zatrzyma się w tym miejscu i będzie czekał, aż listonosz (grundServer) dostarczy powiadomienie.
       Analogia: - Czekamy przy skrzynce na listonosza.

    2. receivedRequest != null, receivedRequest.method == "POST" itd.
       Co to robi? - Sprawdzamy, czy w ogóle coś dostaliśmy i czy listonosz użył poprawnej metody dostarczenia (POST) i wrzucił list do właściwej przegródki (/webhook-endpoint).
       Analogia:  - Sprawdzamy, czy listonosz faktycznie przyszedł.

    3. def notification = new JsonSlurper()...
       Co to robi? - Otwieramy kopertę i czytamy treść listu. getBody().readUtf8() wyciąga tekst z listu, a JsonSlurper zamienia go na obiekt, który możemy łatwo czytać.
       Analogia: - Otwieramy list.

    4. notification.eventType == "MISSION_COMPLETED" itd.
       Co to robi? - Sprawdzamy, czy w liście jest napisane dokładnie to, czego oczekiwaliśmy: "Misja M-123 została zakończona".
       Analogia: - Czytamy treść listu i sprawdzamy, czy zgadza się z naszym zamówieniem.

   W pigułce
GIVEN: Mówimy redakcji, że ma przygotować gazetę i że chcemy dostać powiadomienie na adres naszej skrzynki, gdy będzie gotowa.
WHEN: Wysyłamy do redakcji ostatni artykuł, co kończy pracę nad gazetą.
THEN: Czekamy przy naszej skrzynce, odbieramy list od listonosza, otwieramy go i sprawdzamy, czy jest w nim poprawna informacja.
Mam nadzieję, że ta analogia pomogła rozjaśnić, co dzieje się w tym teście. To naprawdę potężny sposób na weryfikację skomplikowanych interakcji

 */