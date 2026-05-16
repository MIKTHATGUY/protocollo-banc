package org.example.bancariofaccia.protocol.Messaggi.MessageFactory;

import org.example.bancariofaccia.protocol.Messaggi.*;
import org.example.bancariofaccia.protocol.Bank.Bban;
import org.example.bancariofaccia.protocol.PreciseMoney.Money;
import org.example.bancariofaccia.protocol.UTILS.CryptoUtils;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.UUID;

public class MessageFactoryClient extends MessageFactory {

    public MessageFactoryClient(Connection connection) {
        super(connection);
    }

    @Override
    public void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public Messaggio CreaMessaggioDiRichiestaCreazioneAccount(String nome, PublicKey publicKey) {

        sequenceNumber++;
        return new Messaggio()
                .withOpCode(OpCode.ACCOUNT_CREATION)
                .withFlag(Flag.NO_AUTH)
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.USERNAME_TO_CREATE, WireType.STRING, nome)
                .addField(FieldID.PUBLIC_KEY, WireType.PUBLIC_KEY, publicKey);

    }

    public Messaggio informazioneFirmato(UUID user_uuid) throws InvalidKeyException {
        sequenceNumber++;
        Messaggio req =  new Messaggio()
                .withFlag(Flag.RESPONSE)
                .withOpCode(OpCode.CONFIRMATION)
                .withAuth(user_uuid, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.ERROR_MESSAGE, WireType.STRING, "Ciao");

        if(privateKey == null) {
            throw  new InvalidKeyException("Private key null");
        }
        CryptoUtils.signMessage(req, privateKey);

        return req;
    }

    public Messaggio createTransferRequest(UUID user_uuid, Bban targetAccount, Money amount, String currency, String reason) throws InvalidKeyException {
        if (targetAccount == null) {
            throw new IllegalArgumentException("targetAccount cannot be null");
        }
        sequenceNumber++;
        Messaggio req = new Messaggio()
                .withFlag(Flag.REQUEST)
                .withOpCode(OpCode.TRANSFER)
                .withAuth(user_uuid, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.TARGET_ACCOUNT, WireType.BBAN, targetAccount)
                .addField(FieldID.AMOUNT, WireType.MONEY, amount);
        
        if (currency != null && !currency.isEmpty()) {
            req.addField(FieldID.CURRENCY, WireType.STRING, currency);
        }
        if (reason != null && !reason.isEmpty()) {
            req.addField(FieldID.REASON, WireType.STRING, reason);
        }

        if(privateKey == null) {
            throw new InvalidKeyException("Private key null");
        }
        CryptoUtils.signMessage(req, privateKey);

        return req;
    }

    public Messaggio createBalanceRequest(UUID user_uuid, Bban accountBban) throws InvalidKeyException {
        sequenceNumber++;
        Messaggio req = new Messaggio()
                .withFlag(Flag.REQUEST)
                .withOpCode(OpCode.BALANCE)
                .withAuth(user_uuid, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis());
                
        if (accountBban != null) {
            req.addField(FieldID.BBAN, WireType.BBAN, accountBban);
        }

        if(privateKey == null) {
            throw new InvalidKeyException("Private key null");
        }
        CryptoUtils.signMessage(req, privateKey);

        return req;
    }

    // ── New factory methods for GUI integration ─────────────────────────────────

    /**
     * Request login — sends UUID + signed challenge to prove identity.
     */
    public Messaggio createLoginRequest(UUID user_uuid, byte[] signedChallenge) throws InvalidKeyException {
        sequenceNumber++;
        Messaggio req = new Messaggio()
                .withFlag(Flag.REQUEST)
                .withOpCode(OpCode.LOGIN)
                .withAuth(user_uuid, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis());

        if (signedChallenge != null) {
            req.addField(FieldID.SIGNED_CHALLENGE, WireType.BYTE_ARRAY, signedChallenge);
        }

        if (privateKey == null) {
            throw new InvalidKeyException("Private key null");
        }
        CryptoUtils.signMessage(req, privateKey);
        return req;
    }

    /**
     * Request transaction history for a specific account.
     */
    public Messaggio createTransactionHistoryRequest(UUID user_uuid, Bban accountBban) throws InvalidKeyException {
        sequenceNumber++;
        Messaggio req = new Messaggio()
                .withFlag(Flag.REQUEST)
                .withOpCode(OpCode.TRANSACTION_HISTORY)
                .withAuth(user_uuid, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.BBAN, WireType.BBAN, accountBban);

        if (privateKey == null) {
            throw new InvalidKeyException("Private key null");
        }
        CryptoUtils.signMessage(req, privateKey);
        return req;
    }

    /**
     * Request creation of an additional account under the existing user.
     */
    public Messaggio createNewAccountRequest(UUID user_uuid) throws InvalidKeyException {
        sequenceNumber++;
        Messaggio req = new Messaggio()
                .withFlag(Flag.REQUEST)
                .withOpCode(OpCode.CREATE_ACCOUNT)
                .withAuth(user_uuid, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis());

        if (privateKey == null) {
            throw new InvalidKeyException("Private key null");
        }
        CryptoUtils.signMessage(req, privateKey);
        return req;
    }

    /**
     * Request validation of a target BBAN.
     */
    public Messaggio createValidateBbanRequest(UUID user_uuid, Bban targetBban) throws InvalidKeyException {
        sequenceNumber++;
        Messaggio req = new Messaggio()
                .withFlag(Flag.REQUEST)
                .withOpCode(OpCode.VALIDATE_BBAN)
                .withAuth(user_uuid, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.BBAN, WireType.BBAN, targetBban);

        if (privateKey == null) {
            throw new InvalidKeyException("Private key null");
        }
        CryptoUtils.signMessage(req, privateKey);
        return req;
    }

    /**
     * Request a list of all accounts belonging to the user.
     */
    public Messaggio createFetchAccountsRequest(UUID user_uuid) throws InvalidKeyException {
        sequenceNumber++;
        Messaggio req = new Messaggio()
                .withFlag(Flag.REQUEST)
                .withOpCode(OpCode.FETCH_ACCOUNTS)
                .withAuth(user_uuid, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis());

        if (privateKey == null) {
            throw new InvalidKeyException("Private key null");
        }
        CryptoUtils.signMessage(req, privateKey);
        return req;
    }

    // ── New factory methods for extended operations ───────────────────────────

    public Messaggio createCertificateRequest(UUID user_uuid, Bban accountBban) throws InvalidKeyException {
        sequenceNumber++;
        Messaggio req = new Messaggio()
                .withFlag(Flag.REQUEST)
                .withOpCode(OpCode.REQUEST_CERTIFICATE)
                .withAuth(user_uuid, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.BBAN, WireType.BBAN, accountBban);

        if (privateKey == null) {
            throw new InvalidKeyException("Private key null");
        }
        CryptoUtils.signMessage(req, privateKey);
        return req;
    }

    public Messaggio createRenameAccountRequest(UUID user_uuid, Bban accountBban, String newName) throws InvalidKeyException {
        sequenceNumber++;
        Messaggio req = new Messaggio()
                .withFlag(Flag.REQUEST)
                .withOpCode(OpCode.RENAME_ACCOUNT)
                .withAuth(user_uuid, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.BBAN, WireType.BBAN, accountBban)
                .addField(FieldID.ACCOUNT_NAME, WireType.STRING, newName);

        if (privateKey == null) {
            throw new InvalidKeyException("Private key null");
        }
        CryptoUtils.signMessage(req, privateKey);
        return req;
    }

    public Messaggio createSetAccountStatusRequest(UUID user_uuid, Bban accountBban, String newStatus) throws InvalidKeyException {
        sequenceNumber++;
        Messaggio req = new Messaggio()
                .withFlag(Flag.REQUEST)
                .withOpCode(OpCode.SET_ACCOUNT_STATUS)
                .withAuth(user_uuid, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis())
                .addField(FieldID.BBAN, WireType.BBAN, accountBban)
                .addField(FieldID.ACCOUNT_STATUS, WireType.STRING, newStatus);

        if (privateKey == null) {
            throw new InvalidKeyException("Private key null");
        }
        CryptoUtils.signMessage(req, privateKey);
        return req;
    }

    public Messaggio createExchangeRatesRequest(UUID user_uuid) throws InvalidKeyException {
        sequenceNumber++;
        Messaggio req = new Messaggio()
                .withFlag(Flag.REQUEST)
                .withOpCode(OpCode.FETCH_EXCHANGE_RATES)
                .withAuth(user_uuid, sequenceNumber, connection.getSessionNonce())
                .addField(FieldID.TIMESTAMP, WireType.LONG, System.currentTimeMillis());

        if (privateKey == null) {
            throw new InvalidKeyException("Private key null");
        }
        CryptoUtils.signMessage(req, privateKey);
        return req;
    }
}