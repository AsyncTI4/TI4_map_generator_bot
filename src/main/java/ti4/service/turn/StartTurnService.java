package ti4.service.turn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.image.BannerGenerator;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;
import ti4.model.metadata.AutoPingMetadataManager;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;
import ti4.service.fow.FowCommunicationThreadService;
import ti4.service.fow.WhisperService;
import ti4.service.info.CardsInfoService;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
public class StartTurnService {

    public static void turnStart(GenericInteractionCreateEvent event, Game game, Player player) {
        player.setInRoundTurnCount(player.getInRoundTurnCount() + 1);
        CommanderUnlockCheckService.checkPlayer(player, "hacan");
        Map<String, String> maps = new HashMap<>(game.getMessagesThatICheckedForAllReacts());
        for (String id : maps.keySet()) {
            if (id.contains("combatRoundTracker")) {
                game.removeStoredValue(id);
            }
        }
        game.setStoredValue(player.getFaction() + "planetsExplored", "");
        game.setNaaluAgent(false);
        game.setL1Hero(false);
        game.setStoredValue("lawsDisabled", "no");
        game.checkSOLimit(player);
        game.setStoredValue("vaylerianHeroActive", "");
        game.setStoredValue("tnelisCommanderTracker", "");
        game.setStoredValue("planetsTakenThisRound", "");
        game.setStoredValue("absolLux", "");
        game.setStoredValue("mentakHero", "");
        CardsInfoService.sendVariousAdditionalButtons(game, player);
        boolean goingToPass = false;
        if (game.getStoredValue("Pre Pass " + player.getFaction()) != null
            && game.getStoredValue("Pre Pass " + player.getFaction())
                .contains(player.getFaction())) {
            if (game.getStoredValue("Pre Pass " + player.getFaction()).contains(player.getFaction())
                && !player.isPassed()) {
                game.setStoredValue("Pre Pass " + player.getFaction(), "");
                goingToPass = true;
            }
        }
        String text = player.getRepresentationUnfogged() + " UP NEXT (Turn #" + player.getInRoundTurnCount() + ")";
        String buttonText = "Use buttons to do your turn. ";
        if (game.getName().equalsIgnoreCase("pbd1000") || game.getName().equalsIgnoreCase("pbd100two")) {
            buttonText = buttonText + "Your SC number is #" + player.getSCs().toArray()[0];
        }
        List<Button> buttons = getStartOfTurnButtons(player, game, false, event);
        MessageChannel gameChannel = game.getMainGameChannel() == null ? event.getMessageChannel()
            : game.getMainGameChannel();

        game.updateActivePlayer(player);
        game.setPhaseOfGame("action");
        ButtonHelperFactionSpecific.resolveMilitarySupportCheck(player, game);
        Helper.startOfTurnSaboWindowReminders(game, player);
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(game, event);

        if (isFowPrivateGame) {
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, "started turn");

            String fail = "User for next faction not found. Report to ADMIN";
            String success = "The next player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(player, game, event, text, fail, success);
            if (!goingToPass) {
                MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), buttonText, buttons);
            }
            if (getMissedSCFollowsText(game, player) != null
                && !"".equalsIgnoreCase(getMissedSCFollowsText(game, player))) {
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(),
                    getMissedSCFollowsText(game, player));
            }
            if (player.getGenSynthesisInfantry() > 0) {
                if (!ButtonHelper.getPlaceStatusInfButtons(game, player).isEmpty()) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        "Use buttons to revive infantry. You have " + player.getGenSynthesisInfantry() + " infantry left to revive.",
                        ButtonHelper.getPlaceStatusInfButtons(game, player));
                } else {
                    player.setStasisInfantry(0);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                        + " You had infantry II to be revived, but the bot couldn't find planets you own in your HS to place them, so per the rules they now disappear into the ether.");

                }
            }
            ButtonHelperFactionSpecific.resolveKolleccAbilities(player, game);
            ButtonHelperFactionSpecific.resolveMykoMechCheck(player, game);

            game.setPingSystemCounter(0);
            for (int x = 0; x < 10; x++) {
                game.setTileAsPinged(x, null);
            }
        } else {
            //checkhere
            if (game.isShowBanners()) {
                BannerGenerator.drawFactionBanner(player);
            }
            MessageHelper.sendMessageToChannel(gameChannel, text);
            if (!goingToPass) {
                MessageHelper.sendMessageToChannelWithButtons(gameChannel, buttonText, buttons);
            }
            if (getMissedSCFollowsText(game, player) != null
                && !"".equalsIgnoreCase(getMissedSCFollowsText(game, player))) {
                MessageHelper.sendMessageToChannel(gameChannel, getMissedSCFollowsText(game, player));
            }
            if (player.getGenSynthesisInfantry() > 0) {
                if (!ButtonHelper.getPlaceStatusInfButtons(game, player).isEmpty()) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        "Use buttons to revive infantry. You have " + player.getGenSynthesisInfantry() + " infantry left to revive.",
                        ButtonHelper.getPlaceStatusInfButtons(game, player));
                } else {
                    player.setStasisInfantry(0);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                        + " You had infantry II to be revived, but the bot couldn't find planets you own in your HS to place them, so per the rules they now disappear into the ether.");

                }
            }
            ButtonHelperFactionSpecific.resolveMykoMechCheck(player, game);
            ButtonHelperFactionSpecific.resolveKolleccAbilities(player, game);

        }
        if (!game.getStoredValue("futureMessageFor" + player.getFaction()).isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " you left yourself the following message: \n"
                    + game.getStoredValue("futureMessageFor" + player.getFaction()));
            game.setStoredValue("futureMessageFor" + player.getFaction(), "");
        }
        for (Player p2 : game.getRealPlayers()) {
            if (!game
                .getStoredValue("futureMessageFor_" + player.getFaction() + "_" + p2.getFaction())
                .isEmpty()) {
                String msg2 = "This is a message sent from the past:\n" + game
                    .getStoredValue("futureMessageFor_" + player.getFaction() + "_" + p2.getFaction());
                MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(),
                    p2.getRepresentationUnfogged() + " your future message got delivered");
                WhisperService.sendWhisper(game, p2, player, msg2, "n", p2.getCardsInfoThread(), event.getGuild());
                game.setStoredValue("futureMessageFor_" + player.getFaction() + "_" + p2.getFaction(), "");
            }
        }

        if (goingToPass) {
            player.setPassed(true);
            if (game.playerHasLeaderUnlockedOrAlliance(player, "olradincommander")) {
                ButtonHelperCommanders.olradinCommanderStep1(player, game);
            }
            String text2 = player.getRepresentation(true, false) + " PASSED";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), text2);
            if (player.hasTech("absol_aida")) {
                String msg = player.getRepresentation()
                    + " since you have AI Development Algorithm, you may research 1 Unit Upgrade now for 6 influence.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                if (!player.hasAbility("propagation")) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentationUnfogged() + " you may use the button to get your tech.",
                        List.of(Buttons.GET_A_TECH));
                } else {
                    List<Button> buttons2 = ButtonHelper.getGainCCButtons(player);
                    String message2 = player.getRepresentation() + "! Your current CCs are "
                        + player.getCCRepresentation()
                        + ". Use buttons to gain CCs";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        message2, buttons2);
                    game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                }
            }
            if (player.hasAbility("deliberate_action") && (player.getTacticalCC() == 0 || player.getStrategicCC() == 0 || player.getFleetCC() == 0)) {
                String msg = player.getRepresentation()
                    + " since you have deliberate action ability and passed while one of your pools was at 0, you may gain 1 CC to that pool.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                List<Button> buttons2 = ButtonHelper.getGainCCButtons(player);
                String message2 = player.getRepresentation() + "! Your current CCs are " + player.getCCRepresentation()
                    + ". Use buttons to gain CCs";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons2);
            }
            EndTurnService.pingNextPlayer(event, game, player, true);
        }
    }

    public static String getMissedSCFollowsText(Game game, Player player) {
        if (!game.isStratPings())
            return null;
        boolean sendReminder = false;

        StringBuilder sb = new StringBuilder();
        sb.append(player.getRepresentationUnfogged());
        sb.append(" Please resolve these before doing anything else:\n");
        for (int sc : game.getPlayedSCsInOrder(player)) {
            if (game.getName().equalsIgnoreCase("pbd1000") || game.getName().equalsIgnoreCase("pbd100two")) {
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
                sb.append("> ").append(Helper.getSCRepresentation(game, sc));
                if (!game.getStoredValue("scPlay" + sc).isEmpty()) {
                    sb.append(" ").append(game.getStoredValue("scPlay" + sc));
                }
                sb.append("\n");
                sendReminder = true;
            }
        }
        sb.append("You currently have ").append(player.getStrategicCC()).append(" CC in your strategy pool.");
        return sendReminder ? sb.toString() : null;
    }

    public static List<Button> getStartOfTurnButtons(Player player, Game game, boolean doneActionThisTurn, GenericInteractionCreateEvent event) {
        return getStartOfTurnButtons(player, game, doneActionThisTurn, event, false);
    }

    public static List<Button> getStartOfTurnButtons(Player player, Game game, boolean doneActionThisTurn, GenericInteractionCreateEvent event, boolean confirmed2ndAction) {
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
        if (!doneActionThisTurn || confirmed2ndAction) {
            Button tacticalAction = Buttons.green(finChecker + "tacticalAction",
                "Tactical Action (" + player.getTacticalCC() + ")");
            int numOfComponentActions = ComponentActionHelper.getAllPossibleCompButtons(game, player, event).size() - 2;
            Button componentAction = Buttons.green(finChecker + "componentAction", "Component Action (" + numOfComponentActions + ")");

            startButtons.add(tacticalAction);
            startButtons.add(componentAction);

            for (Integer SC : player.getSCs()) {
                if (!game.getPlayedSCs().contains(SC)) {
                    hadAnyUnplayedSCs = true;
                    String name = Helper.getSCName(SC, game);
                    if (game.getName().equalsIgnoreCase("pbd1000")) {
                        name = name + "(" + SC + ")";
                    }
                    Button strategicAction = Buttons.green(finChecker + "strategicAction_" + SC, "Play " + name, CardEmojis.getSCFrontFromInteger(SC));
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
                                Button lButton = Buttons.gray(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Naalu Agent", leaderEmoji);
                                startButtons.add(lButton);
                            }
                        } else {
                            if (leaderID.equalsIgnoreCase("naaluagent")) {
                                Button lButton = Buttons.gray(finChecker + prefix + "leader_" + leaderID, "Use " + leaderName, leaderEmoji);
                                startButtons.add(lButton);
                            }
                        }
                    } else if ("mahactcommander".equalsIgnoreCase(leaderID) && player.getTacticalCC() > 0
                        && !ButtonHelper.getTilesWithYourCC(player, game, event).isEmpty()) {
                        Button lButton = Buttons.gray(finChecker + "mahactCommander", "Use Mahact Commander", leaderEmoji);
                        startButtons.add(lButton);
                    }
                }
            }
        }

        if (!hadAnyUnplayedSCs && !doneActionThisTurn) {
            Button pass = Buttons.red(finChecker + "passForRound", "Pass");

            int numEndOfTurn = ButtonHelper.getEndOfTurnAbilities(player, game).size() - 1;
            if (numEndOfTurn > 0) {
                startButtons.add(Buttons.blue(finChecker + "endOfTurnAbilities", "Do End Of Turn Ability (" + numEndOfTurn + ")"));
            }

            startButtons.add(pass);
            if (!game.isFowMode()) {
                for (Player p2 : game.getRealPlayers()) {
                    for (int sc : player.getSCs()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(p2.getRepresentationUnfogged());
                        sb.append(" You are getting this ping because ").append(Helper.getSCName(sc, game)).append(" has been played and now it is their turn again and you still haven't reacted. If you already reacted, check if your reaction got undone");
                        appendScMessages(game, p2, sc, sb);
                    }
                }
            }
        }
        if (doneActionThisTurn) {
            ButtonHelperFactionSpecific.checkBlockadeStatusOfEverything(player, game, event);
            if (ButtonHelper.getEndOfTurnAbilities(player, game).size() > 1) {
                startButtons.add(Buttons.blue("endOfTurnAbilities", "Do End Of Turn Ability ("
                    + (ButtonHelper.getEndOfTurnAbilities(player, game).size() - 1) + ")"));
            }
            startButtons.add(Buttons.red(finChecker + "turnEnd", "End Turn"));
            if (ButtonHelper.isPlayerElected(game, player, "minister_war")) {
                startButtons.add(Buttons.gray(finChecker + "ministerOfWar", "Use Minister of War"));
            }
            if (!game.isJustPlayedComponentAC()) {
                AutoPingMetadataManager.setupQuickPing(game.getName());
            }
        } else {
            game.setJustPlayedComponentAC(false);
            if (player.getTechs().contains("cm")) {
                startButtons.add(Buttons.gray("startChaosMapping", "Use Chaos Mapping", FactionEmojis.Saar));
            }
            if (player.getTechs().contains("dscymiy") && !player.getExhaustedTechs().contains("dscymiy")) {
                startButtons.add(Buttons.gray("exhaustTech_dscymiy", "Exhaust Recursive Worm", FactionEmojis.cymiae));
            }
            if (player.hasUnexhaustedLeader("florzenagent") && !ButtonHelperAgents.getAttachments(game, player).isEmpty()) {
                startButtons.add(Buttons.green(finChecker + "exhaustAgent_florzenagent_" + player.getFaction(), "Use Florzen Agent", FactionEmojis.florzen));
            }
            if (player.hasUnexhaustedLeader("vadenagent")) {
                startButtons.add(Buttons.gray("exhaustAgent_vadenagent_" + player.getFaction(), "Use Vaden Agent", FactionEmojis.vaden));
            }
            if (player.hasAbility("laws_order") && !game.getLaws().isEmpty()) {
                startButtons.add(Buttons.gray("useLawsOrder", "Pay To Ignore Laws", FactionEmojis.Keleres));
            }
            if ((player.hasTech("td") && !player.getExhaustedTechs().contains("td")) ||
                (player.hasTech("absol_td") && !player.getExhaustedTechs().contains("absol_td"))) {
                startButtons.add(Buttons.gray(finChecker + "exhaustTech_td", "Exhaust Transit Diodes", TechEmojis.CyberneticTech));
            }
            if (player.hasUnexhaustedLeader("kolleccagent")) {
                startButtons.add(Buttons.gray("exhaustAgent_kolleccagent", "Use Kollecc Agent", FactionEmojis.kollecc));
            }
        }
        if (player.hasTech("pa") && ButtonHelper.getPsychoTechPlanets(game, player).size() > 1) {
            startButtons.add(Buttons.green(finChecker + "getPsychoButtons", "Use Psychoarcheology", TechEmojis.BioticTech));
        }

        Button transaction = Buttons.blue("transaction", "Transaction");
        startButtons.add(transaction);
        Button modify = Buttons.gray("getModifyTiles", "Modify Units");
        startButtons.add(modify);
        if (player.hasUnexhaustedLeader("hacanagent")) {
            startButtons.add(Buttons.gray("exhaustAgent_hacanagent", "Use Hacan Agent", FactionEmojis.Hacan));
        }
        if (player.hasRelicReady("e6-g0_network")) {
            startButtons.add(Buttons.green("exhauste6g0network", "Exhaust E6-G0 Network Relic to Draw 1 Acton Card"));
        }
        if (player.hasUnexhaustedLeader("nekroagent") && player.getAc() > 0) {
            startButtons.add(Buttons.gray("exhaustAgent_nekroagent", "Use Nekro Agent", FactionEmojis.Nekro));
        }

        if (game.getLatestTransactionMsg() != null
            && !"".equalsIgnoreCase(game.getLatestTransactionMsg())) {
            game.getMainGameChannel().deleteMessageById(game.getLatestTransactionMsg())
                .queue(Consumers.nop(), BotLogger::catchRestError);
            game.setLatestTransactionMsg("");
        }
        if (game.isFowMode()) {
            startButtons.add(Buttons.gray("showGameAgain", "Show Game"));
            if (FowCommunicationThreadService.isActive(game)) {
                Set<Player> newNeighbors = FowCommunicationThreadService.checkCommThreadsAndNewNeighbors(game, player);
                if (!newNeighbors.isEmpty()) {
                    startButtons.add(Buttons.blue("fowComms_" + newNeighbors.stream().map(Player::getColor).collect(Collectors.joining("-")), "Open new comms"));
                }
            }
        }

        startButtons.add(Buttons.gray("showMap", "Show Map"));
        startButtons.add(Buttons.gray("showPlayerAreas", "Show Player Areas"));
        if (!confirmed2ndAction && doneActionThisTurn) {
            startButtons.add(Buttons.red(finChecker + "confirmSecondAction", "Peform Another Action"));
        }
        return startButtons;
    }

    private static void appendScMessages(Game game, Player player, int sc, StringBuilder sb) {
        if (!game.getStoredValue("scPlay" + sc).isEmpty()) {
            sb.append("Message link is: ").append(game.getStoredValue("scPlay" + sc)).append("\n");
        }
        sb.append("You currently have ").append(player.getStrategicCC())
            .append(" CC in your strategy pool.");
        if (!player.hasFollowedSC(sc)) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                sb.toString());
        }
    }
}
