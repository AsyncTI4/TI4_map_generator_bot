
package ti4.map_ttpg;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
//https://www.jsonschema2pojo.org/
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "players",
    "setupTimestamp",
    "isPoK",
    "scoreboard",
    "config",
    "hexSummary",
    "laws",
    "mapString",
    "objectives",
    "round",
    "timestamp",
    "turn"
})
public class TTPGMap {

    @JsonProperty("players")
    private List<TTPGPlayer> players = null;
    @JsonProperty("setupTimestamp")
    private Float setupTimestamp;
    @JsonProperty("isPoK")
    private Boolean isPoK;
    @JsonProperty("scoreboard")
    private Integer scoreboard;
    @JsonProperty("config")
    private TTPGConfig config;
    @JsonProperty("hexSummary")
    private String hexSummary;
    @JsonProperty("laws")
    private List<Object> laws = null;
    @JsonProperty("mapString")
    private String mapString;
    @JsonProperty("objectives")
    private TTPGObjectives objectives;
    @JsonProperty("round")
    private Integer round;
    @JsonProperty("timestamp")
    private Float timestamp;
    @JsonProperty("turn")
    private String turn;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("players")
    public List<TTPGPlayer> getPlayers() {
        return players;
    }

    @JsonProperty("players")
    public void setPlayers(List<TTPGPlayer> players) {
        this.players = players;
    }

    @JsonProperty("setupTimestamp")
    public Float getSetupTimestamp() {
        return setupTimestamp;
    }

    @JsonProperty("setupTimestamp")
    public void setSetupTimestamp(Float setupTimestamp) {
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

    @JsonProperty("hexSummary")
    public String getHexSummary() {
        return hexSummary;
    }

    @JsonProperty("hexSummary")
    public void setHexSummary(String hexSummary) {
        this.hexSummary = hexSummary;
    }

    @JsonProperty("laws")
    public List<Object> getLaws() {
        return laws;
    }

    @JsonProperty("laws")
    public void setLaws(List<Object> laws) {
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
    public Float getTimestamp() {
        return timestamp;
    }

    @JsonProperty("timestamp")
    public void setTimestamp(Float timestamp) {
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

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
