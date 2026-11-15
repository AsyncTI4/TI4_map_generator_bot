package ti4.service.tournament.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.ModalTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.logging.BotLogger;
import ti4.message.componentsV2.ComponentPanel;
import ti4.message.componentsV2.PanelForms;
import ti4.message.componentsV2.PanelIds;
import ti4.message.componentsV2.PanelView;
import ti4.service.tournament.TournamentAccess;
import ti4.service.tournament.TournamentDiscord;
import ti4.service.tournament.TournamentGameCreator;
import ti4.service.tournament.TournamentMemberResolver;
import ti4.service.tournament.TournamentMemberResolver.Resolution;
import ti4.service.tournament.TournamentSignupPosts;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentEntry;
import ti4.spring.service.tournament.TournamentService;
import ti4.spring.service.tournament.TournamentStatus;

public final class TournamentPanel {

    static final String TAB = "trnTab_";
    static final String PAGE = "trnPage_";
    private static final String REPOST = "trnRepost_";
    private static final String SIGNUPS = "trnSignups_";
    private static final int ARCHIVED_ACCENT = 0x99AAB5;
    private static final String PUBLIC_FOG_WARNING = "⚠️ This channel is visible to everyone. In a Fog of War"
            + " tournament, keep the panel in a private channel so groups and GMs stay hidden. Rosters and results"
            + " are only shown to you.";
    private static final String NAMES_PLACEHOLDER = "One per line: @mention, user id or name";

    private TournamentPanel() {}

    public enum Tab {
        SETTINGS("Settings"),
        PLAYERS("Players"),
        GROUPS("Groups"),
        SETUP("Game setup"),
        CREATE("Create");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    record Context(Tournament tournament, List<TournamentEntry> entries, Tab tab, int page) {

        long id() {
            return tournament.getId();
        }
    }

    public static void post(Tournament tournament, MessageChannel channel, String notice) {
        post(tournament, channel, Tab.SETTINGS, 0, notice);
    }

    private static void post(Tournament tournament, MessageChannel channel, Tab tab, int page, String notice) {
        ComponentPanel.repost(
                channel,
                TournamentDiscord.channel(tournament.getPanelChannelId()),
                tournament.getPanelMessageId(),
                build(load(tournament, tab, page), notice),
                message -> remember(tournament, message));
    }

    private static void remember(Tournament tournament, Message message) {
        TournamentService.getBean().update(tournament.getId(), pool -> {
            pool.setPanelChannelId(message.getChannel().getId());
            pool.setPanelMessageId(message.getId());
        });
    }

    public static void refresh(long id, String notice) {
        Tournament tournament = TournamentService.getBean().find(id).orElse(null);
        if (tournament == null) return;
        GuildMessageChannel channel = TournamentDiscord.channel(tournament.getPanelChannelId());
        if (channel == null || tournament.getPanelMessageId() == null) return;
        channel.editMessageComponentsById(
                        tournament.getPanelMessageId(), build(load(tournament, Tab.CREATE, 0), notice))
                .useComponentsV2()
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    static void show(GenericInteractionCreateEvent event, long id, Tab tab, int page, String notice) {
        Tournament tournament = TournamentService.getBean().find(id).orElse(null);
        if (tournament == null) {
            TournamentDiscord.ephemeral(event, "This tournament no longer exists.");
            return;
        }
        ComponentPanel.showOrRepost(
                event,
                tournament.getPanelMessageId(),
                () -> build(load(tournament, tab, page), notice),
                () -> post(tournament, event.getMessageChannel(), tab, page, notice));
    }

    static void show(GenericInteractionCreateEvent event, long id, Tab tab, String notice) {
        show(event, id, tab, 0, notice);
    }

    private static Context load(Tournament tournament, Tab tab, int page) {
        return new Context(tournament, TournamentService.getBean().entries(tournament.getId()), tab, page);
    }

    static List<MessageTopLevelComponent> build(Context context, String notice) {
        Tournament tournament = context.tournament();
        if (tournament.getStatus() == TournamentStatus.ARCHIVED) {
            return List.of(Container.of(TextDisplay.of(header(context) + "\nThis tournament is archived."))
                    .withAccentColor(ARCHIVED_ACCENT));
        }
        PanelView view = viewFor(context);
        if (tournament.isFogMode() && TournamentDiscord.isPanelPublic(tournament)) {
            view = view.withLead(TextDisplay.of(PUBLIC_FOG_WARNING)).withWarning(true);
        }
        List<ActionRow> navigation = List.of(
                ActionRow.of(ComponentPanel.tabButtons(
                        List.of(Tab.values()),
                        context.tab(),
                        tab -> PanelIds.of(TAB, context.id(), tab.name()),
                        tab -> tab.label)),
                ActionRow.of(utilityButtons(context, view.pageCount())));
        return ComponentPanel.compose(header(context), notice, view, navigation);
    }

    private static PanelView viewFor(Context context) {
        return switch (context.tab()) {
            case SETTINGS -> TournamentPanelSettings.view(context);
            case PLAYERS -> TournamentPanelPlayers.view(context);
            case GROUPS -> TournamentPanelGroups.view(context);
            case SETUP -> TournamentPanelSetup.view(context);
            case CREATE -> TournamentPanelCreate.view(context);
        };
    }

    private static String header(Context context) {
        Tournament tournament = context.tournament();
        int seated = TournamentService.activeCount(context.entries());
        int waitlisted = context.entries().size() - seated;
        long groups = TournamentService.groups(context.entries()).size();
        Guild host = TournamentDiscord.hostGuild(tournament);
        long notOnHost = context.entries().stream()
                .filter(entry -> !entry.isWaitlisted())
                .filter(entry -> TournamentDiscord.isMissingFrom(host, entry.getUserId()))
                .count();
        String title = "### Tournament · " + tournament.displayName() + (tournament.isFogMode() ? " · Fog of War" : "");
        String summary = statusLabel(tournament.getStatus()) + " · " + seated + "/" + tournament.getTargetSize()
                + " players" + (waitlisted > 0 ? " · " + waitlisted + " waitlisted" : "")
                + (groups > 0 ? " · " + groups + " groups" : "")
                + (notOnHost > 0 ? " · " + notOnHost + " not on host" : "")
                + " · " + (host == null ? "no host server" : host.getName());
        return title + "\n-# " + summary;
    }

    static String statusLabel(TournamentStatus status) {
        return switch (status) {
            case OPEN -> "Sign-ups open";
            case CLOSED -> "Sign-ups closed";
            case GROUPED -> "Groups approved";
            case CREATING -> "Creating games";
            case CREATED -> "Games created";
            case ARCHIVED -> "Archived";
        };
    }

    private static List<Button> utilityButtons(Context context, int pageCount) {
        long id = context.id();
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray(REPOST + id, "Repost"));
        TournamentStatus status = context.tournament().getStatus();
        if (status == TournamentStatus.OPEN) {
            buttons.add(Buttons.red(SIGNUPS + id, "Close sign-ups"));
        } else if (status == TournamentStatus.CLOSED) {
            buttons.add(Buttons.green(SIGNUPS + id, "Reopen sign-ups"));
        }
        if (pageCount > 1) {
            int page = Math.min(context.page(), pageCount - 1);
            String tab = context.tab().name();
            String previous = PanelIds.of(PAGE, id, tab, Math.max(0, page - 1));
            String next = PanelIds.of(PAGE, id, tab, "x" + Math.min(pageCount - 1, page + 1));
            buttons.add(Buttons.gray(previous, "◀").withDisabled(page == 0));
            buttons.add(Buttons.gray(next, "▶").withDisabled(page >= pageCount - 1));
        }
        return buttons;
    }

    static boolean canEditGroupsWithoutGame(Tournament tournament) {
        return tournament.getStatus() == TournamentStatus.CREATING
                && !TournamentGameCreator.isRunning(tournament.getId());
    }

    static BooleanSupplier creationRunning(long id) {
        return () -> TournamentGameCreator.isRunning(id);
    }

    static void openNamesModal(
            ButtonInteractionEvent event, String id, String title, String label, ModalTopLevelComponent... extras) {
        TextInput names = TextInput.create("names", TextInputStyle.PARAGRAPH)
                .setRequired(true)
                .setPlaceholder(NAMES_PLACEHOLDER)
                .build();
        PanelForms.open(event, id, title, ArrayUtils.addFirst(extras, Label.of(label, names)));
    }

    static void change(ButtonInteractionEvent event, long id, Tab tab, Consumer<Tournament> change, String notice) {
        if (!TournamentAccess.require(event)) return;
        TournamentService.getBean().update(id, change);
        show(event, id, tab, notice);
    }

    static Resolution resolve(Tournament tournament, String text) {
        return TournamentMemberResolver.resolve(text, TournamentDiscord.lookupGuilds(tournament));
    }

    static String names(List<String> userIds) {
        return String.join(", ", TournamentDiscord.userNames(userIds));
    }

    @ButtonHandler(value = TAB, save = false)
    static void tab(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        show(event, PanelIds.key(buttonID, TAB), Tab.valueOf(PanelIds.arg(buttonID, TAB)), null);
    }

    @ButtonHandler(value = PAGE, save = false)
    static void page(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        String[] parts = PanelIds.arg(buttonID, PAGE).split("_");
        int page = Integer.parseInt(parts[1].replace("x", ""));
        show(event, PanelIds.key(buttonID, PAGE), Tab.valueOf(parts[0]), page, null);
    }

    @ButtonHandler(value = REPOST, save = false)
    static void repost(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        TournamentService.getBean()
                .find(PanelIds.key(buttonID, REPOST))
                .ifPresent(tournament -> post(tournament, event.getMessageChannel(), null));
    }

    @ButtonHandler(value = SIGNUPS, save = false)
    static void toggleSignups(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        long id = PanelIds.key(buttonID, SIGNUPS);
        TournamentService.getBean().update(id, pool -> {
            if (pool.getStatus() == TournamentStatus.OPEN) pool.setStatus(TournamentStatus.CLOSED);
            else if (pool.getStatus() == TournamentStatus.CLOSED) pool.setStatus(TournamentStatus.OPEN);
        });
        TournamentSignupPosts.refreshNow(id);
        show(event, id, Tab.SETTINGS, null);
    }
}
