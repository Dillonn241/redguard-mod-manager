package redguard;

public class Item {
    private final String nameId, name, descriptionId, description;

    public Item(MapDatabase mapDatabase, int id, String nameId, String descriptionId) {
        this.nameId = nameId;
        this.descriptionId = descriptionId;
        switch (id) {
            case 7 -> name = "GUARD SWORD";
            case 15 -> name = "RUNE (2 LINES AND A DOT)";
            case 16 -> name = "RUNE (2 LINES)";
            case 17 -> name = "RUNE (A LINE AND DOT)";
            case 20 -> name = "ORC'S BLOOD (SUBLIMATED)";
            case 22 -> name = "SPIDER'S MILK (SUBLIMATED)";
            case 24 -> name = "ECTOPLASM (SUBLIMATED)";
            case 26 -> name = "HIST SAP (SUBLIMATED)";
            case 30 -> name = "GLASS VIAL (WITH ELIXIR)";
            case 34 -> name = "RUNE (fist)";
            case 35 -> name = "'ELVEN ARTIFACTS VIII' (COPY)";
            case 53 -> name = "ISZARA'S JOURNAL (OPEN)";
            case 57 -> name = "ISZARA'S JOURNAL (LOCKED)";
            case 61 -> name = "N'GASTA'S NECROMANCY BOOK";
            case 62 -> name = "BAR MUG";
            case 63 -> name = "MARIAH'S WATERING CAN";
            case 70 -> name = "SKELETON SWORD";
            case 71 -> name = "KEEP OUT";
            case 72 -> name = "NO TRESPASSING";
            case 73 -> name = "TOBIAS' BAR MUG";
            case 75 -> name = "FLAMING SABRE";
            case 76 -> name = "GOBLIN SWORD";
            case 77 -> name = "OGRE'S AXE";
            case 78 -> name = "DRAM'S SWORD";
            case 79 -> name = "SILVER KEY (PALACE)";
            case 80 -> name = "DRAM'S BOW";
            case 81 -> name = "DRAM'S ARROW";
            case 82 -> name = "SILVER LOCKET (COPY)";
            case 84 -> name = "WANTED POSTER";
            case 85 -> name = "PALACE DIAGRAM";
            case 86 -> name = "LAST";
            default -> name = mapDatabase.getRtxEntries().get(nameId);
        }
        description = mapDatabase.getRtxEntries().get(descriptionId);
    }

    public String getNameId() {
        return nameId;
    }

    public String getName() {
        return name;
    }

    public String getDescriptionId() {
        return descriptionId;
    }

    public String getDescription() {
        return description;
    }
}