package ti4.map;

import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.LoggerHandler;

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

    public Planet(String name, Point holderCenterPosition) {
        super(name, holderCenterPosition);
        String planetInfo = Mapper.getPlanet(name);
        if (planetInfo != null) {
            String[] split = planetInfo.split(",");
            String type = split[1];
            if (Constants.CULTURAL.equals(type) ||
                    Constants.INDUSTRIAL.equals(type) ||
                    Constants.HAZARDOUS.equals(type)) {
                originalPlanetType = type;
            }
            if (split.length > 4) {
                String techSpec = split[4];
                if (Constants.PROPULSION.equals(techSpec) ||
                        Constants.WARFARE.equals(techSpec) ||
                        Constants.BIOTIC.equals(techSpec) ||
                        Constants.CYBERNETICS.equals(techSpec)) {
                    originalTechSpeciality = techSpec;
                }
            }
            try {
                resources = Integer.parseInt(split[2]);
                influence = Integer.parseInt(split[3]);
            } catch (Exception e) {
                LoggerHandler.log("Could not parse res/inf of unitHolder " + name, e);
            }
        }
    }

    private void addTechSpec(String techSpec) {
        if (Constants.PROPULSION.equals(techSpec) ||
                Constants.WARFARE.equals(techSpec) ||
                Constants.BIOTIC.equals(techSpec) ||
                Constants.CYBERNETICS.equals(techSpec)) {
            techSpeciality.add(techSpec);
        }
    }

    private void addType(String type) {
        if (Constants.CULTURAL.equals(type) ||
                Constants.INDUSTRIAL.equals(type) ||
                Constants.HAZARDOUS.equals(type)) {
            planetType.add(type);
        }
    }

    @Override
    public void removeToken(String tokenFileName) {
        super.removeToken(tokenFileName);
        addRemoveTokenData(tokenFileName, true);
    }

    @Override
    public void addToken(String tokenFileName) {
        super.addToken(tokenFileName);
        addRemoveTokenData(tokenFileName, false);
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
                    LoggerHandler.log("Could not parse res/inf in token of unitHolder " + getName(), e);
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
}
