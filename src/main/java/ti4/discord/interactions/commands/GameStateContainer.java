package ti4.discord.interactions.commands;

import ti4.game.Game;
import ti4.game.Player;

public interface GameStateContainer {

    boolean isSaveGame();

    Game getGame();

    Player getPlayer();
}
