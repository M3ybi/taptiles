package sk.tuke.gamestudio.game.Taptiles.Fedorco.consoleui;


import org.springframework.beans.factory.annotation.Autowired;
import sk.tuke.gamestudio.game.Taptiles.Fedorco.core.*;
import sk.tuke.gamestudio.entity.Comment;
import sk.tuke.gamestudio.entity.Rating;
import sk.tuke.gamestudio.entity.Score;
import sk.tuke.gamestudio.service.*;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static sk.tuke.gamestudio.game.Taptiles.Fedorco.core.Field.GAME_NAME;

public class ConsoleUI {
    private Field field;
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    private int score;
    private String username;
    int result;
//    private ScoreService scoreService = new ScoreServiceJDBC();
//    private CommentService commentService = new CommentServiceJDBC();
//    private RatingService ratingService = new RatingServiceJDBC();


    @Autowired
    private ScoreService scoreService;
    @Autowired
    private CommentService commentService;
    @Autowired
    private RatingService ratingService;


    public ConsoleUI(Field field) {
        this.field = field;
    }

    final Pattern CHOOSE_TILE_PATTERN
            = Pattern.compile("([M])([A-Z])([1-9][0-9]*)");
    final Pattern CONNECT_TILE_PATTERN
            = Pattern.compile("([C])([A-Z])([1-9][0-9]*)");

    private String readLine() {
        try {
            return br.readLine();
        } catch (IOException e) {
            System.err.println("Nepodarilo sa nacitat vstup, skus znova");
            return "";
        }
    }

    public int getResult() {
        return result;
    }

    private void printScore() {
        try {
            List<Score> scores = scoreService.getBestScores(GAME_NAME);

            for (Score s : scores) {
                System.out.println(s);
            }
        } catch (ScoreException e) {
            System.err.println(e.getMessage());
        }
    }

    private void printField() {
        printFieldHeader();
        show();
    }

    private void printFieldHeader() {
        for (int column = 0; column < field.getColumnCount() - 2; column++) {
            if (column == 0) {
                System.out.printf("%-4s", "");
            }
            System.out.printf("%-4s", column + 1);

        }
        System.out.println();


    }

    private void show() {
        for (int row = 0; row < field.getRowCount(); row++) {
            if (row != 0 && row != field.getRowCount() - 1) {
                System.out.print((char) (row + 64));
            }
            System.out.printf("%-3s", " ");
            for (int column = 0; column < field.getColumnCount(); column++) {
                Tile tile = field.getTile(row, column);
                if (tile != null && tile.getState() != TileState.CLOSED) {
                    if (tile.getState() != TileState.CLOSED) {
                        //System.out.printf("  ");
                        System.out.print(((Numbers) tile).getValue() + "   ");
                    } else {
                        System.out.print("    ");
                    }
                } else {
                    if (column != 0)
                        System.out.print("    ");
                }
            }
            System.out.println();
        }
    }

    public int getScore() {
        return score;
    }

    public String getUsername() {
        return username;
    }

    public void play() {

        System.out.println("Zadaj vstup:\n"
                + "X - pre ukoncenie hry\n"
                + "MA1 - pre oznacenie dlazdice v riadku A a stlpci 1\n"
                + "CB4 - pre spojenie dlazdice v riadku B a stlpci 4");

        System.out.println("Enter your username: ");
        username = readLine().trim();

        long startTime = System.nanoTime() / 100000;
        do {
            printField();
            handleInput();
        } while (field.getState() == GameState.PLAYING);

        if (field.getState() == GameState.SOLVED) {
            long endTime = System.nanoTime() / 100000;
            score = (int) (endTime - startTime) / 1000;

            playerDatabase(score, username);
        }
    }

    private void playerDatabase(int score, String userName) {
        addScore(score, userName);
        ratingSection(userName);
        commentSection(userName);
        gameAgain(userName);
    }

    private void gameAgain(String username) {

        System.out.println("Zacat hru odznova? a/n");
        String chooseInput = readLine().trim().toUpperCase();
        if (chooseInput.equals("A")) {
            field = new Field(field.getRowCount(),field.getColumnCount());
            play();
        }
//        try {
//            System.out.println("Average rating of this game is: " + ratingService.getAverageRating(GAME_NAME));
//            System.out.println("Your rating: " + ratingService.getRating(GAME_NAME, username) + " was added to database");
//        } catch (Exception ex) {
//            System.out.println("Exception");
//        }
    }

    private void commentSection(String username) {
        System.out.println("Chces zanechat komentar? a/n");
        String chooseCommentInput = readLine().trim().toUpperCase();
        if (chooseCommentInput.equals("A")) {
            System.out.println("Zanechaj za sebou komentar.");
            String comment = readLine();
            addComment(comment, username);
        }
    }

    private void ratingSection(String username) {
        System.out.println("Chces zanechat rating? a/n");
        String chooseCommentInput = readLine().trim().toUpperCase();
        if (chooseCommentInput.equals("A")) {
            System.out.println("Prosim ohodnot hru od 1-5 (velmi zla - vyborna)");
            addRating(username);
        }
    }

    private void addScore(int score, String username) {
        try {
            scoreService.addScore(new Score(
                    GAME_NAME,
                    username,
                    score,
                    new Date()
            ));
            System.out.println("Your score was added to database");
            printScore();
        } catch (ScoreException e) {
            System.err.println(e.getMessage());
        }
    }

    public void printComment() {
        try {
            List<Comment> comments = commentService.getComments(GAME_NAME);

            for (Comment s : comments) {
                System.out.println(s);
            }
        } catch (CommentException e) {
            System.err.println(e.getMessage());
        }
    }

    private void addComment(String comment, String username) {
        try {
            commentService.addComment(new Comment(
                    username,
                    GAME_NAME,
                    comment,
                    new Date()
            ));
            System.out.println("Your comment was added to database");
            printComment();
        } catch (CommentException e) {
            System.err.println(e.getMessage());
        }
    }

    private void addRating(String username) {
        String chooseInput = readLine();
        result = Integer.parseInt(chooseInput);
        if (result == 1 || result == 2 || result == 3 || result == 4 || result == 5) {
            try {
                ratingService.setRating(new Rating(
                        username,
                        GAME_NAME,
                        result,
                        new Date()
                ));

                printAvgRating();

            } catch (RatingException e) {
                System.err.println(e.getMessage());
            }
        } else {
            System.out.println("Zadal si nespravne hodnotenie");
        }
    }



    private void printAvgRating() {
        try {
            System.out.println("Average rating of this game is: " + ratingService.getAverageRating(GAME_NAME));
        } catch (RatingException e) {
            System.err.println(e.getMessage());
        }
    }

    private void handleInput() throws IllegalFormatException {
        String chooseInput = readLine().trim().toUpperCase();
        Matcher chooseMatcher = CHOOSE_TILE_PATTERN.matcher(chooseInput);

        endGame(chooseInput);

        String connectInput = readLine().trim().toUpperCase();
        Matcher connectMatcher = CONNECT_TILE_PATTERN.matcher(connectInput);

        endGame(connectInput);

        chooseMatcher(chooseMatcher);
        connectMatcher(connectMatcher);

    }

    private void endGame(String connectInput) {
        if (connectInput.equals("X")) {
            System.out.println("Hra bola predcasne ukoncena.");
            System.exit(0);
        }
    }

    private void connectMatcher(Matcher connectMatcher) {
        if (connectMatcher.matches()) {
            int connectRow = connectMatcher.group(2).charAt(0) - 'A' + 1;
            int connectColumn = Integer.parseInt(connectMatcher.group(3)); //- 1;
            switch (connectMatcher.group(1)) {
                case "C":
                    field.connectTile(connectRow, connectColumn);
                    break;
                default:
            }
        }
    }

    private void chooseMatcher(Matcher chooseMatcher) {
        if (chooseMatcher.matches()) {
            int choosedRow = chooseMatcher.group(2).charAt(0) - 'A' + 1;
            int choosedcolumn = Integer.parseInt(chooseMatcher.group(3)); //- 1;
            switch (chooseMatcher.group(1)) {
                case "M":
                    field.chooseTile(choosedRow, choosedcolumn);
                    break;
                default:
            }
        }
    }
}
