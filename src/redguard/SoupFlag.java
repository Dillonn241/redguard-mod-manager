package redguard;

public class SoupFlag {
    private final String type, name, value;
    private String comment;

    public SoupFlag(String line) {
        String[] split = line.split(";");
        if (split.length == 2) {
            comment = split[1];
        }
        String[] leftSplit = split[0].split("\\s+");
        type = leftSplit[0];
        name = leftSplit[1];
        value = leftSplit[2];
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public String getComment() {
        return comment;
    }
}