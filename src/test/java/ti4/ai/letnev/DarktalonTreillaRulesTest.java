package ti4.ai.letnev;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Darktalon Treilla lifts the fleet supply for the round, at the price of an action and of the hero. The seat plays it
// only to field a bigger attack than its fleet supply allows, not because it is unlocked.
class DarktalonTreillaRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private String neighbour;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        test.game.setVp(10);
        home = test.nekroHome();
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.aiIsActive("action");
        test.nekro.addAbility("armada");
        test.nekro.setFleetCC(1);
        test.nekro.setTacticalCC(3);
        test.nekro.addLeader("letnevhero");
        test.nekro.getLeader("letnevhero").orElseThrow().setLocked(false);
    }

    private void fleetAtHome(int dreadnoughts) {
        test.units(home, "space", test.nekro, UnitType.Dreadnought, dreadnoughts);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
    }

    private void solFleetOnLodor(int cruisers) {
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "space", test.sol, UnitType.Cruiser, cruisers);
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 1);
    }

    private AiPrompt turnMenu() {
        return prompt(
                "turn",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_tacticalAction",
                "FFCC_nekro_componentAction",
                "FFCC_nekro_passForRound");
    }

    private AiPrompt componentMenu() {
        return prompt(
                "menu",
                PromptSource.AI_THREAD,
                NOW,
                "FFCC_nekro_componentActionRes_leader_letnevhero",
                "FFCC_nekro_componentActionRes_ability_generic",
                "deleteButtons");
    }

    private Optional<AiDecision> beforeTactical() {
        AiTurnContext context = test.context(turnMenu());
        return DarktalonTreillaRules.insteadOfTacticalAction(context, Prompts.thisTurn(context));
    }

    private String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    // Six cruisers on Lodor are too many for the three dreadnoughts the fleet supply lets through, and just right for
    // five: the hero is worth an action.
    @Test
    void playsTheHeroToStackAFleetBeyondItsSupply() {
        fleetAtHome(5);
        solFleetOnLodor(6);

        assertThat(pressed(beforeTactical())).isEqualTo("FFCC_nekro_componentAction");
        AiTurnContext menu = test.context(componentMenu());
        assertThat(pressed(DarktalonTreillaRules.next(menu)))
                .isEqualTo("FFCC_nekro_componentActionRes_leader_letnevhero");
    }

    @Test
    void waitsForTheLeaderButtonWhileTheComponentMenuOpens() {
        fleetAtHome(5);
        solFleetOnLodor(6);
        beforeTactical();

        assertThat(DarktalonTreillaRules.next(test.context())).containsInstanceOf(AiDecision.Wait.class);
    }

    @Test
    void keepsTheHeroWhenTheFleetFitsTheSupplyAnyway() {
        fleetAtHome(3);
        solFleetOnLodor(1);

        assertThat(beforeTactical()).isEmpty();
    }

    @Test
    void keepsTheHeroWithoutAnAttackWorthMaking() {
        fleetAtHome(5);

        assertThat(beforeTactical()).isEmpty();
    }

    @Test
    void doesNothingOnceTheHeroIsActive() {
        fleetAtHome(5);
        solFleetOnLodor(6);
        test.nekro.getLeader("letnevhero").orElseThrow().setActive(true);

        assertThat(beforeTactical()).isEmpty();
    }

    @Test
    void doesNothingWhileTheHeroIsLocked() {
        fleetAtHome(5);
        solFleetOnLodor(6);
        test.nekro.getLeader("letnevhero").orElseThrow().setLocked(true);

        assertThat(beforeTactical()).isEmpty();
    }

    @Test
    void doesNothingOutsideItsOwnTurn() {
        fleetAtHome(5);
        solFleetOnLodor(6);
        test.isActive(test.sol, "action");

        assertThat(beforeTactical()).isEmpty();
    }

    @Test
    void decidesOncePerTurn() {
        fleetAtHome(3);
        solFleetOnLodor(1);
        AiTurnContext context = test.context(turnMenu());
        List<AiPrompt> turn = Prompts.thisTurn(context);

        assertThat(DarktalonTreillaRules.insteadOfTacticalAction(context, turn)).isEmpty();
        fleetAtHome(5);
        solFleetOnLodor(6);

        assertThat(DarktalonTreillaRules.insteadOfTacticalAction(context, turn)).isEmpty();
    }

    private void fleetBesideMecatol(int dreadnoughts) {
        test.place("18", "000");
        String beside = ti4.model.MapPositionMapper.getAdjacentTilePositions("000").stream()
                .filter(candidate -> !"x".equals(candidate) && test.game.getTileByPosition(candidate) == null)
                .findFirst()
                .orElseThrow();
        Tile launch = test.place("47", beside);
        test.units(launch, "space", test.nekro, UnitType.Dreadnought, dreadnoughts);
        test.units(launch, "space", test.nekro, UnitType.Carrier, 1);
        test.units(launch, "space", test.nekro, UnitType.Infantry, 3);
        test.nekro.addPlanet("abyz");
        test.nekro.addPlanet("fria");
        test.nekro.addPlanet("mordaiii");
    }

    // In round four or five the hero is how Imperial gets its point: the fleet that the supply would cut down goes
    // at Mecatol Rex whole.
    @Test
    void playsTheHeroToSendTheWholeFleetAtMecatolWhenHoldingImperial() {
        test.game.setRound(4);
        test.nekro.addSC(8);
        fleetBesideMecatol(5);

        assertThat(DarktalonTreillaRules.assaultsMecatol(test.game, test.nekro)).isTrue();
        assertThat(pressed(beforeTactical())).isEqualTo("FFCC_nekro_componentAction");
    }

    @Test
    void keepsTheHeroForMecatolWithoutImperialOrWarfare() {
        test.game.setRound(4);
        fleetBesideMecatol(5);

        assertThat(DarktalonTreillaRules.assaultsMecatol(test.game, test.nekro)).isFalse();
    }

    @Test
    void sendsTheFleetAtMecatolInAnyRoundOnceTheHeroIsUnlocked() {
        test.game.setRound(2);
        test.nekro.addSC(8);
        fleetBesideMecatol(5);

        assertThat(DarktalonTreillaRules.assaultsMecatol(test.game, test.nekro)).isTrue();
    }

    // With the hero in play the Mecatol strike takes every ship that reaches, not just the carrier and its infantry.
    @Test
    void sendsEveryShipAtMecatolOnceTheHeroIsInPlay() {
        test.game.setRound(4);
        test.nekro.addSC(8);
        fleetBesideMecatol(5);
        test.nekro.getLeader("letnevhero").orElseThrow().setActive(true);

        ti4.ai.tactical.TacticalPlan strike = ti4.ai.tactical.TacticalPlanner.ranked(test.game, test.nekro).stream()
                .filter(plan -> "000".equals(plan.target()))
                .findFirst()
                .orElseThrow();

        assertThat(strike.moves().stream()
                        .filter(move -> move.type() == UnitType.Dreadnought)
                        .mapToInt(ti4.ai.tactical.TacticalPlan.UnitMove::count)
                        .sum())
                .isEqualTo(5);
    }
}
