package redguard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MapHeader {
    private final byte[] data;
    private final String name;
    private final int instances;
    private final int scriptLength;
    private final int scriptDataOffset;
    private final int scriptPC;
    private final List<String> strings;
    private final List<Integer> variables;
    private byte[] scriptBytes;
    private byte[] attributeBytes;
    private String script;

    public MapHeader(byte[] data) {
        this.data = data;
        name = new String(Arrays.copyOfRange(data, 4, 13)).trim();
        instances = Utils.byteRangeToInt(data, 13, 2, true);
        scriptLength = Utils.byteRangeToInt(data, 77, 4, true);
        scriptDataOffset = Utils.byteRangeToInt(data, 81, 4, true);
        scriptPC = Utils.byteRangeToInt(data, 85, 4, true);
        strings = new ArrayList<>();
        variables = new ArrayList<>();
    }

    public byte[] getData() {
        return data;
    }

    public String getName() {
        return name;
    }

    public int getInstances() {
        return instances;
    }

    public int getScriptLength() {
        return scriptLength;
    }

    public int getScriptDataOffset() {
        return scriptDataOffset;
    }

    public int getScriptPC() {
        return scriptPC;
    }

    public List<String> getStrings() {
        return strings;
    }

    public void initStrings(String allStrings, byte[] stringOffsets) {
        int numStrings = Utils.byteRangeToInt(data, 65, 4, true); // Number of strings in this header's script
        if (numStrings > 0) {
            int stringOffsetsIndex = Utils.byteRangeToInt(data, 73, 4, true); // Index for header in map's string offsets table
            for (int i = 0; i < numStrings; i++) {
                int stringOffset = Utils.byteRangeToInt(stringOffsets, stringOffsetsIndex + i * 4, 4, true); // Use offset to find beginning of string
                int stringEnd = allStrings.indexOf("\u0000", stringOffset); // String continues until 0 byte
                String str = allStrings.substring(stringOffset, stringEnd);
                strings.add(str);
            }
        }
    }

    public List<Integer> getVariables() {
        return variables;
    }

    public void initVariables(List<Integer> allVariables) {
        int numVariables = Utils.byteRangeToInt(data, 117, 4, true);
        if (numVariables > 0) {
            int variableOffset = Utils.byteRangeToInt(data, 125, 4, true) / 4;
            variables.addAll(allVariables.subList(variableOffset, variableOffset + numVariables));
        }
    }

    public byte[] getScriptBytes() {
        return scriptBytes;
    }

    public void setScriptBytes(byte[] scriptBytes) {
        this.scriptBytes = scriptBytes;
    }

    public byte[] getAttributeBytes() {
        return attributeBytes;
    }

    public void setAttributeBytes(byte[] attributeBytes) {
        this.attributeBytes = attributeBytes;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }
}