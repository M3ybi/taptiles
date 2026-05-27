package sk.tuke.gamestudio.game.Taptiles.Fedorco.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FieldConnectionTest {
    @Test
    public void generatedSixBySixVisibleBoardIsSolvable() {
        assertGeneratedBoardCanBeSolvedAndScattered(new Field(8, 8));
    }

    @Test
    public void generatedEightByEightVisibleBoardIsSolvable() {
        assertGeneratedBoardCanBeSolvedAndScattered(new Field(10, 10));
    }

    @Test
    public void generatedTenByTenVisibleBoardIsSolvable() {
        assertGeneratedBoardCanBeSolvedAndScattered(new Field(12, 12));
    }

    @Test
    public void easyGeneratedBoardIsSolvableAndStillScattered() {
        Field field = new Field(8, 8, Field.LayoutDifficulty.EASY);

        assertFalse("Easy boards should not be made from obvious adjacent pairs.",
                field.hasAdjacentMatchingOpenPair());
        assertTrue("Easy boards should still be solvable.", field.canBeSolvedByGreedySearch());
    }

    @Test
    public void sameSeedGeneratesSameBoard() {
        Field first = new Field(8, 8, Field.LayoutDifficulty.NORMAL, 12345L);
        Field second = new Field(8, 8, Field.LayoutDifficulty.NORMAL, 12345L);

        for (int row = 1; row < first.getRowCount() - 1; row++) {
            for (int column = 1; column < first.getColumnCount() - 1; column++) {
                assertEquals(((Numbers) first.getTile(row, column)).getValue(),
                        ((Numbers) second.getTile(row, column)).getValue());
            }
        }
    }

    @Test
    public void connectTileClosesMatchingAdjacentTiles() {
        Field field = fieldWithTwoOpenTiles(4, 4);

        field.chooseTile(1, 1);
        boolean connected = field.connectTile(1, 2);

        assertTrue(connected);
        assertEquals(TileState.CLOSED, field.getTile(1, 1).getState());
        assertEquals(TileState.CLOSED, field.getTile(1, 2).getState());
        assertEquals(GameState.SOLVED, field.getState());
    }

    @Test
    public void connectTileLeavesWrongSecondClickOpen() {
        Field field = fieldWithTwoOpenTiles(4, 7);

        field.chooseTile(1, 1);
        boolean connected = field.connectTile(1, 2);

        assertFalse(connected);
        assertEquals(TileState.CHOOSED, field.getTile(1, 1).getState());
        assertEquals(TileState.OPEN, field.getTile(1, 2).getState());
        assertEquals(GameState.PLAYING, field.getState());
    }

    @Test
    public void chooseTileDoesNotSolveSingleRemainingTile() {
        Field field = fieldWithOneOpenTile(4);

        field.chooseTile(1, 1);

        assertEquals(TileState.CHOOSED, field.getTile(1, 1).getState());
        assertEquals(GameState.PLAYING, field.getState());
    }

    @Test
    public void canConnectTilesReportsConnectionWithoutChangingBoard() {
        Field field = fieldWithTwoOpenTiles(4, 4);

        assertTrue(field.canConnectTiles(1, 1, 1, 2));
        assertEquals(TileState.OPEN, field.getTile(1, 1).getState());
        assertEquals(TileState.OPEN, field.getTile(1, 2).getState());
        assertEquals(GameState.PLAYING, field.getState());
    }

    @Test
    public void hasConnectablePairDetectsAvailableMove() {
        Field field = fieldWithTwoOpenTiles(4, 4);

        assertTrue(field.hasConnectablePair());
    }

    @Test
    public void findFirstConnectablePairReturnsMoveWithoutChangingBoard() {
        Field field = fieldWithTwoOpenTiles(4, 4);

        int[][] pair = field.findFirstConnectablePair();

        assertNotNull(pair);
        assertEquals(1, pair[0][0]);
        assertEquals(1, pair[0][1]);
        assertEquals(1, pair[1][0]);
        assertEquals(2, pair[1][1]);
        assertEquals(TileState.OPEN, field.getTile(1, 1).getState());
        assertEquals(TileState.OPEN, field.getTile(1, 2).getState());
        assertEquals(GameState.PLAYING, field.getState());
    }

    @Test
    public void findConnectionPathReturnsRouteBendsWithoutChangingBoard() {
        Field field = fieldWithTwoOpenTilesAt(4, 1, 1, 4, 2, 2);

        int[][] path = field.findConnectionPath(1, 1, 2, 2);

        assertNotNull(path);
        assertEquals(3, path.length);
        assertEquals(1, path[0][0]);
        assertEquals(1, path[0][1]);
        assertEquals(2, path[2][0]);
        assertEquals(2, path[2][1]);
        assertEquals(TileState.OPEN, field.getTile(1, 1).getState());
        assertEquals(TileState.OPEN, field.getTile(2, 2).getState());
    }

    @Test
    public void hasConnectablePairReturnsFalseWhenNoValuesMatch() {
        Field field = fieldWithTwoOpenTiles(4, 7);

        assertFalse(field.hasConnectablePair());
    }

    @Test
    public void diagonalPairsBlockedByEachOtherAreNotSolvable() {
        Field field = new Field(6, 6);
        closeAllTiles(field);
        openNumber(field, 2, 2, 9);
        openNumber(field, 2, 3, 5);
        openNumber(field, 3, 2, 5);
        openNumber(field, 3, 3, 9);

        assertFalse(field.hasConnectablePair());
        assertFalse(field.canBeSolvedByGreedySearch());
    }

    private Field fieldWithTwoOpenTiles(int firstValue, int secondValue) {
        return fieldWithTwoOpenTilesAt(firstValue, 1, 1, secondValue, 1, 2);
    }

    private Field fieldWithOneOpenTile(int value) {
        Field field = new Field(4, 4);
        closeAllTiles(field);
        field.getTiles()[1][1] = new Numbers(value);
        field.getTile(1, 1).setState(TileState.OPEN);
        return field;
    }

    private Field fieldWithTwoOpenTilesAt(int firstValue, int firstRow, int firstColumn,
                                          int secondValue, int secondRow, int secondColumn) {
        Field field = new Field(4, 4);
        closeAllTiles(field);
        field.getTiles()[firstRow][firstColumn] = new Numbers(firstValue);
        field.getTile(firstRow, firstColumn).setState(TileState.OPEN);
        field.getTiles()[secondRow][secondColumn] = new Numbers(secondValue);
        field.getTile(secondRow, secondColumn).setState(TileState.OPEN);
        return field;
    }

    private void openNumber(Field field, int row, int column, int value) {
        field.getTiles()[row][column] = new Numbers(value);
        field.getTile(row, column).setState(TileState.OPEN);
    }

    private void closeAllTiles(Field field) {
        for (int row = 0; row < field.getRowCount(); row++) {
            for (int column = 0; column < field.getColumnCount(); column++) {
                field.getTiles()[row][column] = new Numbers(0);
                field.getTile(row, column).setState(TileState.CLOSED);
            }
        }
    }

    private void assertGeneratedBoardCanBeSolvedAndScattered(Field field) {
        assertFalse("Generated board should not place matching numbers directly next to each other.",
                field.hasAdjacentMatchingOpenPair());
        assertTrue("Generated board should have a guaranteed solve path.", field.canBeSolvedByGreedySearch());
    }

    private boolean connectFirstAdjacentMatchingOpenPair(Field field) {
        for (int row = 1; row < field.getRowCount() - 1; row++) {
            for (int column = 1; column < field.getColumnCount() - 2; column++) {
                if (canConnect(field, row, column, row, column + 1)) {
                    return true;
                }
            }
        }

        for (int row = 1; row < field.getRowCount() - 2; row++) {
            for (int column = 1; column < field.getColumnCount() - 1; column++) {
                if (canConnect(field, row, column, row + 1, column)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean canConnect(Field field, int firstRow, int firstColumn, int secondRow, int secondColumn) {
        Tile firstTile = field.getTile(firstRow, firstColumn);
        Tile secondTile = field.getTile(secondRow, secondColumn);
        if (firstTile.getState() != TileState.OPEN || secondTile.getState() != TileState.OPEN) {
            return false;
        }
        if (((Numbers) firstTile).getValue() != ((Numbers) secondTile).getValue()) {
            return false;
        }

        field.chooseTile(firstRow, firstColumn);
        return field.connectTile(secondRow, secondColumn);
    }
}
