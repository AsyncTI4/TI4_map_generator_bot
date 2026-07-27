package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Veylor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;

@UtilityClass
public class VeylorAbilitiesHandler {
    private static final String TIGHT_SCHEDULING = "tight_scheduling";
    private static final String TIGHT_SCHEDULING_AGENDAS = "tightSchedulingAgendas_";
    private static final String TIGHT_SCHEDULING_REVEAL = "tightSchedulingReveal_";
    private static final String TIGHT_SCHEDULING_BYPASS = "tightSchedulingBypass";
    private static final String TIGHT_SCHEDULING_BOTTOM = "tightSchedulingBottom_";

    // Tight Scheduling
    public static void offerTightScheduling(Game game) {
        for (Player player : game.getRealPlayers()) {
            if (!player.hasAbility(TIGHT_SCHEDULING)
                    || !game.getStoredValue(TIGHT_SCHEDULING_AGENDAS + player.getFaction())
                            .isEmpty()) {
                continue;
            }

            List<String> agendaIds = new ArrayList<>();
            List<MessageEmbed> agendaEmbeds = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Entry<String, Integer> agenda = game.drawAgenda();
                if (agenda == null) {
                    break;
                }

                agendaIds.add(agenda.getKey());
                game.getAgendas().remove(agenda.getKey());
                AgendaModel agendaModel = Mapper.getAgenda(agenda.getKey());
                if (agendaModel != null) {
                    agendaEmbeds.add(agendaModel.getRepresentationEmbed());
                }
            }

            game.setStoredValue(TIGHT_SCHEDULING_AGENDAS + player.getFaction(), String.join(",", agendaIds));
            MessageHelper.sendMessageEmbedsToCardsInfoThread(
                    player,
                    player.getRepresentationUnfogged() + ", you drew these agendas with _Tight Scheduling_:",
                    agendaEmbeds);
        }
    }

    public static boolean offerTightSchedulingRevealChoice(Game game, boolean revealFromBottom) {
        if ("yes".equals(game.getStoredValue(TIGHT_SCHEDULING_BYPASS))) {
            game.removeStoredValue(TIGHT_SCHEDULING_BYPASS);
            return false;
        }

        for (Player player : game.getRealPlayers()) {
            String storedAgendas = game.getStoredValue(TIGHT_SCHEDULING_AGENDAS + player.getFaction());
            if (!player.hasAbility(TIGHT_SCHEDULING) || storedAgendas.isEmpty()) {
                continue;
            }

            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + TIGHT_SCHEDULING_REVEAL + "deck;" + revealFromBottom,
                    "Reveal from Agenda Deck"));

            for (String agendaId : storedAgendas.split(",")) {
                AgendaModel agenda = Mapper.getAgenda(agendaId);
                if (agenda != null) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + TIGHT_SCHEDULING_REVEAL + agendaId + ";" + revealFromBottom,
                            "Reveal " + agenda.getName()));
                }
            }

            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged()
                            + ", choose the next agenda to reveal with _Tight Scheduling_:\n\n**WARNING**: Wait a few seconds before revealing it as going to fast may cause the agenda to not be revealed.",
                    buttons);

            return true;
        }
        return false;
    }

    @ButtonHandler(TIGHT_SCHEDULING_REVEAL)
    public static void revealWithTightScheduling(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasAbility(TIGHT_SCHEDULING)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String[] parts = buttonID.replace(TIGHT_SCHEDULING_REVEAL, "").split(";", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String choice = parts[0];
        boolean revealFromBottom = Boolean.parseBoolean(parts[1]);
        String key = TIGHT_SCHEDULING_AGENDAS + player.getFaction();
        List<String> agendas = new ArrayList<>(List.of(game.getStoredValue(key).split(",")));

        if (!"deck".equals(choice)) {
            if (!agendas.remove(choice)
                    || !game.putAgendaTop(game.getSentAgendas().get(choice))) {
                ButtonHelper.deleteMessage(event);
                return;
            }
            if (agendas.isEmpty()) {
                game.removeStoredValue(key);
            } else {
                game.setStoredValue(key, String.join(",", agendas));
            }
            revealFromBottom = false;
        }

        game.setStoredValue(TIGHT_SCHEDULING_BYPASS, "yes");
        ButtonHelper.deleteMessage(event);
        try {
            AgendaHelper.revealAgenda(event, revealFromBottom, game, game.getMainGameChannel());
        } finally {
            game.removeStoredValue(TIGHT_SCHEDULING_BYPASS);
        }
    }

    public static boolean offerTightSchedulingCleanup(Game game) {
        for (Player player : game.getRealPlayers()) {
            String key = TIGHT_SCHEDULING_AGENDAS + player.getFaction();
            String storedAgendas = game.getStoredValue(key);
            if (!player.hasAbility(TIGHT_SCHEDULING) || storedAgendas.isEmpty()) {
                continue;
            }

            List<Button> buttons = new ArrayList<>();
            for (String agendaId : storedAgendas.split(",")) {
                AgendaModel agenda = Mapper.getAgenda(agendaId);
                if (agenda != null) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + TIGHT_SCHEDULING_BOTTOM + agendaId,
                            "Place " + agenda.getName() + " next on bottom"));
                }
            }

            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged()
                            + ", place your remaining **Tight Scheduling** agendas on the bottom in any order.",
                    buttons);
            return true;
        }
        return false;
    }

    public static void returnUnassignedTightSchedulingAgendas(Game game) {
        for (Player player : game.getRealPlayers()) {
            String key = TIGHT_SCHEDULING_AGENDAS + player.getFaction();
            String storedAgendas = game.getStoredValue(key);
            if (!player.hasAbility(TIGHT_SCHEDULING) || storedAgendas.isEmpty()) {
                continue;
            }

            List<String> agendaIds = new ArrayList<>(List.of(storedAgendas.split(",")));
            Collections.shuffle(agendaIds);
            List<String> agendasNotReturned = new ArrayList<>();
            for (String agendaId : agendaIds) {
                Integer uniqueId = game.getSentAgendas().get(agendaId);
                if (uniqueId == null || !game.putAgendaBottom(uniqueId)) {
                    agendasNotReturned.add(agendaId);
                }
            }

            if (agendasNotReturned.isEmpty()) {
                game.removeStoredValue(key);
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        player.getRepresentation()
                                + ", so as not to disrupt the next Agenda Phase, the remaining unassigned agendas you drew with _Tight Scheduling_ have been placed on the bottom of the agenda deck in a random order.");
            } else {
                game.setStoredValue(key, String.join(",", agendasNotReturned));
            }
        }
    }

    @ButtonHandler(TIGHT_SCHEDULING_BOTTOM)
    public static void placeTightSchedulingAgendaOnBottom(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasAbility(TIGHT_SCHEDULING)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String agendaId = buttonID.replace(TIGHT_SCHEDULING_BOTTOM, "");
        String key = TIGHT_SCHEDULING_AGENDAS + player.getFaction();
        List<String> agendas = new ArrayList<>(List.of(game.getStoredValue(key).split(",")));
        Integer uniqueId = game.getSentAgendas().get(agendaId);

        if (!agendas.remove(agendaId) || uniqueId == null || !game.putAgendaBottom(uniqueId)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        if (agendas.isEmpty()) {
            game.removeStoredValue(key);
            ButtonHelper.deleteMessage(event);
        } else {
            game.setStoredValue(key, String.join(",", agendas));
            ButtonHelper.deleteTheOneButton(event);
        }
    }

    // Lobbyist Dues
    public static void resolveLobbyistDues(GenericInteractionCreateEvent event, Game game, String winner) {
        for (Player player : AgendaHelper.getLosers(winner, game)) {
            if (player.hasAbility("lobbyist_dues")) {
                int commoditiesGained =
                        AgendaHelper.getWinningVoters(winner, game).size();

                if (commoditiesGained == 0) {
                    continue;
                }

                ButtonHelperStats.gainComms(event, game, player, commoditiesGained, false);

                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation() + ", you gained " + commoditiesGained + " from _Lobbyist Dues_.");
            }
        }
    }
}
