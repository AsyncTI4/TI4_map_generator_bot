package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class MapRenderPreparationTest extends BaseTi4Test {

    private Game game;
    private Player holder;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("map-render-preparation-test");
        MapFrame.positionsWithin("000", 1).forEach(position -> game.setTile(new Tile("19", position)));
        holder = game.addPlayer("111", "sol");
        holder.setFaction(game, "sol");
        holder.setColor("blue");
        holder.setUnitsOwned(new HashSet<>(Mapper.getFaction("sol").getUnits()));
    }

    private RenderContext resolve() {
        return MapRenderer.resolve(new MapRenderRequest(game, DisplayType.stats, null, null));
    }

    @Test
    void resolvingARenderRefreshesTheTriadFromTheFragmentsButPaintingDoesNot() {
        holder.addPlanet("triad");
        holder.setHrf(1);
        holder.setIrf(1);
        int base = game.getPlanetsInfo().get("triad").getResources();

        RenderContext context = resolve();
        assertEquals(base + 2, game.getPlanetsInfo().get("triad").getResources());

        holder.setUrf(1);
        MapRenderer.paint(context, RenderTimings.disabled());
        assertEquals(base + 2, game.getPlanetsInfo().get("triad").getResources());

        resolve();
        assertEquals(base + 3, game.getPlanetsInfo().get("triad").getResources());
    }

    @Test
    void resolvingARenderRefreshesTheGroveFromTheCoexistingPlanets() {
        holder.addPlanet("grove");
        int base = game.getPlanetsInfo().get("grove").getInfluence();

        resolve();

        assertEquals(
                base + game.getPlanetsPlayerIsCoexistingOn(holder).size(),
                game.getPlanetsInfo().get("grove").getInfluence());
    }
}
