package ti4.cron;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.AgendaHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.settings.users.UserSettingsManager;

import static java.util.function.Predicate.not;

@UtilityClass
public class AutoPingCron {

    private static final long ONE_HOUR_IN_MILLISECONDS = 60 * 60 * 1000;
    private static final long TEN_MINUTES_IN_MILLISECONDS = 10 * 60 * 1000;
    private static final int DEFAULT_NUMBER_OF_HOURS_BETWEEN_PINGS = 8;

    public static void register() {
        CronManager.schedulePeriodically(AutoPingCron.class, AutoPingCron::autoPingGames, 5, 10, TimeUnit.MINUTES);
    }

    private static void autoPingGames() {
        GameManager.getManagedGames().stream()
            .filter(not(ManagedGame::isHasEnded))
            .map(ManagedGame::getGame)
            .filter(game -> game.getAutoPingStatus() && !game.isTemporaryPingDisable())
            .forEach(AutoPingCron::autoPingGame);
    }

    private static void autoPingGame(Game game) {
        try {
            handleAutoPing(game);
        } catch (Exception e) {
            BotLogger.log("AutoPing failed for game: " + game.getName(), e);
        }
    }

    private static void handleAutoPing(Game game) {
        if ("agendawaiting".equalsIgnoreCase(game.getPhaseOfGame())) {
            agendaPhasePing(game);
            return;
        }
        Player player = game.getActivePlayer();
        if (player == null || player.isAFK()) {
            return;
        }
        int spacer = getPingIntervalInHours(game, player);
        if (spacer == 0) {
            return;
        }
        long milliSinceLastPing = System.currentTimeMillis() - game.getLastActivePlayerPing().getTime();
        if (milliSinceLastPing <= ONE_HOUR_IN_MILLISECONDS * spacer &&
            (!player.shouldPlayerBeTenMinReminded() || milliSinceLastPing <= TEN_MINUTES_IN_MILLISECONDS)) {
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
        player.setWhetherPlayerShouldBeTenMinReminded(false);
        game.setLastActivePlayerPing(new Date());
        GameManager.save(game, "Auto Ping");
    }

    private static void pingPlayer(Game game, Player player, long milliSinceLastTurnChange, int spacer, String pingMessage, int pingNumber, String realIdentity) {
        if (milliSinceLastTurnChange > (ONE_HOUR_IN_MILLISECONDS * spacer * 45) && !game.isFowMode()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                game.getPing() + " the game has stalled on a player, and autoping will now stop pinging them.");
            game.setTemporaryPingDisable(true);
            GameManager.save(game, "Disable Auto Ping");
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
            GameManager.save(game, "Auto Ping");
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
}
