package org.example.bancariofaccia.protocol.UTILS;

import org.example.bancariofaccia.protocol.Messaggi.Messaggio;

import java.security.*;

public class CryptoUtils {

    private static final String ALGORITHM = "Ed25519";

    /**
     * Generates a new Ed25519 KeyPair.
     */
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(ALGORITHM);
        return kpg.generateKeyPair();
    }

    /**
     * Signs the Messaggio using the provided PrivateKey.
     * The signature is automatically set in the Messaggio object.
     */
    public static void signMessage(Messaggio msg, PrivateKey privateKey) {
        Signature sig = null;
        byte[] signature;
        try {
            sig = Signature.getInstance(ALGORITHM);
            sig.initSign(privateKey);
            sig.update(msg.getSignableBytes());
            signature = sig.sign();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }

        msg.setSignature(signature);
    }

    /**
     * Verifies the Messaggio's signature using the provided PublicKey.
     */
    public static boolean verifyMessage(Messaggio msg, PublicKey publicKey) {
        Signature sig;
        try {
            sig = Signature.getInstance(ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(msg.getSignableBytes());

        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
        try {
            return sig.verify(msg.getSignature());
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }
    }
}

