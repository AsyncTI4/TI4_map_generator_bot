package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;

public class AgentDraftItem extends LeaderDraftItem {

    public AgentDraftItem(String itemId) {
        super(DraftCategory.AGENT, itemId);
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        if (game.isTwilightsFallMode()) return getLeader().getTFNameRepresentation();
        return getLeader().getNameRepresentation();
    }

    @Override
    protected String describe(LeaderModel leader, boolean twilightsFall) {
        return abilityText(leader, twilightsFall);
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        return draftable(DraftCategory.AGENT, buildAllItems(factions));
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        return buildLeaderItems(DraftCategory.AGENT, factions);
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        List<DraftItem> items = game.isTwilightsFallMode()
                ? generateAll(
                        DraftCategory.AGENT, Mapper.getDeck(game.getGenomeSpliceDeckID()).getNewShuffledDeck().stream())
                : buildAllItems(factions);
        return draftable(DraftCategory.AGENT, withoutBanned(items, game, "bannedLeaders"));
    }
}
