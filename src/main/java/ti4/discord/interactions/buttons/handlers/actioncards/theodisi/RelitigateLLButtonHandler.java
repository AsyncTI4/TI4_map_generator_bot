package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;

@UtilityClass
public class RelitigateLLButtonHandler {
    private static final String RELITIGATE = "Relitigate";
    private static final String PRESET_RELITIGATE = "relitigatePreset";
    private static final String DIRECT_RELITIGATE = "relitigateDirect";
    private static final String SABOTAGED_RELITIGATE = "relitigateSabotaged_";
    private static final String RELITIGATE_EXTRA_AGENDA = "relitigateExtraAgenda";
    private static final String SELECT_RELITIGATE_AGENDA = "selectRelitigateAgenda_";

    public static boolean offerPreassignedRelitigate(
            GenericInteractionCreateEvent event, Game game, MessageChannel channel) {
        if (!game.getStoredValue("executiveOrder").isEmpty()) return false;

        Player player = game.getPlayerFromColorOrFaction(game.getStoredValue(RELITIGATE));
        if (player == null) return false;
        if (!player.getActionCards().containsKey("relitigate")) {
            game.removeStoredValue(RELITIGATE);
            return false;
        }

        List<Button> buttons = getDiscardAgendaButtons(game, player);
        if (buttons.isEmpty()) {
            game.removeStoredValue(RELITIGATE);
            MessageHelper.sendMessageToChannel(
                    channel,
                    player.getRepresentationNoPing()
                            + " had preset _Relitigate_, but there are no agendas in the discard pile.");
            return false;
        }

        Integer actionCardId = player.getActionCards().get("relitigate");
        if (actionCardId == null) return false;
        game.removeStoredValue(RELITIGATE);
        game.setStoredValue(PRESET_RELITIGATE, player.getFaction());
        String error = ActionCardHelper.playAC(event, game, player, actionCardId.toString(), channel);
        if (error != null) {
            game.removeStoredValue(PRESET_RELITIGATE);
            MessageHelper.sendMessageToChannel(channel, error);
            return false;
        }
        return true;
    }

    @ButtonHandler("resolveRelitigate")
    public static void resolveRelitigate(ButtonInteractionEvent event, Game game, Player player) {
        if ("yes".equals(game.getStoredValue(SABOTAGED_RELITIGATE + player.getFaction()))) {
            game.removeStoredValue(SABOTAGED_RELITIGATE + player.getFaction());
            game.removeStoredValue(PRESET_RELITIGATE);
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getDiscardAgendaButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " has no discarded agendas to select for _Relitigate_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(DIRECT_RELITIGATE, player.getFaction());
        String prefix = player.factionButtonChecker() + SELECT_RELITIGATE_AGENDA;
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", select a discarded agenda for _Relitigate_.",
                NewStuffHelper.buttonPagination(buttons, prefix, 0));
    }

    @ButtonHandler(SELECT_RELITIGATE_AGENDA)
    public static void selectRelitigateAgenda(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String prefix = player.factionButtonChecker() + SELECT_RELITIGATE_AGENDA;
        List<Button> buttons = getDiscardAgendaButtons(game, player);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                player.getRepresentationNoPing() + ", select a discarded agenda for _Relitigate_.",
                prefix,
                buttonID)) {
            return;
        }

        boolean direct = player.getFaction().equals(game.getStoredValue(DIRECT_RELITIGATE));
        if (!direct) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Integer uniqueId;
        try {
            uniqueId = Integer.valueOf(buttonID.substring(SELECT_RELITIGATE_AGENDA.length()));
        } catch (NumberFormatException exception) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String agendaId = game.getDiscardAgendas().entrySet().stream()
                .filter(entry -> uniqueId.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (agendaId == null || !game.putAgendaBackIntoDeckOnTop(uniqueId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.removeStoredValue(RELITIGATE);
        game.removeStoredValue(PRESET_RELITIGATE);
        game.removeStoredValue(DIRECT_RELITIGATE);
        game.setStoredValue(RELITIGATE_EXTRA_AGENDA, "yes");
        AgendaModel agenda = Mapper.getAgenda(agendaId);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " played _Relitigate_, returning _"
                        + (agenda == null ? agendaId : agenda.getName())
                        + "_ from the discard pile for an immediate vote.");
        ActionCardHelper.sendActionCardInfo(game, player);
        AgendaHelper.revealAgenda(event, false, game, event.getMessageChannel());
        ButtonHelper.deleteMessage(event);
    }

    public static boolean hasExtraAgenda(Game game) {
        return "yes".equals(game.getStoredValue(RELITIGATE_EXTRA_AGENDA));
    }

    public static void clearExtraAgenda(Game game) {
        game.removeStoredValue(RELITIGATE_EXTRA_AGENDA);
    }

    public static void clearAgendaPhaseState(Game game) {
        game.removeStoredValue(DIRECT_RELITIGATE);
        game.removeStoredValue(RELITIGATE_EXTRA_AGENDA);
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(SABOTAGED_RELITIGATE + player.getFaction());
        }
    }

    public static void onRelitigateSabotaged(Game game, Player player) {
        game.setStoredValue(SABOTAGED_RELITIGATE + player.getFaction(), "yes");
        if (player.getFaction().equals(game.getStoredValue(PRESET_RELITIGATE))) {
            game.removeStoredValue(PRESET_RELITIGATE);
            MessageHelper.sendMessageToChannelWithButton(
                    game.getMainGameChannel(),
                    "_Relitigate_ was canceled. Please flip the normal agenda.",
                    Buttons.blue("flip_agenda", "Flip Agenda"));
        }
    }

    private static List<Button> getDiscardAgendaButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : game.getDiscardAgendas().entrySet()) {
            AgendaModel agenda = Mapper.getAgenda(entry.getKey());
            if (agenda == null) continue;
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_RELITIGATE_AGENDA + entry.getValue(), agenda.getName()));
        }
        return buttons;
    }
}
