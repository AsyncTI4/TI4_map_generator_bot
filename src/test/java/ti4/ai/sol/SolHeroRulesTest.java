package ti4.ai.sol;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.FreedPlays;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// Jace X returns every command token on the board, so a fleet pinned by its own token can move again. It is a game
// winner and is saved for the moments that decide the game: in the final round, to complete an objective, take Mecatol
// Rex or strike the leader; earlier only when the freed action wins the game or stops the leader from winning it.
class SolHeroRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private Player sol;
    private Tile launch;
    private String launchPosition;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        test.game.setVp(10);
        test.game.setRound(4);
        Tile home = test.place("01", AiTestGame.HOME);
        sol.addPlanet("jord");
        test.units(home, "jord", sol, UnitType.Spacedock, 1);
        test.units(home, "jord", sol, UnitType.Infantry, 4);
        sol.setTacticalCC(3);
        sol.setStrategicCC(2);
        sol.setFleetCC(5);
        sol.setTg(8);
        sol.addLeader("solhero");
        sol.unsafeGetLeader("solhero").setLocked(false);
        test.isActive(sol, "action");
        launchPosition = nextTo("000");
        launch = test.place("47", launchPosition);
        test.units(launch, "space", sol, UnitType.Carrier, 2);
        test.units(launch, "space", sol, UnitType.Cruiser, 2);
        test.units(launch, "space", sol, UnitType.Infantry, 4);
    }

    private static String nextTo(String position) {
        return MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate))
                .findFirst()
                .orElseThrow();
    }

    private void mecatolWaitsForTheTaking() {
        Tile rex = test.place("18", "000");
        rex.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
    }

    private void fleetIsPinnedByItsOwnToken() {
        launch.addCC(Mapper.getCCID(sol.getColor()));
    }

    private void aLeaderIsOnTheDoorstepOfVictory() {
        test.points(test.nekro, 8);
    }

    private AiTurnContext context(AiPrompt... prompts) {
        return test.contextFor(sol, Set.of(), NOW, prompts);
    }

    private static AiPrompt turnMenu() {
        return prompt(
                "turn",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_sol_tacticalAction",
                "FFCC_sol_componentAction",
                "FFCC_sol_passForRound");
    }

    private static AiPrompt componentMenu() {
        return prompt(
                "menu",
                PromptSource.AI_THREAD,
                NOW,
                "FFCC_sol_componentActionRes_leader_solhero",
                "FFCC_sol_componentActionRes_ability_generic");
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private Optional<AiDecision> instead() {
        AiTurnContext context = context(turnMenu());
        return SolHeroRules.insteadOfTacticalAction(context, Prompts.thisTurn(context));
    }

    // The only fleet that reaches Mecatol Rex sits under the seat's own token. With a leader two points from the goal
    // this is the last round, and the custodians point is worth the hero.
    @Test
    void playsTheHeroInTheFinalRoundToTakeMecatolWithAFleetItsOwnTokenPins() {
        mecatolWaitsForTheTaking();
        fleetIsPinnedByItsOwnToken();
        aLeaderIsOnTheDoorstepOfVictory();
        assertThat(TacticalPlanner.ranked(test.game, sol)).noneMatch(plan -> "000".equals(plan.target()));

        assertThat(FreedPlays.gain(test.game, sol).points()).isGreaterThanOrEqualTo(1);
        assertThat(pressed(instead())).isEqualTo("FFCC_sol_componentAction");
        assertThat(pressed(SolHeroRules.next(context(componentMenu()))))
                .isEqualTo("FFCC_sol_componentActionRes_leader_solhero");
        assertThat(SolHeroRules.next(context(componentMenu()))).isEmpty();
    }

    // The same locked-down fleet in an ordinary mid-game spot is not worth the hero: it can wait for the tokens to
    // come off on their own.
    @Test
    void keepsTheHeroInAnOrdinaryMidGameSpotEvenWithTheFleetPinned() {
        mecatolWaitsForTheTaking();
        fleetIsPinnedByItsOwnToken();
        test.points(test.nekro, 3);

        assertThat(FreedPlays.gain(test.game, sol).points()).isGreaterThanOrEqualTo(1);
        assertThat(instead()).isEmpty();
    }

    @Test
    void keepsTheHeroInTheFinalRoundWhenFreeingTheTokenGainsNothingDecisive() {
        fleetIsPinnedByItsOwnToken();
        test.place("26", nextTo(launchPosition));
        aLeaderIsOnTheDoorstepOfVictory();

        assertThat(FreedPlays.gain(test.game, sol).points()).isZero();
        assertThat(instead()).isEmpty();
    }

    @Test
    void keepsTheHeroWhenTheTokensAreNotInTheWay() {
        mecatolWaitsForTheTaking();
        aLeaderIsOnTheDoorstepOfVictory();

        assertThat(FreedPlays.gain(test.game, sol).points()).isZero();
        assertThat(instead()).isEmpty();
    }

    // Expand Borders needs six planets outside the home systems; Sol holds five and the sixth is beside the fleet.
    @Test
    void playsTheHeroInTheFinalRoundToCompleteAControlObjective() {
        fleetIsPinnedByItsOwnToken();
        aLeaderIsOnTheDoorstepOfVictory();
        test.place("13", "304");
        sol.addPlanet("quinarra");
        sol.addPlanet("trenlak");
        String[][] outposts = {{"25", "202", "quann"}, {"20", "203", "vefutii"}, {"27", "204", "newalbion"}};
        for (String[] outpost : outposts) {
            test.place(outpost[0], outpost[1]);
            sol.addPlanet(outpost[2]);
        }
        test.place("26", nextTo(launchPosition));
        assertThat(instead()).isEmpty();

        test.game.getRevealedPublicObjectives().put("expand_borders", 1);

        assertThat(FreedPlays.gain(test.game, sol).points()).isEqualTo(1);
        assertThat(pressed(instead())).isEqualTo("FFCC_sol_componentAction");
    }

    @Test
    void needsATacticalTokenToUseTheFreedSystem() {
        mecatolWaitsForTheTaking();
        fleetIsPinnedByItsOwnToken();
        aLeaderIsOnTheDoorstepOfVictory();
        sol.setTacticalCC(0);

        assertThat(instead()).isEmpty();
    }

    @Test
    void needsTheHeroToBeUnlocked() {
        mecatolWaitsForTheTaking();
        fleetIsPinnedByItsOwnToken();
        aLeaderIsOnTheDoorstepOfVictory();
        sol.unsafeGetLeader("solhero").setLocked(true);

        assertThat(instead()).isEmpty();
    }

    @Test
    void usesItInPlaceOfPassingToo() {
        mecatolWaitsForTheTaking();
        fleetIsPinnedByItsOwnToken();
        aLeaderIsOnTheDoorstepOfVictory();
        AiTurnContext context = context(turnMenu());

        assertThat(pressed(SolHeroRules.beforePassing(context, Prompts.thisTurn(context))))
                .isEqualTo("FFCC_sol_componentAction");
    }

    @Test
    void stopsWaitingOnceTheTurnCanEnd() {
        mecatolWaitsForTheTaking();
        fleetIsPinnedByItsOwnToken();
        aLeaderIsOnTheDoorstepOfVictory();
        instead();
        AiPrompt over = prompt("over", PromptSource.PUBLIC, NOW + 1000, List.of("FFCC_sol_turnEnd"), List.of());

        assertThat(SolHeroRules.next(context(over))).isEmpty();
        assertThat(SolHeroRules.next(context(componentMenu()))).isEmpty();
    }

    // The leader sits one point short of the goal with a public objective ready to score, and its home system is
    // within reach of a fleet the seat's own token pins: freeing it stops the leader from scoring.
    @Test
    void playsTheHeroInTheFinalRoundToStrikeTheLeadersHome() {
        aPinnedFleetBesideTheLeadersHome(9);

        FreedPlays.Gain gain = FreedPlays.gain(test.game, sol);

        assertThat(gain.points()).isGreaterThanOrEqualTo(1);
        assertThat(gain.stopsLeader()).isTrue();
        assertThat(pressed(instead())).isEqualTo("FFCC_sol_componentAction");
    }

    // A raid on a player who is nowhere near winning is an ordinary mid-game attack, not worth the hero.
    @Test
    void keepsTheHeroWhenTheHomeBelongsToNobodyWhoThreatensToWin() {
        aPinnedFleetBesideTheLeadersHome(2);

        assertThat(FreedPlays.gain(test.game, sol).points()).isZero();
        assertThat(instead()).isEmpty();
    }

    private void aPinnedFleetBesideTheLeadersHome(int leaderPoints) {
        Tile nekroHome = test.place("08", "304");
        test.nekro.addPlanet("mordaiii");
        test.units(nekroHome, "mordaiii", test.nekro, UnitType.Spacedock, 1);
        test.units(nekroHome, "mordaiii", test.nekro, UnitType.Infantry, 1);
        test.game.getRevealedPublicObjectives().put("trade_routes", 1);
        test.nekro.setTg(6);
        test.points(test.nekro, leaderPoints);
        Tile raiders = test.place("47", nextTo("304"));
        test.units(raiders, "space", sol, UnitType.Dreadnought, 2);
        test.units(raiders, "space", sol, UnitType.Carrier, 2);
        test.units(raiders, "space", sol, UnitType.Infantry, 6);
        raiders.addCC(Mapper.getCCID(sol.getColor()));
    }
}
