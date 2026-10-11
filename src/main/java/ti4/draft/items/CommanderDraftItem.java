package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;

public class CommanderDraftItem extends LeaderDraftItem {

    public CommanderDraftItem(String itemId) {
        super(DraftCategory.COMMANDER, itemId);
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @Override
    protected String describe(LeaderModel leader, boolean twilightsFall) {
        return abilityText(leader, false) + " **Unlock:** " + leader.getUnlockCondition();
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        return draftable(DraftCategory.COMMANDER, buildAllItems(factions));
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        return buildLeaderItems(DraftCategory.COMMANDER, factions);
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        return draftable(DraftCategory.COMMANDER, withoutBanned(buildAllItems(factions), game, "bannedLeaders"));
    }
}
