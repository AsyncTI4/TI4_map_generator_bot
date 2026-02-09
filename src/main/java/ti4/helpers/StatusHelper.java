package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.buttons.Buttons;
import ti4.buttons.UnfiledButtonHandlers;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.async.RoundSummaryHelper;
import ti4.helpers.omega_phase.PriorityTrackHelper;
import ti4.image.BannerGenerator;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.breakthrough.SowingReapingService;
import ti4.service.breakthrough.ValefarZService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.fow.GMService;
import ti4.service.info.ListPlayerInfoService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.planet.EronousPlanetService;
import ti4.service.turn.StartTurnService;
import ti4.settings.users.UserSettingsManager;

public class StatusHelper {

    public static void AnnounceStatusPhase(Game game) {
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "All players have passed.");
        if (game.isShowBanners()) {
            BannerGenerator.drawPhaseBanner(
                    "status", game.getRound(), game.getActionsChannel(), game.isOmegaPhaseMode() ? "omega" : null);
        }
    }

    public static void offerPreScoringButtons(Game game, Player player) {
        if (game.getPhaseOfGame().contains("action")) {
            List<Button> buttons = new ArrayList<>();
            for (String obbie : game.getRevealedPublicObjectives().keySet()) {
                List<String> scoredPlayerList =
                        game.getScoredPublicObjectives().computeIfAbsent(obbie, key -> new ArrayList<>());
                if (player.isRealPlayer()
                        && !scoredPlayerList.contains(player.getUserID())
                        && Mapper.getPublicObjective(obbie) != null) {
                    int threshold = ListPlayerInfoService.getObjectiveThreshold(obbie, game);
                    int playerProgress = ListPlayerInfoService.getPlayerProgressOnObjective(obbie, game, player);
                    boolean toldarHero = false;
                    if (Mapper.getPublicObjective(obbie)
                            .getName()
                            .equalsIgnoreCase(game.getStoredValue("toldarHeroObj"))) {
                        if (!game.getStoredValue("toldarHeroPlayer").equalsIgnoreCase(player.getFaction())
                                && AgendaHelper.getPlayersWithLeastPoints(game).contains(player)) {
                            toldarHero = true;
                        }
                    }
                    if ((playerProgress >= threshold && threshold > 0) || toldarHero) {
                        buttons.add(Buttons.green(
                                "preScoreObbie_PO_"
                                        + game.getRevealedPublicObjectives().get(obbie),
                                Mapper.getPublicObjective(obbie).getName()));
                    }
                }
            }
            if (!buttons.isEmpty() && Helper.canPlayerScorePOs(game, player)) {
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                String msg = player.getRepresentation()
                        + ", you may use these buttons to queue a public objective to score, to speed up the status phase.";
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
            }

            buttons = new ArrayList<>();
            for (String soID : player.getSecretsUnscored().keySet()) {
                if (ListPlayerInfoService.getObjectiveThreshold(soID, game) > 0
                        && ListPlayerInfoService.getPlayerProgressOnObjective(soID, game, player)
                                > (ListPlayerInfoService.getObjectiveThreshold(soID, game) - 1)
                        && !"dp".equalsIgnoreCase(soID)) {

                    buttons.add(Buttons.green(
                            "preScoreObbie_SO_" + player.getSecretsUnscored().get(soID),
                            Mapper.getSecretObjective(soID).getName()));
                }
            }
            if (!buttons.isEmpty()) {
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                String msg = player.getRepresentation()
                        + ", you may use these buttons to queue a secret objective to score, to speed up the status phase.";
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
            } else {
                var userSettings = UserSettingsManager.get(player.getUserID());
                if (userSettings.getSandbagPref().contains("bot")) {
                    String message = player.getRepresentationNoPing()
                            + ", the bot will auto pass on scoring a secret objective this round for you (if you're still unable to score one when scoring occurs)."
                            + " Click this button to change it and do it manually."
                            + " You will be asked every round like this when you pass early, so no decision is final.";
                    buttons.add(Buttons.gray("sandbagPref_manual", "Manually Say No Scoring"));
                    buttons.add(Buttons.gray("deleteButtons", "Keep Current Setting"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                } else {
                    String message = player.getRepresentationNoPing()
                            + ", the bot will let you manually decide whether to score a secret objective this round."
                            + " Click this button to change it and auto pass on scoring this round (if you still have no secrets to score when scoring occurs)."
                            + " You will be asked every round like this when you pass early, so no decision is final.";
                    buttons.add(Buttons.green("sandbagPref_bot", "Auto Say No Scoring"));
                    buttons.add(Buttons.gray("deleteButtons", "Keep Current Setting"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                }
            }
            if (player.hasLeaderUnlocked("ralnelhero")) {
                if (game.getStoredValue("ralnelHero").isEmpty()) {
                    String presetRalnelHero =
                            "You have Director Nel, the Ral Nel hero, unlocked. You can use the preset button to automatically use your hero when the last player passes."
                                    + " Don't worry, you can always unset the preset later if you decide you don't want to use it.";

                    List<Button> ralnelHeroButtons = new ArrayList<>();
                    ralnelHeroButtons.add(Buttons.blue("resolvePreassignment_ralnelHero", "Preset Ral Nel Hero"));
                    ralnelHeroButtons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCardsInfoThread(), presetRalnelHero, ralnelHeroButtons);
                }
            }
        }
    }

    public static void BeginScoring(GenericInteractionCreateEvent event, Game game, MessageChannel gameChannel) {
        String messageText = "Please score objectives, " + game.getPing() + ".";

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
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged() + " you gained " + numScoredSOs + " trade good"
                                + (numScoredSOs == 1 ? "" : "s") + " and " + numScoredPos + " commodit"
                                + (numScoredSOs == 1 ? "y" : "ies") + " due to Komdar Borodin, the Vaden Commander.");
            }
            if (player.hasTech("hydrothermal")) {
                int oceans = 0;
                for (Player p2 : game.getRealPlayers()) {
                    oceans = Math.max(oceans, p2.getOceans().size());
                }
                if (oceans > 0) {
                    player.setTg(player.getTg() + oceans);
                    ButtonHelperAbilities.pillageCheck(player, game);
                    ButtonHelperAgents.resolveArtunoCheck(player, oceans);
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentationUnfogged() + ", you gained " + oceans + " trade good"
                                    + (oceans == 1 ? "" : "s") + " due to _ Hydrothermal Mining_.");
                }
            }
            if (player.hasTech("tf-geneticresearch")) {
                int maxNum = 0;
                for (TechnologyType type : TechnologyType.mainFour) {
                    maxNum = Math.max(maxNum, ButtonHelper.getNumberOfCertainTypeOfTech(player, type));
                }
                player.setTg(player.getTg() + maxNum);
                ButtonHelperAbilities.pillageCheck(player, game);
                ButtonHelperAgents.resolveArtunoCheck(player, maxNum);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged() + ", you gained " + maxNum + " trade good"
                                + (maxNum == 1 ? "" : "s") + " due to _Genetic Research_.");
            }
            if (player.hasTech("tf-radicaladvancement")) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green(
                        player.getFinsFactionCheckerPrefix() + "radicalAdvancementStart", "Replace a tech"));
                buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                                + " , please choose if you wish to use _Radical Advancement_.",
                        buttons);
            }
            if (player.hasTech("radical")) {
                List<Button> buttons = new ArrayList<>();
                for (String tech : player.getTechs()) {
                    TechnologyModel techModel = Mapper.getTech(tech);
                    if (techModel != null && !techModel.isUnitUpgrade()) {
                        buttons.add(Buttons.green(
                                "jnHeroSwapOut_" + tech, techModel.getName(), techModel.getCondensedReqsEmojis(true)));
                    }
                }
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                                + ", please choose a technology to replace via _Radical Advancement_.",
                        buttons);
            }
            if (player.getPromissoryNotes().containsKey("dspnuyda")
                    && !player.getPromissoryNotesOwned().contains("dspnuyda")) {
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        player.getRepresentationUnfogged()
                                + ", a reminder this is the window to use the _Eerie Predictions_.");
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
        if (game.isZealousOrthodoxyMode()
                || (game.getHighestScore() + 4 > game.getVp() && !game.isCivilizedSocietyMode())) {
            game.setStoredValue("forcedScoringOrder", "true");
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.red("turnOffForcedScoring", "Turn Off Forced Scoring Order"));
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    game.getPing()
                            + ", players will be forced to score in order. Any preemptive scores will be queued. You may turn this off at any time by pressing this button.",
                    buttons);
            for (Player player : GetPlayersInScoringOrder(game)) {
                game.setStoredValue(key3, game.getStoredValue(key3) + player.getFaction() + "*");
                game.setStoredValue(key3b, game.getStoredValue(key3b) + player.getFaction() + "*");
            }
        }

        for (Player player : game.getActionPhaseTurnOrder()) {
            List<String> scorables = new ArrayList<>();
            List<Integer> scorableInts = new ArrayList<>();
            String keyV = player.getFaction() + "Round" + game.getRound() + "PreScoredPO";

            if (game.getStoredValue("BountyContracts").contains(player.getFaction())
                    && player.getPlayableActionCards().contains("bounty_contracts")) {
                ActionCardHelper.playAC(event, game, player, "bounty_contracts", game.getMainGameChannel());
            }
            for (String obbie : game.getRevealedPublicObjectives().keySet()) {
                List<String> scoredPlayerList =
                        game.getScoredPublicObjectives().computeIfAbsent(obbie, key -> new ArrayList<>());
                if (player.isRealPlayer()
                        && !scoredPlayerList.contains(player.getUserID())
                        && Mapper.getPublicObjective(obbie) != null) {
                    int threshold = ListPlayerInfoService.getObjectiveThreshold(obbie, game);
                    int playerProgress = ListPlayerInfoService.getPlayerProgressOnObjective(obbie, game, player);
                    boolean toldarHero = false;
                    if (Mapper.getPublicObjective(obbie)
                            .getName()
                            .equalsIgnoreCase(game.getStoredValue("toldarHeroObj"))) {
                        if (!game.getStoredValue("toldarHeroPlayer").equalsIgnoreCase(player.getFaction())
                                && AgendaHelper.getPlayersWithLeastPoints(game).contains(player)) {
                            toldarHero = true;
                        }
                    }
                    if ((playerProgress >= threshold && threshold > 0) || toldarHero) {
                        scorables.add(Mapper.getPublicObjective(obbie).getRepresentation(false));
                        scorableInts.add(game.getRevealedPublicObjectives().get(obbie));
                    }
                }
            }
            if (!game.getStoredValue(keyV).isEmpty()
                    && Helper.canPlayerScorePOs(game, player)
                    && scorableInts.contains(Integer.parseInt(game.getStoredValue(keyV)))) {
                UnfiledButtonHandlers.poScoring(
                        null, player, game.getStoredValue(keyV), game, player.getCorrectChannel());
            } else {
                if (scorables.isEmpty()) {
                    messageText = player.getRepresentation()
                            + ", the bot does not believe that you can score any public objectives.";
                } else {
                    if (Helper.canPlayerScorePOs(game, player)) {
                        messageText = player.getRepresentation()
                                + ", as a reminder, the bot believes you are capable of scoring the following public objective"
                                + (scorables.size() == 1 ? "" : "s") + ":\n";
                        messageText += String.join("\n", scorables);
                    } else {
                        messageText = player.getRepresentation()
                                + ", you cannot score public objectives because you do not control your home system.";
                    }
                }
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), messageText);
            }

            if (scorables.isEmpty() || !Helper.canPlayerScorePOs(game, player)) {
                String message = player.getRepresentation()
                        + " cannot score any public objectives according to the bot, and has been marked as not scoring a public objective.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                game.setStoredValue(player.getFaction() + "round" + game.getRound() + "PO", "None");
                key2 = "queueToScorePOs";
                key3 = "potentialScorePOBlockers";
                if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key2, game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                }
                if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
                }
            }

            int count = 0;
            StringBuilder message3a = new StringBuilder();
            List<Integer> sos = new ArrayList<>();
            for (String soID : player.getSecretsUnscored().keySet()) {
                if (ListPlayerInfoService.getObjectiveThreshold(soID, game) > 0
                        && ListPlayerInfoService.getPlayerProgressOnObjective(soID, game, player)
                                > (ListPlayerInfoService.getObjectiveThreshold(soID, game) - 1)
                        && !"dp".equalsIgnoreCase(soID)) {
                    message3a
                            .append("\n")
                            .append(Mapper.getSecretObjective(soID).getRepresentation(false));
                    count++;
                    sos.add(player.getSecretsUnscored().get(soID));
                }
            }
            var userSettings = UserSettingsManager.get(player.getUserID());
            keyV = player.getFaction() + "Round" + game.getRound() + "PreScoredSO";

            if (!game.getStoredValue(keyV).isEmpty() && sos.contains(Integer.parseInt(game.getStoredValue(keyV)))) {
                UnfiledButtonHandlers.soScoreFromHand(
                        null,
                        game.getStoredValue(keyV),
                        game,
                        player,
                        player.getCorrectChannel(),
                        gameChannel,
                        gameChannel);
            } else {
                if (count > 0 && player.isRealPlayer() && !player.isNpc()) {
                    message3a.insert(
                            0,
                            player.getRepresentation()
                                    + ", as a reminder, the bot believes you are capable of scoring the following secret objective"
                                    + (count == 1 ? "" : "s") + ":");
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message3a.toString());
                } else if (player.getSo() == 0
                        || player.isNpc()
                        || userSettings.getSandbagPref().contains("bot")) {
                    String message = player.getRepresentation()
                            + " has opted not to score a secret objective at this point in time.";
                    game.setStoredValue(player.getFaction() + "round" + game.getRound() + "SO", "None");
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                    key2 = "queueToScoreSOs";
                    key3 = "potentialScoreSOBlockers";
                    if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                        game.setStoredValue(key2, game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                    }
                    if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                        game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
                    }
                } else if (player.isRealPlayer()) {
                    MessageHelper.sendMessageToChannel(
                            player.getCardsInfoThread(),
                            player.getRepresentation()
                                    + ", the bot does not believe that you can score any of your secret objectives.");
                }
            }
            if (Helper.checkEndGame(game, player)) {
                break;
            }
            ActionCardHelper.sendPlotCardInfo(game, player);
        }

        List<Button> poButtons = getScoreObjectiveButtons(game);
        Button noPOScoring = Buttons.red(Constants.PO_NO_SCORING, "No Public Objective Scored");
        Button noSOScoring = Buttons.red(Constants.SO_NO_SCORING, "No Secret Objective Scored");
        Button scoreAnObjective = Buttons.blue("get_so_score_buttons", "Score A Secret Objective");
        poButtons.add(noPOScoring);
        poButtons.add(scoreAnObjective);
        poButtons.add(noSOScoring);
        if (!game.getStoredValue("newStatusScoringMode").isEmpty() && !game.isFowMode()) {
            poButtons.add(Buttons.gray("refreshStatusSummary", "Refresh Summary"));
        }
        if (game.getActionCards().size() > 130
                && game.getPlayerFromColorOrFaction("hacan") != null
                && !ButtonHelper.getButtonsToSwitchWithAllianceMembers(
                                game.getPlayerFromColorOrFaction("hacan"), game, false)
                        .isEmpty()) {
            poButtons.add(Buttons.gray("getSwapButtons_", "Swap"));
        }
        poButtons.removeIf(Objects::isNull);
        messageText = "Please score objectives, " + game.getPing() + ".";
        if (!game.isFowMode()) {
            game.setStoredValue("newStatusScoringMode", "Yes");
            messageText += "\n\n" + Helper.getNewStatusScoringRepresentation(game);
        }
        MessageHelper.sendMessageToChannelWithPersistentReacts(
                gameChannel, messageText, game, poButtons, GameMessageType.STATUS_SCORING);

        boolean allReacted = true;
        for (Player player : game.getRealPlayers()) {
            String po = game.getStoredValue(player.getFaction() + "round" + game.getRound() + "PO");
            String so = game.getStoredValue(player.getFaction() + "round" + game.getRound() + "SO");
            if (po.isEmpty()
                    || so.isEmpty()
                    || game.getPhaseOfGame().contains("action")
                    || game.getPhaseOfGame().contains("agenda")) {
                allReacted = false;
            }
        }
        if (allReacted) {
            UnfiledButtonHandlers.respondAllHaveScored(game);
        }
    }

    public static List<Player> GetPlayersInScoringOrder(Game game) {
        if (game.hasFullPriorityTrackMode()) {
            return PriorityTrackHelper.getPriorityTrack(game).stream()
                    .filter(Objects::nonNull)
                    .toList();
        } else if (game.getPlanets().contains(EronousPlanetService.CANTRIS_ID)) {
            return EronousPlanetService.resolveCantrisScoringOrder(game);
        }
        return game.getActionPhaseTurnOrder();
    }

    public static void HandleStatusPhaseMiddle(
            GenericInteractionCreateEvent event, Game game, MessageChannel gameChannel) {
        Player vaden = Helper.getPlayerFromAbility(game, "binding_debts");
        if (vaden != null) {
            for (Player p2 : vaden.getNeighbouringPlayers(true)) {
                if (p2.getTg() > 0 && vaden.getDebtTokenCount(p2.getColor(), Constants.VADEN_DEBT_POOL) > 0) {
                    String msg = p2.getRepresentationUnfogged()
                            + ", you have the opportunity to pay off **Binding Debts** here."
                            + " You may pay 1 trade good to get 2 debt tokens forgiven, from the \"Shark Loans\" pool.";
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
                if (pnModel.getText().contains("eturn this card")
                        && (pnModel.getText().contains("start of the status phase")
                                || pnModel.getText().contains("beginning of the status phase"))) {
                    player.removePromissoryNote(pn);
                    pnOwner.setPromissoryNote(pn);
                    PromissoryNoteHelper.sendPromissoryNoteInfo(game, pnOwner, false);
                    PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            "_" + pnModel.getName() + "_ has been returned to " + pnOwner.getRepresentationNoPing()
                                    + ".");
                }
            }
            if (player.hasTech("dsauguy") && player.getTg() > 2) {
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                                + ", you may use the button to pay 3 trade goods and get a technology, using _Sentient Datapool_.",
                        List.of(Buttons.GET_A_TECH));
            }
            Leader playerLeader = player.getLeader("kyrohero").orElse(null);
            if (player.hasLeader("kyrohero")
                    && player.getLeaderByID("kyrohero").isPresent()
                    && playerLeader != null
                    && !playerLeader.isLocked()) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("kyroHeroInitiation", "Play Kyro Hero"));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
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
                    if (unitHolder != null
                            && unitHolder.getTokenList() != null
                            && unitHolder.getTokenList().contains("attachment_tombofemphidia.png")) {
                        if (player.hasRelic("emphidia")) {
                            MessageHelper.sendMessageToChannel(
                                    player.getCardsInfoThread(),
                                    player.getRepresentation()
                                            + ", a reminder this is __not__ the window to use _The Crown of Emphidia_."
                                            + " You may purge _The Crown of Emphidia_ in the status homework phase, which is when buttons will appear.");
                        } else {
                            MessageHelper.sendMessageToChannel(
                                    player.getCardsInfoThread(),
                                    player.getRepresentation()
                                            + ", a reminder this is the window to use _The Crown of Emphidia_.");
                            MessageHelper.sendMessageToChannelWithButtons(
                                    player.getCardsInfoThread(),
                                    player.getRepresentation()
                                            + ", you may use these buttons to resolve _The Crown of Emphidia_.",
                                    ButtonHelper.getCrownButtons());
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
            endOfRoundMessage +=
                    ", you may write down your end of round thoughts, to be shared at the end of the game.";
            endOfRoundMessage +=
                    " Good things to share are highlights, plots, current relations with neighbors, or really anything you want (or nothing).";
            MessageHelper.sendMessageToChannelWithButton(p2.getCardsInfoThread(), endOfRoundMessage, editSummary);

            Player obsidian = Helper.getPlayerFromAbility(game, "marionettes");
            if (obsidian != null
                    && obsidian.getPuppetedFactionsForPlot("seethe").contains(p2.getFaction())) {
                String seetheMsg = obsidian.getRepresentation() + ", please destroy one infantry belonging to "
                        + p2.getRepresentation() + ", using _Seethe_.";
                List<Button> removeButtons = ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(p2, game, "gf");
                MessageHelper.sendMessageToChannel(obsidian.getCorrectChannel(), seetheMsg, removeButtons);
            }
        }

        // Optional abilities
        sendMitosisButtons(game);
        sendYinCloneButtons(game);
        sendHoldingCompanyButtons(game);
        sendEntropicScarButtons(game);
        sendNeuralParasiteButtons(game);
        sendRemoveBreachButtons(game);
        SowingReapingService.sendTheSowingButtons(game);
        SowingReapingService.resolveTheReaping(game);

        // Obligatory abilities
        resolveSolFlagship(game);
    }

    public static void sendRemoveBreachButtons(Game game) {
        Predicate<Tile> hasBreach = t -> t.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_BREACH_ACTIVE);
        Function<Player, Predicate<Tile>> hasPlayerShips = p -> (t -> FoWHelper.playerHasShipsInSystem(p, t));
        for (Player p : game.getRealPlayers()) {
            List<Button> buttons = ButtonHelper.getTilesWithPredicateForAction(
                    p, game, "statusRemoveBreach", hasBreach.and(hasPlayerShips.apply(p)), false);
            if (!buttons.isEmpty()) {
                buttons.add(Buttons.DONE_DELETE_BUTTONS.withLabel("Done removing"));
                String msg = p.getRepresentation()
                        + ", you may remove active Breaches from systems that contain your ships.";
                MessageHelper.sendMessageToChannelWithButtons(p.getCorrectChannel(), msg, buttons);
            }
        }
    }

    private static void sendNeuralParasiteButtons(Game game) {
        List<Player> firmaments = Helper.getPlayersFromTech(game, "parasite-firm");
        if (firmaments == null || firmaments.isEmpty()) return;

        for (Player player : firmaments) {
            Tile home = player.getHomeSystemTile();
            if (home == null) {
                continue;
            }
            List<Button> buttons = new ArrayList<>();
            for (Planet planet : home.getPlanetUnitHolders()) {
                String id = player.finChecker() + "placeOneNDone_skipbuild_gf_" + planet.getName();
                String label = Helper.getUnitHolderRepresentation(home, planet.getName(), game, player);
                buttons.add(Buttons.green(id, label, PlanetEmojis.getPlanetEmoji(planet.getName())));
            }
            TechnologyModel parasiteModel = Mapper.getTech("parasite-firm");
            String parasiteMsg = player.getRepresentationUnfogged() + ", a reminder to do "
                    + parasiteModel.getNameRepresentation() + ".";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), parasiteMsg, buttons);
        }
    }

    private static void sendMitosisButtons(Game game) {
        Player arborec = Helper.getPlayerFromAbility(game, "mitosis");
        if (arborec == null) return;

        String mitosisMessage = arborec.getRepresentationUnfogged() + ", a reminder to do **Mitosis**.";
        MessageHelper.sendMessageToChannelWithButtons(
                arborec.getCardsInfoThread(), mitosisMessage, ButtonHelperAbilities.getMitosisOptions(game, arborec));
    }

    private static void sendYinCloneButtons(Game game) {
        for (Player player : game.getRealPlayers()) {
            if (player.getStasisInfantry() > 0 && player.hasUnit("tf-yinclone")) {
                if (!ButtonHelper.getPlaceStatusInfButtons(game, player).isEmpty()) {
                    List<Button> buttons = ButtonHelper.getPlaceStatusInfButtons(game, player);
                    String msg = player.getRepresentation() + ", please use these buttons to revive infantry. You have "
                            + player.getStasisInfantry() + " infantry left to revive.";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
                } else {
                    String msg = player.getRepresentation() + ", you had infantry II to be revived, but";
                    msg += " the bot couldn't find any planets you control in your home system to place them on";
                    msg += ", so per the rules they now disappear into the ether.";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                    player.setStasisInfantry(0);
                }
            }
        }
    }

    private static void handleMonumentToTheAges(Game game) {
        if (!game.isMonumentToTheAgesMode()) {
            return;
        }
        Player neutral = game.getPlayerFromColorOrFaction("neutral");
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (planet.getUnitCount(UnitType.Spacedock, neutral) > 0) {

                    int remaining = game.changeCommsOnPlanet(1, planet.getName());

                    String msg = "A commodity was placed upon the Monument to the Ages at " + planet.getName() + ".";
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
                }
            }
        }
    }

    public static void sendEntropicScarButtons(Game game) {
        Map<Player, Integer> scars = new HashMap<>();
        for (Tile t : game.getTileMap().values()) {
            if (t.isScar()) {
                for (Player p : game.getRealPlayers()) {
                    if (Tile.tileHasPlayerShips(p).test(t)) {
                        scars.put(p, scars.getOrDefault(p, 0) + 1);
                    }
                }
            }
        }
        for (Map.Entry<Player, Integer> entry : scars.entrySet()) {
            Player player = entry.getKey();
            List<String> factionTechs = new ArrayList<>(player.getFactionTechs());
            if (game.isTwilightsFallMode()) {
                factionTechs.add("antimatter");
                factionTechs.add("wavelength");
            }
            factionTechs.remove("vax");
            factionTechs.remove("vay");
            player.getTechs().forEach(factionTechs::remove);
            List<Button> buttons = new ArrayList<>(factionTechs.stream()
                    .map(tech -> {
                        TechnologyModel model = Mapper.getTech(tech);
                        return Buttons.green(
                                player.getFinsFactionCheckerPrefix() + "entropicScar_" + tech,
                                model.getName(),
                                model.getCondensedReqsEmojis(true));
                    })
                    .toList());
            buttons.add(Buttons.DONE_DELETE_BUTTONS.withLabel("No Thanks"));

            int ccs = player.getStrategicCC();
            int techs = buttons.size() - 1;
            if (game.isTwilightsFallMode() && techs == 0) {
                if (player.hasRelicReady("emelpar") || player.hasRelicReady("absol_emelpar")) {
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentationUnfogged()
                                    + ", you have ships in an Entropic Scar anomaly. However, you have no faction technologies left to gain."
                                    + " _Scepter of Emelpar_ has been exhausted and you have been given +2 command tokens in your strategy pool.");
                    player.setStrategicCC(player.getStrategicCC() + 2);
                    player.addExhaustedRelic("emelpar");
                    player.addExhaustedRelic("absol_emelpar");
                } else if (player.getStrategicCC() > 0) {
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentationUnfogged()
                                    + ", you have ships in an Entropic Scar anomaly. However, you have no faction technologies left to gain."
                                    + " You have been given net +1 command tokens in your strategy pool.");
                    player.setStrategicCC(player.getStrategicCC() + 1);
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, null, "Entropic Scar");
                }
                return;
            }
            String scarMessage = player.getRepresentationUnfogged()
                    + " You have ships in an Entropic Scar anomaly. You may use these buttons to spend a token from your strategy pool to gain one of your faction technologies.";
            scarMessage +=
                    "You currently have " + ccs + " command token" + (ccs == 1 ? "" : "s") + " in your strategy pool.";
            if (player.hasRelicReady("emelpar") || player.hasRelicReady("absol_emelpar"))
                scarMessage += "You also have the _" + RelicHelper.sillySpelling()
                        + "_ available to exhaust (this will be spent first).";
            for (int i = 0; i < techs && i < entry.getValue(); i++) {
                if (i > 0) scarMessage = "Get another one!";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), scarMessage, buttons);
            }
        }
    }

    private static void sendHoldingCompanyButtons(Game game) {
        Player veldyr = Helper.getPlayerFromAbility(game, "holding_company");
        if (veldyr == null) return;

        ButtonHelperFactionSpecific.offerHoldingCompanyButtons(veldyr, game);
    }

    private static void resolveSolFlagship(Game game) {
        for (Player player : game.getRealPlayers()) {
            if (!ValefarZService.hasFlagshipAbility(game, player, "sol_flagship")) continue;

            String colorID = Mapper.getColorID(player.getColor());
            UnitKey infKey = Mapper.getUnitKey("gf", colorID);
            for (Tile tile : game.getTileMap().values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    if (!unitHolder.hasUnits()) continue;
                    if (unitHolder.getUnitCount(UnitType.Flagship, colorID) > 0) {
                        unitHolder.addUnit(infKey, 1);
                        String genesisMessage = player.getRepresentationUnfogged()
                                + ", 1 infantry was added to the space area of the Genesis (the Sol flagship) automatically.";
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), genesisMessage);
                    }
                }
            }
        }
    }

    private static List<Button> getScoreObjectiveButtons(Game game) {
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
            String poName = publicObjectivesState1.get(key);
            poStatus = 0;
            if (poName == null) {
                poName = publicObjectivesState2.get(key);
                poStatus = 1;
            }
            if (poName == null) {
                Integer integer = customPublicVP.get(key);
                if (integer != null
                        && !key.toLowerCase().contains("custodian")
                        && !key.toLowerCase().contains("imperial")
                        && !key.contains("Shard of the Throne")
                        && !key.contains("Seed of an")
                        && !key.contains("Mutiny")
                        && !key.contains("Stellar Atomics")
                        && !key.contains("(Plotted)")
                        && !key.contains("Crown of Emphidia")
                        && !key.contains(Constants.VOICE_OF_THE_COUNCIL_PO)) {
                    poName = key;
                    poStatus = 2;
                }
            }
            if (poName != null) {
                Integer value = objective.getValue();
                Button objectiveButton;
                if (poStatus == 0) { // Stage 1 Objectives
                    objectiveButton = Buttons.green(
                            prefix + Constants.PO_SCORING + value, "(" + value + ") " + poName, CardEmojis.Public1alt);
                    poButtons1.add(objectiveButton);
                } else if (poStatus == 1) { // Stage 2 Objectives
                    objectiveButton = Buttons.blue(
                            prefix + Constants.PO_SCORING + value, "(" + value + ") " + poName, CardEmojis.Public2alt);
                    poButtons2.add(objectiveButton);
                } else { // Other Objectives
                    objectiveButton = Buttons.gray(prefix + Constants.PO_SCORING + value, "(" + value + ") " + poName);
                    poButtonsCustom.add(objectiveButton);
                }
            }
        }

        poButtons.addAll(poButtons1);
        poButtons.addAll(poButtons2);
        poButtons.addAll(poButtonsCustom);
        for (Player player : game.getRealPlayers()) {
            if (game.playerHasLeaderUnlockedOrAlliance(player, "edyncommander") && !game.isFowMode()) {
                poButtons.add(Buttons.gray(
                        "edynCommanderSODraw",
                        "Draw Secret Objective Instead of Scoring Public Objective",
                        FactionEmojis.edyn));
                break;
            }
        }
        poButtons.removeIf(Objects::isNull);
        return poButtons;
    }
}
