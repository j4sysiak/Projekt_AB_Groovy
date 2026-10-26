package com.example.auto.http

interface INotificationClient {
    void send(String url, Object payload)
}