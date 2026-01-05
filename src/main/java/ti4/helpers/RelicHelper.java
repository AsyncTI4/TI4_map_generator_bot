package ti4.helpers;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.helpers.thundersedge.TeHelperUnits;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
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
            info.append("_")
                    .append(relicData.getName())
                    .append("_: ")
                    .append(relicData.getText())
                    .append("\n");
        }
        String msg = player.getRepresentationUnfogged()
                + ", please choose the relic that you wish to draw. The relic text is reproduced for your convenience.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), info.toString());
    }

    public static void drawRelicAndNotify(Player player, GenericInteractionCreateEvent event, Game game) {
        drawRelicAndNotify(player, event, game, 0, false);
    }

    public static void drawRelicAndNotify(
            Player player, GenericInteractionCreateEvent event, Game game, int position, boolean checked) {
        if (!checked
                && (player.hasAbility("data_leak")
                        || (player.getPromissoryNotes().containsKey("dspnflor")
                                && game.getPNOwner("dspnflor") != player))) {
            drawWithAdvantage(player, game, 2);
            return;
        }
        if (player.hasAbility("a_new_edifice")) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + "Due to your **A New Edifice** ability, you get to explore 3 planets rather than get a relic. Reminder that they should be different planets. ");
            List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), player.getRepresentation() + "Explore planet #1 ", buttons);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), player.getRepresentation() + "Explore planet #2 ", buttons);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), player.getRepresentation() + "Explore planet #3 ", buttons);
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
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(), message, relicModel.getRepresentationEmbed(false, true));
        resolveRelicEffects(event, game, player, relicID);
        TeHelperUnits.serveIconoclastDeployAbility(game, player);

        if (checked) game.shuffleRelics();
    }

    public static void resolveRelicEffects(
            GenericInteractionCreateEvent event, Game game, Player player, String relicID) {
        StringBuilder helpMessage = new StringBuilder();
        // Append helpful commands after relic draws and resolve effects:
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
                helpMessage
                        .append("Custom objective _Shard of the Throne_ has been added.\n")
                        .append(player.getRepresentation())
                        .append(" scored _Shard of the Throne_.");
            }
            case "quantumcore" -> {
                String primaryBT = player.getBreakthroughID();
                if (primaryBT != null && !player.isBreakthroughUnlocked(primaryBT) && game.isThundersEdge()) {
                    BreakthroughCommandHelper.unlockAllBreakthroughs(game, player);
                }
            }
            case "thetriad" -> {
                for (Player p : game.getPlayers().values()) p.removePlanet("triad");
                player.addPlanet("triad");
                Planet triad = game.getPlanetsInfo().get("triad");
                if (triad != null) triad.updateTriadStats(player);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(), "Added the Triad \"planet\" card to your play area.");
            }

            case "absol_shardofthethrone1", "absol_shardofthethrone2", "absol_shardofthethrone3" -> {
                int absolShardNum = Integer.parseInt(StringUtils.right(relicID, 1));
                String customPOName = "Shard of the Throne (" + absolShardNum + ")";
                Integer poIndex = game.addCustomPO(customPOName, 1);
                game.scorePublicObjective(player.getUserID(), poIndex);
                helpMessage
                        .append("Custom objective _")
                        .append(customPOName)
                        .append("_ has been added.\n")
                        .append(player.getRepresentation())
                        .append(" scored _")
                        .append(customPOName)
                        .append("_.");
            }
            case "bookoflatvinia" -> {
                if (player.hasAbility("propagation")) {
                    List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                    String message2 = player.getRepresentation()
                            + ", you would research two technologies, but because of **Propagation**, you instead gain 6 command tokens."
                            + " Your current command tokens are " + player.getCCRepresentation()
                            + ". Use buttons to gain command tokens.";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons);
                    game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                } else {
                    List<String> startingTechOptions =
                            new ArrayList<>(Arrays.asList("amd", "det", "nm", "pa", "st", "sdn", "ps", "aida"));
                    if (game.isTwilightsFallMode()) {
                        startingTechOptions = new ArrayList<>(Arrays.asList("wavelength", "antimatter"));
                    }
                    List<TechnologyModel> techs = new ArrayList<>();
                    if (!startingTechOptions.isEmpty()) {
                        for (String tech : game.getTechnologyDeck()) {
                            TechnologyModel model = Mapper.getTech(tech);
                            boolean homebrewReplacesAnOption = model.getHomebrewReplacesID()
                                    .map(startingTechOptions::contains)
                                    .orElse(false);
                            if (startingTechOptions.contains(model.getAlias()) || homebrewReplacesAnOption) {
                                if (!player.getTechs().contains(tech)) {
                                    techs.add(model);
                                }
                            }
                        }
                    }

                    List<Button> buttons = ListTechService.getTechButtons(techs, player, "free");
                    String msg = player.getRepresentationUnfogged()
                            + ", please use the buttons to research a technology with no prerequisites:";
                    if (techs.isEmpty()) {
                        buttons = List.of(Buttons.GET_A_FREE_TECH, Buttons.DONE_DELETE_BUTTONS);
                        if (game.isTwilightsFallMode()) {
                            buttons = ButtonHelper.getGainCCButtons(player);
                            String message2 = player.getRepresentation()
                                    + ", you would research two technologies, but because you have none to research, you instead gain 4 command tokens."
                                    + " Your current command tokens are " + player.getCCRepresentation()
                                    + ". Use buttons to gain command tokens.";
                            MessageHelper.sendMessageToChannelWithButtons(
                                    player.getCorrectChannel(), message2, buttons);
                            game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                        } else {
                            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
                        }
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

    /** Meant to be called AFTER removing the relic from the player */
    public void resolveRelicLossEffects(Game game, Player p1, String relicID) {
        String shardCustomPOName = null;
        switch (relicID) {
            case "shard" -> shardCustomPOName = "Shard of the Throne";
            case "absol_shardofthethrone1", "absol_shardofthethrone2", "absol_shardofthethrone3" -> {
                int absolShardNum = Integer.parseInt(StringUtils.right(relicID, 1));
                shardCustomPOName = "Shard of the Throne (" + absolShardNum + ")";
            }
            case "thetriad" -> p1.removePlanet("triad");
            case "obsidian", "absol_obsidian" -> {
                if (p1.getSoScored() > p1.getMaxSOCount()) {
                    // do something for 4 scored secrets
                    p1.setBonusScoredSecrets(p1.getBonusScoredSecrets() + 1);
                }
            }
        }

        if (shardCustomPOName != null && game.getCustomPublicVP().containsKey(shardCustomPOName)) {
            game.unscorePublicObjective(p1.getUserID(), shardCustomPOName);
            String msg = p1.getRepresentation() + " lost 1 point due to losing _" + shardCustomPOName + "_.";
            MessageHelper.sendMessageToChannel(p1.getCorrectChannel(), msg);
        }
    }

    public void sendFrags(
            GenericInteractionCreateEvent event, Player sender, Player receiver, String trait, int count, Game game) {
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
        String fragString = count + " " + trait + " " + ExploreEmojis.getFragEmoji(trait) + " relic fragment"
                + (count == 1 ? "" : "s");
        String message = p1 + " sent " + fragString + " to " + p2;
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(receiver.getCorrectChannel(), message);
        }
        CommanderUnlockCheckService.checkPlayer(receiver, "kollecc", "bentor");

        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(receiver.getPrivateChannel(), message);

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
            text = new StringBuilder("__Relics remaining in deck__ (")
                    .append(deckCount)
                    .append(" - ")
                    .append(formatPercent.format(deckDrawChance))
                    .append("):");
            Collections.sort(allRelics);
            int x = 1;
            for (String relicId : allRelics) {
                String relicName = Mapper.getRelic(relicId).getName();
                text.append("\n")
                        .append(x)
                        .append(". ")
                        .append(ExploreEmojis.Relic)
                        .append(" _")
                        .append(relicName)
                        .append("_ ")
                        .append(Mapper.getRelic(relicId)
                                .getText()
                                .replace("\n", " ")
                                .replace("> ", ""));
                x++;
            }
        }

        if (player != null && "action".equalsIgnoreCase(game.getPhaseOfGame()) && !over && game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    "It is foggy outside, please wait until status/agenda to do this command, or override the fog.");
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
