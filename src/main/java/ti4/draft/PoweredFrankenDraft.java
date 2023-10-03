package ti4.draft;

public class PoweredFrankenDraft extends FrankenDraft {

    @Override
    public int GetItemLimitForCategory(DraftItem.Category category) {

        int limit = 0;
        switch (category) {

            case ABILITY -> {
                limit = 4;
            }
            case TECH, BLUETILE -> {
                limit = 3;
            }
            case AGENT, COMMANDER, REDTILE, STARTINGFLEET, STARTINGTECH, HOMESYSTEM, PN, COMMODITIES, FLAGSHIP, MECH, HERO -> {
                limit = 2;
            }
            case DRAFTORDER -> {
                limit = 1;
            }
        }
        return limit;
    }

    @Override
    public String getSaveString() {
        return "powered_franken";
    }
}
