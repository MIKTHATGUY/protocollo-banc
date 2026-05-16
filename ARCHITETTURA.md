# Architettura del Protocollo Bancario Binario

## Indice

1. [Panoramica](#1-panoramica)
2. [Struttura del pacchetto wire](#2-struttura-del-pacchetto-wire)
3. [Diagramma delle classi](#3-diagramma-delle-classi)
4. [Flusso di una transazione](#4-flusso-di-una-transazione)
5. [Gestione della memoria](#5-gestione-della-memoria)
6. [Estendibilità](#6-estendibilità)

---

## 1. Panoramica

```mermaid
graph LR
    C[CLIENT<br>JavaFX + Factory] <-->|Protocollo Binario<br>TCP + TLS + Ed25519| S[SERVER<br>BankServer + ClientHandler<br>MessageFactoryServer]
```

Il protocollo è il **livello di trasporto e presentazione** tra un client
bancario e un server. Definisce esattamente come:

- I pacchetti vengono frammentati e ricostruiti su TCP
- I dati vengono serializzati in forma binaria
- Le richieste vengono autenticate tramite firma Ed25519
- Il server risponde con messaggi strutturati

---

## 2. Struttura del pacchetto wire

```mermaid
packet-beta
title Struttura Pacchetto Wire (Big-Endian)
0-31: "PL (4 bytes)"
32-39: "VER (1 byte)"
40-47: "OP (1 byte)"
48-55: "FLG (1 byte)"
56-63: "RSV (1 byte)"
64-191: "UserId (16 bytes)"
192-223: "SeqNum (4 bytes)"
224-287: "Nonce (8 bytes)"
288-311: "Body (N bytes)"
312-823: "Ed25519 Sig (64 bytes)"
```

---

## 3. Diagramma delle classi

```mermaid
classDiagram
    class Messaggio {
        -Header header
        -AuthBlock auth
        -Map~FieldID, BodyField~ bodyFields
        -Trailer trailer
        +Messaggio()
        +Messaggio(ByteBuffer)
        +toByteArray() byte[]
        +getSignableBytes() byte[]
        +addField(FID, WT, Object)
        +withOpCode(OpCode)
        +withFlag(Flag)
        +withAuth(UUID, int, long)
        +setSignature(byte[])
    }
    class Header {
        -int payloadLength
        -byte version
        -OpCode opCode
        -Flag flag
        +SIZE = 8
    }
    class AuthBlock {
        -UUID userId
        -int sequenceNumber
        -long nonce
        +SIZE = 28
    }
    class Trailer {
        -byte[] signature
    }
    class BodyField {
        -FieldID fieldID
        -WireType wireType
        -short size
        -byte[] data
    }
    
    Messaggio *-- Header
    Messaggio *-- AuthBlock
    Messaggio *-- Trailer
    Messaggio *-- BodyField
    
    class Connection {
        -Socket socket
        -long sessionNonce
        +createClient()
        +acceptClient()
        +send(Messaggio)
        +receive() Messaggio
    }
    class ClientHandler {
        -Connection connection
        -Bank bank
        +run()
    }
    class MessageFactory {
        #Connection connection
        #int sequenceNumber
        #PrivateKey privateKey
    }
    class MessageFactoryClient {
        +createRegReq()
        +createTransf()
    }
    class MessageFactoryServer
    
    MessageFactory <|-- MessageFactoryClient
    MessageFactory <|-- MessageFactoryServer
    ClientHandler --> Connection
    MessageFactory --> Connection
    
    class Bank {
        -Map~UUID, Utente~ utenti
        -PrivateKey privateKey
    }
    class Utente {
        -String username
        -UUID uuid
        -Map~UUID, Conto~ conti
    }
    class Conto {
        -Money contenuto
        -List~Transaction~ transazioni
        -String status
    }
    class Bban {
        -UUID id_conto
        -UUID id_prop
        -UUID id_banca
    }
    class Money {
        -BigDecimal amount
        -Locale locale
    }
    class Transaction {
        -Money amount
        -String direction
        -long timestamp
    }
    
    Bank *-- Utente
    Utente *-- Conto
    Conto *-- Transaction
    Transaction --> Money
    Conto --> Money
```

---

## 4. Flusso di una transazione

### 4.1 Invio di un bonifico (passo dopo passo)

```mermaid
sequenceDiagram
    participant C as CLIENT
    participant S as SERVER

    note over C: 1. Costruisce il pacchetto<br/>factory.createTransferRequest(...)
    note over C: 2. Messaggio.java:<br/>- Header: TRANSFER, REQUEST<br/>- AuthBlock: UUID, seq, nonce<br/>- Body: TARGET, AMOUNT
    note over C: 3. CryptoUtils.signMessage(...)<br/>- getSignableBytes()<br/>- Signature.sign()<br/>- setSignature()

    C->>S: 4. connection.send(messaggio)<br/>toByteArray() -> out.write()
    
    note over S: 5. ClientHandler.run()<br/>connection.receive()
    note over S: 6. Connection.receive()<br/>- readExactly(8)<br/>- getTotalPacketSize()<br/>- readExactly(payloadLength)<br/>- new Messaggio(full)
    note over S: 7. Verifica Flag REQUEST
    note over S: 8. verifyMessage()<br/>- getSignableBytes()<br/>- Signature.verify()
    note over S: 9. OpCode.TRANSFER<br/>- bank.eseguiBonifico()<br/>- Verifica fondi<br/>- Registra transazione
    note over S: 10. factory.createTransferResponse()<br/>(Firmato con privateKey del server)

    S-->>C: connection.send(response)
    
    note over C: 11. connection.receive()<br/>12. Verifica firma del server
```

---

## 5. Gestione della memoria

### 5.1 ByteBuffer e letture esatte

Il metodo `readExactly()` in `Connection.java` garantisce che un'interruzione
di TCP non causi letture parziali:

```
readExactly(InputStream, byte[] dest):
  offset = 0
  while offset < dest.length:
    read = stream.read(dest, offset, dest.length - offset)
    if read == -1: throw EOFException
    offset += read
```

Questo è fondamentale perché TCP può frammentare i dati in pacchetti arbitrari.

### 5.2 Allocazione del ByteBuffer

La serializzazione alloca ByteBuffer di dimensione esatta:

```
Header.SIZE + payloadLength
= 8 + (AuthBlock.SIZE + bodyBytes.length + Trailer.SIGNATURE_SIZE)
= 8 + (28 + N + 64)
= 100 + N byte  (dove N è la dimensione del Body)
```

Questo previene il riallocamento e la copia di array durante la costruzione.

### 5.3 Serializzazione del Body

Il body viene serializzato in due passate:
1. **Prima passata**: calcola la dimensione totale di tutti i BodyField
2. **Seconda passata**: scrive i byte in un ByteBuffer della dimensione calcolata

Questo permette di sapere la dimensione finale del body prima di scrivere
l'header, risolvendo il problema della firma su un messaggio che contiene
la propria lunghezza.

---

## 6. Estendibilità

### 6.1 Aggiungere un nuovo OpCode

1. Aggiungere il valore nell'enum `OpCode.java`
2. Aggiungere un metodo factory in `MessageFactoryClient` e `MessageFactoryServer`
3. Aggiungere un case nello switch di `ClientHandler.handleAuthenticated()`

Nessun cambiamento al formato wire o alla serializzazione

### 6.2 Aggiungere un nuovo FieldID

1. Aggiungere il valore nell'enum `FieldID.java`
2. Aggiungere un campo nel Body del messaggio con `.addField()`

Nessun cambiamento al parsing, funziona subito.

### 6.3 Aggiungere un nuovo WireType

1. Aggiungere l'enum in `WireType.java` con implementazioni di `toBytes()` e `fromBytes()`
2. Usare il nuovo tipo nei BodyField

### 6.4 Modificare l'Header

Basta modificare:
1. La costante `SIZE` in `Header`
2. Il costruttore `Header(ByteBuffer)`
3. `toByteBuffer()`

Il resto di `Messaggio.java` non cambia.

### 6.5 Aggiungere una nuova sezione al pacchetto

Per aggiungere, ad esempio, una sezione `Metadata` tra `Body` e `Trailer`:
1. Creare la classe `Metadata` con `SIZE`, `toByteBuffer()`, costruttore da `ByteBuffer`
2. Aggiungere il campo in `Messaggio`
3. Aggiornare `getSignableBytes()` e `toByteBuffer()` e il costruttore da `ByteBuffer`

Ogni sezione è **completamente indipendente** dalle altre.

---

## Riferimenti

- [README.md](README.md) — Quick start e documentazione generale
- [DOCUMENTAZIONE_CAPOLAVORO.md](DOCUMENTAZIONE_CAPOLAVORO.md) — Documento completo per E-Portfolio
- [FORM_RIEPILOGO.md](FORM_RIEPILOGO.md) — Riepilogo campi modulo
