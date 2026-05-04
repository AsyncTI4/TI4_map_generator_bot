package ti4.contest.replay.house.hacan;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.contest.replay.buttons.CombatSideBetButtonIds.Parsed;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatContestSideBetEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.entities.CombatReplayHacanMarketCompactDecisionEntity;
import ti4.contest.replay.entities.CombatReplayHacanSubsidyEntity;
import ti4.contest.replay.entities.CombatReplayHacanSubsidyVoteEntity;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayHacanMarketCompactDecisionRepository;
import ti4.contest.replay.repository.CombatReplayHacanSubsidyRepository;
import ti4.contest.replay.repository.CombatReplayHacanSubsidyVoteRepository;
import ti4.contest.replay.service.CombatReplayAbilityWindowText;
import ti4.contest.replay.service.CombatReplayHouseService;
import ti4.contest.replay.service.CombatReplayInteractionResult;
import ti4.contest.replay.service.CombatReplaySideBetPayoutService;
import ti4.contest.replay.service.CombatReplayVoteTally;
import ti4.contest.replay.service.CombatSideBetAvailabilityService;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@Service
@RequiredArgsConstructor
public class CombatReplayHacanMarketCompactService {

    public static final String BUTTON_PREFIX = "combatReplayHacanSubsidy_";
    public static final String DO_NOT_USE_BUTTON = BUTTON_PREFIX + "doNotUse_";

    private static final String DELIMITER = "~";
    private static final String DO_NOT_USE_TARGET = "__DO_NOT_USE__";

    private final CombatContestSettings settings;
    private final CombatReplayContestRepository replayContestRepository;
    private final CombatCandidateRepository candidateRepository;
    private final CombatReplayHacanSubsidyVoteRepository voteRepository;
    private final CombatReplayHacanSubsidyRepository marketRepository;
    private final CombatReplayHacanMarketCompactDecisionRepository decisionRepository;
    private final CombatReplayHouseService houseService;
    private final CombatReplaySideBetPayoutService payoutService;
    private final CombatSideBetAvailabilityService availabilityService;

    public void postVotingButtonsIfNeeded(
            Game game, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        if (!shouldOfferVoting(candidate)) return;

        TextChannel channel = houseChannel();
        if (channel == null) return;

        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                "## " + FactionEmojis.getFactionIcon(CombatReplayHouse.HACAN.displayName())
                        + " Hacan Delegation Market Compact\n"
                        + CombatReplayAbilityWindowText.votesLockLine(discussionWindowSeconds())
                        + "\n"
                        + marketCompactSummaryLine(),
                List.of(Buttons.red(DO_NOT_USE_BUTTON + contest.getId(), "Do Not Use Market Compact")));
        postButtonsForFaction(channel, game, contest, candidate, candidate.getAttackerFaction());
        postButtonsForFaction(channel, game, contest, candidate, candidate.getDefenderFaction());
    }

    public boolean shouldOfferVoting(CombatCandidateEntity candidate) {
        return enabled()
                && discussionWindowSeconds() > 0
                && candidate != null
                && Boolean.TRUE.equals(candidate.getSideBetCompatible());
    }

    public String marketCompactSummaryLine() {
        return "-# Market Compact: mark up to " + maxMarkedBets()
                + " side bets before discussion closes. Hacan Delegation gains `+"
                + marketMakerPointsPerBet()
                + "` point for each non-Hacan player who takes a marked bet, and `+"
                + favorOnHit()
                + " Favor` each time a marked bet lands.";
    }

    public void sendEphemeralVotingControls(
            ButtonInteractionEvent event,
            Game game,
            CombatReplayContestEntity contest,
            CombatCandidateEntity candidate) {
        if (!shouldOfferVoting(candidate)) return;
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(
                event,
                "## "
                        + FactionEmojis.getFactionIcon(CombatReplayHouse.HACAN.displayName())
                        + " Hacan Delegation Market Compact\n"
                        + CombatReplayAbilityWindowText.votesLockLine(discussionWindowSeconds())
                        + "\n"
                        + renderUserVoteSummary(contest.getId(), event.getUser().getId(), "Current vote:"),
                List.of(Buttons.red(DO_NOT_USE_BUTTON + contest.getId(), "Do Not Use Market Compact")));
        sendEphemeralButtonsForFaction(event, game, contest, candidate, candidate.getAttackerFaction());
        sendEphemeralButtonsForFaction(event, game, contest, candidate, candidate.getDefenderFaction());
    }

    @Transactional
    public synchronized CombatReplayInteractionResult recordMarketVote(
            ButtonInteractionEvent event, Long contestId, CombatSideBetType betType, String targetFaction) {
        if (!enabled()) return CombatReplayInteractionResult.rejected("Hacan Delegation's Market Compact is disabled.");
        if (!userHasHacan(event.getUser().getId())) {
            return CombatReplayInteractionResult.rejected("Only Hacan Delegation may broker the battle market.");
        }

        CombatReplayContestEntity contest =
                replayContestRepository.findById(contestId).orElse(null);
        if (!votingOpen(contest))
            return CombatReplayInteractionResult.rejected("The Hacan Market Compact window is closed.");
        if (isDoNotUseVote(betType, targetFaction)) {
            return recordDoNotUseVote(event, contestId);
        }

        CombatCandidateEntity candidate = loadCandidate(contest);
        if (!isAvailableForCandidate(candidate, betType, targetFaction)) {
            return CombatReplayInteractionResult.rejected("That side bet is not available for this combat.");
        }

        String userId = event.getUser().getId();
        String userName = event.getUser().getEffectiveName();
        var existing = voteRepository.findByContestIdAndDiscordUserIdAndBetTypeAndTargetFaction(
                contestId, userId, betType, targetFaction);
        if (existing.isPresent()) {
            voteRepository.delete(existing.get());
            return CombatReplayInteractionResult.accepted(
                    renderUserVoteSummary(contestId, userId, "Withdrew market backing."));
        }

        List<CombatReplayHacanSubsidyVoteEntity> userVotes =
                voteRepository.findByContestIdAndDiscordUserId(contestId, userId);
        userVotes.stream().filter(this::isDoNotUseVote).forEach(voteRepository::delete);
        userVotes = userVotes.stream().filter(vote -> !isDoNotUseVote(vote)).toList();
        int maxVotes = maxMarkedBets();
        if (userVotes.size() >= maxVotes) {
            return CombatReplayInteractionResult.rejected(
                    "You already backed " + maxVotes + " markets. Click one to withdraw it first.");
        }

        CombatReplayHacanSubsidyVoteEntity vote = new CombatReplayHacanSubsidyVoteEntity();
        vote.setContestId(contestId);
        vote.setDiscordUserId(userId);
        vote.setDiscordUserName(StringUtils.defaultIfBlank(userName, "Unknown User"));
        vote.setBetType(betType);
        vote.setTargetFaction(targetFaction);
        vote.setVotedAt(LocalDateTime.now());
        voteRepository.save(vote);
        return CombatReplayInteractionResult.accepted(
                renderUserVoteSummary(contestId, userId, "Added market backing."));
    }

    private CombatReplayInteractionResult recordDoNotUseVote(ButtonInteractionEvent event, Long contestId) {
        String userId = event.getUser().getId();
        String userName = event.getUser().getEffectiveName();
        var existing = voteRepository.findByContestIdAndDiscordUserIdAndBetTypeAndTargetFaction(
                contestId, userId, doNotUseBetType(), DO_NOT_USE_TARGET);
        if (existing.isPresent()) {
            voteRepository.delete(existing.get());
            return CombatReplayInteractionResult.accepted(
                    renderUserVoteSummary(contestId, userId, "Withdrew Do Not Use."));
        }

        voteRepository.deleteAll(voteRepository.findByContestIdAndDiscordUserId(contestId, userId));
        CombatReplayHacanSubsidyVoteEntity vote = new CombatReplayHacanSubsidyVoteEntity();
        vote.setContestId(contestId);
        vote.setDiscordUserId(userId);
        vote.setDiscordUserName(StringUtils.defaultIfBlank(userName, "Unknown User"));
        vote.setBetType(doNotUseBetType());
        vote.setTargetFaction(DO_NOT_USE_TARGET);
        vote.setVotedAt(LocalDateTime.now());
        voteRepository.save(vote);
        return CombatReplayInteractionResult.accepted(
                renderUserVoteSummary(contestId, userId, "Voted to not use Market Compact."));
    }

    @Transactional
    public synchronized List<MarkedSideBet> lockMarketsIfNeeded(
            Game game, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        if (!enabled()) return List.of();

        List<CombatReplayHacanSubsidyEntity> existingMarkets = marketRepository.findByContestId(contest.getId());
        if (decisionRepository.findByContestId(contest.getId()).isPresent() || !existingMarkets.isEmpty()) {
            return toMarkedBets(existingMarkets);
        }

        List<CombatReplayHacanSubsidyVoteEntity> votes = voteRepository.findByContestId(contest.getId());
        if (distinctVoterCount(votes) < minimumAbilityVotesToResolve()) {
            lockNoMarkets(contest, game);
            return List.of();
        }
        List<VoteTally> tallies = tallyVotes(game, contest, candidate, votes);
        if (!tallies.isEmpty() && tallies.getFirst().isDoNotUse()) {
            lockNoMarkets(contest, game);
            return List.of();
        }
        if (tallies.isEmpty()) {
            lockNoMarkets(contest, game);
            return List.of();
        }
        int maxMarkets = maxMarkedBets();
        LocalDateTime now = LocalDateTime.now();
        List<CombatReplayHacanSubsidyEntity> selected = new ArrayList<>();
        for (int index = 0; index < Math.min(maxMarkets, tallies.size()); index++) {
            VoteTally tally = tallies.get(index);
            if (tally.isDoNotUse()) continue;
            CombatReplayHacanSubsidyEntity market = new CombatReplayHacanSubsidyEntity();
            market.setContestId(contest.getId());
            market.setBetType(tally.betType());
            market.setTargetFaction(tally.targetFaction());
            market.setVoteCount(tally.voteCount());
            market.setSelectedAt(now);
            selected.add(market);
        }
        marketRepository.saveAll(selected);
        saveDecision(contest.getId());
        postLockedMarketSummary(game, selected);
        return toMarkedBets(selected);
    }

    private void lockNoMarkets(CombatReplayContestEntity contest, Game game) {
        saveDecision(contest.getId());
        postLockedMarketSummary(game, List.of());
    }

    public List<MarkedSideBet> markedBets(Long contestId) {
        if (!enabled() || contestId == null) return List.of();
        return toMarkedBets(marketRepository.findByContestId(contestId));
    }

    public boolean isMarked(Long contestId, CombatSideBetType betType, String targetFaction) {
        MarkedSideBet target = new MarkedSideBet(betType, normalizeFaction(targetFaction));
        return markedBets(contestId).contains(target);
    }

    public int marketMakerPoints(Long contestId, List<CombatContestSideBetEntity> sideBets) {
        if (!enabled() || contestId == null || sideBets == null || sideBets.isEmpty()) return 0;
        Set<MarkedSideBet> markedBets = new LinkedHashSet<>(markedBets(contestId));
        if (markedBets.isEmpty()) return 0;

        int takenMarkedSideBets = 0;
        for (CombatContestSideBetEntity sideBet : sideBets) {
            if (houseService.houseForUser(sideBet.getDiscordUserId()) == CombatReplayHouse.HACAN) continue;
            if (markedBets.contains(
                    new MarkedSideBet(sideBet.getBetType(), normalizeFaction(sideBet.getTargetFaction())))) {
                takenMarkedSideBets++;
            }
        }
        return takenMarkedSideBets * marketMakerPointsPerBet();
    }

    public int favorOnHit() {
        return enabled() ? settings.getHouseAbilities().getHacan().getSubsidyFavorOnHit() : 0;
    }

    @Transactional
    public void clear(Long contestId) {
        if (contestId == null) return;
        decisionRepository.deleteByContestId(contestId);
        marketRepository.deleteByContestId(contestId);
        voteRepository.deleteByContestId(contestId);
    }

    private void saveDecision(Long contestId) {
        CombatReplayHacanMarketCompactDecisionEntity decision = new CombatReplayHacanMarketCompactDecisionEntity();
        decision.setContestId(contestId);
        decision.setDecidedAt(LocalDateTime.now());
        decisionRepository.save(decision);
    }

    private void postButtonsForFaction(
            TextChannel channel,
            Game game,
            CombatReplayContestEntity contest,
            CombatCandidateEntity candidate,
            String faction) {
        List<Button> buttons = buttonsForFaction(game, contest, candidate, faction);
        if (buttons.isEmpty()) return;
        MessageHelper.sendMessageToChannelWithButtons(channel, factionSectionTitle(game, faction), buttons);
    }

    private void sendEphemeralButtonsForFaction(
            ButtonInteractionEvent event,
            Game game,
            CombatReplayContestEntity contest,
            CombatCandidateEntity candidate,
            String faction) {
        List<Button> buttons = buttonsForFaction(game, contest, candidate, faction);
        if (buttons.isEmpty()) return;
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, factionSectionTitle(game, faction), buttons);
    }

    private List<Button> buttonsForFaction(
            Game game, CombatReplayContestEntity contest, CombatCandidateEntity candidate, String faction) {
        List<Button> buttons = new ArrayList<>();
        for (CombatSideBetType type : availabilityService.availableTypes(candidate, faction)) {
            int points = payoutService.offeredPayout(contest, candidate, type, faction);
            buttons.add(Buttons.gray(
                    formatButtonId(contest.getId(), type, faction), shortBetLabel(type) + " +" + points, type.emoji()));
        }
        return buttons;
    }

    private String shortBetLabel(CombatSideBetType type) {
        return switch (type) {
            case AFB_SKIPPED -> "AFB skips";
            case AFB_WHIFF -> "AFB whiffs";
            case ROUND_ONE_WHIFF -> "R1 whiff";
            case ROUND_ONE_SLAM -> "R1 slam";
            case MORALE_BOOST -> "Morale";
            case SHIELDS_HOLDING -> "Shields";
            case DIRECT_HIT -> "Direct Hit";
            case FIGHTER_PROTOTYPE -> "Fighter Proto";
            case WINNER_ONE_HP -> "Wins on 1 HP";
        };
    }

    private List<VoteTally> tallyVotes(
            Game game,
            CombatReplayContestEntity contest,
            CombatCandidateEntity candidate,
            List<CombatReplayHacanSubsidyVoteEntity> votes) {
        Map<MarkedSideBet, Integer> countsByBet = new HashMap<>();
        int doNotUseVotes = 0;
        for (CombatReplayHacanSubsidyVoteEntity vote : votes) {
            if (isDoNotUseVote(vote)) {
                doNotUseVotes++;
                continue;
            }
            if (!isAvailableForCandidate(candidate, vote.getBetType(), vote.getTargetFaction())) continue;
            countsByBet.merge(
                    new MarkedSideBet(vote.getBetType(), normalizeFaction(vote.getTargetFaction())), 1, Integer::sum);
        }

        List<VoteTally> tallies = new ArrayList<>();
        if (doNotUseVotes > 0) {
            tallies.add(VoteTally.doNotUse(doNotUseVotes));
        }
        for (Map.Entry<MarkedSideBet, Integer> entry : countsByBet.entrySet()) {
            MarkedSideBet bet = entry.getKey();
            int payout = payoutService.offeredPayout(contest, candidate, bet.betType(), bet.targetFaction());
            tallies.add(new VoteTally(bet.betType(), bet.targetFaction(), entry.getValue(), payout, label(game, bet)));
        }
        tallies.sort(Comparator.comparingInt(VoteTally::voteCount)
                .thenComparingInt(VoteTally::offeredPayout)
                .thenComparing(VoteTally::label, Comparator.reverseOrder())
                .reversed());
        return tallies;
    }

    private String renderUserVoteSummary(Long contestId, String userId, String header) {
        List<CombatReplayHacanSubsidyVoteEntity> votes =
                voteRepository.findByContestIdAndDiscordUserId(contestId, userId);
        if (votes.isEmpty()) return header + "\nNo markets backed.";

        StringBuilder message = new StringBuilder(header).append("\nBacked markets:");
        votes.stream()
                .sorted(Comparator.comparing(CombatReplayHacanSubsidyVoteEntity::getTargetFaction)
                        .thenComparing(vote -> vote.getBetType().label()))
                .forEach(vote -> message.append("\n- ")
                        .append(
                                isDoNotUseVote(vote)
                                        ? "Do Not Use"
                                        : StringUtils.capitalize(vote.getTargetFaction()) + " "
                                                + vote.getBetType().label()));
        return message.toString();
    }

    private void postLockedMarketSummary(Game game, List<CombatReplayHacanSubsidyEntity> selected) {
        TextChannel channel = houseChannel();
        if (channel == null) return;
        if (selected.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    FactionEmojis.getFactionIcon(CombatReplayHouse.HACAN.displayName())
                            + " Hacan Delegation brokered no Market Compact for this combat.");
            return;
        }

        StringBuilder message = new StringBuilder("## ")
                .append(FactionEmojis.getFactionIcon(CombatReplayHouse.HACAN.displayName()))
                .append(" Hacan Delegation Market Compact Locked\n");
        for (CombatReplayHacanSubsidyEntity market : selected) {
            message.append("- ")
                    .append(label(game, new MarkedSideBet(market.getBetType(), market.getTargetFaction())))
                    .append(" (`")
                    .append(market.getVoteCount())
                    .append("` votes)\n");
        }
        MessageHelper.sendMessageToChannel(channel, message.toString().trim());
    }

    private boolean votingOpen(CombatReplayContestEntity contest) {
        if (contest == null || contest.getId() == null || contest.getPostedAt() == null) return false;
        if (contest.getSideBetMarketPostedAt() != null) return false;
        if (contest.getReplayStartAt() == null || !LocalDateTime.now().isBefore(contest.getReplayStartAt()))
            return false;
        return LocalDateTime.now().isBefore(contest.getPostedAt().plusSeconds(discussionWindowSeconds()));
    }

    private boolean isAvailableForCandidate(CombatCandidateEntity candidate, CombatSideBetType type, String faction) {
        if (candidate == null || type == null || StringUtils.isBlank(faction)) return false;
        return availabilityService.isAvailable(candidate, type, faction);
    }

    private boolean userHasHacan(String discordUserId) {
        if (settings.getRuntime().isDevMode()) return true;
        return houseService.houseForUser(discordUserId) == CombatReplayHouse.HACAN;
    }

    private boolean enabled() {
        return settings.isHousesEnabled() && settings.getSideBets().isEnableSideBets();
    }

    private int maxMarkedBets() {
        return settings.getHouseAbilities().getHacan().getMaxSubsidiesPerContest();
    }

    private int marketMakerPointsPerBet() {
        return settings.getHouseAbilities().getHacan().getMarketMakerPointsPerBet();
    }

    public int discussionWindowSeconds() {
        return settings.getReplayExecution().getDiscussionWindowSeconds();
    }

    private long distinctVoterCount(List<CombatReplayHacanSubsidyVoteEntity> votes) {
        return CombatReplayVoteTally.distinctVoterCount(votes, CombatReplayHacanSubsidyVoteEntity::getDiscordUserId);
    }

    private int minimumAbilityVotesToResolve() {
        return Math.max(1, settings.getHouseAbilities().getMinimumAbilityVotesToResolve());
    }

    private String factionSectionTitle(Game game, String faction) {
        String emoji = getFactionEmoji(game, faction);
        String label = buttonFactionDisplayName(game, faction);
        if (StringUtils.isBlank(emoji)) return "### " + label;
        return "### " + emoji + " " + label;
    }

    private String buttonFactionDisplayName(Game game, String faction) {
        if (StringUtils.isBlank(faction)) return "?";
        if (game == null) return faction;
        Player target = game.getPlayerFromColorOrFaction(faction);
        if (target == null) return faction;
        return target.getFactionModel() == null
                ? target.getFaction()
                : target.getFactionModel().getFactionName();
    }

    private String getFactionEmoji(Game game, String faction) {
        if (game == null || StringUtils.isBlank(faction)) return "";
        Player target = game.getPlayerFromColorOrFaction(faction);
        return target == null ? "" : target.getFactionEmoji();
    }

    private String label(Game game, MarkedSideBet bet) {
        Player player = game == null ? null : game.getPlayerFromColorOrFaction(bet.targetFaction());
        String faction = player == null ? StringUtils.capitalize(bet.targetFaction()) : player.getFaction();
        return faction + " " + bet.betType().label();
    }

    private CombatCandidateEntity loadCandidate(CombatReplayContestEntity contest) {
        if (contest == null || contest.getCandidateId() == null) return null;
        return candidateRepository.findById(contest.getCandidateId()).orElse(null);
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

    private List<MarkedSideBet> toMarkedBets(List<CombatReplayHacanSubsidyEntity> markets) {
        return markets.stream()
                .filter(market -> !isDoNotUseVote(market.getBetType(), market.getTargetFaction()))
                .map(market -> new MarkedSideBet(market.getBetType(), normalizeFaction(market.getTargetFaction())))
                .toList();
    }

    private static String formatButtonId(Long contestId, CombatSideBetType type, String faction) {
        return BUTTON_PREFIX + contestId + DELIMITER + type.key() + DELIMITER + faction;
    }

    private boolean isDoNotUseVote(CombatReplayHacanSubsidyVoteEntity vote) {
        return vote != null && isDoNotUseVote(vote.getBetType(), vote.getTargetFaction());
    }

    private static boolean isDoNotUseVote(CombatSideBetType type, String targetFaction) {
        return type == doNotUseBetType() && DO_NOT_USE_TARGET.equals(targetFaction);
    }

    private static CombatSideBetType doNotUseBetType() {
        return CombatSideBetType.ROUND_ONE_WHIFF;
    }

    private static String normalizeFaction(String faction) {
        return StringUtils.defaultString(faction).trim().toLowerCase();
    }

    public static Parsed parseButtonId(String buttonId) {
        if (buttonId == null || !buttonId.startsWith(BUTTON_PREFIX)) {
            throw new IllegalArgumentException("Unknown Hacan market button id: " + buttonId);
        }
        if (buttonId.startsWith(DO_NOT_USE_BUTTON)) {
            return new Parsed(
                    Long.parseLong(buttonId.substring(DO_NOT_USE_BUTTON.length())),
                    doNotUseBetType(),
                    DO_NOT_USE_TARGET);
        }

        String encoded = buttonId.substring(BUTTON_PREFIX.length());
        String[] parts = encoded.split(DELIMITER, 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed Hacan market button id: " + buttonId);
        }
        return new Parsed(Long.parseLong(parts[0]), CombatSideBetType.fromKey(parts[1]), parts[2]);
    }

    public record MarkedSideBet(CombatSideBetType betType, String targetFaction) {}

    private record VoteTally(
            CombatSideBetType betType, String targetFaction, int voteCount, int offeredPayout, String label) {
        private static VoteTally doNotUse(int voteCount) {
            return new VoteTally(doNotUseBetType(), DO_NOT_USE_TARGET, voteCount, 0, "Do Not Use");
        }

        private boolean isDoNotUse() {
            return isDoNotUseVote(betType, targetFaction);
        }
    }
}
