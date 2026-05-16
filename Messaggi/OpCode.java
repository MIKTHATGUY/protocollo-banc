package org.example.bancariofaccia.protocol.Messaggi;

public enum OpCode {
    ACCOUNT_CREATION(0x00),
    TRANSFER(0x01),
    BALANCE(0x02),
    CONFIRMATION(0x03),
    REGISTRATION_CONFIRMATION(0x04),
    TRANSACTION_HISTORY(0x05),
    CREATE_ACCOUNT(0x06),
    VALIDATE_BBAN(0x07),
    LOGIN(0x08),
    FETCH_ACCOUNTS(0x09),
    REQUEST_CERTIFICATE(0x0A),
    RENAME_ACCOUNT(0x0B),
    SET_ACCOUNT_STATUS(0x0C),
    FETCH_EXCHANGE_RATES(0x0D);

    private final byte value;
    private static final OpCode[] LOOKUP = new OpCode[256];

    static {
        for (OpCode op : values()) {
            LOOKUP[op.value & 0xFF] = op;
        }
    }

    OpCode(int value) {
        this.value = (byte) value;
    }

    public byte getValue() {
        return value;
    }

    public static OpCode fromByte(byte b) {
        OpCode op = LOOKUP[b & 0xFF];
        if (op == null) {
            throw new IllegalArgumentException("Unknown OpCode: 0x" + Integer.toHexString(b & 0xFF));
        }
        return op;
    }
}

