package org.example.bancariofaccia.protocol.Bank;

import org.example.bancariofaccia.protocol.PreciseMoney.Money;

import java.io.Serializable;
import java.util.UUID;

/**
 * Records a single transfer between two accounts.
 * Stored inside each {@link Conto} for transaction history.
 */
public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final UUID contoOrigine;
    private final UUID contoDestinazione;
    private final Bban bbanOrigine;
    private final Bban bbanDestinazione;
    private final Money amount;
    private final String currency;
    private final String reason;
    private final long timestamp;
    private final String direction; // "IN" or "OUT" relative to the owning Conto

    public Transaction(UUID contoOrigine, UUID contoDestinazione,
                       Bban bbanOrigine, Bban bbanDestinazione,
                       Money amount, String currency, String reason,
                       long timestamp, String direction) {
        this.id = UUID.randomUUID();
        this.contoOrigine = contoOrigine;
        this.contoDestinazione = contoDestinazione;
        this.bbanOrigine = bbanOrigine;
        this.bbanDestinazione = bbanDestinazione;
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
        this.timestamp = timestamp;
        this.direction = direction;
    }

    public UUID getId()                 { return id; }
    public UUID getContoOrigine()       { return contoOrigine; }
    public UUID getContoDestinazione()  { return contoDestinazione; }
    public Bban getBbanOrigine()        { return bbanOrigine; }
    public Bban getBbanDestinazione()   { return bbanDestinazione; }
    public Money getAmount()            { return amount; }
    public String getCurrency()         { return currency; }
    public String getReason()           { return reason; }
    public long getTimestamp()          { return timestamp; }
    public String getDirection()        { return direction; }

    @Override
    public String toString() {
        return "Transaction{" + direction + " " + amount + " " + currency +
               " from=" + contoOrigine + " to=" + contoDestinazione + "}";
    }
}
