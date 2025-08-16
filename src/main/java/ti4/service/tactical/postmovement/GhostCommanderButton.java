package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class GhostCommanderButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.game.playerHasLeaderUnlockedOrAlliance(ctx.player, "ghostcommander");
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(Buttons.blue(
                ctx.player.finChecker() + "placeGhostCommanderFF_" + ctx.tile.getPosition(),
                "Place Fighter with Creuss Commander",
                FactionEmojis.Ghost));
    }
}


