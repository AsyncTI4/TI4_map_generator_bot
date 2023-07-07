package ti4.model;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.generator.Mapper;
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

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public String getAlias() {
        return getId();
    }

    public String getUnitRepresentation() {
        String faction = getFaction();
        String factionEmoji = Helper.getEmojiFromDiscord(faction);
        String unitEmoji = Helper.getEmojiFromDiscord(getBaseType());

        String representation = unitEmoji + " " + getName() + factionEmoji + ": " + getAbility();
        return representation;
    }

    public MessageEmbed getUnitRepresentationEmbed() {
        
        String faction = getFaction();
        String factionEmoji = Helper.getEmojiFromDiscord(faction);
        String unitEmoji = Helper.getEmojiFromDiscord(getBaseType());

        EmbedBuilder eb = new EmbedBuilder();
        /*
            Set the title:
            1. Arg: title as string
            2. Arg: URL as string or could also be null
        */
        eb.setTitle(factionEmoji + " " + getName(), null);

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
        eb.setDescription(unitEmoji + " " + getBaseType());

        // String afbText = unit.getAfbHitsOn() + 

        /*
            Add fields to embed:
            1. Arg: title as string
            2. Arg: text as string
            3. Arg: inline mode true / false
        */
        // eb.addField("Title of field", "test of field", false);
        // eb.addField("Title of field", "test of field", false);
        eb.addField("Abilities:", getDiceText(), true);
        eb.addField("Title of inline field", "test of inline field", true);
        eb.addField("Title of inline field", "test of inline field", true);

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

    public String getDiceText() {
        StringBuilder sb = new StringBuilder();
        sb.append(getPlanetaryShieldText());
        sb.append(getSustainDamageText());
        return sb.toString();
    }
    
    public String getOtherText() {
        StringBuilder sb = new StringBuilder();
        sb.append(getPlanetaryShieldText());
        sb.append(getSustainDamageText());
        return sb.toString();
    }

    public String getAFBText() {
        if (getAfbDieCount() > 0) {
            return "Anti-Fighter Barrage " + getAfbHitsOn() + " (x" + getAfbDieCount() + ")\n";
        }
        return "";
    }

    public String getBombardText() {
        if (getBombardDieCount() > 0) {
            return "Bombard " + getBombardHitsOn() + " (x" + getBombardDieCount() + ")\n";
        }
        return "";
    }

    public String getSpaceCannonText() {
        if (getSpaceCannonDieCount() > 0) {
            return ((getDeepSpaceCannon() != null && getDeepSpaceCannon()) ? "Deep " : "") + "Space Cannon " + getSpaceCannonHitsOn() + " (x" + getSpaceCannonDieCount() + ")\n";
        }
        return "";
    }

    public String getPlanetaryShieldText() {
        if (getPlanetaryShield() != null && getPlanetaryShield()) {
            return "Planetary Shield\n";
        }
        return "";
    }

    public String getSustainDamageText() {
        if (getSustainDamage() != null && getSustainDamage()) {
            return "Sustain Damage\n";
        }
        return "";
    }
}
