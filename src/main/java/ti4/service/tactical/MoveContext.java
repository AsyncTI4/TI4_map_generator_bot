package ti4.service.tactical;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

public final class MoveContext {
    public final Player player;
    public final Game game;
    public final GenericInteractionCreateEvent event;
    public final Tile active;
    public final String activeSystem;

    public MoveContext(Player player, Game game, GenericInteractionCreateEvent event) {
        this.player = player;
        this.game = game;
        this.event = event;
        activeSystem = game.getActiveSystem();
        active = game.getTileByPosition(activeSystem);
    }
}
