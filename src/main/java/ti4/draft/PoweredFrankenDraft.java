package ti4.draft;

import ti4.map.Game;

public class PoweredFrankenDraft extends FrankenDraft {

    public PoweredFrankenDraft(Game owner) {
        super(owner);
    }

    @Override
    public int getItemLimitForCategory(DraftItem.Category category) {

        int limit = 0;
        switch (category) {
            case ABILITY -> limit = 4;
            case TECH, BLUETILE -> limit = 3;
            case AGENT,
                    COMMANDER,
                    REDTILE,
                    STARTINGFLEET,
                    STARTINGTECH,
                    HOMESYSTEM,
                    PN,
                    COMMODITIES,
                    FLAGSHIP,
                    MECH,
                    HERO -> limit = 2;
            case DRAFTORDER -> limit = 1;
        }
        return limit;
    }

    @Override
    public String getSaveString() {
        return "powered_franken";
    }

    @Override
    public int getBagSize() {
        return 33;
    }
}
