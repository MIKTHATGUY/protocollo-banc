package org.example.bancariofaccia.protocol.Messaggi;

import org.example.bancariofaccia.protocol.Bank.Bank;
import org.example.bancariofaccia.protocol.Bank.Bban;
import org.example.bancariofaccia.protocol.Bank.Conto;
import org.example.bancariofaccia.protocol.Bank.Transaction;
import org.example.bancariofaccia.protocol.Bank.Utente;
import org.example.bancariofaccia.protocol.Messaggi.MessageFactory.MessageFactoryServer;
import org.example.bancariofaccia.protocol.PreciseMoney.Money;
import org.example.bancariofaccia.protocol.UTILS.CryptoUtils;

import java.io.IOException;
import java.security.PublicKey;
import java.util.List;
import java.util.UUID;

public class ClientHandler implements Runnable {
    Connection connection;
    Bank bank;

    public ClientHandler(Connection conn, Bank bank) {
        this.connection = conn;
        this.bank = bank;
    }

    @Override
    public void run() {
        try {
            MessageFactoryServer factory = new MessageFactoryServer(connection, bank);

            while (true) {
                Messaggio received = connection.receive();

                if (received == null) {
                    System.out.println("Client disconnected.");
                    return;
                }

                System.out.println("\n[SERVER] Ricevuto: " + received.getOpCode());

                if (received.getFlag() == Flag.NO_AUTH) {
                    handleNoAuth(received, factory);
                } else if (received.getFlag() == Flag.RESPONSE || received.getFlag() == Flag.REQUEST) {
                    UUID userUUID = received.getUserId();
                    if (!checkMessageFromUUID(userUUID, received)) {
                        System.out.println("Firma non valida per UUID: " + userUUID);
                        connection.send(factory.createErrorResponse(
                                userUUID, received.getOpCode(), 401, "Firma non valida o utente sconosciuto"));
                        continue;
                    }
                    handleAuthenticated(received, factory, userUUID);
                } else {
                    System.out.println("Messaggio non gestito: " + received);
                }
            }

        } catch (IOException e) {
            System.out.println("Connessione chiusa: " + e.getMessage());
        }
    }

    // ── NO_AUTH dispatch ────────────────────────────────────────────────────────

    private void handleNoAuth(Messaggio received, MessageFactoryServer factory) throws IOException {
        if (received.getOpCode() == OpCode.ACCOUNT_CREATION) {
            System.out.println("Registrazione nuovo utente...");
            String username  = (String)    received.getBodyField(FieldID.USERNAME_TO_CREATE).getValueAs(String.class);
            PublicKey pubKey = (PublicKey) received.getBodyField(FieldID.PUBLIC_KEY).getValueAs(PublicKey.class);
            Utente u = bank.registerUtente(username, pubKey);
            connection.send(factory.creaMessaggioDiCOnfermaRegistrazione("Registrazione completata", u));
        } else {
            System.out.println("OpCode NO_AUTH non riconosciuto: " + received.getOpCode());
        }
    }

    // ── Authenticated dispatch ──────────────────────────────────────────────────

    private void handleAuthenticated(Messaggio received, MessageFactoryServer factory, UUID userUUID) throws IOException {
        OpCode op = received.getOpCode();

        switch (op) {

            case CONFIRMATION -> {
                connection.send(factory.creaMessaggioConferma("OK", userUUID));
            }

            case LOGIN -> {
                // Signature already verified by checkMessageFromUUID — confirm login
                System.out.println("Login riuscito per: " + userUUID);
                connection.send(factory.createLoginResponse(userUUID));
            }

            case BALANCE -> {
                Utente u = bank.getUtenteFromUUID(userUUID);
                if (u == null || u.conti.isEmpty()) {
                    connection.send(factory.createErrorResponse(userUUID, OpCode.BALANCE, 404, "Conto non trovato"));
                    return;
                }
                Conto c = u.conti.values().iterator().next();
                Bban bban = bank.makeBBANforConto(c);
                connection.send(factory.createBalanceResponse(userUUID, c.getContenuto(), "EUR", bban));
            }

            case FETCH_ACCOUNTS -> {
                List<Conto> conti = bank.getAllContiForUtente(userUUID);
                connection.send(factory.createFetchAccountsResponse(userUUID, conti));
            }

            case CREATE_ACCOUNT -> {
                try {
                    Conto newConto = bank.addContoToUtente(userUUID);
                    connection.send(factory.createNewAccountResponse(userUUID, newConto));
                } catch (Exception e) {
                    connection.send(factory.createErrorResponse(userUUID, OpCode.CREATE_ACCOUNT, 500, e.getMessage()));
                }
            }

            case TRANSFER -> {
                try {
                    Bban targetBban = (Bban)  received.getBodyField(FieldID.TARGET_ACCOUNT).getValueAs(Bban.class);
                    Money amount    = (Money) received.getBodyField(FieldID.AMOUNT).getValueAs(Money.class);
                    bank.eseguiBonifico(userUUID, targetBban, amount);
                    connection.send(factory.createTransferResponse(userUUID, "Bonifico completato con successo"));
                } catch (Exception e) {
                    connection.send(factory.createErrorResponse(userUUID, OpCode.TRANSFER, 400, e.getMessage()));
                }
            }

            case TRANSACTION_HISTORY -> {
                Bban bban = (Bban) received.getBodyField(FieldID.BBAN).getValueAs(Bban.class);
                List<Transaction> history = bank.getTransactionHistory(bban);
                connection.send(factory.createTransactionHistoryResponse(userUUID, history));
            }

            case VALIDATE_BBAN -> {
                Bban bban  = (Bban) received.getBodyField(FieldID.BBAN).getValueAs(Bban.class);
                boolean ok = bank.bbanExists(bban);
                connection.send(factory.createValidateBbanResponse(userUUID, ok));
            }

            case REQUEST_CERTIFICATE -> {
                try {
                    Bban bban = (Bban) received.getBodyField(FieldID.BBAN).getValueAs(Bban.class);
                    String cert = bank.requestCertificate(userUUID, bban);
                    connection.send(factory.createCertificateResponse(userUUID, cert));
                } catch (Exception e) {
                    connection.send(factory.createErrorResponse(userUUID, OpCode.REQUEST_CERTIFICATE, 500, e.getMessage()));
                }
            }

            case RENAME_ACCOUNT -> {
                try {
                    Bban bban = (Bban) received.getBodyField(FieldID.BBAN).getValueAs(Bban.class);
                    String newName = (String) received.getBodyField(FieldID.ACCOUNT_NAME).getValueAs(String.class);
                    bank.renameConto(userUUID, bban, newName);
                    connection.send(factory.createRenameAccountResponse(userUUID));
                } catch (Exception e) {
                    connection.send(factory.createErrorResponse(userUUID, OpCode.RENAME_ACCOUNT, 400, e.getMessage()));
                }
            }

            case SET_ACCOUNT_STATUS -> {
                try {
                    Bban bban = (Bban) received.getBodyField(FieldID.BBAN).getValueAs(Bban.class);
                    String newStatus = (String) received.getBodyField(FieldID.ACCOUNT_STATUS).getValueAs(String.class);
                    bank.setContoStatus(userUUID, bban, newStatus);
                    connection.send(factory.createSetAccountStatusResponse(userUUID, newStatus));
                } catch (Exception e) {
                    connection.send(factory.createErrorResponse(userUUID, OpCode.SET_ACCOUNT_STATUS, 400, e.getMessage()));
                }
            }

            case FETCH_EXCHANGE_RATES -> {
                String rates = bank.getExchangeRatesPayload();
                connection.send(factory.createExchangeRatesResponse(userUUID, rates));
            }

            default -> System.out.println("[SERVER] OpCode non gestito: " + op);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private boolean checkMessageFromUUID(UUID uuid, Messaggio msg) {
        if (uuid == null) return false;
        Utente utente = bank.getUtenteFromUUID(uuid);
        if (utente == null) return false;
        return CryptoUtils.verifyMessage(msg, utente.publicKey);
    }
}
