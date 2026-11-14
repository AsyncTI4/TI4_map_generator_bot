package ti4.service.tournament.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.json.JsonMapperManager;
import ti4.service.tournament.TournamentSetupAccess;
import ti4.testUtils.BaseTi4Test;
import tools.jackson.databind.JsonNode;

class TournamentSetupCopierTest extends BaseTi4Test {

    @Test
    void copiesDecksFlagsAndRemovedCardsButNotTheShuffle() {
        Game source = freshGame();
        source.setAbsolMode(true);
        source.setVp(12);
        String removedAc = source.getActionCards().getFirst();
        source.getActionCards().remove(removedAc);
        // Remove one objective that sits in the dealt "next five" and one still in the deck.
        String peekedObjective = source.getPublicObjectives1Peekable().getFirst();
        source.getPublicObjectives1Peekable().remove(peekedObjective);
        source.setUpPeekableObjectives(5, 1);
        String deckObjective = source.getPublicObjectives1().getFirst();
        source.getPublicObjectives1().remove(deckObjective);

        Game target = freshGame();
        List<String> problems = TournamentSetupCopier.apply(source, target);

        assertThat(problems).isEmpty();
        assertThat(target.isAbsolMode()).isTrue();
        assertThat(target.getVp()).isEqualTo(12);
        assertThat(target.getAcDeckID()).isEqualTo(source.getAcDeckID());
        assertThat(target.getActionCards()).doesNotContain(removedAc).hasSameSizeAs(source.getActionCards());
        assertThat(target.getPublicObjectives1()).doesNotContain(peekedObjective, deckObjective);
        assertThat(target.getPublicObjectives1Peekable()).hasSize(5).doesNotContain(peekedObjective, deckObjective);
    }

    @Test
    void unsupportedModesCopyNothing() {
        Game source = freshGame();
        source.setOmegaPhaseMode(true);
        source.setVp(14);
        Game target = freshGame();

        List<String> problems = TournamentSetupCopier.apply(source, target);

        assertThat(problems).singleElement().asString().contains("Omega Phase");
        assertThat(target.getVp()).isNotEqualTo(14);
    }

    @Test
    void draftSettingsLoseTheSourceGamesPlayersMessageAndOrder() {
        String milty = """
                {"menuId":"main","messageId":"123","randomSetup":true,
                 "playerSettings":{"menuId":"players","gamePlayers":{"id":"Players","keys":["9000000000000001"]},
                   "presetDraftOrder":{"id":"StaticOrder","val":true},
                   "banFactions":{"id":"BanFactions","keys":["arborec"]}},
                 "sliceSettings":{"menuId":"slice","presetSlices":"25,69,34,49,45;24,28,46,47,67"}}""";

        JsonNode stripped = JsonMapperManager.basic().readTree(TournamentSetupCopier.stripMiltyJson(milty));

        assertThat(stripped.has("messageId")).isFalse();
        assertThat(stripped.has("randomSetup")).isFalse();
        assertThat(stripped.path("playerSettings").has("gamePlayers")).isFalse();
        assertThat(stripped.path("playerSettings")
                        .path("presetDraftOrder")
                        .path("val")
                        .asBoolean())
                .isFalse();
        // Bans and custom slices are settings, so they are kept.
        assertThat(stripped.path("playerSettings")
                        .path("banFactions")
                        .path("keys")
                        .size())
                .isEqualTo(1);
        assertThat(stripped.path("sliceSettings").path("presetSlices").asString())
                .startsWith("25,69");
        assertThat(TournamentSetupApplier.sliceCount("25,69,34,49,45;24,28,46,47,67|1,2,3,4,5"))
                .isEqualTo(3);
    }

    @Test
    void seatlessTournamentAdminsMayOnlyPressSetupButtonsInASetupGame() {
        Game setupGame = new Game();
        Member mod = memberWithRole("Tourney Mod");
        Member player = memberWithRole("Player");

        assertThat(TournamentSetupAccess.allowsSeatlessAdmin(setupGame, mod, "jmfA_main_startMilty"))
                .isFalse();
        setupGame.setStoredValue(TournamentSetupAccess.SETUP_GAME_KEY, "12");
        assertThat(TournamentSetupAccess.allowsSeatlessAdmin(setupGame, mod, "jmfA_main_startMilty"))
                .isTrue();
        assertThat(TournamentSetupAccess.allowsSeatlessAdmin(setupGame, mod, "exploreFront_"))
                .isFalse();
        assertThat(TournamentSetupAccess.allowsSeatlessAdmin(setupGame, player, "jmfA_main_startMilty"))
                .isFalse();
    }

    @Test
    void gamesWithScoredOrDealtCardsCannotBeTheSource() {
        Game source = freshGame();
        assertThat(TournamentSetupApplier.canCopy(source)).isTrue();

        // Purged or held cards would otherwise look "removed" in every tournament game.
        source.getScoredPublicObjectives().put("some_objective", new ArrayList<>(List.of("player")));

        assertThat(TournamentSetupApplier.canCopy(source)).isFalse();
        assertThat(TournamentSetupApplier.warnings(source, 6))
                .anySatisfy(warning -> assertThat(warning).contains("hasn't started"));
    }

    private static Game freshGame() {
        Game game = new Game();
        game.newGameSetup();
        return game;
    }

    private static Member memberWithRole(String roleName) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(roleName);
        Member member = mock(Member.class);
        when(member.getRoles()).thenReturn(List.of(role));
        return member;
    }
}
