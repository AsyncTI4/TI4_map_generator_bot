package ti4.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.persistence.TestGameHarness;
import ti4.testUtils.BaseTi4Test;

class ModernGameInfoServiceTest extends BaseTi4Test {

    @BeforeEach
    void setUp() {
        JdaService.testingMode = true;
        JdaService.jda = mock(JDA.class);
    }

    private static MessageEmbed titled(List<MessageEmbed> embeds, String title) {
        return embeds.stream()
                .filter(embed -> title.equals(embed.getTitle()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no embed " + title));
    }

    private static String fieldValue(MessageEmbed embed, String name) {
        return embed.getFields().stream()
                .filter(field -> name.equals(field.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no field " + name))
                .getValue();
    }

    // A game before setup has no decks; the report must still build.
    @Test
    void bareGameBuildsEveryEmbed() {
        Game game = new Game();
        game.setName("modern-bare");

        List<MessageEmbed> embeds = ModernGameInfoService.buildEmbeds(game, false, true);

        assertThat(embeds)
                .extracting(MessageEmbed::getTitle)
                .contains("Content", "Progress", "Decks", "Players", "Settings & channels");
    }

    // Same gate as the classic output: a private game hides the players and the map.
    @Test
    void privateGameLeavesOutPlayersAndTheMap() {
        Game game = new Game();
        game.setName("modern-private");

        List<MessageEmbed> embeds = ModernGameInfoService.buildEmbeds(game, true, false);

        assertThat(embeds).extracting(MessageEmbed::getTitle).doesNotContain("Players");
        assertThat(fieldValue(embeds.getFirst(), "Map")).contains("hidden");
        assertThat(fieldValue(embeds.getFirst(), "Private game")).isEqualTo("Yes");
    }

    // A fog player who cannot see the whole map has players hidden, but the game itself is not private; the two
    // used to be conflated, so a GM and a player saw different "Private game" values for the same game.
    @Test
    void hiddenMapDoesNotMakeAGamePrivate() {
        Game game = new Game();
        game.setName("modern-fog-player");
        game.setFowMode(true);

        List<MessageEmbed> embeds = ModernGameInfoService.buildEmbeds(game, false, false);

        assertThat(embeds).extracting(MessageEmbed::getTitle).doesNotContain("Players");
        assertThat(fieldValue(embeds.getFirst(), "Private game")).isEqualTo("No");
    }

    @Test
    void normalGameProgressListsRevealedObjectiveNames() {
        Game game = new Game();
        game.setName("modern-objectives");
        game.getRevealedPublicObjectives().put("push_boundaries", 1);

        MessageEmbed progress = titled(ModernGameInfoService.buildEmbeds(game, false, true), "Progress");

        assertThat(fieldValue(progress, "Stage 1 objectives")).contains("Push Boundaries");
    }

    @Test
    void strategyCardTradeGoodsAreReadable() {
        Map<Integer, Integer> tradeGoods = new TreeMap<>(Map.of(7, 2, 2, 1, 4, 0));

        assertThat(ModernGameInfoService.scTradeGoods(tradeGoods)).isEqualTo("SC 2: 1 TG · SC 7: 2 TG");
    }

    @Test
    void fogGameGetsTheFogInfoPointer() {
        Game game = new Game();
        game.setName("modern-fog");
        game.setFowMode(true);

        MessageEmbed overview =
                ModernGameInfoService.buildEmbeds(game, false, false).getFirst();

        assertThat(overview.getFooter()).isNotNull();
        assertThat(overview.getFooter().getText()).contains("/fow game_info");
    }

    @Test
    void setUpGameStaysWithinDiscordLimits() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();

            List<MessageEmbed> embeds = ModernGameInfoService.buildEmbeds(game, false, true);

            assertThat(fieldValue(titled(embeds, "Decks"), "Action cards")).contains(" left");
            for (MessageEmbed embed : embeds) {
                assertThat(embed.getLength()).isLessThanOrEqualTo(MessageEmbed.EMBED_MAX_LENGTH_BOT);
                assertThat(embed.getFields()).hasSizeLessThanOrEqualTo(25);
                embed.getFields()
                        .forEach(field -> assertThat(field.getValue().length())
                                .isLessThanOrEqualTo(MessageEmbed.VALUE_MAX_LENGTH));
            }
        }
    }
}
