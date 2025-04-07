package ti4.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.StrategyCardModel;
import ti4.service.button.ReactionService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.RefreshLeaderService;
import ti4.service.objectives.ScorePublicObjectiveService;

public class ButtonHelperSCs {

    @ButtonHandler("diploRefresh2")
    public static void diploRefresh2(Game game, Player player, ButtonInteractionEvent event) {
        String messageID = event.getMessageId();
        boolean used = addUsedSCPlayer(messageID, game, player);
        StrategyCardModel scModel = null;
        for (int scNum : player.getUnfollowedSCs()) {
            if (game.getStrategyCardModelByInitiative(scNum).get().usesAutomationForSCID("pok2diplomacy")) {
                scModel = game.getStrategyCardModelByInitiative(scNum).get();
            }
        }
        if (scModel == null) {
            scModel = game.getStrategyCardModelByName("pok2diplomacy").orElse(null);
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
            String message = deductCC(game, player, scNum);
            ReactionService.addReaction(event, game, player, message);
        }
        if (scModel != null && !player.getFollowedSCs().contains(scModel.getInitiative())) {
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scModel.getInitiative(), game, event);
        }
        ReactionService.addReaction(event, game, player);
        String message = player.getRepresentationUnfogged() + " Click the names of the planets you wish to ready";

        List<Button> buttons = Helper.getPlanetRefreshButtons(player, game);
        Button doneRefreshing = Buttons.red("deleteButtons_diplomacy", "Done Readying Planets"); // spitItOut
        buttons.add(doneRefreshing);
        // if (!game.isFowMode()) {
        //     MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        // } else {
        //     MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
        // }
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, message, buttons);
        if (player.hasAbility("peace_accords")) {
            List<Button> buttons2 = ButtonHelperAbilities.getXxchaPeaceAccordsButtons(game, player,
                event, player.getFinsFactionCheckerPrefix());
            if (!buttons2.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " use buttons to resolve **Peace Accords**.", buttons2);
            }
        }

    }

    @ButtonHandler("nekroFollowTech")
    public static void nekroFollowTech(Game game, Player player, ButtonInteractionEvent event) {
        boolean used = addUsedSCPlayer(event.getMessageId(), game, player);
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
            String message = deductCC(game, player, scNum);
            ReactionService.addReaction(event, game, player, message);
        }
        Button getTactic = Buttons.green("increase_tactic_cc", "Gain 1 Tactic Token");
        Button getFleet = Buttons.green("increase_fleet_cc", "Gain 1 Fleet Token");
        Button getStrat = Buttons.green("increase_strategy_cc", "Gain 1 Strategy Token");
        Button exhaust = Buttons.red("nekroTechExhaust", "Exhaust Planets");
        Button doneGainingCC = Buttons.red("deleteButtons_technology", "Done Gaining Command Tokens");
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        String message = player.getRepresentationUnfogged() + ", your current command tokens are " + player.getCCRepresentation() + ". Use buttons to gain command tokens.";
        Button resetCC = Buttons.gray(player.getFinsFactionCheckerPrefix() + "resetCCs", "Reset Command Tokens");
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
        if (player == null || game == null) return;
        if (!player.controlsMecatol(true)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Only the player who controls Mecatol Rex may score the **Imperial** victory point.");
            return;
        }
        boolean used = addUsedSCPlayer(event.getMessageId() + "score_imperial", game, player);
        if (used) return;
        ButtonHelperFactionSpecific.keleresIIHQCCGainCheck(player, game);
        ScorePublicObjectiveService.scorePO(event, player.getCorrectChannel(), game, player, 0);
    }

    @ButtonHandler("sc_trade_follow")
    @ButtonHandler("sc_follow_trade")
    public static void followTrade(Game game, Player player, ButtonInteractionEvent event) {
        boolean used = addUsedSCPlayer(event.getMessageId(), game, player);
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
        int scNum = scModel.getInitiative();

        if (player.getStrategicCC() > 0) {
            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed Trade");
        }
        String message = deductCC(game, player, scNum);
        if (!player.getFollowedSCs().contains(scNum)) {
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
        }
        player.addFollowedSC(scNum, event);
        ButtonHelperStats.replenishComms(event, game, player, true);

        ReactionService.addReaction(event, game, player, message);
        ReactionService.addReaction(event, game, player, " has replenished commodities.");

    }

    @ButtonHandler("sc_draw_so")
    public static void scDrawSO(Game game, Player player, ButtonInteractionEvent event) {
        String messageID = event.getMessageId();
        boolean used = addUsedSCPlayer(messageID, game, player);
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
            String message = deductCC(game, player, scNum);
            ReactionService.addReaction(event, game, player, message);
        }
        boolean used2 = addUsedSCPlayer(messageID + "so", game, player);
        if (used2) return;

        Player imperialHolder = Helper.getPlayerWithThisSC(game, scModel.getInitiative());
        if (game.getPhaseOfGame().contains("agenda")) {
            imperialHolder = game.getPlayer(game.getSpeakerUserID());
        }
        String key2 = "queueToDrawSOs";
        String key3 = "potentialBlockers";
        String message = " drew a secret objective.";
        for (Player player2 : Helper.getSpeakerOrderFromThisPlayer(imperialHolder, game)) {
            if (player2 == player) {
                game.drawSecretObjective(player.getUserID());
                if (player.hasAbility("plausible_deniability")) {
                    game.drawSecretObjective(player.getUserID());
                    message += " Drew a second secret objective due to **Plausible Deniability**.";
                }
                SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
                break;
            }
            if (game.getStoredValue(key3).contains(player2.getFaction() + "*")) {
                message = " wishes to draw a secret objective but has people ahead of them in speaker order who need to resolve first."
                    + " They have been queued and will automatically draw a secret objective when everyone ahead of them is clear."
                    + " They may cancel this by hitting \"No Follow\".";
                if (!game.isFowMode()) {
                    message += "\n" + player2.getRepresentationUnfogged()
                        + " is the one the game is currently waiting on.";
                    if (player2.getSecretsScored().size() + player2.getSecretsUnscored().size() >= player2.getMaxSOCount()) {
                        message += " Remember it is not enough to simply draw a secret objective, they will also need to discard one.";
                    }
                }
                game.setStoredValue(key2,
                    game.getStoredValue(key2) + player.getFaction() + "*");
                break;
            }
        }
        ReactionService.addReaction(event, game, player, message);
    }

    @ButtonHandler("sc_refresh")
    public static void refresh(Game game, Player player, ButtonInteractionEvent event) {
        boolean used = addUsedSCPlayer(event.getMessageId(), game, player);
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
        ReactionService.addReaction(event, game, player, "has replenished commodities.");
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
                + " since you do not currently hold your _Trade Agreemnt_, washing here seems likely an error and will mess with the _Trade Agreement_ resolution."
                + " Nothing has been processed as a result. Try a different route of washing your commodities if this correction is wrong.");
            return;
        }

        boolean used = addUsedSCPlayer(messageID, game, player);
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
                int ogComms = p2.getCommodities();
                int ogTG = p2.getTg();
                if (p2.getCommodities() > washedCommsPower) {
                    p2.setTg(p2.getTg() + washedCommsPower);
                    p2.setCommodities(p2.getCommodities() - washedCommsPower);
                    ButtonHelperAbilities.pillageCheck(p2, game);
                    MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                        p2.getRepresentationUnfogged() + ", " + washedCommsPower
                            + " of your commodities got washed in the process of washing "
                            + player.getRepresentationNoPing() + ".");
                } else {
                    p2.setTg(p2.getTg() + p2.getCommodities());
                    p2.setCommodities(0);
                    ButtonHelperAbilities.pillageCheck(p2, game);
                    MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                        p2.getRepresentationUnfogged()
                            + ", your commodities got washed in the process of washing "
                            + player.getFactionEmojiOrColor() + ".");
                }
                if (p2.getPromissoryNotesInPlayArea().contains("dark_pact") && !player.getPromissoryNotesOwned().contains("dark_pact")) {
                    MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                        p2.getRepresentationUnfogged()
                            + ", due to you having dark pact in play, we are undoing the recent wash of your comms as it may not be desired.");
                    p2.setTg(ogTG);
                    p2.setCommodities(ogComms);
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

        ReactionService.addReaction(event, game, player, "replenishing and washing.");
        ButtonHelper.resolveMinisterOfCommerceCheck(game, player, event);
        ButtonHelperAgents.cabalAgentInitiation(game, player);

    }

    @ButtonHandler("anarchy7Build_")
    public static void anarchy7Build(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        List<Button> buttons;
        buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos),
            "anarchy7Build", "place");
        String message = player.getRepresentation() + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }
    public static List<Button> getAnarchy7Buttons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesOfUnitsWithProduction(player, game)) {
                buttons.add(Buttons.green("anarchy7Build_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        return buttons;
    }
    public static List<Button> getAnarchy3SecondaryButtons(Game game) {
        List<Button> scButtons = new ArrayList<>();
        if (!Helper.getRemainingSCs(game).contains(1) || (game.getScPlayed().get(1)!= null && game.getScPlayed().get(1))) {
            scButtons.add(Buttons.green("leadershipGenerateCCButtons", "Spend & Gain Command Tokens"));
        }
        if (!Helper.getRemainingSCs(game).contains(2) || (game.getScPlayed().get(2)!= null && game.getScPlayed().get(2))) {
            scButtons.add( Buttons.gray("anarchy2secondary", "Ready a non-SC Card"));
            scButtons.add(Buttons.green("diploRefresh2", "Ready Planets"));
        }
        if (!Helper.getRemainingSCs(game).contains(4) || (game.getScPlayed().get(4)!= null && game.getScPlayed().get(4))) {
            scButtons.add(Buttons.gray("draw2 AC", "Draw 2 Action Cards", CardEmojis.ActionCard));
        }
        if (!Helper.getRemainingSCs(game).contains(5) || (game.getScPlayed().get(5)!= null && game.getScPlayed().get(5))) {
            scButtons.add(Buttons.green("construction_spacedock", "Place 1 space dock", UnitEmojis.spacedock));
            scButtons.add(Buttons.green("construction_pds", "Place 1 PDS", UnitEmojis.pds));
        }
        if (!Helper.getRemainingSCs(game).contains(6) ||(game.getScPlayed().get(6)!= null && game.getScPlayed().get(6))) {
            scButtons.add(Buttons.gray("sc_refresh", "Replenish Commodities", MiscEmojis.comm));
        }
        if (!Helper.getRemainingSCs(game).contains(7) || (game.getScPlayed().get(7)!= null && game.getScPlayed().get(7))) {
            scButtons.add(Buttons.green("warfareBuild", "Build At Home"));
        }
        if (!Helper.getRemainingSCs(game).contains(8) || (game.getScPlayed().get(8)!= null && game.getScPlayed().get(8))) {
            scButtons.add(Buttons.green("resolveAnarchy8Secondary", "Lift Command Token"));
        }
        if (!Helper.getRemainingSCs(game).contains(9) || (game.getScPlayed().get(9)!= null && game.getScPlayed().get(9))) {
            scButtons.add(Buttons.GET_A_TECH);
        }
        if (!Helper.getRemainingSCs(game).contains(11) || (game.getScPlayed().get(11)!= null && game.getScPlayed().get(11))) {
            scButtons.add(Buttons.gray("non_sc_draw_so", "Draw Secret Objective", CardEmojis.SecretObjective));
        }
        scButtons.add(Buttons.red("deleteButtons", "Done resolving"));

        return scButtons;
    }

    @ButtonHandler("anarchy2ReadyAgent_")
    public static void resolveTCSExhaust(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("absol_jr", "absoljr");
        String agent = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null)
            return;
        Leader playerLeader = p2.getLeader(agent).orElse(null);
        if (playerLeader == null) {
            if (agent.contains("titanprototype")) {
                p2.removeExhaustedRelic("titanprototype");
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getFactionEmoji() + "the Preparation ability was used to ready " + agent + ", owned by "
                        + p2.getColor() + ".");
                if (p2 != player) {
                    MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                        p2.getRepresentationUnfogged() + " the Preparation ability was used by " + player.getColor()
                            + " to ready your " + agent + ".");
                }
                event.getMessage().delete().queue();
            }
            if (agent.contains("absol")) {
                p2.removeExhaustedRelic("absol_jr");
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getFactionEmoji() + " used the Preparation ability to ready " + agent + ", owned by "
                        + p2.getColor() + ".");
                if (p2 != player) {
                    MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                        p2.getRepresentationUnfogged() + " the Preparation ability was used by " + player.getColor()
                            + " to ready your " + agent + ".");
                }
                event.getMessage().delete().queue();
            }
            return;
        }
        RefreshLeaderService.refreshLeader(p2, playerLeader, game);

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " used the Preparation ability to ready " + agent + ", owned by "
                + p2.getColor() + ".");

        if (p2 != player) {
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                p2.getRepresentationUnfogged() + " the Preparation ability was used by " + player.getColor()
                    + " to ready your " + agent + ".");
        }
        event.getMessage().delete().queue();
    }
    public static List<Button> getAnarchy2ReadyComponentButtons(Game game, Player player){

        List<Button> buttons = new ArrayList<>();
        buttons.addAll(ButtonHelper.getAllTechsToReady(player));
        for(Player p2 : game.getRealPlayers()){
            for(String leader : p2.getLeaderIDs()){
                if(!p2.hasUnexhaustedLeader(leader)){
                    LeaderModel leaderM = Mapper.getLeader(leader);
                    buttons.add(Buttons.green("anarchy2ReadyAgent_"+leader+"_"+p2.getFaction(), "Ready "+leaderM.getName()));
                }
            }
        }
        for(String relic : player.getExhaustedRelics()){
            String relicName = relic;
            if(Mapper.getRelic(relic) != null){
                relicName = Mapper.getRelic(relic).getName();
            }
            buttons.add(Buttons.green("anarchy2ReadyRelic_"+relic, "Ready "+relicName));
        }

        return buttons;

    }
    @ButtonHandler("anarchy2secondary")
    public static void secondaryOfAnarchy2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = ButtonHelperSCs.getAnarchy2ReadyComponentButtons(game,player);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true)
                + " use the buttons to ready something",
            buttons);
    }

    public static List<Button> getAnarchy1PrimaryButtons(Game game) {
        List<Button> scButtons = new ArrayList<>();
        Button followButton = Buttons.green("sc_follow_12", "Spend A Strategy Token");
        scButtons.add(followButton);
        if (Helper.getRemainingSCs(game).contains(2) && (game.getScPlayed().get(2)== null || !game.getScPlayed().get(2))) {
            scButtons.add( Buttons.gray("anarchy2secondary", "Ready a non-SC Card"));
            scButtons.add(Buttons.green("diploRefresh2", "Ready Planets"));
        }
        if (Helper.getRemainingSCs(game).contains(3) && (game.getScPlayed().get(3)== null || !game.getScPlayed().get(3))) {
            scButtons.add(Buttons.gray("anarchy3secondary", "Perform Unchosen Or Exhausted Secondary"));
        }
        if (Helper.getRemainingSCs(game).contains(4) && (game.getScPlayed().get(4)== null || !game.getScPlayed().get(4))) {
            scButtons.add(Buttons.gray("draw2 AC", "Draw 2 Action Cards", CardEmojis.ActionCard));
        }
        if (Helper.getRemainingSCs(game).contains(5) && (game.getScPlayed().get(5)== null || !game.getScPlayed().get(5))) {
            scButtons.add(Buttons.green("construction_spacedock", "Place 1 space dock", UnitEmojis.spacedock));
            scButtons.add(Buttons.green("construction_pds", "Place 1 PDS", UnitEmojis.pds));
        }
        if (Helper.getRemainingSCs(game).contains(6) && (game.getScPlayed().get(6)== null || !game.getScPlayed().get(6))) {
            scButtons.add(Buttons.gray("sc_refresh", "Replenish Commodities", MiscEmojis.comm));
        }
        if (Helper.getRemainingSCs(game).contains(7) && (game.getScPlayed().get(7)== null || !game.getScPlayed().get(7))) {
            scButtons.add(Buttons.green("warfareBuild", "Build At Home"));
        }
        if (Helper.getRemainingSCs(game).contains(8) && (game.getScPlayed().get(8)== null || !game.getScPlayed().get(8))) {
            scButtons.add(Buttons.green("resolveAnarchy8Secondary", "Lift Command Token"));
        }
        if (Helper.getRemainingSCs(game).contains(9) && (game.getScPlayed().get(9)== null || !game.getScPlayed().get(9))) {
            scButtons.add(Buttons.GET_A_TECH);
        }
        if (Helper.getRemainingSCs(game).contains(11) && (game.getScPlayed().get(11)== null || !game.getScPlayed().get(11))) {
            scButtons.add(Buttons.gray("non_sc_draw_so", "Draw Secret Objective", CardEmojis.SecretObjective));
        }
        scButtons.add(Buttons.red("deleteButtons", "Done resolving"));

        return scButtons;
    }
    @ButtonHandler("reverseSpeakerOrder")
    public static void reverseSpeakerOrder(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Helper.reverseSpeakerOrder(game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji()+" has reversed speaker order"); 
    }
    @ButtonHandler("primaryOfAnarchy1")
    public static void primaryOfAnarchy1(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = ButtonHelperSCs.getAnarchy1PrimaryButtons(game);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true)
                + " use the buttons to resolve the secondary (remember to spend a strat CC)",
            buttons);
    }
    @ButtonHandler("anarchy3secondary")
    public static void anarchy3secondary(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = ButtonHelperSCs.getAnarchy3SecondaryButtons(game);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true)
                + " use the buttons to resolve the secondary",
            buttons);
    }
    @ButtonHandler("primaryOfAnarchy7")
    public static void primaryOfAnarchy7(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = ButtonHelperSCs.getAnarchy7Buttons(game, player);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true)
                + " use the buttons to build in the desired system",
            buttons);
    }

    @ButtonHandler("resolveAnarchy8Secondary")
    public static void resolveUnexpectedAction(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "unexpected");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation()+" Use buttons to remove token.", buttons);
    }

    @ButtonHandler("warfareBuild")
    public static void warfareBuild(Game game, Player player, ButtonInteractionEvent event) {
        String messageID = event.getMessageId();
        List<Button> buttons;
        boolean used = addUsedSCPlayer(messageID, game, player);
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
            String message = deductCC(game, player, scNum);
            ReactionService.addReaction(event, game, player, message);
        }
        Tile tile = player.getHomeSystemTile();
        buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "warfare", "place");
        int val = Helper.getProductionValue(player, game, tile, true);
        String message = player.getRepresentation()
            + " Use the buttons to produce. Reminder that when following **Warfare**, you may only use 1 space dock in your home system. "
            + ButtonHelper.getListOfStuffAvailableToSpend(player, game, true) + "\n"
            + "You have " + val + " PRODUCTION value in this system.";
        if (val > 0 && game.playerHasLeaderUnlockedOrAlliance(player, "cabalcommander")) {
            message = message
                + ". You also have the That Which Molds Flesh, the Vuil'raith commander, which allows you to produce 2 fighters/infantry that don't count towards PRODUCTION limit. ";
        }
        if (val > 0 && ButtonHelper.isPlayerElected(game, player, "prophecy")) {
            message = message
                + "Reminder that you have _Prophecy of Ixth_ and should produce at least 2 fighters if you wish to keep it. Its removal is not automated.";
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
        boolean used = addUsedSCPlayer(messageID, game, player);
        StrategyCardModel scModel = null;
        for (int scNum : player.getUnfollowedSCs()) {
            if (game.getStrategyCardModelByInitiative(scNum).get().usesAutomationForSCID("pok4construction")) {
                scModel = game.getStrategyCardModelByInitiative(scNum).get();
            }
        }
        if (scModel == null) {
            scModel = game.getStrategyCardModelByName("construction").orElse(null);
        }
        int scNum = scModel.getInitiative();
        boolean automationExists = scModel != null && scModel.usesAutomationForSCID("pok4construction");
        if (!used && scModel != null && !player.getFollowedSCs().contains(scNum)
            && automationExists && game.getPlayedSCs().contains(scNum)) {
            player.addFollowedSC(scNum, event);
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
            if (player.getStrategicCC() > 0) {
                ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed construction");
            }
            String message = deductCC(game, player, scNum);
            ReactionService.addReaction(event, game, player, message);
        }
        ReactionService.addReaction(event, game, player);
        String unit = buttonID.replace("construction_", "");
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColorID());
        String message = player.getRepresentationUnfogged() + ", please choose the planet you wish to put your "
            + unitKey.unitName() + " on for **Construction**.";
        if (!player.getSCs().contains(4)) {
            message += "\n## __It will place a command token in the system as well.__ ";
        }
        List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, unit, "place");
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, message, buttons);
        // List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(message, buttons);
        // for (MessageCreateData messageD : messageList) {
        //     event.getHook().setEphemeral(true).sendMessage(messageD).queue();
        // }

    }

    @ButtonHandler("anarchy10PeekStart")
    public static void anarchy10PeekStart(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for(int x = 0; x < game.getPublicObjectives1Peakable().size();x++){
            buttons.add(Buttons.green("anarchy10PeekAt_"+game.getPublicObjectives1Peakable().get(x), "Stage 1 Position "+(x+1)));
        }
        for(int x = 0; x < game.getPublicObjectives2Peakable().size();x++){
            buttons.add(Buttons.blue("anarchy10PeekAt_"+game.getPublicObjectives2Peakable().get(x), "Stage 2 Position "+(x+1)));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation() +" choose which objective you wish to peek at. "
        +"They are listed in the order that they would normally be revealed", buttons);
    }
    @ButtonHandler("anarchy10PeekAt")
    public static void anarchy10PeekAt(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String objID = buttonID.replace("anarchy10PeekAt_","");
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),player.getFactionEmoji() +" peeked at a public objective");
        PublicObjectiveModel po = Mapper.getPublicObjective(objID);
        player.getCardsInfoThread().sendMessageEmbeds(po.getRepresentationEmbed()).queue(m -> m.pin().queue());
        buttons.add(Buttons.green("cutTape_"+objID, "Reveal Objective"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation() +" choose whether to reveal the objective or not", buttons);
    }

    @ButtonHandler("leadershipGenerateCCButtons")
    public static void leadershipGenerateCCButtons(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        StrategyCardModel scModel = null;
        for (int scNum : player.getUnfollowedSCs()) {
            if (game.getStrategyCardModelByInitiative(scNum).get().usesAutomationForSCID("pok1leadership")) {
                scModel = game.getStrategyCardModelByInitiative(scNum).get();
            }
        }
        boolean unfinished = false;
        if (!game.getStoredValue("ledSpend" + player.getFaction()).isEmpty() && !player.getSpentThingsThisWindow().isEmpty()) {
            unfinished = true;
            game.setStoredValue("resetSpend", "yes");
        }
        if (scModel == null) {
            scModel = game.getStrategyCardModelByName("leadership").orElse(null);
        }
        int leadershipInitiative = scModel.getInitiative();

        if (!player.getFollowedSCs().contains(leadershipInitiative)) {
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, leadershipInitiative, game, event);
        }
        player.addFollowedSC(leadershipInitiative, event);
        String message = player.getRepresentationUnfogged() + " Click the names of the planets you wish to exhaust.";
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        if (unfinished) {
            message = Helper.buildSpentThingsMessage(player, game, "inf");
        } else {
            game.setStoredValue("ledSpend" + player.getFaction(), "Yes");
        }
        Button doneExhausting = Buttons.red("deleteButtons_leadership", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        int ccCount = Helper.getCCCount(game, player.getColor());
        int limit = 16;
        if (!game.getStoredValue("ccLimit").isEmpty()) {
            limit = Integer.parseInt(game.getStoredValue("ccLimit"));
        }
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "You have " + (limit - ccCount) + " command tokens in your reinforcements that you could gain.");
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        } else {
            MessageHelper.sendMessageToChannel(player.getPrivateChannel(), "You have " + (limit - ccCount) + " command tokens in your reinforcements that you could gain.");
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
        }
        //MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, message, buttons);
        ReactionService.addReaction(event, game, player);
        message = player.getRepresentationUnfogged() + ", your current command tokens are " + player.getCCRepresentation() + ". Use buttons to gain command tokens.";
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        Button getTactic = Buttons.green(player.getFinsFactionCheckerPrefix() + "increase_tactic_cc", "Gain 1 Tactic Token");
        Button getFleet = Buttons.green(player.getFinsFactionCheckerPrefix() + "increase_fleet_cc", "Gain 1 Fleet Token");
        Button getStrat = Buttons.green(player.getFinsFactionCheckerPrefix() + "increase_strategy_cc", "Gain 1 Strategy Token");
        Button doneGainingCC = Buttons.red(player.getFinsFactionCheckerPrefix() + "deleteButtons_leadership", "Done Gaining Command Tokens");
        Button resetCC = Buttons.gray(player.getFinsFactionCheckerPrefix() + "resetCCs", "Reset Command Tokens");
        List<Button> buttons2 = Arrays.asList(getTactic, getFleet, getStrat, doneGainingCC, resetCC);
       // MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, message, buttons2);
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons2);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons2);
        }

    }

    public static boolean addUsedSCPlayer(String messageID, Game game, Player player) {
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
                                player.getRepresentationUnfogged() + " Use buttons to remove a command token.", buttonsToRemoveCC);
                        }
                    }
                }
            }
        } else {
            MessageHelper.sendMessageToChannel(channel,
                player.getRepresentationUnfogged() + " exhausted the _" + RelicHelper.sillySpelling() + "_ to follow " + Helper.getSCName(scNum, game) + ".");
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
        String suffix = "";
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
            suffix = " **" + Helper.getSCName(scNum, game) + "**";
        }
        ReactionService.addReaction(event, game, player, "is not following" + suffix + ".");
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
        String lastChar = StringUtils.right(event.getButton().getLabel(), 2).replace("#", "");
        int scNum = 1;
        boolean setStatus = true;
        try {
            scNum = Integer.parseInt(StringUtils.substringAfterLast(buttonID, "_"));
        } catch (NumberFormatException e) {
            try {
                scNum = Integer.parseInt(lastChar);
            } catch (NumberFormatException e2) {
                setStatus = false;
            }
        }
        if (player != null && player.getSCs().contains(scNum)) {
            String message = player.getRepresentation() + " you currently hold this strategy card and therefore should not be spending a command token here." +
                "\nYou may override this protection by running `/player stats strategy_cc:-1`.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            return;
        }
        boolean used = addUsedSCPlayer(messageID, game, player);
        if (!used) {
            if (player.getStrategicCC() > 0) {
                ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed " + Helper.getSCName(scNum, game));
            }
            String message = deductCC(game, player, scNum);

            if (setStatus) {
                if (!player.getFollowedSCs().contains(scNum)) {
                    ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
                }
                player.addFollowedSC(scNum, event);
            }
            ReactionService.addReaction(event, game, player, message);
        }

    }

    @NotNull
    public static String deductCC(Game game, Player player, int scNum) {
        int strategicCC = player.getStrategicCC();
        if (strategicCC == 0) {
            return " have 0 command tokens in strategy pool, can't follow.";
        }

        strategicCC--;
        player.setStrategicCC(strategicCC);
        if (scNum == -1) {
            return " performing the secondary ability of a strategy card with **Grace**."
                + "1 command token has been spent from strategy pool.";
        }
        String stratCardName = Helper.getSCName(scNum, game);
        return " following to perform the secondary ability of **" + stratCardName + "**."
            + " 1 command token has been spent from strategy pool.";
    }

    @ButtonHandler("sc_ac_draw")
    public static void scACDraw(Game game, Player player, ButtonInteractionEvent event) {
        String messageID = event.getMessageId();
        boolean used2 = addUsedSCPlayer(messageID + "ac", game, player);
        if (used2) {
            return;
        }
        boolean used = addUsedSCPlayer(messageID, game, player);
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
            String message = deductCC(game, player, scNum);
            ReactionService.addReaction(event, game, player, message);
        }
        boolean hasSchemingAbility = player.hasAbility("scheming");
        String message = hasSchemingAbility
            ? "drew 3 action cards (**Scheming**) - please discard 1 action card from your hand."
            : "drew 2 action cards.";
        int count = hasSchemingAbility ? 3 : 2;
        if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, count);
            message = player.getFactionEmoji() + " triggered **Autonetic Memory** option.";

        } else {
            for (int i = 0; i < count; i++) {
                game.drawActionCard(player.getUserID());
            }
            ActionCardHelper.sendActionCardInfo(game, player, event);
            ButtonHelper.checkACLimit(game, player);
        }

        ReactionService.addReaction(event, game, player, message);
        if (hasSchemingAbility) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " use buttons to discard",
                ActionCardHelper.getDiscardActionCardButtons(player, false));
        }
        CommanderUnlockCheckService.checkPlayer(player, "yssaril");
        if (player.hasAbility("contagion")) {
            List<Button> buttons2 = ButtonHelperAbilities.getKyroContagionButtons(game, player,
                event, player.getFinsFactionCheckerPrefix());
            if (!buttons2.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + ", please resolve **Contagion**.", buttons2);

                if (Helper.getDateDifference(game.getCreationDate(),
                    Helper.getDateRepresentation(1711997257707L)) > 0) {
                    MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        player.getRepresentationUnfogged()
                            + ", please resolve **Contagion** again (on a different planet).",
                        buttons2);
                }
            }
        }
    }
}
