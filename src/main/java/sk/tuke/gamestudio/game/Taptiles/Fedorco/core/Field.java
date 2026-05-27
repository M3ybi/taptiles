package sk.tuke.gamestudio.game.Taptiles.Fedorco.core;

import java.util.*;

public class Field {
    public enum LayoutDifficulty {
        EASY,
        NORMAL,
        HARD
    }

    private static final int MAX_GENERATION_ATTEMPTS = 5000;
    private static final int[][] DIRECTIONS = new int[][]{{-1, 0}, {0, 1}, {1, 0}, {0, -1}};
    private final Tile[][] tiles;
    private int rowCount;
    private int columnCount;
    private LayoutDifficulty layoutDifficulty;
    private Random random;
    public GameState state = GameState.PLAYING;
    private int chooseRow;
    private int chooseColumn;
    private int connectRow;
    private int connectColumn;
    public static final String GAME_NAME = "TapTiles";

    public void setState(GameState state) {
        this.state = state;
    }

    public Tile[][] getTiles() {
        return tiles;
    }

    public Field(int rowCount, int columnCount) {
        this(rowCount, columnCount, LayoutDifficulty.NORMAL);
    }

    public Field(int rowCount, int columnCount, LayoutDifficulty layoutDifficulty) {
        this(rowCount, columnCount, layoutDifficulty, System.nanoTime());
    }

    public Field(int rowCount, int columnCount, LayoutDifficulty layoutDifficulty, long seed) {
        this(rowCount, columnCount, true, layoutDifficulty, seed);
    }

    private Field(int rowCount, int columnCount, boolean generate) {
        this(rowCount, columnCount, generate, LayoutDifficulty.NORMAL, System.nanoTime());
    }

    private Field(int rowCount, int columnCount, boolean generate, LayoutDifficulty layoutDifficulty, long seed) {
        this.rowCount = rowCount;
        this.columnCount = columnCount;
        this.layoutDifficulty = layoutDifficulty;
        this.random = new Random(seed);
        this.tiles = new Tile[rowCount][columnCount];
        if (generate) {
            generate();
        }
    }

    private void generateField() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            setState(TileState.CLOSED);
            placeLayeredSolvablePairs();
            if (!hasAdjacentMatchingOpenPair() && isGeneratedLayoutDifficultEnough() && canBeSolvedByGreedySearch()) {
                return;
            }
        }

        setState(TileState.CLOSED);
        placeAdjacentSolvablePairs();
    }

    private boolean isGeneratedLayoutDifficultEnough() {
        return layoutDifficulty != LayoutDifficulty.HARD || !hasStraightConnectablePair();
    }

    private void setState(TileState state) {
        for (int row = 0; row < getRowCount(); row++) {
            for (int column = 0; column < getColumnCount(); column++) {
                tiles[row][column] = new Numbers(0);
                tiles[row][column].setState(state);
            }
        }
    }

    private List<Integer> generatePairValues() {
        List<Integer> values = new ArrayList<>();
        int pairCount = getPlayableTileCount() / 2;
        while (values.size() < pairCount) {
            int num = random.nextInt(10);
            values.add(num);
        }
        return values;
    }

    private void placeLayeredSolvablePairs() {
        int layers = Math.min(getRowCount() - 2, getColumnCount() - 2) / 2;
        for (int layer = 0; layer < layers; layer++) {
            List<int[][]> tilePairs = pairNonAdjacentPositions(getLayerPositions(layer));
            Collections.shuffle(tilePairs, random);
            for (int[][] pair : tilePairs) {
                int value = randomValueAwayFromOpenNeighbors(pair);
                placeOpenNumber(pair[0][0], pair[0][1], value);
                placeOpenNumber(pair[1][0], pair[1][1], value);
            }
        }
    }

    private List<int[]> getLayerPositions(int layer) {
        List<int[]> positions = new ArrayList<>();
        int top = 1 + layer;
        int bottom = getRowCount() - 2 - layer;
        int left = 1 + layer;
        int right = getColumnCount() - 2 - layer;

        for (int column = left; column <= right; column++) {
            positions.add(new int[]{top, column});
        }
        for (int row = top + 1; row <= bottom - 1; row++) {
            positions.add(new int[]{row, right});
        }
        if (bottom != top) {
            for (int column = right; column >= left; column--) {
                positions.add(new int[]{bottom, column});
            }
        }
        if (left != right) {
            for (int row = bottom - 1; row >= top + 1; row--) {
                positions.add(new int[]{row, left});
            }
        }

        Collections.shuffle(positions, random);
        return positions;
    }

    private List<int[][]> pairNonAdjacentPositions(List<int[]> positions) {
        List<int[][]> pairs = new ArrayList<>();
        while (!positions.isEmpty()) {
            int[] first = positions.remove(0);
            int matchIndex = findNonAdjacentPosition(first, positions);
            int[] second = positions.remove(matchIndex);
            pairs.add(new int[][]{first, second});
        }
        return pairs;
    }

    private int findNonAdjacentPosition(int[] first, List<int[]> positions) {
        for (int index = 0; index < positions.size(); index++) {
            if (!areAdjacent(first, positions.get(index))) {
                return index;
            }
        }
        return 0;
    }

    private boolean areAdjacent(int[] first, int[] second) {
        return Math.abs(first[0] - second[0]) + Math.abs(first[1] - second[1]) == 1;
    }

    private int randomValueAwayFromOpenNeighbors(int[][] pair) {
        List<Integer> values = new ArrayList<>();
        for (int value = 0; value <= 9; value++) {
            if (!hasOpenNeighborWithValue(pair[0][0], pair[0][1], value)
                    && !hasOpenNeighborWithValue(pair[1][0], pair[1][1], value)) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            return random.nextInt(10);
        }
        return values.get(random.nextInt(values.size()));
    }

    private boolean hasOpenNeighborWithValue(int row, int column, int value) {
        return matchesOpenNeighbor(row - 1, column, value)
                || matchesOpenNeighbor(row + 1, column, value)
                || matchesOpenNeighbor(row, column - 1, value)
                || matchesOpenNeighbor(row, column + 1, value);
    }

    private boolean matchesOpenNeighbor(int row, int column, int value) {
        if (!isInsidePlayableBoard(row, column)) {
            return false;
        }
        Tile tile = getTile(row, column);
        return tile.getState() == TileState.OPEN && ((Numbers) tile).getValue() == value;
    }

    private void placeAdjacentSolvablePairs() {
        List<int[][]> tilePairs = getAdjacentTilePairs();
        List<Integer> pairValues = generatePairValues();
        Collections.shuffle(pairValues, random);
        Collections.shuffle(tilePairs, random);

        for (int index = 0; index < tilePairs.size(); index++) {
            int value = pairValues.get(index);
            int[][] pair = tilePairs.get(index);
            placeOpenNumber(pair[0][0], pair[0][1], value);
            placeOpenNumber(pair[1][0], pair[1][1], value);
        }
    }

    private List<int[]> getPlayablePositions() {
        List<int[]> positions = new ArrayList<>();
        for (int row = 1; row < getRowCount() - 1; row++) {
            for (int column = 1; column < getColumnCount() - 1; column++) {
                positions.add(new int[]{row, column});
            }
        }
        return positions;
    }

    private List<int[][]> getAdjacentTilePairs() {
        List<int[][]> pairs = new ArrayList<>();
        int playableRows = getRowCount() - 2;
        int playableColumns = getColumnCount() - 2;

        if (playableColumns % 2 == 0) {
            for (int row = 1; row < getRowCount() - 1; row++) {
                for (int column = 1; column < getColumnCount() - 1; column += 2) {
                    pairs.add(new int[][]{{row, column}, {row, column + 1}});
                }
            }
        } else if (playableRows % 2 == 0) {
            for (int row = 1; row < getRowCount() - 1; row += 2) {
                for (int column = 1; column < getColumnCount() - 1; column++) {
                    pairs.add(new int[][]{{row, column}, {row + 1, column}});
                }
            }
        }

        return pairs;
    }

    private void placeOpenNumber(int row, int column, int value) {
        tiles[row][column] = new Numbers(value);
        tiles[row][column].setState(TileState.OPEN);
    }

    private int getPlayableTileCount() {
        return (getColumnCount() - 2) * (getRowCount() - 2);
    }

    public boolean hasAdjacentMatchingOpenPair() {
        for (int row = 1; row < getRowCount() - 1; row++) {
            for (int column = 1; column < getColumnCount() - 1; column++) {
                if (isMatchingOpenPair(row, column, row, column + 1)
                        || isMatchingOpenPair(row, column, row + 1, column)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isMatchingOpenPair(int firstRow, int firstColumn, int secondRow, int secondColumn) {
        if (!isInsidePlayableBoard(secondRow, secondColumn)) {
            return false;
        }
        Tile firstTile = getTile(firstRow, firstColumn);
        Tile secondTile = getTile(secondRow, secondColumn);
        return firstTile.getState() == TileState.OPEN
                && secondTile.getState() == TileState.OPEN
                && ((Numbers) firstTile).getValue() == ((Numbers) secondTile).getValue();
    }

    private boolean isInsidePlayableBoard(int row, int column) {
        return row > 0 && row < getRowCount() - 1 && column > 0 && column < getColumnCount() - 1;
    }

    public boolean canBeSolvedByGreedySearch() {
        Field solver = copyForSolving();
        int openTileCount = solver.getOpenTileCount();

        while (openTileCount > 1) {
            if (!solver.connectFirstAvailablePair()) {
                return false;
            }
            openTileCount -= 2;
        }

        return solver.getState() == GameState.SOLVED || solver.isSolved();
    }

    public boolean canConnectTiles(int firstRow, int firstColumn, int secondRow, int secondColumn) {
        return findConnectionPath(firstRow, firstColumn, secondRow, secondColumn) != null;
    }

    public int[][] findConnectionPath(int firstRow, int firstColumn, int secondRow, int secondColumn) {
        if (!canStartPath(firstRow, firstColumn, secondRow, secondColumn)) {
            return null;
        }

        int[][][] bestTurns = new int[getRowCount()][getColumnCount()][DIRECTIONS.length];
        int[][][] parentRow = new int[getRowCount()][getColumnCount()][DIRECTIONS.length];
        int[][][] parentColumn = new int[getRowCount()][getColumnCount()][DIRECTIONS.length];
        int[][][] parentDirection = new int[getRowCount()][getColumnCount()][DIRECTIONS.length];
        for (int row = 0; row < getRowCount(); row++) {
            for (int column = 0; column < getColumnCount(); column++) {
                Arrays.fill(bestTurns[row][column], Integer.MAX_VALUE);
                Arrays.fill(parentRow[row][column], -1);
                Arrays.fill(parentColumn[row][column], -1);
                Arrays.fill(parentDirection[row][column], -1);
            }
        }

        Deque<int[]> queue = new ArrayDeque<>();
        for (int direction = 0; direction < DIRECTIONS.length; direction++) {
            int nextRow = firstRow + DIRECTIONS[direction][0];
            int nextColumn = firstColumn + DIRECTIONS[direction][1];
            if (canPathPass(nextRow, nextColumn, firstRow, firstColumn, secondRow, secondColumn)) {
                bestTurns[nextRow][nextColumn][direction] = 0;
                parentRow[nextRow][nextColumn][direction] = firstRow;
                parentColumn[nextRow][nextColumn][direction] = firstColumn;
                parentDirection[nextRow][nextColumn][direction] = -1;
                queue.add(new int[]{nextRow, nextColumn, direction, 0});
            }
        }

        while (!queue.isEmpty()) {
            int[] state = queue.removeFirst();
            int currentRow = state[0];
            int currentColumn = state[1];
            int currentDirection = state[2];
            int turns = state[3];

            if (currentRow == secondRow && currentColumn == secondColumn) {
                return compressPath(reconstructPath(parentRow, parentColumn, parentDirection,
                        currentRow, currentColumn, currentDirection));
            }

            for (int nextDirection = 0; nextDirection < DIRECTIONS.length; nextDirection++) {
                int nextTurns = turns + (nextDirection == currentDirection ? 0 : 1);
                if (nextTurns > 2) {
                    continue;
                }
                int nextRow = currentRow + DIRECTIONS[nextDirection][0];
                int nextColumn = currentColumn + DIRECTIONS[nextDirection][1];
                if (!canPathPass(nextRow, nextColumn, firstRow, firstColumn, secondRow, secondColumn)
                        || nextTurns >= bestTurns[nextRow][nextColumn][nextDirection]) {
                    continue;
                }
                bestTurns[nextRow][nextColumn][nextDirection] = nextTurns;
                parentRow[nextRow][nextColumn][nextDirection] = currentRow;
                parentColumn[nextRow][nextColumn][nextDirection] = currentColumn;
                parentDirection[nextRow][nextColumn][nextDirection] = currentDirection;
                queue.add(new int[]{nextRow, nextColumn, nextDirection, nextTurns});
            }
        }

        return null;
    }

    private boolean canStartPath(int firstRow, int firstColumn, int secondRow, int secondColumn) {
        if (!isInsidePlayableBoard(firstRow, firstColumn) || !isInsidePlayableBoard(secondRow, secondColumn)) {
            return false;
        }
        if (firstRow == secondRow && firstColumn == secondColumn) {
            return false;
        }

        Tile firstTile = getTile(firstRow, firstColumn);
        Tile secondTile = getTile(secondRow, secondColumn);
        return firstTile.getState() != TileState.CLOSED
                && secondTile.getState() != TileState.CLOSED
                && ((Numbers) firstTile).getValue() == ((Numbers) secondTile).getValue();
    }

    private boolean canPathPass(int row, int column, int firstRow, int firstColumn, int secondRow, int secondColumn) {
        if (row < 0 || row >= getRowCount() || column < 0 || column >= getColumnCount()) {
            return false;
        }
        if ((row == firstRow && column == firstColumn) || (row == secondRow && column == secondColumn)) {
            return true;
        }
        return getTile(row, column).getState() == TileState.CLOSED;
    }

    private int[][] reconstructPath(int[][][] parentRow, int[][][] parentColumn, int[][][] parentDirection,
                                    int row, int column, int direction) {
        List<int[]> points = new ArrayList<>();
        int currentRow = row;
        int currentColumn = column;
        int currentDirection = direction;
        while (currentDirection != -1) {
            points.add(new int[]{currentRow, currentColumn});
            int nextRow = parentRow[currentRow][currentColumn][currentDirection];
            int nextColumn = parentColumn[currentRow][currentColumn][currentDirection];
            int nextDirection = parentDirection[currentRow][currentColumn][currentDirection];
            currentRow = nextRow;
            currentColumn = nextColumn;
            currentDirection = nextDirection;
        }
        points.add(new int[]{currentRow, currentColumn});
        Collections.reverse(points);
        return points.toArray(new int[points.size()][]);
    }

    private int[][] compressPath(int[][] path) {
        if (path.length <= 2) {
            return path;
        }
        List<int[]> points = new ArrayList<>();
        points.add(path[0]);
        for (int index = 1; index < path.length - 1; index++) {
            int previousRowDirection = Integer.compare(path[index][0] - path[index - 1][0], 0);
            int previousColumnDirection = Integer.compare(path[index][1] - path[index - 1][1], 0);
            int nextRowDirection = Integer.compare(path[index + 1][0] - path[index][0], 0);
            int nextColumnDirection = Integer.compare(path[index + 1][1] - path[index][1], 0);
            if (previousRowDirection != nextRowDirection || previousColumnDirection != nextColumnDirection) {
                points.add(path[index]);
            }
        }
        points.add(path[path.length - 1]);
        return points.toArray(new int[points.size()][]);
    }

    private boolean hasStraightConnectablePair() {
        for (int firstRow = 1; firstRow < getRowCount() - 1; firstRow++) {
            for (int firstColumn = 1; firstColumn < getColumnCount() - 1; firstColumn++) {
                Tile firstTile = getTile(firstRow, firstColumn);
                if (firstTile.getState() == TileState.CLOSED) {
                    continue;
                }
                for (int secondRow = firstRow; secondRow < getRowCount() - 1; secondRow++) {
                    int startColumn = secondRow == firstRow ? firstColumn + 1 : 1;
                    for (int secondColumn = startColumn; secondColumn < getColumnCount() - 1; secondColumn++) {
                        int[][] path = findConnectionPath(firstRow, firstColumn, secondRow, secondColumn);
                        if (path != null && path.length == 2) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean hasConnectablePair() {
        return findFirstConnectablePair() != null;
    }

    public int[][] findFirstConnectablePair() {
        for (int firstRow = 1; firstRow < getRowCount() - 1; firstRow++) {
            for (int firstColumn = 1; firstColumn < getColumnCount() - 1; firstColumn++) {
                Tile firstTile = getTile(firstRow, firstColumn);
                if (firstTile.getState() == TileState.CLOSED) {
                    continue;
                }
                for (int secondRow = firstRow; secondRow < getRowCount() - 1; secondRow++) {
                    int startColumn = secondRow == firstRow ? firstColumn + 1 : 1;
                    for (int secondColumn = startColumn; secondColumn < getColumnCount() - 1; secondColumn++) {
                        if (canConnectTiles(firstRow, firstColumn, secondRow, secondColumn)) {
                            return new int[][]{{firstRow, firstColumn}, {secondRow, secondColumn}};
                        }
                    }
                }
            }
        }
        return null;
    }

    public boolean shuffleOpenTilesUntilConnectable() {
        List<int[]> positions = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (int row = 1; row < getRowCount() - 1; row++) {
            for (int column = 1; column < getColumnCount() - 1; column++) {
                Tile tile = getTile(row, column);
                if (tile.getState() == TileState.OPEN || tile.getState() == TileState.CHOOSED) {
                    positions.add(new int[]{row, column});
                    values.add(((Numbers) tile).getValue());
                }
            }
        }

        if (positions.size() <= 1) {
            return false;
        }

        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            Collections.shuffle(values, random);
            for (int index = 0; index < positions.size(); index++) {
                int[] position = positions.get(index);
                tiles[position[0]][position[1]] = new Numbers(values.get(index));
                tiles[position[0]][position[1]].setState(TileState.OPEN);
            }
            if (hasConnectablePair() && canBeSolvedByGreedySearch()) {
                return true;
            }
        }

        return false;
    }

    private Field copyForSolving() {
        Field copy = new Field(rowCount, columnCount, false);
        for (int row = 0; row < getRowCount(); row++) {
            for (int column = 0; column < getColumnCount(); column++) {
                Numbers sourceTile = (Numbers) getTile(row, column);
                copy.tiles[row][column] = new Numbers(sourceTile.getValue());
                copy.tiles[row][column].setState(sourceTile.getState());
            }
        }
        copy.state = state;
        return copy;
    }

    private int getOpenTileCount() {
        int count = 0;
        for (int row = 1; row < getRowCount() - 1; row++) {
            for (int column = 1; column < getColumnCount() - 1; column++) {
                Tile tile = getTile(row, column);
                if (tile.getState() == TileState.OPEN || tile.getState() == TileState.CHOOSED) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean connectFirstAvailablePair() {
        for (int firstRow = 1; firstRow < getRowCount() - 1; firstRow++) {
            for (int firstColumn = 1; firstColumn < getColumnCount() - 1; firstColumn++) {
                Tile firstTile = getTile(firstRow, firstColumn);
                if (firstTile.getState() != TileState.OPEN) {
                    continue;
                }

                for (int secondRow = firstRow; secondRow < getRowCount() - 1; secondRow++) {
                    int startColumn = secondRow == firstRow ? firstColumn + 1 : 1;
                    for (int secondColumn = startColumn; secondColumn < getColumnCount() - 1; secondColumn++) {
                        Tile secondTile = getTile(secondRow, secondColumn);
                        if (secondTile.getState() != TileState.OPEN
                                || ((Numbers) firstTile).getValue() != ((Numbers) secondTile).getValue()) {
                            continue;
                        }

                        chooseTile(firstRow, firstColumn);
                        if (connectTile(secondRow, secondColumn)) {
                            return true;
                        }
                        if (firstTile.getState() == TileState.CHOOSED) {
                            firstTile.setState(TileState.OPEN);
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean isSolved() {
        int openedTile = 0;
        for (int row = 1; row < getRowCount() - 1; row++) {
            for (int column = 1; column < getColumnCount() - 1; column++) {
                if ((tiles[row][column].getState() == TileState.OPEN) || ((tiles[row][column].getState() == TileState.CHOOSED))) {
                    openedTile++;
                }
            }
        }
        if (openedTile > 1) {
            return false;
        } else {
            return true;
        }
    }

    private boolean closeTiles(int row, int col) {
        if (tiles[row][col].getState() == TileState.CHOOSED) {
            tiles[row][col].setState(TileState.CLOSED);
            tiles[connectRow][connectColumn].setState(TileState.CLOSED);
            return true;
        }
        return false;
    }

    private boolean wrongInput() {
        if (tiles[chooseRow][chooseColumn] == null || tiles[connectRow][connectColumn] == null) {
            return true;
        }
        if (((Numbers) tiles[chooseRow][chooseColumn]).getValue() != ((Numbers) tiles[connectRow][connectColumn]).getValue()) {
            return true;
        } else {
            return false;
        }
    }

    private boolean findMainPath(int row, int column) {
        if (wrongInput()) {
            System.out.println("WRONG INPUT");
            return false;
        }
        if (findRightDOWN(row, column) || findUpRIGHT(row, column) || findUpLeft(row, column) || findDownLeft(row, column) || findDownRight(row, column)) {
            return true;
        }
        if (findLeftDOWN(row, column) || findLeftUP(row, column) || findRightUP(row, column)) {
            return true;
        }
        return false;
    }

    private boolean blockedPath(int row, int col) {
        if (tiles[row][col].getState() == TileState.OPEN) {
            if (row != chooseRow || col != chooseColumn) {
                return true;
            }
        }
        return false;
    }

    private boolean findLeftUP(int row, int column) {
        int conRow = row;
        int conCol = column;
        while (conCol != 0) {
            conCol--;
            if (blocked(conRow, conCol)) break;
            if (closeIt(closeTiles(conRow, conCol))) return true;
            int pomConCol = conCol;
            while (conRow != 0) {
                conRow--;
                if (blocked(conRow, pomConCol)) break;
                if (closeIt(closeTiles(conRow, pomConCol))) return true;
                while (pomConCol != 0) {
                    pomConCol--;
                    if (blocked(conRow, pomConCol)) break;
                    if (closeIt(closeTiles(conRow, pomConCol))) return true;
                }
                Boolean x = whileColLeftEdge(conRow, pomConCol);
                if (x != null) return x;
            }
        }
        return false;
    }

    private boolean findLeftDOWN(int row, int column) {
        int conRow = row;
        int conCol = column;
        while (conCol != 0) {
            conCol--;
            if (blocked(conRow, conCol)) break;
            if (closeIt(closeTiles(conRow, conCol))) return true;
            while (conRow != getRowCount() - 1) {
                int pomConCol = conCol;
                conRow++;
                if (blocked(conRow, pomConCol)) break;
                if (closeIt(closeTiles(conRow, pomConCol))) return true;
                while (pomConCol != 0) {
                    pomConCol--;
                    if (blocked(conRow, pomConCol)) break;
                    if (closeIt(closeTiles(conRow, pomConCol))) return true;
                }
                Boolean x = whileColLeftEdge(conRow, pomConCol);
                if (x != null) return x;
            }
        }
        return false;
    }

    private boolean findRightUP(int row, int column) {
        int conRow = row;
        int conCol = column;
        while (conCol != getColumnCount() - 1) {
            conCol++;
            if (blocked(conRow, conCol)) break;
            if (closeIt(closeTiles(conRow, conCol))) return true;
            while (conRow != 0) {
                int pomConCol = conCol;
                conRow--;
                if (blocked(conRow, pomConCol)) break;
                if (closeIt(closeTiles(conRow, pomConCol))) return true;
                while (pomConCol != 0) {
                    pomConCol--;
                    if (blocked(conRow, pomConCol)) break;
                    if (closeIt(closeTiles(conRow, pomConCol))) return true;
                }
                Boolean x = whileColLeftEdge(conRow, pomConCol);
                if (x != null) return x;
            }
        }
        return false;
    }

    private boolean findRightDOWN(int row, int column) {
        int conRow = row;
        int conCol = column;
        while (conCol != getColumnCount() - 1) {
            conCol++;
            if (blocked(conRow, conCol)) break;
            if (closeIt(closeTiles(conRow, conCol))) return true;
            while (conRow != getRowCount() - 1) {
                int pomConCol = conCol;
                conRow++;
                if (blocked(conRow, pomConCol)) break;
                if (closeIt(closeTiles(conRow, pomConCol))) return true;
                while (pomConCol != 0) {
                    pomConCol--;
                    if (blocked(conRow, pomConCol)) break;
                    if (closeIt(closeTiles(conRow, pomConCol))) return true;
                }
                Boolean x = whileColLeftEdge(conRow, pomConCol);
                if (x != null) return x;
            }
        }
        return false;
    }

    private boolean findDownLeft(int row, int column) {
        int conRow = row;
        int conCol = column;
        while (conRow != getRowCount() - 1) {
            conRow++;
            if (blocked(conRow, conCol)) break;
            if (closeIt(closeTiles(conRow, conCol))) return true;
            while (conCol != 0) {
                int pomConRow = conRow;
                conCol--;
                if (blocked(pomConRow, conCol)) break;
                if (closeIt(closeTiles(conRow, conCol))) return true;
                while (pomConRow != 0) {
                    pomConRow--;
                    if (blocked(pomConRow, conCol)) break;
                    if (closeIt(closeTiles(pomConRow, conCol))) return true;
                }
                Boolean x = whileRowLeftEdge(conCol, pomConRow);
                if (x != null) return x;
            }
        }
        return false;
    }

    private boolean findDownRight(int row, int column) {
        int conRow = row;
        int conCol = column;
        while (conRow != getRowCount() - 1) {
            conRow++;
            if (blocked(conRow, conCol)) break;
            if (closeIt(closeTiles(conRow, conCol))) return true;
            while (conCol != getColumnCount() - 1) {
                int pomConRow = conRow;
                conCol++;
                if (blocked(pomConRow, conCol)) break;
                if (closeIt(closeTiles(conRow, conCol))) return true;
                while (pomConRow != 0) {
                    pomConRow--;
                    if (blocked(pomConRow, conCol)) break;
                    if (closeIt(closeTiles(pomConRow, conCol))) return true;
                }
                Boolean x = whileRowLeftEdge(conCol, pomConRow);
                if (x != null) return x;
            }
        }
        return false;
    }

    private boolean findUpRIGHT(int row, int column) {
        int conRow = row;
        int conCol = column;
        while (conRow != 0) {
            conRow--;
            if (blocked(conRow, conCol)) break;
            if (closeIt(closeTiles(conRow, conCol))) return true;
            while (conCol != getColumnCount() - 1) {
                int pomConRow = conRow;
                conCol++;
                if (blocked(pomConRow, conCol)) break;
                if (closeIt(closeTiles(conRow, conCol))) return true;
                while (pomConRow != 0) {
                    pomConRow--;
                    if (blocked(pomConRow, conCol)) break;
                    if (closeIt(closeTiles(pomConRow, conCol))) return true;
                }
                Boolean x = whileRowLeftEdge(conCol, pomConRow);
                if (x != null) return x;
            }
        }
        return false;
    }

    private boolean findUpLeft(int row, int column) {
        int conRow = row;
        int conCol = column;
        while (conRow != 0) {
            conRow--;
            if (blocked(conRow, conCol)) break;
            if (closeIt(closeTiles(conRow, conCol))) return true;
            while (conCol != 0) {
                int pomConRow = conRow;
                conCol--;
                if (blocked(pomConRow, conCol)) break;
                if (closeIt(closeTiles(conRow, conCol))) return true;
                while (pomConRow != 0) {
                    pomConRow--;
                    if (blocked(pomConRow, conCol)) break;
                    if (closeIt(closeTiles(pomConRow, conCol))) return true;
                }
                Boolean x = whileRowLeftEdge(conCol, pomConRow);
                if (x != null) return x;
            }
        }
        return false;
    }

    private Boolean whileColLeftEdge(int conRow, int pomConCol) {
        while (pomConCol != getColumnCount() - 1) {
            pomConCol++;
            if (blocked(conRow, pomConCol)) break;
            if (closeIt(closeTiles(conRow, pomConCol))) return true;
        }
        return null;
    }

    private Boolean whileRowLeftEdge(int conCol, int pomConRow) {
        while (pomConRow != getColumnCount() - 1) {
            pomConRow++;
            if (blocked(pomConRow, conCol)) break;
            if (closeIt(closeTiles(pomConRow, conCol))) return true;
        }
        return null;
    }

    private boolean closeIt(boolean b) {
        if (b) {
            return true;
        }
        return false;
    }

    private boolean blocked(int conRow, int pomConCol) {
        if (closeIt(blockedPath(conRow, pomConCol))) return true;
        return false;
    }

    public boolean connectTile(int row, int column) {
        connectRow = row;
        connectColumn = column;
        final Tile tile = tiles[connectRow][connectColumn];
        if (state == GameState.PLAYING) {
            if (tile.getState() == TileState.CHOOSED) {
                tile.setState(TileState.OPEN);
            }
            boolean connected = findConnectionPath(chooseRow, chooseColumn, connectRow, connectColumn) != null
                    && closeTiles(chooseRow, chooseColumn);
            if (connected && isSolved()) {
                state = GameState.SOLVED;
            }
            return connected;
        }
        return false;
    }

    public void chooseTile(int row, int column) {
        chooseRow = row;
        chooseColumn = column;
        if (state == GameState.PLAYING) {
            final Tile tile = tiles[chooseRow][chooseColumn];
            if (tile.getState() == TileState.CHOOSED) {
                tile.setState(TileState.OPEN);
            } else if (tile.getState() == TileState.OPEN) {
                tile.setState(TileState.CHOOSED);
            }
        }
    }

    public GameState getState() {
        return state;
    }

    public Tile getTile(int row, int column) {
        return tiles[row][column];
    }

    public int getRowCount() {
        return rowCount;
    }

    private void generate() {
        if (getPlayableTileCount() % 2 != 0) {
            System.out.println(getColumnCount());
            System.out.println(getRowCount());
            System.out.println("NESPRAVNY POCET DLAZDIC (Can not divide by 2)");
            return;
        }
        generateField();
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public int getColumnCount() {
        return columnCount;
    }

    public void setColumnCount(int columnCount) {
        this.columnCount = columnCount;
    }
}
