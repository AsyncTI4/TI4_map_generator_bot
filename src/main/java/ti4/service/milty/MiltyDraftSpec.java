package ti4.service.milty;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import ti4.helpers.settingsFramework.menus.GameSettings;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.helpers.settingsFramework.menus.PlayerFactionSettings;
import ti4.helpers.settingsFramework.menus.SliceGenerationSettings;
import ti4.helpers.settingsFramework.menus.SourceSettings;
import ti4.helpers.thundersedge.TeHelperDemo;
import ti4.map.Game;
import ti4.model.MapTemplateModel;
import ti4.model.Source;

@Data
public class MiltyDraftSpec {
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

    public MiltyDraftSpec(Game game) {
        this.game = game;
        playerIDs = new ArrayList<>(game.getPlayerIDs());
        bannedFactions = new ArrayList<>();
        priorityFactions = new ArrayList<>();

        tileSources = new ArrayList<>();
        tileSources.add(Source.ComponentSource.base);
        tileSources.add(Source.ComponentSource.pok);
        tileSources.add(Source.ComponentSource.codex1);
        tileSources.add(Source.ComponentSource.codex2);
        tileSources.add(Source.ComponentSource.codex3);
        tileSources.add(Source.ComponentSource.codex4);
        factionSources = new ArrayList<>(tileSources);
    }

    public static MiltyDraftSpec fromSettings(MiltySettings settings) {
        Game game = settings.getGame();
        MiltyDraftSpec specs = new MiltyDraftSpec(game);

        // Load Game Specifications
        GameSettings gameSettings = settings.getGameSettings();
        specs.setTemplate(gameSettings.getMapTemplate().getValue());

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
            specs.bannedFactions.addAll(TeHelperDemo.getExcludedFactions());
            specs.numFactions = Math.min(25 - TeHelperDemo.getExcludedFactions().size(), specs.numFactions);
        }
        specs.priorityFactions.addAll(pfSettings.getPriFactions().getKeys());
        specs.setPlayerIDs(new ArrayList<>(pfSettings.getGamePlayers().getKeys()));
        if (pfSettings.getPresetDraftOrder().isVal()) {
            specs.playerDraftOrder = new ArrayList<>(game.getPlayers().keySet());
        }
        specs.priorityFactions.removeAll(specs.bannedFactions);

        // Load Sources Specifications
        SourceSettings sources = settings.getSourceSettings();
        specs.setTileSources(sources.getTileSources());
        specs.setFactionSources(sources.getFactionSources());

        if (sliceSettings.getParsedSlices() != null) {
            specs.presetSlices = sliceSettings.getParsedSlices();
        }

        return specs;
    }
}
