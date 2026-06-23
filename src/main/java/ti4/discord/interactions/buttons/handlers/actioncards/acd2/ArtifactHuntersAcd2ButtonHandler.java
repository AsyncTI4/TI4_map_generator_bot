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
class ArtifactHuntersAcd2ButtonHandler {

    private static final int RELIC_TG_COST = 4;

    @ButtonHandler("resolveArtifactHunters")
    public static void resolveArtifactHunters(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);

        List<Button> exploreButtons = ButtonHelper.getButtonsToExploreAllPlanets(player, game);
        if (exploreButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you do not control a planet you can explore for _Artifact Hunters_.");
        } else {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", choose 1 planet you control to explore for _Artifact Hunters_.",
                    exploreButtons);
        }

        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", if the resolved card is a relic fragment, you may use this"
                        + " button to spend " + RELIC_TG_COST + " trade goods, purge that fragment, and gain 1 relic.",
                Buttons.green(player.factionButtonChecker() + "artifactHuntersRelic", "Purge Fragment & Gain Relic"));
    }

    @ButtonHandler("artifactHuntersRelic")
    public static void artifactHuntersRelic(Player player, ButtonInteractionEvent event) {
        if (player.getTg() < RELIC_TG_COST) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " does not have " + RELIC_TG_COST
                            + " trade goods to spend for _Artifact Hunters_.");
            return;
        }
        if (player.getFragments().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " has no relic fragments to purge for _Artifact Hunters_.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String fragId : player.getFragments()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "artifactHuntersPurge_" + fragId,
                    "Purge " + fragmentLabel(fragId),
                    fragmentEmoji(fragId)));
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the relic fragment to purge for _Artifact Hunters_ ("
                        + "this spends " + RELIC_TG_COST + " trade goods).",
                buttons);
    }

    @ButtonHandler("artifactHuntersPurge_")
    public static void artifactHuntersPurge(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String fragId = buttonID.replace("artifactHuntersPurge_", "");
        if (player.getTg() < RELIC_TG_COST || !player.getFragments().contains(fragId)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "Could not resolve _Artifact Hunters_ (missing trade goods or fragment).");
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.setTg(player.getTg() - RELIC_TG_COST);
        player.removeFragment(fragId);
        game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + 1);

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " spent " + RELIC_TG_COST + " trade goods and purged a "
                        + fragmentEmoji(fragId) + fragmentLabel(fragId) + " for _Artifact Hunters_.");
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", use the button to draw your relic.",
                Buttons.green(player.factionButtonChecker() + "drawRelic", "Draw Relic"));
    }

    private static String fragmentLabel(String fragId) {
        return switch (Mapper.getExplore(fragId).getType().toLowerCase()) {
            case "cultural" -> "cultural relic fragment";
            case "industrial" -> "industrial relic fragment";
            case "hazardous" -> "hazardous relic fragment";
            default -> "unknown relic fragment";
        };
    }

    private static String fragmentEmoji(String fragId) {
        return switch (Mapper.getExplore(fragId).getType().toLowerCase()) {
            case "cultural" -> ExploreEmojis.CFrag.toString();
            case "industrial" -> ExploreEmojis.IFrag.toString();
            case "hazardous" -> ExploreEmojis.HFrag.toString();
            default -> ExploreEmojis.UFrag.toString();
        };
    }
}
