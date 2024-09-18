package redguard;

import java.util.ArrayList;
import java.util.List;

public class ParsedMapHeader {
    private final String name;
    private int scriptDataOffset;
    private int scriptPC;
    private final List<String> strings;
    private byte[] scriptBytes;
    private byte[] attributeBytes;

    public ParsedMapHeader(String name) {
        this.name = name;
        strings = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public int getScriptDataOffset() {
        return scriptDataOffset;
    }

    public void setScriptDataOffset(int scriptDataOffset) {
        this.scriptDataOffset = scriptDataOffset;
    }

    public int getScriptPC() {
        return scriptPC;
    }

    public void setScriptPC(int scriptPC) {
        this.scriptPC = scriptPC;
    }

    public List<String> getStrings() {
        return strings;
    }

    public int addString(String str) {
        if (strings.contains(str)) {
            return strings.indexOf(str);
        } else {
            strings.add(str);
            return strings.size() - 1;
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
}
