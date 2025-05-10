package ti4.helpers;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.MiscEmojis;

public class TIGLHelper {

    public enum TIGLRank {
        UNRANKED("Async Rank - Unranked", 0), // <- because current formatter settings will oneline this list
        MINISTER("Async Rank - Minister", 1), //
        AGENT("Async Rank - Agent", 2), //
        COMMANDER("Async Rank - Commander", 3), //
        HERO("Async Rank - Hero", 4), //
        EMPEROR("Async Rank - Galactic Emperor", 99), // this is only obtainable once per TIGL season, not per HERO rankup game
        HERO_ARBOREC("Async Rank - Letani Miasmiala", -1), //
        HERO_ARGENT("Async Rank - Mirik Aun Sissiri", -1), //
        HERO_CABAL("Async Rank - It Feeds on Carrion", -1), //
        HERO_EMPYREAN("Async Rank - Conservator Procyon", -1), //
        HERO_GHOST("Async Rank - Riftwalker Meian", -1), //
        HERO_HACAN("Async Rank - Harrugh Gefhara", -1), //
        HERO_JOLNAR("Async Rank - Rin, The Master's Legacy", -1), //
        HERO_KELERESA("Async Rank - Kuuasi Aun Jalatai", -1), //
        HERO_KELERESM("Async Rank - Harka Leeds", -1), //
        HERO_KELERESX("Async Rank - Odlynn Myrr", -1), //
        HERO_L1Z1X("Async Rank - The Helmsman", -1), //
        HERO_LETNEV("Async Rank - Darktalon Treilla", -1), //
        HERO_MAHACT("Async Rank - Airo Shir Aur", -1), //
        HERO_MENTAK("Async Rank - Ipswitch, Loose Cannon", -1), //
        HERO_MUAAT("Async Rank - Adjudicator Ba'al", -1), //
        HERO_NAALU("Async Rank - The Oracle", -1), //
        HERO_NAAZ("Async Rank - Hesh and Prit", -1), //
        HERO_NEKRO("Async Rank - UNIT.DSGN.FLAYESH", -1), //
        HERO_NOMAD("Async Rank - Ahk-Syl Siven", -1), //
        HERO_SAAR("Async Rank - Gurno Aggero", -1), //
        HERO_SARDAKK("Async Rank - Sh'val, Harbinger", -1), //
        HERO_SOL("Async Rank - Jace X, 4th Air Legion", -1), //
        HERO_TITANS("Async Rank - Ul the Progenitor", -1), //
        HERO_WINNU("Async Rank - Mathis Mathinus", -1), //
        HERO_XXCHA("Async Rank - Xxekir Grom", -1), //
        HERO_YIN("Async Rank - Dannel of the Tenth", -1), //
        HERO_YSSARIL("Async Rank - Kyver, Blade and Key", -1);

        private final String name;
        private final Integer index;

        TIGLRank(String name, int index) {
            this.name = name;
            this.index = index;
        }

        public String getName() {
            return this.name;
        }

        public String getShortName() {
            return StringUtils.substringAfter(getName(), "- ");
        }

        public Integer getIndex() {
            return index;
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

        public TIGLRank getNextRank() {
            return switch (this) {
                case UNRANKED -> TIGLRank.MINISTER;
                case MINISTER -> TIGLRank.AGENT;
                case AGENT -> TIGLRank.COMMANDER;
                case COMMANDER -> TIGLRank.HERO;
                case HERO, EMPEROR -> TIGLRank.EMPEROR;
                default -> null;
            };
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
    }

    private static final String TIGL_CHANNEL_NAME = "ti-global-league";
    private static final String TIGL_ADMIN_THREAD = "tigl-admin";

    public static boolean validateTIGLness() {
        String testing = System.getenv("TESTING");
        if (testing != null) return false;

        boolean tiglProblem = false;
        if (getTIGLChannel() == null) {
            BotLogger.warning("TIGLHelper.validateTIGLness: missing channel: `" + TIGL_CHANNEL_NAME + "`");
            tiglProblem = true;
        }
        if (getTIGLAdminThread() == null) {
            BotLogger.warning("TIGLHelper.validateTIGLness: missing thread: `" + TIGL_ADMIN_THREAD + "`");
            tiglProblem = true;
        }
        if (!AsyncTI4DiscordBot.guildPrimaryID.equals(Constants.ASYNCTI4_HUB_SERVER_ID)) {
            return tiglProblem;
        }
        for (TIGLRank rank : TIGLRank.values()) {
            if (rank.getRole() == null) {
                BotLogger.warning("TIGLHelper.validateTIGLness: missing Role: `" + rank.name + "`");
                tiglProblem = true;
            }
        }
        return tiglProblem;
    }

    public static void initializeTIGLGame(Game game) {
        game.setCompetitiveTIGLGame(true);
        sendTIGLSetupText(game);
        List<User> users = game.getPlayers().values().stream().map(Player::getUser).toList();
        if (!allUsersAreMembersOfHubServer(users)) {
            String message = "Warning - there are players here who are not members of the AsyncTI4 HUB server. Automatic TIGL rank handling will not work.";
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), message);
            return;
        }
        TIGLRank lowestRank = getLowestCommonRankBetweenPlayers(users);
        game.setMinimumTIGLRankAtGameStart(lowestRank);
        for (Player player : game.getPlayers().values()) {
            player.setPlayerTIGLRankAtGameStart(getUsersHighestTIGLRank(player.getUser()));
        }
    }

    public static void sendTIGLSetupText(Game game) {
        String message = "# " + MiscEmojis.TIGL + "TIGL\nThis game has been flagged as a Twilight Imperium Global League (TIGL) Game!\n" +
            "Please ensure you have all:\n" +
            "- [Signed up for TIGL](https://forms.gle/QQKWraMyd373GsLN6) - there is no need to confirm your signup was successful\n" +
            "- Read and accepted the TIGL [Code of Conduct](https://discord.com/channels/943410040369479690/1003741148017336360/1155173892734861402)\n" +
            "For more information, please see this channel: https://discord.com/channels/943410040369479690/1003741148017336360\n" +
            "By continuing forward with this game, it is assumed you have accepted and are subject to the TIGL Code of Conduct";
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), message);
    }

    public static List<Role> getAllTIGLRoles() {
        List<Role> roles = new ArrayList<>();
        for (TIGLRank rank : TIGLRank.values()) {
            roles.add(rank.getRole());
        }
        return roles;
    }

    private static List<TIGLRank> getAllTIGLRanks() {
        return List.of(TIGLRank.values());
    }

    public static List<TIGLRank> getAllHeroTIGLRanks() {
        return getAllTIGLRanks().stream()
            .filter(r -> r.getIndex() == -1)
            .sorted(Comparator.comparing(TIGLRank::toString))
            .toList();
    }

    private static TIGLRank getTIGLRankFromRole(@Nullable Role role) {
        if (role == null) {
            return null;
        }
        for (TIGLRank rank : getAllTIGLRanks()) {
            if (role.equals(rank.getRole())) {
                return rank;
            }
        }
        return null;
    }

    private static TIGLRank getLowestCommonRankBetweenPlayers(List<User> users) {
        TIGLRank lowestRank = TIGLRank.HERO;
        for (User user : users) {
            TIGLRank rank = getUsersHighestTIGLRank(user);
            if (lowestRank.getIndex() > rank.getIndex()) {
                lowestRank = rank;
            }
        }
        return lowestRank;
    }

    public static List<TIGLRank> getUsersTIGLRanks(User user) {
        Member hubMember = AsyncTI4DiscordBot.guildPrimary.getMemberById(user.getId());
        if (hubMember == null) {
            return new ArrayList<>();
        }
        return hubMember.getRoles().stream()
            .filter(r -> getAllTIGLRoles().contains(r))
            .map(TIGLHelper::getTIGLRankFromRole)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(TIGLRank::getIndex))
            .toList();
    }

    private static TIGLRank getUsersHighestTIGLRank(User user) {
        List<TIGLRank> ranks = getUsersTIGLRanks(user);
        if (ranks.isEmpty()) {
            return TIGLRank.UNRANKED;
        }
        return ranks.getLast();
    }

    private static boolean allUsersAreMembersOfHubServer(List<User> users) {
        for (User user : users) {
            Member hubMember = AsyncTI4DiscordBot.guildPrimary.getMemberById(user.getId());
            if (hubMember == null) {
                return false;
            }
        }
        return true;
    }

    private static void promoteUser(User user, TIGLRank toRank) {
        TIGLRank currentRank = getUsersHighestTIGLRank(user);
        if (toRank.getIndex() - currentRank.getIndex() == 1) {
            AsyncTI4DiscordBot.guildPrimary.addRoleToMember(user, toRank.getRole()).queue();
            // AsyncTI4DiscordBot.guildPrimary.removeRoleFromMember(user, currentRank.getRole()).queueAfter(5, TimeUnit.SECONDS);
        }
        String message = user.getAsMention() + " has been promoted to **" + toRank.getRole().getName() + "**!";
        MessageHelper.sendMessageToChannel(getTIGLChannel(), message);
    }

    private static void crownNewHero(User user, String faction) {
        TIGLRank heroRank = TIGLRank.fromString("hero_" + faction);
        if (heroRank == null || heroRank.getRole() == null) {
            BotLogger.warning("TIGLHelper.dethroneHero - faction role not found: " + faction);
            return;
        }
        Role heroRole = heroRank.getRole();
        StringBuilder sb = new StringBuilder(user.getAsMention());
        sb.append(" has taken ").append(heroRole.getAsMention());
        List<Member> membersWithRole = AsyncTI4DiscordBot.guildPrimary.getMembersWithRoles(heroRole);
        if (membersWithRole.isEmpty()) {
            sb.append("!");
        } else {
            sb.append(" from ");
        }
        for (Member member : membersWithRole) {
            sb.append(member.getAsMention());
            AsyncTI4DiscordBot.guildPrimary.removeRoleFromMember(member, heroRank.getRole()).queueAfter(10, TimeUnit.SECONDS);
        }
        AsyncTI4DiscordBot.guildPrimary.addRoleToMember(user, heroRank.getRole()).queue();
        MessageHelper.sendMessageToChannel(getTIGLChannel(), LeaderEmojis.getLeaderEmoji(faction + "hero").toString());
        MessageHelper.sendMessageToChannel(getTIGLChannel(), sb.toString());
        // do stuff
    }

    public static void checkIfTIGLRankUpOnGameEnd(Game game) {
        TIGLRank gameRank = game.getMinimumTIGLRankAtGameStart();
        Player winner = game.getWinner().orElse(null);
        if (gameRank == null || winner == null || !game.isCompetitiveTIGLGame() || !game.isHasEnded()) {
            return;
        }
        User user = winner.getUser();
        TIGLRank userCurrentRank = getUsersHighestTIGLRank(user);
        TIGLRank nextRank = gameRank.getNextRank();
        if (nextRank.getIndex() - userCurrentRank.getIndex() == 1) {
            promoteUser(user, nextRank);
        }
        if (nextRank.getIndex() >= TIGLRank.HERO.getIndex()) {
            crownNewHero(user, winner.getFaction());
        }
    }

    private static TextChannel getTIGLChannel() {
        List<TextChannel> channels = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName(TIGL_CHANNEL_NAME, false);
        if (channels.isEmpty()) {
            return null;
        } else if (channels.size() > 1) {
            BotLogger.warning("TIGLHelper.getTIGLChannel: there appears to be more than one TIGL Channel: `" + TIGL_CHANNEL_NAME + "`");
        }
        return channels.getFirst();
    }

    private static ThreadChannel getTIGLAdminThread() {
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
