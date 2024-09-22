package modManager;

import redguard.Texture;
import redguard.TextureFile;

import javax.imageio.ImageIO;
import javax.swing.Timer;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.*;

public class TextureViewer {
    private static final String TITLE = "Redguard Texture Viewer";
    private static final String DEFAULT_COLOR_MAP = "REDGUARD.COL";
    private static final Set<String> IGNORED_GXA = new HashSet<>();

    static {
        IGNORED_GXA.add("GXICONS.GXA");
        IGNORED_GXA.add("INVBACK.GXA");
        IGNORED_GXA.add("INVMASK.GXA");
        IGNORED_GXA.add("PICKBLOB.GXA");
        IGNORED_GXA.add("SNUFF.GXA");
    }

    // GUI fields
    private JFrame window;
    private JList<String> fileList;

    // Other fields
    private final Path gamePath;
    private TextureFile textureFile;
    private int textureIndex;
    private int frameIndex;

    public TextureViewer(Path gamePath) {
        this.gamePath = gamePath;
        File cMapFile = gamePath.resolve("fxart/" + DEFAULT_COLOR_MAP).toFile();
        try {
            TextureFile.loadDefaultCMap(cMapFile);
        } catch (IOException e) {
            ModManagerUtils.showError(window, "Failed to load color map file: " + cMapFile.getPath());
        }
        createAndShowGUI();
    }

    private void createAndShowGUI() {
        window = new JFrame(TITLE);
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        window.setMinimumSize(new Dimension(800, 600));
        if (RedguardModManager.appIconImage != null) window.setIconImage(RedguardModManager.appIconImage);

        JPanel rightPanel = new JPanel(new BorderLayout());
        window.add(rightPanel, BorderLayout.CENTER);

        TexturePanel texturePanel = new TexturePanel();
        rightPanel.add(texturePanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        bottomPanel.setPreferredSize(new Dimension(0, 40));
        rightPanel.add(bottomPanel, BorderLayout.SOUTH);

        List<String> comboBoxData = new ArrayList<>();
        File[] fxartFileArray = gamePath.resolve("fxart").toFile().listFiles();
        if (fxartFileArray != null) {
            for (File fxartFile : fxartFileArray) {
                if (fxartFile.getName().toUpperCase().endsWith(".COL") && fxartFile.length() > 0) {
                    comboBoxData.add(fxartFile.getName());
                }
            }
        }
        JComboBox<String> colorMapBox = new JComboBox<>(comboBoxData.toArray(new String[0]));
        colorMapBox.setSelectedItem("REDGUARD.COL");
        colorMapBox.addItemListener(_ -> {
            File cMapFile = gamePath.resolve("fxart/" + colorMapBox.getSelectedItem()).toFile();
            try {
                TextureFile.loadDefaultCMap(cMapFile);
            } catch (IOException e) {
                ModManagerUtils.showError(window, "Failed to load color map file: " + cMapFile.getPath());
            }
            if (textureFile.getType() == TextureFile.Type.TEXBSI) {
                loadTextureFile(fileList.getSelectedValue());
            }
        });
        bottomPanel.add(colorMapBox);

        bottomPanel.add(Box.createHorizontalStrut(20));
        ModManagerUtils.createButton(bottomPanel, "<", _ -> setTextureIndex(textureIndex - 1));
        ModManagerUtils.createButton(bottomPanel, ">", _ -> setTextureIndex(textureIndex + 1));
        bottomPanel.add(Box.createHorizontalStrut(20));
        ModManagerUtils.createButton(bottomPanel, "Import PNG", _ -> importImage());
        ModManagerUtils.createButton(bottomPanel, "Export PNG", _ -> exportImage());
        ModManagerUtils.createButton(bottomPanel, "Save Texture", _ -> writeTexture());
        bottomPanel.add(Box.createHorizontalStrut(20));

        createMenuBar();
        createListPanel();

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
        ModManagerUtils.createMenuItem(fileMenu, "Export Texture File", _ -> exportTextureFile());
        ModManagerUtils.createMenuItem(fileMenu, "Export All Files", _ -> exportAllTextures());
        fileMenu.addSeparator();
        ModManagerUtils.createMenuItem(fileMenu, "Exit Texture Viewer", _ -> window.dispose());
    }

    private void createListPanel() {
        List<String> listData = new ArrayList<>();

        File[] fxartFileArray = gamePath.resolve("fxart").toFile().listFiles();
        if (fxartFileArray != null) {
            for (File fxartFile : fxartFileArray) {
                if (fxartFile.getName().toUpperCase().startsWith("TEXBSI")) {
                    listData.add(fxartFile.getName());
                }
            }
        }
        File[] systemFileArray = gamePath.resolve("system").toFile().listFiles();
        if (systemFileArray != null) {
            for (File systemFile : systemFileArray) {
                String upperName = systemFile.getName().toUpperCase();
                if (upperName.endsWith(".GXA") && !IGNORED_GXA.contains(upperName)) {
                    listData.add(systemFile.getName());
                }
            }
        }
        File[] fontsFileArray = gamePath.resolve("fonts").toFile().listFiles();
        if (fontsFileArray != null) {
            for (File fontFile : fontsFileArray) {
                if (fontFile.getName().toUpperCase().endsWith(".FNT")) {
                    listData.add(fontFile.getName());
                }
            }
        }

        fileList = new JList<>(listData.toArray(new String[0]));
        fileList.setSelectedIndex(0);
        loadTextureFile(listData.getFirst());
        fileList.addListSelectionListener(_ -> loadTextureFile(fileList.getSelectedValue()));

        JScrollPane listScrollPane = new JScrollPane(fileList);
        listScrollPane.setPreferredSize(new Dimension(100, 0));
        window.add(listScrollPane, BorderLayout.WEST);
    }

    private void loadTextureFile(String filename) {
        String name = filename.toUpperCase();
        TextureFile.Type fileType;
        String folder;
        if (name.endsWith(".GXA")) {
            fileType = TextureFile.Type.GXA;
            folder = "system";
        } else if (name.endsWith(".FNT")) {
            fileType = TextureFile.Type.FNT;
            folder = "fonts";
        } else {
            fileType = TextureFile.Type.TEXBSI;
            folder = "fxart";
        }

        textureFile = new TextureFile(fileType);
        Path textureFilePath = gamePath.resolve(folder).resolve(filename);
        try {
            textureFile.loadTextures(textureFilePath.toFile());
        } catch (IOException e) {
            ModManagerUtils.showError(window, "Failed to load texture file: " + textureFilePath);
        }

        setTextureIndex(0);
        frameIndex = 0;
    }

    private void setTextureIndex(int value) {
        textureIndex = value;

        List<Texture> textures = textureFile.getTextures();
        int max = textures.size() - 1;
        if (textureIndex < 0) {
            textureIndex = max;
        } else if (textureIndex > max) {
            textureIndex = 0;
        }

        frameIndex = 0;
    }

    private void importImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Image from PNG or Folder");
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        int option = chooser.showOpenDialog(window);
        if (option != JFileChooser.APPROVE_OPTION) return;
        Color[] cMap = textureFile.getTextures().get(textureIndex).getCMap();
        byte[] headerBytes = textureFile.getTextures().get(textureIndex).getHeaderBytes();
        File fileToLoad = chooser.getSelectedFile();
        try {
            if (fileToLoad.isDirectory()) {
                File[] files = fileToLoad.listFiles();
                List<BufferedImage> images = new ArrayList<>();
                if (files != null) {
                    Arrays.sort(files);
                    for (File file : files) {
                        if (file.getName().endsWith(".png")) {
                            images.add(ImageIO.read(file));
                        }
                    }
                }
                BufferedImage[] imageArray = images.toArray(new BufferedImage[0]);
                textureFile.getTextures().set(textureIndex, new Texture(headerBytes, cMap, imageArray));
            } else {
                BufferedImage image = ImageIO.read(fileToLoad);
                textureFile.getTextures().set(textureIndex, new Texture(headerBytes, cMap, image));
            }
        } catch (IOException e) {
            ModManagerUtils.showError(window, "Failed to import image from PNG or folder: " + fileToLoad.getPath());
        }
    }

    private void exportImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Image as PNG");

        String[] split = fileList.getSelectedValue().split("\\.");
        String indexStr = String.format(", Index %03d", textureIndex + 1);
        if (split[1].equals("GXA") || split[1].equals("FNT")) {
            chooser.setSelectedFile(new File(split[0] + indexStr));
        } else {
            chooser.setSelectedFile(new File(split[0] + " " + split[1] + indexStr));
        }

        int option = chooser.showSaveDialog(window);
        if (option != JFileChooser.APPROVE_OPTION) return;
        Texture texture = textureFile.getTextures().get(textureIndex);
        String fileToSavePath = chooser.getSelectedFile().getPath();
        try {
            if (texture.getNumFrames() == 1) {
                File file = new File(fileToSavePath + ".png");
                if (ModManagerUtils.confirmReplace(window, file)) {
                    ImageIO.write(texture.getImages()[0], "png", file);
                }
            } else {
                File file = new File(fileToSavePath);
                if (ModManagerUtils.confirmReplace(window, file)) {
                    if (!file.mkdir()) {
                        ModManagerUtils.showError(window, "Failed to create folder: " + file.getPath());
                        return;
                    }
                    for (int frame = 0; frame < texture.getNumFrames(); frame++) {
                        String str = String.format("%s/%s, Frame %03d.png", file.getPath(), file.getName(), frame + 1);
                        ImageIO.write(texture.getImages()[frame], "png", new File(str));
                    }
                }
            }
        } catch (IOException e) {
            ModManagerUtils.showError(window, "Failed to export image as PNG: " + fileToSavePath);
        }
    }

    private void exportTextureFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Texture File as PNGs");

        String[] split = fileList.getSelectedValue().split("\\.");
        String filePath = split[0];
        if (!split[1].equals("GXA") && !split[1].equals("FNT")) {
            filePath += " " + split[1];
        }
        chooser.setSelectedFile(new File(filePath));

        int option = chooser.showSaveDialog(window);
        if (option != JFileChooser.APPROVE_OPTION) return;
        File selectedFile = chooser.getSelectedFile();
        if (!selectedFile.mkdir()) {
            ModManagerUtils.showError(window, "Failed to create folder: " + selectedFile.getPath());
            return;
        }

        for (int index = 0; index < textureFile.getTextures().size(); index++) {
            Texture texture = textureFile.getTextures().get(index);

            String indexStr = String.format("%s, Index %03d", selectedFile.getName(), index + 1);
            filePath = selectedFile + "/" + indexStr;

            try {
                if (texture.getNumFrames() == 1) {
                    File file = new File(filePath + ".png");
                    if (ModManagerUtils.confirmReplace(window, file)) {
                        ImageIO.write(texture.getImages()[0], "png", file);
                    }
                } else {
                    File file = new File(filePath);
                    if (ModManagerUtils.confirmReplace(window, file)) {
                        if (!file.mkdir()) {
                            continue;
                        }
                        for (int frame = 0; frame < texture.getNumFrames(); frame++) {
                            String str = String.format("%s/%s, Frame %03d.png", filePath, file.getName(), frame + 1);
                            ImageIO.write(texture.getImages()[frame], "png", new File(str));
                        }
                    }
                }
            } catch (IOException e) {
                ModManagerUtils.showError(window, "Failed to export texture file as PNGs: " + filePath);
            }
        }
    }

    private void exportAllTextures() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export All Texture Files as PNGs");
        chooser.setSelectedFile(new File("Redguard Textures"));

        int option = chooser.showSaveDialog(window);
        if (option != JFileChooser.APPROVE_OPTION) return;
        File selectedFile = chooser.getSelectedFile();
        if (!selectedFile.mkdir()) {
            ModManagerUtils.showError(window, "Failed to create folder: " + selectedFile.getPath());
            return;
        }

        for (int fileIndex = 0; fileIndex < fileList.getModel().getSize(); fileIndex++) {
            String fileName = fileList.getModel().getElementAt(fileIndex);
            fileList.setSelectedIndex(fileIndex);
            loadTextureFile(fileName);

            String[] split = fileName.split("\\.");
            String filePath = selectedFile + "/" + split[0];
            if (!split[1].equals("GXA") && !split[1].equals("FNT")) {
                filePath += " " + split[1];
            }
            File textureFileFile = new File(filePath);
            if (!textureFileFile.mkdir()) continue;

            for (int index = 0; index < textureFile.getTextures().size(); index++) {
                Texture texture = textureFile.getTextures().get(index);

                String indexStr = String.format("%s, Index %03d", textureFileFile.getName(), index + 1);
                String filePath2 = filePath + "/" + indexStr;

                try {
                    if (texture.getNumFrames() == 1) {
                        File file = new File(filePath2 + ".png");
                        if (ModManagerUtils.confirmReplace(window, file)) {
                            ImageIO.write(texture.getImages()[0], "png", file);
                        }
                    } else {
                        File file = new File(filePath2);
                        if (ModManagerUtils.confirmReplace(window, file)) {
                            if (!file.mkdir()) {
                                continue;
                            }
                            for (int frame = 0; frame < texture.getNumFrames(); frame++) {
                                String str = String.format("%s/%s, Frame %03d.png", filePath2, file.getName(), frame + 1);
                                ImageIO.write(texture.getImages()[frame], "png", new File(str));
                            }
                        }
                    }
                } catch (IOException e) {
                    ModManagerUtils.showError(window, "Failed to export all texture files as PNGs: " + filePath);
                }
            }
        }
    }

    private void writeTexture() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Texture");
        chooser.setSelectedFile(new File(fileList.getSelectedValue()));
        int option = chooser.showSaveDialog(window);
        if (option != JFileChooser.APPROVE_OPTION) return;
        File fileToSave = chooser.getSelectedFile();
        try {
            textureFile.writeTextures(fileToSave);
        } catch (IOException e) {
            ModManagerUtils.showError(window, "Failed to save texture: " + fileToSave.getPath());
        }
    }

    private class TexturePanel extends JPanel {
        public TexturePanel() {
            setPreferredSize(new Dimension(700, 560));
            setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            new Timer(250, _ -> repaint()).start();
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (textureFile == null) return;
            Texture texture = textureFile.getTextures().get(textureIndex);
            BufferedImage image = texture.getImages()[frameIndex];

            frameIndex++;
            if (frameIndex >= texture.getNumFrames()) {
                frameIndex = 0;
            }

            int width, height;
            if (image.getWidth() > image.getHeight()) {
                width = 500;
                height = width * image.getHeight() / image.getWidth();
            } else {
                height = 400;
                width = height * image.getWidth() / image.getHeight();
            }
            int x = (getWidth() - width) / 2;
            int y = (getHeight() - height) / 2;
            g.drawImage(image, x, y, width, height, null);

            // Details in top-left corner
            g.drawString("Image: " + (textureIndex + 1) + " / " + textureFile.getTextures().size(), 5, 15);
            g.drawString("Size: " + texture.getWidth() + " x " + texture.getHeight() + " px", 5, 30);
            g.drawString("Frames: " + texture.getNumFrames(), 5, 45);
        }
    }
}