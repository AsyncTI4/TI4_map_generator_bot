package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Characterization tests for the Phase 1a fog-of-war helper primitives added to {@link FoWHelper}:
 * {@code actorOrAnon}, {@code actionsChannelOrLocal}, {@code fogSafeTargetButton}, and the per-viewer
 * overloads. Each asserts the primitive reproduces the exact behavior extracted
 * from the real call sites it will eventually replace, for both fogged and unfogged games. These are
 * permanent regression guards, not migration scaffolding — see the project fog-guard unification plan.
 */
class FoWHelperTest extends BaseTi4Test {

    private Game game;
    private Player player;

    @BeforeEach
    void setUp() {
        // A fresh JDA mock (other test classes null out / restub the shared one in their teardown).
        JdaService.testingMode = true;
        JdaService.jda = mock(JDA.class);

        game = new Game();
        game.setName("test-game");
        player = game.addPlayer("test-user-id", "winnu");
        player.setFaction("winnu");
        player.setColor("red");
    }

    // ---- actorOrAnon (Form 1) --------------------------------------------------------------

    @Test
    void actorOrAnon_fogged_returnsSuppliedFogPhrase() {
        game.setFowMode(true);
        assertThat(FoWHelper.actorOrAnon(game, player, "someone")).isEqualTo("someone");
        assertThat(FoWHelper.actorOrAnon(game, player, "Someone")).isEqualTo("Someone");
        assertThat(FoWHelper.actorOrAnon(game, player, "")).isEmpty();
    }

    @Test
    void actorOrAnon_unfogged_returnsRepresentationNoPing_forEveryFogPhrase() {
        game.setFowMode(false);
        String expected = player.getRepresentationNoPing();
        assertThat(FoWHelper.actorOrAnon(game, player, "")).isEqualTo(expected);
        assertThat(FoWHelper.actorOrAnon(game, player, "someone")).isEqualTo(expected);
        assertThat(FoWHelper.actorOrAnon(game, player, "Someone")).isEqualTo(expected);
    }

    /** Exactly the inline expression at ActionCardHelper.java:772, for both fog states. */
    @Test
    void actorOrAnon_reproducesActionCardHelperLine772() {
        game.setFowMode(true);
        assertThat(FoWHelper.actorOrAnon(game, player, "someone")).isEqualTo("someone");
        game.setFowMode(false);
        assertThat(FoWHelper.actorOrAnon(game, player, "someone")).isEqualTo(player.getRepresentationNoPing());
    }

    // ---- actionsChannelOrLocal (Form 12 channel half) --------------------------------------

    @Test
    void actionsChannelOrLocal_fogged_returnsLocalInteractionChannel() {
        game.setFowMode(true);
        var event = mock(GenericInteractionCreateEvent.class);
        MessageChannelUnion local = mock(MessageChannelUnion.class);
        when(event.getMessageChannel()).thenReturn(local);
        assertThat(FoWHelper.actionsChannelOrLocal(game, event)).isSameAs(local);
    }

    @Test
    void actionsChannelOrLocal_unfogged_differentChannel_returnsActionsChannel() {
        game.setFowMode(false);
        TextChannel actions = mock(TextChannel.class);
        when(JdaService.jda.getTextChannelById(nullable(String.class))).thenReturn(actions);
        var event = mock(GenericInteractionCreateEvent.class);
        when(event.getChannel()).thenReturn(null); // null != actions -> route to actions channel
        assertThat(FoWHelper.actionsChannelOrLocal(game, event)).isSameAs(actions);
    }

    // ---- fogSafeTargetButton (Form 10) -----------------------------------------------------

    @Test
    void fogSafeTargetButton_unfogged_usesFactionShortNameAndEmoji() {
        game.setFowMode(false);
        Button button = FoWHelper.fogSafeTargetButton("target_" + player.getFaction(), "gray", player);
        assertThat(button.getLabel()).isEqualTo(player.getFactionModel().getShortName());
        assertThat(button.getEmoji()).isNotNull();
    }

    @Test
    void fogSafeTargetButton_fogged_usesColorNameAndColorChipEmoji() {
        game.setFowMode(true);
        Button button = FoWHelper.fogSafeTargetButton("target_" + player.getFaction(), "gray", player);
        assertThat(button.getLabel()).isEqualTo(player.getFactionNameOrColor());
        // Fogged: neutral color-chip emoji (fogSafeEmoji), NOT the identity-revealing faction emoji.
        assertThat(button.getEmoji()).isNotNull();
        assertThat(button.getEmoji().getFormatted()).isEqualTo(player.fogSafeEmoji());
    }

    @Test
    void fogSafeTargetButton_styleSelectsButtonColor() {
        game.setFowMode(false);
        assertThat(FoWHelper.fogSafeTargetButton("id", "green", player).getStyle())
                .isEqualTo(ButtonStyle.SUCCESS);
        assertThat(FoWHelper.fogSafeTargetButton("id", "red", player).getStyle())
                .isEqualTo(ButtonStyle.DANGER);
        assertThat(FoWHelper.fogSafeTargetButton("id", null, player).getStyle()).isEqualTo(ButtonStyle.SECONDARY);
    }

    // ---- per-viewer overloads (Form F) -----------------------------------------------------

    @Test
    void identityOrColorIfCanSeeStats_fogged_returnsPerViewerColor() {
        game.setFowMode(true);
        Player viewer = game.addPlayer("viewer-id", "hacan");
        viewer.setFaction("hacan");
        viewer.setColor("blue");
        // Matches getColorIfCanSeeStats exactly (color if the viewer may see stats, else "???").
        assertThat(FoWHelper.identityOrColorIfCanSeeStats(game, player, viewer, "UNFOGGED"))
                .isEqualTo(player.getColorIfCanSeeStats(viewer));
    }

    @Test
    void identityOrColorIfCanSeeStats_unfogged_returnsSuppliedRendering() {
        game.setFowMode(false);
        Player viewer = game.addPlayer("viewer-id", "hacan");
        assertThat(FoWHelper.identityOrColorIfCanSeeStats(game, player, viewer, "UNFOGGED"))
                .isEqualTo("UNFOGGED");
    }

    @Test
    void fogSafeTargetButton_perViewer_fogged_usesColorIfCanSeeStatsAndNoEmoji() {
        game.setFowMode(true);
        Player viewer = game.addPlayer("viewer-id", "hacan");
        viewer.setFaction("hacan");
        viewer.setColor("blue");
        Button button = FoWHelper.fogSafeTargetButton("target_" + player.getFaction(), "gray", player, viewer);
        assertThat(button.getLabel()).isEqualTo(player.getColorIfCanSeeStats(viewer));
        assertThat(button.getEmoji()).isNull();
    }

    @Test
    void fogSafeTargetButton_perViewer_unfogged_usesShortNameAndEmoji() {
        game.setFowMode(false);
        Player viewer = game.addPlayer("viewer-id", "hacan");
        Button button = FoWHelper.fogSafeTargetButton("target_" + player.getFaction(), "gray", player, viewer);
        assertThat(button.getLabel()).isEqualTo(player.getFactionModel().getShortName());
        assertThat(button.getEmoji()).isNotNull();
    }
}
