package ti4.contest.replay.core.renderers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.model.FactionModel;
import ti4.service.player.PlayerColorService;
import ti4.testUtils.BaseTi4Test;

class CombatReplayTileRendererTest extends BaseTi4Test {

    @Test
    void rendersUnitOnlySnapshotsAgainstInitialCombatContext() {
        Harness harness = new Harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        Player jolnar = harness.player("jolnar");
        Tile mecatol = harness.tile("112");

        harness.add(mecatol, sol, UnitType.Carrier, 1);
        harness.add(mecatol, sol, UnitType.Dreadnought, 1);
        harness.add(mecatol, mentak, UnitType.Cruiser, 1);
        harness.add(mecatol, "mrte", mentak, UnitType.Infantry, 2);

        String initialSnapshot = CombatReplayTileRenderer.captureInitialSnapshot(harness.game, mecatol.getPosition());

        UnitKey solDreadnought = Units.getUnitKey(UnitType.Dreadnought, sol.getColorID());
        UnitKey mentakCruiser = Units.getUnitKey(UnitType.Cruiser, mentak.getColorID());
        UnitKey mentakInfantry = Units.getUnitKey(UnitType.Infantry, mentak.getColorID());
        mecatol.addUnitDamage(Constants.SPACE, solDreadnought, 1);
        mecatol.getSpaceUnitHolder().removeUnit(mentakCruiser, 1);
        mecatol.getUnitHolderFromPlanet("mrte").removeUnit(mentakInfantry, 1);

        String unitOnlySnapshot =
                CombatReplayTileRenderer.captureUnitStateSnapshot(harness.game, mecatol.getPosition());

        assertFalse(unitOnlySnapshot.contains("\"context\""));
        assertFalse(unitOnlySnapshot.contains("\"tileTemplate\""));
        assertFalse(unitOnlySnapshot.contains("\"players\""));

        Game renderedGame = CombatReplayTileRenderer.render(initialSnapshot, unitOnlySnapshot);
        Tile renderedTile = renderedGame.getTileByPosition(mecatol.getPosition());

        assertNotNull(renderedGame.getPlayerFromColorOrFaction(sol.getFaction()));
        assertNotNull(renderedGame.getPlayerFromColorOrFaction(mentak.getFaction()));
        assertNull(renderedGame.getPlayerFromColorOrFaction(jolnar.getFaction()));
        assertEquals(
                List.of(0, 1, 0, 0),
                renderedTile.getSpaceUnitHolder().getUnitsByState().get(solDreadnought));
        assertNull(renderedTile.getSpaceUnitHolder().getUnitsByState().get(mentakCruiser));
        assertEquals(
                List.of(1, 0, 0, 0),
                renderedTile.getUnitHolderFromPlanet("mrte").getUnitsByState().get(mentakInfantry));
    }

    @Test
    void initialSnapshotRendersItself() {
        Harness harness = new Harness();
        Player argent = harness.player("argent");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.add(tile, argent, UnitType.Destroyer, 2);
        harness.add(tile, sol, UnitType.Fighter, 3);

        String initialSnapshot = CombatReplayTileRenderer.captureInitialSnapshot(harness.game, tile.getPosition());

        Game renderedGame = CombatReplayTileRenderer.render(initialSnapshot, initialSnapshot);

        assertEquals(
                2,
                renderedGame
                        .getTileByPosition(tile.getPosition())
                        .getSpaceUnitHolder()
                        .getUnitsByState()
                        .get(Units.getUnitKey(UnitType.Destroyer, argent.getColorID()))
                        .getFirst());
        assertEquals(
                3,
                renderedGame
                        .getTileByPosition(tile.getPosition())
                        .getSpaceUnitHolder()
                        .getUnitsByState()
                        .get(Units.getUnitKey(UnitType.Fighter, sol.getColorID()))
                        .getFirst());
    }

    @Test
    void preservesInitialRenderContextWhenApplyingUnitOnlySnapshots() {
        Harness harness = new Harness();
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.add(tile, sol, UnitType.Carrier, 1);
        harness.game.setTwilightsFallMode(true);
        harness.game.setWildWildGalaxyMode(true);
        harness.game.setFowMode(true);
        harness.game.setHexBorderStyle("solid");
        harness.game.setShowGears(true);
        harness.game.setShowUnitTags(true);
        harness.game.getLaws().put("articles_war", 1);
        harness.game.getLawsInfo().put("articles_war", "for");

        String initialSnapshot = CombatReplayTileRenderer.captureInitialSnapshot(harness.game, tile.getPosition());
        String unitOnlySnapshot = CombatReplayTileRenderer.captureUnitStateSnapshot(harness.game, tile.getPosition());

        Game renderedGame = CombatReplayTileRenderer.render(initialSnapshot, unitOnlySnapshot);

        assertEquals("solid", renderedGame.getHexBorderStyle());
        assertEquals(1, renderedGame.getLaws().get("articles_war"));
        assertEquals("for", renderedGame.getLawsInfo().get("articles_war"));
        assertEquals(harness.game.isTwilightsFallMode(), renderedGame.isTwilightsFallMode());
        assertEquals(harness.game.isWildWildGalaxyMode(), renderedGame.isWildWildGalaxyMode());
        assertEquals(harness.game.isFowMode(), renderedGame.isFowMode());
        assertEquals(harness.game.isShowGears(), renderedGame.isShowGears());
        assertEquals(harness.game.isShowUnitTags(), renderedGame.isShowUnitTags());
    }

    private static final class Harness {
        private final Game game = new Game();

        private Harness() {
            game.newGameSetup();
            game.setName("Combat Replay Tile Payload Renderer");
        }

        private Player player(String faction) {
            FactionModel model = Mapper.getFaction(faction);
            Player player = game.addPlayer(model.getAlias(), model.getFactionName());
            player.setFaction(game, faction);
            player.setFactionEmoji("<" + faction + ">");
            player.setColor(PlayerColorService.getPreferredColor(player));
            player.setUnitsOwned(new HashSet<>(model.getUnits()));
            player.addBreakthrough(model.getBreakthrough());
            player.setBreakthroughUnlocked(model.getBreakthrough(), true);
            player.setCommoditiesBase(model.getCommodities());
            player.setPlanets(model.getHomePlanets());
            player.setFactionTechs(model.getFactionTech());
            if (model.getStartingTech() != null) {
                player.setTechs(model.getStartingTech());
            }
            for (String tech : player.getFactionTechs()) {
                player.addTech(tech);
            }
            for (Leader leader : player.getLeaders()) {
                leader.setLocked(false);
            }
            return player;
        }

        private Tile tile(String tileId) {
            Tile tile = new Tile(tileId, getNextPosition());
            game.setTile(tile);
            game.setActiveSystem(tile.getPosition());
            return tile;
        }

        private void add(Tile tile, Player player, UnitType unitType, int count) {
            add(tile, Constants.SPACE, player, unitType, count);
        }

        private void add(Tile tile, String holderName, Player player, UnitType unitType, int count) {
            tile.addUnit(holderName, Units.getUnitKey(unitType, player.getColorID()), count);
        }

        private String getNextPosition() {
            for (String position : PositionMapper.getTilePositions()) {
                if (game.getTileByPosition(position) == null) return position;
            }
            return null;
        }
    }
}
