package ti4.service.tactical.movement;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;

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
        this.activeSystem = game.getActiveSystem();
        this.active = game.getTileByPosition(this.activeSystem);
    }
}
