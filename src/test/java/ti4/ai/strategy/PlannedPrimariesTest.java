package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.testUtils.BaseTi4Test;

// Resolving a played strategy card's primary the way its plan intends.
class PlannedPrimariesTest extends BaseTi4Test {

    private static final String CUSTODIANS = "Custodians";
    private static final int CUSTODIANS_ID = 0;

    private AiTestGame test;
    private Tile home;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        test.game.setVp(10);
        test.aiIsActive("action");
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private String resolve(AiPrompt... prompts) {
        return StrategyCardRules.resolvePrimary(test.context(prompts))
                .map(AiTestGame::pressedId)
                .orElse("nothing");
    }

    private void play(AiPrompt card, int initiative) {
        StrategyCardRules.play(test.context(card), card, card.buttons().getFirst(), initiative);
    }

    // Home holds a token but no ships, while the token on the neighbouring system strands a loaded carrier one
    // system short of a free planet. Taking that token back is what lets the carrier move on and land.
    @Test
    void takesBackTheTokenThatUnlocksTheBiggerPlan() {
        test.game.setStrategyCardSet("pok");
        String ccId = Mapper.getCCID(test.nekro.getColor());
        home.addCC(ccId);
        Tile stranded = test.place("40", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(stranded, "space", test.nekro, UnitType.Carrier, 1);
        test.units(stranded, "space", test.nekro, UnitType.Infantry, 2);
        stranded.addCC(ccId);
        test.place("26", beyond(stranded.getPosition()));
        changed();
        AiPrompt card = prompt("warfare", PromptSource.PUBLIC, NOW, "sc_no_follow_6", "primaryOfWarfare");
        play(card, 6);
        assertThat(resolve(card)).isEqualTo("primaryOfWarfare");

        AiPrompt removals = prompt(
                "removals",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_removeCCFromBoard_warfare_" + AiTestGame.HOME,
                "FFCC_nekro_removeCCFromBoard_warfare_" + stranded.getPosition());

        assertThat(resolve(card, removals)).isEqualTo("FFCC_nekro_removeCCFromBoard_warfare_" + stranded.getPosition());
    }

    private static String beyond(String position) {
        return PositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate))
                .filter(candidate -> !PositionMapper.getAdjacentTilePositions(AiTestGame.HOME)
                        .contains(candidate))
                .findFirst()
                .orElseThrow();
    }

    // The token on the neighbouring system holds a loaded carrier above a free planet. Taking it back lets the
    // carrier's infantry land there, which beats taking back the token on an empty home.
    @Test
    void takesBackTheTokenThatLetsStrandedInfantryLand() {
        test.game.setStrategyCardSet("pok");
        String ccId = Mapper.getCCID(test.nekro.getColor());
        home.addCC(ccId);
        Tile lodor = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(lodor, "space", test.nekro, UnitType.Carrier, 1);
        test.units(lodor, "space", test.nekro, UnitType.Infantry, 2);
        lodor.addCC(ccId);
        changed();
        AiPrompt card = prompt("warfare", PromptSource.PUBLIC, NOW, "sc_no_follow_6", "primaryOfWarfare");
        play(card, 6);
        resolve(card);

        AiPrompt removals = prompt(
                "removals",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_removeCCFromBoard_warfare_" + AiTestGame.HOME,
                "FFCC_nekro_removeCCFromBoard_warfare_" + lodor.getPosition());

        assertThat(resolve(card, removals)).isEqualTo("FFCC_nekro_removeCCFromBoard_warfare_" + lodor.getPosition());
    }

    // A landing fleet next to home: Diplomacy first locks the home system, then readies planets as before.
    @Test
    void locksTheThreatenedSystemWithDiplomacy() {
        Tile next = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(next, "space", test.sol, UnitType.Carrier, 2);
        test.units(next, "space", test.sol, UnitType.Infantry, 6);
        changed();
        AiPrompt card = prompt(
                "diplomacy",
                PromptSource.PUBLIC,
                NOW,
                "sc_follow_2",
                "FFCC_nekro_diploSystem",
                "diploRefresh2",
                "sc_no_follow_2");
        play(card, 2);
        assertThat(resolve(card)).isEqualTo("FFCC_nekro_diploSystem");

        AiPrompt systems = prompt(
                "systems",
                PromptSource.AI_THREAD,
                NOW,
                "FFCC_nekro_diplo_lodor_diploP",
                "FFCC_nekro_diplo_mordaiii_diploP");

        assertThat(resolve(card, systems)).isEqualTo("FFCC_nekro_diplo_mordaiii_diploP");
    }

    // Nothing threatens Nekro: Diplomacy still locks its most valuable system, then readies planets.
    @Test
    void locksTheDiplomacySystemEvenWithoutAThreat() {
        test.nekro.exhaustPlanet("mordaiii");
        AiPrompt card = prompt(
                "diplomacy",
                PromptSource.PUBLIC,
                NOW,
                "sc_follow_2",
                "FFCC_nekro_diploSystem",
                "diploRefresh2",
                "sc_no_follow_2");
        play(card, 2);

        assertThat(resolve(card)).isEqualTo("FFCC_nekro_diploSystem");
    }

    // With trade goods to spend, Thunder's Edge Construction uses the home dock's production instead of its first
    // structure.
    @Test
    void producesWithThundersEdgeConstruction() {
        test.nekro.setTg(10);
        changed();
        AiPrompt card = prompt(
                "construction",
                PromptSource.PUBLIC,
                NOW,
                "sc_no_follow_4",
                "constructionPrimary_produce",
                "construction_pds",
                "construction_spacedock");
        play(card, 4);
        assertThat(resolve(card)).isEqualTo("constructionPrimary_produce");

        AiPrompt docks = prompt("docks", PromptSource.PUBLIC, NOW, "FFCC_nekro_constructionBuild_" + AiTestGame.HOME);
        assertThat(resolve(card, docks)).isEqualTo("FFCC_nekro_constructionBuild_" + AiTestGame.HOME);

        AiPrompt produce = prompt(
                "produce",
                PromptSource.PUBLIC,
                NOW,
                List.of("FFCC_nekro_deleteButtons_construction_" + AiTestGame.HOME),
                List.of("Done Producing Units"));
        AiTurnContext context = test.context(card, docks, produce);
        assertThat(StrategyCardRules.buildWithConstruction(context).map(AiTestGame::pressedId))
                .contains("FFCC_nekro_deleteButtons_construction_" + AiTestGame.HOME);
    }

    // Round 1: the one system in reach opens onto two more, and the opening outlook wants two loaded carriers to move
    // forward together. Construction's production is planned with a carrier past the fleet limit (the destroyer is
    // removed for it afterwards), even though the opening, not the units themselves, is what the card is played for.
    @Test
    void producesACarrierPastTheFleetLimitForTheOpening() {
        test.game.setRound(1);
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(3);
        test.nekro.setTg(3);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        String neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("27", neighbour);
        List<String> nearHome = PositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        List<String> past = PositionMapper.getAdjacentTilePositions(neighbour).stream()
                .filter(candidate ->
                        !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate) && !nearHome.contains(candidate))
                .toList();
        test.place("28", past.get(0));
        test.place("30", past.get(1));
        changed();
        AiPrompt card = prompt(
                "construction",
                PromptSource.PUBLIC,
                NOW,
                "sc_no_follow_4",
                "constructionPrimary_produce",
                "construction_pds",
                "construction_spacedock");
        play(card, 4);
        assertThat(resolve(card)).isEqualTo("constructionPrimary_produce");

        AiPrompt docks = prompt("docks", PromptSource.PUBLIC, NOW, "FFCC_nekro_constructionBuild_" + AiTestGame.HOME);
        assertThat(resolve(card, docks)).isEqualTo("FFCC_nekro_constructionBuild_" + AiTestGame.HOME);

        assertThat(test.memory.get("constructionBuild|1").orElseThrow()).contains("carrier~" + AiTestGame.HOME);
    }

    @Test
    void placesStructuresWithPokConstruction() {
        test.game.setStrategyCardSet("pok");
        test.nekro.setTg(10);
        changed();
        AiPrompt card = prompt(
                "construction",
                PromptSource.PUBLIC,
                NOW,
                "sc_no_follow_4",
                "construction_pds",
                "construction_spacedock");
        play(card, 4);

        assertThat(resolve(card)).startsWith("construction_");
    }

    // Once the agenda phase is running, Politics also looks at the top two agendas and buries one that would hurt
    // the seat: Public Execution, while Nekro is not behind on points.
    @Test
    void buriesAHarmfulAgendaWithPolitics() {
        test.game.addCustomPO(CUSTODIANS, 0);
        test.game.scorePublicObjective(test.sol.getUserID(), CUSTODIANS_ID);
        changed();
        AiPrompt card = prompt("politics", PromptSource.PUBLIC, NOW, "sc_follow_3", "sc_no_follow_3", "sc_ac_draw");
        AiPrompt speaker = prompt("speaker", PromptSource.PUBLIC, NOW, "FFCC_nekro_sc_3_assign_speaker_to_nekro");
        play(card, 3);
        assertThat(resolve(card, speaker)).isEqualTo("FFCC_nekro_sc_3_assign_speaker_to_nekro");
        assertThat(resolve(card, speaker)).isEqualTo("sc_ac_draw");

        AiPrompt draw = prompt("agendas", PromptSource.PUBLIC, NOW, "FFCC_nekro_drawAgenda_2");
        assertThat(resolve(card, speaker, draw)).isEqualTo("FFCC_nekro_drawAgenda_2");

        test.game.getSentAgendas().put("execution", 17);
        AiPrompt peeked = prompt("peeked", PromptSource.AI_THREAD, NOW, "topAgenda_17", "bottomAgenda_17");
        assertThat(resolve(card, speaker, draw, peeked)).isEqualTo("bottomAgenda_17");
    }

    @Test
    void leavesTheAgendaDeckAloneBeforeTheAgendaPhase() {
        AiPrompt card = prompt("politics", PromptSource.PUBLIC, NOW, "sc_follow_3", "sc_no_follow_3", "sc_ac_draw");
        AiPrompt speaker = prompt("speaker", PromptSource.PUBLIC, NOW, "FFCC_nekro_sc_3_assign_speaker_to_nekro");
        AiPrompt draw = prompt("agendas", PromptSource.PUBLIC, NOW, "FFCC_nekro_drawAgenda_2");
        play(card, 3);
        resolve(card, speaker);
        resolve(card, speaker);

        assertThat(resolve(card, speaker, draw)).isEqualTo("nothing");
    }
}
