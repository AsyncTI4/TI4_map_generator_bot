package ti4.spring.api.webdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.TestGameHarness;
import ti4.json.JsonMapperManager;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class FogWebDataServiceTest extends BaseTi4Test {

    private static final JsonMapper MAPPER = JsonMapperManager.basic();
    private static final String VIEWER = "deepwrought";

    @Test
    void unknownPlayerIsReducedToTheirColorEverywhere() {
        ObjectNode tree = (ObjectNode) MAPPER.readTree("""
                {
                  "playerData": [
                    {"faction": "cabal", "color": "red", "colorDisplayName": "Red", "userName": "bob",
                     "tg": 5, "passed": true, "techs": ["gd"], "unitCounts": {"dd": 2}},
                    {"faction": "hacan", "color": "yellow", "userName": "amy", "tg": 2}
                  ],
                  "scoreBreakdowns": {"cabal": {}, "hacan": {}},
                  "objectives": {"allObjectives": [{"factionProgress": {"cabal": 1, "hacan": 2}, "scoredFactions": ["cabal"]}]},
                  "tileUnitData": {"101": {"space": {"cabal": [{"entityId": "dd"}]},
                                           "planets": {"cabal": {"controlledBy": "cabal"}}}},
                  "gameState": {"activePlayer": "cabal"}
                }
                """);

        FogWebDataService.anonymize(tree, Map.of("cabal", "red"));

        JsonNode hidden = tree.get("playerData").get(0);
        assertThat(hidden.get("faction").asText()).isEqualTo("red");
        assertThat(hidden.get("userName").asText()).isEqualTo("Red");
        assertThat(hidden.get("tg").asInt()).isZero();
        assertThat(hidden.get("passed").asBoolean()).isFalse();
        assertThat(hidden.get("techs")).isEmpty();
        assertThat(hidden.get("unitCounts")).isEmpty();
        assertThat(hidden.get("fogHidden").asBoolean()).isTrue();
        assertThat(tree.get("playerData").get(1).get("userName").asText()).isEqualTo("amy");

        assertThat(tree.get("scoreBreakdowns").has("cabal")).isFalse();
        assertThat(tree.get("scoreBreakdowns").has("hacan")).isTrue();
        JsonNode objective = tree.get("objectives").get("allObjectives").get(0);
        assertThat(objective.get("factionProgress").has("cabal")).isFalse();
        assertThat(objective.get("scoredFactions").get(0).asText()).isEqualTo("red");

        JsonNode tile = tree.get("tileUnitData").get("101");
        assertThat(tile.get("space").has("red")).isTrue();
        // Planet ids can coincide with faction ids (e.g. Muaat), so planet keys are never renamed.
        assertThat(tile.get("planets").has("cabal")).isTrue();
        assertThat(tile.get("planets").get("cabal").get("controlledBy").asText())
                .isEqualTo("red");
        assertThat(tree.get("gameState").get("activePlayer").asText()).isEqualTo("red");
    }

    @Test
    void activityInUnseenSystemsIsDropped() {
        ObjectNode tree = (ObjectNode) MAPPER.readTree("""
                {"gameState": {"activeSystem": "305", "activeCombat": {"system": "305", "round": 1}}}
                """);

        FogWebDataService.forgetActivityInUnseenSystems(tree, Set.of("101"));

        assertThat(tree.get("gameState").get("activeSystem").isNull()).isTrue();
        assertThat(tree.get("gameState").get("activeCombat").isNull()).isTrue();
    }

    @Test
    void seatViewShowsNoUnitsInRememberedSystemsAndNeverNamesUnknownPlayers() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            // Only a visible home system reveals a player, so some seat must fail to identify someone.
            game.setFowOption(FOWOption.STATS_FROM_HS_ONLY, true);
            Map<String, String> factionByColor = new HashMap<>();
            game.getRealPlayers().forEach(player -> factionByColor.put(player.getColor(), player.getFaction()));
            int liveTileCount = game.getTileMap().size();
            List<String> seats = game.getRealPlayers().stream()
                    .filter(player -> !player.isNeutral())
                    .map(Player::getFaction)
                    .toList();

            int hiddenPlayers = 0;
            for (String seat : seats) {
                ObjectNode tree = FogWebDataService.foggedWebData(game, seat, Map.of("role", "player"));

                JsonNode fogTiles = tree.get("fogTiles");
                assertThat(fogTiles).as("%s remembers systems", seat).isNotEmpty();
                for (String position : fogTiles.propertyNames()) {
                    JsonNode tile = tree.get("tileUnitData").get(position);
                    assertThat(tile.get("space"))
                            .as("units %s sees in remembered %s", seat, position)
                            .isEmpty();
                    assertThat(tile.get("ccs"))
                            .as("tokens %s sees in remembered %s", seat, position)
                            .isEmpty();
                }

                String json = tree.toString();
                for (JsonNode area : tree.get("playerData")) {
                    if (!area.path("fogHidden").asBoolean()) continue;
                    hiddenPlayers++;
                    String hiddenFaction =
                            factionByColor.get(area.get("faction").asText());
                    assertThat(json)
                            .as("%s's view names %s", seat, hiddenFaction)
                            .doesNotContain("\"" + hiddenFaction + "\"");
                }
            }

            // Guards the loop above: without at least one unidentified player it would check nothing.
            assertThat(hiddenPlayers).isPositive();
            assertThat(game.getTileMap()).as("live game is not fogged").hasSize(liveTileCount);
        }
    }

    private static ObjectNode cardPoolTree() {
        return (ObjectNode) MAPPER.readTree("""
                {"cardPool": {
                  "secretObjectiveDeck": ["so_b", "so_a"],
                  "actionCardDiscard": ["sabotage1", "bribery", "ghost_ship"],
                  "culturalExploreDeck": ["c2", "c1"],
                  "culturalExploreDiscard": ["c9", "c3"],
                  "industrialExploreDeck": ["i1"], "industrialExploreDiscard": ["i4"],
                  "hazardousExploreDeck": ["h1"], "hazardousExploreDiscard": ["h4"],
                  "frontierExploreDeck": ["f1"], "frontierExploreDiscard": ["f8", "f2"],
                  "relicDeck": ["shard", "codex"],
                  "agendaDiscard": ["censure"]
                }}
                """);
    }

    private static List<String> cards(ObjectNode tree, String field) {
        List<String> cards = new ArrayList<>();
        tree.get("cardPool").get(field).forEach(card -> cards.add(card.asText()));
        return cards;
    }

    @Test
    void secretObjectiveDeckIsNeverSentToASeat() {
        // The bot refuses "Unscored Secret Objectives" to FoW players; the list is the deck minus every scored secret.
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            game.setPhaseOfGame("statusScoring");
            ObjectNode tree = cardPoolTree();

            FogWebDataService.hideDeckInfo(tree, game);

            assertThat(cards(tree, "secretObjectiveDeck")).isEmpty();
            assertThat(cards(tree, "agendaDiscard")).containsExactly("censure");
        }
    }

    @Test
    void exploreAndRelicDecksAreFoggyDuringTheActionPhase() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            game.setPhaseOfGame("action");
            ObjectNode tree = cardPoolTree();

            FogWebDataService.hideDeckInfo(tree, game);

            for (String type : List.of("cultural", "industrial", "hazardous", "frontier")) {
                assertThat(cards(tree, type + "ExploreDeck")).as(type).isEmpty();
                assertThat(cards(tree, type + "ExploreDiscard")).as(type).isEmpty();
            }
            assertThat(cards(tree, "relicDeck")).isEmpty();
        }
    }

    @Test
    void exploreDiscardsOutsideTheActionPhaseHideTheOrderTheyWereDrawnIn() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            game.setPhaseOfGame("agenda");
            ObjectNode tree = cardPoolTree();

            FogWebDataService.hideDeckInfo(tree, game);

            assertThat(cards(tree, "culturalExploreDeck")).containsExactly("c2", "c1");
            assertThat(cards(tree, "culturalExploreDiscard")).containsExactly("c3", "c9");
            assertThat(cards(tree, "frontierExploreDiscard")).containsExactly("f2", "f8");
            assertThat(cards(tree, "relicDeck")).containsExactly("shard", "codex");
        }
    }

    @Test
    void hideExploresOptionKeepsExploreAndRelicDecksHiddenInEveryPhase() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            game.setPhaseOfGame("agenda");
            game.setFowOption(FOWOption.HIDE_EXPLORES, true);
            ObjectNode tree = cardPoolTree();

            FogWebDataService.hideDeckInfo(tree, game);

            assertThat(cards(tree, "culturalExploreDeck")).isEmpty();
            assertThat(cards(tree, "relicDeck")).isEmpty();
        }
    }

    @Test
    void hideAcDiscardOptionKeepsOnlyPlayedActionCards() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            game.setPhaseOfGame("agenda");
            game.setFowOption(FOWOption.HIDE_AC_DISCARD, true);
            game.getPlayedActionCards().add("sabotage1");
            game.getPlayedActionCards().add("ghost_ship");
            ObjectNode tree = cardPoolTree();

            FogWebDataService.hideDeckInfo(tree, game);

            assertThat(cards(tree, "actionCardDiscard")).containsExactly("sabotage1", "ghost_ship");
        }
    }

    @Test
    void actionCardDiscardIsUntouchedWithoutTheOption() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            game.setPhaseOfGame("agenda");
            ObjectNode tree = cardPoolTree();

            FogWebDataService.hideDeckInfo(tree, game);

            assertThat(cards(tree, "actionCardDiscard")).containsExactly("sabotage1", "bribery", "ghost_ship");
        }
    }

    @Test
    void seatViewCarriesNoSecretObjectiveDeck() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);

            ObjectNode tree = FogWebDataService.foggedWebData(game, VIEWER, Map.of("role", "player"));

            assertThat(tree.get("cardPool").get("secretObjectiveDeck")).isEmpty();
        }
    }

    @Test
    void gameMasterSeesTheWholeGameOrAnySeat() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            FogWebDataService service = new FogWebDataService();

            JsonNode full = MAPPER.readTree(service.forGameMaster(game, null).orElseThrow());
            assertThat(full.has("fogTiles")).isFalse();
            assertThat(full.get("fogView").get("role").asText()).isEqualTo("gm");
            assertThat(full.get("fogView").get("seats").findValuesAsString("faction"))
                    .contains(VIEWER)
                    .doesNotContain("neutral");

            JsonNode asSeat =
                    MAPPER.readTree(service.forGameMaster(game, VIEWER).orElseThrow());
            assertThat(asSeat.has("fogTiles")).isTrue();
            assertThat(asSeat.get("fogView").get("viewingAs").asText()).isEqualTo(VIEWER);
            assertThat(asSeat.get("fogView").get("seats").findValuesAsString("faction"))
                    .as("the GM's seat list is never anonymized")
                    .containsAll(game.getRealPlayers().stream()
                            .filter(Player::isRealPlayer)
                            .filter(player -> !player.isNeutral())
                            .map(Player::getFaction)
                            .toList());

            Optional<String> unknownSeat = service.forGameMaster(game, "not-a-faction");
            assertThat(unknownSeat).isEmpty();
        }
    }
}
