package org.example.bancariofaccia.protocol.Bank;

import org.example.bancariofaccia.protocol.PreciseMoney.Money;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import java.math.BigDecimal;

public class Conto implements Serializable {
    private static final long serialVersionUID = 1L;
    
    UUID id;
    Utente proprietario;
    String username;
    Money contenuto;
    String status;
    String alias;
    List<Transaction> transazioni;

    public Conto(Utente proprietario) {
        this.id = UUID.randomUUID();
        this.proprietario = proprietario;
        if (proprietario != null) {
            this.username = proprietario.username;
        }
        this.contenuto = new Money(new BigDecimal("0.00"), Locale.ITALY);
        this.status = "active";
        this.transazioni = new ArrayList<>();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Utente getProprietario() {
        return proprietario;
    }

    public void setProprietario(Utente proprietario) {
        this.proprietario = proprietario;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Money getContenuto() {
        return contenuto;
    }

    public void setContenuto(Money contenuto) {
        this.contenuto = contenuto;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public List<Transaction> getTransazioni() {
        return transazioni;
    }

    public void addTransaction(Transaction tx) {
        if (this.transazioni == null) {
            this.transazioni = new ArrayList<>();
        }
        this.transazioni.add(tx);
    }
}
