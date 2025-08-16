package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class ShroudOfLithButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.player.hasAbility("shroud_of_lith")
                && ButtonHelperFactionSpecific.getKolleccReleaseButtons(ctx.player, ctx.game).size() > 1;
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(
                Buttons.blue("shroudOfLithStart", "Use Shroud of Lith", FactionEmojis.kollecc),
                Buttons.gray("refreshLandingButtons", "Refresh Landing Buttons", FactionEmojis.kollecc));
    }
}


