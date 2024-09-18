package redguard;

public class ScriptInstruction {
    public static final String INDENT = "  ";

    private final int address;
    private final int indentLevel;
    private final StringBuilder text;
    private String comment;

    public ScriptInstruction(int address, int indentLevel) {
        this.address = address;
        this.indentLevel = indentLevel;
        text = new StringBuilder();
    }

    public int getAddress() {
        return address;
    }

    public int getIndentLevel() {
        return indentLevel;
    }

    public String getText() {
        return text.toString();
    }

    public ScriptInstruction appendText(String str) {
        text.append(str);
        return this;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void build(StringBuilder sb, int previousIndentLevel, boolean addLabel) {
        if (indentLevel > previousIndentLevel) {
            indent(sb, previousIndentLevel);
            sb.append("{\n");
        } else while (indentLevel < previousIndentLevel) {
            indent(sb, previousIndentLevel - 1);
            sb.append("}\n");
            previousIndentLevel--;
        }

        if (addLabel) {
            sb.append("\n");
            indent(sb, indentLevel);
            sb.append(makeLabel(address)).append(":\n");
        }

        indent(sb, indentLevel);
        sb.append(text);

        if (comment != null) {
            sb.append(" // ").append(comment);
        }
        sb.append("\n");
    }

    private void indent(StringBuilder sb, int previousIndentLevel) {
        sb.append(INDENT.repeat(previousIndentLevel));
    }

    public static String makeLabel(int address) {
        return String.format("#%02X", address);
    }

    @Override
    public String toString() {
        return text.toString();
    }
}