package ti4.draft;

import ti4.map.Game;

public class OverdraftFrankenDraft extends FrankenDraft {

    public OverdraftFrankenDraft(Game owner) {
        super(owner);
    }

    @Override
    public int getKeptItemLimitForCategory(DraftCategory category) {
        return getItemLimitForCategory(category);
    }

    @Override
    public String getSaveString() {
        return "overdraft_franken";
    }
}
