package diff;

import java.util.*;

public class Diff {
    public static Map<Integer, List<String>> diff(String[] originalLines, String[] modifiedLines) {
        PathNode path = findPath(originalLines, modifiedLines);
        return formattedDiff(path, modifiedLines);
    }

    private static PathNode findPath(String[] originalLines, String[] modifiedLines) {
        int n = originalLines.length;
        int m = modifiedLines.length;

        int max = n + m + 1;
        int size = 1 + 2 * max;
        int middle = size / 2;
        PathNode[] diagonal = new PathNode[size];

        diagonal[middle + 1] = new PathNode(0, -1, null);
        for (int d = 0; d < max; d++) {
            for (int k = -d; k <= d; k += 2) {
                int kmiddle = middle + k;
                int kplus = kmiddle + 1;
                int kminus = kmiddle - 1;
                PathNode prev;
                int x;

                if (k == -d || (k != d && diagonal[kminus].x() < diagonal[kplus].x())) {
                    x = diagonal[kplus].x();
                    prev = diagonal[kplus];
                } else {
                    x = diagonal[kminus].x() + 1;
                    prev = diagonal[kminus];
                }
                diagonal[kminus] = null;

                int y = x - k;
                PathNode node = new PathNode(x, y, prev);

                while (x < n && y < m && originalLines[x].equals(modifiedLines[y])) {
                    x++;
                    y++;
                }

                if (x != node.x()) {
                    node = new PathNode(x, y, node);
                }

                diagonal[kmiddle] = node;

                if (x >= n && y >= m) {
                    return diagonal[kmiddle];
                }
            }
            diagonal[middle + d - 1] = null;
        }
        return null;
    }

    private static Map<Integer, List<String>> formattedDiff(PathNode path, String[] modifiedLines) {
        Map<Integer, List<String>> lineChanges = new TreeMap<>();

        while (path != null && path.prev() != null && path.prev().y() >= 0) {
            int x = path.x();
            int y = path.y();

            switch (path.getDirection()) {
                case HORIZONTAL -> {
                    if (!lineChanges.containsKey(x - 1)) {
                        lineChanges.put(x - 1, new LinkedList<>());
                    }
                    lineChanges.get(x - 1).addFirst(null);
                }
                case VERTICAL -> {
                    if (!lineChanges.containsKey(x - 1)) {
                        lineChanges.put(x - 1, new LinkedList<>());
                    }
                    lineChanges.get(x - 1).addFirst(modifiedLines[y - 1]);
                }
            }

            path = path.prev();
        }
        return lineChanges;
    }
}