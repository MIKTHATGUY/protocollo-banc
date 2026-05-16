# BancarioFaccia Protocol

> **Binary TCP banking protocol — self-describing, authenticated, and signed.**

[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://java.com)
[![TLS](https://img.shields.io/badge/TLS-1.3-brightgreen)](https://en.wikipedia.org/wiki/Transport_Layer_Security)
[![Sig](https://img.shields.io/badge/Signature-Ed25519-blueviolet)](https://en.wikipedia.org/wiki/EdDSA)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

---

## Overview

BancarioFaccia is a **binary protocol over TCP** designed for secure bank communications.
Every packet is self-framing and self-describing — no JSON, no XML, no HTTP.

**Design principles:**

- **No sessions** — each request carries its own Ed25519 signature
- **No ambiguity** — every byte on the wire has a defined meaning
- **Extensible** — new fields can be added without breaking backward compatibility
- **Anti-replay** — each message includes a server-provided nonce + monotonic sequence number

---

## Packet Structure

```mermaid
block-beta
  columns 1
  block:packet
    Header["1. Header (8 bytes)"]
    AuthBlock["2. AuthBlock (28 bytes)"]
    Body["3. Body (variable)"]
    Trailer["4. Trailer (64 bytes - Ed25519)"]
  end
```

### 1. Header (8 bytes) — Fixed-size frame prefix

```mermaid
packet-beta
title Header Format (8 bytes)
0-31: "PayloadLength (4 bytes)"
32-39: "Version (1 byte)"
40-47: "OpCode (1 byte)"
48-55: "Flag (1 byte)"
56-63: "Reserved (1 byte)"
```

| Offset | Size | Field         | Description                              |
|--------|------|---------------|------------------------------------------|
| 0      | 4    | PayloadLength | Length of AuthBlock + Body + Trailer     |
| 4      | 1    | Version       | Protocol version (0x01)                  |
| 5      | 1    | OpCode        | Operation code (Transfer, Balance, …)    |
| 6      | 1    | Flag          | Request / Response / Error / NoAuth      |
| 7      | 1    | Reserved      | Alignment (0x00)                         |

The 4-byte payload length at offset 0 enables **TCP framing**: after reading exactly 8 header
bytes, the receiver knows exactly how many more bytes to read for the complete packet.

### 2. AuthBlock (28 bytes) — Identity & anti-replay

```mermaid
packet-beta
title AuthBlock Format (28 bytes)
0-31: "UserId (UUID) - Bytes 0-3"
32-63: "UserId (UUID) - Bytes 4-7"
64-95: "UserId (UUID) - Bytes 8-11"
96-127: "UserId (UUID) - Bytes 12-15"
128-159: "SequenceNumber (4 bytes)"
160-191: "Nonce - Bytes 0-3"
192-223: "Nonce - Bytes 4-7"
```

| Offset | Size | Field          | Description                           |
|--------|------|----------------|---------------------------------------|
| 0      | 16   | UserId         | UUID (16 bytes) of the client         |
| 16     | 4    | SequenceNumber | Monotonic counter for anti-replay     |
| 20     | 8    | Nonce          | Server-generated session challenge    |

Omitted when `Flag == NO_AUTH` (registration requests).

### 3. Body (variable) — Self-describing fields

Each field on the wire:

```mermaid
packet-beta
title Body Field Wire Format
0-7: "FieldID (1 byte)"
8-15: "WireType (1 byte)"
16-31: "Size (2 bytes)"
32-63: "Data (first 4 bytes)"
64-95: "Data (next bytes...)"
```

**Supported WireTypes:**

| Byte | Type         | Description                    |
|------|--------------|--------------------------------|
| 0x01 | `LONG`       | 8-byte signed long             |
| 0x02 | `MONEY`      | Self-describing `PreciseMoney` |
| 0x03 | `STRING`     | UTF-8 string                   |
| 0x04 | `PUBLIC_KEY` | X.509-encoded Ed25519 key      |
| 0x05 | `BBAN`       | 48-byte bank account reference  |
| 0x06 | `INT`        | 4-byte signed integer          |
| 0x07 | `BYTE_ARRAY` | Raw bytes                      |

### 4. Trailer (64 bytes) — Ed25519 signature

```mermaid
packet-beta
title Trailer Format (64 bytes)
0-31: "Signature - Bytes 0-3"
32-63: "Signature - Bytes 4-7"
64-95: "Signature - Bytes 8-11"
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
448-479: "Signature - Bytes 56-59"
480-511: "Signature - Bytes 60-63"
```

Ed25519 signature over `Header + AuthBlock + Body`:
- 64 bytes, fixed
- Signed with the sender's private key
- Verified with the sender's public key

---

## OpCodes

| Byte | OpCode               | Description                    |
|------|----------------------|--------------------------------|
| 0x00 | `ACCOUNT_CREATION`   | Register new user              |
| 0x01 | `TRANSFER`           | Send money                     |
| 0x02 | `BALANCE`            | Check balance                  |
| 0x03 | `CONFIRMATION`       | Generic confirmation           |
| 0x04 | `REGISTRATION_CONFIRMATION` | Registration OK        |
| 0x05 | `TRANSACTION_HISTORY` | Get past transactions          |
| 0x06 | `CREATE_ACCOUNT`     | Open a new sub-account         |
| 0x07 | `VALIDATE_BBAN`      | Check if a BBAN exists         |
| 0x08 | `LOGIN`              | Authenticate with signed challenge |
| 0x09 | `FETCH_ACCOUNTS`     | List user's accounts           |
| 0x0A | `REQUEST_CERTIFICATE`| Get a bank-signed certificate  |
| 0x0B | `RENAME_ACCOUNT`     | Change account name            |
| 0x0C | `SET_ACCOUNT_STATUS` | Freeze / unfreeze account      |
| 0x0D | `FETCH_EXCHANGE_RATES` | Get currency conversion rates |

---

## Flags

| Byte | Flag               | Description                         |
|------|--------------------|-------------------------------------|
| 0x00 | `REQUEST`          | Authenticated request               |
| 0x01 | `RESPONSE`         | Successful response                 |
| 0x02 | `ERROR`            | Error response                      |
| 0x04 | `ASYNC`            | Server-pushed message               |
| 0x08 | `NO_AUTH`          | Unauthenticated request (no AuthBlock) |

---

## Authentication Flow

```mermaid
sequenceDiagram
    participant C as CLIENT
    participant S as SERVER

    C->>S: TCP/TLS 1.3 handshake
    S-->>C: 8-byte Session Nonce
    
    rect rgb(240, 248, 255)
    note right of C: Registration (NO_AUTH)
    C->>S: username + public key
    S-->>C: UUID + confirmation
    end

    rect rgb(240, 255, 240)
    note right of C: Login (authenticated)
    C->>S: UUID + seq + nonce (signed with Ed25519)
    S-->>C: response
    end

    rect rgb(255, 250, 240)
    note right of C: Transfer (authenticated)
    C->>S: target + amount (signed with Ed25519)
    S-->>C: confirmation
    end
```

Every **authenticated** message must:
1. Include the server's 8-byte nonce (anti-replay across sessions)
2. Include a monotonic sequence number (anti-replay within session)
3. Be signed with the client's Ed25519 private key

---

## Quick Start

### Prerequisites
- JDK 21+
- Maven or your preferred build tool

### Compile
```bash
javac -d out src/main/java/org/example/bancariofaccia/protocol/**/*.java
```

### Run Server
```bash
java -cp out org.example.bancariofaccia.protocol.BankServer
```

The server automatically:
1. Generates a self-signed TLS keystore (`server.p12`) if missing
2. Loads bank state from `bank_data.ser` if it exists
3. Listens on port **8443** with TLS 1.3

### Connect a Client
```java
Connection conn = Connection.createClient("localhost", 8443);
long nonce = conn.getSessionNonce();

// Register
MessageFactoryClient factory = new MessageFactoryClient(conn);
KeyPair kp = CryptoUtils.generateKeyPair();
Messaggio regMsg = factory.CreaMessaggioDiRichiestaCreazioneAccount("alice", kp.getPublic());
conn.send(regMsg);
Messaggio response = conn.receive();
```

---

## Project Structure

```
protocol/
├── Bank/                      # Domain model
│   ├── Bank.java              # Central bank logic, ser/deser
│   ├── Bban.java              # 48-byte binary bank account number
│   ├── Conto.java             # Account entity
│   ├── Transaction.java       # Transfer record
│   └── Utente.java            # User entity
├── Messaggi/                  # Binary protocol
│   ├── Messaggio.java         # Core packet: Header, AuthBlock, BodyField, Trailer
│   ├── Connection.java        # TLS transport + nonce exchange + send/receive
│   ├── ClientHandler.java     # Server-side dispatcher
│   ├── FieldID.java           # Body field identifiers
│   ├── OpCode.java            # Operation codes
│   ├── WireType.java          # Wire serializers (Long, Money, String, …)
│   ├── Flag.java              # Packet flags
│   └── MessageFactory/        # Builder helpers
│       ├── MessageFactory.java
│       ├── MessageFactoryClient.java
│       └── MessageFactoryServer.java
├── PreciseMoney/              # Value-object money
│   └── Money.java             # Locale-aware, currency-safe, binary-serializable
├── UTILS/                     # Utilities
│   ├── CryptoUtils.java       # Ed25519 sign & verify
│   ├── AsymmetricParser.java  # Key deserialization
│   └── HexDump.java           # Debug hex output
├── BankServer.java            # TLS server entry point
├── test.java                  # Dev scratch
├── README.md
├── ARCHITETTURA.md
└── LICENSE
```

---

## Why Binary?

| Concern              | JSON / HTTP                     | BancarioFaccia binary            |
|----------------------|----------------------------------|----------------------------------|
| Packet size          | 500–2000+ bytes                  | ~100–300 bytes                   |
| Parsing              | String parsing, validation       | Direct byte buffer reads         |
| Typing               | Strings everywhere               | Native types (long, UUID, Money) |
| Signing              | Sign raw bytes of chosen fields  | Sign entire deterministic packet |
| Framing              | Content-Length header            | Fixed 8-byte prefix with length  |

Every byte has a precise meaning. No serialization framework — you control the wire format.

---

## Security

- **Transport**: TLS 1.3 with mutual authentication (server cert verified)
- **Message authentication**: Ed25519 digital signature per packet
- **Anti-replay**: 8-byte random nonce (per connection) + 4-byte sequence number (per message)
- **Key pair**: Ed25519, generated client-side, public key sent during registration
- **No session tokens**: Each request is independently verifiable

---

## License

MIT — see [LICENSE](LICENSE).
