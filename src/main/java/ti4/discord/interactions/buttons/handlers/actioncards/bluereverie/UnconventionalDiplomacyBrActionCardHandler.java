package ti4.discord.interactions.buttons.handlers.actioncards.bluereverie;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.UnusedCommanderHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.leader.UnlockLeaderService;

@UtilityClass
public class UnconventionalDiplomacyBrActionCardHandler {
    private static final String RESOLVE = "resolveUnconventionalDiplomacyBr";

    public static void offerResolution(Game game, Player player, String message) {
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                message,
                Buttons.green(player.factionButtonChecker() + RESOLVE, "Resolve Unconventional Diplomacy"));
    }

    @ButtonHandler(RESOLVE)
    public static void resolve(ButtonInteractionEvent event, Game game, Player player) {
        String leaderId = UnusedCommanderHelper.getUnusedCommander(game);
        if (leaderId == null || leaderId.isBlank()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " cannot gain a commander because none are unused.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.addLeader(leaderId);
        game.addFakeCommander(leaderId);
        UnlockLeaderService.unlockLeader(
                leaderId,
                game,
                player,
                player.getRepresentationNoPing() + " gained and unlocked "
                        + Mapper.getLeader(leaderId).getName() + " using _Unconventional Diplomacy_.");
        ButtonHelper.deleteMessage(event);
    }
}
