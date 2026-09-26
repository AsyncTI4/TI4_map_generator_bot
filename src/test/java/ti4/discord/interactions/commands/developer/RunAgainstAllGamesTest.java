package ti4.discord.interactions.commands.developer;

import java.util.List;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

class RunAgainstAllGamesTest extends BaseTi4Test {

    private static void seat(Game game, String faction, String... planets) {
        Player player = game.addPlayer(faction + "-user", faction);
        player.setFaction(faction);
        player.setColor(COLORS.get(game.getPlayers().size() - 1));
        player.getPlanets().addAll(List.of(planets));
    }

    private static final List<String> COLORS = List.of("red", "blue", "green", "yellow", "purple", "orange");

    private static Player anchoredAt(Game game, String position) {
        Player player = game.addPlayer("user-" + position, "user");
        player.setFaction("keleres");
        player.setColor("red");
        player.setPlayerStatsAnchorPosition(position);
        return player;
    }

    private static Game gameWithTiles(String... tileIds) {
        Game game = new Game();
        int position = 101;
        for (String tileId : tileIds) {
            game.setTile(new Tile(tileId, Integer.toString(position++)));
        }
        return game;
    }
}
