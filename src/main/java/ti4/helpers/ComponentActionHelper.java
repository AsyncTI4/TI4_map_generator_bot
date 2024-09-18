package ti4.helpers;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.buttons.Buttons;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardspn.PlayPN;
import ti4.commands.ds.DrawBlueBackTile;
import ti4.commands.leaders.ExhaustLeader;
import ti4.commands.leaders.HeroPlay;
import ti4.commands.player.TurnStart;
import ti4.commands.units.AddUnits;
import ti4.generator.Mapper;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;

public class ComponentActionHelper {

    @ButtonHandler("componentActionRes_")
    public static void resolvePressedCompButton(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        String prefix = "componentActionRes_";
        String finChecker = p1.getFinsFactionCheckerPrefix();
        buttonID = buttonID.replace(prefix, "");

        String firstPart = buttonID.substring(0, buttonID.indexOf("_"));
        buttonID = buttonID.replace(firstPart + "_", "");

        switch (firstPart) {
            case "tech" -> {
                // DEPRECATED: uses the "exhaustTech_" stack of ButtonListener: `else if
                // (buttonID.startsWith("exhaustTech_"))`
            }
            case "leader" -> {
                if (!Mapper.isValidLeader(buttonID)) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve leader.");
                    return;
                }
                if (buttonID.contains("agent")) {
                    List<String> leadersThatNeedSpecialSelection = List.of("naaluagent", "muaatagent", "kolumeagent",
                        "arborecagent", "bentoragent", "xxchaagent", "axisagent");
                    if (leadersThatNeedSpecialSelection.contains(buttonID)) {
                        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(game, buttonID);
                        String message = p1.getRepresentation(true, true)
                            + " Use buttons to select the user of the agent";
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    } else {
                        ExhaustLeader.exhaustLeader(event, game, p1, p1.getLeader(buttonID).orElse(null));
                        if ("fogallianceagent".equalsIgnoreCase(buttonID)) {
                            ButtonHelperAgents.exhaustAgent("fogallianceagent", event, game, p1, p1.getFactionEmoji());
                        }
                    }
                } else if (buttonID.contains("hero")) {
                    HeroPlay.playHero(event, game, p1, p1.getLeader(buttonID).orElse(null));
                }
            }
            case "relic" -> resolveRelicComponentAction(game, p1, event, buttonID);
            case "pn" -> PlayPN.resolvePNPlay(buttonID, p1, game, event);
            case "ability" -> {
                if ("starForge".equalsIgnoreCase(buttonID)) {

                    List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, p1, UnitType.Warsun);
                    List<Button> buttons = new ArrayList<>();
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        p1.getFactionEmoji() + " Chose to use the starforge ability");
                    String message = "Select the tile you would like to starforge in";
                    for (Tile tile : tiles) {
                        Button starTile = Buttons.green("starforgeTile_" + tile.getPosition(),
                            tile.getRepresentationForButtons(game, p1));
                        buttons.add(starTile);
                    }
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                } else if ("orbitalDrop".equalsIgnoreCase(buttonID)) {
                    String successMessage = p1.getFactionEmoji() + " Spent 1 strategy token using " + Emojis.Sol
                        + "Orbital Drop (" + (p1.getStrategicCC()) + "->" + (p1.getStrategicCC() - 1) + ")";
                    p1.setStrategicCC(p1.getStrategicCC() - 1);
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, game, event, Emojis.Sol + "Orbital Drop");
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                    String message = "Select the planet you would like to place 2 infantry on.";
                    List<Button> buttons = new ArrayList<>(
                        Helper.getPlanetPlaceUnitButtons(p1, game, "2gf", "placeOneNDone_skipbuildorbital"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                } else if ("muaatFS".equalsIgnoreCase(buttonID)) {
                    String successMessage = p1.getFactionEmoji() + " Spent 1 strategy token using " + Emojis.Muaat
                        + Emojis.flagship + "The Inferno (" + (p1.getStrategicCC()) + "->"
                        + (p1.getStrategicCC() - 1) + ") \n";
                    p1.setStrategicCC(p1.getStrategicCC() - 1);
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, game, event,
                        Emojis.Muaat + Emojis.flagship + "The Inferno");
                    List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, p1, UnitType.Flagship);
                    Tile tile = tiles.get(0);
                    List<Button> buttons = TurnStart.getStartOfTurnButtons(p1, game, true, event);
                    new AddUnits().unitParsing(event, p1.getColor(), tile, "1 cruiser", game);
                    successMessage = successMessage + "Produced 1 " + Emojis.cruiser + " in tile "
                        + tile.getRepresentationForButtons(game, p1) + ".";
                    MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
                    String message = "Use buttons to end turn or do another action";
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                    ButtonHelper.deleteMessage(event);
                } else if ("lanefirMech".equalsIgnoreCase(buttonID)) {
                    String message3 = "Use buttons to drop 1 mech on a planet";
                    List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(p1, game,
                        "mech", "placeOneNDone_skipbuild"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message3, buttons);
                    String message2 = "Click the fragment you'd like to purge. ";
                    List<Button> purgeFragButtons = new ArrayList<>();
                    if (p1.getCrf() > 0) {
                        Button transact = Buttons.blue(finChecker + "purge_Frags_CRF_1", "Purge 1 Cultural Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getIrf() > 0) {
                        Button transact = Buttons.green(finChecker + "purge_Frags_IRF_1",
                            "Purge 1 Industrial Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getHrf() > 0) {
                        Button transact = Buttons.red(finChecker + "purge_Frags_HRF_1", "Purge 1 Hazardous Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getUrf() > 0) {
                        Button transact = Buttons.gray(finChecker + "purge_Frags_URF_1",
                            "Purge 1 Frontier Fragment");
                        purgeFragButtons.add(transact);
                    }
                    Button transact3 = Buttons.red(finChecker + "deleteButtons",
                        "Done Purging");
                    purgeFragButtons.add(transact3);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message2,
                        purgeFragButtons);
                    String message = "Use buttons to end turn or do an action";
                    List<Button> systemButtons = TurnStart.getStartOfTurnButtons(p1, game, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, systemButtons);

                } else if ("fabrication".equalsIgnoreCase(buttonID)) {
                    String message = "Click the fragment you'd like to purge. ";
                    List<Button> purgeFragButtons = new ArrayList<>();
                    if (p1.getCrf() > 0) {
                        Button transact = Buttons.blue(finChecker + "purge_Frags_CRF_1", "Purge 1 Cultural Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getIrf() > 0) {
                        Button transact = Buttons.green(finChecker + "purge_Frags_IRF_1",
                            "Purge 1 Industrial Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getHrf() > 0) {
                        Button transact = Buttons.red(finChecker + "purge_Frags_HRF_1", "Purge 1 Hazardous Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getUrf() > 0) {
                        Button transact = Buttons.gray(finChecker + "purge_Frags_URF_1",
                            "Purge 1 Frontier Fragment");
                        purgeFragButtons.add(transact);
                    }
                    Button transact2 = Buttons.green(finChecker + "gain_CC", "Gain CC");
                    purgeFragButtons.add(transact2);
                    Button transact3 = Buttons.red(finChecker + "finishComponentAction",
                        "Done Resolving Fabrication");
                    purgeFragButtons.add(transact3);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, purgeFragButtons);

                } else if ("stallTactics".equalsIgnoreCase(buttonID)) {
                    String secretScoreMsg = "_ _\n" + p1.getRepresentation(true, true)
                        + " Click a button below to discard an Action Card";
                    List<Button> acButtons = ACInfo.getDiscardActionCardButtons(game, p1, true);
                    MessageHelper.sendMessageToChannel(p1.getCorrectChannel(),
                        p1.getRepresentation() + " is resolving their Stall Tactics ability");
                    if (!acButtons.isEmpty()) {
                        List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(secretScoreMsg,
                            acButtons);
                        ThreadChannel cardsInfoThreadChannel = p1.getCardsInfoThread();
                        for (MessageCreateData message : messageList) {
                            cardsInfoThreadChannel.sendMessage(message).queue();
                        }
                    }
                } else if ("mantlecracking".equalsIgnoreCase(buttonID)) {
                    List<Button> buttons = ButtonHelperAbilities.getMantleCrackingButtons(p1, game);
                    // MessageHelper.sendMessageToChannel(event.getChannel(),
                    // p1.getFactionEmoji()+" Chose to use the mantle cracking ability");
                    String message = "Select the planet you would like to mantle crack";
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                } else if ("meditation".equalsIgnoreCase(buttonID)) {
                    if (p1.getStrategicCC() > 0) {
                        String successMessage = p1.getFactionEmoji() + " Reduced strategy pool CCs by 1 ("
                            + (p1.getStrategicCC()) + "->" + (p1.getStrategicCC() - 1) + ")";
                        p1.setStrategicCC(p1.getStrategicCC() - 1);
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, game, event, Emojis.kolume + "Meditation");
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                    } else {
                        String successMessage = p1.getFactionEmoji() + " Exhausted Scepter";
                        p1.addExhaustedRelic("emelpar");
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                    }
                    String message = "Select the tech you would like to ready";
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message, ButtonHelper.getAllTechsToReady(game, p1));
                    List<Button> buttons = TurnStart.getStartOfTurnButtons(p1, game, true, event);
                    String message2 = "Use buttons to end turn or do another action";
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                }
            }
            case "getRelic" -> {
                String message = "Click the fragments you'd like to purge. ";
                List<Button> purgeFragButtons = new ArrayList<>();
                int numToBeat = 2 - p1.getUrf();
                if (game.isAgeOfExplorationMode()) {
                    numToBeat = numToBeat - 1;
                }
                if ((p1.hasAbility("fabrication") || p1.getPromissoryNotes().containsKey("bmf"))) {
                    numToBeat = numToBeat - 1;
                    if (p1.getPromissoryNotes().containsKey("bmf") && game.getPNOwner("bmf") != p1) {
                        Button transact = Buttons.blue(finChecker + "resolvePNPlay_bmfNotHand", "Play Black Market Forgery");
                        purgeFragButtons.add(transact);
                    }

                }
                if (p1.getCrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < p1.getCrf() + 1 && x < 4); x++) {
                        Button transact = Buttons.blue(finChecker + "purge_Frags_CRF_" + x,
                            "Cultural Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                if (p1.getIrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < p1.getIrf() + 1 && x < 4); x++) {
                        Button transact = Buttons.green(finChecker + "purge_Frags_IRF_" + x,
                            "Industrial Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                if (p1.getHrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < p1.getHrf() + 1 && x < 4); x++) {
                        Button transact = Buttons.red(finChecker + "purge_Frags_HRF_" + x,
                            "Hazardous Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }

                if (p1.getUrf() > 0) {
                    for (int x = 1; x < p1.getUrf() + 1; x++) {
                        Button transact = Buttons.gray(finChecker + "purge_Frags_URF_" + x,
                            "Frontier Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                Button transact2 = Buttons.red(finChecker + "drawRelicFromFrag", "Finish Purging and Draw Relic");
                if (p1.hasAbility("a_new_edifice")) {
                    transact2 = Buttons.red(finChecker + "drawRelicFromFrag", "Finish Purging and Explore");
                }
                purgeFragButtons.add(transact2);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, purgeFragButtons);
            }
            case "generic" -> MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Doing unspecified component action.");
            case "absolMOW" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), p1.getFactionEmoji() + " is exhausting the " + Emojis.Agenda + "Minister of War" + Emojis.Absol + " and spending a strategy CC to remove 1 CC from the board");
                if (p1.getStrategicCC() > 0) {
                    p1.setStrategicCC(p1.getStrategicCC() - 1);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), p1.getFactionEmoji() + " strategy CC went from " + (p1.getStrategicCC() + 1) + " to " + p1.getStrategicCC());
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, game, event);
                }
                List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(p1, game, event, "absol");
                MessageChannel channel = p1.getCorrectChannel();
                MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
                game.setStoredValue("absolMOW", p1.getFaction());
            }
            case "actionCards" -> {
                String secretScoreMsg = "_ _\nClick a button below to play an Action Card";
                List<Button> acButtons = ACInfo.getActionPlayActionCardButtons(game, p1);
                if (!acButtons.isEmpty()) {
                    List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(secretScoreMsg, acButtons);
                    ThreadChannel cardsInfoThreadChannel = p1.getCardsInfoThread();
                    for (MessageCreateData message : messageList) {
                        cardsInfoThreadChannel.sendMessage(message).queue();
                    }
                }

            }
            case "doStarCharts" -> {
                ButtonHelper.purge2StarCharters(p1);
                DrawBlueBackTile.drawBlueBackTiles(event, game, p1, 1);
            }
        }

        if (!firstPart.contains("ability") && !firstPart.contains("getRelic") && !firstPart.contains("pn")) {
            serveNextComponentActionButtons(event, game, p1);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static void resolveRelicComponentAction(Game game, Player player, ButtonInteractionEvent event,
        String relicID) {
        if (!Mapper.isValidRelic(relicID) || !player.hasRelic(relicID)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Invalid relic or player does not have specified relic: `" + relicID + "`");
            return;
        }
        String purgeOrExhaust = "Purged";
        if ("titanprototype".equalsIgnoreCase(relicID) || "absol_jr".equalsIgnoreCase(relicID)) { // EXHAUST THE RELIC
            List<Button> buttons2 = AgendaHelper.getPlayerOutcomeButtons(game, null, "jrResolution", null);
            player.addExhaustedRelic(relicID);
            purgeOrExhaust = "Exhausted";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                "Use buttons to decide who to use JR on", buttons2);

            // OFFER TCS
            for (Player p2 : game.getRealPlayers()) {
                if (p2.hasTech("tcs") && !p2.getExhaustedTechs().contains("tcs")) {
                    List<Button> buttons3 = new ArrayList<>();
                    buttons3.add(Buttons.green("exhaustTCS_" + relicID + "_" + player.getFaction(),
                        "Exhaust TCS to Ready " + relicID));
                    buttons3.add(Buttons.red("deleteButtons", "Decline"));
                    String msg = p2.getRepresentation(true, true)
                        + " you have the opportunity to exhaust your TCS tech to ready " + relicID
                        + " and potentially resolve a transaction.";
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), msg, buttons3);
                }
            }
        } else { // PURGE THE RELIC
            player.removeRelic(relicID);
            player.removeExhaustedRelic(relicID);
        }

        RelicModel relicModel = Mapper.getRelic(relicID);
        String message = player.getFactionEmoji() + " " + purgeOrExhaust + ": " + relicModel.getName();
        MessageHelper.sendMessageToChannelWithEmbed(event.getMessageChannel(), message, relicModel.getRepresentationEmbed(false, true));

        // SPECIFIC HANDLING //TODO: Move this shite to RelicPurge
        switch (relicID) {
            case "enigmaticdevice" -> ButtonHelperActionCards.resolveResearch(game, player, relicID, event);
            case "codex", "absol_codex" -> ButtonHelper.offerCodexButtons(player, game, event);
            case "nanoforge", "absol_nanoforge", "baldrick_nanoforge" -> ButtonHelper.offerNanoforgeButtons(player, game, event);
            case "decrypted_cartoglyph" -> DrawBlueBackTile.drawBlueBackTiles(event, game, player, 3);
            case "throne_of_the_false_emperor" -> {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("drawRelic", "Draw a relic"));
                buttons.add(Buttons.blue("thronePoint", "Score a secret someone else scored"));
                buttons.add(Buttons.red("deleteButtons", "Score one of your unscored secrets"));
                message = player.getRepresentation()
                    + " choose one of the options. Reminder than you can't score more secrets than normal with this relic (even if they're someone else's), and you can't score the same secret twice."
                    + " If scoring one of your unscored secrets, just score it via the normal process after pressing the button.";
                MessageHelper.sendMessageToChannel(event.getChannel(), message, buttons);
            }
            case "dynamiscore", "absol_dynamiscore" -> {
                int oldTg = player.getTg();
                player.setTg(oldTg + player.getCommoditiesTotal() + 2);
                if ("absol_dynamiscore".equals(relicID)) {
                    player.setTg(oldTg + Math.min(player.getCommoditiesTotal() * 2, 10));
                } else {
                    player.setTg(oldTg + player.getCommoditiesTotal() + 2);
                }
                message = player.getRepresentation(true, true) + " Your TGs increased from " + oldTg + " -> "
                    + player.getTg();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                ButtonHelperAbilities.pillageCheck(player, game);
                ButtonHelperAgents.resolveArtunoCheck(player, game, player.getTg() - oldTg);
            }
            case "stellarconverter" -> {
                message = player.getRepresentation(true, true) + " Select the planet you want to destroy";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message,
                    ButtonHelper.getButtonsForStellar(player, game));
            }
            case "passturn" -> {
                MessageHelper.sendMessageToChannelWithButton(event.getChannel(), null, Buttons.REDISTRIBUTE_CCs);
            }
            case "titanprototype", "absol_jr" -> {
                // handled above
            }
            default -> MessageHelper.sendMessageToChannel(event.getChannel(),
                "This Relic is not tied to any automation. Please resolve manually.");
        }
    }

    public static void serveNextComponentActionButtons(GenericInteractionCreateEvent event, Game game,
        Player player) {
        String message = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, systemButtons);
    }
}
