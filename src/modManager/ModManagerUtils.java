package modManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static modManager.RedguardModManager.logger;

public class ModManagerUtils {
    /**
     * Tries to delete a folder and all its subfolders and files
     * @param folder The folder to try to delete.
     * @return True if deletion was successful. False otherwise.
     */
    public static boolean deleteFolder(Path folder) {
        File[] fileArray = folder.toFile().listFiles();
        boolean failedToFullyDelete = false;
        if (fileArray != null) {
            for (File fileToDelete : fileArray) {
                if (fileToDelete.isDirectory()) {
                    deleteFolder(fileToDelete.toPath());
                } else {
                    if (fileToDelete.delete()) {
                        logger.info("Deleted file: " + fileToDelete);
                    } else {
                        failedToFullyDelete = true;
                    }
                }
            }
        }
        if (failedToFullyDelete) {
            return false;
        }
        File folderToDelete = folder.toFile();
        if (folderToDelete.delete()) {
            logger.info("Deleted folder: " + folderToDelete);
            return true;
        }
        return false;
    }

    /**
     * Tries to copy a folder and all its subfolders and files from one {@link Path} to another
     * @param source The source path to copy from.
     * @param target The target path to copy to.
     * @throws IOException May be thrown when calling Files.copy() method.
     */
    public static void copyFolder(Path source, Path target) throws IOException {
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        File[] fileArray = source.toFile().listFiles();
        if (fileArray != null) {
            for (File fileToCopy : fileArray) {
                if (fileToCopy.isDirectory()) {
                    copyFolder(fileToCopy.toPath(), target.resolve(fileToCopy.getName()));
                } else {
                    Files.copy(fileToCopy.toPath(), target.resolve(fileToCopy.getName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public static void showError(JFrame window, String message) {
        JOptionPane.showMessageDialog(window, message, "Error", JOptionPane.ERROR_MESSAGE);
        logger.severe(message);
    }

    public static void showWarning(JFrame window, String message) {
        JOptionPane.showMessageDialog(window, message, "Warning", JOptionPane.WARNING_MESSAGE);
        logger.warning(message);
    }

    public static JMenuItem createMenuItem(JMenu menu, String text, ActionListener listener) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(listener);
        menu.add(item);
        return item;
    }

    public static JButton createButton(JPanel panel, String text, ActionListener listener) {
        JButton button = new JButton(text);
        button.addActionListener(listener);
        panel.add(button);
        return button;
    }

    public static File forceFileExtension(File file, String extension) {
        String filePath = file.getPath();
        int dotIndex = filePath.indexOf(".");
        if (dotIndex != -1) {
            filePath = filePath.substring(0, dotIndex);
        }
        return new File(filePath + extension);
    }

    public static boolean confirmReplace(Component parentComponent, File file) {
        if (file.exists()) {
            int option = JOptionPane.showConfirmDialog(parentComponent, file.getName() + " already exists. Do you want to replace it?",
                    "Replace file", JOptionPane.YES_NO_OPTION);
            return option == JOptionPane.YES_OPTION;
        }
        return true;
    }
}