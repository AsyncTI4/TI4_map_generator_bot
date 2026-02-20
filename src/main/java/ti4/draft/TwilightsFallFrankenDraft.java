package ti4.draft;

import ti4.map.Game;

public class TwilightsFallFrankenDraft extends FrankenDraft {

    public TwilightsFallFrankenDraft(Game owner) {
        super(owner);
    }

    @Override
    public int getItemLimitForCategory(DraftCategory category) {
        return switch (category) {
            case TECH, BLUETILE -> 3;
            case REDTILE, STARTINGFLEET, HOMESYSTEM, AGENT, UNIT -> 2;
            case DRAFTORDER, MAHACTKING -> 1;
            default -> 0;
        };
    }

    @Override
    public int getKeptItemLimitForCategory(DraftCategory category) {
        return switch (category) {
            case BLUETILE -> 3;
            case REDTILE -> 2;
            case TECH -> 2;
            case DRAFTORDER, MAHACTKING, STARTINGFLEET -> 1;
            case HOMESYSTEM, AGENT, UNIT -> 1;
            default -> 0;
        };
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
