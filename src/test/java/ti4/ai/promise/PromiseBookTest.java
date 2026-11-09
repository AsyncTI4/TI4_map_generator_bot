package ti4.ai.promise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// The promise book is the AI's record of promise deals: stored on the game, so it rolls back with /game undo like the
// speaker sale record. Broken promises outlive their round so a later attack-memory ledger can read them. A system
// is activated by placing a command token in it (LRR 5.1) and cannot be activated again while it holds one (LRR 5.2),
// so a new token of the promiser's in a protected system is the sign of a broken NO_ATTACK or STAY_OUT promise.
class PromiseBookTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile lodor;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        lodor = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(lodor, "space", test.sol, UnitType.Carrier, 1);
        test.game.setPhaseOfGame("action");
    }

    private Promise offer(Promise.Term term, String target, long at) {
        return Promise.offer(test.game, term, test.nekro, test.sol, test.nekro, "", target, 0, 2, at, at + 60_000);
    }

    @Test
    void aSavedPromiseReadsBackUnchanged() {
        Promise promise = offer(Promise.Term.STAY_OUT, lodor.getPosition(), 5).accepted(test.game);
        PromiseBook.save(test.game, promise);

        assertThat(PromiseBook.find(test.game, promise.id())).contains(promise);
        assertThat(promise.guarded()).containsExactly(lodor.getPosition());
    }

    // A NO_ATTACK snapshot lists the systems that hold the promisee's units or planets, which the promiser has not
    // activated yet; those are the systems it may not activate this round.
    @Test
    void acceptingSnapshotsTheProtectedSystems() {
        Promise promise = offer(Promise.Term.NO_ATTACK, "", 5).accepted(test.game);

        assertThat(promise.status()).isEqualTo(Promise.Status.ACCEPTED);
        assertThat(promise.guarded()).containsExactly(lodor.getPosition());
        assertThat(promise.held()).containsExactly(AiTestGame.HOME);
    }

    @Test
    void anOldRoundIsForgottenButABrokenPromiseIsKept() {
        PromiseBook.save(test.game, offer(Promise.Term.NO_ATTACK, "", 1).with(Promise.Status.KEPT));
        PromiseBook.save(test.game, offer(Promise.Term.STAY_OUT, "x", 2).with(Promise.Status.BROKEN));
        test.game.setRound(3);
        PromiseBook.save(test.game, offer(Promise.Term.NO_ATTACK, "", 3));

        assertThat(PromiseBook.all(test.game))
                .extracting(Promise::status)
                .containsExactly(Promise.Status.BROKEN, Promise.Status.OFFERED);
        assertThat(PromiseBook.brokenAgainst(test.game, test.sol)).hasSize(1);
        assertThat(PromiseBook.brokenBy(test.game, test.nekro, test.sol)).isEqualTo(1);
        assertThat(PromiseBook.brokenAgainst(test.game, test.nekro)).isEmpty();
    }

    @Test
    void anOwnOfferBindsTheOffererButAnOfferToItDoesNot() {
        Promise own = offer(Promise.Term.NO_ATTACK, "", 1);
        Promise asked = Promise.offer(
                test.game, Promise.Term.NO_ATTACK, test.nekro, test.sol, test.sol, "", "", 0, 2, 2, 60_000);

        assertThat(own.binds()).isTrue();
        assertThat(asked.binds()).isFalse();
        assertThat(asked.accepted(test.game).binds()).isTrue();
    }

    @Test
    void controlSeatHasNoBindingPromises() {
        PromiseBook.save(test.game, offer(Promise.Term.NO_ATTACK, "", 1).accepted(test.game));
        assertThat(PromiseBook.forbidsActivating(test.game, test.nekro, lodor)).isTrue();

        AiExperiments.disable(test.nekro, Set.of(Experiment.PROMISE_DEALS));

        assertThat(PromiseBook.bindingOn(test.game, test.nekro)).isEmpty();
        assertThat(PromiseBook.forbidsActivating(test.game, test.nekro, lodor)).isFalse();
    }

    // Breaking: the promiser activates a protected system (its command token is there) and fights or lands there.
    @Test
    void activatingAProtectedSystemBreaksANoAttackPromise() {
        Promise promise = offer(Promise.Term.NO_ATTACK, "", 1).accepted(test.game);
        assertThat(PromiseChecks.territorial(test.game, promise)).isEmpty();

        lodor.addCC(Mapper.getCCID(test.nekro.getColor()));
        test.units(lodor, "space", test.nekro, UnitType.Dreadnought, 1);

        assertThat(PromiseChecks.territorial(test.game, promise)).contains(Promise.Status.BROKEN);
    }

    // The promisee attacking the promiser calls the deal off rather than leaving the promiser bound.
    @Test
    void thePromiseeAttackingThePromiserVoidsIt() {
        Promise promise = offer(Promise.Term.NO_ATTACK, "", 1).accepted(test.game);

        test.game.getTileByPosition(AiTestGame.HOME).addCC(Mapper.getCCID(test.sol.getColor()));

        assertThat(PromiseChecks.territorial(test.game, promise)).contains(Promise.Status.VOID);
    }

    @Test
    void aStayOutPromiseIsOnlyBrokenByShipsMovingIn() {
        Tile empty = test.place("19", "310");
        Promise promise = offer(Promise.Term.STAY_OUT, empty.getPosition(), 1).accepted(test.game);

        empty.addCC(Mapper.getCCID(test.nekro.getColor()));
        assertThat(PromiseChecks.territorial(test.game, promise)).isEmpty();

        test.units(empty, "space", test.nekro, UnitType.Destroyer, 1);
        assertThat(PromiseChecks.territorial(test.game, promise)).contains(Promise.Status.BROKEN);
    }

    @Test
    void aPromiseThatLastedTheRoundWasKept() {
        Promise promise = offer(Promise.Term.NO_ATTACK, "", 1).accepted(test.game);
        test.game.setRound(3);

        assertThat(PromiseChecks.territorial(test.game, promise)).contains(Promise.Status.KEPT);
    }
}
