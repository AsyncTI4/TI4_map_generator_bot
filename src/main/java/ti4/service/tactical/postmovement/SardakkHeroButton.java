package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class SardakkHeroButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.player.hasLeaderUnlocked("sardakkhero")
                && !ctx.tile.getPlanetUnitHolders().isEmpty();
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(
                Buttons.blue(ctx.player.finChecker() + "purgeSardakkHero", "Use N'orr Hero", FactionEmojis.Sardakk));
    }
}
