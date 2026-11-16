package ti4.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The user's rule (2026-11-16): hostility is "how much TG value this other player has cost me this game", next to a
// player who never attacked or took planets and only boat floated. Attacks and planets taken come in at ship cost
// lost and planet value, x3 in the victim's home and x1.5 in its slice; mean "choose a player" cards cost 2 TG and
// Sabotage 1 TG; each round, ships parked in the victim's slice cost 1 TG, over its home 3 TG, and over its
// undefended space dock 2 TG (the largest that applies to the system, not the sum). Every 2 TG lowers relations by 1,
// with no floor.
class HostilityTest extends BaseTi4Test {

    private static final double CLOSE = 1e-9;

    // Sol's dock on a planet away from both homes, with a Nekro cruiser over it and no Sol ship there.
    private static Tile solDockBlockadedByNekro(AiTestGame test) {
        Tile tile = test.place("19", "311");
        test.units(tile, "wellon", test.sol, UnitType.Spacedock, 1);
        test.units(tile, "space", test.nekro, UnitType.Cruiser, 1);
        return tile;
    }

    @Test
    void costsAddUpPerPair() {
        AiTestGame test = AiTestGame.withSolAi();
        Hostility.add(test.game, "sol", "nekro", 1.5);
        Hostility.add(test.game, "sol", "nekro", 2.0);

        assertThat(Hostility.amount(test.game, test.nekro, test.sol)).isCloseTo(3.5, within(CLOSE));
        assertThat(Hostility.amount(test.game, test.sol, test.nekro)).isZero();
    }

    // A blockade costs again every round it stands, but only once a round.
    @Test
    void aBlockadeCostsEachRoundItStands() {
        AiTestGame test = AiTestGame.withSolAi();
        solDockBlockadedByNekro(test);

        Hostility.record(test.game);
        Hostility.record(test.game);
        double oneRound = Hostility.amount(test.game, test.sol, test.nekro);
        assertThat(oneRound).isCloseTo(Hostility.BLOCKADE_COST, within(CLOSE));

        test.game.setRound(3);
        Hostility.record(test.game);
        assertThat(Hostility.amount(test.game, test.sol, test.nekro)).isCloseTo(2 * oneRound, within(CLOSE));
    }

    // A Sol cruiser in the system means the dock is not blockaded.
    @Test
    void shipsOfTheDockOwnerLiftTheBlockade() {
        AiTestGame test = AiTestGame.withSolAi();
        Tile tile = solDockBlockadedByNekro(test);
        test.units(tile, "space", test.sol, UnitType.Cruiser, 1);
        double parked = Hostility.amount(test.game, test.sol, test.nekro);

        Hostility.record(test.game);
        assertThat(Hostility.amount(test.game, test.sol, test.nekro) - parked).isLessThan(Hostility.BLOCKADE_COST);
    }

    // Parking over Sol's home system costs the home rate each round.
    @Test
    void shipsOverTheHomeSystemUpTheAnte() {
        AiTestGame test = AiTestGame.withSolAi();
        Tile home = test.solHomeBeside("000");
        test.units(home, "space", test.nekro, UnitType.Cruiser, 1);

        Hostility.record(test.game);
        assertThat(Hostility.amount(test.game, test.sol, test.nekro))
                .isCloseTo(Hostility.HOME_PRESENCE_COST, within(CLOSE));
    }

    // Nekro sabotages Sol's Plague: Sol holds it against Nekro.
    @Test
    void sabotageAngersThePlayerWhoseCardItCancels() {
        AiTestGame test = AiTestGame.withSolAi();
        test.game.getGameStats().recordAcPlay("Plague", test.sol);
        test.game.getGameStats().markLatestPlayCanceled("Plague");
        test.game.getGameStats().recordAcPlay("Sabotage", test.nekro);

        Hostility.record(test.game);
        assertThat(Hostility.amount(test.game, test.sol, test.nekro)).isCloseTo(Hostility.SABOTAGE_COST, within(CLOSE));
        assertThat(Hostility.amount(test.game, test.nekro, test.sol)).isZero();
    }

    // Sol resolves Plague and Uprising on Nekro: each mean card costs 2 TG, once.
    @Test
    void meanCardsCostTheirTarget() {
        AiTestGame test = AiTestGame.withSolAi();
        test.game.getGameStats().recordAcTarget("Plague", test.sol, test.nekro);
        test.game.getGameStats().recordAcTarget("Uprising", test.sol, test.nekro);

        Hostility.record(test.game);
        Hostility.record(test.game);
        assertThat(Hostility.amount(test.game, test.nekro, test.sol))
                .isCloseTo(2 * Hostility.MEAN_CARD_COST, within(CLOSE));
    }

    // Combat cards are part of the fight, not hostility of their own.
    @Test
    void combatCardsDoNotCount() {
        AiTestGame test = AiTestGame.withSolAi();
        test.isActive(test.nekro, "action");
        test.game.getGameStats().recordAcPlay("Direct Hit", test.sol);

        Hostility.record(test.game);
        assertThat(Hostility.amount(test.game, test.nekro, test.sol)).isZero();
    }
}
