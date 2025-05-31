package ti4.draft.items;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ti4.draft.DraftItem;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.PlanetModel;
import ti4.model.TileModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.TI4Emoji;

public class HomeSystemDraftItem extends DraftItem {
    public HomeSystemDraftItem(String itemId) {
        super(Category.HOMESYSTEM, itemId);
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return Mapper.getFaction(ItemId).getFactionName() + " Home System";
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        if ("ghost".equals(ItemId)) {
            return "Delta Wormhole / Delta Wormhole, Creuss (4/2)";
        }
        FactionModel faction = Mapper.getFaction(ItemId);
        TileModel tile = TileHelper.getTileById(faction.getHomeSystem());
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
        sb.append(" (");
        sb.append(planet.getResources()).append("/").append(planet.getInfluence());
        sb.append(") ");
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return FactionEmojis.getFactionIcon(ItemId);
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = buildAllItems(factions);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.HOMESYSTEM);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            allItems.add(DraftItem.generate(Category.HOMESYSTEM, faction.getAlias()));
        }
        return allItems;
    }
}
