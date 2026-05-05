package ti4.contest.replay.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayChannels;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.entities.CombatReplayHouseEntity;
import ti4.contest.replay.entities.CombatReplayHouseOptOutEntity;
import ti4.contest.replay.entities.CombatReplayLeaderboardEntryEntity;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayHouseOptOutRepository;
import ti4.contest.replay.repository.CombatReplayHouseRepository;
import ti4.contest.replay.repository.CombatReplayLeaderboardEntryRepository;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.TI4Emoji;

@Service
@RequiredArgsConstructor
public class CombatReplayHouseService {

    public static final String TOGGLE_HOUSE_ROLE_BUTTON_ID = "lazaxToggleHouseRole";
    public static final String TOGGLE_HOUSE_OPT_IN_BUTTON_ID = "lazaxToggleHouseOptIn";

    private static final List<String> HOUSE_REACTION_CHANNEL_NAMES = List.of("lazax-hacan-mentak", "lazax-hacan-naalu");

    private final CombatContestSettings settings;
    private final CombatReplayHouseRepository houseRepository;
    private final CombatReplayHouseOptOutRepository houseOptOutRepository;
    private final CombatReplayContestRepository replayContestRepository;
    private final CombatCandidateRepository candidateRepository;
    private final CombatReplayLeaderboardEntryRepository leaderboardEntryRepository;

    public void postPublicParticipationControls(TextChannel channel) {
        if (!settings.isHousesEnabled() || channel == null) return;
        List<Button> buttons = List.of(
                Buttons.gray(TOGGLE_HOUSE_ROLE_BUTTON_ID, "Toggle Role"),
                Buttons.red(TOGGLE_HOUSE_OPT_IN_BUTTON_ID, "Opt In/Out Entirely"));
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                "-# Lazax delegation controls: toggle your Discord role visibility, or opt out entirely while preserving your delegation for a future opt-in.",
                buttons);
    }

    public void addHouseEmojiReactionIfNeeded(MessageReceivedEvent event) {
        if (!settings.isHousesEnabled()) return;
        if (event == null || event.getAuthor().isBot()) return;
        if (!isHouseReactionChannel(event)) return;

        CombatReplayHouseEntity assignment =
                houseRepository.findByDiscordUserId(event.getAuthor().getId()).orElse(null);
        if (assignment == null) return;

        try {
            event.getMessage()
                    .addReaction(Emoji.fromFormatted(
                            FactionEmojis.getFactionIcon(assignment.getHouse().displayName())
                                    .toString()))
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        } catch (Exception e) {
            BotLogger.error("Failed to add combat replay house reaction.", e);
        }
    }

    public void assignHouseForPredictionReaction(MessageReactionAddEvent event, Message message) {
        if (!settings.isHousesEnabled()) return;
        if (event == null
                || message == null
                || event.getUser() == null
                || event.getUser().isBot()) return;

        CombatReplayContestEntity contest = replayContestRepository
                .findByPublicMessageIdOrPublicThreadId(
                        message.getIdLong(), event.getChannel().getIdLong())
                .orElse(null);
        if (contest == null) return;
        if (contest.getReplayStartAt() == null || !LocalDateTime.now().isBefore(contest.getReplayStartAt())) return;

        CombatCandidateEntity candidate =
                candidateRepository.findById(contest.getCandidateId()).orElse(null);
        if (candidate == null || !isPredictionEmoji(event, candidate)) return;

        assignHouseIfAbsent(
                event.getGuild(),
                event.getMember(),
                event.getUser().getId(),
                event.getUser().getName());
    }

    public CombatReplayHouseEntity assignHouseIfAbsent(User user, Guild guild) {
        if (!settings.isHousesEnabled()) return null;
        if (user == null || user.isBot()) return null;

        Member member = null;
        if (guild != null) {
            try {
                member = guild.retrieveMemberById(user.getId()).complete();
            } catch (Exception e) {
                BotLogger.warning("Could not resolve member for Lazax house assignment: " + user.getId());
            }
        }
        return assignHouseIfAbsent(guild, member, user.getId(), user.getName());
    }

    public CombatReplayHouseEntity assignHouseIfAbsent(Guild guild, Member member, User user) {
        if (!settings.isHousesEnabled()) return null;
        if (user == null || user.isBot()) return null;
        return assignHouseIfAbsent(guild, member, user.getId(), user.getName());
    }

    public synchronized int assignLeaderboardEntriesRandomly(
            Guild guild, List<CombatReplayLeaderboardEntryEntity> leaderboardEntries) {
        if (!settings.isHousesEnabled() || leaderboardEntries == null || leaderboardEntries.isEmpty()) return 0;

        List<CombatReplayLeaderboardEntryEntity> entries = new ArrayList<>(leaderboardEntries);
        entries.removeIf(entry -> entry == null
                || StringUtils.isBlank(entry.getDiscordUserId())
                || StringUtils.isBlank(entry.getDiscordUserName()));
        Collections.shuffle(entries);

        List<CombatReplayHouse> houses = new ArrayList<>(CombatReplayHouse.assignmentOrder());
        Collections.shuffle(houses);

        int assigned = 0;
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < entries.size(); index++) {
            CombatReplayLeaderboardEntryEntity entry = entries.get(index);
            CombatReplayHouse house = houses.get(index % houses.size());
            CombatReplayHouseEntity assignment = houseRepository
                    .findByDiscordUserId(entry.getDiscordUserId())
                    .orElseGet(CombatReplayHouseEntity::new);
            assignment.setDiscordUserId(entry.getDiscordUserId());
            assignment.setDiscordUserName(entry.getDiscordUserName());
            assignment.setHouse(house);
            if (assignment.getAssignedAt() == null) {
                assignment.setAssignedAt(now);
            }
            assignment.setUpdatedAt(now);
            houseRepository.save(assignment);
            houseOptOutRepository
                    .findByDiscordUserId(entry.getDiscordUserId())
                    .ifPresent(houseOptOutRepository::delete);

            Member member = retrieveMember(guild, entry.getDiscordUserId());
            setOnlyHouseRole(guild, member, house);
            assigned++;
        }
        return assigned;
    }

    public synchronized int assignLeaderboardEntriesByParticipation(
            Guild guild, List<CombatReplayLeaderboardEntryEntity> leaderboardEntries) {
        if (!settings.isHousesEnabled() || leaderboardEntries == null || leaderboardEntries.isEmpty()) return 0;

        List<CombatReplayLeaderboardEntryEntity> entries = new ArrayList<>(leaderboardEntries);
        entries.removeIf(entry -> entry == null
                || StringUtils.isBlank(entry.getDiscordUserId())
                || StringUtils.isBlank(entry.getDiscordUserName()));
        entries.sort((left, right) -> {
            int participationCompare =
                    Integer.compare(safeInt(right.getPredictionCount()), safeInt(left.getPredictionCount()));
            if (participationCompare != 0) return participationCompare;
            int pointsCompare = Integer.compare(safeInt(right.getTotalPoints()), safeInt(left.getTotalPoints()));
            if (pointsCompare != 0) return pointsCompare;
            return StringUtils.compareIgnoreCase(left.getDiscordUserName(), right.getDiscordUserName());
        });

        Map<CombatReplayHouse, Integer> participationByHouse = new HashMap<>();
        Map<CombatReplayHouse, Integer> membersByHouse = new HashMap<>();
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            participationByHouse.put(house, 0);
            membersByHouse.put(house, 0);
        }

        int assigned = 0;
        LocalDateTime now = LocalDateTime.now();
        for (CombatReplayLeaderboardEntryEntity entry : entries) {
            CombatReplayHouse house = houseWithLowestAssignedParticipation(participationByHouse, membersByHouse);
            CombatReplayHouseEntity assignment = houseRepository
                    .findByDiscordUserId(entry.getDiscordUserId())
                    .orElseGet(CombatReplayHouseEntity::new);
            assignment.setDiscordUserId(entry.getDiscordUserId());
            assignment.setDiscordUserName(entry.getDiscordUserName());
            assignment.setHouse(house);
            if (assignment.getAssignedAt() == null) {
                assignment.setAssignedAt(now);
            }
            assignment.setUpdatedAt(now);
            houseRepository.save(assignment);
            houseOptOutRepository
                    .findByDiscordUserId(entry.getDiscordUserId())
                    .ifPresent(houseOptOutRepository::delete);

            participationByHouse.computeIfPresent(
                    house, (ignored, participation) -> participation + safeInt(entry.getPredictionCount()));
            membersByHouse.computeIfPresent(house, (ignored, members) -> members + 1);

            Member member = retrieveMember(guild, entry.getDiscordUserId());
            setOnlyHouseRole(guild, member, house);
            assigned++;
        }
        return assigned;
    }

    public synchronized CombatReplayHouseEntity overrideHouse(
            Guild guild, Member member, String discordUserId, String discordUserName, CombatReplayHouse house) {
        if (!settings.isHousesEnabled()) return null;
        if (discordUserId == null || discordUserId.isBlank() || house == null) return null;

        String safeUserName = StringUtils.defaultIfBlank(discordUserName, "Unknown User");
        LocalDateTime now = LocalDateTime.now();
        CombatReplayHouseEntity assignment =
                houseRepository.findByDiscordUserId(discordUserId).orElseGet(CombatReplayHouseEntity::new);
        assignment.setDiscordUserId(discordUserId);
        assignment.setDiscordUserName(safeUserName);
        assignment.setHouse(house);
        if (assignment.getAssignedAt() == null) {
            assignment.setAssignedAt(now);
        }
        assignment.setUpdatedAt(now);
        CombatReplayHouseEntity saved = houseRepository.save(assignment);
        houseOptOutRepository.findByDiscordUserId(discordUserId).ifPresent(houseOptOutRepository::delete);

        setOnlyHouseRole(guild, member, house);
        return saved;
    }

    private Member retrieveMember(Guild guild, String discordUserId) {
        if (guild == null || StringUtils.isBlank(discordUserId)) return null;
        try {
            return guild.retrieveMemberById(discordUserId).complete();
        } catch (Exception e) {
            BotLogger.warning("Could not resolve member for Lazax delegation assignment: " + discordUserId);
            return null;
        }
    }

    public synchronized boolean unassignHouse(Guild guild, Member member, String discordUserId) {
        if (!settings.isHousesEnabled()) return false;
        if (discordUserId == null || discordUserId.isBlank()) return false;

        CombatReplayHouseEntity assignment =
                houseRepository.findByDiscordUserId(discordUserId).orElse(null);
        if (assignment == null) {
            clearHouseRoles(guild, member);
            return false;
        }

        houseRepository.delete(assignment);
        clearHouseRoles(guild, member);
        return true;
    }

    public synchronized String toggleHouseRole(Guild guild, Member member, User user) {
        if (!settings.isHousesEnabled()) return "Lazax delegations are not enabled.";
        if (user == null || user.isBot()) return "Could not identify your Lazax delegation.";

        CombatReplayHouseEntity assignment =
                houseRepository.findByDiscordUserId(user.getId()).orElse(null);
        if (assignment == null) {
            if (houseOptOutRepository.findByDiscordUserId(user.getId()).isPresent()) {
                return "You are opted out of Lazax delegations. Use **Opt In/Out Entirely** to opt back in first.";
            }
            return "You do not have a Lazax delegation yet.";
        }
        if (guild == null || member == null) return "Could not update your Lazax delegation role.";

        Role role = findRole(guild, assignment.getHouse());
        if (role == null) return "Could not find the " + assignment.getHouse().displayName() + " Delegation role.";

        if (member.getRoles().contains(role)) {
            guild.removeRoleFromMember(member, role).queue(null, BotLogger::catchRestError);
            return "Removed your **" + assignment.getHouse().displayName()
                    + " Delegation** role. Your delegation assignment is unchanged.";
        }

        grantHouseRole(guild, member, assignment.getHouse(), null);
        return "Added your **" + assignment.getHouse().displayName() + " Delegation** role.";
    }

    public synchronized String toggleHouseOptIn(Guild guild, Member member, User user) {
        if (!settings.isHousesEnabled()) return "Lazax delegations are not enabled.";
        if (user == null || user.isBot()) return "Could not identify your Lazax delegation.";

        CombatReplayHouseEntity assignment =
                houseRepository.findByDiscordUserId(user.getId()).orElse(null);
        if (assignment != null) {
            CombatReplayHouseOptOutEntity optOut = houseOptOutRepository
                    .findByDiscordUserId(user.getId())
                    .orElseGet(CombatReplayHouseOptOutEntity::new);
            optOut.setDiscordUserId(assignment.getDiscordUserId());
            optOut.setDiscordUserName(assignment.getDiscordUserName());
            optOut.setHouse(assignment.getHouse());
            optOut.setAssignedAt(assignment.getAssignedAt());
            optOut.setOptedOutAt(LocalDateTime.now());
            houseOptOutRepository.save(optOut);

            houseRepository.delete(assignment);
            clearHouseRoles(guild, member);
            return "Opted out of Lazax delegations. Your **"
                    + assignment.getHouse().displayName()
                    + " Delegation** assignment is saved if you opt back in later.";
        }

        CombatReplayHouseOptOutEntity optOut =
                houseOptOutRepository.findByDiscordUserId(user.getId()).orElse(null);
        if (optOut != null) {
            CombatReplayHouseEntity restored = new CombatReplayHouseEntity();
            restored.setDiscordUserId(optOut.getDiscordUserId());
            restored.setDiscordUserName(StringUtils.defaultIfBlank(user.getName(), optOut.getDiscordUserName()));
            restored.setHouse(optOut.getHouse());
            restored.setAssignedAt(optOut.getAssignedAt());
            restored.setUpdatedAt(LocalDateTime.now());
            houseRepository.save(restored);
            houseOptOutRepository.delete(optOut);
            ensureLeaderboardEntry(restored.getDiscordUserId(), restored.getDiscordUserName());
            setOnlyHouseRole(guild, member, restored.getHouse());
            return "Opted back in to **" + restored.getHouse().displayName() + " Delegation**.";
        }

        CombatReplayHouseEntity newAssignment = assignHouseIfAbsent(guild, member, user.getId(), user.getName());
        return newAssignment == null
                ? "Could not opt you in to Lazax delegations."
                : "Opted in to **" + newAssignment.getHouse().displayName() + " Delegation**.";
    }

    public Map<String, CombatReplayHouse> housesByUserIds(Collection<String> discordUserIds) {
        Map<String, CombatReplayHouse> housesByUser = new HashMap<>();
        if (discordUserIds == null || discordUserIds.isEmpty()) return housesByUser;

        for (CombatReplayHouseEntity house : houseRepository.findByDiscordUserIdIn(discordUserIds)) {
            housesByUser.put(house.getDiscordUserId(), house.getHouse());
        }
        return housesByUser;
    }

    public CombatReplayHouse houseForUser(String discordUserId) {
        if (discordUserId == null || discordUserId.isBlank()) return null;
        return houseRepository
                .findByDiscordUserId(discordUserId)
                .map(CombatReplayHouseEntity::getHouse)
                .orElse(null);
    }

    public String houseRoleMention(CombatReplayHouse house) {
        Guild guild = JdaService.guildPrimary;
        return houseRoleMention(guild, house);
    }

    private String houseRoleMention(Guild guild, CombatReplayHouse house) {
        if (guild == null) return "**" + house.displayName() + " Delegation**";
        Role role = findRole(guild, house);
        return role == null ? "**" + house.displayName() + " Delegation**" : role.getAsMention();
    }

    public List<CombatReplayHouseEntity> allHouseAssignments() {
        return houseRepository.findAll();
    }

    private boolean isHouseReactionChannel(MessageReceivedEvent event) {
        String channelName = event.getChannel().getName();
        if (channelName == null) return false;
        if (event.getChannel() instanceof ThreadChannel thread) {
            return isContestThread(thread);
        }
        return CombatReplayChannels.contestChannelName(settings).equalsIgnoreCase(channelName)
                || channelName.toLowerCase().startsWith("lazax-war-archives")
                || HOUSE_REACTION_CHANNEL_NAMES.stream()
                        .anyMatch(reactionChannelName -> reactionChannelName.equalsIgnoreCase(channelName));
    }

    private boolean isContestThread(ThreadChannel thread) {
        if (thread == null) return false;
        if (replayContestRepository
                .findByPublicMessageIdOrPublicThreadId(-1L, thread.getIdLong())
                .isPresent()) {
            return true;
        }
        String parentName = thread.getParentChannel().getName();
        return parentName != null
                && (CombatReplayChannels.contestChannelName(settings).equalsIgnoreCase(parentName)
                        || parentName.toLowerCase().startsWith("lazax-war-archives"));
    }

    private synchronized CombatReplayHouseEntity assignHouseIfAbsent(
            Guild guild, Member member, String discordUserId, String discordUserName) {
        if (discordUserId == null || discordUserId.isBlank()) return null;
        String safeUserName = StringUtils.defaultIfBlank(discordUserName, "Unknown User");

        CombatReplayHouseEntity existing =
                houseRepository.findByDiscordUserId(discordUserId).orElse(null);
        if (existing != null) {
            ensureLeaderboardEntry(discordUserId, safeUserName);
            if (!safeUserName.equals(existing.getDiscordUserName())) {
                existing.setDiscordUserName(safeUserName);
                existing.setUpdatedAt(LocalDateTime.now());
                existing = houseRepository.save(existing);
            }
            ensureHouseRole(guild, member, existing.getHouse());
            return existing;
        }
        if (houseOptOutRepository.findByDiscordUserId(discordUserId).isPresent()) {
            clearHouseRoles(guild, member);
            return null;
        }

        CombatReplayHouse house = houseWithFewestMembers();
        LocalDateTime now = LocalDateTime.now();
        CombatReplayHouseEntity assignment = new CombatReplayHouseEntity();
        assignment.setDiscordUserId(discordUserId);
        assignment.setDiscordUserName(safeUserName);
        assignment.setHouse(house);
        assignment.setAssignedAt(now);
        assignment.setUpdatedAt(now);
        CombatReplayHouseEntity saved = houseRepository.save(assignment);
        ensureLeaderboardEntry(discordUserId, safeUserName);

        grantHouseRole(guild, member, house, () -> announceHouseAssignment(guild, discordUserId, house));
        return saved;
    }

    private void ensureLeaderboardEntry(String discordUserId, String discordUserName) {
        leaderboardEntryRepository.findByDiscordUserId(discordUserId).orElseGet(() -> {
            CombatReplayLeaderboardEntryEntity entry = new CombatReplayLeaderboardEntryEntity();
            entry.setDiscordUserId(discordUserId);
            entry.setDiscordUserName(discordUserName);
            entry.setTotalPoints(settings.getHouseAbilities().getInitialIndividualPoints());
            entry.setPredictionCount(0);
            entry.setCorrectPredictions(0);
            entry.setUpdatedAt(LocalDateTime.now());
            return leaderboardEntryRepository.save(entry);
        });
    }

    private CombatReplayHouse houseWithFewestMembers() {
        CombatReplayHouse selected = CombatReplayHouse.NAALU;
        long selectedCount = Long.MAX_VALUE;
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            long count = houseRepository.countByHouse(house);
            if (count < selectedCount) {
                selected = house;
                selectedCount = count;
            }
        }
        return selected;
    }

    private CombatReplayHouse houseWithLowestAssignedParticipation(
            Map<CombatReplayHouse, Integer> participationByHouse, Map<CombatReplayHouse, Integer> membersByHouse) {
        CombatReplayHouse selected = CombatReplayHouse.NAALU;
        int selectedParticipation = Integer.MAX_VALUE;
        int selectedMembers = Integer.MAX_VALUE;
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            int participation = participationByHouse.getOrDefault(house, 0);
            int members = membersByHouse.getOrDefault(house, 0);
            if (participation < selectedParticipation
                    || (participation == selectedParticipation && members < selectedMembers)) {
                selected = house;
                selectedParticipation = participation;
                selectedMembers = members;
            }
        }
        return selected;
    }

    private boolean isPredictionEmoji(MessageReactionAddEvent event, CombatCandidateEntity candidate) {
        String emojiFormatted = event.getEmoji().getFormatted();
        String emojiName = event.getEmoji().getName();
        return matchesFactionEmoji(emojiFormatted, emojiName, candidate.getAttackerFaction())
                || matchesFactionEmoji(emojiFormatted, emojiName, candidate.getDefenderFaction());
    }

    private boolean matchesFactionEmoji(String emojiFormatted, String emojiName, String faction) {
        TI4Emoji factionEmoji = FactionEmojis.getFactionIcon(faction);
        return StringUtils.equals(emojiFormatted, factionEmoji.toString())
                || StringUtils.equalsIgnoreCase(emojiName, factionEmoji.name());
    }

    private void ensureHouseRole(Guild guild, Member member, CombatReplayHouse house) {
        grantHouseRole(guild, member, house, null);
    }

    private void setOnlyHouseRole(Guild guild, Member member, CombatReplayHouse house) {
        if (guild == null || member == null || house == null) return;
        for (CombatReplayHouse existingHouse : CombatReplayHouse.assignmentOrder()) {
            if (existingHouse == house) continue;
            Role role = findRole(guild, existingHouse);
            if (role != null && member.getRoles().contains(role)) {
                guild.removeRoleFromMember(member, role).queue(null, BotLogger::catchRestError);
            }
        }
        grantHouseRole(guild, member, house, null);
    }

    private void clearHouseRoles(Guild guild, Member member) {
        if (guild == null || member == null) return;
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            Role role = findRole(guild, house);
            if (role != null && member.getRoles().contains(role)) {
                guild.removeRoleFromMember(member, role).queue(null, BotLogger::catchRestError);
            }
        }
    }

    private void grantHouseRole(Guild guild, Member member, CombatReplayHouse house, Runnable afterRoleUpdate) {
        if (guild == null || member == null || house == null) return;
        Role role = findRole(guild, house);
        if (role == null) {
            BotLogger.warning("Lazax house role not found: " + house.roleName());
            runAfterRoleUpdate(afterRoleUpdate);
            return;
        }
        if (member.getRoles().contains(role)) {
            runAfterRoleUpdate(afterRoleUpdate);
            return;
        }
        guild.addRoleToMember(member, role).queue(ignored -> runAfterRoleUpdate(afterRoleUpdate), error -> {
            BotLogger.catchRestError(error);
            runAfterRoleUpdate(afterRoleUpdate);
        });
    }

    private void runAfterRoleUpdate(Runnable afterRoleUpdate) {
        if (afterRoleUpdate != null) {
            afterRoleUpdate.run();
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private void announceHouseAssignment(Guild guild, String discordUserId, CombatReplayHouse house) {
        if (guild == null || house == null) return;
        TextChannel channel = guild.getTextChannelsByName(house.channelName(), true).stream()
                .findFirst()
                .orElse(null);
        if (channel == null) {
            BotLogger.warning("Lazax house channel not found: " + house.channelName());
            return;
        }

        MessageHelper.sendMessageToChannel(
                channel, "**" + house.displayName() + " Delegation** welcomes <@" + discordUserId + ">.");
    }

    private Role findRole(Guild guild, CombatReplayHouse house) {
        if (guild == null || house == null) return null;
        return guild.getRolesByName(house.roleName(), true).stream().findFirst().orElse(null);
    }
}
