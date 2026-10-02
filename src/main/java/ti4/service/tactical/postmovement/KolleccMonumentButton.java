package ti4.service.tactical.postmovement;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.unit.monuments.MonumentsDSButtonHandler;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class KolleccMonumentButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.game().getRealPlayers().stream()
                .anyMatch(player -> MonumentsDSButtonHandler.canUseKolleccMonument(ctx.game(), player, ctx.tile()));
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        List<Button> buttons = new ArrayList<>();
        for (var monumentOwner : ctx.game().getRealPlayers()) {
            if (MonumentsDSButtonHandler.canUseKolleccMonument(ctx.game(), monumentOwner, ctx.tile())) {
                MessageHelper.sendMessageToChannel(
                        monumentOwner.getCorrectChannel(),
                        monumentOwner.getRepresentationNoPing() + ", _Shades' Den_ can be used now.");
                buttons.add(Buttons.gray(
                        monumentOwner.factionButtonChecker() + "useKolleccMonument_"
                                + ctx.tile().getPosition(),
                        "Use Shades' Den",
                        FactionEmojis.kollecc));
            }
        }
        return buttons;
    }
}
