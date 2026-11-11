package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.actioncards.ActionCardValue;
import ti4.ai.eval.GameHorizon;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class ExpertLawValuesTest extends BaseTi4Test {

    private static final String QUANN = "25";
    private static final String LODOR = "26";
    private static final String MECATOL = "18";
    private static final String HACAN_HOME = "16";
    private static final List<String> COLORS = List.of("yellow", "red", "green", "purple", "orange", "pink");

    private AiTestGame test;
    private Player nekro;
    private Player sol;
    private Player hacan;
    private int seats;

    // Nekro (AI), Sol (speaker) and Hacan, with Mecatol in the centre and no homes placed unless a test needs them.
    // The expert value table is on by default, so AgendaValues.of goes through ExpertLawValues first.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        nekro = test.nekro;
        sol = test.sol;
        hacan = seat("hacan");
        test.place(MECATOL, "000");
        test.game.setSpeaker(sol);
    }

    private Player seat(String faction) {
        String color = COLORS.get(seats);
        return test.addSeat("30000000000000000" + ++seats, faction, color);
    }

    private double value(Player voter, String agenda, String outcome, String ownVote) {
        OptionalDouble value = AgendaValues.of(test.game, voter, true, agenda, outcome, ownVote);
        assertThat(value).as("%s / %s", agenda, outcome).isPresent();
        return value.getAsDouble();
    }

    private AgendaView ownView(Player voter) {
        return new AgendaView(test.game, voter, true);
    }

    private static void hand(Player player, String... cards) {
        for (int index = 0; index < cards.length; index++) player.setActionCard(cards[index], 500 + index);
    }

    // Every law in this group gives a finite value for each outcome and own vote, and an outcome that is neither
    // For nor Against is left to the legacy formula.
    @Test
    void everyLawInTheGroupHasAFiniteValue() {
        test.nekroHome();
        List<String> laws = List.of(
                "revolution",
                "conventions",
                "travel_ban",
                "sanctions",
                "regulations",
                "defense_act",
                "schematics",
                "conscription",
                "shared_research",
                "wormhole_recon",
                "representative_government",
                "rep_govt",
                "checks",
                "nexus");
        for (String law : laws) {
            for (String outcome : List.of("for", "against")) {
                for (String ownVote : List.of("", "for", "against")) {
                    Double expert = ExpertLawValues.valueOf(ownView(nekro), law, outcome, ownVote);
                    assertThat(expert).as("%s / %s / %s", law, outcome, ownVote).isNotNull();
                    assertThat(expert).isFinite();
                }
            }
            assertThat(ExpertLawValues.valueOf(ownView(nekro), law, "nekro", ""))
                    .isNull();
        }
        assertThat(ExpertLawValues.valueOf(ownView(nekro), "articles_war", "for", ""))
                .isNull();
    }

    // AIR For costs 1.5 TG per round left, counted to the real horizon rather than the 5-round law cap: a 14-point
    // game with nobody scored has 7 rounds left. Nekro's Propagation means it never researches, so it pays nothing.
    @Test
    void antiIntellectualRevolutionCostsOneAndAHalfPerRoundLeftExceptForNekro() {
        test.game.setVp(14);
        int roundsLeft = GameHorizon.roundsLeft(test.game);
        assertThat(roundsLeft).isEqualTo(7);

        assertThat(ExpertLawValues.researchCost(test.game, hacan)).isCloseTo(1.5 * roundsLeft, within(1e-9));
        assertThat(ExpertLawValues.researchCost(test.game, nekro)).isZero();
        assertThat(value(nekro, "revolution", "for", "")).isPositive();
        assertThat(value(hacan, "revolution", "for", "")).isNegative();
    }

    // Conventions of War: nobody cares about For, and Against only costs the seats that voted Against, so abstaining
    // and letting the speaker pick Against is free.
    @Test
    void conventionsOfWarIsNearlyFreeUnlessYouVoteAgainst() {
        hand(nekro, "bribery", "cripple");

        assertThat(value(nekro, "conventions", "for", "")).isZero();
        assertThat(value(nekro, "conventions", "against", "")).isZero();
        assertThat(value(nekro, "conventions", "against", "against")).isNegative();
    }

    // Creuss ("ghost") likes the travel ban.
    @Test
    void creussLikesTheTravelBan() {
        Player creuss = seat("ghost");

        assertThat(ExpertLawValues.travelBanGain(test.game, creuss)).isPositive();
        assertThat(ExpertLawValues.travelBanGain(test.game, hacan)).isZero();
    }

    // A passive faction (Hacan) with a wormhole in its slice likes the ban: it keeps raiders out.
    @Test
    void weakPlasticFactionWithASliceWormholeLikesTheTravelBan() {
        test.place(HACAN_HOME, AiTestGame.HOME);
        test.place(QUANN, AiTestGame.neighbourOf(AiTestGame.HOME));

        assertThat(ExpertLawValues.travelBanGain(test.game, hacan)).isPositive();
        assertThat(value(hacan, "travel_ban", "for", "")).isPositive();
    }

    // An aggressive faction (Nekro) with a wormhole in its slice loses its fast lane out.
    @Test
    void aggressiveFactionWithASliceWormholeDislikesTheTravelBan() {
        test.nekroHome();
        test.place(QUANN, AiTestGame.neighbourOf(AiTestGame.HOME));

        assertThat(ExpertLawValues.travelBanGain(test.game, nekro)).isNegative();
        assertThat(value(nekro, "travel_ban", "for", "")).isNegative();
    }

    // Executive Sanctions For costs the cards beyond the holder's three best; with three cards or fewer it is free.
    @Test
    void executiveSanctionsCostsTheCardsBeyondTheThreeBest() {
        hand(nekro, "bribery", "cripple", "dh1");
        assertThat(ExpertLawValues.sanctionedCards(ownView(nekro), nekro)).isZero();

        // Two more Direct Hits push Bribery and Cripple, the weakest cards here, beyond the three kept.
        hand(nekro, "dh2", "dh3");
        double total = nekro.getActionCards().keySet().stream()
                .mapToDouble(alias -> ActionCardValue.keepValue(test.game, nekro, alias))
                .sum();
        double cost = ExpertLawValues.sanctionedCards(ownView(nekro), nekro);
        assertThat(cost).isPositive().isLessThan(total);
    }

    // A rival's hand is never read: only its size counts, so swapping its cards for others changes nothing.
    @Test
    void executiveSanctionsReadsOnlyTheSizeOfARivalsHand() {
        hand(sol, "bribery", "cripple", "dh1", "abs", "bunker");
        double before = value(nekro, "sanctions", "for", "");

        sol.getActionCards().clear();
        hand(sol, "assassin", "assassin2", "assassin3", "assassin4", "bribery2");
        assertThat(value(nekro, "sanctions", "for", "")).isEqualTo(before);
        assertThat(ExpertLawValues.sanctionedCards(ownView(nekro), sol)).isPositive();
    }

    @Test
    void yssarilIsExemptFromExecutiveSanctions() {
        Player yssaril = seat("yssaril");
        hand(yssaril, "bribery", "cripple", "dh1", "abs", "bunker");

        assertThat(ExpertLawValues.sanctionedCards(ownView(yssaril), yssaril)).isZero();
    }

    // Letnev's Armada keeps two more ships per system than its fleet tokens, so the four-token cap hurts it less.
    @Test
    void fleetRegulationsHurtLetnevLess() {
        Player letnev = seat("letnev");
        letnev.setFleetCC(5);
        hacan.setFleetCC(5);

        assertThat(ExpertLawValues.fleetCapCost(test.game, letnev))
                .isPositive()
                .isLessThan(ExpertLawValues.fleetCapCost(test.game, hacan));
    }

    @Test
    void homelandDefenseActForIsWorthNothing() {
        for (Player voter : List.of(nekro, sol, hacan))
            assertThat(value(voter, "defense_act", "for", "")).isZero();
    }

    // A war sun owner with a hand prefers For: Against discards its whole hand, For only costs sustain damage.
    @Test
    void warSunOwnerWithAHandWantsWeaponSchematicsFor() {
        sol.addTech("ws");
        hand(sol, "bribery", "cripple", "dh1");

        assertThat(value(sol, "schematics", "for", "")).isGreaterThan(value(sol, "schematics", "against", ""));
    }

    // Cabal and L1Z1X like Regulated Conscription; Naalu and Sol are fighter-heavy and are hurt the most.
    @Test
    void regulatedConscriptionSuitsCabalAndL1z1xAndHurtsFighterHeavyFactions() {
        Player cabal = seat("cabal");
        Player l1z1x = seat("l1z1x");
        Player naalu = seat("naalu");

        assertThat(ExpertLawValues.conscriptionGain(test.game, cabal, 0)).isPositive();
        assertThat(ExpertLawValues.conscriptionGain(test.game, l1z1x, 0)).isPositive();
        double hacanLoss = ExpertLawValues.conscriptionGain(test.game, hacan, 0);
        assertThat(hacanLoss).isNegative();
        assertThat(ExpertLawValues.conscriptionGain(test.game, naalu, 0)).isLessThan(hacanLoss);
        assertThat(ExpertLawValues.conscriptionGain(test.game, sol, 0)).isLessThan(hacanLoss);
    }

    // Shared Research For only matters to the Last Bastion ("bastion").
    @Test
    void onlyTheLastBastionCaresAboutSharedResearchFor() {
        Player bastion = seat("bastion");

        assertThat(ExpertLawValues.bastionNebulaEdge(test.game, bastion)).isPositive();
        assertThat(ExpertLawValues.bastionNebulaEdge(test.game, hacan)).isZero();
        assertThat(value(hacan, "shared_research", "for", "")).isNegative();
    }

    // Shared Research Against locks each home system: the more ships parked at home, the more it costs.
    @Test
    void sharedResearchAgainstCountsTheShipsLockedAtHome() {
        Tile home = test.place(HACAN_HOME, AiTestGame.HOME);
        double empty = ExpertLawValues.homeLockCost(test.game, hacan);
        test.units(home, "space", hacan, UnitType.Dreadnought, 2);

        assertThat(ExpertLawValues.homeLockCost(test.game, hacan)).isGreaterThan(empty);
    }

    // Wormhole Reconstruction For helps the big fleets and hurts the small ones; Against costs whoever has ships
    // sitting in wormhole systems.
    @Test
    void wormholeReconstructionFavoursStrongPlasticAndAgainstCountsLockedShips() {
        Tile lodor = test.place(LODOR, AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(lodor, "space", sol, UnitType.Dreadnought, 3);

        assertThat(value(sol, "wormhole_recon", "for", "")).isPositive();
        assertThat(value(nekro, "wormhole_recon", "for", "")).isNegative();
        assertThat(ExpertLawValues.lockedShipCost(test.game, sol)).isPositive();
        assertThat(ExpertLawValues.lockedShipCost(test.game, nekro)).isZero();
        assertThat(value(nekro, "wormhole_recon", "against", "")).isPositive();
    }

    // Representative Government is worth at most 2 TG a round either way: Nekro holds all the influence here.
    @Test
    void representativeGovernmentIsCappedAtTwoPerRound() {
        test.place(LODOR, AiTestGame.HOME);
        nekro.addPlanet("lodor");
        double rounds = AgendaValueMath.lawRounds(test.game);

        assertThat(value(nekro, "rep_govt", "for", "")).isCloseTo(-2 * rounds, within(1e-9));
        assertThat(value(hacan, "representative_government", "for", ""))
                .isPositive()
                .isLessThanOrEqualTo(2 * rounds);
    }

    // Checks and Balances: the last seat in speaker order likes For, the speaker dislikes it.
    @Test
    void checksAndBalancesSuitsLateSpeakerOrderSeats() {
        List<Player> order = Helper.getSpeakerOrFullPriorityOrder(test.game);
        assertThat(order.getFirst()).isEqualTo(sol);

        assertThat(value(order.getLast(), "checks", "for", "")).isPositive();
        assertThat(value(sol, "checks", "for", "")).isNegative();
    }

    // An early seat that fears the speaker winning drops its dislike; one behind on points likes it too.
    @Test
    void earlySeatStopsMindingChecksAndBalancesWhenTheSpeakerIsAContender() {
        seat("xxcha");
        List<Player> order = Helper.getSpeakerOrFullPriorityOrder(test.game);
        Player early = order.get(1);
        assertThat(ExpertLawValues.speakerOrderLean(test.game, early)).isNegative();

        test.game.setVp(10);
        test.points(sol, 9);
        assertThat(ExpertLawValues.speakerOrderLean(test.game, early)).isZero();
        assertThat(value(early, "checks", "for", "")).isPositive();
    }

    // Nexus Sovereignty For suits Creuss and the seat holding Mallice.
    @Test
    void creussAndTheMalliceHolderLikeNexusSovereignty() {
        Player creuss = seat("ghost");
        assertThat(value(creuss, "nexus", "for", "")).isPositive();
        assertThat(value(hacan, "nexus", "for", "")).isZero();

        hacan.addPlanet("mallice");
        assertThat(value(hacan, "nexus", "for", "")).isPositive();
    }
}
