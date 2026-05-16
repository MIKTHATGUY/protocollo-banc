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

La struttura del pacchetto è composta da quattro blocchi principali. Per superare i limiti di visualizzazione ed evitare distorsioni, ogni sezione è dettagliata individualmente qui sotto, mostrando la corretta mappatura dei byte (32 bit per riga).

```mermaid
block-beta
  columns 1
  block:packet
    Header["1. Header (8 bytes)"]
    AuthBlock["2. AuthBlock (28 bytes)"]
    Body["3. Body (variabile)"]
    Trailer["4. Trailer (64 bytes - Ed25519)"]
  end
```

### 1. Header (8 bytes)

```mermaid
packet-beta
title Dettaglio Header (8 bytes)
0-31: "Payload Length (4 bytes)"
32-39: "Version (1 byte)"
40-47: "OpCode (1 byte)"
48-55: "Flag (1 byte)"
56-63: "Reserved (1 byte)"
```

### 2. AuthBlock (28 bytes)

```mermaid
packet-beta
title Dettaglio AuthBlock (28 bytes)
0-31: "UserId (UUID) - Byte 0-3"
32-63: "UserId (UUID) - Byte 4-7"
64-95: "UserId (UUID) - Byte 8-11"
96-127: "UserId (UUID) - Byte 12-15"
128-159: "SequenceNumber (4 bytes)"
160-191: "Nonce - Byte 0-3"
192-223: "Nonce - Byte 4-7"
```

### 3. Body (Dimensione variabile)

Il Body contiene zero o più campi, ciascuno formattato come segue:

```mermaid
packet-beta
title Formato di un singolo Body Field
0-7: "FieldID (1 byte)"
8-15: "WireType (1 byte)"
16-31: "Size (2 bytes)"
32-63: "Data (primi 4 bytes)"
64-95: "Data (byte successivi...)"
```

### 4. Trailer (64 bytes)

Contiene la firma Ed25519 dell'intero pacchetto (Header + AuthBlock + Body). A scopo illustrativo, sono mostrate solo le righe iniziali e finali per rappresentare i 64 byte (16 blocchi da 4 byte).

```mermaid
packet-beta
title Dettaglio Trailer (64 bytes)
0-31: "Signature - Byte 0-3"
32-63: "Signature - Byte 4-7"
64-95: "Signature - Byte 8-11"
96-127: "..."
128-159: "..."
160-191: "..."
192-223: "..."
224-255: "..."
256-287: "..."
288-319: "..."
320-351: "..."
352-383: "..."
384-415: "..."
416-447: "..."
448-479: "Signature - Byte 56-59"
480-511: "Signature - Byte 60-63"
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
