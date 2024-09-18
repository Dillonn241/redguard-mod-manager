package modManager;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ModTable extends JTable {
    private final ModTableModel tableModel;
    private List<Mod> modList; // A list of mods by load order

    public ModTable() {
        super(new ModTableModel());

        tableModel = (ModTableModel) getModel();
        tableModel.addTableModelListener(evt -> {
            if (evt.getType() == TableModelEvent.UPDATE) {
                int row = evt.getFirstRow();
                modList.get(row).setEnabled((boolean) getValueAt(row, 0));
            }
        });
        modList = new ArrayList<>();

        // Better column resizing
        setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        // Hide grid
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 0));
        // Single selection
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Set column widths for checkboxes and mod order to be small
        getColumnModel().getColumn(0).setMinWidth(50);
        getColumnModel().getColumn(0).setMaxWidth(50);
        getColumnModel().getColumn(1).setMinWidth(40);
        getColumnModel().getColumn(1).setMaxWidth(40);
    }

    public List<Mod> getModList() {
        return modList;
    }

    public Mod getMod(int row) {
        if (!modList.isEmpty() && row >= 0 && row < getRowCount()) {
            return modList.get(row);
        }
        return null;
    }

    public Mod getSelectedMod() {
        return getMod(getSelectedRow());
    }

    public void addMod(Mod mod) {
        for (Mod otherMod : modList) {
            if (otherMod.getName().equals(mod.getName())) {
                return;
            }
        }
        modList.add(mod);
        tableModel.addRow(new Object[]{mod.isEnabled(), getRowCount(), mod.getName(), mod.getVersion(), mod.getAuthor()});
    }

    public void removeModAt(int row) {
        if (row >= 0 && row < getRowCount()) {
            modList.remove(row);
            tableModel.removeRow(row);
            for (int i = row; i < getRowCount(); i++) {
                setValueAt(i, i, 1);
            }
        }
    }

    public void removeSelectedMod() {
        removeModAt(getSelectedRow());
    }

    public void clearModList() {
        modList.clear();
        while (tableModel.getRowCount() > 0) {
            tableModel.removeRow(0);
        }
    }

    public void moveSelectedModUp() {
        int row = getSelectedRow();
        if (row > 0) {
            moveMod(row, row - 1);
            setRowSelectionInterval(row - 1, row - 1);
        }
    }

    public void moveSelectedModDown() {
        int row = getSelectedRow();
        if (row != -1 && row < getRowCount() - 1) {
            moveMod(row, row + 1);
            setRowSelectionInterval(row + 1, row + 1);
        }
    }

    public void moveMod(int from, int to) {
        tableModel.moveRow(from, from, to);
        Mod removed = modList.remove(from);
        modList.add(to, removed);
        int start = Math.min(from, to);
        for (int i = start; i < getRowCount(); i++) {
            setValueAt(i, i, 1);
        }
    }

    public void updateRow(int row) {
        Mod mod = getMod(row);
        setValueAt(mod.getName(), row, 2);
        setValueAt(mod.getVersion(), row, 3);
        setValueAt(mod.getAuthor(), row, 4);
    }

    public void updateSelectedRow() {
        updateRow(getSelectedRow());
    }

    // Disables editing except for checkboxes
    @Override
    public boolean isCellEditable(int row, int column) {
        return column == 0;
    }

    private static class ModTableModel extends DefaultTableModel {
        // A custom table model to allow checkboxes for the mod table
        public ModTableModel() {
            super(new Object[]{"Enabled", "Order", "Name", "Version", "Author"}, 0);
        }

        // Allows checkboxes in the first column
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) {
                return Boolean.class;
            }
            return super.getColumnClass(columnIndex);
        }
    }
}

