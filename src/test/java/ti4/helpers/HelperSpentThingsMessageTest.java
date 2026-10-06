package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.dv8tion.jda.api.entities.Message;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

class HelperSpentThingsMessageTest extends BaseTi4Test {

    @Test
    void keepsPlanetEmojisWhenTheMessageFits() {
        Game game = new Game();
        Player player = playerIn(game);
        game.setTile(new Tile("30", "301"));
        player.addPlanet("mellon");
        player.addSpentThing("mellon");

        String message = Helper.buildSpentThingsMessage(player, game, "both");

        assertThat(message).contains(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence("mellon", game));
    }

    @Test
    void dropsPlanetEmojisWhenSpendingEnoughPlanetsToOverflowADiscordMessage() {
        Game game = new Game();
        Player player = playerIn(game);
        // In production each planet line carries 3-4 full custom emojis (~35 chars each), so 16 planets overflowed.
        // Test emojis are spoofed as short "<Name>" strings, so it takes every base and PoK blue system to overflow.
        List<String> planets = spendEveryPlanetIn(game, player, blueSystemTileIds());

        String message = Helper.buildSpentThingsMessage(player, game, "both");

        assertThat(message).hasSizeLessThanOrEqualTo(Message.MAX_CONTENT_LENGTH);
        for (String planet : planets) {
            assertThat(message).contains(Helper.getPlanetRepresentation(planet, game));
        }
        assertThat(message).endsWith("influence.");
    }

    private static List<String> blueSystemTileIds() {
        return IntStream.concat(IntStream.rangeClosed(19, 38), IntStream.rangeClosed(59, 76))
                .mapToObj(String::valueOf)
                .toList();
    }

    private static Player playerIn(Game game) {
        Player player = game.addPlayer("player", "player");
        player.setFaction("hacan");
        player.setColor("yellow");
        return player;
    }

    private static List<String> spendEveryPlanetIn(Game game, Player player, List<String> tileIds) {
        List<String> planets = new ArrayList<>();
        for (int i = 0; i < tileIds.size(); i++) {
            Tile tile = new Tile(tileIds.get(i), String.valueOf(400 + i));
            game.setTile(tile);
            for (Planet planet : tile.getPlanetUnitHolders()) {
                player.addPlanet(planet.getName());
                player.addSpentThing(planet.getName());
                planets.add(planet.getName());
            }
        }
        return planets;
    }
}
