package ti4.map;

import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.BotLogger;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

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
        String planetInfo = Mapper.getPlanet(name);
        if (planetInfo != null) {
            String[] split = planetInfo.split(",");
            originalPlanetType = split[1];
            if (split.length > 4) {
                originalTechSpeciality = split[4];
            }
            if (split.length > 5) {
                hasAbility = true;
            }
        }
        resetOriginalPlanetResInf();
    }

    private void resetOriginalPlanetResInf() {
        String planetInfo = Mapper.getPlanet(getName());
        if (planetInfo != null) {
            String[] split = planetInfo.split(",");
            try {
                resourcesOriginal = Integer.parseInt(split[2]);
                influenceOriginal = Integer.parseInt(split[3]);
            } catch (Exception e) {
                BotLogger.log("Could not reset the original res/inf of unitHolder " + getName(), e);
            }
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
