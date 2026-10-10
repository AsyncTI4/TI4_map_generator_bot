package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.CombatOdds.Force;
import ti4.ai.eval.CombatOdds.Outcome;

class CombatOddsTest {

    private static final Combatant DREADNOUGHT = new Combatant(5, 1, true, 4);
    private static final Combatant CRUISER = new Combatant(7, 1, false, 2);
    private static final Combatant FIGHTER = new Combatant(9, 1, false, 0.5);
    private static final Combatant INFANTRY = new Combatant(8, 1, false, 0.5);

    @Test
    void outcomesAddUpToOne() {
        Outcome outcome = CombatOdds.resolve(List.of(CRUISER, FIGHTER), List.of(DREADNOUGHT));

        assertThat(outcome.attackerWins() + outcome.defenderWins() + outcome.bothDestroyed())
                .isCloseTo(1.0, within(1e-4));
    }

    @Test
    void identicalSidesAreEvenlyMatched() {
        Outcome outcome = CombatOdds.resolve(List.of(CRUISER), List.of(CRUISER));

        assertThat(outcome.attackerWins()).isCloseTo(outcome.defenderWins(), within(1e-6));
    }

    // One cruiser against one cruiser: each round both roll a 7+ (40%). The attacker wins when it hits and the
    // defender misses: 0.4 * 0.6 / (1 - 0.6 * 0.6) = 0.375.
    @Test
    void matchesTheClosedFormForASingleDuel() {
        Outcome outcome = CombatOdds.resolve(List.of(CRUISER), List.of(CRUISER));

        assertThat(outcome.attackerWins()).isCloseTo(0.375, within(1e-4));
        assertThat(outcome.bothDestroyed()).isCloseTo(0.25, within(1e-4));
    }

    @Test
    void overwhelmingForceAlmostAlwaysWins() {
        Outcome outcome = CombatOdds.resolve(List.of(DREADNOUGHT, DREADNOUGHT, DREADNOUGHT), List.of(FIGHTER));

        assertThat(outcome.attackerWins()).isGreaterThan(0.95);
    }

    // Sustain damage absorbs a hit, so a dreadnought beats a cruiser more often than a cruiser beats a cruiser.
    @Test
    void sustainDamageHelps() {
        double cruiserVsCruiser =
                CombatOdds.resolve(List.of(CRUISER), List.of(CRUISER)).attackerWins();
        double dreadnoughtVsCruiser =
                CombatOdds.resolve(List.of(DREADNOUGHT), List.of(CRUISER)).attackerWins();

        assertThat(dreadnoughtVsCruiser).isGreaterThan(cruiserVsCruiser);
    }

    @Test
    void moreInfantryWinsGroundCombatMoreOften() {
        double twoVsOne = CombatOdds.resolve(List.of(INFANTRY, INFANTRY), List.of(INFANTRY))
                .attackerWins();
        double fourVsOne = CombatOdds.resolve(Collections.nCopies(4, INFANTRY), List.of(INFANTRY))
                .attackerWins();

        assertThat(fourVsOne).isGreaterThan(twoVsOne);
    }

    @Test
    void anEmptySideLosesImmediately() {
        assertThat(CombatOdds.resolve(List.of(), List.of(FIGHTER)).attackerWins())
                .isZero();
        assertThat(CombatOdds.resolve(List.of(FIGHTER), List.of()).attackerWins())
                .isEqualTo(1.0);
    }

    // X-89 Bacterial Weapon ΩΩ doubles ground combat hits: one infantry hit kills two defenders. Three infantry
    // against four are underdogs without it and favourites with it.
    @Test
    void doubledHitsTurnAGroundFight() {
        Force attackers = Force.of(List.of(INFANTRY, INFANTRY, INFANTRY));
        Force defenders = Force.of(List.of(INFANTRY, INFANTRY, INFANTRY, INFANTRY));

        double plain = CombatOdds.resolve(attackers, defenders).attackerWins();
        double doubled = CombatOdds.resolve(attackers.doublingHits(), defenders).attackerWins();

        assertThat(plain).isLessThan(0.35);
        assertThat(doubled).isGreaterThan(0.5);
    }

    // Duranium Armor repairs a unit damaged in an earlier round, so dreadnoughts outlast a cruiser swarm more often.
    @Test
    void duraniumArmorRepairsBetweenRounds() {
        Force dreadnoughts = Force.of(List.of(DREADNOUGHT, DREADNOUGHT));
        Force cruisers = Force.of(List.of(CRUISER, CRUISER, CRUISER, CRUISER));

        double plain = CombatOdds.resolve(dreadnoughts, cruisers).attackerWins();
        double repaired = CombatOdds.resolve(dreadnoughts.repairing(), cruisers).attackerWins();

        assertThat(repaired).isGreaterThan(plain + 0.03);
    }

    // Nekro copies a technology after the opponent's first loss: fighters that become Fighter IIs then help in every
    // later round, which is better than never upgrading and worse than starting upgraded.
    @Test
    void anUpgradeAfterTheFirstKillHelpsTheRestOfTheFight() {
        Combatant fighterTwo = new Combatant(8, 1, false, 0.5);
        List<Combatant> fighters = Collections.nCopies(6, FIGHTER);
        List<Combatant> upgraded = Collections.nCopies(6, fighterTwo);
        Force cruisers = Force.of(List.of(CRUISER, CRUISER, CRUISER));

        double never = CombatOdds.resolve(Force.of(fighters), cruisers).attackerWins();
        double later = CombatOdds.resolve(Force.of(fighters).improvingAfterOpponentLoss(Force.of(upgraded)), cruisers)
                .attackerWins();
        double always = CombatOdds.resolve(Force.of(upgraded), cruisers).attackerWins();

        assertThat(later).isGreaterThan(never).isLessThan(always);
    }
}
