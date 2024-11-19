
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
    "codex1",
    "codex2",
    "codex3",
    "baseMagen"
})
public class TTPGConfig {

    @JsonProperty("codex1")
    private Boolean codex1;
    @JsonProperty("codex2")
    private Boolean codex2;
    @JsonProperty("codex3")
    private Boolean codex3;
    @JsonProperty("baseMagen")
    private Boolean baseMagen;
    @JsonIgnore
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    @JsonProperty("codex1")
    public Boolean getCodex1() {
        return codex1;
    }

    @JsonProperty("codex1")
    public void setCodex1(Boolean codex1) {
        this.codex1 = codex1;
    }

    @JsonProperty("codex2")
    public Boolean getCodex2() {
        return codex2;
    }

    @JsonProperty("codex2")
    public void setCodex2(Boolean codex2) {
        this.codex2 = codex2;
    }

    @JsonProperty("codex3")
    public Boolean getCodex3() {
        return codex3;
    }

    @JsonProperty("codex3")
    public void setCodex3(Boolean codex3) {
        this.codex3 = codex3;
    }

    @JsonProperty("baseMagen")
    public Boolean getBaseMagen() {
        return baseMagen;
    }

    @JsonProperty("baseMagen")
    public void setBaseMagen(Boolean baseMagen) {
        this.baseMagen = baseMagen;
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
