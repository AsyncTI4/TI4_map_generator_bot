package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.FoWHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class NokarAgentButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.player.hasUnexhaustedLeader("nokaragent")
                && FoWHelper.playerHasShipsInSystem(ctx.player, ctx.tile);
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(Buttons.gray(
                "exhaustAgent_nokaragent_" + ctx.player.getFaction(),
                "Use Nokar Agent to Place 1 Destroyer",
                FactionEmojis.nokar));
    }
}


