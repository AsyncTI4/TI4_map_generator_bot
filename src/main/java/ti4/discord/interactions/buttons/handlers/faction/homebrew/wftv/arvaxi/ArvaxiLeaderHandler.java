package ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.arvaxi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.actioncard.ForceGiveActionCardService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.PurgeHeroService;

@UtilityClass
public class ArvaxiLeaderHandler {

    public static final String HERO_ACTIVE_KEY = "arvaxiHeroActive";

    public static Button getPurgeHeroButton(Player player, String tilePosition) {
        return Buttons.gray(
                "FFCC_" + player.getFaction() + "_purgeArvaxiHero_" + tilePosition,
                "Purge Arvaxi Hero",
                FactionEmojis.arvaxi);
    }

    @ButtonHandler("purgeArvaxiHero_")
    public static void purgeArvaxiHero(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String tilePosition = buttonID.replace("purgeArvaxiHero_", "");
        PurgeHeroService.purgeHeroPreamble(
                event, player, game, "arvaxihero", "Kaelen, the Invincible, the Arvaxi hero");
        game.setStoredValue(HERO_ACTIVE_KEY, player.getFaction() + "_" + tilePosition);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", during this combat each of your ships whose unit upgrade technology you have researched rolls 1 additional die.");
    }

    public static boolean isHeroActiveForCombat(Game game, Player player, Tile tile) {
        return tile != null
                && game.getStoredValue(HERO_ACTIVE_KEY).equals(player.getFaction() + "_" + tile.getPosition());
    }

    public static void sendCombatButtons(Player player, Player opponent, Game game, String msg) {
        List<Button> buttons = new ArrayList<>();
        if (!game.playerHasLeaderUnlockedOrAlliance(player, "arvaxicommander")) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + "arvaxiCommanderOfferUnlock",
                    "Discard AC to Unlock Commander (Upon Win)",
                    FactionEmojis.arvaxi));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    msg + ", a reminder that if you win this combat, you may discard an action card to unlock the "
                            + FactionEmojis.arvaxi + " **Arvaxi Commander**.",
                    buttons);
        } else {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + "arvaxiCommanderStealAC_" + opponent.getFaction(),
                    "Force Opponent to Give 1 AC (Upon Win)",
                    FactionEmojis.arvaxi));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    msg
                            + ", a reminder that if you win this combat, you may force your opponent to give you 1 action card.",
                    buttons);
        }
    }

    @ButtonHandler("arvaxiCommanderStealAC_")
    public static void arvaxiCommanderStealAC(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String opponentFaction = buttonID.replace("arvaxiCommanderStealAC_", "");
        Player opponent = game.getPlayerFromColorOrFaction(opponentFaction);
        if (opponent == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not find opponent, please resolve manually.");
            return;
        }
        ForceGiveActionCardService.sendGiveACButtons(
                player,
                opponent,
                game,
                "Ravok, the Judicial has demanded an action card. Please choose which card to hand over.");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                "Sent " + opponent.getColor()
                        + " the buttons for resolving Ravok, the Judicial, the Arvaxi commander.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("arvaxiCommanderOfferUnlock")
    public static void arvaxiCommanderOfferUnlock(
            ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        List<Button> buttons = ActionCardHelper.getDiscardActionCardButtonsWithSuffix(player, "arvaxicommander");
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentation() + " has no action cards to discard for the " + FactionEmojis.arvaxi
                            + " **Arvaxi Commander** unlock.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", discard 1 action card to unlock the " + FactionEmojis.arvaxi
                        + " **Arvaxi Commander**.",
                buttons);
    }
}
