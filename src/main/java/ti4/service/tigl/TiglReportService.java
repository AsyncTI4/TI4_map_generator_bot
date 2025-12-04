package ti4.service.tigl;

import static ti4.helpers.Constants.TIGL_FRACTURED_TAG;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;
import ti4.website.UltimateStatisticsWebsiteHelper;

@UtilityClass
public class TiglReportService {

    private static final DateTimeFormatter CREATION_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    public static void handleTiglReporting(Game game, GenericInteractionCreateEvent event) {
        if (!game.isCompetitiveTIGLGame() || game.getWinner().isEmpty()) {
            return;
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), getTIGLFormattedGameEndText(game, event));

        if (!game.isReplacementMade()) {
            UltimateStatisticsWebsiteHelper.sendTiglGameReport(buildTiglReport(game), event.getMessageChannel());
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "This game had a replacement. Please report the results manually: "
                            + "https://www.ti4ultimate.com/community/tigl/report-game");
        }
    }

    private static String getTIGLFormattedGameEndText(Game game, GenericInteractionCreateEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(MiscEmojis.TIGL).append("TIGL\n\n");
        sb.append("This was a TIGL game! 👑")
                .append(game.getWinner().get().getPing())
                .append(" is the winner!\n");
        sb.append("```\nMatch End Date: ")
                .append(Helper.getDateRepresentationTIGL(game.getEndedDate()))
                .append("\n");
        sb.append("Players:").append("\n");
        int index = 1;
        for (Player player : game.getRealPlayers()) {
            int playerVP = player.getTotalVictoryPoints();
            Optional<User> user = Optional.ofNullable(event.getJDA().getUserById(player.getUserID()));
            sb.append("  ").append(index).append(". ");
            sb.append(player.getFaction()).append(" - ");
            if (user.isPresent()) {
                sb.append(user.get().getName());
            } else {
                sb.append(player.getUserName());
            }
            sb.append(" - ").append(playerVP).append(" VP\n");
            index++;
        }

        sb.append("\n");
        sb.append("Platform: Async\n");
        sb.append("Additional Notes: Async Game '").append(game.getName());
        if (!StringUtils.isBlank(game.getCustomName())) sb.append("   ").append(game.getCustomName());
        sb.append("'\n```");

        return sb.toString();
    }

    private static TiglGameReport buildTiglReport(Game game) {
        var report = new TiglGameReport();
        report.setGameId(game.getID());
        report.setScore(game.getVp());
        report.setRound(game.getRound());
        report.setPlayerCount(resolvePlayerCount(game));
        report.setSource("Async");
        report.setStartTimestamp(determineStartTimestamp(game));
        report.setEndTimestamp(determineEndTimestamp(game));
        report.setLeague(determineLeague(game));
        report.setEvents(getEnabledGalacticEvents(game));

        List<Player> winners = Optional.ofNullable(game.getWinners()).orElse(List.of());
        var tiglPlayerResults = game.getRealPlayers().stream()
                .map(player -> {
                    var tiglPlayerResult = new TiglPlayerResult();
                    tiglPlayerResult.setScore(player.getTotalVictoryPoints());
                    if (player.getFactionModel() != null) {
                        tiglPlayerResult.setFaction(player.getFactionModel().getFactionName());
                    } else {
                        tiglPlayerResult.setFaction(player.getFaction());
                    }
                    tiglPlayerResult.setDiscordId(parseDiscordId(player.getUserID()));
                    tiglPlayerResult.setDiscordTag(resolveDiscordTag(player));
                    tiglPlayerResult.setWinner(winners.contains(player));
                    return tiglPlayerResult;
                })
                .toList();

        report.setPlayerResults(tiglPlayerResults);
        return report;
    }

    private static int resolvePlayerCount(Game game) {
        return game.getRealAndEliminatedPlayers().size();
    }

    private static long determineStartTimestamp(Game game) {
        if (game.getStartedDate() > 0) {
            return game.getStartedDate();
        }
        return parseCreationDate(game.getCreationDate());
    }

    private static long determineEndTimestamp(Game game) {
        return game.getEndedDate();
    }

    private static long parseCreationDate(String creationDate) {
        if (StringUtils.isBlank(creationDate)) {
            return 0L;
        }
        try {
            LocalDate date = LocalDate.parse(creationDate, CREATION_DATE_FORMATTER);
            return date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return 0L;
        }
    }

    private static Long parseDiscordId(String userId) {
        if (StringUtils.isBlank(userId)) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String resolveDiscordTag(Player player) {
        User user = player.getUser();
        if (user != null) {
            return user.getEffectiveName();
        }
        return player.getUserName();
    }

    private static String determineLeague(Game game) {
        List<String> tags = Optional.ofNullable(game.getTags()).orElse(List.of());
        boolean hasFracturedTag = tags.stream().anyMatch(TIGL_FRACTURED_TAG::equals);
        return hasFracturedTag ? "Fractured" : "ThundersEdge";
    }

    private static List<String> getEnabledGalacticEvents(Game game) {
        return Stream.of(
                        Map.entry("Minor Factions", (Supplier<Boolean>) game::isMinorFactionsMode),
                        Map.entry("Hidden Agenda", (Supplier<Boolean>) game::isHiddenAgendaMode),
                        Map.entry("Age of Exploration", (Supplier<Boolean>) game::isAgeOfExplorationMode),
                        Map.entry("Total War", (Supplier<Boolean>) game::isTotalWarMode),
                        Map.entry("Dangerous Wilds", (Supplier<Boolean>) game::isDangerousWildsMode),
                        Map.entry("Age of Fighters", (Supplier<Boolean>) game::isAgeOfFightersMode),
                        Map.entry("Mercenaries for Hire", (Supplier<Boolean>) game::isMercenariesForHireMode),
                        Map.entry("Zealous Orthodoxy", (Supplier<Boolean>) game::isZealousOrthodoxyMode),
                        Map.entry("Cultural Exchange Program", (Supplier<Boolean>) game::isCulturalExchangeProgramMode),
                        Map.entry("Rapid Mobilization", (Supplier<Boolean>) game::isRapidMobilizationMode),
                        Map.entry("Cosmic Phenomenae", (Supplier<Boolean>) game::isCosmicPhenomenaeMode),
                        Map.entry("Monument to the Ages", (Supplier<Boolean>) game::isMonumentToTheAgesMode),
                        Map.entry("Weird Wormholes", (Supplier<Boolean>) game::isWeirdWormholesMode),
                        Map.entry("Wild, Wild Galaxy", (Supplier<Boolean>) game::isWildWildGalaxyMode),
                        Map.entry("Call of the Void", (Supplier<Boolean>) game::isCallOfTheVoidMode),
                        Map.entry("Conventions of War Abandoned", (Supplier<Boolean>)
                                game::isConventionsOfWarAbandonedMode),
                        Map.entry("Advent of the Warsun", (Supplier<Boolean>) game::isAdventOfTheWarsunMode),
                        Map.entry("Civilized Society", (Supplier<Boolean>) game::isCivilizedSocietyMode),
                        Map.entry("Stellar Atomics", (Supplier<Boolean>) game::isStellarAtomicsMode),
                        Map.entry("Age of Commerce", (Supplier<Boolean>) game::isAgeOfCommerceMode))
                .filter(entry -> entry.getValue().get())
                .map(Map.Entry::getKey)
                .toList();
    }
}
