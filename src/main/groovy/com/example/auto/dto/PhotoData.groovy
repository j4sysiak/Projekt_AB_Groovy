package com.example.auto.dto

import groovy.transform.Canonical

@Canonical
class PhotoData {
    String photoId
    String actionId
    String content
    long timestamp
    int quality
}