package redguard;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MapDatabase {
    private final List<MapFile> mapFiles;
    private final List<SoupFunction> functions;
    private final List<SoupFlag> flags;
    private final List<Item> items;
    private final List<String> references;
    private final List<String> attributes;

    private final Map<String, String> rtxEntries;
    private final Map<String, MapFile> mapNames;
    private final Map<Integer, MapFile> mapIds;

    public MapDatabase(RtxDatabase rtxDatabase) {
        mapFiles = new ArrayList<>();
        functions = new ArrayList<>();
        flags = new ArrayList<>();
        items = new ArrayList<>();
        references = new ArrayList<>();
        attributes = new ArrayList<>();

        rtxEntries = new HashMap<>();
        rtxDatabase.stream().forEach(rtxEntry -> rtxEntries.put(rtxEntry.getLabel(), rtxEntry.getSubtitle()));

        mapNames = new HashMap<>();
        mapIds = new HashMap<>();
    }

    public Map<String, String> getRtxEntries() {
        return rtxEntries;
    }

    public List<MapFile> getMapFiles() {
        return mapFiles;
    }

    public MapFile getMapFileFromName(String name) {
        return mapNames.get(name);
    }

    public MapFile getMapFileFromId(int id) {
        return mapIds.get(id);
    }

    public List<SoupFunction> getFunctions() {
        return functions;
    }

    public List<SoupFlag> getFlags() {
        return flags;
    }

    public List<Item> getItems() {
        return items;
    }

    public List<String> getReferences() {
        return references;
    }

    public List<String> getAttributes() {
        return attributes;
    }

    public void readWorldFile(File worldFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(worldFile));
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            if (line.startsWith("world_map")) {
                int id = Integer.parseInt(line.substring(10, line.indexOf("]")));
                String fullName = line.substring(line.indexOf("=") + 1).toUpperCase();
                String name = fullName.substring(5, fullName.indexOf("."));

                MapFile newMapFile;
                if (mapNames.containsKey(name)) {
                    newMapFile = mapNames.get(name);
                } else {
                    newMapFile = new MapFile(this, name);
                    mapFiles.add(newMapFile);
                }
                newMapFile.addID(id);

                mapNames.put(newMapFile.getName(), newMapFile);
                mapIds.put(id, newMapFile);
            }
        }
        reader.close();
    }

    public void readSoupFile(File soupFile) throws IOException {
        functions.add(new SoupFunction("function NullFunction params 0"));
        BufferedReader reader = new BufferedReader(new FileReader(soupFile));

        // Functions
        readSoupSection(reader, "[functions]");
        readSoupSection(reader, "[refs]", line -> functions.add(new SoupFunction(line)));
        // References
        readSoupSection(reader, "[equates]", references::add);
        // Attributes
        readSoupSection(reader, "auto");
        readSoupSection(reader, "endauto", line -> {
            String[] split = line.split(" *= *");
            if (split.length == 1) {
                attributes.add(line.trim());
            }
        });
        // Flags
        readSoupSection(reader, "[flags]");
        readSoupSection(reader, null, line -> flags.add(new SoupFlag(line)));
    }

    private void readSoupSection(BufferedReader reader, String stopLine) throws IOException {
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            if (line.equals(stopLine)) {
                break;
            }
        }
    }

    private void readSoupSection(BufferedReader reader, String stopLine, Consumer<String> action) throws IOException {
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            if (line.equals(stopLine)) {
                break;
            }
            if (!line.isEmpty() && !line.startsWith(";")) {
                action.accept(line);
            }
        }
    }

    public void readItemsFile(File itemsFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(itemsFile));
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            if (line.startsWith("name")) {
                String nameID = line.substring(line.indexOf("=") + 2).toLowerCase();
                line = reader.readLine();
                String descriptionID = line.substring(line.indexOf("=") + 2).toLowerCase();
                items.add(new Item(this, items.size(), nameID, descriptionID));
            }
        }
        reader.close();
    }
}