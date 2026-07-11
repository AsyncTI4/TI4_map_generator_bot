package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.onyxxa;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.RelicHelper;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.UnlockLeaderService;

@UtilityClass
public class OnyxxaLeaderHandler {

    public static void postAgentMoveShipButtons(Game game, Player targetPlayer) {
        String msg = targetPlayer.getRepresentationUnfogged()
                + ", please choose the system that you wish to move a ship from.";
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTiles()) {
            if (FoWHelper.playerHasShipsInSystem(targetPlayer, tile)) {
                buttons.add(Buttons.green(
                        "moveShipToAdjacentSystemStep2_" + tile.getPosition() + "_agent",
                        tile.getRepresentationForButtons(game, targetPlayer)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(targetPlayer.getCorrectChannel(), msg, buttons);
    }

    // Commander: Strategist Kreel — unlock condition:
    // "Resolve the primary ability of a strategy card in 1 of your neighbors' play areas."
    public static void offerCommanderUnlockButton(Player player) {
        Button button = Buttons.gray(
                player.factionButtonChecker() + "onyxxaCommanderUnlock",
                "Unlock Strategist Kreel",
                FactionEmojis.onyxxa);
        MessageHelper.sendMessageToChannelWithButton(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", only click if you resolved the primary of a strategy card in a neighbor's play area.",
                button);
    }

    @ButtonHandler("onyxxaCommanderUnlock")
    public static void handleCommanderUnlock(ButtonInteractionEvent event, Game game, Player player) {
        UnlockLeaderService.unlockLeader("onyxxacommander", game, player);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static void onDrawRelic(Player player) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", gain 1 command token from _Strategist Kreel_.",
                ButtonHelper.getGainCCButtons(player));
    }

    public static void onGainFracturePlanet(
            GenericInteractionCreateEvent event, Player player, Game game, Player previousOwner) {
        if (previousOwner != null && "onyxxa".equals(previousOwner.getFaction())) return;
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation(false, false) + " gains 1 relic from _Strategist Kreel_.");
        RelicHelper.drawRelicAndNotify(player, event, game);
    }

    public static void postHeroMoveShipButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTiles()) {
            if (FoWHelper.playerHasActualShipsInSystem(player, tile)) {
                buttons.add(Buttons.green(
                        "moveShipToAdjacentSystemStep2_" + tile.getPosition() + "_combat",
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        buttons.add(Buttons.red("onyxxaHeroDone", "Done Resolving"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.toString() + ", please use buttons to resolve your _Titles Are Silly_ hero ability.",
                buttons);
    }

    @ButtonHandler("onyxxaHeroDone")
    public static void onyxxaHeroDone(ButtonInteractionEvent event, Player player, Game game) {
        ButtonHelper.deleteMessage(event);
        for (Tile tile : game.getTiles()) {
            if (FoWHelper.playerHasActualShipsInSystem(player, tile)
                    && FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                StartCombatService.combatCheck(game, event, tile);
            }
        }
    }
}
