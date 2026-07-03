package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.service.emoji.CardEmojis;

@UtilityClass
class RehashedDebatesAcd2ButtonHandler {

    @ButtonHandler("resolveRehashedDebates")
    public static void resolveRehashedDebates(Player player, Game game, ButtonInteractionEvent event) {
        String currentAgendaId = AgendaHelper.getCurrentAgendaId(game);

        List<Button> buttons = new ArrayList<>();
        for (String agendaId : game.getDiscardAgendas().keySet()) {
            if (agendaId.equals(currentAgendaId)) {
                continue;
            }
            AgendaModel agenda = Mapper.getAgenda(agendaId);
            if (agenda == null) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "rehashedDebatesChoose_disc_" + agendaId,
                    agenda.getName(),
                    CardEmojis.Agenda));
        }
        for (String agendaId : game.getLaws().keySet()) {
            AgendaModel agenda = Mapper.getAgenda(agendaId);
            if (agenda == null) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "rehashedDebatesChoose_law_" + agendaId,
                    agenda.getName() + " (Law in play)",
                    CardEmojis.Agenda));
        }

        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", there are no other agendas in the discard pile or in play to choose for _Rehashed Debates_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", the revealed agenda has been discarded. Choose which agenda players will vote on instead for _Rehashed Debates_.",
                buttons);
    }

    @ButtonHandler("rehashedDebatesChoose_")
    public static void resolveRehashedDebatesChoose(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("rehashedDebatesChoose_", "");
        int separator = payload.indexOf('_');
        if (separator < 0) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Rehashed Debates_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String source = payload.substring(0, separator);
        String agendaId = payload.substring(separator + 1);
        AgendaModel agenda = Mapper.getAgenda(agendaId);
        if (!Mapper.isValidAgenda(agendaId) || agenda == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "That agenda is no longer available for _Rehashed Debates_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        if ("law".equals(source)) {
            game.removeLaw(agendaId);
        }
        game.putAgendaBackIntoDeckOnTop(agendaId);

        // The standard reveal flow re-increments agendaCount and is guarded against rapid re-flips;
        // undo both so this re-reveal replaces the current agenda instead of counting as a new one.
        String agendaCount = game.getStoredValue("agendaCount");
        if (!agendaCount.isEmpty()) {
            try {
                game.setStoredValue("agendaCount", (Integer.parseInt(agendaCount) - 1) + "");
            } catch (NumberFormatException ignored) {
                // leave the stored value as-is if it is somehow non-numeric
            }
        }
        game.removeStoredValue("lastAgendaReactTime");

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " chose _" + agenda.getName()
                        + "_ for _Rehashed Debates_. Players will vote on it instead.");
        AgendaHelper.revealAgenda(event, false, game, game.getMainGameChannel());
    }
}
