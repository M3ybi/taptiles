package sk.tuke.gamestudio.game.Taptiles.Fedorco.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GradingRulesTest {
    @Test
    public void normalSixBySixUsesBoardSizedThresholds() {
        int basis = GradingRules.difficultyAdjustedBasis(36, "NORMAL");

        assertEquals("S", GradingRules.grade(ceilingPercent(basis, 90), 36, "NORMAL"));
        assertEquals("A", GradingRules.grade(ceilingPercent(basis, 75), 36, "NORMAL"));
        assertEquals("B", GradingRules.grade(ceilingPercent(basis, 60), 36, "NORMAL"));
        assertEquals("C", GradingRules.grade(ceilingPercent(basis, 40), 36, "NORMAL"));
    }

    @Test
    public void largerBoardsHaveLargerGradeBasis() {
        assertTrue(GradingRules.difficultyAdjustedBasis(100, "NORMAL")
                > GradingRules.difficultyAdjustedBasis(36, "NORMAL"));
    }

    @Test
    public void hardDifficultyIsMoreLenientThanEasy() {
        assertTrue(GradingRules.difficultyAdjustedBasis(64, "HARD")
                < GradingRules.difficultyAdjustedBasis(64, "EASY"));
    }

    private int ceilingPercent(int value, int percent) {
        return (value * percent + 99) / 100;
    }
}
