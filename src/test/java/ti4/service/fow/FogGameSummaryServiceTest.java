package ti4.service.fow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
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

        MessageEmbed optionsEmbed =
                FogGameSummaryService.buildEmbeds(game, false).get(2);
        String visibility = optionsEmbed.getFields().stream()
                .filter(field -> "Visibility".equals(field.getName()))
                .findFirst()
                .orElseThrow()
                .getValue();

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

        assertThat(embeds).hasSize(4);
        for (MessageEmbed embed : embeds) {
            assertThat(embed.getLength()).isLessThanOrEqualTo(MessageEmbed.EMBED_MAX_LENGTH_BOT);
            assertThat(embed.getFields()).hasSizeLessThanOrEqualTo(25);
            embed.getFields().forEach(field -> {
                assertThat(field.getValue()).isNotBlank();
                assertThat(field.getValue().length()).isLessThanOrEqualTo(MessageEmbed.VALUE_MAX_LENGTH);
            });
        }
        String players = embeds.get(3).getFields().stream()
                .filter(field -> field.getName().startsWith("Players"))
                .map(MessageEmbed.Field::getValue)
                .reduce("", String::concat);
        FACTIONS.forEach(faction -> assertThat(players).contains(faction));
    }
}
