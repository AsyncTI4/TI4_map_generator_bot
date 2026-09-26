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
import ti4.game.Tile;
import ti4.service.option.FOWOptionService.FOWOption;
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

    // ---- fog memory: "have I ever seen this system" -----------------------------------------

    @Test
    void hasEverSeenTile_falseBeforeSeeing_trueAfter() {
        game.setFowMode(true);
        Tile tile = new Tile("18", "000");
        game.setTile(tile);

        assertThat(FoWHelper.hasEverSeenTile(player, "000")).isFalse();
        player.updateFogTile(tile, null);
        assertThat(FoWHelper.hasEverSeenTile(player, "000")).isTrue();
    }

    @Test
    void hasEverSeenTile_survivesTheTileBeingFlipped() {
        // The memory records position -> tileID, but FlipTileService rewrites tile IDs in place (82a -> 82b
        // and similar). Matching on the remembered tileID would turn a system the player genuinely scouted
        // into a false negative, so the check is deliberately position-only.
        game.setFowMode(true);
        Tile before = new Tile("82a", "305");
        game.setTile(before);
        player.updateFogTile(before, null);

        game.setTile(new Tile("82b", "305"));

        assertThat(FoWHelper.hasEverSeenTile(player, "305")).isTrue();
    }

    @Test
    void knowledgePredicates_outsideFogEverythingIsKnown() {
        game.setFowMode(false);
        Tile tile = new Tile("18", "000");
        game.setTile(tile);

        assertThat(FoWHelper.knowsTile(game, player, "000")).isTrue();
        // Never seen, not visible, and nobody owns it - outside fog it is known regardless.
        assertThat(FoWHelper.knowsTile(game, player, "999")).isTrue();
        assertThat(FoWHelper.knowsPlanetExists(game, player, "mr")).isTrue();
        assertThat(FoWHelper.getKnownTilePositions(game, player)).contains("000");
    }

    @Test
    void getKnownTilePositions_inFogUnionsVisibleAndRemembered() {
        game.setFowMode(true);
        Tile remembered = new Tile("25", "304");
        game.setTile(remembered);
        assertThat(FoWHelper.getKnownTilePositions(game, player)).doesNotContain("304");

        player.updateFogTile(remembered, null);
        assertThat(FoWHelper.getKnownTilePositions(game, player)).contains("304");
    }

    // ---- canSeeStatsOfPlayer: per-PN-type stat-reveal toggles --------------------------------

    private Player addViewer() {
        Player viewer = game.addPlayer("viewer-id", "viewer");
        viewer.setFaction("hacan");
        viewer.setColor("blue");
        return viewer;
    }

    @Test
    void canSeeStatsOfPlayer_playAreaFactionPN_defaultOptions_revealsStats() {
        game.setFowMode(true);
        Player viewer = addViewer();
        player.addOwnedPromissoryNoteByID("convoys"); // hacan faction PN, playArea: true
        viewer.addPromissoryNoteToPlayArea("convoys");

        assertThat(FoWHelper.canSeeStatsOfPlayer(game, player, viewer)).isTrue();
    }

    @Test
    void canSeeStatsOfPlayer_playAreaFactionPN_withFactionPnToggleOn_doesNotReveal() {
        game.setFowMode(true);
        game.setFowOption(FOWOption.HIDE_STATS_VIA_FACTION_PN, true);
        Player viewer = addViewer();
        player.addOwnedPromissoryNoteByID("convoys");
        viewer.addPromissoryNoteToPlayArea("convoys");

        assertThat(FoWHelper.canSeeStatsOfPlayer(game, player, viewer)).isFalse();
    }

    @Test
    void canSeeStatsOfPlayer_playAreaFactionPN_withUnrelatedAllianceToggleOn_stillReveals() {
        game.setFowMode(true);
        game.setFowOption(FOWOption.HIDE_STATS_VIA_ALLIANCE, true);
        Player viewer = addViewer();
        player.addOwnedPromissoryNoteByID("convoys");
        viewer.addPromissoryNoteToPlayArea("convoys");

        assertThat(FoWHelper.canSeeStatsOfPlayer(game, player, viewer)).isTrue();
    }

    @Test
    void canSeeStatsOfPlayer_playAreaBlackSpectrumAllianceHomebrew_withAllianceToggleOn_doesNotReveal() {
        // bsp_arborec_alliance is a faction-specific homebrew replacement for the generic
        // <color>_an Alliance card (homebrewReplacesID: "<color>_an"), so it must be classified as
        // Alliance, not Faction PN, even though its own alias doesn't end in "_an".
        game.setFowMode(true);
        game.setFowOption(FOWOption.HIDE_STATS_VIA_ALLIANCE, true);
        player.setFaction("arborec");
        Player viewer = addViewer();
        player.addOwnedPromissoryNoteByID("bsp_arborec_alliance");
        viewer.addPromissoryNoteToPlayArea("bsp_arborec_alliance");

        assertThat(FoWHelper.canSeeStatsOfPlayer(game, player, viewer)).isFalse();
    }

    @Test
    void canSeeStatsOfPlayer_playAreaBlackSpectrumAllianceHomebrew_withUnrelatedFactionPnToggleOn_stillReveals() {
        game.setFowMode(true);
        game.setFowOption(FOWOption.HIDE_STATS_VIA_FACTION_PN, true);
        player.setFaction("arborec");
        Player viewer = addViewer();
        player.addOwnedPromissoryNoteByID("bsp_arborec_alliance");
        viewer.addPromissoryNoteToPlayArea("bsp_arborec_alliance");

        assertThat(FoWHelper.canSeeStatsOfPlayer(game, player, viewer)).isTrue();
    }

    @Test
    void canSeeStatsOfPlayer_playAreaSftt_defaultOptions_revealsStats() {
        game.setFowMode(true);
        Player viewer = addViewer();
        player.addOwnedPromissoryNoteByID("red_sftt"); // player's own color-templated SftT card
        viewer.addPromissoryNoteToPlayArea("red_sftt");

        assertThat(FoWHelper.canSeeStatsOfPlayer(game, player, viewer)).isTrue();
    }

    @Test
    void canSeeStatsOfPlayer_playAreaSftt_withSfttToggleOn_doesNotReveal() {
        game.setFowMode(true);
        game.setFowOption(FOWOption.HIDE_STATS_VIA_SFTT, true);
        Player viewer = addViewer();
        player.addOwnedPromissoryNoteByID("red_sftt");
        viewer.addPromissoryNoteToPlayArea("red_sftt");

        assertThat(FoWHelper.canSeeStatsOfPlayer(game, player, viewer)).isFalse();
    }

    @Test
    void canSeeStatsOfPlayer_masterToggleOn_overridesPermissivePerTypeToggles() {
        game.setFowMode(true);
        game.setFowOption(FOWOption.STATS_FROM_HS_ONLY, true);
        Player viewer = addViewer();
        player.addOwnedPromissoryNoteByID("convoys");
        viewer.addPromissoryNoteToPlayArea("convoys");

        assertThat(FoWHelper.canSeeStatsOfPlayer(game, player, viewer)).isFalse();
    }
}
