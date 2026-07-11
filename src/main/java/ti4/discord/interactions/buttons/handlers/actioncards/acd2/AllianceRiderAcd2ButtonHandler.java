package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.UnusedCommanderHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
class AllianceRiderAcd2ButtonHandler {

    private static final String ALLIANCE_RIDER_CURRENT_ALLY = "allianceRiderCurrentAlly";
    private static final String ALLIANCE_RIDER_PURGED_ALLIES = "allianceRiderPurgedAllies";

    @ButtonHandler("allianceRiderRandomAlly")
    public static void resolveAllianceRiderRandomAlly(Player player, Game game, ButtonInteractionEvent event) {
        Set<String> purgedAllies = new HashSet<>(getStoredCommanderList(game, ALLIANCE_RIDER_PURGED_ALLIES));
        String allyCommander = UnusedCommanderHelper.getUnusedCommander(game, purgedAllies);
        if (allyCommander == null || allyCommander.isBlank()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + " cannot reveal a new ally because none are available.");
            return;
        }

        game.setStoredValue(ALLIANCE_RIDER_CURRENT_ALLY, allyCommander);
        ButtonHelper.deleteMessage(event);

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue(player.factionButtonChecker() + "allianceRiderGainAlly", "Gain Ally"));
        buttons.add(Buttons.red(player.factionButtonChecker() + "allianceRiderPurgeAlly", "Purge Ally"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.toString() + ", choose whether to gain or purge this ally.",
                buttons);
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.toString() + " revealed this ally for _Alliance Rider_.",
                Mapper.getLeader(allyCommander).getRepresentationEmbed());
    }

    @ButtonHandler("allianceRiderGainAlly")
    public static void resolveAllianceRiderGainAlly(Player player, Game game, ButtonInteractionEvent event) {
        String allyCommander = game.getStoredValue(ALLIANCE_RIDER_CURRENT_ALLY);
        if (allyCommander.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + " has no revealed ally to gain. Use _Random Ally_ first.");
            return;
        }

        if (player.hasLeader(allyCommander)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.toString() + " already has this ally ability in play.");
            game.setStoredValue(ALLIANCE_RIDER_CURRENT_ALLY, "");
            return;
        }

        player.addLeader(allyCommander);
        game.addFakeCommander(allyCommander);
        player.getLeader(allyCommander).ifPresent(leader -> leader.setLocked(false));
        game.setStoredValue(ALLIANCE_RIDER_CURRENT_ALLY, "");
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.toString() + " gained the _Alliance Rider_ ally ability: "
                        + Mapper.getLeader(allyCommander).getName() + ".");
    }

    @ButtonHandler("allianceRiderPurgeAlly")
    public static void resolveAllianceRiderPurgeAlly(Player player, Game game, ButtonInteractionEvent event) {
        String allyCommander = game.getStoredValue(ALLIANCE_RIDER_CURRENT_ALLY);
        if (allyCommander.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + " has no revealed ally to purge. Use _Random Ally_ first.");
            return;
        }

        Set<String> purgedAllies = new HashSet<>(getStoredCommanderList(game, ALLIANCE_RIDER_PURGED_ALLIES));
        purgedAllies.add(allyCommander);
        game.setStoredValue(ALLIANCE_RIDER_PURGED_ALLIES, String.join(",", purgedAllies));
        game.setStoredValue(ALLIANCE_RIDER_CURRENT_ALLY, "");
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.toString() + " purged the _Alliance Rider_ ally: "
                        + Mapper.getLeader(allyCommander).getName() + ".");
    }

    private static List<String> getStoredCommanderList(Game game, String storageKey) {
        String storedValue = game.getStoredValue(storageKey);
        if (storedValue.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(storedValue.split(",")));
    }
}
