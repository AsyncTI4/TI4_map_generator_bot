package ti4.helpers;

import java.awt.image.BufferedImage;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.TransactionGenerator;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.model.PromissoryNoteModel;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.SourceEmojis;
import ti4.service.image.FileUploadService;
import ti4.service.info.CardsInfoService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.relic.SendRelicService;
import ti4.service.transaction.SendPromissoryService;
import ti4.settings.users.UserSettingsManager;

public class TransactionHelper {

    private static void acceptTransactionOffer(Player p1, Player p2, Game game, ButtonInteractionEvent event) {
        List<String> transactionItems = p1.getTransactionItemsWithPlayer(p2);
        List<Player> players = new ArrayList<>();
        players.add(p1);
        players.add(p2);
        boolean debtOnly = true;
        MessageChannel channel = p1.getCorrectChannel();
        if ("pbd1000".equalsIgnoreCase(game.getName())) {
            channel = game.getTableTalkChannel();
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    p1.getRepresentation(false, false) + " and " + p2.getRepresentation(false, false)
                            + " have transacted.");
        }

        String publicSummary = "A transaction has been ratified:\n" + buildTransactionOffer(p1, p2, game, true);
        String privateSummary =
                "The following transaction has been accepted:\n" + buildTransactionOffer(p1, p2, game, false);
        MessageHelper.sendMessageToChannel(channel, publicSummary);
        boolean secretRefresh = false;
        for (Player sender : players) {
            Player receiver = p2;
            if (sender == p2) {
                receiver = p1;
            }
            for (String item : transactionItems) {
                if (item.contains("sending" + sender.getFaction())
                        && item.contains("receiving" + receiver.getFaction())) {
                    String thingToTransact = item.split("_")[2];
                    String furtherDetail = item.replace(
                            item.split("_")[0] + "_" + item.split("_")[1] + "_" + item.split("_")[2] + "_", "");
                    int amountToTransact = 1;
                    boolean isGeneralized =
                            List.of("PNs", "ACs", "SOs").contains(thingToTransact) && furtherDetail.contains("generic");
                    if (isGeneralized) {
                        amountToTransact = Integer.parseInt("" + furtherDetail.charAt(furtherDetail.length() - 1));
                        furtherDetail = furtherDetail.substring(0, furtherDetail.length() - 1);
                    }
                    String spoofedButtonID =
                            "send_" + thingToTransact + "_" + receiver.getFaction() + "_" + furtherDetail;
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
                                default -> resolveSpecificTransButtonPress(game, sender, spoofedButtonID, event, false);
                            }
                        }
                        case "PNs" -> {
                            switch (furtherDetail) {
                                case "generic" -> {
                                    List<Button> stuffToTransButtons =
                                            ButtonHelper.getForcedPNSendButtons(game, receiver, sender);
                                    String message = sender.getRepresentationUnfogged()
                                            + ", please choose the promissory note you wish to send.";
                                    MessageHelper.sendMessageToChannelWithButtons(
                                            sender.getCardsInfoThread(), message, stuffToTransButtons);
                                }
                                default -> resolveSpecificTransButtonPress(game, sender, spoofedButtonID, event, false);
                            }
                        }
                        case "SOs" -> {
                            switch (furtherDetail) {
                                case "generic" -> {
                                    for (int x = 0; x < amountToTransact; x++) {
                                        String buttonID = "transact_SOs_" + receiver.getFaction();
                                        resolveSpecificTransButtonsOld(game, sender, buttonID, event);
                                    }
                                }
                                default -> {
                                    secretRefresh = true;
                                    resolveSpecificTransButtonPress(game, sender, spoofedButtonID, event, false);
                                }
                            }
                        }
                        case "Planets" ->
                            ButtonHelperFactionSpecific.resolveHacanMechTradeStepOne(
                                    sender, game, event, "send_" + furtherDetail + "_" + receiver.getFaction());
                        case "AlliancePlanets" -> {
                            String exhausted = "exhausted";
                            if (!furtherDetail.contains(exhausted)) {
                                exhausted = "refreshed";
                            }
                            furtherDetail = furtherDetail.replace(exhausted, "");

                            ButtonHelper.resolveAllianceMemberPlanetTrade(
                                    sender,
                                    game,
                                    event,
                                    "send_" + furtherDetail + "_" + receiver.getFaction() + "_" + exhausted);
                        }
                        case "Technology" -> {
                            receiver.addTech(furtherDetail);

                            ButtonHelperCommanders.resolveNekroCommanderCheck(receiver, furtherDetail, game);
                            CommanderUnlockCheckService.checkPlayer(receiver, "nekro");
                        }
                        case "dmz" ->
                            ButtonHelper.resolveDMZTrade(
                                    sender, game, event, "send_" + furtherDetail + "_" + receiver.getFaction());
                        default -> resolveSpecificTransButtonPress(game, sender, spoofedButtonID, event, false);
                    }
                }
            }
        }
        if (secretRefresh) {
            SecretObjectiveInfoService.sendSecretObjectiveInfo(game, p1, event);
            SecretObjectiveInfoService.sendSecretObjectiveInfo(game, p2, event);
        }

        // Send Summary to Player's CardsInfo threads
        MessageHelper.sendMessageToChannel(
                p1.getCardsInfoThread(), p1.getRepresentationUnfogged() + " " + privateSummary);
        MessageHelper.sendMessageToChannel(
                p2.getCardsInfoThread(), p2.getRepresentationUnfogged() + " " + privateSummary);

        p1.clearTransactionItemsWithPlayer(p2);
        if (!debtOnly) {
            if ((p1.hasAbility("pillage")
                            && !game.getStoredValue("willPillageOwnTransactions" + p1.getFaction())
                                    .isEmpty())
                    || (p2.hasAbility("pillage")
                            && !game.getStoredValue("willPillageOwnTransactions" + p2.getFaction())
                                    .isEmpty())) {

            } else {
                ButtonHelperActionCards.lieInWaitCheck(p1, p2, game);
                ButtonHelperAbilities.pillageCheck(p1, game);
                ButtonHelperAbilities.pillageCheck(p2, game);
            }
            CommanderUnlockCheckService.checkPlayer(p1, "hacan");
            CommanderUnlockCheckService.checkPlayer(p2, "hacan");
        }
    }

    private static String buildTransactionOffer(Player p1, Player p2, Game game, boolean hidePrivateCardText) {
        List<String> transactionItems = p1.getTransactionItemsWithPlayer(p2);
        StringBuilder trans = new StringBuilder();
        List<Player> players = new ArrayList<>();
        players.add(p1);
        players.add(p2);
        for (Player player : players) {
            if (!trans.isEmpty()) {
                trans.append("\n");
            }
            trans.append("> ")
                    .append(player.getRepresentation(false, false, true))
                    .append(" gives:\n");
            boolean sendingNothing = true;
            for (String item : transactionItems) {
                if (!item.contains("sending" + player.getFaction())) {
                    continue;
                }
                trans.append("> - ");
                sendingNothing = false;
                String thingToTransact = item.split("_")[2];
                String furtherDetail = item.replace(
                        item.split("_")[0] + "_" + item.split("_")[1] + "_" + item.split("_")[2] + "_", "");
                int amountToTransact = 1;
                boolean isGeneralized =
                        List.of("PNs", "ACs", "SOs").contains(thingToTransact) && furtherDetail.contains("generic");
                if ("frags".equalsIgnoreCase(thingToTransact) || isGeneralized) {
                    amountToTransact = Integer.parseInt("" + furtherDetail.charAt(furtherDetail.length() - 1));
                    furtherDetail = furtherDetail.substring(0, furtherDetail.length() - 1);
                }
                switch (thingToTransact) {
                    case "TGs" -> {
                        amountToTransact = Integer.parseInt(furtherDetail);
                        if (amountToTransact > 4) {
                            trans.append(amountToTransact).append(MiscEmojis.tg);
                        } else {
                            trans.append(MiscEmojis.tg(amountToTransact));
                        }
                    }
                    case "Comms" -> {
                        amountToTransact = Integer.parseInt(furtherDetail);
                        if (amountToTransact > 4) {
                            trans.append(amountToTransact).append(MiscEmojis.comm);
                        } else {
                            trans.append(MiscEmojis.comm(amountToTransact));
                        }
                    }
                    case "SendDebt" -> {
                        amountToTransact = Integer.parseInt(furtherDetail);
                        trans.append("Send ")
                                .append(amountToTransact)
                                .append(" debt token" + (amountToTransact == 1 ? "" : "s")
                                        + ", for their \"Debt Account\" pool");
                    }
                    case "ClearDebt" -> {
                        amountToTransact = Integer.parseInt(furtherDetail);
                        trans.append("Clear ")
                                .append(amountToTransact)
                                .append(" debt token" + (amountToTransact == 1 ? "" : "s")
                                        + ", from their \"Debt Account\" pool");
                    }
                    case "shipOrders" ->
                        trans.append(Mapper.getRelic(furtherDetail).getName()).append(FactionEmojis.axis);
                    case "starCharts" ->
                        trans.append(Mapper.getRelic(furtherDetail).getName()).append(SourceEmojis.DiscordantStars);
                    case "ACs" -> {
                        switch (furtherDetail) {
                            case "generic" ->
                                trans.append(amountToTransact)
                                        .append(" ")
                                        .append(CardEmojis.getACEmoji(game))
                                        .append(" to be specified by player");
                            default -> {
                                int acNum = Integer.parseInt(furtherDetail);
                                String acID = null;
                                if (!player.getActionCards().containsValue(acNum)) {
                                    continue;
                                }
                                for (Map.Entry<String, Integer> ac :
                                        player.getActionCards().entrySet()) {
                                    if (ac.getValue().equals(acNum)) {
                                        acID = ac.getKey();
                                    }
                                }
                                trans.append(CardEmojis.getACEmoji(game));
                                if (!hidePrivateCardText) {
                                    trans.append(" _")
                                            .append(Mapper.getActionCard(acID).getName())
                                            .append("_");
                                }
                            }
                        }
                    }
                    case "PNs" -> {
                        switch (furtherDetail) {
                            case "generic" -> {
                                if (!hidePrivateCardText) {
                                    trans.append(amountToTransact)
                                            .append(" ")
                                            .append(CardEmojis.PN)
                                            .append(" to be specified by player");
                                } else {
                                    trans.append(CardEmojis.PN);
                                }
                            }
                            default -> {
                                String id = null;
                                int pnIndex;
                                try {
                                    pnIndex = Integer.parseInt(furtherDetail);
                                    for (Map.Entry<String, Integer> pn :
                                            player.getPromissoryNotes().entrySet()) {
                                        if (pn.getValue().equals(pnIndex)) {
                                            id = pn.getKey();
                                        }
                                    }
                                } catch (NumberFormatException e) {
                                    id = furtherDetail.replace("fin9", "_");
                                }
                                trans.append(CardEmojis.PN);
                                if (!hidePrivateCardText) {
                                    if (Mapper.getPromissoryNote(id) != null) {
                                        trans.append(" ")
                                                .append(StringUtils.capitalize(Mapper.getPromissoryNote(id)
                                                        .getColor()
                                                        .orElse("")))
                                                .append(" _")
                                                .append(Mapper.getPromissoryNote(id)
                                                        .getName())
                                                .append("_");
                                    } else {
                                        trans.append(" ")
                                                .append("null pn info for ")
                                                .append(id);
                                    }
                                }
                            }
                        }
                    }
                    case "SOs" -> {
                        switch (furtherDetail) {
                            case "generic" ->
                                trans.append(amountToTransact)
                                        .append(" ")
                                        .append(CardEmojis.SecretObjective)
                                        .append(" to be specified by player");
                            default -> {
                                String soID = furtherDetail;
                                if (!player.getSecretsUnscored().containsKey(soID)) continue;
                                trans.append(CardEmojis.SecretObjective);
                                if (!hidePrivateCardText)
                                    trans.append(" _")
                                            .append(Mapper.getSecretObjective(soID)
                                                    .getName())
                                            .append("_");
                            }
                        }
                    }
                    case "Frags" ->
                        trans.append(ExploreEmojis.getFragEmoji(furtherDetail)
                                .toString()
                                .repeat(amountToTransact));
                    case "Technology" ->
                        trans.append(Mapper.getTech(furtherDetail).getRepresentation(false));
                    case "Planets", "AlliancePlanets", "dmz" ->
                        trans.append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(furtherDetail, game));
                    case "Relics" ->
                        trans.append(Mapper.getRelic(furtherDetail).getName()).append(ExploreEmojis.Relic);
                    case "action" ->
                        trans.append("An in-game ").append(furtherDetail).append(" action");
                    case "details" -> {
                        if (hidePrivateCardText && game.isLimitedWhispersMode()) {
                            trans.append("[REDACTED]");
                        } else {
                            trans.append(furtherDetail.replace("fin777", " "));
                        }
                    }
                    default -> trans.append(" some odd thing: `").append(item).append("`");
                }
                trans.append("\n");
            }
            if (sendingNothing) {
                String nothing = game.getStoredValue(player.getFaction() + "NothingMessage");
                if (nothing.isEmpty()) {
                    nothing = getNothingMessage();
                    game.setStoredValue(player.getFaction() + "NothingMessage", nothing);
                }
                trans.append("> - ").append(nothing).append("\n");
            }
        }

        return trans.toString();
    }

    private static final List<String> nothingMessages = Arrays.asList(
            "Nothing",
            "Nothing",
            "Nothing",
            "Nothing",
            "Nothing",
            "Nothing But Respect And Good Will",
            "Some Pocket Lint",
            "Sunshine And Rainbows",
            "A Sense Of Pride And Accomplishment",
            "A Crisp High Five",
            "A Well Written Thank-You Note",
            "Heartfelt Thanks",
            "The Best Vibes",
            "A Blessing",
            "Good Karma",
            "A Mewling Kitten",
            "A Lost Virtual Puppy",
            "A Fortune Cookie",
            "A Firm Handshake",
            "A Friendly Wave",
            "Well Wishes",
            "A Home-Cooked Meal",
            "$1000 In Monopoly Money",
            "Forgiveness For Past Mistakes",
            "A Lucky Rock",
            "A Warm Cup Of Tea",
            "A Poorly Drawn But Deeply Meaningful Picture",
            "An Unexpected Hug",
            "A Magic Trick",
            "A Pair Of Comfy Socks",
            "A Whiff Of Fresh Cookies",
            "A Charming Smile",
            "A Promise To Call Later",
            "A Supportive Cheer",
            "A Playful Joke",
            "A Chance To See A Beautiful Sunset",
            "A Treasure Map",
            "A Song",
            "A Book Recommendation",
            "A Cozy Blanket",
            "A Cheery Greeting",
            "A Bucket Of Joy",
            "A Gentle Reminder",
            "A Heartwarming Story",
            "A Whisper Of Kindness",
            "An Expired Gift Certificate",
            "A Free Trial CD For AOL",
            "Compost For Your Garden",
            "A Tupperware Party Invitation",
            "A Picture Of A Sandwich",
            "Thoughtful Advice About Your Current Situation",
            "Zip; Zilch; Nada",
            String.format(
                    "Approximately %,d Unique Snow Globes",
                    5 * ThreadLocalRandom.current().nextInt(200, 2000)
                            + ThreadLocalRandom.current().nextInt(1, 5)),
            "Forgiveness For Future Mistakes (Terms And Conditions Apply)",
            "A Token Labelled \"Traid Gud\"",
            "A Hill Of Beans",
            "This Small Portrait Of Benjamin Franklin, Done In Oil Paints",
            "Favourable Timing On Bureaucracy",
            "A Comfortable Sinecure, When I'm Galactic Emperor",
            "This Box Of One Dozen Starving, Crazed Weasels",
            "A jpeg Depicting A Cartoon Monkey",
            "A Somewhat Rare Beanie Baby",
            "Some Good News About Our Lord And Saviour, Harrugh Gefhara",
            "No Spoilers For That TV Show You're Watching",
            "Payment In Exposure",
            "A VHS Recording Of An Episode Of _Bill Nye The Science Guy_",
            "A Short But Comprehensive Lecture On Medieval Siege Weaponry",
            "An _E.T. the Extra-Terrestrial_ Cartridge For The Atari 2600",
            "A Nice Solid Thumbs Up",
            "A Handful Of Dog Treats",
            "One (1) Peppercorn",
            "Poutine",
            "The Deputy Speakership",
            "Half A Slice Of Pizza, With Or Without Pineapple",
            "The Wi-Fi Password",
            "A Second-Hand Toothbrush",
            "A Lamp That Might Contain A Genie, But Probably Doesn't",
            "An Acoustic Rendition Of _Wonderwall_",
            "As Many Spiders As You Desire",
            "Fruit Salad (yummy, yummy)",
            "One Chocolate Chip Muffin Amongst Eleven Raisin Muffins",
            "Invoking The A̴̰̽̑ͅn̶͙͝ĉ̸̤̜̽i̶̯̯͋ě̶͓̜͑n̶̤̩̉t̸̯̎͊͜ ̷́ͅP̶̘̀a̸̧̔̅c̶̣̋̔t̷̺̪͛͋",
            "Some Perfunctory Laughter At Your Next Attempt At A Joke",
            "The Front Half Of Our Pantomime Horse",
            "~~False~~ Reassurances",
            "A Big Mouth Billy Bass",
            "More Cowbell",
            "Some Subpar Macaroni Art",
            "A Chocolate Teapot",
            "Some Week-Old Sushi",
            "My Second Finest Bottle Of Wine Drink™",
            "A Riddle, Wrapped In A Mystery, Inside An Enigma, Coated In Chocolate",
            "A Brand-New Luxury Car, Missing Only Fuel, Tires And Car",
            "Either \"Peace\" Or \"Peas\"; The Ambassador Failed To Elaborate",
            "A Year's Supply Of Brussels Sprouts",
            "A Nintendo Power Glove; ***Now You're Playing With Power***",
            "A Wooden Spoon",
            "An Ingot Of Pyrite",
            "A White Elephant",
            "Ennui",
            "A Smurf TV Tray",
            "A Creepy Doll",
            "A Ziploc Bag Of Ranch Dressing",
            "Nothing. And Furthermore, Carthage Must Be Destroyed!",
            "All The Goulash",
            "Waldo's Location",
            "A Billet Of Ea-nāṣir's Finest Copper",
            "All The Silver In Fort Knox",
            "A Controlling Share Of The Bereg Jet Ski Company",
            "A Handful Of Specially Marked Cereal Boxtops",
            "An Aperture Science Thing We Don't Know What It Does",
            "Nothing, Because I'm A Cheapskate",
            "A Brick, Delivery Speed TBD",
            "An Inanimate Carbon Rod",
            "A Set Of Left-Handed Sarween Tools",
            "A Bridge That's For Sale",
            "In return for this small, helpful deed // A limerick is what I shall cede // It won't cost me a dime // If I trade you this rhyme // For that thing that I want, yes indeed!",
            "The Chameleon's Dish",
            "The Sound Of One Hand Clapping",
            "An Unpaired Sock",
            "\"101 Ways To Make Toast\"",
            "A Chess Set With 31 Missing Pieces",
            "Your Horoscope Reading",
            "Just Deserts",
            "Surprise And Delight",
            "`//Could somebody get ChatGPT to generate a few more messages - Dev`",
            "Some Free Candy, From My Windowless Van",
            "A Phial Of Dihydrogen Monoxide",
            "A Gizmo, A Doohickey, Or Perhaps Even A Whatchamacallit",
            "Nothing (Don't Spend It All At Once)",
            "Industrial Quantities Of Glitter",
            "Potent Potables",
            "A Succulent Chinese Meal?",
            "The Cheap Plastic Imitation Of The Amulet Of Yendor",
            "A Millstone",
            "Behind Door #" + ThreadLocalRandom.current().nextInt(1, 4) + ": A Goat!",
            "A Runcible Spoon",
            "_Nullam Rem Natam_",
            "A Jubba Cloak",
            "The Pretence That Your Secrets Are Unscorable",
            "Double Nothing",
            "A First-Born Child",
            "The Opportunity To Be Your Own Boss",
            "Artisanal, Hand-Crafted Nothing",
            "Reticulating Splines",
            "ADDITIONAL PYLONS",
            "A State-of-the-Art Turbo Encabulator",
            "Help! I'm Trapped In A Transaction Factory!",
            "Emotional Support For The Throne",
            "My Frayed Agreement",
            "A Rock-Shaped Rock",
            "Not Attacking You Through The Samekh Wormhole",
            "A Thneed",
            "Smashing That Like Button",
            "A Sickle Of Leather",
            "A Fish's Bicycle",
            "One Red Paperclip",
            "ACME Bird Seed",
            "Your Choice Of Mountain Dew Or Crab Juice",
            // generates a Sidereal Confluence trade offer in the form of
            // "Two Food/Life Support Cubes And One Biotechnology Cube"
            String.format(String.format(
                    String.format(
                            "%s And %s",
                            switch (Math.min(
                                    ThreadLocalRandom.current().nextInt(5),
                                    ThreadLocalRandom.current().nextInt(5))) {
                                case 0 -> "One %s Cube";
                                case 1 -> "Two %s Cubes";
                                case 2 -> "Three %s Cubes";
                                case 3 -> "Four %s Cubes";
                                default -> "Five %s Cubes";
                            },
                            switch (Math.min(
                                    ThreadLocalRandom.current().nextInt(5),
                                    ThreadLocalRandom.current().nextInt(5))) {
                                case 0 -> "One %s Cube";
                                case 1 -> "Two %s Cubes";
                                case 2 -> "Three %s Cubes";
                                case 3 -> "Four %s Cubes";
                                default -> "Five %s Cubes";
                            }),
                    switch (ThreadLocalRandom.current().nextInt(3)) {
                        case 0 -> "Food/Life Support";
                        case 1 -> "Culture";
                        default -> "Industry";
                    },
                    switch (ThreadLocalRandom.current().nextInt(3)) {
                        case 0 -> "Information";
                        case 1 -> "Biotechnology";
                        default -> "Power/Electrical";
                    })),
            "The Golden Skull of Rauhl",
            "This Defective Postage Stamp, Which Has The Aeroplane Printed Upside-Down",
            "A Flanian Pobble Bead",
            "A Red Herring",
            "∅", // empty set
            switch (ThreadLocalRandom.current().nextInt(0, 30)) {
                case 0 -> "Asgjë (As They Would Say In Albania)";
                case 1 -> "Нищо (As They Would Say In Bulgaria)";
                case 2 -> "Ništa (As They Would Say In Croatia)";
                case 3 -> "Intet (As They Would Say In Denmark)";
                case 4 -> "Niets (As They Would Say In The Netherlands)";
                case 5 -> "Mitte Midagi (As They Would Say In Estonia)";
                case 6 -> "Rien (As They Would Say In France)";
                case 7 -> "არაფერი (As They Would Say In Georgia)";
                case 8 -> "Nichts (As They Would Say In Germany)";
                case 9 -> "Τίποτα (As They Would Say In Greece)";
                case 10 -> "ʻAʻOhe Mea (As They Would Say In Hawaii)";
                case 11 -> "कुछ नहीं (As They Would Say In India)";
                case 12 -> "Ekkert (As They Would Say In Iceland)";
                case 13 -> "Niente (As They Would Say In Italy)";
                case 14 -> "아무것도 아님 (As They Would Say In Korea)";
                case 15 -> "Nekas (As They Would Say In Latvia)";
                case 16 -> "Nieko (As They Would Say In Lithuania)";
                case 17 -> "Näischt (As They Would Say In Luxembourg)";
                case 18 -> "Ништо (As They Would Say In Macedonia)";
                case 19 -> "Kaore He Mea (As They Would Say In New Zealand)";
                case 20 -> "Юу Ч Биш (As They Would Say In Mongolia)";
                case 21 -> "केही छैन (As They Would Say In Nepal)";
                case 22 -> "Ikke Noe (As They Would Say In Norway)";
                case 23 -> "Chan Eil Dad (As They Would Say In Scotland)";
                case 24 -> "Ништа (As They Would Say In Serbia)";
                case 25 -> "Hakuna Kitu (As They Would Say In Tanzania)";
                case 26 -> "Ingenting (As They Would Say In Sweden)";
                case 27 -> "Нічого (As They Would Say In Ukraine)";
                case 28 -> "Không Có Gì (As They Would Say In Vietnam)";
                default -> "Dim Byd (As They Would Say In Wales)";
            },
            "An Air Freshener To Hang On Your Dreadnought's Rear View Mirror",
            "A Participation Trophy",
            "Second-Hand Nothing",
            "A Promissory Vibe",
            "[Nothing](<https://youtu.be/dQw4w9WgXcQ>)",
            "||Surprise Nothing||",
            "A Witty Pop Culture Reference",
            "Hwat's This‽ A Transaction Proposal Woefully Underpopulated By Bees‽ My Briefcase Full Of Bees Ought To Put A Stop To That!");

    public static String getNothingMessage() {
        if (RandomHelper.isOneInX(1000000)) {
            return "The joy of sharing a one in a million empty transaction offer message";
        }

        int result = ThreadLocalRandom.current().nextInt(0, nothingMessages.size());
        try {
            return nothingMessages.get(result);
        } catch (Exception e) {
            return "Nothing";
        }
    }

    public static void transaction(Player player, Game game) {
        transaction(null, player, game, "");
    }

    @ButtonHandler(value = "transact_BMD", save = true) // deprecated
    @ButtonHandler(value = "transaction_BMD", save = true)
    @ButtonHandler(value = "transaction", save = false)
    private static void transaction(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (buttonID.endsWith("_BMD")) {
            game.setStoredValue("blackmarketdealing", player.getFaction());
            ButtonHelper.deleteMessage(event);
        }

        List<Button> buttons = getPlayersToTransact(game, player);
        String message = player.getRepresentation() + ", please choose which player you wish to transact with.";
        if (game.isHiddenAgendaMode() && game.getPhaseOfGame().toLowerCase().contains("agenda")) {
            message = player.getRepresentation()
                    + ", this game is in Hidden Agenda mode, which does not allow transactions in the Agenda Phase.";
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
    }

    @ButtonHandler(value = "newTransact_", save = false)
    public static void resolveSpecificTransButtonsNew(
            Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        List<Button> stuffToTransButtons = new ArrayList<>();
        buttonID = buttonID.replace("newTransact_", "");
        String thingToTrans = buttonID.substring(0, buttonID.indexOf('_'));
        String senderFaction = buttonID.split("_")[1];
        String receiverFaction = buttonID.split("_")[2];
        Player p1 = game.getPlayerFromColorOrFaction(senderFaction);
        Player p2 = game.getPlayerFromColorOrFaction(receiverFaction);
        if (p1 == null || p2 == null) return;

        boolean requesting = (p1 != player);
        Player opposing = p2;
        if (player == p2) {
            opposing = p1;
        }
        String message =
                "Current Transaction Offer is:\n" + buildTransactionOffer(player, opposing, game, false) + "\n";
        String requestOrOffer = "offer";
        if (requesting) {
            requestOrOffer = "request";
        }
        switch (thingToTrans) {
            case "TGs" -> {
                message += " Please choose the number of trade goods you wish to " + requestOrOffer + ".";
                for (int x = 1; x < p1.getTg() + 1 && x < 21; x++) {
                    Button transact = Buttons.green(
                            "offerToTransact_TGs_" + p1.getFaction() + "_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
            }
            case "Comms" -> {
                message += " Please choose the number of commodities you wish to " + requestOrOffer + ".";
                for (int x = 1; x < p1.getCommodities() + 1; x++) {
                    Button transact = Buttons.green(
                            "offerToTransact_Comms_" + p1.getFaction() + "_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
            }
            case "ClearDebt" -> {
                message += " Please choose the amount of debt you wish to " + requestOrOffer + " cleared.";
                for (int x = 1; x < p1.getDebtTokenCount(p2.getColor()) + 1; x++) {
                    Button transact = Buttons.green(
                            "offerToTransact_ClearDebt_" + p1.getFaction() + "_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
            }
            case "SendDebt" -> {
                message += " Please choose the amount of debt you wish to " + requestOrOffer + ".";
                for (int x = 1; x < 6; x++) {
                    Button transact = Buttons.green(
                            "offerToTransact_SendDebt_" + p1.getFaction() + "_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
            }
            case "shipOrders" -> {
                message += " Please choose the _Axis Order_ you wish to " + requestOrOffer + ".";
                for (String shipOrder : ButtonHelper.getPlayersShipOrders(p1)) {
                    Button transact = Buttons.green(
                            "offerToTransact_shipOrders_" + p1.getFaction() + "_" + p2.getFaction() + "_" + shipOrder,
                            Mapper.getRelic(shipOrder).getName());
                    stuffToTransButtons.add(transact);
                }
            }
            case "starCharts" -> {
                message += " Please choose the _Star Chart_ you wish to " + requestOrOffer + ".";
                for (String starChart : ButtonHelper.getPlayersStarCharts(p1)) {
                    Button transact = Buttons.green(
                            "offerToTransact_starCharts_" + p1.getFaction() + "_" + p2.getFaction() + "_" + starChart,
                            Mapper.getRelic(starChart).getName());
                    stuffToTransButtons.add(transact);
                }
            }
            case "Planets" -> {
                message += " Please choose the planet you wish to " + requestOrOffer + ".";
                for (String planet : p1.getPlanetsAllianceMode()) {
                    if (planet.contains("custodia")
                            || planet.contains("ghoti")
                            || ButtonHelper.getUnitHolderFromPlanetName(planet, game) == null) {
                        continue;
                    }
                    UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
                    if (unitHolder != null && unitHolder.getUnitCount(UnitType.Mech, p1.getColor()) > 0) {
                        stuffToTransButtons.add(Buttons.gray(
                                "offerToTransact_Planets_" + p1.getFaction() + "_" + p2.getFaction() + "_" + planet,
                                Helper.getPlanetRepresentation(planet, game)));
                    }
                }
            }
            case "Technology" -> {
                message += " Please choose the technology you wish to " + requestOrOffer + ".";
                for (String tech : p1.getTechs()) {
                    if (resolveAgeOfCommerceTechCheck(p1, p2, tech, game)) {
                        stuffToTransButtons.add(Buttons.gray(
                                "offerToTransact_Technology_" + p1.getFaction() + "_" + p2.getFaction() + "_" + tech,
                                Mapper.getTech(tech).getName()));
                    }
                }
            }
            case "AlliancePlanets" -> {
                message += " Please choose the planet you wish to " + requestOrOffer + ".";
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
                        stuffToTransButtons.add(Buttons.gray(
                                "offerToTransact_AlliancePlanets_" + p1.getFaction() + "_" + p2.getFaction() + "_"
                                        + planet + refreshed,
                                Helper.getPlanetRepresentation(planet, game)));
                    }
                }
            }
            case "ACs" -> {
                if (requesting) {
                    message += player.getRepresentation(false, false)
                            + " Please choose the number of action cards you wish to request."
                            + " Since action cards are private info, you will have to discuss with other other player to explain which action cards you wish to transact;"
                            + " these buttons will just make sure that the player is offered buttons to send them.";
                    int limit = Math.min(7, p1.getAcCount());
                    for (int x = 1; x < limit + 1; x++) {
                        Button transact = Buttons.green(
                                "offerToTransact_ACs_" + p1.getFaction() + "_" + p2.getFaction() + "_generic" + x,
                                x + " ACs");
                        stuffToTransButtons.add(transact);
                    }
                } else {
                    message += player.getRepresentation(false, false)
                            + " Please choose the __green__ button that indicates the action card you wish to "
                            + requestOrOffer + ".";
                    for (String acShortHand : p1.getActionCards().keySet()) {
                        Button transact = Buttons.green(
                                "offerToTransact_ACs_" + p1.getFaction() + "_" + p2.getFaction() + "_"
                                        + p1.getActionCards().get(acShortHand),
                                Mapper.getActionCard(acShortHand).getName());
                        stuffToTransButtons.add(transact);
                    }
                }
            }
            case "PNs" -> {
                if (requesting) {
                    message += player.getRepresentation(false, false)
                            + " Please choose the promissory note you wish to request."
                            + " Since promissory notes are private info, all of the player's starting promissory notes (which are not already in someone's play areas) are available,"
                            + " though the player may not currently hold all of these."
                            + " Please choose the \"TBD Promissory Note\" button if you wish to transact someone else's promissory note, and it will give the player the option to send it;"
                            + " you should discuss this with the player you're transacting with.";
                    boolean hubris = player.hasAbility("hubris");
                    if (hubris) {
                        message += "\nSince you "
                                + (game.isFrankenGame() ? "have the **Hubris** ability" : "are playing Mahact")
                                + ", you cannot request the _Alliance_ promissory note.";
                    }
                    for (String pnShortHand : p1.getPromissoryNotesOwned()) {
                        if (ButtonHelper.anyoneHaveInPlayArea(game, pnShortHand)) {
                            continue;
                        }
                        if (hubris && pnShortHand.endsWith("_an")) {
                            continue;
                        }
                        PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnShortHand);
                        Player owner = game.getPNOwner(pnShortHand);
                        if (p1.getPromissoryNotes().containsKey(pnShortHand)) {
                            stuffToTransButtons.add(Buttons.green(
                                            "offerToTransact_PNs_" + p1.getFaction() + "_" + p2.getFaction() + "_"
                                                    + p1.getPromissoryNotes().get(pnShortHand),
                                            promissoryNote.getName())
                                    .withEmoji(Emoji.fromFormatted(owner.getFactionEmoji())));
                        } else {
                            stuffToTransButtons.add(Buttons.green(
                                            "offerToTransact_PNs_" + p1.getFaction() + "_" + p2.getFaction() + "_"
                                                    + pnShortHand.replace("_", "fin9"),
                                            promissoryNote.getName())
                                    .withEmoji(Emoji.fromFormatted(owner.getFactionEmoji())));
                        }
                    }
                    Button transact = Button.primary(
                            "offerToTransact_PNs_" + p1.getFaction() + "_" + p2.getFaction() + "_" + "generic1",
                            "TBD Promissory Note");

                    stuffToTransButtons.add(transact);
                } else {
                    boolean hubris = p2.hasAbility("hubris");
                    if (hubris) {
                        message += "\nSince they "
                                + (game.isFrankenGame() ? "have the **Hubris** ability" : "are playing Mahact")
                                + ", you cannot send the _Alliance_ promissory note.";
                    }
                    message += p1.getRepresentation(true, false) + ", please choose the promissory note you wish to "
                            + requestOrOffer + ".";
                    for (String pnShortHand : p1.getPromissoryNotes().keySet()) {
                        if (p1.getPromissoryNotesInPlayArea().contains(pnShortHand)
                                || (hubris && pnShortHand.endsWith("_an"))) {
                            continue;
                        }
                        PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnShortHand);
                        Player owner = game.getPNOwner(pnShortHand);
                        if (owner != null) {
                            Button transact = Buttons.green(
                                            "offerToTransact_PNs_" + p1.getFaction() + "_" + p2.getFaction() + "_"
                                                    + p1.getPromissoryNotes().get(pnShortHand),
                                            promissoryNote.getName())
                                    .withEmoji(Emoji.fromFormatted(owner.getFactionEmoji()));
                            stuffToTransButtons.add(transact);
                        } else {
                            MessageHelper.sendMessageToChannel(
                                    player.getCorrectChannel(),
                                    player.getRepresentation()
                                            + ", you have a promissory note with a null owner. Its number ID is `"
                                            + p1.getPromissoryNotes().get(pnShortHand) + "` and its letter ID is "
                                            + pnShortHand + "`.");
                        }
                    }
                }
                message +=
                        "\n A reminder that, unlike other things, you may only send a player 1 promissory note in each transaction"
                                + " (and you may only perform one transaction with each other player on a turn).";
                if (game.isNoSwapMode()) {
                    message += "\n### A reminder that you cannot swap _Supports For The Thrones_ in this game.";
                }
            }
            case "SOs" -> {
                if (requesting) {
                    message += player.getRepresentation(false, false);
                    message += " Click the number of unscored secret objectives you wish to request.";
                    message +=
                            " Since unscored secret objectives are private info, you will have to discuss with other other player to explain which unscored secret objectives you wish to transact;";
                    message += " these buttons will just make sure that the player is offered buttons to send them.";

                    int limit = p2.getSecretsUnscored().size();
                    String prefix = "offerToTransact_SOs_" + p1.getFaction() + "_" + p2.getFaction() + "_generic";
                    for (int x = 1; x < limit + 1; x++) {
                        stuffToTransButtons.add(
                                Buttons.green(prefix + x, x + " Secret Objectives", CardEmojis.SecretObjective));
                    }
                } else {
                    message += player.getRepresentation(false, false);
                    message += " Click the __green__ button that indicates the unscored secret objective you wish to "
                            + requestOrOffer + ".";
                    String prefix = "offerToTransact_SOs_" + p1.getFaction() + "_" + p2.getFaction() + "_";
                    for (String soID : p1.getSecretsUnscored().keySet()) {
                        String name = Mapper.getSecretObjective(soID).getName();
                        stuffToTransButtons.add(Buttons.green(prefix + soID, name, CardEmojis.SecretObjective));
                    }
                }
            }
            case "Frags" -> {
                message += " Please choose the number of relic fragments you wish to " + requestOrOffer + ".";
                String prefix = "offerToTransact_Frags_" + p1.getFaction() + "_" + p2.getFaction();
                for (int x = 1; x <= p1.getCrf(); x++) {
                    stuffToTransButtons.add(
                            Buttons.blue(prefix + "_CRF" + x, "Cultural Fragments (x" + x + ")", ExploreEmojis.CFrag));
                }
                for (int x = 1; x <= p1.getIrf(); x++) {
                    stuffToTransButtons.add(Buttons.green(
                            prefix + "_IRF" + x, "Industrial Fragments (x" + x + ")", ExploreEmojis.IFrag));
                }
                for (int x = 1; x <= p1.getHrf(); x++) {
                    stuffToTransButtons.add(
                            Buttons.red(prefix + "_HRF" + x, "Hazardous Fragments (x" + x + ")", ExploreEmojis.HFrag));
                }
                for (int x = 1; x <= p1.getUrf(); x++) {
                    stuffToTransButtons.add(
                            Buttons.gray(prefix + "_URF" + x, "Unknown Fragments (x" + x + ")", ExploreEmojis.UFrag));
                }
            }
            case "Relics" -> {
                message += " Click the relics you wish to " + requestOrOffer + ".";
                String prefix = "offerToTransact_Relics_" + p1.getFaction() + "_" + p2.getFaction();

                boolean blackmarket =
                        List.of(p1.getFaction(), p2.getFaction()).contains(game.getStoredValue("blackmarketdealing"));
                for (String relic : (blackmarket ? p1.getActualRelics() : p1.getTradableRelics())) {
                    String name = Mapper.getRelic(relic).getName();
                    stuffToTransButtons.add(Buttons.gray(prefix + "_" + relic, name, ExploreEmojis.Relic));
                }
            }
            case "Details" -> {
                String other = p1.getFaction();
                if (player == p1) {
                    other = p2.getFaction();
                }
                event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
                String modalId = "finishDealDetails_" + other;
                String fieldID = "details";
                TextInput summary = TextInput.create(fieldID, TextInputStyle.PARAGRAPH)
                        .setPlaceholder("Edit your deals details here.")
                        .setValue("The deal is that I ")
                        .build();

                Modal modal = Modal.create(modalId, "Deal Details")
                        .addComponents(Label.of("Edit deal details", summary))
                        .build();

                event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
                return;
            }
            case "DetailsInvert" -> {
                String other = p2.getFaction();
                if (player == p2) {
                    other = p1.getFaction();
                }
                event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
                String modalId = "finishDealDetailsInvert_" + other;
                String fieldID = "details";
                TextInput summary = TextInput.create(fieldID, TextInputStyle.PARAGRAPH)
                        .setPlaceholder("Edit your deals details here.")
                        .setValue("The deal is that you ")
                        .build();
                Modal modal = Modal.create(modalId, "Deal Details")
                        .addComponents(Label.of("Edit deal details", summary))
                        .build();
                event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
                return;
            }
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, stuffToTransButtons);
    }

    // Left for future reference.
    private static Modal buildTransactionModel(Player p1, Player p2, Game game) {
        Modal.Builder modal = Modal.create("transactionModelFinish_" + p1.getFaction(), "Traction");
        List<Player> players = new ArrayList<>();
        players.add(p1);
        players.add(p2);

        for (Player player : players) {
            Player otherPlayer = p1;
            if (player == otherPlayer) {
                otherPlayer = p2;
            }
            if (player.getTg() > 0) {
                StringSelectMenu.Builder tgs = StringSelectMenu.create("TGs" + player.getFaction());
                for (int tg = 1; tg < player.getTg() + 1; tg++) {
                    tgs.addOption("" + tg, "" + tg);
                }
                modal.addComponents(
                        Label.of("Trade Goods From " + player.getFactionModel().getFactionName(), tgs.build()));
            }
            if (player.getCommodities() > 0) {
                StringSelectMenu.Builder comms = StringSelectMenu.create("Comms" + player.getFaction());
                for (int comm = 1; comm < player.getCommodities() + 1; comm++) {
                    comms.addOption("" + comm, "" + comm);
                }
                modal.addComponents(
                        Label.of("Commodities From " + player.getFactionModel().getFactionName(), comms.build()));
            }
        }

        return modal.build();
    }

    private static boolean resolveAgeOfCommerceTechCheck(Player owner, Player receiver, String tech, Game game) {
        if (!Mapper.getTech(AliasHandler.resolveTech(tech))
                .getFaction()
                .orElse("")
                .isEmpty()) {
            return false;
        }
        if (receiver.getTechs().contains(tech)) {
            return false;
        }
        return !receiver.getNotResearchedFactionTechs().contains(tech);
    }

    @ModalHandler("finishDealDetails_")
    public static void finishDealDetails(ModalInteractionEvent event, Game game, Player player, String modalID) {
        ModalMapping mapping = event.getValue("details");
        String thoughts = mapping.getAsString();
        Player opposing = game.getPlayerFromColorOrFaction(modalID.split("_")[1]);
        player.addTransactionItem("sending" + player.getFaction() + "_receiving" + modalID.split("_")[1] + "_details_"
                + thoughts.replace("_", "").replace(",", "").replace("\n", "").replace(" ", "fin777"));
        String message = "Current transaction offer is:\n"
                + buildTransactionOffer(player, opposing, game, false)
                + "### Click something that you wish to __offer to__ " + opposing.getRepresentation(false, false);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(), message, getStuffToTransButtonsNew(game, player, player, opposing));
    }

    @ModalHandler("finishTrackRecord_")
    public static void finishTrackRecord(ModalInteractionEvent event, Game game, Player player, String modalID) {
        ModalMapping mapping = event.getValue("record");
        String thoughts = mapping.getAsString();
        String userId = modalID.split("_")[1];
        var userSettings = UserSettingsManager.get(userId);
        userSettings.setTrackRecord(thoughts.replace("\n", ""));
        UserSettingsManager.save(userSettings);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Successfully edited the players track record.");
    }

    @ModalHandler("finishDealDetailsInvert_")
    public static void finishDealDetailsInvert(ModalInteractionEvent event, Game game, Player player, String modalID) {
        ModalMapping mapping = event.getValue("details");
        String thoughts = mapping.getAsString();
        Player opposing = game.getPlayerFromColorOrFaction(modalID.split("_")[1]);
        player.addTransactionItem("sending" + modalID.split("_")[1] + "_receiving" + player.getFaction() + "_details_"
                + thoughts.replace("_", "").replace(",", "").replace("\n", "").replace(" ", "fin777"));
        String message = "Current transaction offer is:\n"
                + buildTransactionOffer(player, opposing, game, false)
                + "### Click something that you wish to __request from__ " + opposing.getRepresentation(false, false);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(), message, getStuffToTransButtonsNew(game, player, opposing, player));
    }

    @ButtonHandler("offerToTransact_")
    public static void resolveOfferToTransact(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String item = buttonID.split("_")[1];
        String sender = buttonID.split("_")[2];
        String receiver = buttonID.split("_")[3];
        String extraDetail =
                buttonID.replace(buttonID.split("_")[0] + "_" + item + "_" + sender + "_" + receiver + "_", "");
        Player p1 = game.getPlayerFromColorOrFaction(sender);
        Player p2 = game.getPlayerFromColorOrFaction(receiver);
        if (p1 == null || p2 == null) return;
        if ("washComms".equalsIgnoreCase(item)) {
            int oldP1Comms = p1.getCommodities();
            int newP1Comms = 0;
            int totalWashPowerP1 = p1.getCommodities() + p1.getTg();
            int totalWashPowerP2 = p2.getCommodities() + p2.getTg();
            if (oldP1Comms > totalWashPowerP2) {
                newP1Comms = oldP1Comms - totalWashPowerP2;
            }
            int commsOfP1Washed = oldP1Comms - newP1Comms;
            int oldP2Comms = p2.getCommodities();
            int newP2Comms = 0;
            if (oldP2Comms > totalWashPowerP1) {
                newP2Comms = oldP2Comms - totalWashPowerP1;
            }
            int commsOfP2Washed = oldP2Comms - newP2Comms;
            int tgP1Sent = commsOfP2Washed - commsOfP1Washed;
            int tgP2Sent = commsOfP1Washed - commsOfP2Washed;
            if (commsOfP1Washed > 0) {
                player.addTransactionItem(
                        "sending" + p1.getFaction() + "_receiving" + p2.getFaction() + "_Comms_" + commsOfP1Washed);
            }
            if (commsOfP2Washed > 0) {
                player.addTransactionItem(
                        "sending" + p2.getFaction() + "_receiving" + p1.getFaction() + "_Comms_" + commsOfP2Washed);
            }
            if (tgP1Sent > 0) {
                player.addTransactionItem(
                        "sending" + p1.getFaction() + "_receiving" + p2.getFaction() + "_TGs_" + tgP1Sent);
            }
            if (tgP2Sent > 0) {
                player.addTransactionItem(
                        "sending" + p2.getFaction() + "_receiving" + p1.getFaction() + "_TGs_" + tgP2Sent);
            }
        } else {
            String itemS = "sending" + sender + "_receiving" + receiver + "_" + item + "_" + extraDetail;
            if (!player.getTransactionItems().contains(itemS) || !itemS.contains("dmz")) {
                player.addTransactionItem(itemS);
            }
        }
        var userSettings = UserSettingsManager.get(player.getUserID());

        if (("tgs".equalsIgnoreCase(item) || "Comms".equalsIgnoreCase(item))
                && p2.getDebtTokenCount(p1.getColor()) > 0
                && userSettings.isPrefersAutoDebtClearance()
                && !p2.hasAbility("data_recovery")) {
            int amount = Math.min(p2.getDebtTokenCount(p1.getColor()), Integer.parseInt(extraDetail));
            String clear = "sending" + receiver + "_receiving" + sender + "_ClearDebt_" + amount;
            if (!player.getTransactionItems().contains(clear)) {
                player.addTransactionItem(clear);
            }
        }
        Player opposing = p2;
        if (player == p2) {
            opposing = p1;
        }
        String message = "Current Transaction Offer is:\n"
                + buildTransactionOffer(player, opposing, game, false)
                + "### Click something else that you wish to __request from__ " + p1.getRepresentation(false, false);
        if (p1 == player) {
            message = "Current Transaction Offer is:\n"
                    + buildTransactionOffer(player, opposing, game, false)
                    + "### Click something else that you wish to __offer to__ " + p2.getRepresentation(false, false);
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(), message, getStuffToTransButtonsNew(game, player, p1, p2));
    }

    @ButtonHandler(value = "getNewTransaction_", save = false)
    public static void getNewTransaction(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String sender = buttonID.split("_")[1];
        String receiver = buttonID.split("_")[2];
        Player p1 = game.getPlayerFromColorOrFaction(sender);
        Player p2 = game.getPlayerFromColorOrFaction(receiver);
        if (p1 == null || p2 == null) return;
        Player opposing = p2;
        if (player == p2) {
            opposing = p1;
        }
        String message = "Current transaction offer is:\n"
                + buildTransactionOffer(player, opposing, game, false)
                + "### Click something that you wish to __request from__ " + p1.getRepresentation(false, false);
        if (p1 == player) {
            message = "Current Transaction Offer is:\n"
                    + buildTransactionOffer(player, opposing, game, false)
                    + "### Click something that you wish to __offer to__ " + p2.getRepresentation(false, false);
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(), message, getStuffToTransButtonsNew(game, player, p1, p2));
    }

    private static boolean sendMemeInsteadOfText(ButtonInteractionEvent event, Game game) {
        if (game.getName().startsWith("test")) return true;
        if (event.getTimeCreated().getMonth() == Month.APRIL
                && event.getTimeCreated().getDayOfMonth() == 1) return true;
        if ("pbd100two".equals(game.getName())) return RandomHelper.isOneInX(5);
        return RandomHelper.isOneInX(1000);
    }

    @ButtonHandler("sendOffer_")
    public static void sendOffer(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p2 == null) return;
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " sent a transaction offer to " + p2.getRepresentationNoPing()
                        + ".");
        if (game.getTableTalkChannel() != null) {
            boolean sentMeme = false;
            String publicOfferText = buildTransactionOffer(player, p2, game, true);
            if (sendMemeInsteadOfText(event, game)) {
                BufferedImage tradeOfferMeme = TransactionGenerator.drawTradeOfferMeme(game, player, p2);
                if (tradeOfferMeme != null) {
                    FileUpload upload = FileUploadService.createFileUpload(tradeOfferMeme, "trade_offer");
                    upload.setDescription(publicOfferText);
                    MessageHelper.sendFileUploadToChannel(game.getTableTalkChannel(), upload);
                    sentMeme = true;
                }
            }
            if (!sentMeme) {
                String offerMessage = "Trade offer from " + player.getRepresentationNoPing() + " to "
                        + p2.getRepresentationNoPing() + ":\n" + publicOfferText;
                MessageHelper.sendMessageToChannel(game.getTableTalkChannel(), offerMessage);
            }
        }

        String bmdSuffix = "";
        for (Player bmdPlayer : List.of(player, p2)) {
            if (game.getStoredValue("blackmarketdealing").equals(bmdPlayer.getFaction())) {
                bmdSuffix = "_BMD_" + bmdPlayer.getFaction();
                game.removeStoredValue("blackmarketdealing");
                break;
            }
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red("rescindOffer_" + p2.getFaction() + bmdSuffix, "Rescind Offer"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing() + " you sent a transaction offer to " + p2.getRepresentationNoPing()
                        + ":\n" + buildTransactionOffer(player, p2, game, false),
                buttons);

        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);

        int offerNumber = 1;
        String key = "offerFrom" + player.getFaction() + "To" + p2.getFaction();
        if (!game.getStoredValue(key).isEmpty()) {
            offerNumber = Integer.parseInt(game.getStoredValue(key)) + 1;
        }
        game.setStoredValue(key, offerNumber + "");

        buttons = new ArrayList<>();
        buttons.add(Buttons.green("acceptOffer_" + player.getFaction() + "_" + offerNumber, "Accept"));
        buttons.add(Buttons.red("rejectOffer_" + player.getFaction() + bmdSuffix, "Reject"));
        buttons.add(Buttons.red("resetOffer_" + player.getFaction() + bmdSuffix, "Reject and CounterOffer"));
        MessageHelper.sendMessageToChannelWithButtons(
                p2.getCardsInfoThread(),
                p2.getRepresentation() + " you have received a transaction offer from "
                        + player.getRepresentationNoPing() + ":\n" + buildTransactionOffer(player, p2, game, false),
                buttons);
        checkTransactionLegality(game, p2, player);
    }

    @ButtonHandler("transact_")
    public static void resolveSpecificTransButtonsOld(
            Game game, Player p1, String buttonID, ButtonInteractionEvent event) {
        String finChecker = "FFCC_" + p1.getFaction() + "_";

        List<Button> stuffToTransButtons = new ArrayList<>();
        buttonID = buttonID.replace("transact_", "");
        String thingToTrans = buttonID.substring(0, buttonID.indexOf('_'));
        String factionToTrans = buttonID.substring(buttonID.indexOf('_') + 1);
        Player p2 = game.getPlayerFromColorOrFaction(factionToTrans);
        if (p2 == null) {
            return;
        }

        switch (thingToTrans) {
            case "TGs" -> {
                String message = "Please choose the number of trade goods you wish to send.";
                for (int x = 1; x < p1.getTg() + 1 && x < 21; x++) {
                    Button transact = Buttons.green(finChecker + "send_TGs_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "Comms" -> {
                String message = "Please choose the number of commodities you wish to send.";
                for (int x = 1; x < p1.getCommodities() + 1; x++) {
                    Button transact = Buttons.green(finChecker + "send_Comms_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "ClearDebt" -> {
                String message = "Please choose the amount of debt you wish to clear.";
                for (int x = 1; x < p1.getDebtTokenCount(p2.getColor()) + 1; x++) {
                    Button transact = Buttons.green(finChecker + "send_ClearDebt_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "SendDebt" -> {
                String message = "Please choose the amount of debt you wish to send.";
                for (int x = 1; x < 6; x++) {
                    Button transact = Buttons.green(finChecker + "send_SendDebt_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "shipOrders" -> {
                String message = "Please choose the _Axis Order_ you wish to send.";
                for (String shipOrder : ButtonHelper.getPlayersShipOrders(p1)) {
                    Button transact = Buttons.green(
                            finChecker + "send_shipOrders_" + p2.getFaction() + "_" + shipOrder,
                            Mapper.getRelic(shipOrder).getName());
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "starCharts" -> {
                String message = "Please choose the _Star Chart_ you wish to send.";
                for (String starChart : ButtonHelper.getPlayersStarCharts(p1)) {
                    Button transact = Buttons.green(
                            finChecker + "send_starCharts_" + p2.getFaction() + "_" + starChart,
                            Mapper.getRelic(starChart).getName());
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "Planets" -> {
                String message = "Please choose the planet you wish to send.";
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getChannel(),
                        message,
                        ButtonHelperFactionSpecific.getTradePlanetsWithHacanMechButtons(p1, p2, game));
            }
            case "AlliancePlanets" -> {
                String message = "Please choose the planet you wish to send";
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getChannel(),
                        message,
                        ButtonHelper.getTradePlanetsWithAlliancePartnerButtons(p1, p2, game));
            }
            case "ACs" -> {
                String message = p1.getRepresentation()
                        + ", please choose the __green__ button that indicates the action card you wish to send.";
                for (String acShortHand : p1.getActionCards().keySet()) {
                    Button transact = Buttons.green(
                            finChecker + "send_ACs_" + p2.getFaction() + "_"
                                    + p1.getActionCards().get(acShortHand),
                            Mapper.getActionCard(acShortHand).getName());
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(), message, stuffToTransButtons);
            }
            case "PNs" -> {
                PromissoryNoteHelper.sendPromissoryNoteInfo(game, p1, false);
                String message =
                        p1.getRepresentationUnfogged() + ", please choose the promissory note you wish to send.";

                for (String pnShortHand : p1.getPromissoryNotes().keySet()) {
                    if (p1.getPromissoryNotesInPlayArea().contains(pnShortHand)
                            || (p2.hasAbility("hubris") && pnShortHand.endsWith("an"))) {
                        continue;
                    }
                    PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnShortHand);
                    Player owner = game.getPNOwner(pnShortHand);
                    if (owner == null) {
                        continue;
                    }
                    Button transact;
                    Integer intID = p1.getPromissoryNotes().get(pnShortHand);
                    if (game.isFowMode()) {
                        transact = Buttons.green(
                                finChecker + "send_PNs_" + p2.getFaction() + "_" + intID,
                                owner.getColor() + " " + promissoryNote.getName());
                    } else {
                        transact = Buttons.green(
                                finChecker + "send_PNs_" + p2.getFaction() + "_" + intID,
                                promissoryNote.getName(),
                                owner.getFactionEmoji());
                    }
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(), message, stuffToTransButtons);
                MessageHelper.sendMessageToChannel(
                        p1.getCardsInfoThread(),
                        "A reminder that, unlike other things,"
                                + " you may only send a player 1 promissory note in each transaction (and you may only perform one transaction with each other player on a turn).");
                if (game.isNoSwapMode()) {
                    MessageHelper.sendMessageToChannel(
                            p1.getCardsInfoThread(),
                            "### A reminder that you cannot swap _Supports For The Thrones_ in this game");
                }
            }
            case "SOs" -> {
                String message = p1.getRepresentation();
                message += " Click the __green__ button that indicates the unscored secret objective you wish to send.";

                String prefix = finChecker + "send_SOs_" + p2.getFaction() + "_";
                for (String soID : p1.getSecretsUnscored().keySet()) {
                    String name = Mapper.getSecretObjective(soID).getName();
                    Integer intID = p1.getSecretsUnscored().get(soID);
                    stuffToTransButtons.add(Buttons.green(prefix + intID, name, CardEmojis.SecretObjective));
                }
                MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(), message, stuffToTransButtons);
            }
            case "Technology" -> {
                String message = "Please choose the technology you wish to send.";
                for (String tech : p1.getTechs()) {
                    if (resolveAgeOfCommerceTechCheck(p1, p2, tech, game)) {
                        Button transact = Buttons.gray(
                                finChecker + "send_Technology_" + p2.getFaction() + "_" + tech,
                                Mapper.getTech(tech).getName());
                        stuffToTransButtons.add(transact);
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(), message, stuffToTransButtons);
            }
            case "Frags" -> {
                String message = "Please choose the amount of relic fragments you wish to send";

                if (p1.getCrf() > 0) {
                    for (int x = 1; x < p1.getCrf() + 1; x++) {
                        Button transact = Buttons.blue(
                                finChecker + "send_Frags_" + p2.getFaction() + "_CRF" + x,
                                "Cultural Fragments (" + x + ")");
                        stuffToTransButtons.add(transact);
                    }
                }
                if (p1.getIrf() > 0) {
                    for (int x = 1; x < p1.getIrf() + 1; x++) {
                        Button transact = Buttons.green(
                                finChecker + "send_Frags_" + p2.getFaction() + "_IRF" + x,
                                "Industrial Fragments (" + x + ")");
                        stuffToTransButtons.add(transact);
                    }
                }
                if (p1.getHrf() > 0) {
                    for (int x = 1; x < p1.getHrf() + 1; x++) {
                        Button transact = Buttons.red(
                                finChecker + "send_Frags_" + p2.getFaction() + "_HRF" + x,
                                "Hazardous Fragments (" + x + ")");
                        stuffToTransButtons.add(transact);
                    }
                }

                if (p1.getUrf() > 0) {
                    for (int x = 1; x < p1.getUrf() + 1; x++) {
                        Button transact = Buttons.gray(
                                finChecker + "send_Frags_" + p2.getFaction() + "_URF" + x,
                                "Frontier Fragments (" + x + ")");
                        stuffToTransButtons.add(transact);
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "Relics" -> {
                String message = " Click the relics you wish to send.";
                String prefix = "send_Relics_" + p2.getFaction() + "_";
                for (String relic : p1.getActualRelics()) {
                    String name = Mapper.getRelic(relic).getName();
                    stuffToTransButtons.add(Buttons.gray(prefix + relic, name, ExploreEmojis.Relic));
                }
                MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(), message, stuffToTransButtons);
            }
        }
    }

    private static void resolveSpecificTransButtonPress(
            Game game, Player p1, String buttonID, ButtonInteractionEvent event, boolean oldWay) {
        String finChecker = "FFCC_" + p1.getFaction() + "_";
        buttonID = buttonID.replace("send_", "");
        List<Button> goAgainButtons = new ArrayList<>();

        String thingToTrans = buttonID.substring(0, buttonID.indexOf('_'));
        buttonID = buttonID.replace(thingToTrans + "_", "");
        String factionToTrans = buttonID.substring(0, buttonID.indexOf('_'));
        String amountToTrans = buttonID.substring(buttonID.indexOf('_') + 1);
        Player p2 = game.getPlayerFromColorOrFaction(factionToTrans);
        if (p1 == null || p2 == null) return;

        String message2 = "";
        String ident = p1.getRepresentation();
        String ident2 = p2.getRepresentation();
        switch (thingToTrans) {
            case "TGs" -> {
                int tgAmount = Integer.parseInt(amountToTrans);
                tgAmount = Math.min(p1.getTg(), tgAmount);
                p1.setTg(p1.getTg() - tgAmount);
                p2.setTg(p2.getTg() + tgAmount);
                message2 = ident + " sent " + tgAmount + " trade good" + (tgAmount == 1 ? "" : "s") + " to " + ident2
                        + ".";
                if (p2.getDebtTokenCount(p1.getColor()) > 0 && !p2.hasAbility("data_recovery") && oldWay) {
                    int amount = Math.min(tgAmount, p2.getDebtTokenCount(p1.getColor()));
                    p2.clearDebt(p1, amount);
                    message2 += "\n" + ident2 + " cleared " + amount + " debt token" + (amount == 1 ? "" : "s")
                            + " owned by " + ident + ", from their \"Debt Account\" pool.";
                }
            }
            case "Comms" -> {
                int tgAmount = Integer.parseInt(amountToTrans);
                tgAmount = Math.min(p1.getCommodities(), tgAmount);
                p1.setCommodities(p1.getCommodities() - tgAmount);
                if (!p1.isPlayerMemberOfAlliance(p2)) {
                    int targetTG = p2.getTg();
                    targetTG += tgAmount;
                    p2.setTg(targetTG);
                } else {
                    int targetTG = p2.getCommodities();
                    targetTG += tgAmount;
                    p2.setCommodities(targetTG);
                }
                ButtonHelperFactionSpecific.resolveDarkPactCheck(game, p1, p2, tgAmount);
                message2 = ident + " sent " + tgAmount + " commodit" + (tgAmount == 1 ? "y" : "ies") + " to " + ident2
                        + ".";
                if (p2.getDebtTokenCount(p1.getColor()) > 0 && !p2.hasAbility("data_recovery") && oldWay) {
                    int amount = Math.min(tgAmount, p2.getDebtTokenCount(p1.getColor()));
                    p2.clearDebt(p1, amount);
                    message2 += "\n" + ident2 + " cleared " + amount + " debt token" + (amount == 1 ? "" : "s")
                            + " owned by " + ident + ", from their \"Debt Account\" pool.";
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
                ButtonHelperFactionSpecific.resolveDarkPactCheck(game, p1, p2, oldP1Comms);
                ButtonHelperFactionSpecific.resolveDarkPactCheck(game, p2, p1, oldP2Comms);
                String id1 = p1.getFactionEmojiOrColor();
                String id2 = p2.getFactionEmojiOrColor();
                int deltaP1 = oldP1Comms - newP1Comms;
                int deltaP2 = oldP2Comms - newP2Comms;
                message2 = ident + " washed their " + deltaP1 + " commodit" + (deltaP1 == 1 ? "" : "s") + " with " + id2
                        + "; "
                        + id1 + " trade goods went from " + oldP1Tg + " to " + p1.getTg() + ".\n"
                        + ident2 + " washed their " + deltaP2 + " commodit" + (deltaP2 == 1 ? "" : "s") + " with " + id1
                        + "; "
                        + id2 + " trade goods went from " + oldP2tg + " to " + p2.getTg() + ".";
            }
            case "shipOrders", "starCharts" -> {
                message2 = ident + " sent " + Mapper.getRelic(amountToTrans).getName() + " to " + ident2;
                if (!p1.hasRelic(amountToTrans)) {
                    if (amountToTrans.contains("duplicate") && p1.hasRelic(amountToTrans.replace("duplicate", ""))) {
                        amountToTrans = amountToTrans.replace("duplicate", "");
                    } else {
                        if (!amountToTrans.contains("duplicate") && p1.hasRelic(amountToTrans + "duplicate")) {
                            amountToTrans += "duplicate";
                        }
                    }
                }
                p1.removeRelic(amountToTrans);
                p2.addRelic(amountToTrans);
            }
            case "SendDebt" -> {
                message2 = ident + " sent " + amountToTrans + " debt token"
                        + (Integer.parseInt(amountToTrans) == 1 ? "" : "s") + " to " + ident2
                        + ", for their \"Debt Account\" pool.";
                p2.addDebtTokens(p1.getColor(), Integer.parseInt(amountToTrans));
                CommanderUnlockCheckService.checkPlayer(p2, "vaden");
            }
            case "ClearDebt" -> {
                message2 = ident + " cleared " + amountToTrans + " debt token"
                        + (Integer.parseInt(amountToTrans) == 1 ? "" : "s") + " of " + ident2
                        + ", from their \"Debt Account\" pool.";
                p1.removeDebtTokens(p2.getColor(), Integer.parseInt(amountToTrans));
            }
            case "ACs" -> {
                message2 = ident + " sent action card #" + amountToTrans + " to " + ident2 + ".";
                int acNum = Integer.parseInt(amountToTrans);
                String acID = null;
                if (!p1.getActionCards().containsValue(acNum)) {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            "Could not find that action card, and so no action card was sent.");
                    return;
                }
                for (Map.Entry<String, Integer> ac : p1.getActionCards().entrySet()) {
                    if (ac.getValue().equals(acNum)) {
                        acID = ac.getKey();
                    }
                }
                p1.removeActionCard(acNum);
                p2.setActionCard(acID);
                ButtonHelper.checkACLimit(game, p2);
                ActionCardHelper.sendActionCardInfo(game, p2);
                ActionCardHelper.sendActionCardInfo(game, p1);

                boolean blackMarket =
                        List.of(p1.getFaction(), p2.getFaction()).contains(game.getStoredValue("blackmarketdealing"));
                if (!p1.hasAbility("arbiters") && !p2.hasAbility("arbiters") && !blackMarket) {
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
                    MessageHelper.sendMessageToChannel(
                            p1.getCorrectChannel(),
                            "# " + p1.getRepresentation()
                                    + " heads up, a promissory note failed to send. This is likely due to you not having the promissory note to send."
                                    + " Maybe you already gave it to someone else and forgot?");
                    return;
                }
                for (Map.Entry<String, Integer> pn : p1.getPromissoryNotes().entrySet()) {
                    if (pn.getValue().equals(pnIndex)) {
                        id = pn.getKey();
                        break;
                    }
                }
                if (id == null) {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            "Could not find that promissory note, so no promissory note was sent.");
                    return;
                }

                SendPromissoryService.sendPromissoryToPlayer(event, game, p1, p2, id);
                CardsInfoService.sendVariousAdditionalButtons(game, p1);
                CardsInfoService.sendVariousAdditionalButtons(game, p2);
            }
            case "SOs" -> {
                String soID = null;
                int soNum = 0;
                if (StringUtils.isNumeric(amountToTrans)) {
                    soNum = Integer.parseInt(amountToTrans);
                    for (Map.Entry<String, Integer> so : p1.getSecretsUnscored().entrySet()) {
                        if (so.getValue().equals(soNum)) {
                            soID = so.getKey();
                        }
                    }
                } else {
                    soID = amountToTrans;
                    for (Map.Entry<String, Integer> so : p1.getSecretsUnscored().entrySet()) {
                        if (so.getKey().equals(soID)) {
                            soNum = so.getValue();
                        }
                    }
                }

                if (!p1.getSecretsUnscored().containsValue(soNum) || soID == null) {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            "Could not find that unscored secret objective, and so no secret objective was sent.");
                    return;
                }

                p1.removeSecret(soNum);
                p2.setSecret(soID);

                if (oldWay) {
                    SecretObjectiveInfoService.sendSecretObjectiveInfo(game, p1, event);
                    SecretObjectiveInfoService.sendSecretObjectiveInfo(game, p2, event);
                }

                String msg = p1.getRepresentation() + " sent "
                        + (RandomHelper.isOneInX(20) ? "||a secret objective||" : "a secret objective") + " to "
                        + p2.getRepresentation() + ".";
                MessageHelper.sendMessageToChannel(p1.getCorrectChannel(), msg);
                if (game.isFowMode()) MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg);
            }
            case "Frags" -> {
                String fragType = amountToTrans.substring(0, 3).toUpperCase();
                int fragNum = Integer.parseInt(amountToTrans.charAt(3) + "");
                String trait =
                        switch (fragType) {
                            case "CRF" -> "cultural";
                            case "HRF" -> "hazardous";
                            case "IRF" -> "industrial";
                            case "URF" -> "frontier";
                            default -> "";
                        };
                RelicHelper.sendFrags(event, p1, p2, trait, fragNum, game);
                message2 = "";
            }
            case "Technology" -> {
                p2.addTech(amountToTrans);
                MessageHelper.sendMessageToChannel(
                        p2.getCorrectChannel(),
                        p2.getRepresentation() + ", you have received the technology _" + Mapper.getTech(amountToTrans)
                                + "_ from a transaction.");
            }
            case "Relics" -> SendRelicService.handleSendRelic(event, game, p1, p2, amountToTrans);
        }
        Button button = Buttons.gray(finChecker + "transactWith_" + p2.getColor(), "Send something else to player?");
        Button done = Buttons.gray("finishTransaction_" + p2.getColor(), "Done With This Transaction");

        goAgainButtons.add(button);
        goAgainButtons.add(Buttons.green("demandSomething_" + p2.getColor(), "Expect Something in Return"));
        goAgainButtons.add(done);
        if (game.isFowMode()) {
            if (!message2.isEmpty()) {
                MessageHelper.sendMessageToChannel(p1.getCardsInfoThread(), message2);
                MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), "**🤝 Transaction:** " + message2);
            }
        } else {
            TextChannel channel = game.getMainGameChannel();
            if ("pbd1000".equalsIgnoreCase(game.getName())) {
                channel = game.getTableTalkChannel();
            }
            if (oldWay
                    || (message2.toLowerCase().contains("alliance")
                            || message2.toLowerCase().contains("support"))) {
                MessageHelper.sendMessageToChannel(channel, message2);
            }
        }
        if (oldWay) {
            MessageHelper.sendMessageToChannelWithButtons(
                    p1.getCardsInfoThread(),
                    ident + ", use these buttons to complete the transaction.",
                    goAgainButtons);
        }
    }

    private static boolean canTheseTwoTransact(Game game, Player player, Player player2) {
        // if(game.getRealPlayers().size() > 26){
        //     return true;
        // }
        if (IsPlayerElectedService.isPlayerElected(game, player2, "tf-censure")
                || IsPlayerElectedService.isPlayerElected(game, player, "tf-censure")) {
            return false;
        }
        return player == player2
                || !"action".equalsIgnoreCase(game.getPhaseOfGame())
                || (player.hasSpaceStation() && player2.hasSpaceStation())
                || game.isAgeOfCommerceMode()
                || player.hasAbility("guild_ships")
                || player.getPromissoryNotesInPlayArea().contains("convoys")
                || player2.getPromissoryNotesInPlayArea().contains("convoys")
                || player2.hasAbility("guild_ships")
                || player.getPromissoryNotesInPlayArea().contains("sigma_trade_convoys")
                || player2.getPromissoryNotesInPlayArea().contains("sigma_trade_convoys")
                || player2.getNeighbouringPlayers(false).contains(player)
                || player.getNeighbouringPlayers(false).contains(player2);
    }

    public static void checkTransactionLegality(Game game, Player player, Player player2) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(player.getRepresentationUnfogged()).append(", this is a friendly reminder that ");
        if (!canTheseTwoTransact(game, player, player2)) {
            sb.append("you cannot transact with ")
                    .append(player2.getRepresentation(false, false))
                    .append(". (probably due to no neighborship)");
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), sb.toString());
        }
        if (player.hasAbility("policy_the_people_control") && !"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            sb.append("you cannot transact during the Agenda Phase due to your ")
                    .append(FactionEmojis.olradin)
                    .append(" _Policy - The People: Control ➖_.");
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
        }
        if (player2.hasAbility("policy_the_people_control") && !"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            sb.append(player2.getRepresentation(false, false))
                    .append(" cannot transact during the Agenda Phase due to their ")
                    .append(FactionEmojis.olradin)
                    .append(" _Policy - The People: Control ➖_.");
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
        }
    }

    private static List<Button> getPlayersToTransact(Game game, Player p) {
        List<Button> playerButtons = new ArrayList<>();
        String finChecker = "FFCC_" + p.getFaction() + "_";
        for (Player player : game.getPlayers().values()) {
            if (player.isRealPlayer()) {
                if (player.getFaction().equalsIgnoreCase(p.getFaction())) {
                    continue;
                }
                String faction = player.getFaction();
                if (faction != null && Mapper.isValidFaction(faction)) {
                    Button button;
                    if (!game.isFowMode()) {
                        String label = player.getUserName();
                        if (p.isNeighboursWith(player)) {
                            button = Buttons.green(finChecker + "transactWith_" + faction, label);
                        } else if (canTheseTwoTransact(game, p, player)) {
                            button = Buttons.blue(finChecker + "transactWith_" + faction, label);
                        } else {
                            button = Buttons.gray(finChecker + "transactWith_" + faction, label);
                        }

                        String factionEmojiString = player.getFactionEmoji();
                        button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    } else {
                        button = Buttons.gray(finChecker + "transactWith_" + player.getColor(), player.getColor());
                    }
                    playerButtons.add(button);
                }
            }
        }
        return playerButtons;
    }

    private static List<Button> getStuffToTransButtonsNew(Game game, Player player, Player p1, Player p2) {
        boolean blackMarket =
                List.of(p1.getFaction(), p2.getFaction()).contains(game.getStoredValue("blackmarketdealing"));
        List<Button> stuffToTransButtons = new ArrayList<>();
        if (p1.getTg() > 0) {
            stuffToTransButtons.add(
                    Buttons.green("newTransact_TGs_" + p1.getFaction() + "_" + p2.getFaction(), "Trade Goods"));
        }
        if (p1.getDebtTokenCount(p2.getColor()) > 0) {
            stuffToTransButtons.add(
                    Buttons.blue("newTransact_ClearDebt_" + p1.getFaction() + "_" + p2.getFaction(), "Clear Debt"));
        }
        stuffToTransButtons.add(
                Buttons.red("newTransact_SendDebt_" + p1.getFaction() + "_" + p2.getFaction(), "Send Debt"));
        if (p1.getCommodities() > 0 && !p1.hasAbility("military_industrial_complex")) {
            stuffToTransButtons.add(
                    Buttons.green("newTransact_Comms_" + p1.getFaction() + "_" + p2.getFaction(), "Commodities"));
        }

        if (p1 == player
                && !game.isFowMode()
                && (p1.getCommodities() > 0 || p2.getCommodities() > 0)
                && !p1.hasAbility("military_industrial_complex")
                && !p1.getAllianceMembers().contains(p2.getFaction())) {
            stuffToTransButtons.add(Buttons.gray(
                    "offerToTransact_washComms_" + player.getFaction() + "_" + p2.getFaction() + "_0",
                    "Wash Both Players' Commodities"));
        }
        if (!ButtonHelper.getPlayersShipOrders(p1).isEmpty()) {
            stuffToTransButtons.add(
                    Buttons.gray("newTransact_shipOrders_" + p1.getFaction() + "_" + p2.getFaction(), "Axis Orders"));
        }
        if (ButtonHelper.getNumberOfStarCharts(p1) > 0) {
            stuffToTransButtons.add(
                    Buttons.gray("newTransact_starCharts_" + p1.getFaction() + "_" + p2.getFaction(), "Star Charts"));
        }
        if ((p1.hasAbility("arbiters")
                        || p2.hasAbility("arbiters")
                        || p1.hasTech("tf-guildships")
                        || p2.hasTech("tf-guildships")
                        || blackMarket)
                && p1.getAcCount() > 0) {
            stuffToTransButtons.add(
                    Buttons.green("newTransact_ACs_" + p1.getFaction() + "_" + p2.getFaction(), "Action Cards"));
        }
        if (p1.getPnCount() > 0) {
            stuffToTransButtons.add(
                    Buttons.green("newTransact_PNs_" + p1.getFaction() + "_" + p2.getFaction(), "Promissory Notes"));
        }
        if ((blackMarket || p1.hasUnlockedBreakthrough("zooidbt") || p2.hasUnlockedBreakthrough("zooidbt"))
                && !p1.getSecretsUnscored().isEmpty()) {
            stuffToTransButtons.add(
                    Buttons.gray("newTransact_SOs_" + p1.getFaction() + "_" + p2.getFaction(), "Secret Objectives"));
        }
        if (!p1.getFragments().isEmpty()) {
            stuffToTransButtons.add(
                    Buttons.green("newTransact_Frags_" + p1.getFaction() + "_" + p2.getFaction(), "Fragments"));
        }
        if ((blackMarket && !p1.getActualRelics().isEmpty())
                || !p1.getTradableRelics().isEmpty()) {
            stuffToTransButtons.add(
                    Buttons.gray("newTransact_Relics_" + p1.getFaction() + "_" + p2.getFaction(), "Relics"));
        }
        if (p1 == player) {
            stuffToTransButtons.add(Buttons.blue(
                    "newTransact_Details_" + p1.getFaction() + "_" + p2.getFaction() + "_~MDL", "Specify Deal"));
        }
        if (p2 == player) {
            stuffToTransButtons.add(Buttons.blue(
                    "newTransact_DetailsInvert_" + p1.getFaction() + "_" + p2.getFaction() + "_~MDL", "Specify Deal"));
        }
        if (!ButtonHelperFactionSpecific.getTradePlanetsWithHacanMechButtons(p1, p2, game)
                .isEmpty()) {
            stuffToTransButtons.add(Buttons.green(
                    "newTransact_Planets_" + p1.getFaction() + "_" + p2.getFaction(), "Planets", FactionEmojis.Hacan));
        }
        if (game.isAgeOfCommerceMode()) {
            stuffToTransButtons.add(
                    Buttons.green("newTransact_Technology_" + p1.getFaction() + "_" + p2.getFaction(), "Technology"));
        }
        if (!ButtonHelper.getTradePlanetsWithAlliancePartnerButtons(p1, p2, game)
                .isEmpty()) {
            stuffToTransButtons.add(Buttons.green(
                    "newTransact_AlliancePlanets_" + p1.getFaction() + "_" + p2.getFaction(),
                    "Alliance Planets",
                    p2.getFactionEmoji()));
        }
        if ((game.getPhaseOfGame().toLowerCase().contains("agenda")
                        || game.getPhaseOfGame().toLowerCase().contains("strategy"))
                && !"no".equalsIgnoreCase(ButtonHelper.playerHasDMZPlanet(p1, game))) {
            Button transact = Buttons.gray(
                    "offerToTransact_dmz_" + p1.getFaction() + "_" + p2.getFaction() + "_"
                            + ButtonHelper.playerHasDMZPlanet(p1, game),
                    "Trade "
                            + Mapper.getPlanet(ButtonHelper.playerHasDMZPlanet(p1, game))
                                    .getName() + " (DMZ)");
            stuffToTransButtons.add(transact);
        }

        if (player == p1) {
            if (!getReturnPNsInPlayAreaButtons(game, p1, p2).isEmpty()) {
                stuffToTransButtons.add(Buttons.gray(
                        "startReturnPNInPlayArea_" + p2.getFaction(), "Return a Play Area Promissory Note"));
            }
            stuffToTransButtons.add(Buttons.gray("resetOffer_" + p2.getFaction(), "Reset Offer"));
            stuffToTransButtons.add(
                    Buttons.red("getNewTransaction_" + p2.getFaction() + "_" + p1.getFaction(), "Ask for Stuff"));
            stuffToTransButtons.add(Buttons.gray("sendOffer_" + p2.getFaction(), "Send the Offer"));
        } else {
            stuffToTransButtons.add(Buttons.gray("resetOffer_" + p1.getFaction(), "Reset Offer"));
            stuffToTransButtons.add(
                    Buttons.red("getNewTransaction_" + p2.getFaction() + "_" + p1.getFaction(), "Offer More"));
            stuffToTransButtons.add(Buttons.gray("sendOffer_" + p1.getFaction(), "Send the Offer"));
        }

        return stuffToTransButtons;
    }

    @ButtonHandler("send_")
    public static void send(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        resolveSpecificTransButtonPress(game, player, buttonID, event, true);
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getReturnPNsInPlayAreaButtons(Game game, Player p1, Player p2) {
        List<Button> buttons = new ArrayList<>();
        for (String pn : p1.getPromissoryNotesInPlayArea()) {
            if (p2 == game.getPNOwner(pn) && Mapper.getPromissoryNote(pn) != null) {
                buttons.add(Buttons.gray(
                        "returnPNInPlayArea_" + pn,
                        "Return " + Mapper.getPromissoryNote(pn).getName()));
            }
        }

        return buttons;
    }

    @ButtonHandler("startReturnPNInPlayArea_")
    public static void startReturnPNInPlayArea(ButtonInteractionEvent event, Player p1, String buttonID, Game game) {
        String message = "## Warning, this is only to be done if a bug or mistake occurred. You cannot normally"
                + " return play area promissory notes via a transaction.";
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = getReturnPNsInPlayAreaButtons(game, p1, p2);
        MessageHelper.sendMessageToChannel(p1.getCardsInfoThread(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("returnPNInPlayArea_")
    public static void returnPNInPlayArea(ButtonInteractionEvent event, Player p1, String buttonID, Game game) {
        String id = buttonID.replace("returnPNInPlayArea_", "");

        Player p2 = game.getPNOwner(id);
        if (p2 != null) {
            p1.removePromissoryNote(id);
            p2.setPromissoryNote(id);
            PromissoryNoteHelper.sendPromissoryNoteInfo(game, p1, false);
            PromissoryNoteHelper.sendPromissoryNoteInfo(game, p2, false);
            String message2 = p1.getRepresentation() + " returned _"
                    + Mapper.getPromissoryNote(id).getName() + "_ from their play area to " + p2.getRepresentation()
                    + ".";
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), message2);
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(p1.getCorrectChannel(), message2);
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getStuffToTransButtonsOld(Game game, Player p1, Player p2) {
        String finChecker = "FFCC_" + p1.getFaction() + "_";
        List<Button> stuffToTransButtons = new ArrayList<>();
        if (p1.getTg() > 0) {
            stuffToTransButtons.add(Buttons.green(finChecker + "transact_TGs_" + p2.getFaction(), "Trade Goods"));
        }
        if (p1.getDebtTokenCount(p2.getColor()) > 0) {
            stuffToTransButtons.add(Buttons.blue(finChecker + "transact_ClearDebt_" + p2.getFaction(), "Clear Debt"));
        }
        stuffToTransButtons.add(Buttons.red(finChecker + "transact_SendDebt_" + p2.getFaction(), "Send Debt"));
        if (p1.getCommodities() > 0 && !p1.hasAbility("military_industrial_complex")) {
            stuffToTransButtons.add(Buttons.green(finChecker + "transact_Comms_" + p2.getFaction(), "Comms"));
        }

        if (!game.isFowMode()
                && (p1.getCommodities() > 0 || p2.getCommodities() > 0)
                && !p1.hasAbility("military_industrial_complex")
                && !p1.getAllianceMembers().contains(p2.getFaction())) {
            stuffToTransButtons.add(Buttons.gray(
                    finChecker + "send_WashComms_" + p2.getFaction() + "_0", "Wash Both Players' Commodities"));
        }
        if (!ButtonHelper.getPlayersShipOrders(p1).isEmpty()) {
            stuffToTransButtons.add(Buttons.gray(finChecker + "transact_shipOrders_" + p2.getFaction(), "Axis Orders"));
        }
        if (ButtonHelper.getNumberOfStarCharts(p1) > 0) {
            stuffToTransButtons.add(Buttons.gray(finChecker + "transact_starCharts_" + p2.getFaction(), "Star Charts"));
        }
        if ((p1.hasAbility("arbiters")
                        || (!game.isFowMode() && p2.hasAbility("arbiters"))
                        || p1.hasTech("tf-guildships")
                        || (!game.isFowMode() && p2.hasTech("tf-guildships")))
                && p1.getAcCount() > 0) {
            stuffToTransButtons.add(Buttons.green(finChecker + "transact_ACs_" + p2.getFaction(), "Action Cards"));
        }
        if (p1.getPnCount() > 0) {
            stuffToTransButtons.add(Buttons.green(finChecker + "transact_PNs_" + p2.getFaction(), "Promissory Notes"));
        }
        if (!p1.getFragments().isEmpty()) {
            stuffToTransButtons.add(Buttons.green(finChecker + "transact_Frags_" + p2.getFaction(), "Fragments"));
        }
        if (!ButtonHelperFactionSpecific.getTradePlanetsWithHacanMechButtons(p1, p2, game)
                .isEmpty()) {
            stuffToTransButtons.add(
                    Buttons.green(finChecker + "transact_Planets_" + p2.getFaction(), "Planets", FactionEmojis.Hacan));
        }
        if (game.isAgeOfCommerceMode()) {
            stuffToTransButtons.add(Buttons.green(finChecker + "transact_Technology_" + p2.getFaction(), "Technology"));
        }
        if (!ButtonHelper.getTradePlanetsWithAlliancePartnerButtons(p1, p2, game)
                .isEmpty()) {
            stuffToTransButtons.add(Buttons.green(
                    finChecker + "transact_AlliancePlanets_" + p2.getFaction(),
                    "Alliance Planets",
                    p2.getFactionEmoji()));
        }
        if (game.getPhaseOfGame().toLowerCase().contains("agenda")
                && !"no".equalsIgnoreCase(ButtonHelper.playerHasDMZPlanet(p1, game))) {
            Button transact = Buttons.gray(
                    finChecker + "resolveDMZTrade_" + ButtonHelper.playerHasDMZPlanet(p1, game) + "_" + p2.getFaction(),
                    "Trade "
                            + Mapper.getPlanet(ButtonHelper.playerHasDMZPlanet(p1, game))
                                    .getName() + " (DMZ)");
            stuffToTransButtons.add(transact);
        }
        return stuffToTransButtons;
    }

    private static void restoreBlackMarketStatus(Game game, String buttonID) {
        if (buttonID.contains("_BMD_")) {
            String prevBlackMarketFaction = buttonID.substring(buttonID.indexOf("_BMD_") + 5);
            Player bmdPlayer = game.getPlayerFromColorOrFaction(prevBlackMarketFaction);
            if (bmdPlayer != null) {
                String msg = "The previous offer you made using _Black Market Dealing_ was rejected, rescinded, ";
                msg += "or is being counter offered. Therefore, the next transaction offer you make will be ";
                msg += "allowed to use _Black Market Dealing_ again.";
                MessageHelper.sendMessageToChannel(bmdPlayer.getCardsInfoThread(), msg);
                game.setStoredValue("blackmarketdealing", prevBlackMarketFaction);
            }
        }
    }

    @ButtonHandler("rescindOffer_")
    public static void rescindOffer(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        restoreBlackMarketStatus(game, buttonID);
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p2 != null) {
            MessageHelper.sendMessageToChannel(
                    p2.getCardsInfoThread(),
                    p2.getRepresentation() + " the latest offer from " + player.getRepresentation(false, false)
                            + " has been rescinded.");
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentation() + "you rescinded the latest offer to "
                            + p2.getRepresentation(false, false));
            player.clearTransactionItemsWithPlayer(p2);
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler("rejectOffer_")
    public static void rejectOffer(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        restoreBlackMarketStatus(game, buttonID);
        Player p1 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p1 != null) {
            MessageHelper.sendMessageToChannel(
                    p1.getCardsInfoThread(),
                    p1.getRepresentation() + " your offer to " + player.getRepresentation(false, false)
                            + " has been rejected.");
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler("acceptOffer_")
    public static void acceptOffer(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player p1 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p1 == null) return;

        if (buttonID.split("_").length > 2) {
            String offerNum = buttonID.split("_")[2];
            String key = "offerFrom" + p1.getFaction() + "To" + player.getFaction();
            String oldOffer = game.getStoredValue(key);
            if (!offerNum.equalsIgnoreCase(oldOffer)) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Only the most recent offer is acceptable. This is an old transaction offer and it can no longer be accepted");
                return;
            }
        }
        acceptTransactionOffer(p1, player, game, event);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("finishTransaction_")
    public static void finishTransaction(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String player2Color = buttonID.split("_")[1];
        Player player2 = game.getPlayerFromColorOrFaction(player2Color);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAbilities.pillageCheck(player2, game);
        CommanderUnlockCheckService.checkPlayer(player, "hacan");
        CommanderUnlockCheckService.checkPlayer(player2, "hacan");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("demandSomething_")
    public static void demandSomething(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String player2Color = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(player2Color);
        if (p2 != null) {
            List<Button> buttons = getStuffToTransButtonsOld(game, p2, player);
            String message = p2.getRepresentationUnfogged()
                    + " you have been given something on the condition that you give something in return. Hopefully the player explained what."
                    + " If you don't hand it over, please return what they sent. Use buttons to send something to "
                    + player.getRepresentationNoPing();
            MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message, buttons);
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler("transactWith_")
    @ButtonHandler("resetOffer_")
    public static void transactWith(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        restoreBlackMarketStatus(game, buttonID);
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 != null) {
            player.clearTransactionItemsWithPlayer(p2);
            List<Button> buttons = getStuffToTransButtonsOld(game, player, p2);
            if (!game.isFowMode() && game.isNewTransactionMethod()) {
                buttons = getStuffToTransButtonsNew(game, player, player, p2);
                if (player.getUserSettings().isShowTransactables() && buttonID.startsWith("transactWith_")) {
                    BufferedImage image = TransactionGenerator.drawTransactableStuffImage(player, p2);
                    FileUpload upload = FileUploadService.createFileUpload(image, "transactable_items");
                    MessageHelper.sendFileUploadToChannel(event.getMessageChannel(), upload);
                }
            }
            String message = player.getRepresentation(true, false) + ", please choose what you wish to transact with "
                    + p2.getRepresentation(false, false) + ".";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            checkTransactionLegality(game, player, p2);
            ButtonHelper.deleteMessage(event);
        }
    }
}
