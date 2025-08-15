package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ti4.draft.DraftItem;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.model.DraftErrataModel;
import ti4.model.PlanetModel;
import ti4.model.PlanetTypeModel;
import ti4.model.TechSpecialtyModel;
import ti4.model.TileModel;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.milty.MiltyDraftManager;
import ti4.service.milty.MiltyDraftTile;

public class BlueTileDraftItem extends DraftItem {
    public BlueTileDraftItem(String itemId) {
        super(Category.BLUETILE, itemId);
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return TileHelper.getTileById(ItemId).getName() + " (" + ItemId + ")";
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        TileModel tile = TileHelper.getTileById(ItemId);
        StringBuilder sb = new StringBuilder();
        List<String> planetIds = tile.getPlanets();
        for (int i = 0; i < planetIds.size() - 1; i++) {
            buildPlanetString(Mapper.getPlanet(planetIds.get(i)), sb);
            sb.append(", ");
        }

        buildPlanetString(Mapper.getPlanet(planetIds.getLast()), sb);

        return sb.toString();
    }

    private void buildPlanetString(PlanetModel planet, StringBuilder sb) {
        sb.append(planet.getName());
        sb.append(planetTypeEmoji(planet.getPlanetType()));
        sb.append(" (");
        sb.append(planet.getResources()).append("/").append(planet.getInfluence());
        if (planet.isLegendary()) {
            sb.append("/").append(MiscEmojis.LegendaryPlanet);
        }
        if (planet.getTechSpecialties() != null) {
            for (var spec : planet.getTechSpecialties()) {
                sb.append("/").append(techSpecEmoji(spec));
            }
        }
        sb.append(") ");
    }

    private String planetTypeEmoji(PlanetTypeModel.PlanetType type) {
        return type.getEmoji();
    }

    private String techSpecEmoji(TechSpecialtyModel.TechSpecialty type) {
        return type.getEmoji();
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return PlanetEmojis.SemLor;
    }

    public static List<DraftItem> buildAllDraftableItems(MiltyDraftManager draftManager) {
        List<DraftItem> allItems = new ArrayList<>();
        for (MiltyDraftTile tile : draftManager.getBlue()) {
            allItems.add(generate(
                    DraftItem.Category.BLUETILE, tile.getTile().getTileID()));
        }
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.BLUETILE);
        return allItems;
    }

    public static List<DraftItem> buildAllDraftableItems(MiltyDraftManager draftManager, Game game) {
        List<DraftItem> allItems = new ArrayList<>();
        String[] results = game.getStoredValue("bannedTiles").split("finSep");
        for (MiltyDraftTile tile : draftManager.getBlue()) {
            if (Arrays.asList(results).contains(tile.getTile().getTileID())) {
                continue;
            }
            allItems.add(generate(
                    DraftItem.Category.BLUETILE, tile.getTile().getTileID()));
        }
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.BLUETILE);
        return allItems;
    }
}
