package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron.IronLeadersHandler;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class IronHeroButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return IronLeadersHandler.canUseIronHero(ctx.game(), ctx.player(), ctx.tile());
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(Buttons.blue(
                ctx.player().factionButtonChecker() + IronLeadersHandler.IRON_HERO_BUTTON_ID,
                "Use Iron Hero",
                FactionEmojis.iron));
    }
}
