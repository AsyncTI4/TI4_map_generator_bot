package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

import ti4.map.Game;
import ti4.map.Player;
import ti4.service.strategycard.PlayStrategyCardService;
import ti4.testUtils.BaseTi4Test;

public class TurnOrderHelperTest extends BaseTi4Test {
    @Test
    void GetNonInitiativeOrderFromPlayer_IncompletePriorityTrack_OmegaPhase() {
        var game = createTestGame(true);
        game.getPlayerFromColorOrFaction("letnev").setPriorityPosition(-1);
        game.getPlayerFromColorOrFaction("sol").setPriorityPosition(-1);
        var player = game.getPlayerFromColorOrFaction("sol");
        var players = Helper.getNonInitiativeOrderFromPlayer(player, game);
        assertEquals("argent", players.get(0).getFaction());
        assertEquals("nomad", players.get(1).getFaction());
        assertEquals("nekro", players.get(2).getFaction());
        assertEquals("xxcha", players.get(3).getFaction());
        assertEquals(4, players.size());
        player = game.getPlayerFromColorOrFaction("xxcha");
        players = Helper.getNonInitiativeOrderFromPlayer(player, game);
        assertEquals("xxcha", players.get(0).getFaction());
        assertEquals("argent", players.get(1).getFaction());
        assertEquals("nomad", players.get(2).getFaction());
        assertEquals("nekro", players.get(3).getFaction());
        assertEquals(4, players.size());
    }

    @Test
    void GetNonInitiativeOrderFromPlayer_NoSpeaker() {
        var game = createTestGame(false);
        game.setSpeakerUserID(null);
        var player = game.getPlayerFromColorOrFaction("sol");
        var players = Helper.getNonInitiativeOrderFromPlayer(player, game);
        assertEquals(0, players.size());
    }

    @Test
    void GetNonInitiativeOrderFromPlayer_NoSpeaker_OmegaPhase() {
        var game = createTestGame(true);
        game.setSpeakerUserID(null);
        var player = game.getPlayerFromColorOrFaction("sol");
        var players = Helper.getNonInitiativeOrderFromPlayer(player, game);
        assertEquals("sol", players.get(0).getFaction());
        assertEquals("nekro", players.get(1).getFaction());
        assertEquals("xxcha", players.get(2).getFaction());
        assertEquals("letnev", players.get(3).getFaction());
        assertEquals("argent", players.get(4).getFaction());
        assertEquals("nomad", players.get(5).getFaction());
    }

    @Test
    void GetNonInitiativeOrderFromPlayer() {
        var game = createTestGame(false);
        var player = game.getPlayerFromColorOrFaction("nekro");
        var players = Helper.getNonInitiativeOrderFromPlayer(player, game);
        assertEquals("nekro", players.get(0).getFaction());
        assertEquals("argent", players.get(1).getFaction());
        assertEquals("xxcha", players.get(2).getFaction());
        assertEquals("nomad", players.get(3).getFaction());
        assertEquals("letnev", players.get(4).getFaction());
        assertEquals("sol", players.get(5).getFaction());
    }

    @Test
    void GetNonInitiativeOrderFromPlayer_OmegaPhase() {
        var game = createTestGame(true);
        var player = game.getPlayerFromColorOrFaction("sol");
        var players = Helper.getNonInitiativeOrderFromPlayer(player, game);
        assertEquals("sol", players.get(0).getFaction());
        assertEquals("nekro", players.get(1).getFaction());
        assertEquals("xxcha", players.get(2).getFaction());
        assertEquals("letnev", players.get(3).getFaction());
        assertEquals("argent", players.get(4).getFaction());
        assertEquals("nomad", players.get(5).getFaction());
    }

    @Test
    void GetPlayerNonInitiativeNumber_IncompletePriorityTrack_OmegaPhase() {
        var game = createTestGame(true);
        game.getPlayerFromColorOrFaction("letnev").setPriorityPosition(-1);
        game.getPlayerFromColorOrFaction("sol").setPriorityPosition(-1);
        var player = game.getPlayerFromColorOrFaction("sol");
        assertEquals(1, Helper.getPlayerNonInitiativeNumber(player, game));
        player = game.getPlayerFromColorOrFaction("xxcha");
        assertEquals(4, Helper.getPlayerNonInitiativeNumber(player, game));
    }

    @Test
    void GetPlayerNonInitiativeNumber_NoSpeaker() {
        var game = createTestGame(false);
        game.setSpeakerUserID(null);
        var player = game.getPlayerFromColorOrFaction("sol");
        assertEquals(1, Helper.getPlayerNonInitiativeNumber(player, game));
    }

    @Test
    void GetPlayerNonInitiativeNumber_NoSpeaker_OmegaPhase() {
        var game = createTestGame(true);
        game.setSpeakerUserID(null);
        var player = game.getPlayerFromColorOrFaction("sol");
        assertEquals(3, Helper.getPlayerNonInitiativeNumber(player, game));
    }

    @Test
    void GetPlayerNonInitiativeNumber() {
        var game = createTestGame(false);
        var player = game.getPlayerFromColorOrFaction("nekro");
        assertEquals(4, Helper.getPlayerNonInitiativeNumber(player, game));
    }

    @Test
    void GetPlayerNonInitiativeNumber_OmegaPhase() {
        var game = createTestGame(true);
        var player = game.getPlayerFromColorOrFaction("sol");
        assertEquals(3, Helper.getPlayerNonInitiativeNumber(player, game));
    }

    @Test
    void GetNonInitiativeTurnOrder_IncompletePriorityTrack_OmegaPhase() {
        var game = createTestGame(true);
        game.getPlayerFromColorOrFaction("letnev").setPriorityPosition(-1);
        game.getPlayerFromColorOrFaction("sol").setPriorityPosition(-1);
        var players = Helper.getNonInitiativeOrder(game);
        assertEquals("argent", players.get(0).getFaction());
        assertEquals("nomad", players.get(1).getFaction());
        assertEquals("nekro", players.get(2).getFaction());
        assertEquals("xxcha", players.get(3).getFaction());
        assertEquals(4, players.size());
    }

    @Test
    void GetNonInitiativeTurnOrder_NoSpeaker() {
        var game = createTestGame(false);
        game.setSpeakerUserID(null);
        var players = Helper.getNonInitiativeOrder(game);
        assertEquals(0, players.size());
    }

    @Test
    void GetNonInitiativeTurnOrder_NoSpeaker_OmegaPhase() {
        var game = createTestGame(true);
        game.setSpeakerUserID(null);
        var players = Helper.getNonInitiativeOrder(game);
        assertEquals("argent", players.get(0).getFaction());
        assertEquals("nomad", players.get(1).getFaction());
        assertEquals("sol", players.get(2).getFaction());
        assertEquals("nekro", players.get(3).getFaction());
        assertEquals("xxcha", players.get(4).getFaction());
        assertEquals("letnev", players.get(5).getFaction());
    }

    @Test
    void GetNonInitiativeTurnOrder() {
        var game = createTestGame(false);
        var players = Helper.getNonInitiativeOrder(game);
        assertEquals("nomad", players.get(0).getFaction());
        assertEquals("letnev", players.get(1).getFaction());
        assertEquals("sol", players.get(2).getFaction());
        assertEquals("nekro", players.get(3).getFaction());
        assertEquals("argent", players.get(4).getFaction());
        assertEquals("xxcha", players.get(5).getFaction());
    }

    @Test
    void GetNonInitiativeTurnOrder_OmegaPhase() {
        var game = createTestGame(true);
        var players = Helper.getNonInitiativeOrder(game);
        assertEquals("argent", players.get(0).getFaction());
        assertEquals("nomad", players.get(1).getFaction());
        assertEquals("sol", players.get(2).getFaction());
        assertEquals("nekro", players.get(3).getFaction());
        assertEquals("xxcha", players.get(4).getFaction());
        assertEquals("letnev", players.get(5).getFaction());
    }

    @Test
    void ImperialAgenda_SODraw_Queue() {
        var game = createTestGame(false);
        game.setPhaseOfGame("agenda");
        PlayStrategyCardService.handleSOQueueing(game, false);
        assertEquals("nomad*letnev*sol*nekro*argent*", game.getStoredValue("potentialBlockers"));
        assertEquals("xxcha*", game.getStoredValue("factionsThatAreNotDiscardingSOs"));
        assertEquals("", game.getStoredValue("queueToDrawSOs"));
    }

    @Test
    void ImperialAgenda_SODraw_Queue_OmegaPhase() {
        var game = createTestGame(true);
        game.setPhaseOfGame("agenda");
        PlayStrategyCardService.handleSOQueueing(game, false);
        assertEquals("nomad*sol*nekro*letnev*argent*", game.getStoredValue("potentialBlockers"));
        assertEquals("xxcha*", game.getStoredValue("factionsThatAreNotDiscardingSOs"));
        assertEquals("", game.getStoredValue("queueToDrawSOs"));
    }

    @Test
    void Imperial_SODraw_Queue() {
        var game = createTestGame(false);
        PlayStrategyCardService.handleSOQueueing(game, false);
        assertEquals("sol*nekro*argent*nomad*letnev*", game.getStoredValue("potentialBlockers"));
        assertEquals("xxcha*", game.getStoredValue("factionsThatAreNotDiscardingSOs"));
        assertEquals("", game.getStoredValue("queueToDrawSOs"));
    }

    @Test
    void Imperial_SODraw_Queue_OmegaPhase() {
        var game = createTestGame(true);
        PlayStrategyCardService.handleSOQueueing(game, false);
        assertEquals("sol*nekro*nomad*argent*letnev*", game.getStoredValue("potentialBlockers"));
        assertEquals("xxcha*", game.getStoredValue("factionsThatAreNotDiscardingSOs"));
        assertEquals("", game.getStoredValue("queueToDrawSOs"));
    }

    private Game createTestGame(boolean withOmegaPhase) {
        Game game = new Game();
        game.setName("testGame");
        game.setOmegaPhaseMode(withOmegaPhase);
        createPlayer("p1", "nekro", "blue", Set.of(1), 4, game, false);
        createPlayer("p2", "argent", "blue", Set.of(4), 1, game, false);
        var playerScoringSecrets = createPlayer("p3", "xxcha", "blue", Set.of(7), 5, game, false);
        createPlayer("p4", "nomad", "blue", Set.of(2), 2, game, true);
        createPlayer("p5", "letnev", "blue", Set.of(5), 6, game, false);
        createPlayer("p6", "sol", "blue", Set.of(8), 3, game, false);
        for (var player : game.getPlayers().values()) {
            if (player == playerScoringSecrets) {
                player.setSecretScored("so1");
                player.setSecretScored("so2");
                player.setSecretScored("so3");
            } else {
                player.setSecret("so1");
                player.setSecret("so2");
                player.setSecret("so3");
            }
        }
        return game;
    }

    private Player createPlayer(String userId, String faction, String color, Set<Integer> stratCards, int priority, Game game, boolean speaker) {
        var player = game.addPlayer(userId, color);
        player.setFaction(faction);
        player.setColor(color);
        player.setSCs(stratCards);
        if (speaker) {
            game.setSpeaker(player);
        }
        if (game.isOmegaPhaseMode()) {
            player.setPriorityPosition(priority);
        }
        return player;
    }
}
