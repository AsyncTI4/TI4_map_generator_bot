package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Focused regression guards for the fog-of-war identity rules in {@link FoWHelper}. These cover the
 * two behaviors that actually matter and that a live fog game makes hard to eyeball: {@code actorOrAnon}
 * hides the acting player's name under fog (and shows it otherwise), and {@code fogSafeTargetButton}
 * (both overloads) renders a neutral color chip / {@code "???"} under fog instead of the
 * identity-revealing faction name and emoji. Each keeps a fog + non-fog pair so the branch itself is
 * exercised, not just one side.
 *
 * <p>Deliberately NOT tested here: trivial one-line ternaries and JDA-mock-heavy channel-routing —
 * code review covers those, and no helper unit test can catch the real failure mode we've hit (picking
 * the wrong helper, or the wrong recipient, at a call site).
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

    // ---- actorOrAnon: hide the actor under fog ---------------------------------------------

    @Test
    void actorOrAnon_fogged_returnsSuppliedFogPhrase() {
        game.setFowMode(true);
        assertThat(FoWHelper.actorOrAnon(game, player, "someone")).isEqualTo("someone");
        assertThat(FoWHelper.actorOrAnon(game, player, "")).isEmpty();
    }

    @Test
    void actorOrAnon_unfogged_returnsRepresentationNoPing_regardlessOfFogPhrase() {
        game.setFowMode(false);
        String expected = player.getRepresentationNoPing();
        assertThat(FoWHelper.actorOrAnon(game, player, "someone")).isEqualTo(expected);
        assertThat(FoWHelper.actorOrAnon(game, player, "")).isEqualTo(expected);
    }

    // ---- fogSafeTargetButton: never leak faction identity in the button ---------------------

    @Test
    void fogSafeTargetButton_unfogged_usesFactionShortNameAndEmoji() {
        game.setFowMode(false);
        Button button = FoWHelper.fogSafeTargetButton("target_" + player.getFaction(), "gray", player);
        assertThat(button.getLabel()).isEqualTo(player.getFactionModel().getShortName());
        assertThat(button.getEmoji()).isNotNull();
    }

    @Test
    void fogSafeTargetButton_fogged_usesColorNameAndColorChipEmoji_notFactionIdentity() {
        game.setFowMode(true);
        Button button = FoWHelper.fogSafeTargetButton("target_" + player.getFaction(), "gray", player);
        assertThat(button.getLabel()).isEqualTo(player.getFactionNameOrColor());
        // The leak that matters: the icon must be the neutral color chip, NOT the faction emoji.
        assertThat(button.getEmoji()).isNotNull();
        assertThat(button.getEmoji().getFormatted()).isEqualTo(player.fogSafeEmoji());
    }

    @Test
    void fogSafeTargetButton_perViewer_unfogged_usesShortNameAndEmoji() {
        game.setFowMode(false);
        Player viewer = game.addPlayer("viewer-id", "hacan");
        Button button = FoWHelper.fogSafeTargetButton("target_" + player.getFaction(), "gray", player, viewer);
        assertThat(button.getLabel()).isEqualTo(player.getFactionModel().getShortName());
        assertThat(button.getEmoji()).isNotNull();
    }

    @Test
    void fogSafeTargetButton_perViewer_fogged_usesColorIfCanSeeStatsAndNoEmoji() {
        game.setFowMode(true);
        Player viewer = game.addPlayer("viewer-id", "hacan");
        viewer.setFaction("hacan");
        viewer.setColor("blue");
        Button button = FoWHelper.fogSafeTargetButton("target_" + player.getFaction(), "gray", player, viewer);
        assertThat(button.getLabel()).isEqualTo(player.getColorIfCanSeeStats(viewer));
        // Per-viewer button drops the icon entirely so it can't leak color to someone who can't see it.
        assertThat(button.getEmoji()).isNull();
    }
}
