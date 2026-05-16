package org.example.bancariofaccia.protocol.Messaggi;

public enum FieldID {
    TARGET_ACCOUNT(0x01),
    AMOUNT(0x02),
    CURRENCY(0x03),
    TIMESTAMP(0x04),
    ERROR_CODE(0x05),
    ERROR_MESSAGE(0x06),
    USERNAME_TO_CREATE(0x07),
    PUBLIC_KEY(0x08),
    CONFIRMATION_MESSAGE(0x09),
    BBAN(0x0A),
    UUID(0x0B),
    REASON(0x0C),
    ACCOUNT_STATUS(0x0D),
    ACCOUNT_COUNT(0x0E),
    ACCOUNT_DATA(0x0F),
    TRANSACTION_DATA(0x10),
    DIRECTION(0x11),
    COUNTERPART_BBAN(0x12),
    CHALLENGE(0x13),
    SIGNED_CHALLENGE(0x14),
    VALID(0x15),
    FILE_PAYLOAD(0x16),
    SIGNATURE(0x17),
    ACCOUNT_NAME(0x18),
    EXCHANGE_RATES_PAYLOAD(0x19);


    private final byte value;
    private static final FieldID[] LOOKUP = new FieldID[256];

    static {
        for (FieldID f : values()) {
            LOOKUP[f.value & 0xFF] = f;
        }
    }

    FieldID(int value) {
        this.value = (byte) value;
    }

    public byte getValue() {
        return value;
    }

    public static FieldID fromByte(byte b) {
        FieldID f = LOOKUP[b & 0xFF];
        if (f == null) {
            throw new IllegalArgumentException("Unknown FieldID: 0x" + Integer.toHexString(b & 0xFF));
        }
        return f;
    }
}