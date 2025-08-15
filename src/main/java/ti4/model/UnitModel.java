package ti4.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.helpers.AliasHandler;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.Source.ComponentSource;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;

@Data
public class UnitModel implements ModelInterface, EmbeddableModel {
    private String id;
    private String baseType;
    private String asyncId;
    private String name;
    private String subtitle;
    private String upgradesFromUnitId;
    private String upgradesToUnitId;
    private String requiredTechId;
    private String faction;
    private List<String> eligiblePlanetTypes;
    private int moveValue;
    private int productionValue;
    private String basicProduction;
    private int capacityValue;
    private int fleetSupplyBonus;
    private int capacityUsed;
    private float cost;
    private int combatHitsOn;
    private int combatDieCount;
    private int afbHitsOn;
    private int afbDieCount;
    private int bombardHitsOn;
    private int bombardDieCount;
    private int spaceCannonHitsOn;
    private int spaceCannonDieCount;
    private Boolean deepSpaceCannon;
    private Boolean planetaryShield;
    private Boolean sustainDamage;
    private Boolean disablesPlanetaryShield;
    private Boolean canBeDirectHit;
    private Boolean isStructure;
    private Boolean isMonument;
    private Boolean isGroundForce;
    private Boolean isShip;
    private Boolean isSpaceOnly;
    private Boolean isPlanetOnly;
    private String ability;
    private String unlock; // for Flagshipping homebrew
    private String homebrewReplacesID;
    private String imageURL;
    private ComponentSource source;
    private List<String> searchTags = new ArrayList<>();

    // Source: units.json - source of json:
    // https://docs.google.com/spreadsheets/d/1nbHylJyn4VURCRKX8ePmOrLa6dAsc504ww0BPZXIRxU/edit?usp=sharing
    // Google Sheet to JSON script:
    // https://gist.githubusercontent.com/pamelafox/1878143/raw/6c23f71231ce1fa09be2d515f317ffe70e4b19aa/exportjson.js?utm_source=thenewstack&utm_medium=website&utm_content=inline-mention&utm_campaign=platform
    // From: https://thenewstack.io/how-to-convert-google-spreadsheet-to-json-formatted-text/

    public boolean isValid() {
        return id != null
                && !id.isEmpty()
                && baseType != null
                && name != null
                && asyncId != null
                && source != null
                && (getFaction().isEmpty()
                        || Mapper.isValidFaction(getFaction().orElse("").toLowerCase()))
                && new HashSet<>(List.of(
                                "CULTURAL",
                                "HAZARDOUS",
                                "INDUSTRIAL",
                                "TECH_SPECIALTY",
                                "LEGENDARY",
                                "MECATOL_REX",
                                "EMPTY_NONANOMALY"))
                        .containsAll(getEligiblePlanetTypes());
    }

    public String getAlias() {
        return id;
    }

    public String getImageFileSuffix() {
        return "_" + asyncId + ".png";
    }

    public String getColorAsyncID(String color) {
        color = AliasHandler.resolveColor(color);
        color = Mapper.getColorID(color);
        return color + getImageFileSuffix();
    }

    public TI4Emoji getFactionEmoji() {
        return FactionEmojis.getFactionIcon(getFaction().orElse(""));
    }

    public UnitType getUnitType() {
        return Units.findUnitType(asyncId);
    }

    public TI4Emoji getUnitEmoji() {
        if (getUnitType() == null) return TI4Emoji.getRandomGoodDog();
        return getUnitType().getUnitTypeEmoji();
    }

    public String getUnitRepresentation() {
        String factionEmoji = getFaction().isEmpty() ? "" : getFactionEmoji().toString();
        TI4Emoji unitEmoji = getUnitEmoji();

        String unitString = unitEmoji + " " + name + factionEmoji;
        if (getAbility().isPresent()) {
            unitString += ": " + getAbility().get();
        }
        return unitString;
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeAliases) {
        String factionEmoji = getFaction().isEmpty() ? "" : getFactionEmoji().toString();
        TI4Emoji unitEmoji = getUnitEmoji();

        EmbedBuilder eb = new EmbedBuilder();

        String name = this.name;
        eb.setTitle(factionEmoji + unitEmoji + " __" + name + "__ " + getSourceEmoji(), null);
        if (getSubtitle().isPresent()) eb.setDescription("-# " + getSubtitle().get() + " " + getEligiblePlanetEmojis());

        if (!getValuesText().isEmpty()) eb.addField("Values:", getValuesText(), true);
        if (!getDiceText().isEmpty()) eb.addField("Dice Rolls:", getDiceText(), true);
        if (!getOtherText().isEmpty()) eb.addField("Traits:", getOtherText(), true);
        if (getAbility().isPresent()) eb.addField("Ability:", getAbility().get(), false);
        if (getUnlock().isPresent()) eb.addField("Unlock:", getUnlock().get(), false);
        // if (getImageURL() != null) eb.setThumbnail(getImageURL());

        if (includeAliases) eb.setFooter("UnitID: " + id + "\nAliases: " + getAsyncIDAliases() + "\nSource: " + source);

        return eb.build();
    }

    public String getNameRepresentation() {
        String factionEmoji = getFaction().isEmpty() ? "" : getFactionEmoji().toString();
        TI4Emoji unitEmoji = getUnitEmoji();
        String name = this.name == null ? "" : this.name;
        return factionEmoji + unitEmoji + " " + name + " " + getSourceEmoji();
    }

    public String getSourceEmoji() {
        return source.emoji();
    }

    public int getCombatDieCountForAbility(CombatRollType rollType) {
        return switch (rollType) {
            case combatround -> getCombatDieCount();
            case AFB -> getAfbDieCount();
            case bombardment -> getBombardDieCount();
            case SpaceCannonOffence, SpaceCannonDefence -> getSpaceCannonDieCount();
        };
    }

    public int getCombatDieCountForAbility(CombatRollType rollType, Player player, Game game) {
        return switch (rollType) {
            case combatround -> getCombatDieCount();
            case AFB -> getAfbDieCount(player, game);
            case bombardment -> getBombardDieCount(player, game);
            case SpaceCannonOffence -> getSpaceCannonDieCount(player, game);
            case SpaceCannonDefence -> getSpaceCannonDieCount();
        };
    }

    public int getAfbDieCount(Player player, Game game) {
        if (capacityValue > 0
                && player.getFaction().equalsIgnoreCase(game.getStoredValue("ShrapnelTurretsFaction"))
                && getExpectedAfbHits() < 0.6) {
            return 2;
        }
        if (afbDieCount == 0
                && isWarsunOrDreadnought()
                && game.playerHasLeaderUnlockedOrAlliance(player, "zeliancommander")) {
            return 1;
        }
        return afbDieCount;
    }

    private double getExpectedAfbHits() {
        return afbDieCount * ((10 - afbHitsOn) / 10.0d);
    }

    private boolean isWarsunOrDreadnought() {
        return "warsun".equalsIgnoreCase(getBaseType()) || "dreadnought".equalsIgnoreCase(getBaseType());
    }

    public int getSpaceCannonDieCount(Player player, Game game) {
        if (game.getStoredValue("EBSFaction").equalsIgnoreCase(player.getFaction())) {
            if ("spacedock".equalsIgnoreCase(getBaseType())) {
                return 3;
            }
        }
        return spaceCannonDieCount;
    }

    public int getSpaceCannonHitsOn(Player player, Game game) {
        if (game.getStoredValue("EBSFaction").equalsIgnoreCase(player.getFaction())
                || player.hasRelic("lightrailordnance")) {
            if ("spacedock".equalsIgnoreCase(getBaseType())) {
                return 5;
            }
        }
        return spaceCannonHitsOn;
    }

    public int getAfbHitsOn(Player player, Game game) {
        if (capacityValue > 0
                && game.getStoredValue("ShrapnelTurretsFaction").equalsIgnoreCase(player.getFaction())
                && getExpectedAfbHits() < 0.6) {
            return 8;
        }
        if (afbDieCount == 0
                && isWarsunOrDreadnought()
                && game.playerHasLeaderUnlockedOrAlliance(player, "zeliancommander")) {
            return 5;
        }
        return afbHitsOn;
    }

    public int getBombardDieCount(Player player, Game game) {
        if (!game.getStoredValue("BlitzFaction").equalsIgnoreCase(player.getFaction())) {
            if (game.getStoredValue("TnelisAgentFaction").equalsIgnoreCase(player.getFaction())
                    && bombardDieCount == 0
                    && afbDieCount > 0) {
                return afbDieCount;
            }
            return bombardDieCount;
        } else {
            if (getIsShip() && !"fighter".equalsIgnoreCase(getBaseType()) && bombardDieCount == 0) {
                return 1;
            } else {
                return bombardDieCount;
            }
        }
    }

    public int getBombardHitsOn(Player player, Game game) {
        if (!game.getStoredValue("BlitzFaction").equalsIgnoreCase(player.getFaction())) {
            if (game.getStoredValue("TnelisAgentFaction").equalsIgnoreCase(player.getFaction())
                    && bombardDieCount == 0
                    && afbDieCount > 0) {
                return afbHitsOn;
            }
            return bombardHitsOn;
        } else {
            if (isShip != null && isShip && !"fighter".equalsIgnoreCase(getBaseType()) && bombardDieCount == 0) {
                return 6;
            } else {
                return bombardHitsOn;
            }
        }
    }

    public int getCombatDieHitsOnForAbility(CombatRollType rollType) {
        return switch (rollType) {
            case combatround -> getCombatHitsOn();
            case AFB -> getAfbHitsOn();
            case bombardment -> getBombardHitsOn();
            case SpaceCannonOffence, SpaceCannonDefence -> getSpaceCannonHitsOn();
        };
    }

    public int getCombatDieHitsOnForAbility(CombatRollType rollType, Player player, Game game) {
        return switch (rollType) {
            case combatround -> getCombatHitsOn();
            case AFB -> getAfbHitsOn(player, game);
            case bombardment -> getBombardHitsOn(player, game);
            case SpaceCannonOffence -> getSpaceCannonHitsOn(player, game);
            case SpaceCannonDefence -> getSpaceCannonHitsOn();
        };
    }

    private String getAsyncIDAliases() {
        String aliases = AliasHandler.getUnitListForHelp().getOrDefault(asyncId, asyncId);
        return asyncId + "=" + aliases;
    }

    private String getValuesText() {
        return getCostText() + getMoveText() + getProductionText() + getCapacityText();
    }

    private String getDiceText() {
        return getCombatText() + getAFBText() + getBombardText() + getSpaceCannonText();
    }

    private String getOtherText() {
        return getPlanetaryShieldText() + getSustainDamageText();
    }

    private String getCostText() {
        if (cost >= 1) {
            return "Cost: " + MiscEmojis.getResourceEmoji(Math.round(cost)) + "\n";
        } else if (cost == 0.5) {
            return "Cost: " + MiscEmojis.Resources_1 + " per 2 " + getUnitEmoji() + "\n";
        }
        return "";
    }

    private String getMoveText() {
        if (moveValue > 0) {
            return "Move: " + moveValue + "\n";
        }
        return "";
    }

    private String getProductionText() {
        if ("res".equals(basicProduction)) {
            return "PRODUCTION: " + MiscEmojis.resources + "+" + productionValue + "\n";
        }
        if (basicProduction != null) {
            return "PRODUCTION: *️⃣+" + productionValue + "\n";
        }
        if (productionValue > 0) {
            return "PRODUCTION: " + productionValue + "\n";
        }
        return "";
    }

    private String getCapacityText() {
        if (capacityValue > 0) {
            return "Capacity: " + capacityValue + "\n";
        }
        return "";
    }

    private String getCombatText() {
        if (combatDieCount == 1) {
            return "Combat: " + combatHitsOn + "\n";
        } else if (combatDieCount > 1) {
            return "Combat: " + combatHitsOn + " (x" + combatDieCount + ")\n";
        } else if ("winnu_flagship".equals(id)) {
            return "Combat: " + combatHitsOn + " (x # of opponent's non-fighter ships)\n";
        }
        return "";
    }

    private String getAFBText() {
        if (afbDieCount == 1) {
            return "ANTI-FIGHTER BARRAGE " + afbHitsOn + "\n";
        } else if (afbDieCount >= 2) {
            return "ANTI-FIGHTER BARRAGE " + afbHitsOn + " (x" + afbDieCount + ")\n";
        }
        return "";
    }

    private String getBombardText() {
        if (bombardDieCount == 1) {
            return "BOMBARDMENT: " + bombardHitsOn + "\n";
        } else if (bombardDieCount >= 2) {
            return "BOMBARDMENT: " + bombardHitsOn + " (x" + bombardDieCount + ")\n";
        }
        return "";
    }

    private String getSpaceCannonText() {
        if (spaceCannonDieCount == 1) {
            return "SPACE CANNON " + spaceCannonHitsOn + "\n";
        } else if (spaceCannonDieCount >= 2) {
            return "SPACE CANNON " + spaceCannonHitsOn + " (x" + spaceCannonDieCount + ")\n";
        }
        return "";
    }

    private String getPlanetaryShieldText() {
        if (getPlanetaryShield()) {
            return "PLANETARY SHIELD\n";
        }
        return "";
    }

    private String getSustainDamageText() {
        if (getSustainDamage()) {
            return "SUSTAIN DAMAGE\n";
        }
        return "";
    }

    public boolean search(String searchString) {
        return name.toLowerCase().contains(searchString)
                || getFaction().orElse("").toLowerCase().contains(searchString)
                || id.toLowerCase().contains(searchString)
                || baseType.toLowerCase().contains(searchString)
                || getSubtitle().orElse("").toLowerCase().contains(searchString)
                || searchTags.contains(searchString);
    }

    public static int sortFactionUnitsFirst(UnitModel a, UnitModel b) {
        boolean fa = a.getFaction().isEmpty();
        boolean fb = b.getFaction().isEmpty();
        if (fa && fb) return 0;
        if (!fa && !fb) return 0;
        if (!fa) return -1;
        return 1;
    }

    public String getAutoCompleteName() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" (");
        if (getFaction().isPresent()) sb.append(getFaction().get()).append(" ");
        sb.append(baseType).append(") [");
        sb.append(source).append("]");
        return sb.toString();
    }

    public boolean getDeepSpaceCannon() {
        return Optional.ofNullable(deepSpaceCannon).orElse(false);
    }

    public boolean getPlanetaryShield() {
        return Optional.ofNullable(planetaryShield).orElse(false);
    }

    public boolean getSustainDamage() {
        return Optional.ofNullable(sustainDamage).orElse(false);
    }

    public boolean getDisablesPlanetaryShield() {
        return Optional.ofNullable(disablesPlanetaryShield).orElse(false);
    }

    public boolean getCanBeDirectHit() {
        return Optional.ofNullable(canBeDirectHit).orElse(false);
    }

    public boolean getIsStructure() {
        return Optional.ofNullable(isStructure).orElse(false);
    }

    public boolean getIsMonument() {
        return Optional.ofNullable(isMonument).orElse(false);
    }

    public boolean getIsGroundForce() {
        return Optional.ofNullable(isGroundForce).orElse(false);
    }

    public boolean getIsShip() {
        return Optional.ofNullable(isShip).orElse(false);
    }

    public boolean getIsSpaceOnly() {
        return Optional.ofNullable(isSpaceOnly).orElse(getIsShip());
    }

    public boolean getIsPlanetOnly() {
        return Optional.ofNullable(isPlanetOnly).orElse(getIsStructure());
    }

    public Optional<String> getUpgradesFromUnitId() {
        return Optional.ofNullable(upgradesFromUnitId);
    }

    public Optional<String> getUpgradesToUnitId() {
        return Optional.ofNullable(upgradesToUnitId);
    }

    public Optional<String> getRequiredTechId() {
        return Optional.ofNullable(requiredTechId);
    }

    public Optional<String> getSubtitle() {
        return Optional.ofNullable(subtitle);
    }

    public Optional<String> getFaction() {
        return Optional.ofNullable(faction);
    }

    public Optional<String> getAbility() {
        return Optional.ofNullable(ability);
    }

    public Optional<String> getUnlock() {
        return Optional.ofNullable(unlock);
    }

    public Optional<String> getHomebrewReplacesID() {
        return Optional.ofNullable(homebrewReplacesID);
    }

    public List<String> getEligiblePlanetTypes() {
        return Optional.ofNullable(eligiblePlanetTypes).orElse(Collections.emptyList());
    }

    public TI4Emoji getMonumentPlanetTypeEmoji(String planetType) {
        return switch (planetType.toLowerCase()) {
            case "cultural", "industrial", "hazardous" -> ExploreEmojis.getTraitEmoji(planetType);
            case "legendary" -> MiscEmojis.LegendaryPlanet;
            case "empty_nonanomaly" -> MiscEmojis.EmptySystem;
            case "tech_specialty" -> TechEmojis.NonUnitTechSkip;
            case "mecatol_rex" -> PlanetEmojis.Mecatol;
            default -> TI4Emoji.getRandomGoodDog();
        };
    }

    public String getEligiblePlanetEmojis() {
        StringBuilder sb = new StringBuilder();
        for (String type : getEligiblePlanetTypes()) {
            sb.append(getMonumentPlanetTypeEmoji(type));
        }
        return sb.toString();
    }
}
