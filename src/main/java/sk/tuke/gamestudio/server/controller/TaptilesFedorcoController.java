package sk.tuke.gamestudio.server.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.WebApplicationContext;
import sk.tuke.gamestudio.entity.Comment;
import sk.tuke.gamestudio.entity.Rating;
import sk.tuke.gamestudio.entity.Score;
import sk.tuke.gamestudio.game.Taptiles.Fedorco.consoleui.ConsoleUI;
import sk.tuke.gamestudio.game.Taptiles.Fedorco.core.*;
import sk.tuke.gamestudio.service.*;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static sk.tuke.gamestudio.game.Taptiles.Fedorco.core.Field.GAME_NAME;

// Canonical public URL: /taptiles. The legacy /taptiles-fedorco path remains as an alias.
@Controller
@Scope(WebApplicationContext.SCOPE_SESSION)
public class TaptilesFedorcoController {
    private static final String TILE_ASSET_VERSION = "20260508tiles2";
    private static final String TAPTILES_PATH = "/taptiles";
    private static final String LEGACY_TAPTILES_PATH = "/taptiles-fedorco";

    private enum Difficulty {
        EASY,
        NORMAL,
        HARD
    }

    private Field field = new Field(8, 8);
    private ConsoleUI consoleUI = new ConsoleUI(field);
    private String username = "default";
    private int rating;
    private int selectedRow = -1;
    private int selectedColumn = -1;
    private int score;
    private boolean scoreSaved;
    private int moveCount;
    private long solvedElapsedSeconds;
    private int invalidRow = -1;
    private int invalidColumn = -1;
    private long startTime;
    private int row;
    private int col;
    private int hintsRemaining;
    private int hintsUsed;
    private int hintStep;
    private int hintFirstRow = -1;
    private int hintFirstColumn = -1;
    private int hintSecondRow = -1;
    private int hintSecondColumn = -1;
    private int streak;
    private int bestStreak;
    private Difficulty difficulty = Difficulty.NORMAL;
    private long boardSeed = System.nanoTime();
    private boolean dailyChallenge;
    private int lastMatchFirstRow = -1;
    private int lastMatchFirstColumn = -1;
    private int lastMatchSecondRow = -1;
    private int lastMatchSecondColumn = -1;
    private int lastScoreAdjustment;
    private String lastScoreAdjustmentLabel = "";
    private boolean boardReady;
    private boolean tutorialMode;
    private boolean failedBoard;
    private boolean abandonedBoard;
    private String comment = "default comment";
    @Autowired
    private ServletContext servletContext;
    @Autowired
    private ScoreService scoreService;
    @Autowired
    private RatingService ratingService;
    @Autowired
    private CommentService commentService;

    public TaptilesFedorcoController() {
        startTime = System.currentTimeMillis();
        row = 10;
        col = 10;
        field = createField();
        resetHintStateForNewGame();
    }

    @RequestMapping({TAPTILES_PATH + "/flag", LEGACY_TAPTILES_PATH + "/flag"})
    public String flag() {
        clearSelectedTile();
        clearHintState();
        return "redirect:/taptiles";
    }

    @RequestMapping({TAPTILES_PATH + "/hint", LEGACY_TAPTILES_PATH + "/hint"})
    public String hint(Model model) {
        clearSelectedTile();
        invalidRow = -1;
        invalidColumn = -1;

        if (!boardReady || failedBoard || tutorialMode || field.getState() != GameState.PLAYING || hintsRemaining <= 0) {
            fillModel(model);
            return "taptiles-fedorco";
        }

        if (!isStoredHintPairConnectable()) {
            setHintPair(field.findFirstConnectablePair());
        }
        if (!hasHintPair()) {
            fillModel(model);
            return "taptiles-fedorco";
        }

        hintsRemaining--;
        hintsUsed++;

        if (hintStep == 0) {
            hintStep = 1;
        } else if (hintStep == 1) {
            hintStep = 2;
        } else {
            connectHintPair();
        }

        fillModel(model);
        return "taptiles-fedorco";
    }

    @RequestMapping({TAPTILES_PATH + "/daily", LEGACY_TAPTILES_PATH + "/daily"})
    public String daily(Model model) {
        boardSeed = getDailySeed();
        dailyChallenge = true;
        tutorialMode = false;
        resetGame(model);
        return "taptiles-fedorco";
    }

    @RequestMapping({TAPTILES_PATH + "/replay", LEGACY_TAPTILES_PATH + "/replay"})
    public String replay(@RequestParam("seed") String seed, Model model) {
        try {
            boardSeed = Long.parseLong(seed);
            dailyChallenge = false;
            tutorialMode = false;
            resetGame(model);
        } catch (NumberFormatException ignored) {
            fillModel(model);
        }
        return "taptiles-fedorco";
    }

    @RequestMapping({TAPTILES_PATH + "/retry", LEGACY_TAPTILES_PATH + "/retry"})
    public String retry(@RequestParam("seed") String seed, Model model) {
        boolean retryDailyChallenge = dailyChallenge;
        try {
            boardSeed = Long.parseLong(seed);
            tutorialMode = false;
            dailyChallenge = retryDailyChallenge;
            resetGame(model);
            boardReady = true;
            startTime = System.currentTimeMillis();
            fillModel(model);
        } catch (NumberFormatException ignored) {
            fillModel(model);
        }
        return "taptiles-fedorco";
    }

    @RequestMapping({TAPTILES_PATH + "/giveUp", LEGACY_TAPTILES_PATH + "/giveUp"})
    public String giveUp() {
        if (!boardReady || failedBoard || abandonedBoard || tutorialMode) {
            return "redirect:/taptiles";
        }
        clearSelectedTile();
        clearHintState();
        for (int row = 0; row < field.getRowCount(); row++) {
            for (int col = 0; col < field.getColumnCount(); col++) {
                Tile tile = field.getTile(row, col);
                tile.setState(TileState.CLOSED);
            }
        }
        score = 0;
        scoreSaved = false;
        abandonedBoard = true;
        field.setState(GameState.SOLVED);
        return "redirect:/taptiles";
    }

    @RequestMapping({TAPTILES_PATH + "/correct", LEGACY_TAPTILES_PATH + "/correct"})
    public String correct() {
        if (!boardReady || failedBoard || tutorialMode) {
            return "redirect:/taptiles";
        }
        clearSelectedTile();
        clearHintState();
        for (int row = 0; row < field.getRowCount(); row++) {
            for (int col = 0; col < field.getColumnCount(); col++) {
                if ((field.getTile(row, col) instanceof Numbers) && (((Numbers) field.getTile(row, col)).getValue() > 4)) {
                    field.getTile(row, col).setState(TileState.CHOOSED);
                }
            }
        }
        return "redirect:/taptiles";
    }

    @RequestMapping({TAPTILES_PATH + "/shuffle", LEGACY_TAPTILES_PATH + "/shuffle"})
    public String shuffle(Model model) {
        clearSelectedTile();
        clearHintState();
        if (boardReady && !tutorialMode && !failedBoard && field.getState() == GameState.PLAYING && !field.hasConnectablePair()) {
            field.shuffleOpenTilesUntilConnectable();
        }
        fillModel(model);
        return "taptiles-fedorco";
    }

    @RequestMapping({TAPTILES_PATH + "/difficulty", LEGACY_TAPTILES_PATH + "/difficulty"})
    public String changeDifficulty(@RequestParam("mode") String mode, Model model) {
        difficulty = parseDifficulty(mode);
        tutorialMode = false;
        TaptilesNew(model);
        return "taptiles-fedorco";
    }

    @RequestMapping({TAPTILES_PATH + "/new", LEGACY_TAPTILES_PATH + "/new"})
    public String newGame(@RequestParam(value = "size", required = false) String size,
                          @RequestParam(value = "difficulty", required = false) String difficultyMode,
                          @RequestParam(value = "playMode", required = false) String playMode,
                          @RequestParam(value = "tutorialSize", required = false) String tutorialSize,
                          Model model) {
        if ("tutorial".equalsIgnoreCase(playMode)) {
            return tutorial(tutorialSize, model);
        }

        int playableSize = parseBoardSize(size);
        row = playableSize + 2;
        col = playableSize + 2;
        difficulty = parseDifficulty(difficultyMode);
        tutorialMode = false;
        TaptilesNew(model);
        return "taptiles-fedorco";
    }

    @RequestMapping({TAPTILES_PATH + "/start", LEGACY_TAPTILES_PATH + "/start"})
    public String start(Model model) {
        boardReady = true;
        startTime = System.currentTimeMillis();
        fillModel(model);
        return "taptiles-fedorco";
    }

    @RequestMapping({TAPTILES_PATH + "/guest", LEGACY_TAPTILES_PATH + "/guest"})
    public String guest() {
        username = "guest" + (10000 + new Random().nextInt(90000));
        rating = 0;
        return "redirect:/taptiles";
    }

    @RequestMapping({TAPTILES_PATH, LEGACY_TAPTILES_PATH})
    public String taptiles(@RequestParam(name = "column", required = false) String columnString,
                           @RequestParam(value = "row", required = false) String rowString,
                           HttpServletRequest request,
                           Model model) {
        if (isAjaxRequest(request)) {
            try {
                int row = Integer.parseInt(rowString);
                int column = Integer.parseInt(columnString);
                if (tutorialMode) {
                    handleTutorialTileClick(row, column);
                } else {
                    saveScoreAfterSuccessfulPlayerConnection(handleTileClick(row, column));
                }
            } catch (NumberFormatException ignored) {
            }
        } else {
            clearSelectedTile();
            clearHintState();
        }
        fillModel(model);
        return "taptiles-fedorco";
    }

    private boolean isAjaxRequest(HttpServletRequest request) {
        return "fetch".equals(request.getHeader("X-Requested-With"));
    }

    @RequestMapping({TAPTILES_PATH + "/addComment", LEGACY_TAPTILES_PATH + "/addComment"})
    public String addComment(@RequestParam("comment") String comment, Model model) {
        this.comment = comment;
        try {
            commentService.addComment(new Comment(username, "TapTiles", this.comment, new Date()));
        } catch (CommentException ex) {
            System.out.println("Could not add comment");
        }
        return "redirect:/taptiles";
    }


    @RequestMapping({TAPTILES_PATH + "/addUsername", LEGACY_TAPTILES_PATH + "/addUsername"})
    public String addUsername(@RequestParam("username") String value, @RequestParam("rating") String rating, Model model) {
        this.username = sanitizeUsername(value);
        try {
            this.rating = Integer.parseInt(rating);
        } catch (NumberFormatException ignored) {
            this.rating = 0;
        }
        if (this.rating < 0 || this.rating > 5) {
            return "redirect:/taptiles";
        }
        try {
            ratingService.setRating(new Rating(
                    username,
                    GAME_NAME,
                    this.rating,
                    new Date()));
        } catch (RatingException rx) {
            System.out.println("Could not set Rating");
        }
        return "redirect:/taptiles";
    }

    private String sanitizeUsername(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return "Player";
        }
        return normalized.length() > 24 ? normalized.substring(0, 24) : normalized;
    }

    @RequestMapping("/6x6")
    public String six(Model model) {
        row = 8;
        col = 8;
        tutorialMode = false;
        TaptilesNew(model);
        return "taptiles-fedorco";
    }

    @RequestMapping("/8x8")
    public String eight(Model model) {
        row = 10;
        col = 10;
        tutorialMode = false;
        TaptilesNew(model);
        return "taptiles-fedorco";
    }

    @RequestMapping("/10x10")
    public String ten(Model model) {
        row = 12;
        col = 12;
        tutorialMode = false;
        TaptilesNew(model);
        return "taptiles-fedorco";
    }

    @RequestMapping("/TaptilesNew")
    public String TaptilesNew(Model model) {
        boardSeed = new Random().nextLong();
        dailyChallenge = false;
        tutorialMode = false;
        resetGame(model);
        return "taptiles-fedorco";
    }

    @RequestMapping({TAPTILES_PATH + "/tutorial", LEGACY_TAPTILES_PATH + "/tutorial"})
    public String tutorial(@RequestParam("size") String size, Model model) {
        int playableSize = parseTutorialSize(size);
        row = playableSize + 2;
        col = playableSize + 2;
        boardSeed = new Random().nextLong();
        dailyChallenge = false;
        tutorialMode = true;
        resetGame(model);
        boardReady = true;
        startTime = System.currentTimeMillis();
        fillModel(model);
        return "taptiles-fedorco";
    }

    private void resetGame(Model model) {
        field = createField();
        score = 0;
        scoreSaved = false;
        moveCount = 0;
        solvedElapsedSeconds = 0;
        startTime = System.currentTimeMillis();
        selectedRow = -1;
        selectedColumn = -1;
        invalidRow = -1;
        invalidColumn = -1;
        resetHintStateForNewGame();
        streak = 0;
        bestStreak = 0;
        boardReady = false;
        failedBoard = false;
        abandonedBoard = false;
        clearLastMatch();
        clearScoreAdjustment();
        fillModel(model);
    }

    public String getHTMLField() {
        StringBuilder sb = new StringBuilder();
        sb.append("<table class='field'>\n");
        for (int row = 1; row < field.getRowCount() - 1; row++) {
            sb.append("<tr>\n");
            for (int column = 1; column < field.getColumnCount() - 1; column++) {
                Tile tile = field.getTile(row, column);
                sb.append("<td>\n");
                if (tile.getState() == TileState.CLOSED) {
                    sb.append(String.format("<span class='%s'>\n", getTileClass(row, column, tile)));
                    sb.append(String.format("<img src='%s/images/taptiles/%s.png?v=%s' alt='Removed Taptiles tile'>",
                            servletContext.getContextPath(), getImageName(row, column, tile), TILE_ASSET_VERSION));
                    sb.append(getScoreAdjustmentBadge(row, column));
                    sb.append("</span>");
                } else {
                    sb.append(String.format("<a class='%s' href='%s/taptiles?row=%s&column=%s'>\n",
                            getTileClass(row, column, tile), servletContext.getContextPath(), row, column));
                    sb.append(String.format("<img src='%s/images/taptiles/%s.png?v=%s' alt='Taptiles tile'>",
                            servletContext.getContextPath(), getImageName(row, column, tile), TILE_ASSET_VERSION));
                    sb.append("</a>");
                }
                sb.append("</td>\n");
            }
            sb.append("</tr>\n");
        }
        sb.append("</table>\n");
        return sb.toString();

    }

    private boolean handleTileClick(int row, int column) {
        clearLastMatch();
        clearScoreAdjustment();
        invalidRow = -1;
        invalidColumn = -1;
        if (!boardReady || failedBoard || field.getState() != GameState.PLAYING || !isInsidePlayableBoard(row, column)) {
            return false;
        }

        Tile clickedTile = field.getTile(row, column);
        if (clickedTile.getState() == TileState.CLOSED) {
            return false;
        }

        clearHintState();

        if (!hasSelectedTile()) {
            chooseTile(row, column);
            return false;
        }

        if (selectedRow == row && selectedColumn == column) {
            field.chooseTile(row, column);
            selectedRow = -1;
            selectedColumn = -1;
            return false;
        }

        Tile selectedTile = field.getTile(selectedRow, selectedColumn);
        int firstRow = selectedRow;
        int firstColumn = selectedColumn;
        moveCount++;
        boolean connected = field.connectTile(row, column);
        selectedRow = -1;
        selectedColumn = -1;

        if (connected) {
            setLastMatch(firstRow, firstColumn, row, column);
            applySuccessfulHit();
            streak++;
            bestStreak = Math.max(bestStreak, streak);
            checkNextMoveAvailabilityAfterConnection();
        } else {
            applyMiss(row, column);
            streak = 0;
        }

        if (!connected && clickedTile.getState() == TileState.OPEN) {
            invalidRow = row;
            invalidColumn = column;
            if (selectedTile.getState() == TileState.CHOOSED) {
                selectedTile.setState(TileState.OPEN);
            }
        }
        return connected;
    }

    private void checkNextMoveAvailabilityAfterConnection() {
        if (field.getState() != GameState.PLAYING || field.hasConnectablePair()) {
            return;
        }
        failedBoard = true;
        clearSelectedTile();
        clearHintState();
    }

    private void handleTutorialTileClick(int row, int column) {
        clearLastMatch();
        clearScoreAdjustment();
        invalidRow = -1;
        invalidColumn = -1;
        clearHintState();
        if (!boardReady || field.getState() != GameState.PLAYING || !isInsidePlayableBoard(row, column)) {
            return;
        }

        Tile clickedTile = field.getTile(row, column);
        if (clickedTile.getState() == TileState.CLOSED) {
            return;
        }

        if (!hasSelectedTile()) {
            chooseTile(row, column);
            return;
        }

        if (selectedRow == row && selectedColumn == column) {
            field.chooseTile(row, column);
            selectedRow = -1;
            selectedColumn = -1;
            return;
        }

        Tile selectedTile = field.getTile(selectedRow, selectedColumn);
        int firstRow = selectedRow;
        int firstColumn = selectedColumn;
        boolean connected = field.connectTile(row, column);
        selectedRow = -1;
        selectedColumn = -1;

        if (connected) {
            setLastMatch(firstRow, firstColumn, row, column);
            checkNextMoveAvailabilityAfterConnection();
            return;
        }

        invalidRow = row;
        invalidColumn = column;
        if (selectedTile.getState() == TileState.CHOOSED) {
            selectedTile.setState(TileState.OPEN);
        }
        if (clickedTile.getState() == TileState.CHOOSED) {
            clickedTile.setState(TileState.OPEN);
        }
    }

    private void saveScoreAfterSuccessfulPlayerConnection(boolean connected) {
        if (!connected || field.getState() != GameState.SOLVED || scoreSaved) {
            return;
        }
        long endTime = System.currentTimeMillis();
        solvedElapsedSeconds = (endTime - startTime) / 1000;
        scoreService.addScore(new Score(getScoreGameName(), username, score, new Date()));
        scoreSaved = true;
    }

    private String getScoreGameName() {
        return dailyChallenge ? "TapTilesDaily" : "TapTiles";
    }

    private Field createField() {
        return new Field(row, col, getLayoutDifficulty(), boardSeed);
    }

    private Field.LayoutDifficulty getLayoutDifficulty() {
        if (difficulty == Difficulty.EASY) {
            return Field.LayoutDifficulty.EASY;
        }
        if (difficulty == Difficulty.HARD) {
            return Field.LayoutDifficulty.HARD;
        }
        return Field.LayoutDifficulty.NORMAL;
    }

    private Difficulty parseDifficulty(String mode) {
        if ("easy".equalsIgnoreCase(mode)) {
            return Difficulty.EASY;
        }
        if ("hard".equalsIgnoreCase(mode)) {
            return Difficulty.HARD;
        }
        return Difficulty.NORMAL;
    }

    private int parseTutorialSize(String size) {
        if ("6".equals(size)) {
            return 6;
        }
        if ("10".equals(size)) {
            return 10;
        }
        return 8;
    }

    private int parseBoardSize(String size) {
        if ("6".equals(size)) {
            return 6;
        }
        if ("8".equals(size)) {
            return 8;
        }
        if ("10".equals(size)) {
            return 10;
        }
        return Math.max(2, row - 2);
    }

    private void chooseTile(int row, int column) {
        clearSelectedTile();
        field.chooseTile(row, column);
        if (field.getTile(row, column).getState() == TileState.CHOOSED) {
            selectedRow = row;
            selectedColumn = column;
        }
    }

    private void clearSelectedTile() {
        for (int row = 1; row < field.getRowCount() - 1; row++) {
            for (int column = 1; column < field.getColumnCount() - 1; column++) {
                Tile tile = field.getTile(row, column);
                if (tile.getState() == TileState.CHOOSED) {
                    tile.setState(TileState.OPEN);
                }
            }
        }
        selectedRow = -1;
        selectedColumn = -1;
        invalidRow = -1;
        invalidColumn = -1;
    }

    private void setLastMatch(int firstRow, int firstColumn, int secondRow, int secondColumn) {
        lastMatchFirstRow = firstRow;
        lastMatchFirstColumn = firstColumn;
        lastMatchSecondRow = secondRow;
        lastMatchSecondColumn = secondColumn;
    }

    private void clearLastMatch() {
        lastMatchFirstRow = -1;
        lastMatchFirstColumn = -1;
        lastMatchSecondRow = -1;
        lastMatchSecondColumn = -1;
    }

    private void applySuccessfulHit() {
        lastScoreAdjustment = ScoringRules.hitDelta(streak);
        score = ScoringRules.scoreAfterHit(score, streak);
        lastScoreAdjustmentLabel = formatScoreAdjustment(lastScoreAdjustment) + (streak > 0 ? " streak" : " match");
    }

    private void applyMiss(int row, int column) {
        score = ScoringRules.scoreAfterMiss(score);
        lastScoreAdjustment = -ScoringRules.MISS_PENALTY;
        lastScoreAdjustmentLabel = formatScoreAdjustment(lastScoreAdjustment) + " miss";
        lastMatchFirstRow = row;
        lastMatchFirstColumn = column;
        lastMatchSecondRow = -1;
        lastMatchSecondColumn = -1;
    }

    private void clearScoreAdjustment() {
        lastScoreAdjustment = 0;
        lastScoreAdjustmentLabel = "";
    }

    private String formatScoreAdjustment(int adjustment) {
        return (adjustment > 0 ? "+" : "") + adjustment;
    }

    private void resetHintStateForNewGame() {
        hintsRemaining = getMaxHints();
        hintsUsed = 0;
        clearHintState();
    }

    private void clearHintState() {
        hintStep = 0;
        hintFirstRow = -1;
        hintFirstColumn = -1;
        hintSecondRow = -1;
        hintSecondColumn = -1;
    }

    private int getMaxHints() {
        int playableRows = field.getRowCount() - 2;
        int baseHints;
        if (playableRows >= 10) {
            baseHints = 5;
        } else if (playableRows >= 8) {
            baseHints = 4;
        } else {
            baseHints = 3;
        }

        if (difficulty == Difficulty.EASY) {
            return baseHints + 2;
        }
        if (difficulty == Difficulty.HARD) {
            return Math.max(1, baseHints - 1);
        }
        return baseHints;
    }

    private boolean hasHintPair() {
        return isInsidePlayableBoard(hintFirstRow, hintFirstColumn)
                && isInsidePlayableBoard(hintSecondRow, hintSecondColumn);
    }

    private boolean isStoredHintPairConnectable() {
        return hasHintPair() && field.canConnectTiles(hintFirstRow, hintFirstColumn, hintSecondRow, hintSecondColumn);
    }

    private void setHintPair(int[][] pair) {
        clearHintState();
        if (pair == null || pair.length != 2) {
            return;
        }
        hintFirstRow = pair[0][0];
        hintFirstColumn = pair[0][1];
        hintSecondRow = pair[1][0];
        hintSecondColumn = pair[1][1];
    }

    private void connectHintPair() {
        if (!isStoredHintPairConnectable()) {
            clearHintState();
            return;
        }

        field.chooseTile(hintFirstRow, hintFirstColumn);
        if (field.connectTile(hintSecondRow, hintSecondColumn)) {
            setLastMatch(hintFirstRow, hintFirstColumn, hintSecondRow, hintSecondColumn);
            streak = 0;
            checkNextMoveAvailabilityAfterConnection();
        }
        clearHintState();
    }

    private boolean hasSelectedTile() {
        return isInsidePlayableBoard(selectedRow, selectedColumn);
    }

    private boolean isInsidePlayableBoard(int row, int column) {
        return row > 0 && row < field.getRowCount() - 1 && column > 0 && column < field.getColumnCount() - 1;
    }

    private String getTileClass(int row, int column, Tile tile) {
        StringBuilder className = new StringBuilder("tile-link");
        if (tile.getState() == TileState.CHOOSED || (selectedRow == row && selectedColumn == column)) {
            className.append(" tile-link--selected");
        }
        if (tile.getState() == TileState.CLOSED) {
            className.append(" tile-link--closed");
        }
        if (isLastMatchedTile(row, column)) {
            className.append(" tile-link--matched");
        }
        if (invalidRow == row && invalidColumn == column) {
            className.append(" tile-link--invalid");
        }
        if (hintStep > 0 && hintFirstRow == row && hintFirstColumn == column) {
            className.append(" tile-link--hint tile-link--hint-primary");
        }
        if (hintStep > 1 && hintSecondRow == row && hintSecondColumn == column) {
            className.append(" tile-link--hint tile-link--hint-secondary");
        }
        return className.toString();
    }


    private String getImageName(int row, int column, Tile tile) {
        if (isHintedTile(row, column) && tile instanceof Numbers && tile.getState() != TileState.CLOSED) {
            return "ch" + ((Numbers) tile).getValue();
        }
        return getImageName(tile);
    }

    private boolean isHintedTile(int row, int column) {
        return (hintStep > 0 && hintFirstRow == row && hintFirstColumn == column)
                || (hintStep > 1 && hintSecondRow == row && hintSecondColumn == column);
    }

    private boolean isLastMatchedTile(int row, int column) {
        return (lastMatchFirstRow == row && lastMatchFirstColumn == column)
                || (lastMatchSecondRow == row && lastMatchSecondColumn == column);
    }

    private String getScoreAdjustmentBadge(int row, int column) {
        if (!isLastMatchedTile(row, column) || lastScoreAdjustmentLabel.isEmpty()) {
            return "";
        }
        String badgeClass = lastScoreAdjustment >= 0 ? "score-pop score-pop--positive" : "score-pop score-pop--negative";
        return String.format("<span class='%s'>%s</span>", badgeClass, lastScoreAdjustmentLabel);
    }

    private String getImageName(Tile tile) {
        switch (tile.getState()) {
            case OPEN:
                if (tile instanceof Numbers)
                    return String.valueOf(((Numbers) tile).getValue());
            case CLOSED:
                return "c" + String.valueOf(((Numbers) tile).getValue());
            case CHOOSED:
                return "ch" + String.valueOf(((Numbers) tile).getValue());
        }
        throw new IllegalArgumentException();

    }


    private void fillModel(Model model) {
        model.addAttribute("username", username);
        model.addAttribute("rating", rating);
        model.addAttribute("hasSelectedTile", hasSelectedTile());
        model.addAttribute("moveCount", moveCount);
        model.addAttribute("elapsedSeconds", getElapsedSeconds());
        model.addAttribute("failedBoard", failedBoard);
        model.addAttribute("abandonedBoard", abandonedBoard);
        model.addAttribute("noMoves", boardReady && !tutorialMode && !failedBoard && field.getState() == GameState.PLAYING && !field.hasConnectablePair());
        model.addAttribute("boardRows", field.getRowCount() - 2);
        model.addAttribute("boardColumns", field.getColumnCount() - 2);
        model.addAttribute("gameState", field.getState());
        model.addAttribute("score", score);
        model.addAttribute("estimatedScore", score);
        model.addAttribute("displayScore", getDisplayScore());
        model.addAttribute("timerRunning", isTimerRunning());
        model.addAttribute("lastScoreAdjustment", lastScoreAdjustment);
        model.addAttribute("lastScoreAdjustmentLabel", lastScoreAdjustmentLabel);
        model.addAttribute("hintsRemaining", hintsRemaining);
        model.addAttribute("hintsUsed", hintsUsed);
        model.addAttribute("maxHints", getMaxHints());
        model.addAttribute("hintLabel", getHintLabel());
        model.addAttribute("streak", streak);
        model.addAttribute("bestStreak", bestStreak);
        model.addAttribute("difficulty", difficulty.name());
        model.addAttribute("hintPath", getHintPathAttribute());
        model.addAttribute("tutorialPaths", getTutorialPathsAttribute());
        model.addAttribute("boardReady", boardReady);
        model.addAttribute("tutorialMode", tutorialMode);
        model.addAttribute("boardSeed", boardSeed);
        model.addAttribute("dailyChallenge", dailyChallenge);
        model.addAttribute("shareUrl", "/taptiles/replay?seed=" + boardSeed);
        model.addAttribute("retryUrl", getRetryUrl());
        model.addAttribute("grade", getGrade());
        try {
            model.addAttribute("comments", commentService.getComments("TapTiles"));
        } catch (CommentException ex) {
            System.out.println("Can not load comments.");
        }
        model.addAttribute("scores", loadGlobalScores());
        model.addAttribute("currentUserScores", loadCurrentUserScores());
        model.addAttribute("hasCurrentUser", hasCurrentUser());
        if (!"default".equals(username)) {
            try {
                model.addAttribute("ratings", ratingService.getAverageRating("TapTiles"));
            } catch (RatingException ex) {
                System.out.println("Cannot find average rating.");
            }
        }
        if (!"default".equals(username)) {

            try {
                model.addAttribute("getRatings", ratingService.getRating("TapTiles", username));
            } catch (RatingException ex) {
                System.out.println("Cannot find rating.");
            }
        }
    }

    private String getHintLabel() {
        if (hintsRemaining <= 0) {
            return "No hints left";
        }
        if (hintStep == 0) {
            return "Hint: first tile";
        }
        if (hintStep == 1) {
            return "Hint: matching tile";
        }
        return "Hint: connect pair";
    }

    private String getRetryUrl() {
        if (tutorialMode) {
            return "/taptiles/tutorial?size=" + (field.getRowCount() - 2);
        }
        return "/taptiles/retry?seed=" + boardSeed;
    }

    private List<Score> loadGlobalScores() {
        try {
            return scoreService.getBestScores("TapTiles");
        } catch (ScoreException ex) {
            System.out.println("Can not load scores.");
            return Collections.emptyList();
        }
    }

    private List<Score> loadCurrentUserScores() {
        if (!hasCurrentUser()) {
            return Collections.emptyList();
        }
        try {
            return scoreService.getBestScoresForPlayer("TapTiles", username);
        } catch (ScoreException ex) {
            System.out.println("Can not load player scores.");
            return Collections.emptyList();
        }
    }

    private boolean hasCurrentUser() {
        return username != null && !"default".equals(username.trim()) && !username.trim().isEmpty();
    }

    private String getHintPathAttribute() {
        if (hintStep < 2 || !hasHintPair()) {
            return "";
        }
        int[][] path = field.findConnectionPath(hintFirstRow, hintFirstColumn, hintSecondRow, hintSecondColumn);
        if (path == null) {
            return "";
        }
        StringBuilder pathValue = new StringBuilder();
        for (int index = 0; index < path.length; index++) {
            if (index > 0) {
                pathValue.append(';');
            }
            pathValue.append(path[index][0]).append(',').append(path[index][1]);
        }
        return pathValue.toString();
    }

    private String getTutorialPathsAttribute() {
        if (!tutorialMode || !hasSelectedTile()) {
            return "";
        }

        Tile selectedTile = field.getTile(selectedRow, selectedColumn);
        if (!(selectedTile instanceof Numbers) || selectedTile.getState() == TileState.CLOSED) {
            return "";
        }

        List<String> paths = new ArrayList<>();
        for (int row = 1; row < field.getRowCount() - 1; row++) {
            for (int column = 1; column < field.getColumnCount() - 1; column++) {
                if (row == selectedRow && column == selectedColumn) {
                    continue;
                }

                int[][] path = field.findConnectionPath(selectedRow, selectedColumn, row, column);
                if (path != null) {
                    paths.add(formatPath(path));
                }
            }
        }
        return String.join("|", paths);
    }

    private String formatPath(int[][] path) {
        StringBuilder pathValue = new StringBuilder();
        for (int index = 0; index < path.length; index++) {
            if (index > 0) {
                pathValue.append(';');
            }
            pathValue.append(path[index][0]).append(',').append(path[index][1]);
        }
        return pathValue.toString();
    }

    private long getDailySeed() {
        LocalDate date = LocalDate.now(ZoneId.of("Europe/Bratislava"));
        return Objects.hash("TapTilesDaily", date.toString(), row, col, difficulty.name());
    }

    private String getGrade() {
        int points = scoreSaved ? score : getDisplayScore();
        int playableTileCount = (field.getRowCount() - 2) * (field.getColumnCount() - 2);
        return GradingRules.grade(points, playableTileCount, difficulty.name());
    }

    public GameState getGameState() {
        return field.getState();
    }

    private long getElapsedSeconds() {
        if (abandonedBoard) {
            return 0;
        }
        if (!isTimerRunning() && !scoreSaved) {
            return 0;
        }
        if (field.getState() == GameState.SOLVED && scoreSaved) {
            return solvedElapsedSeconds;
        }
        return Math.max(0, (System.currentTimeMillis() - startTime) / 1000);
    }

    private boolean isTimerRunning() {
        return boardReady && !abandonedBoard && !tutorialMode && field.getState() == GameState.PLAYING;
    }

    private int getDisplayScore() {
        if (!boardReady || abandonedBoard || tutorialMode) {
            return 0;
        }
        if (scoreSaved) {
            return score;
        }
        return score;
    }

}
