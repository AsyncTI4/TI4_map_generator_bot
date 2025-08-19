package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class KhraskCommanderButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return !ctx.tile.getPlanetUnitHolders().isEmpty()
                && ctx.game.playerHasLeaderUnlockedOrAlliance(ctx.player, "khraskcommander");
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(Buttons.blue(
                ctx.player.finChecker() + "placeKhraskCommanderInf_" + ctx.tile.getPosition(),
                "Place Infantry with Khrask Commander",
                FactionEmojis.khrask));
    }
}
