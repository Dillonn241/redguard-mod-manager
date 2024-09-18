package redguard;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class Texture {
    private final int width, height;
    private final int numFrames;
    private final byte[] headerBytes;
    private final Color[] cMap;
    private byte[] frameData;
    private final Color[] colorData;
    private final BufferedImage[] images;

    public Texture(int width, int height, int numFrames, byte[] headerBytes, Color[] cMap, byte[] frameData, Color[] colorData) {
        this.width = width;
        this.height = height;
        this.numFrames = numFrames;
        this.headerBytes = headerBytes;
        this.cMap = cMap;
        this.frameData = frameData;
        this.colorData = colorData;

        images = new BufferedImage[numFrames];
        if (numFrames == 1) {
            images[0] = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    images[0].setRGB(x, y, colorData[y * width + x].getRGB());
                }
            }
        } else {
            int framePos = 0;
            for (int frame = 0; frame < numFrames; frame++) {
                images[frame] = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < height; y++) {
                    int rowPos = Utils.byteRangeToInt(frameData, framePos, 4, true) - frameData.length;
                    framePos += 4;
                    for (int x = 0; x < width; x++) {
                        images[frame].setRGB(x, y, colorData[rowPos + x].getRGB());
                    }
                }
            }
        }
    }

    public Texture(byte[] headerBytes, Color[] cMap, BufferedImage image) {
        this.headerBytes = headerBytes;
        this.cMap = cMap;
        images = new BufferedImage[1];
        images[0] = image;
        width = image.getWidth();
        height = image.getHeight();
        numFrames = 1;

        colorData = new Color[width * height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                colorData[y * width + x] = new Color(image.getRGB(x, y));
            }
        }
    }

    public Texture(byte[] headerBytes, Color[] cMap, BufferedImage[] images) {
        this.headerBytes = headerBytes;
        this.cMap = cMap;
        this.images = images;
        width = images[0].getWidth();
        height = images[0].getHeight();
        numFrames = images.length;
        frameData = new byte[height * numFrames * 4];

        List<Integer> rgbData = new ArrayList<>();
        List<Integer> offsetData = new ArrayList<>();
        for (int frame = 0; frame < numFrames; frame++) {
            for (int y = 0; y < height; y++) {
                int row = checkForSameRow(rgbData, images[frame], y);
                if (row == -1) {
                    row = rgbData.size();
                    for (int x = 0; x < width; x++) {
                        rgbData.add(images[frame].getRGB(x, y));
                    }
                }
                offsetData.add(row);
            }
        }
        colorData = new Color[rgbData.size()];
        for (int i = 0; i < colorData.length; i++) {
            colorData[i] = new Color(rgbData.get(i));
        }
        frameData = new byte[offsetData.size() * 4];
        int framePos = 0;
        for (int offset : offsetData) {
            byte[] array = Utils.intToByteArray(offset + frameData.length, true);
            System.arraycopy(array, 0, frameData, framePos, 4);
            framePos += 4;
        }
    }

    private int checkForSameRow(List<Integer> rgbData, BufferedImage image, int y) {
        int rows = rgbData.size() / width;
        for (int row = 0; row < rows; row++) {
            boolean same = true;
            for (int x = 0; x < width; x++) {
                if (rgbData.get(row * width + x) != image.getRGB(x, y)) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return row;
            }
        }
        return -1;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getNumFrames() {
        return numFrames;
    }

    public byte[] getHeaderBytes() {
        return headerBytes;
    }

    public Color[] getCMap() {
        return cMap;
    }

    public byte[] getFrameData() {
        return frameData;
    }

    public Color[] getColorData() {
        return colorData;
    }

    public BufferedImage[] getImages() {
        return images;
    }
}