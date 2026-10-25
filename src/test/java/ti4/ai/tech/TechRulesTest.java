package ti4.ai.tech;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.eval.BoardView;
import ti4.ai.expedition.ExpeditionKind;
import ti4.ai.expedition.ExpeditionUnlock;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

class TechRulesTest extends BaseTi4Test {

    private static final String DECLINE = "FFCC_nekro_deleteButtons";

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.place("24", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addPlanet("meharxull");
        test.aiIsActive("action");
    }

    // Mehar Xull has a technology specialty and was spent this turn. Before ending its turn the seat opens its
    // end-of-turn abilities, exhausts Bio-Stims and readies Mehar Xull rather than a technology.
    @Test
    void readiesASpentTechSkipPlanetWithBioStimsBeforeEndingTheTurn() {
        test.nekro.addTech("bs");
        test.nekro.exhaustPlanet("meharxull");
        AiPrompt end = prompt("end", PromptSource.PUBLIC, NOW, "FFCC_nekro_endOfTurnAbilities");
        AiPrompt abilities =
                prompt("abilities", PromptSource.PUBLIC, NOW, "FFCC_nekro_turnEnd", "FFCC_nekro_exhaustTech_bs");
        AiPrompt ready =
                prompt("ready", PromptSource.PUBLIC, NOW, "biostimsReady_tech_st", "biostimsReady_planet_meharxull");

        assertThat(pressedId(endOfTurn(end).orElseThrow())).isEqualTo("FFCC_nekro_endOfTurnAbilities");
        assertThat(pressedId(endOfTurn(abilities).orElseThrow())).isEqualTo("FFCC_nekro_exhaustTech_bs");
        assertThat(pressedId(endOfTurn(ready).orElseThrow())).isEqualTo("biostimsReady_planet_meharxull");
        assertThat(endOfTurn(abilities)).isEmpty();
    }

    // With nothing worth readying, Bio-Stims stays ready and the turn simply ends.
    @Test
    void leavesBioStimsAloneWithoutASpentTechSkipPlanet() {
        test.nekro.addTech("bs");
        AiPrompt end = prompt("end", PromptSource.PUBLIC, NOW, "FFCC_nekro_endOfTurnAbilities");

        assertThat(endOfTurn(end)).isEmpty();
    }

    // Magen Defense Grid's infantry placement is mandatory when another player activates a system with its
    // structures, so the seat always presses it.
    @Test
    void placesInfantryWithMagenDefenseGrid() {
        AiPrompt magen = prompt("magen", PromptSource.PUBLIC, NOW, "FFCC_nekro_useMagenDefense_301");

        assertThat(pressedId(TechRules.placeMagenInfantry(test.context(magen)).orElseThrow()))
                .isEqualTo("FFCC_nekro_useMagenDefense_301");
    }

    // With nothing to save for, a ready planet with a technology specialty is worth more as a trade good once the
    // seat passes: planets ready again in the status phase anyway.
    @Test
    void tradesTechSkipPlanetsForTradeGoodsWithPsychoarchaeologyBeforePassing() {
        everySliceClaimedBySol();
        test.nekro.addTech("pa");
        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_nekro_getPsychoButtons");
        AiPrompt planets = prompt("planets", PromptSource.PUBLIC, NOW, "psychoExhaust_meharxull", "deleteButtons");

        assertThat(pressedId(TechRules.beforePassing(test.context(start), List.of(start))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_getPsychoButtons");
        assertThat(pressedId(TechRules.beforePassing(test.context(planets), List.of(planets))
                        .orElseThrow()))
                .isEqualTo("psychoExhaust_meharxull");

        test.nekro.exhaustPlanet("meharxull");
        assertThat(TechRules.beforePassing(test.context(planets), List.of(planets)))
                .isEmpty();
    }

    // With a strategy token to spare, Production Biomes' 4 trade goods are a better last action than passing; the
    // other 2 go to the player furthest behind. The component action menu is ephemeral, so it is reposted to the
    // seat's private thread.
    @Test
    void takesProductionBiomesTradeGoodsInsteadOfPassing() {
        test.nekro.addTech("pm");
        test.nekro.setStrategicCC(2);
        AiPrompt start =
                prompt("start", PromptSource.PUBLIC, NOW, "FFCC_nekro_componentAction", "FFCC_nekro_passForRound");
        AiPrompt menu = prompt("menu", PromptSource.AI_THREAD, NOW, "FFCC_nekro_exhaustTech_pm");
        AiPrompt recipients = prompt("recipients", PromptSource.PUBLIC, NOW, "productionBiomes_sol");

        assertThat(pressedId(TechRules.beforePassing(test.context(start), List.of(start))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_componentAction");
        assertThat(pressedId(
                        TechRules.continueProductionBiomes(test.context(menu)).orElseThrow()))
                .isEqualTo("FFCC_nekro_exhaustTech_pm");
        assertThat(pressedId(TechRules.continueProductionBiomes(test.context(recipients))
                        .orElseThrow()))
                .isEqualTo("productionBiomes_sol");
    }

    // Sol leads on points, so the 2 trade goods go to Xxcha even though Sol is offered first.
    @Test
    void neverGivesProductionBiomesTradeGoodsToThePointsLeader() {
        ti4.game.Player xxcha = test.addSeat("200000000000000002", "xxcha", "green");
        test.points(test.sol, 4);
        test.points(xxcha, 1);
        test.memory.put("productionBiomes|" + test.context().turnKey(), "recipient");
        AiPrompt recipients =
                prompt("recipients", PromptSource.PUBLIC, NOW, "productionBiomes_sol", "productionBiomes_xxcha");

        assertThat(pressedId(TechRules.continueProductionBiomes(test.context(recipients))
                        .orElseThrow()))
                .isEqualTo("productionBiomes_xxcha");
    }

    // Sol still holds an unplayed Imperial whose secondary the seat wants (it has room for a secret), and its only
    // strategy token is needed for that, so it passes instead of spending the token on Production Biomes.
    @Test
    void keepsItsStrategyTokenForASecondaryItWantsToFollow() {
        test.nekro.addTech("pm");
        test.nekro.setStrategicCC(1);
        test.sol.addSC(8);
        AiPrompt start =
                prompt("start", PromptSource.PUBLIC, NOW, "FFCC_nekro_componentAction", "FFCC_nekro_passForRound");

        assertThat(TechRules.beforePassing(test.context(start), List.of(start))).isEmpty();
    }

    // One trade good short of Negotiate Trade Routes: Production Biomes' 4 trade goods unlock a point, which beats any
    // tactical action available, so it is used as the action rather than kept for the end of the round.
    @Test
    void usesProductionBiomesAsTheActionWhenItsTradeGoodsUnlockAnObjective() {
        test.nekro.addTech("pm");
        test.nekro.setStrategicCC(2);
        test.nekro.setTg(1);
        test.game.getRevealedPublicObjectives().put("trade_routes", 1);
        AiPrompt start =
                prompt("start", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction", "FFCC_nekro_componentAction");

        assertThat(pressedId(TechRules.insteadOfTacticalAction(test.context(start), List.of(start))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_componentAction");
    }

    // Sol activates the system with the seat's lone destroyer, with two dreadnoughts next door. The seat would lose
    // the destroyer, which is not worth a strategy token and a tech exhaust, so the activation stands.
    @Test
    void letsAnAttackOnALoneDestroyerStand() {
        Tile tile = nullificationTarget();
        test.units(tile, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(stagingTile(tile), "space", test.sol, UnitType.Dreadnought, 2);

        assertThat(nullificationAnswer(tile)).isEqualTo(DECLINE);
    }

    // Three cruisers against three dreadnoughts next door: the fleet is lost for little in return, so Nullification
    // Field ends Sol's turn.
    @Test
    void endsTheTurnOfAPlayerWhoWouldDestroyItsFleet() {
        Tile tile = nullificationTarget();
        test.units(tile, "space", test.nekro, UnitType.Cruiser, 3);
        test.units(stagingTile(tile), "space", test.sol, UnitType.Dreadnought, 3);

        assertThat(nullificationAnswer(tile)).isEqualTo(nullifyButton(tile));
    }

    // Three dreadnoughts facing a lone cruiser win the fight, so the token is kept for a real attack.
    @Test
    void letsAnAttackItWouldWinStand() {
        Tile tile = nullificationTarget();
        test.units(tile, "space", test.nekro, UnitType.Dreadnought, 3);
        test.units(stagingTile(tile), "space", test.sol, UnitType.Cruiser, 1);

        assertThat(nullificationAnswer(tile)).isEqualTo(DECLINE);
    }

    // With nothing of Sol's within reach of the system, activating it is no threat and the token is kept.
    @Test
    void keepsTheTokenWhenNothingCanMoveIn() {
        Tile tile = nullificationTarget();
        test.units(tile, "space", test.nekro, UnitType.Cruiser, 3);

        assertThat(nullificationAnswer(tile)).isEqualTo(DECLINE);
    }

    // A fleet that would be wiped out is still not worth a token the seat does not have.
    @Test
    void cannotNullifyWithoutAStrategyToken() {
        Tile tile = nullificationTarget();
        test.nekro.setStrategicCC(0);
        test.units(tile, "space", test.nekro, UnitType.Cruiser, 3);
        test.units(stagingTile(tile), "space", test.sol, UnitType.Dreadnought, 3);

        assertThat(nullificationAnswer(tile)).isEqualTo(DECLINE);
    }

    // A single destroyer is not worth a token, but the same destroyer guarding the home system's planet against a
    // landing is: losing the planet costs far more than the ship.
    @Test
    void endsTheTurnOfAnInvasionOfItsHomeSystem() {
        test.nekro.addTech("nf");
        test.nekro.setStrategicCC(2);
        Tile home = test.game.getTileByPosition(AiTestGame.HOME);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        Tile staging = stagingTile(home);
        test.units(staging, "space", test.sol, UnitType.Dreadnought, 2);
        test.units(staging, "space", test.sol, UnitType.Carrier, 2);
        test.units(staging, "space", test.sol, UnitType.Infantry, 6);

        assertThat(nullificationAnswer(home)).isEqualTo(nullifyButton(home));
    }

    // Intimidate Council wants ships in two systems next to Mecatol Rex. Sol holds one such system with a command
    // token, so moving a dreadnought pair into the seat's lone destroyer's system would complete the objective for
    // Sol: worth a token even though the destroyer alone is not.
    @Test
    void endsTheTurnOfAPlayerWhoWouldCompleteAnObjectiveByTheMove() {
        Tile target = intimidateBoard();
        test.units(target, "space", test.nekro, UnitType.Destroyer, 1);
        test.sol.addPlanet("jord");
        test.place("01", "304");
        Tile held = test.game.getTileByPosition("102");
        test.units(held, "space", test.sol, UnitType.Destroyer, 1);
        held.addCC(ti4.model.Mapper.getCCID(test.sol.getColor()));
        test.units(test.game.getTileByPosition("201"), "space", test.sol, UnitType.Dreadnought, 2);

        assertThat(nullificationAnswer(target)).isEqualTo(nullifyButton(target));
    }

    // The same attack on a destroyer that holds up the seat's own Intimidate Council (a second destroyer sits in
    // the other system next to Mecatol) breaks an objective it meets.
    @Test
    void endsTheTurnOfAnAttackOnTheShipsHoldingItsPresenceObjective() {
        Tile target = intimidateBoard();
        test.units(target, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(test.game.getTileByPosition("102"), "space", test.nekro, UnitType.Destroyer, 1);
        test.units(test.game.getTileByPosition("201"), "space", test.sol, UnitType.Dreadnought, 2);

        assertThat(nullificationAnswer(target)).isEqualTo(nullifyButton(target));
    }

    // Without the objective on the table, the very same attack on a lone destroyer stands.
    @Test
    void letsTheSameAttackStandWhenNoObjectiveDependsOnIt() {
        Tile target = intimidateBoard();
        test.game.getRevealedPublicObjectives().clear();
        test.units(target, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(test.game.getTileByPosition("102"), "space", test.nekro, UnitType.Destroyer, 1);
        test.units(test.game.getTileByPosition("201"), "space", test.sol, UnitType.Dreadnought, 2);

        assertThat(nullificationAnswer(target)).isEqualTo(DECLINE);
    }

    private Tile intimidateBoard() {
        test.nekro.addTech("nf");
        test.nekro.setStrategicCC(2);
        test.game.setPhaseOfGame("action");
        test.place("18", "000");
        Tile target = test.place("26", "101");
        test.place("27", "102");
        test.place("25", "201");
        test.game.getRevealedPublicObjectives().put("intimidate", 1);
        return target;
    }

    private Tile nullificationTarget() {
        test.nekro.addTech("nf");
        test.nekro.setStrategicCC(2);
        return test.game.getTileByPosition(AiTestGame.neighbourOf(AiTestGame.HOME));
    }

    private Tile stagingTile(Tile tile) {
        String next = MapPositionMapper.getAdjacentTilePositions(tile.getPosition()).stream()
                .filter(candidate -> !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate))
                .findFirst()
                .orElseThrow();
        return test.place("25", next);
    }

    private String nullifyButton(Tile tile) {
        return "FFCC_nekro_nullificationField_" + tile.getPosition() + "_" + test.sol.getColor();
    }

    private String nullificationAnswer(Tile tile) {
        AiPrompt nullify = prompt("nullify", PromptSource.PUBLIC, NOW, nullifyButton(tile), DECLINE);
        return pressedId(TechRules.nullificationField(test.context(nullify)).orElseThrow());
    }

    // Neural Parasite destroys an enemy infantry at the start of each turn. It starts it, picks the leader (Sol, on 3
    // points, over Hacan on 1), and takes the last infantry guarding a planet rather than one of a stack in space.
    @Test
    void destroysTheLeadersLastDefenderOfAPlanetWithNeuralParasite() {
        test.nekro.addTech("parasite-obs");
        test.addSeat("100000000000000003", "hacan", "yellow");
        test.game.scorePublicObjective(test.sol.getUserID(), test.game.addCustomPO("Sol points", 3));
        test.game.scorePublicObjective("100000000000000003", test.game.addCustomPO("Hacan points", 1));
        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "startNeuralParasite");
        AiPrompt victims = prompt(
                "victims",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_neuralParasiteS2_hacan",
                "FFCC_nekro_neuralParasiteS2_sol");
        AiPrompt targets = prompt(
                "targets",
                PromptSource.PUBLIC,
                NOW,
                List.of("resolveNeuralParasite_401_space_sol_none", "resolveNeuralParasite_401_lodor_sol_none"),
                List.of("Space 401 (3)", "Lodor (1)"));

        assertThat(pressedId(TechRules.neuralParasite(test.context(start)).orElseThrow()))
                .isEqualTo("startNeuralParasite");
        assertThat(pressedId(TechRules.neuralParasite(test.context(victims)).orElseThrow()))
                .isEqualTo("FFCC_nekro_neuralParasiteS2_sol");
        assertThat(pressedId(TechRules.neuralParasite(test.context(targets)).orElseThrow()))
                .isEqualTo("resolveNeuralParasite_401_lodor_sol_none");
        assertThat(TechRules.neuralParasite(test.context(start))).isEmpty();
    }

    // Salvage Operations pays a trade good once the space combat is decided (here the seat holds the system alone).
    // The optional rebuild costs resources, so it is declined.
    @Test
    void takesTheSalvageOperationsTradeGoodAndSkipsTheRebuild() {
        test.nekro.addTech("so");
        String position = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.units(test.game.getTileByPosition(position), "space", test.nekro, UnitType.Dreadnought, 1);
        AiPrompt salvage = prompt("salvage", PromptSource.AI_THREAD, NOW, "salvageOps_" + position);
        AiPrompt rebuild = AiTestGame.withContent(
                prompt(
                        "rebuild",
                        PromptSource.PUBLIC,
                        NOW,
                        List.of("placeOneNDone_dontskip_destroyer_" + position, "deleteButtons"),
                        List.of("Destroyer", "Decline")),
                test.nekro.getRepresentation()
                        + " Use the buttons to produce 1 ship that was destroyed in the combat. ");

        assertThat(pressedId(TechRules.salvageOperations(test.context(salvage)).orElseThrow()))
                .isEqualTo("salvageOps_" + position);
        assertThat(TechRules.salvageOperations(test.context(salvage))).isEmpty();
        assertThat(pressedId(TechRules.salvageOperations(test.context(rebuild)).orElseThrow()))
                .isEqualTo("deleteButtons");
    }

    // After producing, Self-Assembly Routines places a free mech; it goes on the planet with the space dock.
    @Test
    void placesAFreeMechWithSelfAssemblyRoutinesAfterProducing() {
        test.nekro.addTech("sar");
        AiPrompt production = prompt(
                "production",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_deleteButtons_tacticalAction_301",
                "sarMechStep1_301_tacticalAction");
        AiPrompt planets = prompt(
                "planets",
                PromptSource.PUBLIC,
                NOW,
                "sarMechStep2_meharxull_tacticalAction",
                "sarMechStep2_mordaiii_tacticalAction");

        assertThat(pressedId(TechRules.startSelfAssembly(test.context(production), production)
                        .orElseThrow()))
                .isEqualTo("sarMechStep1_301_tacticalAction");
        assertThat(TechRules.startSelfAssembly(test.context(production), production))
                .isEmpty();
        assertThat(pressedId(
                        TechRules.placeSelfAssemblyMech(test.context(planets)).orElseThrow()))
                .isEqualTo("sarMechStep2_mordaiii_tacticalAction");
    }

    private void everySliceClaimedBySol() {
        for (ExpeditionKind kind : ExpeditionKind.values()) {
            test.game.getExpeditions().getExpeditionFactions().put(kind.id(), test.sol.getFaction());
        }
    }

    private void twoSpentTechSkipPlanets() {
        test.place("22", "202");
        test.nekro.addPlanet("tarmann");
        test.nekro.addTech("bs");
        test.nekro.setTacticalCC(0);
        test.nekro.exhaustPlanet("meharxull");
        test.nekro.exhaustPlanet("tarmann");
        everySliceClaimedBySol();
        test.memory.put("bioStims|" + test.context().turnKey(), "exhausted");
    }

    private String readiedWithBioStims() {
        AiPrompt ready = prompt(
                "ready", PromptSource.PUBLIC, NOW, "biostimsReady_planet_meharxull", "biostimsReady_planet_tarmann");
        String pressed = pressedId(endOfTurn(ready).orElseThrow());
        return pressed.substring(pressed.lastIndexOf('_') + 1);
    }

    // Both spent planets are equally idle, so Bio-Stims readies the larger one.
    @Test
    void readiesTheLargerSpentTechSkipPlanetWhenNeitherWasPaidForAnExpedition() {
        twoSpentTechSkipPlanets();

        assertThat(readiedWithBioStims()).isEqualTo(larger("meharxull", "tarmann"));
    }

    // The expedition exhausted the smaller planet this turn; neither has another use, so it is the one that comes back.
    @Test
    void readiesTheTechSkipPlanetTheExpeditionJustExhaustedWhenNothingElseNeedsReadying() {
        twoSpentTechSkipPlanets();
        String smaller = "meharxull".equals(larger("meharxull", "tarmann")) ? "tarmann" : "meharxull";
        test.memory.put("expeditionPaid|" + test.context().turnKey(), smaller);

        assertThat(readiedWithBioStims()).isEqualTo(smaller);
    }

    private String larger(String first, String second) {
        return size(first) >= size(second) ? first : second;
    }

    private int size(String planet) {
        return BoardView.planetResources(test.game, planet) + BoardView.planetInfluence(test.game, planet);
    }

    // Mehar Xull is the only planet the seat could pay the technology specialty expedition with, so Psychoarchaeology
    // keeps it rather than trading it for a trade good when the seat passes.
    @Test
    void keepsTheTechSkipPlanetAPlannedExpeditionNeedsOutOfPsychoarchaeology() {
        test.nekro.addTech("pa");
        test.nekro.addBreakthrough("nekrobt");
        assertThat(ExpeditionUnlock.plannedPlanets(test.game, test.nekro)).contains("meharxull");
        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_nekro_getPsychoButtons");

        assertThat(TechRules.beforePassing(test.context(start), List.of(start))).isEmpty();
    }

    private Optional<AiDecision> endOfTurn(AiPrompt prompt) {
        return TechRules.endOfTurn(test.context(prompt), List.of(prompt));
    }

    // The component-action menu is an ephemeral message, which the AI actuator re-posts into the seat's private
    // thread, so the AI reads it as a hidden prompt.
    @Test
    void findsTheProductionBiomesMenuInThePrivateThread() {
        test.nekro.addTech("pm");
        test.nekro.setStrategicCC(2);
        AiPrompt start =
                prompt("start", PromptSource.PUBLIC, NOW, "FFCC_nekro_componentAction", "FFCC_nekro_passForRound");
        TechRules.beforePassing(test.context(start), List.of(start));
        AiPrompt menu = prompt("menu", PromptSource.AI_THREAD, NOW, "FFCC_nekro_exhaustTech_pm");

        assertThat(pressedId(
                        TechRules.continueProductionBiomes(test.context(menu)).orElseThrow()))
                .isEqualTo("FFCC_nekro_exhaustTech_pm");
    }
}
