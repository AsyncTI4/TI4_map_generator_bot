package ti4.service.objectives;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.BreakthroughHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.info.ListPlayerInfoService;
import ti4.service.leader.HeroUnlockCheckService;

@UtilityClass
public class ScorePublicObjectiveService {

    public static void scorePO(
            GenericInteractionCreateEvent event, MessageChannel channel, Game game, Player player, int poID) {
        String both = getNameNEMoji(game, poID);
        String poName = both.split("_")[0];
        channel = player.getCorrectChannel();
        String id = "";
        Map<String, Integer> revealedPublicObjectives = game.getRevealedPublicObjectives();
        for (Map.Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
            if (po.getValue().equals(poID)) {
                id = po.getKey();
                break;
            }
        }
        if (Mapper.getPublicObjective(id) != null && event instanceof ButtonInteractionEvent) {
            int threshold = ListPlayerInfoService.getObjectiveThreshold(id, game);
            int playerProgress = ListPlayerInfoService.getPlayerProgressOnObjective(id, game, player);
            if (playerProgress < threshold) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getFactionEmoji() + ", the bot does not believe you meet the requirements to score "
                                + poName + ". The bot has you at " + playerProgress + "/" + threshold
                                + ". If this is a mistake, please report and then you can manually score via `/status po_score` with the number ID of `"
                                + poID + "`.");
                return;
            }
        }
        boolean scored = game.scorePublicObjective(player.getUserID(), poID);
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            game.setStoredValue(player.getFaction() + "round" + game.getRound() + "PO", poName);
        }
        if (!scored) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    player.getFactionEmoji() + ", no such public objective ID found, or already scored, please retry.");
        } else {
            informAboutScoring(event, channel, game, player, poID);
            for (Player p2 : player.getNeighbouringPlayers(true)) {
                if (p2.hasLeaderUnlocked("syndicatecommander")) {
                    p2.setTg(p2.getTg() + 1);
                    String msg = p2.getRepresentationUnfogged() + ", you gained 1 trade good"
                            + " due to your neighbor scoring a public objective while you have Fillipo Rois, the Tnelis commander."
                            + " Your trade goods went from " + (p2.getTg() - 1) + " -> " + p2.getTg() + ".";
                    MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg);
                    ButtonHelperAbilities.pillageCheck(p2, game);
                    ButtonHelperAgents.resolveArtunoCheck(player, 1);
                }
            }
        }
        Helper.checkEndGame(game, player);
    }

    private static String getNameNEMoji(Game game, int poID) {
        String id = "";
        Map<String, Integer> revealedPublicObjectives = game.getRevealedPublicObjectives();
        for (Map.Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
            if (po.getValue().equals(poID)) {
                id = po.getKey();
                break;
            }
        }
        Map<String, String> publicObjectivesState1 = Mapper.getPublicObjectivesStage1();
        Map<String, String> publicObjectivesState2 = Mapper.getPublicObjectivesStage2();
        String poName1 = publicObjectivesState1.get(id);
        String poName2 = publicObjectivesState2.get(id);
        String poName = id;
        String emojiName = "Custom";
        if (poName1 != null) {
            poName = poName1;
            emojiName = CardEmojis.Public1alt.toString();
        } else if (poName2 != null) {
            poName = poName2;
            emojiName = CardEmojis.Public2alt.toString();
        }
        return poName + "_" + emojiName;
    }

    private static void informAboutScoring(
            GenericInteractionCreateEvent event, MessageChannel channel, Game game, Player player, int poID) {
        String both = getNameNEMoji(game, poID);
        String poName = both.split("_")[0];
        String emojiName = both.split("_")[1];

        String message = player.getRepresentation() + " scored " + emojiName + " _" + poName + "_.";
        MessageHelper.sendMessageToChannel(channel, message);
        if (game.isFowMode()) {
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, message);
        }
        HeroUnlockCheckService.checkIfHeroUnlocked(game, player);
        if (player.hasAbility("dark_purpose") && !poName.toLowerCase().contains("custodian")) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " gains 1 command token from **Dark Purpose**.");
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            String message2 = player.getRepresentationUnfogged() + ", your current command tokens are "
                    + player.getCCRepresentation() + ". Use buttons to gain 1 command token.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons);
        }
        if (player.hasTech("tf-yinascendant") && !poName.toLowerCase().contains("custodian")) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.getRepresentation() + " gains 1 card due to Yin Ascendant.");
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("drawSingularNewSpliceCard_ability", "Draw 1 Ability"));
            buttons.add(Buttons.green("drawSingularNewSpliceCard_units", "Draw 1 Unit Upgrade"));
            buttons.add(Buttons.green("drawSingularNewSpliceCard_genome", "Draw 1 Genome"));
            buttons.add(Buttons.red("deleteButtons", "Done resolving"));
            String message2 = player.getRepresentationUnfogged() + " use buttons to resolve.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons);
        }
        if (!poName.toLowerCase().contains("custodian")
                && (player.hasAbility("yin_breakthrough") || player.hasUnlockedBreakthrough("yinbt"))) {
            BreakthroughHelper.resolveYinBreakthroughAbility(game, player);
        }
        String idC = "";
        for (Entry<String, Integer> po : game.getRevealedPublicObjectives().entrySet()) {
            if (po.getValue().equals(poID)) {
                idC = po.getKey();
                break;
            }
        }
        if (idC.equalsIgnoreCase(game.getStoredValue("toldarHeroObj"))) {
            if (!game.getStoredValue("toldarHeroPlayer").equalsIgnoreCase(player.getFaction())) {
                Player p2 = game.getPlayerFromColorOrFaction(game.getStoredValue("toldarHeroPlayer"));
                MessageHelper.sendMessageToChannel(
                        p2.getCorrectChannel(),
                        p2.getRepresentation() + " gains 2 command tokens due to their Concord Renewed hero ability.");
                List<Button> buttons = ButtonHelper.getGainCCButtons(p2);
                String message2 = p2.getRepresentationUnfogged() + ", your current command tokens are "
                        + p2.getCCRepresentation() + ". Use buttons to gain 2 command tokens.";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message2, buttons);
            }
        }
        if (player.hasAbility("reflect")) {

            List<String> scoredPlayerList =
                    game.getScoredPublicObjectives().computeIfAbsent(idC, key -> new ArrayList<>());
            if (scoredPlayerList.size() > 1 && Mapper.getPublicObjective(idC) != null) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation()
                                + " is drawing 1 action card due to scoring an objective someone else already scored while having the _Reflect_ Honor card.");
                game.drawActionCard(player.getUserID());
                ButtonHelper.checkACLimit(game, player);
                ActionCardHelper.sendActionCardInfo(game, player, event);
            }
        }
        if (game.isOmegaPhaseMode()) {
            // Omega Phase objectives require you to have, not spend. Skip all the spending checks.
            return;
        }
        if (poName.toLowerCase().contains("sway the council")
                || poName.toLowerCase().contains("erect a monument")
                || poName.toLowerCase().contains("found a golden age")
                || poName.toLowerCase().contains("amass wealth")
                || poName.toLowerCase().contains("manipulate galactic law")
                || poName.toLowerCase().contains("hold vast reserves")) {
            String message2 = player.getRepresentationUnfogged()
                    + ", please choose the planets you wish to exhaust to score the objective.";
            if (player.hasLeaderUnlocked("xxchahero")
                    && (poName.toLowerCase().contains("amass wealth")
                            || poName.toLowerCase().contains("hold vast reserves"))) {
                message2 +=
                        "\n-# NB: Xxekir Grom , the Xxcha hero, will allow you to use the combined values of each planet for"
                                + " __either__ the resource or influence requirement of this objective, but __not__ both.";
            }
            List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "both");
            Button DoneExhausting = Buttons.red("deleteButtons", "Done Exhausting Planets");
            buttons.add(DoneExhausting);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons);
        }
        if (poName.contains("Negotiate Trade Routes")) {
            int oldtg = player.getTg();
            if (oldtg > 4) {
                player.setTg(oldtg - 5);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation() + ", automatically deducted 5 trade goods (" + oldtg + "->"
                                + player.getTg() + ").");
            } else {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        "Didn't deduct 5 trade goods because you don't have 5 trade goods.");
            }
        }
        if (poName.contains("Centralize Galactic Trade")) {
            int oldtg = player.getTg();
            if (oldtg > 9) {
                player.setTg(oldtg - 10);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation() + ", automatically deducted 10 trade goods (" + oldtg + "->"
                                + player.getTg() + ").");
            } else {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        "Didn't deduct 10 trade goods because you don't have 10 trade goods.");
            }
        }
        if (poName.contains("Lead From the Front")) {
            int currentStrat = player.getStrategicCC();
            int requiredSpend = 3;
            if (player.hasRelicReady("emelpar")) {
                requiredSpend--;
            }
            int currentTact = player.getTacticalCC();
            if (currentStrat + currentTact >= requiredSpend) {
                if (currentStrat >= requiredSpend) {
                    for (int x = 0; x < requiredSpend; x++) {
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(
                                player, game, event, "scored " + CardEmojis.Public1 + " _Lead from the Front_.");
                    }
                    player.setStrategicCC(currentStrat - requiredSpend);
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentation()
                                    + ", 3 command tokens have automatically been deducted from your strategy pool ("
                                    + currentStrat + "->" + player.getStrategicCC() + ").");
                } else {
                    String currentCC = player.getCCRepresentation();
                    int subtract = requiredSpend - currentStrat;
                    for (int x = 0; x < currentStrat; x++) {
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(
                                player, game, event, "scored " + CardEmojis.Public1 + " _Lead from the Front_.");
                    }
                    player.setStrategicCC(0);
                    player.setTacticalCC(currentTact - subtract);
                    if (currentStrat == 0) {
                        MessageHelper.sendMessageToChannel(
                                player.getCorrectChannel(),
                                player.getRepresentation()
                                        + ", 3 command tokens have automatically been deducted from your tactic pool ("
                                        + currentCC + "->" + player.getCCRepresentation() + ")");
                    } else {
                        MessageHelper.sendMessageToChannel(
                                player.getCorrectChannel(),
                                player.getRepresentation()
                                        + ", " + subtract + " and " + currentStrat
                                        + " command tokens (3 total) have automatically been deducted from your tactic and/or strategy pools respectively ("
                                        + currentCC + "->" + player.getCCRepresentation() + ")");
                    }
                }
            } else {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation()
                                + ", you do not have 3 command tokens in your tactic and/or strategy pools. No command tokens have been removed.");
            }
        }
        if (poName.contains("Galvanize the People")) {
            int currentStrat = player.getStrategicCC();
            int currentTact = player.getTacticalCC();
            int requiredSpend = 6;
            if (player.hasRelicReady("emelpar")) {
                requiredSpend--;
            }
            if (currentStrat + currentTact >= requiredSpend) {
                if (currentStrat >= requiredSpend) {
                    for (int x = 0; x < requiredSpend; x++) {
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(
                                player, game, event, "scored " + CardEmojis.Public2 + " _Galvanize the People_.");
                    }
                    player.setStrategicCC(currentStrat - requiredSpend);
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentation()
                                    + ", 6 command tokens have automatically been deducted from your strategy pool ("
                                    + currentStrat + "->" + player.getStrategicCC() + ")");
                } else {
                    String currentCC = player.getCCRepresentation();
                    int subtract = requiredSpend - currentStrat;
                    for (int x = 0; x < currentStrat; x++) {
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(
                                player, game, event, "scored " + CardEmojis.Public2 + " _Galvanize the People_.");
                    }
                    player.setStrategicCC(0);
                    player.setTacticalCC(currentTact - subtract);
                    if (currentStrat == 0) {
                        MessageHelper.sendMessageToChannel(
                                player.getCorrectChannel(),
                                player.getRepresentation()
                                        + ", 6 command tokens have automatically been deducted from your tactic pool ("
                                        + currentCC + "->" + player.getCCRepresentation() + ")");
                    } else {
                        MessageHelper.sendMessageToChannel(
                                player.getCorrectChannel(),
                                player.getRepresentation()
                                        + ", " + subtract + " and " + currentStrat
                                        + " command tokens (6 total) have automatically been deducted from your tactic and/or strategy pools respectively ("
                                        + currentCC + "->" + player.getCCRepresentation() + ")");
                    }
                }
            } else {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation()
                                + ", you do not have 6 command tokens in your tactic and/or strategy pools. No command tokens have been removed.");
            }
        }
    }
}
