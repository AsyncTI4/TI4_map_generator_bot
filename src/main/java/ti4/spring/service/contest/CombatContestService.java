package ti4.spring.service.contest;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
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
import ti4.model.ActionCardModel;
import ti4.model.LeaderModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.emoji.ColorEmojis;

@Service
@RequiredArgsConstructor
public class CombatContestService {

    public static final String LAZAX_MINIGAME_SUBSCRIPTION_MARKER = "-# Lazax Minigame Subscription";
    public static final String LAZAX_MINIGAME_ROLE_NAME = "Lazax Minigame";
    private static final String CONTEST_CHANNEL_NAME = "lazax-war-archives";
    private static final Duration CONTEST_COOLDOWN = Duration.ofMinutes(10);
    private static final double MIN_FLEET_RESOURCES = 20.0;
    private static final String THREAD_SUMMARY_HEADER = "## Combat Units\n";
    private static final Set<String> COMBAT_SUMMARY_TECH_ALIASES = Set.of("da", "asc", "x89", "x89c4");
    private static final Set<String> COMBAT_SUMMARY_RELIC_ALIASES = Set.of(
            "metalivoidarmaments", "metalivoidshielding", "lightrailordnance", "baldrick_crownofthalnos", "pi_thalnos");
    private static final double MIN_HP_RATIO = 0.9;
    private static final double ZERO_EPSILON = 0.0001;
    private static final boolean PREDICTION_LOCK_ENABLED = false;
    private static final String SUBSCRIBE_EMOJI = "🟢";
    private static final String UNSUBSCRIBE_EMOJI = "🔴";
    private static final Set<CombatContestStatus> ACTIVE_STATUSES = Set.of(CombatContestStatus.POSTED);

    private final CombatContestRepository repository;
    private final CombatContestPredictionRepository predictionRepository;

    public void onSpaceCombatStarted(Game game, Player attacker, Player defender, Tile tile) {
        try {
            if (!isEligibleGame(game) || !isEligibleCombat(game, attacker, defender, tile)) return;
            // if (!cooldownElapsed()) return;
            if (repository
                    .findFirstByGameNameAndTilePositionAndCombatTypeAndStatusInOrderByPostedAtDesc(
                            game.getName(), tile.getPosition(), CombatContestType.SPACE, ACTIVE_STATUSES)
                    .isPresent()) {
                return;
            }

            SpaceCombatSnapshot snapshot = buildSnapshot(game, attacker, defender, tile);
            if (snapshot == null || !snapshot.isMeaningful()) return;

            TextChannel contestChannel = getContestChannel();
            if (contestChannel == null) return;

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
            contest.setInitialStrengthAttacker(snapshot.attackerStrength());
            contest.setInitialStrengthDefender(snapshot.defenderStrength());
            contest.setInitialHpAttacker(snapshot.attackerHp());
            contest.setInitialHpDefender(snapshot.defenderHp());
            contest.setUpsetIndex(snapshot.upsetIndex());
            long contestId = repository.save(contest).getId();
            postContestMessage(
                    game,
                    attacker,
                    defender,
                    tile,
                    contestChannel,
                    contestId,
                    snapshot.parentPostText(),
                    snapshot.threadSummaryText());
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
            if (game == null || player == null || opponent == null || tile == null) return;
            CombatContestEntity contest = repository
                    .findFirstByGameNameAndTilePositionAndCombatTypeAndStatusInOrderByPostedAtDesc(
                            game.getName(), tile.getPosition(), CombatContestType.SPACE, ACTIVE_STATUSES)
                    .orElse(null);
            if (contest == null || !matchesParticipants(contest, player, opponent)) return;

            MessageChannel threadOrChannel = getContestThreadOrChannel(contest);
            if (threadOrChannel == null) return;
            if (PREDICTION_LOCK_ENABLED) {
                lockPredictions(game, contest, threadOrChannel);
            }
            MessageHelper.splitAndSentWithAction("## Roll Update\n" + message, threadOrChannel, null);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Combat contest roll mirror failed.", e);
        }
    }

    public void mirrorCombatActionCard(Game game, Player player, ActionCardModel actionCard) {
        try {
            if (game == null || player == null || actionCard == null) return;
            List<CombatContestEntity> activeContests =
                    repository.findByGameNameAndStatusIn(game.getName(), ACTIVE_STATUSES);
            if (activeContests.isEmpty()) return;

            for (CombatContestEntity contest : activeContests) {
                if (!matchesParticipant(contest, player)) continue;

                MessageChannel threadOrChannel = getContestThreadOrChannel(contest);
                if (threadOrChannel == null) continue;

                String message =
                        "## Action Card\n" + player.getRepresentation() + " played _" + actionCard.getName() + "_.";
                MessageHelper.sendMessageToChannelWithEmbed(
                        threadOrChannel, message, actionCard.getRepresentationEmbed(false, true, game));
            }
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Combat contest action card relay failed.", e);
        }
    }

    public void mirrorCombatAgent(Game game, Player player, LeaderModel agent) {
        try {
            if (game == null || player == null || agent == null) return;
            List<CombatContestEntity> activeContests =
                    repository.findByGameNameAndStatusIn(game.getName(), ACTIVE_STATUSES);
            if (activeContests.isEmpty()) return;

            for (CombatContestEntity contest : activeContests) {
                if (!matchesParticipant(contest, player)) continue;

                MessageChannel threadOrChannel = getContestThreadOrChannel(contest);
                if (threadOrChannel == null) continue;

                String message = "## Agent\n" + player.getRepresentation() + " used _" + agent.getName() + "_.";

                MessageHelper.sendMessageToChannelWithEmbed(threadOrChannel, message, agent.getRepresentationEmbed());
            }
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Combat contest agent relay failed.", e);
        }
    }

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
        return repository
                .findFirstByOrderByPostedAtDesc()
                .map(contest -> Duration.between(contest.getPostedAt(), LocalDateTime.now())
                                .compareTo(CONTEST_COOLDOWN)
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

    private SpaceCombatSnapshot buildSnapshot(Game game, Player attacker, Player defender, Tile tile) {
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        if (space == null) return null;

        FleetStrength attackerStrength = calculateFleetStrength(game, attacker, space);
        FleetStrength defenderStrength = calculateFleetStrength(game, defender, space);
        if (!attackerStrength.hasNonFighterShip() || !defenderStrength.hasNonFighterShip()) return null;

        double stronger = Math.max(attackerStrength.hp(), defenderStrength.hp());
        double weaker = Math.min(attackerStrength.hp(), defenderStrength.hp());
        double ratio = stronger == 0 ? 0 : weaker / stronger;
        CombatContestUpsetIndex upsetIndex = getUpsetIndex(ratio);
        String summary = extractSpaceOnlySummary(ButtonHelper.getCombatTileSummaryMessage(
                game, tile, attacker, null, "space", Constants.SPACE, List.of(attacker, defender)));

        String openerText =
                formatContestPostHeader(game, tile, attacker, defender, upsetIndex, attackerStrength, defenderStrength);
        String threadSummaryText = null;
        String parentPostText = openerText + "\n\n" + summary;
        if (parentPostText.length() > Message.MAX_CONTENT_LENGTH) {
            parentPostText = openerText;
            threadSummaryText = THREAD_SUMMARY_HEADER + summary;
        }
        return new SpaceCombatSnapshot(
                parentPostText,
                threadSummaryText,
                attackerStrength.value(),
                defenderStrength.value(),
                attackerStrength.hp(),
                defenderStrength.hp(),
                ratio,
                upsetIndex);
    }

    private FleetStrength calculateFleetStrength(Game game, Player player, UnitHolder space) {
        double total = 0;
        double hp = 0;
        boolean hasNonFighterShip = false;
        for (UnitKey unitKey : space.getUnitKeys()) {
            if (!unitKey.getColorID().equalsIgnoreCase(player.getColorID())) {
                continue;
            }
            UnitModel unitModel = player.getPriorityUnitByAsyncID(unitKey.asyncID(), space);
            if (unitModel == null || !unitModel.getIsShip()) {
                continue;
            }
            int totalUnits = space.getUnitCount(unitKey);
            int damagedUnits = space.getDamagedUnitCount(unitKey);
            int undamagedUnits = totalUnits - damagedUnits;

            total += unitModel.getCost() * totalUnits;
            hp += totalUnits;
            if (unitModel.getSustainDamage(player, space)) {
                hp += undamagedUnits;
                if (player.hasTech("nes")) {
                    hp += undamagedUnits;
                }
            }
            if (!"fighter".equalsIgnoreCase(unitModel.getBaseType())) {
                hasNonFighterShip = true;
            }
        }
        return new FleetStrength(total, hp, hasNonFighterShip);
    }

    private String formatContestPostHeader(
            Game game,
            Tile tile,
            Player attacker,
            Player defender,
            CombatContestUpsetIndex upsetIndex,
            FleetStrength attackerStrength,
            FleetStrength defenderStrength) {
        String attackerLegend = ColorEmojis.getColorEmoji(attacker.getColor()) + " " + attacker.getFactionEmoji()
                + " = " + attacker.getUserName();
        String defenderLegend = ColorEmojis.getColorEmoji(defender.getColor()) + " " + defender.getFactionEmoji()
                + " = " + defender.getUserName();
        return "## A New Combat Contest Has Emerged!\n"
                + getLazaxMinigameRoleMention()
                + "\n"
                + "**Game:** `" + game.getName() + "`\n"
                + "**Game Link:** [Open Game](https://asyncti4.com/game/" + game.getName() + ")\n"
                + "**System:** " + tile.getRepresentationForButtons() + "\n"
                + "**Combat:** Space Combat\n"
                + "**Outlook:** "
                + formatUpsetIndex(upsetIndex, attacker, defender, attackerStrength.hp(), defenderStrength.hp()) + "\n"
                + "**Predict the winner by reacting below.**\n"
                + "- " + attackerLegend + "\n"
                + "- " + defenderLegend;
    }

    private String getLazaxMinigameRoleMention() {
        if (JdaService.guildPrimary == null) return "";
        Role role = JdaService.guildPrimary.getRolesByName(LAZAX_MINIGAME_ROLE_NAME, true).stream()
                .findFirst()
                .orElse(null);
        return role == null ? "" : role.getAsMention();
    }

    private void postContestMessage(
            Game game,
            Player attacker,
            Player defender,
            Tile tile,
            TextChannel contestChannel,
            long contestId,
            String parentPostText,
            String threadSummaryText) {
        try {
            FileUpload fileUpload =
                    new TileGenerator(game, null, null, 0, tile.getPosition(), attacker).createFileUpload();
            contestChannel
                    .sendMessage(new MessageCreateBuilder()
                            .addContent(parentPostText)
                            .addFiles(fileUpload)
                            .build())
                    .queue(
                            message -> {
                                persistMessageAndThread(game, contestId, contestChannel, message, threadSummaryText);
                                addPredictionReactions(game, attacker, defender, message);
                            },
                            error -> BotLogger.error(
                                    new LogOrigin(game), "Failed to post combat contest opener.", error));
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Failed to create combat contest opener image.", e);
            MessageHelper.splitAndSentWithAction(parentPostText, contestChannel, message -> {
                persistMessageAndThread(game, contestId, contestChannel, message, threadSummaryText);
                addPredictionReactions(game, attacker, defender, message);
            });
        }
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

    private CombatContestUpsetIndex getUpsetIndex(double ratio) {
        if (ratio >= 0.9) return CombatContestUpsetIndex.EVEN_FIGHT;
        if (ratio >= 0.78) return CombatContestUpsetIndex.FAVORED;
        return CombatContestUpsetIndex.LONG_SHOT;
    }

    private String formatUpsetIndex(
            CombatContestUpsetIndex upsetIndex,
            Player attacker,
            Player defender,
            double attackerHp,
            double defenderHp) {
        return switch (upsetIndex) {
            case EVEN_FIGHT -> "Even Fight";
            case FAVORED, LONG_SHOT -> {
                Player favoredPlayer = attackerHp >= defenderHp ? attacker : defender;
                String label = upsetIndex == CombatContestUpsetIndex.FAVORED ? "Favored" : "Long Shot";
                yield favoredPlayer.getFactionEmoji() + " " + label;
            }
        };
    }

    private String formatUpsetIndex(Game game, CombatContestEntity contest) {
        Player attacker = game.getPlayerFromColorOrFaction(contest.getAttackerFaction());
        Player defender = game.getPlayerFromColorOrFaction(contest.getDefenderFaction());
        if (attacker == null || defender == null) {
            return switch (contest.getUpsetIndex()) {
                case EVEN_FIGHT -> "Even Fight";
                case FAVORED -> "Favored";
                case LONG_SHOT -> "Long Shot";
            };
        }
        return formatUpsetIndex(
                contest.getUpsetIndex(),
                attacker,
                defender,
                safeDouble(contest.getInitialHpAttacker()),
                safeDouble(contest.getInitialHpDefender()));
    }

    public boolean postLeaderboard() {
        String message = buildLeaderboardMessage();
        if (message == null) return false;

        postLeaderboardMessage(message);
        return true;
    }

    private void maybePostLeaderboardAfterResolvedContest() {
        List<CombatContestEntity> pendingBatch =
                repository.findTop5ByStatusAndResolvedAtIsNotNullAndLeaderboardPostedAtIsNullOrderByResolvedAtAsc(
                        CombatContestStatus.RESOLVED);
        if (pendingBatch.size() < 5) return;
        if (!postLeaderboard()) return;

        markLeaderboardBatchPosted(pendingBatch);
    }

    private String getSafeLeaderboardName(String userName) {
        if (userName == null || userName.isBlank()) return "Unknown User";
        return userName.replace("@", "@\u200B");
    }

    private void lockPredictions(Game game, CombatContestEntity contest, MessageChannel threadOrChannel) {
        if (contest.getPredictionLockedAt() != null
                || contest.getPublicChannelId() == null
                || contest.getPublicMessageId() == null) return;

        TextChannel contestChannel = JdaService.guildPrimary.getTextChannelById(contest.getPublicChannelId());
        if (contestChannel == null) return;

        try {
            Message message = contestChannel
                    .retrieveMessageById(contest.getPublicMessageId())
                    .complete();
            PredictionSnapshot snapshot = capturePredictions(game, message, contest);
            contest.setPredictionLockedAt(LocalDateTime.now());
            contest.setLockedAttackerPredictions(snapshot.attackerPredictions());
            contest.setLockedDefenderPredictions(snapshot.defenderPredictions());
            repository.save(contest);
            refreshParentMessageSummary(game, contest, null, null);
            MessageHelper.sendMessageToChannel(threadOrChannel, formatLockAnnouncement(game, contest, snapshot));
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Failed to lock combat contest predictions.", e);
        }
    }

    private PredictionSnapshot capturePredictions(Game game, Message message, CombatContestEntity contest) {
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
        int attackerPredictions = 0;
        int defenderPredictions = 0;
        int invalidPredictions = 0;
        for (Map.Entry<String, Set<String>> entry : factionsByUser.entrySet()) {
            if (entry.getValue().size() != 1) {
                invalidPredictions++;
                continue;
            }

            String predictedFaction = entry.getValue().iterator().next();
            CombatContestPredictionEntity prediction = new CombatContestPredictionEntity();
            prediction.setContestId(contest.getId());
            prediction.setDiscordUserId(entry.getKey());
            prediction.setDiscordUserName(namesByUser.getOrDefault(entry.getKey(), "Unknown User"));
            prediction.setPredictedFaction(predictedFaction);
            prediction.setLockedAt(LocalDateTime.now());
            predictions.add(prediction);

            if (predictedFaction.equalsIgnoreCase(contest.getAttackerFaction())) attackerPredictions++;
            if (predictedFaction.equalsIgnoreCase(contest.getDefenderFaction())) defenderPredictions++;
        }
        predictionRepository.saveAll(predictions);
        return new PredictionSnapshot(attackerPredictions, defenderPredictions, predictions.size(), invalidPredictions);
    }

    private String getFactionEmoji(Game game, String faction) {
        Player player = game.getPlayerFromColorOrFaction(faction);
        return player == null ? "" : player.getFactionEmoji();
    }

    private String formatLockAnnouncement(Game game, CombatContestEntity contest, PredictionSnapshot snapshot) {
        return "## Votes Locked In\n"
                + getFactionEmoji(game, contest.getAttackerFaction()) + " `"
                + snapshot.attackerPredictions() + "`\n"
                + getFactionEmoji(game, contest.getDefenderFaction()) + " `"
                + snapshot.defenderPredictions() + "`\n"
                + (snapshot.invalidPredictions() > 0
                        ? "-# `" + snapshot.invalidPredictions()
                                + "` multi-outcome vote" + (snapshot.invalidPredictions() == 1 ? " was" : "s were")
                                + " excluded.\n"
                        : "")
                + "*May fortune favor the bold.*";
    }

    private void persistMessageAndThread(
            Game game, Long contestId, TextChannel contestChannel, Message message, String threadSummaryText) {
        CombatContestEntity contest = repository.findById(contestId).orElse(null);
        if (contest == null) return;
        contest.setPublicChannelId(contestChannel.getIdLong());
        contest.setPublicMessageId(message.getIdLong());
        repository.save(contest);

        message.createThreadChannel("combat-predictor-" + contest.getGameName() + "-" + contest.getTilePosition())
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS)
                .queue(
                        thread -> {
                            CombatContestEntity latest =
                                    repository.findById(contestId).orElse(null);
                            if (latest == null) return;
                            latest.setPublicThreadId(thread.getIdLong());
                            repository.save(latest);
                            postContestThreadIntro(game, latest, thread, threadSummaryText);
                        },
                        error -> BotLogger.error(
                                "Failed to create combat predictor thread for contest " + contestId, error));
    }

    private String buildLeaderboardMessage() {
        List<CombatContestLeaderboardRow> topEntries = predictionRepository.findLeaderboardRows(PageRequest.of(0, 10));
        if (topEntries.isEmpty()) return null;

        StringBuilder message = new StringBuilder("## Lazax War Archives Leaderboard\n");
        message.append("-# Posted daily at 15:00 UTC (9:00 CST).\n");
        int rank = 1;
        for (CombatContestLeaderboardRow entry : topEntries) {
            long predictions = entry.getPredictionCount() == null ? 0 : entry.getPredictionCount();
            long correctPredictions = entry.getCorrectPredictions() == null ? 0 : entry.getCorrectPredictions();
            int accuracy = predictions == 0 ? 0 : Math.round((100f * correctPredictions) / predictions);
            message.append('`')
                    .append(rank++)
                    .append(".` ")
                    .append(getSafeLeaderboardName(entry.getDiscordUserName()))
                    .append(" - **")
                    .append(entry.getTotalPoints())
                    .append("** points")
                    .append(" (`")
                    .append(correctPredictions)
                    .append('/')
                    .append(predictions)
                    .append("` correct, ")
                    .append(accuracy)
                    .append("%)\n");
        }
        return message.toString().trim();
    }

    private void postLeaderboardMessage(String message) {
        TextChannel contestChannel = getContestChannel();
        if (contestChannel == null) return;
        MessageHelper.sendMessageToChannel(contestChannel, message);
    }

    private void markLeaderboardBatchPosted(List<CombatContestEntity> pendingBatch) {
        LocalDateTime postedAt = LocalDateTime.now();
        pendingBatch.forEach(contest -> contest.setLeaderboardPostedAt(postedAt));
        repository.saveAll(pendingBatch);
    }

    private void postContestThreadIntro(
            Game game, CombatContestEntity contest, ThreadChannel thread, String threadSummaryText) {
        postThreadSummaryThen(game, contest, thread, threadSummaryText, () -> {
            String intro =
                    "Use this thread for combat follow-up. The bot will post the result here when the space combat resolves.";
            MessageHelper.splitAndSentWithAction(
                    intro, thread, ignored -> postCombatTechSummary(game, contest, thread));
        });
    }

    private void postThreadSummaryThen(
            Game game, CombatContestEntity contest, ThreadChannel thread, String threadSummaryText, Runnable nextStep) {
        if (threadSummaryText == null) {
            nextStep.run();
            return;
        }
        MessageHelper.splitAndSentWithAction(threadSummaryText, thread, ignored -> nextStep.run());
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

    private String formatCombatTechLine(Player player) {
        String techSummary = player.getTechs().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .filter(tech -> !player.getPurgedTechs().contains(tech.getAlias()))
                .filter(tech -> tech.isFactionTech()
                        || tech.isUnitUpgrade()
                        || COMBAT_SUMMARY_TECH_ALIASES.contains(tech.getAlias()))
                .sorted((left, right) -> {
                    int typeComparison = Integer.compare(getTechTypeOrder(left), getTechTypeOrder(right));
                    if (typeComparison != 0) return typeComparison;
                    int tierComparison = Integer.compare(
                            left.getRequirements().orElse("").length(),
                            right.getRequirements().orElse("").length());
                    if (tierComparison != 0) return tierComparison;
                    return left.getName().compareToIgnoreCase(right.getName());
                })
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

    private int getTechTypeOrder(TechnologyModel tech) {
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

    private boolean evaluateContestCompletion(Game game, ButtonInteractionEvent event, CombatContestEntity contest) {
        Tile tile = game.getTileByPosition(contest.getTilePosition());
        if (tile == null) return false;

        List<Player> remainingShipPlayers = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile).stream()
                .filter(Player::isRealPlayer)
                .filter(player -> !player.isDummy())
                .toList();
        if (remainingShipPlayers.size() == 1) {
            Player winner = remainingShipPlayers.getFirst();
            String loserFaction = winner.getFaction().equalsIgnoreCase(contest.getAttackerFaction())
                    ? contest.getDefenderFaction()
                    : contest.getAttackerFaction();
            resolveContest(game, event, contest, tile, winner, loserFaction);
            return true;
        }

        if (remainingShipPlayers.isEmpty()) {
            cancelContest(game, contest, "The tracked space combat ended with no ships remaining.");
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
        contest.setStatus(CombatContestStatus.RESOLVED);
        contest.setResolvedAt(LocalDateTime.now());
        contest.setWinnerFaction(winner.getFaction());
        contest.setLoserFaction(loserFaction);
        awardPredictionPoints(contest);
        repository.save(contest);

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
            postPredictionPointsSummary(threadOrChannel, contest, () -> {
                postParticipantFollowup(game, contest, threadOrChannel);
                postSubscriptionPrompt(threadOrChannel);
            });
        }
        refreshParentMessageSummary(
                game,
                contest,
                buildResultStamp(contest, tile, winner),
                buildLossSummary(game, tile, contest, winner, loserFaction));
        maybePostLeaderboardAfterResolvedContest();
    }

    private void cancelContest(Game game, CombatContestEntity contest, String reason) {
        contest.setStatus(CombatContestStatus.CANCELLED);
        contest.setResolvedAt(LocalDateTime.now());
        repository.save(contest);

        MessageChannel threadOrChannel = getContestThreadOrChannel(contest);
        if (threadOrChannel != null)
            MessageHelper.sendMessageToChannel(threadOrChannel, "## Contest Closed\n" + reason);
        refreshParentMessageSummary(game, contest, "Cancelled", reason);
    }

    private void capturePredictionsAtResolution(Game game, CombatContestEntity contest) {
        if (PREDICTION_LOCK_ENABLED) return;
        if (!predictionRepository.findByContestId(contest.getId()).isEmpty()) return;
        if (contest.getPublicChannelId() == null || contest.getPublicMessageId() == null) return;

        TextChannel contestChannel = JdaService.guildPrimary.getTextChannelById(contest.getPublicChannelId());
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
        if (contest.getPublicChannelId() == null || contest.getPublicMessageId() == null) return;
        TextChannel contestChannel = JdaService.guildPrimary.getTextChannelById(contest.getPublicChannelId());
        if (contestChannel == null) return;
        StringBuilder updated = new StringBuilder(contest.getInitialSummaryText());
        updated.append("\n\n-# Contest outlook: ").append(formatUpsetIndex(game, contest));
        if (contest.getPredictionLockedAt() != null) {
            int totalPredictions =
                    safeInt(contest.getLockedAttackerPredictions()) + safeInt(contest.getLockedDefenderPredictions());
            updated.append("\n-# Contest lock: Predictions locked at first dice roll (")
                    .append(totalPredictions)
                    .append(" captured).");
        }
        if (resultStamp != null) updated.append("\n-# Contest result: ").append(resultStamp);
        if (detailStamp != null) updated.append("\n-# Contest details: ").append(detailStamp);
        contestChannel
                .retrieveMessageById(contest.getPublicMessageId())
                .queue(
                        message -> message.editMessage(updated.toString()).queueAfter(100, TimeUnit.MILLISECONDS),
                        BotLogger::catchRestError);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double safeDouble(Double value) {
        return value == null ? 0 : value;
    }

    private void awardPredictionPoints(CombatContestEntity contest) {
        List<CombatContestPredictionEntity> predictions = predictionRepository.findByContestId(contest.getId());
        if (predictions.isEmpty()) return;

        int attackerPredictions = (int) predictions.stream()
                .filter(prediction -> prediction.getPredictedFaction().equalsIgnoreCase(contest.getAttackerFaction()))
                .count();
        int defenderPredictions = predictions.size() - attackerPredictions;
        for (CombatContestPredictionEntity prediction : predictions) {
            boolean correct = prediction.getPredictedFaction().equalsIgnoreCase(contest.getWinnerFaction());
            prediction.setCorrect(correct);
            int winnerPredictions = contest.getWinnerFaction().equalsIgnoreCase(contest.getAttackerFaction())
                    ? attackerPredictions
                    : defenderPredictions;
            int totalPredictions = attackerPredictions + defenderPredictions;
            prediction.setPointsAwarded(correct ? calculatePredictionPoints(winnerPredictions, totalPredictions) : 0);
        }
        predictionRepository.saveAll(predictions);
    }

    private void postPredictionPointsSummary(
            MessageChannel threadOrChannel, CombatContestEntity contest, Runnable afterPost) {
        List<CombatContestPredictionEntity> predictions = predictionRepository.findByContestId(contest.getId());
        if (predictions.isEmpty()) {
            if (afterPost != null) afterPost.run();
            return;
        }

        List<CombatContestPredictionEntity> winningPredictions = predictions.stream()
                .filter(prediction -> safeInt(prediction.getPointsAwarded()) > 0)
                .toList();
        if (winningPredictions.isEmpty()) {
            if (afterPost != null) afterPost.run();
            return;
        }

        Map<String, Integer> totalsByUser = predictionRepository
                .findPointTotalsByDiscordUserIdIn(winningPredictions.stream()
                        .map(CombatContestPredictionEntity::getDiscordUserId)
                        .toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        CombatContestUserPointsRow::getDiscordUserId, row -> safeInt(row.getTotalPoints())));

        StringBuilder message = new StringBuilder("## Prediction Points\n");
        winningPredictions.stream()
                .sorted((left, right) -> {
                    int pointsComparison =
                            Integer.compare(safeInt(right.getPointsAwarded()), safeInt(left.getPointsAwarded()));
                    if (pointsComparison != 0) return pointsComparison;
                    return left.getDiscordUserName().compareToIgnoreCase(right.getDiscordUserName());
                })
                .forEach(prediction -> {
                    int pointsAwarded = safeInt(prediction.getPointsAwarded());
                    int totalPoints = totalsByUser.getOrDefault(prediction.getDiscordUserId(), 0);
                    message.append("<@")
                            .append(prediction.getDiscordUserId())
                            .append("> - ")
                            .append(totalPoints)
                            .append(" points (+")
                            .append(pointsAwarded)
                            .append(")\n");
                });

        MessageHelper.splitAndSentWithAction(message.toString().trim(), threadOrChannel, postedMessage -> {
            if (afterPost != null) afterPost.run();
        });
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

    private int calculatePredictionPoints(int winnerPredictions, int totalPredictions) {
        totalPredictions = Math.max(1, totalPredictions);
        double winnerShare = winnerPredictions / (double) totalPredictions;
        double scaledPoints = 4.0 / Math.max(winnerShare, ZERO_EPSILON);
        return (int) Math.round(Math.max(4.0, Math.min(12.0, scaledPoints)));
    }

    private String buildResultStamp(CombatContestEntity contest, Tile tile, Player winner) {
        List<CombatContestPredictionEntity> predictions = predictionRepository.findByContestId(contest.getId());
        int totalPredictions = predictions.size();
        long correctPredictions = predictions.stream()
                .filter(prediction -> prediction.getPredictedFaction().equalsIgnoreCase(winner.getFaction()))
                .count();
        String loserFaction = winner.getFaction().equalsIgnoreCase(contest.getAttackerFaction())
                ? contest.getDefenderFaction()
                : contest.getAttackerFaction();
        return winner.getFactionEmoji() + " defeated `" + loserFaction + "` in " + tile.getRepresentationForButtons()
                + " (" + correctPredictions + "/" + totalPredictions + " called it)";
    }

    private String buildLossSummary(
            Game game, Tile tile, CombatContestEntity contest, Player winner, String loserFaction) {
        Player loser = game.getPlayerFromColorOrFaction(loserFaction);
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        if (space == null || loser == null) return null;

        double winnerRemaining = calculateFleetStrength(game, winner, space).value();
        double loserRemaining = calculateFleetStrength(game, loser, space).value();
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
        double inflictedDamage = Math.max(0, initialStrength - remainingStrength);
        if (inflictedDamage <= ZERO_EPSILON) return "no losses";
        double losses = inflictedDamage / initialStrength;
        if (losses < 0.2) return "light losses";
        if (losses < 0.5) return "moderate losses";
        if (losses < 0.85) return "heavy losses";
        return "catastrophic losses";
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

    private TextChannel getContestChannel() {
        if (JdaService.guildPrimary == null) return null;
        return JdaService.guildPrimary.getTextChannelsByName(CONTEST_CHANNEL_NAME, true).stream()
                .findFirst()
                .orElse(null);
    }

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

    private int getCurrentRound(Game game, CombatContestEntity contest) {
        return Math.max(
                getCurrentRound(game, contest.getAttackerFaction(), contest.getTilePosition()),
                getCurrentRound(game, contest.getDefenderFaction(), contest.getTilePosition()));
    }

    private int getCurrentRound(Game game, String faction, String tilePosition) {
        String tracker = game.getStoredValue("combatRoundTracker" + faction + tilePosition + Constants.SPACE);
        return tracker.isBlank() ? 0 : Integer.parseInt(tracker);
    }

    private boolean matchesParticipants(CombatContestEntity contest, Player player, Player opponent) {
        return matchesParticipant(contest, player) && matchesParticipant(contest, opponent);
    }

    private boolean matchesParticipant(CombatContestEntity contest, Player player) {
        return player.getFaction().equalsIgnoreCase(contest.getAttackerFaction())
                || player.getFaction().equalsIgnoreCase(contest.getDefenderFaction());
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

    private record PredictionSnapshot(
            int attackerPredictions, int defenderPredictions, int totalPredictions, int invalidPredictions) {}

    private record FleetStrength(double value, double hp, boolean hasNonFighterShip) {}

    private record SpaceCombatSnapshot(
            String parentPostText,
            String threadSummaryText,
            double attackerStrength,
            double defenderStrength,
            double attackerHp,
            double defenderHp,
            double strengthRatio,
            CombatContestUpsetIndex upsetIndex) {
        private boolean isMeaningful() {
            double strongerResources = Math.max(attackerStrength, defenderStrength);
            double weakerResources = Math.min(attackerStrength, defenderStrength);
            return strongerResources >= MIN_FLEET_RESOURCES
                    && weakerResources >= MIN_FLEET_RESOURCES
                    && attackerHp > 0
                    && defenderHp > 0
                    && strengthRatio >= MIN_HP_RATIO;
        }
    }
}
