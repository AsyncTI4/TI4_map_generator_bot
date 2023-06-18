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
import ti4.model.PlanetModel;

@JsonTypeName("planet")
public class Planet extends UnitHolder {

    private int resourcesOriginal = 0;
    private int influenceOriginal = 0;
    private int resourcesModifier = 0;
    private int influenceModifier = 0;
    private String originalPlanetType = "";
    private String originalTechSpeciality = "";
    private ArrayList<String> planetType = new ArrayList<>();
    private ArrayList<String> techSpeciality = new ArrayList<>();
    private boolean hasAbility = false;

    @JsonCreator
    public Planet(@JsonProperty("name") String name, @JsonProperty("holderCenterPosition") Point holderCenterPosition) {
        super(name, holderCenterPosition);
        PlanetModel planetInfo = Mapper.getPlanet(name);
        if (Optional.ofNullable(planetInfo).isPresent()) {
            originalPlanetType = planetInfo.getPlanetType().toString();
            if(Optional.ofNullable(planetInfo.getTechSpecialties()).orElse(new ArrayList<>()).size() > 0)
                originalTechSpeciality = planetInfo.getTechSpecialties().get(0).toString(); //TODO: Make this support multiple specialties
            if (StringUtils.isBlank(planetInfo.getLegendaryAbilityName()))
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
    public boolean hasGroundForces() {
        return getUnits().keySet().stream().anyMatch(u -> u.contains("mf") || u.contains("gf"));
    }

    @Override
    public boolean removeToken(String tokenFileName) {
        boolean containedToken = super.removeToken(tokenFileName);
        if (containedToken) {
            addRemoveTokenData(tokenFileName, true);
        }
        return containedToken;
    }

    @Override
    public boolean addToken(String tokenFileName) {
        boolean newToken = super.addToken(tokenFileName);
        if (newToken) {
            addRemoveTokenData(tokenFileName, false);
        }
        return newToken;
    }

    private void addRemoveTokenData(String tokenFileName, boolean removeTokenData) {
        if (tokenFileName.equals(Constants.GLEDGE_CORE_PNG)) { //THIS TOKEN HARD SETS THE BASE RES/INF TO 2/0
            if (removeTokenData) {
                resetOriginalPlanetResInf();
            } else {
                resourcesOriginal = 2;
                influenceOriginal = 0;
            }
        }

        List<String> attachmentInfoAll = Mapper.getAttachmentInfoAll();
        for (String id : attachmentInfoAll) {
            String attachmentID = Mapper.getAttachmentID(id);
            if (tokenFileName.equals(attachmentID)) {
                String attachmentInfo = Mapper.getAttachmentInfo(id);
                String[] split = attachmentInfo.split(";");

                try {
                    if (removeTokenData) {
                        resourcesModifier -= Integer.parseInt(split[0]);
                        influenceModifier -= Integer.parseInt(split[1]);
                    } else {
                        resourcesModifier += Integer.parseInt(split[0]);
                        influenceModifier += Integer.parseInt(split[1]);
                    }

                } catch (Exception e) {
                    BotLogger.log("Could not parse res/inf in token of unitHolder " + getName(), e);
                }

                //ADD TYPES
                if (split.length > 2) {
                    String additional = split[2];
                    if (additional.contains(",")) {
                        String[] subSplit = additional.split(",");
                        for (String type : subSplit) {
                            if (removeTokenData) {
                                planetType.remove(type);
                            } else {
                                addType(type);
                            }
                        }
                    } else {
                        if (removeTokenData) {
                            techSpeciality.remove(additional);
                            planetType.remove(additional);
                        } else {
                            addTechSpec(additional);
                            addType(additional);
                        }
                    }
                }
                break;
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

    public ArrayList<String> getPlanetType() {
        return planetType;
    }

    public ArrayList<String> getTechSpeciality() {
        return techSpeciality;
    }

    public boolean isHasAbility() {
        return hasAbility;
    }
}
