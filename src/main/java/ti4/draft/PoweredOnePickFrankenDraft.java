package ti4.draft;

import ti4.game.Game;

public class PoweredOnePickFrankenDraft extends PoweredFrankenDraft {

    public PoweredOnePickFrankenDraft(Game owner) {
        super(owner, 1, 1);
    }

    @Override
    public String getSaveString() {
        return "poweredonepick_franken";
    }
}
