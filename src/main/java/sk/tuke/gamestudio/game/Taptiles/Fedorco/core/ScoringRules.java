package sk.tuke.gamestudio.game.Taptiles.Fedorco.core;

public final class ScoringRules {
    public static final int BASE_HIT_SCORE = 10;
    public static final int MISS_PENALTY = 5;
    public static final int STREAK_BONUS_STEP = 1;
    public static final int MAX_STREAK_BONUS = 10;
    public static final int MIN_SCORE = 0;

    private ScoringRules() {
    }

    public static int hitDelta(int currentStreak) {
        return BASE_HIT_SCORE + streakBonus(currentStreak);
    }

    public static int scoreAfterHit(int currentScore, int currentStreak) {
        return Math.max(MIN_SCORE, currentScore + hitDelta(currentStreak));
    }

    public static int scoreAfterMiss(int currentScore) {
        return Math.max(MIN_SCORE, currentScore - MISS_PENALTY);
    }

    public static int streakBonus(int currentStreak) {
        return Math.min(MAX_STREAK_BONUS, Math.max(0, currentStreak) * STREAK_BONUS_STEP);
    }
}
