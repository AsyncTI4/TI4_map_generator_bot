package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.DiceHelper.Die;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;

public class ButtonHelperActionCardsWillHomebrew {

    public static void resolveDataArchive(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game, true);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation() + "Use buttons to explore planet #1", buttons);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation() + "Use buttons to explore planet #2 (different planet from #1)", buttons);
        if (game.getPhaseOfGame().toLowerCase().contains("agenda")) {
            for (String planet : player.getPlanets()) {
                player.exhaustPlanet(planet);
            }
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " exhausted all planets");
        }
        event.getMessage().delete().queue();
    }

    public static void resolveDefenseInstallation(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getReadiedPlanets()) {
            buttons.add(Button.success("defenseInstallationStep2_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " select the planet you wish to exhaust and put a pds on",
            buttons);
    }

    public static void resolveDefenseInstallationStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        player.exhaustPlanet(planet);
        new AddUnits().unitParsing(event, buttonID, game.getTileFromPlanet(planet), "pds " + planet, game);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " exhausted " + Helper.getPlanetRepresentation(planet, game) + " and put a pds on it");
    }

    public static void resolveBoardingParty(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        String type = "sling";
        String pos = game.getActiveSystem();
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), type,
            "placeOneNDone_skipbuild");
        String message = player.getRepresentation() + " Use the buttons to place the 1 ship you killed under 5 cost. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
    }

    public static void resolveMercenaryContract(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        String type = "sling";
        String pos = game.getActiveSystem();
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), type,
            "placeOneNDone_dontskip");
        String message = player.getRepresentation() + " Use the buttons to place up to 2 ships that have a combined cost of 4 or less";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
    }

    public static void resolveSisterShip(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        List<Button> buttons = new ArrayList<>();
        Set<Tile> tiles = ButtonHelper.getTilesOfUnitsWithProduction(player, game);

        for (Tile tile : tiles) {
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                String buttonID = "produceOneUnitInTile_" + tile.getPosition() + "_sling";
                Button tileButton = Button.success(buttonID,
                    tile.getRepresentationForButtons(game, player));
                buttons.add(tileButton);

            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Select which tile you would like to produce a ship in. The bot will not know that it is reduced cost and limited to a specific ship type, but you know that. ", buttons);
    }

    public static void resolveChainReaction(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        int hits = 1;
        StringBuilder msg = new StringBuilder("The chain reaction rolled: ");
        int currentRequirement = 7;
        Die die;
        while ((die = new Die(currentRequirement)).isSuccess()) {
            hits++;
            currentRequirement++;
            msg.append(die.getResult()).append(" :boom: ");
        }
        msg.append(die.getResult());
        List<Button> buttons = new ArrayList<>();
        if (game.getActiveSystem() != null && !game.getActiveSystem().isEmpty()) {
            buttons.add(Button.danger("getDamageButtons_" + game.getActiveSystem() + "_" + "combat", "Assign Hits"));
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg + "\n " + player.getRepresentation() + " your opponent needs to assign " + hits + " hits", buttons);
    }

    public static void resolveFlawlessStrategy(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> scButtons = new ArrayList<>();
        event.getMessage().delete().queue();
        if (player.getSCs().contains(2)) {
            scButtons.add(Button.success("diploRefresh2", "Ready 2 Planets"));
        }
        if (player.getSCs().contains(3)) {
            scButtons.add(Button.secondary("draw2ac", "Draw 2 Action Cards")
                .withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
        }
        if (player.getSCs().contains(4)) {
            scButtons.add(
                Button.success("construction_spacedock", "Place A SD").withEmoji(Emoji.fromFormatted(Emojis.spacedock)));
            scButtons.add(Button.success("construction_pds", "Place a PDS").withEmoji(Emoji.fromFormatted(Emojis.pds)));
        }
        if (player.getSCs().contains(5)) {
            scButtons.add(Button.secondary("sc_refresh", "Replenish Commodities")
                .withEmoji(Emoji.fromFormatted(Emojis.comm)));
        }
        if (player.getSCs().contains(6)) {
            scButtons.add(Button.success("warfareBuild", "Build At Home"));
        }
        if (player.getSCs().contains(7)) {
            scButtons.add(Buttons.GET_A_TECH);
        }
        if (player.getSCs().contains(8)) {
            scButtons.add(Button.secondary("non_sc_draw_so", "Draw Secret Objective")
                .withEmoji(Emoji.fromFormatted(Emojis.SecretObjective)));
        }
        scButtons.add(Button.danger("deleteButtons", "Done resolving"));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " use buttons to resolve", scButtons);

    }

    public static void resolveRendezvousPoint(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "rendezvous");
        MessageChannel channel = player.getCorrectChannel();
        MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token from what was the active system when you played the card. Then end your turn after doing any end of turn abilities you wish to do.", buttons);
    }

    public static void resolveAncientTradeRoutes(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        player.setCommodities(player.getCommodities() + 2);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " gained 2 commodities");
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("ancientTradeRoutesStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("ancientTradeRoutesStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        buttons.add(Button.danger("deleteButtons", "Dont give comms"));
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot who you want to give 2 comms to",
            buttons);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot who else you want to give 2 comms to (different from the first time)",
            buttons);
    }

    public static void resolveArmsDeal(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();

        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player || !player.getNeighbouringPlayers().contains(p2)) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("armsDealStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("armsDealStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot which neighbor you want to get a cruiser+destroyer",
            buttons);
    }

    public static void resolveArmsDealStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) return;
        List<Button> buttons = new ArrayList<>(Helper.getTileWithShipsPlaceUnitButtons(player, game, "cruiser", "placeOneNDone_skipbuild"));
        buttons.add(Button.danger("deleteButtons", "Dont place"));
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), p2.getRepresentation() + "Use buttons to put 1 cruiser with your ships due to the arms deal", buttons);
        buttons = new ArrayList<>(Helper.getTileWithShipsPlaceUnitButtons(player, game, "destroyer", "placeOneNDone_skipbuild"));
        buttons.add(Button.danger("deleteButtons", "Dont place"));
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), p2.getRepresentation() + "Use buttons to put 1 destroyer with your ships due to the arms deal", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveAncientTradeRoutesStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) return;
        p2.setCommodities(p2.getCommodities() + 2);
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), p2.getFactionEmoji() + " gained 2 commodities due to ancient trade routes and is neighbors with " + ButtonHelper.getIdentOrColor(player, game) + " for this turn");
        event.getMessage().delete().queue();
    }

    public static void resolveTombRaiders(Player player, Game game, ButtonInteractionEvent event) {
        List<String> types = new ArrayList<>(List.of("hazardous", "cultural", "industrial", "frontier"));
        StringBuilder sb = new StringBuilder();
        for (String type : types) {
            List<String> deck = game.getExploreDeck(type);
            String cardID = deck.get(0);

            ExploreModel card = Mapper.getExplore(cardID);
            String cardType = card.getResolution();
            if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
                cardID = game.drawExplore(type);
                sb.append(Mapper.getExplore(cardID).getName()).append(System.lineSeparator());
                sb.append(player.getRepresentation(true, true)).append(" Gained relic fragment\n");
                player.addFragment(cardID);
                game.purgeExplore(cardID);
            } else {
                sb.append("Looked at the top of the ").append(type).append(" deck and saw that it was not a relic frag.\n");
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), card.getName());
            }
        }
        ButtonHelper.fullCommanderUnlockCheck(player, game, "kollecc", event);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
        event.getMessage().delete().queue();
    }

    public static void resolveTechnologicalBreakthrough(Player player, Game game, ButtonInteractionEvent event) {
        for (String planet : player.getPlanetsAllianceMode()) {
            if (ButtonHelper.checkForTechSkips(game, planet)) {
                player.refreshPlanet(planet);
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(),
            player.getFactionEmoji() + " readied every tech skip planet");
        event.getMessage().delete().queue();

    }

    public static void resolveStrandedShipStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = getStrandedShipButtons(game, player);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot which tile you wish to place a ghost ship in",
            buttons);
    }

    public static void resolveStrandedShipStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        tile = MoveUnits.flipMallice(event, tile, game);
        new AddUnits().unitParsing(event, player.getColor(), tile, "cruiser", game);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " put a cruiser in " + tile.getRepresentation());

        // If Empyrean Commander is in game check if unlock condition exists
        Player p2 = game.getPlayerFromLeader("empyreancommander");
        if (p2 != null) {
            if (!p2.hasLeaderUnlocked("empyreancommander")) {
                ButtonHelper.commanderUnlockCheck(p2, game, "empyrean", event);
            }
        }
    }

    public static void resolveSpatialCollapseStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = getSpatialCollapseTilesStep1(game, player);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot which tile with your ships you wish to swap with an adjacent system",
            buttons);
    }

    public static List<Button> getSpatialCollapseTilesStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPosition().contains("t") || tile.getPosition().contains("b") || tile.isHomeSystem() || tile.isMecatol()) {
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
            if (tile.getPosition().contains("t") || tile.getPosition().contains("b") || tile == tile1 || tile.isHomeSystem() || tile.isMecatol()) {
                continue;
            }

            buttons.add(Button.secondary("spatialCollapseStep3_" + pos1 + "_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player)));

        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
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
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " Chose to swap "
                + tile2.getRepresentationForButtons(game, player) + " with "
                + tile.getRepresentationForButtons(game, player));
        event.getMessage().delete().queue();
    }

    public static List<Button> getStrandedShipButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPlanetUnitHolders().isEmpty() && FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game)) {
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
            String successMessage = p1.getFactionEmoji() + " Reduced strategy pool CCs by 1 (" + (p1.getStrategicCC()) + " -> " + (p1.getStrategicCC() - 1) + ")";
            p1.setStrategicCC(p1.getStrategicCC() - 1);
            ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, game, event, Emojis.ActionCard + "Side Project");
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
        } else {
            String successMessage = p1.getFactionEmoji() + " Exhausted Scepter of Emelpar";
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
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " choose the target of brutal occupation");
    }

    public static void resolveShrapnelTurrents(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        game.setStoredValue("ShrapnelTurrentsFaction", player.getFaction());
        if (buttonID.contains("_")) {
            ButtonHelper.resolveCombatRoll(player, game, event,
                "combatRoll_" + buttonID.split("_")[1] + "_space_afb");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find active system. You will need to roll using /roll");
        }
        game.setStoredValue("ShrapnelTurrentsFaction", "");
        event.getMessage().delete().queue();
    }

    public static void resolveBrutalOccupationStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String planet = buttonID.split("_")[1];
        player.refreshPlanet(planet);
        List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, game), player);
        if (!buttons.isEmpty()) {
            String message = player.getFactionEmoji() + " Click button to explore "
                + Helper.getPlanetRepresentation(planet, game);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                message, buttons);
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " refreshed and explored " + Helper.getPlanetRepresentation(planet, game));
    }

}
