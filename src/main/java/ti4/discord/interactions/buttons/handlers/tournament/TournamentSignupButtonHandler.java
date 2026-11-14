package ti4.discord.interactions.buttons.handlers.tournament;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.service.tournament.TournamentDiscord;
import ti4.service.tournament.TournamentHours;
import ti4.service.tournament.TournamentPlayTimes;
import ti4.service.tournament.TournamentRoles;
import ti4.service.tournament.TournamentSignupPosts;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentEntry;
import ti4.spring.service.tournament.TournamentService;
import ti4.spring.service.tournament.TournamentService.JoinResult;
import ti4.spring.service.tournament.TournamentService.LeaveResult;

@UtilityClass
class TournamentSignupButtonHandler {

    @ButtonHandler(value = TournamentSignupPosts.JOIN, save = false)
    static void join(ButtonInteractionEvent event, String buttonID) {
        Tournament tournament = tournamentOrReply(event, buttonID, TournamentSignupPosts.JOIN);
        if (tournament == null) return;
        String userId = event.getUser().getId();
        boolean signedUp = TournamentService.getBean().entries(tournament.getId()).stream()
                .anyMatch(entry -> entry.getUserId().equals(userId));
        UserSettings settings = UserSettingsManager.get(userId);
        if (!signedUp && tournament.getStatus().acceptsSignups() && TournamentHours.needsPlayTimes(settings)) {
            TournamentDiscord.ephemeralWithComponents(
                    event,
                    TournamentPlayTimes.pickerText(settings, true),
                    TournamentPlayTimes.picker(tournament.getId(), settings, true));
            return;
        }
        TournamentDiscord.ephemeral(event, joinReply(tournament, userId));
        TournamentSignupPosts.scheduleRefresh(tournament.getId());
    }

    @ButtonHandler(value = TournamentPlayTimes.CONFIRM_JOIN, save = false)
    static void confirmJoin(ButtonInteractionEvent event, String buttonID) {
        Tournament tournament = tournamentOrReply(event, buttonID, TournamentPlayTimes.CONFIRM_JOIN);
        if (tournament == null) return;
        String userId = event.getUser().getId();
        if (!TournamentPlayTimes.hasPlayTimes(UserSettingsManager.get(userId))) {
            TournamentDiscord.ephemeral(event, "Pick your time zone and at least one part of the day first.");
            return;
        }
        event.getHook()
                .editOriginal(joinReply(tournament, userId))
                .setComponents()
                .queue(Consumers.nop(), BotLogger::catchRestError);
        TournamentSignupPosts.scheduleRefresh(tournament.getId());
    }

    private static String joinReply(Tournament tournament, String userId) {
        JoinResult result = TournamentService.getBean().join(tournament.getId(), userId);
        String name = "**" + tournament.displayName() + "**";
        String reply = switch (result.outcome()) {
            case JOINED -> joinedMessage(tournament, userId, name, result);
            case WAITLISTED ->
                "The pool is full, so you're **#" + result.waitlistPosition() + " on the waitlist** for " + name
                        + ". You'll be pinged here if a seat opens.";
            case ALREADY_JOINED -> "You're already in " + name + ".";
            case ALREADY_WAITLISTED ->
                "You're already **#" + result.waitlistPosition() + " on the waitlist** for " + name + ".";
            case CLOSED -> "Sign-ups for " + name + " are closed.";
            case NOT_FOUND -> "This tournament no longer exists.";
        };
        return reply;
    }

    private static String joinedMessage(Tournament tournament, String userId, String name, JoinResult result) {
        String message = "You're in " + name + " (" + result.activeCount() + "/" + tournament.getTargetSize() + ").";
        if (TournamentRoles.give(tournament, userId)) return message;
        Guild host = TournamentDiscord.hostGuild(tournament);
        if (host == null || TournamentDiscord.member(host, userId) != null) return message;
        return message + "\nThe games will be on **" + host.getName() + "**. Join it here: "
                + Helper.getGuildInviteURL(host, 1);
    }

    @ButtonHandler(value = TournamentSignupPosts.LEAVE, save = false)
    static void leave(ButtonInteractionEvent event, String buttonID) {
        Tournament tournament = tournamentOrReply(event, buttonID, TournamentSignupPosts.LEAVE);
        if (tournament == null) return;
        String userId = event.getUser().getId();
        LeaveResult result = TournamentService.getBean().leave(tournament.getId(), userId);
        if (result.refusal() != null) {
            TournamentDiscord.ephemeral(event, result.refusal());
            return;
        }
        if (!result.removed()) {
            TournamentDiscord.ephemeral(event, "You weren't signed up for **" + tournament.displayName() + "**.");
            return;
        }
        TournamentRoles.take(tournament, userId);
        TournamentDiscord.ephemeral(event, "You've withdrawn from **" + tournament.displayName() + "**.");
        TournamentSignupPosts.announcePromotion(tournament, result.promotedUserId(), event.getMessageChannel());
        TournamentSignupPosts.scheduleRefresh(tournament.getId());
    }

    @ButtonHandler(value = TournamentPlayTimes.OPEN, save = false)
    static void openPlayTimes(ButtonInteractionEvent event, String buttonID) {
        Tournament tournament = tournamentOrReply(event, buttonID, TournamentPlayTimes.OPEN);
        if (tournament == null) return;
        UserSettings settings = UserSettingsManager.get(event.getUser().getId());
        TournamentDiscord.ephemeralWithComponents(
                event,
                TournamentPlayTimes.pickerText(settings, false),
                TournamentPlayTimes.picker(tournament.getId(), settings, false));
    }

    @SelectionHandler(value = TournamentPlayTimes.ZONE_SELECT, save = false)
    static void timeZone(StringSelectInteractionEvent event) {
        Integer offset = parseOffset(event.getValues().getFirst());
        if (offset == null || !TournamentPlayTimes.isKnown(offset)) return;
        updatePlayTimes(event, TournamentPlayTimes.ZONE_SELECT, settings -> settings.setUtcOffsetMinutes(offset));
    }

    @SelectionHandler(value = TournamentPlayTimes.PARTS_SELECT, save = false)
    static void playTimeParts(StringSelectInteractionEvent event) {
        List<String> parts = TournamentPlayTimes.parts(event.getValues()).stream()
                .map(Enum::name)
                .toList();
        if (parts.isEmpty()) return;
        updatePlayTimes(event, TournamentPlayTimes.PARTS_SELECT, settings -> settings.setPlayTimeParts(parts));
    }

    private static void updatePlayTimes(
            StringSelectInteractionEvent event, String prefix, Consumer<UserSettings> change) {
        if (TournamentService.isUnavailable()) {
            TournamentDiscord.ephemeral(event, TournamentService.UNAVAILABLE);
            return;
        }
        String arg = event.getComponentId().substring(prefix.length());
        boolean joining = arg.endsWith(TournamentPlayTimes.JOINING_SUFFIX);
        Optional<Long> id = parseId(StringUtils.removeEnd(arg, TournamentPlayTimes.JOINING_SUFFIX), "");
        if (id.isEmpty()) return;
        UserSettings settings = UserSettingsManager.get(event.getUser().getId());
        change.accept(settings);
        UserSettingsManager.save(settings);
        if (!event.getMessage().isEphemeral()) {
            TournamentDiscord.ephemeral(event, "Saved.\n" + TournamentPlayTimes.summary(settings));
            return;
        }
        event.getHook()
                .editOriginal(TournamentPlayTimes.pickerText(settings, joining))
                .setComponents(TournamentPlayTimes.picker(id.get(), settings, joining))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static Integer parseOffset(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @ButtonHandler(value = TournamentSignupPosts.STATUS, save = false)
    static void status(ButtonInteractionEvent event, String buttonID) {
        Tournament tournament = tournamentOrReply(event, buttonID, TournamentSignupPosts.STATUS);
        if (tournament == null) return;
        String userId = event.getUser().getId();
        List<TournamentEntry> entries = TournamentService.getBean().entries(tournament.getId());
        TournamentEntry entry = entries.stream()
                .filter(candidate -> candidate.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
        String name = "**" + tournament.displayName() + "**";
        String reply;
        if (entry == null) {
            reply = "You're not signed up for " + name + ".";
        } else if (entry.isWaitlisted()) {
            reply = "You're **#" + TournamentService.waitlistPosition(entries, userId) + " on the waitlist** for "
                    + name + ".";
        } else if (entry.getGameName() != null) {
            reply = "You're in " + name + ". Your game is **" + entry.getGameName() + "**.";
        } else {
            reply = "You're in " + name + ".";
        }
        TournamentDiscord.ephemeral(event, reply);
    }

    private static Tournament tournamentOrReply(ButtonInteractionEvent event, String buttonID, String prefix) {
        if (TournamentService.isUnavailable()) {
            TournamentDiscord.ephemeral(event, TournamentService.UNAVAILABLE);
            return null;
        }
        Tournament tournament = parseId(buttonID, prefix)
                .flatMap(id -> TournamentService.getBean().find(id))
                .orElse(null);
        if (tournament == null) TournamentDiscord.ephemeral(event, "This tournament no longer exists.");
        return tournament;
    }

    static Optional<Long> parseId(String buttonID, String prefix) {
        try {
            return Optional.of(Long.parseLong(buttonID.substring(prefix.length())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
