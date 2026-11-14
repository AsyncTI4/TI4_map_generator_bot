package ti4.service.tournament.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.tournament.TournamentAccess;
import ti4.service.tournament.TournamentDiscord;
import ti4.service.tournament.TournamentGameCreator;
import ti4.service.tournament.TournamentGameCreator.Preflight;
import ti4.service.tournament.TournamentInvites;
import ti4.service.tournament.TournamentMemberResolver;
import ti4.service.tournament.TournamentMemberResolver.Resolution;
import ti4.service.tournament.TournamentPacing;
import ti4.service.tournament.TournamentRoles;
import ti4.service.tournament.TournamentSetupOptions;
import ti4.service.tournament.TournamentSignupPosts;
import ti4.service.tournament.panel.TournamentPanel.Context;
import ti4.service.tournament.panel.TournamentPanel.Tab;
import ti4.service.tournament.panel.TournamentPanel.View;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentEntry;
import ti4.spring.service.tournament.TournamentService;
import ti4.spring.service.tournament.TournamentStatus;
import ti4.spring.service.tournamentwinner.TourneyWinnerService;

final class TournamentPanelCreate {

    private static final String INVITE_MODAL = "trnInvite~MDL_";
    private static final String INVITE_SAVE = "trnInviteSave_";
    private static final String INVITE_COPY = "trnInviteCopy_";
    private static final String CREATE_ASK = "trnCreateAsk_";
    private static final String CREATE_GO = "trnCreateGo_";
    private static final String ARCHIVE_ASK = "trnArchiveAsk_";
    private static final String ARCHIVE_GO = "trnArchiveGo_";
    private static final String NEXT_ROUND_MODAL = "trnNextRound~MDL_";
    private static final String NEXT_ROUND_SAVE = "trnNextRoundSave_";
    private static final String WINNERS_MODAL = "trnWinners~MDL_";
    private static final String WINNERS_SAVE = "trnWinnersSave_";
    private static final String BROADCAST_MODAL = "trnBroadcast~MDL_";
    private static final String BROADCAST_SAVE = "trnBroadcastSave_";
    private static final String APPLY_SETUP = "trnApplySetup_";
    private static final int BROADCAST_MAX_LENGTH = 1800;
    private static final int CONFIRM_CREATE = 1;
    private static final int CONFIRM_ARCHIVE = 2;
    private static final int MAX_MISSING_LINES = 10;

    private TournamentPanelCreate() {}

    static View view(Context context) {
        Tournament tournament = context.tournament();
        Preflight preflight = TournamentGameCreator.preflight(tournament, context.entries());
        boolean running = TournamentGameCreator.isRunning(tournament.getId());
        List<String> lines = new ArrayList<>();
        lines.add(intro(tournament, preflight, running));
        List<String> warnings = warnings(tournament, preflight);
        List<ContainerChildComponent> body = new ArrayList<>();
        body.add(TextDisplay.of(String.join("\n", lines)));
        if (!warnings.isEmpty()) body.add(TextDisplay.of(String.join("\n", warnings)));
        return View.of(body, controls(context, preflight, running)).withWarning(!warnings.isEmpty());
    }

    private static String intro(Tournament tournament, Preflight preflight, boolean running) {
        Guild host = TournamentDiscord.hostGuild(tournament);
        String hostName = host == null ? "no server" : host.getName();
        return switch (tournament.getStatus()) {
            case OPEN, CLOSED -> "Approve the groups on the **Groups** tab first.";
            case GROUPED ->
                "**" + preflight.pendingGames() + "** of " + preflight.groupCount() + " games still to create on **"
                        + hostName + "**.";
            case CREATING ->
                running
                        ? "Creating games on **" + hostName + "**…"
                        : "A creation run was interrupted. Press **Create** to continue where it stopped.";
            case CREATED -> "All " + preflight.groupCount() + " games are created.";
            case ARCHIVED -> "This tournament is archived.";
        };
    }

    private static List<String> warnings(Tournament tournament, Preflight preflight) {
        List<String> warnings = new ArrayList<>();
        if (preflight.capacityProblem() != null && preflight.pendingGames() > 0) {
            warnings.add("⚠️ " + preflight.capacityProblem());
        }
        if (preflight.hasMissingPlayers()) {
            Guild host = TournamentDiscord.hostGuild(tournament);
            warnings.add("⚠️ Not on **" + (host == null ? "the host server" : host.getName()) + "** yet"
                    + (tournament.isFogMode()
                            ? " (these games are skipped until everyone has joined; use **Send invite…** or **Copy invite**):"
                            : " (use **Send invite…** or **Copy invite**):"));
            preflight.missingFromHostByGroup().entrySet().stream()
                    .limit(MAX_MISSING_LINES)
                    .forEach(group -> warnings.add(
                            "-# Group " + group.getKey() + ": " + TournamentPanel.names(group.getValue())));
            if (preflight.missingFromHostByGroup().size() > MAX_MISSING_LINES) {
                warnings.add(
                        "-# …and " + (preflight.missingFromHostByGroup().size() - MAX_MISSING_LINES) + " more groups");
            }
        }
        if (!preflight.takenNames().isEmpty()) {
            warnings.add("⚠️ These names are already used by a game or role, so those games are skipped: "
                    + String.join(", ", preflight.takenNames()) + ". Pick another prefix in Settings.");
        }
        if (tournament.isFogMode() && !preflight.groupsWithoutGameMaster().isEmpty()) {
            warnings.add("⚠️ No GM for group(s) " + StringUtils.join(preflight.groupsWithoutGameMaster(), ", ")
                    + ". Those games are skipped.");
        }
        if (tournament.isFogMode() && panelIsPublic(tournament)) {
            warnings.add("⚠️ This channel is visible to everyone. In a Fog of War tournament, keep the panel in a"
                    + " private channel so groups and GMs stay hidden.");
        }
        return warnings;
    }

    private static boolean panelIsPublic(Tournament tournament) {
        GuildMessageChannel channel = TournamentDiscord.channel(tournament.getPanelChannelId());
        if (channel == null) return false;
        return channel.getGuild()
                .getPublicRole()
                .hasPermission(channel.getPermissionContainer(), Permission.VIEW_CHANNEL);
    }

    private static List<ActionRow> controls(Context context, Preflight preflight, boolean running) {
        long id = context.id();
        TournamentStatus status = context.tournament().getStatus();
        String cancel = TournamentPanel.PAGE + id + "_" + Tab.CREATE.name() + "_0";
        if (context.page() == CONFIRM_CREATE) {
            return List.of(ActionRow.of(
                    Buttons.green(CREATE_GO + id, "Yes, create " + preflight.pendingGames() + " games"),
                    Buttons.gray(cancel, "Cancel")));
        }
        if (context.page() == CONFIRM_ARCHIVE) {
            return List.of(ActionRow.of(
                    Buttons.red(ARCHIVE_GO + id, "Yes, archive this tournament"), Buttons.gray(cancel, "Cancel")));
        }
        if (running) return List.of();
        List<Button> buttons = new ArrayList<>();
        boolean canCreate = (status == TournamentStatus.GROUPED || status == TournamentStatus.CREATING)
                && preflight.pendingGames() > 0;
        if (canCreate) {
            buttons.add(Buttons.green(CREATE_ASK + id, "Create " + preflight.pendingGames() + " games"));
        }
        if (status == TournamentStatus.CREATED) {
            buttons.add(Buttons.blue(NEXT_ROUND_MODAL + id, "Start next round"));
            buttons.add(Buttons.gray(WINNERS_MODAL + id, "Record winners"));
        }
        buttons.add(Buttons.red(ARCHIVE_ASK + id, "Archive"));
        List<Button> sharing = new ArrayList<>();
        sharing.add(Buttons.blue(INVITE_MODAL + id, "Send invite…"));
        sharing.add(Buttons.gray(INVITE_COPY + id, "Copy invite"));
        if (!createdGameNames(context.entries()).isEmpty()) {
            sharing.add(Buttons.blue(BROADCAST_MODAL + id, "Message all games"));
            sharing.add(Buttons.gray(APPLY_SETUP + id, "Apply game setup to all games"));
        }
        return List.of(ActionRow.of(buttons), ActionRow.of(sharing));
    }

    static List<String> createdGameNames(List<TournamentEntry> entries) {
        return entries.stream()
                .map(TournamentEntry::getGameName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @ButtonHandler(value = BROADCAST_MODAL, save = false)
    static void openBroadcast(ButtonInteractionEvent event, String buttonID) {
        long id = TournamentPanel.idOf(buttonID, BROADCAST_MODAL);
        TextInput message = TextInput.create("message", TextInputStyle.PARAGRAPH)
                .setRequired(true)
                .setMaxLength(BROADCAST_MAX_LENGTH)
                .build();
        Modal modal = Modal.create(BROADCAST_SAVE + id, "Message all games")
                .addComponents(Label.of("Posted in every game's main channel", message))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(value = BROADCAST_SAVE, save = false)
    static void broadcast(ModalInteractionEvent event) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(event.getModalId(), BROADCAST_SAVE);
        TournamentService service = TournamentService.getBean();
        Tournament tournament = service.find(id).orElseThrow();
        String text = "**Message from the " + tournament.getSeriesName() + " organizers:**\n"
                + TournamentPanel.value(event, "message");
        List<String> gameNames = createdGameNames(service.entries(id));
        boolean started = TournamentPacing.forEach(
                "tournamentBroadcast" + id,
                gameNames,
                gameName -> sendToGame(gameName, text),
                result -> TournamentPanel.refresh(id, result.summary("Sent the message to")));
        TournamentPanel.show(
                event,
                id,
                Tab.CREATE,
                started
                        ? "Sending the message to " + gameNames.size() + " game(s), one at a time."
                        : "A message is already being sent, or the bot is under load. Try again in a minute.");
    }

    private static String sendToGame(String gameName, String text) {
        MessageChannel channel = GameManager.isValid(gameName)
                ? GameManager.getManagedGame(gameName).getGame().getMainGameChannel()
                : null;
        if (channel == null) return "Couldn't find " + gameName + ".";
        MessageHelper.sendMessageToChannel(channel, text);
        return null;
    }

    @ButtonHandler(value = APPLY_SETUP, save = false)
    static void applySetup(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(buttonID, APPLY_SETUP);
        TournamentService service = TournamentService.getBean();
        Tournament tournament = service.find(id).orElseThrow();
        if (TournamentSetupOptions.all().isEmpty()) {
            TournamentPanel.show(event, id, Tab.CREATE, "There are no game setup options yet.");
            return;
        }
        Map<String, String> values = TournamentService.gameSetup(tournament);
        List<String> gameNames = createdGameNames(service.entries(id));
        boolean started = TournamentPacing.forEach(
                "tournamentApplySetup" + id,
                gameNames,
                gameName -> TournamentGameCreator.applySetup(gameName, values),
                result -> TournamentPanel.refresh(id, result.summary("Applied the game setup to")));
        TournamentPanel.show(
                event,
                id,
                Tab.CREATE,
                started
                        ? "Applying the game setup to " + gameNames.size() + " game(s), one at a time."
                        : "The setup is already being applied, or the bot is under load. Try again in a minute.");
    }

    @ButtonHandler(value = INVITE_MODAL, save = false)
    static void openInvite(ButtonInteractionEvent event, String buttonID) {
        long id = TournamentPanel.idOf(buttonID, INVITE_MODAL);
        Tournament tournament = TournamentService.getBean().find(id).orElse(null);
        String suggestion = tournament == null ? null : TournamentInvites.defaultChannelLink(tournament);
        Modal modal = Modal.create(INVITE_SAVE + id, "Send the host server invite")
                .addComponents(
                        TextDisplay.of("Any channel on any server the bot is in. Players who aren't on the host"
                                + " server yet are pinged."),
                        Label.of(
                                "Channel link, #mention or id",
                                TournamentPanel.input("channel", TextInputStyle.SHORT, suggestion, true)))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(value = INVITE_SAVE, save = false)
    static void sendInvite(ModalInteractionEvent event) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(event.getModalId(), INVITE_SAVE);
        Tournament tournament = TournamentService.getBean().find(id).orElseThrow();
        GuildMessageChannel channel = TournamentInvites.resolveChannel(TournamentPanel.value(event, "channel"));
        String notice = channel == null
                ? "Couldn't find that channel. Paste a channel link (right click › Copy Link), #mention or id."
                : TournamentInvites.send(tournament, channel);
        TournamentPanel.show(event, id, Tab.CREATE, notice);
    }

    @ButtonHandler(value = INVITE_COPY, save = false)
    static void copyInvite(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(buttonID, INVITE_COPY);
        Tournament tournament = TournamentService.getBean().find(id).orElseThrow();
        String text = TournamentInvites.text(tournament);
        if (text == null) {
            TournamentDiscord.ephemeral(event, "Pick a host server first.");
            return;
        }
        TournamentDiscord.ephemeral(
                event, "Copy this and paste it anywhere:\n```\n" + StringUtils.abbreviate(text, 1900) + "\n```");
    }

    @ButtonHandler(value = CREATE_ASK, save = false)
    static void askCreate(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        TournamentPanel.show(event, TournamentPanel.idOf(buttonID, CREATE_ASK), Tab.CREATE, CONFIRM_CREATE, null);
    }

    @ButtonHandler(value = CREATE_GO, save = false)
    static void create(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(buttonID, CREATE_GO);
        Tournament tournament = TournamentService.getBean().find(id).orElseThrow();
        boolean ready = tournament.getStatus() == TournamentStatus.GROUPED
                || (tournament.getStatus() == TournamentStatus.CREATING && !TournamentGameCreator.isRunning(id));
        if (!ready) {
            TournamentPanel.show(event, id, Tab.CREATE, "Approve the groups first.");
            return;
        }
        String error = TournamentGameCreator.start(tournament);
        TournamentPanel.show(
                event,
                id,
                Tab.CREATE,
                error == null ? "Started creating games. This panel updates as it goes." : error);
    }

    @ButtonHandler(value = ARCHIVE_ASK, save = false)
    static void askArchive(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        TournamentPanel.show(event, TournamentPanel.idOf(buttonID, ARCHIVE_ASK), Tab.CREATE, CONFIRM_ARCHIVE, null);
    }

    @ButtonHandler(value = ARCHIVE_GO, save = false)
    static void archive(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(buttonID, ARCHIVE_GO);
        if (TournamentGameCreator.isRunning(id)) {
            TournamentPanel.show(event, id, Tab.CREATE, "Wait until game creation has finished.");
            return;
        }
        Tournament tournament = TournamentService.getBean()
                .update(id, pool -> pool.setStatus(TournamentStatus.ARCHIVED))
                .orElseThrow();
        TournamentSignupPosts.refreshNow(id);
        TournamentRoles.deleteIfUnused(tournament);
        TournamentPanel.show(event, id, Tab.CREATE, null);
    }

    @ButtonHandler(value = NEXT_ROUND_MODAL, save = false)
    static void openNextRound(ButtonInteractionEvent event, String buttonID) {
        long id = TournamentPanel.idOf(buttonID, NEXT_ROUND_MODAL);
        Modal modal = Modal.create(NEXT_ROUND_SAVE + id, "Start the next round")
                .addComponents(Label.of(
                        "Round name", TournamentPanel.input("round", TextInputStyle.SHORT, "Semifinals", true)))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(value = NEXT_ROUND_SAVE, save = false)
    static void nextRound(ModalInteractionEvent event) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(event.getModalId(), NEXT_ROUND_SAVE);
        String round = StringUtils.left(TournamentPanel.value(event, "round"), 40);
        if (round.isBlank()) {
            TournamentPanel.show(event, id, Tab.CREATE, "The round needs a name.");
            return;
        }
        Tournament next =
                TournamentService.getBean().nextRound(id, round, event.getUser().getId());
        TournamentPanel.post(
                next,
                event.getMessageChannel(),
                "New round created with the same settings and an empty pool. Bulk add the players on the **Players**"
                        + " tab, or reopen sign-ups and use `/tournament post_signup`. Game names are back to pbd"
                        + " numbers; set a new prefix in Settings for custom names.");
        TournamentPanel.show(event, id, Tab.CREATE, "Started **" + next.displayName() + "**. Its panel is below.");
    }

    @ButtonHandler(value = WINNERS_MODAL, save = false)
    static void openWinners(ButtonInteractionEvent event, String buttonID) {
        long id = TournamentPanel.idOf(buttonID, WINNERS_MODAL);
        event.replyModal(TournamentPanel.namesModal(
                        WINNERS_SAVE + id,
                        "Record tournament winners",
                        "Winners",
                        "One per line: @mention, user id or name"))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(value = WINNERS_SAVE, save = false)
    static void recordWinners(ModalInteractionEvent event) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(event.getModalId(), WINNERS_SAVE);
        Tournament tournament = TournamentService.getBean().find(id).orElseThrow();
        Resolution resolution = TournamentMemberResolver.resolve(
                TournamentPanel.value(event, "names"), TournamentDiscord.lookupGuilds(tournament));
        for (String userId : resolution.userIds()) {
            TourneyWinnerService.getBean().add(userId, TournamentDiscord.userName(userId), tournament.getSeriesName());
        }
        String notice = "Recorded " + resolution.userIds().size() + " winner(s) for **" + tournament.getSeriesName()
                + "**" + (resolution.userIds().isEmpty() ? "." : ": " + TournamentPanel.names(resolution.userIds()));
        if (!resolution.problems().isEmpty()) notice += "\n" + resolution.problems();
        TournamentPanel.show(event, id, Tab.CREATE, notice);
    }
}
