package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.model.FactionModel;
import ti4.model.PlanetModel;
import ti4.model.TileModel;

import java.util.List;

public class HomeSystemDraftItem extends DraftItem {
    public HomeSystemDraftItem(String itemId) {
        super(Category.HOMESYSTEM, itemId);
    }

    @Override
    public String getShortDescription() {
        return Mapper.getFactionRepresentations().get(ItemId) + " Home System";
    }

    @Override
    public String getLongDescription() {
        FactionModel faction = Mapper.getFactionSetup(ItemId);
        TileModel tile = TileHelper.getTile(faction.getHomeSystem());
        StringBuilder sb = new StringBuilder();
        List<String> planetIds = tile.getPlanetIds();
        for (int i = 0; i < planetIds.size()-1; i++) {
            buildPlanetString(Mapper.getPlanet(planetIds.get(i)), sb);
            sb.append(", ");
        }

        buildPlanetString(Mapper.getPlanet(planetIds.get(planetIds.size()-1)), sb);

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
        return Helper.getFactionIconFromDiscord(ItemId);
    }
}
