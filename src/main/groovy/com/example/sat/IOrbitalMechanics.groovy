package com.example.sat

import com.example.sat.domain.Coordinates

// Interfejs dla modułu, który oblicza pozycję satelity
interface IOrbitalMechanics {

    Coordinates getCurrentPosition()
}