package ti4.buttons.handlers.actioncards;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.model.ExploreModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.planet.FlipTileService;
import ti4.service.unit.AddUnitService;

@UtilityClass
class ActionCardDeck2ButtonHandler {

    @ButtonHandler("resolveDataArchive")
    public static void resolveDataArchive(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game, true);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", use buttons to explore planet #1.",
                buttons);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", use buttons to explore planet #2 (different planet from #1).",
                buttons);
        if (game.getPhaseOfGame().toLowerCase().contains("agenda")) {
            for (String planet : player.getPlanets()) {
                player.exhaustPlanet(planet);
            }
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.getFactionEmoji() + " exhausted all planets.");
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveDefenseInstallation")
    public static void resolveDefenseInstallation(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = player.getPlanets().stream()
                .map(planetName -> ButtonHelper.getUnitHolderFromPlanetName(planetName, game))
                .filter(Objects::nonNull)
                .filter(planet -> planet.getUnitCount(Units.UnitType.Pds, player.getColor()) == 0)
                .map(planet -> Buttons.green(
                        "defenseInstallationStep2_" + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)))
                .toList();
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose the planet you wish to put 1 PDS on.",
                buttons);
    }

    @ButtonHandler("defenseInstallationStep2_")
    public static void resolveDefenseInstallationStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "pds " + planet);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " put 1 PDS on " + Helper.getPlanetRepresentation(planet, game));
    }

    @ButtonHandler("resolveBoardingParty")
    public static void resolveBoardingParty(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        String type = "sling";
        String pos = game.getActiveSystem();
        List<Button> buttons = Helper.getPlaceUnitButtons(
                event, player, game, game.getTileByPosition(pos), type, "placeOneNDone_skipbuild");
        String message = player.getRepresentation() + ", use the buttons to place the 1 ship you killed under 5 cost. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
    }

    @ButtonHandler("resolveRapidFulfillment")
    public static void resolveRapidFulfillment(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        String type = "sling";
        String pos = game.getActiveSystem();
        List<Button> buttons = Helper.getPlaceUnitButtons(
                event, player, game, game.getTileByPosition(pos), type, "placeOneNDone_dontskip");
        String message = player.getRepresentation()
                + ", use the buttons to place up to 2 ships that have a combined cost of 3 or less.";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
    }

    @ButtonHandler("resolveSisterShip")
    public static void resolveSisterShip(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        List<Button> buttons = new ArrayList<>();
        Set<Tile> tiles = ButtonHelper.getTilesOfUnitsWithProduction(player, game);

        for (Tile tile : tiles) {
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                String buttonID = "produceOneUnitInTile_" + tile.getPosition() + "_sling";
                Button tileButton = Buttons.green(buttonID, tile.getRepresentationForButtons(game, player));
                buttons.add(tileButton);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Please choose which system you wish to produce a ship in. The bot will not know that it is reduced cost and limited to a specific ship type, but you know that. ",
                buttons);
    }

    @ButtonHandler("resolveChainReaction")
    public static void resolveChainReaction(ButtonInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Effect changed, so old implementation was deprecated. Roll manually.");
        //        StringBuilder msg = new StringBuilder("The _Chain Reaction_ rolled: ");
        //        int currentRequirement = 7;
        //        Die die;
        //        while ((die = new Die(currentRequirement)).isSuccess()) {
        //            hits++;
        //            currentRequirement++;
        //            msg.append(die.getResult()).append(" :boom: ");
        //        }
        //        msg.append(die.getResult());
        //        List<Button> buttons = new ArrayList<>();
        //        if (game.getActiveSystem() != null && !game.getActiveSystem().isEmpty()) {
        //            buttons.add(Buttons.red("getDamageButtons_" + game.getActiveSystem() + "_" + "combat", "Assign
        // Hit" + (hits == 1 ? "" : "s")));
        //        }
        //        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg + "\n " +
        // player.getRepresentation() +
        //            " your opponent needs to assign " + hits + " hit" + (hits == 1 ? "" : "s"), buttons);
    }

    @ButtonHandler("resolveFlawlessStrategy")
    public static void resolveFlawlessStrategy(Player player, ButtonInteractionEvent event) {
        List<Button> scButtons = new ArrayList<>();
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        if (player.getSCs().contains(2)) {
            scButtons.add(Buttons.green("diploRefresh2", "Ready 2 Planets"));
        }
        if (player.getSCs().contains(3)) {
            scButtons.add(Buttons.gray("draw2 AC", "Draw 2 Action Cards", CardEmojis.ActionCard));
        }
        if (player.getSCs().contains(4)) {
            scButtons.add(Buttons.green("construction_spacedock", "Place 1 space dock", UnitEmojis.spacedock));
            scButtons.add(Buttons.green("construction_pds", "Place 1 PDS", UnitEmojis.pds));
        }
        if (player.getSCs().contains(5)) {
            scButtons.add(Buttons.gray("sc_refresh", "Replenish Commodities", MiscEmojis.comm));
        }
        if (player.getSCs().contains(6)) {
            scButtons.add(Buttons.green("warfareBuild", "Build At Home"));
        }
        if (player.getSCs().contains(7)) {
            scButtons.add(Buttons.GET_A_TECH);
        }
        if (player.getSCs().contains(8)) {
            scButtons.add(Buttons.gray("non_sc_draw_so", "Draw Secret Objective", CardEmojis.SecretObjective));
        }
        scButtons.add(Buttons.red("deleteButtons", "Done resolving"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), player.getRepresentation() + ", use the buttons to resolve.", scButtons);
    }

    @ButtonHandler("resolveAncientTradeRoutes")
    public static void resolveAncientTradeRoutes(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        player.setCommodities(player.getCommodities() + 2);
        ButtonHelperAgents.toldarAgentInitiation(game, player, 2);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getFactionEmoji() + " gained 2 commodities.");
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("ancientTradeRoutesStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("ancientTradeRoutesStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Don't Give Commodities"));
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose the player you wish to give 2 commodities to.",
                buttons);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose a __different__ player you wish to give 2 commodities to.",
                buttons);
    }

    @ButtonHandler("resolveArmsDeal")
    public static void resolveArmsDeal(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();

        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player || !player.getNeighbouringPlayers(true).contains(p2)) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("armsDealStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("armsDealStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose which neighbor gets 1 cruiser and 1 destroyer.",
                buttons);
    }

    @ButtonHandler("armsDealStep2_")
    public static void resolveArmsDealStep2(Game game, ButtonInteractionEvent event, String buttonID) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) return;
        List<Button> buttons = new ArrayList<>(
                Helper.getTileWithShipsPlaceUnitButtons(p2, game, "cruiser", "placeOneNDone_skipbuild"));
        buttons.add(Buttons.red("deleteButtons", "Don't Place"));
        MessageHelper.sendMessageToChannelWithButtons(
                p2.getCorrectChannel(),
                p2.getRepresentation() + ", please choose where you wish to place the _Arms Deal_ cruiser.",
                buttons);
        buttons = new ArrayList<>(
                Helper.getTileWithShipsPlaceUnitButtons(p2, game, "destroyer", "placeOneNDone_skipbuild"));
        buttons.add(Buttons.red("deleteButtons", "Don't Place"));
        MessageHelper.sendMessageToChannelWithButtons(
                p2.getCorrectChannel(),
                p2.getRepresentation() + ", please choose where you wish to place the _Arms Deal_ destroyer.",
                buttons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("ancientTradeRoutesStep2_")
    public static void resolveAncientTradeRoutesStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) return;
        p2.setCommodities(p2.getCommodities() + 2);
        ButtonHelperAgents.toldarAgentInitiation(game, p2, 2);
        MessageHelper.sendMessageToChannel(
                p2.getCorrectChannel(),
                p2.getFactionEmoji() + " gained 2 commodities due to _Ancient Trade Routes_ and may transact with "
                        + player.getFactionEmojiOrColor() + " for this turn.");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveTombRaiders")
    public static void resolveTombRaiders(Player player, Game game, ButtonInteractionEvent event) {
        player.gainCommodities(2);
        List<String> types = new ArrayList<>(List.of("hazardous", "cultural", "industrial", "frontier"));
        StringBuilder sb = new StringBuilder();
        sb.append(player.getRepresentationUnfogged()).append(" gained 2 commodities and:");
        for (String type : types) {
            String cardId = game.drawExplore(type);
            ExploreModel card = Mapper.getExplore(cardId);
            String cardType = card.getResolution();
            sb.append("\nRevealed '")
                    .append(card.getName())
                    .append("' from the top of the ")
                    .append(type)
                    .append(" deck and ");
            if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
                sb.append("gained it.");
                player.addFragment(cardId);
                game.purgeExplore(cardId);
            } else {
                sb.append("discarded it.");
            }
        }
        CommanderUnlockCheckService.checkPlayer(player, "kollecc");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("innovation")
    public static void resolveInnovation(Player player, Game game, ButtonInteractionEvent event) {
        for (String planet : player.getPlanetsAllianceMode()) {
            if (ButtonHelper.checkForTechSkips(game, planet)) {
                player.refreshPlanet(planet);
            }
        }
        MessageHelper.sendMessageToChannel(
                event.getChannel(), player.getFactionEmoji() + " readied every planet with a technology specialty.");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("strandedShipStep1")
    public static void resolveStrandedShipStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = getStrandedShipButtons(game, player);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose the system you wish to place the _Ghost Ship_ in.",
                buttons);
    }

    @ButtonHandler("strandedShipStep2_")
    public static void resolveStrandedShipStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        tile = FlipTileService.flipTileIfNeeded(event, tile, game);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "cruiser");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " put 1 cruiser in " + tile.getRepresentation() + ".");

        // If Empyrean Commander is in game check if unlock condition exists
        Player p2 = game.getPlayerFromLeader("empyreancommander");
        CommanderUnlockCheckService.checkPlayer(p2, "empyrean");
    }

    private static List<Button> getSpatialCollapseTilesStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPosition().contains("t")
                    || tile.getPosition().contains("b")
                    || tile.isHomeSystem(game)
                    || tile.isMecatol()) {
                continue;
            }
            if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                buttons.add(Buttons.gray(
                        "spatialCollapseStep2_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    @ButtonHandler("spatialCollapseStep2_")
    public static void resolveSpatialCollapseStep2(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos1 = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        Tile tile1 = game.getTileByPosition(pos1);
        for (String tilePos2 : FoWHelper.getAdjacentTiles(game, pos1, player, false, false)) {
            Tile tile = game.getTileByPosition(tilePos2);
            if (tile.getPosition().contains("t")
                    || tile.getPosition().contains("b")
                    || tile == tile1
                    || tile.isHomeSystem(game)
                    || tile.isMecatol()) {
                continue;
            }

            buttons.add(Buttons.gray(
                    "spatialCollapseStep3_" + pos1 + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose which system you wish to swap places with "
                        + tile1.getRepresentationForButtons(game, player) + ".",
                buttons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("spatialCollapseStep3_")
    public static void resolveSpatialCollapseStep3(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String position = buttonID.split("_")[1];
        String position2 = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(position);
        Tile tile2 = game.getTileByPosition(position2);
        tile.setPosition(position2);
        tile2.setPosition(position);
        game.setTile(tile);
        game.setTile(tile2);
        game.rebuildTilePositionAutoCompleteList();
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " Chose to swap "
                        + tile2.getRepresentationForButtons(game, player) + " with "
                        + tile.getRepresentationForButtons(game, player));
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static List<Button> getStrandedShipButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPlanetUnitHolders().isEmpty() && !FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game)) {
                buttons.add(Buttons.green(
                        "strandedShipStep2_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    @ButtonHandler("sideProject")
    public static void resolveSideProject(Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        ButtonHelperFactionSpecific.offerWinnuStartingTech(player);
    }

    @ButtonHandler("brutalOccupation")
    public static void resolveBrutalOccupationStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getExhaustedPlanets()) {
            buttons.add(Buttons.green("brutalOccupationStep2_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please choose the target of _Brutal Occupation_.",
                buttons);
    }

    @ButtonHandler("resolveShrapnelTurrets_")
    public static void resolveShrapnelTurrets(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        game.setStoredValue("ShrapnelTurretsFaction", player.getFaction());
        if (buttonID.contains("_")) {
            ButtonHelper.resolveCombatRoll(player, game, event, "combatRoll_" + buttonID.split("_")[1] + "_space_afb");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not find active system. You will need to roll using `/roll`.");
        }
        game.setStoredValue("ShrapnelTurretsFaction", "");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("brutalOccupationStep2_")
    public static void resolveBrutalOccupationStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        Tile tile = game.getTileFromPlanet(planet);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "2 inf " + planet);

        player.refreshPlanet(planet);

        List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(
                game, ButtonHelper.getUnitHolderFromPlanetName(planet, game), player);
        if (!buttons.isEmpty()) {
            String message = player.getFactionEmoji() + ", please press the button to explore "
                    + Helper.getPlanetRepresentation(planet, game) + ".";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        }

        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " readied and explored "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game) + ".");
    }
}
