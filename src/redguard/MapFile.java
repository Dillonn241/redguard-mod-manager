package redguard;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class MapFile {
    private final MapDatabase mapDatabase;
    private final String name, fullName;
    private final List<Integer> ids;
    private final Map<String, byte[]> records;
    private final List<MapHeader> mapHeaders;
    private int scriptDataOffset;

    public MapFile(MapDatabase mapDatabase, String name) {
        this.mapDatabase = mapDatabase;
        this.name = name;
        fullName = "MAPS/" + name + ".RGM";
        ids = new ArrayList<>();
        records = new HashMap<>();
        mapHeaders = new ArrayList<>();
    }

    public String getFullName() {
        return fullName;
    }

    public String getName() {
        return name;
    }

    public List<Integer> getIDs() {
        return ids;
    }

    public void addID(int id) {
        ids.add(id);
    }

    public void readMap(File fileToRead) throws IOException {
        records.clear();
        mapHeaders.clear();

        DataInputStream input = new DataInputStream(new FileInputStream(fileToRead));
        while (input.available() > 0) {
            String name = Utils.readString(input, 4);
            if (name.equals("END ")) {
                break;
            }
            int length = input.readInt();
            records.put(name, Utils.readBytes(input, length));
        }
        input.close();

        // RAFS - Fsphere

        // RAHD - Headers
        byte[] headerBytes = records.get("RAHD");
        int numMapHeaders = Utils.byteRangeToInt(headerBytes, 0, 4, true);
        for (int i = 0; i < numMapHeaders; i++) {
            int start = 8 + i * 165;
            byte[] subrecord = Arrays.copyOfRange(headerBytes, start, start + 165);
            mapHeaders.add(new MapHeader(subrecord));
        }

        // RAST - String tables
        String allStrings = new String(records.get("RAST"));

        // RASB - String offsets for RAST
        byte[] stringOffsets = records.get("RASB");

        // RAVA - Local variables
        byte[] variableBytes = records.get("RAVA");
        List<Integer> variables = new ArrayList<>();
        for (int i = 0; i < variableBytes.length / 4; i++) {
            variables.add(Utils.byteRangeToInt(variableBytes, i * 4, 4, true));
        }

        // RASC - Scripts
        scriptDataOffset = mapHeaders.getFirst().getScriptDataOffset();
        int scriptPos = scriptDataOffset;

        // Initialize map headers
        for (MapHeader header : mapHeaders) {
            header.initStrings(allStrings, stringOffsets);
            header.initVariables(variables);

            // Read this header's part of the script
            byte[] scriptBytes = Arrays.copyOfRange(records.get("RASC"), scriptPos, scriptPos + header.getScriptLength());
            scriptPos += header.getScriptLength();
            header.setScriptBytes(scriptBytes);
        }

        // RAHK - Hooks
        // RALC - Locations
        // RAEX - Extra

        //RAAT - Attributes
        for (int i = 0; i < mapHeaders.size(); i++) {
            byte[] attributeBytes = Arrays.copyOfRange(records.get("RAAT"), i * 256, (i + 1) * 256);
            mapHeaders.get(i).setAttributeBytes(attributeBytes);
        }

        for (MapHeader header : mapHeaders) {
            ScriptReader scriptReader = new ScriptReader(mapDatabase, header);
            header.setScript(scriptReader.read());
        }

        // RAAN - Animation file
        // RAGR - Animation group
        // RANM - Name space
        // MPOB - Objects
        // MPRP - Ropes
        // MPSO - Statics
        // MPSL - Lights
        // MPSF - Flats
        // MPMK - Markers
        // MPSZ - Sizes
        // WDNM - Node maps
        // FLAT - Flats
    }

    public boolean isEmpty() {
        return mapHeaders.isEmpty();
    }

    public void writeMap(File fileToWrite, String script) throws IOException {
        DataOutputStream output = new DataOutputStream(new FileOutputStream(fileToWrite));
        ScriptParser scriptParser = new ScriptParser(mapDatabase, script);
        List<ParsedMapHeader> parsedHeaders = scriptParser.parse();

        // RAHD - Headers
        output.writeBytes("RAHD"); // Section name
        output.writeInt(8 + parsedHeaders.size() * 165); // Section length
        Utils.writeLittleEndianInt(output, parsedHeaders.size()); // Number of headers
        // Always the same four bytes
        output.writeByte(27);
        output.writeByte(128);
        output.writeByte(55);
        output.writeByte(0);

        // Copy possibly new script information to header data
        for (int i = 0; i < mapHeaders.size(); i++) {
            ParsedMapHeader parsedHeader = parsedHeaders.get(i);
            byte[] data = mapHeaders.get(i).getData();

            // Script length, calculated by the script parser
            byte[] scriptLength = Utils.intToByteArray(parsedHeader.getScriptBytes().length, true);
            System.arraycopy(scriptLength, 0, data, 77, 4);
            // Script offset, from parser
            int dataOffset = scriptDataOffset + parsedHeader.getScriptDataOffset();
            byte[] scriptDataOffset = Utils.intToByteArray(dataOffset, true);
            System.arraycopy(scriptDataOffset, 0, data, 81, 4);
            // Execution offset, also from parser
            byte[] scriptPC = Utils.intToByteArray(parsedHeader.getScriptPC(), true);
            System.arraycopy(scriptPC, 0, data, 85, 4);

            output.write(data);
        }

        // RAFS - Fsphere
        output.writeBytes("RAFS"); // Section header
        output.writeInt(1); // Always length 1
        output.writeByte(0);

        // RAST - All strings
        Set<String> strings = new LinkedHashSet<>();
        for (ParsedMapHeader header : parsedHeaders) {
            strings.addAll(header.getStrings());
        }
        StringBuilder rast = new StringBuilder();
        for (String str : strings) {
            rast.append(str).append("\u0000");
        }
        output.writeBytes("RAST"); // Section header
        output.writeInt(rast.length()); // Section length
        output.writeBytes(rast.toString());

        // RASB - String offsets for RAST
        ByteArrayOutputStream rasb = new ByteArrayOutputStream();
        for (ParsedMapHeader header : parsedHeaders) {
            for (String str : header.getStrings()) {
                rasb.write(Utils.intToByteArray(rast.indexOf(str), true));
            }
        }
        output.writeBytes("RASB"); // Section header
        output.writeInt(rasb.size()); // Section length
        output.write(rasb.toByteArray());

        // RAVA - Local variables
        ByteArrayOutputStream rava = new ByteArrayOutputStream();
        rava.write(new byte[] {0, 0, 0, 0});
        for (MapHeader header : mapHeaders) {
            for (int j = 0; j < header.getInstances(); j++) {
                for (int variable : header.getVariables()) {
                    rava.write(Utils.intToByteArray(variable, true));
                }
            }
        }
        output.writeBytes("RAVA"); // Section header
        output.writeInt(rava.size()); // Section length
        output.write(rava.toByteArray());

        // RASC - Scripts
        output.writeBytes("RASC"); // Section header
        output.writeInt(scriptDataOffset + scriptParser.getTotalScriptLength()); // Section length
        // Initial data offset, all zeros
        for (int i = 0; i < scriptDataOffset; i++) {
            output.writeByte(0);
        }
        // Write parsed scripts
        for (ParsedMapHeader parsedHeader : parsedHeaders) {
            output.write(parsedHeader.getScriptBytes());
        }

        // Write records between script and attributes
        copyRecordsToOutput(output, "RAHK", "RALC", "RAEX");

        // RAAT - Attributes
        output.writeBytes("RAAT");
        output.write(Utils.intToByteArray(parsedHeaders.size() * 256, false));
        for (ParsedMapHeader parsedHeader : parsedHeaders) {
            output.write(parsedHeader.getAttributeBytes());
        }

        // Write remaining records
        copyRecordsToOutput(output, "RAAN", "RAGR", "RANM", "MPOB", "MPRP", "MPSO", "MPSL", "MPSF", "MPMK", "MPSZ", "WDNM", "FLAT");
        output.writeBytes("END ");
        output.close();
    }

    private void copyRecordsToOutput(DataOutputStream output, String... recordNames) throws IOException {
        for (String recordName : recordNames) {
            output.writeBytes(recordName);
            byte[] data = records.get(recordName);
            output.write(Utils.intToByteArray(data.length, false));
            output.write(data);
        }
    }

    public String getScript() {
            StringBuilder sb = new StringBuilder();
            sb.append("Maps\\").append(name).append(".RGM\nID");
            if (ids.size() > 1) {
                sb.append("s");
            }
            sb.append(": ").append(ids.stream().map(String::valueOf).collect(Collectors.joining(", "))).append("\n\n");
            for (int i = 0; i < mapHeaders.size(); i++) {
                MapHeader mapHeader = mapHeaders.get(i);
                if (i > 0) {
                    sb.append("\n\n");
                }
                if (mapHeader.getVariables().size() > 4) {
                    for (int j = 2; j < mapHeader.getVariables().size() - 2; j++) {
                        sb.append("var").append(j).append(" = ").append(mapHeader.getVariables().get(j)).append("\n");
                        if (j == mapHeader.getVariables().size() - 3) {
                            sb.append("\n");
                        }
                    }
                }
                boolean hasAttribute = false;
                for (int j = 0; j < mapHeader.getAttributeBytes().length; j++) {
                    byte value = mapHeader.getAttributeBytes()[j];
                    if (value != 0) {
                        String name = mapDatabase.getAttributes().get(j);
                        sb.append(name).append(" = ").append(value).append("\n");
                        hasAttribute = true;
                    }
                }
                if (hasAttribute) {
                    sb.append("\n");
                }
                sb.append(mapHeader.getScript());
            }
            return sb.toString();
    }

    public String getModifiedScript(MapChanges mapChanges) {
        StringBuilder sb = new StringBuilder();
        String[] scriptLines = getScript().split("\n");

        for (int pos = 0; pos < scriptLines.length; pos++) {
            List<String> lines = mapChanges.lineChangesAt(name, pos);
            if (lines == null) {
                sb.append(scriptLines[pos]).append("\n");
            } else {
                if (!lines.getFirst().equals("null")) {
                    sb.append(scriptLines[pos]).append("\n");
                }
                for (String line : lines) {
                    if (!line.equals("null")) {
                        sb.append(line).append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MapFile) {
            return name.equals(((MapFile) obj).getName());
        }
        return false;
    }

    @Override
    public String toString() {
        return name;
    }
}