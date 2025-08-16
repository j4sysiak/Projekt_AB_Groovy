package com.example.sat.http

interface INotificationClient {
    void send(String url, Object payload)
}