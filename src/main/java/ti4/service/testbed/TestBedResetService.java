package ti4.service.testbed;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;

@UtilityClass
public class TestBedResetService {

    public record ResetResult(int removedSeats, int resetSeats, int missingChannels) {}

    public static ResetResult reset(Game game) {
        List<String> deletedChannelIds = TestBedChannelService.createdChannelIds(game);
        int missingChannels = TestBedChannelService.deleteCreatedChannels(game);
        int removedSeats = 0;
        int resetSeats = 0;
        for (Player player : new ArrayList<>(game.getPlayers().values())) {
            if (TestBedService.isVirtualSeat(player)) {
                game.removePlayer(player.getUserID());
                removedSeats++;
            } else if (player.isRealPlayer()) {
                replaceWithUnseatedPlayer(game, player, deletedChannelIds);
                resetSeats++;
            }
        }
        resetGameState(game);
        TestBedService.clearAllActingAs(game);
        TestBedService.markAsTestBed(game, false);
        game.removeStoredValue(TestBedShortcuts.STORED_KEY);
        game.removeStoredValue(TestBedApplyService.APPLIED_PRESET_KEY);
        return new ResetResult(removedSeats, resetSeats, missingChannels);
    }

    private static void replaceWithUnseatedPlayer(Game game, Player seated, List<String> deletedChannelIds) {
        String privateChannelId = seated.getPrivateChannelID();
        String cardsInfoThreadId = seated.getCardsInfoThreadID();
        game.removePlayer(seated.getUserID());
        Player fresh = game.addPlayer(seated.getUserID(), seated.getUserName());
        if (!deletedChannelIds.contains(privateChannelId)) {
            fresh.setPrivateChannelID(privateChannelId);
            fresh.setCardsInfoThreadID(cardsInfoThreadId);
        }
    }

    private static void resetGameState(Game game) {
        game.clearTileMap();
        game.setSpeakerUserID("");
        game.setActivePlayerID(null);
        game.setPhaseOfGame("");
        game.setRound(1);
        game.setActionCards(new ArrayList<>(Mapper.getDeck(game.getAcDeckID()).getNewShuffledDeck()));
        game.removeOverruleIfPurged();
        game.getDiscardActionCards().clear();
        game.setSecretObjectives(
                new ArrayList<>(Mapper.getDeck(game.getSoDeckID()).getNewShuffledDeck()));
        game.setRelics(new ArrayList<>(Mapper.getDeck(game.getRelicDeckID()).getNewShuffledDeck()));
    }
}
