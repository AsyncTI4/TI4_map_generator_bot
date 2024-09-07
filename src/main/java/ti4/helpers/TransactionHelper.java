package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class TransactionHelper {
    public static void acceptTransactionOffer(Player p1, Player p2, Game game, ButtonInteractionEvent event) {
        String summary = "The following transaction between " + p1.getRepresentation(false, false) + " and" + p2.getRepresentation(false, false) + " has been accepted:\n" + buildTransactionOffer(p1, p2, game, false);
        List<String> transactionItems = p1.getTransactionItems();
        List<Player> players = new ArrayList<>();
        players.add(p1);
        players.add(p2);
        boolean debtOnly = true;
        MessageChannel channel = p1.getCorrectChannel();
        if (game.getName().equalsIgnoreCase("pbd1000")) {
            channel = game.getTableTalkChannel();
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), p1.getRepresentation(false, false) + " and" + p2.getRepresentation(false, false) + " have transacted");
        }

        String messageText = "A transaction has been ratified between:\n-# - " + p1.getRepresentation() + "\n-# - " + p2.getRepresentation();
        MessageEmbed embed = getTransactionEmbed(p1, p2, game, true);
        MessageHelper.sendMessageToChannelWithEmbed(channel, messageText, embed);
        embed = getTransactionEmbed(p1, p2, game, false);
        MessageHelper.sendMessageToChannelWithEmbed(channel, messageText, embed); // del
        MessageHelper.sendMessageToChannel(channel, buildTransactionOffer(p1, p2, game, true)); //del
        MessageHelper.sendMessageToChannel(channel, buildTransactionOffer(p1, p2, game, false)); //del
        MessageHelper.sendMessageToChannel(channel, p1.getFactionEmoji() + p2.getFactionEmoji() + " transaction details below: ");
        for (Player sender : players) {
            Player receiver = p2;
            if (sender == p2) {
                receiver = p1;
            }
            for (String item : transactionItems) {
                if (item.contains("sending" + sender.getFaction()) && item.contains("receiving" + receiver.getFaction())) {
                    String thingToTransact = item.split("_")[2];
                    String furtherDetail = item.split("_")[3];
                    int amountToTransact = 1;
                    if (((thingToTransact.equalsIgnoreCase("ACs") || thingToTransact.equalsIgnoreCase("PNs")) && furtherDetail.contains("generic"))) {
                        amountToTransact = Integer.parseInt("" + furtherDetail.charAt(furtherDetail.length() - 1));
                        furtherDetail = furtherDetail.substring(0, furtherDetail.length() - 1);
                    }
                    String spoofedButtonID = "send_" + thingToTransact + "_" + receiver.getFaction() + "_" + furtherDetail;
                    if (!thingToTransact.toLowerCase().contains("debt")) {
                        debtOnly = false;
                    }
                    switch (thingToTransact) {
                        case "ACs" -> {
                            switch (furtherDetail) {
                                case "generic" -> {
                                    for (int x = 0; x < amountToTransact; x++) {
                                        String buttonID = "transact_ACs_" + receiver.getFaction();
                                        ButtonHelper.resolveSpecificTransButtonsOld(game, sender, buttonID, event);
                                    }
                                }
                                default -> {
                                    ButtonHelper.resolveSpecificTransButtonPress(game, sender, spoofedButtonID, event, false);
                                }
                            }
                        }
                        case "PNs" -> {
                            switch (furtherDetail) {
                                case "generic" -> {
                                    List<Button> stuffToTransButtons = ButtonHelper.getForcedPNSendButtons(game, receiver, sender);
                                    String message = sender.getRepresentation(true, true)
                                        + "Please select the promissory note you would like to send.";
                                    MessageHelper.sendMessageToChannelWithButtons(sender.getCardsInfoThread(), message, stuffToTransButtons);
                                }
                                default -> {
                                    ButtonHelper.resolveSpecificTransButtonPress(game, sender, spoofedButtonID, event, false);
                                }
                            }
                        }

                        case "Planets" -> {
                            ButtonHelperFactionSpecific.resolveHacanMechTradeStepOne(sender, game, event, "send_" + furtherDetail + "_" + receiver.getFaction());
                        }
                        case "AlliancePlanets" -> {
                            String exhausted = "exhausted";
                            if (!furtherDetail.contains(exhausted)) {
                                exhausted = "refreshed";
                            }
                            furtherDetail = furtherDetail.replace(exhausted, "");

                            ButtonHelper.resolveAllianceMemberPlanetTrade(sender, game, event, "send_" + furtherDetail + "_" + receiver.getFaction() + "_" + exhausted);
                        }
                        case "dmz" -> {
                            ButtonHelper.resolveDMZTrade(sender, game, event, "send_" + furtherDetail + "_" + receiver.getFaction());
                        }
                        default -> {
                            ButtonHelper.resolveSpecificTransButtonPress(game, sender, spoofedButtonID, event, false);
                        }
                    }

                }
            }
        }
        
        // Send Summary to Player's CardsInfo threads
        embed = getTransactionEmbed(p1, p2, game, false);
        String summary = "The following transaction between " + p1.getRepresentation(false, false) + " and" + p2.getRepresentation(false, false) + " has been accepted";
        MessageHelper.sendMessageToChannelWithEmbed(p1.getCardsInfoThread(), summary, embed);
        MessageHelper.sendMessageToChannelWithEmbed(p2.getCardsInfoThread(), summary, embed);
        
        p1.clearTransactionItemsWith(p2);
        if (!debtOnly) {
            ButtonHelperAbilities.pillageCheck(p2, game);
            ButtonHelperAbilities.pillageCheck(p1, game);
        }
    }

    public static MessageEmbed getTransactionEmbed(Player p1, Player p2, Game game, boolean publiclyShared) {
        EmbedBuilder eb = new EmbedBuilder();
        String trans = buildTransactionOffer(p1, p2, game, publiclyShared);
        if (trans.startsWith("\n")) {
            trans = StringUtils.substringAfter(trans, "\n");
        }
        trans = trans.replace("**", ""); // kill all the bold formatting

        // Handle the formatting of buildTransactionOffer based on publiclyShared
        if (!publiclyShared) {
            trans = StringUtils.substringBeforeLast(trans, "\n");
        }
        String transactionSeparator = publiclyShared ? "\n" : "\n\n";
        String itemSeparator = publiclyShared ? ": " : ":\n";
        String target = publiclyShared ? "; " : "\n";
        String replacement = "\n> - ";

        // Player 1
        String trans1 = StringUtils.substringBefore(trans, transactionSeparator);
        String title1 = "> " + StringUtils.substringBefore(trans1, ">") + "> gives:";
        String items1 = StringUtils.substringAfter(trans1, itemSeparator);
        String text1 = items1.isEmpty() ? "> - " + getNothingMessage() : "> - " + items1.replace(target, replacement);
        eb.addField(title1, text1, true);

        // Player 2
        String trans2 = StringUtils.substringAfter(trans, transactionSeparator);
        String title2 = "> " + StringUtils.substringBefore(trans2, ">") + "> gives:";
        String items2 = StringUtils.substringAfter(trans2, itemSeparator);
        String text2 = items2.isEmpty() ? "> - " + getNothingMessage() : "> - " + items2.replace(target, replacement);
        eb.addField(title2, text2, true);

        return eb.build();
    }

    public static String buildTransactionOffer(Player p1, Player p2, Game game, boolean publiclyShared) {
        List<String> transactionItems = p1.getTransactionItems();
        String wholeSummary = "";
        List<Player> players = new ArrayList<>();
        players.add(p1);
        players.add(p2);
        for (Player sender : players) {
            Player receiver = p2;
            if (sender == p2) {
                receiver = p1;
            }
            int num = 1;
            String summary = "**" + sender.getRepresentation(false, false) + " gives " + receiver.getRepresentation(false, false) + " the following:**\n";
            if (publiclyShared) {
                summary = sender.getFactionEmoji() + " gives " + receiver.getFactionEmoji() + ": ";
                num = 0;
            }
            for (String item : transactionItems) {
                if (item.contains("sending" + sender.getFaction()) && item.contains("receiving" + receiver.getFaction())) {
                    String thingToTransact = item.split("_")[2];
                    String furtherDetail = item.split("_")[3];
                    int amountToTransact = 1;
                    if (thingToTransact.equalsIgnoreCase("tgs") || thingToTransact.contains("Debt") || thingToTransact.equalsIgnoreCase("comms")) {
                        amountToTransact = Integer.parseInt(furtherDetail);
                    }
                    if (thingToTransact.equalsIgnoreCase("frags") || ((thingToTransact.equalsIgnoreCase("PNs") || thingToTransact.equalsIgnoreCase("ACs")) && furtherDetail.contains("generic"))) {
                        amountToTransact = Integer.parseInt("" + furtherDetail.charAt(furtherDetail.length() - 1));
                        furtherDetail = furtherDetail.substring(0, furtherDetail.length() - 1);
                    }
                    switch (thingToTransact) {
                        case "TGs" -> {
                            summary = summary + amountToTransact + " " + Emojis.tg + "\n";
                        }
                        case "SendDebt" -> {
                            summary = summary + "Send " + amountToTransact + " debt\n";
                        }
                        case "ClearDebt" -> {
                            summary = summary + "Clear " + amountToTransact + " debt\n";
                        }
                        case "Comms" -> {
                            summary = summary + amountToTransact + " " + Emojis.comm + "\n";
                        }
                        case "shipOrders" -> {
                            summary = summary + Mapper.getRelic(furtherDetail).getName() + Emojis.axis + "\n";
                        }
                        case "starCharts" -> {
                            summary = summary + Mapper.getRelic(furtherDetail).getName() + Emojis.DiscordantStars + "\n";
                        }
                        case "ACs" -> {
                            switch (furtherDetail) {
                                case "generic" -> {
                                    summary = summary + amountToTransact + " " + Emojis.ActionCard + " to be specified verbally\n";
                                }
                                default -> {
                                    int acNum = Integer.parseInt(furtherDetail);
                                    String acID = null;
                                    if (!sender.getActionCards().containsValue(acNum)) {
                                        continue;
                                    }
                                    for (Map.Entry<String, Integer> ac : sender.getActionCards().entrySet()) {
                                        if (ac.getValue().equals(acNum)) {
                                            acID = ac.getKey();
                                        }
                                    }
                                    if (publiclyShared) {
                                        summary = summary + Emojis.ActionCard + "\n";
                                    } else {
                                        summary = summary + Emojis.ActionCard + " " + Mapper.getActionCard(acID).getName() + "\n";
                                    }
                                }
                            }
                        }
                        case "PNs" -> {
                            switch (furtherDetail) {
                                case "generic" -> {
                                    if (publiclyShared) {
                                        summary = summary + Emojis.PN + "\n";
                                    } else {
                                        summary = summary + amountToTransact + " " + Emojis.PN + " to be specified verbally\n";
                                    }
                                }
                                default -> {
                                    String id = null;
                                    int pnIndex;
                                    try {
                                        pnIndex = Integer.parseInt(furtherDetail);
                                        for (Map.Entry<String, Integer> pn : sender.getPromissoryNotes().entrySet()) {
                                            if (pn.getValue().equals(pnIndex)) {
                                                id = pn.getKey();
                                            }
                                        }
                                    } catch (NumberFormatException e) {
                                        id = furtherDetail.replace("fin9", "_");
                                    }
                                    if (id == null) {
                                        continue;
                                    }
                                    if (publiclyShared) {
                                        summary = summary + Emojis.PN + "\n";
                                    } else {
                                        summary = summary + Emojis.PN + " " + StringUtils.capitalize(Mapper.getPromissoryNote(id).getColor().orElse("")) + " " + Mapper.getPromissoryNote(id).getName() + "\n";
                                    }
                                }
                            }
                        }
                        case "Frags" -> {
                            summary = summary + amountToTransact + " " + Emojis.getFragEmoji(furtherDetail) + "\n";
                        }
                        case "Planets", "AlliancePlanets", "dmz" -> {
                            summary = summary + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(furtherDetail, game) + "\n";
                        }
                        case "action" -> {
                            summary = summary + "An in-game " + furtherDetail + " action\n";
                        }
                        default -> {
                            summary += " some odd thing: `" + thingToTransact + "`\n";
                        }
                    }

                }
            }
            if (StringUtils.countMatches(summary, "\n") > num) {
                if (publiclyShared) {
                    summary = summary.replace("\n", "; ");
                    summary = summary.substring(0, summary.length() - 2);
                }
                wholeSummary = wholeSummary + "\n" + summary;

            } else {
                wholeSummary = wholeSummary + "\n" + summary + getNothingMessage();
            }
        }

        return wholeSummary;
    }

    public static String getNothingMessage() {
        int result = ThreadLocalRandom.current().nextInt(1, 21);
        return switch (result) {
            case 1 -> "Nothing But Respect And Good Will";
            case 2 -> "Some Pocket Lint";
            case 3 -> "Sunshine and Rainbows";
            case 4 -> "A Feeling Of Accomplishment";
            case 5 -> "A Crisp High Five";
            case 6 -> "A Well Written Thank-You Note";
            case 7 -> "Heartfelt Thanks";
            case 8 -> "The Best Vibes";
            case 9 -> "A Bot's Blessing For Your Trouble";
            case 10 -> "Good Karma";
            case 11 -> "A Mewling Kitten";
            case 12 -> "A Lost Virtual Puppy";
            case 13 -> "A Fortune Cookie";
            case 14 -> "A Firm Handshake";
            case 15 -> "A Friendly Wave";
            case 16 -> "Well Wishes";
            case 17 -> "A Home-cooked Meal";
            case 18 -> "$1000 In Monopoly Money";
            case 19 -> "Forgiveness For Past Mistakes";
            case 20 -> "A Lucky Rock";
            default -> "Nothing";
        };
    }
}
