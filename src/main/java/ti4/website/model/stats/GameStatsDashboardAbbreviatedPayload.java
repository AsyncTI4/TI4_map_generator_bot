package ti4.website.model.stats;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.pojo.PlayerProperties;

public class GameStatsDashboardAbbreviatedPayload {

    private final Game game;

    public GameStatsDashboardAbbreviatedPayload(Game game) {
        this.game = game;
    }

    public String getAsyncGameID() {
        return game.getID();
    }

    public String getAsyncFunGameName() {
        return game.getCustomName();
    }

    public List<PlayerStatsDashboardAbbreviatedPayload> getPlayers() {
        return game.getRealAndEliminatedPlayers().stream()
                .map(PlayerStatsDashboardAbbreviatedPayload::new)
                .toList();
    }

    public int getRound() {
        return game.getRound();
    }

    public int getScoreboard() {
        return game.getVp();
    }

    public String getSpeaker() {
        if (game.getSpeaker() == null) return null;
        return game.getSpeaker().getColor();
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
        if (activePlayer == null) return null;
        return activePlayer.getColor();
    }

    public List<String> getWinners() {
        return game.getWinners().stream().map(PlayerProperties::getUserID).toList();
    }

    public boolean hasCompleted() {
        return game.getWinner().isPresent() && game.isHasEnded();
    }

    public boolean isHomebrew() {
        return game.hasHomebrew();
    }

    public boolean isDiscordantStarsMode() {
        return game.isDiscordantStarsMode();
    }

    public boolean isAbsolMode() {
        return game.isAbsolMode();
    }

    public boolean isFrankenGame() {
        return game.isFrankenGame();
    }

    public boolean isAllianceMode() {
        return game.isAllianceMode();
    }

    public boolean isTIGLGame() {
        return game.isCompetitiveTIGLGame();
    }

    @JsonIgnore
    public Game getGame() {
        return game;
    }
}
