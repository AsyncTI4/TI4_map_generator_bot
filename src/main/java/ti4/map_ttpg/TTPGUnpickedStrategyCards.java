package ti4.map_ttpg;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"Leadership", "Technology", "Trade", "Imperial", "Diplomacy", "Warfare", "Construction", "Politics"
})
public class TTPGUnpickedStrategyCards {

    @JsonProperty("Leadership")
    private Integer leadership;

    @JsonProperty("Technology")
    private Integer technology;

    @JsonProperty("Trade")
    private Integer trade;

    @JsonProperty("Imperial")
    private Integer imperial;

    @JsonProperty("Diplomacy")
    private Integer diplomacy;

    @JsonProperty("Warfare")
    private Integer warfare;

    @JsonProperty("Construction")
    private Integer construction;

    @JsonProperty("Politics")
    private Integer politics;

    @JsonIgnore
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    @JsonProperty("Leadership")
    public Integer getLeadership() {
        return leadership;
    }

    @JsonProperty("Leadership")
    public void setLeadership(Integer leadership) {
        this.leadership = leadership;
    }

    @JsonProperty("Technology")
    public Integer getTechnology() {
        return technology;
    }

    @JsonProperty("Technology")
    public void setTechnology(Integer technology) {
        this.technology = technology;
    }

    @JsonProperty("Trade")
    public Integer getTrade() {
        return trade;
    }

    @JsonProperty("Trade")
    public void setTrade(Integer trade) {
        this.trade = trade;
    }

    @JsonProperty("Imperial")
    public Integer getImperial() {
        return imperial;
    }

    @JsonProperty("Imperial")
    public void setImperial(Integer imperial) {
        this.imperial = imperial;
    }

    @JsonProperty("Diplomacy")
    public Integer getDiplomacy() {
        return diplomacy;
    }

    @JsonProperty("Diplomacy")
    public void setDiplomacy(Integer diplomacy) {
        this.diplomacy = diplomacy;
    }

    @JsonProperty("Warfare")
    public Integer getWarfare() {
        return warfare;
    }

    @JsonProperty("Warfare")
    public void setWarfare(Integer warfare) {
        this.warfare = warfare;
    }

    @JsonProperty("Construction")
    public Integer getConstruction() {
        return construction;
    }

    @JsonProperty("Construction")
    public void setConstruction(Integer construction) {
        this.construction = construction;
    }

    @JsonProperty("Politics")
    public Integer getPolitics() {
        return politics;
    }

    @JsonProperty("Politics")
    public void setPolitics(Integer politics) {
        this.politics = politics;
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
