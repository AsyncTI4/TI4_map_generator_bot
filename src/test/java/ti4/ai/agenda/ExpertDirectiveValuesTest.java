package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.ValueScale;
import ti4.ai.strategy.CardValueScale;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class ExpertDirectiveValuesTest extends BaseTi4Test {

    private static final double EPSILON = 1e-9;

    private AiTestGame test;
    private Player nekro;
    private Player sol;
    private Player hacan;
    private Tile mecatol;
    private Tile lodorTile;

    // Same three-seat table as AgendaValuesTest: Nekro and Sol at home, Hacan seated with nothing on the board, Lodor
    // beside Nekro's home and Mecatol Rex at the centre. The game plays to 10 points.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        nekro = test.nekro;
        sol = test.sol;
        hacan = test.addSeat("300000000000000003", "hacan", "yellow");
        test.game.setVp(10);
        test.nekroHome();
        String lodorPosition = AiTestGame.neighbourOf(AiTestGame.HOME);
        lodorTile = test.place("26", lodorPosition);
        test.place("24", "105");
        mecatol = test.place("18", "000");
        test.solHomeBeside(lodorPosition);
        test.game.setSpeaker(sol);
        for (Player seat : List.of(nekro, sol, hacan)) {
            seat.setTacticalCC(3);
            seat.setFleetCC(3);
            seat.setStrategicCC(2);
        }
    }

    private double expert(Player voter, String agenda, String outcome, String ownVote) {
        Double value = ExpertDirectiveValues.valueOf(new AgendaView(test.game, voter, true), agenda, outcome, ownVote);
        assertThat(value).as("%s / %s", agenda, outcome).isNotNull();
        return value;
    }

    private double expert(Player voter, String agenda, String outcome) {
        return expert(voter, agenda, outcome, "");
    }

    // The groups left on the legacy formula return null so AgendaValues falls back.
    @Test
    void agendasOutsideTheGroupFallBackToLegacy() {
        for (String agenda : List.of(
                "mutiny",
                "seed_empire",
                "unconventional",
                "rearmament",
                "abolishment",
                "miscount",
                "constitution",
                "minister_war")) {
            assertThat(ExpertDirectiveValues.valueOf(new AgendaView(test.game, nekro, true), agenda, "for", "for"))
                    .as(agenda)
                    .isNull();
        }
    }

    // The default experiment set routes Arms Reduction through this class.
    @Test
    void agendaValuesUsesTheExpertValueByDefault() {
        test.points(sol, 9);
        test.units(lodorTile, "space", sol, UnitType.Dreadnought, 5);

        assertThat(AgendaValues.of(test.game, nekro, true, "arms_reduction", "for", "")
                        .getAsDouble())
                .isCloseTo(expert(nekro, "arms_reduction", "for"), within(EPSILON));
    }

    // Arms Reduction For costs Sol three excess dreadnoughts. When Sol is a contender, the table weighs that loss more
    // than the plain threat-weighted legacy formula, so Nekro likes For more.
    @Test
    void armsReductionLikesHurtingTheLeaderMost() {
        test.points(sol, 9);
        test.units(lodorTile, "space", sol, UnitType.Dreadnought, 5);

        double expertFor = expert(nekro, "arms_reduction", "for");
        double legacyFor = DirectiveValues.armsReduction(test.game, nekro, "for");
        assertThat(expertFor).isPositive();
        assertThat(expertFor).isGreaterThan(legacyFor);
    }

    // A seat that would lose its own excess dreadnoughts counts that loss against For.
    @Test
    void armsReductionForHurtsTheSeatWithExcessDreadnoughts() {
        test.units(lodorTile, "space", nekro, UnitType.Dreadnought, 5);

        assertThat(expert(nekro, "arms_reduction", "for")).isNegative();
    }

    // Nekro has no trade goods, so For's 5 trade goods help it, and the small For bias is added on top.
    @Test
    void economicEqualityAddsASmallForBiasForASeatNotBadlyHurt() {
        double legacy = DirectiveValues.economicEquality(test.game, nekro, "for");

        assertThat(expert(nekro, "economic_equality", "for"))
                .isCloseTo(legacy + ExpertDirectiveValues.EQUALITY_FOR_BIAS, within(EPSILON));
    }

    // Nekro sits on 12 trade goods and would drop to 5, a 7 TG loss, so it gets no For bias.
    @Test
    void economicEqualityDropsTheBiasWhenTheSeatIsBadlyHurt() {
        nekro.setTg(12);
        double legacy = DirectiveValues.economicEquality(test.game, nekro, "for");

        assertThat(expert(nekro, "economic_equality", "for")).isCloseTo(legacy, within(EPSILON));
        assertThat(expert(nekro, "economic_equality", "for")).isNegative();
    }

    // Against just wipes trade goods; with all seats on zero nobody loses anything.
    @Test
    void economicEqualityAgainstCountsTheTradeGoodsLost() {
        sol.setTg(6);

        assertThat(expert(nekro, "economic_equality", "against")).isPositive();
        assertThat(expert(sol, "economic_equality", "against")).isNegative();
    }

    // A seat 3 points ahead of the table likes For (stage I) and dislikes Against (stage II) by the lead term.
    @Test
    void incentiveProgramLeaderLikesForAndTheTrailerLikesAgainst() {
        test.points(nekro, 5);
        test.points(sol, 2);
        test.points(hacan, 2);

        double lead = ExpertDirectiveValues.INCENTIVE_LEAD_PER_POINT * 3;
        assertThat(expert(nekro, "incentive", "for"))
                .isCloseTo(DirectiveValues.incentiveProgram(test.game, nekro, "for") + lead, within(EPSILON));
        assertThat(expert(nekro, "incentive", "against"))
                .isCloseTo(DirectiveValues.incentiveProgram(test.game, nekro, "against") - lead, within(EPSILON));
        assertThat(expert(sol, "incentive", "against") - DirectiveValues.incentiveProgram(test.game, sol, "against"))
                .isPositive();
    }

    // Hacan (alias "hacan") gets the stage II faction bonus on Against; Nekro does not.
    @Test
    void incentiveProgramHacanLikesAgainst() {
        assertThat(expert(hacan, "incentive", "against")
                        - DirectiveValues.incentiveProgram(test.game, hacan, "against"))
                .isCloseTo(ExpertDirectiveValues.INCENTIVE_STAGE_TWO_FACTION_BONUS, within(EPSILON));
        assertThat(expert(nekro, "incentive", "against")
                        - DirectiveValues.incentiveProgram(test.game, nekro, "against"))
                .isCloseTo(0, within(EPSILON));
    }

    // Jol-Nar's alias is "jolnar".
    @Test
    void incentiveProgramJolNarLikesAgainst() {
        Player jolnar = test.addSeat("300000000000000004", "jolnar", "purple");

        assertThat(expert(jolnar, "incentive", "against")
                        - DirectiveValues.incentiveProgram(test.game, jolnar, "against"))
                .isCloseTo(ExpertDirectiveValues.INCENTIVE_STAGE_TWO_FACTION_BONUS, within(EPSILON));
    }

    // Deepwrought (Thunder's Edge) has the alias "deepwrought".
    @Test
    void incentiveProgramDeepwroughtLikesAgainst() {
        Player deepwrought = test.addSeat("300000000000000005", "deepwrought", "green");

        assertThat(expert(deepwrought, "incentive", "against")
                        - DirectiveValues.incentiveProgram(test.game, deepwrought, "against"))
                .isCloseTo(ExpertDirectiveValues.INCENTIVE_STAGE_TWO_FACTION_BONUS, within(EPSILON));
    }

    // With nothing near Mecatol Rex the blast cannot hurt Nekro, so it gets the small "fun" For bias.
    @Test
    void ixthianArtifactHasASmallForBiasWhenTheBlastIsHarmless() {
        double legacy = DirectiveValues.ixthianArtifact(test.game, nekro, "for");

        assertThat(expert(nekro, "artifact", "for"))
                .isCloseTo(legacy + ExpertDirectiveValues.ARTIFACT_FOR_BIAS, within(EPSILON));
        assertThat(expert(nekro, "artifact", "against")).isZero();
    }

    // Six Nekro ships on Mecatol Rex would all die on a 1-5, a devastating blast, so the bias is dropped.
    @Test
    void ixthianArtifactDropsTheBiasWhenTheBlastIsDevastating() {
        test.units(mecatol, "space", nekro, UnitType.Cruiser, 6);
        double legacy = DirectiveValues.ixthianArtifact(test.game, nekro, "for");

        assertThat(expert(nekro, "artifact", "for")).isCloseTo(legacy, within(EPSILON));
    }

    // A revealed spend objective (Erect a Monument) makes the trade goods from For worth more to every seat.
    @Test
    void swordsToPlowsharesForGainsWithRevealedSpendObjectives() {
        double before = expert(nekro, "plowshares", "for");
        test.game.getRevealedPublicObjectives().put("monument", 7);
        test.game.getRevealedPublicObjectives().put("corner", 8);

        assertThat(expert(nekro, "plowshares", "for") - before)
                .isCloseTo(ExpertDirectiveValues.PLOWSHARES_PER_SPEND_OBJECTIVE, within(EPSILON));
    }

    // A contender likes For: the others lose infantry they would retaliate with.
    @Test
    void swordsToPlowsharesLeaderLikesFor() {
        double before = expert(nekro, "plowshares", "for");
        test.points(nekro, 9);

        assertThat(expert(nekro, "plowshares", "for")).isGreaterThan(before);
    }

    // Against: only seats that voted Against lose a command token, so an abstaining voter loses nothing, while a voter
    // who voted Against pays for its own token.
    @Test
    void wormholeResearchAgainstOnlyHurtsAgainstVoters() {
        assertThat(expert(nekro, "wormhole_research", "against", "")).isZero();
        assertThat(expert(nekro, "wormhole_research", "against", "against")).isNegative();
    }

    // A rival who voted Against loses a token if Against wins, which is good for the abstaining voter.
    @Test
    void wormholeResearchAgainstIsGoodWhenARivalVotedAgainst() {
        test.game.setCurrentAgendaVote("against", "yellow_3");

        assertThat(expert(nekro, "wormhole_research", "against", "")).isPositive();
    }

    // For: Nekro's fleet at Lodor (alpha wormhole) is destroyed, which outweighs its free tech.
    @Test
    void wormholeResearchForWeighsShipsLostAtAlphaWormholes() {
        test.units(lodorTile, "space", nekro, UnitType.Dreadnought, 6);

        assertThat(expert(nekro, "wormhole_research", "for")).isNegative();
    }

    // Against: a fleet pool of 3 dropping to 2 with three cruisers at Lodor loses the token and the cheapest ship.
    @Test
    void clandestineAgainstCountsShipsOverTheNewFleetLimit() {
        test.units(lodorTile, "space", nekro, UnitType.Cruiser, 3);

        assertThat(ExpertDirectiveValues.fleetTokenLoss(test.game, nekro))
                .isCloseTo(CardValueScale.HELD_TOKEN + 2 * ValueScale.RESOURCE, within(EPSILON));
        assertThat(expert(nekro, "cladenstine", "against")).isNegative();
        assertThat(expert(sol, "cladenstine", "against")).isPositive();
    }

    // Letnev's Armada lets it keep 2 more non-fighter ships per system, so the same fleet loses no ship.
    @Test
    void clandestineAgainstSparesLetnevsArmada() {
        Player letnev = test.addSeat("300000000000000006", "letnev", "red");
        letnev.setFleetCC(3);
        test.units(lodorTile, "space", letnev, UnitType.Cruiser, 3);

        assertThat(ExpertDirectiveValues.fleetTokenLoss(test.game, letnev))
                .isCloseTo(CardValueScale.HELD_TOKEN, within(EPSILON));
    }

    // For: a seat down to 1 tactic, 0 fleet and 1 strategy token must empty both pools, paying the empty-pool costs;
    // a seat with full pools only gives up tokens.
    @Test
    void clandestineForCostsMoreWhenPoolsRunDry() {
        sol.setTacticalCC(1);
        sol.setFleetCC(0);
        sol.setStrategicCC(1);

        double expected = 2 * CardValueScale.HELD_TOKEN
                + ExpertDirectiveValues.EMPTY_TACTIC_POOL
                + ExpertDirectiveValues.EMPTY_STRATEGY_POOL;
        assertThat(ExpertDirectiveValues.sheetTokenLoss(test.game, sol)).isCloseTo(expected, within(EPSILON));
        assertThat(ExpertDirectiveValues.sheetTokenLoss(test.game, nekro))
                .isCloseTo(2 * CardValueScale.HELD_TOKEN, within(EPSILON));
        assertThat(expert(nekro, "cladenstine", "for")).isPositive();
    }

    // For with only fleet tokens left: both tokens come off the fleet pool (3 -> 1), so three cruisers at Lodor
    // lose two of them, not one at limit 2 plus two more at limit 1.
    @Test
    void clandestineForCountsEachShipOnceWhenBothTokensComeOffTheFleetPool() {
        nekro.setTacticalCC(0);
        nekro.setStrategicCC(0);
        test.units(lodorTile, "space", nekro, UnitType.Cruiser, 3);

        assertThat(ExpertDirectiveValues.sheetTokenLoss(test.game, nekro))
                .isCloseTo(2 * CardValueScale.HELD_TOKEN + 4 * ValueScale.RESOURCE, within(EPSILON));
    }

    // Every outcome string other than For or Against is worth nothing.
    @Test
    void otherOutcomesAreWorthNothing() {
        for (String agenda : List.of(
                "arms_reduction",
                "economic_equality",
                "incentive",
                "artifact",
                "plowshares",
                "wormhole_research",
                "cladenstine")) {
            assertThat(expert(nekro, agenda, "sol")).as(agenda).isZero();
        }
    }
}
