package ti4.draft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import ti4.draft.items.AbilityDraftItem;
import ti4.draft.items.AgentDraftItem;
import ti4.draft.items.BreakthroughDraftItem;
import ti4.draft.items.CommanderDraftItem;
import ti4.draft.items.CommoditiesDraftItem;
import ti4.draft.items.FlagshipDraftItem;
import ti4.draft.items.HeroDraftItem;
import ti4.draft.items.HomeSystemDraftItem;
import ti4.draft.items.MahactKingDraftItem;
import ti4.draft.items.MechDraftItem;
import ti4.draft.items.MonumentDraftItem;
import ti4.draft.items.PNDraftItem;
import ti4.draft.items.SpeakerOrderDraftItem;
import ti4.draft.items.StartingFleetDraftItem;
import ti4.draft.items.StartingTechDraftItem;
import ti4.draft.items.TechDraftItem;
import ti4.draft.items.TileDraftItem;
import ti4.draft.items.UnitDraftItem;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.PatternHelper;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.service.milty.EchoesOfYggdrasilService;
import ti4.service.milty.MiltyDraftHelper;
import ti4.service.milty.MiltyDraftManager;

public class FrankenDraft extends BagDraft {

    public FrankenDraft(Game owner) {
        this(owner, 3, 2);
    }

    protected FrankenDraft(Game owner, int picksFromFirstBag, int picksFromNextBags) {
        super(owner, picksFromFirstBag, picksFromNextBags);
    }

    /**
     * FoW games don't pre-draft map tiles, and table/speaker order is handled separately by the FoW setup
     * wizard - blue tile, red tile, and draft order limits are always 0 in Fog of War, for every Franken
     * draft variant.
     */
    protected boolean isFowExcludedCategory(DraftCategory category) {
        return getOwner().isFowMode()
                && (category == DraftCategory.BLUETILE
                        || category == DraftCategory.REDTILE
                        || category == DraftCategory.DRAFTORDER);
    }

    @Override
    public int getItemLimitForCategory(DraftCategory category) {
        if (isFowExcludedCategory(category)) return 0;
        return switch (category) {
            case ABILITY, BLUETILE -> 3;
            case TECH, REDTILE, STARTINGFLEET -> 2;
            case STARTINGTECH, HOMESYSTEM, PN -> 2;
            case COMMODITIES, FLAGSHIP, MECH -> 2;
            case HERO, COMMANDER, AGENT, BREAKTHROUGH, MONUMENT -> 2;
            case DRAFTORDER -> 1;
            case FACTION, UNIT, PLOT, MAHACTKING -> 0;
        };
    }

    @Override
    public int getKeptItemLimitForCategory(DraftCategory category) {
        if (isFowExcludedCategory(category)) return 0;
        return switch (category) {
            case ABILITY, BLUETILE -> 3;
            case TECH, REDTILE -> 2;
            case STARTINGTECH, HOMESYSTEM, PN -> 1;
            case COMMODITIES, FLAGSHIP, MECH -> 1;
            case HERO, COMMANDER, AGENT, BREAKTHROUGH -> 1;
            case MONUMENT -> getConfiguredMonumentLimit();
            case DRAFTORDER, STARTINGFLEET -> 1;
            case FACTION, UNIT, PLOT, MAHACTKING -> 0;
        };
    }

    protected int getConfiguredMonumentLimit() {
        return getConfiguredLimit(DraftCategory.MONUMENT, 2);
    }

    protected int getConfiguredLimit(DraftCategory category, int defaultLimit) {
        String configuredLimit = getOwner().getStoredValue("frankenLimit" + category);
        return configuredLimit.isEmpty() ? defaultLimit : Integer.parseInt(configuredLimit);
    }

    public static int getItemLimitForCategory(DraftCategory category, Game game) {
        if (game == null || (category == DraftCategory.MONUMENT && !game.isMonumentsMode())) {
            return 0;
        }
        BagDraft activeDraft = game.getActiveBagDraft();
        int baseLimit = activeDraft == null ? 0 : activeDraft.getItemLimitForCategory(category);
        if (baseLimit > 0 && !game.getStoredValue("frankenLimit" + category).isEmpty()) {
            return Integer.parseInt(game.getStoredValue("frankenLimit" + category));
        }
        return baseLimit;
    }

    @Override
    public String getSaveString() {
        return "franken";
    }

    private static final List<String> EXCLUDED_FACTIONS = List.of(
            "lazax",
            "admins",
            "franken",
            "keleresm",
            "keleresx",
            "miltymod",
            "qulane",
            "neutral",
            "obsidian",
            "stoneborn",
            "morpha");

    public static List<FactionModel> getDraftableFactionsForGame(Game game) {
        List<FactionModel> factionSet = getAllFrankenLegalFactions(game);
        String[] results = PatternHelper.FIN_SEPERATOR_PATTERN.split(game.getStoredValue("bannedFactions"));
        if (!game.isDiscordantStarsMode()) {
            factionSet.removeIf(factionModel ->
                    factionModel.getSource().isDs() && !factionModel.getSource().isTe());
        }
        if (!game.isBlueReverieMode()) {
            factionSet.removeIf(factionModel ->
                    factionModel.getSource().isBr() && !factionModel.getSource().isTe());
        }
        factionSet.removeIf(factionModel -> contains(results, factionModel.getAlias()));

        return factionSet;
    }

    private static boolean contains(String[] array, String target) {
        for (String str : array) {
            if (str.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    public static List<FactionModel> getAllFrankenLegalFactions(@Nullable Game game) {
        List<FactionModel> factionSet = Mapper.getFactionsValues();
        factionSet.removeIf((FactionModel model) -> {
            if (EXCLUDED_FACTIONS.contains(model.getAlias())) {
                return true;
            }
            if ((game == null || game.isThundersEdge()) && model.getSource().isTe()) {
                return false;
            }
            if ((game == null || game.isDiscordantStarsMode())
                    && model.getSource().isDs()) {
                return false;
            }
            if ((game == null || game.isBlueReverieMode()) && model.getSource().isBr()) {
                return false;
            }
            if ((game == null || game.initializeFrankenSettings().isLostLegaciesEnabled())
                    && model.getSource() == ComponentSource.theodisi) {
                return false;
            }
            return !model.getSource().isPok();
        });
        return factionSet;
    }

    @Override
    public List<DraftBag> generateBags(Game game) {
        Map<DraftCategory, List<DraftItem>> allDraftableItems = new HashMap<>();
        List<FactionModel> allDraftableFactions = getDraftableFactionsForGame(game);

        allDraftableItems.put(
                DraftCategory.ABILITY, AbilityDraftItem.buildAllDraftableItems(allDraftableFactions, game));
        allDraftableItems.put(DraftCategory.TECH, TechDraftItem.buildAllDraftableItems(allDraftableFactions, game));
        allDraftableItems.put(
                DraftCategory.BREAKTHROUGH, BreakthroughDraftItem.buildAllDraftableItems(allDraftableFactions, game));
        allDraftableItems.put(DraftCategory.AGENT, AgentDraftItem.buildAllDraftableItems(allDraftableFactions, game));
        allDraftableItems.put(
                DraftCategory.COMMANDER, CommanderDraftItem.buildAllDraftableItems(allDraftableFactions, game));
        allDraftableItems.put(DraftCategory.HERO, HeroDraftItem.buildAllDraftableItems(allDraftableFactions, game));
        allDraftableItems.put(
                DraftCategory.COMMODITIES, CommoditiesDraftItem.buildAllDraftableItems(allDraftableFactions, game));
        allDraftableItems.put(
                DraftCategory.FLAGSHIP, FlagshipDraftItem.buildAllDraftableItems(allDraftableFactions, game));
        allDraftableItems.put(DraftCategory.MECH, MechDraftItem.buildAllDraftableItems(allDraftableFactions, game));
        allDraftableItems.put(
                DraftCategory.HOMESYSTEM, HomeSystemDraftItem.buildAllDraftableItems(allDraftableFactions, game));
        allDraftableItems.put(DraftCategory.PN, PNDraftItem.buildAllDraftableItems(allDraftableFactions, game));
        allDraftableItems.put(
                DraftCategory.STARTINGFLEET, StartingFleetDraftItem.buildAllDraftableItems(allDraftableFactions, game));
        allDraftableItems.put(
                DraftCategory.STARTINGTECH, StartingTechDraftItem.buildAllDraftableItems(allDraftableFactions, game));
        allDraftableItems.put(DraftCategory.UNIT, UnitDraftItem.buildAllDraftableItems(game));
        if (game.isMonumentsMode()) {
            allDraftableItems.put(DraftCategory.MONUMENT, MonumentDraftItem.buildAllDraftableItems(game));
        }
        allDraftableItems.put(DraftCategory.MAHACTKING, MahactKingDraftItem.buildAllDraftableItems(game));
        putSharedDraftableItems(allDraftableItems, game);
        return fillBags(game, allDraftableItems, "franken");
    }

    protected static void putSharedDraftableItems(Map<DraftCategory, List<DraftItem>> allDraftableItems, Game game) {
        allDraftableItems.put(DraftCategory.DRAFTORDER, SpeakerOrderDraftItem.buildAllDraftableItems(game));
        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        draftManager.clear();
        initFrankenDraftTiles(draftManager, game);
        allDraftableItems.put(
                DraftCategory.REDTILE,
                TileDraftItem.buildAllDraftableItems(DraftCategory.REDTILE, draftManager.getRed(), game));
        allDraftableItems.put(
                DraftCategory.BLUETILE,
                TileDraftItem.buildAllDraftableItems(DraftCategory.BLUETILE, draftManager.getBlue(), game));
    }

    private static void initFrankenDraftTiles(MiltyDraftManager draftManager, Game game) {
        List<ComponentSource> sources = new ArrayList<>(List.of(
                ComponentSource.base,
                ComponentSource.codex1,
                ComponentSource.codex2,
                ComponentSource.codex3,
                ComponentSource.codex4,
                ComponentSource.pok));
        if (game.isDiscordantStarsMode()) {
            sources.add(ComponentSource.ds);
        }
        if (game.isUnchartedSpaceStuff()) {
            sources.add(ComponentSource.uncharted_space);
        }
        if (Boolean.parseBoolean(game.getStoredValue(Constants.INCLUDE_ERONOUS_TILES))) {
            sources.add(ComponentSource.eronous);
        }
        if ((!game.isBaseGameMode() && game.getStoredValue("useOldPok").isEmpty()) || game.isTwilightsFallMode()) {
            sources.add(ComponentSource.thunders_edge);
        }
        MiltyDraftHelper.initDraftTiles(draftManager, sources);
        EchoesOfYggdrasilService.addTiles(game, draftManager);
    }

    @Override
    public int getBagSize() {
        return 33;
    }
}
