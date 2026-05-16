package org.example.bancariofaccia.protocol.Bank;

import org.example.bancariofaccia.protocol.UTILS.CryptoUtils;
import org.example.bancariofaccia.protocol.PreciseMoney.Money;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public class Bank implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String SAVE_FILE = "bank_data.ser";

    HashMap<UUID, Utente> utenti = new HashMap<UUID, Utente>();
    String nome;
    UUID id;
    public PrivateKey privateKey;
    PublicKey publicKey;

    public synchronized Utente registerUtente(String username, PublicKey publicKey) {
        Utente u = new Utente(username, UUID.randomUUID(), publicKey);
        
        Conto primaryConto = new Conto(u);
        primaryConto.setContenuto(new Money(new BigDecimal("1000.00"), Locale.ITALY));
        u.conti.put(primaryConto.getId(), primaryConto);
        
        utenti.put(u.uuid, u);
        System.out.println("Registrazione " + u.uuid + " avvenuta con successo. Bonus assegnato.");
        
        salvaStato();
        return u;
    }

    public synchronized void eseguiBonifico(UUID mittenteId, Bban targetBban, Money amount) throws Exception {
        Utente mittente = getUtenteFromUUID(mittenteId);
        if (mittente == null) throw new Exception("Mittente non trovato");
        
        if (mittente.conti.isEmpty()) throw new Exception("Il mittente non ha conti");
        Conto contoSorgente = mittente.conti.values().iterator().next(); 
        
        Utente destinatario = getUtenteFromBBAN(targetBban);
        if (destinatario == null) throw new Exception("Destinatario non trovato");
        
        Conto contoDestinazione = destinatario.conti.get(targetBban.getId_conto());
        if (contoDestinazione == null) throw new Exception("Conto destinazione non trovato");
        
        if (contoSorgente.getStatus() != null && "frozen".equals(contoSorgente.getStatus()))
            throw new Exception("Il conto sorgente è congelato");
        if (contoDestinazione.getStatus() != null && "frozen".equals(contoDestinazione.getStatus()))
            throw new Exception("Il conto destinazione è congelato");

        if (amount.isMinus() || amount.isZero()) throw new Exception("Importo non valido");
        
        if (contoSorgente.getContenuto().lt(amount)) {
            throw new Exception("Fondi insufficienti");
        }
        
        contoSorgente.setContenuto(contoSorgente.getContenuto().minus(amount));
        contoDestinazione.setContenuto(contoDestinazione.getContenuto().plus(amount));

        // Record transaction in both accounts
        long now = System.currentTimeMillis();
        Bban bbanSorgente = makeBBANforConto(contoSorgente);
        Bban bbanDest = targetBban;

        Transaction txOut = new Transaction(
                contoSorgente.getId(), contoDestinazione.getId(),
                bbanSorgente, bbanDest,
                amount, "EUR", "Bonifico",
                now, "OUT");
        contoSorgente.addTransaction(txOut);

        Transaction txIn = new Transaction(
                contoSorgente.getId(), contoDestinazione.getId(),
                bbanSorgente, bbanDest,
                amount, "EUR", "Bonifico",
                now, "IN");
        contoDestinazione.addTransaction(txIn);
        
        System.out.println("Bonifico di " + amount.asHumanReadableString() + " eseguito con successo");
        salvaStato();
    }

    /**
     * Creates an additional account for an existing user.
     */
    public synchronized Conto addContoToUtente(UUID utenteId) throws Exception {
        Utente u = getUtenteFromUUID(utenteId);
        if (u == null) throw new Exception("Utente non trovato");

        Conto newConto = new Conto(u);
        u.conti.put(newConto.getId(), newConto);
        
        System.out.println("Nuovo conto creato per " + u.username + ": " + newConto.getId());
        salvaStato();
        return newConto;
    }

    /**
     * Checks if a BBAN exists in this bank.
     */
    public synchronized boolean bbanExists(Bban bban) {
        if (bban == null) return false;
        Utente u = getUtenteFromBBAN(bban);
        if (u == null) return false;
        return u.conti.containsKey(bban.getId_conto());
    }

    /**
     * Returns the transaction history for a specific account.
     */
    public synchronized List<Transaction> getTransactionHistory(Bban bban) {
        Conto c = getContoFromBBAN(bban);
        if (c == null) return List.of();
        if (c.getTransazioni() == null) return List.of();
        return new ArrayList<>(c.getTransazioni());
    }

    /**
     * Returns all accounts for a given user.
     */
    public synchronized List<Conto> getAllContiForUtente(UUID utenteId) {
        Utente u = getUtenteFromUUID(utenteId);
        if (u == null) return List.of();
        return new ArrayList<>(u.conti.values());
    }

    public synchronized void salvaStato() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SAVE_FILE))) {
            oos.writeObject(this);
            System.out.println("Stato della banca salvato.");
        } catch (IOException e) {
            System.err.println("Errore salvataggio banca: " + e.getMessage());
        }
    }

    public static Bank caricaStato() {
        File f = new File(SAVE_FILE);
        if (f.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                Bank b = (Bank) ois.readObject();
                System.out.println("Stato della banca caricato da file.");
                return b;
            } catch (Exception e) {
                System.err.println("Errore caricamento banca, creo nuova: " + e.getMessage());
            }
        } else {
            System.out.println("Nessun salvataggio trovato, creo una nuova banca.");
        }
        return new Bank("Intesa San Paolo");
    }

    public Bban makeBBANforConto(Conto conto){
        return new Bban(conto.getId(), conto.proprietario.uuid, id);
    }

    public Conto getContoFromBBAN(Bban bban){
        Utente u = getUtenteFromBBAN(bban);
        if (u == null) return null;
        return u.conti.get(bban.getId_conto());
    }

    public Utente getUtenteFromUUID(UUID uuid){
        return utenti.get(uuid);
    }

    public Utente getUtenteFromBBAN(Bban bban){
        return utenti.get(bban.getId_proprietario());
    }

    public synchronized void renameConto(UUID utenteId, Bban bban, String newName) throws Exception {
        Utente u = getUtenteFromUUID(utenteId);
        if (u == null) throw new Exception("Utente non trovato");
        Conto c = getContoFromBBAN(bban);
        if (c == null) throw new Exception("Conto non trovato");
        c.setAlias(newName);
        System.out.println("Account renamed to: " + newName);
        salvaStato();
    }

    public synchronized void setContoStatus(UUID utenteId, Bban bban, String newStatus) throws Exception {
        Utente u = getUtenteFromUUID(utenteId);
        if (u == null) throw new Exception("Utente non trovato");
        Conto c = getContoFromBBAN(bban);
        if (c == null) throw new Exception("Conto non trovato");
        if (!"active".equals(newStatus) && !"frozen".equals(newStatus))
            throw new Exception("Status non valido: " + newStatus);
        c.setStatus(newStatus);
        System.out.println("Account status changed to: " + newStatus);
        salvaStato();
    }

    public synchronized String getExchangeRatesPayload() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, BigDecimal> e : getExchangeRates().entrySet()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(e.getKey()).append("=").append(e.getValue().toPlainString());
        }
        return sb.toString();
    }

    private Map<String, BigDecimal> getExchangeRates() {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        rates.put("USD", BigDecimal.ONE);
        rates.put("EUR", new BigDecimal("1.08"));
        rates.put("GBP", new BigDecimal("1.27"));
        rates.put("JPY", new BigDecimal("0.0065"));
        rates.put("CHF", new BigDecimal("1.13"));
        rates.put("CAD", new BigDecimal("0.73"));
        rates.put("AUD", new BigDecimal("0.65"));
        return rates;
    }

    public synchronized String requestCertificate(UUID utenteId, Bban bban) throws Exception {
        Utente u = getUtenteFromUUID(utenteId);
        if (u == null) throw new Exception("Utente non trovato");
        Conto c = getContoFromBBAN(bban);
        if (c == null) throw new Exception("Conto non trovato");

        String text = "BANK CERTIFICATE\n" +
                "Bank: " + nome + "\n" +
                "Bank ID: " + id + "\n" +
                "User: " + u.username + "\n" +
                "User UUID: " + u.uuid + "\n" +
                "Account BBAN: " + bban.getId_conto() + "-" + bban.getId_proprietario() + "-" + bban.getId_banca() + "\n" +
                "Balance: " + c.getContenuto().asHumanReadableString() + "\n" +
                "Status: " + c.getStatus() + "\n" +
                "Issued: " + new java.util.Date() + "\n";

        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(privateKey);
        sig.update(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] signature = sig.sign();

        String hexSig = HexFormat.of().formatHex(signature);
        return text + "\n--- SERVER SIGNATURE (Ed25519) ---\n" + hexSig;
    }

    public UUID getId() {
        return id;
    }

    Bank(UUID id){
        this.id = id;
    }

    public Bank(String nome){
        this.nome = nome;
        this.id = UUID.randomUUID();
        initializeKeys();
    }

    Bank(){
        this.id = UUID.randomUUID();
        this.nome = UUID.randomUUID().toString();
        initializeKeys();
    }

    private void initializeKeys() {
        try {
            KeyPair kp = CryptoUtils.generateKeyPair();
            this.privateKey = kp.getPrivate();
            this.publicKey = kp.getPublic();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate keys for Bank", e);
        }
    }
}
