package org.example.bancariofaccia.protocol.Bank;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.io.Serializable;

/**
 * Represents a Bank.BBAN (Basic Bank Account Number-like structure) composed of:
 * - an account UUID (id_conto)
 * - an owner UUID (id_proprietario)
 * - a bank UUID (id_banca)
 *
 * <p>This class provides a compact binary encoding of the three UUIDs.
 * Instead of using String representations (36 bytes each), it stores them
 * in their raw binary form (16 bytes each), resulting in a fixed size of 48 bytes.</p>
 */
public class Bban implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final int BYTE_LENGTH = 48;

    /** UUID representing the account identifier */
    private final UUID id_conto;

    /** UUID representing the owner identifier */
    private final UUID id_proprietario;

    /** UUID representing the owner's bank */
    private final UUID id_banca;

    /**
     * Creates a random Bank.BBAN.
     */
    public Bban() {
        this.id_conto = UUID.randomUUID();
        this.id_proprietario = UUID.randomUUID();
        this.id_banca = UUID.randomUUID();
    }

    /**
     * Creates a Bank.BBAN from three specific UUIDs.
     *
     * @param id_conto account identifier
     * @param id_proprietario owner identifier
     * @param id_banca bank identifier
     */
    public Bban(UUID id_conto, UUID id_proprietario, UUID id_banca) {
        this.id_conto = Objects.requireNonNull(id_conto);
        this.id_proprietario = Objects.requireNonNull(id_proprietario);
        this.id_banca = Objects.requireNonNull(id_banca);
    }

    /**
     * Decodes a Bank.BBAN from its binary representation.
     *
     * @param encoded a byte array that must be exactly 48 bytes long
     * @throws IllegalArgumentException if the array is not exactly 48 bytes
     */
    public Bban(byte[] encoded) {
        if (encoded == null || encoded.length != BYTE_LENGTH) {
            throw new IllegalArgumentException("Encoded Bank.BBAN must be exactly 48 bytes");
        }

        ByteBuffer buffer = ByteBuffer.wrap(encoded);

        this.id_conto = new UUID(buffer.getLong(), buffer.getLong());
        this.id_proprietario = new UUID(buffer.getLong(), buffer.getLong());
        this.id_banca = new UUID(buffer.getLong(), buffer.getLong());
    }

    /**
     * Encodes this Bank.BBAN into a 48-byte array.
     *
     * @return a byte array of length 48 containing the binary representation
     */
    public byte[] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(BYTE_LENGTH);

        buffer.putLong(id_conto.getMostSignificantBits());
        buffer.putLong(id_conto.getLeastSignificantBits());

        buffer.putLong(id_proprietario.getMostSignificantBits());
        buffer.putLong(id_proprietario.getLeastSignificantBits());

        buffer.putLong(id_banca.getMostSignificantBits());
        buffer.putLong(id_banca.getLeastSignificantBits());

        return buffer.array();
    }

    public UUID getId_conto() {
        return id_conto;
    }

    public UUID getId_proprietario() {
        return id_proprietario;
    }

    public UUID getId_banca() {
        return id_banca;
    }

    public static int getLength() {
        return BYTE_LENGTH;
    }

    public static int getByteLength() {
        return BYTE_LENGTH;
    }

    public static int getBitLength() {
        return BYTE_LENGTH * 8;
    }

    public static Bban consumeFromBuffer(ByteBuffer buffer) {
        Bban b = new Bban(Arrays.copyOfRange(buffer.array(), buffer.position(), buffer.position() + BYTE_LENGTH));
        buffer.position(buffer.position() + BYTE_LENGTH);
        return b;
    }

    // --- Standard Methods for Value Objects ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bban bban = (Bban) o;
        return id_conto.equals(bban.id_conto) &&
                id_proprietario.equals(bban.id_proprietario) &&
                id_banca.equals(bban.id_banca);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id_conto, id_proprietario, id_banca);
    }

    @Override
    public String toString() {
        return "Bank.BBAN{" +
                "conto=" + id_conto +
                ", proprietario=" + id_proprietario +
                ", banca=" + id_banca +
                '}';
    }
}