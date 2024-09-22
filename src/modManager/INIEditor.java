package modManager;

import redguard.*;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static modManager.RedguardModManager.logger;

public class INIEditor {
    private static final String TITLE = "Redguard INI Editor";

    // GUI fields
    private JFrame window;
    private JMenuItem saveINIItem;
    private JTabbedPane tabbedPane;
    private List<INIFile> iniFileTabs;
    private INIFile currentINIFile;
    private JTextField searchBar;

    public INIEditor() {
        createAndShowGUI();
    }

    private void createAndShowGUI() {
        // Remove focus border around tabs
        UIManager.put("TabbedPane.focus", new Color(0, 0, 0, 0));

        window = new JFrame(TITLE);
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        window.setMinimumSize(new Dimension(800, 600));
        if (RedguardModManager.appIconImage != null) window.setIconImage(RedguardModManager.appIconImage);

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
        saveINIItem = ModManagerUtils.createMenuItem(fileMenu, "Save INI...", _ -> saveINIFile());
        saveINIItem.setEnabled(false);
        fileMenu.addSeparator();
        ModManagerUtils.createMenuItem(fileMenu, "Exit INI Editor", _ -> window.dispose());
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
            currentINIFile = (index == -1) ? null : iniFileTabs.get(index);

            boolean canSave = currentINIFile != null;
            saveINIItem.setEnabled(canSave);
        });
        window.add(tabbedPane, BorderLayout.CENTER);
        iniFileTabs = new ArrayList<>();
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
        bottomPanel.add(Box.createHorizontalStrut(25));

        // Buttons
        ModManagerUtils.createButton(bottomPanel, "Load INI File", _ -> loadINIFile());
        ModManagerUtils.createButton(bottomPanel, "Save to Mod", _ -> saveToMod());
    }

    /**
     * Try to find the text in searchBar within the current INI's text.
     */
    private void findText() {
        if (currentINIFile == null) return;
        String find = searchBar.getText().toLowerCase();
        if (!find.isEmpty()) {
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
     * Add a new tab or replace an existing tab with the same name, then put the given INI file into the new tab's text
     * area. Associate this tab with the given INI file in iniFileTabs.
     *
     * @param iniFile The INI file which names this tab and fills in the text area
     */
    private void addTab(INIFile iniFile) {
        currentINIFile = iniFile;

        JTextArea textArea;
        int tabIndex = iniFileTabs.indexOf(currentINIFile);
        if (tabIndex == -1) {
            // Create new tab
            tabIndex = iniFileTabs.size();
            iniFileTabs.add(currentINIFile);

            textArea = new JTextArea();
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setMargin(new Insets(5, 5, 5, 5));

            tabbedPane.addTab(currentINIFile.getName(), new JScrollPane(textArea));
            createTabButton(tabIndex);
        } else {
            // Tab exists
            textArea = getTextAreaAt(tabIndex);
        }
        textArea.setText(iniFile.getINIText());
        textArea.setCaretPosition(0); // Move back to the top
        tabbedPane.setSelectedIndex(tabIndex);
    }

    private void createTabButton(int index) {
        Box tabBox = Box.createHorizontalBox();
        tabBox.add(new JLabel(currentINIFile.getName()));
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
        iniFileTabs.remove(index);
    }

    private void loadINIFile() {
        String[] iniFiles = RedguardModManager.INI_FILES;
        String selection = (String) JOptionPane.showInputDialog(window, "Choose an INI file to load.",
                "Load INI File", JOptionPane.PLAIN_MESSAGE, null, iniFiles, iniFiles[0]);
        if (selection == null) return;
        INIFile iniFile = new INIFile(selection);
        try {
            iniFile.readINI(RedguardModManager.getBackupFile(selection));
        } catch (IOException e) {
            ModManagerUtils.showError(window, "Failed to read INI file from backup: " + selection);
        }
        addTab(iniFile);
    }

    private void saveINIFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save INI File");
        chooser.setFileFilter(new FileNameExtensionFilter("INI files (*.INI)", "INI"));
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setSelectedFile(new File(currentINIFile.getName()));
        int option = chooser.showSaveDialog(window);
        if (option != JFileChooser.APPROVE_OPTION) return;
        File fileToSave = ModManagerUtils.forceFileExtension(chooser.getSelectedFile(), ".INI");
        if (ModManagerUtils.confirmReplace(window, fileToSave)) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileToSave))) {
                String[] lineSplit = getSelectedTextArea().getText().split("\n");
                for (int i = 0; i < lineSplit.length; i++) {
                    writer.write(lineSplit[i]);
                    if (i < lineSplit.length - 1) {
                        writer.newLine();
                    }
                }
            } catch (IOException e) {
                ModManagerUtils.showError(window, "Failed to save INI file: " + fileToSave.getPath());
            }
        }
    }

    public void loadChangesFile(File fileToLoad) {
        INIChanges iniChanges = new INIChanges();
        try {
            iniChanges.readChanges(fileToLoad);
        } catch (IOException e) {
            ModManagerUtils.showError(window, "Failed to read changes file: " + fileToLoad.getPath());
        }

        for (INIFile iniChangedFile : iniChanges.getINIChangesFiles()) {
            String name = iniChangedFile.getName();
            INIFile iniFile = new INIFile(name);
            try {
                iniFile.readINI(RedguardModManager.getBackupFile(name));
            } catch (IOException e) {
                ModManagerUtils.showError(window, "Failed to read INI file from backup: " + name);
            }
            iniFile.applyChanges(iniChangedFile);
            addTab(iniFile);
        }
    }

    private void saveChangesFile(File fileToSave) {
        INIChanges iniChanges = new INIChanges();
        for (int i = 0; i < iniFileTabs.size(); i++) {
            INIFile originalINIFile = iniFileTabs.get(i);
            INIFile changedINIFile = new INIFile(originalINIFile.getName());
            changedINIFile.readINI(getTextAreaAt(i).getText());
            iniChanges.addINIChangeFile(changedINIFile.diff(originalINIFile));
        }
        try {
            iniChanges.writeChanges(fileToSave);
        } catch (IOException e) {
            ModManagerUtils.showError(window, "Failed to save changes file: " + fileToSave);
        }
    }

    private void saveToMod() {
        Mod mod = RedguardModManager.showModSelectionDialog(window, "Choose a mod to save INI changes.",
                "Save to Mod");
        if (mod == null) return;
        File changesFile = RedguardModManager.getModPath(mod).resolve(RedguardModManager.INI_CHANGES).toFile();
        saveChangesFile(changesFile);
        if (changesFile.length() == 0) {
            if (!changesFile.delete()) {
                logger.info("Deleted empty changes file: " + changesFile.getPath());
            } else {
                ModManagerUtils.showError(window, "Failed to delete empty changes file.");
            }
        }
    }
}