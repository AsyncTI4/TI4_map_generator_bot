package ti4.service.turn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.collections4.ListUtils;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.buttons.Buttons;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.async.RoundSummaryHelper;
import ti4.image.BannerGenerator;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.info.ListPlayerInfoService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
public class EndTurnService {

    public static Player findNextUnpassedPlayer(Game game, Player currentPlayer) {
        List<Player> turnOrder = game.getActionPhaseTurnOrder();
        if (turnOrder.isEmpty()) {
            return null;
        }
        while (!turnOrder.getLast().equals(currentPlayer))
            Collections.rotate(turnOrder, 1);
        for (Player p : turnOrder) {
            if (!p.isPassed() && !p.isEliminated()) {
                return p;
            }
        }
        return null;
    }

    public static void endTurnAndUpdateMap(GenericInteractionCreateEvent event, Game game, Player player) {
        pingNextPlayer(event, game, player);
        CommanderUnlockCheckService.checkPlayer(player, "naaz");
        if (!game.isFowMode()) {
            ButtonHelper.updateMap(game, event, "End of Turn " + player.getInRoundTurnCount() + ", Round " + game.getRound() + " for " + player.getRepresentationNoPing() + ".");
        }
    }

    public static void pingNextPlayer(GenericInteractionCreateEvent event, Game game, Player mainPlayer) {
        pingNextPlayer(event, game, mainPlayer, false);
    }

    public static void pingNextPlayer(GenericInteractionCreateEvent event, Game game, Player mainPlayer, boolean justPassed) {
        game.setStoredValue("lawsDisabled", "no");
        game.removeStoredValue("endTurnWhenSCFinished");
        game.removeStoredValue("fleetLogWhenSCFinished");
        CommanderUnlockCheckService.checkPlayer(mainPlayer, "sol", "hacan");
        for (Player player : game.getRealPlayers()) {
            for (Player player_ : game.getRealPlayers()) {
                if (player_ == player) {
                    continue;
                }
                String key = player.getFaction() + "whisperHistoryTo" + player_.getFaction();
                if (!game.getStoredValue(key).isEmpty()) {
                    game.setStoredValue(key, "");
                }
            }
        }
        game.setStoredValue("mahactHeroTarget", "");
        game.setActiveSystem("");
        game.setStoredValue("possiblyUsedRift", "");
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(mainPlayer.getPrivateChannel(), "_ _\n"
                + "**End of Turn " + mainPlayer.getInRoundTurnCount() + ", Round " + game.getRound() + " for** " + mainPlayer.getRepresentation());
        } else {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), mainPlayer.getRepresentation(true, false) + " ended turn.");
        }

        MessageChannel gameChannel = game.getMainGameChannel() == null ? event.getMessageChannel() : game.getMainGameChannel();

        //MAKE ALL NON-REAL PLAYERS PASSED
        for (Player player : game.getPlayers().values()) {
            if (!player.isRealPlayer()) {
                player.setPassed(true);
            }
        }

        if (game.getPlayers().values().stream().allMatch(Player::isPassed)) {
            if (mainPlayer.getSecretsUnscored().containsKey("pe")) {
                MessageHelper.sendMessageToChannel(mainPlayer.getCardsInfoThread(),
                    "You were the last player to pass, and so you can score _Prove Endurance_.");
            }
            showPublicObjectivesWhenAllPassed(event, game, gameChannel);
            game.updateActivePlayer(null);
            ButtonHelperAgents.checkForEdynAgentPreset(game, mainPlayer, mainPlayer, event);
            ButtonHelperAgents.checkForEdynAgentActive(game, event);
            return;
        }

        Player nextPlayer = findNextUnpassedPlayer(game, mainPlayer);
        if (!game.isFowMode()) {
            GameMessageManager
                .remove(game.getName(), GameMessageType.TURN)
                .ifPresent(messageId -> game.getMainGameChannel().deleteMessageById(messageId).queue());
        }
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(game);
        if (isFowPrivateGame) {
            FoWHelper.pingAllPlayersWithFullStats(game, event, mainPlayer, "ended turn");
        }
        ButtonHelper.checkFleetInEveryTile(mainPlayer, game, event);
        if (mainPlayer != nextPlayer) {
            ButtonHelper.checkForPrePassing(game, mainPlayer);
        }
        CommanderUnlockCheckService.checkPlayer(nextPlayer, "sol");
        if (justPassed) {
            if (!ButtonHelperAgents.checkForEdynAgentPreset(game, mainPlayer, nextPlayer, event)) {
                StartTurnService.turnStart(event, game, nextPlayer);
            }
        } else {
            if (!ButtonHelperAgents.checkForEdynAgentActive(game, event)) {
                StartTurnService.turnStart(event, game, nextPlayer);
            }
        }

    }

    public static List<Button> getScoreObjectiveButtons(Game game) {
        return getScoreObjectiveButtons(game, "");
    }

    public static List<Button> getScoreObjectiveButtons(Game game, String prefix) {
        Map<String, Integer> revealedPublicObjectives = game.getRevealedPublicObjectives();
        Map<String, String> publicObjectivesState1 = Mapper.getPublicObjectivesStage1();
        Map<String, String> publicObjectivesState2 = Mapper.getPublicObjectivesStage2();
        Map<String, Integer> customPublicVP = game.getCustomPublicVP();
        List<Button> poButtons = new ArrayList<>();
        List<Button> poButtons1 = new ArrayList<>();
        List<Button> poButtons2 = new ArrayList<>();
        List<Button> poButtonsCustom = new ArrayList<>();
        int poStatus;
        for (Map.Entry<String, Integer> objective : revealedPublicObjectives.entrySet()) {
            String key = objective.getKey();
            String po_name = publicObjectivesState1.get(key);
            poStatus = 0;
            if (po_name == null) {
                po_name = publicObjectivesState2.get(key);
                poStatus = 1;
            }
            if (po_name == null) {
                Integer integer = customPublicVP.get(key);
                if (integer != null && !key.toLowerCase().contains("custodian") && !key.toLowerCase().contains("imperial")
                    && !key.contains("Shard of the Throne")) {
                    po_name = key;
                    poStatus = 2;
                }
            }
            if (po_name != null) {
                Integer value = objective.getValue();
                Button objectiveButton;
                if (poStatus == 0) { //Stage 1 Objectives
                    objectiveButton = Buttons.green(prefix + Constants.PO_SCORING + value, "(" + value + ") " + po_name, CardEmojis.Public1alt);
                    poButtons1.add(objectiveButton);
                } else if (poStatus == 1) { //Stage 2 Objectives
                    objectiveButton = Buttons.blue(prefix + Constants.PO_SCORING + value, "(" + value + ") " + po_name, CardEmojis.Public2alt);
                    poButtons2.add(objectiveButton);
                } else { //Other Objectives
                    objectiveButton = Buttons.gray(prefix + Constants.PO_SCORING + value, "(" + value + ") " + po_name);
                    poButtonsCustom.add(objectiveButton);
                }
            }
        }

        poButtons.addAll(poButtons1);
        poButtons.addAll(poButtons2);
        poButtons.addAll(poButtonsCustom);
        for (Player player : game.getRealPlayers()) {
            if (game.playerHasLeaderUnlockedOrAlliance(player, "edyncommander") && !game.isFowMode()) {
                poButtons.add(Buttons.gray("edynCommanderSODraw", "Draw Secret Objective Instead of Scoring Public Objective", FactionEmojis.edyn));
                break;
            }
        }
        poButtons.removeIf(Objects::isNull);
        return poButtons;
    }

    public static void showPublicObjectivesWhenAllPassed(GenericInteractionCreateEvent event, Game game, MessageChannel gameChannel) {
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "All players have passed.");
        if (game.isShowBanners()) {
            BannerGenerator.drawPhaseBanner("status", game.getRound(), game.getActionsChannel());
        }
        String messageText = "Please score objectives, " + game.getPing() + ".";

        if (!game.isFowMode()) {
            game.setStoredValue("newStatusScoringMode", "Yes");
            messageText += "\n\n" + Helper.getNewStatusScoringRepresentation(game);
        }

        game.setPhaseOfGame("statusScoring");
        game.setStoredValue("startTimeOfRound" + game.getRound() + "StatusScoring", System.currentTimeMillis() + "");
        for (Player player : game.getRealPlayers()) {
            SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
            List<String> relics = new ArrayList<>(player.getRelics());
            for (String relic : relics) {
                if (player.getExhaustedRelics().contains(relic) && relic.contains("axisorder")) {
                    player.removeRelic(relic);
                }
            }
        }
        Player vaden = Helper.getPlayerFromAbility(game, "binding_debts");
        if (vaden != null) {
            for (Player p2 : vaden.getNeighbouringPlayers(true)) {
                if (p2.getTg() > 0 && vaden.getDebtTokenCount(p2.getColor()) > 0) {
                    String msg = p2.getRepresentationUnfogged() + " you have the opportunity to pay off **Binding Debts** here."
                        + " You may pay 1 trade good to get 2 debt tokens forgiven.";
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green("bindingDebtsRes_" + vaden.getFaction(), "Pay 1 Trade Good"));
                    buttons.add(Buttons.red("deleteButtons", "Stay Indebted"));
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
                }
            }
        }
        List<Button> poButtons = getScoreObjectiveButtons(game);
        Button noPOScoring = Buttons.red(Constants.PO_NO_SCORING, "No Public Objective Scored");
        Button noSOScoring = Buttons.red(Constants.SO_NO_SCORING, "No Secret Objective Scored");
        Button scoreAnObjective = Buttons.blue("get_so_score_buttons", "Score A Secret Objective");
        poButtons.add(noPOScoring);
        poButtons.add(scoreAnObjective);
        poButtons.add(noSOScoring);
        if (!game.getStoredValue("newStatusScoringMode").isEmpty()) {
            poButtons.add(Buttons.gray("refreshStatusSummary", "Refresh Summary"));
        }
        if (game.getActionCards().size() > 130 && game.getPlayerFromColorOrFaction("hacan") != null
            && !ButtonHelper.getButtonsToSwitchWithAllianceMembers(game.getPlayerFromColorOrFaction("hacan"), game, false).isEmpty()) {
            poButtons.add(Buttons.gray("getSwapButtons_", "Swap"));
        }
        poButtons.removeIf(Objects::isNull);
        List<List<Button>> partitions = ListUtils.partition(poButtons, 5);
        List<ActionRow> actionRows = new ArrayList<>();
        for (List<Button> partition : partitions) {
            actionRows.add(ActionRow.of(partition));
        }
        MessageCreateData messageObject = new MessageCreateBuilder()
            .addContent(messageText)
            .addComponents(actionRows).build();

        gameChannel.sendMessage(messageObject).queue(message -> GameMessageManager.replace(game.getName(), message.getId(), GameMessageType.STATUS_SCORING, game.getLastModifiedDate()));

        int maxVP = 0;
        for (Player player : game.getRealPlayers()) {
            if (player.getTotalVictoryPoints() > maxVP) {
                maxVP = player.getTotalVictoryPoints();
            }
            if (game.playerHasLeaderUnlockedOrAlliance(player, "vadencommander")) {
                int numScoredSOs = player.getSoScored();
                int numScoredPos = player.getPublicVictoryPoints(false);
                if (numScoredPos + player.getCommodities() > player.getCommoditiesTotal()) {
                    numScoredPos = player.getCommoditiesTotal() - player.getCommodities();
                }
                player.setTg(player.getTg() + numScoredSOs);
                if (numScoredSOs > 0) {
                    ButtonHelperAbilities.pillageCheck(player, game);
                    ButtonHelperAgents.resolveArtunoCheck(player, numScoredSOs);
                }
                player.setCommodities(player.getCommodities() + numScoredPos);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " you gained " + numScoredSOs + " trade good" + (numScoredSOs == 1 ? "" : "s")
                        + " and " + numScoredPos + " commodit" + (numScoredSOs == 1 ? "y" : "ies") + " due to Komdar Borodin, the Vaden Commander.");
            }
            if (player.getPromissoryNotes().keySet().contains("dspnuyda") && !player.getPromissoryNotesOwned().contains("dspnuyda")) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + " reminder this is the window to use the Uydai promissory note");
            }
        }

        for (Player player : game.getRealPlayers()) {
            List<String> pns = new ArrayList<>(player.getPromissoryNotesInPlayArea());
            for (String pn : pns) {
                Player pnOwner = game.getPNOwner(pn);
                if (pnOwner == null || !pnOwner.isRealPlayer()) {
                    continue;
                }
                PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pn);
                if (pnModel.getText().contains("eturn this card") && (pnModel.getText().contains("start of the status phase") || pnModel.getText().contains("beginning of the status phase"))) {
                    player.removePromissoryNote(pn);
                    pnOwner.setPromissoryNote(pn);
                    PromissoryNoteHelper.sendPromissoryNoteInfo(game, pnOwner, false);
                    PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        "_" + pnModel.getName() + "_ has been returned to " + pnOwner.getRepresentationNoPing() + ".");
                }
            }
            if (player.hasTech("dsauguy") && player.getTg() > 2) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " you may use the button to pay 3 trade goods and get a technology, using _Sentient Datapool_.", List.of(Buttons.GET_A_TECH));
            }
            Leader playerLeader = player.getLeader("kyrohero").orElse(null);
            if (player.hasLeader("kyrohero") && player.getLeaderByID("kyrohero").isPresent()
                && playerLeader != null && !playerLeader.isLocked()) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("kyroHeroInitiation", "Play Kyro Hero"));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                    player.getRepresentation()
                        + " Reminder this is the window to play Speygh, the Kyro hero. You may use the buttons to start the process.",
                    buttons);
            }

            if (player.getRelics() != null && (player.hasRelic("emphidia") || player.hasRelic("absol_emphidia"))) {
                for (String pl : player.getPlanetsAllianceMode()) {
                    Tile tile = game.getTile(AliasHandler.resolveTile(pl));
                    if (tile == null) {
                        continue;
                    }
                    UnitHolder unitHolder = tile.getUnitHolders().get(pl);
                    if (unitHolder != null && unitHolder.getTokenList() != null && unitHolder.getTokenList().contains("attachment_tombofemphidia.png")) {
                        if (player.hasRelic("emphidia")) {
                            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                                player.getRepresentation() + "Reminder this is __not__ the window to use _The Crown of Emphidia_."
                                    + " You may purge _The Crown of Emphidia_ in the status homework phase, which is when buttons will appear.");
                        } else {
                            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                                player.getRepresentation() + "Reminder this is the window to use _The Crown of Emphidia_.");
                            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                                player.getRepresentation() + " You may use these buttons to resolve _The Crown of Emphidia_.", ButtonHelper.getCrownButtons());
                        }
                    }
                }
            }

            List<String> scorables = new ArrayList<>();
            for (String obbie : game.getRevealedPublicObjectives().keySet()) {
                List<String> scoredPlayerList = game.getScoredPublicObjectives().computeIfAbsent(obbie, key -> new ArrayList<>());
                if (player.isRealPlayer() && !scoredPlayerList.contains(player.getUserID()) && Mapper.getPublicObjective(obbie) != null) {
                    int threshold = ListPlayerInfoService.getObjectiveThreshold(obbie, game);
                    int playerProgress = ListPlayerInfoService.getPlayerProgressOnObjective(obbie, game, player);
                    if (playerProgress >= threshold) {
                        scorables.add(Mapper.getPublicObjective(obbie).getRepresentation(false));
                    }
                }
            }
            if (scorables.isEmpty()) {
                messageText = player.getRepresentation() + ", the bot does not believe that you can score any public objectives.";
            } else {
                if (Helper.canPlayerScorePOs(game, player)) {
                    messageText = player.getRepresentation() + ", as a reminder, the bot believes you are capable of scoring the following public objectives: ";
                    messageText += String.join(", ", scorables);
                } else {
                    messageText = player.getRepresentation() + ", you cannot score public objectives because you do not control your home system.";
                }
            }
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), messageText);

            int count = 0;
            StringBuilder message3a = new StringBuilder(player.getRepresentation() + " as a reminder, the bot believes you are capable of scoring the following secret objectives:");
            for (String soID : player.getSecretsUnscored().keySet()) {
                if (ListPlayerInfoService.getObjectiveThreshold(soID, game) > 0 &&
                    ListPlayerInfoService.getPlayerProgressOnObjective(soID, game, player) > (ListPlayerInfoService.getObjectiveThreshold(soID, game) - 1) &&
                    !soID.equalsIgnoreCase("dp")) {
                    message3a.append(" ").append(Mapper.getSecretObjective(soID).getName());
                    count++;
                }
            }
            if (count > 0 && player.isRealPlayer()) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message3a.toString());
            }

        }

        for (Player p2 : game.getRealPlayers()) {
            String ms2 = StartTurnService.getMissedSCFollowsText(game, p2);
            if (ms2 != null && !"".equalsIgnoreCase(ms2)) {
                MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), ms2);
            }

            Button editSummary = RoundSummaryHelper.editSummaryButton(game, p2, game.getRound());
            String endOfRoundMessage = p2.getRepresentation();
            endOfRoundMessage += " you may write down your end of round thoughts, to be shared at the end of the game.";
            endOfRoundMessage += " Good things to share are highlights, plots, current relations with neighbors, or really anything you want (or nothing).";
            MessageHelper.sendMessageToChannelWithButton(p2.getCardsInfoThread(), endOfRoundMessage, editSummary);
        }

        String key2 = "queueToScorePOs";
        String key3 = "potentialScorePOBlockers";
        String key2b = "queueToScoreSOs";
        String key3b = "potentialScoreSOBlockers";

        game.setStoredValue(key2, "");
        game.setStoredValue(key3, "");
        game.setStoredValue(key2b, "");
        game.setStoredValue(key3b, "");
        if (game.getHighestScore() + 4 > game.getVp()) {
            game.setStoredValue("forcedScoringOrder", "true");
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.red("turnOffForcedScoring", "Turn off forced scoring order"));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), game.getPing() +
                "Players will be forced to score in order. Any preemptive scores will be queued. You may turn this off at any time by pressing this button.", buttons);
            for (Player player : game.getActionPhaseTurnOrder()) {
                game.setStoredValue(key3, game.getStoredValue(key3) + player.getFaction() + "*");
                game.setStoredValue(key3b, game.getStoredValue(key3b) + player.getFaction() + "*");
            }
        }

        // Optional abilities
        sendMitosisButtons(game);
        sendHoldingCompanyButtons(game);

        // Obligatory abilities
        resolveSolFlagship(game);
    }

    private static void sendMitosisButtons(Game game) {
        Player arborec = Helper.getPlayerFromAbility(game, "mitosis");
        if (arborec == null) return;

        String mitosisMessage = arborec.getRepresentationUnfogged() + ", a reminder to do **Mitosis**.";
        MessageHelper.sendMessageToChannelWithButtons(arborec.getCardsInfoThread(), mitosisMessage, ButtonHelperAbilities.getMitosisOptions(game, arborec));
    }

    private static void sendHoldingCompanyButtons(Game game) {
        Player veldyr = Helper.getPlayerFromAbility(game, "holding_company");
        if (veldyr == null) return;

        ButtonHelperFactionSpecific.offerHoldingCompanyButtons(veldyr, game);
    }

    private static void resolveSolFlagship(Game game) {
        for (Player player : game.getRealPlayers()) {
            if (!player.hasUnit("sol_flagship")) continue;

            String colorID = Mapper.getColorID(player.getColor());
            UnitKey infKey = Mapper.getUnitKey("gf", colorID);
            for (Tile tile : game.getTileMap().values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    if (unitHolder.getUnits() == null) continue;
                    if (unitHolder.getUnitCount(UnitType.Flagship, colorID) > 0) {
                        unitHolder.addUnit(infKey, 1);
                        String genesisMessage = player.getRepresentationUnfogged() + " 1 infantry was added to the space area of the Genesis (the Sol flagship) automatically.";
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), genesisMessage);
                    }
                }
            }
        }
    }
}
