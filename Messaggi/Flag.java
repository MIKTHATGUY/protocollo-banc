package org.example.bancariofaccia.protocol.Messaggi;


public enum Flag {
    REQUEST(0x00),
    RESPONSE(0x01),
    ERROR(0x02),
    ASYNC(0x04),
    REQUEST_NO_AUTH(0x08),
    NO_AUTH(0x08);

    private final byte value;
    private static final Flag[] LOOKUP = new Flag[256];

    static {
        for (Flag f : values()) {
            LOOKUP[f.value & 0xFF] = f;
        }
    }

    Flag(int value) {
        this.value = (byte) value;
    }

    public byte getValue() {
        return value;
    }

    public static Flag fromByte(byte b) {
        Flag f = LOOKUP[b & 0xFF];
        if (f == null) {
            throw new IllegalArgumentException("Unknown Flag: 0x" + Integer.toHexString(b & 0xFF));
        }
        return f;
    }
}
