package ti4.discord.interactions.commands.homebrew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.commands.Command;
import ti4.discord.interactions.commands.GameStateContainer;
import ti4.discord.interactions.commands.Subcommand;

class HomebrewCommandTest {

    private final HomebrewCommand command = new HomebrewCommand();

    // The slash listener picks the game lock from the resolved command, so a grouped subcommand that saves the game
    // must resolve to the leaf itself, not to its group, or it would run without the write lock.
    @Test
    void groupedSubcommandResolvesToTheLeafSoTheListenerTakesTheGameLock() {
        Command<SlashCommandInteractionEvent> resolved =
                command.getSubcommand(event("omegaphase", "assign_player_priority"));

        assertThat(resolved).isInstanceOf(Subcommand.class).isInstanceOf(GameStateContainer.class);
        assertThat(((GameStateContainer) resolved).isSaveGame()).isTrue();
        assertThat(resolved.getName()).isEqualTo("assign_player_priority");
    }

    @Test
    void everyOldTopLevelCommandIsNowAGroup() {
        assertThat(command.getSubcommandGroups()).containsOnlyKeys("lost_legacies", "monument", "omegaphase");
        assertThat(command.getSubcommandGroups().get("lost_legacies").getGroupSubcommands())
                .containsOnlyKeys(
                        "add_fragmentum_token",
                        "hc_discard",
                        "hc_draw",
                        "hc_draw_specific",
                        "hc_send",
                        "purged_stoneborn_frags",
                        "remove_fragmentum_token",
                        "relic_trading_hub",
                        "rev_leader_fix",
                        "unleash",
                        "view_stoneborn_frags");
        assertThat(command.getSubcommandGroups().get("monument").getGroupSubcommands())
                .containsOnlyKeys(
                        "ready", "exhaust", "belkosea_token_add", "belkosea_token_remove", "belkosea_token_move");
        assertThat(command.getSubcommandGroups().get("omegaphase").getGroupSubcommands())
                .hasSize(6);
    }

    @Test
    void unknownGroupOrSubcommandResolvesToNothingAndIsNotAccepted() {
        SlashCommandInteractionEvent unknownGroup = event("nope", "assign_player_priority");
        SlashCommandInteractionEvent unknownLeaf = event("omegaphase", "nope");

        assertThat(command.getSubcommand(unknownGroup)).isNull();
        assertThat(command.getSubcommand(unknownLeaf)).isNull();
        assertThat(command.accept(unknownLeaf)).isFalse();
    }

    private static SlashCommandInteractionEvent event(String group, String subcommand) {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getName()).thenReturn("homebrew");
        when(event.getInteraction().getSubcommandGroup()).thenReturn(group);
        when(event.getInteraction().getSubcommandName()).thenReturn(subcommand);
        return event;
    }
}
