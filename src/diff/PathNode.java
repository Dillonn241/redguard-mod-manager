package diff;

public record PathNode(int x, int y, diff.PathNode prev) {
    public enum Direction {
        NONE, HORIZONTAL, VERTICAL, DIAGONAL
    }

    public Direction getDirection() {
        if (prev == null) {
            return Direction.NONE;
        }
        if (prev.x == x - 1 && prev.y == y) {
            return Direction.HORIZONTAL;
        }
        if (prev.x == x && prev.y == y - 1) {
            return Direction.VERTICAL;
        }
        return Direction.DIAGONAL;
    }
}