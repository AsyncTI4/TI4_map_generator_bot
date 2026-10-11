package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;

public class HeroDraftItem extends LeaderDraftItem {

    public HeroDraftItem(String itemId) {
        super(DraftCategory.HERO, itemId);
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return super.getShortDescription().replace("\n", "");
    }

    @Override
    protected String describe(LeaderModel leader, boolean twilightsFall) {
        return "**" + leader.getAbilityName().orElse("").replace("\n", "") + "** - "
                + abilityText(leader, twilightsFall);
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        return draftable(DraftCategory.HERO, buildAllItems(factions));
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        return buildLeaderItems(DraftCategory.HERO, factions);
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        return draftable(DraftCategory.HERO, withoutBanned(buildAllItems(factions), game, "bannedLeaders"));
    }
}
