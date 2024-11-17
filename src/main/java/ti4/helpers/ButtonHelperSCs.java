package ti4.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import ti4.buttons.Buttons;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands.status.ScorePublic;
import ti4.commands.tech.GetTechButton;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;

public class ButtonHelperSCs {

    @ButtonHandler("diploRefresh2")
    public static void diploRefresh2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String messageID = event.getMessageId();
        boolean used = addUsedSCPlayer(messageID, game, player, event, "");
        StrategyCardModel scModel = null;
        for (int scNum : player.getUnfollowedSCs()) {
            if (game.getStrategyCardModelByInitiative(scNum).get().usesAutomationForSCID("pok2diplomacy")) {
                scModel = game.getStrategyCardModelByInitiative(scNum).get();
            }
        }
        if (scModel == null) {
            scModel = game.getStrategyCardModelByName("diplomacy").orElse(null);
        }
        if (!used && scModel != null && scModel.usesAutomationForSCID("pok2diplomacy")
            && !player.getFollowedSCs().contains(scModel.getInitiative())
            && game.getPlayedSCs().contains(scModel.getInitiative())) {
            int scNum = scModel.getInitiative();
            player.addFollowedSC(scNum, event);
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
            if (player.getStrategicCC() > 0) {
                ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event,
                    "followed diplomacy");
            }
            String message = deductCC(player, event);
            ButtonHelper.addReaction(event, false, false, message, "");
        }
        if (scModel != null && !player.getFollowedSCs().contains(scModel.getInitiative())) {
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scModel.getInitiative(), game, event);
        }
        ButtonHelper.addReaction(event, false, false, "", "");
        String message = player.getRepresentationUnfogged() + " Click the names of the planets you wish to ready";

        List<Button> buttons = Helper.getPlanetRefreshButtons(event, player, game);
        Button doneRefreshing = Buttons.red("deleteButtons_diplomacy", "Done Readying Planets"); // spitItOut
        buttons.add(doneRefreshing);
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(), message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
        }
        if (player.hasAbility("peace_accords")) {
            List<Button> buttons2 = ButtonHelperAbilities.getXxchaPeaceAccordsButtons(game, player,
                event, player.getFinsFactionCheckerPrefix());
            if (!buttons2.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " use buttons to resolve peace accords", buttons2);
            }
        }

    }

    @ButtonHandler("nekroFollowTech")
    public static void nekroFollowTech(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        boolean used = addUsedSCPlayer(event.getMessageId(), game, player, event, "");
        StrategyCardModel scModel = null;
        for (int scNum : player.getUnfollowedSCs()) {
            if (game.getStrategyCardModelByInitiative(scNum).get().usesAutomationForSCID("pok7technology")) {
                scModel = game.getStrategyCardModelByInitiative(scNum).get();
            }
        }
        if (scModel == null) {
            scModel = game.getStrategyCardModelByName("technology").orElse(null);
        }

        if (!used && scModel != null && scModel.usesAutomationForSCID("pok7technology")
            && !player.getFollowedSCs().contains(scModel.getInitiative())
            && game.getPlayedSCs().contains(scModel.getInitiative())) {

            int scNum = scModel.getInitiative();
            player.addFollowedSC(scNum, event);
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
            if (player.getStrategicCC() > 0) {
                ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed tech");
            }
            String message = deductCC(player, event);
            ButtonHelper.addReaction(event, false, false, message, "");
        }
        Button getTactic = Buttons.green("increase_tactic_cc", "Gain 1 Tactic CC");
        Button getFleet = Buttons.green("increase_fleet_cc", "Gain 1 Fleet CC");
        Button getStrat = Buttons.green("increase_strategy_cc", "Gain 1 Strategy CC");
        Button exhaust = Buttons.red("nekroTechExhaust", "Exhaust Planets");
        Button doneGainingCC = Buttons.red("deleteButtons_technology", "Done Gaining CCs");
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        String message = player.getRepresentationUnfogged() + "! Your current CCs are " + player.getCCRepresentation() + ". Use buttons to gain CCs";
        Button resetCC = Buttons.gray(player.getFinsFactionCheckerPrefix() + "resetCCs", "Reset CCs");
        List<Button> buttons = Arrays.asList(getTactic, getFleet, getStrat, doneGainingCC, resetCC);
        List<Button> buttons2 = Collections.singletonList(exhaust);
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Exhaust using this", buttons2);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), "Exhaust using this", buttons2);
        }
    }

    @ButtonHandler("score_imperial")
    public static void scoreImperial(Game game, Player player, ButtonInteractionEvent event) {
        if (player == null || game == null) {
            return;
        }
        if (!player.controlsMecatol(true)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Only the player who controls Mecatol Rex may score the Imperial point.");
            return;
        }
        boolean used = addUsedSCPlayer(event.getMessageId() + "score_imperial", game, player, event,
            " scored Imperial");
        if (used) {
            return;
        }
        ButtonHelperFactionSpecific.KeleresIIHQCCGainCheck(player, game);
        ScorePublic.scorePO(event, player.getCorrectChannel(), game, player, 0);
    }

    @ButtonHandler("sc_trade_follow")
    @ButtonHandler("sc_follow_trade")
    public static void followTrade(Game game, Player player, ButtonInteractionEvent event) {
        boolean used = addUsedSCPlayer(event.getMessageId(), game, player, event, "");
        if (used) {
            return;
        }
        StrategyCardModel scModel = null;
        for (int scNum : player.getUnfollowedSCs()) {
            if (game.getStrategyCardModelByInitiative(scNum).get().usesAutomationForSCID("pok5trade")) {
                scModel = game.getStrategyCardModelByInitiative(scNum).get();
            }
        }
        if (scModel == null) {
            scModel = game.getStrategyCardModelByName("trade").orElse(null);
        }
        int tradeInitiative = scModel.getInitiative();

        if (player.getStrategicCC() > 0) {
            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed Trade");
        }
        String message = deductCC(player, event);
        if (!player.getFollowedSCs().contains(tradeInitiative)) {
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, tradeInitiative, game, event);
        }
        player.addFollowedSC(tradeInitiative, event);
        ButtonHelperStats.replenishComms(event, game, player, true);

        ButtonHelper.addReaction(event, false, false, message, "");
        ButtonHelper.addReaction(event, false, false, "Replenishing Commodities", "");

    }

    @ButtonHandler("sc_draw_so")
    public static void scDrawSO(Game game, Player player, ButtonInteractionEvent event) {
        String messageID = event.getMessageId();
        boolean used = addUsedSCPlayer(messageID, game, player, event, "");
        StrategyCardModel scModel = null;
        for (int scNum : player.getUnfollowedSCs()) {
            if (game.getStrategyCardModelByInitiative(scNum).get().usesAutomationForSCID("pok8imperial")) {
                scModel = game.getStrategyCardModelByInitiative(scNum).get();
            }
        }
        if (scModel == null) {
            scModel = game.getStrategyCardModelByName("imperial").orElse(null);
        }
        if (!player.getFollowedSCs().contains(scModel.getInitiative())) {
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scModel.getInitiative(), game, event);
        }
        if (!game.getPhaseOfGame().contains("agenda") && !used && scModel.usesAutomationForSCID("pok8imperial") && !player.getFollowedSCs().contains(scModel.getInitiative()) && game.getPlayedSCs().contains(scModel.getInitiative())) {
            int scNum = scModel.getInitiative();
            player.addFollowedSC(scNum, event);
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
            if (player.getStrategicCC() > 0) {
                ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed Imperial");
            }
            String message = deductCC(player, event);
            ButtonHelper.addReaction(event, false, false, message, "");
        }
        boolean used2 = addUsedSCPlayer(messageID + "so", game, player, event,
            " Drew a " + Emojis.SecretObjective);
        if (used2) {
            return;
        }

        Player imperialHolder = Helper.getPlayerWithThisSC(game, scModel.getInitiative());
        if (game.getPhaseOfGame().contains("agenda")) {
            imperialHolder = game.getPlayer(game.getSpeakerUserID());
        }
        String key2 = "queueToDrawSOs";
        String key3 = "potentialBlockers";
        String message = "Drew A Secret Objective";
        for (Player player2 : Helper.getSpeakerOrderFromThisPlayer(imperialHolder, game)) {
            if (player2 == player) {
                game.drawSecretObjective(player.getUserID());
                if (player.hasAbility("plausible_deniability")) {
                    game.drawSecretObjective(player.getUserID());
                    message = message + ". Drew a second SO due to Plausible Deniability";
                }
                SecretObjectiveHelper.sendSecretObjectiveInfo(game, player, event);
                break;
            }
            if (game.getStoredValue(key3).contains(player2.getFaction() + "*")) {
                message = "Wants to draw an SO but has people ahead of them in speaker order who need to resolve first. They have been queued and will automatically draw an SO when everyone ahead of them is clear."
                    + " They may cancel this by hitting 'No Follow'";
                if (!game.isFowMode()) {
                    message = message + "\n" + player2.getRepresentationUnfogged()
                        + " is the one the game is currently waiting on. Remember it is not enough to simply draw an SO, they will also need to discard one. ";
                }
                game.setStoredValue(key2,
                    game.getStoredValue(key2) + player.getFaction() + "*");
                break;
            }
        }
        ButtonHelper.addReaction(event, false, false, message, "");
    }

    @ButtonHandler("sc_refresh")
    public static void refresh(Game game, Player player, ButtonInteractionEvent event) {
        boolean used = addUsedSCPlayer(event.getMessageId(), game, player, event, "Replenish");
        if (used) {
            return;
        }
        int initComm = player.getCommodities();
        player.setCommodities(player.getCommoditiesTotal());
        StrategyCardModel scModel = null;
        for (int scNum : player.getUnfollowedSCs()) {
            if (game.getStrategyCardModelByInitiative(scNum).get().usesAutomationForSCID("pok5trade")) {
                scModel = game.getStrategyCardModelByInitiative(scNum).get();
            }
        }
        if (scModel == null) {
            scModel = game.getStrategyCardModelByName("trade").orElse(null);
        }
        int tradeInitiative = scModel.getInitiative();
        if (!player.getFollowedSCs().contains(tradeInitiative)) {
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, tradeInitiative, game, event);
        }
        player.addFollowedSC(tradeInitiative, event);
        ButtonHelper.addReaction(event, false, false, "Replenishing Commodities", "");
        ButtonHelper.resolveMinisterOfCommerceCheck(game, player, event);
        ButtonHelperAgents.cabalAgentInitiation(game, player);
        ButtonHelperStats.afterGainCommsChecks(game, player, player.getCommodities() - initComm);

    }

    @ButtonHandler("sc_refresh_and_wash")
    public static void refreshAndWash(Game game, Player player, ButtonInteractionEvent event) {
        String messageID = event.getMessageId();
        if (player.hasAbility("military_industrial_complex")) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationUnfogged()
                + " since you cannot send players commodities due to your faction ability, washing here seems likely an error. Nothing has been processed as a result. Try a different route if this correction is wrong");
            return;
        }

        if (!player.getPromissoryNotes().containsKey(player.getColor() + "_ta")) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentationUnfogged()
                + " since you do not currently hold your TA, washing here seems likely an error and will mess with the TA resolution. Nothing has been processed as a result. Try a different route of washing your comms if this correction is wrong");
            return;
        }

        boolean used = addUsedSCPlayer(messageID, game, player, event, "Replenish and Wash");
        if (used) {
            return;
        }
        int washedCommsPower = player.getCommoditiesTotal() + player.getTg();
        int commoditiesTotal = player.getCommoditiesTotal();
        int tg = player.getTg();
        player.setTg(tg + commoditiesTotal);
        ButtonHelperAbilities.pillageCheck(player, game);
        player.setCommodities(0);

        StrategyCardModel scModel = null;
        for (int scNum : player.getUnfollowedSCs()) {
            if (game.getStrategyCardModelByInitiative(scNum).get().usesAutomationForSCID("pok5trade")) {
                scModel = game.getStrategyCardModelByInitiative(scNum).get();
            }
        }
        if (scModel == null) {
            scModel = game.getStrategyCardModelByName("trade").orElse(null);
        }
        int tradeInitiative = scModel.getInitiative();
        if (!player.getFollowedSCs().contains(tradeInitiative)) {
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, tradeInitiative, game, event);
        }
        player.addFollowedSC(tradeInitiative, event);
        for (Player p2 : game.getRealPlayers()) {
            if (p2.getSCs().contains(tradeInitiative) && p2.getCommodities() > 0) {
                if (p2.getCommodities() > washedCommsPower) {
                    p2.setTg(p2.getTg() + washedCommsPower);
                    p2.setCommodities(p2.getCommodities() - washedCommsPower);
                    ButtonHelperAbilities.pillageCheck(p2, game);
                    MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                        p2.getRepresentationUnfogged() + " " + washedCommsPower
                            + " of your commodities got washed in the process of washing "
                            + player.getFactionEmojiOrColor());
                } else {
                    p2.setTg(p2.getTg() + p2.getCommodities());
                    p2.setCommodities(0);
                    ButtonHelperAbilities.pillageCheck(p2, game);
                    MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                        p2.getRepresentationUnfogged()
                            + " your commodities got washed in the process of washing "
                            + player.getFactionEmojiOrColor());
                }
                ButtonHelperFactionSpecific.resolveDarkPactCheck(game, player, p2,
                    player.getCommoditiesTotal());
                ButtonHelperFactionSpecific.resolveDarkPactCheck(game, p2, player,
                    p2.getCommoditiesTotal());
            } else {
                if (p2.getSCs().contains(tradeInitiative)) {
                    ButtonHelperFactionSpecific.resolveDarkPactCheck(game, player, p2,
                        player.getCommoditiesTotal());
                }
            }
            if (p2.getSCs().contains(tradeInitiative)) {
                TransactionHelper.checkTransactionLegality(game, player, p2);
            }
        }

        ButtonHelper.addReaction(event, false, false, "Replenishing and washing", "");
        ButtonHelper.resolveMinisterOfCommerceCheck(game, player, event);
        ButtonHelperAgents.cabalAgentInitiation(game, player);

    }

    @ButtonHandler("warfareBuild")
    public static void warfareBuild(Game game, Player player, ButtonInteractionEvent event) {
        String messageID = event.getMessageId();
        List<Button> buttons;
        boolean used = addUsedSCPlayer(messageID, game, player, event, "");
        StrategyCardModel scModel = null;
        for (int scNum : player.getUnfollowedSCs()) {
            if (game.getStrategyCardModelByInitiative(scNum).get().usesAutomationForSCID("pok6warfare")) {
                scModel = game.getStrategyCardModelByInitiative(scNum).get();
            }
        }
        if (scModel == null) {
            scModel = game.getStrategyCardModelByName("warfare").orElse(null);
        }
        if (!used && scModel != null && scModel.usesAutomationForSCID("pok6warfare")
            && !player.getFollowedSCs().contains(scModel.getInitiative())
            && game.getPlayedSCs().contains(scModel.getInitiative())) {
            int scNum = scModel.getInitiative();
            player.addFollowedSC(scNum, event);
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
            if (player.getStrategicCC() > 0) {
                ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed warfare");
            }
            String message = deductCC(player, event);
            ButtonHelper.addReaction(event, false, false, message, "");
        }
        Tile tile = player.getHomeSystemTile();
        buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "warfare", "place");
        int val = Helper.getProductionValue(player, game, tile, true);
        String message = player.getRepresentation()
            + " Use the buttons to produce. Reminder that when following warfare, you may only use 1 space dock in your home system. "
            + ButtonHelper.getListOfStuffAvailableToSpend(player, game, true) + "\n"
            + "You have " + val + " PRODUCTION value in this system.";
        if (val > 0 && game.playerHasLeaderUnlockedOrAlliance(player, "cabalcommander")) {
            message = message
                + ". You also have the That Which Molds Flesh, the Vuil'raith commander, which allows you to produce 2 fighters/infantry that don't count towards production limit. ";
        }
        if (val > 0 && ButtonHelper.isPlayerElected(game, player, "prophecy")) {
            message = message
                + "Reminder that you have Prophecy of Ixth and should produce 2 fighters if you want to keep it. Its removal is not automated.";
        }
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Produce Units",
                buttons);
        } else {
            MessageHelper.sendMessageToChannel(player.getPrivateChannel(), message);
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), "Produce Units",
                buttons);
        }

    }

    @ButtonHandler("construction_")
    public static void construction(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String messageID = event.getMessageId();
        boolean used = addUsedSCPlayer(messageID, game, player, event, "");
        StrategyCardModel scModel = null;
        for (int scNum : player.getUnfollowedSCs()) {
            if (game.getStrategyCardModelByInitiative(scNum).get().usesAutomationForSCID("pok4construction")) {
                scModel = game.getStrategyCardModelByInitiative(scNum).get();
            }
        }
        if (scModel == null) {
            scModel = game.getStrategyCardModelByName("construction").orElse(null);
        }
        boolean automationExists = scModel != null && scModel.usesAutomationForSCID("pok4construction");
        if (!used && scModel != null && !player.getFollowedSCs().contains(scModel.getInitiative())
            && automationExists && game.getPlayedSCs().contains(scModel.getInitiative())) {
            player.addFollowedSC(scModel.getInitiative(), event);
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scModel.getInitiative(), game, event);
            if (player.getStrategicCC() > 0) {
                ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed construction");
            }
            String message = deductCC(player, event);
            ButtonHelper.addReaction(event, false, false, message, "");
        }
        ButtonHelper.addReaction(event, false, false, "", "");
        String unit = buttonID.replace("construction_", "");
        String message = player.getRepresentationUnfogged() + " Click the name of the planet you wish to put your "
            + Emojis.getEmojiFromDiscord(unit) + " on for construction. If you are resolving the secondary, it will place a command counter in the system as well.";
        List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, unit, "place");
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
        }

    }

    @ButtonHandler("leadershipGenerateCCButtons")
    public static void leadershipGenerateCCButtons(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        int leadershipInitiative = 1;
        StrategyCardModel scModel = null;
        for (int scNum : player.getUnfollowedSCs()) {
            if (game.getStrategyCardModelByInitiative(scNum).get().usesAutomationForSCID("pok1leadership")) {
                scModel = game.getStrategyCardModelByInitiative(scNum).get();
            }
        }
        if (scModel == null) {
            scModel = game.getStrategyCardModelByName("leadership").orElse(null);
        }
        leadershipInitiative = scModel.getInitiative();

        if (!player.getFollowedSCs().contains(leadershipInitiative)) {
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, leadershipInitiative, game, event);
        }
        player.addFollowedSC(leadershipInitiative, event);
        String message = player.getRepresentationUnfogged() + " Click the names of the planets you wish to exhaust.";
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        Button doneExhausting = Buttons.red("deleteButtons_leadership", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        int ccCount = Helper.getCCCount(game, player.getColor());
        int limit = 16;
        if (!game.getStoredValue("ccLimit").isEmpty()) {
            limit = Integer.parseInt(game.getStoredValue("ccLimit"));
        }
        message = message + "\nYou have " + (limit - ccCount) + " CCs remaining that you could gain";
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
        }
        ButtonHelper.addReaction(event, false, false, "", "");
        message = player.getRepresentationUnfogged() + "! Your current CCs are " + player.getCCRepresentation() + ". Use buttons to gain CCs";
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        Button getTactic = Buttons.green(player.getFinsFactionCheckerPrefix() + "increase_tactic_cc", "Gain 1 Tactic CC");
        Button getFleet = Buttons.green(player.getFinsFactionCheckerPrefix() + "increase_fleet_cc", "Gain 1 Fleet CC");
        Button getStrat = Buttons.green(player.getFinsFactionCheckerPrefix() + "increase_strategy_cc", "Gain 1 Strategy CC");
        // Button exhaust = Buttons.red(finsFactionCheckerPrefix +
        // "leadershipExhaust", "Exhaust Planets");
        Button doneGainingCC = Buttons.red(player.getFinsFactionCheckerPrefix() + "deleteButtons_leadership", "Done Gaining CCs");
        Button resetCC = Buttons.gray(player.getFinsFactionCheckerPrefix() + "resetCCs", "Reset CCs");
        List<Button> buttons2 = Arrays.asList(getTactic, getFleet, getStrat, doneGainingCC, resetCC);
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons2);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons2);
        }

    }

    public static boolean addUsedSCPlayer(String messageID, Game game, Player player,
        @NotNull ButtonInteractionEvent event, String defaultText) {
        String players = game.getStoredValue(messageID + "SCReacts");
        boolean contains = players.contains(player.getFaction());
        game.setStoredValue(messageID + "SCReacts", players + "_" + player.getFaction());
        return contains;
    }

    @ButtonHandler("scepterE_follow_")
    @ButtonHandler("mahactA_follow_")
    public static void mahactAndScepterFollow(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String lastChar = StringUtils.right(event.getButton().getLabel(), 2).replace("#", "");
        boolean setStatus = true;
        int scNum = 1;
        try {
            scNum = Integer.parseInt(StringUtils.substringAfterLast(buttonID, "_"));
        } catch (NumberFormatException e) {
            try {
                scNum = Integer.parseInt(lastChar);
            } catch (NumberFormatException e2) {
                setStatus = false;
            }
        }
        if (setStatus) {
            if (!player.getFollowedSCs().contains(scNum)) {
                ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
            }
            player.addFollowedSC(scNum, event);
        }
        MessageChannel channel = ButtonHelper.getSCFollowChannel(game, player, scNum);
        if (buttonID.contains("mahact")) {
            MessageHelper.sendMessageToChannel(channel,
                player.getFactionEmojiOrColor() + " exhausted " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                    + "Jae Mir Kan, the Mahact" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " Agent, to follow " + Helper.getSCName(scNum, game));
            Leader playerLeader = player.unsafeGetLeader("mahactagent");
            if (playerLeader != null) {
                playerLeader.setExhausted(true);
                for (Player p2 : game.getPlayers().values()) {
                    for (Integer sc2 : p2.getSCs()) {
                        if (sc2 == scNum) {
                            List<Button> buttonsToRemoveCC = new ArrayList<>();
                            for (Tile tile : ButtonHelper.getTilesWithYourCC(p2, game, event)) {
                                buttonsToRemoveCC.add(Buttons.green(
                                    player.getFinsFactionCheckerPrefix() + "removeCCFromBoard_mahactAgent" + p2.getFaction() + "_" + tile.getPosition(),
                                    tile.getRepresentationForButtons(game, player)));
                            }
                            MessageHelper.sendMessageToChannelWithButtons(channel,
                                player.getRepresentationUnfogged() + " Use buttons to remove a CC", buttonsToRemoveCC);
                        }
                    }
                }
            }
        } else {
            MessageHelper.sendMessageToChannel(channel,
                player.getRepresentationUnfogged() + " exhausted Scepter of Silly Spelling to follow " + Helper.getSCName(scNum, game));
            player.addExhaustedRelic("emelpar");
        }
        Emoji emojiToUse = Emoji.fromFormatted(player.getFactionEmoji());

        if (channel instanceof ThreadChannel) {
            game.getActionsChannel().addReactionById(channel.getId(), emojiToUse).queue();
        } else {
            MessageHelper.sendMessageToChannel(channel,
                "Hey, something went wrong leaving a react, please just hit the no follow button on the strategy card to do so.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("sc_no_follow_")
    public static void scNoFollow(Game game, Player player, @NotNull ButtonInteractionEvent event, String buttonID) {
        String messageID = event.getMessageId();
        String lastchar = StringUtils.right(event.getButton().getLabel(), 2).replace("#", "");
        int scNum = 1;
        boolean setStatus = true;
        try {
            scNum = Integer.parseInt(StringUtils.substringAfterLast(buttonID, "_"));
        } catch (NumberFormatException e) {
            try {
                scNum = Integer.parseInt(lastchar);
            } catch (NumberFormatException e2) {
                setStatus = false;
            }
        }
        if (setStatus) {
            player.addFollowedSC(scNum, event);
            if (scNum == 7 || scNum / 10 == 7) {
                GetTechButton.postTechSummary(game);
            }
        }
        ButtonHelper.addReaction(event, false, false, "Not Following", "");
        String players = game.getStoredValue(messageID + "SCReacts");

        game.setStoredValue(messageID + "SCReacts", players.replace(player.getFaction(), ""));

        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(scNum).orElse(null);
        if (scModel == null) {
            return;
        }
        if ("pok8imperial".equals(scModel.getBotSCAutomationID())) {// HANDLE SO QUEUEING
            String key = "queueToDrawSOs";
            if (game.getStoredValue(key).contains(player.getFaction() + "*")) {
                game.setStoredValue(key, game.getStoredValue(key).replace(player.getFaction() + "*", ""));
            }
            key = "factionsThatAreNotDiscardingSOs";
            if (!game.getStoredValue(key).contains(player.getFaction() + "*")) {
                game.setStoredValue(key, game.getStoredValue(key) + player.getFaction() + "*");
            }
            key = "potentialBlockers";
            if (game.getStoredValue(key).contains(player.getFaction() + "*")) {
                game.setStoredValue(key, game.getStoredValue(key).replace(player.getFaction() + "*", ""));
                Helper.resolveQueue(game);
            }
        }
    }

    @ButtonHandler("sc_follow_")
    public static void scFollow(Game game, Player player, @NotNull ButtonInteractionEvent event, String buttonID) {
        String messageID = event.getMessageId();
        String lastchar = StringUtils.right(event.getButton().getLabel(), 2).replace("#", "");
        int scNum = 1;
        boolean setStatus = true;
        try {
            scNum = Integer.parseInt(StringUtils.substringAfterLast(buttonID, "_"));
        } catch (NumberFormatException e) {
            try {
                scNum = Integer.parseInt(lastchar);
            } catch (NumberFormatException e2) {
                setStatus = false;
            }
        }
        if (player != null && player.getSCs().contains(scNum)) {
            String message = player.getRepresentation()
                + " you currently hold this strategy card and therefore should not be spending a CC here.\nYou may override this protection by running `/player stats strategy_cc:-1`.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            return;
        }
        boolean used = addUsedSCPlayer(messageID, game, player, event, "");
        if (!used) {
            if (player.getStrategicCC() > 0) {
                ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed " + Helper.getSCName(scNum, game));
            }
            String message = deductCC(player, event);

            if (setStatus) {
                if (!player.getFollowedSCs().contains(scNum)) {
                    ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
                }
                player.addFollowedSC(scNum, event);
            }
            ButtonHelper.addReaction(event, false, false, message, "");
        }

    }

    @NotNull
    public static String deductCC(Player player, @NotNull ButtonInteractionEvent event) {
        int strategicCC = player.getStrategicCC();
        String message;
        if (strategicCC == 0) {
            message = " have 0 command tokens in strategy pool, can't follow.";
        } else {
            strategicCC--;
            player.setStrategicCC(strategicCC);
            message = " following strategy card, deducted 1 command tokens from strategy pool.";
        }
        return message;
    }

    @ButtonHandler("sc_ac_draw")
    public static void scACDraw(Game game, Player player, ButtonInteractionEvent event) {
        String messageID = event.getMessageId();
        boolean used2 = addUsedSCPlayer(messageID + "ac", game, player, event, "");
        if (used2) {
            return;
        }
        boolean used = addUsedSCPlayer(messageID, game, player, event, "");
        StrategyCardModel scModel = null;
        for (int scNum : player.getUnfollowedSCs()) {
            if (game.getStrategyCardModelByInitiative(scNum).get().usesAutomationForSCID("pok3politics")) {
                scModel = game.getStrategyCardModelByInitiative(scNum).get();
            }
        }
        if (scModel == null) {
            scModel = game.getStrategyCardModelByName("politics").orElse(null);
        }
        if (!used && scModel != null && scModel.usesAutomationForSCID("pok3politics")
            && !player.getFollowedSCs().contains(scModel.getInitiative())
            && game.getPlayedSCs().contains(scModel.getInitiative())) {
            int scNum = scModel.getInitiative();
            player.addFollowedSC(scNum, event);
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
            if (player.getStrategicCC() > 0) {
                ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed Politics");
            }
            String message = deductCC(player, event);
            ButtonHelper.addReaction(event, false, false, message, "");
        }
        boolean hasSchemingAbility = player.hasAbility("scheming");
        String message = hasSchemingAbility
            ? "Drew 3 Action Cards (Scheming) - please discard 1 action card from your hand"
            : "Drew 2 Action cards";
        int count = hasSchemingAbility ? 3 : 2;
        if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, count);
            message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";

        } else {
            for (int i = 0; i < count; i++) {
                game.drawActionCard(player.getUserID());
            }
            ActionCardHelper.sendActionCardInfo(game, player, event);
            ButtonHelper.checkACLimit(game, event, player);
        }

        ButtonHelper.addReaction(event, false, false, message, "");
        if (hasSchemingAbility) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " use buttons to discard",
                ActionCardHelper.getDiscardActionCardButtons(player, false));
        }
        CommanderUnlockCheck.checkPlayer(player, "yssaril");
        if (player.hasAbility("contagion")) {
            List<Button> buttons2 = ButtonHelperAbilities.getKyroContagionButtons(game, player,
                event, player.getFinsFactionCheckerPrefix());
            if (!buttons2.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + " use buttons to resolve contagion", buttons2);

                if (Helper.getDateDifference(game.getCreationDate(),
                    Helper.getDateRepresentation(1711997257707L)) > 0) {
                    MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        player.getRepresentationUnfogged()
                            + " use buttons to resolve contagion planet #2 (should not be the same as planet #1)",
                        buttons2);
                }
            }
        }
    }
}
