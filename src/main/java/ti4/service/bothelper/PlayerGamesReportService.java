package ti4.service.bothelper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.game.persistence.ManagedPlayer;
import ti4.helpers.SearchGameHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.emoji.ColorEmojis;
import ti4.service.game.TeamSeatService;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;
import ti4.spring.service.statistics.UserGameInfoService;
import ti4.spring.service.statistics.UserGameInfoService.UserGameRecord;

@UtilityClass
public class PlayerGamesReportService {

    private static final String EDIT_TRACK_RECORD_BUTTON = "editTrackRecord~MDL";
    private static final String DETAIL_PREFIX = "└ ";

    public static void sendReport(
            SlashCommandInteractionEvent event,
            User target,
            boolean includeEndedGames,
            boolean ignoreAborted,
            boolean showGameModes) {
        String targetId = target.getId();
        UserGameRecord record = UserGameInfoService.get().getUserGameRecord(targetId);
        List<GameSeat> seats = findSeats(targetId, includeEndedGames, ignoreAborted);

        String report = buildHeader(target, record)
                + buildActiveSection(seats, targetId, showGameModes)
                + buildEndedSection(seats, targetId)
                + buildReplacedGames(record.replacedGameNames());
        List<Button> buttons = List.of(Buttons.gray(EDIT_TRACK_RECORD_BUTTON + "_" + targetId, "Edit Track Record"));
        for (MessageCreateData message : MessageHelper.getMessageCreateDataObjects(report, buttons)) {
            event.getHook().sendMessage(message).setEphemeral(true).queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    public static Modal trackRecordModal(String userId) {
        String previousRecord = UserSettingsManager.get(userId).getTrackRecord();
        TextInput summary = TextInput.create("record", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Edit the user's track record here.")
                .setValue(previousRecord.isEmpty() ? null : previousRecord)
                .setRequired(false)
                .build();
        return Modal.create("finishTrackRecord_" + userId, "Track Record")
                .addComponents(Label.of("Edit user's track record", summary))
                .build();
    }

    @ButtonHandler(value = EDIT_TRACK_RECORD_BUTTON, save = false)
    public static void editTrackRecord(ButtonInteractionEvent event, String buttonID) {
        if (!CommandHelper.hasRole(event, JdaService.bothelperRoles)) {
            event.reply("You must have the Bothelper role to use this.")
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }
        String userId = StringUtils.substringAfter(buttonID, EDIT_TRACK_RECORD_BUTTON + "_");
        event.replyModal(trackRecordModal(userId)).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private record GameSeat(Game game, Player player) {}

    private static List<GameSeat> findSeats(String targetId, boolean includeEndedGames, boolean ignoreAborted) {
        List<GameSeat> seats = new ArrayList<>();
        for (ManagedGame managedGame :
                SearchGameHelper.findGamesIncludingActiveFog(targetId, includeEndedGames, ignoreAborted)) {
            Game game = managedGame.getGame();
            Player player = TeamSeatService.seatOf(game, targetId);
            if (player != null && !player.isSpectator()) {
                seats.add(new GameSeat(game, player));
            }
        }
        return seats;
    }

    private static String buildHeader(User target, UserGameRecord record) {
        String userId = target.getId();
        UserSettings settings = UserSettingsManager.get(userId);
        StringBuilder sb = new StringBuilder("## ")
                .append(target.getName())
                .append("  ·  `")
                .append(userId)
                .append("`\n");
        appendTrackRecord(sb, settings.getTrackRecord());
        appendGameLimit(sb, userId, settings);
        appendLifetimeRecord(sb, userId, record);
        appendActivity(sb, settings);
        return sb.toString();
    }

    static List<String> trackRecordNotes(String trackRecord) {
        return Arrays.stream(trackRecord.split("(?<=\\.)\\s+"))
                .map(String::trim)
                .filter(note -> !note.isEmpty())
                .toList();
    }

    private static void appendTrackRecord(StringBuilder sb, String trackRecord) {
        sb.append("**Track record**\n");
        List<String> notes = trackRecordNotes(trackRecord);
        if (notes.isEmpty()) {
            sb.append("> _No notes._\n");
            return;
        }
        for (String note : notes) {
            sb.append("> • ").append(note).append('\n');
        }
    }

    private static void appendGameLimit(StringBuilder sb, String userId, UserSettings settings) {
        ManagedPlayer managedPlayer = GameManager.getManagedPlayer(userId);
        int ongoing = UserGameInfoService.countOngoingGamesThatAffectJoinLimit(managedPlayer);
        int completed = UserGameInfoService.countCompletedGamesThatAffectJoinLimit(managedPlayer);
        int dropped = UserGameInfoService.countDroppedGames(userId);
        sb.append("**Limit:** `")
                .append(ongoing)
                .append("` ongoing / `")
                .append(completed - dropped + 3)
                .append("` allowed  (")
                .append(completed)
                .append(" completed, ")
                .append(dropped)
                .append(" dropped)");
        if (UserGameInfoService.isOverStandardGameLimit(managedPlayer)) {
            sb.append("  ⚠️ **over limit**");
        }
        if (settings.getGameLimit() > 0) {
            sb.append(" · custom limit `").append(settings.getGameLimit()).append('`');
        }
        LocalDateTime lockedUntil = settings.getLockedFromCreatingGamesUntil();
        if (lockedUntil != null) {
            long epochSeconds = lockedUntil.atZone(ZoneId.systemDefault()).toEpochSecond();
            sb.append(" · 🔒 can't create games until <t:").append(epochSeconds).append(":f>");
        }
        sb.append('\n');
    }

    private static void appendLifetimeRecord(StringBuilder sb, String userId, UserGameRecord record) {
        sb.append("**Lifetime:** `").append(record.completedGames()).append("` completed");
        if (record.completedGames() > 0) {
            int winPercentage = Math.round(100f * record.wins() / record.completedGames());
            sb.append(" · `")
                    .append(record.wins())
                    .append("` wins (")
                    .append(winPercentage)
                    .append("%)");
        }
        sb.append(" · `").append(record.ongoingGames()).append("` ongoing");
        int replaced = record.replacedCompleted() + record.replacedOngoing();
        if (replaced > 0) {
            sb.append(" · replaced in `").append(replaced).append('`');
        }
        sb.append('\n');

        List<Integer> fastestDays = UserGameInfoService.get().getUsersThreeFastestDaysToComplete6PlayerGames(userId);
        if (!fastestDays.isEmpty()) {
            sb.append("**Fastest 6p:** ")
                    .append(StringUtils.join(fastestDays, ", "))
                    .append(" days\n");
        }
    }

    private static void appendActivity(StringBuilder sb, UserSettings settings) {
        String activeHours = UserSettings.summarizeActiveHoursEmoji(settings.getActiveHours());
        if (StringUtils.isNotBlank(activeHours)) {
            sb.append("**Active hours:** ").append(activeHours).append('\n');
        }
        if (StringUtils.isNotBlank(settings.getAfkHours())) {
            sb.append("**AFK hours:** ").append(settings.getAfkHours()).append('\n');
        }
    }

    private static String buildActiveSection(List<GameSeat> seats, String targetId, boolean showModes) {
        List<GameSeat> active = seats.stream()
                .filter(seat -> !seat.game().isHasEnded())
                .sorted(Comparator.comparing(seat -> isFogGame(seat.game())))
                .toList();
        StringBuilder sb =
                new StringBuilder("\n### Active (").append(active.size()).append(")\n");
        if (active.isEmpty()) {
            sb.append("_None._\n");
        }
        for (GameSeat seat : active) {
            sb.append(activeGameBlock(seat.game(), seat.player(), targetId, showModes));
        }
        return sb.toString();
    }

    private static String buildEndedSection(List<GameSeat> seats, String targetId) {
        List<GameSeat> ended =
                seats.stream().filter(seat -> seat.game().isHasEnded()).toList();
        if (ended.isEmpty()) {
            return "";
        }
        StringBuilder sb =
                new StringBuilder("\n### Ended (").append(ended.size()).append(")\n");
        for (GameSeat seat : ended) {
            sb.append(endedGameLine(seat.game(), seat.player(), targetId)).append('\n');
        }
        return sb.toString();
    }

    static boolean isFogGame(Game game) {
        return game.isFowMode() || game.getName().startsWith("fow");
    }

    static String activeGameBlock(Game game, Player player, String targetId, boolean showModes) {
        if (isFogGame(game)) {
            return fogGameTitle(game) + " · R" + game.getRound() + " · day " + gameAgeDays(game) + "\n";
        }
        StringBuilder sb = new StringBuilder(gameTitle(game, player, targetId));
        appendChannelLink(sb, game);
        sb.append('\n')
                .append(DETAIL_PREFIX)
                .append("R")
                .append(game.getRound())
                .append(" · ")
                .append(player.getTotalVictoryPoints())
                .append('/')
                .append(game.getVp())
                .append(" VP · day ")
                .append(gameAgeDays(game))
                .append(" · ")
                .append(averageTurn(player));
        for (String flag : activeFlags(game, player)) {
            sb.append(" · ").append(flag);
        }
        sb.append('\n');
        if (showModes) {
            sb.append(DETAIL_PREFIX)
                    .append("Modes: ")
                    .append(game.getGameModesText())
                    .append('\n');
        }
        return sb.toString();
    }

    static String endedGameLine(Game game, Player player, String targetId) {
        long daysPlayed = Duration.ofMillis(game.getEndedDate() - game.getCreationDateTime())
                .toDays();
        String outcome = game.hasWinner() ? "completed" : "abandoned";
        String duration = daysPlayed + (daysPlayed == 1 ? " day" : " days");
        if (isFogGame(game)) {
            return fogGameTitle(game) + " · " + duration + " · " + outcome;
        }
        StringBuilder sb = new StringBuilder(gameTitle(game, player, targetId));
        if (game.getWinners().contains(player)) {
            sb.append(" 👑 winner");
        }
        if (player.isEliminated()) {
            sb.append(" · eliminated");
        }
        return sb.append(" · ").append(duration).append(" · ").append(outcome).toString();
    }

    private static String fogGameTitle(Game game) {
        StringBuilder sb = new StringBuilder("🌫️ **").append(game.getName()).append("**");
        appendChannelLink(sb, game);
        return sb.toString();
    }

    private static void appendChannelLink(StringBuilder sb, Game game) {
        if (game.getActionsChannel() != null) {
            sb.append(' ').append(game.getActionsChannel().getAsMention());
        }
    }

    private static String gameTitle(Game game, Player player, String targetId) {
        StringBuilder sb = new StringBuilder("**").append(game.getName()).append("** ");
        if (player.getColor() != null && !"null".equals(player.getColor())) {
            sb.append(ColorEmojis.getColorEmoji(player.getColor()));
        }
        if (Mapper.isValidFaction(player.getFaction())) {
            sb.append(player.getFactionEmoji());
        }
        if (!player.getUserID().equals(targetId)) {
            sb.append(" · team seat");
        }
        return sb.toString();
    }

    private static List<String> activeFlags(Game game, Player player) {
        List<String> flags = new ArrayList<>();
        if (player.getUserID().equals(game.getActivePlayerID()) && game.getLastActivePlayerChange() != null) {
            long waitingMillis = System.currentTimeMillis()
                    - game.getLastActivePlayerChange().getTime();
            flags.add("⏳ **on turn " + formatDuration(waitingMillis) + "**");
        }
        long pendingSecondaries = game.getPlayedSCs().stream()
                .filter(sc -> !player.hasFollowedSC(sc) && !player.getSCs().contains(sc))
                .count();
        if (pendingSecondaries > 0) {
            flags.add(pendingSecondaries + " secondaries pending");
        }
        if (player.isEliminated()) {
            flags.add("**eliminated**");
        }
        if (player.isNpc()) {
            flags.add("NPC");
        }
        return flags;
    }

    private static long gameAgeDays(Game game) {
        return Duration.ofMillis(System.currentTimeMillis() - game.getCreationDateTime())
                .toDays();
    }

    private static String averageTurn(Player player) {
        if (player.getNumberOfTurns() == 0) {
            return "no turns yet";
        }
        return formatDuration(player.getTotalTurnTime() / player.getNumberOfTurns()) + "/turn ("
                + player.getNumberOfTurns() + ")";
    }

    private static String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(Math.max(millis, 0));
        if (duration.toDays() > 0) {
            return duration.toDays() + "d " + duration.toHoursPart() + "h";
        }
        return duration.toHours() + "h " + duration.toMinutesPart() + "m";
    }

    private static String buildReplacedGames(List<String> replacedGameNames) {
        if (replacedGameNames.isEmpty()) {
            return "";
        }
        List<String> names = replacedGameNames.stream()
                .map(PlayerGamesReportService::replacedGameLabel)
                .toList();
        return "\n### Replaced in (" + names.size() + ")\n" + String.join(", ", names) + "\n";
    }

    private static String replacedGameLabel(String gameName) {
        ManagedGame managedGame = GameManager.getManagedGame(gameName);
        boolean fog = gameName.startsWith("fow") || managedGame != null && managedGame.isFowMode();
        return (fog ? "🌫️ " : "") + "`" + gameName + "`";
    }
}
