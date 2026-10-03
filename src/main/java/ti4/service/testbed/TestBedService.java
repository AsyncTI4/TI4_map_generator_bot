package ti4.service.testbed;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.fow.GMService;
import ti4.settings.GlobalSettings;
import ti4.settings.GlobalSettings.ImplementedSettings;

@UtilityClass
public class TestBedService {

    static final String TEST_BED_KEY = "testBed";
    static final String ACTING_AS_PREFIX = "testBedActingAs_";
    static final String REAL_PLAYERS_KEY = "testBedRealPlayers";
    public static final String FOLLOW_TURN = "@turn";
    public static final String VIRTUAL_SEAT_ID_PREFIX = "90000000000000";
    private static final Set<Integer> SAVE_FORMAT_SEPARATORS = Set.of((int) ',', (int) ':', (int) '\n');

    public static boolean isEnabled() {
        Object value = GlobalSettings.getSetting(ImplementedSettings.TESTBED_ENABLED.toString(), Object.class, false);
        return Boolean.TRUE.equals(value)
                || "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    public static boolean isTestBed(@Nullable Game game) {
        return isEnabled() && isMarkedAsTestBed(game);
    }

    static boolean isMarkedAsTestBed(@Nullable Game game) {
        return game != null && "true".equals(game.getStoredValue(TEST_BED_KEY));
    }

    public static boolean isSaveSafe(String value) {
        return value.chars().noneMatch(SAVE_FORMAT_SEPARATORS::contains);
    }

    static void store(Game game, String key, String value) {
        if (!isSaveSafe(key) || !isSaveSafe(value)) {
            throw new IllegalArgumentException("Stored value `" + key + "` would corrupt the game save: " + value);
        }
        game.setStoredValue(key, value);
    }

    public static void markAsTestBed(Game game, boolean testBed) {
        if (testBed) {
            store(game, TEST_BED_KEY, "true");
        } else {
            game.removeStoredValue(TEST_BED_KEY);
            game.removeStoredValue(REAL_PLAYERS_KEY);
        }
    }

    public static boolean allowsRealPlayers(Game game) {
        return "true".equals(game.getStoredValue(REAL_PLAYERS_KEY));
    }

    public static void allowRealPlayers(Game game) {
        store(game, REAL_PLAYERS_KEY, "true");
    }

    static boolean actAsApplies(Game game, boolean componentInteraction) {
        return componentInteraction || !allowsRealPlayers(game);
    }

    public static boolean isDeveloper(@Nullable Member member) {
        return hasAnyRole(member, JdaService.developerRoles);
    }

    public static boolean isDeveloperId(@Nullable Guild guild, String userId) {
        return guild != null && isDeveloper(guild.getMemberById(userId));
    }

    @Nullable
    public static Player findNonDeveloper(@Nullable Guild guild, Collection<Player> players) {
        return players.stream()
                .filter(player -> !isVirtualSeat(player)
                        && !isBot(guild, player.getUserID())
                        && !isDeveloperId(guild, player.getUserID()))
                .findFirst()
                .orElse(null);
    }

    private static boolean isBot(@Nullable Guild guild, String userId) {
        Member member = guild == null ? null : guild.getMemberById(userId);
        User user =
                member != null ? member.getUser() : JdaService.jda == null ? null : JdaService.jda.getUserById(userId);
        return user != null && user.isBot();
    }

    public static boolean isVirtualSeat(Player player) {
        return isVirtualSeatId(player.getUserID());
    }

    static boolean isVirtualSeatId(@Nullable String userId) {
        return userId != null && userId.startsWith(VIRTUAL_SEAT_ID_PREFIX);
    }

    public static void clearAllActingAs(Game game) {
        List<String> keys = game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(ACTING_AS_PREFIX))
                .toList();
        keys.forEach(game::removeStoredValue);
    }

    private static boolean hasAnyRole(@Nullable Member member, Collection<Role> roles) {
        return member != null && member.getRoles().stream().anyMatch(roles::contains);
    }

    public static void setActingAs(Game game, String userId, @Nullable Player seat) {
        if (seat == null) {
            game.removeStoredValue(ACTING_AS_PREFIX + userId);
        } else {
            store(game, ACTING_AS_PREFIX + userId, seat.getFaction());
        }
    }

    static String rawActingAs(Game game, String userId) {
        return game.getStoredValue(ACTING_AS_PREFIX + userId);
    }

    static void restoreActingAs(Game game, String userId, String raw) {
        if (raw.isEmpty()) {
            game.removeStoredValue(ACTING_AS_PREFIX + userId);
        } else {
            store(game, ACTING_AS_PREFIX + userId, raw);
        }
    }

    public static void followTurn(Game game, String userId) {
        store(game, ACTING_AS_PREFIX + userId, FOLLOW_TURN);
    }

    public static boolean isFollowingTurn(Game game, String userId) {
        return FOLLOW_TURN.equals(game.getStoredValue(ACTING_AS_PREFIX + userId));
    }

    @Nullable
    public static Player getActingAs(Game game, String userId) {
        String faction = game.getStoredValue(ACTING_AS_PREFIX + userId);
        if (faction.isEmpty()) return null;
        if (FOLLOW_TURN.equals(faction)) return game.getActivePlayer();
        return game.getPlayerFromColorOrFaction(faction);
    }

    @Nullable
    public static Player resolveActingPlayer(
            Game game,
            @Nullable Member member,
            String userId,
            @Nullable String channelId,
            @Nullable Player defaultPlayer) {
        return resolve(game, member, userId, channelId, defaultPlayer, false);
    }

    @Nullable
    public static Player resolveActingPlayer(
            Game game, GenericInteractionCreateEvent event, @Nullable Player defaultPlayer) {
        return resolve(game, event, defaultPlayer, false);
    }

    @Nullable
    public static Player resolveActingPlayerForComponent(
            Game game, GenericInteractionCreateEvent event, @Nullable Player defaultPlayer) {
        return resolve(game, event, defaultPlayer, true);
    }

    @Nullable
    private static Player resolve(
            Game game, GenericInteractionCreateEvent event, @Nullable Player defaultPlayer, boolean component) {
        if (!isTestBed(game)) return defaultPlayer;
        Channel channel = event.getChannel();
        return resolve(
                game,
                event.getMember(),
                event.getUser().getId(),
                channel == null ? null : channel.getId(),
                defaultPlayer,
                component);
    }

    @Nullable
    private static Player resolve(
            Game game,
            @Nullable Member member,
            String userId,
            @Nullable String channelId,
            @Nullable Player defaultPlayer,
            boolean component) {
        if (!isTestBed(game) || !isDeveloper(member) || !actAsApplies(game, component)) return defaultPlayer;
        return resolveForDeveloper(game, userId, channelId, defaultPlayer);
    }

    public static boolean isPanelComponent(String componentId) {
        return componentId.startsWith(TestBedPanelService.PREFIX);
    }

    public static void logPanelUse(Game game, String developerName, String target, String buttonLabel) {
        String line = "[dev " + developerName + " panel, as " + target + "] `" + buttonLabel + "`";
        if (allowsRealPlayers(game) && !game.isFowMode()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "🛠️ " + line);
            return;
        }
        GMService.logActivity(game, line, false);
    }

    public static void logActingAs(Game game, String developerName, Player seat, String action) {
        String line = "[dev " + developerName + " as " + seat.getFaction() + "] `" + action + "`";
        if (allowsRealPlayers(game) && !isVirtualSeat(seat) && !game.isFowMode()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "🛠️ " + line);
            return;
        }
        GMService.logActivity(game, line, false);
    }

    @Nullable
    static Player resolveForDeveloper(
            Game game, String userId, @Nullable String channelId, @Nullable Player defaultPlayer) {
        Player seatOfChannel = seatOwningChannel(game, channelId);
        if (seatOfChannel != null) return seatOfChannel;
        Player actingAs = getActingAs(game, userId);
        return actingAs != null ? actingAs : defaultPlayer;
    }

    @Nullable
    private static Player seatOwningChannel(Game game, @Nullable String channelId) {
        if (channelId == null) return null;
        for (Player player : game.getRealPlayers()) {
            if (channelId.equals(player.getPrivateChannelID()) || channelId.equals(player.getCardsInfoThreadID())) {
                return player;
            }
        }
        return null;
    }
}
