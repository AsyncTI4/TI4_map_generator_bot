package ti4.map;

import org.apache.commons.lang3.StringUtils;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.BotLogger;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import ti4.model.AttachmentModel;
import ti4.model.PlanetModel;

@JsonTypeName("planet")
public class Planet extends UnitHolder {

    private int resourcesOriginal;
    private int influenceOriginal;
    private int resourcesModifier;
    private int influenceModifier;
    private String originalPlanetType = "";
    private String originalTechSpeciality = "";
    private final ArrayList<String> planetType = new ArrayList<>();
    private final ArrayList<String> techSpeciality = new ArrayList<>();
    private boolean hasAbility;
    private int spaceCannonHitsOn = 0;
    private int spaceCannonDieCount = 0;

    @JsonCreator
    public Planet(@JsonProperty("name") String name, @JsonProperty("holderCenterPosition") Point holderCenterPosition) {
        super(name, holderCenterPosition);
        PlanetModel planetInfo = Mapper.getPlanet(name);
        if (Optional.ofNullable(planetInfo).isPresent()) {
            originalPlanetType = planetInfo.getPlanetType().toString();
            if(Optional.ofNullable(planetInfo.getTechSpecialties()).orElse(new ArrayList<>()).size() > 0)
                originalTechSpeciality = planetInfo.getTechSpecialties().get(0).toString(); //TODO: Make this support multiple specialties
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
    public boolean hasAttachment() {
        return tokenList.stream().anyMatch(token -> !token.contains("sleeper") && !token.contains("dmz_large") && !Helper.isFakeAttachment(token));
    }

    @JsonIgnore
    public boolean hasGroundForces(Player player) {
        return getUnits().keySet().stream()
                .map(unitID -> player.getPriorityUnitByAsyncID(unitID, this))
                .anyMatch(u -> u.getIsGroundForce());
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

    public List<String> getTechSpeciality() {
        return techSpeciality;
    }

    public boolean isHasAbility() {
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
}
