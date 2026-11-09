package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.ai.strategy.SpeakerSale;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameSnapshots;
import ti4.testUtils.BaseTi4Test;

// ATTACK_MEMORY with PROMISE_DEALS: a promise deal is non-binding (LRR 28.4: its terms resolve later), so a player can
// take the trade goods and attack anyway. The attack ledger counts that betrayal against the breaker as a grievance
// sized by the trade goods the victim paid for nothing. The board damage of the same attack is already in the ledger
// from the board diff, so the grievance adds only the price. It is read from the promise book at grudge time, so it
// decays with the same halving per round, is forgotten after three rounds, and rolls back with /game undo along with
// the book.
class PromiseGrievanceTest extends BaseTi4Test {

    private static final int PRICE = 2;
    private static final double EXACT = 1e-9;

    private final DefenseScenario board = new DefenseScenario();
    private final Game game = board.test.game;
    private final Player nekro = board.test.nekro;
    private final Player sardakk = board.sardakk;

    private Promise noAttack(Player promiser, Player promisee, long at) {
        return Promise.offer(game, Promise.Term.NO_ATTACK, promiser, promisee, promisee, "", "", 0, PRICE, at, at + 1)
                .accepted(game);
    }

    private void sardakkBreaks(long at) {
        PromiseBook.save(game, noAttack(sardakk, nekro, at).with(Promise.Status.BROKEN));
    }

    private void ledgerEntry(int round, Player attacker, Player victim, double damage) {
        game.setStoredValue(
                AttackLedger.LEDGER_KEY,
                round + "/" + attacker.getFaction() + "/" + victim.getFaction() + "=" + damage);
    }

    private double hurtWeight() {
        return new Standings(game, nekro, null).hurtWeight(sardakk);
    }

    @Test
    void aBrokenNoAttackPromiseRaisesTheVictimsGrudgeAndHurtWeight() {
        double before = hurtWeight();
        sardakkBreaks(1);

        assertThat(AttackLedger.grudge(game, nekro, sardakk)).isEqualTo(PRICE);
        assertThat(AttackLedger.grudge(game, sardakk, nekro)).isZero();
        assertThat(hurtWeight()).isGreaterThan(before);
        assertThat(hurtWeight()).isCloseTo(Standings.hurtWeight(game, sardakk, PRICE), within(EXACT));
    }

    @Test
    void aBetrayalHalvesEachRoundAndIsForgottenAfterThree() {
        game.setRound(4);
        sardakkBreaks(1);

        double fresh = AttackLedger.grudge(game, nekro, sardakk);
        game.setRound(5);
        double lastRound = AttackLedger.grudge(game, nekro, sardakk);
        game.setRound(6);
        double twoAgo = AttackLedger.grudge(game, nekro, sardakk);
        game.setRound(7);
        double old = AttackLedger.grudge(game, nekro, sardakk);

        assertThat(fresh).isEqualTo(PRICE);
        assertThat(lastRound).isEqualTo(PRICE * AttackLedger.DECAY_PER_ROUND);
        assertThat(twoAgo).isEqualTo(PRICE * AttackLedger.DECAY_PER_ROUND * AttackLedger.DECAY_PER_ROUND);
        assertThat(old).isZero();
    }

    // /game undo restores the save from before the breach: the book there still holds the promise as accepted, so the
    // grievance is gone with no ledger write to undo.
    @Test
    void undoingTheBreachUndoesTheGrievance() {
        game.newGameSetup();
        PromiseBook.save(game, noAttack(sardakk, nekro, 1));
        Game beforeBreach = GameSnapshots.copy(game);
        sardakkBreaks(1);

        Game undone = GameSnapshots.copy(beforeBreach);

        assertThat(AttackLedger.grudge(game, nekro, sardakk)).isEqualTo(PRICE);
        assertThat(AttackLedger.grudge(
                        undone,
                        undone.getPlayerFromColorOrFaction("nekro"),
                        undone.getPlayerFromColorOrFaction("sardakk")))
                .isZero();
    }

    // The ships Sardakk destroyed while breaking the promise are in the ledger from the board diff; the betrayal adds
    // only the price, and reading the ledger again (as every tick does) does not add it again.
    @Test
    void theBreachsBoardDamageIsNotCountedTwice() {
        double boardDamage = 8;
        ledgerEntry(game.getRound(), sardakk, nekro, boardDamage);
        sardakkBreaks(1);
        AttackLedger.record(game);
        AttackLedger.record(game);

        assertThat(AttackLedger.grudge(game, nekro, sardakk)).isEqualTo(boardDamage + PRICE);
    }

    @Test
    void onlyBrokenPromisesToTheHolderCount() {
        PromiseBook.save(game, noAttack(sardakk, nekro, 1).with(Promise.Status.KEPT));
        PromiseBook.save(game, noAttack(sardakk, nekro, 2));
        PromiseBook.save(game, noAttack(nekro, sardakk, 3).with(Promise.Status.BROKEN));

        assertThat(AttackLedger.grudge(game, nekro, sardakk)).isZero();
        assertThat(AttackLedger.grudge(game, sardakk, nekro)).isEqualTo(PRICE);
    }

    // Nekro sold Sardakk the speaker token in round 2 for the first pick and named Imperial (8); Sardakk took it
    // anyway.
    @Test
    void aBrokenSpeakerSalePickCountsAsABetrayal() {
        game.setRound(3);
        game.setStoredValue("aiSpeakerSale", "nekro|sardakk|2|2|ACCEPTED|1|2|8");
        sardakk.addSC(8);

        assertThat(AttackLedger.grudge(game, nekro, sardakk))
                .isEqualTo(SpeakerSale.brokenPick(game, nekro, sardakk)
                        .orElseThrow()
                        .grievance());
        assertThat(AttackLedger.grudge(game, nekro, sardakk)).isPositive();
    }

    @Test
    void aSeatWithoutAttackMemoryIgnoresBetrayals() {
        AiExperiments.disable(nekro, EnumSet.of(Experiment.ATTACK_MEMORY));
        double before = hurtWeight();
        sardakkBreaks(1);

        assertThat(hurtWeight()).isEqualTo(before);
    }

    // Reading the book at grudge time decodes at most 48 records; a thousand grudge reads stay well under the cost of
    // one self-play tick.
    @Test
    void readingBetrayalsIsCheap() {
        for (int at = 0; at < 48; at++) sardakkBreaks(at);
        long start = System.nanoTime();
        double total = 0;
        for (int read = 0; read < 1000; read++) total += AttackLedger.grudge(game, nekro, sardakk);
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(total).isEqualTo(1000.0 * 48 * PRICE);
        assertThat(millis).isLessThan(2000);
    }
}
