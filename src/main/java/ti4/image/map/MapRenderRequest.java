package ti4.image.map;

import javax.annotation.Nullable;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.helpers.DisplayType;

record MapRenderRequest(
        Game game,
        @Nullable DisplayType displayType,
        @Nullable GenericInteractionCreateEvent event,
        @Nullable String segment) {}
