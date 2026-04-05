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
    public int getItemLimitForCategory(DraftCategory category) {
        return switch (category) {
            case TECH -> 3;
            case AGENT, UNIT -> 2;
            default -> 0;
        };
    }

    @Override
    public int getKeptItemLimitForCategory(DraftCategory category) {
        return switch (category) {
            case TECH -> 2;
            case AGENT, UNIT -> 1;
            default -> 0;
        };
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
