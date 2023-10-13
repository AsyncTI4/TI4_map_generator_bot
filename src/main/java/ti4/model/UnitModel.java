package ti4.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.CombatRollType;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;

@Data
public class UnitModel implements ModelInterface, EmbeddableModel {
    private String id;
    private String baseType;
    private String asyncId;
    private String name;
    private String upgradesFromUnitId;
    private String upgradesToUnitId;
    private String requiredTechId;
    private String source;
    private String faction;
    private int moveValue;
    private int productionValue;
    private int capacityValue;
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
    private Boolean isGroundForce;
    private Boolean isShip;
    private String ability;
    private List<String> searchTags = new ArrayList<>();

    //Source: units.json - source of json: https://docs.google.com/spreadsheets/d/1nbHylJyn4VURCRKX8ePmOrLa6dAsc504ww0BPZXIRxU/edit?usp=sharing
    //Google Sheet to JSON script: https://gist.githubusercontent.com/pamelafox/1878143/raw/6c23f71231ce1fa09be2d515f317ffe70e4b19aa/exportjson.js?utm_source=thenewstack&utm_medium=website&utm_content=inline-mention&utm_campaign=platform
    //From: https://thenewstack.io/how-to-convert-google-spreadsheet-to-json-formatted-text/

    public boolean isValid() {
        return id != null 
            && !id.isEmpty()
            && baseType != null
            && asyncId != null
            && source != null
            && List.of("ca","cv","dd","dn","ff","fs","gf","mf","pd","sd","ws","csd","plenaryorbital","tyrantslament").contains(getAsyncId())
            && (getFaction() == null || Mapper.isFaction(getFaction().toLowerCase()));
    }

    public String getAlias() {
        return getId();
    }

    public String getImageFileSuffix() {
        return "_" + getAsyncId() + ".png";
    }

    public String getColourAsyncID(String colour) {
        colour = AliasHandler.resolveColor(colour);
        colour = Mapper.getColorID(colour);
        return colour + getImageFileSuffix();
    }

    public String getUnitEmoji() {
        return Helper.getEmojiFromDiscord(getBaseType());
    }

    public String getUnitRepresentation() {
        String faction = getFaction();
        String factionEmoji = Helper.getEmojiFromDiscord(faction);
        String unitEmoji = Helper.getEmojiFromDiscord(getBaseType());

        return unitEmoji + " " + getName() + factionEmoji + ": " + getAbility();
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeAliases) {

        String factionEmoji = getFaction() == null ? "" : Helper.getFactionIconFromDiscord(getFaction());
        String unitEmoji = getBaseType() == null ? "" : Helper.getEmojiFromDiscord(getBaseType());

        EmbedBuilder eb = new EmbedBuilder();
      
        String name = getName() == null ? "" : getName();
        eb.setTitle(factionEmoji + unitEmoji + " __" + name + "__ " + getSourceEmoji(), null);

        if (!getValuesText().isEmpty()) eb.addField("Values:", getValuesText(), true);
        if (!getDiceText().isEmpty()) eb.addField("Dice Rolls:", getDiceText(), true);
        if (!getOtherText().isEmpty()) eb.addField("Traits:", getOtherText(), true);
        if (getAbility() != null) eb.addField("Ability:", getAbility(), false);

        if (includeAliases) eb.setFooter("UnitID: " + getId() + "\nAliases: " + getAsyncIDAliases() + "\nSource: " + getSource());

        return eb.build();
    }

    public String getSourceEmoji() {
        return switch (getSource()) {
            case "absol" -> Emojis.Absol;
            case "ds" -> Emojis.DiscordantStars;
            default -> "";
        };
    }

    public int getCombatDieCountForAbility(CombatRollType rollType) {
        return switch (rollType) {
            case combatround -> getCombatDieCount();
            case AFB -> getAfbDieCount();
            case bombardment -> getBombardDieCount();
            case SpaceCannonOffence, SpaceCannonDefence -> getSpaceCannonDieCount();
            default -> getCombatDieCount();
        };
    }

    public int getCombatDieHitsOnForAbility(CombatRollType rollType) {
        return switch (rollType) {
            case combatround -> getCombatHitsOn();
            case AFB -> getAfbHitsOn();
            case bombardment -> getBombardHitsOn();
            case SpaceCannonOffence, SpaceCannonDefence -> getSpaceCannonHitsOn();
            default -> getCombatHitsOn();
        };
    }

    public boolean getIsGroundForce() {
        return Optional.ofNullable(isGroundForce).orElse(false);
    }

    private String getAsyncIDAliases() {
        String aliases = AliasHandler.getUnitListForHelp().getOrDefault(asyncId, asyncId);
        return getAsyncId() + "=" + aliases;

    }

    private String getValuesText() {
        return getCostText() +
            getMoveText() +
            getProductionText() +
            getCapacityText();
    }

    private String getDiceText() {
        return getCombatText() +
            getAFBText() +
            getBombardText() +
            getSpaceCannonText();
    }

    private String getOtherText() {
        return getPlanetaryShieldText() +
            getSustainDamageText();
    }

    private String getCostText() {
        if (getCost() >= 1) {
            return "Cost: " + Helper.getResourceEmoji(Math.round(getCost())) + "\n";
        } else if (getCost() == 0.5) {
            return "Cost: " + Emojis.Resources_1 + " (for 2 " + Helper.getEmojiFromDiscord(getBaseType()) + ")\n";
        }
        return "";
    }

    private String getMoveText() {
        if (getMoveValue() > 0) {
            return "Move: " + getMoveValue() + "\n";
        }
        return "";
    }

    private String getProductionText() {
        if (getProductionValue() > 0) {
            return "Production: " + getProductionValue() + "\n";
        }
        return "";
    }

    private String getCapacityText() {
        if (getCapacityValue() > 0) {
            return "Capacity: " + getCapacityValue() + "\n";
        }
        return "";
    }

    private String getCombatText() {
        if (getCombatDieCount() == 1) {
            return "Combat: " + getCombatHitsOn() + "\n";
        } else if (getCombatDieCount() > 1) {
            return "Combat: " + getCombatHitsOn() + " (x" + getCombatDieCount() + ")\n";
        }
        return "";
    }

    private String getAFBText() {
        if (getAfbDieCount() > 0) {
            return "Anti-Fighter Barrage " + getAfbHitsOn() + " (x" + getAfbDieCount() + ")\n";
        }
        return "";
    }

    private String getBombardText() {
        if (getBombardDieCount() == 1) {
            return "Bombardment: " + getBombardHitsOn() + "\n";
        } else if (getBombardDieCount() > 0) {
            return "Bombardment: " + getBombardHitsOn() + " (x" + getBombardDieCount() + ")\n";
        }
        return "";
    }

    private String getSpaceCannonText() {
        if (getSpaceCannonDieCount() > 0) {
            return ((getDeepSpaceCannon() != null && getDeepSpaceCannon()) ? "Deep " : "") + "Space Cannon " + getSpaceCannonHitsOn() + " (x" + getSpaceCannonDieCount() + ")\n";
        }
        return "";
    }
    private String getPlanetaryShieldText() {
        if (getPlanetaryShield() != null && getPlanetaryShield()) {
            return "Planetary Shield\n";
        }
        return "";
    }

    private String getSustainDamageText() {
        if (getSustainDamage() != null && getSustainDamage()) {
            return "Sustain Damage\n";
        }
        return "";
    }

    public boolean search(String searchString) {
        return getName().toLowerCase().contains(searchString)
            || (getFaction() != null && getFaction().toLowerCase().contains(searchString))
            || getId().toLowerCase().contains(searchString)
            || getBaseType().toLowerCase().contains(searchString)
            || getSearchTags().contains(searchString);
    }

    public String getAutoCompleteName() {
        StringBuilder sb = new StringBuilder();
        sb.append(getName()).append(" (");
        if (getFaction() != null) sb.append(getFaction()).append(" ");
        sb.append(getBaseType()).append(")");
        return sb.toString();
    }
}
