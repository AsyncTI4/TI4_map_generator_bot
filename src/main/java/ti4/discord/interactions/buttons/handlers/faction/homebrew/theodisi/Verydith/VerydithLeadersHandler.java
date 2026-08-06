package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Verydith;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class VerydithLeadersHandler {
    private static final String CHOOSE_PLAYER = "chooseVerydithPlayer_";
    private static final String SELECT_SYSTEM = "selectVerydithHeroSystem_";
    // Agent
    private static final String AGENT_STEP1 = "selectFirstTargetVerydithAgent";
    private static final String AGENT_STEP2 = "selectSecondTargetVerydithAgent_";

    // Agent
    public static Button getVerydithAgentCardsInfoButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + AGENT_STEP1, "Use Seris Kael", FactionEmojis.verydith);
    }

    @ButtonHandler(AGENT_STEP1)
    public static void selectFirstTargetVerydithAgent(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasUnexhaustedLeader("verydithagent")) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + AGENT_STEP2 + target.getColor(),
                    target.getFactionNameOrColor(),
                    target.getFactionEmojiOrColor()));
        }
    }

    // Commander
    public static void checkVerydithCommander(Game activeMap) {
        for (Player player : activeMap.getRealPlayers()) {
            String tokenToAddOrRemove = Constants.VERYDITH_ATTACHMENT_PNG;
            if (activeMap.playerHasLeaderUnlockedOrAlliance(player, "verydithcommander")) {
                for (Tile tile : activeMap.getTileMap().values()) {
                    for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                        if (unitHolder instanceof Planet planet) {
                            if (player.getPlanets().contains(planet.getName())) {
                                for (Player otherPlayer : activeMap.getRealPlayersExcludingThis(player)) {
                                    if (!tile.hasPlayerCC(otherPlayer)
                                            && planet.getTokenList().contains(tokenToAddOrRemove)) {
                                        planet.removeToken(tokenToAddOrRemove);
                                    } else if (tile.hasPlayerCC(otherPlayer)
                                            && !planet.getTokenList().contains(tokenToAddOrRemove)) {
                                        planet.addToken(tokenToAddOrRemove);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Hero
    public static void startVerydithHero(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target == player) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + CHOOSE_PLAYER + target.getColor(),
                    target.getFactionNameOrColor(),
                    target.getFactionEmojiOrColor()));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", select the player whose command token you would like to place in a system.",
                buttons);
    }

    @ButtonHandler(CHOOSE_PLAYER)
    public static void selectVerydithHeroSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String payload = buttonID.replace(CHOOSE_PLAYER, "");
        Player targetPlayer = game.getPlayerFromColorOrFaction(payload);
        if (targetPlayer == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find player.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);

        List<Button> systems = new ArrayList<>();
        String targetCCId = Mapper.getCCID(targetPlayer.getColor());
        for (Tile tile : ButtonHelper.getTilesWithShipsInTheSystem(player, game)) {
            if (tile.hasCC(targetCCId)) {
                continue;
            }

            systems.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_SYSTEM + targetPlayer.getColor() + "|" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", select the system in which to place one of "
                        + targetPlayer.getFactionNameOrColor() + "'s command tokens.",
                systems);
    }

    @ButtonHandler(SELECT_SYSTEM)
    public static void resolveVerydithHero(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String[] payload = buttonID.replace(SELECT_SYSTEM, "").split("\\|", 2);
        if (payload.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String faction = payload[0];
        String tilePos = payload[1];

        Player target = game.getPlayerFromColorOrFaction(faction);
        Tile tile = game.getTileByPosition(tilePos);
        if (target == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find player.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find tile.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String targetCCId = Mapper.getCCID(target.getColor());
        tile.addCC(targetCCId);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " added 1 of " + target.getFactionNameOrColor() + "'s command tokens to "
                        + tile.getRepresentation() + " using _Aranth Vel_, the Verydith Hero.");

        ButtonHelper.deleteMessage(event);
    }
}
