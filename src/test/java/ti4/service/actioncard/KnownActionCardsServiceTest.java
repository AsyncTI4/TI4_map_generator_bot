package ti4.service.actioncard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class KnownActionCardsServiceTest extends BaseTi4Test {

    @Test
    void rememberViewedHandAndForgetRemovedCards() {
        Game game = new Game();

        Player viewer = createPlayer(game, "viewer", "yssaril", "blue");
        Player target = createPlayer(game, "target", "hacan", "yellow");
        Player other = createPlayer(game, "other", "saar", "black");

        target.setActionCard("dh1", 11);
        target.setActionCard("s_retreat1", 12);
        target.setActionCard("s_retreat2", 13);

        LinkedHashMap<String, Player> players = new LinkedHashMap<>();
        players.put(viewer.getUserID(), viewer);
        players.put(target.getUserID(), target);
        players.put(other.getUserID(), other);
        game.setPlayers(players);

        KnownActionCardsService.rememberViewedHand(viewer, target);

        assertThat(viewer.getStoredList("knownActionCards_target"))
                .containsExactlyInAnyOrder("dh1", "s_retreat1", "s_retreat2");

        target.removeActionCard(12);

        assertThat(viewer.getStoredList("knownActionCards_target")).containsExactlyInAnyOrder("dh1", "s_retreat2");
    }

    @Test
    void getKnownActionCardsTextGroupsByPlayerAndCardName() {
        Game game = new Game();

        Player viewer = createPlayer(game, "viewer", "yssaril", "blue");
        Player firstTarget = createPlayer(game, "first", "hacan", "yellow");
        Player secondTarget = createPlayer(game, "second", "saar", "black");

        LinkedHashMap<String, Player> players = new LinkedHashMap<>();
        players.put(viewer.getUserID(), viewer);
        players.put(firstTarget.getUserID(), firstTarget);
        players.put(secondTarget.getUserID(), secondTarget);
        game.setPlayers(players);

        viewer.addToStoredList("knownActionCards_first", "dh1", "s_retreat1");
        viewer.addToStoredList("knownActionCards_second", "s_retreat2");

        String text = KnownActionCardsService.getKnownActionCardsText(game, viewer);

        assertThat(text).contains("Known action cards in other players' hands");
        assertThat(text).contains("Hacan");
        assertThat(text).contains("Saar");
        assertThat(text).contains("Direct Hit");
        assertThat(text).contains("Skilled Retreat");
    }

    @Test
    void forgetRemovedCardsForEveryViewerTrackingThatHand() {
        Game game = new Game();

        Player firstViewer = createPlayer(game, "viewer1", "yssaril", "blue");
        Player secondViewer = createPlayer(game, "viewer2", "jolnar", "purple");
        Player target = createPlayer(game, "target", "hacan", "yellow");

        target.setActionCard("dh1", 11);
        target.setActionCard("s_retreat1", 12);

        LinkedHashMap<String, Player> players = new LinkedHashMap<>();
        players.put(firstViewer.getUserID(), firstViewer);
        players.put(secondViewer.getUserID(), secondViewer);
        players.put(target.getUserID(), target);
        game.setPlayers(players);

        KnownActionCardsService.rememberViewedHand(firstViewer, target);
        KnownActionCardsService.rememberViewedHand(secondViewer, target);

        target.removeActionCard(11);

        assertThat(firstViewer.getStoredList("knownActionCards_target")).containsExactly("s_retreat1");
        assertThat(secondViewer.getStoredList("knownActionCards_target")).containsExactly("s_retreat1");
    }

    @Test
    void shouldShowKnownActionCardsButtonForMageonOwnersOrTrackedKnowledge() {
        Game game = new Game();
        Player viewer = createPlayer(game, "viewer", "yssaril", "blue");

        assertThat(KnownActionCardsService.shouldShowKnownActionCardsButton(viewer))
                .isFalse();

        viewer.setTechs(List.of("mi"));
        assertThat(KnownActionCardsService.shouldShowKnownActionCardsButton(viewer))
                .isTrue();

        viewer.setTechs(List.of());
        viewer.addToStoredList("knownActionCards_hacan", "dh1");
        assertThat(KnownActionCardsService.shouldShowKnownActionCardsButton(viewer))
                .isTrue();
    }

    @Test
    void rememberShownActionCardTracksSingleReveal() {
        Game game = new Game();
        Player viewer = createPlayer(game, "viewer", "sol", "blue");
        Player target = createPlayer(game, "target", "hacan", "yellow");

        KnownActionCardsService.rememberShownActionCard(viewer, target, "dh1");

        assertThat(viewer.getStoredList("knownActionCards_target")).containsExactly("dh1");
    }

    @Test
    void rememberShownActionCardToAllTracksPublicRevealForEveryoneElse() {
        Game game = new Game();
        Player source = createPlayer(game, "source", "sol", "blue");
        Player viewerOne = createPlayer(game, "viewer1", "hacan", "yellow");
        Player viewerTwo = createPlayer(game, "viewer2", "saar", "black");

        LinkedHashMap<String, Player> players = new LinkedHashMap<>();
        players.put(source.getUserID(), source);
        players.put(viewerOne.getUserID(), viewerOne);
        players.put(viewerTwo.getUserID(), viewerTwo);
        game.setPlayers(players);

        KnownActionCardsService.rememberShownActionCardToAll(game, source, "dh1");

        assertThat(source.getStoredList("knownActionCards_source")).isEmpty();
        assertThat(viewerOne.getStoredList("knownActionCards_source")).containsExactly("dh1");
        assertThat(viewerTwo.getStoredList("knownActionCards_source")).containsExactly("dh1");
    }

    private Player createPlayer(Game game, String userId, String faction, String color) {
        Player player = new Player(userId, userId, game);
        player.setFaction(faction);
        player.setColor(color);
        player.setUserName(userId);
        return player;
    }
}
