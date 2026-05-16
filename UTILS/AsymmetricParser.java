package org.example.bancariofaccia.protocol.UTILS;

import java.security.AsymmetricKey;

public class AsymmetricParser {

    static private final byte ASYNC = 0x00;


    public AsymmetricParser(){

    }

    public AsymmetricKey createkeyFromValues(byte[] encoded){
        AsymmetricKey key = new AsymmetricKey() {
            @Override
            public String getAlgorithm() {
                return "EdDSA";
            }

            @Override
            public String getFormat() {
                return "X.509";
            }

            @Override
            public byte[] getEncoded() {
                return encoded;
            }
        };
        return key;
    }

}
