package ti4.commands.player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.fow.Whisper;
import ti4.commands.uncategorized.CardsInfo;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;

public class TurnStart extends PlayerSubcommandData {
    public TurnStart() {
        super(Constants.TURN_START, "Start Turn");
        addOptions(
            new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player mainPlayer = activeGame.getPlayer(getUser().getId());
        mainPlayer = Helper.getGamePlayer(activeGame, mainPlayer, event, null);
        mainPlayer = Helper.getPlayer(activeGame, mainPlayer, event);

        if (mainPlayer == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player/Faction/Color could not be found in map:" + activeGame.getName());
            return;
        }
        turnStart(event, activeGame, mainPlayer);
    }

    public static void turnStart(GenericInteractionCreateEvent event, Game activeGame, Player player) {
        player.setWhetherPlayerShouldBeTenMinReminded(false);
        player.setTurnCount(player.getTurnCount() + 1);
        Map<String, String> maps = new HashMap<>();
        maps.putAll(activeGame.getMessagesThatICheckedForAllReacts());
        for (String id : maps.keySet()) {
            if (id.contains("combatRoundTracker")) {
                activeGame.removeStoredValue(id);
            }
        }
        activeGame.setStoredValue(player.getFaction() + "planetsExplored", "");
        activeGame.setNaaluAgent(false);
        activeGame.setL1Hero(false);
        activeGame.checkSOLimit(player);
        activeGame.setStoredValue("vaylerianHeroActive", "");
        activeGame.setStoredValue("tnelisCommanderTracker", "");
        activeGame.setStoredValue("planetsTakenThisRound", "");
        activeGame.setStoredValue("absolLux", "");
        activeGame.setStoredValue("mentakHero", "");
        CardsInfo.sendVariousAdditionalButtons(activeGame, player);
        boolean goingToPass = false;
        if (activeGame.getStoredValue("Pre Pass " + player.getFaction()) != null
            && activeGame.getStoredValue("Pre Pass " + player.getFaction())
                .contains(player.getFaction())) {
            if (activeGame.getStoredValue("Pre Pass " + player.getFaction()).contains(player.getFaction())
                && !player.isPassed()) {
                activeGame.setStoredValue("Pre Pass " + player.getFaction(), "");
                goingToPass = true;
            }
        }
        String text = "# " + player.getRepresentation(true, true) + " UP NEXT (Turn #" + player.getTurnCount() + ")";
        String buttonText = "Use buttons to do your turn. ";
        List<Button> buttons = getStartOfTurnButtons(player, activeGame, false, event);
        MessageChannel gameChannel = activeGame.getMainGameChannel() == null ? event.getMessageChannel()
            : activeGame.getMainGameChannel();

        activeGame.updateActivePlayer(player);
        activeGame.setCurrentPhase("action");
        ButtonHelperFactionSpecific.resolveMilitarySupportCheck(player, activeGame);
        Helper.startOfTurnSaboWindowReminders(activeGame, player);
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(activeGame, event);

        if (isFowPrivateGame) {
            FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, "started turn");

            String fail = "User for next faction not found. Report to ADMIN";
            String success = "The next player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(player, activeGame, event, text, fail, success);
            if (!goingToPass) {
                MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), buttonText, buttons);
            }
            if (getMissedSCFollowsText(activeGame, player) != null
                && !"".equalsIgnoreCase(getMissedSCFollowsText(activeGame, player))) {
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(),
                    getMissedSCFollowsText(activeGame, player));
            }
            Player privatePlayer = player;
            if (privatePlayer.getStasisInfantry() > 0) {
                if (ButtonHelper.getPlaceStatusInfButtons(activeGame, privatePlayer).size() > 0) {
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(privatePlayer, activeGame),
                        "Use buttons to revive infantry. You have " + privatePlayer.getStasisInfantry() + " infantry left to revive.",
                        ButtonHelper.getPlaceStatusInfButtons(activeGame, privatePlayer));
                } else {
                    privatePlayer.setStasisInfantry(0);
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(privatePlayer, activeGame), privatePlayer.getRepresentation()
                        + " You had infantry2 to be revived, but the bot couldnt find planets you own in your HS to place them, so per the rules they now disappear into the ether");

                }
            }
            ButtonHelperFactionSpecific.resolveKolleccAbilities(player, activeGame);
            ButtonHelperFactionSpecific.resolveMykoMechCheck(player, activeGame);

            activeGame.setPingSystemCounter(0);
            for (int x = 0; x < 10; x++) {
                activeGame.setTileAsPinged(x, null);
            }
        } else {
            MessageHelper.sendMessageToChannel(gameChannel, text);
            if (!goingToPass) {
                MessageHelper.sendMessageToChannelWithButtons(gameChannel, buttonText, buttons);
            }
            if (getMissedSCFollowsText(activeGame, player) != null
                && !"".equalsIgnoreCase(getMissedSCFollowsText(activeGame, player))) {
                MessageHelper.sendMessageToChannel(gameChannel, getMissedSCFollowsText(activeGame, player));
            }
            Player privatePlayer = player;
            if (privatePlayer.getStasisInfantry() > 0) {
                if (ButtonHelper.getPlaceStatusInfButtons(activeGame, privatePlayer).size() > 0) {
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(privatePlayer, activeGame),
                        "Use buttons to revive infantry. You have " + privatePlayer.getStasisInfantry() + " infantry left to revive.",
                        ButtonHelper.getPlaceStatusInfButtons(activeGame, privatePlayer));
                } else {
                    privatePlayer.setStasisInfantry(0);
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(privatePlayer, activeGame), privatePlayer.getRepresentation()
                        + " You had infantry2 to be revived, but the bot couldnt find planets you own in your HS to place them, so per the rules they now disappear into the ether");

                }
            }
            ButtonHelperFactionSpecific.resolveMykoMechCheck(player, activeGame);
            ButtonHelperFactionSpecific.resolveKolleccAbilities(player, activeGame);

        }
        if (!activeGame.getStoredValue("futureMessageFor" + player.getFaction()).isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                player.getRepresentation(true, true) + " you left yourself the following message: \n"
                    + activeGame.getStoredValue("futureMessageFor" + player.getFaction())
                        .replace("666fin", ":"));
            activeGame.setStoredValue("futureMessageFor" + player.getFaction(), "");
        }
        for (Player p2 : activeGame.getRealPlayers()) {
            if (!activeGame
                .getStoredValue("futureMessageFor_" + player.getFaction() + "_" + p2.getFaction())
                .isEmpty()) {
                String msg2 = "This is a message sent from the past:\n" + activeGame
                    .getStoredValue("futureMessageFor_" + player.getFaction() + "_" + p2.getFaction())
                    .replace("666fin", ":");
                MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(),
                    p2.getRepresentation(true, true) + " your future message got delivered");
                Whisper.sendWhisper(activeGame, p2, player, msg2, "n", p2.getCardsInfoThread(), event.getGuild());
                activeGame.setStoredValue("futureMessageFor_" + player.getFaction() + "_" + p2.getFaction(), "");
            }
        }

        if (goingToPass) {
            player.setPassed(true);
            if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "olradincommander")) {
                ButtonHelperCommanders.olradinCommanderStep1(player, activeGame);
            }
            String text2 = player.getRepresentation() + " PASSED";
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), text2);
            if (player.hasTech("absol_aida")) {
                String msg = player.getRepresentation()
                    + " since you have absol AIDEV, you can research 1 Unit Upgrade here for 6 influence";
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
                if (!player.hasAbility("propagation")) {
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                        player.getRepresentation(true, true) + " you can use the button to get your tech",
                        List.of(Buttons.GET_A_TECH));
                } else {
                    List<Button> buttons2 = ButtonHelper.getGainCCButtons(player);
                    String message2 = player.getRepresentation() + "! Your current CCs are "
                        + player.getCCRepresentation()
                        + ". Use buttons to gain CCs";
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                        message2, buttons2);
                    activeGame.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                }
            }
            if (player.hasAbility("deliberate_action") && (player.getTacticalCC() == 0 || player.getStrategicCC() == 0 || player.getFleetCC() == 0)) {
                String msg = player.getRepresentation()
                    + " since you have deliberate action ability and passed while one of your pools was at 0, you can gain a CC to that pool";
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
                List<Button> buttons2 = ButtonHelper.getGainCCButtons(player);
                String message2 = player.getRepresentation() + "! Your current CCs are " + player.getCCRepresentation()
                    + ". Use buttons to gain CCs";
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message2, buttons2);
            }
            TurnEnd.pingNextPlayer(event, activeGame, player, true);
        }
    }

    public static String getMissedSCFollowsText(Game activeGame, Player player) {
        if (!activeGame.isStratPings())
            return null;
        boolean sendReminder = false;

        StringBuilder sb = new StringBuilder();
        sb.append(player.getRepresentation(true, true));
        sb.append(" Please resolve these before doing anything else:\n");
        int count = 0;
        for (int sc : activeGame.getPlayedSCsInOrder(player, activeGame)) {
            if (!player.hasFollowedSC(sc)) {
                sb.append("> ").append(Helper.getSCRepresentation(activeGame, sc));
                if (!activeGame.getStoredValue("scPlay" + sc).isEmpty()) {
                    sb.append(" ")
                        .append(activeGame.getStoredValue("scPlay" + sc).replace("666fin", ":"));
                }
                sb.append("\n");
                sendReminder = true;
                count++;
            }
        }
        sb.append("You currently have ").append(player.getStrategicCC()).append(" CC in your strategy pool.");
        return sendReminder ? sb.toString() : null;
    }

    public static List<Button> getStartOfTurnButtons(Player player, Game activeGame, boolean doneActionThisTurn,
        GenericInteractionCreateEvent event) {

        if (!doneActionThisTurn) {
            for (Player p2 : activeGame.getRealPlayers()) {
                if (!activeGame.getStoredValue(p2.getFaction() + "graviton").isEmpty()) {
                    activeGame.setStoredValue(p2.getFaction() + "graviton", "");
                }
            }
        }
        String finChecker = player.getFinsFactionCheckerPrefix();
        activeGame.setDominusOrb(false);

        List<Button> startButtons = new ArrayList<>();
        Button tacticalAction = Button.success(finChecker + "tacticalAction",
            "Tactical Action (" + player.getTacticalCC() + ")");
        int numOfComponentActions = ButtonHelper.getAllPossibleCompButtons(activeGame, player, event).size() - 2;
        Button componentAction = Button.success(finChecker + "componentAction",
            "Component Action (" + numOfComponentActions + ")");

        startButtons.add(tacticalAction);
        startButtons.add(componentAction);
        boolean hadAnyUnplayedSCs = false;
        for (Integer SC : player.getSCs()) {
            if (!activeGame.getPlayedSCs().contains(SC)) {
                hadAnyUnplayedSCs = true;
                if (activeGame.isHomeBrewSCMode()) {
                    Button strategicAction = Button.success(finChecker + "strategicAction_" + SC, "Play SC #" + SC);
                    startButtons.add(strategicAction);
                } else {
                    Button strategicAction = Button.success(finChecker + "strategicAction_" + SC, "Play SC #" + SC)
                        .withEmoji(Emoji.fromFormatted(Emojis.getSCEmojiFromInteger(SC)));
                    startButtons.add(strategicAction);
                }
            }
        }

        if (!hadAnyUnplayedSCs && !doneActionThisTurn) {
            Button pass = Button.danger(finChecker + "passForRound", "Pass");
            if (ButtonHelper.getEndOfTurnAbilities(player, activeGame).size() > 1) {
                startButtons.add(Button.primary("endOfTurnAbilities", "Do End Of Turn Ability ("
                    + (ButtonHelper.getEndOfTurnAbilities(player, activeGame).size() - 1) + ")"));
            }

            startButtons.add(pass);
            if (!activeGame.isFoWMode()) {
                for (Player p2 : activeGame.getRealPlayers()) {
                    for (int sc : player.getSCs()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(p2.getRepresentation(true, true));
                        sb.append(" You are getting this ping because SC #").append(sc)
                            .append(
                                " has been played and now it is their turn again and you still havent reacted. If you already reacted, check if your reaction got undone");
                        if (!activeGame.getStoredValue("scPlay" + sc).isEmpty()) {
                            sb.append("Message link is: ").append(
                                activeGame.getStoredValue("scPlay" + sc).replace("666fin", ":"))
                                .append("\n");
                        }
                        sb.append("You currently have ").append(p2.getStrategicCC())
                            .append(" CC in your strategy pool.");
                        if (!p2.hasFollowedSC(sc)) {
                            MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), sb.toString());
                        }
                    }
                }
            }

        }
        if (doneActionThisTurn) {
            ButtonHelperFactionSpecific.checkBlockadeStatusOfEverything(player, activeGame, event);
            if (ButtonHelper.getEndOfTurnAbilities(player, activeGame).size() > 1) {
                startButtons.add(Button.primary("endOfTurnAbilities", "Do End Of Turn Ability ("
                    + (ButtonHelper.getEndOfTurnAbilities(player, activeGame).size() - 1) + ")"));
            }
            startButtons.add(Button.danger(finChecker + "turnEnd", "End Turn"));
            if (ButtonHelper.isPlayerElected(activeGame, player, "minister_war")) {
                startButtons.add(Button.secondary(finChecker + "ministerOfWar", "Use Minister of War"));
            }
            if (!activeGame.getJustPlayedComponentAC()) {
                player.setWhetherPlayerShouldBeTenMinReminded(true);
            }
        } else {
            activeGame.setJustPlayedComponentAC(false);
            if (player.getTechs().contains("cm")) {
                Button chaos = Button.secondary("startChaosMapping", "Use Chaos Mapping")
                    .withEmoji(Emoji.fromFormatted(Emojis.Saar));
                startButtons.add(chaos);
            }
            if (player.getTechs().contains("dscymiy") && !player.getExhaustedTechs().contains("dscymiy")) {
                Button chaos = Button.secondary("exhaustTech_dscymiy", "Exhaust Recursive Worm")
                    .withEmoji(Emoji.fromFormatted(Emojis.cymiae));
                startButtons.add(chaos);
            }
            if (player.hasUnexhaustedLeader("florzenagent")
                && ButtonHelperAgents.getAttachments(activeGame, player).size() > 0) {
                startButtons.add(Button
                    .success(finChecker + "exhaustAgent_florzenagent_" + player.getFaction(),
                        "Use Florzen Agent")
                    .withEmoji(Emoji.fromFormatted(Emojis.florzen)));
            }
            if (player.hasUnexhaustedLeader("vadenagent")) {
                Button chaos = Button.secondary("exhaustAgent_vadenagent_" + player.getFaction(),
                    "Use Vaden Agent")
                    .withEmoji(Emoji.fromFormatted(Emojis.vaden));
                startButtons.add(chaos);
            }
            if (player.hasAbility("laws_order") && !activeGame.getLaws().isEmpty()) {
                Button chaos = Button.secondary("useLawsOrder", "Pay To Ignore Laws")
                    .withEmoji(Emoji.fromFormatted(Emojis.Keleres));
                startButtons.add(chaos);
            }
            if (player.hasTech("td") && !player.getExhaustedTechs().contains("td")) {
                Button transit = Button.secondary(finChecker + "exhaustTech_td", "Exhaust Transit Diodes");
                transit = transit.withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
                startButtons.add(transit);
            }
            if (player.hasUnexhaustedLeader("kolleccagent")) {
                Button nekroButton = Button.secondary("exhaustAgent_kolleccagent",
                    "Use Kollecc Agent")
                    .withEmoji(Emoji.fromFormatted(Emojis.kollecc));
                startButtons.add(nekroButton);
            }
        }
        if (player.hasTech("pa") && ButtonHelper.getPsychoTechPlanets(activeGame, player).size() > 1) {
            Button psycho = Button.success(finChecker + "getPsychoButtons",
                "Use Psychoarcheology");
            psycho = psycho.withEmoji(Emoji.fromFormatted(Emojis.BioticTech));
            startButtons.add(psycho);
        }
        Player p1 = player;
        String prefix = "componentActionRes_";
        for (Leader leader : p1.getLeaders()) {
            if (!leader.isExhausted() && !leader.isLocked()) {
                String leaderID = leader.getId();
                LeaderModel leaderModel = Mapper.getLeader(leaderID);
                if (leaderModel == null) {
                    continue;
                }
                String leaderName = leaderModel.getName();
                String leaderAbilityWindow = leaderModel.getAbilityWindow();
                String factionEmoji = Emojis.getFactionLeaderEmoji(leader);
                if ("ACTION:".equalsIgnoreCase(leaderAbilityWindow) || leaderName.contains("Ssruu")) {
                    if (leaderName.contains("Ssruu")) {
                        String led = "naaluagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Button
                                .secondary(finChecker + prefix + "leader_" + led,
                                    "Use " + leaderName + " as Z'eu (Naalu Agent)")
                                .withEmoji(Emoji.fromFormatted(factionEmoji));
                            startButtons.add(lButton);
                        }
                    } else {
                        if (leaderID.equalsIgnoreCase("naaluagent")) {
                            Button lButton = Button
                                .secondary(finChecker + prefix + "leader_" + leaderID, "Use " + leaderName)
                                .withEmoji(Emoji.fromFormatted(factionEmoji));
                            startButtons.add(lButton);
                        }
                    }
                } else if ("mahactcommander".equalsIgnoreCase(leaderID) && p1.getTacticalCC() > 0
                    && ButtonHelper.getTilesWithYourCC(p1, activeGame, event).size() > 0) {
                    Button lButton = Button.secondary(finChecker + "mahactCommander", "Use " + leaderName)
                        .withEmoji(Emoji.fromFormatted(factionEmoji));
                    startButtons.add(lButton);
                }
            }
        }

        Button transaction = Button.primary("transaction", "Transaction");
        startButtons.add(transaction);
        Button modify = Button.secondary("getModifyTiles", "Modify Units");
        startButtons.add(modify);
        if (player.hasUnexhaustedLeader("hacanagent")) {
            Button hacanButton = Button.secondary("exhaustAgent_hacanagent",
                "Use Hacan Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.Hacan));
            startButtons.add(hacanButton);
        }
        if (player.hasRelicReady("e6-g0_network")) {
            startButtons.add(Button.success("exhauste6g0network", "Exhaust E6-G0 Network Relic to Draw AC"));
        }
        if (player.hasUnexhaustedLeader("nekroagent") && player.getAc() > 0) {
            Button nekroButton = Button.secondary("exhaustAgent_nekroagent",
                "Use Nekro Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.Nekro));
            startButtons.add(nekroButton);
        }

        if (activeGame.getLatestTransactionMsg() != null
            && !"".equalsIgnoreCase(activeGame.getLatestTransactionMsg())) {
            activeGame.getMainGameChannel().deleteMessageById(activeGame.getLatestTransactionMsg()).queueAfter(1,
                TimeUnit.SECONDS);
            activeGame.setLatestTransactionMsg("");
        }
        // if (activeGame.getActionCards().size() > 130 &&
        // getButtonsToSwitchWithAllianceMembers(player, activeGame, false).size() > 0)
        // {
        // startButtons.addAll(getButtonsToSwitchWithAllianceMembers(player, activeGame,
        // false));
        // }
        if (!doneActionThisTurn && activeGame.isFoWMode()) {
            startButtons.add(Button.secondary("showGameAgain", "Show Game"));
        }

        return startButtons;
    }
}
