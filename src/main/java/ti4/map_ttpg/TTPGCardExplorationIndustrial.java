package ti4.map_ttpg;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "deck",
    "discard"
})
public class TTPGCardExplorationIndustrial {

    @JsonProperty("deck")
    private List<String> deck;
    @JsonProperty("discard")
    private List<String> discard;
    @JsonIgnore
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    @JsonProperty("deck")
    public List<String> getDeck() {
        return deck;
    }

    @JsonProperty("deck")
    public void setDeck(List<String> deck) {
        this.deck = deck;
    }

    @JsonProperty("discard")
    public List<String> getDiscard() {
        return discard;
    }

    @JsonProperty("discard")
    public void setDiscard(List<String> discard) {
        this.discard = discard;
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
