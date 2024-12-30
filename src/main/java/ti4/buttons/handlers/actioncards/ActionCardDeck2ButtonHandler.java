package ti4.buttons.handlers.actioncards;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RelicHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
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

    @ButtonHandler("resolveDefenseInstallation")
    public static void resolveDefenseInstallation(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getReadiedPlanets()) {
            buttons.add(Buttons.green("defenseInstallationStep2_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " select the planet you wish to exhaust and put 1 PDS on",
            buttons);
    }

    @ButtonHandler("defenseInstallationStep2_")
    public static void resolveDefenseInstallationStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        player.exhaustPlanet(planet);
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "pds " + planet);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " exhausted " + Helper.getPlanetRepresentation(planet, game) + " and put 1 PDS on it");
    }

    @ButtonHandler("resolveBoardingParty")
    public static void resolveBoardingParty(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        String type = "sling";
        String pos = game.getActiveSystem();
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), type,
            "placeOneNDone_skipbuild");
        String message = player.getRepresentation() + " Use the buttons to place the 1 ship you killed under 5 cost. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
    }

    @ButtonHandler("resolveMercenaryContract")
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

    @ButtonHandler("resolveSisterShip")
    public static void resolveSisterShip(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        List<Button> buttons = new ArrayList<>();
        Set<Tile> tiles = ButtonHelper.getTilesOfUnitsWithProduction(player, game);

        for (Tile tile : tiles) {
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                String buttonID = "produceOneUnitInTile_" + tile.getPosition() + "_sling";
                Button tileButton = Buttons.green(buttonID,
                    tile.getRepresentationForButtons(game, player));
                buttons.add(tileButton);

            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Select which tile you would like to produce a ship in. The bot will not know that it is reduced cost and limited to a specific ship type, but you know that. ", buttons);
    }

    @ButtonHandler("resolveChainReaction")
    public static void resolveChainReaction(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        int hits = 1;
        StringBuilder msg = new StringBuilder("The _Chain Reaction_ rolled: ");
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
            buttons.add(Buttons.red("getDamageButtons_" + game.getActiveSystem() + "_" + "combat", "Assign Hit" + (hits == 1 ? "" : "s")));
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg + "\n " + player.getRepresentation() +
            " your opponent needs to assign " + hits + " hit" + (hits == 1 ? "" : "s"), buttons);
    }

    @ButtonHandler("resolveFlawlessStrategy")
    public static void resolveFlawlessStrategy(Player player, ButtonInteractionEvent event) {
        List<Button> scButtons = new ArrayList<>();
        event.getMessage().delete().queue();
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
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation() + " use buttons to resolve", scButtons);

    }

    @ButtonHandler("resolveRendezvousPoint")
    public static void resolveRendezvousPoint(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "rendezvous");
        MessageChannel channel = player.getCorrectChannel();
        MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token from what was the active system when you played the card. Then end your turn after doing any end of turn abilities you wish to do.", buttons);
    }

    @ButtonHandler("resolveAncientTradeRoutes")
    public static void resolveAncientTradeRoutes(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        player.setCommodities(player.getCommodities() + 2);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " gained 2 commodities");
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
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " tell the bot which player you wish to give 2 commodities to.",
            buttons);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " tell the bot which __different__ player you wish to give 2 commodities to.",
            buttons);
    }

    @ButtonHandler("resolveArmsDeal")
    public static void resolveArmsDeal(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();

        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player || !player.getNeighbouringPlayers().contains(p2)) {
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
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + ", please choose which neighbor gets 1 cruiser and 1 destroyer.",
            buttons);
    }

    @ButtonHandler("armsDealStep2_")
    public static void resolveArmsDealStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) return;
        List<Button> buttons = new ArrayList<>(Helper.getTileWithShipsPlaceUnitButtons(player, game, "cruiser", "placeOneNDone_skipbuild"));
        buttons.add(Buttons.red("deleteButtons", "Don't place"));
        MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), p2.getRepresentation() +
            ", please choose where you wish to place the _Arms Deal_ cruiser.", buttons);
        buttons = new ArrayList<>(Helper.getTileWithShipsPlaceUnitButtons(player, game, "destroyer", "placeOneNDone_skipbuild"));
        buttons.add(Buttons.red("deleteButtons", "Don't place"));
        MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), p2.getRepresentation() +
            ", please choose where you wish to place the _Arms Deal_ destroyer.", buttons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("ancientTradeRoutesStep2_")
    public static void resolveAncientTradeRoutesStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) return;
        p2.setCommodities(p2.getCommodities() + 2);
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), p2.getFactionEmoji()
            + " gained 2 commodities due to _Ancient Trade Routes) and is neighbors with " + player.getFactionEmojiOrColor() + " for this turn.");
        event.getMessage().delete().queue();
    }

    @ButtonHandler("resolveTombRaiders")
    public static void resolveTombRaiders(Player player, Game game, ButtonInteractionEvent event) {
        List<String> types = new ArrayList<>(List.of("hazardous", "cultural", "industrial", "frontier"));
        StringBuilder sb = new StringBuilder();
        for (String type : types) {
            List<String> deck = game.getExploreDeck(type);
            String cardID = deck.getFirst();

            ExploreModel card = Mapper.getExplore(cardID);
            String cardType = card.getResolution();
            if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
                cardID = game.drawExplore(type);
                sb.append(Mapper.getExplore(cardID).getName()).append(System.lineSeparator());
                sb.append(player.getRepresentationUnfogged()).append(" Gained relic fragment\n");
                player.addFragment(cardID);
                game.purgeExplore(cardID);
            } else {
                sb.append("Looked at the top of the ").append(type).append(" deck and saw that it was not a relic fragment.\n");
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), card.getName());
            }
        }
        CommanderUnlockCheckService.checkPlayer(player, "kollecc");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
        event.getMessage().delete().queue();
    }

    @ButtonHandler("breakthrough") // TODO: this might be annoying at some point in time
    public static void resolveBreakthrough(Player player, Game game, ButtonInteractionEvent event) {
        for (String planet : player.getPlanetsAllianceMode()) {
            if (ButtonHelper.checkForTechSkips(game, planet)) {
                player.refreshPlanet(planet);
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmoji() + " readied every planet with a technology specialty.");
        event.getMessage().delete().queue();
    }

    @ButtonHandler("strandedShipStep1")
    public static void resolveStrandedShipStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = getStrandedShipButtons(game, player);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " tell the bot which tile you wish to place the _Ghost Ship_ in.",
            buttons);
    }

    @ButtonHandler("strandedShipStep2_")
    public static void resolveStrandedShipStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        tile = FlipTileService.flipTileIfNeeded(event, tile, game);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "cruiser");
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " put 1 cruiser in " + tile.getRepresentation() + ".");

        // If Empyrean Commander is in game check if unlock condition exists
        Player p2 = game.getPlayerFromLeader("empyreancommander");
        CommanderUnlockCheckService.checkPlayer(p2, "empyrean");
    }

    @ButtonHandler("spatialCollapseStep1")
    public static void resolveSpatialCollapseStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = getSpatialCollapseTilesStep1(game, player);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " tell the bot which tile with your ships you wish to swap with an adjacent system",
            buttons);
    }

    public static List<Button> getSpatialCollapseTilesStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPosition().contains("t") || tile.getPosition().contains("b") || tile.isHomeSystem() || tile.isMecatol()) {
                continue;
            }
            if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                buttons.add(Buttons.gray("spatialCollapseStep2_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
            }

        }
        return buttons;
    }

    @ButtonHandler("spatialCollapseStep2_")
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

            buttons.add(Buttons.gray("spatialCollapseStep3_" + pos1 + "_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player)));

        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + ", please choose which tile you wish to swap places with "
                + tile1.getRepresentationForButtons(game, player) + ".",
            buttons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("spatialCollapseStep3_")
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
            player.getRepresentationUnfogged() + " Chose to swap "
                + tile2.getRepresentationForButtons(game, player) + " with "
                + tile.getRepresentationForButtons(game, player));
        event.getMessage().delete().queue();
    }

    public static List<Button> getStrandedShipButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPlanetUnitHolders().isEmpty() && !FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game)) {
                buttons.add(Buttons.green("strandedShipStep2_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    @ButtonHandler("sideProject")
    public static void resolveSideProject(Player player, Game game, ButtonInteractionEvent event) {
        String successMessage;
        if (player.getStrategicCC() > 0) {
            successMessage = player.getFactionEmoji() + ", 1 command token has been removed from your strategy pool (" + (player.getStrategicCC()) + " -> " + (player.getStrategicCC() - 1) + ").";
            player.setStrategicCC(player.getStrategicCC() - 1);
            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, CardEmojis.ActionCard + "Side Project");
        } else {
            successMessage = player.getFactionEmoji() + " exhausted the _" + RelicHelper.sillySpelling() + "_.";
            player.addExhaustedRelic("emelpar");
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);

        event.getMessage().delete().queue();
        ButtonHelperFactionSpecific.offerWinnuStartingTech(player);
    }

    @ButtonHandler("brutalOccupation")
    public static void resolveBrutalOccupationStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getExhaustedPlanets()) {
            buttons.add(Buttons.green("brutalOccupationStep2_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation() +
            " choose the target of _Brutal Occupation_.", buttons);
    }

    @ButtonHandler("resolveShrapnelTurrets_")
    public static void resolveShrapnelTurrets(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        game.setStoredValue("ShrapnelTurretsFaction", player.getFaction());
        if (buttonID.contains("_")) {
            ButtonHelper.resolveCombatRoll(player, game, event,
                "combatRoll_" + buttonID.split("_")[1] + "_space_afb");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find active system. You will need to roll using `/roll`.");
        }
        game.setStoredValue("ShrapnelTurretsFaction", "");
        event.getMessage().delete().queue();
    }

    @ButtonHandler("brutalOccupationStep2_")
    public static void resolveBrutalOccupationStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String planet = buttonID.split("_")[1];
        player.refreshPlanet(planet);
        List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, game), player);
        if (!buttons.isEmpty()) {
            String message = player.getFactionEmoji() + ", please press the button to explore "
                + Helper.getPlanetRepresentation(planet, game) + ".";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                message, buttons);
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
            + " readied and explored " + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game) + ".");
    }

}
