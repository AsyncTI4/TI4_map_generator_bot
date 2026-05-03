package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class KhraskCommanderButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return !ctx.tile().getPlanetUnitHolders().isEmpty()
                && ctx.game().playerHasLeaderUnlockedOrAlliance(ctx.player(), "khraskcommander");
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(Buttons.blue(
                ctx.player().factionButtonChecker() + "placeKhraskCommanderInf_"
                        + ctx.tile().getPosition(),
                "Place Infantry with Khrask Commander",
                FactionEmojis.khrask));
    }
}
