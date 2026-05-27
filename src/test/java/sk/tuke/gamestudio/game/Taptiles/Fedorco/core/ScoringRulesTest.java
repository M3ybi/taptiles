package sk.tuke.gamestudio.game.Taptiles.Fedorco.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ScoringRulesTest {
    @Test
    public void firstSuccessfulHitAwardsBaseScoreOnly() {
        assertEquals(10, ScoringRules.hitDelta(0));
        assertEquals(10, ScoringRules.scoreAfterHit(0, 0));
    }

    @Test
    public void successfulStreakAddsModerateBonus() {
        assertEquals(13, ScoringRules.hitDelta(3));
        assertEquals(28, ScoringRules.scoreAfterHit(15, 3));
    }

    @Test
    public void streakBonusIsCapped() {
        assertEquals(10, ScoringRules.streakBonus(10));
        assertEquals(10, ScoringRules.streakBonus(30));
        assertEquals(20, ScoringRules.hitDelta(30));
    }

    @Test
    public void missSubtractsPenaltyWithoutGoingNegative() {
        assertEquals(7, ScoringRules.scoreAfterMiss(12));
        assertEquals(0, ScoringRules.scoreAfterMiss(2));
    }
}
