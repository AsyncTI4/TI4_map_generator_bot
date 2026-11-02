package ti4.service.async;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.helpers.DiceHelper;

class DieRollSequenceServiceTest {

    private final List<String> activities = List.of("A...", "B...");
    private final List<DiceHelper.Die> rolls = List.of(DiceHelper.spoof(10, 3), DiceHelper.spoof(10, 10));

    @Test
    void showsOnlyTheHeaderBeforeAnyStep() {
        assertEquals("head", DieRollSequenceService.render("head", activities, rolls, 0));
    }

    @Test
    void showsAnActivityBeforeItsDieIsRevealed() {
        assertEquals("head\nA...", DieRollSequenceService.render("head", activities, rolls, 1));
    }

    @Test
    void revealsEachDieBeforeStartingTheNextActivity() {
        String revealed = DieRollSequenceService.render("head", activities, rolls, 2);
        String nextActivity = DieRollSequenceService.render("head", activities, rolls, 3);

        assertEquals("head\nA... " + rolls.get(0).getGreenDieIfSuccessOrRedDieIfFailure(), revealed);
        assertEquals(revealed + "\nB...", nextActivity);
    }
}
