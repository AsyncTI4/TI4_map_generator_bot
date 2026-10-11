package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Map;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.service.emoji.TI4Emoji;

public abstract class LeaderDraftItem extends DraftItem {

    protected LeaderDraftItem(DraftCategory category, String itemId) {
        super(category, itemId);
    }

    @JsonIgnore
    protected LeaderModel getLeader() {
        return Mapper.getLeader(getItemId());
    }

    protected abstract String describe(LeaderModel leader, boolean twilightsFall);

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        return getLeader().getNameRepresentation();
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        LeaderModel leader = getLeader();
        if (leader == null) {
            return getAlias();
        }
        return leader.getName();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        LeaderModel leader = getLeader();
        return leader == null ? "" : describe(leader, false);
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        LeaderModel leader = getLeader();
        return leader == null ? "" : describe(leader, game.isTwilightsFallMode());
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        LeaderModel leader = getLeader();
        return leader == null ? null : leader.getLeaderEmoji();
    }

    protected static String abilityText(LeaderModel leader, boolean twilightsFall) {
        if (twilightsFall) {
            return "*" + leader.getTFAbilityWindowIfAble() + "* " + leader.getTFAbilityTextIfAble();
        }
        return "*" + leader.getAbilityWindow() + "* " + leader.getAbilityText();
    }

    protected static List<DraftItem> buildLeaderItems(DraftCategory category, List<FactionModel> factions) {
        Map<String, LeaderModel> allLeaders = Mapper.getLeaders();
        String type = category.name().toLowerCase();
        return generateAll(
                category,
                factions.stream()
                        .flatMap(faction -> faction.getLeaders().stream())
                        .filter(leader -> type.equals(allLeaders.get(leader).getType())));
    }
}
