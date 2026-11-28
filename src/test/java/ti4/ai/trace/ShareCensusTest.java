package ti4.ai.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.DecidingSeat;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// The census never changes an answer: every call still computes. It only records, per key, who computed it before
// on the same save (or, for pure functions, on any save), so the report says how much a shared cache could save.
class ShareCensusTest extends BaseTi4Test {

    private final AiTestGame test = new AiTestGame();
    private final Player rival = test.addSeat("200000000000000002", "sardakk", "red");

    @BeforeEach
    void clear() {
        ShareCensus.reset();
    }

    @AfterEach
    void restore() {
        ShareCensus.enable(Boolean.getBoolean(ShareCensus.PROPERTY));
        ShareCensus.reset();
    }

    private int computeAs(Player seat, String name, String key) {
        return DecidingSeat.of(seat, () -> ShareCensus.perSave(name, test.game, key, () -> 1));
    }

    private String line(String name) {
        return ShareCensus.lines().stream()
                .filter(line -> line.startsWith("SHARECENSUS " + name + ":"))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void switchedOffItOnlyComputes() {
        ShareCensus.enable(false);

        assertThat(ShareCensus.perSave("x", test.game, "k", () -> 7)).isEqualTo(7);
        assertThat(ShareCensus.lines()).hasSize(1);
    }

    @Test
    void repeatsAreSortedByWhoAskedAndWhen() {
        ShareCensus.enable(true);
        test.game.setLastModifiedDate(1000);
        ShareCensus.startTick(test.game);

        computeAs(test.nekro, "odds", "a");
        computeAs(test.nekro, "odds", "a");
        computeAs(rival, "odds", "a");
        ShareCensus.startTick(test.game);
        computeAs(test.nekro, "odds", "a");
        computeAs(test.nekro, "odds", "b");

        assertThat(line("odds"))
                .contains("5 computations")
                .contains("repeats 3 (60% of calls)")
                .contains("same seat, same tick 1")
                .contains("same seat, later tick 1")
                .contains("another seat 1");
    }

    // A per-save key starts over once the game is saved again: nothing carries to the next save.
    @Test
    void aNewSaveStartsAFreshCount() {
        ShareCensus.enable(true);
        test.game.setLastModifiedDate(1000);
        ShareCensus.startTick(test.game);
        computeAs(test.nekro, "odds", "a");
        test.game.setLastModifiedDate(2000);
        ShareCensus.startTick(test.game);
        computeAs(test.nekro, "odds", "a");

        assertThat(line("odds")).contains("2 computations").contains("repeats 0");
    }

    // A pure function's answer does not depend on the save, so a repeat on a later save still counts.
    @Test
    void aPureKeyRepeatsAcrossSaves() {
        ShareCensus.enable(true);
        test.game.setLastModifiedDate(1000);
        ShareCensus.startTick(test.game);
        DecidingSeat.of(test.nekro, () -> ShareCensus.pure("battle", List.of(1, 2), () -> 0.5));
        test.game.setLastModifiedDate(2000);
        ShareCensus.startTick(test.game);
        DecidingSeat.of(rival, () -> ShareCensus.pure("battle", List.of(1, 2), () -> 0.5));

        assertThat(line("battle")).contains("repeats 1").contains("another save 1");
    }
}
