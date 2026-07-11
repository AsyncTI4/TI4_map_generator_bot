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
            List.of("revenantverydithagent", "revenantmyrrcommander", "revenantthroneshero");
    private static final List<String> SET_TWO =
            List.of("revenantarcanumagent", "revenantoblivioncommander", "revenantkairnhero");
    private static final List<String> SET_THREE =
            List.of("revenantxytherisagent", "revenantponthouscommander", "revenantkryxoshero");
    private static final List<String> ALL_OPTIONAL_LEADERS = new ArrayList<>();

    static {
        ALL_OPTIONAL_LEADERS.addAll(SET_ONE);
        ALL_OPTIONAL_LEADERS.addAll(SET_TWO);
        ALL_OPTIONAL_LEADERS.addAll(SET_THREE);
    }

    public static void offerCallOfTheHauntedButtons(Game game, Player player) {
        if (game == null || player == null || !player.hasAbility(CALL_OF_THE_HAUNTED) || hasChosenLeaderSet(player)) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + CHOOSE_SET_PREFIX + "set1", "Choose Verydith / Myrr / Thrones"));
        buttons.add(Buttons.green(
                player.factionButtonChecker() + CHOOSE_SET_PREFIX + "set2", "Choose Arcanum / Oblivion / Kairn"));
        buttons.add(Buttons.green(
                player.factionButtonChecker() + CHOOSE_SET_PREFIX + "set3", "Choose Xytheris / Ponthous / Kryxos"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.toString() + ", choose 1 Revenant leader set for **Call of the Haunted**.",
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
                    default -> List.of();
                };
        if (chosenSet.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "That Revenant leader set is no longer valid.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        for (String leaderId : ALL_OPTIONAL_LEADERS) {
            player.removeLeader(leaderId);
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
                player.toString()
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
