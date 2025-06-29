# Projekt_AB_Groovy
Projekt kluczowy dla mojego życia
---------------------------------


Projekt rekrutacyjny.
Mam zaimplementowac tekie rozwiazanie.
Mam miec Projekt_AB w środowisku:
- Spock version: 1.3-groovy-2.5 (from dependencies section)
- Groovy version: 2.5.4
- Java version: 1.8.0_452
- Gdadle: 5.3.1
  Niech to będzie symulacja jakiegoś dużego system z wieloma modulami - np jakis system satelitarny satelitarny - To ma byc jakis niewielki szkoleniowy projekt.
  W implementacj aplikacji Groovy w łańcuchach klas i funkcji wykorzystywane są identyczne serwery, zasoby, zmienne itd.
  To samo jest w testach.
  Potem mam zrobic jak poniżej w treści mojego zadania projektowego na rekrutacje


"
W wielu testach łańcucha funkcji wykorzystywane serwery, zasoby, zmienne itd. są identyczne.   
Obecnie każdy test zawiera własną kopię tych wspólnych elementów.
Dla łańcucha funkcji Projektu_AB (reprocesowanie) należy przeanalizować, a następnie wdrożyć, w jakim zakresie wspólne elementy
można przenieść do konkretnej klasy bazowej o nazwie SomeBaseTest.
W testach łańcucha funkcji Projektu_AB należy zbadać, które definicje, importy itp. warto umieścić w klasie bazowej.
Należy przeanalizować, które definicje, importy itd. warto umieścić w klasie bazowej dla testów łańcucha funkcjonalnego Projektu_AB.


Przykład:
groovy
class BaseSpec extends Specification {
@Shared int value = 1def setupSpec() {
println "BaseSpec.setupSpec"}
def cleanupSpec() {
println "BaseSpec.cleanupSpec"}}
class Derived extends BaseSpec {
def setupSpec() {
println "Derived.setupSpec"value = 2}
def "print value"() {
setup: "print value"println value
}
def cleanupSpec() {
println "Derived.cleanupSpec"}}
Wynik wykonania testu Derived:

Executing Spec: com.example.rest.specerrors
BaseSpec.setupSpec
Derived.setupSpec
Starting new feature print value
Feature/Iteration 1/1 : print value
SETUP  print value 2
Duration: 1ms
Derived.cleanupSpec
BaseSpec.cleanupSpec
"
