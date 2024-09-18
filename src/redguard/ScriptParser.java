package redguard;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScriptParser {
    // Used to determine how to use incoming values in parseValue
    private enum ValueMode {
        MAIN, LHS, RHS, PARAMETER, REFERENCE, FORMULA
    }

    // Basic script-related symbols
    private static final Map<String, Integer> OBJECT_NAME_VALUES = new HashMap<>();
    private static final Map<String, Integer> OPERATOR_VALUES = new HashMap<>();
    private static final Map<String, Integer> COMPARISON_VALUES = new HashMap<>();
    // Functions that have dialogue as an argument. Needed to differentiate between strings and dialogue.
    private static final Set<String> DIALOGUE_FUNCTIONS = new HashSet<>();

    // Initialize static maps and lists that do not require the map database
    static {
        // Object name values
        OBJECT_NAME_VALUES.put("Me", 0);
        OBJECT_NAME_VALUES.put("Player", 1);
        OBJECT_NAME_VALUES.put("Camera", 2);

        // Operator values, no extra spacing unlike ScriptReader
        OPERATOR_VALUES.put("", 0);
        OPERATOR_VALUES.put("+", 1);
        OPERATOR_VALUES.put("-", 2);
        OPERATOR_VALUES.put("/", 3);
        OPERATOR_VALUES.put("*", 4);
        OPERATOR_VALUES.put("<<", 5);
        OPERATOR_VALUES.put(">>", 6);
        OPERATOR_VALUES.put("&", 7);
        OPERATOR_VALUES.put("|", 8);
        OPERATOR_VALUES.put("^", 9);
        OPERATOR_VALUES.put("++", 10);
        OPERATOR_VALUES.put("--", 11);

        // Comparison values, no extra spacing unlike ScriptReader
        COMPARISON_VALUES.put("=", 0);
        COMPARISON_VALUES.put("!=", 1);
        COMPARISON_VALUES.put("<", 2);
        COMPARISON_VALUES.put(">", 3);
        COMPARISON_VALUES.put("<=", 4);
        COMPARISON_VALUES.put(">=", 5);

        // Dialogue functions
        DIALOGUE_FUNCTIONS.add("ACTIVATE");
        DIALOGUE_FUNCTIONS.add("AddLog");
        DIALOGUE_FUNCTIONS.add("AmbientRtx");
        DIALOGUE_FUNCTIONS.add("menuAddItem");
        DIALOGUE_FUNCTIONS.add("RTX");
        DIALOGUE_FUNCTIONS.add("rtxAnim");
        DIALOGUE_FUNCTIONS.add("RTXp");
        DIALOGUE_FUNCTIONS.add("RTXpAnim");
        DIALOGUE_FUNCTIONS.add("TorchActivate");
    }

    // Maps to look up ids for various names that ScriptReader introduced
    private static final Map<String, Integer> mapIds = new HashMap<>();
    private static final Map<String, Integer> functionIds = new HashMap<>();
    private static final Map<String, Integer> flagIds = new HashMap<>();
    private static final Map<String, Integer> itemIds = new HashMap<>();
    private static final Map<String, Integer> references = new HashMap<>();
    private static final Map<String, Integer> attributes = new HashMap<>();

    private final MapDatabase mapDatabase;
    private Scanner scanner;
    private final List<ParsedMapHeader> parsedHeaders;
    private ParsedMapHeader currentHeader;
    private final List<Byte> currentScriptBytes;
    private final Map<Integer, List<Integer>> labels;
    private int pos;
    private String currentTask;
    private int totalScriptLength;

    public ScriptParser(MapDatabase mapDatabase, String script) {
        // Initialize reverse maps for fast lookup
        this.mapDatabase = mapDatabase;
        initReverseMaps();

        parsedHeaders = new ArrayList<>();
        currentScriptBytes = new ArrayList<>();
        labels = new HashMap<>();
        preParse(script);
    }

    private void initReverseMaps() {
        // Map files, include angle brackets
        for (MapFile mapFile : mapDatabase.getMapFiles()) {
            mapIds.put("<" + mapFile.getName() + ">", mapFile.getIDs().getFirst());
        }
        // Functions
        for (int i = 0; i < mapDatabase.getFunctions().size(); i++) {
            functionIds.put(mapDatabase.getFunctions().get(i).getName(), i);
        }
        // Flags
        for (int i = 0; i < mapDatabase.getFlags().size(); i++) {
            flagIds.put(mapDatabase.getFlags().get(i).getName(), i);
        }
        // Items, include angle brackets
        for (int i = 0; i < mapDatabase.getItems().size(); i++) {
            itemIds.put("<" + mapDatabase.getItems().get(i).getName() + ">", i);
        }
        // References
        for (int i = 0; i < mapDatabase.getReferences().size(); i++) {
            references.put(mapDatabase.getReferences().get(i), i);
        }
        // Attributes
        for (int i = 0; i < mapDatabase.getAttributes().size(); i++) {
            attributes.put(mapDatabase.getAttributes().get(i), i);
        }
    }

    public int getTotalScriptLength() {
        return totalScriptLength;
    }

    /**
     * Go through the script and remove comma spacing and comments, add spaces before unary operators,
     * and convert names into ids. This simplifies the main parsing task.
     *
     * @param script The script to pre-parse
     */
    private void preParse(String script) {
        script = script.replaceAll(", +", ","); // Remove commas after spaces
        script = script.replaceAll(" +//.*", ""); // Remove comments
        script = script.replaceAll("(\\+\\+|--)", " $1"); // Add a space before unary operators

        // Convert map and item names to their ids
        Pattern p = Pattern.compile("<[a-zA-Z0-9 ()']+>");
        Matcher m = p.matcher(script);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            if (mapIds.containsKey(m.group())) {
                m.appendReplacement(sb, String.valueOf(mapIds.get(m.group())));
            } else if (itemIds.containsKey(m.group())) {
                m.appendReplacement(sb, String.valueOf(itemIds.get(m.group())));
            }
        }
        m.appendTail(sb);
        scanner = new Scanner(sb.toString());
    }

    /**
     * The main parsing method. Steps through the script and starts the recursive script parser for each map header.
     *
     * @return A list of parsed map headers
     */
    public List<ParsedMapHeader> parse() {
        for (int i = 0; i < 3; i++) {
            scanner.nextLine();
        }
        byte[] attributeBytes = new byte[256];
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (!line.isEmpty() && !line.startsWith("var")) {
                if (line.contains("=")) {
                    // Attributes
                    String[] split = line.split(" *= *");
                    int index = attributes.get(split[0]);
                    attributeBytes[index] = Byte.parseByte(split[1]);
                } else {
                    String[] split = line.split(" +"); // Split to check for (Execution starts...
                    currentHeader = new ParsedMapHeader(split[0]);
                    currentHeader.setAttributeBytes(attributeBytes);
                    attributeBytes = new byte[256];
                    parsedHeaders.add(currentHeader);
                    // If split is this long, it has an (Execution starts...
                    if (split.length >= 5) {
                        int label = parseLabel(split[4].substring(0, split[4].length() - 1), false, false);
                        currentHeader.setScriptPC(label);
                    }
                    // Start the recursive script parser for this header
                    parseBlock();

                    // Fill in empty labels with the position of the label marker
                    for (int label : labels.keySet()) {
                        List<Integer> labelPositions = labels.get(label);
                        byte[] array = Utils.shortToByteArray(labelPositions.getFirst().shortValue(), true);
                        for (int i = 1; i < labelPositions.size(); i++) {
                            int position = labelPositions.get(i);
                            currentScriptBytes.set(position, array[0]);
                            currentScriptBytes.set(position + 1, array[1]);
                        }
                    }

                    // Copy script byte list into a byte array
                    byte[] scriptBytesArray = new byte[currentScriptBytes.size()];
                    for (int i = 0; i < currentScriptBytes.size(); i++) {
                        scriptBytesArray[i] = currentScriptBytes.get(i);
                    }
                    // Give the parsed header a finished script and the current script offset
                    currentHeader.setScriptBytes(scriptBytesArray);
                    currentHeader.setScriptDataOffset(totalScriptLength);
                    totalScriptLength += currentScriptBytes.size();

                    // Reset for next header
                    labels.clear();
                    currentScriptBytes.clear();
                    pos = 0;
                }
            }
        }
        scanner.close();
        return parsedHeaders;
    }

    private void parseBlock() {
        scanner.nextLine();
        String line = scanner.nextLine().trim();
        while (!line.equals("}")) {
            if (!line.isEmpty()) {
                parseValue(line, ValueMode.MAIN);
            }
            line = scanner.nextLine().trim();
        }
    }

    private void parseValue(String value, ValueMode mode) {
        String[] valueSplit = value.split(" +");
        if (value.startsWith("#")) {
            parseLabel(value, false, true);
        } else if (value.matches("[@a-zA-Z0-9]+\\(.*")) {
            // Task, multitask, or function
            parseTask(value, true);
        } else if (valueSplit.length > 1 && valueSplit[1].equals("<ScriptRv>")) {
            addByte(30);
            addByte(1);
            parseBlock();
        } else if (valueSplit[0].equals("if")) {
            // If
            addByte(3);
            parseIf(valueSplit);
        } else if (valueSplit[0].equals("Goto")) {
            // Goto
            addByte(4);
            if (valueSplit.length == 1) {
                addInt(0, false);
            } else {
                parseLabel(valueSplit[1], true, true);
            }
        } else if (valueSplit[0].equals("End")) {
            // End
            addByte(5);
            if (valueSplit.length == 1) {
                addInt(0, false);
            } else {
                parseLabel(valueSplit[1], true, true);
            }
        } else if (flagIds.containsKey(valueSplit[0])) {
            // Flag
            addByte(6);
            addShort(flagIds.get(valueSplit[0]), true);
            switch (mode) {
                case MAIN -> parseFormula(value.substring(valueSplit[0].length()).trim());
                case LHS, RHS -> parseOperator((valueSplit.length > 1) ? valueSplit[1] : "");
                case PARAMETER -> addShort(0, false);
            }
        } else if (value.matches("\".*\"") && !DIALOGUE_FUNCTIONS.contains(currentTask)) {
            addByte(21);
            String str = value.substring(value.indexOf("\"") + 1, value.lastIndexOf("\""));
            addInt(currentHeader.addString(str), true);
        } else if (value.matches("\".*\"|[-0-9]+")) {
            // Numeric
            if (mode == ValueMode.PARAMETER || mode == ValueMode.RHS) {
                if (currentTask != null && (currentTask.equals("TestGlobalFlag") || currentTask.equals("SetGlobalFlag") ||
                        currentTask.equals("ResetGlobalFlag"))) {
                    addByte(22);
                } else {
                    addByte(7);
                }
                if (value.matches("\".*\"")) {
                    addString(value.substring(1, value.length() - 1));
                } else {
                    addInt(Integer.parseInt(value), true);
                }
            } else {
                addByte(7);
                addInt(Integer.parseInt(value), true);
            }
        } else if (value.startsWith("var")) {
            // Local Variable
            addByte(10);
            addByte(Integer.parseInt(valueSplit[0].substring(3)));
            switch (mode) {
                case MAIN -> parseFormula(value.substring(valueSplit[0].length()).trim());
                case LHS, RHS -> parseOperator(value.substring(valueSplit[0].length()).trim());
                case PARAMETER -> {
                    addByte(0);
                    addByte(0);
                    addByte(0);
                }
            }
        } else if (valueSplit[0].equals("Gosub")) {
            addByte(17);
            parseLabel(valueSplit[1], true, true);
        } else if (value.equals("Return")) {
            addByte(18);
        } else if (value.equals("Endint")) {
            addByte(19);
        } else if (value.startsWith("<Anchor>=")) {
            String[] equalSplit = value.split("=");
            addByte(Byte.parseByte(equalSplit[1]));
        } else if (value.startsWith("<TaskPause")) {
            addByte(27);
            String[] parenSplit = value.split("\\(");
            String numStr = parenSplit[1].substring(0, parenSplit[1].length() - 2);
            parseLabel(numStr, true, true);
        } else {
            // Object-Dot
            String[] dotSplit = value.split("\\.");
            int parenIndex = dotSplit[1].indexOf("(");
            if (parenIndex > 0) {
                // Object-Dot-Task
                String name = dotSplit[1].substring(0, parenIndex);
                if (name.charAt(0) == '@') {
                    name = name.substring(1);
                }
                if (functionIds.containsKey(name)) {
                    if (mapDatabase.getFunctions().get(functionIds.get(name)).getType().equals("function")) {
                        addByte(26);
                    } else {
                        addByte(25);
                    }
                    parseObjectName(dotSplit[0]);
                    if (mode == ValueMode.MAIN) {
                        parseValue(value.substring(dotSplit[0].length() + 1), ValueMode.REFERENCE);
                    } else {
                        parseTask(value.substring(dotSplit[0].length() + 1), false);
                    }
                }
            } else {
                // Object-Dot Only
                addByte(20);
                parseObjectName(dotSplit[0]);
                parseReferenceName(valueSplit[0].substring(dotSplit[0].length() + 1));
                if (mode == ValueMode.MAIN) {
                    parseFormula(value.substring(valueSplit[0].length()).trim());
                } else if (mode == ValueMode.LHS) {
                    parseOperator(value.substring(valueSplit[0].length()).trim());
                }
            }
        }
    }

    private void parseTask(String line, boolean writeBytes) {
        String[] split = line.split("\\(");
        currentTask = split[0];
        boolean multitask = false;
        if (currentTask.charAt(0) == '@') {
            currentTask = currentTask.substring(1);
            multitask = true;
        }
        int functionID = functionIds.get(currentTask);
        SoupFunction function = mapDatabase.getFunctions().get(functionID);

        if (writeBytes) {
            if (multitask) {
                addByte(1);
            } else {
                addByte(function.getType().equals("task") ? 0 : 2);
            }
        }
        addShort(functionID, true);

        int paramNum = function.getParamCount();
        addByte(paramNum);
        if (paramNum > 0) {
            String[] params = split[1].substring(0, split[1].length() - 1).split(",");
            for (int i = 0; i < paramNum; i++) {
                parseValue(params[i], ValueMode.PARAMETER);
            }
        }
        currentTask = null;
    }

    private void parseIf(String[] lineSplit) {
        int conjunction;
        int counter = 1;
        do {
            conjunction = 0;
            parseValue(lineSplit[counter], ValueMode.LHS);
            counter++;
            addByte(COMPARISON_VALUES.get(lineSplit[counter]));
            counter++;
            parseValue(lineSplit[counter], ValueMode.RHS);
            counter++;
            if (lineSplit.length > counter) {
                if (lineSplit[counter].equals("and")) {
                    conjunction = 1;
                    addByte(1);
                } else {
                    conjunction = 2;
                    addByte(2);
                }
                counter++;
            } else {
                addByte(0);
            }
        } while (conjunction != 0);

        int arrayPos = pos;
        addInt(0, false);
        parseBlock();
        byte[] lengthBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(pos).array();
        for (int i = 0; i < lengthBytes.length; i++) {
            currentScriptBytes.set(arrayPos + i, lengthBytes[i]);
        }
    }

    private int parseLabel(String label, boolean writeBytes, boolean savePos) {
        int endIndex = label.indexOf(":");
        int labelNum = Integer.parseInt(label.substring(1, (endIndex > 0) ? endIndex : label.length()), 16);
        if (!labels.containsKey(labelNum)) {
            labels.put(labelNum, new ArrayList<>());
        }
        if (savePos) {
            if (endIndex > 0) {
                labels.get(labelNum).addFirst(pos);
            } else {
                labels.get(labelNum).add(pos);
            }
        }
        if (writeBytes) {
            addInt(0, false);
        }
        return labelNum;
    }

    private void parseFormula(String line) {
        String[] lineSplit = line.split(" +");
        int counter = 1;
        do {
            parseValue(lineSplit[counter++], ValueMode.FORMULA);
        } while (parseOperator((counter < lineSplit.length) ? lineSplit[counter++] : ""));
    }

    private boolean parseOperator(String line) {
        boolean wantsValue = false;
        int operator = OPERATOR_VALUES.get(line);
        addByte(operator);
        if (operator == 10 || operator == 11) {
            addByte(0);
        }
        if (operator > 0 && operator < 10) {
            wantsValue = true;
        }
        return wantsValue;
    }

    private void parseObjectName(String line) {
        if (OBJECT_NAME_VALUES.containsKey(line)) {
            addByte(OBJECT_NAME_VALUES.get(line));
            addByte(0);
        } else if (line.matches("[a-z0-9._]+")) {
            addByte(4);
            addByte(currentHeader.addString(line));
        } else {
            addByte(10);
        }
    }

    private void parseReferenceName(String line) {
        addShort(references.get(line), true);
    }

    private void addString(String str) {
        pos += 4;
        byte[] array = str.getBytes();
        for (byte b : array) {
            currentScriptBytes.add(b);
        }
    }

    private void addInt(int num, boolean littleEndian) {
        pos += 4;
        byte[] array = ByteBuffer.allocate(4).order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN).putInt(num).array();
        for (byte b : array) {
            currentScriptBytes.add(b);
        }
    }

    private void addShort(int num, boolean littleEndian) {
        pos += 2;
        byte[] array = ByteBuffer.allocate(2).order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN).putShort((short) num).array();
        for (byte b : array) {
            currentScriptBytes.add(b);
        }
    }

    private void addByte(int num) {
        pos += 1;
        currentScriptBytes.add((byte) num);
    }
}