// src/main/groovy/com/example/sat/dto/PhotoData.groovy
package com.example.sat.dto

import groovy.transform.Canonical

/**
 * Reprezentuje pojedyncze zdjęcie wraz z jego metadanymi.
 * Używa @Canonical do automatycznego generowania konstruktorów, equals, hashCode i toString.
 */
@Canonical
class PhotoData {
    // Pola nie powinny mieć domyślnych, dynamicznych wartości, 
    // aby uniknąć przypadkowego nadpisywania danych.
    // Wartości powinny być zawsze dostarczane przez konstruktor.

    String photoId
    String missionId
    String content
    long timestamp
    int quality
}