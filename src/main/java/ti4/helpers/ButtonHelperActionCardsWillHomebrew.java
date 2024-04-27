package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.agenda.DrawAgenda;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.SentACRandom;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.explore.ExpFrontier;
import ti4.commands.explore.ExploreAndDiscard;
import ti4.commands.special.NaaluCommander;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.RemoveCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.ExploreModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

public class ButtonHelperActionCardsWillHomebrew {

    public static void resolveTombRaiders(Player player, Game game, ButtonInteractionEvent event) {
        List<String> types = new ArrayList<String>(List.of("hazardous", "cultural", "industrial", "frontier"));
        StringBuilder sb = new StringBuilder();
        for (String type : types) {
            String cardID = game.drawExplore(type);

            ExploreModel card = Mapper.getExplore(cardID);
            String cardType = card.getResolution();
            if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
                sb.append(new ExploreAndDiscard().displayExplore(cardID)).append(System.lineSeparator());
                sb.append(player.getRepresentation(true, true)).append(" Gained relic fragment\n");
                player.addFragment(cardID);
                game.purgeExplore(cardID);
            } else {
                sb.append("Looked at the top of the " + type + " deck and saw that it was not a relic frag");
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), new ExploreAndDiscard().displayExplore(cardID));
            }
        }
        ButtonHelper.fullCommanderUnlockCheck(player, game, "kollecc", event);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, game), sb.toString());
        event.getMessage().delete().queue();
    }

    public static void resolveTechnologicalBreakthrough(Player player, Game game, ButtonInteractionEvent event) {
        for (String planet : player.getPlanetsAllianceMode()) {
            if (ButtonHelper.checkForTechSkips(game, planet)) {
                player.refreshPlanet(planet);
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(),
            ButtonHelper.getIdent(player) + " readied every tech skip planet");
        event.getMessage().delete().queue();

    }

    public static void resolveStrandedShipStep1(Player player, Game activeGame, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = getStrandedShipButtons(activeGame, player);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true) + " tell the bot which tile you wish to place a ghost ship in",
            buttons);
    }

    public static void resolveStrandedShipStep2(Player player, Game activeGame, ButtonInteractionEvent event,
        String buttonID) {
        Tile tile = activeGame.getTileByPosition(buttonID.split("_")[1]);
        tile = MoveUnits.flipMallice(event, tile, activeGame);
        new AddUnits().unitParsing(event, player.getColor(), tile, "cruiser", activeGame);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            ButtonHelper.getIdent(player) + " put a cruiser in " + tile.getRepresentation());

        // If Empyrean Commander is in game check if unlock condition exists
        Player p2 = activeGame.getPlayerFromLeader("empyreancommander");
        if (p2 != null) {
            if (!p2.hasLeaderUnlocked("empyreancommander")) {
                ButtonHelper.commanderUnlockCheck(p2, activeGame, "empyrean", event);
            }
        }
    }

    public static void resolveSpatialCollapseStep1(Player player, Game activeGame, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = getSpatialCollapseTilesStep1(activeGame, player);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true) + " tell the bot which tile with your ships you wish to swap with an adjacent system",
            buttons);
    }

    public static List<Button> getSpatialCollapseTilesStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPosition().contains("t") || tile.getPosition().contains("b") || tile.isHomeSystem() || tile.getTileID().equalsIgnoreCase("18")) {
                continue;
            }
            if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                buttons.add(Button.secondary("spatialCollapseStep2_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
            }

        }
        return buttons;
    }

    public static void resolveSpatialCollapseStep2(Game game, Player player, ButtonInteractionEvent event,
        String buttonID) {
        String pos1 = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        Tile tile1 = game.getTileByPosition(pos1);
        for (String tilePos2 : FoWHelper.getAdjacentTiles(game, pos1, player, false, false)) {
            Tile tile = game.getTileByPosition(tilePos2);
            if (tile.getPosition().contains("t") || tile.getPosition().contains("b") || tile == tile1 || tile.isHomeSystem() || tile.getTileID().equalsIgnoreCase("18")) {
                continue;
            }

            buttons.add(Button.secondary("spatialCollapseStep3_" + pos1 + "_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player)));

        }
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, game),
            player.getRepresentation(true, true) + " Chose the tile you want to swap places with "
                + tile1.getRepresentationForButtons(game, player),
            buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveSpatialCollapseStep3(Game game, Player player, ButtonInteractionEvent event,
        String buttonID) {
        String position = buttonID.split("_")[1];
        String position2 = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(position);
        Tile tile2 = game.getTileByPosition(position2);
        tile.setPosition(position2);
        tile2.setPosition(position);
        game.setTile(tile);
        game.setTile(tile2);
        game.rebuildTilePositionAutoCompleteList();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, game),
            player.getRepresentation(true, true) + " Chose to swap "
                + tile2.getRepresentationForButtons(game, player) + " with "
                + tile.getRepresentationForButtons(game, player));
        event.getMessage().delete().queue();
    }

    public static List<Button> getStrandedShipButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPlanetUnitHolders().size() == 0 && FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game)) {
                buttons.add(Button.success("strandedShipStep2_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));

            }
        }
        return buttons;
    }

    public static void resolveSideProject(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p1 = player;
        if (p1.getStrategicCC() > 0) {
            String successMessage = ButtonHelper.getIdent(p1) + " Reduced strategy pool CCs by 1 ("
                + (p1.getStrategicCC()) + "->" + (p1.getStrategicCC() - 1) + ")";
            p1.setStrategicCC(p1.getStrategicCC() - 1);
            ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, game, event);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
        } else {
            String successMessage = ButtonHelper.getIdent(p1) + " Exhausted Scepter";
            p1.addExhaustedRelic("emelpar");
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
        }

        event.getMessage().delete().queue();
        ButtonHelperFactionSpecific.offerWinnuStartingTech(player, game);
    }

    public static void resolveBrutalOccupationStep1(Player player, Game game, ButtonInteractionEvent event) {

        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getExhaustedPlanets()) {
            buttons.add(Button.success("brutalOccupationStep2_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, game), player.getRepresentation() + " choose the target of brutal occupation");
    }

    public static void resolveBrutalOccupationStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String planet = buttonID.split("_")[1];
        player.refreshPlanet(planet);
        List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, game), player);
        if (!buttons.isEmpty()) {
            String message = player.getFactionEmoji() + " Click button to explore "
                + Helper.getPlanetRepresentation(planet, game);
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, game),
                message, buttons);
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, game), player.getRepresentation() + " refreshed and explored " + Helper.getPlanetRepresentation(planet, game));
    }

}
