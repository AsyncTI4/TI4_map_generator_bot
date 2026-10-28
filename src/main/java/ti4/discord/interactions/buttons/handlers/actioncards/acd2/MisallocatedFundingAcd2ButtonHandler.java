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
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.tech.ListTechService;
import ti4.service.tech.PlayerTechService;

@UtilityClass
class MisallocatedFundingAcd2ButtonHandler {

    private static final String CARD = "_Misallocated Funding_";

    @ButtonHandler("resolveMisallocatedFunding")
    public static void resolveMisallocatedFunding(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target != player && !returnableTechs(target).isEmpty()) {
                buttons.add(FoWHelper.fogSafeTargetButton(
                        player.factionButtonChecker() + "misallocatedFundingPlayer_" + target.getFaction(),
                        "gray",
                        target));
            }
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", no other player owns a non-faction, non-unit upgrade technology for " + CARD + ".");
            return;
        }
        buttons.add(Buttons.CANCEL_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose which player must return a technology for " + CARD + ".",
                buttons);
    }

    @ButtonHandler("misallocatedFundingPlayer_")
    public static void choosePlayer(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("misallocatedFundingPlayer_", ""));
        ButtonHelper.deleteMessage(event);
        if (target == null || target == player) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve " + CARD + ".");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (TechnologyModel tech : returnableTechs(target)) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + "misallocatedFundingTech_" + target.getFaction() + "_"
                            + tech.getAlias(),
                    tech.getName(),
                    tech.getCondensedReqsEmojis(true)));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    target.getRepresentationNoPing() + " no longer owns a non-faction, non-unit upgrade technology for "
                            + CARD + ".");
            return;
        }
        buttons.add(Buttons.CANCEL_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose which of " + target.getRepresentationNoPing()
                        + "'s technologies they must return for " + CARD + ".",
                buttons);
    }

    @ButtonHandler("misallocatedFundingTech_")
    public static void chooseTech(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("misallocatedFundingTech_", "").split("_", 2);
        ButtonHelper.deleteMessage(event);
        Player target = parts.length < 2 ? null : game.getPlayerFromColorOrFaction(parts[0]);
        TechnologyModel returned = parts.length < 2 ? null : Mapper.getTech(parts[1]);
        if (target == null || returned == null || !returnableTechs(target).contains(returned)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve " + CARD + ".");
            return;
        }

        PlayerTechService.removeTech(event, target, returned.getAlias());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " made " + target.getRepresentationNoPing() + " return "
                        + returned.getNameRepresentation() + " to the technology deck using " + CARD + ".");
        offerReplacementResearch(game, target, returned);
    }

    private static void offerReplacementResearch(Game game, Player target, TechnologyModel returned) {
        List<TechnologyModel> options = new ArrayList<>();
        for (TechnologyType type : TechnologyType.mainFour) {
            if (!returned.getTypes().contains(type)) {
                options.addAll(ListTechService.getAllTechOfAType(game, type.toString(), target, false, true));
            }
        }
        if (options.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    target.getRepresentationUnfogged()
                            + " has no researchable non-unit upgrade technology of a different color than "
                            + returned.getNameRepresentation() + " for " + CARD + ".");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged() + ", you must research a technology of a different color than "
                        + returned.getNameRepresentation() + " for " + CARD + ".",
                ListTechService.getTechButtons(options, target, "free"));
    }

    private static List<TechnologyModel> returnableTechs(Player player) {
        return player.getTechs().stream()
                .map(Mapper::getTech)
                .filter(tech -> tech != null && tech.getFaction().orElse("").isEmpty() && !tech.isUnitUpgrade())
                .toList();
    }
}
