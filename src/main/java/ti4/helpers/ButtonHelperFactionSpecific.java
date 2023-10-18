package ti4.helpers;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import ti4.commands.cardsac.ACInfo;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.player.SendDebt;
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

public class ButtonHelperFactionSpecific {

    public static boolean somebodyHasThisRelic(Game activeGame, String relic) {
        boolean somebodyHasIt = false;
        for (Player player : activeGame.getRealPlayers()) {
            if (player.hasRelic(relic)) {
                somebodyHasIt = true;
                return true;
            }
        }
        return somebodyHasIt;
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
        List<Button> buttons = new ArrayList<Button>();
        if (!hacan.hasUnit("hacan_mech")) {
            return buttons;
        }
        String colorID = Mapper.getColorID(hacan.getColor());
        UnitKey mechKey = Mapper.getUnitKey("mf", colorID);
        for (String planet : hacan.getPlanetsAllianceMode()) {
            if (planet.contains("custodia")) {
                continue;
            }
            if (ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame).getUnits().containsKey(mechKey)) {
                buttons.add(Button.secondary("hacanMechTradeStepOne_" + planet + "_" + receiver.getFaction(), Helper.getPlanetRepresentation(planet, activeGame)));
            }
        }
        return buttons;
    }

    public static void resolveHacanMechTradeStepOne(Player hacan, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String origPlanet = buttonID.split("_")[1];
        String receiverFaction = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<Button>();
        for (String planet : hacan.getPlanetsAllianceMode()) {
            if (!planet.equalsIgnoreCase(origPlanet)) {
                buttons.add(Button.secondary("hacanMechTradeStepTwo_" + origPlanet + "_" + receiverFaction + "_" + planet, "Relocate to " + Helper.getPlanetRepresentation(planet, activeGame)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), ButtonHelper.getTrueIdentity(hacan, activeGame) + "Choose which planet to relocate your units to", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveProductionBiomesStep2(Player hacan, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player player = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Could not resolve target player, please resolve manually.");
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
        Player player = hacan;
        int oldStratCC = player.getStrategicCC();
        if (oldStratCC < 1) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player) + " did not have enough strat cc. #rejected");
            return;
        }

        int oldTg = player.getTg();
        player.setTg(oldTg + 4);
        player.setStrategicCC(oldStratCC - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, activeGame, event);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            ButtonHelper.getIdent(player) + " lost a strat cc and gained 4tg (" + oldTg + "->" + player.getTg() + ")");
        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 4);

        List<Button> buttons = new ArrayList<Button>();
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
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " choose who should get 2tg", buttons);
    }

    public static void resolveQuantumDataHubNodeStep1(Player hacan, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player player = hacan;
        player.exhaustTech("qdn");
        int oldStratCC = player.getStrategicCC();
        if (oldStratCC < 1) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player) + " did not have enough strat cc. #rejected");
            return;
        }

        int oldTg = player.getTg();
        player.setStrategicCC(oldStratCC - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, activeGame, event);
        player.setTg(oldTg - 3);

        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player) + " lost a strat cc and 3tg (" + oldTg + "->" + player.getTg() + ")");

        List<Button> buttons = getSwapSCButtons(activeGame, "qdn", hacan);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " choose who you want to swap CCs with",
            buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getSwapSCButtons(Game activeGame, String type, Player hacan) {
        List<Button> buttons = new ArrayList<Button>();
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
        List<Button> buttons = new ArrayList<Button>();
        for (Integer sc : p2.getSCs()) {
            for (Integer sc2 : player.getSCs()) {
                buttons.add(Button.secondary("swapSCs_" + p2.getFaction() + "_" + type + "_" + sc + "_" + sc2, "Swap " + sc2 + " with " + sc));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " choose which SC you want to swap with",
            buttons);
    }

    public static void resolveSwapSC(Player player1, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String type = buttonID.split("_")[2];
        Player player2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player2 == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player1, activeGame), "Could not resolve second player, please resolve manually.");
            return;
        }
        Integer player1SC = Integer.parseInt(buttonID.split("_")[4]);
        Integer player2SC = Integer.parseInt(buttonID.split("_")[3]);
        if (type.equalsIgnoreCase("qdn")) {
            int oldTg = player2.getTg();
            player2.setTg(oldTg + 3);
            ButtonHelperAbilities.pillageCheck(player2, activeGame);
            ButtonHelperAgents.resolveArtunoCheck(player2, activeGame, 3);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player2, activeGame), ButtonHelper.getIdent(player2) + " gained 3tg from QDN (" + oldTg + "->" + player2.getTg() + ")");
        }
        player1.addSC(player2SC);
        player1.removeSC(player1SC);
        player2.addSC(player1SC);
        player2.removeSC(player2SC);
        String sb = Helper.getPlayerRepresentation(player1, activeGame) + " swapped SC with " + Helper.getPlayerRepresentation(player2, activeGame) + "\n" +
            "> " + Helper.getPlayerRepresentation(player2, activeGame) + Helper.getSCEmojiFromInteger(player2SC) + " " + ":arrow_right:" + " " + Helper.getSCEmojiFromInteger(player1SC) + "\n" +
            "> " + Helper.getPlayerRepresentation(player1, activeGame) + Helper.getSCEmojiFromInteger(player1SC) + " " + ":arrow_right:" + " " + Helper.getSCEmojiFromInteger(player2SC) + "\n";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player2, activeGame), sb);
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
        HashMap<UnitKey, Integer> units = new HashMap<>(oriPlanet.getUnits());
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            UnitKey unitKey = unitEntry.getKey();
            int amount = unitEntry.getValue();
            String unitName = ButtonHelper.getUnitName(unitKey.asyncID());

            new RemoveUnits().unitParsing(event, hacan.getColor(), activeGame.getTileFromPlanet(origPlanet), amount + " " + unitName + " " + origPlanet, activeGame);
            new AddUnits().unitParsing(event, hacan.getColor(), activeGame.getTileFromPlanet(newPlanet), amount + " " + unitName + " " + newPlanet, activeGame);
        }
        new PlanetAdd().doAction(p2, origPlanet, activeGame, event);

        List<Button> goAgainButtons = new ArrayList<Button>();
        Button button = Button.secondary("transactWith_" + p2.getColor(), "Send something else to player?");
        Button done = Button.secondary("finishTransaction_" + p2.getColor(), "Done With This Transaction");
        Player p1 = hacan;
        String ident = ButtonHelper.getIdent(hacan);
        String message2 = ident + " traded the planet " + Helper.getPlanetRepresentation(origPlanet, activeGame) + " to " + ButtonHelper.getIdentOrColor(p2, activeGame)
            + " and relocated the unit(s) to " + Helper.getPlanetRepresentation(newPlanet, activeGame);
        goAgainButtons.add(button);
        goAgainButtons.add(done);
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(p1.getPrivateChannel(), message2);
            MessageHelper.sendMessageToChannelWithButtons(p1.getPrivateChannel(), ident + " Use Buttons To Complete Transaction", goAgainButtons);
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
            ButtonHelper.getTrueIdentity(cabal, activeGame) + " released 1 " + ButtonHelper.getIdentOrColor(player, activeGame) + " " + unit + " from prison");
        if (cabal != player) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getTrueIdentity(player, activeGame) + " a " + unit + " of yours was released from prison.");
        }
        if (!cabal.getNomboxTile().getUnitHolders().get("space").getUnits().keySet().contains(Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor()))) {
            ButtonHelper.deleteTheOneButton(event);
        }

    }

    public static List<Button> getReleaseButtons(Player cabal, Game activeGame) {
        List<Button> buttons = new ArrayList<Button>();
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
                String msg1 = ButtonHelper.getTrueIdentity(voter, activeGame) + " you may want to wait for " + genetic + " here. Use your discretion.";
                String msg2 = ButtonHelper.getTrueIdentity(p2, activeGame) + " you may use " + genetic + " here on ";
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
                    ButtonHelper.getTrueIdentity(player, activeGame) + " you sent 1 debt token to " + ButtonHelper.getIdentOrColor(p2, activeGame) + " due to their fine print ability");
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame) + " you collected 1 debt token from "
                    + ButtonHelper.getIdentOrColor(player, activeGame) + " due to your fine print ability. This is technically optional, done automatically for conveinance.");
                break;
            }

        }
    }

    public static String getAllOwnedPlanetTypes(Player player, Game activeGame) {

        String types = "";
        for (String planetName : player.getPlanetsAllianceMode()) {
            if (planetName.contains("custodia")) {
                continue;
            }
            Planet planet = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planetName, activeGame);
            String planetType = planet.getOriginalPlanetType();
            if (("industrial".equalsIgnoreCase(planetType) || "cultural".equalsIgnoreCase(planetType) || "hazardous".equalsIgnoreCase(planetType)) && !types.contains(planetType)) {
                types = types + planetType;
            }
            if (planet.getTokenList().contains("attachment_titanspn.png")) {
                types = types + "cultural";
                types = types + "industrial";
                types = types + "hazardous";
            }
        }

        return types;
    }

    public static void offerMahactInfButtons(Player player, Game activeGame) {
        String message = ButtonHelper.getTrueIdentity(player, activeGame) + " Resolve Mahact infantry loss using the buttons";
        Button convert2CommButton = Button.success("convert_1_comms", "Convert 1 Commodity Into TG").withEmoji(Emoji.fromFormatted(Emojis.Wash));
        Button get2CommButton = Button.primary("gain_1_comm_from_MahactInf", "Gain 1 Commodity").withEmoji(Emoji.fromFormatted(Emojis.comm));
        List<Button> buttons = List.of(convert2CommButton, get2CommButton, Button.danger("deleteButtons", "Done resolving"));
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
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
                String trueIdentity = Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), true);
                String message = trueIdentity + " Due to your IIHQ tech, you get to gain 2 commmand counters when someone scores an imperial point.";
                String message2 = trueIdentity + "! Your current CCs are " + p2.getCCRepresentation() + ". Use buttons to gain CCs";
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), message);
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), message2, buttons);
                break;
            }
        }

    }

    public static void resolveResearchAgreementCheck(Player player, String tech, Game activeGame) {
        if (activeGame.getPlayerFromColorOrFaction(Mapper.getPromissoryNoteOwner("ra")) == player) {
            if ("".equals(Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction())) {
                for (Player p2 : activeGame.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (p2.getPromissoryNotes().containsKey("ra") && !p2.getTechs().contains(tech)) {
                        String msg = ButtonHelper.getTrueIdentity(p2, activeGame) + " the RA owner has researched the tech " + Helper.getTechRepresentation(AliasHandler.resolveTech(tech))
                            + "Use the below button if you want to play RA to get it.";
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
                    String msg = ButtonHelper.getTrueIdentity(p2, activeGame) + " the Military Support owner has started their turn, use the button to play Military Support if you want";
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
        } {     

        if (activeGame.getPlayerFromColorOrFaction(Mapper.getPromissoryNoteOwner("dspnkoll")) == player) {
            for (Player p2 : activeGame.getRealPlayers()) {
                if (p2 == player) {                    
                    continue;                    
                }
                if (p2.getPromissoryNotes().containsKey("dspnkoll")) {
                    String msg = ButtonHelper.getTrueIdentity(p2, activeGame) + " the Kollecc AI Survey PN owner has started their turn, use the button to play AI Survey if you want";
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
        System.out.println(player.getFaction() + " is playing PN KOLLEC");
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
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, p1, UnitType.Spacedock);
        if (tiles.isEmpty()) {
            tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, p1, UnitType.CabalSpacedock);
        }
        for (Tile tile : tiles) {
            Button tileButton = Button.success("produceOneUnitInTile_" + tile.getPosition() + "_chaosM", tile.getRepresentationForButtons(activeGame, p1));
            buttons.add(tileButton);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(p1) + " has chosen to use the chaos mapping technology");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select which tile you would like to chaos map in.", buttons);
    }

    public static boolean isCabalBlockadedByPlayer(Player player, Game activeGame, Player cabal) {
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, cabal, UnitType.CabalSpacedock);
        if (tiles.isEmpty()) {
            tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, cabal, UnitType.Spacedock);
        }
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
        String msg = Helper.getPlayerRepresentation(cabal, activeGame, activeGame.getGuild(), true) + " has failed to eat " + amount + " of the " + unit + "s owned by "
            + Helper.getPlayerRepresentation(player, activeGame) + " because they were blockaded. Wah-wah.";
        String unitP = AliasHandler.resolveUnit(unit);
        if (unitP.contains("sd") || unitP.contains("pd") || cabal.getAllianceMembers().contains(player.getFaction())) {
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
        List<Button> options = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ButtonHelper.getIdent(player) + " replaced 1 of their infantry with 1 " + unit + " on " + Helper.getPlanetRepresentation(planet, activeGame) + " using the mech's deploy ability");
        options.add(Button.danger("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), ButtonHelper.getTrueIdentity(player, activeGame) + " pay 2r for it please", options);
        event.getMessage().delete().queue();
    }

    public static void resolveDarkPactCheck(Game activeGame, Player sender, Player receiver, int numOfComms, GenericInteractionCreateEvent event) {
        for (String pn : sender.getPromissoryNotesInPlayArea()) {
            if ("dark_pact".equalsIgnoreCase(pn) && activeGame.getPNOwner(pn).getFaction().equalsIgnoreCase(receiver.getFaction())) {
                if (numOfComms == sender.getCommoditiesTotal()) {
                    MessageChannel channel = activeGame.getActionsChannel();
                    if (activeGame.isFoWMode()) {
                        channel = sender.getPrivateChannel();
                    }
                    String message = Helper.getPlayerRepresentation(sender, activeGame, activeGame.getGuild(), true) + " Dark Pact triggered, your tgs have increased by 1 (" + sender.getTg() + "->"
                        + (sender.getTg() + 1) + ")";
                    sender.setTg(sender.getTg() + 1);
                    MessageHelper.sendMessageToChannel(channel, message);
                    message = Helper.getPlayerRepresentation(receiver, activeGame, activeGame.getGuild(), true) + " Dark Pact triggered, your tgs have increased by 1 (" + receiver.getTg() + "->"
                        + (receiver.getTg() + 1) + ")";
                    receiver.setTg(receiver.getTg() + 1);
                    if (activeGame.isFoWMode()) {
                        channel = receiver.getPrivateChannel();
                    }
                    MessageHelper.sendMessageToChannel(channel, message);
                    ButtonHelperAbilities.pillageCheck(sender, activeGame);
                    ButtonHelperAgents.resolveArtunoCheck(sender, activeGame, 1);
                    ButtonHelperAbilities.pillageCheck(receiver, activeGame);
                    ButtonHelperAgents.resolveArtunoCheck(receiver, activeGame, 1);
                }
            }
        }
    }

    public static List<Button> getUnitButtonsForVortex(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, player, UnitType.CabalSpacedock);
        tiles.addAll(ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, player, UnitType.Spacedock));

        if (tiles.size() == 0) {
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
            .map(pos -> activeGame.getTileByPosition(pos))
            .flatMap(tile -> tile.getUnitHolders().values().stream())
            .flatMap(uh -> uh.getUnits().entrySet().stream().filter(e -> e.getValue() > 0).map(Map.Entry::getKey))
            .filter(unitKey -> !colorsBlockading.contains(unitKey.getColorID()))
            .collect(Collectors.toSet());
        List<Button> buttons = availableUnits.stream()
            .filter(unitKey -> vortexButtonAvailable(activeGame, unitKey))
            .map(unitKey -> buildVortexButton(activeGame, unitKey))
            .toList();
        return buttons;
    }

    public static boolean vortexButtonAvailable(Game activeGame, UnitKey unitKey) {
        int baseUnitCap = switch (unitKey.getUnitType()) {
            case Infantry, Fighter -> 10000;
            case Destroyer, Cruiser -> 8;
            case Mech, Carrier -> 4;
            case Warsun -> 2;
            case Flagship -> 1;
            default -> 0; // everything else that can't be captured
        };
        int unitCap = activeGame.getPlayerByColorID(unitKey.getColorID())
            .map(p -> p.getUnitCap(unitKey.asyncID()) == 0 ? baseUnitCap : p.getUnitCap(unitKey.asyncID()))
            .orElse(baseUnitCap);
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
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame) + " a " + unit + " of yours has been captured by vortex.");
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
            if (oneOfThree || planet.contains("custodiavigilia")) {
                buttons.add(Button.success("terraformPlanet_" + planet, Helper.getPlanetRepresentation(planet, activeGame)));
            }
        }
        String message = "Use buttons to select which planet to terraform";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
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
        String ident2 = Helper.getPlayerRepresentation(player2, activeGame, activeGame.getGuild(), false);
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
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + "Acquired " + acID);
        MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
            "# " + Helper.getPlayerRepresentation(player2, activeGame, activeGame.getGuild(), true) + " Lost " + acID + " to mageon (or perhaps Yssaril hero)");
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

    public static List<Button> getCreusIFFTypeOptions(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("creussIFFStart_beta", "Beta").withEmoji(Emoji.fromFormatted(Emojis.CreussBeta)));
        buttons.add(Button.danger("creussIFFStart_gamma", "Gamma").withEmoji(Emoji.fromFormatted(Emojis.CreussGamma)));
        buttons.add(Button.secondary("creussIFFStart_alpha", "Alpha").withEmoji(Emoji.fromFormatted(Emojis.CreussAlpha)));
        return buttons;
    }

    public static void resolveCreussIFFStart(Game activeGame, @NotNull Player player, String buttonID, String ident, ButtonInteractionEvent event) {
        String type = buttonID.split("_")[1];
        List<Button> buttons = getCreusIFFLocationOptions(activeGame, player, type);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ident + " please select the tile you would like to put a wormhole in", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveCreussIFF(Game activeGame, Player player, String buttonID, String ident, ButtonInteractionEvent event) {
        String type = buttonID.split("_")[1];
        String tokenName = "creuss" + type;
        String pos = buttonID.split("_")[2];
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
            StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(player, activeGame));
            tile.addToken(Mapper.getTokenID(tokenName), Constants.SPACE);
            sb.append(" moved ").append(Helper.getEmojiFromDiscord(tokenName)).append(" to ").append(tile.getRepresentationForButtons(activeGame, player));
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
            if (planet.toLowerCase().contains("custodia")) {
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
        playerLeader.setExhausted(false);

        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player) + " exhausted TCS tech to ready " + agent + ", owned by " + p2.getColor());

        if (p2 != player) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                ButtonHelper.getTrueIdentity(p2, activeGame) + " the TCS tech was exhausted by " + player.getColor() + " to ready your " + agent);
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
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, EmpyPlayer, UnitType.Mech);
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

}