package redguard;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScriptReader {
    private enum ValueMode {
        MAIN, LHS, RHS, PARAMETER, REFERENCE, FORMULA
    }

    private enum ParameterType {
        NORMAL, DIALOGUE, MAP, ITEM
    }

    private static final String[] COMPARISONS = {
            " = ", " != ", " < ", " > ", " <= ", " >= "
    };

    private static final String[] OPERATORS = {
            "", " + ", " - ", " / ", " * ", " << ", " >> ", " & ", " | ", " ^ ", "++", "--"
    };

    private static final String[] OBJECT_NAMES = {
            "Me", "Player", "Camera"
    };

    private static final Map<String, ParameterType> PARAMETER_TYPES = new HashMap<>();

    static {
        PARAMETER_TYPES.put("ACTIVATE0", ParameterType.DIALOGUE);
        PARAMETER_TYPES.put("AddLog0", ParameterType.DIALOGUE);
        PARAMETER_TYPES.put("AmbientRtx0", ParameterType.DIALOGUE);
        PARAMETER_TYPES.put("menuAddItem0", ParameterType.DIALOGUE);
        PARAMETER_TYPES.put("RTX0", ParameterType.DIALOGUE);
        PARAMETER_TYPES.put("rtxAnim0", ParameterType.DIALOGUE);
        PARAMETER_TYPES.put("RTXp0", ParameterType.DIALOGUE);
        PARAMETER_TYPES.put("RTXpAnim0", ParameterType.DIALOGUE);
        PARAMETER_TYPES.put("TorchActivate0", ParameterType.DIALOGUE);
        PARAMETER_TYPES.put("LoadWorld0", ParameterType.MAP);
        PARAMETER_TYPES.put("ActiveItem9", ParameterType.ITEM);
        PARAMETER_TYPES.put("AddItem0", ParameterType.ITEM);
        PARAMETER_TYPES.put("DropItem0", ParameterType.ITEM);
        PARAMETER_TYPES.put("HandItem0", ParameterType.ITEM);
        PARAMETER_TYPES.put("HaveItem0", ParameterType.ITEM);
        PARAMETER_TYPES.put("SelectItem0", ParameterType.ITEM);
        PARAMETER_TYPES.put("ShowItem0", ParameterType.ITEM);
        PARAMETER_TYPES.put("ShowItemNoRtx0", ParameterType.ITEM);
    }

    private final MapDatabase mapDatabase;
    private final MapHeader header;
    private final DataInputStream input;
    private final List<ScriptInstruction> instructions;
    private ScriptInstruction currentInstruction;
    private final List<Integer> labels;
    private int pos;
    private int indentLevel;
    private String currentTask;
    private int taskParamNum;

    public ScriptReader(MapDatabase mapDatabase, MapHeader header) {
        this.mapDatabase = mapDatabase;
        this.header = header;
        input = new DataInputStream(new ByteArrayInputStream(header.getScriptBytes()));
        instructions = new ArrayList<>();
        labels = new ArrayList<>();
        pos = 0;
        indentLevel = 0;
        currentTask = null;
        taskParamNum = 0;
    }

    public String read() throws IOException {
        // Starts recursive reading of entire script for this header
        getBlock(header.getScriptLength());

        // Begin building a readable script based on the list of instructions
        StringBuilder script = new StringBuilder();
        script.append(header.getName()); // Header name
        // If scriptPC is not 0, then execution starts at a label
        if (header.getScriptPC() > 0) {
            script.append(" (Execution starts at ").append(addLabel(header.getScriptPC())).append(")");
        }
        script.append("\n{\n"); // Opening curly brace for header

        // Loop through all instructions and write the script, adding indentation. The Instruction class takes care of details.
        indentLevel = 1;
        for (ScriptInstruction instruction : instructions) {
            instruction.build(script, indentLevel, labels.contains(instruction.getAddress()));
            indentLevel = instruction.getIndentLevel();
        }
        script.append("}"); // Closing curly brace for header

        return script.toString();
    }

    private void getValue(ValueMode mode) throws IOException {
        // Value determines what to do next
        int valueType = readByte();
        switch (valueType) {
            case 0: // Task
            case 1: // Multitask
            case 2: // Function
                getTaskText(valueType);
                break;
            case 3: // If
                getIf();
                break;
            case 4: // Goto
                getLabel("Goto");
                break;
            case 5: // End
                getLabel("End");
                break;
            case 6: // Flag
                int flagIndex = readShort();
                SoupFlag flag = mapDatabase.getFlags().get(flagIndex);
                currentInstruction.appendText(flag.getName());
                switch (mode) {
                    case MAIN -> {
                        getFormula();
                        currentInstruction.setComment(flag.getComment());
                    }
                    case LHS, RHS -> getOperator();
                    case PARAMETER -> readShort();
                }
                break;
            case 7:
            case 22: // Numeric
                if (mode == ValueMode.PARAMETER || mode == ValueMode.RHS) {
                    currentInstruction.appendText(readIntFlexible());
                } else {
                    currentInstruction.appendText(String.valueOf(readInt()));
                }
                break;
            case 10: // Local Variable
                int varIndex = readByte();
                currentInstruction.appendText("var").appendText(String.valueOf(varIndex));
                switch (mode) {
                    case MAIN -> getFormula();
                    case LHS, RHS -> getOperator();
                    case PARAMETER -> {
                        readByte();
                        readByte();
                        readByte();
                    }
                }
                break;
            case 15:
            case 16: // Object-Dot Only with ++ or -- operator
                getObjectName();
                getReferenceName();
                currentInstruction.appendText((valueType == 15) ? "++" : "--");
                break;
            case 17: // Gosub
                currentInstruction.appendText("Gosub " + addLabel(readInt()));
                break;
            case 18: // Return
                currentInstruction.appendText("Return");
                break;
            case 19: // Endint
                currentInstruction.appendText("Endint");
                break;
            case 20: // Object-Dot Only
                getObjectName();
                getReferenceName();
                if (mode == ValueMode.MAIN) {
                    getFormula();
                } else if (mode == ValueMode.LHS) {
                    getOperator();
                }
                break;
            case 21: // String
                int stringIndex = readInt();
                currentInstruction.appendText('"' + header.getStrings().get(stringIndex) + '"');
                break;
            case 23: // <Anchor>=
                int anchor = readByte();
                currentInstruction.appendText("<Anchor>=" + anchor);
                break;
            case 25: // Object-Dot-Task
            case 26:
                getObjectName();
                if (mode == ValueMode.MAIN) {
                    getValue(ValueMode.REFERENCE);
                } else {
                    getTaskText(26);
                }
                break;
            case 27: // TaskPause
                int taskNum = readInt();
                String label = addLabel(taskNum);
                currentInstruction.appendText("<TaskPause(" + label + ")>");
                break;
            case 30: // if <ScriptRV> =
                int value = readByte();
                currentInstruction.appendText("if <ScriptRv> = " + value);
                getBlock(pos + 4);
                break;
        }
    }

    private void getBlock(int lastPos) throws IOException {
        indentLevel++;
        while (pos < lastPos) {
            currentInstruction = new ScriptInstruction(pos, indentLevel);
            instructions.add(currentInstruction);
            getValue(ValueMode.MAIN);
        }
        indentLevel--;
    }

    private void getTaskText(int taskType) throws IOException {
        int id = readShort();
        if (taskType == 1) {
            currentInstruction.appendText("@");
        }
        currentTask = mapDatabase.getFunctions().get(id).getName();
        currentInstruction.appendText(currentTask + "(");
        int numParams = (id == 0) ? 0 : readByte();
        if (numParams > 0) {
            for (int i = 0; i < numParams; i++) {
                if (i != 0) {
                    currentInstruction.appendText(", ");
                }
                taskParamNum = i;
                getValue(ValueMode.PARAMETER);
            }
            taskParamNum = 9;
        }
        currentInstruction.appendText(")");
    }

    private void getIf() throws IOException {
        currentInstruction.appendText("if ");
        int conjunction;
        do {
            getValue(ValueMode.LHS);
            int comparison = readByte();
            currentInstruction.appendText(COMPARISONS[comparison]);
            getValue(ValueMode.RHS);
            conjunction = readByte();
            if (conjunction > 0) {
                currentInstruction.appendText((conjunction == 1) ? " and " : " or ");
            }
        } while (conjunction != 0);
        int end = readInt();
        getBlock(end);
    }

    private void getLabel(String prefix) throws IOException {
        int label = readInt();
        currentInstruction.appendText(prefix);
        if (label != 0) {
            currentInstruction.appendText(" " + addLabel(label));
        }
    }

    private String addLabel(int label) {
        labels.add(label);
        return ScriptInstruction.makeLabel(label);
    }

    private void getFormula() throws IOException {
        currentInstruction.appendText(" = ");
        do {
            getValue(ValueMode.FORMULA);
        } while (getOperator());
    }

    private boolean getOperator() throws IOException {
        int operator = readByte();
        if (operator == 10 || operator == 11) {
            currentInstruction.appendText(OPERATORS[operator]);
            readByte();
            return false;
        }
        boolean wantsValue = false;
        if (operator > 0 && operator < 10) {
            currentInstruction.appendText(OPERATORS[operator]);
            wantsValue = true;
        }
        return wantsValue;
    }

    private void getObjectName() throws IOException {
        int paramValue = readByte();
        switch (paramValue) {
            case 0, 1, 2 -> {
                currentInstruction.appendText(OBJECT_NAMES[paramValue]);
                readByte();
            }
            case 4 -> currentInstruction.appendText(header.getStrings().get(readByte()));
            case 10 -> currentInstruction.appendText(String.valueOf(header.getVariables().get(readByte())));
        }
        currentInstruction.appendText(".");
    }

    private void getReferenceName() throws IOException {
        int offset = readShort() & 0xff;
        currentInstruction.appendText(mapDatabase.getReferences().get(offset));
    }

    private String readIntFlexible() throws IOException {
        // Determine how to read an integer based on the current task. Not actually necessary, but allows for more readable scripts.
        ParameterType paramType = PARAMETER_TYPES.getOrDefault(currentTask + taskParamNum, ParameterType.NORMAL);
        switch (paramType) {
            case DIALOGUE:
                String text = readString();
                String subtitle = mapDatabase.getRtxEntries().get(text);
                if (subtitle != null) {
                    currentInstruction.setComment("Dlg " + text + " = " + subtitle);
                }
                currentTask = null;
                return '"' + text + '"';
            case ITEM:
                int itemId = readInt();
                String itemName = mapDatabase.getItems().get(itemId).getName();
                currentTask = null;
                return "<" + itemName + ">";
            case MAP:
                int mapId = readInt();
                String mapName = mapDatabase.getMapFileFromId(mapId).getName();
                currentTask = null;
                return "<" + mapName + ">";
            default:
                return String.valueOf(readInt());
        }
    }

    private int readByte() throws IOException {
        pos++;
        return Utils.readUnsignedByte(input);
    }

    private int readShort() throws IOException {
        pos += 2;
        return Utils.readLittleEndianShort(input);
    }

    private int readInt() throws IOException {
        pos += 4;
        return Utils.readLittleEndianInt(input);
    }

    private String readString() throws IOException {
        pos += 4;
        return Utils.readString(input, 4);
    }
}