package ti4.spring.service.statistics.matchmaking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedPlayer;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameLaunchPostService;
import ti4.service.game.CreateGameService;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;
import ti4.spring.context.SpringContext;
import ti4.spring.service.statistics.UserGameInfoService;

@AllArgsConstructor
@Service
public class MatchmakerService {

    private static final int DEFAULT_MAX_QUEUE_TIME_HOURS = 8;
    private static final int NUMBER_OF_ACTIVE_HOUR_BUCKETS = 6;
    private static final int ACTIVE_HOUR_BUCKET_SIZE = 4;
    private static final int ACTIVE_HOUR_BUCKET_MATCH_THRESHOLD = 3;
    private static final long ACTIVE_HOUR_SHARED_BUCKET_REQUIREMENT = 3;
    private static final double SIMILAR_SKILL_DIFFERENCE_THRESHOLD = 2.0;
    private static final double RELAXED_SIMILAR_SKILL_DIFFERENCE_THRESHOLD = 4.0;
    // Fraction of a party's max queue time after which restrictions are relaxed.
    private static final double QUEUE_TIME_RELAX_FRACTION = 0.5;
    private static final int NEW_PLAYER_GAME_THRESHOLD = 3;
    private static final BigDecimal NEW_PLAYER_MATCHMAKING_RATING = BigDecimal.valueOf(20.0);

    private final MatchmakingQueuePartyRepository partyRepository;
    private final MatchmakingQueueMemberRepository memberRepository;

    public static boolean isQueueingDisabled() {
        return DatabasePersistenceGate.isDisabled();
    }

    /** Whether the user is in a party that has been queued for a game. */
    public boolean isUserQueued(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return false;
        return memberRepository
                .findByUserId(userId)
                .flatMap(member -> partyRepository.findById(member.getPartyId()))
                .map(MatchmakingQueueParty::isQueued)
                .orElse(false);
    }

    /** Whether the user belongs to any party (formed or queued). */
    public boolean isUserInParty(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return false;
        return memberRepository.existsByUserId(userId);
    }

    /** The user ids of the user's party, or just the user themselves if they aren't in a party. */
    public List<String> partyMemberIds(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return List.of(userId);
        return memberRepository
                .findByUserId(userId)
                .map(member -> memberRepository.findAllByPartyId(member.getPartyId()).stream()
                        .map(MatchmakingQueueMember::getUserId)
                        .toList())
                .filter(ids -> !ids.isEmpty())
                .orElse(List.of(userId));
    }

    /**
     * Create an unqueued group containing the creator and the selected members.
     *
     * @return an error message if the group can't be formed (a member is already in a party, or two members avoid each
     *     other); empty on success.
     */
    @Transactional
    public Optional<String> formGroup(String creatorId, List<String> memberIds) {
        if (DatabasePersistenceGate.isDisabled()) return Optional.of("Queueing is currently disabled.");

        List<String> allIds = distinctMembers(creatorId, memberIds);

        List<String> alreadyGrouped =
                allIds.stream().filter(memberRepository::existsByUserId).toList();
        if (!alreadyGrouped.isEmpty()) {
            return Optional.of("These players are already in a group or the queue and must leave first: "
                    + alreadyGrouped.stream().map(id -> "<@" + id + ">").collect(Collectors.joining(", ")));
        }

        Optional<String> avoidConflict = firstAvoidConflict(allIds);
        if (avoidConflict.isPresent()) return avoidConflict;

        MatchmakingQueueParty party = new MatchmakingQueueParty();
        party.setQueued(false);
        long partyId = partyRepository.save(party).getId();

        List<MatchmakingQueueMember> members = allIds.stream()
                .map(id -> {
                    MatchmakingQueueMember member = new MatchmakingQueueMember();
                    member.setUserId(id);
                    member.setPartyId(partyId);
                    return member;
                })
                .toList();
        memberRepository.saveAll(members);
        return Optional.empty();
    }

    /**
     * Queue the user. If they are in a formed group it becomes queued under the queuer's preferences; otherwise a solo
     * party is created. The clicker's preferences must already be saved to their {@link UserSettings}.
     *
     * @return an error message if the party can't be queued; empty on success.
     */
    @Transactional
    public Optional<String> queue(String queuerId) {
        if (DatabasePersistenceGate.isDisabled()) return Optional.of("Queueing is currently disabled.");

        Optional<MatchmakingQueueMember> memberOpt = memberRepository.findByUserId(queuerId);
        if (memberOpt.isPresent()) {
            MatchmakingQueueParty party =
                    partyRepository.findById(memberOpt.get().getPartyId()).orElse(null);
            if (party == null) {
                memberRepository.delete(memberOpt.get()); // orphaned membership; fall through to a solo queue
            } else if (party.isQueued()) {
                return Optional.of("You are already queued for a game.");
            } else {
                List<String> otherIds = memberRepository.findAllByPartyId(party.getId()).stream()
                        .map(MatchmakingQueueMember::getUserId)
                        .filter(id -> !id.equals(queuerId))
                        .toList();
                Optional<String> error = validateParty(queuerId, otherIds);
                if (error.isPresent()) return error;

                party.setLeaderId(queuerId);
                party.setQueued(true);
                party.setQueuedAt(Instant.now());
                partyRepository.save(party);
                return Optional.empty();
            }
        }

        Optional<String> error = validateParty(queuerId, List.of());
        if (error.isPresent()) return error;

        MatchmakingQueueParty party = new MatchmakingQueueParty();
        party.setQueued(true);
        party.setQueuedAt(Instant.now());
        party.setLeaderId(queuerId);
        long partyId = partyRepository.save(party).getId();

        MatchmakingQueueMember member = new MatchmakingQueueMember();
        member.setUserId(queuerId);
        member.setPartyId(partyId);
        memberRepository.save(member);
        return Optional.empty();
    }

    /** Remove the user's whole party (formed or queued) from the queue and notify the other members. */
    @Transactional
    public boolean leaveQueue(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return false;
        Optional<MatchmakingQueueMember> memberOpt = memberRepository.findByUserId(userId);
        if (memberOpt.isEmpty()) return false;

        long partyId = memberOpt.get().getPartyId();
        List<MatchmakingQueueMember> partyMembers = memberRepository.findAllByPartyId(partyId);
        memberRepository.deleteAllByPartyIdIn(List.of(partyId));
        partyRepository.deleteById(partyId);
        notifyPartyLeft(partyMembers, userId);
        return true;
    }

    private void notifyPartyLeft(List<MatchmakingQueueMember> partyMembers, String leaverId) {
        if (partyMembers.size() <= 1) return;
        String message = "Your matchmaking group was removed from the queue because <@" + leaverId + "> left it.";
        for (MatchmakingQueueMember member : partyMembers) {
            if (member.getUserId().equals(leaverId)) continue;
            User user = JdaService.jda.getUserById(member.getUserId());
            if (user == null) continue;
            MessageHelper.sendMessageToUser(message, user);
        }
    }

    private Optional<String> firstAvoidConflict(List<String> userIds) {
        Map<String, List<String>> avoidById = new HashMap<>();
        for (String id : userIds) {
            avoidById.put(id, UserSettingsManager.get(id).getMatchmakingAvoidList());
        }
        for (int i = 0; i < userIds.size(); i++) {
            for (int j = i + 1; j < userIds.size(); j++) {
                String a = userIds.get(i);
                String b = userIds.get(j);
                if (avoidById.get(a).contains(b) || avoidById.get(b).contains(a)) {
                    return Optional.of(
                            "<@" + a + "> and <@" + b + "> have each other on an avoid list and can't be grouped.");
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Validate that a party can be queued under the queuer's preferences: it must fit a chosen game size, every member
     * must be eligible for the chosen pace and under their game limit, and every pair of members must be compatible
     * under the queuer's restrictions.
     *
     * @return an error message describing the first problem found, or empty if the party is valid.
     */
    public Optional<String> validateParty(String leaderId, List<String> memberIds) {
        List<String> allIds = distinctMembers(leaderId, memberIds);
        UserSettings leaderSettings = UserSettingsManager.get(leaderId);

        int largestChosenPlayerCount = leaderSettings.getMatchmakingPlayerCounts().stream()
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
        if (allIds.size() > largestChosenPlayerCount) {
            return Optional.of("Your group has " + allIds.size()
                    + " players, which is larger than the biggest player count you selected ("
                    + largestChosenPlayerCount + "). Pick fewer players or allow a larger game size.");
        }

        Optional<String> paceProblem = validatePaceEligibility(allIds, leaderSettings);
        if (paceProblem.isPresent()) return paceProblem;

        Optional<String> gameLimitProblem = validateGameLimits(allIds);
        if (gameLimitProblem.isPresent()) return gameLimitProblem;

        Map<String, PlayerMatchData> dataById = buildValidationData(allIds, leaderSettings);
        for (int i = 0; i < allIds.size(); i++) {
            for (int j = i + 1; j < allIds.size(); j++) {
                Optional<String> reason =
                        incompatibilityReason(dataById.get(allIds.get(i)), dataById.get(allIds.get(j)), false);
                if (reason.isPresent()) return reason;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> validatePaceEligibility(List<String> userIds, UserSettings leaderSettings) {
        UserGameInfoService userGameInfoService = UserGameInfoService.get();
        for (String pace : leaderSettings.getMatchmakingPaces()) {
            Integer requirement = MatchmakingOptions.PACE_RESTRICTION_TO_GAME_DAYS_TO_COMPLETE_REQUIREMENT.get(pace);
            if (requirement == null) continue;
            for (String id : userIds) {
                if (!userGameInfoService.hasCompletedGameInDays(id, requirement)) {
                    return Optional.of("<@" + id + "> isn't eligible for the \"" + pace
                            + "\" pace (it requires a game completed within " + requirement + " days)."
                            + " Deselect the \"" + pace + "\" pace to queue with that player.");
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> validateGameLimits(List<String> userIds) {
        for (String id : userIds) {
            ManagedPlayer managedPlayer = GameManager.getManagedPlayer(id);
            if (managedPlayer == null) continue;
            if (UserGameInfoService.isOverStandardGameLimit(managedPlayer)) {
                return Optional.of("<@" + id + "> is at their game limit and can't join more games right now.");
            }
            UserSettings ownSettings = UserSettingsManager.get(id);
            int ongoing = UserGameInfoService.countOngoingGamesThatAffectJoinLimit(managedPlayer);
            if (ownSettings.getGameLimit() > 0 && ongoing >= ownSettings.getGameLimit()) {
                return Optional.of("<@" + id + "> is under a personal game limit and can't join more games right now.");
            }
        }
        return Optional.empty();
    }

    private static List<String> distinctMembers(String leaderId, List<String> memberIds) {
        List<String> allIds = new ArrayList<>();
        allIds.add(leaderId);
        for (String id : memberIds) {
            if (!allIds.contains(id)) allIds.add(id);
        }
        return allIds;
    }

    private static int getHours(String maxQueueTime) {
        if (maxQueueTime == null) return DEFAULT_MAX_QUEUE_TIME_HOURS;
        return MatchmakingOptions.MAX_QUEUE_TIME_OPTIONS_TO_HOURS.getOrDefault(
                maxQueueTime.trim(), DEFAULT_MAX_QUEUE_TIME_HOURS);
    }

    @Transactional
    public void processQueue() {
        if (DatabasePersistenceGate.isDisabled()) return;

        List<QueuedParty> active = removeExpiredParties(loadQueuedParties(), Instant.now());
        Map<MatchmakingQueueMember, PlayerMatchData> matchData = buildMatchData(active);

        List<MatchedGame> gamesToCreate = new ArrayList<>();
        Set<MatchmakingQueueMember> playersAddedToGames = new HashSet<>();

        for (String playerCountOption : MatchmakingOptions.getPlayerCountOptionsDescending()) {
            for (String victoryPointGoalOption : MatchmakingOptions.getShuffledVictoryPointOptions()) {
                for (String expansionOption : MatchmakingOptions.getShuffledExpansionsOptions()) {
                    for (String paceOption : MatchmakingOptions.getShuffledPaceRestrictions()) {
                        matchAndCollect(
                                active,
                                playersAddedToGames,
                                gamesToCreate,
                                playerCountOption,
                                victoryPointGoalOption,
                                expansionOption,
                                paceOption,
                                matchData);
                    }
                }
            }
        }

        if (!gamesToCreate.isEmpty()) {
            List<Long> matchedPartyIds = gamesToCreate.stream()
                    .flatMap(game -> game.parties().stream())
                    .map(MatchmakingQueueParty::getId)
                    .toList();
            memberRepository.deleteAllByPartyIdIn(matchedPartyIds);
            partyRepository.deleteAllById(matchedPartyIds);
        }

        postMatchedGroupsToMakingNewGamesForum(gamesToCreate);
    }

    private List<QueuedParty> loadQueuedParties() {
        List<MatchmakingQueueParty> parties = partyRepository.findAllByQueuedTrueOrderByQueuedAtAsc();
        if (parties.isEmpty()) return List.of();

        List<Long> partyIds = parties.stream().map(MatchmakingQueueParty::getId).toList();
        Map<Long, List<MatchmakingQueueMember>> membersByParty = memberRepository.findAllByPartyIdIn(partyIds).stream()
                .collect(Collectors.groupingBy(MatchmakingQueueMember::getPartyId));

        List<QueuedParty> queuedParties = new ArrayList<>();
        for (MatchmakingQueueParty party : parties) {
            List<MatchmakingQueueMember> members = membersByParty.getOrDefault(party.getId(), List.of());
            if (members.isEmpty()) continue;
            queuedParties.add(new QueuedParty(party, members, UserSettingsManager.get(party.getLeaderId())));
        }
        return queuedParties;
    }

    /**
     * Greedily fills games for one (playerCount, victoryPoint, expansion, pace) combination. Each party is added or
     * skipped as a whole; solo players are parties of one.
     */
    private void matchAndCollect(
            List<QueuedParty> parties,
            Set<MatchmakingQueueMember> playersAddedToGames,
            List<MatchedGame> gamesToCreate,
            String playerCountOption,
            String victoryPointGoalOption,
            String expansionOption,
            String paceOption,
            Map<MatchmakingQueueMember, PlayerMatchData> matchData) {
        int playerCount = Integer.parseInt(playerCountOption);

        List<QueuedParty> remaining = parties.stream()
                .filter(party -> party.members().stream().noneMatch(playersAddedToGames::contains))
                .filter(party -> party.size() <= playerCount)
                .filter(party -> {
                    UserSettings settings = party.leaderSettings();
                    return settings.getMatchmakingPlayerCounts().contains(playerCountOption)
                            && settings.getMatchmakingVictoryPointGoals().contains(victoryPointGoalOption)
                            && settings.getMatchmakingExpansions().contains(expansionOption)
                            && settings.getMatchmakingPaces().contains(paceOption);
                })
                .sorted(Comparator.comparing((QueuedParty party) ->
                                getHours(party.leaderSettings().getMatchmakingMaxQueueTime()))
                        .reversed()
                        .thenComparing(party -> party.party().getQueuedAt()))
                .collect(Collectors.toCollection(ArrayList::new));

        // Greedy grouping: seed from the front (longest queue time, then longest-waiting first),
        // then fill the game with the first compatible parties found.
        while (totalPlayers(remaining) >= playerCount) {
            QueuedParty seed = remaining.getFirst();
            List<MatchmakingQueueMember> group = new ArrayList<>(seed.members());
            List<QueuedParty> groupParties = new ArrayList<>();
            groupParties.add(seed);

            for (QueuedParty party : remaining) {
                if (groupParties.contains(party)) continue;
                if (group.size() == playerCount) break;
                if (group.size() + party.size() > playerCount) continue;
                if (partyCompatibleWithGroup(party, group, matchData)) {
                    group.addAll(party.members());
                    groupParties.add(party);
                }
            }

            if (group.size() == playerCount) {
                List<String> restrictions = groupParties.stream()
                        .flatMap(party -> party.leaderSettings().getMatchmakingRestrictions().stream())
                        .distinct()
                        .sorted()
                        .toList();
                gamesToCreate.add(new MatchedGame(
                        new ArrayList<>(group),
                        new ArrayList<>(
                                groupParties.stream().map(QueuedParty::party).toList()),
                        playerCountOption,
                        victoryPointGoalOption,
                        expansionOption,
                        paceOption,
                        restrictions));
                playersAddedToGames.addAll(group);
                remaining.removeAll(groupParties);
            } else {
                // This party can't be completed into a full game right now; skip its seed.
                remaining.remove(seed);
            }
        }
    }

    private boolean partyCompatibleWithGroup(
            QueuedParty party,
            List<MatchmakingQueueMember> group,
            Map<MatchmakingQueueMember, PlayerMatchData> matchData) {
        for (MatchmakingQueueMember newMember : party.members()) {
            for (MatchmakingQueueMember existing : group) {
                PlayerMatchData a = matchData.get(existing);
                PlayerMatchData b = matchData.get(newMember);
                boolean relaxed = a.halfQueueTimePassed() || b.halfQueueTimePassed();
                if (incompatibilityReason(a, b, relaxed).isPresent()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int totalPlayers(List<QueuedParty> parties) {
        return parties.stream().mapToInt(QueuedParty::size).sum();
    }

    /**
     * The first incompatibility between two players under their (effective) restrictions, or empty if they are
     * compatible. Used both for cron-time matching (the reason is ignored) and submit-time party validation (the reason
     * is surfaced to the queuer).
     */
    private Optional<String> incompatibilityReason(PlayerMatchData a, PlayerMatchData b, boolean relaxed) {
        if (a.avoidList().contains(b.userId())) {
            return Optional.of("<@" + a.userId() + "> has <@" + b.userId() + "> on their avoid list."
                    + " Remove them from your avoid list (Additional Settings) to queue with that player.");
        }
        if (b.avoidList().contains(a.userId())) {
            return Optional.of("<@" + b.userId() + "> has <@" + a.userId() + "> on their avoid list."
                    + " Remove them from your avoid list (Additional Settings) to queue with that player.");
        }

        List<String> aRestrictions = a.restrictions();
        List<String> bRestrictions = b.restrictions();

        if (MatchmakingOptions.wantsTigl(aRestrictions) != MatchmakingOptions.wantsTigl(bRestrictions)) {
            return Optional.of("<@" + a.userId() + "> and <@" + b.userId() + "> disagree on the \""
                    + MatchmakingOptions.TIGL_OPTION + "\" restriction."
                    + removeOptionHint(MatchmakingOptions.TIGL_OPTION));
        }

        Optional<String> roleReason = roleViolationReason(aRestrictions, a.roleNames(), b.roleNames(), b.userId());
        if (roleReason.isPresent()) return roleReason;
        roleReason = roleViolationReason(bRestrictions, b.roleNames(), a.roleNames(), a.userId());
        if (roleReason.isPresent()) return roleReason;

        boolean aIsNew = a.completedGames() < NEW_PLAYER_GAME_THRESHOLD;
        boolean bIsNew = b.completedGames() < NEW_PLAYER_GAME_THRESHOLD;
        if (bIsNew && MatchmakingOptions.wantsToAvoidNewPlayers(aRestrictions)) {
            return Optional.of("<@" + b.userId() + "> is a new async player."
                    + removeOptionHint(MatchmakingOptions.AVOID_NEW_PLAYERS_OPTION));
        }
        if (aIsNew && MatchmakingOptions.wantsToAvoidNewPlayers(bRestrictions)) {
            return Optional.of("<@" + a.userId() + "> is a new async player."
                    + removeOptionHint(MatchmakingOptions.AVOID_NEW_PLAYERS_OPTION));
        }

        if (MatchmakingOptions.wantsSimilarActiveHours(aRestrictions)
                || MatchmakingOptions.wantsSimilarActiveHours(bRestrictions)) {
            long sharedBuckets = a.activeHourBuckets().stream()
                    .filter(b.activeHourBuckets()::contains)
                    .count();
            if (sharedBuckets < ACTIVE_HOUR_SHARED_BUCKET_REQUIREMENT) {
                return Optional.of(
                        "<@" + a.userId() + "> and <@" + b.userId() + "> don't have similar enough active hours."
                                + removeOptionHint(MatchmakingOptions.SIMILAR_ACTIVE_HOURS_OPTION));
            }
        }

        if (MatchmakingOptions.wantsSimilarPlayerSkill(aRestrictions)
                || MatchmakingOptions.wantsSimilarPlayerSkill(bRestrictions)) {
            BigDecimal aRating = aIsNew ? NEW_PLAYER_MATCHMAKING_RATING : a.rating();
            BigDecimal bRating = bIsNew ? NEW_PLAYER_MATCHMAKING_RATING : b.rating();
            double tolerance =
                    relaxed ? RELAXED_SIMILAR_SKILL_DIFFERENCE_THRESHOLD : SIMILAR_SKILL_DIFFERENCE_THRESHOLD;
            if (aRating.subtract(bRating).abs().compareTo(BigDecimal.valueOf(tolerance)) > 0) {
                return Optional.of(
                        "<@" + a.userId() + "> and <@" + b.userId() + "> don't have similar enough skill ratings."
                                + removeOptionHint(MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION));
            }
        }

        return Optional.empty();
    }

    private static String removeOptionHint(String restrictionOption) {
        return " Remove the \"" + restrictionOption + "\" queue option to queue with that player.";
    }

    private static Optional<String> roleViolationReason(
            List<String> chooserRestrictions,
            Set<String> chooserRoleNames,
            Set<String> otherRoleNames,
            String otherId) {
        if (chooserRoleNames.contains(MatchmakingOptions.FLOATERS_ROLE_NAME)
                && MatchmakingOptions.wantsOnlyFloaters(chooserRestrictions)
                && !otherRoleNames.contains(MatchmakingOptions.FLOATERS_ROLE_NAME)) {
            return Optional.of("<@" + otherId + "> doesn't have the " + MatchmakingOptions.FLOATERS_ROLE_NAME + " role."
                    + removeOptionHint(MatchmakingOptions.ONLY_MATCH_FLOATERS_OPTION));
        }
        if (chooserRoleNames.contains(MatchmakingOptions.WARRIORS_ROLE_NAME)
                && MatchmakingOptions.wantsOnlyWarriors(chooserRestrictions)
                && !otherRoleNames.contains(MatchmakingOptions.WARRIORS_ROLE_NAME)) {
            return Optional.of("<@" + otherId + "> doesn't have the " + MatchmakingOptions.WARRIORS_ROLE_NAME + " role."
                    + removeOptionHint(MatchmakingOptions.ONLY_MATCH_WARRIORS_OPTION));
        }
        return Optional.empty();
    }

    private boolean isHalfQueueTimePassed(QueuedParty party, Instant now) {
        double maxHours = getHours(party.leaderSettings().getMatchmakingMaxQueueTime());
        double hoursWaited = Duration.between(party.party().getQueuedAt(), now).toMinutes() / 60.0;
        return hoursWaited >= maxHours * QUEUE_TIME_RELAX_FRACTION;
    }

    /**
     * Build per-member matching data for the queued parties. Restrictions come from the party's leader, while the avoid
     * list and profile attributes (active hours, rating, completed games, roles) come from the member's own account.
     */
    private Map<MatchmakingQueueMember, PlayerMatchData> buildMatchData(List<QueuedParty> parties) {
        Set<String> userIds = parties.stream()
                .flatMap(party -> party.members().stream())
                .map(MatchmakingQueueMember::getUserId)
                .collect(Collectors.toSet());
        Map<String, BigDecimal> ratings = MatchmakingRatingEventService.get().getPlayerRatings(userIds);
        Guild guild = JdaService.guildPrimary;
        Instant now = Instant.now();

        Map<MatchmakingQueueMember, PlayerMatchData> matchData = new HashMap<>();
        for (QueuedParty party : parties) {
            List<String> leaderRestrictions = party.leaderSettings().getMatchmakingRestrictions();
            boolean halfQueueTimePassed = isHalfQueueTimePassed(party, now);
            for (MatchmakingQueueMember member : party.members()) {
                String id = member.getUserId();
                UserSettings ownSettings = UserSettingsManager.get(id);
                matchData.put(
                        member,
                        new PlayerMatchData(
                                id,
                                leaderRestrictions,
                                ownSettings.getMatchmakingAvoidList(),
                                ratings.getOrDefault(id, NEW_PLAYER_MATCHMAKING_RATING),
                                computeActiveHourBuckets(ownSettings.getActiveHoursAsIntegers()),
                                completedGames(id),
                                roleNames(guild, id),
                                halfQueueTimePassed));
            }
        }
        return matchData;
    }

    private Map<String, PlayerMatchData> buildValidationData(List<String> userIds, UserSettings leaderSettings) {
        Map<String, BigDecimal> ratings = MatchmakingRatingEventService.get().getPlayerRatings(new HashSet<>(userIds));
        Guild guild = JdaService.guildPrimary;
        List<String> leaderRestrictions = leaderSettings.getMatchmakingRestrictions();

        Map<String, PlayerMatchData> dataById = new HashMap<>();
        for (String id : userIds) {
            UserSettings ownSettings = UserSettingsManager.get(id);
            dataById.put(
                    id,
                    new PlayerMatchData(
                            id,
                            leaderRestrictions,
                            ownSettings.getMatchmakingAvoidList(),
                            ratings.getOrDefault(id, NEW_PLAYER_MATCHMAKING_RATING),
                            computeActiveHourBuckets(ownSettings.getActiveHoursAsIntegers()),
                            completedGames(id),
                            roleNames(guild, id),
                            false));
        }
        return dataById;
    }

    private static int completedGames(String userId) {
        ManagedPlayer managedPlayer = GameManager.getManagedPlayer(userId);
        if (managedPlayer == null) return 0;
        return UserGameInfoService.countCompletedGamesThatAffectJoinLimit(managedPlayer);
    }

    private static Set<String> roleNames(Guild guild, String userId) {
        Member member = guild == null ? null : guild.getMemberById(userId);
        return member == null ? Set.of() : MatchmakingOptions.getHeldOnlyMatchRoleNames(guild, member);
    }

    private Set<Integer> computeActiveHourBuckets(Set<Integer> activeHours) {
        Set<Integer> matchedBuckets = new HashSet<>();
        for (int i = 0; i < NUMBER_OF_ACTIVE_HOUR_BUCKETS; i++) {
            int startHour = i * ACTIVE_HOUR_BUCKET_SIZE;
            int endHour = startHour + ACTIVE_HOUR_BUCKET_SIZE - 1;
            if (getBucketScore(activeHours, startHour, endHour) >= ACTIVE_HOUR_BUCKET_MATCH_THRESHOLD) {
                matchedBuckets.add(i);
            }
        }
        return matchedBuckets;
    }

    private int getBucketScore(Set<Integer> activeHours, int startInclusive, int endInclusive) {
        int score = 0;
        for (int hour : activeHours) {
            if (hour >= startInclusive && hour <= endInclusive) {
                score++;
            }
        }
        return score;
    }

    @NonNull
    private List<QueuedParty> removeExpiredParties(List<QueuedParty> parties, Instant now) {
        List<QueuedParty> expired = parties.stream()
                .filter(party -> party.party()
                        .getQueuedAt()
                        .plus(Duration.ofHours(getHours(party.leaderSettings().getMatchmakingMaxQueueTime())))
                        .isBefore(now))
                .toList();
        if (!expired.isEmpty()) {
            List<Long> expiredIds =
                    expired.stream().map(party -> party.party().getId()).toList();
            BotLogger.info("Expiring " + expired.size() + " matchmaking parties.");
            memberRepository.deleteAllByPartyIdIn(expiredIds);
            partyRepository.deleteAllById(expiredIds);

            String expiryMessage =
                    "The matchmaking service wasn't able to find you a game in the time frame you selected. "
                            + "Queue again when ready and consider being open to additional game types or longer wait "
                            + "times.";
            for (QueuedParty party : expired) {
                for (MatchmakingQueueMember member : party.members()) {
                    User user = JdaService.jda.getUserById(member.getUserId());
                    if (user == null) continue;
                    MessageHelper.sendMessageToUser(expiryMessage, user);
                }
            }
        }
        return parties.stream().filter(party -> !expired.contains(party)).toList();
    }

    private void postMatchedGroupsToMakingNewGamesForum(List<MatchedGame> gamesToCreate) {
        Guild guild = JdaService.guildPrimary;
        if (gamesToCreate.isEmpty() || guild == null) return;

        List<ForumChannel> forums =
                guild.getForumChannelsByName(CreateGameLaunchPostService.MAKING_NEW_GAMES_CHANNEL, true);
        if (forums.isEmpty()) {
            BotLogger.error("MatchmakerService could not find a thread container named #"
                    + CreateGameLaunchPostService.MAKING_NEW_GAMES_CHANNEL + ".");
            return;
        }
        IThreadContainer threadContainer = forums.getFirst();

        for (MatchedGame game : gamesToCreate) {
            List<MatchmakingQueueMember> queueMembers = game.members();
            List<Member> members = queueMembers.stream()
                    .map(member -> guild.getMemberById(member.getUserId()))
                    .filter(Objects::nonNull)
                    .toList();
            if (members.size() != queueMembers.size()) continue;

            String gameFunName = CreateGameService.autoGenerateGameName();
            String threadTitle = "Matchmaker Game: " + gameFunName.replace(":", "");
            String setupMessage = game.describeSetup();
            threadContainer.createThreadChannel(threadTitle).queue(thread -> {
                MessageHelper.sendMessageToChannel(thread, setupMessage);
                CreateGameLaunchPostService.postLaunchButtons(thread, members, gameFunName);
            });
        }
    }

    /** A queued party with its members and its leader's effective settings. */
    private record QueuedParty(
            MatchmakingQueueParty party, List<MatchmakingQueueMember> members, UserSettings leaderSettings) {
        private int size() {
            return members.size();
        }
    }

    /** Pre-computed matching inputs for one player. */
    private record PlayerMatchData(
            String userId,
            List<String> restrictions,
            List<String> avoidList,
            BigDecimal rating,
            Set<Integer> activeHourBuckets,
            int completedGames,
            Set<String> roleNames,
            boolean halfQueueTimePassed) {}

    private record MatchedGame(
            List<MatchmakingQueueMember> members,
            List<MatchmakingQueueParty> parties,
            String playerCount,
            String victoryPointGoal,
            String expansion,
            String pace,
            List<String> restrictions) {

        private String describeSetup() {
            String restrictionsText = restrictions.isEmpty() ? "None" : String.join(", ", restrictions);
            return "The players were matched on the following game setup:\n"
                    + "- **Player count:** " + playerCount + "\n"
                    + "- **Victory point goal:** " + victoryPointGoal + "\n"
                    + "- **Expansion:** " + expansion + "\n"
                    + "- **Pace:** " + pace + "\n"
                    + "- **Restrictions:** " + restrictionsText;
        }
    }

    public static MatchmakerService get() {
        return SpringContext.getBean(MatchmakerService.class);
    }
}
