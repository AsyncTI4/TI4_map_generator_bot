package ti4.helpers;

import java.util.List;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import ti4.AsyncTI4DiscordBot;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class TIGLHelper {

    public enum TIGLRank {
        UNRANKED("Unranked"),
        MINISTER("Minister"),
        AGENT("Agent"),
        COMMANDER("Commander"),
        HERO("Hero"),
        HERO_JOLNAR("Hero - JolNarHerosName"),
        HERO_MAHACT("Name - Mahact'sName"),
        ARBITER("Imperial Arbiter");

        private final String name;

        TIGLRank(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }

        public Role getRole() {
            List<Role> roles = AsyncTI4DiscordBot.guildPrimary.getRolesByName(name, false);
            if (roles.isEmpty()) {
                return null;
            }
            return roles.getFirst();
        }

        /**
         * Converts a string identifier to the corresponding SimpleStatistics enum value.
         * 
         * @param id the string identifier
         * @return the SimpleStatistics enum value, or null if not found
         */
        public static TIGLRank fromString(String id) {
            for (TIGLRank rank : values()) {
                if (id.equals(rank.toString())) {
                    return rank;
                }
            }
            return null;
        }

        public TIGLRank getNextRank() {
            return switch (this) {
                case UNRANKED -> TIGLRank.MINISTER;
                case MINISTER -> TIGLRank.AGENT;
                case AGENT -> TIGLRank.COMMANDER;
                case COMMANDER -> TIGLRank.HERO;
                case HERO -> TIGLRank.ARBITER;
                case ARBITER -> TIGLRank.ARBITER;
                default -> null;
            };
        }
    }

    private static final String TIGL_CHANNEL_NAME = "ti-global-league";
    private static final String TIGL_ADMIN_THREAD = "tigl-admin";

    public static boolean validateTIGLness() {
        boolean tiglProblem = false;
        for (TIGLRank rank : TIGLRank.values()) {
            if (rank.getRole() == null) {
                BotLogger.log("TIGLHelper.validateRoles: missing Role: `" + rank.name + "`");
                tiglProblem = true;
            }
        }
        if (getTIGLChannel() == null) {
            BotLogger.log("TIGLHelper.validateTIGLness: missing channel: `" + TIGL_CHANNEL_NAME + "`");
            tiglProblem = true;
        }
        if (getTIGLAdminThread() == null) {            
            BotLogger.log("TIGLHelper.validateTIGLness: missing thread: `" + TIGL_ADMIN_THREAD + "`");
            tiglProblem = true;
        }
        return tiglProblem;
    }

    public static void sendTIGLSetupText(Game game) {
        game.setCompetitiveTIGLGame(true);
        String message = "# " + Emojis.TIGL + "TIGL\nThis game has been flagged as a Twilight Imperium Global League (TIGL) Game!\n" +
            "Please ensure you have all:\n" +
            "- [Signed up for TIGL](https://forms.gle/QQKWraMyd373GsLN6) - there is no need to confirm your signup was successful\n" +
            "- Read and accepted the TIGL [Code of Conduct](https://discord.com/channels/943410040369479690/1003741148017336360/1155173892734861402)\n" +
            "For more information, please see this channel: https://discord.com/channels/943410040369479690/1003741148017336360\n" +
            "By continuing forward with this game, it is assumed you have accepted and are subject to the TIGL Code of Conduct";
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), message);
    }

    public static Role getLowestCommonRoleBetweenPlayers(List<User> users) {
        return null;
    }

    private static void promoteUser(User user, TIGLRank toRank) {

    }

    private static void dethroneHero(String faction) {

    }

    public static void checkIfTIGLRankUpOnGameEnd(Game game) {
        TIGLRank gameRank = game.getMinimumTIGLRankAtGameStart();
        Player winner = game.getWinner().orElse(null);
        if (gameRank == null || winner == null || !game.isCompetitiveTIGLGame()) {
            return;
        }
    }

    public static TextChannel getTIGLChannel() {
        List<TextChannel> channels = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName(TIGL_CHANNEL_NAME, false);
        if (channels.isEmpty()) {
            return null;
        } else if (channels.size() > 1) {
            BotLogger.log("TIGLHelper.getTIGLChannel: there appears to be more than one TIGL Channel: `" + TIGL_CHANNEL_NAME + "`");
        }
        return channels.getFirst();
    }

    public static ThreadChannel getTIGLAdminThread() {
        if (getTIGLChannel() == null) {
            return null;
        }
        ThreadChannel thread = getTIGLChannel().getThreadChannels().stream()
            .filter(c -> TIGL_ADMIN_THREAD.equals(c.getName()))
            .findFirst()
            .orElse(null);
        if (thread != null) {
            return thread;
        }
        for (ThreadChannel archivedThread : getTIGLChannel().retrieveArchivedPrivateThreadChannels().complete()) {
            if (TIGL_ADMIN_THREAD.equals(archivedThread.getName())) {
                archivedThread.getManager().setArchived(false).complete();
                thread = archivedThread;
            }
        }
        return thread;
    }
}
