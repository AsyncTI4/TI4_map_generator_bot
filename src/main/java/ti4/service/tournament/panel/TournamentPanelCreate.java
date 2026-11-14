package ti4.service.tournament.panel;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
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
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.executors.CircuitBreaker;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.tournament.TournamentAccess;
import ti4.service.tournament.TournamentDiscord;
import ti4.service.tournament.TournamentGameCreator;
import ti4.service.tournament.TournamentGameCreator.Preflight;
import ti4.service.tournament.TournamentInvites;
import ti4.service.tournament.TournamentMemberResolver.Resolution;
import ti4.service.tournament.TournamentPacing;
import ti4.service.tournament.TournamentResults;
import ti4.service.tournament.TournamentRoles;
import ti4.service.tournament.TournamentSignupPosts;
import ti4.service.tournament.panel.TournamentPanel.Context;
import ti4.service.tournament.panel.TournamentPanel.Tab;
import ti4.service.tournament.panel.TournamentPanel.View;
import ti4.spring.service.tournament.Tournament;
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
    private static final String RESULTS = "trnResults_";
    private static final int BROADCAST_MAX_LENGTH = 1800;
    private static final int CONFIRM_CREATE = 1;
    private static final int CONFIRM_ARCHIVE = 2;
    private static final int MAX_MISSING_LINES = 10;

    private TournamentPanelCreate() {}

    static View view(Context context) {
        Tournament tournament = context.tournament();
        Preflight preflight = TournamentGameCreator.preflight(tournament, context.entries());
        boolean running = TournamentGameCreator.isRunning(tournament.getId());
        TournamentResults.Progress progress = TournamentResults.progress(context.entries());
        boolean hasGames = progress.total() > 0;
        List<String> lines = new ArrayList<>();
        lines.add(intro(tournament, preflight, running));
        if (hasGames) {
            lines.add("**Results:** " + progress.finished() + "/" + progress.total() + " games finished.");
        }
        List<String> warnings = warnings(tournament, preflight);
        List<ContainerChildComponent> body = new ArrayList<>();
        body.add(TextDisplay.of(String.join("\n", lines)));
        if (!warnings.isEmpty()) body.add(TextDisplay.of(String.join("\n", warnings)));
        return View.of(body, controls(context, preflight, running, hasGames)).withWarning(!warnings.isEmpty());
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
                        : "**" + preflight.pendingGames() + "** of " + preflight.groupCount()
                                + " games still to create. Press **Create** to continue. Groups with a game are"
                                + " locked; players in the other groups can still be swapped or replaced.";
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
        if (!preflight.groupsWithoutGameMaster().isEmpty()) {
            warnings.add("⚠️ No GM for group(s) " + StringUtils.join(preflight.groupsWithoutGameMaster(), ", ")
                    + ". Those games are skipped.");
        }
        if (!preflight.groupsWhereGameMasterPlays().isEmpty()) {
            warnings.add("⚠️ The GM also plays in group(s) "
                    + StringUtils.join(preflight.groupsWhereGameMasterPlays(), ", ")
                    + ". Those games are skipped until you set another GM.");
        }
        return warnings;
    }

    private static List<ActionRow> controls(Context context, Preflight preflight, boolean running, boolean hasGames) {
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
        if (hasGames) {
            sharing.add(Buttons.blue(BROADCAST_MODAL + id, "Message all games"));
            sharing.add(Buttons.gray(APPLY_SETUP + id, "Apply game setup to all games"));
            sharing.add(Buttons.gray(RESULTS + id, "Copy results"));
        }
        return List.of(ActionRow.of(buttons), ActionRow.of(sharing));
    }

    @ButtonHandler(value = BROADCAST_MODAL, save = false)
    static void openBroadcast(ButtonInteractionEvent event, String buttonID) {
        long id = TournamentPanel.idOf(buttonID, BROADCAST_MODAL);
        TextInput message = TextInput.create("message", TextInputStyle.PARAGRAPH)
                .setRequired(true)
                .setMaxLength(BROADCAST_MAX_LENGTH)
                .build();
        TournamentPanel.openModal(
                event,
                BROADCAST_SAVE + id,
                "Message all games",
                Label.of("Posted in every game's main channel", message));
    }

    @ModalHandler(value = BROADCAST_SAVE, save = false)
    static void broadcast(ModalInteractionEvent event) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(event.getModalId(), BROADCAST_SAVE);
        Tournament tournament = TournamentService.getBean().find(id).orElseThrow();
        String text = "**Message from the " + tournament.getSeriesName() + " organizers:**\n"
                + TournamentPanel.value(event, "message");
        forAllGames(
                event,
                id,
                "tournamentBroadcast",
                gameName -> sendToGame(gameName, text),
                "Sent the message to",
                "Sending the message to",
                "A message is already being sent. Wait until it has finished.");
    }

    private static void forAllGames(
            GenericInteractionCreateEvent event,
            long id,
            String jobKey,
            Function<String, String> step,
            String doneVerb,
            String startingVerb,
            String busyText) {
        List<String> gameNames =
                TournamentResults.gameNames(TournamentService.getBean().entries(id));
        boolean started = TournamentPacing.forEach(
                jobKey + id, gameNames, step, result -> TournamentPanel.refresh(id, result.summary(doneVerb)));
        TournamentPanel.show(
                event,
                id,
                Tab.CREATE,
                started ? startingVerb + " " + gameNames.size() + " game(s), one at a time." : notStarted(busyText));
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
        Tournament tournament = TournamentService.getBean().find(id).orElseThrow();
        if (!TournamentGameCreator.hasAnySetup(tournament)) {
            TournamentPanel.show(
                    event, id, Tab.CREATE, "There is no game setup to apply. Pick a setup game on the Game setup tab.");
            return;
        }
        forAllGames(
                event,
                id,
                "tournamentApplySetup",
                gameName -> TournamentGameCreator.applySetup(tournament, gameName),
                "Applied the game setup to",
                "Applying the game setup to",
                "The setup is already being applied. Wait until it has finished.");
    }

    private static String notStarted(String alreadyRunning) {
        return CircuitBreaker.isOpen()
                ? "The bot is pausing work after repeated errors. Try again in a few minutes."
                : alreadyRunning;
    }

    @ButtonHandler(value = INVITE_MODAL, save = false)
    static void openInvite(ButtonInteractionEvent event, String buttonID) {
        long id = TournamentPanel.idOf(buttonID, INVITE_MODAL);
        Tournament tournament = TournamentService.getBean().find(id).orElse(null);
        String suggestion = tournament == null ? null : TournamentInvites.defaultChannelLink(tournament);
        TournamentPanel.openModal(
                event,
                INVITE_SAVE + id,
                "Send the host server invite",
                TextDisplay.of("Any channel on any server the bot is in. Players who aren't on the host"
                        + " server yet are pinged."),
                TournamentPanel.field("Channel link, #mention or id", "channel", suggestion, true));
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

    @ButtonHandler(value = RESULTS, save = false)
    static void copyResults(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(buttonID, RESULTS);
        Tournament tournament = TournamentService.getBean().find(id).orElseThrow();
        String text = TournamentResults.text(TournamentResults.results(id));
        String header = "Results for **" + tournament.displayName() + "**";
        FileUpload file = FileUpload.fromData(text.getBytes(StandardCharsets.UTF_8), "results.txt");
        if (tournament.isFogMode()) {
            TournamentDiscord.ephemeral(event, header, List.of(file));
            return;
        }
        if (text.length() <= 1800) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), header + "\n```\n" + text + "\n```");
            return;
        }
        event.getMessageChannel().sendMessage(header).addFiles(file).queue(Consumers.nop(), BotLogger::catchRestError);
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
                || TournamentPanel.canEditGroupsWithoutGame(tournament);
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
        int games = TournamentService.groups(TournamentService.getBean().entries(id))
                .size();
        TournamentPanel.openModal(
                event,
                NEXT_ROUND_SAVE + id,
                "Start the next round",
                TournamentPanel.field("Round name", "round", "Semifinals", true),
                TextDisplay.of("Players advancing: each finished game's winner first, then the best runners-up"
                        + " by VP. Leave it empty to start with an empty pool."),
                TournamentPanel.field("Players advancing", "advancing", String.valueOf(games), false));
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
        String advancingText = TournamentPanel.value(event, "advancing");
        int advancing = advancingText.isBlank() ? 0 : NumberUtils.toInt(advancingText, -1);
        if (advancing < 0) {
            TournamentPanel.show(event, id, Tab.CREATE, "Players advancing must be a number, or empty.");
            return;
        }
        TournamentService service = TournamentService.getBean();
        Tournament created = service.nextRound(id, round, event.getUser().getId());
        String advanced = advancing == 0 ? null : advancePlayers(id, created, advancing);
        Tournament next = service.find(created.getId()).orElse(created);
        TournamentPanel.post(
                next,
                event.getMessageChannel(),
                (advanced == null
                                ? "New round created with the same settings and an empty pool. Bulk add the players on"
                                        + " the **Players** tab, or reopen sign-ups and use `/tournament post_signup`."
                                : advanced)
                        + " Game names are back to pbd numbers; set a new prefix in Settings for custom names.");
        TournamentPanel.show(event, id, Tab.CREATE, "Started **" + next.displayName() + "**. Its panel is below.");
    }

    private static String advancePlayers(long previousId, Tournament next, int advancing) {
        TournamentResults.Advancement advancement =
                TournamentResults.advance(TournamentResults.results(previousId), advancing);
        List<String> players = advancement.advancing();
        TournamentService service = TournamentService.getBean();
        service.update(next.getId(), pool -> pool.setTargetSize(Math.max(advancing, players.size())));
        service.bulkAdd(next.getId(), players, () -> false);
        players.forEach(userId -> TournamentRoles.give(next, userId));
        List<String> lines = new ArrayList<>();
        lines.add("Advanced " + advancement.winners().size() + " winner(s)"
                + (advancement.wildcards().isEmpty()
                        ? ""
                        : " and " + advancement.wildcards().size() + " by VP: "
                                + TournamentPanel.names(advancement.wildcards()))
                + ".");
        if (!advancement.tiedAtCut().isEmpty()) {
            lines.add("Tied at the cut, check by hand: " + TournamentPanel.names(advancement.tiedAtCut()) + ".");
        }
        if (!advancement.unfinishedGames().isEmpty()) {
            lines.add("Still running, nobody advanced from them: " + String.join(", ", advancement.unfinishedGames())
                    + ". Bulk add their players later.");
        }
        return String.join("\n", lines);
    }

    @ButtonHandler(value = WINNERS_MODAL, save = false)
    static void openWinners(ButtonInteractionEvent event, String buttonID) {
        long id = TournamentPanel.idOf(buttonID, WINNERS_MODAL);
        TournamentPanel.openNamesModal(event, WINNERS_SAVE + id, "Record tournament winners", "Winners");
    }

    @ModalHandler(value = WINNERS_SAVE, save = false)
    static void recordWinners(ModalInteractionEvent event) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(event.getModalId(), WINNERS_SAVE);
        Tournament tournament = TournamentService.getBean().find(id).orElseThrow();
        Resolution resolution = TournamentPanel.resolve(tournament, TournamentPanel.value(event, "names"));
        for (String userId : resolution.userIds()) {
            TourneyWinnerService.getBean().add(userId, TournamentDiscord.userName(userId), tournament.getSeriesName());
        }
        String notice = "Recorded " + resolution.userIds().size() + " winner(s) for **" + tournament.getSeriesName()
                + "**" + (resolution.userIds().isEmpty() ? "." : ": " + TournamentPanel.names(resolution.userIds()));
        if (!resolution.problems().isEmpty()) notice += "\n" + resolution.problems();
        TournamentPanel.show(event, id, Tab.CREATE, notice);
    }
}
