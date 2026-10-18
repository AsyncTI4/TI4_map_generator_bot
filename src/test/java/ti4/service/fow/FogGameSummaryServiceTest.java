package ti4.service.fow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.TestGameHarness;
import ti4.image.MapSegment;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

class FogGameSummaryServiceTest extends BaseTi4Test {

    private static final List<String> FACTIONS =
            List.of("sol", "hacan", "xxcha", "jolnar", "arborec", "letnev", "muaat", "yssaril");
    private static final List<String> COLORS =
            List.of("red", "blue", "green", "yellow", "purple", "orange", "pink", "black");

    private Game game;

    @BeforeEach
    void setUpFogGame() {
        game = new Game();
        game.setName("fow-summary-test");
        game.setFowMode(true);
    }

    @Test
    void fogVariantDistinguishesFogPlusLightAndFranken() {
        assertThat(FogGameSummaryService.fogVariant(game)).isEqualTo("Fog");

        game.setFowOption(FOWOption.FOW_PLUS, true);
        assertThat(FogGameSummaryService.fogVariant(game)).isEqualTo("Fog+");

        Player franken = game.addPlayer("franken-id", "franken-user");
        franken.setFaction("franken1");
        franken.setColor("red");
        assertThat(FogGameSummaryService.fogVariant(game)).isEqualTo("Franken Fog+");

        Game lightFog = new Game();
        lightFog.setLightFogMode(true);
        assertThat(FogGameSummaryService.fogVariant(lightFog)).isEqualTo("Light Fog");
    }

    @Test
    void enabledOptionsIncludesDisableFractureStoredOutsideTheOptionMap() {
        game.setFowOption(FOWOption.HIDE_MAP, true);
        game.setNoFractureMode(true);

        assertThat(FogGameSummaryService.enabledOptions(game))
                .containsExactly(FOWOption.HIDE_MAP, FOWOption.DISABLE_FRACTURE);
    }

    @Test
    void fogOptionsEmbedListsUnsetOptionsInEnumOrder() {
        game.setFowOption(FOWOption.BRIGHT_NOVAS, true);

        String visibility = fieldValue(embedTitled("Fog options"), "Visibility");

        // HIDE_MAP was never set, but the GM still needs to see it as off.
        assertThat(visibility).startsWith("✅ Bright Novas").contains("🚫 Hide Unexplored Map");
        assertThat(visibility.indexOf("Bright Novas")).isLessThan(visibility.indexOf("Hide Unexplored Map"));
    }

    @Test
    void embedsStayWithinDiscordLimitsWithAFullTable() {
        for (int i = 0; i < FACTIONS.size(); i++) {
            Player player = game.addPlayer("user-" + i, "a-rather-long-discord-username-" + i);
            player.setFaction(FACTIONS.get(i));
            player.setColor(COLORS.get(i));
        }
        game.setCustomName("a custom name that people like to give their fog games");

        List<MessageEmbed> embeds = FogGameSummaryService.buildEmbeds(game, false);

        assertThat(embeds).hasSize(7);
        // Discord's 6000-character cap is per message; MessageHelper splits embeds across messages to stay under
        // it, so each embed on its own must fit.
        for (MessageEmbed embed : embeds) {
            assertThat(embed.getLength()).isLessThanOrEqualTo(MessageEmbed.EMBED_MAX_LENGTH_BOT);
            assertThat(embed.getFields()).hasSizeLessThanOrEqualTo(25);
            embed.getFields().forEach(field -> {
                assertThat(field.getValue()).isNotBlank();
                assertThat(field.getValue().length()).isLessThanOrEqualTo(MessageEmbed.VALUE_MAX_LENGTH);
            });
        }
        String players = embedTitled("People").getFields().stream()
                .filter(field -> field.getName().startsWith("Players"))
                .map(MessageEmbed.Field::getValue)
                .reduce("", String::concat);
        FACTIONS.forEach(faction -> assertThat(players).contains(faction));
    }

    @Test
    void galaxiesEmbedListsExtraGalaxiesWithTheirTileCounts() {
        game.setTile(new Tile("19", "000"));
        game.setTile(new Tile("20", "101"));
        game.setTile(new Tile("21", "a000"));
        game.setTile(new Tile("22", "a101"));
        game.setTile(new Tile("23", "c000"));

        String galaxies = fieldValue(embedTitled("Galaxies & sectors"), "Galaxies");

        assertThat(FogGameSummaryService.galaxyCount(game)).isEqualTo(3);
        assertThat(galaxies).contains("`main`").contains("`a`").contains("`c`").doesNotContain("`b`");
        assertThat(galaxies.lines()).anyMatch(line -> line.startsWith("`a`") && line.endsWith("2 tiles"));
        assertThat(galaxies.lines()).anyMatch(line -> line.startsWith("`main`") && line.endsWith("2 tiles"));
    }

    @Test
    void singleGalaxyGameWithoutSectorsReportsNone() {
        game.setTile(new Tile("19", "000"));

        MessageEmbed galaxies = embedTitled("Galaxies & sectors");

        assertThat(FogGameSummaryService.galaxyCount(game)).isEqualTo(1);
        assertThat(FogGameSummaryService.usesSectors(game)).isFalse();
        // One galaxy: the tile count is already in the overview, so the Galaxies field is left out.
        assertThat(galaxies.getFields()).noneMatch(field -> "Galaxies".equals(field.getName()));
        assertThat(fieldValue(galaxies, "Sectors")).isEqualTo("None");
    }

    @Test
    void sectorLinesIncludeHowManySystemsTheyHold() {
        game.setTile(new Tile("19", "000"));
        game.setTile(new Tile("20", "101"));
        MapSegment.put(game, new MapSegment("core", "000", 1));

        assertThat(fieldValue(embedTitled("Galaxies & sectors"), "Sectors"))
                .contains("core")
                .endsWith("2 systems");
    }

    // The Separate Fracture option can be on while no Fracture tile is placed yet; the field must not contradict it.
    @Test
    void fractureFieldDistinguishesNotInPlayFromSeparateMap() {
        game.setTile(new Tile("19", "000"));
        game.setFowOption(FOWOption.FRACTURE_SEPARATE_MAP, true);

        assertThat(fieldValue(embedTitled("Galaxies & sectors"), "Fracture")).isEqualTo("Not in play");

        game.setTile(new Tile("19", "frac1"));

        assertThat(fieldValue(embedTitled("Galaxies & sectors"), "Fracture")).isEqualTo("Separate map");
    }

    @Test
    void missingMapTemplateShowsNoneAndStrategyCardsShowTheSetName() {
        game.setMapTemplateID("null");

        MessageEmbed overview = FogGameSummaryService.buildEmbeds(game, false).getFirst();

        assertThat(fieldValue(overview, "Map template")).isEqualTo("None");
        assertThat(fieldValue(overview, "Strategy cards"))
                .isEqualTo(game.getStrategyCardSet().getName());
    }

    @Test
    void playerLinesShowVictoryPointsAndTheSpeaker() {
        Player player = game.addPlayer("player-id", "player-user");
        player.setFaction("sol");
        player.setColor("red");
        game.setSpeakerUserID("player-id");

        String players = fieldValue(embedTitled("People"), "Players");

        assertThat(players).contains("0 VP").contains("speaker");
    }

    @Test
    void progressEmbedListsObjectivesPerStageAndLaws() {
        MessageEmbed progress = embedTitled("Progress");

        assertThat(fieldValue(progress, "Stage 1 objectives")).endsWith("0 revealed\n0 staged");
        assertThat(fieldValue(progress, "Stage 2 objectives")).contains("revealed");
        assertThat(fieldValue(progress, "Laws in play")).isEqualTo("None");
    }

    // A bare Game has no decks until setup; the Decks embed must still show the deck ids instead of crashing.
    @Test
    void decksEmbedWithoutSetUpDecksShowsIdsOnly() {
        MessageEmbed decks = embedTitled("Decks");

        assertThat(decks.getDescription()).contains("not set up");
        assertThat(fieldValue(decks, "Action cards")).contains("`").doesNotContain("left");
    }

    @Test
    void setUpGameReportsCardsLeftPerDeckWithinDiscordLimits() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game loaded = harness.load();
            loaded.setFowMode(true);

            List<MessageEmbed> embeds = FogGameSummaryService.buildEmbeds(loaded, false);
            MessageEmbed decks = embeds.stream()
                    .filter(embed -> "Decks".equals(embed.getTitle()))
                    .findFirst()
                    .orElseThrow();

            assertThat(fieldValue(decks, "Action cards")).contains(" left\n`" + loaded.getAcDeckID() + "`");
            assertThat(fieldValue(decks, "Explores")).contains("Frontier **");
            for (MessageEmbed embed : embeds) {
                assertThat(embed.getLength()).isLessThanOrEqualTo(MessageEmbed.EMBED_MAX_LENGTH_BOT);
                embed.getFields()
                        .forEach(field -> assertThat(field.getValue().length())
                                .isLessThanOrEqualTo(MessageEmbed.VALUE_MAX_LENGTH));
            }
        }
    }

    @Test
    void fogOptionsEmbedCarriesGameSettingsAndLoreCount() {
        assertThat(fieldValue(embedTitled("Fog options"), "Settings"))
                .contains("Auto-ping")
                .contains("Lore entries: 0");
    }

    @Test
    void spectatorSeatsAreListedAsObserversNotAsOtherSeats() {
        Player player = game.addPlayer("player-id", "player-user");
        player.setFaction("sol");
        player.setColor("red");
        game.addPlayer("spectator-id", "spectator-user");

        MessageEmbed people = embedTitled("People");

        assertThat(fieldValue(people, "Observers")).isEqualTo("spectator-user");
        assertThat(people.getFields()).noneMatch(field -> "Other seats".equals(field.getName()));
    }

    // At game end the GM role is deleted before the settings log is built, so the GMs are captured first and
    // passed in; they must show as game masters and not fall through to the observer list.
    @Test
    void gameMastersPassedInAreShownEvenWithoutTheirRole() {
        Player gm = game.addPlayer("gm-id", "gm-user");

        MessageEmbed people = FogGameSummaryService.buildEmbeds(game, false, List.of(gm)).stream()
                .filter(embed -> "People".equals(embed.getTitle()))
                .findFirst()
                .orElseThrow();

        assertThat(fieldValue(people, "Game masters")).isEqualTo("gm-user");
        assertThat(people.getFields()).noneMatch(field -> "Observers".equals(field.getName()));
    }

    // The GM role is gone after game end (and the test game has no guild at all), so /fow game_info on an ended
    // game must fall back to the GM ids remembered at end time.
    @Test
    void rememberedGameMastersAreShownOnceTheRoleIsGone() {
        Player gm = game.addPlayer("gm-id", "gm-user");
        FogGameSummaryService.rememberGameMasters(game, List.of(gm));
        game.setHasEnded(true);

        assertThat(FogGameSummaryService.gameMasters(game)).containsExactly(gm);
        assertThat(fieldValue(embedTitled("People"), "Game masters")).isEqualTo("gm-user");
    }

    // Games ended before GMs were remembered cannot recover them; say so rather than claiming there was none.
    @Test
    void endedGameWithoutRememberedGameMastersExplainsTheGap() {
        game.setHasEnded(true);

        assertThat(fieldValue(embedTitled("People"), "Game masters")).startsWith("Unknown");
    }

    @Test
    void runningGameWithoutGameMastersReportsNone() {
        assertThat(fieldValue(embedTitled("People"), "Game masters")).isEqualTo("None");
    }

    private MessageEmbed embedTitled(String title) {
        return FogGameSummaryService.buildEmbeds(game, false).stream()
                .filter(embed -> title.equals(embed.getTitle()))
                .findFirst()
                .orElseThrow();
    }

    private static String fieldValue(MessageEmbed embed, String name) {
        return embed.getFields().stream()
                .filter(field -> name.equals(field.getName()))
                .findFirst()
                .orElseThrow()
                .getValue();
    }
}
