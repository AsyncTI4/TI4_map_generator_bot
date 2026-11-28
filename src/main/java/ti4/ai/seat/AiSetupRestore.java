package ti4.ai.seat;

import lombok.experimental.UtilityClass;
import ti4.ai.AiSeats;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class AiSetupRestore {

    static final String GAME_KEY = "aiSetupRestore";
    static final String SEAT_KEY = "aiSetupTile";
    static final String SPEAKER_KEY = "aiSetupSpeaker";
    private static final String SEPARATOR = "|";

    static void remember(Game game, Player seat, String homePosition) {
        if (game.getStoredValue(GAME_KEY).isEmpty()) {
            game.setStoredValue(
                    GAME_KEY,
                    String.join(
                            SEPARATOR,
                            String.valueOf(game.isHomebrew()),
                            String.valueOf(game.isNewTransactionMethod())));
        }
        String speaker = game.getSpeakerUserID();
        if (speaker != null && !speaker.isBlank()) seat.setStoredValue(SPEAKER_KEY, speaker);
        Tile replaced = game.getTileByPosition(homePosition);
        seat.setStoredValue(SEAT_KEY, homePosition + SEPARATOR + (replaced == null ? "" : replaced.getTileID()));
    }

    static void restore(Game game, Player seat) {
        String color = seat.getColor();
        if (color != null) {
            for (Tile tile : game.getTileMap().values()) tile.removeAllUnits(color);
        }
        restoreTile(game, seat);
        String[] flags = game.getStoredValue(GAME_KEY).split("\\" + SEPARATOR, -1);
        boolean lastSeat = AiSeats.aiSeats(game).stream()
                .allMatch(other -> other.getUserID().equals(seat.getUserID()));
        if (lastSeat && flags.length == 2) {
            game.setHomebrew(Boolean.parseBoolean(flags[0]));
            game.setNewTransactionMethod(Boolean.parseBoolean(flags[1]));
        }
        if (seat.getUserID().equals(game.getSpeakerUserID())) {
            String previous = seat.getStoredValue(SPEAKER_KEY);
            Player previousSpeaker = game.getPlayer(previous);
            boolean stillPlaying = previousSpeaker != null && previousSpeaker != seat && previousSpeaker.isRealPlayer();
            game.setSpeakerUserID(stillPlaying ? previous : "");
        }
        seat.removeStoredValue(SPEAKER_KEY);
        if (lastSeat) game.removeStoredValue(GAME_KEY);
    }

    private static void restoreTile(Game game, Player seat) {
        String[] parts = seat.getStoredValue(SEAT_KEY).split("\\" + SEPARATOR, -1);
        if (parts.length != 2 || parts[0].isEmpty()) return;
        if (parts[1].isEmpty()) {
            game.removeTile(parts[0]);
        } else {
            game.setTile(new Tile(parts[1], parts[0]));
        }
        seat.removeStoredValue(SEAT_KEY);
    }
}
