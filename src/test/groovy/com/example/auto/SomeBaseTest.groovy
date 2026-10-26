package com.example.auto

import spock.lang.Specification

// Wersja Ostateczna: Klasa bazowa zawiera TYLKO wspólne, statyczne dane konfiguracyjne.
abstract class SomeBaseTest extends Specification {

    protected static final Map<String, SystemConfig> SYSTEM_CONFIGS = [
            "ALPHA": new SystemConfig(
                    "auto.alpha-partner.com", 9001, "https://auth.alpha.net/token",
                    "udp://metrics.alpha.com:5555", "cmd.alpha-partner.com", 2201,
                    "ALPHA-VALID-API-KEY"
            ),
            "BETA": new SystemConfig(
                    "beta-auto.internal.net", 19001, "http://10.0.2.15/beta_auth",
                    "tcp://10.0.2.16:6000", "10.0.2.17", 2202,
                    "BETA-SECRET-KEY-99"
            )
    ]
}