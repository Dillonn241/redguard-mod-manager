package redguard;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class INIFile {
    private final String name;
    private final Map<String, List<String>> iniData;

    public INIFile(String name) {
        iniData = new LinkedHashMap<>();
        this.name = name;
    }

    public INIFile(INIFile other) {
        this(other.getName());
        for (String header : other.iniData.keySet()) {
            iniData.put(header, new ArrayList<>(other.iniData.get(header)));
        }
    }

    public String getName() {
        return name;
    }

    public List<String> getLines(String header) {
        return iniData.get(header);
    }

    public String getINIText() {
        return getINIText("");
    }

    public String getINIText(String indent) {
        StringBuilder iniBuilder = new StringBuilder();
        for (String header : iniData.keySet()) {
            if (!header.isEmpty()) {
                iniBuilder.append(indent).append("[").append(header).append("]").append("\n");
            }
            for (String line : iniData.get(header)) {
                iniBuilder.append(indent).append(line).append("\n");
            }
        }
        return iniBuilder.toString();
    }

    public void readINI(File fileToRead) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(fileToRead));
        String str = reader.lines().collect(Collectors.joining("\n"));
        reader.close();
        readINI(str);
    }

    public void readINI(String fileText) {
        String lastHeader = null;
        String[] split = fileText.split("\n");
        for (String line : split) {
            String trim = line.trim();
            if (trim.startsWith("[")) {
                String header = trim.replace("[", "").replace("]", "");
                iniData.put(header, new ArrayList<>());
                lastHeader = header;
            } else {
                if (lastHeader == null) {
                    iniData.put("", new ArrayList<>());
                    lastHeader = "";
                }
                iniData.get(lastHeader).add(line);
            }
        }
    }

    public void writeINI(File fileToWrite) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(fileToWrite));
        String[] lineSplit = getINIText().split("\n");
        for (int i = 0; i < lineSplit.length; i++) {
            writer.write(lineSplit[i]);
            if (i < lineSplit.length - 1) {
                writer.newLine();
            }
        }
        writer.close();
    }

    public Map<String, String> getKeyValues(String header) {
        Map<String, String> keyValues = new LinkedHashMap<>();
        for (String line : iniData.get(header)) {
            if (line.contains("=")) {
                String[] splitLine = line.trim().split("\\s*=\\s*");
                keyValues.put(splitLine[0], splitLine.length > 1 ? splitLine[1] : "");
            }
        }
        return keyValues;
    }

    public INIFile diff(INIFile original) {
        INIFile diffINI = new INIFile(name);
        for (String header : iniData.keySet()) {
            if (!original.iniData.containsKey(header)) {
                diffINI.iniData.put(header, new ArrayList<>(iniData.get(header)));
            } else {
                Map<String, String> keyValues = getKeyValues(header);
                Map<String, String> originalKeyValues = original.getKeyValues(header);
                for (String key : keyValues.keySet()) {
                    if (!originalKeyValues.containsKey(key) || !keyValues.get(key).equals(originalKeyValues.get(key))) {
                        if (!diffINI.iniData.containsKey(header)) {
                            diffINI.iniData.put(header, new ArrayList<>());
                        }
                        diffINI.iniData.get(header).add(key + " = " + keyValues.get(key));
                    }
                }
            }
        }
        return diffINI;
    }

    public void applyChanges(INIFile iniChangesFile) {
        for (String header : iniChangesFile.iniData.keySet()) {
            if (!iniData.containsKey(header)) {
                iniData.put(header, new ArrayList<>(iniChangesFile.iniData.get(header)));
            } else {
                Map<String, String> keyValues = getKeyValues(header);
                keyValues.putAll(iniChangesFile.getKeyValues(header));
                iniData.put(header, new ArrayList<>());
                for (String key : keyValues.keySet()) {
                    iniData.get(header).add(key + " = " + keyValues.get(key));
                }
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof INIFile) {
            return name.equals(((INIFile) obj).getName());
        }
        return false;
    }

    @Override
    public String toString() {
        return name;
    }
}