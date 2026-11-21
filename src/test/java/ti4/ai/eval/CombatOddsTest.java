package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.CombatOdds.Detailed;
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

    // Munitions Reserves rerolls the misses of a round, so a side that pays for it hits more often: each paid round
    // helps, and a budget of zero rounds changes nothing.
    @Test
    void rerollingMissesInTheFirstRoundsImprovesTheOdds() {
        Force dreadnoughts = Force.of(List.of(DREADNOUGHT, DREADNOUGHT));

        double plain = CombatOdds.resolve(dreadnoughts, dreadnoughts).attackerWins();
        double oneRound = CombatOdds.resolve(dreadnoughts.rerollingMissesInFirstRounds(1), dreadnoughts)
                .attackerWins();
        double threeRounds = CombatOdds.resolve(dreadnoughts.rerollingMissesInFirstRounds(3), dreadnoughts)
                .attackerWins();

        assertThat(oneRound).isGreaterThan(plain + 0.05);
        assertThat(threeRounds).isGreaterThan(oneRound);
        assertThat(CombatOdds.resolve(dreadnoughts.rerollingMissesInFirstRounds(0), dreadnoughts)
                        .attackerWins())
                .isCloseTo(plain, within(1e-9));
    }

    // A cruiser duel with the attacker rerolling its first round: it then hits with 1 - 0.6 * 0.6 = 0.64 against the
    // defender's 0.4. It wins at once with 0.64 * 0.6, and 0.6 * 0.36 of the time goes on to a plain duel (0.375).
    @Test
    void aRerolledDieHitsWithTheChanceOfEitherRoll() {
        Force cruiser = Force.of(List.of(CRUISER));

        Outcome outcome = CombatOdds.resolve(cruiser.rerollingMissesInFirstRounds(1), cruiser);

        assertThat(outcome.attackerWins()).isCloseTo(0.64 * 0.6 + 0.36 * 0.6 * 0.375, within(1e-4));
    }

    @Test
    void theOtherSidesRerollsHurtTheOpponent() {
        Force dreadnoughts = Force.of(List.of(DREADNOUGHT, DREADNOUGHT));

        double plain = CombatOdds.resolve(dreadnoughts, dreadnoughts).attackerWins();
        double facingRerolls = CombatOdds.resolve(dreadnoughts, dreadnoughts.rerollingMissesInFirstRounds(2))
                .attackerWins();

        assertThat(facingRerolls).isLessThan(plain - 0.05);
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

    // One infantry each, hitting on 8 (30%). Every time the defender hits, the weave gives the attacker a hit too, so
    // the pair die together (0.3) or the attacker scores alone (0.7 * 0.3 = 0.21) and the defender can never win.
    // Over the rounds that nobody scores (0.49): both die 0.3 / 0.51, the attacker wins 0.21 / 0.51.
    @Test
    void particleWeaveAnswersEveryHitTheOpponentScores() {
        Force weavers = Force.of(List.of(INFANTRY)).weaving();
        Force plain = Force.of(List.of(INFANTRY));

        Outcome outcome = CombatOdds.resolve(weavers, plain);

        assertThat(outcome.defenderWins()).isCloseTo(0.0, within(1e-6));
        assertThat(outcome.bothDestroyed()).isCloseTo(0.3 / 0.51, within(1e-4));
        assertThat(outcome.attackerWins()).isCloseTo(0.21 / 0.51, within(1e-4));
    }

    @Test
    void particleWeaveIsNothingWhenTheOpponentCannotHit() {
        Combatant harmless = new Combatant(11, 1, false, 0.5);
        Force weavers = Force.of(List.of(INFANTRY)).weaving();
        Force defenders = Force.of(List.of(harmless));

        double woven = CombatOdds.resolve(weavers, defenders).attackerWins();
        double plain =
                CombatOdds.resolve(Force.of(List.of(INFANTRY)), defenders).attackerWins();

        assertThat(woven).isCloseTo(plain, within(1e-9));
    }

    @Test
    void particleWeaveMakesTheWeaverTheFavouriteInAnEvenGroundFight() {
        List<Combatant> infantry = Collections.nCopies(3, INFANTRY);

        Outcome weaving = CombatOdds.resolve(Force.of(infantry).weaving(), Force.of(infantry));

        assertThat(weaving.attackerWins()).isGreaterThan(weaving.defenderWins() + 0.2);
    }

    // The mech is the attacker's only unit and cannot hit. The defender's single infantry hits every round: the mech
    // sustains it and, when it retaliates, kills the infantry in the same round; without the retaliation the second
    // round destroys the mech.
    @Test
    void aSustainingMechThatRetaliatesKillsTheUnitThatDamagedIt() {
        Combatant mech = new Combatant(11, 1, true, 2);
        Combatant sureHit = new Combatant(1, 1, false, 0.5);
        Force defenders = Force.of(List.of(sureHit));

        Outcome retaliating = CombatOdds.resolve(Force.of(List.of(mech.retaliatingOnSustain())), defenders);
        Outcome plain = CombatOdds.resolve(Force.of(List.of(mech)), defenders);

        assertThat(retaliating.attackerWins()).isCloseTo(1.0, within(1e-9));
        assertThat(plain.defenderWins()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void aMechOnlyRetaliatesForTheHitsItsSustainAbsorbs() {
        Combatant mech = new Combatant(11, 1, true, 2).retaliatingOnSustain();
        Combatant sureHit = new Combatant(1, 1, false, 0.5);
        Force defenders = Force.of(List.of(sureHit, sureHit, sureHit));

        Detailed detailed = CombatOdds.resolveDetailed(Force.of(List.of(mech)), defenders);

        assertThat(detailed.outcome().defenderWins()).isCloseTo(1.0, within(1e-9));
        assertThat(detailed.defenderCostLost()).isCloseTo(0.5, within(1e-9));
    }
}
