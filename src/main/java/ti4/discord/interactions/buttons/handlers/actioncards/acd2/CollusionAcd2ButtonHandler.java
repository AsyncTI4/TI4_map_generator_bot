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
import ti4.service.strategycard.StrategyCardSecondaryButtonService;

@UtilityClass
class CollusionAcd2ButtonHandler {

    @ButtonHandler("resolveCollusion")
    public static void resolveCollusion(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target == player || !hasStrategyCard(target)) continue;
            buttons.add(FoWHelper.fogSafeTargetButton(
                    player.factionButtonChecker() + "collusionTarget_" + target.getFaction(), "gray", target, player));
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", no other player has a strategy card for _Collusion_.");
            return;
        }
        buttons.add(Buttons.DONE_RED_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose 1 other player to target with _Collusion_.",
                buttons);
    }

    @ButtonHandler("collusionTarget_")
    public static void resolveCollusionTarget(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring("collusionTarget_".length()));
        ButtonHelper.deleteMessage(event);
        if (target == null || target == player) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Collusion_.");
            return;
        }
        List<Button> buttons = List.of(
                Buttons.green(
                        target.factionButtonChecker() + "collusionConsent_allow_" + player.getFaction(),
                        "Allow Secondary"),
                Buttons.red(
                        target.factionButtonChecker() + "collusionConsent_refuse_" + player.getFaction(), "Refuse"));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged() + ", " + player.getRepresentationNoPing()
                        + " played _Collusion_ targeting you. Will you allow them to resolve the secondary ability of"
                        + " 1 of your strategy cards without spending a command token?",
                buttons);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", waiting for " + target.getRepresentationNoPing()
                        + " to decide on _Collusion_.");
    }

    @ButtonHandler("collusionConsent_")
    public static void resolveCollusionConsent(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.substring("collusionConsent_".length()).split("_", 2);
        ButtonHelper.deleteMessage(event);
        Player acting = parts.length < 2 ? null : game.getPlayerFromColorOrFaction(parts[1]);
        if (acting == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Collusion_.");
            return;
        }
        if (!"allow".equals(parts[0])) {
            MessageHelper.sendMessageToChannel(
                    acting.getCorrectChannel(),
                    acting.getRepresentationUnfogged() + ", " + player.getRepresentationNoPing()
                            + " refused _Collusion_.");
            return;
        }

        List<Button> buttons =
                new ArrayList<>(StrategyCardSecondaryButtonService.getSecondaryAbilityButtons(game, player.getSCs()));
        buttons.add(Buttons.DONE_RED_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                acting.getCorrectChannel(),
                acting.getRepresentationUnfogged() + ", " + player.getRepresentationNoPing()
                        + " allowed _Collusion_. Resolve the secondary ability of 1 of their strategy cards. No"
                        + " command token is spent.",
                buttons);
    }

    private static boolean hasStrategyCard(Player player) {
        return player.getSCs() != null && player.getSCs().stream().anyMatch(sc -> sc > 0);
    }
}
