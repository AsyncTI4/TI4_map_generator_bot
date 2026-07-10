package ti4.service.combat.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.model.FactionModel;
import ti4.model.UnitModel;
import ti4.service.combat.v2.CombatV2RollData.Request;
import ti4.service.player.PlayerColorService;
import ti4.testUtils.BaseTi4Test;

class CombatV2UnitServiceTest extends BaseTi4Test {

    @Test
    void antiFighterBarrageScansAllHoldersAndKeepsLocations() {
        Harness harness = new Harness();
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.add(tile, Constants.SPACE, sol, UnitType.Destroyer, 1);
        harness.add(tile, Constants.SPACE, sol, UnitType.Cruiser, 1);

        Map<Pair<UnitModel, UnitHolder>, Integer> units = CombatV2UnitService.selectAntiFighterBarrage(
                        harness.request(sol, tile, Constants.SPACE), tile.getSpaceUnitHolder())
                .units();

        assertEquals(1, units.size());
        Pair<UnitModel, UnitHolder> destroyer = units.keySet().iterator().next();
        assertEquals(UnitType.Destroyer, destroyer.getLeft().getUnitType());
        assertSame(tile.getSpaceUnitHolder(), destroyer.getRight());
        assertEquals(1, units.get(destroyer));
    }

    @Test
    void bombardmentKeepsOnlyBombardmentUnitsAndHomesThemInSpace() {
        Harness harness = new Harness();
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.add(tile, Constants.SPACE, sol, UnitType.Dreadnought, 1);
        harness.add(tile, Constants.SPACE, sol, UnitType.Cruiser, 1);

        Map<Pair<UnitModel, UnitHolder>, Integer> units =
                CombatV2UnitService.getUnitsInBombardment(harness.request(sol, tile, Constants.SPACE));

        assertEquals(1, units.size());
        Pair<UnitModel, UnitHolder> dreadnought = units.keySet().iterator().next();
        assertEquals(UnitType.Dreadnought, dreadnought.getLeft().getUnitType());
        assertSame(tile.getSpaceUnitHolder(), dreadnought.getRight());
    }

    @Test
    void combatRoundSelectionSeparatesGroundAndSpaceUnits() {
        Harness harness = new Harness();
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        String planet = tile.getPlanetUnitHolders().getFirst().getName();
        harness.add(tile, Constants.SPACE, sol, UnitType.Cruiser, 1);
        harness.add(tile, planet, sol, UnitType.Infantry, 2);
        harness.add(tile, planet, sol, UnitType.Pds, 1);

        Map<Pair<UnitModel, UnitHolder>, Integer> space = CombatV2UnitService.selectCombatRound(
                        harness.request(sol, tile, Constants.SPACE), tile.getSpaceUnitHolder())
                .units();
        Map<Pair<UnitModel, UnitHolder>, Integer> ground = CombatV2UnitService.selectCombatRound(
                        harness.request(sol, tile, planet),
                        tile.getUnitHolders().get(planet))
                .units();

        assertTrue(hasUnit(space, UnitType.Cruiser));
        assertFalse(hasUnit(space, UnitType.Infantry));
        assertTrue(hasUnit(ground, UnitType.Infantry));
        assertFalse(hasUnit(ground, UnitType.Pds));
    }

    @Test
    void opponentScanReturnsTheEnemyOnlyOnceAcrossRepeatedHolders() {
        Harness harness = new Harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        Tile tile = harness.tile("19");
        harness.add(tile, Constants.SPACE, sol, UnitType.Cruiser, 1);
        harness.add(tile, Constants.SPACE, mentak, UnitType.Cruiser, 1);
        UnitHolder space = tile.getSpaceUnitHolder();

        Player opponent =
                CombatV2UnitService.getOpponent(harness.request(sol, tile, Constants.SPACE), List.of(space, space));

        assertSame(mentak, opponent);
    }

    @Test
    void spaceCannonOffenseFindsCannonsAcrossPlanetHolders() {
        Harness harness = new Harness();
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        String planet = tile.getPlanetUnitHolders().getFirst().getName();
        harness.add(tile, planet, sol, UnitType.Pds, 1);

        CombatV2UnitService.UnitSelection selection =
                CombatV2UnitService.selectSpaceCannonOffense(harness.request(sol, tile, Constants.SPACE));

        assertTrue(hasUnit(selection.units(), UnitType.Pds));
        assertTrue(selection.notices().isEmpty());
    }

    @Test
    void unresolvedUnitsBecomeNoticesInsteadOfCrashingTheScan() {
        Harness harness = new Harness();
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        UnitModel pds = sol.getPriorityUnitByAsyncID("pd", null);
        sol.getUnitsOwned().remove(pds.getId());
        String planet = tile.getPlanetUnitHolders().getFirst().getName();
        harness.add(tile, planet, sol, UnitType.Pds, 1);

        CombatV2UnitService.UnitSelection selection =
                CombatV2UnitService.selectSpaceCannonOffense(harness.request(sol, tile, Constants.SPACE));

        assertFalse(hasUnit(selection.units(), UnitType.Pds));
        assertEquals(1, selection.notices().size());
        assertTrue(selection.notices().getFirst().contains("Unowned units"));
    }

    @Test
    void limitedAdjacentSpaceCannonSelectionIsDeterministic() {
        Harness harness = new Harness();
        Player mirveda = harness.player("mirveda");
        Tile target = harness.tile("18");
        List<String> positions = PositionMapper.getAdjacentTilePositions(target.getPosition()).stream()
                .filter(position -> !position.equals(target.getPosition()))
                .limit(2)
                .toList();
        assertEquals(2, positions.size());

        Tile first = harness.tileAt("19", positions.get(0));
        Tile second = harness.tileAt("20", positions.get(1));
        String firstPlanet = first.getPlanetUnitHolders().getFirst().getName();
        String secondPlanet = second.getPlanetUnitHolders().getFirst().getName();
        harness.add(first, firstPlanet, mirveda, UnitType.Pds, 1);
        harness.add(second, secondPlanet, mirveda, UnitType.Pds, 1);

        CombatV2UnitService.UnitSelection selection =
                CombatV2UnitService.selectSpaceCannonOffense(harness.request(mirveda, target, Constants.SPACE));

        assertEquals(1, selection.units().size());
        String selectedHolder =
                selection.units().keySet().iterator().next().getRight().getName();
        assertEquals(firstPlanet.compareTo(secondPlanet) < 0 ? firstPlanet : secondPlanet, selectedHolder);
    }

    @Test
    void nekroFlagshipPullsGroundForcesIntoSpaceCombat() {
        Harness harness = new Harness();
        Player nekro = harness.player("nekro");
        Tile tile = harness.tile("19");
        String planet = tile.getPlanetUnitHolders().getFirst().getName();
        harness.add(tile, Constants.SPACE, nekro, UnitType.Flagship, 1);
        harness.add(tile, planet, nekro, UnitType.Infantry, 2);

        CombatV2UnitService.UnitSelection selection = CombatV2UnitService.selectCombatRound(
                harness.request(nekro, tile, Constants.SPACE), tile.getSpaceUnitHolder());

        assertTrue(hasUnit(selection.units(), UnitType.Flagship));
        assertTrue(hasUnit(selection.units(), UnitType.Infantry));
    }

    @Test
    void purpleMechsJoinSpaceCombatWithoutNestedSelectionLogic() {
        Harness harness = new Harness();
        Player purple = harness.player("purpletf");
        Tile tile = harness.tile("19");
        String planet = tile.getPlanetUnitHolders().getFirst().getName();
        harness.add(tile, Constants.SPACE, purple, UnitType.Cruiser, 1);
        harness.add(tile, planet, purple, UnitType.Mech, 1);

        CombatV2UnitService.UnitSelection selection = CombatV2UnitService.selectCombatRound(
                harness.request(purple, tile, Constants.SPACE), tile.getSpaceUnitHolder());

        assertTrue(hasUnit(selection.units(), UnitType.Cruiser));
        assertTrue(hasUnit(selection.units(), UnitType.Mech));
    }

    @Test
    void starfallCreatesOneVirtualCannonPerEligibleShipUpToItsLimit() {
        Harness harness = new Harness();
        Player kolume = harness.player("kolume");
        Tile tile = harness.tile("19");
        harness.game.setActivePlayerID(kolume.getUserID());
        harness.add(tile, Constants.SPACE, kolume, UnitType.Cruiser, 4);

        CombatV2UnitService.UnitSelection selection =
                CombatV2UnitService.selectSpaceCannonOffense(harness.request(kolume, tile, Constants.SPACE));

        Map.Entry<Pair<UnitModel, UnitHolder>, Integer> starfall = selection.units().entrySet().stream()
                .filter(entry -> "starfallpds".equals(entry.getKey().getLeft().getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(3, starfall.getValue());
    }

    @Test
    void opponentPreferenceSkipsAnActiveAlly() {
        Harness harness = new Harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        Player hacan = harness.player("hacan");
        Tile tile = harness.tile("19");
        harness.add(tile, Constants.SPACE, sol, UnitType.Cruiser, 1);
        harness.add(tile, Constants.SPACE, mentak, UnitType.Cruiser, 1);
        harness.add(tile, Constants.SPACE, hacan, UnitType.Cruiser, 1);
        harness.game.setActivePlayerID(mentak.getUserID());
        sol.addAllianceMember(mentak.getFaction());

        Player opponent = CombatV2UnitService.getOpponent(
                harness.request(sol, tile, Constants.SPACE), List.of(tile.getSpaceUnitHolder()));

        assertSame(hacan, opponent);
    }

    private static boolean hasUnit(Map<Pair<UnitModel, UnitHolder>, Integer> units, UnitType type) {
        return units.keySet().stream().anyMatch(unit -> unit.getLeft().getUnitType() == type);
    }

    private static final class Harness {
        private final Game game = new Game();
        private final GenericInteractionCreateEvent event = mock(GenericInteractionCreateEvent.class);

        private Harness() {
            game.newGameSetup();
            game.setName("Combat V2 Unit Service Test");
            game.setCcNPlasticLimit(false);
            when(event.getMessageChannel()).thenReturn(mock(MessageChannel.class));
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
            if (model.getStartingTech() != null) player.setTechs(model.getStartingTech());
            for (String tech : player.getFactionTechs()) player.addTech(tech);
            for (Leader leader : player.getLeaders()) leader.setLocked(false);
            return player;
        }

        private Tile tile(String tileId) {
            Tile tile = new Tile(tileId, nextPosition());
            game.setTile(tile);
            game.setActiveSystem(tile.getPosition());
            return tile;
        }

        private Tile tileAt(String tileId, String position) {
            Tile tile = new Tile(tileId, position);
            game.setTile(tile);
            return tile;
        }

        private Request request(Player player, Tile tile, String holder) {
            return new Request(player, game, event, tile, holder);
        }

        private void add(Tile tile, String holder, Player player, UnitType type, int count) {
            tile.addUnit(holder, Units.getUnitKey(type, player.getColorID()), count);
        }

        private String nextPosition() {
            for (String position : PositionMapper.getTilePositions()) {
                if (game.getTileByPosition(position) == null) return position;
            }
            return null;
        }
    }
}
