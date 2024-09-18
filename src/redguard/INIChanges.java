package redguard;

import java.io.*;
import java.util.*;

public class INIChanges {
    /**
     * Stores a list of INI line changes under their file name and header.
     */
    private final List<INIFile> iniChangesFiles;

    public INIChanges() {
        iniChangesFiles = new ArrayList<>();
    }

    public boolean hasINIChangesFile(String iniName) {
        for (INIFile iniFile : iniChangesFiles) {
            if (iniName.equals(iniFile.getName())) {
                return true;
            }
        }
        return false;
    }

    public List<INIFile> getINIChangesFiles() {
        return iniChangesFiles;
    }

    public void addINIChangeFile(INIFile modifiedFile) {
        iniChangesFiles.add(modifiedFile);
    }

    public void readChanges(File changesFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(changesFile));
        INIFile currentINIFile = null;
        StringBuilder iniTextBuilder = new StringBuilder();
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            String trim = line.trim();
            if (!trim.isEmpty() && line.charAt(0) != ' ') {
                if (currentINIFile != null && !iniTextBuilder.isEmpty()) {
                    currentINIFile.readINI(iniTextBuilder.toString());
                    iniTextBuilder = new StringBuilder();
                }
                currentINIFile = new INIFile(trim);
                iniChangesFiles.add(currentINIFile);
            } else {
                iniTextBuilder.append(trim).append("\n");
            }
        }
        if (currentINIFile != null && !iniTextBuilder.isEmpty()) {
            currentINIFile.readINI(iniTextBuilder.toString());
        }
        reader.close();
    }

    public void writeChanges(File fileToWrite) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(fileToWrite));
        for (INIFile iniFile : iniChangesFiles) {
            writer.write(iniFile.getName());
            writer.newLine();
            writer.write(iniFile.getINIText("  "));
        }
        writer.close();
    }
}