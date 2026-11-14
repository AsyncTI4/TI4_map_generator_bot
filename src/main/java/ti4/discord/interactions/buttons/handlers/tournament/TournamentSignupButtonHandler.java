package ti4.discord.interactions.buttons.handlers.tournament;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.helpers.Helper;
import ti4.service.tournament.TournamentDiscord;
import ti4.service.tournament.TournamentRoles;
import ti4.service.tournament.TournamentSignupPosts;
import ti4.service.tournament.TournamentTimeZones;
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
        TournamentDiscord.ephemeral(event, reply);
        TournamentSignupPosts.scheduleRefresh(tournament.getId());
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

    @SelectionHandler(value = TournamentTimeZones.SELECT, save = false)
    static void timeZone(StringSelectInteractionEvent event) {
        if (TournamentService.isUnavailable()) {
            TournamentDiscord.ephemeral(event, TournamentService.UNAVAILABLE);
            return;
        }
        int offset;
        try {
            offset = Integer.parseInt(event.getValues().getFirst());
        } catch (NumberFormatException e) {
            return;
        }
        if (!TournamentTimeZones.isKnown(offset)) return;
        UserSettings settings = UserSettingsManager.get(event.getUser().getId());
        settings.setUtcOffsetMinutes(offset);
        UserSettingsManager.save(settings);
        TournamentDiscord.ephemeral(
                event,
                "Saved your time zone as **" + TournamentTimeZones.describe(offset) + "**. Groups use it only when the"
                        + " bot has no recorded active hours or AFK hours for you. It stays saved for future tournaments.");
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
