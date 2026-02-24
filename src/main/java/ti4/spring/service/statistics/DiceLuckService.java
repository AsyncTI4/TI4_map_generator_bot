package ti4.spring.service.statistics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
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
    public void getDiceLuck(SlashCommandInteractionEvent event) {
        boolean ignoreEndedGames = event.getOption(Constants.IGNORE_ENDED_GAMES, false, OptionMapping::getAsBoolean);
        boolean sortOrderAscending = event.getOption("ascending", true, OptionMapping::getAsBoolean);
        int topLimit = event.getOption(Constants.TOP_LIMIT, DEFAULT_PLAYER_LIMIT, OptionMapping::getAsInt);
        int minimumExpectedHits = event.getOption(
                Constants.MINIMUM_NUMBER_OF_EXPECTED_HITS, DEFAULT_MINIMUM_EXPECTED_HITS, OptionMapping::getAsInt);

        List<PlayerEntity> players = ignoreEndedGames
                ? playerEntityRepository.findAllWithUsersByActiveGame()
                : playerEntityRepository.findAllWithUsers();

        Map<UserEntity, DiceLuckAccumulator> userIdsToDiceLuckAccumulators = new HashMap<>();
        for (PlayerEntity player : players) {
            userIdsToDiceLuckAccumulators
                    .computeIfAbsent(player.getUser(), user -> new DiceLuckAccumulator(user.getName()))
                    .addGame(player.getExpectedHits(), player.getActualHits());
        }

        List<DiceLuckAccumulator> sortedResults = userIdsToDiceLuckAccumulators.values().stream()
                .filter(s -> s.expectedHits > minimumExpectedHits && s.actualHits > 0)
                .sorted((a, b) -> sortOrderAscending
                        ? Double.compare(a.getDiceLuck(), b.getDiceLuck())
                        : Double.compare(b.getDiceLuck(), a.getDiceLuck()))
                .limit(topLimit)
                .toList();

        MessageHelper.sendMessageToThread(event.getChannel(), "Dice Luck Record", toResultString(sortedResults));
    }

    @Transactional(readOnly = true)
    public String getDiceLuck(List<String> userIds) {
        List<PlayerEntity> players = playerEntityRepository.findAllWithUsersByUserIdIn(userIds);

        Map<String, DiceLuckAccumulator> userIdsToDiceLuckAccumulators = new HashMap<>();
        for (PlayerEntity player : players) {
            UserEntity user = player.getUser();
            userIdsToDiceLuckAccumulators
                    .computeIfAbsent(user.getId(), key -> new DiceLuckAccumulator(user.getName()))
                    .addGame(player.getExpectedHits(), player.getActualHits());
        }

        StringBuilder sb = new StringBuilder("## __**Dice Luck**__\n");
        int index = 1;
        for (DiceLuckAccumulator diceLuckAccumulator : userIdsToDiceLuckAccumulators.values()) {
            if (diceLuckAccumulator == null
                    || diceLuckAccumulator.expectedHits == 0
                    || diceLuckAccumulator.actualHits == 0) {
                continue;
            }
            sb.append("`").append(Helper.leftpad(String.valueOf(index), 3)).append(". ");
            sb.append(String.format("%.2f", diceLuckAccumulator.getDiceLuck()));
            sb.append("` ").append(diceLuckAccumulator.username);
            sb.append("   [")
                    .append(diceLuckAccumulator.actualHits)
                    .append("/")
                    .append(String.format("%.1f", diceLuckAccumulator.expectedHits))
                    .append(" actual/expected]\n");
            index++;
        }
        return sb.toString();
    }

    private String toResultString(List<DiceLuckAccumulator> sortedResults) {
        StringBuilder sb = new StringBuilder("## __**Dice Luck**__\n");
        int index = 1;
        for (var stats : sortedResults) {
            sb.append("`").append(Helper.leftpad(String.valueOf(index), 3)).append(". ");
            index++;
            sb.append(String.format("%.2f", stats.getDiceLuck()));
            sb.append("` ")
                    .append(stats.username)
                    .append("   [")
                    .append(stats.actualHits)
                    .append("/")
                    .append(String.format("%.1f", stats.expectedHits))
                    .append(" actual/expected]\n");
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

        void addGame(double gameExpectedHits, int gameActualHits) {
            expectedHits += gameExpectedHits;
            actualHits += gameActualHits;
        }

        double getDiceLuck() {
            return expectedHits == 0 ? 0 : actualHits / expectedHits;
        }
    }
}
