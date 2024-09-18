package modManager;

import javax.swing.*;
import java.io.*;
import java.nio.file.Path;

public class Mod {
    public static final String ABOUT_FILE = "About.txt";

    private String name, version, author, description;
    private boolean enabled;

    public Mod(Path modPath) {
        name = modPath.toFile().getName();
    }

    public Mod(Path modPath, boolean enabled) throws IOException {
        name = modPath.toFile().getName();
        this.enabled = enabled;

        File aboutFile = modPath.resolve(ABOUT_FILE).toFile();
        if (aboutFile.exists()) {
            BufferedReader reader = new BufferedReader(new FileReader(aboutFile));
            version = reader.readLine();
            author = reader.readLine();

            StringBuilder sb = new StringBuilder();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                sb.append(line).append("\n");
            }
            description = sb.toString();
            reader.close();
        }
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void saveInfo(JFrame window, Path modPath, String version, String author, String description) throws IOException {
        this.name = modPath.toFile().getName();
        this.version = version;
        this.author = author;
        this.description = description;

        File aboutFile = modPath.resolve(ABOUT_FILE).toFile();
        if (version.isEmpty() && author.isEmpty() && description.isEmpty()) {
            if (!aboutFile.delete()) {
                ModManagerUtils.showError(window, "Failed to delete empty about file.");
            }
        } else {
            BufferedWriter writer = new BufferedWriter(new FileWriter(aboutFile));
            writer.write(version);
            writer.newLine();
            writer.write(author);
            writer.newLine();
            writer.write(description);
            writer.close();
        }
    }

    @Override
    public String toString() {
        return name;
    }
}