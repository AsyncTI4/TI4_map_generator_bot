package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.service.emoji.MiscEmojis;
import ti4.service.map.RefractureRulesService;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class MoveCharonButton implements PostMovementAbilityButton {
    @Override
    public boolean enabled(PostMovementButtonContext ctx) {
        return RefractureRulesService.canMoveCharonTo(ctx.game(), ctx.player(), ctx.tile());
    }

    @Override
    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(Buttons.blue(
                ctx.player().factionButtonChecker() + "moveCharon_" + ctx.game().getActiveSystem(),
                "Move Charon Into This System",
                MiscEmojis.LegendaryPlanet));
    }
}
