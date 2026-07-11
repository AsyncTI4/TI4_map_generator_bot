package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.tech.PlayerTechService;

@UtilityClass
class JointResearchAcd2ButtonHandler {

    @ButtonHandler("resolveJointResearch")
    public static void resolveJointResearch(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (String techID : player.getTechs()) {
            TechnologyModel tech = Mapper.getTech(techID);
            if (tech == null || tech.isFactionTech()) {
                continue;
            }
            buttons.add(Buttons.green(player.factionButtonChecker() + "jointResearchTech_" + techID, tech.getName()));
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you own no non-faction technology to share for _Joint"
                            + " Research_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the technology you just gained to share for _Joint"
                        + " Research_.",
                buttons);
    }

    @ButtonHandler("jointResearchTech_")
    public static void resolveJointResearchTech(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String techID = buttonID.replace("jointResearchTech_", "");
        TechnologyModel tech = Mapper.getTech(techID);
        ButtonHelper.deleteMessage(event);
        if (tech == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Joint Research_.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player || p2.hasExactTech(techID)) {
                continue;
            }
            String id = player.factionButtonChecker() + "jointResearchGive_" + p2.getFaction() + "_" + techID;
            if (game.isFowMode()) {
                buttons.add(Buttons.gray(id, p2.getColor()));
            } else {
                buttons.add(Buttons.gray(id, p2.getFactionModel().getShortName())
                        .withEmoji(Emoji.fromFormatted(p2.getFactionEmoji())));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", no other player can gain _" + tech.getName()
                            + "_ for _Joint Research_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose which player gains _" + tech.getName()
                        + "_ for _Joint Research_.",
                buttons);
    }

    @ButtonHandler("jointResearchGive_")
    public static void resolveJointResearchGive(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("jointResearchGive_", "").split("_", 2);
        ButtonHelper.deleteMessage(event);
        if (parts.length < 2) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        String techID = parts[1];
        TechnologyModel tech = Mapper.getTech(techID);
        if (target == null || tech == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Joint Research_.");
            return;
        }
        if (target.hasExactTech(techID)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    target.getRepresentationNoPing() + " already has _" + tech.getName() + "_.");
            return;
        }

        PlayerTechService.addTech(event, game, target, techID);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " used _Joint Research_ to give _" + tech.getName() + "_ to "
                        + target.getRepresentationNoPing() + ".");
    }
}
