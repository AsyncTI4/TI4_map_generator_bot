package ti4.map;

import ti4.generator.Mapper;
import ti4.helpers.LoggerHandler;
import ti4.message.BotLogger;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Planet extends UnitHolder {

    private int resources = 0;
    private int influence = 0;
    private String originalPlanetType = "";
    private String originalTechSpeciality = "";
    private ArrayList<String> planetType = new ArrayList<>();
    private ArrayList<String> techSpeciality = new ArrayList<>();
    private boolean hasAbility = false;

    public Planet(String name, Point holderCenterPosition) {
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
            try {
                resources = Integer.parseInt(split[2]);
                influence = Integer.parseInt(split[3]);
            } catch (Exception e) {
                BotLogger.log("Could not parse res/inf of unitHolder " + name);
            }
        }
    }

    private void addTechSpec(String techSpec) {
        techSpeciality.add(techSpec);
    }

    private void addType(String type) {
        planetType.add(type);
    }

    public boolean hasAttachment() {
        return tokenList.stream().anyMatch(token -> !token.contains("sleeper") && !token.contains("dmz_large"));
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
        List<String> attachmentInfoAll = Mapper.getAttachmentInfoAll();
        for (String id : attachmentInfoAll) {
            String attachmentID = Mapper.getAttachmentID(id);
            if (tokenFileName.equals(attachmentID)) {
                String attachmentInfo = Mapper.getAttachmentInfo(id);
                String[] split = attachmentInfo.split(";");
                try {
                    if (removeTokenData) {
                        resources -= Integer.parseInt(split[0]);
                        influence -= Integer.parseInt(split[1]);
                    } else {
                        resources += Integer.parseInt(split[0]);
                        influence += Integer.parseInt(split[1]);
                    }
                } catch (Exception e) {
                    BotLogger.log("Could not parse res/inf in token of unitHolder " + getName());
                }

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
            }
        }
    }

    public int getResources() {
        return resources;
    }

    public int getInfluence() {
        return influence;
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
