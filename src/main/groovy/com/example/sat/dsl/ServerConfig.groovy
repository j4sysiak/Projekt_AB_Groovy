package com.example.sat.dsl

import groovy.transform.Canonical

/**
 * Prosta klasa-kontener (DTO) do przechowywania konfiguracji serwera
 * zdefiniowanej w bloku closure w teście.
 */
@Canonical
class ServerConfig {
    String host
    String defaultRequestMediaType
    String defaultRespondMediaType
}