package modManager;

import diff.Diff;
import redguard.MapChanges;
import redguard.MapDatabase;
import redguard.MapFile;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static modManager.RedguardModManager.logger;

public class ScriptEditor {
    private static final String TITLE = "Redguard Script Editor";

    // GUI fields
    private JFrame window;
    private JMenuItem saveMapItem, saveScriptItem;
    private JTabbedPane tabbedPane;
    private List<MapFile> mapFileTabs;
    private MapFile currentMapFile;
    private JTextField searchBar;

    // Other fields
    private final MapDatabase mapDatabase;

    public ScriptEditor(MapDatabase mapDatabase) {
        this.mapDatabase = mapDatabase;
        createAndShowGUI();
    }

    private void createAndShowGUI() {
        // Remove focus border around tabs
        UIManager.put("TabbedPane.focus", new Color(0, 0, 0, 0));

        window = new JFrame(TITLE);
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        window.setMinimumSize(new Dimension(800, 600));

        // Split GUI creation into sections
        createMenuBar();
        createTabbedPane();
        createBottomPanel();

        window.pack();
        window.setLocationRelativeTo(null);
        window.setVisible(true);
    }

    /**
     * Create the menu bar at the top with items for various commands.
     */
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        window.setJMenuBar(menuBar);

        // File menu
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic('f');
        menuBar.add(fileMenu);
        // File menu items
        saveMapItem = ModManagerUtils.createMenuItem(fileMenu, "Save Map...", _ -> saveMapFile());
        saveMapItem.setEnabled(false);
        fileMenu.addSeparator();
        ModManagerUtils.createMenuItem(fileMenu, "Load Script...", _ -> loadScriptFile());
        saveScriptItem = ModManagerUtils.createMenuItem(fileMenu, "Save Script...", _ -> saveScriptFile());
        saveScriptItem.setEnabled(false);
        fileMenu.addSeparator();
        ModManagerUtils.createMenuItem(fileMenu, "Exit Script Editor", _ -> window.dispose());
    }

    /**
     * Set up the tabbed pane containing tabs and text areas.
     */
    private void createTabbedPane() {
        tabbedPane = new JTabbedPane();
        tabbedPane.setPreferredSize(new Dimension(800, 560));
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.addChangeListener(_ -> {
            int index = tabbedPane.getSelectedIndex();
            currentMapFile = (index == -1) ? null : mapFileTabs.get(index);

            boolean canSave = currentMapFile != null;
            saveMapItem.setEnabled(canSave);
            saveScriptItem.setEnabled(canSave);
        });
        window.add(tabbedPane, BorderLayout.CENTER);
        mapFileTabs = new ArrayList<>();
    }

    /**
     * Create bottom panel that holds the find text field and load/save buttons.
     */
    private void createBottomPanel() {
        JPanel bottomPanel = new JPanel();
        bottomPanel.setPreferredSize(new Dimension(0, 40));
        window.add(bottomPanel, BorderLayout.SOUTH);

        // Search bar and button for finding text matches
        searchBar = new JTextField(10);
        searchBar.addActionListener(_ -> findText());
        bottomPanel.add(searchBar);
        JButton searchButton = new JButton("\uD83D\uDD0D");
        searchButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                findText();
            }
        });
        bottomPanel.add(searchButton);

        // Buttons
        ModManagerUtils.createButton(bottomPanel, "Load Map", _ -> loadMapFile());
        ModManagerUtils.createButton(bottomPanel, "Save to Mod", _ -> saveToMod());
    }

    /**
     * Try to find the text in searchBar within the current script's text.
     */
    private void findText() {
        if (currentMapFile == null) return;
        String find = searchBar.getText().toLowerCase();
        if (find.isEmpty()) return;
        JTextArea textArea = getSelectedTextArea();
        String text = textArea.getText().toLowerCase();
        int pos = textArea.getCaretPosition();
        int index = text.substring(pos).indexOf(find);
        if (index == -1) {
            index = text.substring(0, pos).indexOf(find);
            pos = 0;
        }
        if (index >= 0) {
            textArea.requestFocus();
            textArea.setCaretPosition(pos + index + find.length());
            textArea.select(pos + index, pos + index + find.length());
            JScrollBar vBar = getSelectedScrollPane().getVerticalScrollBar();
            vBar.setValue(vBar.getValue() + textArea.getHeight());
        }
    }

    private JScrollPane getScrollPaneAt(int index) {
        return (JScrollPane) tabbedPane.getComponentAt(index);
    }

    private JScrollPane getSelectedScrollPane() {
        return (JScrollPane) tabbedPane.getSelectedComponent();
    }

    private JTextArea getTextAreaAt(int index) {
        return (JTextArea) getScrollPaneAt(index).getViewport().getView();
    }

    private JTextArea getSelectedTextArea() {
        return (JTextArea) getSelectedScrollPane().getViewport().getView();
    }

    /**
     * Add a new tab or replace an existing tab with the same name, then put the given script into the new tab's text
     * area. Associate this tab with the given map file in mapFileTabs.
     *
     * @param mapFile The map file which names this tab and which determines how to handle the script
     * @param script  The script to add to the tab's text area
     */
    private void addTab(MapFile mapFile, String script) {
        currentMapFile = mapFile;

        JTextArea textArea;
        int tabIndex = mapFileTabs.indexOf(currentMapFile);
        if (tabIndex == -1) {
            // Create new tab
            tabIndex = mapFileTabs.size();
            mapFileTabs.add(currentMapFile);

            textArea = new JTextArea();
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setMargin(new Insets(5, 5, 5, 5));

            tabbedPane.addTab(currentMapFile.getName(), new JScrollPane(textArea));
            createTabButton(tabIndex);
        } else {
            // Tab exists
            textArea = getTextAreaAt(tabIndex);
        }
        textArea.setText(script);
        textArea.setCaretPosition(0); // Move back to the top
        tabbedPane.setSelectedIndex(tabIndex);
    }

    private void createTabButton(int index) {
        Box tabBox = Box.createHorizontalBox();
        tabBox.add(new JLabel(currentMapFile.getName()));
        tabBox.add(Box.createHorizontalStrut(10));

        JButton closeButton = new JButton(" X ");
        closeButton.setBorderPainted(false);
        closeButton.setMargin(new Insets(0, 0, 0, 0));
        closeButton.setFocusable(false);
        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeButton.setBorderPainted(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                closeButton.setBorderPainted(false);
            }
        });
        closeButton.addActionListener(_ -> removeTab(tabbedPane.indexOfTabComponent(tabBox)));
        tabBox.add(closeButton);
        tabbedPane.setTabComponentAt(index, tabBox);
    }

    private void removeTab(int index) {
        tabbedPane.removeTabAt(index);
        mapFileTabs.remove(index);
    }

    private void loadMapFile() {
        Object[] mapFiles = mapDatabase.getMapFiles().toArray();
        MapFile selection = (MapFile) JOptionPane.showInputDialog(window, "Choose a map to load.",
                "Load Map", JOptionPane.PLAIN_MESSAGE, null, mapFiles, mapFiles[0]);
        if (selection == null) return;
        File fileToLoad = RedguardModManager.getBackupFile(selection.getFullName());
        try {
            selection.readMap(fileToLoad);
        } catch (IOException e) {
            ModManagerUtils.showError(window, "Failed to read map file: " + fileToLoad.getPath());
        }
        addTab(selection, selection.getScript());
    }

    private void saveMapFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Map");
        chooser.setFileFilter(new FileNameExtensionFilter("Map files (*.RGM)", "RGM"));
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setSelectedFile(new File(currentMapFile.getName()));
        int option = chooser.showSaveDialog(window);
        if (option != JFileChooser.APPROVE_OPTION) return;
        File fileToSave = ModManagerUtils.forceFileExtension(chooser.getSelectedFile(), ".RGM");
        if (ModManagerUtils.confirmReplace(window, fileToSave)) {
            try {
                currentMapFile.writeMap(fileToSave, getSelectedTextArea().getText());
            } catch (IOException e) {
                ModManagerUtils.showError(window, "Failed to save map file: " + fileToSave.getPath());
            }
        }
    }

    private void loadScriptFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load Script");
        chooser.setFileFilter(new FileNameExtensionFilter("Normal text files (*.txt)", "txt"));
        chooser.setAcceptAllFileFilterUsed(false);
        int option = chooser.showOpenDialog(window);
        if (option == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile().getName().endsWith(".txt")) {
            StringBuilder script;
            String name;
            File fileToLoad = chooser.getSelectedFile();
            try (BufferedReader reader = new BufferedReader(new FileReader(fileToLoad))) {
                script = new StringBuilder();
                name = reader.readLine();
                script.append(name);
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    script.append("\n").append(line);
                }
            } catch (IOException e) {
                ModManagerUtils.showError(window, "Failed to read script file: " + fileToLoad.getPath());
                return;
            }
            MapFile mapFile = mapDatabase.getMapFileFromName(name);
            try {
                mapFile.readMap(RedguardModManager.getBackupFile(mapFile.getFullName()));
            } catch (IOException e) {
                ModManagerUtils.showError(window, "Failed to read map file from backup: " + mapFile.getFullName());
            }
            addTab(mapFile, script.toString());
        }
    }

    private void saveScriptFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Script");
        chooser.setFileFilter(new FileNameExtensionFilter("Normal text files (*.txt)", "txt"));
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setSelectedFile(new File(currentMapFile.getName()));
        int option = chooser.showSaveDialog(window);
        if (option != JFileChooser.APPROVE_OPTION) return;
        File fileToSave = ModManagerUtils.forceFileExtension(chooser.getSelectedFile(), ".txt");
        if (ModManagerUtils.confirmReplace(window, fileToSave)) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileToSave))) {
                writer.write(getSelectedTextArea().getText());
            } catch (IOException e) {
                ModManagerUtils.showError(window, "Failed to save script file: " + fileToSave.getPath());
            }
        }
    }

    public void loadChangesFile(File fileToLoad) {
        MapChanges mapChanges = new MapChanges();
        try {
            mapChanges.readChanges(fileToLoad);
        } catch (IOException e) {
            ModManagerUtils.showError(window, "Failed to read changes file: " + fileToLoad.getPath());
        }

        for (MapFile mapFile : mapDatabase.getMapFiles()) {
            if (mapChanges.hasModifiedMap(mapFile.getName())) {
                try {
                    mapFile.readMap(RedguardModManager.getBackupFile(mapFile.getFullName()));
                } catch (IOException e) {
                    ModManagerUtils.showError(window, "Failed to read map file from : " + mapFile.getFullName());
                }
                String modifiedScript = mapFile.getModifiedScript(mapChanges);
                addTab(mapFile, modifiedScript);
            }
        }
    }

    private void saveChangesFile(File fileToSave) {
        MapChanges mapChanges = new MapChanges();
        for (int i = 0; i < mapFileTabs.size(); i++) {
            MapFile mapFile = mapFileTabs.get(i);
            String[] originalLines = mapFile.getScript().split("\n");
            String[] modifiedLines = getTextAreaAt(i).getText().split("\n");
            mapChanges.addChanges(mapFile.getName(), Diff.diff(originalLines, modifiedLines));
        }
        try {
            mapChanges.writeChanges(fileToSave);
        } catch (IOException e) {
            ModManagerUtils.showError(window, "Failed to save changes file: " + fileToSave.getPath());
        }
    }

    private void saveToMod() {
        Mod mod = RedguardModManager.showModSelectionDialog(window, "Choose a mod to save script changes.",
                "Save to Mod");
        if (mod == null) return;
        File changesFile = RedguardModManager.getModPath(mod).resolve(RedguardModManager.MAP_CHANGES).toFile();
        saveChangesFile(changesFile);
        if (changesFile.length() == 0) {
            if (!changesFile.delete()) {
                logger.info("Deleted empty changes file: " + changesFile.getPath());
            } else {
                ModManagerUtils.showError(window, "Failed to delete empty changes file: " + changesFile.getPath());
            }
        }
    }
}