package ti4.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
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

    // The user's rule (2026-11-21): a mean card costs what it actually took from its target. Sol aims a card at Nekro
    // and Nekro then loses 2 trade goods' worth before the window closes, so Sol is charged 2, once.
    @Test
    void meanCardsCostWhatTheyTook() {
        AiTestGame test = AiTestGame.withSolAi();
        test.isActive(test.sol, "action");
        test.nekro.setTg(5);
        Hostility.record(test.game);
        test.game.getGameStats().recordAcTarget("Plague", test.sol, test.nekro);
        Hostility.record(test.game);
        assertThat(Hostility.pending(test.game)).hasSize(1);

        test.nekro.setTg(3);
        Hostility.record(test.game);
        Hostility.record(test.game);
        assertThat(Hostility.amount(test.game, test.nekro, test.sol)).isCloseTo(2, within(CLOSE));
        assertThat(Hostility.pending(test.game)).isEmpty();
    }

    // A Tactical Bombardment used as a blank stall, on a system where Nekro has nothing readied, costs nothing: once
    // the turn passes the window closes with no loss.
    @Test
    void aBlankTacticalBombardmentCostsNothing() {
        AiTestGame test = AiTestGame.withSolAi();
        test.isActive(test.sol, "action");
        Hostility.record(test.game);
        test.game.getGameStats().recordAcTarget(Hostility.TACTICAL_BOMBARDMENT, test.sol, test.nekro);
        Hostility.record(test.game);

        test.isActive(test.nekro, "action");
        Hostility.record(test.game);
        assertThat(Hostility.amount(test.game, test.nekro, test.sol)).isZero();
        assertThat(Hostility.pending(test.game)).isEmpty();
    }

    // The user's rule (2026-11-21): Spy costs what was stolen. Stealing Overrule from Nekro costs Sol more than
    // stealing Diplomatic Pressure would.
    @Test
    void spyCostsWhatWasStolen() {
        double overrule = stolen("overrule");
        double pressure = stolen("dp1");

        assertThat(overrule).isPositive();
        assertThat(overrule).isGreaterThan(pressure);
    }

    private static double stolen(String alias) {
        AiTestGame test = AiTestGame.withSolAi();
        test.isActive(test.sol, "action");
        test.nekro.setActionCard(alias, 1);
        test.nekro.setActionCard("sabo1", 2);
        Hostility.record(test.game);
        test.game.getGameStats().recordAcTarget(Hostility.SPY, test.sol, test.nekro);
        Hostility.record(test.game);
        assertThat(Hostility.pending(test.game)).hasSize(1);

        test.nekro.removeActionCard(1);
        test.sol.setActionCard(alias, 3);
        Hostility.record(test.game);
        assertThat(Hostility.pending(test.game)).isEmpty();
        return Hostility.amount(test.game, test.nekro, test.sol);
    }

    // Assassinate Representative waits for the votes; if the agenda moves on before they are in, it cost nothing.
    @Test
    void anAssassinationIsJudgedOnItsAgenda() {
        AiTestGame test = AiTestGame.withSolAi();
        test.game.setStoredValue("agendaCount", "1");
        test.game.setCurrentAgendaInfo("Directive_Elect Player_7_execution");
        test.game.getGameStats().recordAcTarget(Hostility.ASSASSINATE, test.sol, test.nekro);
        Hostility.record(test.game);
        assertThat(Hostility.pending(test.game)).hasSize(1);

        test.game.setCurrentAgendaInfo("Law_Elect Player_8_minister_war");
        Hostility.record(test.game);
        assertThat(Hostility.pending(test.game)).isEmpty();
        assertThat(Hostility.amount(test.game, test.nekro, test.sol)).isZero();
    }

    // Ancient Burial Sites costs a set 0.5 TG.
    @Test
    void ancientBurialSitesCostsHalfATradeGood() {
        AiTestGame test = AiTestGame.withSolAi();
        test.game.getGameStats().recordAcTarget("Ancient Burial Sites", test.sol, test.nekro);

        Hostility.record(test.game);
        assertThat(Hostility.amount(test.game, test.nekro, test.sol)).isCloseTo(0.5, within(CLOSE));
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

    // The engine also records targets for combat cards, cards that help their target, and Sabotage (already counted
    // from the card plays above). None of them is a mean card; only the Plague counts.
    @Test
    void onlyMeanCardTargetsCount() {
        AiTestGame test = AiTestGame.withSolAi();
        for (String card :
                List.of("Direct Hit", "Intercept", "Salvage", "Forward Supply Base", "Brilliance", "Sabotage"))
            test.game.getGameStats().recordAcTarget(card, test.sol, test.nekro);
        test.game.getGameStats().recordAcTarget("Plague", test.sol, test.nekro);

        Hostility.record(test.game);
        assertThat(Hostility.pending(test.game)).hasSize(1);
        assertThat(Hostility.pending(test.game).getFirst().offender()).isEqualTo("sol");
    }

    // The user's note (2026-11-17): a tendency not to anger too many players at once. Nekro has already cost Sol and
    // Xxcha a trade good or more this round, so angering a third player weighs 50% more; next round it starts over.
    @Test
    void angeringMorePlayersInARoundCostsMore() {
        AiTestGame test = AiTestGame.withSolAi();
        ti4.game.Player xxcha = test.addSeat("7100000555555555", "xxcha", "red");
        ti4.game.Player hacan = test.addSeat("7100000666666666", "hacan", "yellow");
        assertThat(Hostility.spreadFactor(test.game, test.nekro, hacan)).isEqualTo(1);

        Hostility.add(test.game, "nekro", "sol", 2.0);
        Hostility.add(test.game, "nekro", "xxcha", 1.0);
        Hostility.add(test.game, "nekro", "hacan", 0.5);
        assertThat(Hostility.spreadFactor(test.game, test.nekro, hacan))
                .isCloseTo(1 + 2 * Hostility.SPREAD_STEP, within(CLOSE));
        assertThat(Hostility.spreadFactor(test.game, test.nekro, xxcha))
                .isCloseTo(1 + Hostility.SPREAD_STEP, within(CLOSE));

        test.game.setRound(test.game.getRound() + 1);
        assertThat(Hostility.spreadFactor(test.game, test.nekro, hacan)).isEqualTo(1);
        assertThat(Hostility.amount(test.game, test.sol, test.nekro)).isCloseTo(2.0, within(CLOSE));
    }

    // A Tactical Bombardment that exhausts Nekro's readied planets costs what those planets were worth to spend.
    @Test
    void aRealTacticalBombardmentCostsTheExhaustedPlanets() {
        AiTestGame test = AiTestGame.withSolAi();
        test.isActive(test.sol, "action");
        test.place("19", "311");
        test.nekro.addPlanet("wellon");
        test.nekro.refreshPlanet("wellon");
        Hostility.record(test.game);
        test.game.getGameStats().recordAcTarget(Hostility.TACTICAL_BOMBARDMENT, test.sol, test.nekro);
        test.nekro.exhaustPlanet("wellon");

        Hostility.record(test.game);
        ti4.game.Planet wellon = test.game.getPlanetsInfo().get("wellon");
        assertThat(Hostility.amount(test.game, test.nekro, test.sol))
                .isCloseTo(
                        ti4.ai.eval.ValueScale.spendable(wellon.getResources(), wellon.getInfluence()), within(CLOSE));
    }

    // The user's value (2026-11-21): Reparations costs half of what the planet it exhausts was worth to spend.
    @Test
    void reparationsCostsHalfTheExhaustedPlanet() {
        AiTestGame test = AiTestGame.withSolAi();
        test.isActive(test.sol, "action");
        test.place("19", "311");
        test.nekro.addPlanet("wellon");
        test.nekro.refreshPlanet("wellon");
        Hostility.record(test.game);
        test.game.getGameStats().recordAcTarget("Reparations", test.sol, test.nekro);
        Hostility.record(test.game);
        test.nekro.exhaustPlanet("wellon");

        Hostility.record(test.game);
        ti4.game.Planet wellon = test.game.getPlanetsInfo().get("wellon");
        assertThat(Hostility.amount(test.game, test.nekro, test.sol))
                .isCloseTo(
                        0.5 * ti4.ai.eval.ValueScale.spendable(wellon.getResources(), wellon.getInfluence()),
                        within(CLOSE));
    }
}
