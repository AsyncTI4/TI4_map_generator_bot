package ti4.map_ttpg;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
// https://www.jsonschema2pojo.org/
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "players",
    "setupTimestamp",
    "isPoK",
    "scoreboard",
    "config",
    "decks",
    "hexSummary",
    "laws",
    "mapString",
    "objectives",
    "round",
    "timestamp",
    "turn",
    "unpickedStrategyCards"
})
public class TTPGMap {

    @JsonProperty("players")
    private List<TTPGPlayer> players;

    @JsonProperty("setupTimestamp")
    private Double setupTimestamp;

    @JsonProperty("isPoK")
    private Boolean isPoK;

    @JsonProperty("scoreboard")
    private Integer scoreboard;

    @JsonProperty("config")
    private TTPGConfig config;

    @JsonProperty("decks")
    private TTPGDecks decks;

    @JsonProperty("hexSummary")
    private String hexSummary;

    @JsonProperty("laws")
    private List<String> laws;

    @JsonProperty("mapString")
    private String mapString;

    @JsonProperty("objectives")
    private TTPGObjectives objectives;

    @JsonProperty("round")
    private Integer round;

    @JsonProperty("timestamp")
    private Double timestamp;

    @JsonProperty("turn")
    private String turn;

    @JsonProperty("unpickedStrategyCards")
    private TTPGUnpickedStrategyCards unpickedStrategyCards;

    @JsonIgnore
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    @JsonProperty("players")
    public List<TTPGPlayer> getPlayers() {
        return players;
    }

    @JsonProperty("players")
    public void setPlayers(List<TTPGPlayer> players) {
        this.players = players;
    }

    @JsonProperty("setupTimestamp")
    public Double getSetupTimestamp() {
        return setupTimestamp;
    }

    @JsonProperty("setupTimestamp")
    public void setSetupTimestamp(Double setupTimestamp) {
        this.setupTimestamp = setupTimestamp;
    }

    @JsonProperty("isPoK")
    public Boolean getIsPoK() {
        return isPoK;
    }

    @JsonProperty("isPoK")
    public void setIsPoK(Boolean isPoK) {
        this.isPoK = isPoK;
    }

    @JsonProperty("scoreboard")
    public Integer getScoreboard() {
        return scoreboard;
    }

    @JsonProperty("scoreboard")
    public void setScoreboard(Integer scoreboard) {
        this.scoreboard = scoreboard;
    }

    @JsonProperty("config")
    public TTPGConfig getConfig() {
        return config;
    }

    @JsonProperty("config")
    public void setConfig(TTPGConfig config) {
        this.config = config;
    }

    @JsonProperty("decks")
    public TTPGDecks getDecks() {
        return decks;
    }

    @JsonProperty("decks")
    public void setDecks(TTPGDecks decks) {
        this.decks = decks;
    }

    @JsonProperty("hexSummary")
    public String getHexSummary() {
        return hexSummary;
    }

    @JsonProperty("hexSummary")
    public void setHexSummary(String hexSummary) {
        this.hexSummary = hexSummary;
    }

    @JsonProperty("laws")
    public List<String> getLaws() {
        return laws;
    }

    @JsonProperty("laws")
    public void setLaws(List<String> laws) {
        this.laws = laws;
    }

    @JsonProperty("mapString")
    public String getMapString() {
        return mapString;
    }

    @JsonProperty("mapString")
    public void setMapString(String mapString) {
        this.mapString = mapString;
    }

    @JsonProperty("objectives")
    public TTPGObjectives getObjectives() {
        return objectives;
    }

    @JsonProperty("objectives")
    public void setObjectives(TTPGObjectives objectives) {
        this.objectives = objectives;
    }

    @JsonProperty("round")
    public Integer getRound() {
        return round;
    }

    @JsonProperty("round")
    public void setRound(Integer round) {
        this.round = round;
    }

    @JsonProperty("timestamp")
    public Double getTimestamp() {
        return timestamp;
    }

    @JsonProperty("timestamp")
    public void setTimestamp(Double timestamp) {
        this.timestamp = timestamp;
    }

    @JsonProperty("turn")
    public String getTurn() {
        return turn;
    }

    @JsonProperty("turn")
    public void setTurn(String turn) {
        this.turn = turn;
    }

    @JsonProperty("unpickedStrategyCards")
    public TTPGUnpickedStrategyCards getUnpickedStrategyCards() {
        return unpickedStrategyCards;
    }

    @JsonProperty("unpickedStrategyCards")
    public void setUnpickedStrategyCards(TTPGUnpickedStrategyCards unpickedStrategyCards) {
        this.unpickedStrategyCards = unpickedStrategyCards;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        additionalProperties.put(name, value);
    }
}
