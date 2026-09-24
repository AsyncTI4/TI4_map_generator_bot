package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Revenant;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
public class RevenantAbilityHandler {
    private static final String CALL_OF_THE_HAUNTED = "call_of_the_haunted";
    private static final String CHOOSE_SET_PREFIX = "revenantChooseLeaderSet_";

    private static final List<String> SET_ONE =
            List.of("revenantstonebornagent", "revenantoblivioncommander", "revenantkairnhero");
    private static final List<String> SET_TWO =
            List.of("revenantardentiaagent", "revenantxytheriscommander", "revenantthroneshero");
    private static final List<String> SET_THREE =
            List.of("revenantscrapyardagent", "revenantponthouscommander", "revenantmyrrhero");
    private static final List<String> SET_FOUR =
            List.of("revenantarcanumagent", "revenantvanguardcommander", "revenantkryxoshero");
    private static final List<String> SET_FIVE =
            List.of("revenantverydithagent", "revenantveylorcommander", "revenantthurvialihero");
    private static final List<String> RETIRED_OPTIONAL_LEADERS =
            List.of("revenantxytherisagent", "revenantmyrrcommander");
    private static final List<String> ALL_OPTIONAL_LEADERS = new ArrayList<>();

    static {
        ALL_OPTIONAL_LEADERS.addAll(SET_ONE);
        ALL_OPTIONAL_LEADERS.addAll(SET_TWO);
        ALL_OPTIONAL_LEADERS.addAll(SET_THREE);
        ALL_OPTIONAL_LEADERS.addAll(SET_FOUR);
        ALL_OPTIONAL_LEADERS.addAll(SET_FIVE);
    }

    public static void offerCallOfTheHauntedButtons(Game game, Player player) {
        if (game == null || player == null || !player.hasAbility(CALL_OF_THE_HAUNTED) || hasChosenLeaderSet(player)) {
            return;
        }

        player.getPromissoryNotesOwned().stream()
                .filter(pnID -> pnID.endsWith("_an"))
                .findFirst()
                .ifPresent(pnID -> {
                    if (!game.getPurgedPN().contains(pnID)) {
                        game.setPurgedPN(pnID);
                    }
                    player.removePromissoryNote(pnID);
                });

        sendCallOfTheHauntedButtons(player);
    }

    public static boolean resetCallOfTheHauntedLeaders(Game game, Player player) {
        if (game == null || player == null || !player.hasAbility(CALL_OF_THE_HAUNTED)) {
            return false;
        }
        RevenantLeadersHandler.clearPantheonState(game, player);
        for (String leaderId : ALL_OPTIONAL_LEADERS) {
            player.removeLeader(leaderId);
        }
        for (String leaderId : RETIRED_OPTIONAL_LEADERS) {
            player.removeLeader(leaderId);
        }
        sendCallOfTheHauntedButtons(player);
        return true;
    }

    public static List<String> getCurrentPantheonAgentIds() {
        return ALL_OPTIONAL_LEADERS.stream()
                .filter(leaderId -> leaderId.endsWith("agent"))
                .toList();
    }

    private static void sendCallOfTheHauntedButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(
                Buttons.green(player.factionButtonChecker() + CHOOSE_SET_PREFIX + "set1", "Pantheon of Exploration"));
        buttons.add(Buttons.green(player.factionButtonChecker() + CHOOSE_SET_PREFIX + "set2", "Pantheon of War"));
        buttons.add(
                Buttons.green(player.factionButtonChecker() + CHOOSE_SET_PREFIX + "set3", "Pantheon of Production"));
        buttons.add(Buttons.green(player.factionButtonChecker() + CHOOSE_SET_PREFIX + "set4", "Pantheon of Wisdom"));
        buttons.add(
                Buttons.green(player.factionButtonChecker() + CHOOSE_SET_PREFIX + "set5", "Pantheon of the People"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please choose 1 Revenant leader set for **Call of the Haunted**.",
                buttons);
    }

    @ButtonHandler(CHOOSE_SET_PREFIX)
    public static void resolveChooseLeaderSet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null || !player.hasAbility(CALL_OF_THE_HAUNTED)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (hasChosenLeaderSet(player)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "A Revenant leader set has already been chosen.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String setId = buttonID.substring(CHOOSE_SET_PREFIX.length());
        List<String> chosenSet =
                switch (setId) {
                    case "set1" -> SET_ONE;
                    case "set2" -> SET_TWO;
                    case "set3" -> SET_THREE;
                    case "set4" -> SET_FOUR;
                    case "set5" -> SET_FIVE;
                    default -> List.of();
                };
        if (chosenSet.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "That Revenant leader set is no longer valid.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        for (String leaderId : chosenSet) {
            player.addLeader(leaderId);
        }

        List<MessageEmbed> embeds = new ArrayList<>();
        for (String leaderId : chosenSet) {
            if (Mapper.getLeader(leaderId) != null) {
                embeds.add(Mapper.getLeader(leaderId).getRepresentationEmbed());
            }
        }

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " chose a Revenant leader set with **Call of the Haunted** and gained 1 additional leader of each type.");
        if (!embeds.isEmpty() && player.getCardsInfoThread() != null) {
            MessageHelper.sendMessageToChannelWithEmbeds(
                    player.getCardsInfoThread(), "__Additional Revenant Leaders__", embeds);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static boolean hasChosenLeaderSet(Player player) {
        for (String leaderId : ALL_OPTIONAL_LEADERS) {
            if (player.hasLeader(leaderId)) {
                return true;
            }
        }
        return false;
    }
}
