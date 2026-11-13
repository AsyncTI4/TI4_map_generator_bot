package ti4.service.tournament.panel;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.logging.BotLogger;
import ti4.message.componentsV2.ComponentPanel;
import ti4.service.tournament.TournamentAccess;
import ti4.service.tournament.TournamentDiscord;
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
    private static final int ACCENT = 0x5865F2;
    private static final int WARNING_ACCENT = 0xF0B232;
    private static final int ARCHIVED_ACCENT = 0x99AAB5;
    private static final int MAX_NOTICE_LENGTH = 900;

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

    record View(List<ContainerChildComponent> body, List<ActionRow> controls, boolean warning, int pageCount) {

        static View of(List<ContainerChildComponent> body, List<ActionRow> controls) {
            return new View(body, controls, false, 1);
        }

        View withWarning(boolean warning) {
            return new View(body, controls, warning, pageCount);
        }

        View withPages(int pageCount) {
            return new View(body, controls, warning, pageCount);
        }
    }

    public static void post(Tournament tournament, MessageChannel channel, String notice) {
        post(tournament, channel, Tab.SETTINGS, 0, notice);
    }

    private static void post(Tournament tournament, MessageChannel channel, Tab tab, int page, String notice) {
        String oldChannelId = tournament.getPanelChannelId();
        String oldMessageId = tournament.getPanelMessageId();
        GuildMessageChannel oldChannel = TournamentDiscord.channel(oldChannelId);
        if (oldChannel != null && oldMessageId != null) {
            oldChannel.deleteMessageById(oldMessageId).queue(Consumers.nop(), BotLogger::catchRestError);
        }
        ComponentPanel.post(
                channel, build(load(tournament, tab, page), notice), message -> remember(tournament, message));
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
        Message message = ComponentPanel.messageOf(event);
        boolean onPanel = message != null && message.getId().equals(tournament.getPanelMessageId());
        if (!onPanel) {
            post(tournament, event.getMessageChannel(), tab, page, notice);
            return;
        }
        ComponentPanel.show(event, build(load(tournament, tab, page), notice));
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
        View view = viewFor(context);
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of(header(context)));
        if (StringUtils.isNotBlank(notice)) {
            children.add(TextDisplay.of(quote(StringUtils.abbreviate(notice.trim(), MAX_NOTICE_LENGTH))));
        }
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.addAll(view.body());

        List<MessageTopLevelComponent> components = new ArrayList<>();
        components.add(Container.of(children).withAccentColor(view.warning() ? WARNING_ACCENT : ACCENT));
        components.addAll(view.controls());
        components.add(ActionRow.of(ComponentPanel.tabButtons(
                List.of(Tab.values()), context.tab(), tab -> TAB + context.id() + "_" + tab.name(), tab -> tab.label)));
        components.add(ActionRow.of(utilityButtons(context, view.pageCount())));
        if (!ComponentPanel.fits(components) && StringUtils.isNotBlank(notice)) return build(context, null);
        return components;
    }

    private static View viewFor(Context context) {
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
        long seated = context.entries().stream()
                .filter(entry -> !entry.isWaitlisted())
                .count();
        long waitlisted = context.entries().size() - seated;
        long groups = TournamentService.groups(context.entries()).size();
        Guild host = TournamentDiscord.hostGuild(tournament);
        String title = "### Tournament · " + tournament.displayName() + (tournament.isFogMode() ? " · Fog of War" : "");
        String summary = statusLabel(tournament.getStatus()) + " · " + seated + "/" + tournament.getTargetSize()
                + " players" + (waitlisted > 0 ? " · " + waitlisted + " waitlisted" : "")
                + (groups > 0 ? " · " + groups + " groups" : "")
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
            String base = PAGE + id + "_" + context.tab().name() + "_";
            buttons.add(Buttons.gray(base + Math.max(0, page - 1), "◀").withDisabled(page == 0));
            buttons.add(Buttons.gray(base + "x" + Math.min(pageCount - 1, page + 1), "▶")
                    .withDisabled(page >= pageCount - 1));
        }
        return buttons;
    }

    static String quote(String text) {
        return "> " + text.replace("\n", "\n> ");
    }

    static long idOf(String componentId, String prefix) {
        return Long.parseLong(StringUtils.substringBefore(componentId.substring(prefix.length()), "_"));
    }

    static String argOf(String componentId, String prefix) {
        return StringUtils.substringAfter(componentId.substring(prefix.length()), "_");
    }

    static TextInput input(String id, TextInputStyle style, String value, boolean required) {
        TextInput.Builder builder = TextInput.create(id, style).setRequired(required);
        if (StringUtils.isNotBlank(value)) builder.setValue(value);
        return builder.build();
    }

    static Modal namesModal(String modalId, String title, String label, String placeholder) {
        TextInput names = TextInput.create("names", TextInputStyle.PARAGRAPH)
                .setRequired(true)
                .setPlaceholder(placeholder)
                .build();
        return Modal.create(modalId, title)
                .addComponents(Label.of(label, names))
                .build();
    }

    static String value(ModalInteractionEvent event, String field) {
        ModalMapping mapping = event.getValue(field);
        return mapping == null ? "" : mapping.getAsString().trim();
    }

    static Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String names(List<String> userIds) {
        return String.join(", ", TournamentDiscord.userNames(userIds));
    }

    @ButtonHandler(value = TAB, save = false)
    static void tab(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        show(event, idOf(buttonID, TAB), Tab.valueOf(argOf(buttonID, TAB)), null);
    }

    @ButtonHandler(value = PAGE, save = false)
    static void page(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        String[] parts = argOf(buttonID, PAGE).split("_");
        int page = Integer.parseInt(parts[1].replace("x", ""));
        show(event, idOf(buttonID, PAGE), Tab.valueOf(parts[0]), page, null);
    }

    @ButtonHandler(value = REPOST, save = false)
    static void repost(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        TournamentService.getBean()
                .find(idOf(buttonID, REPOST))
                .ifPresent(tournament -> post(tournament, event.getMessageChannel(), null));
    }

    @ButtonHandler(value = SIGNUPS, save = false)
    static void toggleSignups(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        long id = idOf(buttonID, SIGNUPS);
        TournamentService.getBean().update(id, pool -> {
            if (pool.getStatus() == TournamentStatus.OPEN) pool.setStatus(TournamentStatus.CLOSED);
            else if (pool.getStatus() == TournamentStatus.CLOSED) pool.setStatus(TournamentStatus.OPEN);
        });
        TournamentSignupPosts.refreshNow(id);
        show(event, id, Tab.SETTINGS, null);
    }
}
