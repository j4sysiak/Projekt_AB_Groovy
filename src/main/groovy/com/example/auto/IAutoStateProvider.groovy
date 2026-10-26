package com.example.auto

import com.example.auto.domain.AutoState

interface IAutoStateProvider {
    AutoState getCurrentState()
}