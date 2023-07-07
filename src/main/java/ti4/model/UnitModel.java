package ti4.model;

import lombok.Data;

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

    public String getCardText() {
        StringBuilder sb = new StringBuilder();
        sb.append(getAFBText());
        sb.append(getBombardText());
        sb.append(getSpaceCannonText());
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
