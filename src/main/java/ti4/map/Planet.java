package ti4.map;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.model.AttachmentModel;
import ti4.model.PlanetModel;
import ti4.model.PlanetTypeModel;
import ti4.model.TechSpecialtyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.model.UnitModel;

@JsonTypeName("planet")
public class Planet extends UnitHolder {

    private int resourcesOriginal;
    private int influenceOriginal;
    private int resourcesModifier;
    private int influenceModifier;
    private String originalPlanetType;
    private String originalTechSpeciality;

    @Getter
    private final List<String> planetType = new ArrayList<>();

    @Getter
    private final List<String> techSpeciality = new ArrayList<>();

    @Getter
    private boolean hasAbility;

    @Setter
    @Getter
    private int spaceCannonHitsOn;

    @Setter
    @Getter
    private int spaceCannonDieCount;

    @Getter
    private String contrastColor;

    @Getter
    private float radius;

    @JsonCreator
    public Planet(@JsonProperty("name") String name, @JsonProperty("holderCenterPosition") Point holderCenterPosition) {
        super(name, holderCenterPosition);
        PlanetModel planetInfo = Mapper.getPlanet(name);
        if (planetInfo != null) {
            if (planetInfo.getPlanetTypes() != null) {
                planetType.addAll(planetInfo.getPlanetTypes().stream()
                        .map(PlanetTypeModel.PlanetType::toString)
                        .toList());
            }
            if (planetInfo.getPlanetType() == null
                    && planetInfo.getPlanetTypes() != null
                    && !planetInfo.getPlanetTypes().isEmpty()) {
                originalPlanetType = planetInfo.getPlanetTypes().getFirst().toString();
            } else if (planetInfo.getPlanetType() != null) {
                originalPlanetType = planetInfo.getPlanetType().toString();
            }
            if (planetInfo.getContrastColor() != null) {
                contrastColor = planetInfo.getContrastColor();
            }
            List<TechSpecialtyModel.TechSpecialty> techSpecialties = planetInfo.getTechSpecialties();
            if (techSpecialties != null && !techSpecialties.isEmpty()) {
                originalTechSpeciality = techSpecialties.getFirst().toString();
                techSpeciality.addAll(techSpecialties.stream()
                        .map(TechSpecialtyModel.TechSpecialty::toString)
                        .toList());
            }
            if (!isBlank(planetInfo.getLegendaryAbilityName())) hasAbility = true;
            radius = planetInfo.getRadius();
        }
        resetOriginalPlanetResInf();
    }

    private void resetOriginalPlanetResInf() {
        PlanetModel planetInfo = Mapper.getPlanet(getName());
        if (planetInfo != null) {
            resourcesOriginal = planetInfo.getResources();
            influenceOriginal = planetInfo.getInfluence();
        }
    }

    @SuppressWarnings("deprecation") // TODO (Jazz): add a better way to handle fake attachies
    private boolean isRealAttachmentToken(String token) {
        AttachmentModel attach = Mapper.getAttachmentInfo(token);
        if (attach != null && attach.isFakeAttachment()) return false;
        if (token.contains("superweapon")) return false;
        if (token.contains("token_tomb")) return false;
        if (token.contains("facility")) return false;
        if (token.contains("sleeper")) return false;
        if (token.contains("dmz_large")) return false;
        if (token.contains("custodiavigilia")) return false;
        return !Helper.isFakeAttachment(token);
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
        return tokenList.stream().anyMatch(this::isRealAttachmentToken);
    }

    public void updateTriadStats(Player player) {
        if ("triad".equals(getName())) {
            resourcesModifier = 0;
            if (player.getHrf() > 0) resourcesModifier++;
            if (player.getIrf() > 0) resourcesModifier++;
            if (player.getCrf() > 0) resourcesModifier++;
            if (player.getUrf() > 0) resourcesModifier++;
            influenceModifier = resourcesModifier;
        }
    }

    public void updateGroveStats(Player player) {
        if ("grove".equals(getName())) {

            influenceModifier =
                    player.getGame().getPlanetsPlayerIsCoexistingOn(player).size();
            resourcesModifier = 0;
            if (influenceModifier == 0) {
                resourcesModifier = -2;
            }
        }
    }

    @JsonIgnore
    @SuppressWarnings("deprecation") // TODO (Jazz): add a better way to handle fake attachies
    public List<String> getAttachments() {
        return tokenList.stream().filter(this::isRealAttachmentToken).toList();
    }

    public String getRepresentation(Game game) {
        return Helper.getPlanetRepresentation(getName(), game);
    }

    public boolean hasGroundForces(Player player) {
        return getUnits().keySet().stream()
                .filter(player::unitBelongsToPlayer)
                .map(uk -> player.getPriorityUnitByAsyncID(uk.asyncID(), this))
                .filter(Objects::nonNull)
                .anyMatch(UnitModel::getIsGroundForce);
    }

    public boolean hasStructures(Player player) {
        return getUnits().keySet().stream()
                .filter(player::unitBelongsToPlayer)
                .map(uk -> player.getPriorityUnitByAsyncID(uk.asyncID(), this))
                .filter(Objects::nonNull)
                .anyMatch(UnitModel::getIsStructure);
    }

    public boolean hasGroundForces(Game game) {
        return getUnits().keySet().stream()
                .flatMap(uk -> game.getPriorityUnitByUnitKey(uk, this).stream())
                .anyMatch(UnitModel::getIsGroundForce);
    }

    public boolean hasStructures(Game game) {
        return getUnits().keySet().stream()
                .flatMap(uk -> game.getPriorityUnitByUnitKey(uk, this).stream())
                .anyMatch(UnitModel::getIsStructure);
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
        if (Constants.GLEDGE_CORE_PNG.equals(tokenFileName)) { // THIS TOKEN HARD SETS THE BASE RES/INF TO 2/0
            resourcesOriginal = 2;
            influenceOriginal = 0;
        }
        AttachmentModel attachment = Mapper.getAttachmentInfo(tokenFileName);
        if (attachment != null) {
            resourcesModifier += attachment.getResourcesModifier();
            influenceModifier += attachment.getInfluenceModifier();
            int originalRes = resourcesOriginal + resourcesModifier;
            int originalInf = influenceOriginal + influenceModifier;
            if ("designtranspose".equalsIgnoreCase(attachment.getAlias())) {
                resourcesModifier += originalInf - originalRes;
                influenceModifier += originalRes - originalInf;
            }
            if ("designgrand".equalsIgnoreCase(attachment.getAlias())) {
                resourcesModifier += originalRes;
                influenceModifier += originalInf;
            }
            if ("designcombine".equalsIgnoreCase(attachment.getAlias())) {
                resourcesModifier += originalInf;
                influenceModifier += originalRes;
            }
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
        if (Constants.GLEDGE_CORE_PNG.equals(tokenFileName)) {
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
    public int getHigherofInfluenceOrResource() {
        return Math.max(getInfluence(), getResources());
    }

    @JsonIgnore
    public int getMaxResInf() {
        return Math.max(getResources(), getInfluence());
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

    @Nullable
    public String getOriginalPlanetType() {
        return originalPlanetType;
    }

    @Nullable
    public String getOriginalTechSpeciality() {
        return originalTechSpeciality;
    }

    @JsonIgnore
    public PlanetModel getPlanetModel() {
        return Mapper.getPlanet(getName());
    }

    @JsonIgnore
    public boolean isSpaceStation() {
        return getPlanetModel().isSpaceStation();
    }

    @JsonIgnore
    public boolean isFake() {
        return getPlanetModel().isFake();
    }

    @JsonIgnore
    public Set<String> getPlanetTypes() {
        Set<String> types = new HashSet<>();
        List<String> three = List.of("hazardous", "cultural", "industrial");
        for (String type : planetType) {
            if (three.contains(type)) types.add(type);
        }
        if (isNotBlank(originalPlanetType) && three.contains(originalPlanetType)) types.add(originalPlanetType);
        return types;
    }

    @JsonIgnore
    public boolean hasTechSpecialty(TechnologyType type) {
        return techSpeciality.contains(type.toString().toLowerCase())
                || techSpeciality.contains(type.toString().toUpperCase());
    }

    @JsonIgnore
    public List<String> getTechSpecialities() {

        List<String> specialties = new ArrayList<>(techSpeciality);
        specialties.removeAll(Collections.singleton(null));
        specialties.removeAll(Collections.singleton(""));
        if (isNotBlank(originalTechSpeciality) && specialties.isEmpty()) {
            specialties.add(originalTechSpeciality);
        }
        return specialties;
    }

    @JsonIgnore
    public boolean isLegendary() {
        PlanetModel model = getPlanetModel();
        if ("ghoti".equalsIgnoreCase(getName())) {
            return false;
        }
        if (model != null && model.isLegendary()) return true;

        for (String token : tokenList) {
            AttachmentModel attachment = Mapper.getAttachmentInfo(token);
            if (attachment == null) continue;
            if (attachment.isLegendary()) return true;
        }
        return hasAbility;
    }

    @JsonIgnore
    public boolean isHomePlanet() {
        return getPlanetModel().getFactionHomeworld() != null;
    }

    @JsonIgnore
    public boolean isHomePlanet(Game game) {
        Tile t = game.getTileFromPlanet(getName());
        if (t != null) return t.isHomeSystem(game);
        return false;
    }
}
