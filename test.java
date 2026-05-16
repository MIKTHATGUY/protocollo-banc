package org.example.bancariofaccia.protocol;

import org.example.bancariofaccia.protocol.PreciseMoney.Money;
import org.example.bancariofaccia.protocol.UTILS.AsymmetricParser;
import org.example.bancariofaccia.protocol.UTILS.CryptoUtils;
import org.example.bancariofaccia.protocol.UTILS.HexDump;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.*;
import java.util.Arrays;
import java.util.Currency;
import java.util.Locale;

public class test {
    static void main() throws NoSuchAlgorithmException {


        KeyPair keyPair = CryptoUtils.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();






        AsymmetricParser asymmetricParser = new AsymmetricParser();


        System.out.println(Arrays.toString(publicKey.getEncoded()));
        System.out.println(publicKey.getFormat());
        System.out.println(publicKey.getAlgorithm());

        AsymmetricKey publicClone = asymmetricParser.createkeyFromValues(publicKey.getEncoded());

        System.out.println(Arrays.toString(publicClone.getEncoded()));
        System.out.println(publicClone.getFormat());
        System.out.println(publicClone.getAlgorithm());


        System.out.println(publicClone.equals((AsymmetricKey)  publicKey));





    }
}
