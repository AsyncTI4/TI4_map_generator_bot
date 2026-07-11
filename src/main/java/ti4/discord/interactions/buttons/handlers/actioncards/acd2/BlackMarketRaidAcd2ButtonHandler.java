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
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.ExploreEmojis;

@UtilityClass
class BlackMarketRaidAcd2ButtonHandler {

    @ButtonHandler("resolveBlackMarketRaid")
    public static void resolveBlackMarketRaid(Player player, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        int totalFragments = player.getCrf() + player.getHrf() + player.getIrf() + player.getUrf();
        int commandSheetTokens = player.getTacticalCC() + player.getStrategicCC() + player.getFleetCC();
        List<Button> buttons = new ArrayList<>();
        for (int fragsToPurge = 0; fragsToPurge <= Math.min(totalFragments, 3); fragsToPurge++) {
            int tokenCost = 3 - fragsToPurge;
            if (commandSheetTokens < tokenCost) {
                continue;
            }
            String label = fragsToPurge == 0
                    ? "Spend 3 command tokens (purge 0 fragments)"
                    : "Purge " + fragsToPurge + " fragment" + (fragsToPurge == 1 ? "" : "s") + " (spend " + tokenCost
                            + " command token" + (tokenCost == 1 ? "" : "s") + ")";
            buttons.add(Buttons.green("resolveBlackMarketRaidStep2_" + fragsToPurge, label));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString()
                            + " cannot resolve _Black Market Raid_: not enough command tokens or relic fragments.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose how many relic fragments to purge for _Black Market Raid_.",
                buttons);
    }

    @ButtonHandler("resolveBlackMarketRaidStep2_")
    public static void resolveBlackMarketRaidStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        int fragsToPurge;
        try {
            fragsToPurge = Integer.parseInt(buttonID.replace("resolveBlackMarketRaidStep2_", ""));
        } catch (NumberFormatException e) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Black Market Raid_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        ButtonHelper.deleteMessage(event);

        // Purge relic fragments
        StringBuilder resolveMsg = new StringBuilder(player.getRepresentationUnfogged());
        resolveMsg.append(" resolved _Black Market Raid_");
        if (fragsToPurge > 0) {
            List<String> fragments = new ArrayList<>(player.getFragments());
            int purged = 0;
            StringBuilder fragMsg = new StringBuilder();
            for (String fragId : fragments) {
                if (purged >= fragsToPurge) break;
                player.removeFragment(fragId);
                game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + 1);
                String emoji =
                        switch (Mapper.getExplore(fragId).getType().toLowerCase()) {
                            case "cultural" -> ExploreEmojis.CFrag.toString();
                            case "industrial" -> ExploreEmojis.IFrag.toString();
                            case "hazardous" -> ExploreEmojis.HFrag.toString();
                            default -> ExploreEmojis.UFrag.toString();
                        };
                fragMsg.append(emoji);
                purged++;
            }
            resolveMsg
                    .append(", purging ")
                    .append(fragMsg)
                    .append(" relic fragment")
                    .append(purged == 1 ? "" : "s");
        }
        resolveMsg.append(".");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), resolveMsg.toString());

        // Prompt player to manually remove command tokens if needed
        int tokenCost = 3 - fragsToPurge;
        if (tokenCost > 0) {
            String tokenMsg = player.getRepresentationUnfogged() + ", you need to remove "
                    + tokenCost + " command token" + (tokenCost == 1 ? "" : "s")
                    + " from your tactic, strategy, or fleet pool. Your current tokens are "
                    + player.getCCRepresentation() + ".";
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), tokenMsg, ButtonHelper.getLoseCCButtons(player));
        }

        // Give a button to draw the relic rather than drawing it automatically
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", use the button to draw a relic.",
                Buttons.green(player.factionButtonChecker() + "drawRelic", "Draw Relic"));
    }
}
