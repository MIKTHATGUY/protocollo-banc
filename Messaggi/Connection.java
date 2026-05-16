package org.example.bancariofaccia.protocol.Messaggi;

import javax.net.ssl.*;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * A secure, authenticated transport layer for the Banking Protocol.
 * This class handles the TLS encryption, network framing, and the cryptographic Nonce exchange.
 */
public class Connection {
    private final Socket socket;
    private final OutputStream out;
    private final InputStream in;

    // The cryptographically secure session challenge.
    // Client must put this in EVERY Messaggio's AuthBlock.
    private final long sessionNonce;

    /**
     * Private constructor. You must use the static factories below to ensure
     * the TLS handshake and Nonce exchange happen correctly.
     */
    private Connection(Socket socket, long sessionNonce) throws IOException {
        this.socket = socket;
        this.out = socket.getOutputStream();
        this.in = socket.getInputStream();
        this.sessionNonce = sessionNonce;
    }

    /* ============================================================
       TLS FACTORY METHODS
       ============================================================ */

    /**
     * CLIENT SIDE: Connects to the server, establishes "Dev Mode" TLS, and reads the Nonce.
     */
    public static Connection createClient(String host, int port) throws Exception {
        // 1. Dev Mode: Blind trust manager
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(null, trustAllCerts, new SecureRandom());

        // 2. Connect and Handshake
        SSLSocketFactory factory = sslContext.getSocketFactory();
        SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
        socket.startHandshake();

        // 3. Read the 8-byte session Nonce from the server immediately
        DataInputStream dataIn = new DataInputStream(socket.getInputStream());
        long nonce = dataIn.readLong();

        return new Connection(socket, nonce);
    }

    /**
     * SERVER SIDE: Wraps an accepted SSLSocket, generates the Nonce, and sends it to the client.
     */
    public static Connection acceptClient(SSLSocket socket) throws Exception {
        // 1. Generate a cryptographically secure 8-byte nonce
        long nonce = new SecureRandom().nextLong();

        // 2. Send it to the client immediately
        DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
        dataOut.writeLong(nonce);
        dataOut.flush();

        return new Connection(socket, nonce);
    }

    /* ============================================================
       GETTERS
       ============================================================ */

    /**
     * Returns the session Nonce.
     * The client MUST call this and use it in: msg.withAuth(uuid, seq, connection.getSessionNonce());
     */
    public long getSessionNonce() {
        return sessionNonce;
    }

    public Socket getSocket() {
        return socket;
    }

    /* ============================================================
       PACKET I/O
       ============================================================ */

    public void send(Messaggio messaggio) throws IOException {
        // Getting the binary array is instant because of your custom serializer
        byte[] messageBytes = messaggio.toByteArray();
        out.write(messageBytes);
        out.flush();
    }

    public Messaggio receive() throws IOException {
        // 1. Read exactly the fixed-size header (8 bytes).
        byte[] headerBytes = new byte[Messaggio.Header.SIZE];
        readExactly(in, headerBytes);

        // 2. Extract payload size (Requires Messaggio to expose this statically, which you did brilliantly)
        int totalSize = Messaggio.getTotalPacketSize(headerBytes);
        int payloadSize = totalSize - Messaggio.Header.SIZE;

        // 3. Read exactly the remaining payload bytes.
        byte[] payloadBytes = new byte[payloadSize];
        readExactly(in, payloadBytes);

        // 4. Stitch header + payload together and parse.
        ByteBuffer full = ByteBuffer.allocate(totalSize);
        full.put(headerBytes).put(payloadBytes).flip();

        return new Messaggio(full);
    }

    /**
     * Reads exactly `len` bytes from the stream, or throws EOFException.
     * This safely prevents partial TCP packet reads.
     */
    private void readExactly(InputStream stream, byte[] dest) throws IOException {
        int offset = 0;
        while (offset < dest.length) {
            int read = stream.read(dest, offset, dest.length - offset);
            if (read == -1) {
                throw new EOFException("Stream closed unexpectedly before reading full packet.");
            }
            offset += read;
        }
    }

    public void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }
}