package redguard;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static modManager.RedguardModManager.logger;

public class Utils {
    public static byte[] readBytes(DataInputStream input, int numBytes) throws IOException {
        byte[] array = new byte[numBytes];
        if (input.read(array) != numBytes) {
            logger.warning("End of file reached early while reading " + numBytes + " bytes from data input stream.");
        }
        return array;
    }

    public static int readUnsignedByte(DataInputStream input) throws IOException {
        return input.readByte() & 0xff;
    }

    public static int readLittleEndianShort(DataInputStream input) throws IOException {
        return byteArrayToInt(readBytes(input, 2), true);
    }

    public static int readLittleEndianInt(DataInputStream input) throws IOException {
        return byteArrayToInt(readBytes(input, 4), true);
    }

    public static String readString(DataInputStream input, int n) throws IOException {
        return new String(readBytes(input, n));
    }

    public static void writeLittleEndianShort(DataOutputStream output, int num) throws IOException {
        output.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) num).array());
    }

    public static void writeLittleEndianInt(DataOutputStream output, int num) throws IOException {
        output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(num).array());
    }

    public static int byteArrayToInt(byte[] array, boolean littleEndian) {
        int value = 0;
        for (int i = 0; i < array.length; i++) {
            if (littleEndian) {
                value += (array[i] & 0xff) << (8 * i);
            } else {
                value = (value << 8) + (array[i] & 0xff);
            }
        }
        return value;
    }

    public static int byteRangeToInt(byte[] array, int start, int length, boolean littleEndian) {
        int value = 0;
        for (int i = 0; i < length; i++) {
            if (littleEndian) {
                value += (array[start + i] & 0xff) << (8 * i);
            } else {
                value = (value << 8) + (array[start + i] & 0xff);
            }
        }
        return value;
    }

    public static byte[] shortToByteArray(short num, boolean littleEndian) {
        return ByteBuffer.allocate(2).order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN).putShort(num).array();
    }

    public static byte[] intToByteArray(int num, boolean littleEndian) {
        return ByteBuffer.allocate(4).order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN).putInt(num).array();
    }

    public static void copyDoNotReplace(Path sourcePath, Path backupPath) throws IOException {
        if (!backupPath.toFile().exists()) {
            Files.copy(sourcePath, backupPath);
        }
    }

    public static String validFilename(String str) {
        return str.replaceAll("[\\\\/:*\"<>|]", "_");
    }
}