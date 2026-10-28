package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.stoneborn;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.leader.UnlockLeaderService;

@UtilityClass
public class StonebornPromissoryHandler {
    private static final String UNLEASHED_POTENTIAL = "thpnstoneborn";
    private static final String UNLOCK_POTENTIAL = "unlockPotential_";

    public static void offerPotentialChoices(Player player, Game game) {
        if (!hasUnleashedPotentialInPlayArea(player, game)) {
            return;
        }

        List<Button> buttons = player.getBreakthroughIDs().stream()
                .filter(breakthrough -> !player.hasUnlockedBreakthrough(breakthrough))
                .map(breakthrough -> Buttons.green(
                        player.factionButtonChecker() + UNLOCK_POTENTIAL + "bt_" + breakthrough,
                        "Unlock " + Mapper.getBreakthrough(breakthrough).getName() + " Breakthrough"))
                .toList();
        Leader commander = player.getLeader("commander").orElse(null);
        if (commander != null && commander.isLocked()) {
            buttons = new java.util.ArrayList<>(buttons);
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + UNLOCK_POTENTIAL + "commander_" + commander.getId(),
                    "Unlock Commander"));
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " has no locked breakthrough or commander for _Unleashed Potential_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose how to resolve _Unleashed Potential_.",
                buttons);
    }

    @ButtonHandler(UNLOCK_POTENTIAL)
    public static void resolveUnleashedPotential(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!hasUnleashedPotentialInPlayArea(player, game)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String choice = buttonID.substring(UNLOCK_POTENTIAL.length());
        if (choice.startsWith("bt_")) {
            String breakthrough = choice.substring("bt_".length());
            if (!player.hasBreakthrough(breakthrough) || player.hasUnlockedBreakthrough(breakthrough)) {
                ButtonHelper.deleteMessage(event);
                return;
            }
            BreakthroughCommandHelper.unlockBreakthrough(game, player, breakthrough);
        } else if (choice.startsWith("commander_")) {
            String commander = choice.substring("commander_".length());
            Leader leader = player.getLeader(commander).orElse(null);
            if (leader == null || !"commander".equals(leader.getType()) || !leader.isLocked()) {
                ButtonHelper.deleteMessage(event);
                return;
            }
            UnlockLeaderService.unlockLeader(commander, game, player);
        } else {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ButtonHelper.deleteMessage(event);
    }

    private static boolean hasUnleashedPotentialInPlayArea(Player player, Game game) {
        return game != null
                && player != null
                && !player.ownsPromissoryNote(UNLEASHED_POTENTIAL)
                && player.getPromissoryNotesInPlayArea().contains(UNLEASHED_POTENTIAL);
    }
}
