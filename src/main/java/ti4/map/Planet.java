package ti4.map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.model.AttachmentModel;
import ti4.model.PlanetModel;
import ti4.model.UnitModel;

@JsonTypeName("planet")
public class Planet extends UnitHolder {

    private int resourcesOriginal;
    private int influenceOriginal;
    private int resourcesModifier;
    private int influenceModifier;
    private String originalPlanetType = "";
    private String originalTechSpeciality = "";
    private final List<String> planetType = new ArrayList<>();
    private final List<String> techSpeciality = new ArrayList<>();
    private boolean hasAbility;
    private int spaceCannonHitsOn;
    private int spaceCannonDieCount;
    private String contrastColor = "";

    @JsonCreator
    public Planet(@JsonProperty("name") String name, @JsonProperty("holderCenterPosition") Point holderCenterPosition) {
        super(name, holderCenterPosition);
        PlanetModel planetInfo = Mapper.getPlanet(name);
        if (Optional.ofNullable(planetInfo).isPresent()) {
            if (planetInfo.getPlanetTypes() != null) {
                planetType.addAll(planetInfo.getPlanetTypes().stream().map(x -> x.toString()).toList());
            }
            if (planetInfo.getPlanetType() == null && planetInfo.getPlanetTypes() != null && planetInfo.getPlanetTypes().size() > 0) {
                originalPlanetType = planetInfo.getPlanetTypes().get(0).toString();
            } else if (planetInfo.getPlanetType() != null) {
                originalPlanetType = planetInfo.getPlanetType().toString();
            }
            if (planetInfo.getContrastColor() != null) {
                contrastColor = planetInfo.getContrastColor();
            }
            if (Optional.ofNullable(planetInfo.getTechSpecialties()).orElse(new ArrayList<>()).size() > 0) {
                originalTechSpeciality = planetInfo.getTechSpecialties().get(0).toString();
                techSpeciality.addAll(planetInfo.getTechSpecialties().stream().map(x -> x.toString()).toList());
            }
            if (!StringUtils.isBlank(planetInfo.getLegendaryAbilityName()))
                hasAbility = true;
        }
        resetOriginalPlanetResInf();
    }

    private void resetOriginalPlanetResInf() {
        PlanetModel planetInfo = Mapper.getPlanet(getName());
        if (Optional.ofNullable(planetInfo).isPresent()) {
            resourcesOriginal = planetInfo.getResources();
            influenceOriginal = planetInfo.getInfluence();
        }
    }

    private void addTechSpec(String techSpec) {
        techSpeciality.add(techSpec);
    }

    private void addType(String type) {
        planetType.add(type);
    }

    @JsonIgnore
    @SuppressWarnings("deprecation") // TODO (Jazz): add a better way to handle fake attachies
    public boolean hasAttachment() {
        return tokenList.stream().anyMatch(token -> {
            AttachmentModel attach = Mapper.getAttachmentInfo(token);
            if (attach != null && attach.isFakeAttachment()) return false;

            if (token.contains("sleeper")) return false;
            if (token.contains("dmz_large")) return false;
            if (token.contains("custodiavigilia")) return false;
            return !Helper.isFakeAttachment(token);
        });
    }

    @JsonIgnore
    @SuppressWarnings("deprecation") // TODO (Jazz): add a better way to handle fake attachies
    public List<String> getAttachments() {
        return tokenList.stream().filter(token -> {
            AttachmentModel attach = Mapper.getAttachmentInfo(token);
            if (attach != null && attach.isFakeAttachment()) return false;

            if (token.contains("sleeper")) return false;
            if (token.contains("dmz_large")) return false;
            if (token.contains("custodiavigilia")) return false;
            return !Helper.isFakeAttachment(token);
        }).toList();
    }

    @JsonIgnore
    public boolean hasGroundForces(Player player) {
        return getUnits().keySet().stream()
            .map(UnitKey::asyncID)
            .map(unitID -> player.getPriorityUnitByAsyncID(unitID, this))
            .filter(Objects::nonNull)
            .anyMatch(UnitModel::getIsGroundForce);
    }

    @Override
    public boolean removeToken(String tokenFileName) {
        boolean containedToken = super.removeToken(tokenFileName);
        if (containedToken) {
            removeTokenData(tokenFileName);
        }
        return containedToken;
    }

    @Override
    public boolean addToken(String tokenFileName) {
        boolean newToken = super.addToken(tokenFileName);
        if (newToken) {
            addTokenData(tokenFileName);
        }
        return newToken;
    }

    private void addTokenData(String tokenFileName) {
        if (tokenFileName.equals(Constants.GLEDGE_CORE_PNG)) { // THIS TOKEN HARD SETS THE BASE RES/INF TO 2/0
            resourcesOriginal = 2;
            influenceOriginal = 0;
        }

        AttachmentModel attachment = Mapper.getAttachmentInfo(tokenFileName);
        if (attachment != null) {

            resourcesModifier += attachment.getResourcesModifier();
            influenceModifier += attachment.getInfluenceModifier();
            for (String planetType : attachment.getPlanetTypes()) {
                addType(planetType);
            }
            for (String techSpeciality : attachment.getTechSpeciality()) {
                addTechSpec(techSpeciality);
            }
            if (spaceCannonDieCount == 0 && attachment.getSpaceCannonDieCount() > 0) {
                spaceCannonDieCount = attachment.getSpaceCannonDieCount();
                spaceCannonHitsOn = attachment.getSpaceCannonHitsOn();
            }
        }
    }

    private void removeTokenData(String tokenFileName) {
        if (tokenFileName.equals(Constants.GLEDGE_CORE_PNG)) {
            resetOriginalPlanetResInf();
        }

        AttachmentModel attachment = Mapper.getAttachmentInfo(tokenFileName);
        if (attachment != null) {
            resourcesModifier -= attachment.getResourcesModifier();
            influenceModifier -= attachment.getInfluenceModifier();

            for (String type : attachment.getPlanetTypes()) {
                planetType.remove(type);
            }
            for (String speciality : attachment.getTechSpeciality()) {
                techSpeciality.remove(speciality);
            }
            if (attachment.getSpaceCannonDieCount() > 0) {
                spaceCannonDieCount = 0;
                spaceCannonHitsOn = 0;
            }
        }
    }

    @JsonIgnore
    public int getResources() {
        return resourcesOriginal + resourcesModifier;
    }

    @JsonIgnore
    public int getInfluence() {
        return influenceOriginal + influenceModifier;
    }

    @JsonIgnore
    public int getOptimalResources() {
        if (getResources() > getInfluence()) {
            return getResources();
        } else {
            return 0;
        }
    }

    @JsonIgnore
    public int getOptimalInfluence() {
        if (getInfluence() > getResources()) {
            return getInfluence();
        } else {
            return 0;
        }
    }

    @JsonIgnore
    public int getFlexResourcesOrInfluence() {
        if (getInfluence() == getResources()) {
            return getInfluence();
        } else {
            return 0;
        }
    }

    @JsonIgnore
    public int getSumResourcesInfluence() {
        return getResources() + getInfluence();
    }

    public String getOriginalPlanetType() {
        return originalPlanetType;
    }

    public String getOriginalTechSpeciality() {
        return originalTechSpeciality;
    }

    public List<String> getPlanetType() {
        return planetType;
    }

    @JsonIgnore
    public Set<String> getPlanetTypes() {
        Set<String> types = new HashSet<String>();
        List<String> three = List.of("hazardous", "cultural", "industrial");
        for (String type : planetType) {
            if (three.contains(type)) types.add(type);
        }
        if (three.contains(originalPlanetType)) types.add(originalPlanetType);
        return types;
    }

    public List<String> getTechSpeciality() {
        return techSpeciality;
    }

    @JsonIgnore
    public Set<String> getTechSpecialities() {
        Set<String> specialties = new HashSet<>();
        if (originalTechSpeciality != null && !originalTechSpeciality.isEmpty()) {
            specialties.add(originalTechSpeciality);
        }
        specialties.addAll(techSpeciality);
        return specialties;
    }

    public boolean isHasAbility() {
        return hasAbility;
    }

    @JsonIgnore
    public boolean isLegendary() {
        for (String token : tokenList) {
            AttachmentModel attachment = Mapper.getAttachmentInfo(token);
            if (attachment == null)
                continue;
            if (attachment.isLegendary())
                return true;
        }
        return hasAbility;
    }

    public int getSpaceCannonDieCount() {
        return spaceCannonDieCount;
    }

    public int getSpaceCannonHitsOn() {
        return spaceCannonHitsOn;
    }

    public void setSpaceCannonDieCount(int dieCount) {
        spaceCannonDieCount = dieCount;
    }

    public void setSpaceCannonHitsOn(int hitsOn) {
        spaceCannonHitsOn = hitsOn;
    }

    public String getContrastColor() {
        return contrastColor;
    }
}
