package ti4.service.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.buttons.Buttons;
import ti4.commands.special.SetupNeutralPlayer;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.FoWHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.BreakthroughModel;
import ti4.model.ColorModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.breakthrough.AlRaithService;
import ti4.service.emoji.DiceEmojis;
import ti4.service.fow.GMService;
import ti4.service.rules.ThundersEdgeRulesService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class FractureService {

    public static boolean isFractureInPlay(Game game) {

        return game.getTileFromPlanet("styx") != null
                || Stream.of("frac1", "frac2", "frac3", "frac4", "frac5", "frac6", "frac7")
                        .allMatch(pos -> game.getTileByPosition(pos) != null);
    }

    @ButtonHandler("rollFracture")
    private static void resolveFractureRoll(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String bt = player.getBreakthroughID();
        if (buttonID.contains("_")) bt = buttonID.split("_")[1];

        int result = new Die(0).getResult();
        if ("cabalbt".equals(bt)) {
            String msg = player.getRepresentation(false, false)
                    + " has _Al'Raith Ix Ianovar_ so The Fracture enters automatically"
                    + "! Ingress tokens will automatically have been placed in their position on the map, if there were no choices to be made.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            spawnFracture(event, game);
            spawnIngressTokens(event, game, player, bt);
            AlRaithService.serveBeginCabalBreakthroughButtons(event, game, player);
        } else {
            if (result == 1 || result == 10) { // success
                String msg = player.getRepresentation(false, false) + " rolled a " + DiceEmojis.getGreenDieEmoji(result)
                        + "! The Fracture is now in play! Ingress tokens will automatically have been placed in their position on the map, if there were no choices to be made.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                spawnFracture(event, game);
                spawnIngressTokens(event, game, player, bt);
            } else if (result == 6) {
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

    public static void spawnFracture(GenericInteractionCreateEvent event, Game game) {
        if (isFractureInPlay(game)) return;
        List<String> fracture = Arrays.asList(
                "fracture1", "fracture2", "fracture3", "fracture4", "fracture5", "fracture6", "fracture7");
        List<String> positions = Arrays.asList("frac1", "frac2", "frac3", "frac4", "frac5", "frac6", "frac7");

        Player neutral = game.getPlayerFromColorOrFaction("neutral");
        if (neutral == null) {
            List<String> unusedColors =
                    game.getUnusedColors().stream().map(ColorModel::getName).toList();
            String color = new SetupNeutralPlayer().pickNeutralColor(unusedColors);
            game.setupNeutralPlayer(color);
            neutral = game.getPlayerFromColorOrFaction("neutral");
        }
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
        for (TechnologyType type : techTypesToAddIngress) {
            List<Tile> tilesWithSkip = getTilesWithSkipAndNoIngressAndNotAdding(game, type, automaticAdds);
            if (tilesWithSkip.size() <= numberOfIngressPerTechType) continue;

            List<Button> buttons = new ArrayList<>(tilesWithSkip.stream()
                    .map(tile -> {
                        String id = player.finChecker() + "addIngressToken_" + tile.getPosition() + "_" + countPer;
                        String label = "Add Ingress To " + tile.getRepresentationForButtons(game, player);
                        return Buttons.red(id, label, type.emoji());
                    })
                    .toList());

            String msg = game.isFowMode()
                    ? GMService.gmPing(game)
                    : player.getRepresentation() + ", please a system with a " + type.emoji()
                            + " to place an Ingress token.";
            buttons.add(Buttons.gray("deleteButtons", "Done Resolving"));
            MessageHelper.sendMessageToChannelWithButtons(
                    game.isFowMode() ? GMService.getGMChannel(game) : player.getCorrectChannel(), msg, buttons);
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(
                        player.getPrivateChannel(),
                        player.getRepresentationUnfogged()
                                + ", buttons to resolve Ingress tokens for The Fracture have been sent to the GM.");
            } else {
                MessageHelper.sendMessageToChannel(
                        game.getMainGameChannel(),
                        "## Please do not place more ingress tokens than legal."
                                + " If brought in by breakthrough, that means up to 3 planets per technology type of the breakthrough (6 total)."
                                + " Otherwise, 1 planet per technology type (4 total).");
            }
        }

        ThundersEdgeRulesService.alertTabletalkWithFractureRules(game);
    }

    @ButtonHandler("addIngressToken_")
    private static void addIngressTokenButtonHandler(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {

        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), "Placed an ingress token in " + tile.getRepresentationForButtons() + ".");
        tile.addToken(Constants.TOKEN_INGRESS, "space");

        if (game.isFowMode()) {
            FoWHelper.pingSystem(game, tile.getPosition(), "A new ingress tears into The Fracture.", false);
        }

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
