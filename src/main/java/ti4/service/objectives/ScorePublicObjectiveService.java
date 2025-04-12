package ti4.service.objectives;

import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
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
import ti4.service.unit.AddUnitService;

@UtilityClass
public class ScorePublicObjectiveService {

    public static void scorePO(GenericInteractionCreateEvent event, MessageChannel channel, Game game, Player player, int poID) {
        String both = getNameNEMoji(game, poID);
        String poName = both.split("_")[0];
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
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getFactionEmoji() + " the bot does not believe you meet the requirements to score "
                        + poName + ". The bot has you at " + playerProgress + "/" + threshold
                        + ". If this is a mistake, please report and then you can manually score via `/status po_score` with the number ID of `"
                        + poID + "`.");
                return;
            }
        }
        boolean scored = game.scorePublicObjective(player.getUserID(), poID);
        if (!game.getPhaseOfGame().equalsIgnoreCase("action")) {
            game.setStoredValue(player.getFaction() + "round" + game.getRound() + "PO", poName);
        }
        if (!scored) {
            MessageHelper.sendMessageToChannel(channel,
                player.getFactionEmoji() + "No such public objective ID found, or already scored, please retry.");
        } else {
            informAboutScoring(event, channel, game, player, poID);
            for (Player p2 : player.getNeighbouringPlayers(true)) {
                if (p2.hasLeaderUnlocked("syndicatecommander")) {
                    p2.setTg(p2.getTg() + 1);
                    String msg = p2.getRepresentationUnfogged() + " you gained 1 trade good"
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

    public static String getNameNEMoji(Game game, int poID) {
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

    public static void informAboutScoring(
        GenericInteractionCreateEvent event, MessageChannel channel, Game game,
        Player player, int poID
    ) {
        String both = getNameNEMoji(game, poID);
        String poName = both.split("_")[0];
        String emojiName = both.split("_")[1];

        String message = player.getRepresentation() + " scored " + emojiName + " _" + poName + "_.";
        MessageHelper.sendMessageToChannel(channel, message);
        if (game.isFowMode()) {
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, message);
        }
        HeroUnlockCheckService.checkIfHeroUnlocked(game, player);
        if (player.hasAbility("dark_purpose")) {
            AddUnitService.addUnits(event, player.getNomboxTile(), game, player.getColor(), "2 infantry");
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " captured 2 infantry due to scoring an objective while having the Dark Purpose ability");
        }
        if (poName.toLowerCase().contains("sway the council") || poName.toLowerCase().contains("erect a monument")
            || poName.toLowerCase().contains("found a golden age")
            || poName.toLowerCase().contains("amass wealth")
            || poName.toLowerCase().contains("manipulate galactic law")
            || poName.toLowerCase().contains("hold vast reserves")) {
            String message2 = player.getRepresentationUnfogged()
                + ", please choose the planets you wish to exhaust to score the objective.";
            if (player.hasLeaderUnlocked("xxchahero") && (poName.toLowerCase().contains("amass wealth")
                || poName.toLowerCase().contains("hold vast reserves"))) {
                message2 += "\n-# NB: Xxekir Grom , the Xxcha hero, will allow you to use the combined values of each planet for"
                    + " __either__ the resource or influence requirement of this objective, but __not__ both.";
            }
            List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "both");
            Button DoneExhausting = Buttons.red("deleteButtons", "Done Exhausting Planets");
            buttons.add(DoneExhausting);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2,
                buttons);
        }
        if (poName.contains("Negotiate Trade Routes")) {
            int oldtg = player.getTg();
            if (oldtg > 4) {
                player.setTg(oldtg - 5);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getRepresentation() + ", automatically deducted 5 trade goods (" + oldtg
                        + "->" + player.getTg() + ").");
            } else {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Didn't deduct 5 trade goods because you don't have 5 trade goods.");
            }
        }
        if (poName.contains("Centralize Galactic Trade")) {
            int oldtg = player.getTg();
            if (oldtg > 9) {
                player.setTg(oldtg - 10);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getRepresentation() + " Automatically deducted 10 trade goods (" + oldtg
                        + "->" + player.getTg() + ")");
            } else {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Didn't deduct 10 trade goods because you don't have 10 trade goods.");
            }
        }
        if (poName.contains("Lead From the Front")) {
            int currentStrat = player.getStrategicCC();
            int currentTact = player.getTacticalCC();
            if (currentStrat + currentTact >= 3) {
                if (currentStrat >= 3) {
                    for (int x = 0; x < 3; x++) {
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "Scored " + CardEmojis.Public1 + " _Lead from the Front_.");
                    }
                    player.setStrategicCC(currentStrat - 3);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                        + ", 3 command tokens have automatically been deducted from your strategy pool (" + currentStrat + "->" + player.getStrategicCC() + ").");
                } else {
                    String currentCC = player.getCCRepresentation();
                    int subtract = 3 - currentStrat;
                    for (int x = 0; x < currentStrat; x++) {
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "Scored " + CardEmojis.Public1 + " _Lead from the Front_.");
                    }
                    player.setStrategicCC(0);
                    player.setTacticalCC(currentTact - subtract);
                    if (currentStrat == 0) {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                            + ", 3 command tokens have automatically been deducted from your tactic pool ("
                            + currentCC + "->" + player.getCCRepresentation() + ")");
                    } else {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                            + ", " + subtract + " and " + currentStrat + " command tokens (3 total) have automatically been deducted from your tactic and/or strategy pools respectively ("
                            + currentCC + "->" + player.getCCRepresentation() + ")");
                    }
                }
            } else {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                    + ", you do not have 3 command tokens in your tactic and/or strategy pools. No command tokens have been removed.");
            }
        }
        if (poName.contains("Galvanize the People")) {
            int currentStrat = player.getStrategicCC();
            int currentTact = player.getTacticalCC();
            if (currentStrat + currentTact >= 6) {
                if (currentStrat >= 6) {
                    for (int x = 0; x < 6; x++) {
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "Scored " + CardEmojis.Public2 + " _Galvanize the People_.");
                    }
                    player.setStrategicCC(currentStrat - 6);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                        + ", 6 command tokens have automatically been deducted from your strategy pool (" + currentStrat + "->" + player.getStrategicCC() + ")");
                } else {
                    String currentCC = player.getCCRepresentation();
                    int subtract = 6 - currentStrat;
                    for (int x = 0; x < currentStrat; x++) {
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "Scored " + CardEmojis.Public2 + " _Galvanize the People_.");
                    }
                    player.setStrategicCC(0);
                    player.setTacticalCC(currentTact - subtract);
                    if (currentStrat == 0) {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                            + ", 6 command tokens have automatically been deducted from your tactic pool ("
                            + currentCC + "->" + player.getCCRepresentation() + ")");
                    } else {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                            + ", " + subtract + " and " + currentStrat + " command tokens (6 total) have automatically been deducted from your tactic and/or strategy pools respectively ("
                            + currentCC + "->" + player.getCCRepresentation() + ")");
                    }
                }
            } else {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                    + ", you do not have 6 command tokens in your tactic and/or strategy pools. No command tokens have been removed.");
            }
        }
    }
}
