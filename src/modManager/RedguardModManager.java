package modManager;

import redguard.*;

import javax.imageio.ImageIO;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.List;
import java.util.logging.Logger;

public class RedguardModManager {
    private static final String TITLE = "Redguard Mod Manager";

    // Logger for error tracking
    public static final Logger logger = Logger.getLogger(RedguardModManager.class.getName());

    // Standard mod changes file names
    public static final String RTX_CHANGES = "RTX Changes.txt";
    public static final String INI_CHANGES = "INI Changes.txt";
    public static final String MAP_CHANGES = "Map Changes.txt";
    public static final String[] INI_FILES = new String[]{"COMBAT.INI", "ITEM.INI", "KEYS.INI", "MENU.INI", "REGISTRY.INI", "surface.ini", "SYSTEM.INI", "WORLD.INI"};

    // Folders and files created by the program
    private static final String MODS_FOLDER = "Mods";
    private static final String RTX_AUDIO_FOLDER = "RTX Audio";
    private static final String BACKUP_FOLDER = "backup";
    private static final String SETTINGS_FILE = "Settings.txt";
    private static final String MOD_LIST_FILE = "Mod List.txt";

    // Path fields
    private static Path modManagerPath;
    private static Path modsPath;
    private static Path backupPath;
    private static Path gamePath;

    // GUI fields
    public static Image appIconImage;
    private static JFrame window;
    private static ModTable modTable; // Table listing all the available mods
    private static JTextField nameField, versionField, authorField;
    private static JTextArea descriptionArea;

    // Storage for various mod changes
    private static RtxDatabase rtxDatabase;
    private static MapDatabase mapDatabase;

    /**
     * Starting point for the application. Set the look and feel, initialize various paths, load settings from previous
     * sessions, load the mod list, and create the GUI.
     *
     * @param args Not used
     */
    public static void main(String[] args) {
        logger.info("Application started.");
        // Set system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | UnsupportedLookAndFeelException e) {
            logger.warning("System look and feel setup did not work.");
        }

        // Get mod manager path
        try {
            modManagerPath = Paths.get(RedguardModManager.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        } catch (URISyntaxException e) {
            logger.severe("Failed to get mod manager path. Exiting program.");
            System.exit(-1);
        }
        modsPath = modManagerPath.resolve(MODS_FOLDER);

        // Load settings to get gamePath
        loadSettings();
        backupPath = gamePath.resolve(BACKUP_FOLDER);

        // Load mod list
        modTable = new ModTable();
        loadModList();

        // Load common data files
        preloadFiles();

        try {
            URL iconURL = RedguardModManager.class.getClassLoader().getResource("icon.png");
            if (iconURL != null) {
                appIconImage = new ImageIcon(ImageIO.read(iconURL)).getImage();
            }
        } catch (IOException e) {
            logger.warning("Failed to load app icon.");
        }

        // Create GUI
        SwingUtilities.invokeLater(RedguardModManager::createAndShowGUI);
    }

    /**
     * Copy files to the backup folder and load some common files for use later.
     */
    private static void preloadFiles() {
        // Back up files if the backup folder doesn't exist yet
        File backupFolder = backupPath.toFile();
        if (!backupFolder.exists() && !backupFolder.mkdir()) {
            logger.severe("Failed to create backup folder (" + backupPath + "). Exiting program.");
            System.exit(-1);
        }
        try {
            Utils.copyDoNotReplace(gamePath.resolve("ENGLISH.RTX"), backupPath.resolve("ENGLISH.RTX"));
        } catch (IOException e) {
            logger.severe("Failed to back up ENGLISH.RTX. Exiting program.");
            System.exit(-1);
        }
        try {
            for (String ini : INI_FILES) {
                Utils.copyDoNotReplace(gamePath.resolve(ini), backupPath.resolve(ini));
            }
        } catch (IOException e) {
            logger.severe("Failed to back up INI files. Exiting program.");
            System.exit(-1);
        }
        try {
            rtxDatabase = new RtxDatabase();
            rtxDatabase.readFile(backupPath.resolve("ENGLISH.RTX").toFile());
            mapDatabase = new MapDatabase(rtxDatabase);
            mapDatabase.readWorldFile(backupPath.resolve("WORLD.INI").toFile());
            mapDatabase.readSoupFile(gamePath.resolve("soup386/SOUP386.DEF").toFile());
            mapDatabase.readItemsFile(backupPath.resolve("ITEM.INI").toFile());
        } catch (IOException e) {
            logger.severe("Failed to read game files. Exiting program.");
            System.exit(-1);
        }

        // Finish backing up now that we have the list of map files
        if (backupPath.resolve("maps").toFile().mkdir()) {
            for (MapFile mapFile : mapDatabase.getMapFiles()) {
                try {
                    Files.copy(gamePath.resolve(mapFile.getFullName()), backupPath.resolve(mapFile.getFullName()));
                } catch (IOException e) {
                    logger.severe("Failed to back up map files. Exiting program.");
                    System.exit(-1);
                }
            }
        }
    }

    /**
     * Create all the GUI components for the main mod manager window.
     */
    private static void createAndShowGUI() {
        // Create a window and make sure to save data before exiting
        window = new JFrame(TITLE);
        window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        window.setMinimumSize(new Dimension(800, 640));
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                saveAndExitProgram();
            }
        });
        window.setIconImage(appIconImage);

        // GUI is complex enough to have separate methods for each section
        createMenuBar();
        addModTable();
        createInfoPanel();
        createBottomPanel();

        // Pack the window, center it, and make it visible
        window.pack();
        window.setLocationRelativeTo(null);
        window.setVisible(true);
    }

    /**
     * Create the menu bar at the top with items for various commands.
     */
    private static void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        window.setJMenuBar(menuBar);

        // File menu
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic('f');
        menuBar.add(fileMenu);
        // File menu items
        ModManagerUtils.createMenuItem(fileMenu, "Open Mod Manager folder", _ -> openFolder(modManagerPath));
        ModManagerUtils.createMenuItem(fileMenu, "Open Mods folder", _ -> openFolder(modsPath));
        ModManagerUtils.createMenuItem(fileMenu, "Open Redguard folder", _ -> openFolder(gamePath));
        fileMenu.addSeparator();

        ModManagerUtils.createMenuItem(fileMenu, "Exit Mod Manager", _ -> saveAndExitProgram());

        // Mods menu
        JMenu modsMenu = new JMenu("Mods");
        modsMenu.setMnemonic('m');
        menuBar.add(modsMenu);
        // Mods menu items
        ModManagerUtils.createMenuItem(modsMenu, "Refresh mod list", _ -> loadModList());
        ModManagerUtils.createMenuItem(modsMenu, "Add mod...", _ -> addMod());
        ModManagerUtils.createMenuItem(modsMenu, "Delete selected mod...", _ -> deleteSelectedMod());
        ModManagerUtils.createMenuItem(modsMenu, "Create mod...", _ -> createMod());

        // Edit menu
        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic('e');
        menuBar.add(editMenu);
        // Edit menu items
        ModManagerUtils.createMenuItem(editMenu, "Open Dialogue Editor", _ -> new DialogueEditor(rtxDatabase));
        ModManagerUtils.createMenuItem(editMenu, "Open Selected Mod in Dialogue Editor", _ -> openModInDialogueEditor());
        ModManagerUtils.createMenuItem(editMenu, "Open Selected Mod's Dialogue Audio Folder", _ -> openModDialogueAudioFolder());
        editMenu.addSeparator();
        ModManagerUtils.createMenuItem(editMenu, "Open INI Editor", _ -> new INIEditor());
        ModManagerUtils.createMenuItem(editMenu, "Open Selected Mod in INI Editor", _ -> openModInINIEditor());
        editMenu.addSeparator();
        ModManagerUtils.createMenuItem(editMenu, "Open Script Editor", _ -> new ScriptEditor(mapDatabase));
        ModManagerUtils.createMenuItem(editMenu, "Open Selected Mod in Script Editor", _ -> openModInScriptEditor());
        editMenu.addSeparator();
        ModManagerUtils.createMenuItem(editMenu, "Open Texture Viewer", _ -> new TextureViewer(gamePath));
    }

    /**
     * Add the mod table to the window with a scroll pane. Also enables mod selections to show up on the info panel.
     */
    private static void addModTable() {
        // When a row is selected, add the mod's information to the info panel
        modTable.getSelectionModel().addListSelectionListener(_ -> loadInfoPanel());

        // Scroll pane for mod table
        JScrollPane tableScroll = new JScrollPane(modTable);
        tableScroll.setPreferredSize(new Dimension(625, 550));
        window.add(tableScroll, BorderLayout.CENTER);
    }

    /**
     * Create info panel that shows name, version, author, and description of selected mod.
     */
    private static void createInfoPanel() {
        SpringLayout layout = new SpringLayout();
        JPanel infoPanel = new JPanel(layout);
        infoPanel.setPreferredSize(new Dimension(250, 0));
        infoPanel.setBackground(new Color(230, 230, 230));
        window.add(infoPanel, BorderLayout.EAST);

        // Mod name
        JLabel nameLabel = new JLabel("Name:");
        nameField = new JTextField();
        addLabeledComponent(layout, infoPanel, nameLabel, nameField);
        layout.putConstraint(SpringLayout.NORTH, nameLabel, 5, SpringLayout.NORTH, infoPanel);

        // Mod version
        JLabel versionLabel = new JLabel("Version:");
        versionField = new JTextField();
        addLabeledComponent(layout, infoPanel, versionLabel, versionField);
        layout.putConstraint(SpringLayout.NORTH, versionLabel, 5, SpringLayout.SOUTH, nameField);

        // Mod author
        JLabel authorLabel = new JLabel("Author:");
        authorField = new JTextField();
        addLabeledComponent(layout, infoPanel, authorLabel, authorField);
        layout.putConstraint(SpringLayout.NORTH, authorLabel, 5, SpringLayout.SOUTH, versionField);

        // Mod description
        JLabel descriptionLabel = new JLabel("Description:");
        descriptionArea = new JTextArea(20, 0);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setFont(nameField.getFont());
        JScrollPane descriptionScrollPane = new JScrollPane(descriptionArea);
        addLabeledComponent(layout, infoPanel, descriptionLabel, descriptionScrollPane);
        layout.putConstraint(SpringLayout.NORTH, descriptionLabel, 5, SpringLayout.SOUTH, authorField);

        // Save and cancel buttons
        JButton saveButton = new JButton("Save"); // Save button
        saveButton.addActionListener(_ -> saveInfoPanel());
        infoPanel.add(saveButton);
        JButton cancelButton = new JButton("Cancel"); // Cancel button
        cancelButton.addActionListener(_ -> loadInfoPanel());
        infoPanel.add(cancelButton);
        layout.putConstraint(SpringLayout.NORTH, saveButton, 10, SpringLayout.SOUTH, descriptionScrollPane);
        layout.putConstraint(SpringLayout.EAST, saveButton, -5, SpringLayout.HORIZONTAL_CENTER, infoPanel);
        layout.putConstraint(SpringLayout.NORTH, cancelButton, 10, SpringLayout.SOUTH, descriptionScrollPane);
        layout.putConstraint(SpringLayout.WEST, cancelButton, 5, SpringLayout.HORIZONTAL_CENTER, infoPanel);
    }

    /**
     * Helper method for createInfoPanel() to add metadata entries to the layout.
     *
     * @param layout    The layout to put constraints on the component
     * @param container The container to add the component in
     * @param label     A label for the component
     * @param component The component itself
     */
    private static void addLabeledComponent(SpringLayout layout, JComponent container, JLabel label, JComponent component) {
        container.add(label);
        container.add(component);
        layout.putConstraint(SpringLayout.WEST, label, 5, SpringLayout.WEST, container);
        layout.putConstraint(SpringLayout.WEST, component, 5, SpringLayout.WEST, container);
        layout.putConstraint(SpringLayout.EAST, component, -5, SpringLayout.EAST, container);
        layout.putConstraint(SpringLayout.NORTH, component, 0, SpringLayout.SOUTH, label);
    }

    /**
     * Create the panel at the bottom of the window, containing buttons like move up and move down.
     */
    private static void createBottomPanel() {
        JPanel bottomPanel = new JPanel();
        bottomPanel.setPreferredSize(new Dimension(0, 50));
        window.add(bottomPanel, BorderLayout.SOUTH);

        // Buttons
        ModManagerUtils.createButton(bottomPanel, "Add Mod", _ -> addMod());
        bottomPanel.add(Box.createHorizontalStrut(25));
        ModManagerUtils.createButton(bottomPanel, "Move Up", _ -> modTable.moveSelectedModUp());
        ModManagerUtils.createButton(bottomPanel, "Move Down", _ -> modTable.moveSelectedModDown());
        bottomPanel.add(Box.createHorizontalStrut(25));
        ModManagerUtils.createButton(bottomPanel, "Apply Changes", _ -> applyChanges());
    }

    /**
     * Ask the user where the Redguard folder is found.
     */
    private static void askGameFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open Redguard Folder Containing REDGUARD.EXE");
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        while (true) {
            int option = chooser.showOpenDialog(window);
            if (option != JFileChooser.APPROVE_OPTION) {
                System.exit(0);
            }
            if (chooser.getSelectedFile().isDirectory()) {
                gamePath = chooser.getSelectedFile().toPath();
                if (!gamePath.resolve("ENGLISH.RTX").toFile().exists()) {
                    if (gamePath.resolve("Redguard").toFile().exists()) {
                        gamePath = gamePath.resolve("Redguard");
                    } else {
                        ModManagerUtils.showWarning(window, "Wrong folder.");
                        askGameFolder();
                    }
                }
                break;
            }
            ModManagerUtils.showWarning(window, "Please select a folder.");
        }
    }

    /**
     * Load settings file if it exists and set associated variables. Otherwise, ask for game folder.
     */
    private static void loadSettings() {
        File settingsFile = modManagerPath.resolve(SETTINGS_FILE).toFile();
        if (settingsFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(settingsFile))) {
                String line = reader.readLine();
                String[] split = line.split(" = ");
                gamePath = Paths.get(split[1]);
                if (!gamePath.toFile().exists()) {
                    askGameFolder();
                }
            } catch (IOException e) {
                logger.severe("Failed to load settings from " + settingsFile);
            }
        } else {
            askGameFolder();
        }
    }

    /**
     * Save settings to a file.
     */
    private static void saveSettings() {
        File fileToSave = modManagerPath.resolve(SETTINGS_FILE).toFile();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileToSave))) {
            writer.write("gamePath = " + gamePath);
            writer.newLine();
        } catch (IOException e) {
            ModManagerUtils.showError(window, "Failed to save settings to file: " + fileToSave.getPath());
        }
    }

    /**
     * Create the mods folder if it does not exist, load mods from the mod list, and check for new mods in the mods folder.
     */
    private static void loadModList() {
        // Ensure mod list is empty (needed for refresh)
        modTable.clearModList();

        // Create mods folder if it does not exist
        File modsFolder = modsPath.toFile();
        if (!modsFolder.exists() && !modsFolder.mkdir()) {
            logger.severe("Failed to create mods folder (" + modsPath + "). Exiting program.");
            System.exit(-1);
        }

        // Load mod list if it exists
        File modListFile = modManagerPath.resolve(MOD_LIST_FILE).toFile();
        if (modListFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(modListFile))) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    // Split by tabs: should only have two parts, the first part being whether the mod is enabled, and the second part its name
                    String[] split = line.split("\t");
                    Path modPath = modsPath.resolve(split[1]);
                    if (modPath.toFile().exists()) {
                        // Mod class handles parsing the About.txt file to get version, author, etc.
                        modTable.addMod(new Mod(modPath, Boolean.parseBoolean(split[0])));
                    }
                }
            } catch (IOException e) {
                logger.severe("Failed to load mod list from " + MOD_LIST_FILE + ".");
            }
        }

        // Check for new mods
        File[] modFolderArray = modsFolder.listFiles();
        if (modFolderArray != null) {
            for (File modDir : modFolderArray) {
                if (modTable.getModList().stream().noneMatch(mod -> mod.getName().equals(modDir.getName()))) {
                    try {
                        modTable.addMod(new Mod(modDir.toPath(), false));
                    } catch (IOException e) {
                        logger.severe("Failed to load metadata from " + Mod.ABOUT_FILE + " for mod " + modDir.getName() + ".");
                    }
                }
            }
        }
    }

    /**
     * Save mod list to a file for the next time the mod manager runs.
     */
    private static void saveModList() {
        File modListFile = modManagerPath.resolve(MOD_LIST_FILE).toFile();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(modListFile))) {
            for (Mod mod : modTable.getModList()) {
                writer.write(mod.isEnabled() + "\t" + mod.getName());
                writer.newLine();
            }
        } catch (IOException e) {
            ModManagerUtils.showError(window, "Failed to save mod list to " + MOD_LIST_FILE + ".");
        }
    }

    /**
     * Update the info panel with details for the current selected mod.
     */
    private static void loadInfoPanel() {
        Mod selectedMod = modTable.getSelectedMod();
        if (selectedMod == null) {
            nameField.setText("");
            versionField.setText("");
            authorField.setText("");
            descriptionArea.setText("");
        } else {
            nameField.setText(selectedMod.getName());
            versionField.setText(selectedMod.getVersion());
            authorField.setText(selectedMod.getAuthor());
            descriptionArea.setText(selectedMod.getDescription());
            descriptionArea.setCaretPosition(0);
        }
    }

    /**
     * Save the mod details in the info panel to the mod's folder.
     */
    private static void saveInfoPanel() {
        Mod selectedMod = modTable.getSelectedMod();
        if (selectedMod != null) {
            // Fix blank and untrimmed names
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                name = selectedMod.getName();
            }
            nameField.setText(name);

            // Move the mod folder if necessary and save the new information there (handled by Mod class)
            Path path = modsPath.resolve(name);
            if (name.equals(selectedMod.getName()) || !path.toFile().exists()) {
                try {
                    Files.move(modsPath.resolve(selectedMod.getName()), path, StandardCopyOption.REPLACE_EXISTING);
                    selectedMod.saveInfo(window, modsPath.resolve(name), versionField.getText(), authorField.getText(), descriptionArea.getText());
                } catch (IOException e) {
                    ModManagerUtils.showError(window, "Failed to save metadata to " + Mod.ABOUT_FILE + " for mod " + selectedMod.getName() + ".");
                }
            } else {
                ModManagerUtils.showWarning(window, "That name is already in use.");
            }
            modTable.updateSelectedRow();
        }
    }

    /**
     * Add a mod from a folder that the user selects.
     */
    private static void addMod() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open Mod Folder");
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        while (true) {
            int option = chooser.showOpenDialog(window);
            File file = chooser.getSelectedFile();
            if (option != JFileChooser.APPROVE_OPTION) {
                break;
            }
            if (file.isDirectory()) {
                Path copyPath = modsPath.resolve(file.getName());
                try {
                    ModManagerUtils.copyFolder(file.toPath(), copyPath);
                    Mod mod = new Mod(file.toPath(), false);
                    modTable.addMod(mod);
                } catch (IOException e) {
                    ModManagerUtils.showError(window, "Failed to copy folder (" + file.toPath() + ") to: " + copyPath);
                }
                break;
            }
            ModManagerUtils.showWarning(window, "Please select a folder.");
        }
    }

    /**
     * Confirm if the user wants to delete a selected mod, then delete its files and the mod table entry.
     */
    private static void deleteSelectedMod() {
        Mod mod = modTable.getSelectedMod();
        if (mod != null) {
            int shouldDelete = JOptionPane.showConfirmDialog(window, "Are you sure you want to delete "
                    + mod.getName() + "?", "Confirm deletion", JOptionPane.YES_NO_OPTION);
            if (shouldDelete == JOptionPane.YES_OPTION) {
                Path modPath = getModPath(mod);
                if (ModManagerUtils.deleteFolder(modPath)) {
                    modTable.removeSelectedMod();
                } else {
                    ModManagerUtils.showError(window, "Failed to fully delete mod folder: " + modPath);
                }
            }
        }
    }

    /**
     * Collect all the changes from enabled mods in their load order and patch the game files.
     */
    private static void applyChanges() {
        // Check if the user really wants to apply changes
        int shouldApply = JOptionPane.showConfirmDialog(window, "Are you sure you want to apply changes from the enabled mods?",
                "Confirm apply changes", JOptionPane.YES_NO_OPTION);
        if (shouldApply != JOptionPane.YES_OPTION) {
            return;
        }

        RtxDatabase modifiedDatabase = null;
        INIChanges iniChanges = new INIChanges();
        MapChanges mapChanges = new MapChanges();
        try {
            // Go through all enabled mods and collect the sum of their changes, based on load order
            for (Mod mod : modTable.getModList()) {
                if (mod.isEnabled()) {
                    Path path = getModPath(mod);

                    // Get RTX changes
                    File rtxChangesFile = path.resolve(RTX_CHANGES).toFile();
                    if (rtxChangesFile.exists()) {
                        if (modifiedDatabase == null) {
                            modifiedDatabase = new RtxDatabase(rtxDatabase);
                        }
                        modifiedDatabase.applyChanges(rtxChangesFile);
                    }

                    // Get RTX audio changes
                    File audioFolder = getModPath(mod).resolve(RTX_AUDIO_FOLDER).toFile();
                    try {
                        rtxDatabase.loadAudioFolder(audioFolder);
                    } catch (UnsupportedAudioFileException | IOException e) {
                        ModManagerUtils.showError(window, "Failed to load selected mod's audio folder.");
                    }

                    // Get INI changes
                    File iniChangesFile = path.resolve(INI_CHANGES).toFile();
                    if (iniChangesFile.exists()) {
                        iniChanges.readChanges(iniChangesFile);
                    }

                    // Get map changes
                    File mapChangesFile = path.resolve(MAP_CHANGES).toFile();
                    if (mapChangesFile.exists()) {
                        mapChanges.readChanges(mapChangesFile);
                    }
                }
            }

            // Replace ENGLISH.RTX, either with a new version or the original copy
            if (modifiedDatabase == null) {
                Files.copy(backupPath.resolve("ENGLISH.RTX"), gamePath.resolve("ENGLISH.RTX"), StandardCopyOption.REPLACE_EXISTING);
            } else {
                modifiedDatabase.writeFile(gamePath.resolve("ENGLISH.RTX").toFile());
            }

            // Replace INI files, either with a new version or the original copy
            for (String ini : INI_FILES) {
                if (iniChanges.hasINIChangesFile(ini)) {
                    INIFile iniFile = new INIFile(ini);
                    iniFile.readINI(backupPath.resolve(ini).toFile());
                    for (INIFile iniChangesFile : iniChanges.getINIChangesFiles()) {
                        if (iniFile.getName().equals(iniChangesFile.getName())) {
                            iniFile.applyChanges(iniChangesFile);
                        }
                    }
                    iniFile.writeINI(gamePath.resolve(ini).toFile());
                } else {
                    Files.copy(backupPath.resolve(ini), gamePath.resolve(ini), StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // Replace map files that have changes and copy the rest
            for (MapFile mapFile : mapDatabase.getMapFiles()) {
                if (mapChanges.hasModifiedMap(mapFile.getName())) {
                    if (mapFile.isEmpty()) {
                        mapFile.readMap(backupPath.resolve(mapFile.getFullName()).toFile());
                    }
                    String modifiedScript = mapFile.getModifiedScript(mapChanges);
                    mapFile.writeMap(gamePath.resolve(mapFile.getFullName()).toFile(), modifiedScript);
                } else {
                    Files.copy(backupPath.resolve(mapFile.getFullName()), gamePath.resolve(mapFile.getFullName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }

            JOptionPane.showMessageDialog(window, "Changes were applied successfully.", "Redguard Mod Manager",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            ModManagerUtils.showError(window, "An error occurred while applying changes from mods to game.");
        }
    }

    /**
     * Create an empty mod with a chosen name so that files can be added to it through the edit menu.
     */
    private static void createMod() {
        String name = JOptionPane.showInputDialog(window, "Enter mod name:", "Create mod", JOptionPane.QUESTION_MESSAGE);
        // User canceled or exited the dialog
        if (name == null) {
            return;
        }
        // Check if name is blank
        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) {
            ModManagerUtils.showWarning(window, "Name cannot be blank.");
            createMod();
            return;
        }

        // Create folder if it exists, or throw an error message
        Path path = modsPath.resolve(trimmedName);
        if (path.toFile().mkdir()) {
            modTable.addMod(new Mod(path));
        } else {
            ModManagerUtils.showWarning(window, "That name is already in use.");
        }
    }

    /**
     * If a mod is selected, open the dialogue editor and load the mod's changes file.
     */
    public static void openModInDialogueEditor() {
        Mod mod = modTable.getSelectedMod();
        if (mod != null) {
            File changesFile = getModPath(mod).resolve(RTX_CHANGES).toFile();
            File audioFolder = getModPath(mod).resolve(RTX_AUDIO_FOLDER).toFile();
            DialogueEditor editor = null;
            if (changesFile.exists()) {
                editor = new DialogueEditor(rtxDatabase);
                editor.loadChangesFile(changesFile);
            }
            File[] audioFileArray = audioFolder.listFiles();
            if (audioFileArray != null && audioFileArray.length > 0) {
                if (editor == null) editor = new DialogueEditor(rtxDatabase);
                try {
                    rtxDatabase.loadAudioFolder(audioFolder);
                } catch (UnsupportedAudioFileException | IOException e) {
                    ModManagerUtils.showError(window, "Failed to load selected mod's audio folder.");
                }
            }
            if (editor == null) {
            JOptionPane.showMessageDialog(window, "Mod does not have an " + RTX_CHANGES + " file or any audio files in an " +
                    RTX_AUDIO_FOLDER + " folder.", "No changes file or audio files", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    public static void openModDialogueAudioFolder() {
        Mod mod = modTable.getSelectedMod();
        if (mod != null) {
            Path audioPath = getModPath(mod).resolve(RTX_AUDIO_FOLDER);
            File audioFolder = audioPath.toFile();
            if (!audioFolder.exists() && !audioFolder.mkdir()) {
                ModManagerUtils.showError(window, "Failed to create mod's dialogue audio folder: " + audioPath);
            }
            openFolder(audioPath);
        }
    }

    /**
     * If a mod is selected, open the INI editor and load the mod's changes file.
     */
    public static void openModInINIEditor() {
        Mod mod = modTable.getSelectedMod();
        if (mod != null) {
            File changesFile = getModPath(mod).resolve(INI_CHANGES).toFile();
            if (changesFile.exists()) {
                INIEditor editor = new INIEditor();
                editor.loadChangesFile(changesFile);
            } else {
                JOptionPane.showMessageDialog(window, "Mod does not have an " + INI_CHANGES + " file.",
                        "No changes file", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    /**
     * If a mod is selected, open it in the script editor and load all the mod's map changes files.
     */
    public static void openModInScriptEditor() {
        Mod mod = modTable.getSelectedMod();
        if (mod != null) {
            File changesFile = getModPath(mod).resolve(MAP_CHANGES).toFile();
            if (changesFile.exists()) {
                ScriptEditor editor = new ScriptEditor(mapDatabase);
                editor.loadChangesFile(changesFile);
            } else {
                JOptionPane.showMessageDialog(window, "Mod does not have a " + MAP_CHANGES + " file.",
                        "No changes file", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    /**
     * Show a list of mod names to choose from.
     *
     * @return The selected mod, or null if there are no mods or the dialog was closed
     */
    public static Mod showModSelectionDialog(Component parentComponent, String message, String title) {
        List<Mod> modList = modTable.getModList();
        if (modList.isEmpty()) {
            return null;
        }
        Object[] modArray = modList.toArray();
        return (Mod) JOptionPane.showInputDialog(parentComponent, message, title,
                JOptionPane.PLAIN_MESSAGE, null, modArray, modArray[0]);
    }

    /**
     * Save settings and the mod list before exiting.
     */
    private static void saveAndExitProgram() {
        // Save settings and mod list before exiting
        saveSettings();
        saveModList();
        logger.info("Application exited safely, saving settings and mod list.");
        System.exit(0);
    }

    /**
     * Open a specified folder in the user's file explorer program.
     *
     * @param folder The folder to open for browsing
     */
    public static void openFolder(Path folder) {
        try {
            Desktop.getDesktop().open(folder.toFile());
        } catch (IOException e) {
            ModManagerUtils.showWarning(window, "Failed to open folder: " + folder);
        }
    }

    /**
     * Get the path to a mod's folder.
     *
     * @param mod The mod whose path is returned
     * @return The path to the mod
     */
    public static Path getModPath(Mod mod) {
        return modsPath.resolve(mod.getName());
    }

    /**
     * Get a game file from the backup folder.
     *
     * @param filename The name of the file
     * @return The backed-up file as an object
     */
    public static File getBackupFile(String filename) {
        return backupPath.resolve(filename).toFile();
    }
}