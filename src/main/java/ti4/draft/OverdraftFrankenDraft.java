package ti4.draft;

import ti4.game.Game;

public class OverdraftFrankenDraft extends FrankenDraft {

    public OverdraftFrankenDraft(Game owner) {
        super(owner);
    }

    @Override
    public int getKeptItemLimitForCategory(DraftCategory category) {
        if (category == DraftCategory.MONUMENT) return getConfiguredMonumentLimit();
        return getItemLimitForCategory(category);
    }

    @Override
    public String getSaveString() {
        return "overdraft_franken";
    }
}
