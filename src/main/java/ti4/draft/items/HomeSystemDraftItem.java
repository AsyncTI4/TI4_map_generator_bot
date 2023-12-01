package ti4.draft.items;

import java.util.List;
import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.helpers.Emojis;
import ti4.model.FactionModel;
import ti4.model.PlanetModel;
import ti4.model.TileModel;

public class HomeSystemDraftItem extends DraftItem {
    public HomeSystemDraftItem(String itemId) {
        super(Category.HOMESYSTEM, itemId);
    }

    @Override
    public String getShortDescription() {
        return Mapper.getFaction(ItemId).getFactionName() + " Home System";
    }

    @Override
    public String getLongDescriptionImpl() {
        if ("ghost".equals(ItemId)) {
            return "Delta Wormhole / Delta Wormhole, Creuss (4/2)";
        }
        FactionModel faction = Mapper.getFaction(ItemId);
        TileModel tile = TileHelper.getTile(faction.getHomeSystem());
        StringBuilder sb = new StringBuilder();
        List<String> planetIds = tile.getPlanetIds();
        for (int i = 0; i < planetIds.size() - 1; i++) {
            buildPlanetString(Mapper.getPlanet(planetIds.get(i)), sb);
            sb.append(", ");
        }

        buildPlanetString(Mapper.getPlanet(planetIds.get(planetIds.size() - 1)), sb);

        return sb.toString();
    }

    private void buildPlanetString(PlanetModel planet, StringBuilder sb) {
        sb.append(planet.getName());
        sb.append(" (");
        sb.append(planet.getResources()).append("/").append(planet.getInfluence());
        sb.append(") ");
    }

    @Override
    public String getItemEmoji() {
        return Emojis.getFactionIconFromDiscord(ItemId);
    }
}
