package redguard;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TextureFile {
    private static Color[] defaultCMap;

    public enum Type {
        TEXBSI, GXA, FNT
    }

    private final Type type;
    private final List<Texture> textures;

    public TextureFile(Type type) {
        this.type = type;
        textures = new ArrayList<>();
    }

    public Type getType() {
        return type;
    }

    public List<Texture> getTextures() {
        return textures;
    }

    public void loadTextures(File textureFile) throws IOException {
        switch (type) {
            case TEXBSI -> loadTEXBSI(textureFile);
            case GXA -> loadGXA(textureFile);
            case FNT -> loadFNT(textureFile);
        }
    }

    private void loadTEXBSI(File textureFile) throws IOException {
        DataInputStream input = new DataInputStream(new FileInputStream(textureFile));
        while (true) {
            if (input.available() < 21) {
                break;
            }
            byte[] beginBytes = Utils.readBytes(input, 21);
            if (beginBytes[0] == 0) {
                break;
            }

            // BSIF or IFHD
            byte[] middleBytes = null;
            if (beginBytes[13] == 'I') { // Check for IFHD
                middleBytes = Utils.readBytes(input, 44);
            }

            // BHDR
            byte[] endBytes = Utils.readBytes(input, 34);
            int width = Utils.byteRangeToInt(endBytes, 12, 2, true);
            int height = Utils.byteRangeToInt(endBytes, 14, 2, true);
            int numFrames = Utils.byteRangeToInt(endBytes, 22, 2, true);

            byte[] headerBytes = getHeaderBytes(beginBytes, middleBytes, endBytes);

            // CMAP and DATA
            Color[] cMap = defaultCMap; // Use default cmap if there is no CMAP section
            if (Utils.readBytes(input, 4)[0] == 'C') { // Check for CMAP
                input.skipBytes(4); // Skip CMAP length
                readCMap(input, 1);
                input.skipBytes(4); // Skip DATA header
            }
            int dataLength = input.readInt();

            // DATA contents
            byte[] frameData = null;
            if (numFrames != 1) {
                frameData = Utils.readBytes(input, height * numFrames * 4);
                dataLength -= frameData.length;
            }
            Color[] colorData = new Color[dataLength];
            for (int i = 0; i < colorData.length; i++) {
                colorData[i] = cMap[Utils.readUnsignedByte(input)];
            }
            textures.add(new Texture(width, height, numFrames, headerBytes, cMap, frameData, colorData));

            // END
            input.skipBytes(8);
        }
        input.close();
    }

    private byte[] getHeaderBytes(byte[] begin, byte[] middle, byte[] end) {
        byte[] headerBytes;
        if (middle == null) {
            headerBytes = new byte[begin.length + end.length];
            System.arraycopy(end, 0, headerBytes, begin.length, end.length);
        } else {
            headerBytes = new byte[begin.length + middle.length + end.length];
            System.arraycopy(middle, 0, headerBytes, begin.length, middle.length);
            System.arraycopy(end, 0, headerBytes, begin.length + middle.length, end.length);
        }
        System.arraycopy(begin, 0, headerBytes, 0, begin.length);
        return headerBytes;
    }

    private void loadGXA(File textureFile) throws IOException {
        DataInputStream input = new DataInputStream(new FileInputStream(textureFile));

        // Includes up to the BPAL size
        Utils.readBytes(input, 50); // header bytes
        Color[] cMap = readCMap(input, 4);

        // BBMP
        input.skipBytes(8);
        while (true) {
            // Read first two bytes (1 followed by 0) and check for end
            byte[] b = Utils.readBytes(input, 2);
            if (b[0] == 'E') {
                break;
            }

            int width = Utils.readLittleEndianShort(input);
            int height = Utils.readLittleEndianShort(input);
            input.skipBytes(12);

            Color[] colorData = new Color[width * height];
            for (int i = 0; i < colorData.length; i++) {
                colorData[i] = cMap[Utils.readUnsignedByte(input)];
            }
            textures.add(new Texture(width, height, 1, null, cMap, null, colorData));
        }
        input.close();
    }

    private void loadFNT(File textureFile) throws IOException {
        DataInputStream input = new DataInputStream(new FileInputStream(textureFile));

        // Includes up to the FPAL size
        Utils.readBytes(input, 72); // beginning bytes
        Color[] cMap = readCMap(input, 4);

        // FBMP
        input.skipBytes(8);
        while (true) {
            byte[] b = Utils.readBytes(input, 4);
            if (b[0] == 'R') {
                input.skipBytes(4);
                int rdatSize = input.readInt();
                Utils.readBytes(input, rdatSize); // ending bytes
                break;
            } else if (b[0] == 'E') {
                break;
            }

            input.skipBytes(2);
            int width = Utils.readLittleEndianShort(input);
            int height = Utils.readLittleEndianShort(input);

            Color[] colorData = new Color[width * height];
            for (int i = 0; i < colorData.length; i++) {
                colorData[i] = cMap[Utils.readUnsignedByte(input)];
            }
            textures.add(new Texture(width, height, 1, null, cMap, null, colorData));
        }
        input.close();
    }

    public void writeTextures(File fileToWrite) throws IOException {
        DataOutputStream output = new DataOutputStream(new FileOutputStream(fileToWrite));
        for (Texture texture : textures) {
            // Header - includes name, BSIF or IFHD, and BHDR
            output.write(texture.getHeaderBytes());

            // CMAP if it is not the default
            if (texture.getCMap() != defaultCMap) {
                output.writeBytes("CMAP");
                writeCMap(output, texture.getCMap());
            }

            // DATA
            output.writeBytes("DATA");
            int dataLength = texture.getColorData().length;
            if (texture.getNumFrames() != 1) {
                dataLength += texture.getFrameData().length;
            }
            output.writeInt(dataLength);

            // Data contents
            if (texture.getNumFrames() != 1) {
                output.write(texture.getFrameData());
            }
            Map<Color, Integer> rgbMap = new HashMap<>();
            for (int i = 0; i < texture.getCMap().length; i++) {
                rgbMap.put(texture.getCMap()[i], i);
            }
            for (Color c : texture.getColorData()) {
                if (rgbMap.containsKey(c)) {
                    output.writeByte(rgbMap.get(c));
                } else {
                    Color closestColor = findClosestColor(c, rgbMap);
                    output.writeByte(rgbMap.get(closestColor));
                }
            }
            output.writeBytes("END ");
            output.writeInt(0);
        }
        output.writeInt(0);
        output.writeInt(0);
        output.writeByte(0);
        output.close();
    }

    private static Color findClosestColor(Color c, Map<Color, Integer> rgbMap) {
        int closestDiff = Integer.MAX_VALUE;
        Color closestColor = null;
        for (Color rgbColor : rgbMap.keySet()) {
            int diff = Math.abs(rgbColor.getRed() - c.getRed()) + Math.abs(rgbColor.getGreen() - c.getGreen()) + Math.abs(rgbColor.getBlue() - c.getBlue());
            if (diff < closestDiff) {
                closestDiff = diff;
                closestColor = rgbColor;
            }
        }
        return closestColor;
    }

    public static void loadDefaultCMap(File cMapFile) throws IOException {
        DataInputStream input = new DataInputStream(new FileInputStream(cMapFile));
        input.skipBytes(8);
        defaultCMap = readCMap(input, 1);
        input.close();
    }

    private static Color[] readCMap(DataInputStream input, int multiplier) throws IOException {
        Color[] cMap = new Color[256];
        for (int i = 0; i < cMap.length; i++) {
            int r = Utils.readUnsignedByte(input) * multiplier;
            int g = Utils.readUnsignedByte(input) * multiplier;
            int b = Utils.readUnsignedByte(input) * multiplier;
            cMap[i] = new Color(r, g, b);
        }
        return cMap;
    }

    private static void writeCMap(DataOutputStream output, Color[] cMap) throws IOException {
        for (Color c : cMap) {
            int r = c.getRed();
            output.writeByte(r);
            int g = c.getGreen();
            output.writeByte(g);
            int b = c.getBlue();
            output.writeByte(b);
        }
    }
}