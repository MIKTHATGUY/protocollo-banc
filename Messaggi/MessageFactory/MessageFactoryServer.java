package org.example.bancariofaccia.protocol.Messaggi.MessageFactory;

import org.example.bancariofaccia.protocol.Bank.*;
import org.example.bancariofaccia.protocol.Messaggi.*;
import org.example.bancariofaccia.protocol.PreciseMoney.Money;
import org.example.bancariofaccia.protocol.UTILS.CryptoUtils;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.util.List;
import java.util.UUID;

public class MessageFactoryServer extends MessageFactory {
    Bank bank;

    public MessageFactoryServer(Connection connection, Bank bank) {
        super(connection, bank.privateKey);
        this.bank = bank;
    }

    public Messaggio creaMessaggioDiCOnfermaRegistrazione(String messaggio, Utente utente) {
        Messaggio m = new Messaggio()
                .withFlag(Flag.RESPONSE)
                .withOpCode(OpCode.REGISTRATION_CONFIRMATION)
                .withAuth(UUID.randomUUID(), sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.CONFIRMATION_MESSAGE, WireType.STRING, messaggio)
                .addField(FieldID.UUID, WireType.STRING, utente.uuid.toString());

        CryptoUtils.signMessage(m, privateKey);
        return m;
    }

    public Messaggio creaMessaggioConferma(String messaggio, UUID userUUID) {
        Messaggio m = new Messaggio()
                .withFlag(Flag.RESPONSE)
                .withOpCode(OpCode.REGISTRATION_CONFIRMATION)
                .withAuth(UUID.randomUUID(), sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.CONFIRMATION_MESSAGE, WireType.STRING, messaggio)
                .addField(FieldID.UUID, WireType.STRING, userUUID.toString());

        CryptoUtils.signMessage(m, privateKey);
        return m;
    }

    public Messaggio createTransferResponse(UUID userUUID, String confirmationMessage) {
        sequenceNumber++;
        Messaggio m = new Messaggio()
                .withFlag(Flag.RESPONSE)
                .withOpCode(OpCode.TRANSFER)
                .withAuth(userUUID, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.CONFIRMATION_MESSAGE, WireType.STRING, confirmationMessage);

        CryptoUtils.signMessage(m, privateKey);
        return m;
    }

    public Messaggio createBalanceResponse(UUID userUUID, Money amount, String currency, Bban bban) {
        sequenceNumber++;
        Messaggio m = new Messaggio()
                .withFlag(Flag.RESPONSE)
                .withOpCode(OpCode.BALANCE)
                .withAuth(userUUID, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.AMOUNT, WireType.MONEY, amount);
                
        if (currency != null && !currency.isEmpty()) {
            m.addField(FieldID.CURRENCY, WireType.STRING, currency);
        }
        if (bban != null) {
            m.addField(FieldID.BBAN, WireType.BBAN, bban);
        }

        CryptoUtils.signMessage(m, privateKey);
        return m;
    }

    public Messaggio createErrorResponse(UUID userUUID, OpCode originalOpCode, long errorCode, String errorMessage) {
        sequenceNumber++;
        Messaggio m = new Messaggio()
                .withFlag(Flag.ERROR)
                .withOpCode(originalOpCode)
                .withAuth(userUUID, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.ERROR_CODE, WireType.LONG, errorCode)
                .addField(FieldID.ERROR_MESSAGE, WireType.STRING, errorMessage);

        CryptoUtils.signMessage(m, privateKey);
        return m;
    }

    // ── New response factories for GUI integration ──────────────────────────────

    /**
     * Login response — confirms identity and returns UUID.
     */
    public Messaggio createLoginResponse(UUID userUUID) {
        sequenceNumber++;
        Messaggio m = new Messaggio()
                .withFlag(Flag.RESPONSE)
                .withOpCode(OpCode.LOGIN)
                .withAuth(userUUID, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.UUID, WireType.STRING, userUUID.toString())
                .addField(FieldID.CONFIRMATION_MESSAGE, WireType.STRING, "Login riuscito");

        CryptoUtils.signMessage(m, privateKey);
        return m;
    }

    /**
     * Transaction history response — serializes each transaction as a set of string fields.
     * Format: each transaction is encoded as a pipe-separated string:
     * "timestamp|direction|counterpartBban|currency|amount"
     */
    public Messaggio createTransactionHistoryResponse(UUID userUUID, List<Transaction> transactions) {
        sequenceNumber++;
        Messaggio m = new Messaggio()
                .withFlag(Flag.RESPONSE)
                .withOpCode(OpCode.TRANSACTION_HISTORY)
                .withAuth(userUUID, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.ACCOUNT_COUNT, WireType.INT, transactions.size());

        // Serialize transactions as a single delimited string
        StringBuilder sb = new StringBuilder();
        for (Transaction tx : transactions) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(tx.getTimestamp()).append("|");
            sb.append(tx.getDirection()).append("|");
            // Encode counterpart BBAN as hex string
            Bban counterpart = "IN".equals(tx.getDirection()) ? tx.getBbanOrigine() : tx.getBbanDestinazione();
            sb.append(bbanToString(counterpart)).append("|");
            sb.append(tx.getCurrency()).append("|");
            sb.append(tx.getAmount().getAmount().toPlainString());
        }
        if (sb.length() > 0) {
            m.addField(FieldID.TRANSACTION_DATA, WireType.STRING, sb.toString());
        }

        CryptoUtils.signMessage(m, privateKey);
        return m;
    }

    /**
     * Account creation response — returns new account details.
     */
    public Messaggio createNewAccountResponse(UUID userUUID, Conto newConto) {
        sequenceNumber++;
        Bban bban = bank.makeBBANforConto(newConto);
        Messaggio m = new Messaggio()
                .withFlag(Flag.RESPONSE)
                .withOpCode(OpCode.CREATE_ACCOUNT)
                .withAuth(userUUID, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.BBAN, WireType.BBAN, bban)
                .addField(FieldID.AMOUNT, WireType.MONEY, newConto.getContenuto())
                .addField(FieldID.ACCOUNT_STATUS, WireType.STRING, newConto.getStatus())
                .addField(FieldID.CONFIRMATION_MESSAGE, WireType.STRING, "Conto creato");

        CryptoUtils.signMessage(m, privateKey);
        return m;
    }

    /**
     * BBAN validation response.
     */
    public Messaggio createValidateBbanResponse(UUID userUUID, boolean valid) {
        sequenceNumber++;
        Messaggio m = new Messaggio()
                .withFlag(Flag.RESPONSE)
                .withOpCode(OpCode.VALIDATE_BBAN)
                .withAuth(userUUID, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.VALID, WireType.INT, valid ? 1 : 0);

        CryptoUtils.signMessage(m, privateKey);
        return m;
    }

    /**
     * Fetch all accounts response — serializes each account as delimited string.
     * Format per account: "bban_hex|balance|currency|status"
     */
    public Messaggio createFetchAccountsResponse(UUID userUUID, List<Conto> conti) {
        sequenceNumber++;
        Messaggio m = new Messaggio()
                .withFlag(Flag.RESPONSE)
                .withOpCode(OpCode.FETCH_ACCOUNTS)
                .withAuth(userUUID, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.ACCOUNT_COUNT, WireType.INT, conti.size());

        StringBuilder sb = new StringBuilder();
        for (Conto c : conti) {
            if (sb.length() > 0) sb.append("\n");
            Bban bban = bank.makeBBANforConto(c);
            sb.append(bbanToString(bban)).append("|");
            sb.append(c.getContenuto().getAmount().toPlainString()).append("|");
            sb.append(c.getContenuto().getCurrency().getCurrencyCode()).append("|");
            sb.append(c.getStatus() != null ? c.getStatus() : "active").append("|");
            sb.append(c.getAlias() != null ? c.getAlias() : "");
        }
        if (sb.length() > 0) {
            m.addField(FieldID.ACCOUNT_DATA, WireType.STRING, sb.toString());
        }

        CryptoUtils.signMessage(m, privateKey);
        return m;
    }

    // ── New response factories for extended operations ────────────────────────

    public Messaggio createCertificateResponse(UUID userUUID, String certificateText) {
        sequenceNumber++;
        Messaggio m = new Messaggio()
                .withFlag(Flag.RESPONSE)
                .withOpCode(OpCode.REQUEST_CERTIFICATE)
                .withAuth(userUUID, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.FILE_PAYLOAD, WireType.STRING, certificateText);

        CryptoUtils.signMessage(m, privateKey);
        return m;
    }

    public Messaggio createRenameAccountResponse(UUID userUUID) {
        sequenceNumber++;
        Messaggio m = new Messaggio()
                .withFlag(Flag.RESPONSE)
                .withOpCode(OpCode.RENAME_ACCOUNT)
                .withAuth(userUUID, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.CONFIRMATION_MESSAGE, WireType.STRING, "Account renamed");

        CryptoUtils.signMessage(m, privateKey);
        return m;
    }

    public Messaggio createSetAccountStatusResponse(UUID userUUID, String newStatus) {
        sequenceNumber++;
        Messaggio m = new Messaggio()
                .withFlag(Flag.RESPONSE)
                .withOpCode(OpCode.SET_ACCOUNT_STATUS)
                .withAuth(userUUID, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.ACCOUNT_STATUS, WireType.STRING, newStatus)
                .addField(FieldID.CONFIRMATION_MESSAGE, WireType.STRING, "Account status updated");

        CryptoUtils.signMessage(m, privateKey);
        return m;
    }

    public Messaggio createExchangeRatesResponse(UUID userUUID, String ratesPayload) {
        sequenceNumber++;
        Messaggio m = new Messaggio()
                .withFlag(Flag.RESPONSE)
                .withOpCode(OpCode.FETCH_EXCHANGE_RATES)
                .withAuth(userUUID, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.EXCHANGE_RATES_PAYLOAD, WireType.STRING, ratesPayload);

        CryptoUtils.signMessage(m, privateKey);
        return m;
    }

    // ── Helper ──────────────────────────────────────────────────────────────────

    /**
     * Convert a Bban to a string representation: "contoUUID-proprietarioUUID-bancaUUID"
     */
    private String bbanToString(Bban bban) {
        return bban.getId_conto().toString() + "-" +
               bban.getId_proprietario().toString() + "-" +
               bban.getId_banca().toString();
    }
}