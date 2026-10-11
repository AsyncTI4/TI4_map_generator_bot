package ti4.draft;

import ti4.game.Game;

public class PoweredOverdraftFrankenDraft extends PoweredFrankenDraft {

    public PoweredOverdraftFrankenDraft(Game owner) {
        super(owner);
    }

    @Override
    public int getKeptItemLimitForCategory(DraftCategory category) {
        if (category == DraftCategory.MONUMENT) return getConfiguredMonumentLimit();
        return getItemLimitForCategory(category);
    }

    @Override
    public String getSaveString() {
        return "powered_overdraft_franken";
    }
}
