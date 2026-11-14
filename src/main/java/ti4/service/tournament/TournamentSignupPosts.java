package ti4.service.tournament;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.executors.ExecutorServiceManager;
import ti4.logging.BotLogger;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentEntry;
import ti4.spring.service.tournament.TournamentService;
import ti4.spring.service.tournament.TournamentSignupPost;
import ti4.spring.service.tournament.TournamentStatus;

@UtilityClass
public class TournamentSignupPosts {

    public static final String JOIN = "trnJoin_";
    public static final String LEAVE = "trnLeave_";
    public static final String STATUS = "trnStatus_";
    private static final int ACCENT = 0x57F287;
    private static final int CLOSED_ACCENT = 0x99AAB5;
    private static final long REFRESH_DELAY_SECONDS = 4;
    private static final Set<Long> PENDING_REFRESHES = ConcurrentHashMap.newKeySet();

    public static List<MessageTopLevelComponent> card(Tournament tournament, List<TournamentEntry> entries) {
        long active = entries.stream().filter(entry -> !entry.isWaitlisted()).count();
        long waitlisted = entries.size() - active;
        String text = "## " + tournament.getSeriesName() + " · " + tournament.getRoundLabel()
                + "\n" + formatLine(tournament)
                + "\n**" + active + "/" + tournament.getTargetSize() + "** players signed up"
                + (waitlisted > 0 ? " · **" + waitlisted + "** on the waitlist" : "")
                + "\n-# " + statusLine(tournament.getStatus());
        List<MessageTopLevelComponent> components = new ArrayList<>();
        boolean open = tournament.getStatus().acceptsSignups();
        components.add(Container.of(TextDisplay.of(text)).withAccentColor(open ? ACCENT : CLOSED_ACCENT));
        if (tournament.getStatus().isActive()) {
            components.add(ActionRow.of(buttons(tournament)));
        }
        if (tournament.getStatus().allowsRosterChanges()) {
            components.add(ActionRow.of(TournamentTimeZones.select(tournament.getId())));
        }
        return components;
    }

    public static String formatLine(Tournament tournament) {
        return (tournament.isFogMode() ? "Fog of War · " : "") + tournament.getGroupSize() + " players per game";
    }

    private static String statusLine(TournamentStatus status) {
        return switch (status) {
            case OPEN -> "Press **I will play** to enter. That's all you need to do.";
            case CLOSED, GROUPED -> "Sign-ups are closed.";
            case CREATING -> "Games are being created.";
            case CREATED -> "Games have been created. Good luck!";
            case ARCHIVED -> "This tournament is over.";
        };
    }

    private static List<Button> buttons(Tournament tournament) {
        long id = tournament.getId();
        List<Button> buttons = new ArrayList<>();
        if (tournament.getStatus().acceptsSignups()) {
            buttons.add(Buttons.green(JOIN + id, "I will play"));
        }
        if (tournament.getStatus().allowsRosterChanges()) {
            buttons.add(Buttons.red(LEAVE + id, "Withdraw"));
        }
        buttons.add(Buttons.gray(STATUS + id, "My status"));
        return buttons;
    }

    public static void post(Tournament tournament, GuildMessageChannel channel) {
        List<TournamentEntry> entries = TournamentService.getBean().entries(tournament.getId());
        channel.sendMessageComponents(card(tournament, entries))
                .useComponentsV2()
                .queue(
                        message -> TournamentService.getBean()
                                .addSignupPost(
                                        tournament.getId(),
                                        channel.getGuild().getId(),
                                        channel.getId(),
                                        message.getId()),
                        BotLogger::catchRestError);
    }

    public static void scheduleRefresh(long tournamentId) {
        if (!PENDING_REFRESHES.add(tournamentId)) return;
        CompletableFuture.delayedExecutor(REFRESH_DELAY_SECONDS, TimeUnit.SECONDS)
                .execute(() -> ExecutorServiceManager.runAsync("Tournament sign-up refresh " + tournamentId, () -> {
                    PENDING_REFRESHES.remove(tournamentId);
                    refreshNow(tournamentId);
                }));
    }

    public static void refreshNow(long tournamentId) {
        TournamentService service = TournamentService.getBean();
        Tournament tournament = service.find(tournamentId).orElse(null);
        if (tournament == null) return;
        List<MessageTopLevelComponent> card = card(tournament, service.entries(tournamentId));
        for (TournamentSignupPost post : service.signupPosts(tournamentId)) {
            GuildMessageChannel channel = TournamentDiscord.channel(post.getChannelId());
            if (channel == null) {
                service.removeSignupPost(post.getId());
                continue;
            }
            channel.editMessageComponentsById(post.getMessageId(), card)
                    .useComponentsV2()
                    .queue(Consumers.nop(), error -> forgetDeletedPost(post, error));
        }
    }

    public static void delete(TournamentSignupPost post) {
        TournamentService.getBean().removeSignupPost(post.getId());
        GuildMessageChannel channel = TournamentDiscord.channel(post.getChannelId());
        if (channel == null) return;
        channel.deleteMessageById(post.getMessageId()).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static String describe(TournamentSignupPost post) {
        GuildMessageChannel channel = TournamentDiscord.channel(post.getChannelId());
        if (channel == null) return "deleted channel";
        return "#" + channel.getName() + " (" + channel.getGuild().getName() + ")";
    }

    private static void forgetDeletedPost(TournamentSignupPost post, Throwable error) {
        if (error instanceof ErrorResponseException response
                && response.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
            TournamentService.getBean().removeSignupPost(post.getId());
            return;
        }
        BotLogger.catchRestError(error);
    }
}
