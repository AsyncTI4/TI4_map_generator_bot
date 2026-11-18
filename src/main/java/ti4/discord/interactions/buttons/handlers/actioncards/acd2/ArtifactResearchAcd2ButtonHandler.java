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
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.tech.ListTechService;

@UtilityClass
class ArtifactResearchAcd2ButtonHandler {

    @ButtonHandler("resolveArtifactResearch")
    public static void resolveArtifactResearch(Player player, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        if (player.getFragments().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + " owns no relic fragments, so they cannot discard any for _Artifact Research_.");
            return;
        }
        sendDiscardButtons(player, 0);
    }

    @ButtonHandler("artifactResearchDiscard_")
    public static void artifactResearchDiscard(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("artifactResearchDiscard_", "").split("_", 2);
        ButtonHelper.deleteMessage(event);
        if (parts.length < 2) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Artifact Research_.");
            return;
        }
        int discarded;
        try {
            discarded = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Artifact Research_.");
            return;
        }
        String fragId = parts[1];
        if (!player.getFragments().contains(fragId)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not resolve _Artifact Research_ (missing fragment).");
            return;
        }

        player.removeFragment(fragId);
        game.discardExplore(fragId);
        discarded++;
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " discarded a " + fragmentEmoji(fragId) + fragmentLabel(fragId)
                        + " for _Artifact Research_.");
        if (player.getFragments().isEmpty()) {
            sendTechButtons(player, game, discarded);
        } else {
            sendDiscardButtons(player, discarded);
        }
    }

    @ButtonHandler("artifactResearchDone_")
    public static void artifactResearchDone(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        ButtonHelper.deleteMessage(event);
        try {
            sendTechButtons(player, game, Integer.parseInt(buttonID.replace("artifactResearchDone_", "")));
        } catch (NumberFormatException e) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Artifact Research_.");
        }
    }

    private static void sendDiscardButtons(Player player, int discarded) {
        List<Button> buttons = new ArrayList<>();
        for (String fragId : player.getFragments()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "artifactResearchDiscard_" + discarded + "_" + fragId,
                    "Discard " + fragmentLabel(fragId),
                    fragmentEmoji(fragId)));
        }
        if (discarded > 0) {
            buttons.add(Buttons.blue(
                    player.factionButtonChecker() + "artifactResearchDone_" + discarded,
                    "Done Discarding (" + discarded + ")"));
        } else {
            buttons.add(Buttons.CANCEL_DELETE_BUTTONS);
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose relic fragments to discard for _Artifact Research_"
                        + (discarded > 0 ? " (" + discarded + " discarded so far)" : "") + ".",
                buttons);
    }

    private static void sendTechButtons(Player player, Game game, int discarded) {
        if (discarded < 1) return;
        List<TechnologyModel> techs = getEligibleTechs(player, game, discarded);
        if (techs.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " has no eligible technology with " + discarded
                            + " or fewer prerequisites to gain with _Artifact Research_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose a technology with " + discarded
                        + " or fewer prerequisites to gain with _Artifact Research_.",
                ListTechService.getTechButtons(techs, player, "free"));
    }

    static List<TechnologyModel> getEligibleTechs(Player player, Game game, int discarded) {
        List<TechnologyModel> techs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TechnologyType techType : TechnologyType.mainFive) {
            for (TechnologyModel tech : ListTechService.getAllTechOfAType(game, techType.toString(), player)) {
                if (seen.add(tech.getAlias())
                        && tech.getRequirements().orElse("").length() <= discarded) {
                    techs.add(tech);
                }
            }
        }
        return techs;
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
