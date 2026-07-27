package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Veylor;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Helper;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class VeylorBreakthroughHandler {
    private static final String FILIBUSTER = "veylorbt";
    private static final String USE_FILIBUSTER = "useFilibusteredLegislation";

    public static Button offerFilibusterButton(Player player, Game game) {
        return Buttons.blue(
                player.factionButtonChecker() + USE_FILIBUSTER,
                "Exhaust Fillibustered Legislation",
                FactionEmojis.veylor);
    }

    @ButtonHandler(USE_FILIBUSTER)
    public static void resolveFilibusteredLegislation(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasReadyBreakthrough(FILIBUSTER)) {
            return;
        }

        BreakthroughCommandHelper.exhaustBreakthrough(player, FILIBUSTER);
        game.setStoredValue("veylorBtExtraAgenda", "yes");

        AgendaHelper.resolveWithNoEffect(event, game);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), "You may all ready 1 planet because of _Filibustered Legislation_.");

        for (Player p : game.getRealPlayers()) {
            List<Button> buttons = Helper.getPlanetRefreshButtons(p, game);
            buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Readying Planets"));
            MessageHelper.sendMessageToChannelWithButtons(p.getCorrectChannel(), p.getRepresentation() + "", buttons);
        }
    }
}
