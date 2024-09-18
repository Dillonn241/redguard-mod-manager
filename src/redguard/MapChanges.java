package redguard;

import java.io.*;
import java.util.*;

public class MapChanges {
    /**
     * Stores a list of script line changes under their map name and position. Deletions are represented by a null first
     * element. Insertions are all the other elements, guaranteed to not be null.
     */
    private final Map<String, Map<Integer, List<String>>> lineChanges;

    public MapChanges() {
        lineChanges = new HashMap<>();
    }

    public List<String> lineChangesAt(String mapName, int pos) {
        if (lineChanges.containsKey(mapName) && lineChanges.get(mapName).containsKey(pos)) {
            return lineChanges.get(mapName).get(pos);
        }
        return null;
    }

    public boolean hasModifiedMap(String mapName) {
        return lineChanges.containsKey(mapName);
    }

    public void addChanges(String mapName, Map<Integer, List<String>> mapChanges) {
        for (int pos : mapChanges.keySet()) {
            for (String line : mapChanges.get(pos)) {
                addChange(mapName, pos, line);
            }
        }
    }

    public void addChange(String mapName, int pos, String line) {
        if (!lineChanges.containsKey(mapName)) {
            lineChanges.put(mapName, new TreeMap<>());
        }
        if (!lineChanges.get(mapName).containsKey(pos)) {
            lineChanges.get(mapName).put(pos, new LinkedList<>());
        }
        List<String> list = lineChanges.get(mapName).get(pos);
        if (line == null) {
            if (list.isEmpty() || list.getFirst() != null) {
                list.addFirst(null);
            }
        } else {
            list.add(line);
        }
    }

    public void readChanges(File changesFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(changesFile));
        String currentMap = null;
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            String trim = line.trim();
            if (!trim.isEmpty()) {
                if (line.charAt(0) != ' ') {
                    currentMap = trim;
                    lineChanges.put(currentMap, new TreeMap<>());
                } else {
                    String[] split = line.split("\t");
                    int pos = Integer.parseInt(split[0].trim());
                    if (!lineChanges.get(currentMap).containsKey(pos)) {
                        lineChanges.get(currentMap).put(pos, new LinkedList<>());
                    }
                    String change = split.length > 1 ? split[1] : "";
                    addChange(currentMap, pos, change);
                }
            }
        }
        reader.close();
    }

    public void writeChanges(File fileToWrite) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(fileToWrite));
        for (String mapName : lineChanges.keySet()) {
            writer.write(mapName);
            writer.newLine();
            for (int pos : lineChanges.get(mapName).keySet()) {
                List<String> list = lineChanges.get(mapName).get(pos);
                for (String line : list) {
                    writer.write("  " + pos + "\t" + line);
                    writer.newLine();
                }
            }
        }
        writer.close();
    }
}