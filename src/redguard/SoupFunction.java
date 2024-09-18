package redguard;

public class SoupFunction {
    private final String type, name;
    private final int paramCount;

    public SoupFunction(String line) {
        String[] split = line.split("\\s+");
        type = split[0];
        name = split[1];
        paramCount = Integer.parseInt(split[3]);
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public int getParamCount() {
        return paramCount;
    }
}