package ti4.service.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import org.jetbrains.annotations.NotNull;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.FoWHelper;
import ti4.helpers.RandomHelper;
import ti4.image.TileHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.BreakthroughModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.model.TileModel;
import ti4.service.breakthrough.AlRaithService;
import ti4.service.emoji.DiceEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.fow.GMService;
import ti4.service.rules.ThundersEdgeRulesService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class FractureService {

    /** Set once ingress placement has been carried out, so removing the last token cannot re-open the roll. */
    public static final String INGRESS_PLACED = "fractureIngressPlaced";

    /** Is there any Fracture space on the board, whether from a tile or from a fracture token? */
    public static boolean isFractureInPlay(Game game) {
        return game.getTileMap().values().stream().anyMatch(Tile::isFracture);
    }

    /** Is there any ingress token anywhere on the board? */
    public static boolean anyIngressOnBoard(Game game) {
        return game.getTileMap().values().stream()
                .anyMatch(tile -> tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_INGRESS));
    }

    /**
     * Tiles alone do not mean The Fracture is in play - in fog the GM places every token by hand, so the region can
     * sit on the board with placement never offered, and the roll must still be available until it has been.
     */
    private static boolean ingressPlacementResolved(Game game) {
        return anyIngressOnBoard(game) || "true".equals(game.getStoredValue(INGRESS_PLACED));
    }

    /** Positional check, for map rendering only - use {@link #isFractureInPlay} for rules. */
    public static boolean isFractureRegionOnMap(Game game) {
        return Stream.of("frac1", "frac2", "frac3", "frac4", "frac5", "frac6", "frac7")
                .anyMatch(pos -> game.getTileByPosition(pos) != null);
    }

    public static boolean isFractureExpandedRegionOnMap(Game game) {
        return Stream.of(
                        "frac8", "frac9", "frac10", "frac11", "frac12", "frac13", "frac14", "frac15", "frac16",
                        "frac17", "frac18", "frac19", "frac20", "frac21", "frac22", "frac23", "frac24", "frac25")
                .anyMatch(pos -> game.getTileByPosition(pos) != null);
    }

    public static boolean canFractureEnterPlay(Game game) {
        if (game.isCosmicConvergenceMode()) {
            return true;
        }
        return !game.isNoFractureMode() && !(isFractureInPlay(game) && ingressPlacementResolved(game));
    }

    public static String whyFractureCannotEnterPlay(Game game) {
        if (isFractureInPlay(game)) return "The Fracture is already in play.";
        if (game.isNoFractureMode()) return "The Fracture is disabled for this game, so it cannot enter play.";
        return "";
    }

    @ButtonHandler("rollFracture")
    private static void resolveFractureRoll(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String bt = player.getBreakthroughID();
        if (buttonID.contains("_")) bt = buttonID.split("_")[1];

        if (!canFractureEnterPlay(game)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), whyFractureCannotEnterPlay(game));
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        int result = new Die(0).getResult();
        if ("cabalbt".equals(bt)) {
            String msg = player.getRepresentation(false, false)
                    + " has _Al'Raith Ix Ianovar_ so The Fracture enters automatically"
                    + "! Ingress tokens will automatically have been placed in their position on the map, if there were no choices to be made.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            if (spawnFracture(event, game)) spawnIngressTokens(event, game, player, bt);
            AlRaithService.serveBeginCabalBreakthroughButtons(event, game, player);
        } else {
            if (result == 1 || result == 10) { // success
                if (game.isCosmicConvergenceMode()) {
                    int countPer = 1;
                    boolean goneThrough = false;
                    List<TechnologyType> techTypesToAddIngress = new ArrayList<>();
                    techTypesToAddIngress.addAll(TechnologyType.mainFour);
                    for (TechnologyType type : techTypesToAddIngress) {
                        List<Tile> tilesWithSkip =
                                getTilesWithSkipAndNoIngressAndNotAdding(game, type, new ArrayList<>());
                        if (tilesWithSkip.isEmpty()) continue;

                        // The GM presses these in fog, so they must not carry the player's FFCC_ ownership prefix
                        String prefix = game.isFowMode() ? "" : player.factionButtonChecker();
                        List<Button> buttons = new ArrayList<>(tilesWithSkip.stream()
                                .map(tile -> {
                                    String id = prefix + "addIngressToken_" + tile.getPosition() + "_" + countPer;
                                    String label = "Add Ingress To " + tile.getRepresentationForButtons(game, player);
                                    return Buttons.red(id, label, type.emoji());
                                })
                                .toList());

                        String msg = game.isFowMode()
                                ? GMService.gmPing(game) + ", please choose a system with a " + type.emoji()
                                        + " to place an Ingress token for "
                                        + player.getRepresentationUnfoggedNoPing() + "."
                                : player.getRepresentation() + ", please choose a system with a " + type.emoji()
                                        + " to place an Ingress token.";
                        buttons.add(Buttons.gray("deleteButtons", "Done Resolving"));
                        MessageHelper.sendMessageToChannelWithButtons(
                                game.isFowMode() ? GMService.getGMChannel(game) : player.getCorrectChannel(),
                                msg,
                                buttons);
                        if (!game.isFowMode() && !goneThrough) {
                            goneThrough = true;
                            MessageHelper.sendMessageToChannel(
                                    game.getMainGameChannel(), "## Please only place one ingress token.");
                        }
                    }
                    String newTileID = "";
                    List<String> redTilesToPullFrom = new ArrayList<>(List.of(
                            "ef1", "ef2", "ef3", "ef4", "ef5", "ef6", "ef7", "ef8", "ef9", "ef10", "ef11", "ef12"));
                    List<Button> buttons = new ArrayList<>();
                    redTilesToPullFrom.removeAll(game.getTileMap().values().stream()
                            .map(Tile::getTileID)
                            .toList());
                    List<String> tileToPullFromUnshuffled = new ArrayList<>(redTilesToPullFrom);
                    Collections.shuffle(redTilesToPullFrom);
                    List<MessageEmbed> tileEmbeds = new ArrayList<>();
                    List<String> ids = new ArrayList<>();

                    String tileID = redTilesToPullFrom.getFirst();
                    ids.add(tileID);
                    TileModel tile = TileHelper.getTileById(tileID);
                    tileEmbeds.add(tile.getRepresentationEmbed(false));

                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            player.getRepresentation() + " drew 1 fracture tile from this list:\n> "
                                    + tileToPullFromUnshuffled);

                    event.getMessageChannel()
                            .sendMessageEmbeds(tileEmbeds)
                            .queue(Consumers.nop(), BotLogger::catchRestError);

                    newTileID = ids.getFirst();
                    List<String> directlyAdjacentTiles = new ArrayList<>(
                            List.of("frac9", "frac10", "frac14", "frac15", "frac19", "frac20", "frac22", "frac23"));
                    if ("ef2".equalsIgnoreCase(newTileID)) {
                        directlyAdjacentTiles = new ArrayList<>(List.of("tl", "tr", "bl", "br"));
                    }
                    for (String pos : directlyAdjacentTiles) {
                        Tile tile2 = game.getTileByPosition(pos);
                        if (tile2 == null) {
                            buttons.add(Buttons.green(
                                    player.factionButtonChecker() + "cosmicConStep3_" + newTileID + "_" + pos, pos));
                        }
                    }

                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCorrectChannel(),
                            player.getRepresentation()
                                    + ", please choose the location for the new tile (it probably doesnt matter too much).",
                            buttons);

                } else {
                    String msg =
                            player.getRepresentation(false, false) + " rolled a " + DiceEmojis.getGreenDieEmoji(result)
                                    + "! The Fracture is now in play! Ingress tokens will automatically have been placed in their position on the map, if there were no choices to be made.";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                    if (spawnFracture(event, game)) spawnIngressTokens(event, game, player, bt);
                }
            } else if (result == 6 && RandomHelper.isOneInX(10)) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        "> \"Thunder rolled...\n> It rolled a " + DiceEmojis.getGrayDieEmoji(6)
                                + ".\"\n> \\- Terry Pratchett, _Guards! Guards!_");
            } else { // fail
                String msg = player.getRepresentation(true, false) + " rolled a " + DiceEmojis.getGrayDieEmoji(result)
                        + ", better luck next time.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    /** Brings The Fracture into play for an automatic effect, saying so only if it is switched off for the game. */
    public static boolean enterPlayOrExplain(
            GenericInteractionCreateEvent event, Game game, @NotNull Player player, String breakthrough) {
        if (!spawnFracture(event, game)) {
            // Already in play is the normal case for a later effect, so stay quiet unless it is actually disabled
            if (!isFractureInPlay(game)) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), whyFractureCannotEnterPlay(game));
            }
            return false;
        }
        spawnIngressTokens(event, game, player, breakthrough);
        return true;
    }

    /** Places The Fracture if it is allowed to enter play. Returns true if The Fracture is on the board afterwards. */
    public static boolean spawnFracture(GenericInteractionCreateEvent event, Game game) {
        if (!canFractureEnterPlay(game)) return false;
        // The tiles can already be down with ingress placement never resolved (fog, or a hand-placed region).
        // Returning true lets the caller go on to place ingress without duplicating tiles and neutral units.
        if (isFractureInPlay(game)) return true;
        List<String> fracture = Arrays.asList(
                "fracture1", "fracture2", "fracture3", "fracture4", "fracture5", "fracture6", "fracture7");
        List<String> positions = Arrays.asList("frac1", "frac2", "frac3", "frac4", "frac5", "frac6", "frac7");

        Player neutral = game.getNeutral();
        String neutralColorID = neutral.getColorID();
        List<String> units =
                Arrays.asList("2 ca, 2 inf c", "", "", "2 dn, 1 dd, 3 inf s", "", "", "1 cv, 4 ff, 1 inf l, 1 inf p");
        for (int i = 0; i < 7; ++i) {
            String pos = positions.get(i);
            Tile tile = new Tile(fracture.get(i), pos);
            // add tokens
            if (i == 0) tile.addToken("token_relictoken.png", "cocytus");
            if (i == 3) tile.addToken("token_relictoken.png", "styx");
            if (i == 6) {
                tile.addToken("token_relictoken.png", "lethe");
                tile.addToken("token_relictoken.png", "phlegethon");
            }
            // set tile
            game.setTile(tile);
            // add units
            AddUnitService.addUnits(event, game.getTileByPosition(pos), game, neutralColorID, units.get(i));
        }
        return true;
    }

    public static void spawnIngressTokens(
            GenericInteractionCreateEvent event, Game game, @NotNull Player player, String breakthrough) {
        List<Tile> automaticAdds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        Tile extra = game.getTileFromPlanet(Constants.THUNDERSEDGE);
        if (extra == null) extra = game.getMecatolTile();
        if (extra != null) automaticAdds.add(extra);
        if (extra == null) errors.add("Could not find Thunder's Edge or Mecatol Rex.");

        List<TechnologyType> techTypesToAddIngress = new ArrayList<>();
        int numberOfIngressPerTechType = 3;
        BreakthroughModel bt = player.getBreakthroughModel(breakthrough);
        if (bt != null && bt.hasSynergy()) {
            techTypesToAddIngress.addAll(bt.getSynergy());
        } else {
            techTypesToAddIngress.addAll(TechnologyType.mainFour);
            numberOfIngressPerTechType = 1;
        }

        for (int rpt = 0; rpt < techTypesToAddIngress.size(); rpt++) {
            for (TechnologyType type : techTypesToAddIngress) {
                List<Tile> tilesWithSkip = getTilesWithSkipAndNoIngressAndNotAdding(game, type, automaticAdds);
                if (!game.isFowMode() && tilesWithSkip.size() <= numberOfIngressPerTechType)
                    automaticAdds.addAll(tilesWithSkip);
            }
        }

        StringBuilder automatic =
                new StringBuilder("## ").append(game.getPing()).append(" - The Fracture is now in play.");
        if (!game.isFowMode() && !automaticAdds.isEmpty()) {
            automatic.append(" Automatically added ingress tokens to the following tiles:");
        }
        for (Tile t : automaticAdds) {
            t.addToken(Constants.TOKEN_INGRESS, "space");
            if (!game.isFowMode()) {
                automatic.append("\n- ").append(t.getRepresentationForButtons(game, player));
            }
        }
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), automatic.toString());

        int countPer = numberOfIngressPerTechType;
        boolean anyChoicesToMake = false;
        boolean goneThrough = false;
        for (TechnologyType type : techTypesToAddIngress) {
            List<Tile> tilesWithSkip = getTilesWithSkipAndNoIngressAndNotAdding(game, type, automaticAdds);
            if (tilesWithSkip.isEmpty()) continue;
            // Nothing auto-places in fog, so the GM gets buttons for every type
            if (!game.isFowMode() && tilesWithSkip.size() <= numberOfIngressPerTechType) continue;
            anyChoicesToMake = true;

            // The GM presses these in fog, so they must not carry the player's FFCC_ ownership prefix
            String prefix = game.isFowMode() ? "" : player.factionButtonChecker();
            List<Button> buttons = new ArrayList<>(tilesWithSkip.stream()
                    .map(tile -> {
                        String id = prefix + "addIngressToken_" + tile.getPosition() + "_" + countPer;
                        String label = "Add Ingress To " + tile.getRepresentationForButtons(game, player);
                        return Buttons.red(id, label, type.emoji());
                    })
                    .toList());

            String msg = game.isFowMode()
                    ? GMService.gmPing(game) + ", please choose a system with a " + type.emoji()
                            + " to place an Ingress token for "
                            + player.getRepresentationUnfoggedNoPing() + "."
                    : player.getRepresentation() + ", please choose a system with a " + type.emoji()
                            + " to place an Ingress token.";
            buttons.add(Buttons.gray("deleteButtons", "Done Resolving"));
            MessageHelper.sendMessageToChannelWithButtons(
                    game.isFowMode() ? GMService.getGMChannel(game) : player.getCorrectChannel(), msg, buttons);
            if (!game.isFowMode() && !goneThrough) {
                goneThrough = true;
                MessageHelper.sendMessageToChannel(
                        game.getMainGameChannel(),
                        "## Please do not place more ingress tokens than legal."
                                + " If brought in by breakthrough, that means up to 3 planets per technology type of the breakthrough (6 total)."
                                + " Otherwise, 1 planet per technology type (4 total).");
            }
        }

        if (game.isFowMode() && anyChoicesToMake) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", The Fracture is entering play. In fog games Ingress tokens are placed by the GM"
                            + " — please wait for " + GMService.gmPing(game)
                            + " to resolve it, and do not place any yourself.");
        }

        // Mark placement resolved even when nothing was placed - The Fracture is in play, there simply were no
        // legal targets. The GM panel and /map ingress_tokens are the escape hatch if it needs re-running.
        game.setStoredValue(INGRESS_PLACED, "true");
        ThundersEdgeRulesService.alertTabletalkWithFractureRules(game);
    }

    @ButtonHandler("addIngressToken_")
    private static void addIngressTokenButtonHandler(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {

        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        if (tile == null) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        tile.addToken(Constants.TOKEN_INGRESS, "space");

        String confirmation = "Placed an ingress token in " + tile.getRepresentationForButtons() + ".";
        if (game.isFowMode()) {
            // In fog the presser is the GM, who need not be a seated player
            MessageHelper.sendMessageToChannel(GMService.getGMChannel(game), confirmation);
            FoWHelper.pingSystem(game, tile.getPosition(), "A new ingress tears into The Fracture.", false);
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), confirmation);
        }

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    /**
     * Adds the Fracture category to the GM panel. Present for the whole game in Thunder's Edge games, so it stays
     * re-summonable with {@code /fow gm} rather than being buried in whatever was posted at setup.
     */
    public static void addFractureGMButton(Game game, List<Button> buttons) {
        if (!game.isThundersEdge()) return;
        buttons.add(Buttons.green("gmFracture", "Fracture Activation", MiscEmojis.Ingress));
    }

    @ButtonHandler(value = "gmFracture", save = false)
    private static void gmFracturePanel(ButtonInteractionEvent event, Game game) {
        String state = "**The Fracture**\n" + "- region on the board: `" + isFractureInPlay(game) + "`\n"
                + "- ingress tokens placed: `" + anyIngressOnBoard(game) + "`\n"
                + "- placement already resolved: `" + ingressPlacementResolved(game) + "`\n"
                + "- breakthrough roll available: `" + canFractureEnterPlay(game) + "`";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("gmPlaceIngress_", "Place Ingress Tokens", MiscEmojis.Ingress));
        buttons.add(Buttons.DONE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), state, buttons);
    }

    // Deliberately no `save = false` - this mutates the board, and the flag picks the write lock.
    // Deliberately no Player parameter and no factionButtonChecker() prefix - the GM need not be seated.
    @ButtonHandler("gmPlaceIngress_")
    private static void gmPlaceIngress(ButtonInteractionEvent event, Game game, String buttonID) {
        String faction = buttonID.replace("gmPlaceIngress_", "");
        if (faction.isEmpty()) {
            List<Button> buttons = new ArrayList<>();
            for (Player p : game.getRealPlayers()) {
                buttons.add(Buttons.green(
                        "gmPlaceIngress_" + p.getFaction(),
                        StringUtils.capitalize(p.getColor()) + ", " + p.getUserName(),
                        p.getFactionEmoji()));
            }
            buttons.add(Buttons.DONE_DELETE_BUTTONS);
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getChannel(),
                    "Whose breakthrough is placing the Ingress tokens?"
                            + " Their synergy sets how many go down per technology type.",
                    buttons);
            return;
        }
        Player player = game.getPlayerFromColorOrFaction(faction);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not find player `" + faction + "`.");
            return;
        }
        spawnIngressTokens(event, game, player, player.getBreakthroughID());
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    private static List<Tile> getTilesWithSkipAndNoIngressAndNotAdding(
            Game game, TechnologyType type, List<Tile> alreadyCounted) {
        List<Tile> tiles = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (alreadyCounted.contains(tile)) continue;
            if (tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_INGRESS)) continue;
            if (tile.getPlanetUnitHolders().stream().anyMatch(p -> p.hasTechSpecialty(type))) tiles.add(tile);
        }
        return tiles;
    }
}
