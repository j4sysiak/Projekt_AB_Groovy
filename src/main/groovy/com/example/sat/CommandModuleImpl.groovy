package com.example.sat

import groovy.transform.Canonical

@Canonical
class CommandModuleImpl implements CommandModule {
    String commandHost
    int commandPort

    @Override
    String sendCommand(String command, Map params ) {
        println "COMMAND to [${commandHost}:${commandPort}]: Sending '${command}'"
        return "ACK for command: ${command}"
    }
}