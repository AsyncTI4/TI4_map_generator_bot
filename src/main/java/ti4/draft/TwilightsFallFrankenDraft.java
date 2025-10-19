package ti4.draft;

import ti4.map.Game;

public class TwilightsFallFrankenDraft extends FrankenDraft {

    public TwilightsFallFrankenDraft(Game owner) {
        super(owner);
    }

    @Override
    public int getItemLimitForCategory(DraftItem.Category category) {

        int limit = 0;
        switch (category) {
            case ABILITY -> limit = 0;
            case TECH, BLUETILE -> limit = 3;
            case REDTILE, STARTINGFLEET, HOMESYSTEM, AGENT, UNIT -> limit = 2;
            case COMMANDER, STARTINGTECH, PN, COMMODITIES, FLAGSHIP, MECH, HERO -> limit = 0;
            case DRAFTORDER, MAHACTKING -> limit = 1;
        }
        return limit;
    }

    @Override
    public String getSaveString() {
        return "twilights_fall";
    }

    @Override
    public int getBagSize() {
        return 18;
    }
}
