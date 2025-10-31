package ti4.draft;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ti4.map.Game;

public class InauguralSpliceFrankenDraft extends FrankenDraft {

    public InauguralSpliceFrankenDraft(Game owner) {
        super(owner);
    }

    @Override
    public int getPicksFromFirstBag() {
        return 1;
    }

    @Override
    public int getPicksFromNextBags() {
        return 1;
    }

    @Override
    public int getItemLimitForCategory(DraftItem.Category category) {

        int limit = 0;
        switch (category) {
            case ABILITY -> limit = 0;
            case TECH -> limit = 3;
            case REDTILE, BLUETILE, STARTINGFLEET, HOMESYSTEM -> limit = 0;
            case DRAFTORDER, MAHACTKING, COMMANDER, STARTINGTECH, PN, COMMODITIES, FLAGSHIP, MECH, HERO -> limit = 0;
            case AGENT, UNIT -> limit = 2;
        }
        return limit;
    }

    @Override
    public String getSaveString() {
        return "inaugural_splice";
    }

    @Override
    public int getBagSize() {
        return 7;
    }

    @JsonIgnore
    @Override
    public String getDraftStatusMessage() {
        String baseMessage = super.getDraftStatusMessage();
        return baseMessage.replace("__Draft Status__", "__Inaugural Splice Status__");
    }
}
