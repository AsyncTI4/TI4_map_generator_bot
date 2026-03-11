package ti4.draft;

import ti4.map.Game;

public class PoweredFrankenDraft extends FrankenDraft {

    public PoweredFrankenDraft(Game owner) {
        super(owner);
    }

    @Override
    public int getItemLimitForCategory(DraftCategory category) {
        return switch (category) {
            case ABILITY -> 4;
            case TECH, BLUETILE -> 3;
            case AGENT, COMMANDER, HERO -> 2;
            case STARTINGFLEET, STARTINGTECH, HOMESYSTEM -> 2;
            case FLAGSHIP, MECH, BREAKTHROUGH -> 2;
            case PN, COMMODITIES, REDTILE -> 2;
            case DRAFTORDER -> 1;
            case UNIT, PLOT, MAHACTKING -> 0;
        };
    }

    @Override
    public int getKeptItemLimitForCategory(DraftCategory category) {
        return switch (category) {
            case ABILITY -> 4;
            case TECH -> 3;
            case BLUETILE -> 3;
            case REDTILE -> 2;
            case COMMODITIES, FLAGSHIP, MECH, PN -> 1;
            case HERO, COMMANDER, AGENT, BREAKTHROUGH -> 1;
            case DRAFTORDER, STARTINGFLEET, STARTINGTECH, HOMESYSTEM -> 1;
            case UNIT, PLOT, MAHACTKING -> 0;
        };
    }

    @Override
    public String getSaveString() {
        return "powered_franken";
    }
}
