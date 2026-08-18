package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.dream;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.combat.CombatRollService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class DreamAbilitiesHandler {
    private static final String DREAM_FLAGSHIP_UNIT = "dream_flagship";
    private static final String NEXUS_TOKEN_ALIAS = "beansnexus";

    public static boolean hasNexusToken(Tile tile) {
        if (tile == null) {
            return false;
        }

        String tokenId = Mapper.getTokenID(NEXUS_TOKEN_ALIAS);
        return tile.getSpaceUnitHolder().getTokenList().stream()
                .anyMatch(token -> (tokenId != null && tokenId.equalsIgnoreCase(token))
                        || NEXUS_TOKEN_ALIAS.equalsIgnoreCase(token)
                        || NEXUS_TOKEN_ALIAS.equalsIgnoreCase(Mapper.getTokenKey(token)));
    }

    public static boolean ignoresNebula(Player player, Game game, Tile tile) {
        return player != null
                && (DreamPromissoryHandler.hasVisionsInPlayArea(player)
                        || (player.hasAbility("dream_nexus") && hasNexusToken(tile)));
    }

    public static boolean hasNexusTokenOrDreamFlagship(Game game, Tile tile) {
        return hasNexusToken(tile) || getDreamFlagshipPlayerInTile(game, tile) != null;
    }

    // The Waking

    @ButtonHandler("dream_remove_nexus_")
    public static void removeNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game.getRealPlayers().stream().noneMatch(dreamPlayer -> dreamPlayer.hasAbility("the_waking"))) {
            MessageHelper.sendMessageToEventChannel(event, "The Waking is not in play.");
            return;
        }
        String position = buttonID.replace("dream_remove_nexus_", "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find that system.");
            return;
        }

        boolean hasShips =
                FoWHelper.playerHasShipsInSystem(player, tile) || FoWHelper.playerHasActualShipsInSystem(player, tile);
        if (!hasShips || !hasNexusToken(tile)) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "You can only resolve **The Waking** in a system that contains both your ships and a Dreaming Throne nexus token.");
            return;
        }

        if (!removePhysicalNexusToken(tile)) {
            MessageHelper.sendMessageToEventChannel(event, "Failed to remove the nexus token from that system.");
            return;
        }

        game.setStoredValue("theWakingRemovedFor" + player.getFaction() + "Round" + game.getRound(), "removed");
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + ", removed a nexus token from "
                        + tile.getRepresentationForButtons(game, player) + ".");
    }

    public static void offerTheWakingButtons(Game game) {
        if (game.getRealPlayers().stream().noneMatch(player -> player.hasAbility("the_waking"))) return;
        for (Player player : game.getRealPlayers()) {
            List<Tile> eligibleTiles = getTheWakingEligibleTiles(game, player);
            if (eligibleTiles.isEmpty()) continue;

            List<Button> buttons = new ArrayList<>();
            for (Tile tile : eligibleTiles) {
                buttons.add(Buttons.red(
                        "dream_remove_nexus_" + tile.getPosition(),
                        "Remove Nexus From " + tile.getRepresentationForButtons(game, player)));
            }
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", may resolve **The Waking** now: remove 1 nexus token from a system that contains both your ships and a nexus token.",
                    buttons);
        }
    }

    private static List<Tile> getTheWakingEligibleTiles(Game game, Player player) {
        if (player == null || player.hasAbility("the_waking")) return List.of();
        String key = "theWakingRemovedFor" + player.getFaction() + "Round" + game.getRound();
        if (!game.getStoredValue(key).isBlank()) return List.of();

        return game.getTileMap().values().stream()
                .filter(DreamAbilitiesHandler::hasNexusToken)
                .filter(tile -> FoWHelper.playerHasShipsInSystem(player, tile)
                        || FoWHelper.playerHasActualShipsInSystem(player, tile))
                .toList();
    }

    // Incomprehensible Form

    public static List<Button> getIncomprehensibleFormButtons(Game game, Player p1, Player p2, Tile tile) {
        return Stream.of(p1, p2)
                .filter(player -> player != null && player.hasAbility("incomprehensible_form"))
                .map(player -> Buttons.gray(
                        player.factionButtonChecker() + "incomprehensible_form_" + tile.getPosition(),
                        "Use Incomprehensible Form",
                        FactionEmojis.dream))
                .toList();
    }

    @ButtonHandler("incomprehensible_form_")
    public static void presentIncomprehensibleChoices(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasAbility("incomprehensible_form")) {
            MessageHelper.sendMessageToEventChannel(event, "Only a player with Incomprehensible Form may use this.");
            return;
        }
        String pos = buttonID.replace("incomprehensible_form_", "");
        Tile tile = game.getTileByPosition(pos);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find that system.");
            return;
        }

        boolean hasToken = hasNexusToken(tile);
        boolean hasFlagship = getDreamFlagshipPlayerInTile(game, tile) != null;
        if (!hasToken && !hasFlagship) {
            MessageHelper.sendMessageToEventChannel(event, "There is no nexus token or Dream flagship in that system.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        if (hasToken) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + "incomprehensible_form_use_token_" + pos,
                    "Remove Nexus Token",
                    FactionEmojis.dream));
        }
        if (hasFlagship) {
            buttons.add(Buttons.blue(
                    player.factionButtonChecker() + "incomprehensible_form_use_flagship_" + pos,
                    "Remove Dream Flagship",
                    FactionEmojis.dream));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToEventChannelWithButtons(
                event,
                player.getRepresentation() + ", choose whether to remove the nexus token or the Dream flagship:",
                buttons);
    }

    @ButtonHandler("incomprehensible_form_use_flagship_")
    @ButtonHandler("incomprehensible_form_use_token_")
    public static void useIncomprehensibleForm(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasAbility("incomprehensible_form")) {
            MessageHelper.sendMessageToEventChannel(event, "Only a player with Incomprehensible Form may use this.");
            return;
        }
        boolean choiceFlagship = buttonID.contains("_use_flagship_");
        String pos = buttonID.replace("incomprehensible_form_use_flagship_", "")
                .replace("incomprehensible_form_use_token_", "")
                .replace("incomprehensible_form_", "");
        Tile tile = game.getTileByPosition(pos);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find that system.");
            return;
        }

        boolean usedFlagship = false;
        if (choiceFlagship) {
            Player dreamPlayer = getDreamFlagshipPlayerInTile(game, tile);
            if (dreamPlayer == null) {
                MessageHelper.sendMessageToEventChannel(event, "There is no Dream flagship in that system to use.");
                return;
            }
            var removedFlagship = RemoveUnitService.removeUnit(
                    event, tile, game, dreamPlayer, tile.getSpaceUnitHolder(), UnitType.Flagship, 1);
            if (removedFlagship.isEmpty()) {
                MessageHelper.sendMessageToEventChannel(event, "Failed to remove the Dream flagship from that system.");
                return;
            }
            usedFlagship = true;
        } else if (!removePhysicalNexusToken(tile)) {
            MessageHelper.sendMessageToEventChannel(event, "Failed to remove the nexus token from the active system.");
            return;
        }

        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation()
                        + ", used **Incomprehensible Form** in " + tile.getRepresentationForButtons(game, player)
                        + " to remove a nexus token from the active system instead of destroying a ship. If the Dreaming Throne player removed their flagship, the hit produced is assigned by the Dreaming Throne player.");

        if (usedFlagship) {
            String playersInCombat = game.getStoredValue("factionsInCombat");
            if (!playersInCombat.isBlank() && playersInCombat.contains(player.getFaction())) {
                for (Player opponent : game.getRealPlayersExcludingThis(player)) {
                    if (playersInCombat.contains(opponent.getFaction())) {
                        CombatRollService.sendSpaceAssignHitsButtons(event, game, opponent, tile, 1);
                        break;
                    }
                }
            }
        }
    }

    private static String getPhysicalNexusToken(Tile tile) {
        String tokenId = Mapper.getTokenID(NEXUS_TOKEN_ALIAS);
        return tile.getSpaceUnitHolder().getTokenList().stream()
                .filter(token -> (tokenId != null && tokenId.equalsIgnoreCase(token))
                        || NEXUS_TOKEN_ALIAS.equalsIgnoreCase(token)
                        || NEXUS_TOKEN_ALIAS.equalsIgnoreCase(Mapper.getTokenKey(token)))
                .findFirst()
                .orElse(null);
    }

    private static boolean removePhysicalNexusToken(Tile tile) {
        String token = getPhysicalNexusToken(tile);
        return token != null && tile.removeToken(token, "space");
    }

    private static Player getDreamFlagshipPlayerInTile(Game game, Tile tile) {
        return game.getRealPlayers().stream()
                .filter(player -> player.hasUnit(DREAM_FLAGSHIP_UNIT))
                .filter(player -> ButtonHelper.doesPlayerHaveFSHere(DREAM_FLAGSHIP_UNIT, player, tile))
                .findFirst()
                .orElse(null);
    }
}
