package ti4.service.draft;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import ti4.helpers.settingsFramework.menus.DraftSystemSettings;
import ti4.helpers.settingsFramework.menus.GameSettings;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.helpers.settingsFramework.menus.MiltySliceDraftableSettings;
import ti4.helpers.settingsFramework.menus.PlayerFactionSettings;
import ti4.helpers.settingsFramework.menus.SliceGenerationSettings;
import ti4.helpers.settingsFramework.menus.SourceSettings;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.MapTemplateModel;
import ti4.model.Source;
import ti4.service.milty.MiltyDraftSlice;

/**
 * This DraftSpec represents the conversion of draft settings -> draft.
 * To ensure the new system is compatible with the previous, this should
 * basically be a copy of MiltyDraftSpec for now.
 * TODO: This shouldn't be needed. In the future, settings should be
 * directly applicable to each desired draftable/orchestrator.
 */
@Data
public class DraftSpec {
    public Game game;
    public List<String> playerIDs, bannedFactions, priorityFactions, playerDraftOrder;
    public MapTemplateModel template;
    public List<Source.ComponentSource> tileSources, factionSources;
    public Integer numSlices, numFactions;

    // slice generation settings
    public Boolean anomaliesCanTouch = false, extraWHs = true;
    public Double minRes = 2.0, minInf = 3.0;
    public Integer minTot = 9, maxTot = 13;
    public Integer minLegend = 1, maxLegend = 2;

    // other
    public List<MiltyDraftSlice> presetSlices;

    public DraftSpec(Game game) {
        this.game = game;
        playerIDs = new ArrayList<>(game.getPlayerIDs());
        bannedFactions = new ArrayList<>();
        priorityFactions = new ArrayList<>();

        // TODO: These should be derived from game settings.
        tileSources = new ArrayList<>();
        tileSources.add(Source.ComponentSource.base);
        tileSources.add(Source.ComponentSource.pok);
        tileSources.add(Source.ComponentSource.codex1);
        tileSources.add(Source.ComponentSource.codex2);
        tileSources.add(Source.ComponentSource.codex3);
        tileSources.add(Source.ComponentSource.codex4);
        factionSources = new ArrayList<>(tileSources);
    }

    public static DraftSpec CreateFromMiltySettings(MiltySettings settings) {
        Game game = settings.getGame();
        DraftSpec specs = new DraftSpec(game);

        // Load Game Specifications
        GameSettings gameSettings = settings.getGameSettings();
        MapTemplateModel template = gameSettings.getMapTemplate().getValue();
        if (template == null) {
            template = Mapper.getDefaultMapTemplateForPlayerCount(specs.playerIDs.size());
            game.setMapTemplateID(template.getID());
        }
        specs.setTemplate(template);

        // Load Slice Generation Specifications
        SliceGenerationSettings sliceSettings = settings.getSliceSettings();
        specs.numFactions = sliceSettings.getNumFactions().getVal();
        specs.numSlices = sliceSettings.getNumSlices().getVal();
        specs.anomaliesCanTouch = false;
        specs.extraWHs = sliceSettings.getExtraWorms().isVal();
        specs.minLegend = sliceSettings.getNumLegends().getValLow();
        specs.maxLegend = sliceSettings.getNumLegends().getValHigh();
        specs.minTot = sliceSettings.getTotalValue().getValLow();
        specs.maxTot = sliceSettings.getTotalValue().getValHigh();

        // Load Player & Faction Ban Specifications
        PlayerFactionSettings pfSettings = settings.getPlayerSettings();
        specs.bannedFactions.addAll(pfSettings.getBanFactions().getKeys());
        if (game.isThundersEdge()) {
            List<String> newKeys = new ArrayList<>();
            newKeys.addAll(List.of("titans", "keleresm", "cabal", "argent"));
            specs.bannedFactions.addAll(newKeys);
            specs.numFactions = Math.min(25 - newKeys.size(), specs.numFactions);
        }

        specs.priorityFactions.addAll(pfSettings.getPriFactions().getKeys());
        specs.priorityFactions.removeAll(specs.bannedFactions);
        specs.setPlayerIDs(new ArrayList<>(pfSettings.getGamePlayers().getKeys()));
        if (pfSettings.getPresetDraftOrder().isVal()) {
            specs.playerDraftOrder = new ArrayList<>(game.getPlayers().keySet());
        }

        // Load Sources Specifications
        // TODO: These should be derived from game settings.
        SourceSettings sources = settings.getSourceSettings();
        specs.setTileSources(sources.getTileSources());
        specs.setFactionSources(sources.getFactionSources());

        if (sliceSettings.getParsedSlices() != null) {
            specs.presetSlices = sliceSettings.getParsedSlices();
        }

        return specs;
    }

    public static DraftSpec SliceSpecsFromDraftSystemSettings(DraftSystemSettings settings) {
        Game game = settings.getGame();
        DraftSpec specs = new DraftSpec(game);

        // Load Game Specifications
        MapTemplateModel template = settings.getSliceSettings().getMapTemplate().getValue();
        if (template == null) {
            template = Mapper.getDefaultMapTemplateForPlayerCount(specs.playerIDs.size());
            game.setMapTemplateID(template.getID());
        }
        specs.setTemplate(template);

        // Load Slice Generation Specifications
        MiltySliceDraftableSettings sliceSettings = settings.getSliceSettings().getMiltySettings();
        specs.numSlices = settings.getSliceSettings().getNumSlices().getVal();
        specs.anomaliesCanTouch = false;
        specs.extraWHs = sliceSettings.getExtraWorms().isVal();
        specs.minLegend = sliceSettings.getNumLegends().getValLow();
        specs.maxLegend = sliceSettings.getNumLegends().getValHigh();
        specs.minTot = sliceSettings.getTotalValue().getValLow();
        specs.maxTot = sliceSettings.getTotalValue().getValHigh();
        specs.setPlayerIDs(new ArrayList<>(settings.getPlayerUserIds()));

        // Load Sources Specifications
        SourceSettings sources = settings.getSourceSettings();
        specs.setTileSources(sources.getTileSources());
        specs.setFactionSources(sources.getFactionSources());

        if (settings.getSliceSettings().getParsedSlices() != null) {
            specs.presetSlices = settings.getSliceSettings().getParsedSlices();
        }

        return specs;
    }
}
