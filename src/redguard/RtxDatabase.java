package redguard;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class RtxDatabase {
    private static final byte[] AUDIO_NO = {0, 0};
    private static final byte[] AUDIO_YES = {0, 1};

    private final Map<String, RtxEntry> rtxEntryMap;

    public RtxDatabase() {
        rtxEntryMap = new LinkedHashMap<>();
    }

    public RtxDatabase(RtxDatabase other) {
        rtxEntryMap = new LinkedHashMap<>(other.rtxEntryMap);
    }

    public Stream<RtxEntry> stream() {
        return rtxEntryMap.values().stream();
    }

    public RtxEntry get(String label) {
        return rtxEntryMap.get(label);
    }

    public RtxEntry get(int index) {
        return stream().toList().get(index);
    }

    public int indexOf(RtxEntry rtxEntry) {
        return stream().toList().indexOf(rtxEntry);
    }

    public int size() {
        return rtxEntryMap.size();
    }

    public void add(RtxEntry rtxEntry) {
        rtxEntryMap.put(rtxEntry.getLabel(), rtxEntry);
    }

    public void remove(RtxEntry rtxEntry) {
        rtxEntryMap.remove(rtxEntry.getLabel());
    }

    public boolean hasLabel(String label) {
        return rtxEntryMap.containsKey(label);
    }

    public void readFile(File fileToRead) throws IOException {
        rtxEntryMap.clear();
        DataInputStream input = new DataInputStream(new FileInputStream(fileToRead));
        while (true) {
            String label = Utils.readString(input, 4);
            if (label.equals("END ")) {
                break;
            }

            input.readInt(); // total length, not needed
            boolean hasAudio = input.readShort() == 1;

            int subtitleLength = Utils.readLittleEndianInt(input);
            String subtitle = Utils.readString(input, subtitleLength);

            byte[] audioBytes = null;
            int sampleRate = 11025;
            int doubleSize = 0;
            if (hasAudio) {
                doubleSize = Utils.readLittleEndianInt(input); // 1 for all 22050 sample rate sounds except #vi1, #vi2, #vi3, txx1, txx2
                Utils.readLittleEndianInt(input); // same as above
                sampleRate = Utils.readLittleEndianInt(input);
                Utils.readLittleEndianInt(input); // always 100
                Utils.readLittleEndianShort(input); // always 0
                Utils.readLittleEndianInt(input); // always -1
                int audioLength = Utils.readLittleEndianInt(input);
                input.readByte(); // always 0
                audioBytes = Utils.readBytes(input, audioLength);
            }
            rtxEntryMap.put(label, new RtxEntry(label, subtitle, audioBytes, sampleRate, doubleSize));
        }
        input.close();
    }

    public void writeFile(File fileToWrite) throws IOException {
        List<RtxEntry> rtxEntryList = stream().toList();
        DataOutputStream output = new DataOutputStream(new FileOutputStream(fileToWrite));
        int totalBytes = 0;
        int[] entryPositions = new int[rtxEntryList.size()];
        for (int i = 0; i < rtxEntryList.size(); i++) {
            RtxEntry entry = rtxEntryList.get(i);
            String label = entry.getLabel();
            int subtitleLength = entry.subtitleLength();
            output.writeBytes(label);
            output.writeInt(entry.length());
            totalBytes += 8;
            entryPositions[i] = totalBytes;
            output.write(entry.hasAudio() ? AUDIO_YES : AUDIO_NO);
            Utils.writeLittleEndianInt(output, subtitleLength);
            output.writeBytes(entry.getSubtitle());
            totalBytes += 6 + subtitleLength;
            if (entry.hasAudio()) {
                int audioLength = entry.getAudioBytes().length;
                int sampleRate = entry.getSampleRate();
                int doubleSize = entry.getDoubleSize() ? 1 : 0;
                Utils.writeLittleEndianInt(output, doubleSize);
                Utils.writeLittleEndianInt(output, doubleSize);
                Utils.writeLittleEndianInt(output, sampleRate);
                Utils.writeLittleEndianInt(output, 100);
                Utils.writeLittleEndianShort(output, 0);
                Utils.writeLittleEndianInt(output, -1);
                Utils.writeLittleEndianInt(output, audioLength);
                output.writeByte(0);
                output.write(entry.getAudioBytes());
                totalBytes += 27 + audioLength;
            }
        }
        output.writeBytes("END ");
        totalBytes += 4;
        for (int i = rtxEntryList.size() - 1; i >= 0; i--) {
            RtxEntry entry = rtxEntryList.get(i);
            output.writeBytes(entry.getLabel());
            Utils.writeLittleEndianInt(output, entryPositions[i]);
            Utils.writeLittleEndianInt(output, entry.length());
        }
        output.writeBytes("RNAV");
        Utils.writeLittleEndianInt(output, totalBytes);
        Utils.writeLittleEndianInt(output, rtxEntryList.size());
        output.close();
    }

    public void applyChanges(File changesFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(changesFile));
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            String[] entry = line.split("\t");
            String subtitle = entry.length < 3 ? "" : entry[2];
            int index = Integer.parseInt(entry[0]);
            if (index >= rtxEntryMap.size()) {
                rtxEntryMap.put(entry[1], new RtxEntry(entry[1], subtitle));
            } else {
                rtxEntryMap.get(entry[1]).setSubtitle(subtitle);
            }
        }
        reader.close();
    }

    public void loadAudioFolder(File audioFolder) throws UnsupportedAudioFileException, IOException {
        File[] audioFileArray = audioFolder.listFiles();
        if (audioFileArray == null) return;
        for (File audioFile : audioFileArray) {
            String filename = audioFile.getName();
            String labelStr = filename.substring(0, filename.lastIndexOf("."));
            RtxEntry rtxEntry = get(labelStr);
            rtxEntry.loadAudioFromFile(audioFile);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (RtxEntry entry : rtxEntryMap.values()) {
            sb.append(entry.getLabel()).append(": ").append(entry.getSubtitle()).append("\n");
        }
        return sb.toString();
    }
}