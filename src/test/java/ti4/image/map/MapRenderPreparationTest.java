package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.service.transaction.DebtPoolIconService;
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
        return resolve(DisplayType.stats);
    }

    private RenderContext resolve(DisplayType type) {
        return MapRenderer.resolve(new MapRenderRequest(game, type, null, null));
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

    @Test
    void renderingSkipsAPlanetThatIsNotInTheGameAndLeavesThePlayerAlone() {
        holder.addPlanet("nosuchplanet");

        MapRenderer.paint(resolve(), RenderTimings.disabled());

        assertTrue(holder.getPlanets().contains("nosuchplanet"));
    }

    @Test
    void resolvingARenderGivesADebtPoolWithoutAnIconItsDefaultIcon() {
        Player creditor = game.addPlayer("222", "hacan");
        creditor.setFaction(game, "hacan");
        creditor.setColor("yellow");
        creditor.setUnitsOwned(new HashSet<>(Mapper.getFaction("hacan").getUnits()));
        holder.addDebtTokens(creditor.getColor(), 2, "favours");

        resolve();

        assertEquals(DebtPoolIconService.defaultIcon(game, "favours"), game.getDebtPoolIcon("favours"));
    }

    @Test
    void resolvingARenderNormalizesAFactionEmojiThatIsNotAnEmoji() {
        holder.setFactionEmoji("not an emoji");

        resolve();

        assertNull(holder.getFactionEmojiRaw());
    }

    @Test
    void resolvingABoardRenderWithUnitsOnTheBoardCreatesTheNeutralPlayerBecauseTheLayoutCountsIt() {
        game.getTileByPosition("101")
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, holder.getColorID()), 1);

        resolve(DisplayType.map);

        assertTrue(game.findNeutral().isPresent());
    }

    @Test
    void resolvingABoardRenderWithoutUnitsLeavesTheNeutralPlayerOut() {
        resolve(DisplayType.map);

        assertTrue(game.findNeutral().isEmpty());
    }

    @Test
    void resolvingAStatsRenderLeavesTheNeutralPlayerOut() {
        game.getTileByPosition("101")
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, holder.getColorID()), 1);

        resolve(DisplayType.stats);

        assertTrue(game.findNeutral().isEmpty());
    }

    @Test
    void paintingNeverCreatesTheNeutralPlayer() {
        game.getTileByPosition("101")
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, holder.getColorID()), 1);
        RenderContext context = resolve(DisplayType.map);
        game.getPlayers().remove(game.findNeutral().orElseThrow().getUserID());

        MapRenderer.paint(context, RenderTimings.disabled());

        assertTrue(game.findNeutral().isEmpty());
    }
}
