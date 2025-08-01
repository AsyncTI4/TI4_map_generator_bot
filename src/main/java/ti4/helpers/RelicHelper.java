package ti4.helpers;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.fow.FOWPlusService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.tech.ListTechService;

@UtilityClass
public class RelicHelper {

    public static void drawWithAdvantage(Player player, Game game, int advantage) {
        List<Button> buttons = new ArrayList<>();
        List<String> relics = game.getAllRelics();
        StringBuilder info = new StringBuilder();
        for (int x = 0; x < advantage && x < relics.size(); x++) {
            RelicModel relicData = Mapper.getRelic(relics.get(x));
            buttons.add(Buttons.green("drawRelicAtPosition_" + x, relicData.getName()));
            info.append("_").append(relicData.getName()).append("_: ").append(relicData.getText()).append("\n");
        }
        String msg = player.getRepresentationUnfogged() + ", please choose the relic that you wish to draw. The relic text is reproduced for your convenience.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), info.toString());
    }

    public static void drawRelicAndNotify(Player player, GenericInteractionCreateEvent event, Game game) {
        drawRelicAndNotify(player, event, game, 0, false);
    }

    public static void drawRelicAndNotify(Player player, GenericInteractionCreateEvent event, Game game, int position, boolean checked) {
        if (!checked && (player.hasAbility("data_leak") || (player.getPromissoryNotes().containsKey("dspnflor") && game.getPNOwner("dspnflor") != player))) {
            drawWithAdvantage(player, game, 2);
            return;
        }
        if (player.hasAbility("a_new_edifice")) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                + "Due to your **A New Edifice** ability, you get to explore 3 planets rather than get a relic. Reminder that they should be different planets. ");
            List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation() + "Explore planet #1 ", buttons);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation() + "Explore planet #2 ", buttons);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation() + "Explore planet #3 ", buttons);
            return;
        }

        String relicID = game.drawRelic(position);
        if (relicID.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Relic deck is empty");
            return;
        }
        relicID = relicID.replace("extra1", "");
        relicID = relicID.replace("extra2", "");
        player.addRelic(relicID);
        RelicModel relicModel = Mapper.getRelic(relicID);

        String message = player.getRepresentation() + " drew the _" + relicModel.getName() + "_ relic.";
        if (game.isFowMode()) {
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, message);
        }
        MessageHelper.sendMessageToChannelWithEmbed(player.getCorrectChannel(), message, relicModel.getRepresentationEmbed(false, true));
        resolveRelicEffects(event, game, player, relicID);

        if (checked) game.shuffleRelics();
    }

    public static void resolveRelicEffects(GenericInteractionCreateEvent event, Game game, Player player, String relicID) {
        StringBuilder helpMessage = new StringBuilder();
        //Append helpful commands after relic draws and resolve effects:
        switch (relicID) {
            case "obsidian", "absol_obsidian" -> {
                game.drawSecretObjective(player.getUserID());

                if (game.isFowMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(game, event, player, "Drew a secret objective.");
                }

                helpMessage.append("A secret objective has been automatically drawn.");
                if (player.hasAbility("plausible_deniability")) {
                    game.drawSecretObjective(player.getUserID());
                    helpMessage.append(" Drew a second secret objective due to **Plausible Deniability**.");
                }
                SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
            }
            case "shard" -> {
                Integer poIndex = game.addCustomPO("Shard of the Throne", 1);
                game.scorePublicObjective(player.getUserID(), poIndex);
                helpMessage.append("Custom objective _Shard of the Throne_ has been added.\n")
                    .append(player.getRepresentation()).append(" scored _Shard of the Throne_.");
            }
            case "absol_shardofthethrone1", "absol_shardofthethrone2", "absol_shardofthethrone3" -> {
                int absolShardNum = Integer.parseInt(StringUtils.right(relicID, 1));
                String customPOName = "Shard of the Throne (" + absolShardNum + ")";
                Integer poIndex = game.addCustomPO(customPOName, 1);
                game.scorePublicObjective(player.getUserID(), poIndex);
                helpMessage.append("Custom objective _").append(customPOName).append("_ has been added.\n")
                    .append(player.getRepresentation()).append(" scored _").append(customPOName).append("_.");
            }
            case "bookoflatvinia" -> {
                if (player.hasAbility("propagation")) {
                    List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                    String message2 = player.getRepresentation()
                        + ", you would research two technologies, but because of **Propagation**, you instead gain 6 command tokens."
                        + " Your current command tokens are " + player.getCCRepresentation()
                        + ". Use buttons to gain command tokens.";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        message2, buttons);
                    game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                } else {
                    List<String> startingTechOptions = new ArrayList<>(Arrays.asList("amd", "det", "nm", "pa", "st", "sdn", "ps", "aida"));
                    List<TechnologyModel> techs = new ArrayList<>();
                    if (!startingTechOptions.isEmpty()) {
                        for (String tech : game.getTechnologyDeck()) {
                            TechnologyModel model = Mapper.getTech(tech);
                            boolean homebrewReplacesAnOption = model.getHomebrewReplacesID().map(startingTechOptions::contains).orElse(false);
                            if (startingTechOptions.contains(model.getAlias()) || homebrewReplacesAnOption) {
                                if (!player.getTechs().contains(tech)) {
                                    techs.add(model);
                                }
                            }
                        }
                    }

                    List<Button> buttons = ListTechService.getTechButtons(techs, player, "free");
                    String msg = player.getRepresentationUnfogged() + ", please use the buttons to research a technology with no prerequisites:";
                    if (techs.isEmpty()) {
                        buttons = List.of(Buttons.GET_A_FREE_TECH, Buttons.DONE_DELETE_BUTTONS);
                        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
                    } else {
                        for (int x = 0; x < 2; x++) {
                            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
                        }
                    }
                }

            }
        }

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), helpMessage.toString());
        Helper.checkEndGame(game, player);
    }

    public static void offerInitialNeuraLoopChoice(Game game, String poID) {
        for (Player player : game.getRealPlayers()) {
            if (player.hasRelic("neuraloop")) {
                String name = "";
                if (Mapper.getPublicObjective(poID) != null) {
                    name = Mapper.getPublicObjective(poID).getName();
                } else {
                    if (Mapper.getSecretObjective(poID) != null) {
                        name = Mapper.getSecretObjective(poID).getName();
                    } else {
                        name = poID;
                    }
                }
                String msg = player.getRepresentation() + " you have the opportunity to use the _Neuraloop_ relic to replace the objective " + name
                    + " with a random objective from __any__ of the objective decks. Doing so will cause you to purge one of your relics."
                    + " Use buttons to decide which objective deck, if any, you wish to draw the new objective from..";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.gray("neuraloopPart1;" + poID + ";stage1", "Replace with Stage 1", CardEmojis.Public1));
                buttons.add(Buttons.gray("neuraloopPart1;" + poID + ";stage2", "Replace with Stage 2", CardEmojis.Public2));
                buttons.add(Buttons.gray("neuraloopPart1;" + poID + ";secret", "Replace with Secret Objective", CardEmojis.SecretObjective));
                buttons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
            }
        }
    }

    public static List<Button> getNeuraLoopButton(Player player, String poID, String type, Game game) {
        List<Button> buttons = new ArrayList<>();

        for (String relic : player.getRelics()) {
            if (Mapper.getRelic(relic) == null || Mapper.getRelic(relic).isFakeRelic()) {
                continue;
            }
            buttons.add(Buttons.gray("neuraloopPart2;" + poID + ";" + type + ";" + relic, Mapper.getRelic(relic).getName()));
        }
        return buttons;
    }

    public void sendFrags(GenericInteractionCreateEvent event, Player sender, Player receiver, String trait, int count, Game game) {
        List<String> fragments = new ArrayList<>();
        for (String cardID : sender.getFragments()) {
            ExploreModel card = Mapper.getExplore(cardID);
            if (card.getType().equalsIgnoreCase(trait)) {
                fragments.add(cardID);
            }
        }

        if (fragments.size() >= count) {
            for (int i = 0; i < count; i++) {
                String fragID = fragments.get(i);
                sender.removeFragment(fragID);
                receiver.addFragment(fragID);
            }
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Not enough fragments of the specified trait");
            return;
        }

        String p1 = sender.getRepresentation();
        String p2 = receiver.getRepresentation();
        String fragString = count + " " + trait + " " + ExploreEmojis.getFragEmoji(trait) + " relic fragment" + (count == 1 ? "" : "s");
        String message = p1 + " sent " + fragString + " to " + p2;
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(receiver.getCorrectChannel(), message);
        }
        CommanderUnlockCheckService.checkPlayer(receiver, "kollecc", "bentor");

        if (game.isFowMode()) {
            String fail = "User for faction not found. Report to ADMIN";
            String success = "The other player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(receiver, game, event, message, fail, success);

            // Add extra message for transaction visibility
            FoWHelper.pingPlayersTransaction(game, event, sender, receiver, fragString, null);
        }
        TransactionHelper.checkTransactionLegality(game, sender, receiver);
        CommanderUnlockCheckService.checkPlayer(receiver, "kollecc");
    }

    public static void showRemaining(MessageChannel channel, boolean over, Game game, Player player) {
        if (!FOWPlusService.deckInfoAvailable(player, game)) {
            return;
        }

        List<String> allRelics = new ArrayList<>(game.getAllRelics());

        Integer deckCount = allRelics.size();
        Double deckDrawChance = deckCount == 0 ? 0.0 : 1.0 / deckCount;
        NumberFormat formatPercent = NumberFormat.getPercentInstance();
        formatPercent.setMaximumFractionDigits(1);
        StringBuilder text;
        if (allRelics.isEmpty()) {
            text = new StringBuilder("There are no more cards in the relic deck.");
        } else {
            text = new StringBuilder("__Relics remaining in deck__ (").append(deckCount).append(" - ").append(formatPercent.format(deckDrawChance)).append("):");
            Collections.sort(allRelics);
            for (String relicId : allRelics) {
                String relicName = Mapper.getRelic(relicId).getName();
                text.append("\n1. ").append(ExploreEmojis.Relic).append(" _").append(relicName).append("_");
            }
        }

        if (player != null && "action".equalsIgnoreCase(game.getPhaseOfGame()) && !over && game.isFowMode()) {
            MessageHelper.sendMessageToChannel(channel, "It is foggy outside, please wait until status/agenda to do this command, or override the fog.");
        } else {
            MessageHelper.sendMessageToChannel(channel, text.toString());
        }
    }

    public static String sillySpelling() {
        StringBuilder empelar = new StringBuilder("Scepter of E");
        List<Character> letters = Arrays.asList('m', 'e', 'l', 'p', 'a');
        Collections.shuffle(letters);
        for (Character c : letters) {
            empelar.append(c);
        }
        empelar.append("r");
        return empelar.toString();
    }
}
