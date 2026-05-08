package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans;

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
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.combat.CombatRollService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.transaction.SendPromissoryService;

@UtilityClass
public class DreamButtonHandler {

    private static final String NEXUS_TOKEN = "beansnexus";
    private static final String LITURGY_I_UNIT = "dream_destroyer";
    private static final String LITURGY_II_UNIT = "dream_destroyer2";
    private static final String LITURGY_II_TECH = "bedreamdd";
    private static final String BACK_TO_LITURGY_MENU_BUTTON_ID = "dream_liturgy_menu_back";

    public static void offerLiturgyButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        Tile tile = getActiveLiturgyTile(game, player);
        if (tile == null) {
            return;
        }
        sendLiturgyMenu(game, player);
    }

    @ButtonHandler("dream_liturgy_menu")
    public static void showLiturgyMenu(ButtonInteractionEvent event, Game game, Player player) {
        Tile tile = getActiveLiturgyTile(game, player);
        if (tile == null) return;

        ButtonHelper.deleteMessage(event);
        sendLiturgyMenu(game, player);
    }

    private static void sendLiturgyMenu(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("dream_offer_add_nexus", "Add Nexus Token"));
        buttons.add(Buttons.blue("dream_offer_move_nexus", "Move Nexus Token"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " you may resolve _Liturgy_ unit ability now by placing or moving 1 nexus token.",
                buttons);
    }

    @ButtonHandler("dream_offer_add_nexus")
    public static void offerAddNexusButtons(ButtonInteractionEvent event, Game game, Player player) {
        Tile activeTile = getActiveLiturgyTile(game, player);
        if (activeTile == null) return;

        ButtonHelper.deleteMessage(event);
        if (countNexusTokens(game) >= 3) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " cannot add a nexus token because all 3 are already on the map.");
            return;
        }

        List<Tile> tilesWithUnits =
                hasLiturgyII(player, activeTile) ? getTilesContainingPlayersUnits(game, player) : List.of(activeTile);
        List<Button> buttons = new ArrayList<>();
        for (Tile tileWithUnits : tilesWithUnits) {
            buttons.add(Buttons.green(
                    "dream_add_nexus" + tileWithUnits.getPosition(),
                    "Place nexus in " + tileWithUnits.getRepresentationForButtons(game, player)));
        }
        buttons.add(Buttons.gray(BACK_TO_LITURGY_MENU_BUTTON_ID, "Back"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + " choose where to add a nexus token:",
                buttons);
    }

    @ButtonHandler("dream_offer_move_nexus")
    public static void offerMoveNexusButtons(ButtonInteractionEvent event, Game game, Player player) {
        Tile activeTile = getActiveLiturgyTile(game, player);
        if (activeTile == null) return;
        ButtonHelper.deleteMessage(event);
        List<Tile> tilesWithUnits =
                hasLiturgyII(player, activeTile) ? getTilesContainingPlayersUnits(game, player) : List.of(activeTile);
        List<Tile> tilesWithNexusTokens = getTilesContainingNexusTokens(game);
        List<Button> buttons = new ArrayList<>();
        for (Tile fromTile : tilesWithNexusTokens) {
            for (Tile toTile : tilesWithUnits) {
                if (fromTile.getPosition().equals(toTile.getPosition())) {
                    continue;
                }
                buttons.add(Buttons.blue(
                        "dream_move_nexus" + fromTile.getPosition() + "_to_" + toTile.getPosition(),
                        "Move from " + fromTile.getRepresentationForButtons(game, player) + " to "
                                + toTile.getRepresentationForButtons(game, player)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no valid nexus token moves available right now.");
            return;
        }
        buttons.add(Buttons.gray(BACK_TO_LITURGY_MENU_BUTTON_ID, "Back"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + " choose where to move a nexus token:",
                buttons);
    }

    static boolean hasLiturgyII(Player player, Tile tile) {
        return ButtonHelper.doesPlayerHaveUnitHere(LITURGY_II_UNIT, player, tile)
                || (player.hasTech(LITURGY_II_TECH)
                        && ButtonHelper.doesPlayerHaveUnitHere(LITURGY_I_UNIT, player, tile));
    }

    static List<Tile> getTilesContainingPlayersUnits(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.containsPlayersUnits(player))
                .toList();
    }

    static int countNexusTokens(Game game) {
        String tokenId = Mapper.getTokenID(NEXUS_TOKEN);
        int count = 0;
        for (Tile tile : game.getTileMap().values()) {
            count += (int) tile.getSpaceUnitHolder().getTokenList().stream()
                    .filter(tokenId::equals)
                    .count();
        }
        return count;
    }

    static List<Tile> getTilesContainingNexusTokens(Game game) {
        String tokenId = Mapper.getTokenID(NEXUS_TOKEN);
        return game.getTileMap().values().stream()
                .filter(tile ->
                        tile.getSpaceUnitHolder().getTokenList().stream().anyMatch(tokenId::equals))
                .toList();
    }

    private static Tile getActiveLiturgyTile(Game game, Player player) {
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            return null;
        }
        String activeSystem = game.getActiveSystem();
        if (activeSystem == null || activeSystem.isBlank()) {
            return null;
        }
        Tile tile = game.getTileByPosition(activeSystem);
        if (tile == null) {
            return null;
        }
        if (!ButtonHelper.doesPlayerHaveUnitHere(LITURGY_I_UNIT, player, tile)
                && !ButtonHelper.doesPlayerHaveUnitHere(LITURGY_II_UNIT, player, tile)) {
            return null;
        }
        return tile;
    }

    @ButtonHandler("dream_add_nexus")
    public static void addNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.replace("dream_add_nexus", "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find that system.");
            return;
        }
        String tokenId = Mapper.getTokenID(NEXUS_TOKEN);
        tile.addToken(tokenId, "space");
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + " added a nexus token to " + tile.getRepresentationForButtons(game, player)
                        + ".");
    }

    @ButtonHandler("dream_move_nexus")
    public static void moveNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String data = buttonID.replace("dream_move_nexus", "");
        String[] parts = data.split("_to_");
        if (parts.length != 2) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse nexus move request.");
            return;
        }
        Tile fromTile = game.getTileByPosition(parts[0]);
        Tile toTile = game.getTileByPosition(parts[1]);
        if (fromTile == null || toTile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find one of those systems.");
            return;
        }
        String tokenId = Mapper.getTokenID(NEXUS_TOKEN);
        if (!fromTile.removeToken(tokenId, "space")) {
            MessageHelper.sendMessageToEventChannel(event, "The source system does not contain a nexus token.");
            return;
        }
        toTile.addToken(tokenId, "space");
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + " moved a nexus token to "
                        + toTile.getRepresentationForButtons(game, player) + ".");
    }

    @ButtonHandler("dream_remove_nexus")
    public static void removeNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        // The Dreaming Throne is not eligible to resolve The Waking
        if (player != null && "dream".equalsIgnoreCase(player.getFaction())) {
            MessageHelper.sendMessageToEventChannel(event, "The Dreaming Throne may not resolve _The Waking_.");
            return;
        }
        String position = buttonID.replace("dream_remove_nexus", "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find that system.");
            return;
        }
        // Only valid during statusHomework phase
        if (!"statusHomework".equalsIgnoreCase(game.getPhaseOfGame())) {
            MessageHelper.sendMessageToEventChannel(
                    event, "You can only resolve _The Waking_ during StatusHomework phase.");
            return;
        }

        // Validate that this tile contains both the player's ships and a nexus token
        boolean hasShips =
                FoWHelper.playerHasShipsInSystem(player, tile) || FoWHelper.playerHasActualShipsInSystem(player, tile);
        if (!hasShips || !tileContainsNexusToken(game, tile)) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "You can only resolve _The Waking_ in a system that contains both your ships and a Dreaming Throne nexus token.");
            return;
        }

        // Find the actual token string present on the tile that corresponds to our nexus token
        String mappedTokenId = Mapper.getTokenID(NEXUS_TOKEN);
        String tokenToRemove = null;
        for (String t : tile.getSpaceUnitHolder().getTokenList()) {
            if (t == null) continue;
            if (mappedTokenId != null && mappedTokenId.equalsIgnoreCase(t)) {
                tokenToRemove = t;
                break;
            }
            if (NEXUS_TOKEN.equalsIgnoreCase(t)) {
                tokenToRemove = t;
                break;
            }
            String key = Mapper.getTokenKey(t);
            if (key != null && NEXUS_TOKEN.equalsIgnoreCase(key)) {
                tokenToRemove = t;
                break;
            }
        }

        if (tokenToRemove == null) {
            MessageHelper.sendMessageToEventChannel(event, "That system does not contain a nexus token.");
            return;
        }

        boolean removed = tile.removeToken(tokenToRemove, "space");
        if (!removed) {
            MessageHelper.sendMessageToEventChannel(event, "Failed to remove the nexus token from that system.");
            return;
        }

        // Record that this player has used The Waking this round so they cannot remove another
        try {
            game.setStoredValue("theWakingRemovedFor" + player.getFaction() + "Round" + game.getRound(), "removed");
        } catch (Exception ignored) {
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + " removed a nexus token from "
                        + tile.getRepresentationForButtons(game, player) + ".");
    }

    public static void offerTheWakingButtons(Game game) {
        // Only offer The Waking during statusHomework
        if (!"statusHomework".equalsIgnoreCase(game.getPhaseOfGame())) return;

        for (Player player : game.getRealPlayers()) {
            // Dreaming Throne is not eligible for The Waking
            if (player != null && "dream".equalsIgnoreCase(player.getFaction())) continue;
            // Skip players who already removed a nexus token this round
            String key = "theWakingRemovedFor" + player.getFaction() + "Round" + game.getRound();
            String val = game.getStoredValue(key);
            if (val != null && !val.isBlank()) continue;

            List<Tile> eligibleTiles = game.getTileMap().values().stream()
                    .filter(tile -> tileContainsNexusToken(game, tile))
                    .filter(tile -> FoWHelper.playerHasShipsInSystem(player, tile)
                            || FoWHelper.playerHasActualShipsInSystem(player, tile))
                    .toList();
            if (eligibleTiles.isEmpty()) continue;

            List<Button> buttons = new ArrayList<>();
            for (Tile tile : eligibleTiles) {
                buttons.add(Buttons.red(
                        "dream_remove_nexus_" + tile.getPosition(),
                        "Remove nexus from " + tile.getRepresentationForButtons(game, player)));
            }
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " may resolve _The Waking_ now: remove 1 nexus token from a system that contains both your ships and a nexus token.",
                    buttons);
        }
    }

    /**
     * Offer the Visions promissory (bepndream) to the owner at the start of a tactical action.
     * The owner may return the card to the Dreaming Throne player to remove 1 nexus token from
     * a system that contains a planet they control.
     */
    public static void offerVisionsPromissoryAtTacticalStart(Game game, Player player) {
        // Only offer to players who actually own the promissory and are not the Dreaming Throne
        if (player == null) return;
        if ("dream".equalsIgnoreCase(player.getFaction())) return;
        if (!player.getPromissoryNotes().containsKey("bepndream")) return;

        // Find eligible tiles: contains a planet controlled by the player AND contains a nexus token
        List<Tile> eligible = game.getTileMap().values().stream()
                .filter(tile -> !tile.getPlanetUnitHolders().isEmpty())
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .anyMatch(p -> player.getPlanets().contains(p.getName())))
                .filter(tile -> tileContainsNexusToken(game, tile, true))
                .toList();
        if (eligible.isEmpty()) return;

        List<Button> buttons = new ArrayList<>();
        for (Tile t : eligible) {
            buttons.add(Buttons.green(
                    "promissory_bepndream_return_" + t.getPosition(),
                    "Return Visions to Dream & remove nexus from " + t.getRepresentationForButtons(game, player)));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + " may return Visions to the Dreaming Throne to remove 1 nexus token:",
                buttons);
    }

    @ButtonHandler("promissory_bepndream_return_")
    public static void resolveVisionsPromissory(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (player == null) return;
        // Only non-Dream players who own the promissory may use it
        if ("dream".equalsIgnoreCase(player.getFaction())) {
            MessageHelper.sendMessageToEventChannel(event, "The Dreaming Throne may not resolve this promissory.");
            return;
        }
        if (!player.getPromissoryNotes().containsKey("bepndream")) {
            MessageHelper.sendMessageToEventChannel(event, "You do not own Visions.");
            return;
        }

        String pos = buttonID.replace("promissory_bepndream_return_", "");
        Tile tile = game.getTileByPosition(pos);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find that system.");
            return;
        }

        if (!tile.getPlanetUnitHolders().stream()
                .anyMatch(p -> player.getPlanets().contains(p.getName()))) {
            MessageHelper.sendMessageToEventChannel(event, "You do not control a planet in that system.");
            return;
        }

        if (!tileContainsNexusToken(game, tile, true)) {
            MessageHelper.sendMessageToEventChannel(event, "That system does not contain a nexus token.");
            return;
        }

        // Find Dreaming Throne player (owner)
        Player dreamOwner = game.getPNOwner("bepndream");
        if (dreamOwner == null) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Could not find the Dreaming Throne player to return the card to.");
            return;
        }

        // Use canonical service to transfer the promissory (handles play-area -> owner and hand cases)
        SendPromissoryService.sendPromissoryToPlayer(event, game, player, dreamOwner, "bepndream");

        // Remove actual nexus token string robustly
        String mappedTokenId = Mapper.getTokenID(NEXUS_TOKEN);
        String tokenToRemove = null;
        for (String t : tile.getSpaceUnitHolder().getTokenList()) {
            if (t == null) continue;
            if (mappedTokenId != null && mappedTokenId.equalsIgnoreCase(t)) {
                tokenToRemove = t;
                break;
            }
            if (NEXUS_TOKEN.equalsIgnoreCase(t)) {
                tokenToRemove = t;
                break;
            }
            String key = Mapper.getTokenKey(t);
            if (key != null && NEXUS_TOKEN.equalsIgnoreCase(key)) {
                tokenToRemove = t;
                break;
            }
        }

        if (tokenToRemove == null) {
            MessageHelper.sendMessageToEventChannel(event, "Failed to locate a nexus token to remove.");
            return;
        }

        boolean removed = tile.removeToken(tokenToRemove, "space");
        if (!removed) {
            MessageHelper.sendMessageToEventChannel(event, "Failed to remove the nexus token from that system.");
            return;
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + " returned Visions to " + dreamOwner.getRepresentation()
                        + " and removed a nexus token from " + tile.getRepresentationForButtons(game, player) + ".");
    }

    static List<Tile> getTilesContainingNexusTokensWithPlayersShips(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasShipsInSystem(player, tile))
                .filter(tile -> tileContainsNexusToken(game, tile, true))
                .toList();
    }

    /**
     * Check whether a system contains a nexus token. If includeFlagship is true, a Dream flagship
     * present in the system also counts as a nexus token (used by abilities other than Liturgy I/II).
     */
    private static boolean tileContainsNexusToken(Game game, Tile tile, boolean includeFlagship) {
        String tokenId = Mapper.getTokenID(NEXUS_TOKEN);
        boolean hasToken = tile.getSpaceUnitHolder().getTokenList().stream()
                .anyMatch(token -> (tokenId != null && tokenId.equals(token))
                        || NEXUS_TOKEN.equalsIgnoreCase(token)
                        || NEXUS_TOKEN.equalsIgnoreCase(Mapper.getTokenKey(token)));
        if (hasToken) return true;
        if (!includeFlagship) return false;
        // Treat Dream flagship as a nexus token when includeFlagship is true
        for (Player p : game.getRealPlayers()) {
            if (!"dream".equalsIgnoreCase(p.getFaction())) continue;
            if (ButtonHelper.doesPlayerHaveFSHere("dream_flagship", p, tile)) return true;
        }
        return false;
    }

    // Backwards-compatible helper for internal calls that want the default behavior (includeFlagship=false)
    private static boolean tileContainsNexusToken(Game game, Tile tile) {
        return tileContainsNexusToken(game, tile, false);
    }

    public static List<Button> getIncomprehensibleFormButtons(Game game, Player p1, Player p2, Tile tile) {
        List<Button> out = new ArrayList<>();
        if (tile == null) return out;
        // Include Dream flagship as counting for the button (flagship counts as nexus for abilities other than Liturgy)
        if (!tileContainsNexusToken(game, tile, true)) return out;
        // Show button only if either combatant is Dreaming Throne
        boolean p1IsDream = p1 != null && "dream".equalsIgnoreCase(p1.getFaction());
        boolean p2IsDream = p2 != null && "dream".equalsIgnoreCase(p2.getFaction());
        if (!p1IsDream && !p2IsDream) return out;

        out.add(Buttons.gray(
                "incomprehensible_form_" + tile.getPosition(), "Use Incomprehensible Form", FactionEmojis.dream));
        return out;
    }

    @ButtonHandler("incomprehensible_form_")
    public static void presentIncomprehensibleChoices(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String pos = buttonID.replace("incomprehensible_form_", "");
        Tile tile = game.getTileByPosition(pos);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find that system.");
            return;
        }

        // Determine presence of physical token and flagship
        String mappedTokenId = Mapper.getTokenID(NEXUS_TOKEN);
        boolean hasToken = tile.getSpaceUnitHolder().getTokenList().stream()
                .anyMatch(t -> t != null
                        && ((mappedTokenId != null && mappedTokenId.equalsIgnoreCase(t))
                                || NEXUS_TOKEN.equalsIgnoreCase(t)
                                || NEXUS_TOKEN.equalsIgnoreCase(Mapper.getTokenKey(t))));
        boolean hasFlagship = false;
        for (Player p : game.getRealPlayers()) {
            if (!"dream".equalsIgnoreCase(p.getFaction())) continue;
            if (ButtonHelper.doesPlayerHaveFSHere("dream_flagship", p, tile)) {
                hasFlagship = true;
                break;
            }
        }

        if (!hasToken && !hasFlagship) {
            MessageHelper.sendMessageToEventChannel(event, "There is no nexus token or Dream flagship in that system.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        if (hasToken) {
            buttons.add(
                    Buttons.gray("incomprehensible_form_use_token_" + pos, "Remove Nexus Token", FactionEmojis.dream));
        }
        if (hasFlagship) {
            buttons.add(Buttons.blue(
                    "incomprehensible_form_use_flagship_" + pos, "Use Dream Flagship as Nexus", FactionEmojis.dream));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToEventChannelWithButtons(
                event,
                player.getRepresentation() + " choose whether to remove the nexus token or use the Dream flagship:",
                buttons);
    }

    @ButtonHandler("incomprehensible_form_use_flagship_")
    @ButtonHandler("incomprehensible_form_use_token_")
    public static void useIncomprehensibleForm(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        boolean choiceFlagship = buttonID.contains("_use_flagship_");
        String pos = buttonID.replace("incomprehensible_form_use_flagship_", "")
                .replace("incomprehensible_form_use_token_", "")
                .replace("incomprehensible_form_", "");
        Tile tile = game.getTileByPosition(pos);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find that system.");
            return;
        }

        // Find and remove the actual nexus token string on the tile
        String mappedTokenId = Mapper.getTokenID(NEXUS_TOKEN);
        String tokenToRemove = null;
        for (String t : tile.getSpaceUnitHolder().getTokenList()) {
            if (t == null) continue;
            if (mappedTokenId != null && mappedTokenId.equalsIgnoreCase(t)) {
                tokenToRemove = t;
                break;
            }
            if (NEXUS_TOKEN.equalsIgnoreCase(t)) {
                tokenToRemove = t;
                break;
            }
            String key = Mapper.getTokenKey(t);
            if (key != null && NEXUS_TOKEN.equalsIgnoreCase(key)) {
                tokenToRemove = t;
                break;
            }
        }

        boolean usedFlagship = false;
        if (choiceFlagship) {
            // user explicitly chose to use the flagship
            boolean hasDreamFS = false;
            for (Player p : game.getRealPlayers()) {
                if (!"dream".equalsIgnoreCase(p.getFaction())) continue;
                if (ButtonHelper.doesPlayerHaveFSHere("dream_flagship", p, tile)) {
                    hasDreamFS = true;
                    break;
                }
            }
            if (!hasDreamFS) {
                MessageHelper.sendMessageToEventChannel(event, "There is no Dream flagship in that system to use.");
                return;
            }
            usedFlagship = true;
        } else {
            // token path (either explicitly chosen or default)
            if (tokenToRemove == null) {
                MessageHelper.sendMessageToEventChannel(event, "There is no nexus token in the active system.");
                return;
            }
            boolean removed = tile.removeToken(tokenToRemove, "space");
            if (!removed) {
                MessageHelper.sendMessageToEventChannel(
                        event, "Failed to remove the nexus token from the active system.");
                return;
            }
        }

        // Announce the effect in the same channel/thread without deleting the combat buttons
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation()
                        + " used **Incomprehensible Form** in " + tile.getRepresentationForButtons(game, player)
                        + " to remove a nexus token from the active system instead of destroying a ship. If the Dreaming Throne player removed their flagship, the hit produced is assigned by the Dreaming Throne player.");

        // If the nexus was represented by the Dream flagship, produce 1 hit that the Dream player assigns to the
        // opponent's ships
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
}
