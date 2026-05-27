package sk.tuke.gamestudio.game.Taptiles.Fedorco.core;

public final class GradingRules {
    private GradingRules() {
    }

    public static String grade(int score, int playableTileCount, String difficulty) {
        int basis = Math.max(1, difficultyAdjustedBasis(playableTileCount, difficulty));
        int percentage = (score * 100) / basis;
        if (percentage >= 90) {
            return "S";
        }
        if (percentage >= 75) {
            return "A";
        }
        if (percentage >= 60) {
            return "B";
        }
        if (percentage >= 40) {
            return "C";
        }
        return "D";
    }

    static int difficultyAdjustedBasis(int playableTileCount, String difficulty) {
        int pairCount = Math.max(0, playableTileCount) / 2;
        int perfectScore = 0;
        for (int streakIndex = 0; streakIndex < pairCount; streakIndex++) {
            perfectScore += ScoringRules.hitDelta(streakIndex);
        }
        if ("EASY".equalsIgnoreCase(difficulty)) {
            return (perfectScore * 11) / 10;
        }
        if ("HARD".equalsIgnoreCase(difficulty)) {
            return (perfectScore * 9) / 10;
        }
        return perfectScore;
    }
}
