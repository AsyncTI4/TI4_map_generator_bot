package ti4.model;


import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;

@Data
public class UnitModel implements ModelInterface {
    private String id;
    private String baseType;
    private String asyncId;
    private String imageFileSuffix;
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

    //Source: units.json - source of json: https://docs.google.com/spreadsheets/d/1nbHylJyn4VURCRKX8ePmOrLa6dAsc504ww0BPZXIRxU/edit?usp=sharing
    //Google Sheet to JSON script: https://gist.githubusercontent.com/pamelafox/1878143/raw/6c23f71231ce1fa09be2d515f317ffe70e4b19aa/exportjson.js?utm_source=thenewstack&utm_medium=website&utm_content=inline-mention&utm_campaign=platform
    //From: https://thenewstack.io/how-to-convert-google-spreadsheet-to-json-formatted-text/

    @Override
    public boolean isValid() {
        return this.id != null && !this.id.isEmpty() && (getFaction() == null || Mapper.isFaction(getFaction().toLowerCase()));
    }

    @Override
    public String getAlias() {
        return getId();
    }

    public String getUnitRepresentation() {
        String faction = getFaction();
        String factionEmoji = Helper.getEmojiFromDiscord(faction);
        String unitEmoji = Helper.getEmojiFromDiscord(getBaseType());

      return unitEmoji + " " + getName() + factionEmoji + ": " + getAbility();
    }
    
    public MessageEmbed getUnitRepresentationEmbed(boolean includeAliases) {
        
        String factionEmoji = getFaction() == null ? "" : Helper.getFactionIconFromDiscord(getFaction().toLowerCase());
        String unitEmoji = getBaseType() == null ? "" : Helper.getEmojiFromDiscord(getBaseType());

        EmbedBuilder eb = new EmbedBuilder();
        /*
            Set the title:
            1. Arg: title as string
            2. Arg: URL as string or could also be null
        */
        String name = getName() == null ? "" : getName();
        eb.setTitle(factionEmoji + unitEmoji + " __" + name + "__", null);

        /*
            Set the color
        */
        // eb.setColor(Color.red);
        // eb.setColor(new Color(0xF40C0C));
        // eb.setColor(new Color(255, 0, 54));

        /*
            Set the text of the Embed:
            Arg: text as string
        */
        // eb.setDescription(getId());

        // String afbText = unit.getAfbHitsOn() + 

        /*
            Add fields to embed:
            1. Arg: title as string
            2. Arg: text as string
            3. Arg: inline mode true / false
        */
        // eb.addField("Title of field", "test of field", false);
        // eb.addField("Title of field", "test of field", false);
        if (!getValuesText().isEmpty()) eb.addField("Values:", getValuesText(), true);
        if (!getDiceText().isEmpty()) eb.addField("Dice Rolls:", getDiceText(), true);
        if (!getOtherText().isEmpty()) eb.addField("Traits:", getOtherText(), true);
        if (getAbility() != null) eb.addField("Ability:", getAbility(), false);

        /*
            Add spacer like field
            Arg: inline mode true / false
        */
        // eb.addBlankField(false);

        /*
            Add embed author:
            1. Arg: name as string
            2. Arg: url as string (can be null)
            3. Arg: icon url as string (can be null)
        */
        // eb.setAuthor("name", null, "https://github.com/zekroTJA/DiscordBot/blob/master/.websrc/zekroBot_Logo_-_round_small.png");

        /*
            Set footer:
            1. Arg: text as string
            2. icon url as string (can be null)
        */
        // eb.setFooter("Text", "https://github.com/zekroTJA/DiscordBot/blob/master/.websrc/zekroBot_Logo_-_round_small.png");
        if (includeAliases) eb.setFooter("UnitID: " + getId() + "\nAliases: " + getAsyncIDAliases() + "\nSource: " + getSource());

        /*
            Set image:
            Arg: image url as string
        */
        // eb.setImage("https://github.com/zekroTJA/DiscordBot/blob/master/.websrc/logo%20-%20title.png");

        /*
            Set thumbnail image:
            Arg: image url as string
        */
        // eb.setThumbnail("https://github.com/zekroTJA/DiscordBot/blob/master/.websrc/logo%20-%20title.png");

        return eb.build();
    }

    private String getAsyncIDAliases() {
        String aliases = AliasHandler.getUnitListForHelp().getOrDefault(asyncId, asyncId);
        return getAsyncId() + "=" + aliases;

    }

    private String getValuesText() {
        StringBuilder sb = new StringBuilder();
        sb.append(getCostText());
        sb.append(getMoveText());
        sb.append(getProductionText());
        sb.append(getCapacityText());
        return sb.toString();
    }

    private String getDiceText() {
        StringBuilder sb = new StringBuilder();
        sb.append(getCombatText());
        sb.append(getAFBText());
        sb.append(getBombardText());
        sb.append(getSpaceCannonText());
        return sb.toString();
    }
    
    private String getOtherText() {
        StringBuilder sb = new StringBuilder();
        sb.append(getPlanetaryShieldText());
        sb.append(getSustainDamageText());
        return sb.toString();
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
}
