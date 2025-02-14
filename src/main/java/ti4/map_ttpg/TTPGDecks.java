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
@JsonPropertyOrder({
    "card.objective.public_1",
    "card.objective.public_2",
    "card.action",
    "card.agenda",
    "card.objective.secret",
    "card.planet",
    "card.exploration.cultural",
    "card.exploration.hazardous",
    "card.exploration.industrial",
    "card.exploration.frontier",
    "card.relic",
    "card.legendary_planet"
})
public class TTPGDecks {

    @JsonProperty("card.objective.public_1")
    private TTPGCardObjectivePublic1 cardObjectivePublic1;

    @JsonProperty("card.objective.public_2")
    private TTPGCardObjectivePublic2 cardObjectivePublic2;

    @JsonProperty("card.action")
    private TTPGCardAction cardAction;

    @JsonProperty("card.agenda")
    private TTPGCardAgenda cardAgenda;

    @JsonProperty("card.objective.secret")
    private TTPGCardObjectiveSecret cardObjectiveSecret;

    @JsonProperty("card.planet")
    private TTPGCardPlanet cardPlanet;

    @JsonProperty("card.exploration.cultural")
    private TTPGCardExplorationCultural cardExplorationCultural;

    @JsonProperty("card.exploration.hazardous")
    private TTPGCardExplorationHazardous cardExplorationHazardous;

    @JsonProperty("card.exploration.industrial")
    private TTPGCardExplorationIndustrial cardExplorationIndustrial;

    @JsonProperty("card.exploration.frontier")
    private TTPGCardExplorationFrontier cardExplorationFrontier;

    @JsonProperty("card.relic")
    private TTPGCardRelic cardRelic;

    @JsonProperty("card.legendary_planet")
    private TTPGCardLegendaryPlanet cardLegendaryPlanet;

    @JsonIgnore
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    @JsonProperty("card.objective.public_1")
    public TTPGCardObjectivePublic1 getCardObjectivePublic1() {
        return cardObjectivePublic1;
    }

    @JsonProperty("card.objective.public_1")
    public void setCardObjectivePublic1(TTPGCardObjectivePublic1 cardObjectivePublic1) {
        this.cardObjectivePublic1 = cardObjectivePublic1;
    }

    @JsonProperty("card.objective.public_2")
    public TTPGCardObjectivePublic2 getCardObjectivePublic2() {
        return cardObjectivePublic2;
    }

    @JsonProperty("card.objective.public_2")
    public void setCardObjectivePublic2(TTPGCardObjectivePublic2 cardObjectivePublic2) {
        this.cardObjectivePublic2 = cardObjectivePublic2;
    }

    @JsonProperty("card.action")
    public TTPGCardAction getCardAction() {
        return cardAction;
    }

    @JsonProperty("card.action")
    public void setCardAction(TTPGCardAction cardAction) {
        this.cardAction = cardAction;
    }

    @JsonProperty("card.agenda")
    public TTPGCardAgenda getCardAgenda() {
        return cardAgenda;
    }

    @JsonProperty("card.agenda")
    public void setCardAgenda(TTPGCardAgenda cardAgenda) {
        this.cardAgenda = cardAgenda;
    }

    @JsonProperty("card.objective.secret")
    public TTPGCardObjectiveSecret getCardObjectiveSecret() {
        return cardObjectiveSecret;
    }

    @JsonProperty("card.objective.secret")
    public void setCardObjectiveSecret(TTPGCardObjectiveSecret cardObjectiveSecret) {
        this.cardObjectiveSecret = cardObjectiveSecret;
    }

    @JsonProperty("card.planet")
    public TTPGCardPlanet getCardPlanet() {
        return cardPlanet;
    }

    @JsonProperty("card.planet")
    public void setCardPlanet(TTPGCardPlanet cardPlanet) {
        this.cardPlanet = cardPlanet;
    }

    @JsonProperty("card.exploration.cultural")
    public TTPGCardExplorationCultural getCardExplorationCultural() {
        return cardExplorationCultural;
    }

    @JsonProperty("card.exploration.cultural")
    public void setCardExplorationCultural(TTPGCardExplorationCultural cardExplorationCultural) {
        this.cardExplorationCultural = cardExplorationCultural;
    }

    @JsonProperty("card.exploration.hazardous")
    public TTPGCardExplorationHazardous getCardExplorationHazardous() {
        return cardExplorationHazardous;
    }

    @JsonProperty("card.exploration.hazardous")
    public void setCardExplorationHazardous(TTPGCardExplorationHazardous cardExplorationHazardous) {
        this.cardExplorationHazardous = cardExplorationHazardous;
    }

    @JsonProperty("card.exploration.industrial")
    public TTPGCardExplorationIndustrial getCardExplorationIndustrial() {
        return cardExplorationIndustrial;
    }

    @JsonProperty("card.exploration.industrial")
    public void setCardExplorationIndustrial(TTPGCardExplorationIndustrial cardExplorationIndustrial) {
        this.cardExplorationIndustrial = cardExplorationIndustrial;
    }

    @JsonProperty("card.exploration.frontier")
    public TTPGCardExplorationFrontier getCardExplorationFrontier() {
        return cardExplorationFrontier;
    }

    @JsonProperty("card.exploration.frontier")
    public void setCardExplorationFrontier(TTPGCardExplorationFrontier cardExplorationFrontier) {
        this.cardExplorationFrontier = cardExplorationFrontier;
    }

    @JsonProperty("card.relic")
    public TTPGCardRelic getCardRelic() {
        return cardRelic;
    }

    @JsonProperty("card.relic")
    public void setCardRelic(TTPGCardRelic cardRelic) {
        this.cardRelic = cardRelic;
    }

    @JsonProperty("card.legendary_planet")
    public TTPGCardLegendaryPlanet getCardLegendaryPlanet() {
        return cardLegendaryPlanet;
    }

    @JsonProperty("card.legendary_planet")
    public void setCardLegendaryPlanet(TTPGCardLegendaryPlanet cardLegendaryPlanet) {
        this.cardLegendaryPlanet = cardLegendaryPlanet;
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
