package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import ti4.draft.BagDraft;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.PatternHelper;
import ti4.image.Mapper;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.TI4Emoji;

public class FactionDraftItem extends DraftItem {

    public FactionDraftItem(String itemId) {
        super(DraftCategory.FACTION, itemId);
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        FactionModel faction = getFaction();
        if (faction == null) {
            return getAlias();
        }
        return faction.getNameRepresentation();
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        FactionModel faction = getFaction();
        if (faction == null) {
            return getAlias();
        }
        return faction.getShortName();
    }

    @JsonIgnore
    @Override
    protected String getLongDescriptionImpl() {
        return getLongDescriptionImpl(null);
    }

    @JsonIgnore
    @Override
    protected String getLongDescriptionImpl(Game game) {
        FactionModel faction = getFaction();
        if (faction == null) {
            return "";
        }
        if (game == null) {
            return faction.getFactionName();
        }

        return getComponentList(game);
    }

    @JsonIgnore
    @Override
    public List<TextDisplay> getTextDisplays(Game game, Player player, boolean showDescr) {
        return List.of(TextDisplay.of(getTitle(game)));
    }

    @JsonIgnore
    public String getComponentList(Game game) {
        FactionModel faction = getFaction();
        if (faction == null) {
            return "";
        }

        Map<DraftCategory, List<DraftItem>> components =
                getComponents(game).stream().collect(Collectors.groupingBy(DraftItem::getItemCategory));
        StringBuilder sb = new StringBuilder();
        sb.append("Commodities: ").append(faction.getCommodities());
        for (DraftCategory category : DraftCategory.values()) {
            if (category == DraftCategory.FACTION) {
                continue;
            }
            List<DraftItem> items = components.get(category);
            if (items == null || items.isEmpty()) {
                continue;
            }
            sb.append('\n').append(category.title(game).replace("## ", "")).append(": ");
            sb.append(items.stream().map(DraftItem::getShortDescription).collect(Collectors.joining(", ")));
        }
        return sb.toString();
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return FactionEmojis.getFactionIcon(getItemId());
    }

    @Override
    public boolean isDraftable(Player player) {
        if (player.getDraftHand().Contents.contains(this)) {
            return false;
        }
        BagDraft draft = player.getGame().getActiveBagDraft();
        if (draft.playerQueueIsFull(player)) {
            return false;
        }
        int taken = player.getDraftHand().getCategoryCount(DraftCategory.FACTION)
                + player.getDraftQueue().getCategoryCount(DraftCategory.FACTION);
        return taken < draft.getItemLimitForCategory(DraftCategory.FACTION);
    }

    @JsonIgnore
    public List<DraftItem> getComponents(Game game) {
        FactionModel faction = getFaction();
        if (faction == null) {
            return List.of();
        }

        List<DraftItem> components = new ArrayList<>();
        for (String ability : faction.getAbilities()) {
            addIfAllowed(game, components, DraftCategory.ABILITY, ability, ability);
        }
        for (String tech : faction.getFactionTech()) {
            addIfAllowed(game, components, DraftCategory.TECH, tech, tech);
        }
        for (String leader : faction.getLeaders()) {
            LeaderModel model = Mapper.getLeader(leader);
            if (model == null) {
                continue;
            }
            switch (model.getType()) {
                case "agent" -> addIfAllowed(game, components, DraftCategory.AGENT, leader, leader);
                case "commander" -> addIfAllowed(game, components, DraftCategory.COMMANDER, leader, leader);
                case "hero" -> addIfAllowed(game, components, DraftCategory.HERO, leader, leader);
                default -> {}
            }
        }
        for (String unit : faction.getUnits()) {
            UnitModel model = Mapper.getUnit(unit);
            if (model == null) {
                continue;
            }
            switch (model.getBaseType()) {
                case "mech" -> addIfAllowed(game, components, DraftCategory.MECH, unit, faction.getAlias());
                case "flagship" -> addIfAllowed(game, components, DraftCategory.FLAGSHIP, unit, faction.getAlias());
                default -> {}
            }
        }
        addIfAllowed(game, components, DraftCategory.COMMODITIES, faction.getAlias(), faction.getAlias());
        for (String pn : faction.getPromissoryNotes()) {
            addIfAllowed(game, components, DraftCategory.PN, pn, pn);
        }
        addIfAllowed(game, components, DraftCategory.HOMESYSTEM, faction.getAlias(), faction.getAlias());
        addIfAllowed(game, components, DraftCategory.STARTINGTECH, faction.getAlias(), faction.getAlias());
        addIfAllowed(game, components, DraftCategory.STARTINGFLEET, faction.getAlias(), faction.getAlias());

        String breakthrough = faction.getBreakthrough();
        if (Mapper.isValidBreakthrough(breakthrough)) {
            addIfAllowed(game, components, DraftCategory.BREAKTHROUGH, breakthrough, breakthrough);
        }
        return components;
    }

    private static void addIfAllowed(
            Game game, List<DraftItem> components, DraftCategory category, String itemId, String banValue) {
        String resolvedItemId = resolveFrankenReplacement(game, category, itemId);
        if (resolvedItemId == null) {
            return;
        }
        String resolvedBanValue = itemId.equals(banValue) ? resolvedItemId : banValue;
        if (isBanned(game, category, resolvedBanValue)) {
            return;
        }
        DraftItem item = generate(category, resolvedItemId);
        if (Boolean.TRUE.equals(item.getErrata().getUndraftable())) {
            return;
        }
        components.add(item);
    }

    private static String resolveFrankenReplacement(Game game, DraftCategory category, String itemId) {
        if (!game.isFrankenGame()) {
            return itemId;
        }

        DraftItem item = generate(category, itemId);
        if (!Boolean.TRUE.equals(item.getErrata().getUndraftable())) {
            return itemId;
        }

        String replacementId = itemId + "_y";
        if (!isValidReplacement(category, replacementId)) {
            return itemId;
        }

        DraftItem replacement = generate(category, replacementId);
        if (Boolean.TRUE.equals(replacement.getErrata().getUndraftable())) {
            return itemId;
        }
        return replacementId;
    }

    private static boolean isValidReplacement(DraftCategory category, String itemId) {
        return switch (category) {
            case ABILITY -> Mapper.isValidAbility(itemId);
            case TECH -> Mapper.isValidTech(itemId);
            case AGENT, COMMANDER, HERO -> Mapper.isValidLeader(itemId);
            case MECH, FLAGSHIP, UNIT -> Mapper.isValidUnit(itemId);
            case PN -> Mapper.isValidPromissoryNote(itemId);
            case HOMESYSTEM, STARTINGTECH, STARTINGFLEET, COMMODITIES, FACTION -> Mapper.isValidFaction(itemId);
            case BREAKTHROUGH -> Mapper.isValidBreakthrough(itemId);
            default -> false;
        };
    }

    private static boolean isBanned(Game game, DraftCategory category, String value) {
        return switch (category) {
            case ABILITY -> storedListContains(game, "bannedAbilities", value);
            case TECH -> storedListContains(game, "bannedTechs", value);
            case BREAKTHROUGH -> storedListContains(game, "bannedBreakthroughs", value);
            case AGENT, COMMANDER, HERO -> storedListContains(game, "bannedLeaders", value);
            case MECH -> storedListContains(game, "bannedMechs", value);
            case FLAGSHIP -> storedListContains(game, "bannedFSs", value);
            case COMMODITIES -> storedListContains(game, "bannedComms", value);
            case PN -> storedListContains(game, "bannedPNs", value);
            case HOMESYSTEM -> storedListContains(game, "bannedHSs", value);
            case STARTINGTECH -> storedListContains(game, "bannedStartingTechs", value);
            case STARTINGFLEET -> storedListContains(game, "bannedFleets", value);
            default -> false;
        };
    }

    private static boolean storedListContains(Game game, String key, String value) {
        return Arrays.asList(PatternHelper.FIN_SEPERATOR_PATTERN.split(game.getStoredValue(key)))
                .contains(value);
    }

    @JsonIgnore
    private FactionModel getFaction() {
        return Mapper.getFaction(getItemId());
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = buildAllItems(factions);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.FACTION);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            allItems.add(generate(DraftCategory.FACTION, faction.getAlias()));
        }
        return allItems;
    }
}
