package org.example.bancariofaccia.protocol;

import org.example.bancariofaccia.protocol.Bank.Bank;
import org.example.bancariofaccia.protocol.Messaggi.ClientHandler;
import org.example.bancariofaccia.protocol.Messaggi.Connection;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;

public class BankServer {
    private static final int PORT = 8443;
    private static final String KEYSTORE_FILE = "server.p12";
    private static final String KEYSTORE_PASS = "bankpassword"; // Hardcoded for Dev Mode
    public static Bank bank;

    public static void main(String[] args) {
        try {
            System.out.println("Starting Bank Server...");
            
            // Carica la banca dal salvataggio, se esiste
            bank = Bank.caricaStato();

            // 1. Ensure the TLS Keystore exists
            ensureKeystoreExists();

            // 2. Load the Keystore
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(KEYSTORE_FILE)) {
                keyStore.load(fis, KEYSTORE_PASS.toCharArray());
            }

            // 3. Set up the Key Manager and TLS Context
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(keyStore, KEYSTORE_PASS.toCharArray());

            SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(kmf.getKeyManagers(), null, null);

            // 4. Start the Server Socket
            SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
            SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(PORT);

            System.out.println("TLS Bank Server listening on port " + PORT + "...");

            // 5. The Accept Loop
            while (true) {
                try {
                    // Wait for a client to connect
                    SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                    System.out.println("New client connected from " + clientSocket.getInetAddress());

                    // Wrap it in our custom Connection (This instantly sends the Nonce!)
                    Connection conn = Connection.acceptClient(clientSocket);

                    // Hand it off to a new thread so the server can accept more clients
                    new Thread(new ClientHandler(conn, bank)).start();

                } catch (Exception e) {
                    System.err.println("Failed to accept client connection: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("CRITICAL SERVER FAILURE:");
            e.printStackTrace();
        }
    }

    /**
     * Dev Mode feature: Automatically generates a self-signed TLS certificate if missing.
     */
    private static void ensureKeystoreExists() throws Exception {
        File keystore = new File(KEYSTORE_FILE);
        if (!keystore.exists()) {
            System.out.println("No Keystore found. Generating 'server.p12' automatically...");
            String command = String.format(
                    "keytool -genkeypair -alias server-alias -keyalg RSA -keysize 2048 -validity 365 " +
                            "-keystore %s -storetype PKCS12 -storepass %s -dname \"CN=MyBank, OU=Dev, O=School, L=City, S=State, C=US\"",
                    KEYSTORE_FILE, KEYSTORE_PASS
            );
            Process p = Runtime.getRuntime().exec(command);
            p.waitFor();

            if (!keystore.exists()) {
                throw new RuntimeException("Failed to generate keystore. Ensure JDK 'keytool' is in your system PATH.");
            }
            System.out.println("Keystore generated successfully!");
        }
    }
}