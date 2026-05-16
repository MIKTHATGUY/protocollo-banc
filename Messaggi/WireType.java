package org.example.bancariofaccia.protocol.Messaggi;

import org.example.bancariofaccia.protocol.Bank.Bban;
import org.example.bancariofaccia.protocol.PreciseMoney.Money;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;

public enum WireType {
    LONG(0x01) {
        @Override
        public int sizeOf(Object value) {
            return 8;
        }

        @Override
        public byte[] toBytes(Object value) {
            return ByteBuffer.allocate(8).putLong((Long) value).array();
        }

        @Override
        public Long fromBytes(byte[] data) {
            return ByteBuffer.wrap(data).getLong();
        }
    },
    MONEY(0x02) {
        @Override
        public int sizeOf(Object value) {
            // Example: 12-byte fixed-point (scale + unscaled)
            Money money = (Money) value;
            return money.asByteBuffer().array().length;
        }

        @Override
        public byte[] toBytes(Object value) {
            // Implement based on your PreciseMoney class
            Money money = (Money) value;
            return money.asByteBuffer().array();
        }

        @Override
        public Object fromBytes(byte[] data) {
            return Money.fromByteBuffer(ByteBuffer.wrap(data));
        }
    },



    STRING(0x03) {
        @Override
        public int sizeOf(Object value) {
            return ((String) value).getBytes(StandardCharsets.UTF_8).length;
        }

        @Override
        public byte[] toBytes(Object value) {
            return ((String) value).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String fromBytes(byte[] data) {
            return new String(data, StandardCharsets.UTF_8);
        }
    },
    PUBLIC_KEY(0x04) {
        @Override
        public int sizeOf(Object value) {
            return ((PublicKey) value).getEncoded().length;
        }

        @Override
        public byte[] toBytes(Object value) {
            return ((PublicKey) value).getEncoded();
        }

        @Override
        public PublicKey fromBytes(byte[] data) {
            try {
                java.security.spec.X509EncodedKeySpec spec =
                        new java.security.spec.X509EncodedKeySpec(data);

                java.security.KeyFactory kf =
                        java.security.KeyFactory.getInstance("Ed25519");

                return kf.generatePublic(spec);
            } catch (Exception e) {
                throw new RuntimeException("Failed to decode PublicKey", e);
            }
        }

    },
    BBAN(0x05) {
        @Override
        public int sizeOf(Object value) {
            return Bban.getByteLength();
        }

        @Override
        public byte[] toBytes(Object value) {

            return ((Bban) value).encode();
        }

        @Override
        public Object fromBytes(byte[] data) {
            return new Bban(data);
        }
    },
    INT(0x06) {
        @Override
        public int sizeOf(Object value) {
            return 4;
        }

        @Override
        public byte[] toBytes(Object value) {
            return ByteBuffer.allocate(4).putInt((Integer) value).array();
        }

        @Override
        public Integer fromBytes(byte[] data) {
            return ByteBuffer.wrap(data).getInt();
        }
    },
    BYTE_ARRAY(0x07) {
        @Override
        public int sizeOf(Object value) {
            return ((byte[]) value).length;
        }

        @Override
        public byte[] toBytes(Object value) {
            return (byte[]) value;
        }

        @Override
        public byte[] fromBytes(byte[] data) {
            return data.clone();
        }
    };


    private final byte value;
    private static final WireType[] LOOKUP = new WireType[256];

    static {
        for (WireType wt : values()) {
            LOOKUP[wt.value & 0xFF] = wt;
        }
    }

    WireType(int value) {
        this.value = (byte) value;
    }

    public byte getValue() {
        return value;
    }

    public static WireType fromByte(byte b) {
        WireType wt = LOOKUP[b & 0xFF];
        if (wt == null) {
            throw new IllegalArgumentException("Unknown WireType: 0x" + Integer.toHexString(b & 0xFF));
        }
        return wt;
    }

    /**
     * Size of the *payload* (data bytes only, not the 4-byte header).
     */
    public abstract int sizeOf(Object value);

    public abstract byte[] toBytes(Object value);

    public abstract Object fromBytes(byte[] data);
}
