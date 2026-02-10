package ti4.service.turn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.StringHelper;
import ti4.helpers.thundersedge.TeHelperTechs;
import ti4.image.BannerGenerator;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.model.LeaderModel;
import ti4.model.metadata.AutoPingMetadataManager;
import ti4.service.actioncard.SabotageService;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.breakthrough.EidolonMaximumService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;
import ti4.service.fow.FowCommunicationThreadService;
import ti4.service.fow.WhisperService;
import ti4.service.info.CardsInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.strategycard.PlayStrategyCardService;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
public class StartTurnService {

    public static void turnStart(GenericInteractionCreateEvent event, Game game, Player player) {
        player.setInRoundTurnCount(player.getInRoundTurnCount() + 1);
        game.removeStoredValue("currentActionSummary" + player.getFaction());

        CommanderUnlockCheckService.checkPlayer(player, "hacan");
        Map<String, String> maps = new HashMap<>(game.getMessagesThatICheckedForAllReacts());
        for (String id : maps.keySet()) {
            if (id.contains("combatRoundTracker")) {
                game.removeStoredValue(id);
            }
        }

        game.removeStoredValue("fortuneSeekers");
        ButtonHelperTacticalAction.resetStoredValuesForTacticalAction(game);
        game.setStoredValue(player.getFaction() + "planetsExplored", "");
        game.setStoredValue("lawsDisabled", "no");
        game.removeStoredValue("audioSent");
        game.checkSOLimit(player);
        CardsInfoService.sendVariousAdditionalButtons(game, player);
        EidolonMaximumService.sendEidolonMaximumFlipButtons(game, player);
        boolean goingToPass = false;
        if (game.getStoredValue("Pre Pass " + player.getFaction()) != null
                && game.getStoredValue("Pre Pass " + player.getFaction()).contains(player.getFaction())) {
            if (game.getStoredValue("Pre Pass " + player.getFaction()).contains(player.getFaction())
                    && !player.isPassed()) {
                game.setStoredValue("Pre Pass " + player.getFaction(), "");
                goingToPass = true;
            }
        }

        if (player.isNpc()) {
            boolean hadAnyUnplayedSCs = false;
            for (Integer SC : player.getSCs()) {
                if (!game.getPlayedSCs().contains(SC)) {
                    hadAnyUnplayedSCs = true;
                }
            }
            if (!hadAnyUnplayedSCs) {
                goingToPass = true;
            }
        }

        String text = player.getRepresentationUnfogged() + ", it is now your turn (your "
                + StringHelper.ordinal(player.getInRoundTurnCount()) + " turn of round " + game.getRound() + ").";
        Player nextPlayer = EndTurnService.findNextUnpassedPlayer(game, player);
        if (nextPlayer != null && !game.isFowMode()) {
            if (nextPlayer == player) {
                text +=
                        "\n-# All other players are passed; you will take consecutive turns until you pass, ending the Action Phase.";
                if (player.getSecretsUnscored().containsKey("pe")
                        && "".equals(game.getStoredValue("autoProveEndurance_" + player.getFaction()))) {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green(
                            "autoProveEndurance_yes", "Queue Prove Endurance", CardEmojis.SecretObjectiveAlt));
                    buttons.add(Buttons.red("autoProveEndurance_no", "Decline Prove Endurance", "🙅"));
                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCardsInfoThread(),
                            player.getRepresentation()
                                    + " All other players have passed. As such, when you pass, you could score _Prove Endurance_."
                                    + " You may use these buttons to queue scoring this when you pass (or not).",
                            buttons);
                }
            } else {
                String ping = UserSettingsManager.get(nextPlayer.getUserID()).isPingOnNextTurn()
                        ? nextPlayer.getRepresentationUnfogged()
                        : nextPlayer.getRepresentationNoPing();
                int numUnpassed = -2;
                boolean anyPassed = false;
                for (Player p2 : game.getRealPlayers()) {
                    numUnpassed += p2.isPassed() || p2.isEliminated() ? 0 : 1;
                    anyPassed |= p2.isPassed() || p2.isEliminated();
                }
                text += "\n-# " + ping + " will start their turn once you've ended yours. ";
                if (!anyPassed) {
                    text += "All players are yet to pass.";
                } else if (numUnpassed == 0) {
                    text += "No other players are unpassed.";
                } else {
                    text += numUnpassed + " other player" + (numUnpassed == 1 ? " is" : "s are") + " still unpassed.";
                }
            }
        }

        String buttonText = "Use buttons to do your turn. ";
        if ("pbd1000".equalsIgnoreCase(game.getName()) || "pbd100two".equalsIgnoreCase(game.getName())) {
            buttonText +=
                    "Your strategy card initiative number is " + player.getSCs().toArray()[0] + ".";
        }
        List<Button> buttons = getStartOfTurnButtons(player, game, false, event);
        MessageChannel gameChannel =
                game.getMainGameChannel() == null ? event.getMessageChannel() : game.getMainGameChannel();

        game.updateActivePlayer(player);
        game.setPhaseOfGame("action");
        ButtonHelperFactionSpecific.resolveMilitarySupportCheck(player, game);
        SabotageService.startOfTurnSaboWindowReminders(game, player);
        boolean isFowPrivateGame = game.isFowMode();

        if (game.isShowBanners()) {
            BannerGenerator.drawFactionBanner(player);
        }
        game.removeStoredValue("violatedSystems");
        if (isFowPrivateGame) {
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, "started turn");

            MessageHelper.sendMessageToChannel(player.getPrivateChannel(), text);
            if (!goingToPass) {
                MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), buttonText, buttons);
                FowCommunicationThreadService.checkNewCommPartners(game, player);
            }
            if (getMissedSCFollowsText(game, player) != null
                    && !"".equalsIgnoreCase(getMissedSCFollowsText(game, player))) {
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(), getMissedSCFollowsText(game, player));
            }
            reviveInfantryII(player);
            ButtonHelperFactionSpecific.resolveKolleccAbilities(player, game);
            ButtonHelperFactionSpecific.resolveMykoMechCheck(player, game);

            game.resetListOfTilesPinged();

        } else {
            MessageHelper.sendMessageToChannel(gameChannel, text);
            if (getMissedSCFollowsText(game, player) != null
                    && !"".equalsIgnoreCase(getMissedSCFollowsText(game, player))) {
                MessageHelper.sendMessageToChannel(gameChannel, getMissedSCFollowsText(game, player));
            }
            reviveInfantryII(player);
            if (!goingToPass) {
                MessageHelper.sendMessageToChannelWithButtons(gameChannel, buttonText, buttons);
            }
            ButtonHelperFactionSpecific.resolveMykoMechCheck(player, game);
            ButtonHelperFactionSpecific.resolveKolleccAbilities(player, game);
        }
        if (!game.getStoredValue("futureMessageFor" + player.getFaction()).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + " you left yourself the following message: \n"
                            + game.getStoredValue("futureMessageFor" + player.getFaction()));
            game.setStoredValue("futureMessageFor" + player.getFaction(), "");
        }
        for (Player p2 : game.getRealPlayers()) {
            if (!game.getStoredValue("futureMessageFor_" + player.getFaction() + "_" + p2.getFaction())
                    .isEmpty()) {
                String msg2 = "This is a message sent from the past:\n"
                        + game.getStoredValue("futureMessageFor_" + player.getFaction() + "_" + p2.getFaction());
                MessageHelper.sendMessageToChannel(
                        p2.getCardsInfoThread(), p2.getRepresentationUnfogged() + " your future message got delivered");
                WhisperService.sendWhisper(game, p2, player, msg2, "n", p2.getCardsInfoThread(), event.getGuild());
                game.setStoredValue("futureMessageFor_" + player.getFaction() + "_" + p2.getFaction(), "");
            }
        }

        if (!game.isFowMode()
                && (game.playerHasLeaderUnlockedOrAlliance(player, "redcreusscommander")
                        || game.playerHasLeaderUnlockedOrAlliance(player, "crimsoncommander"))
                && player.getCommodities() > 0) {
            for (Player p2 : game.getRealPlayers()) {
                if (!p2.equals(player)
                        && !p2.getAllianceMembers().contains(player.getFaction())
                        && (game.playerHasLeaderUnlockedOrAlliance(p2, "redcreusscommander")
                                || game.playerHasLeaderUnlockedOrAlliance(p2, "crimsoncommander"))
                        && p2.getCommodities() > 0
                        && player.getNeighbouringPlayers(true).contains(p2)) {
                    List<Button> buttonsRedCreuss = new ArrayList<>();
                    buttonsRedCreuss.add(Buttons.green(
                            player.getFinsFactionCheckerPrefix() + "redCreussWashFull_" + p2.getUserID(),
                            "Full Wash",
                            MiscEmojis.Wash));
                    buttonsRedCreuss.add(Buttons.blue(
                            player.getFinsFactionCheckerPrefix() + "redCreussWashPartial_" + p2.getUserID(),
                            "Partial Wash",
                            MiscEmojis.Wash));
                    buttonsRedCreuss.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCorrectChannel(),
                            player.getRepresentationUnfogged() + ", both you and " + p2.getRepresentationNoPing()
                                    + " have commodities."
                                    + " You may use these buttons to wash them, should you both agree."
                                    + "\n-# \"Partial Wash\" will only swap commodities for an equal number of commodities. \"Full Wash\" will also swap trade goods, to wash as many commodities as possible."
                                    + " If both player have the same number of commodities, the buttons are identical.",
                            buttonsRedCreuss);
                }
            }
        }
        if (game.getStoredValue("ExtremeDuress").equalsIgnoreCase(player.getColor()) && player.hasUnplayedSCs()) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2.getPlayableActionCards().contains("extremeduress")) {
                    game.removeStoredValue("ExtremeDuress");
                    ActionCardHelper.playAC(event, game, p2, "extremeduress", game.getMainGameChannel());
                    List<Button> buttons2 = new ArrayList<>();
                    buttons2.add(Buttons.red(
                            player.getFinsFactionCheckerPrefix() + "concedeToED_" + p2.getFaction(),
                            "Lose Action Cards, Give Trade Goods, And Show Secrets"));
                    buttons2.add(
                            Buttons.green("deleteButtons", "Give In And Play Strategy Card (or Sabo Extreme Duress)"));
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentation() + ", please resolve _Extreme Duress_.",
                            buttons2);
                }
            }
        }
        if (game.getStoredValue("Crisis Target").equalsIgnoreCase(player.getColor())) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2.getPlayableActionCards().contains("crisis")) {
                    game.removeStoredValue("Crisis Target");
                    ActionCardHelper.playAC(event, game, p2, "crisis", game.getMainGameChannel());
                    List<Button> buttons2 = new ArrayList<>();
                    buttons2.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "turnEnd", "End Turn"));
                    buttons2.add(Buttons.green("deleteButtons", "Delete These (If Crisis Was Sabo'd)"));
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentation() + ", please resolve _Crisis_.",
                            buttons2);
                }
            }
        }
        if (game.getStoredValue("Stasis Target").equalsIgnoreCase(player.getColor())) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2.getPlayableActionCards().contains("tf-stasis")) {
                    game.removeStoredValue("Stasis Target");
                    ActionCardHelper.playAC(event, game, p2, "tf-stasis", game.getMainGameChannel());
                    List<Button> buttons2 = new ArrayList<>();
                    buttons2.add(ButtonHelper.getEndTurnButton(game, player));
                    buttons2.add(Buttons.green("deleteButtons", "Delete These (If Stasis Was Sabo'd)"));
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentation() + ", please resolve _Stasis_.",
                            buttons2);
                }
            }
        }

        if (goingToPass) {
            PassService.passPlayerForRound(event, game, player, true);
        } else {
            if (player.isNpc()) {

                for (Integer SC : player.getSCs()) {
                    if (!game.getPlayedSCs().contains(SC)) {
                        PlayStrategyCardService.playSC(event, SC, game, game.getMainGameChannel(), player);
                        return;
                    }
                }
            }
        }
    }

    public static void reviveInfantryII(Player player) {
        Game game = player.getGame();
        if (player.getStasisInfantry() > 0 && !player.hasUnit("tf-yinclone")) {
            if (!ButtonHelper.getPlaceStatusInfButtons(game, player).isEmpty()) {
                List<Button> buttons = ButtonHelper.getPlaceStatusInfButtons(game, player);
                String msg = "Use buttons to revive infantry. You have " + player.getStasisInfantry()
                        + " infantry left to revive.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
            } else {
                String msg = player.getRepresentation() + ", you had infantry II to be revived, but";
                msg += " the bot couldn't find any planets you control in your home system to place them on";
                msg += ", so per the rules they now disappear into the ether.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                player.setStasisInfantry(0);
            }
        }

        if (!game.getStoredValue("pathOf" + player.getFaction()).isEmpty()) {
            String msg1 = player.getRepresentation() + "The Starlit path points you towards a "
                    + game.getStoredValue("pathOf" + player.getFaction()) + ".";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg1);
            String msg = player.getRepresentation() + " use buttons to either accept or refuse the path";
            List<Button> buttons = new ArrayList<>();
            game.removeStoredValue("pathOf" + player.getFaction());
            buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "acceptPath", "Accept Path"));
            buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "declinePath", "Refuse Path"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        }
    }

    public static String getMissedSCFollowsText(Game game, Player player) {
        if (!game.isStratPings()) return null;
        boolean sendReminder = false;

        if (!player.isRealPlayer()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(player.getRepresentationUnfogged());
        sb.append(" Please resolve these before doing anything else:\n");
        for (int sc : game.getPlayedSCsInOrder(player)) {
            if (game.getStrategyCardModelByInitiative(sc)
                    .map(strat -> "te6warfare".equals(strat.getAlias()))
                    .orElse(false)) {
                if (player != game.getActivePlayer()) {
                    // skip warning for warfare if we are presently resolving warfare
                    continue;
                }
            }
            if ("pbd1000".equalsIgnoreCase(game.getName()) || "pbd100two".equalsIgnoreCase(game.getName())) {
                String num = sc + "";
                num = num.substring(num.length() - 1);
                for (Integer sc2 : player.getSCs()) {
                    String num2 = sc2 + "";
                    num2 = num2.substring(num2.length() - 1);
                    if (!num2.equalsIgnoreCase(num) && !player.hasFollowedSC(sc)) {
                        player.addFollowedSC(sc);
                    }
                }
            }
            if (!player.hasFollowedSC(sc)) {
                sb.append("> ").append(game.getSCEmojiWordRepresentation(sc));
                if (!game.getStoredValue("scPlay" + sc).isEmpty()) {
                    sb.append(" ").append(game.getStoredValue("scPlay" + sc));
                }
                sb.append("\n");
                sendReminder = true;
            }
        }
        sb.append("You currently have ")
                .append(player.getStrategicCC())
                .append(" command token")
                .append(player.getStrategicCC() == 1 ? "" : "s")
                .append(" in your strategy pool.");
        return sendReminder ? sb.toString() : null;
    }

    public static List<Button> getStartOfTurnButtons(
            Player player, Game game, boolean doneActionThisTurn, GenericInteractionCreateEvent event) {
        return getStartOfTurnButtons(player, game, doneActionThisTurn, event, false);
    }

    public static List<Button> getStartOfTurnButtons(
            Player player,
            Game game,
            boolean doneActionThisTurn,
            GenericInteractionCreateEvent event,
            boolean confirmed2ndAction) {
        if (!doneActionThisTurn) {
            for (Player p2 : game.getRealPlayers()) {
                if (!game.getStoredValue(p2.getFaction() + "graviton").isEmpty()) {
                    game.setStoredValue(p2.getFaction() + "graviton", "");
                }
            }
        }
        String finChecker = player.getFinsFactionCheckerPrefix();
        game.setDominusOrb(false);
        List<Button> startButtons = new ArrayList<>();
        boolean hadAnyUnplayedSCs = false;

        if (doneActionThisTurn && player.hasTech("fl")) {
            confirmed2ndAction = true;
        }
        if (!doneActionThisTurn || confirmed2ndAction) {
            Button tacticalAction =
                    Buttons.green(finChecker + "tacticalAction", "Tactical Action (" + player.getTacticalCC() + ")");
            List<Button> acButtons = ActionCardHelper.getActionPlayActionCardButtons(player);
            int numOfComponentActions = ComponentActionHelper.getAllPossibleCompButtons(game, player, event)
                            .size()
                    - 3
                    - acButtons.size();
            if (game.isFowMode()) {
                numOfComponentActions += acButtons.size();
            }
            if (IsPlayerElectedService.isPlayerElected(player.getGame(), player, "censure")
                    || IsPlayerElectedService.isPlayerElected(player.getGame(), player, "absol_censure")) {
                numOfComponentActions += 1;
            }
            Button componentAction =
                    Buttons.green(finChecker + "componentAction", "Component Action (" + numOfComponentActions + ")");

            startButtons.add(tacticalAction);
            startButtons.add(componentAction);

            for (Integer SC : player.getSCs()) {
                if (!game.getPlayedSCs().contains(SC)) {
                    hadAnyUnplayedSCs = true;
                    String name = Helper.getSCName(SC, game);
                    if ("pbd1000".equalsIgnoreCase(game.getName())) {
                        name += "(" + SC + ")";
                    }
                    Button strategicAction = Buttons.green(
                            finChecker + "strategicAction_" + SC, "Play " + name, CardEmojis.getSCFrontFromInteger(SC));
                    startButtons.add(strategicAction);
                }
            }
            String prefix = "componentActionRes_";
            for (Leader leader : player.getLeaders()) {
                if (!leader.isExhausted() && !leader.isLocked()) {
                    String leaderID = leader.getId();
                    LeaderModel leaderModel = Mapper.getLeader(leaderID);
                    if (leaderModel == null) {
                        continue;
                    }
                    String leaderName = leaderModel.getName();
                    String leaderAbilityWindow = leaderModel.getAbilityWindow();
                    TI4Emoji leaderEmoji = LeaderEmojis.getLeaderEmoji(leader);
                    if ("ACTION:".equalsIgnoreCase(leaderAbilityWindow) || leaderName.contains("Ssruu")) {
                        if (leaderName.contains("Ssruu")) {
                            String led = "naaluagent";
                            if (player.hasExternalAccessToLeader(led)) {
                                Button lButton = Buttons.gray(
                                        finChecker + prefix + "leader_" + led,
                                        "Use " + leaderName + " as Naalu Agent",
                                        leaderEmoji);
                                startButtons.add(lButton);
                            }
                        } else {
                            if ("naaluagent".equalsIgnoreCase(leaderID)) {
                                Button lButton = Buttons.gray(
                                        finChecker + prefix + "leader_" + leaderID, "Use " + leaderName, leaderEmoji);
                                startButtons.add(lButton);
                            }
                        }
                    } else if ("mahactcommander".equalsIgnoreCase(leaderID)
                            && player.getTacticalCC() > 0
                            && !ButtonHelper.getTilesWithYourCC(player, game, event)
                                    .isEmpty()) {
                        Button lButton =
                                Buttons.gray(finChecker + "mahactCommander", "Use Mahact Commander", leaderEmoji);
                        startButtons.add(lButton);
                    }
                }
            }
        }

        if (!hadAnyUnplayedSCs && !doneActionThisTurn) {
            if (player.hasLeaderUnlocked("ralnelhero")) {
                if (game.getStoredValue("ralnelHero").isEmpty()) {
                    String presetRalnelHero =
                            "You have Director Nel, the Ral Nel hero, unlocked. If you're not about to pass, you can ignore this message."
                                    + " Otherwise, you can use the preset button to automatically use your hero when the last player passes."
                                    + " Don't worry, you can always unset the preset later if you decide you don't want to use it.";

                    List<Button> ralnelHeroButtons = new ArrayList<>();
                    ralnelHeroButtons.add(Buttons.blue("resolvePreassignment_ralnelHero", "Preset Ral Nel Hero"));
                    ralnelHeroButtons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCardsInfoThread(), presetRalnelHero, ralnelHeroButtons);
                }
            }

            if (player.getPlayableActionCards().contains("puppetsonastring")) {
                String msg =
                        "You have _Puppets On A String_ in your hand. If you're not about to pass, you can ignore this message."
                                + " Otherwise, you can use the preset button to automatically use it when the last player passes."
                                + " Don't worry, you can always unset the preset later if you decide you don't want to use it.";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("resolvePreassignment_Puppets On A String", "Pre-Play Puppets On A String"));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
            }

            startButtons.add(ButtonHelper.getPassButton(game, player));
            if (!game.isFowMode()) {
                for (Player p2 : game.getRealPlayers()) {
                    for (int sc : player.getSCs()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(p2.getRepresentationUnfogged());
                        sb.append(" You are getting this ping because **")
                                .append(Helper.getSCName(sc, game))
                                .append(
                                        "** has been played and now it is their turn again and you still haven't reacted. If you already reacted, check if your reaction got undone.");

                        if (!game.getStoredValue("scPlay" + sc).isEmpty()) {
                            sb.append(" Message link is: ")
                                    .append(game.getStoredValue("scPlay" + sc))
                                    .append(".\n");
                        }
                        sb.append("You currently have ")
                                .append(player.getStrategicCC())
                                .append(" command token")
                                .append(player.getStrategicCC() == 1 ? "" : "s")
                                .append(" in your strategy pool.");
                        if (!player.hasFollowedSC(sc)) {
                            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), sb.toString());
                        }
                    }
                }
            }
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "uydaicommander")) {
            startButtons.add(Buttons.gray("uydaiCommander", "Pay 1tg For Uydai Commander", FactionEmojis.uydai));
        }
        if (player.getPathTokenCounter() > 0) {
            startButtons.add(Buttons.gray("redistributePath", "Redistribute 1 CC With Path", FactionEmojis.uydai));
        }
        if (doneActionThisTurn) {
            ButtonHelperFactionSpecific.checkBlockadeStatusOfEverything(player, game, event);
            startButtons.add(ButtonHelper.getEndTurnButton(game, player));
            if (IsPlayerElectedService.isPlayerElected(game, player, "minister_war")) {
                startButtons.add(Buttons.gray(finChecker + "ministerOfWar", "Use Minister of War"));
            }
            if (!game.isJustPlayedComponentAC()) {
                AutoPingMetadataManager.setupQuickPing(game.getName());
            }
        } else {
            game.setJustPlayedComponentAC(false);
            Player nomad = Helper.getPlayerFromUnlockedBreakthrough(game, "nomadbt");
            if (nomad != null && (!game.isFowMode() || player.hasUnlockedBreakthrough("nomadbt"))) {
                String label = (player == nomad ? "Use" : "Use/Request") + " Thunder's Paradox";
                startButtons.add(Buttons.gray("startThundersParadox", label, FactionEmojis.Nomad));
            }
            if (player.hasTech("parasite-obs") || player.hasTech("tf-neuralparasite")) {
                if (!TeHelperTechs.neuralParasiteButtons(game, player).isEmpty()) {
                    startButtons.add(Buttons.gray(
                            "startNeuralParasite", "Use Neural Parasite (Mandatory)", FactionEmojis.Obsidian));
                }
            }

            if (player.hasTech("cm") || player.hasTech("tf-chaos")) {
                startButtons.add(
                        Buttons.gray(finChecker + "startChaosMapping", "Use Chaos Mapping", FactionEmojis.Saar));
            }

            if (player.ownsUnit("tf-ahksylfier")
                    && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "cruiser", false) > 0) {
                startButtons.add(
                        Buttons.gray("creussTFCruiserStep1_", "Use Ahk Syl Fier Ability", CardEmojis.MixedWormhole));
            }
            if (player.hasUnit("redtf_flagship")
                    && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "flagship", true) < 1) {
                startButtons.add(Buttons.gray(finChecker + "startRedTFDeploy", "Deploy Flagship", FactionEmojis.redtf));
            }
            if (game.isOrdinianC1Mode()
                    && !ButtonHelper.isCoatlHealed(game)
                    && player == ButtonHelper.getPlayerWhoControlsCoatl(game)) {
                startButtons.add(
                        Buttons.gray(finChecker + "healCoatl", "Heal Coatl (Costs 6 Resources)", FactionEmojis.Argent));
            }
            if (player.hasTech("dspharinf")
                    && !ButtonHelperFactionSpecific.getPharadnInf2ReleaseButtons(player, game)
                            .isEmpty()) {
                startButtons.add(Buttons.gray(
                        finChecker + "startPharadnInfRevive", "Release 1 Infantry", FactionEmojis.pharadn));
            }
            if (player.hasUnit("tf-vortexer")
                    && !ButtonHelperFactionSpecific.getVortexerReleaseButtons(player, game)
                            .isEmpty()) {
                startButtons.add(Buttons.gray(
                        finChecker + "startVortexerRevive", "Release Vortexer Infantry/Fighters", FactionEmojis.Cabal));
            }
            if (player.hasTech("dscymiy") && !player.getExhaustedTechs().contains("dscymiy")) {
                startButtons.add(Buttons.gray(
                        finChecker + "exhaustTech_dscymiy", "Exhaust Recursive Worm", FactionEmojis.cymiae));
            }
            if (player.hasUnexhaustedLeader("florzenagent")
                    && !ButtonHelperAgents.getAttachments(game, player).isEmpty()) {
                startButtons.add(Buttons.green(
                        finChecker + "exhaustAgent_florzenagent_" + player.getFaction(),
                        "Use Florzen Agent",
                        FactionEmojis.florzen));
            }
            if (player.hasUnexhaustedLeader("vadenagent")) {
                startButtons.add(Buttons.gray(
                        finChecker + "exhaustAgent_vadenagent_" + player.getFaction(),
                        "Use Vaden Agent",
                        FactionEmojis.vaden));
            }
            if (player.hasAbility("laws_order") && !game.getLaws().isEmpty()) {
                startButtons.add(Buttons.gray(
                        player.getFinsFactionCheckerPrefix() + "useLawsOrder",
                        "Pay To Ignore Laws",
                        FactionEmojis.Keleres));
            }
            if ((player.hasTech("td") && !player.getExhaustedTechs().contains("td"))
                    || (player.hasTech("absol_td")
                            && !player.getExhaustedTechs().contains("absol_td"))) {
                startButtons.add(Buttons.gray(
                        finChecker + "exhaustTech_td", "Exhaust Transit Diodes", TechEmojis.CyberneticTech));
            }
            if (player.hasUnexhaustedLeader("kolleccagent")) {
                startButtons.add(Buttons.gray(
                        finChecker + "exhaustAgent_kolleccagent", "Use Kollecc Agent", FactionEmojis.kollecc));
            }
        }
        if (player.hasTech("pa")
                && ButtonHelper.getPsychoTechPlanets(game, player).size() > 1) {
            startButtons.add(
                    Buttons.green(finChecker + "getPsychoButtons", "Use Psychoarcheology", TechEmojis.BioticTech));
        }
        if (player.hasTechReady("dsuydag")) {
            startButtons.add(Buttons.green(
                    finChecker + "exhaustTech_dsuydag", "Exhaust Messiah Protocols", TechEmojis.BioticTech));
        }

        Button transaction = Buttons.blue("transaction", "Transaction");
        startButtons.add(transaction);
        Button modify = Buttons.gray("getModifyTiles", "Modify Units");
        startButtons.add(modify);
        if (player.hasUnexhaustedLeader("hacanagent")) {
            startButtons.add(
                    Buttons.gray(finChecker + "exhaustAgent_hacanagent", "Use Hacan Agent", FactionEmojis.Hacan));
        }
        if (player.hasUnlockedBreakthrough("titansbt")) {
            startButtons.add(Buttons.gray("selectPlayerToSleeper", "Add a sleeper token", MiscEmojis.Sleeper));
        }
        if (player.hasRelicReady("superweaponavailyn")) {
            startButtons.add(Buttons.gray(
                    finChecker + "exhaustSuperweapon_availyn",
                    "Produce 3 Fighters With Availyn",
                    FactionEmojis.belkosea));
        }
        if (player.hasUnexhaustedLeader("pharadnagent")) {
            startButtons.add(
                    Buttons.gray(finChecker + "exhaustAgent_pharadnagent", "Use Pharadn Agent", FactionEmojis.pharadn));
        }
        if (player.hasRelicReady("e6-g0_network")) {
            startButtons.add(Buttons.green(
                    finChecker + "exhauste6g0network", "Exhaust E6-G0 Network Relic to Draw 1 Acton Card"));
        }
        if (player.hasUnexhaustedLeader("nekroagent") && player.getAcCount() > 0) {
            startButtons.add(
                    Buttons.gray(finChecker + "exhaustAgent_nekroagent", "Use Nekro Agent", FactionEmojis.Nekro));
        }
        if (player.hasReadyBreakthrough("lanefirbt")) {
            startButtons.add(Buttons.gray("useLanefirBt", "Use Lanefir Breakthrough", FactionEmojis.lanefir));
        }
        if (player.hasUnexhaustedLeader("hyperagent")) {
            startButtons.add(Buttons.gray(
                    "getAgentSelection_hyperagent", "Use Hyper Genome on Someone Else", FactionEmojis.Mentak));
        }

        if (game.isVeiledHeartMode()
                && !game.getStoredValue("veiledCards" + player.getFaction()).isEmpty()) {
            startButtons.add(Buttons.green("revealVeiledCards", "Reveal Veiled Cards"));
        }

        GameMessageManager.remove(game.getName(), GameMessageType.TURN).ifPresent(messageId -> game.getMainGameChannel()
                .deleteMessageById(messageId)
                .queue(Consumers.nop(), BotLogger::catchRestError));
        if (game.isFowMode()) {
            startButtons.add(Buttons.gray("showGameAgain", "Refresh Map"));
        } else {
            startButtons.add(Buttons.gray("showMap", "Show Map"));
            startButtons.add(Buttons.gray("showPlayerAreas", "Show Player Areas"));
        }

        if (!confirmed2ndAction && doneActionThisTurn) {

            startButtons.add(Buttons.red(finChecker + "confirmSecondAction", "Use Ability To Do Another Action"));
        }
        return startButtons;
    }
}
