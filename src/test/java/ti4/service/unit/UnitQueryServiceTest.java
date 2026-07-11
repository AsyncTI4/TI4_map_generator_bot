package ti4.service.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class UnitQueryServiceTest extends BaseTi4Test {

    private Game game;
    private Player player;
    private Tile tile;
    private UnitKey carrier;

    @BeforeEach
    void setUp() {
        game = new Game();
        player = new Player("player", "player", game);
        player.setColor("red");
        player.setFaction("sol");
        game.setPlayers(new LinkedHashMap<>());
        game.getPlayers().put(player.getUserID(), player);

        tile = new Tile("18", "000");
        game.setTile(tile);
        carrier = Units.getUnitKey(UnitType.Carrier, player.getColor());
        tile.addUnit(Constants.SPACE, carrier, 2);
    }

    @Test
    void countsAndFindsPlayerUnitsOnBoard() {
        assertThat(UnitQueryService.countUnitsOnBoard(game, carrier)).isEqualTo(2);
        assertThat(UnitQueryService.countUnitsOnBoard(game, player, UnitType.Carrier))
                .isEqualTo(2);
        assertThat(UnitQueryService.countUnitsInSystem(player, tile, UnitType.Carrier))
                .isEqualTo(2);
        assertThat(UnitQueryService.hasUnitsInSystem(player, tile, UnitType.Carrier))
                .isTrue();
        assertThat(UnitQueryService.getTilesContainingPlayersUnits(game, player, UnitType.Carrier))
                .containsExactly(tile);

        assertThat(UnitQueryService.findUnits(game, player, key -> key.unitType() == UnitType.Carrier))
                .singleElement()
                .satisfies(location -> {
                    assertThat(location.tile()).isSameAs(tile);
                    assertThat(location.unitHolder()).isSameAs(tile.getSpaceUnitHolder());
                    assertThat(location.unitKey()).isEqualTo(carrier);
                    assertThat(location.count()).isEqualTo(2);
                });
    }

    @Test
    void excludesNomboxesUnlessExplicitlyRequested() {
        player.getNomboxTile().addUnit(Constants.SPACE, carrier, 3);

        assertThat(UnitQueryService.countUnitsOnBoard(game, carrier)).isEqualTo(2);
        assertThat(UnitQueryService.countUnits(game, carrier, false)).isEqualTo(2);
        assertThat(UnitQueryService.countUnits(game, carrier, true)).isEqualTo(5);
    }
}
