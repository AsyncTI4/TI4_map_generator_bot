package ti4.service.turn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.commons.collections4.ListUtils;
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
import ti4.helpers.Units;
import ti4.helpers.async.RoundSummaryHelper;
import ti4.image.BannerGenerator;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.info.ListPlayerInfoService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
public class EndTurnService {

    public static Player findNextUnpassedPlayer(Game game, Player currentPlayer) {
        int startingInitiative = game.getPlayersTurnSCInitiative(currentPlayer);
        //in a normal game, 8 is the maximum number, so we modulo on 9
        List<Player> unpassedPlayers = game.getRealPlayers().stream().filter(p -> !p.isPassed()).toList();
        int maxSC = Collections.max(game.getSCList()) + 1;
        if (ButtonHelper.getKyroHeroSC(game) != 1000) {
            maxSC = maxSC + 1;
        }
        for (int i = 1; i <= maxSC; i++) {
            int scCheck = (startingInitiative + i) % maxSC;
            for (Player p : unpassedPlayers) {
                if (game.getPlayersTurnSCInitiative(p) == scCheck) {
                    return p;
                }
            }
        }
        if (unpassedPlayers.isEmpty()) {
            return null;
        } else {
            return unpassedPlayers.getFirst();
        }
    }

    public static void pingNextPlayer(GenericInteractionCreateEvent event, Game game, Player mainPlayer) {
        pingNextPlayer(event, game, mainPlayer, false);
    }

    public static void pingNextPlayer(GenericInteractionCreateEvent event, Game game, Player mainPlayer, boolean justPassed) {
        game.setStoredValue("lawsDisabled", "no");
        game.setStoredValue("endTurnWhenSCFinished", "");
        game.setStoredValue("fleetLogWhenSCFinished", "");
        mainPlayer.setWhetherPlayerShouldBeTenMinReminded(false);
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
                + "**End of Turn " + mainPlayer.getInRoundTurnCount() + " for** " + mainPlayer.getRepresentation());
        } else {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), mainPlayer.getRepresentation(true, false) + " ended turn");
        }

        MessageChannel gameChannel = game.getMainGameChannel() == null ? event.getMessageChannel() : game.getMainGameChannel();

        //MAKE ALL NON-REAL PLAYERS PASSED
        for (Player player : game.getPlayers().values()) {
            if (!player.isRealPlayer()) {
                player.setPassed(true);
            }
        }

        if (game.getPlayers().values().stream().allMatch(Player::isPassed)) {
            showPublicObjectivesWhenAllPassed(event, game, gameChannel);
            game.updateActivePlayer(null);
            ButtonHelperAgents.checkForEdynAgentPreset(game, mainPlayer, mainPlayer, event);
            ButtonHelperAgents.checkForEdynAgentActive(game, event);
            return;
        }

        Player nextPlayer = findNextUnpassedPlayer(game, mainPlayer);
        if (!game.isFowMode()) {
            String lastTransaction = game.getLatestTransactionMsg();
            try {
                if (lastTransaction != null && !lastTransaction.isEmpty()) {
                    game.setLatestTransactionMsg("");
                    game.getMainGameChannel().deleteMessageById(lastTransaction).queue(null, e -> {
                    });
                }
            } catch (Exception e) {
                //  Block of code to handle errors
            }
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
                poButtons.add(Buttons.gray("edynCommanderSODraw", "Draw SO instead of Scoring PO", FactionEmojis.edyn));
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
        String message = "Please score objectives, " + game.getPing() + ".";

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
            for (Player p2 : vaden.getNeighbouringPlayers()) {
                if (p2.getTg() > 0 && vaden.getDebtTokenCount(p2.getColor()) > 0) {
                    String msg = p2.getRepresentationUnfogged() + " you have the opportunity to pay off binding debts here. You may pay 1TG to get 2 debt tokens forgiven.";
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green("bindingDebtsRes_" + vaden.getFaction(), "Pay 1TG"));
                    buttons.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
                }
            }
        }
        List<Button> poButtons = getScoreObjectiveButtons(game);
        Button noPOScoring = Buttons.red(Constants.PO_NO_SCORING, "No PO Scored");
        Button noSOScoring = Buttons.red(Constants.SO_NO_SCORING, "No SO Scored");
        poButtons.add(noPOScoring);
        poButtons.add(noSOScoring);
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
            .addContent(message)
            .addComponents(actionRows).build();

        gameChannel.sendMessage(messageObject).queue();

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
                    player.getRepresentationUnfogged() + " you gained " + numScoredSOs + " TG" + (numScoredSOs == 1 ? "" : "s") + " and " + numScoredPos + " commodit" + (numScoredSOs == 1 ? "y" : "ies") + " due to Komdar Borodin, the Vaden Commander.");
            }
        }

        Map<String, Player> players = game.getPlayers();
        for (Player player : players.values()) {
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
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), pnOwner.getFactionEmoji() + " " + pnModel.getName() + " was returned");
                }
            }
            if (player.hasTech("dsauguy") && player.getTg() > 2) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " you may use the button to pay 3TGs and get a tech, using your Sentient Datapool technology.", List.of(Buttons.GET_A_TECH));
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
                                player.getRepresentation() + "Reminder this is not the window to use " + ExploreEmojis.Relic + "Crown of Emphidia. You may purge " +
                                    ExploreEmojis.Relic + "Crown of Emphidia in the status homework phase, which is when buttons will appear.");
                        } else {
                            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation() + "Reminder this is the window to use " + ExploreEmojis.Relic + "Crown of Emphidia.");
                            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation() + " You may use these buttons to resolve " + ExploreEmojis.Relic + "Crown of Emphidia.", ButtonHelper.getCrownButtons());
                        }
                    }
                }
            }

            String message2a = player.getRepresentation() + " as a reminder, the bot believes you are capable of scoring the following public objectives: ";
            StringBuilder message2b = new StringBuilder("none");
            for (String obbie : game.getRevealedPublicObjectives().keySet()) {
                List<String> scoredPlayerList = game.getScoredPublicObjectives().computeIfAbsent(obbie, key -> new ArrayList<>());
                if (player.isRealPlayer() && !scoredPlayerList.contains(player.getUserID()) && Mapper.getPublicObjective(obbie) != null) {
                    int threshold = ListPlayerInfoService.getObjectiveThreshold(obbie, game);
                    int playerProgress = ListPlayerInfoService.getPlayerProgressOnObjective(obbie, game, player);
                    if (playerProgress >= threshold) {
                        if (message2b.toString().equalsIgnoreCase("none")) {
                            message2b = new StringBuilder(Mapper.getPublicObjective(obbie).getName());
                        } else {
                            message2b.append(", ").append(Mapper.getPublicObjective(obbie).getName());
                        }
                    }
                }
            }
            if (player.isRealPlayer()) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message2a + message2b);
            }
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
            for (Player player : Helper.getInitativeOrder(game)) {
                game.setStoredValue(key3, game.getStoredValue(key3) + player.getFaction() + "*");
                game.setStoredValue(key3b, game.getStoredValue(key3b) + player.getFaction() + "*");
            }
        }
        Player arborec = Helper.getPlayerFromAbility(game, "mitosis");
        if (arborec != null) {
            String mitosisMessage = arborec.getRepresentationUnfogged() + " reminder to do mitosis!";
            MessageHelper.sendMessageToChannelWithButtons(arborec.getCardsInfoThread(), mitosisMessage, ButtonHelperAbilities.getMitosisOptions(game, arborec));
        }
        Player veldyr = Helper.getPlayerFromAbility(game, "holding_company");
        if (veldyr != null) {
            ButtonHelperFactionSpecific.offerHoldingCompanyButtons(veldyr, game);
        }
        Player solPlayer = Helper.getPlayerFromUnit(game, "sol_flagship");

        if (solPlayer == null) {
            return;
        }
        String colorID = Mapper.getColorID(solPlayer.getColor());
        Units.UnitKey infKey = Mapper.getUnitKey("gf", colorID);
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder.getUnits() != null) {
                    if (unitHolder.getUnitCount(Units.UnitType.Flagship, colorID) > 0) {
                        unitHolder.addUnit(infKey, 1);
                        String genesisMessage = solPlayer.getRepresentationUnfogged()
                            + " 1 infantry was added to the space area of the Genesis (the Sol flagship) automatically.";
                        if (game.isFowMode()) {
                            MessageHelper.sendMessageToChannel(solPlayer.getPrivateChannel(), genesisMessage);
                        } else {
                            MessageHelper.sendMessageToChannel(gameChannel, genesisMessage);
                        }
                    }
                }
            }
        }
    }
}
