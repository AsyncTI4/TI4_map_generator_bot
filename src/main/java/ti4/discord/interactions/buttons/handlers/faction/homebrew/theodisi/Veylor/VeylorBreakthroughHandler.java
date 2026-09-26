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
    private static final String DECLINE_FILIBUSTER = "declineFilibusteredLegislation_";

    public static Button offerFilibusterButton(Player player, Game game) {
        return Buttons.blue(
                player.factionButtonChecker() + USE_FILIBUSTER,
                "Exhaust Filibustered Legislation",
                FactionEmojis.veylor);
    }

    public static Button offerDeclineFilibusterButton(Player player, String winner) {
        return Buttons.red(player.factionButtonChecker() + DECLINE_FILIBUSTER + winner, "Resolve Agenda Normally");
    }

    @ButtonHandler(USE_FILIBUSTER)
    public static void resolveFilibusteredLegislation(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null
                || player == null
                || !VeylorLeadersHandler.isVeylorAgendaPhase(game)
                || !player.hasReadyBreakthrough(FILIBUSTER)) {
            return;
        }

        BreakthroughCommandHelper.exhaustBreakthrough(player, FILIBUSTER);
        game.setStoredValue("veylorBtExtraAgenda", "yes");

        AgendaHelper.resolveWithNoEffect(event, game);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), "All players may ready 1 planet because of _Filibustered Legislation_.");

        for (Player p : game.getRealPlayers()) {
            List<Button> buttons = Helper.getPlanetRefreshButtons(p, game);
            buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Readying Planets"));
            MessageHelper.sendMessageToChannelWithButtons(
                    p.getCorrectChannel(), p.getRepresentation() + ", please choose a planet to ready.", buttons);
        }
    }

    @ButtonHandler(DECLINE_FILIBUSTER)
    public static void declineFilibusteredLegislation(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null
                || player == null
                || !VeylorLeadersHandler.isVeylorAgendaPhase(game)
                || !player.hasReadyBreakthrough(FILIBUSTER)) {
            return;
        }

        String winner = buttonID.substring(DECLINE_FILIBUSTER.length());
        if (winner.isBlank()) {
            return;
        }

        List<Button> resolutions = List.of(
                Buttons.blue("agendaResolution_" + winner, "Resolve with Current Winner"),
                Buttons.red("autoresolve_manual", "Resolve it Manually"));
        MessageHelper.editMessageWithButtons(
                event,
                event.getMessage().getContentRaw()
                        + "\n"
                        + player.getRepresentationNoPing()
                        + " declined to use _Filibustered Legislation_.",
                resolutions);
    }
}
