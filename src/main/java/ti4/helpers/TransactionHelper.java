package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.explore.SendFragments;
import ti4.commands.player.ClearDebt;
import ti4.commands.uncategorized.CardsInfo;
import ti4.generator.Mapper;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;

public class TransactionHelper {
    public static void acceptTransactionOffer(Player p1, Player p2, Game game, ButtonInteractionEvent event) {
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
                                        resolveSpecificTransButtonsOld(game, sender, buttonID, event);
                                    }
                                }
                                default -> {
                                    resolveSpecificTransButtonPress(game, sender, spoofedButtonID, event, false);
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
                                    resolveSpecificTransButtonPress(game, sender, spoofedButtonID, event, false);
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
                            resolveSpecificTransButtonPress(game, sender, spoofedButtonID, event, false);
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

    public static void resolveSpecificTransButtonsNew(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        List<Button> stuffToTransButtons = new ArrayList<>();
        buttonID = buttonID.replace("newTransact_", "");
        String thingToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        String senderFaction = buttonID.split("_")[1];
        String receiverFaction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(receiverFaction);
        Player p1 = game.getPlayerFromColorOrFaction(senderFaction);
        if (p2 == null) {
            return;
        }
        boolean requesting = (p1 != player);
        Player opposing = p2;
        if (player == p2) {
            opposing = p1;
        }
        String message = "Current Transaction Offer is: " + TransactionHelper.buildTransactionOffer(player, opposing, game, false) + "\n";
        String requestOrOffer = "offer";
        if (requesting) {
            requestOrOffer = "request";
        }
        switch (thingToTrans) {
            case "TGs" -> {
                message = message + "Click the amount of TGs you would like to " + requestOrOffer;
                for (int x = 1; x < p1.getTg() + 1; x++) {
                    Button transact = Button.success(
                        "offerToTransact_TGs_" + p1.getFaction() + "_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
            }
            case "Comms" -> {
                message = message + "Click the amount of commodities you would like to " + requestOrOffer;
                for (int x = 1; x < p1.getCommodities() + 1; x++) {
                    Button transact = Button.success(
                        "offerToTransact_Comms_" + p1.getFaction() + "_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }

            }
            case "ClearDebt" -> {
                message = message + "Click the amount of debt you would like to " + requestOrOffer + " cleared";
                for (int x = 1; x < p1.getDebtTokenCount(p2.getColor()) + 1; x++) {
                    Button transact = Button.success(
                        "offerToTransact_ClearDebt_" + p1.getFaction() + "_" + p2.getFaction() + "_" + x,
                        "" + x);
                    stuffToTransButtons.add(transact);
                }

            }
            case "SendDebt" -> {
                message = message + "Click the amount of debt you would like to " + requestOrOffer;
                for (int x = 1; x < 6; x++) {
                    Button transact = Button.success(
                        "offerToTransact_SendDebt_" + p1.getFaction() + "_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }

            }
            case "shipOrders" -> {
                message = message + "Click the axis order you would like to " + requestOrOffer;
                for (String shipOrder : ButtonHelper.getPlayersShipOrders(p1)) {
                    Button transact = Button.success(
                        "offerToTransact_shipOrders_" + p1.getFaction() + "_" + p2.getFaction() + "_" + shipOrder,
                        "" + Mapper.getRelic(shipOrder).getName());
                    stuffToTransButtons.add(transact);
                }

            }
            case "starCharts" -> {
                message = message + "Click the star chart you would like to " + requestOrOffer;
                for (String shipOrder : ButtonHelper.getPlayersStarCharts(p1)) {
                    Button transact = Button.success(
                        "offerToTransact_starCharts_" + p1.getFaction() + "_" + p2.getFaction() + "_" + shipOrder,
                        "" + Mapper.getRelic(shipOrder).getName());
                    stuffToTransButtons.add(transact);
                }
            }
            case "Planets" -> {
                message = message + "Click the planet you would like to " + requestOrOffer;
                for (String planet : p1.getPlanetsAllianceMode()) {
                    if (planet.contains("custodia") || planet.contains("ghoti")) {
                        continue;
                    }
                    if (ButtonHelper.getUnitHolderFromPlanetName(planet, game).getUnitCount(UnitType.Mech,
                        p1.getColor()) > 0) {
                        stuffToTransButtons.add(Button.secondary(
                            "offerToTransact_Planets_" + p1.getFaction() + "_" + p2.getFaction() + "_" + planet,
                            Helper.getPlanetRepresentation(planet, game)));
                    }
                }

            }
            case "AlliancePlanets" -> {
                message = message + "Click the planet you would like to " + requestOrOffer;
                for (String planet : p1.getPlanets()) {
                    if (planet.contains("custodia") || planet.contains("ghoti")) {
                        continue;
                    }
                    UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
                    if (unitHolder != null && unitHolder.getUnitColorsOnHolder().contains(p2.getColorID())) {
                        String refreshed = "refreshed";
                        if (p1.getExhaustedPlanets().contains(planet)) {
                            refreshed = "exhausted";
                        }
                        stuffToTransButtons.add(Button.secondary(
                            "offerToTransact_AlliancePlanets_" + p1.getFaction() + "_" + p2.getFaction() + "_"
                                + planet + refreshed,
                            Helper.getPlanetRepresentation(planet, game)));
                    }
                }
            }
            case "ACs" -> {
                if (requesting) {
                    message = message + player.getRepresentation()
                        + " Click the number of ACs you'd like to request. Since ACs are private info, you will have to use messages to explain what ACs you want, these buttons will just make sure that the player is offered buttons to send.";
                    int limit = Math.min(7, p2.getAc());
                    for (int x = 1; x < limit + 1; x++) {
                        Button transact = Button.success(
                            "offerToTransact_ACs_" + p1.getFaction() + "_" + p2.getFaction() + "_generic" + x,
                            x + " ACs");
                        stuffToTransButtons.add(transact);
                    }
                } else {
                    message = message + player.getRepresentation()
                        + " Click the GREEN button that indicates the AC you would like to " + requestOrOffer;
                    for (String acShortHand : p1.getActionCards().keySet()) {
                        Button transact = Button.success(
                            "offerToTransact_ACs_" + p1.getFaction() + "_" + p2.getFaction() + "_"
                                + p1.getActionCards().get(acShortHand),
                            Mapper.getActionCard(acShortHand).getName());
                        stuffToTransButtons.add(transact);
                    }
                }
            }
            case "PNs" -> {
                if (requesting) {
                    message = message + player.getRepresentation()
                        + " Click the PN you'd like to request. Since PNs are private info, all of the player's starting PNs which are not in play areas are available, though the player may not currently hold all of these. Click TBD Note if you want someone else's PN, and it will give the player the option to send it.";
                    for (String pnShortHand : p1.getPromissoryNotesOwned()) {
                        if (ButtonHelper.anyoneHaveInPlayArea(game, pnShortHand)) {
                            continue;
                        }
                        PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnShortHand);
                        Player owner = game.getPNOwner(pnShortHand);
                        if (p1.getPromissoryNotes().containsKey(pnShortHand)) {
                            stuffToTransButtons.add(Button
                                .success("offerToTransact_PNs_" + p1.getFaction() + "_" + p2.getFaction() + "_"
                                    + p1.getPromissoryNotes().get(pnShortHand), promissoryNote.getName())
                                .withEmoji(Emoji.fromFormatted(owner.getFactionEmoji())));
                        } else {
                            stuffToTransButtons.add(Button
                                .success("offerToTransact_PNs_" + p1.getFaction() + "_" + p2.getFaction() + "_"
                                    + pnShortHand.replace("_", "fin9"), promissoryNote.getName())
                                .withEmoji(Emoji.fromFormatted(owner.getFactionEmoji())));
                        }

                    }
                    Button transact = Button
                        .primary("offerToTransact_PNs_" + p1.getFaction() + "_" + p2.getFaction() + "_"
                            + "generic1", "TBD PN");

                    stuffToTransButtons.add(transact);
                } else {
                    message = message + p1.getRepresentation(true, true) + " Click the PN you would like to "
                        + requestOrOffer;
                    for (String pnShortHand : p1.getPromissoryNotes().keySet()) {
                        if (p1.getPromissoryNotesInPlayArea().contains(pnShortHand)
                            || (p2.getAbilities().contains("hubris") && pnShortHand.endsWith("an"))) {
                            continue;
                        }
                        PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnShortHand);
                        Player owner = game.getPNOwner(pnShortHand);
                        Button transact = Button
                            .success("offerToTransact_PNs_" + p1.getFaction() + "_" + p2.getFaction() + "_"
                                + p1.getPromissoryNotes().get(pnShortHand), promissoryNote.getName())
                            .withEmoji(Emoji.fromFormatted(owner.getFactionEmoji()));

                        stuffToTransButtons.add(transact);
                    }
                }
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    "Reminder that, unlike other things, you may only send a person 1 PN in a transaction.");
            }
            case "Frags" -> {
                message = message + "Click the amount of fragments you would like to " + requestOrOffer;
                for (int x = 1; x < p1.getCrf() + 1; x++) {
                    Button transact = Button.primary(
                        "offerToTransact_Frags_" + p1.getFaction() + "_" + p2.getFaction() + "_CRF" + x,
                        "Cultural Fragments (" + x + ")");
                    stuffToTransButtons.add(transact);
                }

                for (int x = 1; x < p1.getIrf() + 1; x++) {
                    Button transact = Button.success(
                        "offerToTransact_Frags_" + p1.getFaction() + "_" + p2.getFaction() + "_IRF" + x,
                        "Industrial Fragments (" + x + ")");
                    stuffToTransButtons.add(transact);
                }

                for (int x = 1; x < p1.getHrf() + 1; x++) {
                    Button transact = Button.danger(
                        "offerToTransact_Frags_" + p1.getFaction() + "_" + p2.getFaction() + "_HRF" + x,
                        "Hazardous Fragments (" + x + ")");
                    stuffToTransButtons.add(transact);
                }

                for (int x = 1; x < p1.getUrf() + 1; x++) {
                    Button transact = Button.secondary(
                        "offerToTransact_Frags_" + p1.getFaction() + "_" + p2.getFaction() + "_URF" + x,
                        "Frontier Fragments (" + x + ")");
                    stuffToTransButtons.add(transact);
                }

            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, stuffToTransButtons);

    }

    public static void resolveOfferToTransact(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String item = buttonID.split("_")[1];
        String sender = buttonID.split("_")[2];
        String receiver = buttonID.split("_")[3];
        String extraDetail = buttonID.split("_")[4];
        player.addTransactionItem("sending" + sender + "_receiving" + receiver + "_" + item + "_" + extraDetail);
        Player p1 = game.getPlayerFromColorOrFaction(sender);
        Player p2 = game.getPlayerFromColorOrFaction(receiver);
        Player opposing = p2;
        if (player == p2) {
            opposing = p1;
        }
        String message = "Current Transaction Offer is: " + TransactionHelper.buildTransactionOffer(player, opposing, game, false)
            + "\n## Click something else that you want to request from " + p1.getRepresentation(false, false);
        if (p1 == player) {
            message = "Current Transaction Offer is: " + TransactionHelper.buildTransactionOffer(player, opposing, game, false)
                + "\n## Click something else that YOU want to offer";
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message,
            ButtonHelper.getStuffToTransButtonsNew(game, player, p1, p2));
    }

    public static void getNewTransaction(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String sender = buttonID.split("_")[1];
        String receiver = buttonID.split("_")[2];
        Player p1 = game.getPlayerFromColorOrFaction(sender);
        Player p2 = game.getPlayerFromColorOrFaction(receiver);
        Player opposing = p2;
        if (player == p2) {
            opposing = p1;
        }
        String message = "Current Transaction Offer is: " + TransactionHelper.buildTransactionOffer(player, opposing, game, false)
            + "\n## Click something that you want to request from " + p1.getRepresentation(false, false);
        if (p1 == player) {
            message = "Current Transaction Offer is: " + TransactionHelper.buildTransactionOffer(player, opposing, game, false)
                + "\n## Click something that YOU want to offer";
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message,
            ButtonHelper.getStuffToTransButtonsNew(game, player, p1, p2));
    }

    @ButtonHandler("sendOffer_")
    public static void sendOffer(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " sent a transaction offer to " + p2.getFactionEmoji());
        if (game.getTableTalkChannel() != null) {
            MessageHelper.sendMessageToChannel(game.getTableTalkChannel(), "An offer has been sent by " + player.getFactionEmoji() + " to " + p2.getFactionEmoji() + ". The offer is: " + TransactionHelper.buildTransactionOffer(player, p2, game, true));
        }

        MessageEmbed embed = TransactionHelper.getTransactionEmbed(player, p2, game, false);

        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.danger("rescindOffer_" + p2.getFaction(), "Rescind Offer"));
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(player.getCardsInfoThread(), player.getRepresentationNoPing() + " you sent a transaction offer to " + p2.getRepresentationNoPing() + ":", Collections.singletonList(embed), buttons);

        event.getMessage().delete().queue();

        int offerNumber = 1;
        String key = "offerFrom" + player.getFaction() + "To" + p2.getFaction();
        if (!game.getStoredValue(key).isEmpty()) {
            offerNumber = Integer.parseInt(game.getStoredValue(key)) + 1;
        }
        game.setStoredValue(key, offerNumber + "");

        buttons = new ArrayList<>();
        buttons.add(Button.success("acceptOffer_" + player.getFaction() + "_" + offerNumber, "Accept"));
        buttons.add(Button.danger("rejectOffer_" + player.getFaction(), "Reject"));
        buttons.add(Button.danger("resetOffer_" + player.getFaction(), "Reject and CounterOffer"));
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(p2.getCardsInfoThread(), p2.getRepresentation() + " you have received a transaction offer from " + player.getRepresentationNoPing() + ":", Collections.singletonList(embed), buttons);
    }

    public static void resolveSpecificTransButtonsOld(Game game, Player p1, String buttonID,
        ButtonInteractionEvent event) {
        String finChecker = "FFCC_" + p1.getFaction() + "_";

        List<Button> stuffToTransButtons = new ArrayList<>();
        buttonID = buttonID.replace("transact_", "");
        String thingToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        String factionToTrans = buttonID.substring(buttonID.indexOf("_") + 1);
        Player p2 = game.getPlayerFromColorOrFaction(factionToTrans);
        if (p2 == null) {
            return;
        }

        switch (thingToTrans) {
            case "TGs" -> {
                String message = "Click the amount of TGs you would like to send";
                for (int x = 1; x < p1.getTg() + 1; x++) {
                    Button transact = Button.success(finChecker + "send_TGs_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "Comms" -> {
                String message = "Click the amount of commodities you would like to send";
                for (int x = 1; x < p1.getCommodities() + 1; x++) {
                    Button transact = Button.success(finChecker + "send_Comms_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "ClearDebt" -> {
                String message = "Click the amount of debt you would like to clear";
                for (int x = 1; x < p1.getDebtTokenCount(p2.getColor()) + 1; x++) {
                    Button transact = Button.success(finChecker + "send_ClearDebt_" + p2.getFaction() + "_" + x,
                        "" + x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "SendDebt" -> {
                String message = "Click the amount of debt you would like to send";
                for (int x = 1; x < 6; x++) {
                    Button transact = Button.success(finChecker + "send_SendDebt_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "shipOrders" -> {
                String message = "Click the axis order you would like to send";
                for (String shipOrder : ButtonHelper.getPlayersShipOrders(p1)) {
                    Button transact = Button.success(
                        finChecker + "send_shipOrders_" + p2.getFaction() + "_" + shipOrder,
                        "" + Mapper.getRelic(shipOrder).getName());
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "starCharts" -> {
                String message = "Click the star chart you would like to send";
                for (String shipOrder : ButtonHelper.getPlayersStarCharts(p1)) {
                    Button transact = Button.success(
                        finChecker + "send_starCharts_" + p2.getFaction() + "_" + shipOrder,
                        "" + Mapper.getRelic(shipOrder).getName());
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "Planets" -> {
                String message = "Click the planet you would like to send";
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message,
                    ButtonHelperFactionSpecific.getTradePlanetsWithHacanMechButtons(p1, p2, game));
            }
            case "AlliancePlanets" -> {
                String message = "Click the planet you would like to send";
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message,
                    ButtonHelper.getTradePlanetsWithAlliancePartnerButtons(p1, p2, game));
            }
            case "ACs" -> {
                String message = p1.getRepresentation()
                    + " Click the GREEN button that indicates the AC you would like to send";
                for (String acShortHand : p1.getActionCards().keySet()) {
                    Button transact = Button.success(
                        finChecker + "send_ACs_" + p2.getFaction() + "_" + p1.getActionCards().get(acShortHand),
                        Mapper.getActionCard(acShortHand).getName());
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(), message, stuffToTransButtons);
            }
            case "PNs" -> {
                PNInfo.sendPromissoryNoteInfo(game, p1, false);
                String message = p1.getRepresentation(true, true) + " Click the PN you would like to send.";

                for (String pnShortHand : p1.getPromissoryNotes().keySet()) {
                    if (p1.getPromissoryNotesInPlayArea().contains(pnShortHand)
                        || (p2.getAbilities().contains("hubris") && pnShortHand.endsWith("an"))) {
                        continue;
                    }
                    PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnShortHand);
                    Player owner = game.getPNOwner(pnShortHand);
                    Button transact;
                    if (game.isFowMode()) {
                        transact = Button.success(
                            finChecker + "send_PNs_" + p2.getFaction() + "_"
                                + p1.getPromissoryNotes().get(pnShortHand),
                            owner.getColor() + " " + promissoryNote.getName());
                    } else {
                        transact = Button
                            .success(finChecker + "send_PNs_" + p2.getFaction() + "_"
                                + p1.getPromissoryNotes().get(pnShortHand), promissoryNote.getName())
                            .withEmoji(Emoji.fromFormatted(owner.getFactionEmoji()));
                    }
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(), message, stuffToTransButtons);
                MessageHelper.sendMessageToChannel(p1.getCardsInfoThread(),
                    "Reminder that, unlike other things, you may only send a person 1 PN in a transaction.");
            }
            case "Frags" -> {
                String message = "Click the amount of fragments you would like to send";

                if (p1.getCrf() > 0) {
                    for (int x = 1; x < p1.getCrf() + 1; x++) {
                        Button transact = Button.primary(finChecker + "send_Frags_" + p2.getFaction() + "_CRF" + x,
                            "Cultural Fragments (" + x + ")");
                        stuffToTransButtons.add(transact);
                    }
                }
                if (p1.getIrf() > 0) {
                    for (int x = 1; x < p1.getIrf() + 1; x++) {
                        Button transact = Button.success(finChecker + "send_Frags_" + p2.getFaction() + "_IRF" + x,
                            "Industrial Fragments (" + x + ")");
                        stuffToTransButtons.add(transact);
                    }
                }
                if (p1.getHrf() > 0) {
                    for (int x = 1; x < p1.getHrf() + 1; x++) {
                        Button transact = Button.danger(finChecker + "send_Frags_" + p2.getFaction() + "_HRF" + x,
                            "Hazardous Fragments (" + x + ")");
                        stuffToTransButtons.add(transact);
                    }
                }

                if (p1.getUrf() > 0) {
                    for (int x = 1; x < p1.getUrf() + 1; x++) {
                        Button transact = Button.secondary(finChecker + "send_Frags_" + p2.getFaction() + "_URF" + x,
                            "Frontier Fragments (" + x + ")");
                        stuffToTransButtons.add(transact);
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);

            }
        }
    }

    public static void resolveSpecificTransButtonPress(Game game, Player p1, String buttonID,
        ButtonInteractionEvent event, boolean oldWay) {
        String finChecker = "FFCC_" + p1.getFaction() + "_";
        buttonID = buttonID.replace("send_", "");
        List<Button> goAgainButtons = new ArrayList<>();

        String thingToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        buttonID = buttonID.replace(thingToTrans + "_", "");
        String factionToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        String amountToTrans = buttonID.substring(buttonID.indexOf("_") + 1);
        Player p2 = game.getPlayerFromColorOrFaction(factionToTrans);
        String message2 = "";
        String ident = p1.getRepresentation();
        String ident2 = p2.getRepresentation();
        switch (thingToTrans) {
            case "TGs" -> {
                int tgAmount = Integer.parseInt(amountToTrans);
                p1.setTg(p1.getTg() - tgAmount);
                p2.setTg(p2.getTg() + tgAmount);
                ButtonHelper.fullCommanderUnlockCheck(p2, game, "hacan", event);
                message2 = ident + " sent " + tgAmount + " TGs to " + ident2;
                if (!p2.hasAbility("binding_debts") && p2.getDebtTokenCount(p1.getColor()) > 0) {
                    int amount = Math.min(tgAmount, p2.getDebtTokenCount(p1.getColor()));
                    ClearDebt.clearDebt(p2, p1, amount);
                    message2 = message2 + "\n" + ident2 + " cleared " + amount + " debt tokens owned by " + ident;
                }
            }
            case "Comms" -> {
                int tgAmount = Integer.parseInt(amountToTrans);
                p1.setCommodities(p1.getCommodities() - tgAmount);
                if (!p1.isPlayerMemberOfAlliance(p2)) {
                    int targetTG = p2.getTg();
                    targetTG += tgAmount;
                    p2.setTg(targetTG);
                } else {
                    int targetTG = p2.getCommodities();
                    targetTG += tgAmount;
                    if (targetTG > p2.getCommoditiesTotal()) {
                        targetTG = p2.getCommoditiesTotal();
                    }
                    p2.setCommodities(targetTG);
                }

                ButtonHelper.fullCommanderUnlockCheck(p2, game, "hacan", event);
                ButtonHelperFactionSpecific.resolveDarkPactCheck(game, p1, p2, tgAmount);
                message2 = ident + " sent " + tgAmount + " Commodities to " + ident2;
                if (!p2.hasAbility("binding_debts") && p2.getDebtTokenCount(p1.getColor()) > 0) {
                    int amount = Math.min(tgAmount, p2.getDebtTokenCount(p1.getColor()));
                    ClearDebt.clearDebt(p2, p1, amount);
                    message2 = message2 + "\n" + ident2 + " cleared " + amount + " debt tokens owned by " + ident;
                }

            }
            case "WashComms" -> {
                int oldP1Tg = p1.getTg();
                int oldP2tg = p2.getTg();
                int oldP1Comms = p1.getCommodities();
                int newP1Comms = 0;
                int totalWashPowerP1 = p1.getCommodities() + p1.getTg();
                int totalWashPowerP2 = p2.getCommodities() + p2.getTg();
                if (oldP1Comms > totalWashPowerP2) {
                    newP1Comms = oldP1Comms - totalWashPowerP2;

                }
                int oldP2Comms = p2.getCommodities();
                int newP2Comms = 0;
                if (oldP2Comms > totalWashPowerP1) {
                    newP2Comms = oldP2Comms - totalWashPowerP1;
                }
                p1.setCommodities(newP1Comms);
                p2.setCommodities(newP2Comms);
                p1.setTg(p1.getTg() + (oldP1Comms - newP1Comms));
                p2.setTg(p2.getTg() + (oldP2Comms - newP2Comms));
                ButtonHelper.fullCommanderUnlockCheck(p2, game, "hacan", event);
                ButtonHelper.fullCommanderUnlockCheck(p1, game, "hacan", event);
                ButtonHelperFactionSpecific.resolveDarkPactCheck(game, p1, p2, oldP1Comms);
                ButtonHelperFactionSpecific.resolveDarkPactCheck(game, p2, p1, oldP2Comms);
                // ButtonHelperAbilities.pillageCheck(p1, game);
                // ButtonHelperAbilities.pillageCheck(p2, game);
                String id1 = p1.getFactionEmojiOrColor();
                String id2 = p2.getFactionEmojiOrColor();
                message2 = ident + " washed their " + (oldP1Comms - newP1Comms) + " Commodities with " + ident2 + "  ("
                    + id1 + " TGs went from (" + oldP1Tg + "->" + p1.getTg() + "))\n" + id2
                    + " washed their " + (oldP2Comms - newP2Comms) + " Commodities with " + id1 + " (" + id2
                    + " TGs went from (" + oldP2tg + "->" + p2.getTg() + "))";
            }
            case "shipOrders" -> {
                message2 = ident + " sent " + Mapper.getRelic(amountToTrans).getName() + " to " + ident2;
                p1.removeRelic(amountToTrans);
                p2.addRelic(amountToTrans);
            }
            case "SendDebt" -> {
                message2 = ident + " sent " + amountToTrans + " debt tokens to " + ident2;
                p2.addDebtTokens(p1.getColor(), Integer.parseInt(amountToTrans));
                ButtonHelper.fullCommanderUnlockCheck(p2, game, "vaden", event);
            }
            case "ClearDebt" -> {
                message2 = ident + " cleared " + amountToTrans + " debt tokens of " + ident2;
                p1.removeDebtTokens(p2.getColor(), Integer.parseInt(amountToTrans));
            }
            case "starCharts" -> {
                message2 = ident + " sent " + Mapper.getRelic(amountToTrans).getName() + " to " + ident2;
                p1.removeRelic(amountToTrans);
                p2.addRelic(amountToTrans);
            }
            case "ACs" -> {

                message2 = ident + " sent AC #" + amountToTrans + " to " + ident2;
                int acNum = Integer.parseInt(amountToTrans);
                String acID = null;
                if (!p1.getActionCards().containsValue(acNum)) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that AC, no AC sent");
                    return;
                }
                for (Map.Entry<String, Integer> ac : p1.getActionCards().entrySet()) {
                    if (ac.getValue().equals(acNum)) {
                        acID = ac.getKey();
                    }
                }
                p1.removeActionCard(acNum);
                p2.setActionCard(acID);
                ButtonHelper.checkACLimit(game, event, p2);
                ACInfo.sendActionCardInfo(game, p2);
                ACInfo.sendActionCardInfo(game, p1);
                if (!p1.hasAbility("arbiters") && !p2.hasAbility("arbiters")) {
                    if (game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(p1.getPrivateChannel(), message2);
                        MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), message2);
                    } else {
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message2);
                    }
                    return;
                }
            }
            case "PNs" -> {
                String id = null;
                int pnIndex;
                try {
                    pnIndex = Integer.parseInt(amountToTrans);
                } catch (NumberFormatException e) {
                    MessageHelper.sendMessageToChannel(p1.getCorrectChannel(), "# " + p1.getRepresentation() + " heads up, a PN failed to send. This is likely due to you not having the PN to send. Maybe you already gave it to someone else and forgot?");
                    return;
                }
                for (Map.Entry<String, Integer> pn : p1.getPromissoryNotes().entrySet()) {
                    if (pn.getValue().equals(pnIndex)) {
                        id = pn.getKey();
                    }
                }
                if (id == null) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that PN, no PN sent");
                    return;
                }
                p1.removePromissoryNote(id);
                p2.setPromissoryNote(id);
                if (id.contains("dspnveld")) {
                    ButtonHelper.resolvePNPlay(id, p2, game, event);
                }
                boolean sendSftT = false;
                boolean sendAlliance = false;
                String promissoryNoteOwner = Mapper.getPromissoryNote(id).getOwner();
                if ((id.endsWith("_sftt") || id.endsWith("_an")) && !promissoryNoteOwner.equals(p2.getFaction())
                    && !promissoryNoteOwner.equals(p2.getColor())
                    && !p2.isPlayerMemberOfAlliance(game.getPlayerFromColorOrFaction(promissoryNoteOwner))) {
                    p2.setPromissoryNotesInPlayArea(id);
                    if (id.endsWith("_sftt")) {
                        sendSftT = true;
                    } else {
                        sendAlliance = true;
                        if (game.getPNOwner(id).hasLeaderUnlocked("bentorcommander")) {
                            p2.setCommoditiesTotal(p2.getCommoditiesTotal() + 1);
                        }
                    }
                }
                PNInfo.sendPromissoryNoteInfo(game, p1, false);
                CardsInfo.sendVariousAdditionalButtons(game, p1);
                PNInfo.sendPromissoryNoteInfo(game, p2, false);
                CardsInfo.sendVariousAdditionalButtons(game, p2);
                String text = sendSftT ? "**Support for the Throne** " : (sendAlliance ? "**Alliance** " : "");
                message2 = p1.getRepresentation() + " sent " + Emojis.PN + text + "PN to " + ident2;
                Helper.checkEndGame(game, p2);
            }
            case "Frags" -> {

                String fragType = amountToTrans.substring(0, 3).toUpperCase();
                int fragNum = Integer.parseInt(amountToTrans.charAt(3) + "");
                String trait = switch (fragType) {
                    case "CRF" -> "cultural";
                    case "HRF" -> "hazardous";
                    case "IRF" -> "industrial";
                    case "URF" -> "frontier";
                    default -> "";
                };
                new SendFragments().sendFrags(event, p1, p2, trait, fragNum, game);
                message2 = "";
            }
        }
        Button button = Button.secondary(finChecker + "transactWith_" + p2.getColor(),
            "Send something else to player?");
        Button done = Button.secondary("finishTransaction_" + p2.getColor(), "Done With This Transaction");

        goAgainButtons.add(button);
        goAgainButtons.add(Button.success("demandSomething_" + p2.getColor(), "Expect something in return"));
        goAgainButtons.add(done);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(p1.getPrivateChannel(), message2);
            if (oldWay) {
                MessageHelper.sendMessageToChannelWithButtons(p1.getPrivateChannel(),
                    ident + " Use Buttons To Complete Transaction", goAgainButtons);
            }
            MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), message2);
        } else {
            TextChannel channel = game.getMainGameChannel();
            if (game.getName().equalsIgnoreCase("pbd1000")) {
                channel = game.getTableTalkChannel();
            }
            MessageHelper.sendMessageToChannel(channel, message2);
            if (oldWay) {
                MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(),
                    ident + " Use Buttons To Complete Transaction", goAgainButtons);
            }
        }
    }
}
