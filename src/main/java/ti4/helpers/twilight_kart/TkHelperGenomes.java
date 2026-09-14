package ti4.helpers.twilight_kart;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class TkHelperGenomes {
    private static final String DEPLOYMENT_AGENT = "tknovadeploymentagent";
    private static final String EXHAUST_AGENT = "exhaustAgent_";

    public static List<Button> getStartOfTurnButtons(Game game, Player player, String factionChecker) {
        List<Button> startButtons = new ArrayList<>();

        if ((player.hasUnexhaustedLeader(DEPLOYMENT_AGENT))) {
            startButtons.add(Buttons.gray(
                    factionChecker + EXHAUST_AGENT + DEPLOYMENT_AGENT, "Use Deployment Genome", FactionEmojis.Nomad));
        }

        return startButtons;
    }

    public static void onExhaust(
            GenericInteractionCreateEvent event, Game game, Player player, String agent, String ssruuClever, String rest) {
        if (DEPLOYMENT_AGENT.equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted the " + ssruuClever + "_Deployment Genome_.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), exhaustText);
            ButtonHelper.resolveTransitDiodesStep1(game, player);
        }
    }
}
