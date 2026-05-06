package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.testUtils.BaseTi4Test;

class DreamButtonHandlerTest extends BaseTi4Test {

    @Test
    void liturgyIIFromTechTargetsEverySystemContainingPlayersUnits() {
        Game game = new Game();
        Player player = new Player("player", "player", game);
        player.setColor("red");
        player.addOwnedUnitByID("dream_destroyer");
        player.getTechs().add("bedreamdd");

        Tile activeSystem = new Tile("19", "101");
        activeSystem.addUnit(Constants.SPACE, new Units.UnitKey(Units.UnitType.Destroyer, "red"), 1);

        Tile otherSystemWithPlayersUnits = new Tile("20", "102");
        otherSystemWithPlayersUnits.addUnit(Constants.SPACE, new Units.UnitKey(Units.UnitType.Carrier, "red"), 1);

        Tile systemWithoutPlayersUnits = new Tile("21", "103");
        systemWithoutPlayersUnits.addUnit(Constants.SPACE, new Units.UnitKey(Units.UnitType.Cruiser, "blue"), 1);

        game.setTile(activeSystem);
        game.setTile(otherSystemWithPlayersUnits);
        game.setTile(systemWithoutPlayersUnits);

        assertThat(DreamButtonHandler.hasLiturgyII(player, activeSystem)).isTrue();
        assertThat(DreamButtonHandler.getTilesContainingPlayersUnits(game, player).stream()
                        .map(Tile::getPosition)
                        .collect(Collectors.toSet()))
                .isEqualTo(Set.of("101", "102"));
    }
}
