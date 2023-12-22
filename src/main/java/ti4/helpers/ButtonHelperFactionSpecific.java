package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import ti4.commands.cardsac.ACInfo;
import ti4.commands.leaders.RefreshLeader;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.player.SendDebt;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

public class ButtonHelperFactionSpecific {


    public static void handleTitansConstructionMechDeployStep1(Game activeGame, Player player){
        List<Button> buttons = new ArrayList<>();
        if(ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "mech") > 3){
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation(true, true) + " you have all your mechs out and cant deploy more");
            return;
        }
        for(String planet : player.getPlanets())
        {
            buttons.add(Button.success("titansConstructionMechDeployStep2_"+planet, Helper.getPlanetRepresentation(planet, activeGame)));
        }
        String msg = player.getRepresentation(true, true) + " select the planet that you wish to drop a mech and infantry on";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
    }
     public static void handleTitansConstructionMechDeployStep2(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID){
        String planet = buttonID.split("_")[1];
        Tile tile = activeGame.getTileFromPlanet(planet);
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 mech "+planet, activeGame);
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 inf "+planet, activeGame);
        String msg = player.getRepresentation(true, true) + " deployed a mech and infantry on "+Helper.getPlanetRepresentation(planet, activeGame);
        ButtonHelper.sendMessageToRightStratThread(player, activeGame, msg, "construction");
        if (!player.getSCs().contains(Integer.parseInt("4"))) {
            String color = player.getColor();
            if (Mapper.isValidColor(color)) {
                AddCC.addCC(event, color, tile);
            }
            ButtonHelper.sendMessageToRightStratThread(player, activeGame, ButtonHelper.getIdent(player) + " Placed A CC From Reinforcements In The "
                + Helper.getPlanetRepresentation(planet, activeGame) + " system", "construction");
        }
        event.getMessage().delete().queue();
    }
    public static void checkForStymie(Game activeGame, Player activePlayer, Tile tile) {
        for (Player p2 : ButtonHelper.getOtherPlayersWithUnitsInTheSystem(activePlayer, activeGame, tile)) {
            if (p2.getPromissoryNotes().containsKey("stymie") && activeGame.getPNOwner("stymie") != p2) {
                String msg = p2.getRepresentation(true, true) + " you have the opportunity to stymie " + ButtonHelper.getIdentOrColor(activePlayer, activeGame);
                List<Button> buttons = new ArrayList<>();
                buttons.add(Button.success("stymiePlayerStep1_" + activePlayer.getFaction(), "Play Stymie"));
                buttons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
            }
        }
    }

    public static void resolveStymiePlayerStep1(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        Player activePlayer = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String msg = player.getRepresentation(true, true) + " choose the system in which you wish to stymie";
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            if (!tile.getPosition().equalsIgnoreCase(activeGame.getActiveSystem()) && !ButtonHelper.isTileHomeSystem(tile) && !AddCC.hasCC(event, activePlayer.getColor(), tile)) {
                buttons.add(Button.success("stymiePlayerStep2_" + activePlayer.getFaction() + "_" + tile.getPosition(), tile.getRepresentationForButtons(activeGame, player)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        event.getMessage().delete().queue();
        ButtonHelper.resolvePNPlay("stymie", player, activeGame, event);
    }

    public static void resolveStymiePlayerStep2(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String pos = buttonID.split("_")[2];
        Tile tile = activeGame.getTileByPosition(pos);
        AddCC.addCC(event, p2.getColor(), tile);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true) + " you stymied the tile: " + tile.getRepresentationForButtons(activeGame, player));
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
            p2.getRepresentation(true, true) + " you were stymied in tile: " + tile.getRepresentationForButtons(activeGame, p2));
    }

    public static void offerASNButtonsStep1(Game activeGame, Player player, String warfareOrTactical) {
        String msg = player.getRepresentation(true, true)
            + " you may have the ability to use Agency Supply Network (ASN). Select the tile you want to build out of, or decline (please decline if you already used ASN)";
        List<Button> buttons = new ArrayList<>();
        Set<Tile> tiles = ButtonHelper.getTilesOfUnitsWithProduction(player, activeGame);
        for (Tile tile : tiles) {
            buttons.add(Button.success("asnStep2_" + tile.getPosition() + "_" + warfareOrTactical, tile.getRepresentation()));
        }
        buttons.add(Button.danger("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void resolveASNStep2(Game activeGame, Player player, String buttonID, ButtonInteractionEvent event) {

        Tile tile = activeGame.getTileByPosition(buttonID.split("_")[1]);
        String msg = ButtonHelper.getIdent(player) + " is resolving Agency Supply Network in tile " + tile.getRepresentation();

        String warfareOrTactical = buttonID.split("_")[2];
        ButtonHelper.sendMessageToRightStratThread(player, activeGame, msg, warfareOrTactical);
        List<Button> buttons;
        buttons = Helper.getPlaceUnitButtons(event, player, activeGame, tile, warfareOrTactical, "place");
        String message = player.getRepresentation()
            + " Use the buttons to produce."
            + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame);
        if (!activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
        }
        event.getMessage().delete().queue();

    }

    public static boolean somebodyHasThisRelic(Game activeGame, String relic) {
        for (Player player : activeGame.getRealPlayers()) {
            if (player.hasRelic(relic)) {
                return true;
            }
        }
        return false;
    }

    public static Player findPNOwner(String pn, Game activeGame) {
        for (Player player : activeGame.getRealPlayers()) {
            if (player.ownsPromissoryNote(pn)) {
                return player;
            }
        }
        return null;
    }

    public static void delete(ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
    }

    public static void placeSaarMech(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String msg = ButtonHelper.getIdent(player) + " paid 1tg(" + player.getTg() + "->" + (player.getTg() - 1) + ") to place a mech on " + Helper.getPlanetRepresentation(planet, activeGame);
        delete(event);
        new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet(planet), "mech " + planet, activeGame);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
        player.setTg(player.getTg() - 1);
    }

    public static void offerKeleresStartingTech(Player player, Game activeGame, ButtonInteractionEvent event) {
        List<String> techToGain = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            techToGain = ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(player, p2, techToGain, activeGame);
        }
        List<Button> techs = new ArrayList<>();
        for (String tech : techToGain) {
            if ("".equals(Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction().orElse(""))) {
                techs.add(Button.success("getTech_" + Mapper.getTech(tech).getAlias() + "__noPay", Mapper.getTech(tech).getName()));
            }
        }
        event.getMessage().delete().queue();
        List<Button> techs2 = new ArrayList<>(techs);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation(true, true) + " use the buttons to get a tech the other players had",
            techs);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true) + " use the buttons to get another tech the other players had", techs2);

    }

    public static void offerArgentStartingTech(Player player, Game activeGame) {
        List<String> techToGain = new ArrayList<>();
        techToGain.add("st");
        techToGain.add("nm");
        techToGain.add("ps");
        List<Button> techs = new ArrayList<>();
        for (String tech : techToGain) {
            if ("".equals(Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction().orElse(""))) {
                techs.add(Button.success("getTech_" + Mapper.getTech(tech).getAlias() + "__noPay", Mapper.getTech(tech).getName()));
            }
        }
        List<Button> techs2 = new ArrayList<>(techs);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true) + " use the buttons to get one of the 3 starting argent tech", techs);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true) + " use the buttons to the second of the 3 starting argent tech", techs2);
    }

    public static void offerWinnuStartingTech(Player player, Game activeGame) {
        List<String> techToGain = new ArrayList<>();
        techToGain.add("st");
        techToGain.add("nm");
        techToGain.add("ps");
        techToGain.add("amd");
        techToGain.add("det");
        techToGain.add("aida");
        techToGain.add("pa");
        techToGain.add("sdn");
        List<Button> techs = new ArrayList<>();
        for (String tech : techToGain) {
            if ("".equals(Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction().orElse(""))) {
                techs.add(Button.success("getTech_" + Mapper.getTech(tech).getAlias() + "__noPay", Mapper.getTech(tech).getName()));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true) + " use the buttons to get one of the starting winnu tech", techs);
    }

    public static void offerSpyNetOptions(Player player) {
        String msg = player.getRepresentation()
            + " you have a choice now as to how you want to resolve spy net. You can do it the traditional way of accepting a card Yssaril chooses, without looking " +
            "at the other cards. Or you can look at all of Yssaril's cards and choose one.";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("spyNetYssarilChooses", "Have Yssaril Choose For You"));
        buttons.add(Button.secondary("spyNetPlayerChooses", "Look And Choose Yourself"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void resolveSpyNetYssarilChooses(Player player, Game activeGame, ButtonInteractionEvent event) {
        Player yssaril = findPNOwner("spynet", activeGame);
        String buttonID = "transact_ACs_" + player.getFaction();
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Sent Yssaril buttons so that they can send you an AC");
        ButtonHelper.resolveSpecificTransButtons(activeGame, yssaril, buttonID, event);
        event.getMessage().delete().queue();
    }

    public static void resolveSpyNetPlayerChooses(Player player, Game activeGame, ButtonInteractionEvent event) {
        Player yssaril = findPNOwner("spynet", activeGame);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Use Buttons to take an AC", ACInfo.getToBeStolenActionCardButtons(activeGame, yssaril));
        event.getMessage().delete().queue();
    }

    public static void returnFightersToSpace(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos);
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet) {
                if (unitHolder.getUnitCount(UnitType.Fighter, player.getColor()) > 0) {
                    int numff = unitHolder.getUnitCount(UnitType.Fighter, player.getColor());
                    new AddUnits().unitParsing(event, player.getColor(), tile, numff + " ff", activeGame);
                    new RemoveUnits().unitParsing(event, player.getColor(), tile, numff + " ff " + unitHolder.getName(), activeGame);
                }
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(player) + " put all fighters in tile " + pos + " back into space");
        event.getMessage().delete().queue();
    }

    public static List<Button> getTradePlanetsWithHacanMechButtons(Player hacan, Player receiver, Game activeGame) {
        List<Button> buttons = new ArrayList<>();
        if (!hacan.hasUnit("hacan_mech")) {
            return buttons;
        }
        for (String planet : hacan.getPlanetsAllianceMode()) {
            if (planet.contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            if (ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame).getUnitCount(UnitType.Mech, hacan.getColor()) > 0) {
                buttons.add(Button.secondary("hacanMechTradeStepOne_" + planet + "_" + receiver.getFaction(), Helper.getPlanetRepresentation(planet, activeGame)));
            }
        }
        return buttons;
    }

    public static List<Button> getRaghsCallButtons(Player player, Game activeGame, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        if (!player.getPromissoryNotes().containsKey("ragh")) {
            return buttons;
        }
        Player saar = activeGame.getPNOwner("ragh");
        if (saar == player) {
            return buttons;
        }
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            if (uH instanceof Planet && FoWHelper.playerHasUnitsOnPlanet(saar, tile, uH.getName())) {
                buttons.add(Button.secondary("raghsCallStep1_" + uH.getName(), "Ragh's Call on " + Helper.getPlanetRepresentation(uH.getName(), activeGame)));
            }
        }
        return buttons;
    }

    public static void checkForNaaluPN(Game activeGame) {
        activeGame.setCurrentReacts("Play Naalu PN", "");
        for (Player player : activeGame.getRealPlayers()) {
            boolean naalu = false;
            for (String pn : player.getPromissoryNotes().keySet()) {
                if ("gift".equalsIgnoreCase(pn) && !player.ownsPromissoryNote("gift")) {
                    naalu = true;
                }
            }
            if (naalu) {
                String msg = player.getRepresentation()
                    + " you have the option to pre-play Naalu PN. Naalu PN is an awkward timing window for async, so if you intend to play it, its best to pre-play it now. Feel free to ignore this message if you dont intend to play it";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Button.success("resolvePreassignment_Play Naalu PN", "Pre-play Naalu PN"));
                buttons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
            }
        }
    }

    public static void resolveRaghsCallStepOne(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String origPlanet = buttonID.split("_")[1];
        ButtonHelper.resolvePNPlay("ragh", player, activeGame, event);
        List<Button> buttons = new ArrayList<>();
        Player saar = activeGame.getPNOwner("ragh");
        for (String planet : saar.getPlanetsAllianceMode()) {
            if (!planet.equalsIgnoreCase(origPlanet)) {
                buttons.add(Button.secondary("raghsCallStepTwo_" + origPlanet + "_" + planet, "Relocate to " + Helper.getPlanetRepresentation(planet, activeGame)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation(true, true) + "Choose which planet to relocate saar ground forces to", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveHacanMechTradeStepOne(Player hacan, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String origPlanet = buttonID.split("_")[1];
        String receiverFaction = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();
        for (String planet : hacan.getPlanetsAllianceMode()) {
            if (!planet.equalsIgnoreCase(origPlanet)) {
                buttons.add(Button.secondary("hacanMechTradeStepTwo_" + origPlanet + "_" + receiverFaction + "_" + planet, "Relocate to " + Helper.getPlanetRepresentation(planet, activeGame)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), hacan.getRepresentation(true, true) + "Choose which planet to relocate your units to", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveProductionBiomesStep2(Player hacan, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player player = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(null, activeGame), "Could not resolve target player, please resolve manually.");
            return;
        }
        int oldTg = player.getTg();
        player.setTg(oldTg + 2);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            ButtonHelper.getIdentOrColor(player, activeGame) + " gained 2tg due to production biomes (" + oldTg + "->" + player.getTg() + ")");
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(hacan, activeGame), ButtonHelper.getIdentOrColor(player, activeGame) + " gained 2tg due to production biomes");
        }
        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 2);
        event.getMessage().delete().queue();
    }

    public static void resolveProductionBiomesStep1(Player hacan, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        int oldStratCC = hacan.getStrategicCC();
        if (oldStratCC < 1) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(hacan, activeGame), ButtonHelper.getIdent(hacan) + " did not have enough strat cc. #rejected");
            return;
        }

        int oldTg = hacan.getTg();
        hacan.setTg(oldTg + 4);
        hacan.setStrategicCC(oldStratCC - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(hacan, activeGame, event);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(hacan, activeGame),
            ButtonHelper.getIdent(hacan) + " lost a strat cc and gained 4tg (" + oldTg + "->" + hacan.getTg() + ")");
        ButtonHelperAbilities.pillageCheck(hacan, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(hacan, activeGame, 4);

        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == hacan) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("productionBiomes_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("productionBiomes_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(hacan, activeGame), hacan.getRepresentation(true, true) + " choose who should get 2tg", buttons);
    }

    public static void resolveQuantumDataHubNodeStep1(Player hacan, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        hacan.exhaustTech("qdn");
        int oldStratCC = hacan.getStrategicCC();
        if (oldStratCC < 1) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(hacan, activeGame), ButtonHelper.getIdent(hacan) + " did not have enough strat cc. #rejected");
            return;
        }

        int oldTg = hacan.getTg();
        hacan.setStrategicCC(oldStratCC - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(hacan, activeGame, event);
        hacan.setTg(oldTg - 3);

        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(hacan, activeGame), ButtonHelper.getIdent(hacan) + " lost a strat cc and 3tg (" + oldTg + "->" + hacan.getTg() + ")");

        List<Button> buttons = getSwapSCButtons(activeGame, "qdn", hacan);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(hacan, activeGame), hacan.getRepresentation(true, true) + " choose who you want to swap SCs with", buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getSwapSCButtons(Game activeGame, String type, Player hacan) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == hacan) {
                continue;
            }
            if (p2.getSCs().size() > 1) {
                if (activeGame.isFoWMode()) {
                    buttons.add(Button.secondary("selectBeforeSwapSCs_" + p2.getFaction() + "_" + type, p2.getColor()));
                } else {
                    Button button = Button.secondary("selectBeforeSwapSCs_" + p2.getFaction() + "_" + type, " ");
                    String factionEmojiString = p2.getFactionEmoji();
                    button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    buttons.add(button);
                }
            } else {
                if (activeGame.isFoWMode()) {
                    buttons.add(Button.secondary("swapSCs_" + p2.getFaction() + "_" + type + "_" + p2.getSCs().toArray()[0] + "_" + hacan.getSCs().toArray()[0], p2.getColor()));
                } else {
                    Button button = Button.secondary("swapSCs_" + p2.getFaction() + "_" + type + "_" + p2.getSCs().toArray()[0] + "_" + hacan.getSCs().toArray()[0], " ");
                    String factionEmojiString = p2.getFactionEmoji();
                    button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    buttons.add(button);
                }
            }
        }
        return buttons;
    }

    public static void resolveSelectedBeforeSwapSC(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String type = buttonID.split("_")[2];
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Could not resolve second player, please resolve manually.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Integer sc : p2.getSCs()) {
            for (Integer sc2 : player.getSCs()) {
                buttons.add(Button.secondary("swapSCs_" + p2.getFaction() + "_" + type + "_" + sc + "_" + sc2, "Swap " + sc2 + " with " + sc));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation(true, true) + " choose which SC you want to swap with",
            buttons);
    }

    public static void resolveSwapSC(Player player1, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String type = buttonID.split("_")[2];
        Player player2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player2 == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player1, activeGame), "Could not resolve second player, please resolve manually.");
            return;
        }
        int player1SC = Integer.parseInt(buttonID.split("_")[4]);
        int player2SC = Integer.parseInt(buttonID.split("_")[3]);
        if ("qdn".equalsIgnoreCase(type)) {
            int oldTg = player2.getTg();
            player2.setTg(oldTg + 3);
            ButtonHelperAbilities.pillageCheck(player2, activeGame);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player2, activeGame), ButtonHelper.getIdent(player2) + " gained 3tg from QDN (" + oldTg + "->" + player2.getTg() + ")");
        }
        player1.addSC(player2SC);
        player1.removeSC(player1SC);
        player2.addSC(player1SC);
        player2.removeSC(player2SC);
        String sb = player1.getRepresentation() + " swapped SC with " + player2.getRepresentation() + "\n" +
            "> " + player2.getRepresentation() + Emojis.getSCEmojiFromInteger(player2SC) + " " + ":arrow_right:" + " " + Emojis.getSCEmojiFromInteger(player1SC) + "\n" +
            "> " + player1.getRepresentation() + Emojis.getSCEmojiFromInteger(player1SC) + " " + ":arrow_right:" + " " + Emojis.getSCEmojiFromInteger(player2SC) + "\n";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player2, activeGame), sb);
        event.getMessage().delete().queue();
        ButtonHelper.startActionPhase(event, activeGame);
    }

    public static void resolveRaghsCallStepTwo(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String origPlanet = buttonID.split("_")[1];
        String newPlanet = buttonID.split("_")[2];
        Player saar = activeGame.getPNOwner("ragh");
        UnitHolder oriPlanet = ButtonHelper.getUnitHolderFromPlanetName(origPlanet, activeGame);
        Map<UnitKey, Integer> units = new HashMap<>(oriPlanet.getUnits());
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            UnitKey unitKey = unitEntry.getKey();
            int amount = unitEntry.getValue();
            String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
            if (!unitName.contains("pds")) {
                new RemoveUnits().unitParsing(event, saar.getColor(), activeGame.getTileFromPlanet(origPlanet), amount + " " + unitName + " " + origPlanet, activeGame);
                new AddUnits().unitParsing(event, saar.getColor(), activeGame.getTileFromPlanet(newPlanet), amount + " " + unitName + " " + newPlanet, activeGame);
            }
        }
        String ident = ButtonHelper.getIdentOrColor(player, activeGame);
        String message2 = ident + " moved the ground forces on the planet " + Helper.getPlanetRepresentation(origPlanet, activeGame) + " to " + Helper.getPlanetRepresentation(newPlanet, activeGame);
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(player.getPrivateChannel(), message2);
            MessageHelper.sendMessageToChannel(saar.getPrivateChannel(), message2);
        } else {
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message2);
        }
        event.getMessage().delete().queue();
    }

    public static void resolveHacanMechTradeStepTwo(Player hacan, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String origPlanet = buttonID.split("_")[1];
        String receiverFaction = buttonID.split("_")[2];
        String newPlanet = buttonID.split("_")[3];
        Player p2 = activeGame.getPlayerFromColorOrFaction(receiverFaction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(hacan, activeGame), "Could not resolve second player, please resolve manually.");
            return;
        }

        UnitHolder oriPlanet = ButtonHelper.getUnitHolderFromPlanetName(origPlanet, activeGame);
        Map<UnitKey, Integer> units = new HashMap<>(oriPlanet.getUnits());
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            UnitKey unitKey = unitEntry.getKey();
            int amount = unitEntry.getValue();
            String unitName = ButtonHelper.getUnitName(unitKey.asyncID());

            new RemoveUnits().unitParsing(event, hacan.getColor(), activeGame.getTileFromPlanet(origPlanet), amount + " " + unitName + " " + origPlanet, activeGame);
            new AddUnits().unitParsing(event, hacan.getColor(), activeGame.getTileFromPlanet(newPlanet), amount + " " + unitName + " " + newPlanet, activeGame);
        }
        new PlanetAdd().doAction(p2, origPlanet, activeGame, event);

        List<Button> goAgainButtons = new ArrayList<>();
        Button button = Button.secondary("transactWith_" + p2.getColor(), "Send something else to player?");
        Button done = Button.secondary("finishTransaction_" + p2.getColor(), "Done With This Transaction");
        String ident = ButtonHelper.getIdent(hacan);
        String message2 = ident + " traded the planet " + Helper.getPlanetRepresentation(origPlanet, activeGame) + " to " + ButtonHelper.getIdentOrColor(p2, activeGame)
            + " and relocated the unit(s) to " + Helper.getPlanetRepresentation(newPlanet, activeGame);
        goAgainButtons.add(button);
        goAgainButtons.add(done);
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(hacan.getPrivateChannel(), message2);
            MessageHelper.sendMessageToChannelWithButtons(hacan.getPrivateChannel(), ident + " Use Buttons To Complete Transaction", goAgainButtons);
            MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), message2);
        } else {
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message2);
            MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), ident + " Use Buttons To Complete Transaction", goAgainButtons);
        }
        event.getMessage().delete().queue();
    }

    public static void resolveReleaseButton(Player cabal, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        String faction = buttonID.split("_")[1];
        Player player = activeGame.getPlayerFromColorOrFaction(faction);
        if (player == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(cabal, activeGame), "Could not resolve second player, please resolve manually.");
            return;
        }

        String unit = buttonID.split("_")[2];
        new RemoveUnits().unitParsing(event, player.getColor(), cabal.getNomboxTile(), unit, activeGame);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(cabal, activeGame),
            cabal.getRepresentation(true, true) + " released 1 " + ButtonHelper.getIdentOrColor(player, activeGame) + " " + unit + " from prison");
        if (cabal != player) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " a " + unit + " of yours was released from prison.");
        }
        if (!cabal.getNomboxTile().getUnitHolders().get("space").getUnits().containsKey(Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor()))) {
            ButtonHelper.deleteTheOneButton(event);
        }

    }

    public static void checkBlockadeStatusOfEverything(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        for (Player p2 : activeGame.getRealPlayers()) {
            if (doesPlayerHaveAnyCapturedUnits(p2, activeGame, player, event)) {
                if (isCabalBlockadedByPlayer(player, activeGame, p2)) {
                    releaseAllUnits(p2, activeGame, player, event);
                }
            }
        }
    }

    public static boolean doesPlayerHaveAnyCapturedUnits(Player cabal, Game activeGame, Player blockader, GenericInteractionCreateEvent event) {
        if (cabal == blockader) {
            return false;
        }
        for (UnitHolder unitHolder : cabal.getNomboxTile().getUnitHolders().values()) {
            List<UnitKey> unitKeys = new ArrayList<>(unitHolder.getUnits().keySet());
            for (UnitKey unitKey : unitKeys) {
                if (blockader.unitBelongsToPlayer(unitKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void releaseAllUnits(Player cabal, Game activeGame, Player blockader, GenericInteractionCreateEvent event) {
        for (UnitHolder unitHolder : cabal.getNomboxTile().getUnitHolders().values()) {
            List<UnitKey> unitKeys = new ArrayList<>(unitHolder.getUnits().keySet());
            for (UnitKey unitKey : unitKeys) {
                if (blockader.unitBelongsToPlayer(unitKey)) {
                    int amount = unitHolder.getUnits().get(unitKey);
                    String unit = ButtonHelper.getUnitName(unitKey.asyncID());
                    new RemoveUnits().unitParsing(event, blockader.getColor(), cabal.getNomboxTile(), amount + " " + unit, activeGame);
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(cabal, activeGame),
                        cabal.getRepresentation(true, true) + " released " + amount + " " + ButtonHelper.getIdentOrColor(blockader, activeGame) + " " + unit + " from prison due to blockade");
                    if (cabal != blockader) {
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(blockader, activeGame),
                            blockader.getRepresentation(true, true) + " " + amount + " " + unit + " of yours was released from prison.");
                    }

                }

            }
        }
    }

    public static List<Button> getReleaseButtons(Player cabal, Game activeGame) {
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder unitHolder : cabal.getNomboxTile().getUnitHolders().values()) {
            for (UnitKey unitKey : unitHolder.getUnits().keySet()) {
                for (Player player : activeGame.getRealPlayers()) {
                    if (player.unitBelongsToPlayer(unitKey)) {
                        String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                        String buttonID = "cabalRelease_" + player.getFaction() + "_" + unitName;
                        if (activeGame.isFoWMode()) {
                            buttons.add(Button.secondary(buttonID, "Release 1 " + player.getColor() + " " + unitName));
                        } else {
                            buttons.add(Button.secondary(buttonID, "Release 1 " + player.getFaction() + " " + unitName).withEmoji(Emoji.fromFormatted(player.getFactionEmoji())));
                        }
                    }
                }
            }
        }
        buttons.add(Button.danger("deleteButtons", "Delete These Buttons"));
        return buttons;
    }

    public static void checkForGeneticRecombination(Player voter, Game activeGame) {
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == voter) {
                continue;
            }
            if (p2.hasTechReady("gr")) {
                String genetic = Emojis.BioticTech + " " + Emojis.Mahact + " Genetic Recombination";
                String msg1 = voter.getRepresentation(true, true) + " you may want to wait for " + genetic + " here. Use your discretion.";
                String msg2 = p2.getRepresentation(true, true) + " you may use " + genetic + " here on ";
                msg2 += StringUtils.capitalize(voter.getColor()) + ". This is not automated/tracked by the bot";
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(voter, activeGame), msg1);
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), msg2);
            }
        }
    }

    public static void resolveVadenSCDebt(Player player, int sc, Game activeGame) {
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2.getSCs().contains(sc) && p2 != player && p2.hasAbility("fine_print")) {
                SendDebt.sendDebt(player, p2, 1);
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    player.getRepresentation(true, true) + " you sent 1 debt token to " + ButtonHelper.getIdentOrColor(p2, activeGame) + " due to their fine print ability");
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), p2.getRepresentation(true, true) + " you collected 1 debt token from "
                    + ButtonHelper.getIdentOrColor(player, activeGame) + " due to your fine print ability. This is technically optional, done automatically for conveinance.");
                break;
            }

        }
    }

    public static String getAllOwnedPlanetTypes(Player player, Game activeGame) {

        StringBuilder types = new StringBuilder();
        for (String planetName : player.getPlanetsAllianceMode()) {
            if (planetName.contains("custodia") || planetName.contains("ghoti")) {
                continue;
            }
            Planet planet = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planetName, activeGame);
            String planetType = planet.getOriginalPlanetType();
            if (("industrial".equalsIgnoreCase(planetType) || "cultural".equalsIgnoreCase(planetType) || "hazardous".equalsIgnoreCase(planetType)) && !types.toString().contains(planetType)) {
                types.append(planetType);
            }
            if (planet.getTokenList().contains("attachment_titanspn.png")) {
                types.append("cultural");
                types.append("industrial");
                types.append("hazardous");
            }
        }

        return types.toString();
    }

    public static void increaseMykoMech(Game activeGame) {
        int amount;
        if (!activeGame.getFactionsThatReactedToThis("mykoMech").isEmpty()) {
            amount = Integer.parseInt(activeGame.getFactionsThatReactedToThis("mykoMech"));
            amount = amount + 1;
        } else {
            amount = 1;
        }
        activeGame.setCurrentReacts("mykoMech", "" + amount);
    }

    public static void decreaseMykoMech(Game activeGame) {
        int amount = 0;
        if (!activeGame.getFactionsThatReactedToThis("mykoMech").isEmpty()) {
            amount = Integer.parseInt(activeGame.getFactionsThatReactedToThis("mykoMech"));
            amount = amount - 1;
        }
        if (amount < 0) {
            amount = 0;
        }
        activeGame.setCurrentReacts("mykoMech", "" + amount);
    }

    public static void resolveMykoMech(Player player, Game activeGame) {
        decreaseMykoMech(activeGame);
        List<Button> buttons = new ArrayList<>(ButtonHelperAbilities.getPlanetPlaceUnitButtonsForMechMitosis(player, activeGame, ""));
        String message = player.getRepresentation(true, true) + " Use buttons to replace 1 infantry with a mech";
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
    }

    public static void resolveMykoMechCheck(Player player, Game activeGame) {
        if (player.hasUnit("mykomentori_mech")) {
            if (!activeGame.getFactionsThatReactedToThis("mykoMech").isEmpty()) {
                int amount = Integer.parseInt(activeGame.getFactionsThatReactedToThis("mykoMech"));
                List<Button> buttons = new ArrayList<>();
                buttons.add(Button.success("resolveMykoMech", "Replace Infantry With Mech"));
                buttons.add(Button.danger("deleteButtons", "Decline"));
                if (amount > 0) {
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                        player.getRepresentation(true, true) + " you have " + amount + " mechs that can replace infantry", buttons);
                }
            }
        }
    }

    public static void deployMykoSD(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        int requiredNum = 4;
        Tile tile = activeGame.getTileFromPlanet(planet);
        if (player.hasTech("dsmykosd")) {
            requiredNum = 3;
        }
        if (ButtonHelper.getNumberOfInfantryOnPlanet(planet, activeGame, player) + 1 > requiredNum) {
            new RemoveUnits().unitParsing(event, player.getColor(), tile, requiredNum + " infantry " + planet, activeGame);
            new AddUnits().unitParsing(event, player.getColor(), tile, "sd " + planet, activeGame);
            MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player) + " deployed a spacedock on " + planet + " by removing " + requiredNum + " infantry");
            event.getMessage().delete().queue();
            if (player.hasAbility("necrophage")) {
                player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
            }
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                ButtonHelper.getIdent(player) + " does not have " + requiredNum + " infantry on " + planet + " and therefore cannot deploy the spacedock");
        }
    }

    public static void offerMahactInfButtons(Player player, Game activeGame) {
        String message = player.getRepresentation(true, true) + " Resolve Mahact infantry loss using the buttons";
        Button convert2CommButton = Button.success("convert_1_comms", "Convert 1 Commodity Into TG").withEmoji(Emoji.fromFormatted(Emojis.Wash));
        Button get2CommButton = Button.primary("gain_1_comm_from_MahactInf", "Gain 1 Commodity").withEmoji(Emoji.fromFormatted(Emojis.comm));
        List<Button> buttons = List.of(convert2CommButton, get2CommButton, Button.danger("deleteButtons", "Done resolving"));
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
    }

    public static void offerHoldingCompanyButtons(Player player, Game activeGame) {
        String message = player.getRepresentation(true, true) + " Resolve Holding Company comm gain using the buttons. Remember you get 1 comm per attachment you've given out. ";
        Button convert2CommButton = Button.success("convert_1_comms", "Convert 1 Commodity Into TG").withEmoji(Emoji.fromFormatted(Emojis.Wash));
        Button get2CommButton = Button.primary("gain_1_comm_from_MahactInf", "Gain 1 Commodity").withEmoji(Emoji.fromFormatted(Emojis.comm));
        List<Button> buttons = List.of(convert2CommButton, get2CommButton, Button.danger("deleteButtons", "Done resolving"));
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
    }

    public static void offerNekrophageButtons(Player player, Game activeGame, ButtonInteractionEvent event) {
        String message = player.getRepresentation(true, true) + " Resolve Necrophage ability using buttons. ";
        Button convert2CommButton = Button.success("convert_1_comms", "Convert 1 Commodity Into TG").withEmoji(Emoji.fromFormatted(Emojis.Wash));
        Button get2CommButton = Button.primary("gain_1_comm_from_MahactInf", "Gain 1 Commodity").withEmoji(Emoji.fromFormatted(Emojis.comm));
        List<Button> buttons = List.of(convert2CommButton, get2CommButton, Button.danger("deleteButtons", "Done resolving"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void KeleresIIHQCCGainCheck(Player player, Game activeGame) {
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (p2.hasTech("iihq")) {
                String finChecker = "FFCC_" + p2.getFaction() + "_";
                Button getTactic = Button.success(finChecker + "increase_tactic_cc", "Gain 1 Tactic CC");
                Button getFleet = Button.success(finChecker + "increase_fleet_cc", "Gain 1 Fleet CC");
                Button getStrat = Button.success(finChecker + "increase_strategy_cc", "Gain 1 Strategy CC");
                Button DoneGainingCC = Button.danger("deleteButtons", "Done Gaining CCs");
                List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
                String trueIdentity = p2.getRepresentation(true, true);
                String message = trueIdentity + " Due to your IIHQ tech, you get to gain 2 commmand counters when someone scores an imperial point.";
                String message2 = trueIdentity + "! Your current CCs are " + p2.getCCRepresentation() + ". Use buttons to gain CCs";
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), message);
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), message2, buttons);
                break;
            }
        }

    }

    public static void resolveResearchAgreementCheck(Player player, String tech, Game activeGame) {
        if (activeGame.getPNOwner("ra") != null && activeGame.getPNOwner("ra") == player) {
            if ("".equals(Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction().orElse(""))) {
                for (Player p2 : activeGame.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (p2.getPromissoryNotes().containsKey("ra") && !p2.getTechs().contains(tech)) {
                        String msg = p2.getRepresentation(true, true) + " the RA owner has researched the tech " + Helper.getTechRepresentation(AliasHandler.resolveTech(tech))
                            + "\nUse the below button if you want to play RA to get it.";
                        Button transact = Button.success("resolvePNPlay_ra_" + AliasHandler.resolveTech(tech), "Acquire " + tech);
                        List<Button> buttons = new ArrayList<>();
                        buttons.add(transact);
                        buttons.add(Button.danger("deleteButtons", "Decline"));
                        MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
                    }
                }
            }
        }
    }

    public static void resolveMilitarySupportCheck(Player player, Game activeGame) {
        if (activeGame.getPlayerFromColorOrFaction(Mapper.getPromissoryNoteOwner("ms")) == player) {
            for (Player p2 : activeGame.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (p2.getPromissoryNotes().containsKey("ms")) {
                    String msg = p2.getRepresentation(true, true) + " the Military Support owner has started their turn, use the button to play Military Support if you want";
                    Button transact = Button.success("resolvePNPlay_ms", "Play Military Support ");
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(transact);
                    buttons.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
                }
            }
        }
    }

    public static void resolveKolleccAbilities(Player player, Game activeGame) {
        if (player.hasAbility("treasure_hunters")) {
            // resolve treasure hunters
            String msg = "Kollecc player, please choose which exploration deck to look at the top card of";
            Button transact1 = Button.success("resolveExp_Look_industrial", "Peek at Industrial deck");
            Button transact2 = Button.success("resolveExp_Look_hazardous", "Peek at Hazardous deck");
            Button transact3 = Button.success("resolveExp_Look_cultural", "Peek at Cultural deck");
            List<Button> buttons1 = new ArrayList<>();
            buttons1.add(transact1);
            buttons1.add(transact2);
            buttons1.add(transact3);
            buttons1.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), msg, buttons1);

            Button transact = Button.success("relic_look_top", "Look at top of Relic Deck");
            String msg2 = "Kollecc may also look at the top card of the relic deck.";
            List<Button> buttons2 = new ArrayList<>();
            buttons2.add(transact);
            buttons2.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), msg2, buttons2);
        }
        {

            if (activeGame.getPlayerFromColorOrFaction(Mapper.getPromissoryNoteOwner("dspnkoll")) == player) {
                for (Player p2 : activeGame.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (p2.getPromissoryNotes().containsKey("dspnkoll")) {
                        String msg = p2.getRepresentation(true, true) + " the Kollecc AI Survey PN owner has started their turn, use the button to play AI Survey if you want";
                        Button transact = Button.success("resolvePNPlay_dspnkoll", "Play AI Survey");
                        List<Button> buttons = new ArrayList<>();
                        buttons.add(transact);
                        buttons.add(Button.danger("deleteButtons", "Decline"));
                        MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
                    }
                }
            }
        }
    }

    public static void offerKolleccPNButtons(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        Button transact1 = Button.success("explore_look_All", "Peek at Industrial/Hazardous/Cultural decks");
        Button transact2 = Button.success("relic_look_top", "Peek at Relic deck");
        List<Button> buttons = new ArrayList<>();
        buttons.add(transact1);
        buttons.add(transact2);
        buttons.add(Button.danger("deleteButtons", "Decline"));
        String message = "Use buttons to select how to use the Kollecc AI Survey PN";
       // System.out.println(player.getFaction() + " is playing PN KOLLEC");
        MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), message, buttons);
    }

    public static void replacePDSWithFS(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        buttonID = buttonID.replace("replacePDSWithFS_", "");
        String planet = buttonID;
        String message = ident + " replaced " + Emojis.pds + " on " + Helper.getPlanetRepresentation(planet, activeGame) + " with a " + Emojis.flagship;
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        new AddUnits().unitParsing(event, player.getColor(), activeGame.getTile(AliasHandler.resolveTile(planet)), "flagship", activeGame);
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit("pds"), player.getColor());
        activeGame.getTile(AliasHandler.resolveTile(planet)).removeUnit(planet, unitKey, 1);
        event.getMessage().delete().queue();
    }

    public static void firstStepOfChaos(Game activeGame, Player p1, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        Set<Tile> tiles = ButtonHelper.getTilesOfUnitsWithProduction(p1, activeGame);
        for (Tile tile : tiles) {
            Button tileButton = Button.success("produceOneUnitInTile_" + tile.getPosition() + "_chaosM", tile.getRepresentationForButtons(activeGame, p1));
            buttons.add(tileButton);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(p1) + " has chosen to use the chaos mapping technology");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select which tile you would like to chaos map in.", buttons);
    }

    public static boolean isCabalBlockadedByPlayer(Player player, Game activeGame, Player cabal) {
        if (cabal == player) {
            return false;
        }
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, cabal, UnitType.CabalSpacedock, UnitType.Spacedock);
        if (tiles.isEmpty()) {
            return false;
        }
        for (Tile tile : tiles) {
            if (FoWHelper.playerHasShipsInSystem(player, tile) && !FoWHelper.playerHasShipsInSystem(cabal, tile)) {
                return true;
            }
        }
        return false;
    }

    public static void cabalEatsUnit(Player player, Game activeGame, Player cabal, int amount, String unit, GenericInteractionCreateEvent event) {
        cabalEatsUnit(player, activeGame, cabal, amount, unit, event, false);
    }

    public static void cabalEatsUnit(Player player, Game activeGame, Player cabal, int amount, String unit, GenericInteractionCreateEvent event, boolean cabalAgent) {
        String msg = cabal.getRepresentation(true, true) + " has failed to eat " + amount + " of the " + unit + "s owned by "
            + player.getRepresentation() + " because they were blockaded. Wah-wah.";
        String unitP = AliasHandler.resolveUnit(unit);
        if (unitP.contains("sd") || unitP.contains("pd") || (cabal.getAllianceMembers().contains(player.getFaction()) && !cabalAgent)) {
            return;
        }
        if (!isCabalBlockadedByPlayer(player, activeGame, cabal)) {
            msg = cabal.getFactionEmoji() + " has devoured " + amount + " of the " + unit + "s owned by " + player.getColor() + ". Chomp chomp.";
            String color = player.getColor();

            if (unitP.contains("ff") || unitP.contains("gf")) {
                color = cabal.getColor();
            }
            msg = msg.replace("Infantrys", "infantry");

            new AddUnits().unitParsing(event, color, cabal.getNomboxTile(), amount + " " + unit, activeGame);
        }
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(cabal, activeGame), msg);
        } else {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(cabal, activeGame), msg);
        }

    }

    public static void resolveLetnevMech(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        Tile tile = activeGame.getTileFromPlanet(planet);
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 " + unit + " " + planet, activeGame);
        new RemoveUnits().unitParsing(event, player.getColor(), tile, "1 infantry " + planet, activeGame);
        List<Button> options = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, "res");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ButtonHelper.getIdent(player) + " replaced 1 of their infantry with 1 " + unit + " on " + Helper.getPlanetRepresentation(planet, activeGame) + " using the mech's deploy ability");
        options.add(Button.danger("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation(true, true) + " pay 2r for it please", options);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void resolveDarkPactCheck(Game activeGame, Player sender, Player receiver, int numOfComms, GenericInteractionCreateEvent event) {
        for (String pn : sender.getPromissoryNotesInPlayArea()) {
            if ("dark_pact".equalsIgnoreCase(pn) && activeGame.getPNOwner(pn).getFaction().equalsIgnoreCase(receiver.getFaction())) {
                if (numOfComms == sender.getCommoditiesTotal()) {
                    MessageChannel channel = activeGame.getActionsChannel();
                    if (activeGame.isFoWMode()) {
                        channel = sender.getPrivateChannel();
                    }
                    String message = sender.getRepresentation(true, true) + " Dark Pact triggered, your tgs have increased by 1 (" + sender.getTg() + "->"
                        + (sender.getTg() + 1) + ")";
                    sender.setTg(sender.getTg() + 1);
                    MessageHelper.sendMessageToChannel(channel, message);
                    message = receiver.getRepresentation(true, true) + " Dark Pact triggered, your tgs have increased by 1 (" + receiver.getTg() + "->"
                        + (receiver.getTg() + 1) + ")";
                    receiver.setTg(receiver.getTg() + 1);
                    if (activeGame.isFoWMode()) {
                        channel = receiver.getPrivateChannel();
                    }
                    MessageHelper.sendMessageToChannel(channel, message);
                    // ButtonHelperAbilities.pillageCheck(sender, activeGame);
                    ButtonHelperAgents.resolveArtunoCheck(sender, activeGame, 1);
                    // ButtonHelperAbilities.pillageCheck(receiver, activeGame);
                    ButtonHelperAgents.resolveArtunoCheck(receiver, activeGame, 1);
                }
            }
        }
    }

    public static List<Button> getUnitButtonsForVortex(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.CabalSpacedock, UnitType.Spacedock);
        if (tiles.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Couldnt find any docks");
            return List.of();
        }
        Set<String> adjTiles = FoWHelper.getAdjacentTiles(activeGame, tiles.get(0).getPosition(), player, false);
        for (Tile tile : tiles) {
            adjTiles.addAll(FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), player, false));
        }

        Set<String> colorsBlockading = tiles.stream()
            .map(tile -> tile.getUnitHolders().get("space"))
            .flatMap(unitHolder -> unitHolder.getUnitColorsOnHolder().stream())
            .collect(Collectors.toSet());
        Set<UnitKey> availableUnits = adjTiles.stream()
            .map(activeGame::getTileByPosition)
            .flatMap(tile -> tile.getUnitHolders().values().stream())
            .flatMap(uh -> uh.getUnits().entrySet().stream().filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey))
            .filter(unitKey -> !colorsBlockading.contains(unitKey.getColorID()))
            .collect(Collectors.toSet());
        return availableUnits.stream()
            .filter(unitKey -> vortexButtonAvailable(activeGame, unitKey))
            .map(unitKey -> buildVortexButton(activeGame, unitKey))
            .toList();
    }

    public static boolean vortexButtonAvailable(Game activeGame, UnitKey unitKey) {
        int baseUnitCap = switch (unitKey.getUnitType()) {
            case Infantry, Fighter -> 10000;
            case Destroyer, Cruiser -> 8;
            case Dreadnought -> 5;
            case Mech, Carrier -> 4;
            case Warsun -> 2;
            case Flagship -> 1;
            default -> 0; // everything else that can't be captured
        };
        int unitCap = activeGame.getPlayerByColorID(unitKey.getColorID()).filter(p -> p.getUnitCap(unitKey.asyncID()) != 0).map(p -> p.getUnitCap(unitKey.asyncID())).orElse(baseUnitCap);
        return ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, unitKey) < unitCap;
    }

    public static Button buildVortexButton(Game activeGame, UnitKey unitKey) {
        String faction = activeGame.getPlayerByColorID(unitKey.getColorID()).map(Player::getFaction).get();
        String buttonID = "cabalVortextCapture_" + unitKey.unitName() + "_" + faction;
        String buttonText = String.format("Capture %s %s", unitKey.getColor(), unitKey.getUnitType().humanReadableName());
        return Button.danger(buttonID, buttonText).withEmoji(Emoji.fromFormatted(unitKey.unitEmoji()));
    }

    public static void resolveVortexCapture(String buttonID, Player player, Game activeGame, ButtonInteractionEvent event) {
        String unit = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), p2.getRepresentation(true, true) + " a " + unit + " of yours has been captured by vortex.");
        cabalEatsUnit(p2, activeGame, player, 1, unit, event);
        event.getMessage().delete().queue();
    }

    public static void offerTerraformButtons(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = planetReal != null && planetReal.getOriginalPlanetType() != null && ("industrial".equalsIgnoreCase(planetReal.getOriginalPlanetType())
                || "cultural".equalsIgnoreCase(planetReal.getOriginalPlanetType()) || "hazardous".equalsIgnoreCase(planetReal.getOriginalPlanetType()));
            if (oneOfThree || planet.contains("custodiavigilia") || planet.contains("ghoti")) {
                buttons.add(Button.success("terraformPlanet_" + planet, Helper.getPlanetRepresentation(planet, activeGame)));
            }
        }
        String message = "Use buttons to select which planet to terraform";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    public static void resolveScour(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID){
        String planet = buttonID.split("_")[1];
        String msg = ButtonHelper.getIdent(player) +" used the Scour ability to discard an AC and ready "+Helper.getPlanetRepresentation(planet, activeGame);
        player.refreshPlanet(planet);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation(true, true) + " use buttons to discard",
                ACInfo.getDiscardActionCardButtons(activeGame, player, false));
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
    }

    public static void offerVeldyrButtons(Player player, Game activeGame, GenericInteractionCreateEvent event, String pnID) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = planetReal != null && planetReal.getOriginalPlanetType() != null && ("industrial".equalsIgnoreCase(planetReal.getOriginalPlanetType())
                || "cultural".equalsIgnoreCase(planetReal.getOriginalPlanetType()) || "hazardous".equalsIgnoreCase(planetReal.getOriginalPlanetType()));
            if (oneOfThree || planet.contains("custodiavigilia") || planet.contains("ghoti")) {
                buttons.add(Button.success("veldyrAttach_" + planet+"_"+pnID, Helper.getPlanetRepresentation(planet, activeGame)));
            }
        }
        String message = player.getRepresentation(true, true)+" Use buttons to select which planet to put the attachment on";
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
    }

    public static List<Button> getButtonsToTakeSomeonesAC(Game activeGame, Player thief, Player victim) {
        List<Button> takeACs = new ArrayList<>();
        String secretScoreMsg = "_ _\nClick a button to take an Action Card";
        List<Button> acButtons = ACInfo.getToBeStolenActionCardButtons(activeGame, victim);
        if (!acButtons.isEmpty()) {
            List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(secretScoreMsg, acButtons);
            ThreadChannel cardsInfoThreadChannel = thief.getCardsInfoThread();
            for (MessageCreateData message : messageList) {
                cardsInfoThreadChannel.sendMessage(message).queue();
            }
        }
        return takeACs;
    }

    public static void mageon(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String trueIdentity) {
        buttonID = buttonID.replace("takeAC_", "");
        int acNum = Integer.parseInt(buttonID.split("_")[0]);

        String faction2 = buttonID.split("_")[1];
        Player player2 = activeGame.getPlayerFromColorOrFaction(faction2);
        if (player2 == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Could not find player, please resolve manually.");
            return;
        }
        if (!player2.getActionCards().containsValue(acNum)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that AC, no AC added/lost");
            return;
        }
        String ident2 = player2.getRepresentation();
        String message2 = trueIdentity + " took AC #" + acNum + " from " + ident2;
        String acID = null;
        for (Map.Entry<String, Integer> so : player2.getActionCards().entrySet()) {
            if (so.getValue().equals(acNum)) {
                acID = so.getKey();
            }
        }
        if (activeGame.isFoWMode()) {
            message2 = "Someone took AC #" + acNum + " from " + player2.getColor();
            MessageHelper.sendMessageToChannel(player.getPrivateChannel(), message2);
            MessageHelper.sendMessageToChannel(player2.getPrivateChannel(), message2);
        } else {
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message2);
        }
        player2.removeActionCard(acNum);
        player.setActionCard(acID);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation(true, true) + "Acquired " + acID);
        MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
            "# " + player2.getRepresentation(true, true) + " Lost " + acID + " to a players ability");
        ACInfo.sendActionCardInfo(activeGame, player2);
        ACInfo.sendActionCardInfo(activeGame, player);
        if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
        }
        event.getMessage().delete().queue();
    }

    public static void terraformPlanet(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String planet = buttonID.replace("terraformPlanet_", "");
        UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);
        Planet planetReal = (Planet) unitHolder;
        planetReal.addToken(Constants.ATTACHMENT_TITANSPN_PNG);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Attached terraform to " + Helper.getPlanetRepresentation(planet, activeGame));
        event.getMessage().delete().queue();
    }

    public static void resolveBranchOffice(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        String planet = buttonID.split("_")[1];
        String pnID = buttonID.split("_")[2];
        UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);
        Planet planetReal = (Planet) unitHolder;
        switch(pnID) {
            case "dspnveld1" -> planetReal.addToken("attachment_veldyrtaxhaven.png");
            case "dspnveld2" -> planetReal.addToken("attachment_veldyrbroadcasthub.png");
            case "dspnveld3" -> planetReal.addToken("attachment_veldyrreservebank.png");
            case "dspnveld4" -> planetReal.addToken("attachment_veldyrorbitalshipyard.png");
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Attached branch office to " + Helper.getPlanetRepresentation(planet, activeGame));
        event.getMessage().delete().queue();
    }

    public static List<Button> getCreusIFFTypeOptions(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("creussIFFStart_beta", "Beta").withEmoji(Emoji.fromFormatted(Emojis.CreussBeta)));
        buttons.add(Button.danger("creussIFFStart_gamma", "Gamma").withEmoji(Emoji.fromFormatted(Emojis.CreussGamma)));
        buttons.add(Button.secondary("creussIFFStart_alpha", "Alpha").withEmoji(Emoji.fromFormatted(Emojis.CreussAlpha)));
        return buttons;
    }

    public static void creussMechStep3(Game activeGame, Player player, String buttonID, ButtonInteractionEvent event) {
        String tilePos = buttonID.split("_")[1];
        String type = buttonID.split("_")[2];
        String tokenName = "creuss" + type;
        Tile tile = activeGame.getTileByPosition(tilePos);
        StringBuilder sb = new StringBuilder(player.getRepresentation());
        tile.addToken(Mapper.getTokenID(tokenName), Constants.SPACE);
        sb.append(" moved ").append(Emojis.getEmojiFromDiscord(tokenName)).append(" to ").append(tile.getRepresentationForButtons(activeGame, player));
        for (Tile tile_ : activeGame.getTileMap().values()) {
            if (!tile.equals(tile_) && tile_.removeToken(Mapper.getTokenID(tokenName), Constants.SPACE)) {
                sb.append(" (from ").append(tile_.getRepresentationForButtons(activeGame, player)).append(")");
                break;
            }
        }
        boolean removed = false;
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            if (uH.getUnitCount(UnitType.Mech, player.getColor()) > 0 && !removed) {
                removed = true;
                String name = uH.getName();
                if ("space".equals(name)) {
                    name = "";
                }
                new RemoveUnits().unitParsing(event, player.getColor(), tile, "1 mech " + name, activeGame);
                sb.append("\n ").append(ButtonHelper.getIdent(player)).append(" removed 1 mech from ").append(tile.getRepresentation()).append("(").append(uH.getName()).append(")");
            }
        }
        String msg = sb.toString();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
        event.getMessage().delete().queue();
    }

    public static void creussMechStep2(Game activeGame, Player player, String buttonID, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        String tilePos = buttonID.split("_")[1];
        buttons.add(Button.success("creussMechStep3_" + tilePos + "_beta", "Beta").withEmoji(Emoji.fromFormatted(Emojis.CreussBeta)));
        buttons.add(Button.danger("creussMechStep3_" + tilePos + "_gamma", "Gamma").withEmoji(Emoji.fromFormatted(Emojis.CreussGamma)));
        buttons.add(Button.secondary("creussMechStep3_" + tilePos + "_alpha", "Alpha").withEmoji(Emoji.fromFormatted(Emojis.CreussAlpha)));
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true) + " choose the type of wormhole you wish to place in " + tilePos, buttons);
        event.getMessage().delete().queue();
    }

    public static void creussMechStep1(Game activeGame, Player player, String buttonID, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Mech)) {
            buttons.add(Button.success("creussMechStep2_" + tile.getPosition(), tile.getRepresentationForButtons(activeGame, player)));
        }
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true) + " choose the tile where you wish to remove a mech and place a creuss wormhole", buttons);
    }

    public static void resolveWinnuPN(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        String scNum = buttonID.split("_")[1];
        int sc = Integer.parseInt(scNum);
        player.addFollowedSC(sc);
        ButtonHelper.resolvePNPlay("acq", player, activeGame, event);
        String msg = player.getRepresentation(true, true) + " you will be marked as having followed " + sc
            + " without having needed to spend a CC. Please still use the SC buttons to resolve the SC effect";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
    }

    public static List<Button> getGreyfireButtons(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        for (UnitHolder uH : tile.getPlanetUnitHolders()) {
            buttons.add(Button.success("greyfire_" + uH.getName(), Helper.getPlanetRepresentation(uH.getName(), activeGame)));
        }
        return buttons;
    }

    public static void resolveGreyfire(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String unit = "infantry";
        Tile tile = activeGame.getTileFromPlanet(planet);
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 " + unit + " " + planet, activeGame);
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (FoWHelper.playerHasInfantryOnPlanet(p2, tile, planet)) {
                new RemoveUnits().unitParsing(event, p2.getColor(), tile, "1 infantry " + planet, activeGame);
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ButtonHelper.getIdent(player) + " replaced 1 of their opponent's infantry with 1 " + unit + " on " + Helper.getPlanetRepresentation(planet, activeGame) + " using greyfire");
        event.getMessage().delete().queue();
    }

    public static void resolveCreussIFFStart(Game activeGame, @NotNull Player player, String buttonID, String ident, ButtonInteractionEvent event) {
        String type = buttonID.split("_")[1];
        List<Button> buttons = getCreusIFFLocationOptions(activeGame, player, type);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ident + " please select the tile you would like to put a wormhole in", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveCreussIFF(Game activeGame, Player player, String buttonID, String ident, ButtonInteractionEvent event) {
        String type = buttonID.split("_")[1];
        String pos = buttonID.split("_")[2];
        String tokenName = "creuss" + type;
        Tile tile = activeGame.getTileByPosition(pos);
        String msg;
        if (activeGame.isFoWMode() && !isTileCreussIFFSuitable(activeGame, player, tile)) {
            msg = "Tile was not suitable for the iff.";
            if (player.getTg() > 0) {
                player.setTg(player.getTg() - 1);
                msg = msg + " You lost a tg";
            } else {
                if (player.getTacticalCC() > 0) {
                    player.setTacticalCC(player.getTacticalCC() - 1);
                    msg = msg + " You lost a tactic cc";
                } else {
                    if (player.getFleetCC() > 0) {
                        player.setFleetCC(player.getFleetCC() - 1);
                        msg = msg + " You lost a fleet cc";
                    }
                }
            }
        } else {
            StringBuilder sb = new StringBuilder(player.getRepresentation());
            tile.addToken(Mapper.getTokenID(tokenName), Constants.SPACE);
            sb.append(" moved ").append(Emojis.getEmojiFromDiscord(tokenName)).append(" to ").append(tile.getRepresentationForButtons(activeGame, player));
            for (Tile tile_ : activeGame.getTileMap().values()) {
                if (!tile.equals(tile_) && tile_.removeToken(Mapper.getTokenID(tokenName), Constants.SPACE)) {
                    sb.append(" (from ").append(tile_.getRepresentationForButtons(activeGame, player)).append(")");
                    break;
                }
            }
            msg = sb.toString();
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
        event.getMessage().delete().queue();
    }

    public static List<Button> getCreusIFFLocationOptions(Game activeGame, @NotNull Player player, String type) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            if (isTileCreussIFFSuitable(activeGame, player, tile) || (activeGame.isFoWMode() && !FoWHelper.getTilePositionsToShow(activeGame, player).contains(tile.getPosition()))) {
                buttons.add(Button.success("creussIFFResolve_" + type + "_" + tile.getPosition(), tile.getRepresentationForButtons(activeGame, player)));
            }
        }
        return buttons;
    }

    public static boolean isTileCreussIFFSuitable(Game activeGame, Player player, Tile tile) {
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            if (activeGame.getTileFromPlanet(planet) == null) {
                continue;
            }
            if (activeGame.getTileFromPlanet(planet).getPosition().equalsIgnoreCase(tile.getPosition())) {
                return true;
            }
        }
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (FoWHelper.playerHasShipsInSystem(p2, tile)) {
                return false;
            }
            Tile hs = activeGame.getTile(AliasHandler.resolveTile(p2.getFaction()));
            if (hs == null) {
                hs = ButtonHelper.getTileOfPlanetWithNoTrait(p2, activeGame);
            }
            if (hs != null && hs.getPosition().equalsIgnoreCase(tile.getPosition())) {
                return false;
            }
        }
        return true;
    }

    public static void resolveTCSExhaust(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        String agent = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
        if (p2 == null) return;
        player.exhaustTech("tcs");
        Leader playerLeader = p2.getLeader(agent).orElse(null);
        if (playerLeader == null) {
            return;
        }
        RefreshLeader.refreshLeader(p2, playerLeader, activeGame);

        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player) + " exhausted TCS tech to ready " + agent + ", owned by " + p2.getColor());

        if (p2 != player) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                p2.getRepresentation(true, true) + " the TCS tech was exhausted by " + player.getColor() + " to ready your " + agent);
        }
        event.getMessage().delete().queue();
    }

    public static boolean isNextToEmpyMechs(Game activeGame, Player ACPlayer, Player EmpyPlayer) {
        if (ACPlayer == null || EmpyPlayer == null) {
            return false;
        }
        if (ACPlayer.getFaction().equalsIgnoreCase(EmpyPlayer.getFaction())) {
            return false;
        }
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, EmpyPlayer, UnitType.Mech);
        for (Tile tile : tiles) {
            Set<String> adjTiles = FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), EmpyPlayer, true);
            for (String adjTile : adjTiles) {
                Tile adjT = activeGame.getTileMap().get(adjTile);
                if (FoWHelper.playerHasUnitsInSystem(ACPlayer, adjT)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<Button> getLanefirATSButtons(Player p1, Player p2) {
        List<Button> ats = new ArrayList<>();

        for (int i = 0; i < p1.getAtsCount(); i++) {
            ats.add(Button.secondary("FFCC_" + p1.getFaction() + "_"+"lanefirATS_" + (i+1), String.valueOf(i+1)));
        }

        for (int i = 0; i < p2.getAtsCount(); i++) {
            ats.add(Button.secondary("FFCC_" + p2.getFaction() + "_"+"lanefirATS_" + (i+1), String.valueOf(i+1)));
        }

        return ats;
    }

    public static void resolveLanefirATS(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String count = buttonID.split("_")[1];
        int origATS = player.getAtsCount();

        if(player.getAtsCount() < Integer.parseInt(count)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), 
                player.getRepresentation(true, false) + " does not have " + count + " commodities to remove from ATS Armaments. Current count: " + player.getAtsCount());
            return;
        }

        player.setAtsCount(player.getAtsCount() - Integer.parseInt(count));

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), 
            player.getRepresentation(true, false) + " removed " + count + " commodities from ATS Armaments ("+origATS+"->"+player.getAtsCount()+")");
    }


    public static void resolveRohDhnaIndustrious(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String tilePos = buttonID.split("_")[1];
        String toRemove = buttonID.split("_")[2];
        String planet = toRemove.split(" ")[1];
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, "res");
        Button DoneExhausting = Button.danger("deleteButtons_spitItOut", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(tilePos), "warsun", activeGame);
        new RemoveUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(tilePos), toRemove, activeGame);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player) + " replaced "+Emojis.spacedock+" on "+ Helper.getPlanetRepresentationPlusEmoji(planet) +" with a "+Emojis.warsun);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Click the names of the planets you wish to exhaust to pay the 6 resources", buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getRohDhnaRecycleButtons(Game activeGame, Player player) {
        List<UnitKey> availableUnits = new ArrayList<>();
        Map<UnitKey, Integer> units = activeGame.getTileByPosition(activeGame.getActiveSystem()).getUnitHolders().get("space").getUnits();
        for (UnitKey unit : units.keySet()) {
            if(unit.getColor() == player.getColor() && (unit.getUnitType() == UnitType.Cruiser || unit.getUnitType() == UnitType.Carrier || unit.getUnitType() == UnitType.Dreadnought)) {
                //if unit is not in the list, add it
                if(!availableUnits.contains(unit)) {
                    availableUnits.add(unit);
                }
            }
        }

        List<Button> buttons = new ArrayList<>();
        for (UnitKey unit : availableUnits) {
            buttons.add(Button.success("FFCC_" + player.getFaction()+"_rohdhnaRecycle_" + unit.unitName(), unit.getUnitType().humanReadableName()).withEmoji(Emoji.fromFormatted(unit.unitEmoji())));

        }

        if(!buttons.isEmpty()) {
            buttons.add(Button.danger("FFCC_" + player.getFaction()+"_deleteButtons", "Decline"));
        }

        return buttons;
    }

    public static void resolveRohDhnaRecycle(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String unitName = buttonID.split("_")[1];
        new RemoveUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(activeGame.getActiveSystem()), "1 "+unitName, activeGame);
        UnitModel unit = Mapper.getUnit(unitName);
        int toGain = (int) unit.getCost() - 1;
        int before = player.getTg();
        player.setTg(before + toGain);

        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, toGain);

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), 
            player.getRepresentation(true, false) + " recycled "+unit.getUnitEmoji()+" "+unit.getName()+" for "+toGain+" tg ("+before+"->"+player.getTg()+")");

        event.getMessage().delete().queue();
    }
}
