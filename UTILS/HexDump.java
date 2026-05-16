package org.example.bancariofaccia.protocol.UTILS;

public class HexDump {

    /**
     * Prints a hex dump of the given byte array.
     *
     * Format:
     * 00000000  48 65 6C 6C 6F 20 77 6F  72 6C 64           |Hello world|
     *
     * @param data the byte array to dump
     */
    public static void printHex(byte[] data) {
        final int bytesPerLine = 16;

        for (int i = 0; i < data.length; i += bytesPerLine) {

            // Print offset (8 hex digits)
            System.out.printf("%08X  ", i);

            // Print hex values
            for (int j = 0; j < bytesPerLine; j++) {
                if (i + j < data.length) {
                    System.out.printf("%02X ", data[i + j]);
                } else {
                    System.out.print("   "); // padding
                }

                // Add extra space in the middle (8 bytes)
                if (j == 7) System.out.print(" ");
            }

            System.out.print(" |");

            // Print ASCII representation
            for (int j = 0; j < bytesPerLine; j++) {
                if (i + j < data.length) {
                    byte b = data[i + j];
                    if (b >= 32 && b <= 126) {
                        System.out.print((char) b);
                    } else {
                        System.out.print(".");
                    }
                }
            }

            System.out.println("|");
        }
    }
}