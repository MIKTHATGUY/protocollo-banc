package org.example.bancariofaccia.protocol.Messaggi;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A self-describing, signed binary packet for a banking protocol.
 *
 * <h2>Why this structure?</h2>
 * <p>
 * The packet is divided into four <b>independent sections</b>: {@link Header},
 * {@link AuthBlock}, {@link BodyField}, and {@link Trailer}.  Each section knows
 * its own wire format, size, and how to serialize itself.  If you need to add
 * a field to the Header during development, you only touch the {@link Header}
 * class — nothing else in this file has to change.
 * </p>
 *
 * <h2>Reading from a network stream (TCP)</h2>
 * <p>
 * TCP gives you a raw byte stream, so you must <i>frame</i> the packets.
 * The {@link Header} is a fixed size ({@value Header#SIZE} bytes) and ends
 * with a 4-byte <b>Payload Length</b>.  That length tells you exactly how
 * many bytes remain (AuthBlock + Body + Trailer).
 * </p>
 *
 * <pre>
 * // 1. Pull the fixed-size header from the socket.
 * byte[] headerBytes = readExactly(inputStream, Header.SIZE);
 * int totalSize = Messaggio.getTotalPacketSize(headerBytes);
 *
 * // 2. Pull the rest in one shot (payload length is already known).
 * byte[] payloadBytes = readExactly(inputStream, totalSize - Header.SIZE);
 *
 * // 3. Glue together and parse.
 * ByteBuffer full = ByteBuffer.allocate(totalSize);
 * full.put(headerBytes).put(payloadBytes).flip();
 * Messaggio msg = new Messaggio(full);
 * </pre>
 *
 * <h2>Packet Layout</h2>
 * <pre>
 * +--------+-------------------------------------------+
 * | Header |  Payload (AuthBlock + Body + Trailer)     |
 * | 8 B    |  variable length (known from Header)      |
 * +--------+-------------------------------------------+
 * </pre>
 */
public class Messaggio implements Serializable {

    /* ============================================================
       PROTOCOL VERSION
       Never changes unless you do a breaking v2 redesign.
       ============================================================ */
    public static final byte PROTOCOL_VERSION = 0x01;



    /* ============================================================
       HEADER (8 Bytes) — self-contained, versioned, and fixed-size
       ============================================================
       4 Bytes – Payload Length   (Int) → size of Auth + Body + Trailer.
                 This is the ONLY number you need to know how much
                 more data to read from the wire after the header.
                 Placed FIRST so TCP framing can read it immediately.
       1 Byte  – Version          (always 0x01)
       1 Byte  – OpCode           (e.g., Transfer, Balance)
       1 Byte  – Flag             (Response, Error, …)
       1 Byte  – Reserved         (0x00, keeps 4-byte alignment)
       ============================================================
       If you add a field here, update:
         • SIZE constant below (sum of all field widths)
         • Constructor {@link #Header(ByteBuffer)}
         • {@link #toByteBuffer()}
       Nothing else in Messaggio needs to change.
       ============================================================ */
    /**
     * Returns the total wire size of a packet given only its header bytes.
     * Because Payload Length is the first 4 bytes, you can call this
     * immediately after reading {@link Header#SIZE} bytes from a socket.
     */
    public static int getTotalPacketSize(byte[] headerBytes) {
        if (headerBytes == null || headerBytes.length < Header.SIZE) {
            throw new IllegalArgumentException(
                    "Need at least " + Header.SIZE + " header bytes");
        }
        ByteBuffer buf = ByteBuffer.wrap(headerBytes);
        int payloadLength = buf.getInt(); // first 4 bytes = payload length
        return Header.SIZE + payloadLength;
    }

    public static class Header {
        /**
         * Sum of every field below.  Change this if you add/remove header fields.
         */
        public static final int SIZE = 4 + 1 + 1 + 1 + 1; // 8 bytes
        public static final int _SIZE_OF_LENGTH = 4;

        private byte version = PROTOCOL_VERSION;
        private OpCode opCode;
        private Flag flag;
        private byte reserved = 0;
        /**
         * Length of EVERYTHING after this header (AuthBlock + Body + Trailer).
         */
        private int payloadLength;

        public Header() {
        }

        public Header(ByteBuffer buf) {
            this.payloadLength = buf.getInt();
            this.version = buf.get();
            if (this.version != PROTOCOL_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported protocol version: 0x" + Integer.toHexString(this.version & 0xFF));
            }
            this.opCode = OpCode.fromByte(buf.get());
            this.flag = Flag.fromByte(buf.get());
            this.reserved = buf.get();
        }

        public ByteBuffer toByteBuffer() {
            ByteBuffer buf = ByteBuffer.allocate(SIZE);
            buf.putInt(payloadLength);
            buf.put(version);
            buf.put(opCode.getValue());
            buf.put(flag.getValue());
            buf.put(reserved);
            buf.flip();
            return buf;
        }

        // --- Getters / Setters / Fluent API -------------------------

        public byte getVersion() {
            return version;
        }

        public OpCode getOpCode() {
            return opCode;
        }

        public Flag getFlag() {
            return flag;
        }

        public byte getReserved() {
            return reserved;
        }

        public int getPayloadLength() {
            return payloadLength;
        }

        public void withOpCode(OpCode opCode) {
            this.opCode = opCode;
        }

        public void withFlag(Flag flag) {
            this.flag = flag;
        }

        public void withPayloadLength(int len) {
            this.payloadLength = len;
        }
    }

    /* ============================================================
       AUTH BLOCK (28 Bytes) — identity and anti-replay data
       ============================================================
       16 Bytes – UUID (most significant bits + least significant bits)
       4 Bytes  – Sequence Number (anti-replay)
       8 Bytes  – Nonce (from server's handshake)
       ============================================================
       If you add a field here, update SIZE and the two buffer methods.
       ============================================================ */
    public static class AuthBlock {
        public static final int SIZE = 16 + 4 + 8; // 28 bytes

        private UUID userId;
        private int sequenceNumber;
        private long nonce;

        public AuthBlock() {
        }

        public AuthBlock(ByteBuffer buf) {
            long most = buf.getLong();
            long least = buf.getLong();
            this.userId = new UUID(most, least);
            this.sequenceNumber = buf.getInt();
            this.nonce = buf.getLong();
        }

        public ByteBuffer toByteBuffer() {
            ByteBuffer buf = ByteBuffer.allocate(SIZE);
            buf.putLong(userId.getMostSignificantBits());
            buf.putLong(userId.getLeastSignificantBits());
            buf.putInt(sequenceNumber);
            buf.putLong(nonce);
            buf.flip();
            return buf;
        }

        // --- Getters / Setters / Fluent API -------------------------

        public UUID getUserId() {
            return userId;
        }

        public int getSequenceNumber() {
            return sequenceNumber;
        }

        public long getNonce() {
            return nonce;
        }

        public void withUserId(UUID id) {
            this.userId = id;
        }

        public void withSequenceNumber(int seq) {
            this.sequenceNumber = seq;
        }

        public void withNonce(long nonce) {
            this.nonce = nonce;
        }
    }

    /* ============================================================
       TRAILER (64 Bytes) — Ed25519 signature
       ============================================================
       64 Bytes – Signature over Header + AuthBlock + Body.
       The signature is NOT included in the signed bytes (obviously).
       ============================================================ */
    public static class Trailer {
        public static final int SIGNATURE_SIZE = 64;

        private byte[] signature = new byte[SIGNATURE_SIZE];

        public Trailer() {
        }

        public Trailer(ByteBuffer buf) {
            buf.get(signature);
        }

        public ByteBuffer toByteBuffer() {
            return ByteBuffer.wrap(signature);
        }

        public byte[] getSignature() {
            return signature.clone();
        }

        public void setSignature(byte[] sig) {
            if (sig == null || sig.length != SIGNATURE_SIZE) {
                throw new IllegalArgumentException(
                        "Signature must be exactly " + SIGNATURE_SIZE + " bytes");
            }
            this.signature = sig.clone();
        }
    }

    /* ============================================================
       BODY FIELD — a single key/value entry inside the Body
       ============================================================
       Every field on the wire is:
         1 Byte  – Field ID (what this field means)
         1 Byte  – Wire Type (Long, PreciseMoney, String, …)
         2 Bytes – Size (unsigned short)  →  only for variable-length types
         N Bytes – Raw data
       ============================================================ */
    public static class BodyField {
        private final FieldID fieldID;
        private final WireType wireType;
        private final short size;   // wire format: unsigned short
        private final byte[] data;

        /**
         * Convenience: build from a typed Java value.
         */
        public BodyField(FieldID fieldID, WireType wireType, Object value) {
            this.fieldID = fieldID;
            this.wireType = wireType;
            byte[] payload = wireType.toBytes(value);
            this.size = (short) payload.length;
            this.data = payload;
        }

        public BodyField(byte[] bytes) {
            this(ByteBuffer.wrap(bytes));
        }

        public BodyField(ByteBuffer buf) {
            this.fieldID = FieldID.fromByte(buf.get());
            this.wireType = WireType.fromByte(buf.get());
            this.size = buf.getShort();
            // Treat 'size' as unsigned (0 … 65535) even though Java shorts are signed.
            int dataLen = size & 0xFFFF;
            this.data = new byte[dataLen];
            buf.get(this.data);
        }

        /**
         * Returns the unsigned size (0 … 65535).
         */
        public int getSize() {
            return size & 0xFFFF;
        }

        public FieldID getFieldID() {
            return fieldID;
        }

        public Object getValue() {
            return wireType.fromBytes(data);
        }

        public <T> T getValueAs(Class<T> type) {
            return type.cast(getValue());
        }

        public ByteBuffer toByteBuffer() {
            ByteBuffer buf = ByteBuffer.allocate(4 + data.length);
            buf.put(fieldID.getValue());
            buf.put(wireType.getValue());
            buf.putShort(size);
            buf.put(data);
            buf.flip();
            return buf;
        }

        public byte[] toByteArray() {
            return toByteBuffer().array();
        }

        @Override
        public String toString() {
            return String.format("%s(%s): %s", fieldID, wireType, getValue());
        }
    }

    /* ============================================================
       PACKET INSTANCE
       ============================================================ */

    private final Header header;
    private final AuthBlock auth;
    private final Map<FieldID, BodyField> bodyFields = new HashMap<>();
    private final Trailer trailer;

    /* ============================================================
       CONSTRUCTORS / PARSING
       ============================================================ */

    public Messaggio() {
        this.header = new Header();
        this.auth = new AuthBlock();
        this.trailer = new Trailer();
    }

    /**
     * Parse a <b>complete</b> packet from a ByteBuffer.
     * The buffer must contain at least {@link Header#SIZE} + header.payloadLength bytes.
     */
    public Messaggio(ByteBuffer buffer) {
        // 1. Fixed-size header (8 bytes).  From this we know exactly how much remains.
        this.header = new Header(buffer);

        int payloadLength = header.getPayloadLength();
        if (buffer.remaining() < payloadLength) {
            throw new IllegalArgumentException(
                    "Buffer underflow: header promises " + payloadLength +
                            " payload bytes, but only " + buffer.remaining() + " remain.");
        }

        // 2. Auth block (fixed size).
        boolean hasAuth = header.getFlag() != Flag.NO_AUTH;

        if (hasAuth) {
            this.auth = new AuthBlock(buffer);
        } else {
            this.auth = null;
        }

        // 3. Body (variable size).  Derive it from the payload length.
        int bodyLength = payloadLength - Trailer.SIGNATURE_SIZE;
        if (hasAuth) {
            bodyLength -= AuthBlock.SIZE;
        }
        if (bodyLength < 0) {
            throw new IllegalArgumentException(
                    "Invalid payload length in header: " + payloadLength +
                            " (not enough room for AuthBlock + Trailer)");
        }
        parseBodyFields(buffer, bodyLength);

        // 4. Trailer (fixed size).
        this.trailer = new Trailer(buffer);
    }

    public Messaggio(byte[] wire) {
        this(ByteBuffer.wrap(wire));
    }

    private void parseBodyFields(ByteBuffer buf, int bodyLength) {
        int endPosition = buf.position() + bodyLength;
        while (buf.position() < endPosition) {
            BodyField field = new BodyField(buf);
            bodyFields.put(field.getFieldID(), field);
        }
    }

    /* ============================================================
       SERIALIZATION
       ============================================================ */

    public ByteBuffer toByteBuffer() {
        byte[] bodyBytes = serializeBody();

        // Payload = AuthBlock + Body + Trailer
        boolean hasAuth = (header.getFlag() != Flag.NO_AUTH);

        int payloadLength = bodyBytes.length + Trailer.SIGNATURE_SIZE;
        if (hasAuth) {
            payloadLength += AuthBlock.SIZE;
        }

        header.withPayloadLength(payloadLength);


        int totalSize = Header.SIZE + payloadLength;
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);

        buffer.put(header.toByteBuffer());

        if (hasAuth) {
            buffer.put(auth.toByteBuffer());
        }

        buffer.put(bodyBytes);
        buffer.put(trailer.toByteBuffer());

        buffer.flip();
        return buffer;
    }

    public byte[] toByteArray() {
        return toByteBuffer().array();
    }

    private byte[] serializeBody() {
        int bodySize = 0;
        for (BodyField field : bodyFields.values()) {
            bodySize += 4 + field.getSize();
        }
        ByteBuffer buf = ByteBuffer.allocate(bodySize);
        for (BodyField field : bodyFields.values()) {
            buf.put(field.toByteArray());
        }
        return buf.array();
    }

    /**
     * Returns the bytes that must be signed (Header + Auth + Body, no Trailer).
     * The Header's PayloadLength field is updated first so the signature covers
     * the exact same header bytes that will travel on the wire.
     */
    public byte[] getSignableBytes() {
        byte[] bodyBytes = serializeBody();

        // Ensure the header length field matches the wire format before signing.
        int payloadLength = AuthBlock.SIZE + bodyBytes.length + Trailer.SIGNATURE_SIZE;
        header.withPayloadLength(payloadLength);

        int signableLength = Header.SIZE + AuthBlock.SIZE + bodyBytes.length;
        ByteBuffer buf = ByteBuffer.allocate(signableLength);
        boolean hasAuth = header.getFlag() != Flag.NO_AUTH;
        buf.put(header.toByteBuffer());
        if (hasAuth) {
            buf.put(auth.toByteBuffer());
        }
        buf.put(bodyBytes);

        return buf.array();
    }

    /* ============================================================
       NETWORK FRAMING HELPERS
       ============================================================ */

    /* ============================================================
       BUILDER-STYLE API
       ============================================================ */

    public Messaggio withOpCode(OpCode opCode) {
        header.withOpCode(opCode);
        return this;
    }

    public Messaggio withFlag(Flag flag) {
        header.withFlag(flag);
        return this;
    }

    public Messaggio withAuth(UUID userId, int sequenceNumber, long nonce) {
        auth.withUserId(userId);
        auth.withSequenceNumber(sequenceNumber);
        auth.withNonce(nonce);
        return this;
    }

    public Messaggio addField(FieldID fieldID, WireType wireType, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Field value cannot be null for " + fieldID);
        }
        this.bodyFields.put(fieldID, new BodyField(fieldID, wireType, value));
        return this;
    }

    public void setSignature(byte[] signature) {
        this.trailer.setSignature(signature);
    }

    /* ============================================================
       GETTERS — flat convenience accessors
       (You can also grab the section objects directly below.)
       ============================================================ */

    public Header getHeader() {
        return header;
    }

    public AuthBlock getAuth() {
        return auth;
    }

    public Trailer getTrailer() {
        return trailer;
    }

    public byte getVersion() {
        return header.getVersion();
    }

    public OpCode getOpCode() {
        return header.getOpCode();
    }

    public Flag getFlag() {
        return header.getFlag();
    }

    public byte getReserved() {
        return header.getReserved();
    }

    public int getPayloadLength() {
        return header.getPayloadLength();
    }

    public UUID getUserId() {
        return auth.getUserId();
    }

    public int getSequenceNumber() {
        return auth.getSequenceNumber();
    }

    public long getNonce() {
        return auth.getNonce();
    }

    public Map<FieldID, BodyField> getBodyFields() {
        return bodyFields;
    }

    public BodyField getBodyField(FieldID fieldID) {
        return bodyFields.get(fieldID);
    }

    public byte[] getSignature() {
        return trailer.getSignature();
    }

    /* ============================================================
       DIAGNOSTICS
       ============================================================ */

    private String getSignaturePrefix() {
        byte[] sig = trailer.getSignature();
        if (sig == null || sig.length == 0) return "null";
        StringBuilder sb = new StringBuilder();
        int len = Math.min(sig.length, 8);
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02X", sig[i]));
        }
        if (sig.length > 8) sb.append("...");
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Messaggio {\n");

        sb.append("  [HEADER]  Version: ").append(header.getVersion())
                .append(", OpCode: ").append(header.getOpCode())
                .append(", Flag: ").append(header.getFlag())
                .append(", PayloadLength: ").append(header.getPayloadLength()).append("\n");

        if(auth != null) {
            sb.append("  [AUTH]    UserID: ").append(auth.getUserId())
                    .append(", Seq: ").append(auth.getSequenceNumber())
                    .append(", Nonce: ").append(auth.getNonce()).append("\n");
        }

        sb.append("  [BODY]    Fields: (").append(bodyFields.size()).append(")\n");
        for (BodyField field : bodyFields.values()) {
            sb.append("            - ").append(field).append("\n");
        }

        sb.append("  [TRAILER] Signature: ").append(getSignaturePrefix()).append("\n");
        sb.append("}");
        return sb.toString();
    }
}