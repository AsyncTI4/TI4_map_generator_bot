package ti4.service.objectives;

import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.image.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.info.ListPlayerInfoService;

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
                        + poName + ", The bot has you at " + playerProgress + "/" + threshold
                        + ". If this is a mistake, please report and then you can manually score via /status po_score with the number ID of "
                        + poID);
                return;
            }
        }
        boolean scored = game.scorePublicObjective(player.getUserID(), poID);
        if (!scored) {
            MessageHelper.sendMessageToChannel(channel,
                player.getFactionEmoji() + "No such Public Objective ID found or already scored, please retry");
        } else {
            informAboutScoring(event, channel, game, player, poID);
            for (Player p2 : player.getNeighbouringPlayers()) {
                if (p2.hasLeaderUnlocked("syndicatecommander")) {
                    p2.setTg(p2.getTg() + 1);
                    String msg = p2.getRepresentationUnfogged()
                        + " you gained 1TG due to your neighbor scoring a PO while you have Fillipo Rois, the Tnelis commander. Your TGs went from "
                        + (p2.getTg() - 1) + " -> " + p2.getTg();
                    MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg);
                    ButtonHelperAbilities.pillageCheck(p2, game);
                    ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
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
            emojiName = Emojis.Public1alt;
        } else if (poName2 != null) {
            poName = poName2;
            emojiName = Emojis.Public2alt;
        }
        return poName + "_" + emojiName;
    }

    public static void informAboutScoring(GenericInteractionCreateEvent event, MessageChannel channel, Game game,
        Player player, int poID) {
        String both = getNameNEMoji(game, poID);
        String poName = both.split("_")[0];
        String emojiName = both.split("_")[1];

        String message = player.getRepresentation() + " scored " + emojiName + " __**" + poName + "**__";
        MessageHelper.sendMessageToChannel(channel, message);
        if (game.isFowMode()) {
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, message);
        }
        Helper.checkIfHeroUnlocked(game, player);
        if (poName.toLowerCase().contains("sway the council") || poName.toLowerCase().contains("erect a monument")
            || poName.toLowerCase().contains("found a golden age")
            || poName.toLowerCase().contains("amass wealth")
            || poName.toLowerCase().contains("manipulate galactic law")
            || poName.toLowerCase().contains("hold vast reserves")) {
            String message2 = player.getRepresentationUnfogged()
                + " Click the names of the planets you wish to exhaust to score the objective.";
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
                    player.getRepresentation() + " Automatically deducted 5TGs (" + oldtg + "->" + player.getTg()
                        + ")");
            } else {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Didn't deduct 5TGs because you don't have 5TGs.");
            }
        }
        if (poName.contains("Centralize Galactic Trade")) {
            int oldtg = player.getTg();
            if (oldtg > 9) {
                player.setTg(oldtg - 10);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getRepresentation() + " Automatically deducted 10TGs (" + oldtg + "->" + player.getTg()
                        + ")");
            } else {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Didn't deduct 10TGs because you don't have that much");
            }
        }
        if (poName.contains("Lead From the Front")) {
            int currentStrat = player.getStrategicCC();
            int currentTact = player.getTacticalCC();
            if (currentStrat + currentTact > 2) {
                if (currentStrat > 2) {
                    for (int x = 0; x < 3; x++) {
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "Scored " + Emojis.Public1 + " Lead from the Front");
                    }
                    player.setStrategicCC(currentStrat - 3);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " Automatically deducted 3 strategy CCs (" + currentStrat + "->" + player.getStrategicCC() + ")");
                } else {
                    String currentCC = player.getCCRepresentation();
                    int subtract = 3 - currentStrat;
                    for (int x = 0; x < currentStrat; x++) {
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "Scored " + Emojis.Public1 + " Lead from the Front");
                    }
                    player.setStrategicCC(0);
                    player.setTacticalCC(currentTact - subtract);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " Automatically deducted 3 strategy/tactic CCs (" + currentCC + "->" + player.getCCRepresentation() + ")");
                }
            } else {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Did not deduct 3 CCs because you didn't have that");
            }
        }
        if (poName.contains("Galvanize the People")) {
            int currentStrat = player.getStrategicCC();
            int currentTact = player.getTacticalCC();
            if (currentStrat + currentTact > 5) {
                if (currentStrat > 5) {
                    for (int x = 0; x < 6; x++) {
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "Scored " + Emojis.Public2 + " Galvanize the People");
                    }
                    player.setStrategicCC(currentStrat - 6);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " Automatically deducted 6 strategy CCs (" + currentStrat + "->" + player.getStrategicCC() + ")");
                } else {
                    String currentCC = player.getCCRepresentation();
                    int subtract = 6 - currentStrat;
                    for (int x = 0; x < currentStrat; x++) {
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "Scored " + Emojis.Public2 + " Galvanize the People");
                    }
                    player.setStrategicCC(0);
                    player.setTacticalCC(currentTact - subtract);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " Automatically deducted 6 strategy/tactic CCs (" + currentCC + "->" + player.getCCRepresentation() + ")");
                }
            } else {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Did not deduct 6 CCs because you didn't have that");
            }
        }
    }
}
