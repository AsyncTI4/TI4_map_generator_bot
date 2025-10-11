package ti4.website.model.stats;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import ti4.map.Game;
import ti4.map.Player;

public class AbbreviatedGameDashboardPayload {

    private final Game game;

    public AbbreviatedGameDashboardPayload(Game game) {
        this.game = game;
    }

    @JsonIgnore
    public Game getGame() {
        return game;
    }

    public String getAsyncGameID() {
        return game.getID();
    }

    public String getAsyncFunGameName() {
        return game.getCustomName();
    }

    public String getPlatform() {
        return "asyncti4";
    }

    public boolean isPoK() {
        return !game.isBaseGameMode();
    }

    public String getPhaseOfGame() {
        return game.getPhaseOfGame();
    }

    public int getRound() {
        return game.getRound();
    }

    public int getScoreboard() {
        return game.getVp();
    }

    public boolean isHasEnded() {
        return game.isHasEnded();
    }

    public long getTimestamp() {
        try {
            return Instant.ofEpochMilli(game.getLastModifiedDate()).getEpochSecond();
        } catch (DateTimeException e) {
            return Instant.now().getEpochSecond();
        }
    }

    public Long getEndedTimestamp() {
        if (!game.isHasEnded()) {
            return null;
        }
        return Instant.ofEpochMilli(game.getEndedDate()).getEpochSecond();
    }

    public String getTurn() {
        Player activePlayer = game.getActivePlayer();
        if (activePlayer == null) {
            return null;
        }
        return activePlayer.getColor();
    }

    public String getSpeaker() {
        Player speaker = game.getSpeaker();
        if (speaker == null) {
            return null;
        }
        return speaker.getColor();
    }

    public List<AbbreviatedPlayerDashboardPayload> getPlayers() {
        return game.getRealAndEliminatedPlayers().stream()
                .map(AbbreviatedPlayerDashboardPayload::new)
                .toList();
    }
}
