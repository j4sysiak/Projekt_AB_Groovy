package com.example.sat.simulation

import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.PhotoData

/**
 * Główny, kompletny kontrakt dla dowolnej implementacji symulatora satelity.
 *
 * Łączy w sobie wszystkie wymagane role, które satelita musi pełnić w systemie:
 * - Zarządzanie połączeniem (`SatelliteConnection`)
 * - Wykonywanie komend (`CommandModule`)
 * - Zarządzanie nowymi misjami fotograficznymi.
 *
 * Klasy serwerów (jak `SatelliteSimulatorServer`) powinny zależeć od tego interfejsu,
 * a nie od konkretnej implementacji (jak `fakes.SatelliteSimulator`).
 */
interface ISatelliteSimulator extends SatelliteConnection, CommandModule {

    /**
     * Zapisuje nowy plan misji (manifest) w pamięci symulatora i przygotowuje
     * zadania do wykonania.
     * @param manifest Obiekt manifestu zawierający listę zadań.
     */
    void storeMissionPlan(PhotoCollectionManifest manifest)

    /**
     * Zwraca listę gotowych do wysłania zdjęć (w paczce) i zmienia status
     * odpowiednich zadań na "DOWNLINKING".
     * @return Lista obiektów PhotoData gotowych do wysłania.
     */
    List<PhotoData> downlinkQueuedPhotos()
}