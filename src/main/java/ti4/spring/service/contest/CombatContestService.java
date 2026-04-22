package ti4.spring.service.contest;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.image.TileGenerator;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatStatsService;
import ti4.service.combat.CombatUnitSelectionHelper;
import ti4.service.emoji.ColorEmojis;

@Service
@RequiredArgsConstructor
public class CombatContestService {

    public static final String LAZAX_MINIGAME_SUBSCRIPTION_MARKER = "-# Lazax Minigame Subscription";
    public static final String LAZAX_MINIGAME_ROLE_NAME = "Lazax Minigame";
    private static final String CONTEST_CHANNEL_NAME = "lazax-war-archives";
    private static final String THREAD_SUMMARY_HEADER = "## Combat Units\n";
    private static final Set<String> COMBAT_SUMMARY_TECH_ALIASES = Set.of("da", "asc", "x89", "x89c4");
    private static final Set<String> COMBAT_SUMMARY_RELIC_ALIASES = Set.of(
            "metalivoidarmaments", "metalivoidshielding", "lightrailordnance", "baldrick_crownofthalnos", "pi_thalnos");
    private static final double ZERO_EPSILON = 0.0001;
    private static final String SUBSCRIBE_EMOJI = "\uD83D\uDFE2";
    private static final String UNSUBSCRIBE_EMOJI = "\uD83D\uDD34";
    private static final Set<CombatContestStatus> ACTIVE_STATUSES = Set.of(CombatContestStatus.POSTED);
    private static final Comparator<TechnologyModel> TECH_COMPARATOR = Comparator.comparingInt(
                    CombatContestService::getTechTypeOrder)
            .thenComparingInt(tech -> tech.getRequirements().orElse("").length())
            .thenComparing(TechnologyModel::getName, String.CASE_INSENSITIVE_ORDER);

    private final CombatContestRepository repository;
    private final CombatContestPredictionRepository predictionRepository;
    private final CombatContestSampleRepository sampleRepository;
    private final CombatContestSelectionService selectionService;

    // ==================== Public Entry Points ====================

    public void onSpaceCombatStarted(Game game, Player attacker, Player defender, Tile tile) {
        try {
            if (!isEligibleGame(game) || !isEligibleCombat(game, attacker, defender, tile)) return;

            SpaceCombatSnapshot snapshot = buildSnapshot(game, attacker, defender, tile);
            if (snapshot == null) return;

            CombatContestSelectionService.Evaluation evaluation = selectionService.evaluate(
                    snapshot.attackerStrength(),
                    snapshot.defenderStrength(),
                    snapshot.attackerHp(),
                    snapshot.defenderHp(),
                    snapshot.attackerExpectedHits(),
                    snapshot.defenderExpectedHits());
            CombatContestSelectionService.Settings settings = selectionService.getCurrentSettings();
            boolean eligibleForContest =
                    evaluation.eligibleUnderCurrentThresholds() && !hasExcludedFlagship(attacker, defender);
            CombatContestSampleEntity sample = sampleRepository.save(
                    buildSampleEntity(game, tile, snapshot, evaluation, settings, eligibleForContest));

            if (repository
                    .findFirstByGameNameAndTilePositionAndCombatTypeAndStatusInOrderByPostedAtDesc(
                            game.getName(), tile.getPosition(), CombatContestType.SPACE, ACTIVE_STATUSES)
                    .isPresent()) {
                return;
            }
            if (!eligibleForContest || !cooldownElapsed()) return;

            TextChannel contestChannel = getContestChannel();
            if (contestChannel == null) return;

            CombatContestEntity contest = repository.save(buildContestEntity(game, attacker, defender, tile, snapshot));
            sample.setContestPosted(true);
            sampleRepository.save(sample);
            postContestMessage(game, attacker, defender, tile, contestChannel, contest, snapshot);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Combat contest nomination failed.", e);
        }
    }

    public void onButtonInteractionSettled(Game game, Player player, ButtonInteractionEvent event) {
        try {
            List<CombatContestEntity> activeContests =
                    repository.findByGameNameAndStatusIn(game.getName(), ACTIVE_STATUSES);
            if (activeContests.isEmpty()) return;
            for (CombatContestEntity contest : activeContests) {
                if (evaluateContestCompletion(game, event, contest)) continue;
                trackHitAssignments(game, player, event, contest);
            }
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Combat contest completion check failed.", e);
        }
    }

    public void mirrorCombatRoll(Game game, Player player, Player opponent, Tile tile, String message) {
        try {
            CombatContestEntity contest = repository
                    .findFirstByGameNameAndTilePositionAndCombatTypeAndStatusInOrderByPostedAtDesc(
                            game.getName(), tile.getPosition(), CombatContestType.SPACE, ACTIVE_STATUSES)
                    .orElse(null);
            if (contest == null || !matchesParticipants(contest, player, opponent)) return;
            if (!hasRecordedRolls(contest)) {
                contest.setDiceRolled(true);
                repository.save(contest);
            }

            MessageChannel threadOrChannel = getContestThreadOrChannel(contest);
            if (threadOrChannel == null) return;
            MessageHelper.splitAndSentWithAction("## Roll Update\n" + message, threadOrChannel, null);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Combat contest roll mirror failed.", e);
        }
    }

    public void mirrorCombatEvent(Game game, Player player, String header, String name, MessageEmbed embed) {
        try {
            List<CombatContestEntity> activeContests =
                    repository.findByGameNameAndStatusIn(game.getName(), ACTIVE_STATUSES);
            if (activeContests.isEmpty()) return;

            String message = "## " + header + "\n" + player.getRepresentation() + " " + name;
            for (CombatContestEntity contest : activeContests) {
                if (!matchesParticipant(contest, player)) continue;
                MessageChannel threadOrChannel = getContestThreadOrChannel(contest);
                if (threadOrChannel == null) continue;
                MessageHelper.sendMessageToChannelWithEmbed(threadOrChannel, message, embed);
            }
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Combat contest " + header.toLowerCase() + " relay failed.", e);
        }
    }

    public boolean postLeaderboard() {
        String message = buildLeaderboardMessage();
        if (message == null) return false;

        TextChannel contestChannel = getContestChannel();
        if (contestChannel == null) return false;
        MessageHelper.sendMessageToChannel(contestChannel, message);
        return true;
    }

    // ==================== Contest Creation & Eligibility ====================

    private boolean isEligibleGame(Game game) {
        return game != null
                && !game.isHasEnded()
                && !game.isAllianceMode()
                && !game.isFowMode()
                && !game.isAbsolMode()
                && !game.isFrankenGame()
                && !game.isTwilightsFallMode();
    }

    private boolean cooldownElapsed() {
        Duration cooldown =
                Duration.ofMinutes(selectionService.getCurrentSettings().cooldownMinutes());
        return repository
                .findLatestContestCountingTowardCooldown()
                .map(contest -> Duration.between(contest.getPostedAt(), LocalDateTime.now())
                                .compareTo(cooldown)
                        >= 0)
                .orElse(true);
    }

    private boolean isEligibleCombat(Game game, Player attacker, Player defender, Tile tile) {
        if (attacker == null || defender == null || tile == null || attacker == defender) return false;
        if (attacker.isDummy() || defender.isDummy() || !attacker.isRealPlayer() || !defender.isRealPlayer())
            return false;
        List<Player> shipPlayers = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile).stream()
                .filter(Player::isRealPlayer)
                .filter(player -> !player.isDummy())
                .toList();
        return shipPlayers.size() == 2;
    }

    private CombatContestEntity buildContestEntity(
            Game game, Player attacker, Player defender, Tile tile, SpaceCombatSnapshot snapshot) {
        CombatContestEntity contest = new CombatContestEntity();
        contest.setStatus(CombatContestStatus.POSTED);
        contest.setCombatType(CombatContestType.SPACE);
        contest.setGameName(game.getName());
        contest.setTilePosition(tile.getPosition());
        contest.setTileRepresentation(tile.getRepresentationForButtons());
        contest.setAttackerFaction(attacker.getFaction());
        contest.setDefenderFaction(defender.getFaction());
        contest.setAttackerColor(attacker.getColor());
        contest.setDefenderColor(defender.getColor());
        contest.setPostedAt(LocalDateTime.now());
        contest.setInitialSummaryText(snapshot.parentPostText());
        contest.setActivePlayerSummary(snapshot.activePlayerSummary());
        contest.setInitialStrengthAttacker(snapshot.attackerStrength());
        contest.setInitialStrengthDefender(snapshot.defenderStrength());
        contest.setInitialHpAttacker(snapshot.attackerHp());
        contest.setInitialHpDefender(snapshot.defenderHp());
        contest.setDiceRolled(false);
        return contest;
    }

    private CombatContestSampleEntity buildSampleEntity(
            Game game,
            Tile tile,
            SpaceCombatSnapshot snapshot,
            CombatContestSelectionService.Evaluation evaluation,
            CombatContestSelectionService.Settings settings,
            boolean eligibleForContest) {
        CombatContestSampleEntity sample = new CombatContestSampleEntity();
        sample.setStartedAt(LocalDateTime.now());
        sample.setGameName(game.getName());
        sample.setTilePosition(tile.getPosition());
        sample.setAttackerStrength(snapshot.attackerStrength());
        sample.setDefenderStrength(snapshot.defenderStrength());
        sample.setAttackerHp(snapshot.attackerHp());
        sample.setDefenderHp(snapshot.defenderHp());
        sample.setAttackerExpectedHits(snapshot.attackerExpectedHits());
        sample.setDefenderExpectedHits(snapshot.defenderExpectedHits());
        sample.setWeakerStrength(evaluation.weakerStrength());
        sample.setStrongerStrength(evaluation.strongerStrength());
        sample.setWeakerHp(evaluation.weakerHp());
        sample.setStrongerHp(evaluation.strongerHp());
        sample.setFairnessRatio(evaluation.fairnessRatio());
        sample.setContestScore(evaluation.contestScore());
        sample.setScoreCutoffAtStart(settings.combatSizeCutoff());
        sample.setSelectionModeAtStart(settings.selectionMode());
        sample.setEligibleUnderCurrentThresholds(eligibleForContest);
        sample.setContestPosted(false);
        return sample;
    }

    private boolean hasExcludedFlagship(Player attacker, Player defender) {
        return attacker.hasUnit("yin_flagship") || defender.hasUnit("yin_flagship");
    }

    // ==================== Combat Snapshot & Strength ====================

    private SpaceCombatSnapshot buildSnapshot(Game game, Player attacker, Player defender, Tile tile) {
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        if (space == null) return null;

        FleetStrength attackerStrength = calculateFleetStrength(game, attacker, defender, tile, space);
        FleetStrength defenderStrength = calculateFleetStrength(game, defender, attacker, tile, space);

        double stronger = Math.max(attackerStrength.hp(), defenderStrength.hp());
        double weaker = Math.min(attackerStrength.hp(), defenderStrength.hp());
        double ratio = stronger == 0 ? 0 : weaker / stronger;
        String summary = extractSpaceOnlySummary(ButtonHelper.getCombatTileSummaryMessage(
                game, tile, attacker, null, "space", Constants.SPACE, List.of(attacker, defender)));

        String activePlayerSummary = formatActivePlayerSummary(game);
        String openerText = formatContestPostHeader(game, tile, attacker, defender, activePlayerSummary);
        String threadSummaryText = null;
        String parentPostText = openerText + "\n\n" + summary;
        if (parentPostText.length() > Message.MAX_CONTENT_LENGTH) {
            parentPostText = openerText;
            threadSummaryText = THREAD_SUMMARY_HEADER + summary;
        }
        return new SpaceCombatSnapshot(
                parentPostText,
                threadSummaryText,
                activePlayerSummary,
                attackerStrength.value(),
                defenderStrength.value(),
                attackerStrength.hp(),
                defenderStrength.hp(),
                attackerStrength.expectedHits(),
                defenderStrength.expectedHits(),
                ratio);
    }

    private FleetStrength calculateFleetStrength(
            Game game, Player player, Player opponent, Tile tile, UnitHolder space) {
        double total = 0;
        double hp = 0;
        double expectedHits = 0;
        Map<UnitModel, Integer> unitsInCombat = CombatUnitSelectionHelper.collectCombatRoundUnits(tile, space, player);
        Map<String, Integer> damagedCountsByAsyncId = getDamagedCountsByAsyncId(tile, player);
        for (Map.Entry<UnitModel, Integer> entry : unitsInCombat.entrySet()) {
            UnitModel unitModel = entry.getKey();
            if (unitModel == null) continue;

            int totalUnits = entry.getValue();
            int damagedUnits = Math.min(totalUnits, damagedCountsByAsyncId.getOrDefault(unitModel.getAsyncId(), 0));
            int undamagedUnits = Math.max(0, totalUnits - damagedUnits);

            total += unitModel.getCost() * totalUnits;
            hp += totalUnits;
            CombatStatsService.CombatRoundProfile combatProfile =
                    CombatStatsService.getCombatRoundProfile(true, unitModel, player, tile, opponent);
            expectedHits += computeExpectedHits(totalUnits * combatProfile.diceCount(), combatProfile.hitsOn());
            if (unitModel.getSustainDamage(player, space)) {
                hp += undamagedUnits;
                if (player.hasTech("nes")) {
                    hp += undamagedUnits;
                }
            }
        }
        return new FleetStrength(total, hp, expectedHits);
    }

    private Map<String, Integer> getDamagedCountsByAsyncId(Tile tile, Player player) {
        Map<String, Integer> damagedCountsByAsyncId = new HashMap<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : unitHolder.getUnitKeys()) {
                if (!unitKey.getColorID().equalsIgnoreCase(player.getColorID())) continue;
                int damagedUnits = unitHolder.getDamagedUnitCount(unitKey);
                if (damagedUnits <= 0) continue;
                damagedCountsByAsyncId.merge(unitKey.asyncID(), damagedUnits, Integer::sum);
            }
        }
        return damagedCountsByAsyncId;
    }

    private double computeExpectedHits(int totalDice, int hitsOn) {
        if (totalDice <= 0 || hitsOn <= 0) return 0;
        return totalDice * Math.max(0, 11 - hitsOn) / 10.0;
    }

    private String extractSpaceOnlySummary(String summary) {
        String[] lines = summary.split("\n");
        StringBuilder trimmed = new StringBuilder();
        boolean inSpaceSection = false;
        for (String line : lines) {
            if (trimmed.isEmpty()) {
                trimmed.append(line).append('\n');
                continue;
            }
            if (!inSpaceSection && "Space".equalsIgnoreCase(line.trim())) {
                inSpaceSection = true;
                trimmed.append(line).append('\n');
                continue;
            }
            if (!inSpaceSection) continue;
            trimmed.append(line).append('\n');
            if ("----------".equals(line.trim())) break;
        }
        return trimmed.toString().trim();
    }

    // ==================== Contest Resolution ====================

    private boolean evaluateContestCompletion(Game game, ButtonInteractionEvent event, CombatContestEntity contest) {
        Tile tile = game.getTileByPosition(contest.getTilePosition());
        if (tile == null) return false;

        List<Player> remainingShipPlayers = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile).stream()
                .filter(Player::isRealPlayer)
                .filter(player -> !player.isDummy())
                .toList();
        if (remainingShipPlayers.size() == 1) {
            if (!hasRecordedRolls(contest)) {
                cancelContest(game, contest, "The tracked space combat ended before any dice were rolled.");
                return true;
            }
            Player winner = remainingShipPlayers.getFirst();
            String loserFaction = winner.getFaction().equalsIgnoreCase(contest.getAttackerFaction())
                    ? contest.getDefenderFaction()
                    : contest.getAttackerFaction();
            resolveContest(game, event, contest, tile, winner, loserFaction);
            return true;
        }

        if (remainingShipPlayers.isEmpty()) {
            String reason = hasRecordedRolls(contest)
                    ? "The tracked space combat ended with no ships remaining."
                    : "The tracked space combat ended before any dice were rolled.";
            cancelContest(game, contest, reason);
            return true;
        }

        if (remainingShipPlayers.size() > 2) {
            cancelContest(game, contest, "The tracked space combat no longer has exactly two sides.");
            return true;
        }

        boolean containsBothParticipants = remainingShipPlayers.stream()
                .map(Player::getFaction)
                .allMatch(faction -> faction.equalsIgnoreCase(contest.getAttackerFaction())
                        || faction.equalsIgnoreCase(contest.getDefenderFaction()));
        if (!containsBothParticipants) {
            cancelContest(game, contest, "The tracked space combat drifted away from the original participants.");
            return true;
        }
        return false;
    }

    private void resolveContest(
            Game game,
            ButtonInteractionEvent event,
            CombatContestEntity contest,
            Tile tile,
            Player winner,
            String loserFaction) {
        capturePredictionsAtResolution(game, contest);
        closeContest(contest, CombatContestStatus.RESOLVED, winner.getFaction(), loserFaction);
        List<CombatContestPredictionEntity> predictions = awardPredictionPoints(contest);

        MessageChannel threadOrChannel = getContestThreadOrChannel(contest);
        String resultMessage = "## Contest Result\n"
                + winner.getFactionEmoji() + " " + winner.getUserName() + " won the space combat in "
                + tile.getRepresentationForButtons() + ".";
        if (threadOrChannel != null) {
            try (FileUpload fileUpload =
                    new TileGenerator(game, event, null, 0, tile.getPosition(), winner).createFileUpload()) {
                MessageHelper.sendMessageWithFile(threadOrChannel, fileUpload, resultMessage, false, false);
            } catch (IOException e) {
                BotLogger.error(new LogOrigin(game), "Failed to create combat contest result image.", e);
                MessageHelper.sendMessageToChannel(threadOrChannel, resultMessage);
            }
            postPredictionPointsSummary(threadOrChannel, predictions, () -> {
                postParticipantFollowup(game, contest, threadOrChannel);
                postSubscriptionPrompt(threadOrChannel);
            });
        }
        refreshParentMessageSummary(
                game,
                contest,
                buildResultStamp(predictions, tile, winner, contest),
                buildLossSummary(game, tile, contest, winner, loserFaction));
        maybePostLeaderboardAfterResolvedContest();
    }

    private void cancelContest(Game game, CombatContestEntity contest, String reason) {
        closeContest(contest, CombatContestStatus.CANCELLED, null, null);

        MessageChannel threadOrChannel = getContestThreadOrChannel(contest);
        if (threadOrChannel != null)
            MessageHelper.sendMessageToChannel(threadOrChannel, "## Contest Closed\n" + reason);
        refreshParentMessageSummary(game, contest, "Cancelled", reason);
    }

    private void closeContest(
            CombatContestEntity contest, CombatContestStatus status, String winnerFaction, String loserFaction) {
        contest.setStatus(status);
        contest.setResolvedAt(LocalDateTime.now());
        contest.setWinnerFaction(winnerFaction);
        contest.setLoserFaction(loserFaction);
        repository.save(contest);
    }

    private boolean hasRecordedRolls(CombatContestEntity contest) {
        return !Boolean.FALSE.equals(contest.getDiceRolled());
    }

    private void capturePredictionsAtResolution(Game game, CombatContestEntity contest) {
        if (!predictionRepository.findByContestId(contest.getId()).isEmpty()) return;
        TextChannel contestChannel = getContestPublicChannel(contest);
        if (contestChannel == null) return;

        try {
            Message message = contestChannel
                    .retrieveMessageById(contest.getPublicMessageId())
                    .complete();
            capturePredictions(game, message, contest);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Failed to capture combat contest predictions at resolution.", e);
        }
    }

    private void refreshParentMessageSummary(
            Game game, CombatContestEntity contest, String resultStamp, String detailStamp) {
        TextChannel contestChannel = getContestPublicChannel(contest);
        if (contestChannel == null) return;
        StringBuilder updated = new StringBuilder(contest.getInitialSummaryText());
        if (resultStamp != null) updated.append("\n-# Contest result: ").append(resultStamp);
        if (detailStamp != null) updated.append("\n-# Contest details: ").append(detailStamp);
        contestChannel
                .retrieveMessageById(contest.getPublicMessageId())
                .queue(
                        message -> message.editMessage(updated.toString()).queueAfter(100, TimeUnit.MILLISECONDS),
                        BotLogger::catchRestError);
    }

    private String buildResultStamp(
            List<CombatContestPredictionEntity> predictions, Tile tile, Player winner, CombatContestEntity contest) {
        long correctPredictions = predictions.stream()
                .filter(prediction -> prediction.getPredictedFaction().equalsIgnoreCase(winner.getFaction()))
                .count();
        return winner.getFactionEmoji() + " defeated `" + contest.getLoserFaction() + "` in "
                + tile.getRepresentationForButtons()
                + " (" + correctPredictions + "/" + predictions.size() + " called it)";
    }

    private String buildLossSummary(
            Game game, Tile tile, CombatContestEntity contest, Player winner, String loserFaction) {
        Player loser = game.getPlayerFromColorOrFaction(loserFaction);
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        if (space == null || loser == null) return null;

        double winnerRemaining =
                calculateFleetStrength(game, winner, loser, tile, space).value();
        double loserRemaining =
                calculateFleetStrength(game, loser, winner, tile, space).value();
        double winnerInitial = winner.getFaction().equalsIgnoreCase(contest.getAttackerFaction())
                ? contest.getInitialStrengthAttacker()
                : contest.getInitialStrengthDefender();
        double loserInitial = loser.getFaction().equalsIgnoreCase(contest.getAttackerFaction())
                ? contest.getInitialStrengthAttacker()
                : contest.getInitialStrengthDefender();
        return winner.getFactionEmoji() + " suffered " + describeLosses(winnerInitial, winnerRemaining) + "; "
                + loser.getFactionEmoji() + " suffered " + describeLosses(loserInitial, loserRemaining) + ".";
    }

    private String describeLosses(double initialStrength, double remainingStrength) {
        if (initialStrength <= 0) return "unknown losses";
        double lossRatio = Math.max(0, initialStrength - remainingStrength) / initialStrength;
        if (lossRatio <= ZERO_EPSILON) return "no losses";
        if (lossRatio < 0.2) return "light losses";
        if (lossRatio < 0.5) return "moderate losses";
        if (lossRatio < 0.85) return "heavy losses";
        return "catastrophic losses";
    }

    // ==================== Predictions ====================

    private void capturePredictions(Game game, Message message, CombatContestEntity contest) {
        Map<String, Set<String>> factionsByUser = new HashMap<>();
        Map<String, String> namesByUser = new HashMap<>();
        Map<String, String> factionToEmoji = Map.of(
                contest.getAttackerFaction(), getFactionEmoji(game, contest.getAttackerFaction()),
                contest.getDefenderFaction(), getFactionEmoji(game, contest.getDefenderFaction()));

        for (MessageReaction reaction : message.getReactions()) {
            String predictedFaction = null;
            for (Map.Entry<String, String> entry : factionToEmoji.entrySet()) {
                if (reaction.getEmoji().getFormatted().equals(entry.getValue())) {
                    predictedFaction = entry.getKey();
                    break;
                }
            }
            if (predictedFaction == null) continue;

            List<User> users = reaction.retrieveUsers().complete();
            for (User user : users) {
                if (user.isBot()) continue;
                factionsByUser
                        .computeIfAbsent(user.getId(), key -> new HashSet<>())
                        .add(predictedFaction);
                namesByUser.put(user.getId(), user.getName());
            }
        }

        List<CombatContestPredictionEntity> predictions = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : factionsByUser.entrySet()) {
            if (entry.getValue().size() != 1) continue;

            String predictedFaction = entry.getValue().iterator().next();
            CombatContestPredictionEntity prediction = new CombatContestPredictionEntity();
            prediction.setContestId(contest.getId());
            prediction.setDiscordUserId(entry.getKey());
            prediction.setDiscordUserName(namesByUser.getOrDefault(entry.getKey(), "Unknown User"));
            prediction.setPredictedFaction(predictedFaction);
            prediction.setLockedAt(LocalDateTime.now());
            predictions.add(prediction);
        }
        predictionRepository.saveAll(predictions);
    }

    private List<CombatContestPredictionEntity> awardPredictionPoints(CombatContestEntity contest) {
        List<CombatContestPredictionEntity> predictions = predictionRepository.findByContestId(contest.getId());
        if (predictions.isEmpty()) return predictions;

        int attackerPredictions = (int) predictions.stream()
                .filter(prediction -> prediction.getPredictedFaction().equalsIgnoreCase(contest.getAttackerFaction()))
                .count();
        int defenderPredictions = predictions.size() - attackerPredictions;
        int winnerPredictions = contest.getWinnerFaction().equalsIgnoreCase(contest.getAttackerFaction())
                ? attackerPredictions
                : defenderPredictions;
        int totalPredictions = attackerPredictions + defenderPredictions;
        for (CombatContestPredictionEntity prediction : predictions) {
            boolean correct = prediction.getPredictedFaction().equalsIgnoreCase(contest.getWinnerFaction());
            prediction.setCorrect(correct);
            prediction.setPointsAwarded(correct ? calculatePredictionPoints(winnerPredictions, totalPredictions) : 0);
        }
        predictionRepository.saveAll(predictions);
        return predictions;
    }

    private int calculatePredictionPoints(int winnerPredictions, int totalPredictions) {
        totalPredictions = Math.max(1, totalPredictions);
        double winnerShare = winnerPredictions / (double) totalPredictions;
        double scaledPoints = 4.0 / Math.max(winnerShare, ZERO_EPSILON);
        return (int) Math.round(Math.max(4.0, Math.min(100.0, scaledPoints)));
    }

    private void postPredictionPointsSummary(
            MessageChannel threadOrChannel, List<CombatContestPredictionEntity> predictions, Runnable afterPost) {
        List<CombatContestPredictionEntity> winningPredictions = predictions.stream()
                .filter(prediction -> safeInt(prediction.getPointsAwarded()) > 0)
                .toList();
        if (winningPredictions.isEmpty()) {
            afterPost.run();
            return;
        }

        Map<String, Integer> totalsByUser = predictionRepository
                .findPointTotalsByDiscordUserIdIn(winningPredictions.stream()
                        .map(CombatContestPredictionEntity::getDiscordUserId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(
                        CombatContestUserPointsRow::getDiscordUserId, row -> safeInt(row.getTotalPoints())));

        String message = buildLineSection(
                "## Prediction Points",
                winningPredictions.stream()
                        .sorted((left, right) -> {
                            int pointsComparison = Integer.compare(
                                    safeInt(right.getPointsAwarded()), safeInt(left.getPointsAwarded()));
                            if (pointsComparison != 0) return pointsComparison;
                            return left.getDiscordUserName().compareToIgnoreCase(right.getDiscordUserName());
                        })
                        .map(prediction -> {
                            int pointsAwarded = safeInt(prediction.getPointsAwarded());
                            int totalPoints = totalsByUser.getOrDefault(prediction.getDiscordUserId(), 0);
                            return "<@" + prediction.getDiscordUserId() + "> - " + totalPoints + " points (+"
                                    + pointsAwarded + ")";
                        }));

        MessageHelper.splitAndSentWithAction(message, threadOrChannel, postedMessage -> afterPost.run());
    }

    // ==================== Leaderboard ====================

    private void maybePostLeaderboardAfterResolvedContest() {
        List<CombatContestEntity> pendingBatch =
                repository.findTop5ByStatusAndResolvedAtIsNotNullAndLeaderboardPostedAtIsNullOrderByResolvedAtAsc(
                        CombatContestStatus.RESOLVED);
        if (pendingBatch.size() < 5) return;
        if (!postLeaderboard()) return;

        LocalDateTime postedAt = LocalDateTime.now();
        pendingBatch.forEach(contest -> contest.setLeaderboardPostedAt(postedAt));
        repository.saveAll(pendingBatch);
    }

    private String buildLeaderboardMessage() {
        List<CombatContestLeaderboardRow> topEntries = predictionRepository.findLeaderboardRows(PageRequest.of(0, 10));
        if (topEntries.isEmpty()) return null;

        final int[] rank = {1};
        return buildLineSection(
                "## Lazax War Archives Leaderboard", topEntries.stream().map(entry -> {
                    long predictions = entry.getPredictionCount() == null ? 0 : entry.getPredictionCount();
                    long correctPredictions = entry.getCorrectPredictions() == null ? 0 : entry.getCorrectPredictions();
                    int accuracy = predictions == 0 ? 0 : Math.round((100f * correctPredictions) / predictions);
                    return '`' + Integer.toString(rank[0]++) + ".` "
                            + getSafeLeaderboardName(entry.getDiscordUserName())
                            + " - **" + entry.getTotalPoints() + "** points (`" + correctPredictions + "/" + predictions
                            + "` correct, " + accuracy + "%)";
                }));
    }

    private String getSafeLeaderboardName(String userName) {
        if (userName == null || userName.isBlank()) return "Unknown User";
        return userName.replace("@", "@\u200B");
    }

    // ==================== Hit Tracking ====================

    private void trackHitAssignments(
            Game game, Player player, ButtonInteractionEvent event, CombatContestEntity contest) {
        if (player == null || !isSpaceCombatHitAssignment(game, player, event)) return;
        if (!contest.getTilePosition().equals(getTilePosition(event.getButton().getCustomId()))) return;
        if (!matchesParticipant(contest, player)) return;

        int round = getCurrentRound(game, contest);
        boolean changed = false;
        if (player.getFaction().equalsIgnoreCase(contest.getAttackerFaction())
                && !Objects.equals(contest.getAttackerHitAssignedRound(), round)) {
            contest.setAttackerHitAssignedRound(round);
            changed = true;
        }
        if (player.getFaction().equalsIgnoreCase(contest.getDefenderFaction())
                && !Objects.equals(contest.getDefenderHitAssignedRound(), round)) {
            contest.setDefenderHitAssignedRound(round);
            changed = true;
        }
        if (!changed) return;

        postAssignedHitsImage(game, player, event, contest, round);
        repository.save(contest);
    }

    private boolean isSpaceCombatHitAssignment(Game game, Player player, ButtonInteractionEvent event) {
        String buttonId = stripFactionChecker(event.getButton().getCustomId());
        if (buttonId.startsWith("autoAssignSpaceHits_") || buttonId.startsWith("autoAssignSpaceCannonOffenceHits_"))
            return true;
        if (!buttonId.startsWith("assignHits_") && !buttonId.startsWith("assignDamage_")) return false;

        String assignHitsType =
                game.getStoredValue(player.getFaction() + "latestAssignHits").toLowerCase();
        return "spacecombat".equals(assignHitsType) || "pds".equals(assignHitsType);
    }

    private int getCurrentRound(Game game, CombatContestEntity contest) {
        return Math.max(
                getCurrentRound(game, contest.getAttackerFaction(), contest.getTilePosition()),
                getCurrentRound(game, contest.getDefenderFaction(), contest.getTilePosition()));
    }

    private int getCurrentRound(Game game, String faction, String tilePosition) {
        String tracker = game.getStoredValue("combatRoundTracker" + faction + tilePosition + Constants.SPACE);
        return tracker.isBlank() ? 0 : Integer.parseInt(tracker);
    }

    private void postAssignedHitsImage(
            Game game, Player player, ButtonInteractionEvent event, CombatContestEntity contest, int round) {
        MessageChannel threadOrChannel = getContestThreadOrChannel(contest);
        if (threadOrChannel == null) return;

        Tile tile = game.getTileByPosition(contest.getTilePosition());
        if (tile == null) return;

        String roundLabel = round > 0 ? "round #" + round : "the current exchange";
        String message = "## Combat Update\n"
                + player.getFactionEmoji() + " " + player.getFaction() + " " + player.getUserName()
                + " assigned hits for " + roundLabel + ".";
        try (FileUpload fileUpload = new TileGenerator(game, event, null, 0, tile.getPosition()).createFileUpload()) {
            MessageHelper.sendMessageWithFile(threadOrChannel, fileUpload, message, false, false);
        } catch (IOException e) {
            BotLogger.error(new LogOrigin(game), "Failed to create combat contest hit-assignment image.", e);
            MessageHelper.sendMessageToChannel(threadOrChannel, message);
        }
    }

    // ==================== Message Formatting ====================

    private String formatContestPostHeader(
            Game game, Tile tile, Player attacker, Player defender, String activePlayerSummary) {
        String attackerLegend = ColorEmojis.getColorEmoji(attacker.getColor()) + " " + attacker.getFactionEmoji()
                + " = " + attacker.getUserName();
        String defenderLegend = ColorEmojis.getColorEmoji(defender.getColor()) + " " + defender.getFactionEmoji()
                + " = " + defender.getUserName();
        return "## A New Combat Contest Has Emerged!\n"
                + getLazaxMinigameRoleMention()
                + "\n"
                + "**Game:** `" + game.getName() + "`\n"
                + "**Game Link:** [Open Game](https://asyncti4.com/game/" + game.getName() + ")\n"
                + activePlayerSummary
                + "**System:** " + tile.getRepresentationForButtons() + "\n"
                + "**Combat:** Space Combat\n"
                + "**Predict the winner by reacting below.**\n"
                + "- " + attackerLegend + "\n"
                + "- " + defenderLegend;
    }

    private String formatActivePlayerSummary(Game game) {
        Player activePlayer = game.getActivePlayer();
        if (activePlayer == null) {
            return "**Active Player:** None\n";
        }
        String factionName = activePlayer.getFactionModel() == null
                ? activePlayer.getFaction()
                : activePlayer.getFactionModel().getFactionName();
        return "**Active Player:** " + activePlayer.getFactionEmoji() + " " + factionName + " "
                + activePlayer.getUserName() + "\n";
    }

    private String formatCombatTechLine(Player player) {
        String techSummary = player.getTechs().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .filter(tech -> !player.getPurgedTechs().contains(tech.getAlias()))
                .filter(tech -> tech.isFactionTech()
                        || tech.isUnitUpgrade()
                        || COMBAT_SUMMARY_TECH_ALIASES.contains(tech.getAlias()))
                .sorted(TECH_COMPARATOR)
                .map(tech -> tech.getCondensedReqsEmojis(false) + " " + tech.getName())
                .reduce((left, right) -> left + ", " + right)
                .orElse("No technologies");
        return formatPlayerSummaryLine(player, techSummary);
    }

    private String formatCombatRelicSection(Player attacker, Player defender) {
        String attackerRelics = formatCombatRelicLine(attacker);
        String defenderRelics = formatCombatRelicLine(defender);
        if (attackerRelics == null && defenderRelics == null) return null;

        StringBuilder section = new StringBuilder("## Relics");
        if (attackerRelics != null) {
            section.append("\n").append(attackerRelics);
        }
        if (defenderRelics != null) {
            section.append("\n").append(defenderRelics);
        }
        return section.toString();
    }

    private String formatCombatRelicLine(Player player) {
        String relicSummary = player.getRelics().stream()
                .distinct()
                .filter(COMBAT_SUMMARY_RELIC_ALIASES::contains)
                .map(Mapper::getRelic)
                .filter(Objects::nonNull)
                .map(RelicModel::getName)
                .sorted(String::compareToIgnoreCase)
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
        if (relicSummary == null) return null;

        return formatPlayerSummaryLine(player, relicSummary);
    }

    private String formatPlayerSummaryLine(Player player, String summary) {
        String factionName = player.getFactionModel() == null
                ? player.getFaction()
                : player.getFactionModel().getFactionName();
        return "- " + player.getFactionEmoji() + " " + factionName + " " + player.getUserName() + ": " + summary;
    }

    private static int getTechTypeOrder(TechnologyModel tech) {
        TechnologyModel.TechnologyType type = tech.getFirstType();
        return switch (type) {
            case PROPULSION -> 0;
            case BIOTIC -> 1;
            case CYBERNETIC -> 2;
            case WARFARE -> 3;
            case UNITUPGRADE -> 4;
            case GENERICTF -> 5;
            case NONE -> 6;
        };
    }

    // ==================== Message Posting & Discord ====================

    private void postContestMessage(
            Game game,
            Player attacker,
            Player defender,
            Tile tile,
            TextChannel contestChannel,
            CombatContestEntity contest,
            SpaceCombatSnapshot snapshot) {
        Consumer<Message> onMessage = message -> {
            persistMessageAndThread(game, contest, contestChannel, message, snapshot.threadSummaryText());
            addPredictionReactions(game, attacker, defender, message);
        };
        try {
            FileUpload fileUpload =
                    new TileGenerator(game, null, null, 0, tile.getPosition(), attacker).createFileUpload();
            contestChannel
                    .sendMessage(new MessageCreateBuilder()
                            .addContent(snapshot.parentPostText())
                            .addFiles(fileUpload)
                            .build())
                    .queue(
                            onMessage,
                            error -> BotLogger.error(
                                    new LogOrigin(game), "Failed to post combat contest opener.", error));
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Failed to create combat contest opener image.", e);
            MessageHelper.splitAndSentWithAction(snapshot.parentPostText(), contestChannel, onMessage);
        }
    }

    private void persistMessageAndThread(
            Game game,
            CombatContestEntity contest,
            TextChannel contestChannel,
            Message message,
            String threadSummaryText) {
        contest.setPublicChannelId(contestChannel.getIdLong());
        contest.setPublicMessageId(message.getIdLong());
        repository.save(contest);

        message.createThreadChannel("combat-predictor-" + contest.getGameName() + "-" + contest.getTilePosition())
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS)
                .queue(
                        thread -> {
                            CombatContestEntity latest =
                                    repository.findById(contest.getId()).orElse(null);
                            if (latest == null) return;
                            latest.setPublicThreadId(thread.getIdLong());
                            repository.save(latest);
                            postContestThreadIntro(game, latest, thread, threadSummaryText);
                        },
                        error -> BotLogger.error(
                                "Failed to create combat predictor thread for contest " + contest.getId(), error));
    }

    private void postContestThreadIntro(
            Game game, CombatContestEntity contest, ThreadChannel thread, String threadSummaryText) {
        Runnable postIntro = () -> {
            String intro =
                    "Use this thread for combat follow-up. The bot will post the result here when the space combat resolves.";
            MessageHelper.splitAndSentWithAction(
                    intro, thread, ignored -> postCombatTechSummary(game, contest, thread));
        };
        if (threadSummaryText == null) {
            postIntro.run();
        } else {
            MessageHelper.splitAndSentWithAction(threadSummaryText, thread, ignored -> postIntro.run());
        }
    }

    private void postCombatTechSummary(Game game, CombatContestEntity contest, MessageChannel threadOrChannel) {
        Player attacker = game.getPlayerFromColorOrFaction(contest.getAttackerFaction());
        Player defender = game.getPlayerFromColorOrFaction(contest.getDefenderFaction());
        if (attacker == null || defender == null) return;

        StringBuilder message = new StringBuilder("## Combat Technologies\n")
                .append(formatCombatTechLine(attacker))
                .append("\n")
                .append(formatCombatTechLine(defender));
        String relicSection = formatCombatRelicSection(attacker, defender);
        if (relicSection != null) {
            message.append("\n\n").append(relicSection);
        }
        MessageHelper.sendMessageToChannel(threadOrChannel, message.toString());
    }

    private void addPredictionReactions(Game game, Player attacker, Player defender, Message message) {
        addReaction(game, attacker, message);
        addReaction(game, defender, message);
    }

    private void addReaction(Game game, Player player, Message message) {
        try {
            message.addReaction(Emoji.fromFormatted(player.getFactionEmoji()))
                    .queue(
                            null,
                            error -> BotLogger.error(
                                    new LogOrigin(game),
                                    "Failed to add combat predictor reaction for " + player.getFaction(),
                                    error));
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Failed to parse combat predictor reaction emoji.", e);
        }
    }

    private void postParticipantFollowup(Game game, CombatContestEntity contest, MessageChannel threadOrChannel) {
        Player attacker = game.getPlayerFromColorOrFaction(contest.getAttackerFaction());
        Player defender = game.getPlayerFromColorOrFaction(contest.getDefenderFaction());
        if (attacker == null || defender == null) return;

        String message = "## Summons From The Archives\n"
                + "<@" + attacker.getUserID() + "> <@" + defender.getUserID() + ">\n"
                + "By decree of the Lazax War Game, your fleets have been judged worthy of remembrance. "
                + "If it pleases the honored claimants, tarry a moment and read the commentaries, acclaim, and quiet condemnation recorded above concerning your struggle.";
        MessageHelper.sendMessageToChannel(threadOrChannel, message);
    }

    private void postSubscriptionPrompt(MessageChannel threadOrChannel) {
        String message = "Did you like this? React " + SUBSCRIBE_EMOJI
                + " to subscribe to more, " + UNSUBSCRIBE_EMOJI
                + " to opt out if already subscribed.\n"
                + LAZAX_MINIGAME_SUBSCRIPTION_MARKER;
        MessageHelper.splitAndSentWithAction(message, threadOrChannel, postedMessage -> {
            postedMessage.addReaction(Emoji.fromUnicode(SUBSCRIBE_EMOJI)).queue(null, BotLogger::catchRestError);
            postedMessage.addReaction(Emoji.fromUnicode(UNSUBSCRIBE_EMOJI)).queue(null, BotLogger::catchRestError);
        });
    }

    // ==================== Utilities ====================

    private TextChannel getContestChannel() {
        if (JdaService.guildPrimary == null) return null;
        return JdaService.guildPrimary.getTextChannelsByName(CONTEST_CHANNEL_NAME, true).stream()
                .findFirst()
                .orElse(null);
    }

    private TextChannel getContestPublicChannel(CombatContestEntity contest) {
        if (contest.getPublicChannelId() == null
                || contest.getPublicMessageId() == null
                || JdaService.guildPrimary == null) return null;
        return JdaService.guildPrimary.getTextChannelById(contest.getPublicChannelId());
    }

    private MessageChannel getContestThreadOrChannel(CombatContestEntity contest) {
        if (contest.getPublicThreadId() != null) {
            ThreadChannel thread = JdaService.guildPrimary.getThreadChannelById(contest.getPublicThreadId());
            if (thread != null) return thread;
        }
        if (contest.getPublicChannelId() != null)
            return JdaService.guildPrimary.getTextChannelById(contest.getPublicChannelId());
        return null;
    }

    private String getLazaxMinigameRoleMention() {
        if (JdaService.guildPrimary == null) return "";
        Role role = JdaService.guildPrimary.getRolesByName(LAZAX_MINIGAME_ROLE_NAME, true).stream()
                .findFirst()
                .orElse(null);
        return role == null ? "" : role.getAsMention();
    }

    private boolean matchesParticipants(CombatContestEntity contest, Player player, Player opponent) {
        return matchesParticipant(contest, player) && matchesParticipant(contest, opponent);
    }

    private boolean matchesParticipant(CombatContestEntity contest, Player player) {
        return player.getFaction().equalsIgnoreCase(contest.getAttackerFaction())
                || player.getFaction().equalsIgnoreCase(contest.getDefenderFaction());
    }

    private String getFactionEmoji(Game game, String faction) {
        Player player = game.getPlayerFromColorOrFaction(faction);
        return player == null ? "" : player.getFactionEmoji();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String buildLineSection(String header, Stream<String> lines) {
        return lines.collect(Collectors.joining("\n", header + "\n", ""));
    }

    private String getTilePosition(String buttonId) {
        String sanitized = stripFactionChecker(buttonId).replace("deleteThis", "");
        String[] parts = sanitized.split("_");
        return parts.length > 1 ? parts[1] : "";
    }

    private String stripFactionChecker(String buttonId) {
        if (!buttonId.startsWith("FFCC_")) return buttonId;
        int secondUnderscore = buttonId.indexOf('_', 5);
        if (secondUnderscore < 0) return buttonId;
        return buttonId.substring(secondUnderscore + 1);
    }

    // ==================== Records ====================

    private record FleetStrength(double value, double hp, double expectedHits) {}

    private record SpaceCombatSnapshot(
            String parentPostText,
            String threadSummaryText,
            String activePlayerSummary,
            double attackerStrength,
            double defenderStrength,
            double attackerHp,
            double defenderHp,
            double attackerExpectedHits,
            double defenderExpectedHits,
            double strengthRatio) {}
}
