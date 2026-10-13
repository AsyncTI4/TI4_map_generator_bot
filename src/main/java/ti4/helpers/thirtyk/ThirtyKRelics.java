package ti4.helpers.thirtyk;

import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ThirtyKRelics {

    private static final Set<String> OWN_COMPONENT_ACTIONS = Set.of();

    public static boolean handlesComponentAction(String relicID) {
        return OWN_COMPONENT_ACTIONS.contains(relicID);
    }

    public static void resolveComponentAction(Game game, Player player, ButtonInteractionEvent event, String relicID) {}

    public static void resolveRelicGained(
            GenericInteractionCreateEvent event, Game game, Player player, String relicID) {
        if (!ThirtyKCards.isRelic(relicID)) return;
    }
}
