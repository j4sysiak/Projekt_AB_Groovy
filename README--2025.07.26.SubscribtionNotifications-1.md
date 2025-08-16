Doskonały pomysł! Wprowadzenie subskrypcji powiadomień to fantastyczny, naturalny krok w ewolucji tego systemu. To zmiana, która przenosi go z prostego, wewnętrznego pipeline'u do poziomu platformy, która może integrować się z innymi systemami.
To otwiera mnóstwo nowych możliwości biznesowych i technicznych.
Koncepcja Biznesowa: "System Jako Platforma Usługowa"
Obecnie Twój system działa na zasadzie "pytanie-odpowiedź". Ktoś musi aktywnie pytać: "Czy misja X jest już gotowa?".
Subskrypcje Powiadomień odwracają ten model. System staje się proaktywny. Inne systemy (lub użytkownicy) mogą powiedzieć: "Hej, systemie, po prostu daj mi znać, kiedy misja X się zakończy".
Wartość biznesowa:
Automatyzacja i Integracja: Inny system (np. system analityczny, system fakturowania klienta, archiwum długoterminowe) może automatycznie rozpocząć swoją pracę, gdy tylko otrzyma powiadomienie, że zdjęcia są gotowe. Nie musi ciągle "pytać" o status.
Monitoring w czasie rzeczywistym: Operatorzy lub menedżerowie mogą otrzymywać natychmiastowe alerty o kluczowych zdarzeniach, np. o niepowodzeniu misji lub pomyślnym jej zakończeniu.
Oddzielenie systemów (Decoupling): System, który zleca misję, nie musi być tym samym systemem, który konsumuje jej wyniki. To pozwala na budowanie bardziej złożonych i elastycznych architektur.
Proponowane Rozwiązanie Techniczne: Powiadomienia przez Webhooki
Najpopularniejszym i najbardziej elastycznym sposobem implementacji subskrypcji jest mechanizm webhooków (HTTP callbacks).
Kluczowe Komponenty
Moduł Zarządzania Subskrypcjami (w GroundControlServerApp):
Nowy endpoint API, np. POST /subscriptions.
Nowa "tabela" w bazie danych (lub w pamięci, jak FakeRelationalDB), która przechowuje subskrypcje, np. subscriptionsTable.
Każdy wpis w tabeli zawiera co najmniej:
missionId: ID misji, której dotyczy subskrypcja.
eventType: Typ zdarzenia, np. MISSION_COMPLETED, MISSION_FAILED.
callbackUrl: Adres URL, na który ma być wysłane powiadomienie (webhook).
Moduł Wysyłania Powiadomień (w GroundControlServerApp):
Serwis (NotificationService), który jest odpowiedzialny za wysyłanie żądań HTTP POST na adresy callbackUrl.
Emitowanie Zdarzeń:
Istniejący kod musi zostać zmodyfikowany, aby w kluczowych momentach "emitował zdarzenia". W praktyce oznacza to wywołanie NotificationService.
Proponowany Przepływ
Krok 1: Klient subskrybuje powiadomienie.
System zewnętrzny (lub Twój test) wysyła żądanie:
POST /subscriptions
Z ciałem (body) w formacie JSON:
Generated json
{
"missionId": "MCOL_123456789",
"eventType": "MISSION_COMPLETED",
"callbackUrl": "https://moj-system-analityczny.com/api/mission-results"
}
Use code with caution.
Json
GroundControlServerApp zapisuje tę subskrypcję w swojej bazie.
Krok 2: Występuje zdarzenie.
Cały pipeline, który razem debugowaliśmy, kończy się sukcesem.
W metodzie, która potwierdza finalny zapis danych w GrundServer (np. w endpoincie /data/ingest), dodajemy logikę:
Generated groovy
// w GroundControlServerApp, w endpoincie /data/ingest
// ... po zapisaniu zdjęć do bazy ...

// Sprawdzamy, czy cała misja jest kompletna
if (isMissionComplete(missionId)) {
// EMITUJEMY ZDARZENIE!
notificationService.publishEvent("MISSION_COMPLETED", missionId, finalData)
}
Use code with caution.
Groovy
Krok 3: System wysyła powiadomienie.
notificationService znajduje wszystkie subskrypcje dla missionId i eventType: "MISSION_COMPLETED".
Dla każdej znalezionej subskrypcji, wysyła żądanie POST na zapisany callbackUrl.
W ciele żądania wysyła szczegóły zdarzenia:
Generated json
{
"eventId": "EVT_987654321",
"eventType": "MISSION_COMPLETED",
"timestamp": "2023-10-27T10:00:00Z",
"payload": {
"missionId": "MCOL_123456789",
"photoCount": 120,
"message": "All photos for mission have been successfully processed and stored."
}
}
Use code with caution.
Json
Jak to przetestować?
To jest najlepsza część! Możesz użyć w teście fałszywego serwera, który będzie udawał system analityczny.
W teście Spocka uruchamiasz MockWebServer (popularna biblioteka do tego celu) lub nawet kolejną instancję Twojego SatelliteSimulatorServer, która będzie nasłuchiwać na powiadomienia.
W bloku given: subskrybujesz powiadomienie, podając adres URL swojego fałszywego serwera.
W bloku when: uruchamiasz cały pipeline.
W bloku then: sprawdzasz, czy Twój fałszywy serwer otrzymał żądanie POST z poprawnymi danymi powiadomienia.
To świetny, konkretny i bardzo wartościowy kierunek rozwoju. Jeśli chcesz, możemy zacząć go implementować krok po kroku, zaczynając od definicji DTO i nowego endpointu API.
