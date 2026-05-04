package ti4.contest.replay.house.hacan;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.entities.CombatReplayHacanTradeConvoysEntity;
import ti4.contest.replay.entities.CombatReplayHacanTradeConvoysVoteEntity;
import ti4.contest.replay.entities.CombatReplayHouseAbilityUseEntity;
import ti4.contest.replay.entities.CombatReplayHouseScoreEntity;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayHacanTradeConvoysRepository;
import ti4.contest.replay.repository.CombatReplayHacanTradeConvoysVoteRepository;
import ti4.contest.replay.repository.CombatReplayHouseAbilityUseRepository;
import ti4.contest.replay.repository.CombatReplayHouseScoreRepository;
import ti4.contest.replay.service.CombatReplayAbilityWindowText;
import ti4.contest.replay.service.CombatReplayHouseFavorService;
import ti4.contest.replay.service.CombatReplayHouseService;
import ti4.contest.replay.service.CombatReplayInteractionResult;
import ti4.contest.replay.service.CombatReplayVoteTally;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@Service
@RequiredArgsConstructor
public class CombatReplayHacanTradeConvoysService {

    public static final String HACAN_TRADE_CONVOYS_PREFIX = "combatReplayHacanTradeConvoys_";

    private static final String DELIMITER = "~";
    private final CombatContestSettings settings;
    private final CombatReplayHouseService houseService;
    private final CombatReplayHouseFavorService houseFavorService;
    private final CombatReplayContestRepository contestRepository;
    private final CombatReplayHouseAbilityUseRepository abilityUseRepository;
    private final CombatReplayHacanTradeConvoysRepository tradeConvoysRepository;
    private final CombatReplayHacanTradeConvoysVoteRepository tradeConvoysVoteRepository;
    private final CombatReplayHouseScoreRepository houseScoreRepository;

    public void postTradeConvoysVotingButtonsIfNeeded(
            CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        if (!shouldOfferVoting(contest, candidate)) return;
        postTradeConvoysVotingButtons(contest);
    }

    public void postPostCombatTradeConvoysButtonsIfNeeded(
            CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        if (!canOfferTradeConvoys(contest, candidate)) return;
        postTradeConvoysVotingButtons(contest);
    }

    private void postTradeConvoysVotingButtons(CombatReplayContestEntity contest) {

        TextChannel channel = houseChannel();
        if (channel == null) return;

        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                "## " + FactionEmojis.getFactionIcon(CombatReplayHouse.HACAN.displayName())
                        + " Hacan Delegation Trade Convoys\n"
                        + CombatReplayAbilityWindowText.votesLockWhenNextContestPostsLine()
                        + "\n"
                        + favorBalanceLine()
                        + "\n"
                        + tradeConvoysSummaryLine(),
                List.of(Buttons.red(
                        formatButtonId(contest.getId(), CombatReplayHouse.HACAN, 0, 0), "Do Not Use Trade Convoys")));
        postTradeConvoysButtonsForHouse(channel, contest, CombatReplayHouse.NAALU);
        postTradeConvoysButtonsForHouse(channel, contest, CombatReplayHouse.MENTAK);
    }

    public boolean shouldOfferVoting(CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        return canOfferTradeConvoys(contest, candidate) && postCombatVotingOpen(contest);
    }

    private boolean canOfferTradeConvoys(CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        return settings.isHousesEnabled()
                && contest != null
                && contest.getId() != null
                && candidate != null
                && !contestRepository.existsByIdGreaterThan(contest.getId())
                && tradeConvoysRepository.findByContestId(contest.getId()).isEmpty();
    }

    public String tradeConvoysSummaryLine() {
        return "-# Trade Convoys: send Favor to one rival Delegation. Hacan gains the listed share of that Delegation's earned points in the next combat.";
    }

    private String favorBalanceLine() {
        return "-# Total Favor: `" + houseFavorService.balance(CombatReplayHouse.HACAN) + "`";
    }

    public void sendEphemeralVotingControls(
            ButtonInteractionEvent event, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        if (!shouldOfferVoting(contest, candidate)) return;
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(
                event,
                "## "
                        + FactionEmojis.getFactionIcon(CombatReplayHouse.HACAN.displayName())
                        + " Hacan Delegation Trade Convoys\n"
                        + CombatReplayAbilityWindowText.votesLockWhenNextContestPostsLine()
                        + "\n"
                        + favorBalanceLine()
                        + "\n"
                        + voteSummary(contest.getId()),
                List.of(Buttons.red(
                        formatButtonId(contest.getId(), CombatReplayHouse.HACAN, 0, 0), "Do Not Use Trade Convoys")));
        sendEphemeralTradeConvoysButtonsForHouse(event, contest, CombatReplayHouse.NAALU);
        sendEphemeralTradeConvoysButtonsForHouse(event, contest, CombatReplayHouse.MENTAK);
    }

    @Transactional
    public synchronized CombatReplayInteractionResult recordTradeConvoysVote(
            ButtonInteractionEvent event, ParsedTradeConvoysButton request) {
        if (!settings.isHousesEnabled())
            return CombatReplayInteractionResult.rejected("Hacan Delegation Trade Convoys is disabled.");
        if (!userHasHacan(event.getUser().getId())) {
            return CombatReplayInteractionResult.rejected("Only Hacan Delegation may broker Trade Convoys.");
        }
        if (!isValidRequest(request))
            return CombatReplayInteractionResult.rejected("That Hacan Trade Convoys option is not available.");
        CombatReplayContestEntity contest = tradeConvoysContest(request.contestId());
        if (!votingOpen(contest))
            return CombatReplayInteractionResult.rejected("The Hacan Trade Convoys window is closed.");
        if (tradeConvoysRepository.findByContestId(request.contestId()).isPresent()) {
            return CombatReplayInteractionResult.rejected("Hacan Trade Convoys has already resolved for this combat.");
        }
        if (!isDoNotUse(request) && !houseFavorService.canAfford(CombatReplayHouse.HACAN, request.favorCost())) {
            return CombatReplayInteractionResult.rejected(
                    "Hacan Delegation does not have enough Favor for that Trade Convoys option.");
        }

        CombatReplayHacanTradeConvoysVoteEntity vote = tradeConvoysVoteRepository
                .findByContestIdAndDiscordUserId(
                        request.contestId(), event.getUser().getId())
                .orElse(null);
        if (vote != null
                && vote.getTargetHouse() == request.targetHouse()
                && safeInt(vote.getFavorCost()) == request.favorCost()
                && safeInt(vote.getPredictionBonus()) == request.bonusPercent()) {
            tradeConvoysVoteRepository.delete(vote);
            return CombatReplayInteractionResult.accepted(
                    "Withdrew Hacan Trade Convoys vote.\n" + voteSummary(request.contestId()));
        }
        if (vote == null) {
            vote = new CombatReplayHacanTradeConvoysVoteEntity();
            vote.setContestId(request.contestId());
            vote.setDiscordUserId(event.getUser().getId());
        }
        vote.setTargetHouse(isDoNotUse(request) ? null : request.targetHouse());
        vote.setFavorCost(isDoNotUse(request) ? 0 : request.favorCost());
        vote.setPredictionBonus(isDoNotUse(request) ? 0 : request.bonusPercent());
        vote.setDiscordUserName(StringUtils.defaultIfBlank(event.getUser().getEffectiveName(), "Unknown User"));
        vote.setVotedAt(LocalDateTime.now());
        tradeConvoysVoteRepository.save(vote);

        return CombatReplayInteractionResult.accepted("Cast Hacan Trade Convoys vote for **" + optionLabel(request)
                + "**.\n" + voteSummary(request.contestId()));
    }

    @Transactional
    public synchronized TradeConvoys lockTradeConvoysIfNeeded(
            CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        if (!settings.isHousesEnabled()) return TradeConvoys.none();
        if (contest == null || contest.getId() == null || candidate == null || candidate.getId() == null) {
            return TradeConvoys.none();
        }

        CombatReplayHacanTradeConvoysEntity existing =
                tradeConvoysRepository.findByContestId(contest.getId()).orElse(null);
        if (existing != null) return toTradeConvoys(existing);

        List<CombatReplayHacanTradeConvoysVoteEntity> votes =
                tradeConvoysVoteRepository.findByContestId(contest.getId());
        if (distinctVoterCount(votes) < minimumAbilityVotesToResolve()) {
            return lockNoTradeConvoys(contest, distinctVoterCount(votes));
        }

        TradeConvoysTally winning = winningTally(votes);
        if (winning == null || winning.targetHouse() == null || winning.bonusPercent() <= 0) {
            return lockNoTradeConvoys(contest, winning == null ? 0 : winning.voteCount());
        }
        if (!houseFavorService.canAfford(CombatReplayHouse.HACAN, effectiveFavorCost(winning.favorCost()))) {
            return lockNoTradeConvoys(contest, winning.voteCount());
        }
        if (!claimHacanTradeConvoysUse(
                candidate.getId(),
                effectiveFavorCost(winning.favorCost()),
                winning.discordUserId(),
                winning.discordUserName())) {
            return lockNoTradeConvoys(contest, winning.voteCount());
        }

        CombatReplayHacanTradeConvoysEntity tradeConvoys = new CombatReplayHacanTradeConvoysEntity();
        tradeConvoys.setContestId(contest.getId());
        tradeConvoys.setTargetHouse(winning.targetHouse());
        tradeConvoys.setFavorCost(winning.favorCost());
        tradeConvoys.setPredictionBonus(winning.bonusPercent());
        tradeConvoys.setVoteCount(winning.voteCount());
        tradeConvoys.setSelectedAt(LocalDateTime.now());
        tradeConvoysRepository.save(tradeConvoys);
        applyFavorGrant(tradeConvoys);
        postLockedTradeConvoysSummary(tradeConvoys);
        return toTradeConvoys(tradeConvoys);
    }

    public TradeConvoys tradeConvoysForContest(Long contestId) {
        if (!settings.isHousesEnabled() || contestId == null) return TradeConvoys.none();
        return tradeConvoysRepository
                .findByContestId(contestId)
                .map(this::toTradeConvoys)
                .orElse(TradeConvoys.none());
    }

    public TradeConvoys tradeConvoysForNextCombat(Long contestId) {
        if (!settings.isHousesEnabled() || contestId == null) return TradeConvoys.none();
        Set<Long> scoredContestIds = houseScoreRepository.findAll().stream()
                .map(CombatReplayHouseScoreEntity::getContestId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        return tradeConvoysRepository.findAll().stream()
                .filter(entity -> entity.getContestId() != null)
                .filter(entity -> entity.getContestId() < contestId)
                .filter(entity -> entity.getTargetHouse() != null)
                .filter(entity -> safeInt(entity.getPredictionBonus()) > 0)
                .filter(entity -> noScoredContestBetween(entity.getContestId(), contestId, scoredContestIds))
                .max(Comparator.comparing(CombatReplayHacanTradeConvoysEntity::getContestId))
                .map(this::toTradeConvoys)
                .orElse(TradeConvoys.none());
    }

    public static int tradeConvoysBonusPoints(int targetEarnedPoints, int bonusPercent) {
        if (targetEarnedPoints <= 0 || bonusPercent <= 0) return 0;
        return Math.round(targetEarnedPoints * bonusPercent / 100.0f);
    }

    @Transactional
    public void clearTradeConvoys(Long contestId) {
        if (contestId == null) return;
        tradeConvoysRepository.deleteByContestId(contestId);
        tradeConvoysVoteRepository.deleteByContestId(contestId);
    }

    private void postTradeConvoysButtonsForHouse(
            TextChannel channel, CombatReplayContestEntity contest, CombatReplayHouse target) {
        List<Button> buttons = tradeConvoysButtonsForHouse(contest, target);
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                "### " + FactionEmojis.getFactionIcon(target.displayName()) + " " + target.displayName()
                        + " Delegation",
                buttons);
    }

    private void sendEphemeralTradeConvoysButtonsForHouse(
            ButtonInteractionEvent event, CombatReplayContestEntity contest, CombatReplayHouse target) {
        List<Button> buttons = tradeConvoysButtonsForHouse(contest, target);
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(
                event,
                "### " + FactionEmojis.getFactionIcon(target.displayName()) + " " + target.displayName()
                        + " Delegation",
                buttons);
    }

    List<Button> tradeConvoysButtonsForHouse(CombatReplayContestEntity contest, CombatReplayHouse target) {
        List<Button> buttons = new ArrayList<>();
        for (TradeConvoysTier tier : tradeConvoysTiers()) {
            Button button = Buttons.green(
                    formatButtonId(contest.getId(), target, tier.favorCost(), tier.bonusPercent()),
                    tier.favorCost() + " Favor: " + tier.bonusPercent() + "% next combat",
                    FactionEmojis.getFactionIcon(target.displayName()).toString());
            buttons.add(
                    houseFavorService.canAfford(CombatReplayHouse.HACAN, tier.favorCost())
                            ? button
                            : button.asDisabled());
        }
        return buttons;
    }

    private CombatReplayContestEntity tradeConvoysContest(Long contestId) {
        return contestId == null ? null : contestRepository.findById(contestId).orElse(null);
    }

    private boolean votingOpen(CombatReplayContestEntity contest) {
        return postCombatVotingOpen(contest);
    }

    private boolean postCombatVotingOpen(CombatReplayContestEntity contest) {
        return contest != null
                && contest.getId() != null
                && !contestRepository.existsByIdGreaterThan(contest.getId())
                && (contest.getReplayCompletedAt() != null || contest.getLeaderboardPostedAt() != null);
    }

    private boolean userHasHacan(String discordUserId) {
        if (settings.getRuntime().isDevMode()) return true;
        return houseService.houseForUser(discordUserId) == CombatReplayHouse.HACAN;
    }

    private boolean isValidRequest(ParsedTradeConvoysButton request) {
        if (request.contestId() == null || request.targetHouse() == null) return false;
        if (isDoNotUse(request)) return true;
        if (request.targetHouse() != CombatReplayHouse.NAALU && request.targetHouse() != CombatReplayHouse.MENTAK) {
            return false;
        }
        return tradeConvoysTiers().contains(new TradeConvoysTier(request.favorCost(), request.bonusPercent()));
    }

    private List<TradeConvoysTier> tradeConvoysTiers() {
        CombatContestSettings.Hacan hacan = settings.getHouseAbilities().getHacan();
        return List.of(
                new TradeConvoysTier(hacan.getLowTradeConvoysFavorCost(), hacan.getLowTradeConvoysPredictionBonus()),
                new TradeConvoysTier(
                        hacan.getMediumTradeConvoysFavorCost(), hacan.getMediumTradeConvoysPredictionBonus()),
                new TradeConvoysTier(hacan.getHighTradeConvoysFavorCost(), hacan.getHighTradeConvoysPredictionBonus()));
    }

    private TradeConvoysTally winningTally(List<CombatReplayHacanTradeConvoysVoteEntity> votes) {
        return CombatReplayVoteTally.tallies(
                        votes,
                        vote -> new TradeConvoysOption(
                                vote.getTargetHouse(),
                                safeInt(vote.getFavorCost()),
                                safeInt(vote.getPredictionBonus())))
                .stream()
                .max(Comparator.comparingInt(
                                (CombatReplayVoteTally.Tally<
                                                        CombatReplayHacanTradeConvoysVoteEntity, TradeConvoysOption>
                                                tally) -> tally.voteCount())
                        .thenComparingInt(tally -> tally.option().bonusPercent())
                        .thenComparing(tally -> tally.option().targetHouse() == null
                                ? ""
                                : tally.option().targetHouse().displayName()))
                .map(tally -> {
                    CombatReplayHacanTradeConvoysVoteEntity firstVote = tally.firstVote();
                    return new TradeConvoysTally(
                            tally.option().targetHouse(),
                            tally.option().favorCost(),
                            tally.option().bonusPercent(),
                            firstVote.getDiscordUserId(),
                            firstVote.getDiscordUserName(),
                            tally.voteCount());
                })
                .orElse(null);
    }

    private TradeConvoys lockNoTradeConvoys(CombatReplayContestEntity contest, long voteCount) {
        CombatReplayHacanTradeConvoysEntity tradeConvoys = new CombatReplayHacanTradeConvoysEntity();
        tradeConvoys.setContestId(contest.getId());
        tradeConvoys.setTargetHouse(null);
        tradeConvoys.setFavorCost(0);
        tradeConvoys.setPredictionBonus(0);
        tradeConvoys.setVoteCount((int) Math.min(Integer.MAX_VALUE, voteCount));
        tradeConvoys.setSelectedAt(LocalDateTime.now());
        tradeConvoysRepository.save(tradeConvoys);
        postLockedTradeConvoysSummary(tradeConvoys);
        return TradeConvoys.none();
    }

    private boolean claimHacanTradeConvoysUse(
            long candidateId, int favorCost, String discordUserId, String discordUserName) {
        if (abilityUseRepository.existsByCandidateIdAndHouse(candidateId, CombatReplayHouse.HACAN)) return false;
        CombatReplayHouseAbilityUseEntity use = new CombatReplayHouseAbilityUseEntity();
        use.setCandidateId(candidateId);
        use.setHouse(CombatReplayHouse.HACAN);
        use.setFavorCost(favorCost);
        use.setDiscordUserId(discordUserId);
        use.setDiscordUserName(StringUtils.defaultIfBlank(discordUserName, "Unknown User"));
        use.setUsedAt(LocalDateTime.now());
        try {
            abilityUseRepository.saveAndFlush(use);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    private void applyFavorGrant(CombatReplayHacanTradeConvoysEntity tradeConvoys) {
        if (tradeConvoys.getContestId() == null
                || tradeConvoys.getTargetHouse() == null
                || safeInt(tradeConvoys.getFavorCost()) <= 0) return;
        for (CombatReplayHouseScoreEntity score : houseScoreRepository.findByContestId(tradeConvoys.getContestId())) {
            if (score.getHouse() != tradeConvoys.getTargetHouse()) continue;
            score.setFavorPoints(safeInt(score.getFavorPoints()) + safeInt(tradeConvoys.getFavorCost()));
            houseScoreRepository.save(score);
            return;
        }
    }

    private String voteSummary(Long contestId) {
        List<CombatReplayHacanTradeConvoysVoteEntity> votes = tradeConvoysVoteRepository.findByContestId(contestId);
        if (votes.isEmpty()) return "No Hacan Trade Convoys votes recorded.";
        Map<String, Integer> countsByOption = new HashMap<>();
        for (CombatReplayHacanTradeConvoysVoteEntity vote : votes) {
            countsByOption.merge(optionLabel(vote), 1, Integer::sum);
        }
        List<String> lines = countsByOption.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> "- " + entry.getKey() + ": `" + entry.getValue() + "`")
                .toList();
        return "Current tally:\n" + String.join("\n", lines) + "\nVotes needed to resolve: `"
                + minimumAbilityVotesToResolve() + "`";
    }

    private String optionLabel(CombatReplayHacanTradeConvoysVoteEntity vote) {
        if (vote.getTargetHouse() == null || safeInt(vote.getPredictionBonus()) <= 0) return "Do Not Use";
        return vote.getTargetHouse().displayName() + " " + vote.getPredictionBonus() + "% of next combat";
    }

    private String optionLabel(ParsedTradeConvoysButton request) {
        if (isDoNotUse(request)) return "Do Not Use";
        return request.targetHouse().displayName() + " " + request.bonusPercent() + "% of next combat";
    }

    private void postLockedTradeConvoysSummary(CombatReplayHacanTradeConvoysEntity tradeConvoys) {
        TextChannel channel = houseChannel();
        if (channel == null) return;
        if (tradeConvoys.getTargetHouse() == null || safeInt(tradeConvoys.getPredictionBonus()) <= 0) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    FactionEmojis.getFactionIcon(CombatReplayHouse.HACAN.displayName())
                            + " Hacan Delegation brokered no Trade Convoys for this combat.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                channel,
                "## "
                        + FactionEmojis.getFactionIcon(CombatReplayHouse.HACAN.displayName())
                        + " Hacan Delegation Trade Convoys Locked\n"
                        + FactionEmojis.getFactionIcon(
                                tradeConvoys.getTargetHouse().displayName())
                        + " Hacan sends `"
                        + safeInt(tradeConvoys.getFavorCost())
                        + " Favor` to " + tradeConvoys.getTargetHouse().displayName()
                        + " Delegation and will gain `"
                        + safeInt(tradeConvoys.getPredictionBonus())
                        + "%` of that Delegation's earned points in the next combat.");
    }

    private TradeConvoys toTradeConvoys(CombatReplayHacanTradeConvoysEntity entity) {
        if (entity == null || entity.getTargetHouse() == null || safeInt(entity.getPredictionBonus()) <= 0) {
            return TradeConvoys.none();
        }
        return new TradeConvoys(
                entity.getContestId(),
                entity.getTargetHouse(),
                safeInt(entity.getFavorCost()),
                safeInt(entity.getPredictionBonus()));
    }

    private boolean noScoredContestBetween(Long sourceContestId, Long currentContestId, Set<Long> scoredContestIds) {
        for (Long scoredContestId : scoredContestIds) {
            if (scoredContestId > sourceContestId && scoredContestId < currentContestId) return false;
        }
        return true;
    }

    private long distinctVoterCount(List<CombatReplayHacanTradeConvoysVoteEntity> votes) {
        return CombatReplayVoteTally.distinctVoterCount(
                votes, CombatReplayHacanTradeConvoysVoteEntity::getDiscordUserId);
    }

    private int minimumAbilityVotesToResolve() {
        return Math.max(1, settings.getHouseAbilities().getMinimumAbilityVotesToResolve());
    }

    private int effectiveFavorCost(int favorCost) {
        return favorCost;
    }

    private boolean isDoNotUse(ParsedTradeConvoysButton request) {
        return request.targetHouse() == CombatReplayHouse.HACAN
                && request.favorCost() == 0
                && request.bonusPercent() == 0;
    }

    private TextChannel houseChannel() {
        Guild guild = JdaService.guildPrimary;
        if (guild == null) return null;
        TextChannel channel = guild.getTextChannelsByName(CombatReplayHouse.HACAN.channelName(), true).stream()
                .findFirst()
                .orElse(null);
        if (channel == null) {
            BotLogger.warning("Lazax house channel not found: " + CombatReplayHouse.HACAN.channelName());
        }
        return channel;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static String formatButtonId(
            Long contestId, CombatReplayHouse targetHouse, int favorCost, int predictionBonus) {
        return HACAN_TRADE_CONVOYS_PREFIX
                + contestId
                + DELIMITER
                + targetHouse.name()
                + DELIMITER
                + favorCost
                + DELIMITER
                + predictionBonus;
    }

    public static ParsedTradeConvoysButton parseButtonId(String buttonId) {
        if (buttonId == null || !buttonId.startsWith(HACAN_TRADE_CONVOYS_PREFIX)) {
            throw new IllegalArgumentException("Unknown Hacan Trade Convoys button id: " + buttonId);
        }
        String encoded = buttonId.substring(HACAN_TRADE_CONVOYS_PREFIX.length());
        String[] parts = encoded.split(DELIMITER, 4);
        if (parts.length != 4)
            throw new IllegalArgumentException("Malformed Hacan Trade Convoys button id: " + buttonId);
        return new ParsedTradeConvoysButton(
                Long.parseLong(parts[0]),
                CombatReplayHouse.valueOf(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]));
    }

    public record ParsedTradeConvoysButton(
            Long contestId, CombatReplayHouse targetHouse, int favorCost, int bonusPercent) {}

    public record TradeConvoys(Long sourceContestId, CombatReplayHouse targetHouse, int favorCost, int bonusPercent) {
        public static TradeConvoys none() {
            return new TradeConvoys(null, null, 0, 0);
        }

        public boolean active() {
            return sourceContestId != null && targetHouse != null && bonusPercent > 0;
        }
    }

    private record TradeConvoysTier(int favorCost, int bonusPercent) {}

    private record TradeConvoysOption(CombatReplayHouse targetHouse, int favorCost, int bonusPercent) {}

    private record TradeConvoysTally(
            CombatReplayHouse targetHouse,
            int favorCost,
            int bonusPercent,
            String discordUserId,
            String discordUserName,
            int voteCount) {}
}
