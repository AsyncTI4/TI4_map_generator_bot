package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.collections4.ListUtils;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.buttons.Buttons;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.async.RoundSummaryHelper;
import ti4.helpers.omega_phase.PriorityTrackHelper;
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
import ti4.service.fow.GMService;
import ti4.service.info.ListPlayerInfoService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.turn.StartTurnService;

public class StatusHelper {

    public static void AnnounceStatusPhase(Game game) {
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "All players have passed.");
        if (game.isShowBanners()) {
            BannerGenerator.drawPhaseBanner("status", game.getRound(), game.getActionsChannel(), game.isOmegaPhaseMode() ? "omega" : null);
        }
    }

    public static void BeginScoring(GenericInteractionCreateEvent event, Game game, MessageChannel gameChannel) {
        String messageText = "Please score objectives, " + game.getPing() + ".";

        if (!game.isFowMode()) {
            game.setStoredValue("newStatusScoringMode", "Yes");
            messageText += "\n\n" + Helper.getNewStatusScoringRepresentation(game);
        }

        if (game.isOmegaPhaseMode()) {
            // Show the effects of the Agendas while scoring
            ButtonHelper.updateMap(game, event, "After Agendas, Round " + game.getRound() + ".");
        }

        game.setPhaseOfGame("statusScoring");
        game.setStoredValue("startTimeOfRound" + game.getRound() + "StatusScoring", System.currentTimeMillis() + "");
        GMService.logActivity(game, "**StatusScoring** Phase for Round " + game.getRound() + " started.", true);
        for (Player player : game.getRealPlayers()) {
            SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
            List<String> relics = new ArrayList<>(player.getRelics());
            for (String relic : relics) {
                if (player.getExhaustedRelics().contains(relic) && relic.contains("axisorder")) {
                    player.removeRelic(relic);
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
            if (player.getPromissoryNotes().containsKey("dspnuyda") && !player.getPromissoryNotesOwned().contains("dspnuyda")) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + ", a reminder this is the window to use the _Eerie Predictions_.");
            }
        }
        String key2 = "queueToScorePOs";
        String key3 = "potentialScorePOBlockers";
        String key2b = "queueToScoreSOs";
        String key3b = "potentialScoreSOBlockers";

        game.setStoredValue(key2, "");
        game.setStoredValue(key3, "");
        game.setStoredValue(key2b, "");
        game.setStoredValue(key3b, "");
        if (game.getHighestScore() + 4 > game.getVp() && !game.isCivilizedSocietyMode()) {
            game.setStoredValue("forcedScoringOrder", "true");
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.red("turnOffForcedScoring", "Turn Off Forced Scoring Order"));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), game.getPing() +
                ", players will be forced to score in order. Any preemptive scores will be queued. You may turn this off at any time by pressing this button.", buttons);
            for (Player player : GetPlayersInScoringOrder(game)) {
                game.setStoredValue(key3, game.getStoredValue(key3) + player.getFaction() + "*");
                game.setStoredValue(key3b, game.getStoredValue(key3b) + player.getFaction() + "*");
            }
        }

        for (Player player : game.getRealPlayers()) {
            List<String> scorables = new ArrayList<>();
            for (String obbie : game.getRevealedPublicObjectives().keySet()) {
                List<String> scoredPlayerList = game.getScoredPublicObjectives().computeIfAbsent(obbie, key -> new ArrayList<>());
                if (player.isRealPlayer() && !scoredPlayerList.contains(player.getUserID()) && Mapper.getPublicObjective(obbie) != null) {
                    int threshold = ListPlayerInfoService.getObjectiveThreshold(obbie, game);
                    int playerProgress = ListPlayerInfoService.getPlayerProgressOnObjective(obbie, game, player);
                    boolean toldarHero = false;
                    if (Mapper.getPublicObjective(obbie).getName().equalsIgnoreCase(game.getStoredValue("toldarHeroObj"))) {
                        if (!game.getStoredValue("toldarHeroPlayer").equalsIgnoreCase(player.getFaction()) && AgendaHelper.getPlayersWithLeastPoints(game).contains(player)) {
                            toldarHero = true;
                        }
                    }
                    if ((playerProgress >= threshold && threshold > 0) || toldarHero) {
                        scorables.add(Mapper.getPublicObjective(obbie).getRepresentation(false));
                    }
                }
            }
            if (scorables.isEmpty()) {
                messageText = player.getRepresentation() + ", the bot does not believe that you can score any public objectives.";
            } else {
                if (Helper.canPlayerScorePOs(game, player)) {
                    messageText = player.getRepresentation() + ", as a reminder, the bot believes you are capable of scoring the following public objective"
                        + (scorables.size() == 1 ? "" : "s") + ":\n";
                    messageText += String.join("\n", scorables);
                } else {
                    messageText = player.getRepresentation() + ", you cannot score public objectives because you do not control your home system.";
                }
            }
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), messageText);
            if (scorables.isEmpty() || !Helper.canPlayerScorePOs(game, player)) {
                String message = player.getRepresentation()
                    + " cannot score any public objectives according to the bot, and has been marked as not scoring a public objective.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                game.setStoredValue(player.getFaction() + "round" + game.getRound() + "PO", "None");
                key2 = "queueToScorePOs";
                key3 = "potentialScorePOBlockers";
                if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key2,
                        game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                }
                if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
                }
            }

            int count = 0;
            String message3a = "";
            for (String soID : player.getSecretsUnscored().keySet()) {
                if (ListPlayerInfoService.getObjectiveThreshold(soID, game) > 0
                    && ListPlayerInfoService.getPlayerProgressOnObjective(soID, game, player) > (ListPlayerInfoService.getObjectiveThreshold(soID, game) - 1)
                    && !soID.equalsIgnoreCase("dp")) {
                    message3a += "\n" + Mapper.getSecretObjective(soID).getRepresentation(false);
                    count++;
                }
            }
            if (count > 0 && player.isRealPlayer()) {
                message3a = player.getRepresentation() + ", as a reminder, the bot believes you are capable of scoring the following secret objective"
                    + (count == 1 ? "" : "s") + ":" + message3a;
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message3a);
            } else if (player.getSo() == 0) {
                String message = player.getRepresentation()
                    + " has no secret objectives to score at this time.";
                game.setStoredValue(player.getFaction() + "round" + game.getRound() + "SO", "None");
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                key2 = "queueToScoreSOs";
                key3 = "potentialScoreSOBlockers";
                if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key2,
                        game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                }
                if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key3,
                        game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
                }
            } else if (player.isRealPlayer()) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    player.getRepresentation() + ", the bot does not believe that you can score any of your secret objectives.");
            }
        }

    }

    public static List<Player> GetPlayersInScoringOrder(Game game) {
        if (game.hasFullPriorityTrackMode()) {
            return PriorityTrackHelper.GetPriorityTrack(game)
                .stream().filter(Objects::nonNull).toList();
        } else {
            return game.getActionPhaseTurnOrder();
        }
    }

    public static void HandleStatusPhaseMiddle(GenericInteractionCreateEvent event, Game game, MessageChannel gameChannel) {
        Player vaden = Helper.getPlayerFromAbility(game, "binding_debts");
        if (vaden != null) {
            for (Player p2 : vaden.getNeighbouringPlayers(true)) {
                if (p2.getTg() > 0 && vaden.getDebtTokenCount(p2.getColor()) > 0) {
                    String msg = p2.getRepresentationUnfogged() + ", you have the opportunity to pay off **Binding Debts** here."
                        + " You may pay 1 trade good to get 2 debt tokens forgiven.";
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green("bindingDebtsRes_" + vaden.getFaction(), "Pay 1 Trade Good"));
                    buttons.add(Buttons.red("deleteButtons", "Stay Indebted"));
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
                }
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
                    player.getRepresentationUnfogged() + ", you may use the button to pay 3 trade goods and get a technology, using _Sentient Datapool_.", List.of(Buttons.GET_A_TECH));
            }
            Leader playerLeader = player.getLeader("kyrohero").orElse(null);
            if (player.hasLeader("kyrohero") && player.getLeaderByID("kyrohero").isPresent()
                && playerLeader != null && !playerLeader.isLocked()) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("kyroHeroInitiation", "Play Kyro Hero"));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                    player.getRepresentation()
                        + ", a reminder this is the window to play Speygh, the Kyro hero. You may use the buttons to start the process.",
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
                                player.getRepresentation() + ", a reminder this is __not__ the window to use _The Crown of Emphidia_."
                                    + " You may purge _The Crown of Emphidia_ in the status homework phase, which is when buttons will appear.");
                        } else {
                            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                                player.getRepresentation() + ", a reminder this is the window to use _The Crown of Emphidia_.");
                            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                                player.getRepresentation() + ", you may use these buttons to resolve _The Crown of Emphidia_.", ButtonHelper.getCrownButtons());
                        }
                    }
                }
            }
        }

        for (Player p2 : game.getRealPlayers()) {
            String ms2 = StartTurnService.getMissedSCFollowsText(game, p2);
            if (ms2 != null && !"".equalsIgnoreCase(ms2)) {
                MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), ms2);
            }

            Button editSummary = RoundSummaryHelper.editSummaryButton(game, p2, game.getRound());
            String endOfRoundMessage = p2.getRepresentation();
            endOfRoundMessage += ", you may write down your end of round thoughts, to be shared at the end of the game.";
            endOfRoundMessage += " Good things to share are highlights, plots, current relations with neighbors, or really anything you want (or nothing).";
            MessageHelper.sendMessageToChannelWithButton(p2.getCardsInfoThread(), endOfRoundMessage, editSummary);
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
                    if (!unitHolder.hasUnits()) continue;
                    if (unitHolder.getUnitCount(UnitType.Flagship, colorID) > 0) {
                        unitHolder.addUnit(infKey, 1);
                        String genesisMessage = player.getRepresentationUnfogged() + ", 1 infantry was added to the space area of the Genesis (the Sol flagship) automatically.";
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), genesisMessage);
                    }
                }
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
                    && !key.contains("Shard of the Throne") && !key.contains(Constants.VOICE_OF_THE_COUNCIL_PO)) {
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
}
