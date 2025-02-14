package ti4.draft;

import ti4.map.Game;

public class OnePickFrankenDraft extends FrankenDraft {

    public OnePickFrankenDraft(Game owner) {
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
    public String getSaveString() {
        return "onepick_franken";
    }
}
