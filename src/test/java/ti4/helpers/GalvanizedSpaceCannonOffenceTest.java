package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.FactionModel;
import ti4.model.Mapper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.player.PlayerColorService;
import ti4.testUtils.BaseTi4Test;

class GalvanizedSpaceCannonOffenceTest extends BaseTi4Test {

    private static final String GALVANIZE_MOD = "roll_1_for_galvanize_spacecannon_offense";
    private static final String ACTIVE_POSITION = "101";
    private static final String ADJACENT_POSITION = "102";

    private Game game;
    private Player xxcha;
    private Player letnev;
    private Tile activeSystem;
    private Tile adjacentSystem;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("Galvanized Space Cannon Offence");
        game.setupNeutralPlayer("gray");
        xxcha = addPlayer("xxcha");
        letnev = addPlayer("letnev");

        activeSystem = new Tile("37", ACTIVE_POSITION);
        adjacentSystem = new Tile("38", ADJACENT_POSITION);
        game.setTile(activeSystem);
        game.setTile(adjacentSystem);
        activeSystem.addUnit("space", Units.getUnitKey(UnitType.Destroyer, letnev.getColorID()), 1);
        game.setActivePlayerID(letnev.getUserID());
        game.setActiveSystem(ACTIVE_POSITION);
    }

    @Test
    void galvanizedFlagshipInAdjacentSystemGivesOneExtraDie() {
        placeFlagship(adjacentSystem, true);

        assertThat(galvanizeDiceForFlagship(adjacentSystem)).isEqualTo(1);
    }

    @Test
    void galvanizedFlagshipInTheActiveSystemGivesOneExtraDie() {
        placeFlagship(activeSystem, true);

        assertThat(galvanizeDiceForFlagship(activeSystem)).isEqualTo(1);
    }

    @Test
    void flagshipThatIsNotGalvanizedGivesNoExtraDie() {
        placeFlagship(adjacentSystem, false);

        assertThat(galvanizeDiceForFlagship(adjacentSystem)).isZero();
    }

    private void placeFlagship(Tile tile, boolean galvanized) {
        var flagship = Units.getUnitKey(UnitType.Flagship, xxcha.getColorID());
        tile.addUnit("space", flagship, 1);
        if (galvanized) {
            tile.addGalvanize("space", flagship, 1);
        }
    }

    private int galvanizeDiceForFlagship(Tile flagshipTile) {
        Map<UnitModel, Integer> units = CombatRollService.getUnitsInCombat(
                activeSystem,
                activeSystem.getUnitHolders().get("space"),
                xxcha,
                null,
                CombatRollType.SpaceCannonOffence,
                game);
        Map<UnitModel, Integer> opponentUnits = CombatRollService.getUnitsInCombat(
                activeSystem,
                activeSystem.getUnitHolders().get("space"),
                letnev,
                null,
                CombatRollType.SpaceCannonOffence,
                game);
        List<NamedCombatModifierModel> galvanize = CombatModHelper.getModifiers(
                        xxcha,
                        letnev,
                        units,
                        opponentUnits,
                        activeSystem.getTileModel(),
                        game,
                        CombatRollType.SpaceCannonOffence,
                        Constants.COMBAT_EXTRA_ROLLS)
                .stream()
                .filter(mod -> GALVANIZE_MOD.equals(mod.getModifier().getAlias()))
                .toList();
        UnitModel flagship = units.keySet().stream()
                .filter(unit -> unit.getUnitType() == UnitType.Flagship)
                .findFirst()
                .orElseThrow();
        if (galvanize.isEmpty()) return 0;
        return CombatModHelper.getCombinedModifierForUnit(
                flagship,
                units.get(flagship),
                galvanize,
                xxcha,
                letnev,
                game,
                new ArrayList<>(units.keySet()),
                CombatRollType.SpaceCannonOffence,
                activeSystem,
                flagshipTile.getUnitHolders().get("space"));
    }

    private Player addPlayer(String faction) {
        FactionModel model = Mapper.getFaction(faction);
        Player player = game.addPlayer(model.getAlias(), model.getFactionName());
        player.setFaction(game, faction);
        player.setFactionEmoji("<" + faction + ">");
        player.setColor(PlayerColorService.getPreferredColor(player));
        player.setUnitsOwned(new HashSet<>(model.getUnits()));
        player.setPlanets(model.getHomePlanets());
        return player;
    }
}
