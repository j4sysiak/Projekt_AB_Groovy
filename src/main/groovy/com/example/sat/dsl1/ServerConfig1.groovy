package com.example.sat.dsl1

import groovy.transform.Canonical

/**
 * Prosta klasa-kontener (DTO) do przechowywania konfiguracji serwera
 * zdefiniowanej w bloku closure w teście.
 */
@Canonical
class ServerConfig1 {
    String host
    String defaultRequestMediaType
    String defaultRespondMediaType
}