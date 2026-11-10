package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.FactionModel;
import ti4.model.Mapper;
import ti4.service.player.PlayerColorService;
import ti4.testUtils.BaseTi4Test;

/**
 * Qhet Battlecruiser II may move through systems with other players' ships, but anomalies still block it. Board:
 * Mecatol plus ring 1, so 101 to 104 is 2 through 000 or 3 around the ring.
 */
class CheckDistanceHelperTest extends BaseTi4Test {

    private static final String PLAIN_SYSTEM = "19";
    private static final String SUPERNOVA = "43";

    private Game game;
    private Player mover;
    private Player blocker;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("check-distance-test");
        game.setTile(new Tile(PLAIN_SYSTEM, "000"));
        for (int index = 1; index <= 6; index++) {
            game.setTile(new Tile(PLAIN_SYSTEM, "10" + index));
        }
        mover = addPlayer("hacan");
        blocker = addPlayer("letnev");
    }

    @Test
    void otherPlayersShipsBlockUnlessIgnored() {
        game.getTileByPosition("000").addUnit("space", Units.getUnitKey(UnitType.Destroyer, blocker.getColorID()), 1);

        assertThat(CheckDistanceHelper.getDistanceBetweenTwoTiles(game, mover, "101", "104", true))
                .isEqualTo(3);
        assertThat(CheckDistanceHelper.getDistanceIgnoringOtherShips(game, mover, "101", "104", true))
                .isEqualTo(2);
    }

    @Test
    void ignoringShipsStillRespectsAnomalies() {
        // The Qhet hint used to pass no player, which also switched off anomaly blocking.
        game.setTile(new Tile(SUPERNOVA, "000"));

        assertThat(CheckDistanceHelper.getDistanceIgnoringOtherShips(game, mover, "101", "104", true))
                .isEqualTo(3);
        assertThat(CheckDistanceHelper.getDistanceBetweenTwoTiles(game, null, "101", "104", true))
                .isEqualTo(2);
    }

    private Player addPlayer(String faction) {
        FactionModel model = Mapper.getFaction(faction);
        Player player = game.addPlayer(model.getAlias(), model.getFactionName());
        player.setFaction(game, faction);
        player.setColor(PlayerColorService.getPreferredColor(player));
        player.setUnitsOwned(new HashSet<>(model.getUnits()));
        return player;
    }
}
