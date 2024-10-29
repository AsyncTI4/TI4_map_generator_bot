package ti4.map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.utils.StringUtils;
import ti4.generator.Mapper;
import ti4.message.BotLogger;
import ti4.model.PublicObjectiveModel;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameStatsDashboardPayload {

    private final Game game;

    public GameStatsDashboardPayload(Game game) {
        this.game = game;
    }

    @JsonIgnore
    public String getJson() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            BotLogger.log("Could not get GameStatsDashboardPayload JSON for Game ", e);
            return null;
        }
    }

    public String getAsyncGameID() {
        return game.getID();
    }

    public String getAsyncFunGameName() {
        return game.getCustomName();
    }

    public String getActiveSystem() { //TODO: needs to be object returning planets list and tileID
        /*
         * "activeSystem": {
         * "planets": [
         * "Quann"
         * ],
         * "tile": 25
         * }
         */
        String activeSystemPosition = game.getActiveSystem();
        if (StringUtils.isEmpty(activeSystemPosition)) return null;

        Tile tile = game.getTileByPosition(activeSystemPosition);
        if (tile == null) return null;

        return tile.getTileID();
    }

    public Object getConfig() {
        /*
         * "config": {
         * "baseMagen": false,
         * "codex1": true,
         * "codex2": true,
         * "codex3": true,
         * "codex4": true
         * }
         */
        return null;
    }

    public String getHexSummary() {
        // 18+0+0*b;Bio,71+0+2Rct;Ro;Ri,36+1+1Kcf;Km*I;Ki,76+1-1;;;,72+0-2; ......
        // See ConvertTTPGtoAsync.ConvertTTPGHexToAsyncTile() and reverse it!
        return null;
    }

    public Map<Timestamp, GameStatsDashboardPayload> getHistory() {
        return game.getHistoricalGameStatsDashboardPayloads();
    }

    public boolean isPoK() {
        return game.isProphecyOfKings();
    }

    public List<String> getLaws() {
        return Collections.emptyList(); // List.emptyList(); //list of laws in play by proper name
    }

    public String getMapString() {
        return game.getMapString();
    }

    public Map<String, List<String>> getObjectives() {
        Map<String, List<String>> objectives = new HashMap<>();

        objectives.put("Custom", new ArrayList<>(game.getCustomPublicVP().keySet()));

        for (String customVPName : game.getCustomPublicVP().keySet()) {

        }

        for (Player player : game.getRealAndEliminatedPlayers()) {
            objectives.put("Other", player.getPromissoryNotesOwned().stream()
                .map(Mapper::getPromissoryNote)
                .filter(pn -> "Support for the Throne".equalsIgnoreCase(pn.getName()))
                .map(pn -> "Support for the Throne" + player.getColor())
                .toList());
        }

        objectives.put("Public Objectives I",
            game.getPublicObjectives1().stream()
                .map(Mapper::getPublicObjective)
                .map(PublicObjectiveModel::getName).toList());

        objectives.put("Public Objectives II",
            game.getPublicObjectives2().stream()
                .map(Mapper::getPublicObjective)
                .map(PublicObjectiveModel::getName).toList());
        /*
         * "objectives": {
         * "Agenda": [],
         * "Other": [
         * "Support for the Throne (Blue)",
         * "Support for the Throne (Red)",
         * "Support for the Throne (Purple)",
         * "Support for the Throne (Yellow)",
         * "Support for the Throne (Green)"
         * ],
         * "Public Objectives I": [
         * "Explore Deep Space",
         * "Diversify Research",
         * "Amass Wealth",
         * "Raise a Fleet",
         * "Develop Weaponry"
         * ],
         * "Public Objectives II": [],
         * "Relics": [],
         * "Secret Objectives": [
         * "Adapt New Strategies",
         * "Become a Martyr",
         * "Hoard Raw Materials",
         * "Master the Laws of Physics"
         * ]
         * }
         */
        return null;
    }

    public String getPlatform() {
        return "asyncti4";
    }

    public Object getPlayerTimer() {
        /*
         * "playerTimer": {
         * "blue": {
         * "1": 513,
         * "2": 372,
         * "3": 515,
         * "4": 1507,
         * "5": 0,
         * "6": 0,
         * "7": 0
         * },
         * "green": {
         * "1": 504,
         * "2": 214,
         * "3": 485,
         * "4": 643,
         * "5": 0,
         * "6": 0,
         * "7": 0
         * },
         * "pink": {
         * "1": 324,
         * "2": 854,
         * "3": 1674,
         * "4": 662,
         * "5": 0,
         * "6": 0,
         * "7": 0
         * },
         * "purple": {
         * "1": 874,
         * "2": 297,
         * "3": 679,
         * "4": 1587,
         * "5": 0,
         * "6": 0,
         * "7": 0
         * },
         * "red": {
         * "1": 864,
         * "2": 1503,
         * "3": 1213,
         * "4": 1442,
         * "5": 0,
         * "6": 0,
         * "7": 0
         * },
         * "yellow": {
         * "1": 502,
         * "2": 1109,
         * "3": 602,
         * "4": 567,
         * "5": 0,
         * "6": 0,
         * "7": 0
         * }
         * }
         */
        return null;
    }

    public List<PlayerStatsDashboardPayload> getPlayers() {
        return game.getRealAndEliminatedPlayers().stream()
            .map(player -> new PlayerStatsDashboardPayload(player, game))
            .toList();
    }

    public int getRound() {
        return game.getRound();
    }

    public int getScoreboard() {
        return game.getVp();
    }

    public Timestamp getSetupTimestamp() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd");
        Date parsedDate;
        try {
            parsedDate = dateFormat.parse(game.getCreationDate());
            return new Timestamp(parsedDate.getTime());
        } catch (ParseException e) {
            BotLogger.log("Can't parse CreationDate: " + game.getCreationDate(), e);
            return null;
        }
    }

    public String getSpeaker() {
        if (game.getSpeaker() == null) return null;
        return game.getSpeaker().getColor();
    }

    public Object getTimer() {
        /*
         * "timer": {
         * "anchorSeconds": 26745,
         * "anchorTimestamp": 1726447240,
         * "countDown": -1,
         * "direction": 1,
         * "seconds": 26748
         * }
         */
        return null;
    }

    public Timestamp getTimestamp() {
        return Timestamp.from(Instant.now());
    }

    public String getTurn() {
        Player activePlayer = game.getActivePlayer();
        if (activePlayer == null) return null;
        return activePlayer.getColor();
    }

    public List<Object> getUnpickedStrategyCards() {
        /*
         * "unpickedStrategyCards": {
         * "Construction": 2,
         * "Imperial": 0,
         * "Politics": 1
         * }
         */
        return Collections.emptyList();
    }

}
