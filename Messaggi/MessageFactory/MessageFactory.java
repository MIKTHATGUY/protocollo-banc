package org.example.bancariofaccia.protocol.Messaggi.MessageFactory;

import org.example.bancariofaccia.protocol.Messaggi.Connection;

import java.security.PrivateKey;

public abstract class MessageFactory {

    protected Connection connection;
    protected int sequenceNumber = 1;
    protected PrivateKey privateKey;

    MessageFactory(Connection connection) {
        this.connection = connection;
    }

    MessageFactory(Connection connection, PrivateKey privateKey) {
        this.connection = connection;
        this.privateKey = privateKey;
    }

    protected void incrementSequence() {
        sequenceNumber++;
    }

    public void setPrivateKey(PrivateKey privateKey){
        this.privateKey = privateKey;
    };
}