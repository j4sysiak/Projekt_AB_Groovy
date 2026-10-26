package com.example.auto

import com.example.auto.domain.Coordinates

// Interfejs dla modułu, który oblicza pozycję auto
interface IOrbitalMechanics {

    Coordinates getCurrentPosition()
}