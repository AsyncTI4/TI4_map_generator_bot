package ti4.spring.service.statistics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;
import ti4.spring.persistence.PlayerEntity;
import ti4.spring.persistence.PlayerEntityRepository;
import ti4.spring.persistence.UserEntity;

@Service
@RequiredArgsConstructor
public class DiceLuckService {

    private static final int DEFAULT_PLAYER_LIMIT = 50;
    private static final int DEFAULT_MINIMUM_EXPECTED_HITS = 50;

    private final PlayerEntityRepository playerEntityRepository;

    @Transactional(readOnly = true)
    public void getActualVersusExpectedHits(SlashCommandInteractionEvent event) {
        boolean ignoreEndedGames = event.getOption(Constants.IGNORE_ENDED_GAMES, false, OptionMapping::getAsBoolean);
        boolean sortOrderAscending = event.getOption("ascending", true, OptionMapping::getAsBoolean);
        int topLimit = event.getOption(Constants.TOP_LIMIT, DEFAULT_PLAYER_LIMIT, OptionMapping::getAsInt);
        int minimumExpectedHits = event.getOption(
                Constants.MINIMUM_NUMBER_OF_EXPECTED_HITS, DEFAULT_MINIMUM_EXPECTED_HITS, OptionMapping::getAsInt);

        List<PlayerEntity> players = ignoreEndedGames
                ? playerEntityRepository.findAllWithUsersByActiveGame()
                : playerEntityRepository.findAllWithUsers();

        Map<UserEntity, DiceLuckAccumulator> usersToDiceLuckAccumulators = getUsersToDiceLuckAccumulator(players);

        List<DiceLuckAccumulator> sortedResults = usersToDiceLuckAccumulators.values().stream()
                .filter(s -> s.expectedHits >= minimumExpectedHits && s.actualHits > 0)
                .sorted((a, b) -> sortOrderAscending
                        ? Double.compare(a.getActualHitsOutOfExpected(), b.getActualHitsOutOfExpected())
                        : Double.compare(b.getActualHitsOutOfExpected(), a.getActualHitsOutOfExpected()))
                .limit(topLimit)
                .toList();

        MessageHelper.sendMessageToThread(event.getChannel(), "Dice Luck Record", toResultString(sortedResults));
    }

    @NotNull
    private static Map<UserEntity, DiceLuckAccumulator> getUsersToDiceLuckAccumulator(List<PlayerEntity> players) {
        Map<UserEntity, DiceLuckAccumulator> userIdsToDiceLuckAccumulators = new HashMap<>();
        for (PlayerEntity player : players) {
            if (player.getExpectedHits() == 0) continue;
            // ignore anomalies, like nearly infinite battles
            if (2 * player.getExpectedHits() >= player.getTotalNumberOfTurns()) continue;
            userIdsToDiceLuckAccumulators
                    .computeIfAbsent(player.getUser(), user -> new DiceLuckAccumulator(user.getName()))
                    .addGame(player.getExpectedHits(), player.getActualHits());
        }
        return userIdsToDiceLuckAccumulators;
    }

    @Transactional(readOnly = true)
    public String getDiceLuck(List<String> userIds) {
        List<PlayerEntity> players = playerEntityRepository.findAllWithUsersByUserIdIn(userIds);

        Map<UserEntity, DiceLuckAccumulator> userIdsToDiceLuckAccumulators = getUsersToDiceLuckAccumulator(players);

        return toResultString(userIdsToDiceLuckAccumulators.values());
    }

    private String toResultString(Iterable<DiceLuckAccumulator> diceLuckAccumulators) {
        StringBuilder sb = new StringBuilder("## __**Dice Luck**__\n");
        int index = 1;
        for (var diceLuckAccumulator : diceLuckAccumulators) {
            sb.append("`")
                    .append(Helper.leftpad(String.valueOf(index), 3))
                    .append(". ")
                    .append(String.format("%.2f", diceLuckAccumulator.getActualHitsOutOfExpected()))
                    .append("` ")
                    .append(diceLuckAccumulator.username)
                    .append("   [")
                    .append(diceLuckAccumulator.actualHits)
                    .append("/")
                    .append(String.format("%.1f", diceLuckAccumulator.expectedHits))
                    .append(" actual/expected hits]\n");
            index++;
        }
        return sb.toString();
    }

    public static DiceLuckService getBean() {
        return SpringContext.getBean(DiceLuckService.class);
    }

    private static class DiceLuckAccumulator {
        String username;
        double expectedHits;
        int actualHits;

        DiceLuckAccumulator(String username) {
            this.username = username;
        }

        void addGame(double expectedHits, int actualHits) {
            this.expectedHits += expectedHits;
            this.actualHits += actualHits;
        }

        double getActualHitsOutOfExpected() {
            return expectedHits == 0 ? 0 : actualHits / expectedHits;
        }
    }
}
