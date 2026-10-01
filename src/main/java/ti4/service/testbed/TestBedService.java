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
import ti4.service.fow.GMService;
import ti4.settings.GlobalSettings.ImplementedSettings;

@UtilityClass
public class TestBedService {

    static final String TEST_BED_KEY = "testBed";
    static final String ACTING_AS_PREFIX = "testBedActingAs_";
    public static final String VIRTUAL_SEAT_ID_PREFIX = "90000000000000";
    private static final Set<Integer> SAVE_FORMAT_SEPARATORS = Set.of((int) ',', (int) ':', (int) '\n');

    public static boolean isEnabled() {
        return ImplementedSettings.TESTBED_ENABLED.getAsBoolean(false);
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
        }
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

    @Nullable
    public static Player getActingAs(Game game, String userId) {
        String faction = game.getStoredValue(ACTING_AS_PREFIX + userId);
        return faction.isEmpty() ? null : game.getPlayerFromColorOrFaction(faction);
    }

    @Nullable
    public static Player resolveActingPlayer(
            Game game,
            @Nullable Member member,
            String userId,
            @Nullable String channelId,
            @Nullable Player defaultPlayer) {
        if (!isTestBed(game) || !isDeveloper(member)) return defaultPlayer;
        return resolveForDeveloper(game, userId, channelId, defaultPlayer);
    }

    @Nullable
    public static Player resolveActingPlayer(
            Game game, GenericInteractionCreateEvent event, @Nullable Player defaultPlayer) {
        Channel channel = event.getChannel();
        return resolveActingPlayer(
                game,
                event.getMember(),
                event.getUser().getId(),
                channel == null ? null : channel.getId(),
                defaultPlayer);
    }

    public static void logActingAs(Game game, String developerName, Player seat, String action) {
        GMService.logActivity(game, "[dev " + developerName + " as " + seat.getFaction() + "] `" + action + "`", false);
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
