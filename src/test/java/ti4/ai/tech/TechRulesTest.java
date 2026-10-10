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
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.image.PositionMapper;
import ti4.testUtils.BaseTi4Test;

class TechRulesTest extends BaseTi4Test {

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
    // other 2 go to the player furthest behind.
    @Test
    void takesProductionBiomesTradeGoodsInsteadOfPassing() {
        test.nekro.addTech("pm");
        test.nekro.setStrategicCC(2);
        AiPrompt start =
                prompt("start", PromptSource.PUBLIC, NOW, "FFCC_nekro_componentAction", "FFCC_nekro_passForRound");
        AiPrompt menu = prompt("menu", PromptSource.PUBLIC, NOW, "FFCC_nekro_exhaustTech_pm");
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

    // Sol activates the system with the seat's lone destroyer, with two dreadnoughts next door: Nullification Field
    // ends Sol's turn. With nothing able to move in, the seat lets the activation stand.
    @Test
    void endsTheTurnOfAPlayerWhoCouldOverwhelmItsShips() {
        test.nekro.addTech("nf");
        test.nekro.setStrategicCC(2);
        String position = AiTestGame.neighbourOf(AiTestGame.HOME);
        Tile tile = test.game.getTileByPosition(position);
        test.units(tile, "space", test.nekro, UnitType.Destroyer, 1);
        String next = PositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate))
                .findFirst()
                .orElseThrow();
        Tile staging = test.place("25", next);
        AiPrompt nullify = prompt(
                "nullify",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_nullificationField_" + position + "_" + test.sol.getColor(),
                "FFCC_nekro_deleteButtons");
        assertThat(pressedId(TechRules.nullificationField(test.context(nullify)).orElseThrow()))
                .isEqualTo("FFCC_nekro_deleteButtons");

        test.units(staging, "space", test.sol, UnitType.Dreadnought, 2);

        assertThat(pressedId(TechRules.nullificationField(test.context(nullify)).orElseThrow()))
                .isEqualTo("FFCC_nekro_nullificationField_" + position + "_" + test.sol.getColor());
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

    private Optional<AiDecision> endOfTurn(AiPrompt prompt) {
        return TechRules.endOfTurn(test.context(prompt), List.of(prompt));
    }
}
