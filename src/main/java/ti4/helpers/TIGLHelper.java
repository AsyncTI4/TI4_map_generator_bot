package ti4.helpers;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.executors.ExecutionLockManager;
import ti4.executors.ExecutionLockType;
import ti4.executors.ExecutorServiceManager;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.tigl.TiglPlayerRankHistory;
import ti4.service.tigl.TiglRankHistoryService;

public final class TIGLHelper {

    private static final String RANK_SNAPSHOT_TASK = "tiglRankSnapshot-";
    public static final String RETRY_RANK_SNAPSHOT_BUTTON = "tiglRetryRankSnapshot";

    private enum TIGLLadder {
        STANDARD,
        FRACTURED
    }

    public enum TIGLRank {
        UNRANKED("TIGL - Unranked", 0), // <- because current formatter settings will oneline this list
        MINISTER("TIGL - Minister", 1), //
        AGENT("TIGL - Agent", 2), //
        COMMANDER("TIGL - Commander", 3), //
        HERO("TIGL - Hero", 4), //
        EMPEROR(
                "TIGL - Galactic Emperor",
                99), // this is only obtainable once per TIGL season, not per HERO rankup game
        THRALL("TIGL - Thrall", 1, TIGLLadder.FRACTURED),
        ACOLYTE("TIGL - Acolyte", 2, TIGLLadder.FRACTURED),
        LEGIONNAIRE("TIGL - Legionnaire", 3, TIGLLadder.FRACTURED),
        STARLANCER("TIGL - Starlancer", 4, TIGLLadder.FRACTURED),
        GENESORCERER("TIGL - Gene-Sorcerer", 5, TIGLLadder.FRACTURED),
        IXTHLORD("TIGL - Ixth-Lord", 6, TIGLLadder.FRACTURED),
        ARCHON("TIGL - Archon", 7, TIGLLadder.FRACTURED),
        HERO_ARBOREC("TIGL - Letani Miasmiala", -1), //
        HERO_ARGENT("TIGL - Mirik Aun Sissiri", -1), //
        HERO_CABAL("TIGL - It Feeds on Carrion", -1), //
        HERO_EMPYREAN("TIGL - Conservator Procyon", -1), //
        HERO_GHOST("TIGL - Riftwalker Meian", -1), //
        HERO_HACAN("TIGL - Harrugh Gefhara", -1), //
        HERO_JOLNAR("TIGL - Rin, The Master's Legacy", -1), //
        HERO_KELERESA("TIGL - Kuuasi Aun Jalatai", -1), //
        HERO_KELERESM("TIGL - Harka Leeds", -1), //
        HERO_KELERESX("TIGL - Odlynn Myrr", -1), //
        HERO_L1Z1X("TIGL - The Helmsman", -1), //
        HERO_LETNEV("TIGL - Darktalon Treilla", -1), //
        HERO_MAHACT("TIGL - Airo Shir Aur", -1), //
        HERO_MENTAK("TIGL - Ipswitch, Loose Cannon", -1), //
        HERO_MUAAT("TIGL - Adjudicator Ba'al", -1), //
        HERO_NAALU("TIGL - The Oracle", -1), //
        HERO_NAAZ("TIGL - Hesh and Prit", -1), //
        HERO_NEKRO("TIGL - UNIT.DSGN.FLAYESH", -1), //
        HERO_NOMAD("TIGL - Ahk-Syl Siven", -1), //
        HERO_SAAR("TIGL - Gurno Aggero", -1), //
        HERO_SARDAKK("TIGL - Sh'val, Harbinger", -1), //
        HERO_SOL("TIGL - Jace X, 4th Air Legion", -1), //
        HERO_TITANS("TIGL - Ul the Progenitor", -1), //
        HERO_WINNU("TIGL - Mathis Mathinus", -1), //
        HERO_XXCHA("TIGL - Xxekir Grom", -1), //
        HERO_YIN("TIGL - Dannel of the Tenth", -1), //
        HERO_YSSARIL("TIGL - Kyver, Blade and Key", -1);

        private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-z0-9]");

        @Getter
        private final String name;

        private final Integer index;
        private final Set<TIGLLadder> ladders;

        TIGLRank(String name, int index) {
            this(name, index, Set.of(TIGLLadder.STANDARD));
        }

        TIGLRank(String name, int index, TIGLLadder ladder) {
            this(name, index, Set.of(ladder));
        }

        TIGLRank(String name, int index, Set<TIGLLadder> ladders) {
            this.name = name;
            this.index = index;
            this.ladders = ladders;
        }

        public String getShortName() {
            return StringUtils.substringAfter(name, "- ");
        }

        Integer getIndex() {
            return index;
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase(Locale.ROOT);
        }

        public Role getRole() {
            List<Role> roles = JdaService.guildPrimary.getRolesByName(name, false);
            if (roles.isEmpty()) {
                return null;
            }
            return roles.getFirst();
        }

        boolean belongsToLadder(boolean isFractured) {
            TIGLLadder ladder = isFractured ? TIGLLadder.FRACTURED : TIGLLadder.STANDARD;
            return ladders.contains(ladder);
        }

        TIGLRank getNextRank() {
            return switch (this) {
                case UNRANKED -> MINISTER;
                case MINISTER -> AGENT;
                case AGENT -> COMMANDER;
                case COMMANDER -> HERO;
                case HERO, EMPEROR -> EMPEROR;
                default -> null;
            };
        }

        public static List<TIGLRank> getSortedRanks() {
            return Arrays.stream(values())
                    .filter(rank -> rank.index >= 0)
                    .sorted(Comparator.comparing(TIGLRank::getIndex))
                    .toList();
        }

        /**
         * Converts a string identifier to the corresponding TIGL rank.
         *
         * @param id the string identifier
         * @return the TIGL rank, or null if not found
         */
        public static TIGLRank fromString(String id) {
            if (isBlank(id)) return null;
            String normalizedInput = normalizeRankId(id);
            for (TIGLRank rank : values()) {
                if (id.equals(rank.toString())
                        || normalizedInput.equals(normalizeRankId(rank.toString()))
                        || normalizedInput.equals(normalizeRankId(rank.getName()))
                        || normalizedInput.equals(normalizeRankId(rank.getShortName()))) {
                    return rank;
                }
            }
            return null;
        }

        private static String normalizeRankId(String id) {
            return NON_ALPHANUMERIC_PATTERN.matcher(id.toLowerCase(Locale.ROOT)).replaceAll("");
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
        if (!JdaService.isProduction()) {
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

    public static void initializeTIGLGame(Game game, boolean isFractured) {
        if (!markAsTIGLGame(game, isFractured)) {
            return;
        }
        initializeRanksAsync(game, game.getTableTalkOrActionsChannel());
    }

    public static boolean markAsTIGLGame(Game game, boolean isFractured) {
        if (!game.canBeCompetitiveTIGLGame(isFractured)) {
            return false;
        }
        boolean wasAlreadyTiglGame = game.isCompetitiveTIGLGame();
        if (isFractured) {
            addFracturedTag(game);
        } else {
            removeFracturedTag(game);
        }
        game.setCompetitiveTIGLGame(true);
        if (!wasAlreadyTiglGame) {
            sendTIGLSetupText(game);
        }
        return true;
    }

    public static TIGLRank rankAtGameStartFor(Game game, String userId) {
        Long discordUserId = parseDiscordUserId(userId);
        if (discordUserId == null) {
            return TIGLRank.UNRANKED;
        }
        try {
            Map<Long, TiglPlayerRankHistory> histories = TiglRankHistoryService.fetchHistories(List.of(discordUserId));
            String league = TiglRankHistoryService.leagueFor(isFracturedTIGLGame(game));
            return TiglRankHistoryService.rankAtTimestamp(
                            histories.get(discordUserId), league, game.getCreationDateTime())
                    .map(TIGLHelper::resolveLeagueRankName)
                    .filter(Objects::nonNull)
                    .orElse(TIGLRank.UNRANKED);
        } catch (Exception e) {
            BotLogger.error(
                    Constants.lazikPing() + " " + Constants.niuPing() + " TIGL rank lookup failed for user " + userId
                            + " in game " + game.getName() + ": " + e.getMessage(),
                    e);
            return null;
        }
    }

    public static boolean isBelowGameRank(Game game, TIGLRank rank) {
        TIGLRank minimum = game.getMinimumTIGLRankAtGameStart();
        return minimum != null && rank != null && rank.getIndex() < minimum.getIndex();
    }

    public static void initializeRanksAsync(Game game, MessageChannel channel) {
        List<Long> discordUserIds = discordUserIds(game.getRealPlayers());
        if (!game.isCompetitiveTIGLGame() || discordUserIds.isEmpty()) {
            return;
        }
        String gameName = game.getName();
        boolean isFractured = isFracturedTIGLGame(game);
        ExecutorServiceManager.runAsync(
                RANK_SNAPSHOT_TASK + gameName, () -> snapshotRanks(gameName, isFractured, discordUserIds, channel));
    }

    private static void snapshotRanks(
            String gameName, boolean isFractured, List<Long> discordUserIds, MessageChannel channel) {
        Map<Long, TiglPlayerRankHistory> histories;
        try {
            histories = TiglRankHistoryService.fetchHistories(discordUserIds);
        } catch (Exception e) {
            offerRetry(gameName, channel, e);
            return;
        }
        ExecutionLockManager.lock(gameName, ExecutionLockType.WRITE);
        try {
            recordRanks(gameName, isFractured, histories);
        } finally {
            ExecutionLockManager.unlock(gameName, ExecutionLockType.WRITE);
        }
    }

    private static void recordRanks(String gameName, boolean isFractured, Map<Long, TiglPlayerRankHistory> histories) {
        Game game = GameManager.reload(gameName);
        if (game == null || !game.isCompetitiveTIGLGame()) {
            return;
        }
        List<Player> players = game.getRealPlayers();
        if (players.isEmpty()) {
            return;
        }
        String league = TiglRankHistoryService.leagueFor(isFractured);
        long gameStart = game.getCreationDateTime();
        TIGLRank lowestRank = null;
        for (Player player : players) {
            TIGLRank rank = rankAtGameStart(histories, player, league, gameStart);
            player.setPlayerTIGLRankAtGameStart(rank);
            if (lowestRank == null || rank.getIndex() < lowestRank.getIndex()) {
                lowestRank = rank;
            }
        }
        game.setMinimumTIGLRankAtGameStart(lowestRank);
        GameManager.save(game, "TIGL rank snapshot");
    }

    private static void offerRetry(String gameName, MessageChannel channel, Exception cause) {
        BotLogger.error(
                Constants.lazikPing() + " " + Constants.niuPing() + " TIGL rank lookup failed for game " + gameName
                        + ": " + cause.getMessage(),
                cause);
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                "The TIGL league could not be reached, so this game has no ranks recorded yet.",
                List.of(Buttons.green(RETRY_RANK_SNAPSHOT_BUTTON, "Retry the league lookup"), Buttons.CANCEL));
    }

    static TIGLRank rankAtGameStart(
            Map<Long, TiglPlayerRankHistory> histories, Player player, String league, long gameStart) {
        Long discordUserId = parseDiscordUserId(player.getUserID());
        if (discordUserId == null) {
            return TIGLRank.UNRANKED;
        }
        return TiglRankHistoryService.rankAtTimestamp(histories.get(discordUserId), league, gameStart)
                .map(name -> resolveOrReport(name, player))
                .orElse(TIGLRank.UNRANKED);
    }

    private static TIGLRank resolveOrReport(String rankName, Player player) {
        TIGLRank rank = resolveLeagueRankName(rankName);
        if (rank != null) {
            return rank;
        }
        BotLogger.warning("TIGL rank name \"" + rankName + "\" did not map to a ladder rank, so " + player.getUserName()
                + " was recorded as Unranked.");
        return TIGLRank.UNRANKED;
    }

    static TIGLRank resolveLeagueRankName(String rankName) {
        TIGLRank direct = TIGLRank.fromString(rankName);
        if (direct != null) {
            return direct;
        }
        String ladderRank = StringUtils.substringBetween(rankName, "(", ")");
        return ladderRank == null ? null : TIGLRank.fromString(ladderRank);
    }

    private static List<Long> discordUserIds(List<Player> players) {
        return players.stream()
                .map(player -> parseDiscordUserId(player.getUserID()))
                .filter(Objects::nonNull)
                .toList();
    }

    private static Long parseDiscordUserId(String userId) {
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static void sendTIGLSetupText(Game game) {
        String message = "# " + MiscEmojis.TIGL
                + "TIGL\nThis game has been flagged as a Twilight Imperium Global League (TIGL) Game!\n"
                + "Please ensure you have all read the TIGL [Code of Conduct](https://docs.google.com/document/d/1WoFPiluIz5cw80x1-WxUeckADIdYszNFZFTb6d648tk/edit?tab=t.0#heading=h.v2yzcvem3ohu)\n"
                + "By continuing forward with this game, it is assumed you have accepted and are subject to the TIGL Code of Conduct.\n\n"
                + "For more information, please see this channel: https://discord.com/channels/943410040369479690/1003741148017336360\n and the [Rules Document](https://docs.google.com/document/d/1WoFPiluIz5cw80x1-WxUeckADIdYszNFZFTb6d648tk/edit?tab=t.0)";
        MessageHelper.sendMessageToChannel(game.getTableTalkOrActionsChannel(), message);
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

    public static List<String> filterStandardTiglRankOptionsAtOrBelow(User user, List<String> options) {
        return filterStandardTiglRankOptionsAtOrBelow(
                getUsersHighestTIGLRank(user, false).getIndex(), options);
    }

    public static List<String> filterStandardTiglRankOptionsAtOrBelow(List<User> users, List<String> options) {
        if (users.isEmpty()) {
            return List.of();
        }
        return filterStandardTiglRankOptionsAtOrBelow(
                getLowestCommonRankBetweenPlayers(users, false).getIndex(), options);
    }

    private static List<String> filterStandardTiglRankOptionsAtOrBelow(int maxIndex, List<String> options) {
        return options.stream()
                .filter(opt -> {
                    TIGLRank rank = TIGLRank.fromString(opt);
                    return rank != null && rank.getIndex() >= 0 && rank.getIndex() <= maxIndex;
                })
                .toList();
    }

    private static Map<Long, TIGLRank> getTIGLRoleIdToRankMap() {
        Map<Long, TIGLRank> roleIdToRank = new HashMap<>();
        for (TIGLRank rank : getAllTIGLRanks()) {
            Role role = rank.getRole();
            if (role != null) {
                roleIdToRank.put(role.getIdLong(), rank);
            }
        }
        return roleIdToRank;
    }

    private static TIGLRank getLowestCommonRankBetweenPlayers(List<User> users, boolean isFractured) {
        TIGLRank lowestRank = isFractured ? TIGLRank.ARCHON : TIGLRank.HERO;
        for (User user : users) {
            TIGLRank rank = getUsersHighestTIGLRank(user, isFractured);
            if (lowestRank.getIndex() > rank.getIndex()) {
                lowestRank = rank;
            }
        }
        return lowestRank;
    }

    private static List<TIGLRank> getUsersTIGLRanks(User user, boolean isFractured) {
        Member hubMember = JdaService.guildPrimary.getMemberById(user.getId());
        if (hubMember == null) {
            return new ArrayList<>();
        }
        Map<Long, TIGLRank> roleIdToRank = getTIGLRoleIdToRankMap();
        return hubMember.getRoles().stream()
                .map(r -> roleIdToRank.get(r.getIdLong()))
                .filter(Objects::nonNull)
                .filter(r -> r.belongsToLadder(isFractured))
                .sorted(Comparator.comparing(TIGLRank::getIndex))
                .toList();
    }

    private static TIGLRank getUsersHighestTIGLRank(User user) {
        return getUsersHighestTIGLRank(user, false);
    }

    private static TIGLRank getUsersHighestTIGLRank(User user, boolean isFractured) {
        List<TIGLRank> ranks = getUsersTIGLRanks(user, isFractured);
        if (ranks.isEmpty()) {
            return TIGLRank.UNRANKED;
        }
        return ranks.getLast();
    }

    private static void promoteUser(User user, TIGLRank toRank) {
        TIGLRank currentRank = getUsersHighestTIGLRank(user);
        if (toRank.getIndex() - currentRank.getIndex() == 1) {
            JdaService.guildPrimary
                    .addRoleToMember(user, toRank.getRole())
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
        String message = user.getAsMention() + " has been promoted to **"
                + toRank.getRole().getName() + "**!";
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
        List<Member> membersWithRole = JdaService.guildPrimary.getMembersWithRoles(heroRole);
        if (membersWithRole.isEmpty()) {
            sb.append("!");
        } else {
            sb.append(" from ");
        }
        for (Member member : membersWithRole) {
            sb.append(member.getAsMention());
            JdaService.guildPrimary
                    .removeRoleFromMember(member, heroRank.getRole())
                    .queueAfter(10, TimeUnit.SECONDS);
        }
        JdaService.guildPrimary
                .addRoleToMember(user, heroRank.getRole())
                .queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannel(
                getTIGLChannel(), LeaderEmojis.getLeaderEmoji(faction + "hero").toString());
        MessageHelper.sendMessageToChannel(getTIGLChannel(), sb.toString());
        // do stuff
    }

    /**
     * @deprecated Obsolete legacy TIGL rank-up flow. No active code paths call this method.
     */
    @Deprecated(forRemoval = true, since = "2026-04")
    public static void checkIfTIGLRankUpOnGameEnd(Game game) {
        TIGLRank gameRank = game.getMinimumTIGLRankAtGameStart();
        Player winner = game.getWinner().orElse(null);
        if (gameRank == null || winner == null || !game.isCompetitiveTIGLGame() || !game.isHasEnded()) {
            return;
        }
        User user = winner.getUser();
        TIGLRank userCurrentRank = getUsersHighestTIGLRank(user);
        TIGLRank nextRank = gameRank.getNextRank();
        if (nextRank == null) {
            return;
        }
        if (nextRank.getIndex() - userCurrentRank.getIndex() == 1) {
            promoteUser(user, nextRank);
        }
        if (nextRank.getIndex() >= TIGLRank.HERO.getIndex()) {
            crownNewHero(user, winner.getFaction());
        }
    }

    private static TextChannel getTIGLChannel() {
        List<TextChannel> channels = JdaService.guildPrimary.getTextChannelsByName(TIGL_CHANNEL_NAME, false);
        if (channels.isEmpty()) {
            return null;
        } else if (channels.size() > 1) {
            BotLogger.warning("TIGLHelper.getTIGLChannel: there appears to be more than one TIGL Channel: `"
                    + TIGL_CHANNEL_NAME + "`");
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
        for (ThreadChannel archivedThread :
                getTIGLChannel().retrieveArchivedPrivateThreadChannels().complete()) {
            if (TIGL_ADMIN_THREAD.equals(archivedThread.getName())) {
                archivedThread.getManager().setArchived(false).complete();
                thread = archivedThread;
            }
        }
        return thread;
    }

    // Fractured
    public static boolean isFracturedTIGLGame(Game game) {
        return game.getTags().contains(Constants.TIGL_FRACTURED_TAG);
    }

    private static void addFracturedTag(Game game) {
        if (!isFracturedTIGLGame(game)) {
            game.addTag(Constants.TIGL_FRACTURED_TAG);
        }
    }

    public static void removeFracturedTag(Game game) {
        if (isFracturedTIGLGame(game)) {
            game.removeTag(Constants.TIGL_FRACTURED_TAG);
        }
    }
}
