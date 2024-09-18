package modManager;

import redguard.RtxDatabase;
import redguard.RtxEntry;
import redguard.Utils;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static modManager.RedguardModManager.logger;

public class DialogueEditor {
    private static final String TITLE = "Redguard Dialogue Editor";
    private static final Color MODIFIED_COLOR = Color.GREEN;

    // GUI fields
    private JFrame window;
    private List<JTextField> subtitleFields;
    private JPanel dialogueListPanel;
    private SpringLayout dialogueListLayout;
    private JScrollPane scrollPane;
    private JPanel selectedLabelPanel;
    private JButton playStopButton;
    private JTextField searchBar;
    private int lastFoundIndex;

    // RTX Database
    private final RtxDatabase rtxDatabase;
    private RtxEntry selectedRtxEntry;
    private Clip currentAudioClip;

    public DialogueEditor(RtxDatabase rtxDatabase) {
        this.rtxDatabase = rtxDatabase;
        selectedRtxEntry = null;
        selectedLabelPanel = null;
        currentAudioClip = null;
        createAndShowGUI();
    }

    private void createAndShowGUI() {
        window = new JFrame(TITLE);
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        window.setMinimumSize(new Dimension(800, 600));
        subtitleFields = new ArrayList<>();

        // Split GUI creation into sections
        createMenuBar();
        createTextFields();
        createBottomPanel();

        packGUI();
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
        ModManagerUtils.createMenuItem(fileMenu, "Save RTX...", _ -> saveRtxFile());
        fileMenu.addSeparator();
        ModManagerUtils.createMenuItem(fileMenu, "Export Selected Audio...", _ -> exportSelectedAudio());
        ModManagerUtils.createMenuItem(fileMenu, "Export All Audio...", _ -> exportAllAudio());
        fileMenu.addSeparator();
        ModManagerUtils.createMenuItem(fileMenu, "Exit Dialogue Editor", _ -> window.dispose());
    }

    /**
     * Create the list of text fields that takes up the majority of the editor window.
     */
    private void createTextFields() {
        dialogueListLayout = new SpringLayout();
        dialogueListPanel = new JPanel(dialogueListLayout);

        rtxDatabase.stream().forEach(this::createTextField);

        scrollPane = new JScrollPane(dialogueListPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setPreferredSize(new Dimension(800, 560));
        scrollPane.getVerticalScrollBar().setUnitIncrement(4);
        scrollPane.getVerticalScrollBar().addAdjustmentListener(_ -> lastFoundIndex = (scrollPane.getVerticalScrollBar().getValue() + 20) / 30);
        window.add(scrollPane, BorderLayout.CENTER);
    }

    private void createTextField(RtxEntry rtxEntry) {
        JTextField textField = new JTextField();

        addTextModifiedListener(textField, rtxEntry);
        if (subtitleFields.isEmpty()) {
            dialogueListLayout.putConstraint(SpringLayout.NORTH, textField, 10, SpringLayout.NORTH, dialogueListPanel);
        } else {
            dialogueListLayout.putConstraint(SpringLayout.NORTH, textField, 10, SpringLayout.SOUTH, subtitleFields.getLast());
        }
        dialogueListLayout.putConstraint(SpringLayout.WEST, textField, 45, SpringLayout.WEST, dialogueListPanel);
        dialogueListLayout.putConstraint(SpringLayout.EAST, textField, -5, SpringLayout.EAST, dialogueListPanel);
        String subtitle = rtxEntry.getSubtitle();
        if (subtitle.isEmpty()) {
            textField.setBackground(MODIFIED_COLOR);
        } else {
            textField.setText(subtitle);
            textField.setCaretPosition(0);
        }
        subtitleFields.add(textField);
        dialogueListPanel.add(textField);

        JPanel labelPanel = new JPanel();
        addLabelPanelSelectionListener(labelPanel, rtxEntry);
        labelPanel.add(new JLabel(rtxEntry.getLabel()));
        dialogueListLayout.putConstraint(SpringLayout.NORTH, labelPanel, 0, SpringLayout.NORTH, textField);
        dialogueListLayout.putConstraint(SpringLayout.WEST, labelPanel, 5, SpringLayout.WEST, dialogueListPanel);
        dialogueListPanel.add(labelPanel);
    }

    private void packGUI() {
        dialogueListPanel.setPreferredSize(new Dimension(0, 10 + rtxDatabase.size() * 30));
        window.pack();
    }

    /**
     * Add a mouse adapter to the provided panel to allow selecting dialogue labels and playing audio from them.
     *
     * @param labelPanel The panel to add this listener to
     * @param rtxEntry   The RtxEntry to associate this panel with
     */
    private void addLabelPanelSelectionListener(JPanel labelPanel, RtxEntry rtxEntry) {
        labelPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (currentAudioClip != null) {
                    currentAudioClip.stop();
                }
                if (selectedLabelPanel != null) {
                    if (selectedLabelPanel.equals(labelPanel)) {
                        playOrStopSelectedAudio();
                    } else {
                        selectedLabelPanel.setBackground(null);
                    }
                }
                selectedRtxEntry = rtxEntry;
                selectedLabelPanel = labelPanel;
                labelPanel.setBackground(Color.GRAY);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (!labelPanel.equals(selectedLabelPanel)) {
                    labelPanel.setBackground(Color.LIGHT_GRAY);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!labelPanel.equals(selectedLabelPanel)) {
                    labelPanel.setBackground(null);
                }
            }
        });
    }

    /**
     * Add a key adapter to the provided panel to allow showing modified color.
     *
     * @param textField The text field to add this listener to
     * @param rtxEntry  The RtxEntry to associate this text field with
     */
    private void addTextModifiedListener(JTextField textField, RtxEntry rtxEntry) {
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String subtitle = rtxEntry.getSubtitle();
                if (!subtitle.isEmpty() && textField.getText().equals(subtitle)) {
                    textField.setBackground(Color.WHITE);
                } else {
                    textField.setBackground(MODIFIED_COLOR);
                }
            }
        });
    }

    /**
     * Create bottom panel that holds the find text field and load/save buttons.
     */
    private void createBottomPanel() {
        JPanel bottomPanel = new JPanel();
        bottomPanel.setPreferredSize(new Dimension(0, 40));
        window.add(bottomPanel, BorderLayout.SOUTH);

        // Play & stop audio button
        playStopButton = ModManagerUtils.createButton(bottomPanel, "▶", _ -> playOrStopSelectedAudio());

        // Search bar and button for finding label or subtitle matches
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
        ModManagerUtils.createButton(bottomPanel, "Save to Mod", _ -> saveToMod());
        ModManagerUtils.createButton(bottomPanel, "Add Entry", _ -> addRtxEntry());
        ModManagerUtils.createButton(bottomPanel, "Delete Entry", _ -> deleteSelectedRtxEntry());
    }

    /**
     * Search for the next label or text field starting at foundIndex that matches the text in searchBar. If
     * the last text field is reached, continue from the top to make sure everything is searched.
     */
    private void findText() {
        String findText = searchBar.getText().toLowerCase();
        for (int i = 0; i < rtxDatabase.size(); i++) {
            int nextIndex = (lastFoundIndex + 1 + i) % rtxDatabase.size();
            JTextField foundSubtitleField = subtitleFields.get(nextIndex);
            boolean found = false;

            // Check label
            if (rtxDatabase.get(nextIndex).getLabel().toLowerCase().contains(findText)) {
                found = true;
            } else {
                // Check text field
                int textPos = foundSubtitleField.getText().toLowerCase().indexOf(findText);
                if (textPos != -1) {
                    found = true;
                    // Select found text
                    foundSubtitleField.requestFocus();
                    foundSubtitleField.setCaretPosition(textPos);
                    foundSubtitleField.moveCaretPosition(textPos + findText.length());
                }
            }

            if (found) {
                // Deselect last found subtitle text
                JTextField lastFoundSubtitleField = subtitleFields.get(lastFoundIndex);
                if (lastFoundSubtitleField != null) {
                    lastFoundSubtitleField.setCaretPosition(0);
                    lastFoundSubtitleField.moveCaretPosition(0);
                }

                scrollPane.getVerticalScrollBar().setValue(subtitleFields.get(nextIndex).getY());
                lastFoundIndex = nextIndex;
                break;
            }
        }
    }

    /**
     * Ask for the folder to save the RTX file, then use the public method.
     */
    private void saveRtxFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save RTX");
        chooser.setSelectedFile(new File("ENGLISH"));
        int option = chooser.showSaveDialog(window);
        if (option != JFileChooser.APPROVE_OPTION) return;
        File fileToSave = ModManagerUtils.forceFileExtension(chooser.getSelectedFile(), ".RTX");
        if (ModManagerUtils.confirmReplace(window, fileToSave)) {
            RtxDatabase modifiedDatabase = new RtxDatabase(rtxDatabase);
            for (int i = 0; i < modifiedDatabase.size(); i++) {
                modifiedDatabase.get(i).setSubtitle(subtitleFields.get(i).getText());
            }
            try {
                modifiedDatabase.writeFile(fileToSave);
            } catch (IOException e) {
                ModManagerUtils.showError(window, "Failed to save RTX file: " + fileToSave.getPath());
            }
        }
    }

    public void loadChangesFile(File fileToLoad) {
        try (BufferedReader reader = new BufferedReader(new FileReader(fileToLoad))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                String[] entry = line.split("\t");
                String subtitle = entry.length < 3 ? "" : entry[2];
                int index = Integer.parseInt(entry[0]);
                if (index >= subtitleFields.size()) {
                    addRtxEntry(entry[1], subtitle);
                }
                subtitleFields.get(index).setText(subtitle);
                subtitleFields.get(index).setBackground(MODIFIED_COLOR);
                subtitleFields.get(index).setCaretPosition(0);
            }
        } catch (IOException e) {
            ModManagerUtils.showError(window, "Failed to read changes from file: " + fileToLoad.getPath());
        }
    }

    private void saveChangesFile(File fileToSave) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileToSave))) {
            for (int i = 0; i < rtxDatabase.size(); i++) {
                RtxEntry rtxEntry = rtxDatabase.get(i);
                JTextField subtitleField = subtitleFields.get(i);
                String text = subtitleField.getText();
                if (subtitleField.getBackground().equals(MODIFIED_COLOR)) {
                    writer.write(i + "\t" + rtxEntry.getLabel() + "\t" + text);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            ModManagerUtils.showError(window, "Failed to save changes to file: " + fileToSave.getPath());
        }
    }

    private void saveToMod() {
        Mod mod = RedguardModManager.showModSelectionDialog(window, "Choose a mod to save dialogue changes.",
                "Save to Mod");
        if (mod == null) return;
        File changesFile = RedguardModManager.getModPath(mod).resolve(RedguardModManager.RTX_CHANGES).toFile();
        saveChangesFile(changesFile);
        if (changesFile.length() == 0) {
            if (changesFile.delete()) {
                logger.info("Deleted empty changes file: " + changesFile.getPath());
            } else {
                ModManagerUtils.showError(window, "Failed to delete empty changes file: " + changesFile.getPath());
            }
        }
    }

    private void addRtxEntry() {
        String labelStr = JOptionPane.showInputDialog(window, "Enter dialogue label:", "Add Dialogue Entry", JOptionPane.QUESTION_MESSAGE);
        // User canceled or exited the dialog
        if (labelStr == null) {
            return;
        }
        // Check if label is blank
        String trimmedLabel = labelStr.trim();
        if (trimmedLabel.isEmpty()) {
            ModManagerUtils.showWarning(window, "Dialogue label cannot be blank.");
            addRtxEntry();
            return;
        }
        addRtxEntry(trimmedLabel, "");
    }

    /**
     * Add dialogue entry if it's not a duplicate, or throw an error message.
     *
     * @param labelStr    The label to give the entry
     * @param subtitleStr The subtitle to give the entry
     */
    private void addRtxEntry(String labelStr, String subtitleStr) {
        if (!rtxDatabase.hasLabel(labelStr)) {
            RtxEntry newEntry = new RtxEntry(labelStr, subtitleStr);
            rtxDatabase.add(newEntry);
            createTextField(newEntry);
            packGUI();
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
            subtitleFields.getLast().requestFocus();
        } else {
            ModManagerUtils.showError(window, "The dialogue label " + labelStr + " is already in use.");
        }
    }

    private void deleteSelectedRtxEntry() {
        if (!checkHasSelection()) return;
        int shouldDelete = JOptionPane.showConfirmDialog(window, "Are you sure you want to delete "
                + selectedRtxEntry.getLabel() + "?", "Confirm deletion", JOptionPane.YES_NO_OPTION);
        if (shouldDelete == JOptionPane.YES_OPTION) {
            if (currentAudioClip != null) {
                currentAudioClip.stop();
            }
            int index = rtxDatabase.indexOf(selectedRtxEntry);
            JTextField textFieldToDelete = subtitleFields.get(index);
            // redo layout so text field below the one being deleted moves to its place
            if (index < subtitleFields.size() - 1) {
                JTextField nextTextField = subtitleFields.get(index + 1);
                if (index == 0) {
                    dialogueListLayout.putConstraint(SpringLayout.NORTH, nextTextField, 10, SpringLayout.NORTH, dialogueListPanel);
                } else {
                    JTextField previousTextField = subtitleFields.get(index - 1);
                    dialogueListLayout.putConstraint(SpringLayout.NORTH, nextTextField, 10, SpringLayout.SOUTH, previousTextField);
                }
            }
            subtitleFields.remove(index);
            dialogueListPanel.remove(textFieldToDelete);
            dialogueListPanel.remove(selectedLabelPanel);
            rtxDatabase.remove(selectedRtxEntry);
            packGUI();
        }
    }

    private boolean checkHasSelection() {
        if (selectedRtxEntry != null) {
            return true;
        } else {
            JOptionPane.showMessageDialog(window, "No dialogue label selected.", "No selection", JOptionPane.INFORMATION_MESSAGE);
        }
        return false;
    }

    private boolean checkHasAudio() {
        if (checkHasSelection()) {
            if (selectedRtxEntry.hasAudio()) {
                return true;
            } else {
                JOptionPane.showMessageDialog(window, "Selected dialogue label has no audio.", "No audio", JOptionPane.INFORMATION_MESSAGE);
            }
        }
        return false;
    }

    private void playOrStopSelectedAudio() {
        if (currentAudioClip != null) {
            currentAudioClip.stop();
        } else if (checkHasAudio()) {
            try {
                currentAudioClip = selectedRtxEntry.playAudio();
                playStopButton.setText("⏹");
                currentAudioClip.addLineListener(_ -> {
                    if (!currentAudioClip.isRunning()) {
                        playStopButton.setText("▶");
                        currentAudioClip = null;
                    }
                });
            } catch (IOException e) {
                ModManagerUtils.showError(window, "Failed to play audio: " + selectedRtxEntry.getLabel());
            }
        }
    }

    private void exportSelectedAudio() {
        if (!checkHasAudio()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Audio File");
        chooser.setFileFilter(new FileNameExtensionFilter("WAV files (*.wav)", "wav"));
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setSelectedFile(new File(Utils.validFilename(selectedRtxEntry.getLabel())));
        int option = chooser.showSaveDialog(window);
        if (option == JFileChooser.APPROVE_OPTION) {
            File file = ModManagerUtils.forceFileExtension(chooser.getSelectedFile(), ".wav");
            if (ModManagerUtils.confirmReplace(window, file)) {
                exportAudio(selectedRtxEntry, file);
            }
        }
    }

    private void exportAudio(RtxEntry rtxEntry, File fileToSave) {
        if (!rtxEntry.hasAudio()) return;
        try {
            AudioSystem.write(rtxEntry.audioInputStream(), AudioFileFormat.Type.WAVE, fileToSave);
        } catch (IOException e) {
            ModManagerUtils.showError(window, "Failed to write audio to file: " + fileToSave.getPath());
        }
    }

    private void exportAllAudio() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export All Audio Files as WAVs");
        chooser.setSelectedFile(new File("Redguard Audio"));

        int option = chooser.showSaveDialog(window);
        if (option == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            if (!selectedFile.mkdir()) {
                ModManagerUtils.showError(window, "Failed to create folder: " + selectedFile.getPath());
                return;
            }
            rtxDatabase.stream().forEach(rtxEntry -> exportAudio(rtxEntry, new File(selectedFile + "/" + Utils.validFilename(rtxEntry.getLabel()) + ".wav")));
        }
    }
}