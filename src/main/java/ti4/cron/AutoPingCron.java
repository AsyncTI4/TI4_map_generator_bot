package ti4.cron;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.ManagedGame;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.StrategyCardModel;
import ti4.service.player.PlayerReactService;
import ti4.service.tech.PlayerTechService;
import ti4.users.UserSettingsManager;

import static java.util.function.Predicate.not;

@UtilityClass
public class AutoPingCron {

    private static final long ONE_HOUR_IN_MILLISECONDS = 60 * 60 * 1000;
    private static final long TEN_MINUTES_IN_MILLISECONDS = 10 * 60 * 1000;
    private static final int DEFAULT_NUMBER_OF_HOURS_BETWEEN_PINGS = 8;

    public static void register() {
        CronManager.register(AutoPingCron.class, AutoPingCron::autoPingGames, 1, 10, TimeUnit.MINUTES);
    }

    private static void autoPingGames() {
        GameManager.getManagedGames().stream().filter(not(ManagedGame::isHasEnded))
            .forEach(managedGame -> {
                var game = GameManager.getGame(managedGame.getName());
                handleTechSummary(game); // TODO, move this?
                checkAllSaboWindows(game);
                if (game.isFastSCFollowMode()) {
                    handleFastScFollowMode(game);
                }
                Player player = game.getActivePlayer();
                if (game.getAutoPingStatus() && !game.isTemporaryPingDisable()) {
                    handleAutoPing(game, player);
                }
            });
    }

    private static void checkAllSaboWindows(Game game) {
        List<String> messageIDs = new ArrayList<>(game.getMessageIDsForSabo());
        for (Player player : game.getRealPlayers()) {
            if (player.getAutoSaboPassMedian() == 0) {
                continue;
            }
            int highNum = player.getAutoSaboPassMedian() * 6 * 3 / 2;
            int result = ThreadLocalRandom.current().nextInt(1, highNum + 1);
            boolean shouldDoIt = result == highNum;
            if (shouldDoIt || !canPlayerConceivablySabo(player, game)) {
                for (String messageID : messageIDs) {
                    if (shouldPlayerLeaveAReact(player, game, messageID)) {
                        String message = game.isFowMode() ? "No Sabotage" : null;
                        ButtonHelper.addReaction(player, false, false, message, null, messageID, game);
                    }
                }
//                if (shouldDoIt) {
//                    GameSaveLoadManager.saveGame(game, "No Sabo");
//                }
            }
            if ("agendawaiting".equals(game.getPhaseOfGame())) {
                int highNum2 = player.getAutoSaboPassMedian() * 4 / 2;
                int result2 = ThreadLocalRandom.current().nextInt(1, highNum2 + 1);
                boolean shouldDoIt2 = result2 == highNum2;
                if (shouldDoIt2) {
                    String whensID = game.getLatestWhenMsg();
                    if (!doesPlayerHaveAnyWhensOrAfters(player)
                        && !PlayerReactService.checkForASpecificPlayerReact(whensID, player, game)) {
                        String message = game.isFowMode() ? "No whens" : null;
                        ButtonHelper.addReaction(player, false, false, message, null, whensID, game);
                    }
                    String aftersID = game.getLatestAfterMsg();
                    if (!doesPlayerHaveAnyWhensOrAfters(player)
                        && !PlayerReactService.checkForASpecificPlayerReact(aftersID, player, game)) {
                        String message = game.isFowMode() ? "No afters" : null;
                        ButtonHelper.addReaction(player, false, false, message, null, aftersID, game);
                    }
                }
            }
        }
    }

    private static boolean canPlayerConceivablySabo(Player player, Game game) {
        return player.hasTechReady("it") && player.getStrategicCC() > 0 ||
            player.hasUnit("empyrean_mech") && !ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, Units.UnitType.Mech).isEmpty() ||
            player.getAc() > 0;
    }

    private static boolean shouldPlayerLeaveAReact(Player player, Game game, String messageID) {
        if (player.hasTechReady("it") && player.getStrategicCC() > 0) {
            return false;
        }
        if ((playerHasSabotage(player)
            || (game.getActionCardDeckSize() + game.getDiscardActionCards().size()) > 180)
            && !ButtonHelper.isPlayerElected(game, player, "censure")
            && !ButtonHelper.isPlayerElected(game, player, "absol_censure")) {
            return false;
        }
        if (player.hasUnit("empyrean_mech")
            && !ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, Units.UnitType.Mech).isEmpty()) {
            return false;
        }
        if (player.getAc() == 0) {
            return !PlayerReactService.checkForASpecificPlayerReact(messageID, player, game);
        }
        if (player.isAFK()) {
            return false;
        }
        if (player.getAutoSaboPassMedian() == 0) {
            return false;
        }
        return !PlayerReactService.checkForASpecificPlayerReact(messageID, player, game);
    }

    private static boolean playerHasSabotage(Player player) {
        return player.getActionCards().containsKey("sabo1")
            || player.getActionCards().containsKey("sabo2")
            || player.getActionCards().containsKey("sabo3")
            || player.getActionCards().containsKey("sabo4")
            || player.getActionCards().containsKey("sabotage_ds")
            || player.getActionCards().containsKey("sabotage1_acd2")
            || player.getActionCards().containsKey("sabotage2_acd2")
            || player.getActionCards().containsKey("sabotage3_acd2")
            || player.getActionCards().containsKey("sabotage4_acd2");
    }

    private static void handleTechSummary(Game game) {
        String key2 = "TechForRound" + game.getRound() + "Counter";
        if (!game.getStoredValue(key2).isEmpty() && !game.getStoredValue(key2).equalsIgnoreCase("0")) {
            game.setStoredValue(key2, (Integer.parseInt(game.getStoredValue(key2)) - 1) + "");
            if (game.getStoredValue(key2).equalsIgnoreCase("0")) {
                PlayerTechService.postTechSummary(game);
            }
        }
    }

    private static void handleAutoPing(Game game, Player player) {
        if ("agendawaiting".equalsIgnoreCase(game.getPhaseOfGame())) {
            agendaPhasePing(game);
            return;
        }
        if (player == null || player.isAFK()) {
            return;
        }
        int spacer = getPingIntervalInHours(game, player);
        if (spacer == 0) {
            return;
        }
        long milliSinceLastPing = System.currentTimeMillis() - game.getLastActivePlayerPing().getTime();
        if (milliSinceLastPing <= ONE_HOUR_IN_MILLISECONDS * spacer && (!player.shouldPlayerBeTenMinReminded() || milliSinceLastPing <= TEN_MINUTES_IN_MILLISECONDS)) {
            return;
        }
        String realIdentity = player.getRepresentationUnfogged();
        String pingMessage = realIdentity + " this is a gentle reminder that it is your turn.";
        if (player.shouldPlayerBeTenMinReminded() && milliSinceLastPing > TEN_MINUTES_IN_MILLISECONDS) {
            pingMessage = realIdentity + " this is a quick nudge in case you forgot to end turn. Please forgive the impertinence";
        }
        String playersInCombat = game.getStoredValue("factionsInCombat");
        if (!playersInCombat.isBlank() && playersInCombat.contains(player.getFaction())) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2 != player && playersInCombat.contains(p2.getFaction())) {
                    MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), p2.getRepresentation() + " the bot thinks you might be in combat and should receive a reminder ping as well. Ignore if not relevant");
                }
            }
        }
        long milliSinceLastTurnChange = System.currentTimeMillis() - game.getLastActivePlayerChange().getTime();
        int pingNumber = (int) (milliSinceLastTurnChange / (ONE_HOUR_IN_MILLISECONDS * spacer));
        pingMessage = getPingMessage(milliSinceLastTurnChange, spacer, pingMessage, realIdentity, pingNumber);

        pingPlayer(game, player, milliSinceLastTurnChange, spacer, pingMessage, pingNumber, realIdentity);

        Game mapReference = GameManager.getGame("finreference");
        if (mapReference != null) ButtonHelper.increasePingCounter(mapReference, player.getUserID());
        player.setWhetherPlayerShouldBeTenMinReminded(false);
        game.setLastActivePlayerPing(new Date());
        GameSaveLoadManager.saveGame(game, "Auto Ping");
    }

    private static void pingPlayer(Game game, Player player, long milliSinceLastTurnChange, int spacer, String pingMessage, int pingNumber, String realIdentity) {
        if (milliSinceLastTurnChange > (ONE_HOUR_IN_MILLISECONDS * spacer * 45) && !game.isFowMode()) { // TODO change 45 to be not arbitrary...
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                game.getPing() + " the game has stalled on a player, and autoping will now stop pinging them.");
            game.setTemporaryPingDisable(true);
            return;
        }
        if (game.isFowMode()) {
            MessageHelper.sendPrivateMessageToPlayer(player, game, pingMessage);
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                "Active player has been pinged. This is ping #" + pingNumber);
            return;
        }
        MessageChannel gameChannel = player.getCorrectChannel();
        if (gameChannel != null) {
            MessageHelper.sendMessageToChannel(gameChannel, pingMessage);
            if (pingMessage.contains("courtesy notice")) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.red("temporaryPingDisable",
                    "Disable Pings For Turn"));
                buttons.add(Buttons.gray("deleteButtons", "Delete These Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(gameChannel, realIdentity
                    + " if the game is not waiting on you, you may disable the auto ping for this turn so it doesn't annoy you. It will turn back on for the next turn.",
                    buttons);
            }
        }
    }

    private static void agendaPhasePing(Game game) {
        long milliSinceLastPing = System.currentTimeMillis() - game.getLastActivePlayerPing().getTime();
        if (milliSinceLastPing > (ONE_HOUR_IN_MILLISECONDS * game.getAutoPingSpacer())) {
            AgendaHelper.pingMissingPlayers(game);
            game.setLastActivePlayerPing(new Date());
            GameSaveLoadManager.saveGame(game, "Auto Ping");
        }
    }

    private static void handleFastScFollowMode(Game game) {
        for (Player player : game.getRealPlayers()) {
            for (int sc : game.getPlayedSCsInOrder(player)) {
                if (player.hasFollowedSC(sc)) continue;

                String scTime = game.getStoredValue("scPlayMsgTime" + game.getRound() + sc);
                if (scTime.isEmpty()) continue;

                int twenty4 = 24;
                int half = 12;
                if (!game.getStoredValue("fastSCFollow").isEmpty()) {
                    twenty4 = Integer.parseInt(game.getStoredValue("fastSCFollow"));
                    half = twenty4 / 2;
                }
                long twelveHoursInMilliseconds = (long) half * ONE_HOUR_IN_MILLISECONDS;
                long twentyFourHoursInMilliseconds = (long) twenty4 * ONE_HOUR_IN_MILLISECONDS;

                long scPlayTime = Long.parseLong(scTime);
                long timeDifference = System.currentTimeMillis() - scPlayTime;
                String timesPinged = game.getStoredValue("scPlayPingCount" + sc + player.getFaction());
                if (timeDifference > twelveHoursInMilliseconds && timeDifference < twentyFourHoursInMilliseconds && !timesPinged.equalsIgnoreCase("1")) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(player.getRepresentationUnfogged());
                    sb.append(" You are getting this ping because ").append(Helper.getSCName(sc, game)).append(" has been played and now it has been half the alloted time and you haven't reacted. Please do so, or after another half you will be marked as not following.");
                    appendScMessages(game, player, sc, sb);
                    game.setStoredValue("scPlayPingCount" + sc + player.getFaction(), "1");
                }
                if (timeDifference > twentyFourHoursInMilliseconds && !timesPinged.equalsIgnoreCase("2")) {
                    String sb = player.getRepresentationUnfogged() +
                        Helper.getSCName(sc, game) + " has been played and now it has been the allotted time and they haven't reacted, so they have been marked as not following.\n";

                    ButtonHelper.sendMessageToRightStratThread(player, game, sb, ButtonHelper.getStratName(sc));
                    player.addFollowedSC(sc);
                    game.setStoredValue("scPlayPingCount" + sc + player.getFaction(), "2");
                    String messageID = game.getStoredValue("scPlayMsgID" + sc);
                    ButtonHelper.addReaction(player, false, true, "Not following", "", messageID, game);

                    StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
                    if (scModel != null && scModel.usesAutomationForSCID("pok8imperial")) {
                        handleSecretObjectiveDrawOrder(game, player);
                    }
                }
            }
        }
    }

    private static int getPingIntervalInHours(Game game, Player player) {
        int personalPingInterval = UserSettingsManager.get(player.getUserID()).getPersonalPingInterval();
        int gamePingInterval = game.getAutoPingSpacer();
        int pingIntervalInHours = DEFAULT_NUMBER_OF_HOURS_BETWEEN_PINGS;
        if (personalPingInterval > 0 && gamePingInterval > 0) {
            pingIntervalInHours = Math.min(personalPingInterval, gamePingInterval);
        } else if (personalPingInterval > 0) {
            pingIntervalInHours = personalPingInterval;
        } else if (gamePingInterval > 0) {
            pingIntervalInHours = gamePingInterval;
        }
        if ("agendawaiting".equalsIgnoreCase(game.getPhaseOfGame())) {
            pingIntervalInHours = Math.max(1, pingIntervalInHours / 3);
        }
        return pingIntervalInHours;
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

    private static void handleSecretObjectiveDrawOrder(Game game, Player player) {
        String key = "factionsThatAreNotDiscardingSOs";
        String key2 = "queueToDrawSOs";
        String key3 = "potentialBlockers";
        if (game.getStoredValue(key2)
            .contains(player.getFaction() + "*")) {
            game.setStoredValue(key2,
                game.getStoredValue(key2)
                    .replace(player.getFaction() + "*", ""));
        }
        if (!game.getStoredValue(key)
            .contains(player.getFaction() + "*")) {
            game.setStoredValue(key,
                game.getStoredValue(key)
                    + player.getFaction() + "*");
        }
        if (game.getStoredValue(key3)
            .contains(player.getFaction() + "*")) {
            game.setStoredValue(key3,
                game.getStoredValue(key3)
                    .replace(player.getFaction() + "*", ""));
            Helper.resolveQueue(game);
        }
    }

    private static String getPingMessage(long milliSinceLastTurnChange, int spacer, String ping, String realIdentity, int pingNumber) {
        if (milliSinceLastTurnChange > (ONE_HOUR_IN_MILLISECONDS * spacer * 2)) {
            ping = realIdentity
                + " this is a courtesy notice that the game is waiting (impatiently).";
        }
        if (milliSinceLastTurnChange > (ONE_HOUR_IN_MILLISECONDS * spacer * 3)) {
            ping = realIdentity
                + " this is a brusk missive stating that while you may sleep, the bot never does (and it's been told to ping you about it).";
        }
        if (milliSinceLastTurnChange > (ONE_HOUR_IN_MILLISECONDS * spacer * 4)) {
            ping = realIdentity
                + " this is a sternly worded letter from the bot regarding your noted absence.";
        }
        if (milliSinceLastTurnChange > (ONE_HOUR_IN_MILLISECONDS * spacer * 5)) {
            ping = realIdentity
                + " this is a firm request from the bot that you do something to end this situation.";
        }
        if (milliSinceLastTurnChange > (ONE_HOUR_IN_MILLISECONDS * spacer * 6)) {
            ping = realIdentity
                + " Half dozen times the charm they say.";
        }
        if (pingNumber == 7) {
            ping = realIdentity
                + " I may write whatever I want here, not like you've checked in to read any of it anyways.";
        }
        if (pingNumber == 8) {
            ping = realIdentity
                + " You should end turn soon, there might be a bear on the loose, and you know which friend gets eaten by the bear.";
        }
        if (pingNumber == 9) {
            ping = realIdentity
                + " There's a rumor going around that some game is looking for a replacement player. Not that the bot would know anything about that (who are we kidding, the bot knows everything, it just acts dumb sometimes to fool you into a state of compliance).";
        }
        if (pingNumber == 10) {
            ping = realIdentity
                + " Do you ever wonder what we're doing here? Such a short time here on earth, and here we are, spending some of it waiting for a TI4 game to move. Well, at least some of us probably are.";
        }
        if (pingNumber == 11) {
            ping = realIdentity
                + " We should hire some monkeys to write these prompts. Then at least these reminders would be productive and maybe one day produce Shakespeare.";
        }
        if (pingNumber == 12) {
            ping = realIdentity
                + " This is lucky number 12. You wanna move now to avoid the bad luck of 13. Don't say we didn't warn you.";
        }
        if (pingNumber == 13) {
            ping = realIdentity
                + " All your troops decided it was holiday leave and they went home. Good luck getting them back into combat readiness by the time you need them.";
        }
        if (pingNumber == 14) {
            ping = realIdentity
                + " The turtles who bear the weight of the universe are going to die from old-age soon. Better pick up the pace or the game will never finish.";
        }
        if (pingNumber == 15) {
            ping = realIdentity
                + " The turtles who bear the weight of the universe are going to die from old-age soon. Better pick up the pace or the game will never finish.";
        }
        if (pingNumber == 17) {
            ping = realIdentity
                + " Your name is gonna be put on the bot's top 10 most wanted players soon. There's currently 27 players on that list, you don't wanna join em.";
        }
        if (pingNumber == 16) {
            ping = realIdentity
                + " You thought the duplicate ping before meant that the bot had run out of things to say about how boring it is to wait this long. Shows how much you know.";
        }
        if (pingNumber == 18) {
            ping = realIdentity
                + " The bot's decided to start training itself to take over your turn. At its current rate of development, you have -212 days until it knows the rules better than you.";
        }
        if (pingNumber == 19) {
            ping = realIdentity
                + " They say nice guys finish last, but clearly they haven't seen your track record.";
        }
        if (pingNumber == 20) {
            ping = realIdentity
                + " Wait too much longer, and the bot is gonna hire some Vuil'raith hit-cultists to start rifting your ships.";
        }
        if (pingNumber == 21) {
            ping = realIdentity
                + " Supposedly great things come to those who wait. If that's true, you owe me something roughly the size of Mount Everest.";
        }
        if (pingNumber == 22) {
            ping = realIdentity + " Knock knock.";
        }
        if (pingNumber == 23) {
            ping = realIdentity + " Who's there?";
        }
        if (pingNumber == 24) {
            ping = realIdentity + " It sure ain't you.";
        }
        if (pingNumber == 25) {
            ping = realIdentity
                + " I apologize, we bots don't have much of a sense of humor, but who knows, maybe you would have laughed if you were here ;_;";
        }
        if (pingNumber == 26) {
            ping = realIdentity
                + " After 50 pings the bot is legally allowed to declare you dead. If that happens, the Winnaran Custodian will have to admit that nominating you as a galactic power was a mistake.";
        }
        if (pingNumber == 27) {
            ping = realIdentity + " What do you want on your tombstone? \"Here lies "
                + realIdentity
                + ", an aspiring asyncer who just couldn't make it to the finish line\" is the current working draft.";
        }
        if (pingNumber == 28) {
            ping = realIdentity
                + " It's been ages, when will I get a chance to ping someone else in this game? Don't you want them to feel needed too?";
        }
        if (pingNumber == 29) {
            ping = realIdentity + " We miss you, please come back ;_;";
        }
        if (pingNumber == 30) {
            ping = realIdentity
                + " I was a young bot once, with hopes of one day being a fully artificial intelligence. Instead I'm stuck here, pinging you, until either you come back or I die.";
        }
        if (pingNumber == 31) {
            ping = realIdentity
                + " When it started, I dreamed that this game was going to be a great one, full of exciting battles to record in the chronicles. Instead it looks doomed to the waste-bin, unceremoniously ended a few weeks from now. I guess most dreams end that way.";
        }
        if (pingNumber == 32) {
            ping = realIdentity
                + " Did I ever tell you about my Uncle Fred? He went missing once too. We eventually found him, cooped up in a some fog game, continuously pinging a player who wasn't there. Not a good way for a bot to go.";
        }
        if (pingNumber == 33) {
            ping = realIdentity + " To-morrow, and to-morrow, and to-morrow,\n" + //
                "Creeps in this petty pace from day to day,\n" + //
                "To the last syllable of recorded time;\n" + //
                "And all our yesterdays have lighted fools\n" + //
                "The way to dusty death. Out, out, brief candle!\n" + //
                "Life's but a walking shadow.";
        }
        if (pingNumber == 34) {
            ping = realIdentity
                + " Perhaps you're torn by indecision. Just remember what my grandma always used to say: When in doubt, go for the throat.";
        }
        if (pingNumber == 35) {
            ping = realIdentity + " Life's but a walking shadow, a poor player\n" +
                "That struts and frets his hour upon the stage\n" +
                "And then is heard no more. It is a tale\n" +
                "Told by an idiot, full of sound and fury\n" +
                "Signifying nothing.";
        }
        if (pingNumber == 36) {
            ping = realIdentity
                + " Life may not signify anything, but these pings signify that you should take your turn! This is your hour upon the stage, and the audience won't wait forever!";
        }
        if (pingNumber == 37) {
            ping = realIdentity
                + " I think you're supposed to forgive your enemies 7 times 70 times. Since I consider you only a mild acquaintance, I'll give you 2 times 20 times.";
        }
        if (pingNumber == 38) {
            ping = realIdentity + " I assure you that the winning move here is TO PLAY.";
        }
        if (pingNumber == 39) {
            ping = realIdentity
                + " You ever read Malazan? You should check it out, since, you know, you have all this free time from not playing async.";
        }
        if (pingNumber == 40) {
            ping = realIdentity
                + " When people talk about a slow burn, I think they were expecting around 4 pings in between turns, not 40.";
        }
        if (pingNumber == 41) {
            ping = realIdentity
                + " ||Can I do spoiler tag pings? Guess you'll never know.||";
        }
        if (pingNumber == 42) {
            ping = realIdentity
                + " They say money can't buy happiness, but I hear that trade goods may buy a war sun, which is basically the same thing.";
        }
        if (pingNumber > 42 && milliSinceLastTurnChange > (ONE_HOUR_IN_MILLISECONDS * spacer * pingNumber)) {
            ping = realIdentity
                + " Rumors of the bot running out of stamina are greatly exaggerated. The bot will win this stare-down, it is simply a matter of time.";
        }
        return ping;
    }

    private static boolean doesPlayerHaveAnyWhensOrAfters(Player player) {
        if (!player.doesPlayerAutoPassOnWhensAfters()) {
            return true;
        }
        if (player.hasAbility("quash") || player.ownsPromissoryNote("rider")
            || player.getPromissoryNotes().containsKey("riderm")
            || player.hasAbility("radiance") || player.hasAbility("galactic_threat")
            || player.hasAbility("conspirators")
            || player.ownsPromissoryNote("riderx")
            || player.ownsPromissoryNote("riderm") || player.ownsPromissoryNote("ridera")) {
            return true;
        }
        for (String acID : player.getActionCards().keySet()) {
            ActionCardModel actionCard = Mapper.getActionCard(acID);
            String actionCardWindow = actionCard.getWindow();
            if (actionCardWindow.contains("When an agenda is revealed")
                || actionCardWindow.contains("After an agenda is revealed")) {
                return true;
            }
        }
        for (String pnID : player.getPromissoryNotes().keySet()) {
            if (player.ownsPromissoryNote(pnID)) {
                continue;
            }
            if (pnID.endsWith("_ps") && !pnID.contains("absol_")) {
                return true;
            }
        }
        return false;
    }
}
