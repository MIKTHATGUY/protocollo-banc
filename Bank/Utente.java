package org.example.bancariofaccia.protocol.Bank;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.UUID;

public class Utente implements Serializable {
    private static final long serialVersionUID = 1L;
    
    String username;
    public UUID uuid;
    public HashMap<UUID, Conto> conti = new HashMap<>();
    public PublicKey publicKey;

    public Utente(String username, UUID uuid, PublicKey publicKey) {
        this.username = username;
        this.uuid = uuid;
        this.publicKey = publicKey;
    }

    private void loadConti(){

    }

    public String toString() {
        return "username: " + username + ", uuid: " + uuid;
    }
}
