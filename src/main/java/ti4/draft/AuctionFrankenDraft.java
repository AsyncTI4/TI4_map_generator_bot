package ti4.draft;

import ti4.game.Game;

public class AuctionFrankenDraft extends FrankenDraft {

    public AuctionFrankenDraft(Game owner) {
        super(owner);
    }

    @Override
    public int getItemLimitForCategory(DraftCategory category) {
        if (isFowExcludedCategory(category)) return 0;
        return switch (category) {
            case ABILITY, BLUETILE -> 3;
            case TECH, REDTILE -> 2;
            case BREAKTHROUGH,
                    AGENT,
                    COMMANDER,
                    HERO,
                    COMMODITIES,
                    FLAGSHIP,
                    MECH,
                    HOMESYSTEM,
                    PN,
                    STARTINGFLEET,
                    STARTINGTECH,
                    DRAFTORDER -> 1;
            case FACTION, UNIT, PLOT, MAHACTKING, MONUMENT -> 0;
        };
    }

    @Override
    public int getKeptItemLimitForCategory(DraftCategory category) {
        return FrankenDraft.getItemLimitForCategory(category, getOwner());
    }

    @Override
    public String getSaveString() {
        return "auction_franken";
    }
}
