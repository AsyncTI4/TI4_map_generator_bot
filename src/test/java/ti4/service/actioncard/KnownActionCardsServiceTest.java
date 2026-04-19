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

        assertThat(viewer.getStoredList("knownActionCards_hacan"))
                .containsExactlyInAnyOrder("dh1", "s_retreat1", "s_retreat2");

        target.removeActionCard(12);

        assertThat(viewer.getStoredList("knownActionCards_hacan")).containsExactlyInAnyOrder("dh1", "s_retreat2");
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

        viewer.addToStoredList("knownActionCards_hacan", "dh1", "s_retreat1");
        viewer.addToStoredList("knownActionCards_saar", "s_retreat2");

        String text = KnownActionCardsService.getKnownActionCardsText(game, viewer);

        assertThat(text).contains("Known action cards in other players' hands");
        assertThat(text).contains("Hacan");
        assertThat(text).contains("Saar");
        assertThat(text).contains("Direct Hit");
        assertThat(text).contains("Skilled Retreat");
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

    private Player createPlayer(Game game, String userId, String faction, String color) {
        Player player = new Player(userId, userId, game);
        player.setFaction(faction);
        player.setColor(color);
        player.setUserName(userId);
        return player;
    }
}
