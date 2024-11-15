
package ti4.map_ttpg;


import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "agent",
    "commander",
    "hero"
})
public class TTPGLeaders {

    @JsonProperty("agent")
    private String agent = "unlocked";
    @JsonProperty("commander")
    private String commander = "locked";
    @JsonProperty("hero")
    private String hero = "purged";
    @JsonIgnore
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    @JsonProperty("agent")
    public String getAgent() {
        return agent;
    }

    @JsonProperty("agent")
    public void setAgent(String agent) {
        this.agent = agent;
    }

    @JsonProperty("commander")
    public String getCommander() {
        return commander;
    }

    @JsonProperty("commander")
    public void setCommander(String commander) {
        this.commander = commander;
    }

    @JsonProperty("hero")
    public String getHero() {
        return hero;
    }

    @JsonProperty("hero")
    public void setHero(String hero) {
        this.hero = hero;
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
