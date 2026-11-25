package ti4.service.tournament.panel;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.dv8tion.jda.api.components.ModalTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.GuardedBy;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.executors.CircuitBreaker;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.message.componentsV2.ComponentPanel;
import ti4.message.componentsV2.PanelForms;
import ti4.message.componentsV2.PanelIds;
import ti4.message.componentsV2.PanelView;
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
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentService;
import ti4.spring.service.tournament.TournamentStatus;
import ti4.spring.service.tournamentwinner.TourneyWinnerService;

@GuardedBy(TournamentPanelGuard.class)
final class TournamentPanelCreate {

    private static final String INVITE_MODAL = "trnInvite~MDL_";
    private static final String INVITE_SAVE = "trnInviteSave_";
    private static final String INVITE_COPY = "trnInviteCopy_";
    private static final String INVITE_TARGET = "target";
    private static final String ALL = "all";
    private static final String ALL_SIGNUP_CHANNELS_LABEL = "All sign-up channels listed below";
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

    static PanelView view(Context context) {
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
        return PanelView.of(body, controls(context, preflight, running, hasGames))
                .withWarning(!warnings.isEmpty());
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
        String cancel = PanelIds.of(TournamentPanel.PAGE, id, Tab.CREATE.name(), 0);
        if (context.page() == CONFIRM_CREATE) {
            return List.of(ComponentPanel.confirmRow(
                    Buttons.green(CREATE_GO + id, "Yes, create " + preflight.pendingGames() + " games"), cancel));
        }
        if (context.page() == CONFIRM_ARCHIVE) {
            return List.of(
                    ComponentPanel.confirmRow(Buttons.red(ARCHIVE_GO + id, "Yes, archive this tournament"), cancel));
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
        long id = PanelIds.key(buttonID, BROADCAST_MODAL);
        TextInput message = TextInput.create("message", TextInputStyle.PARAGRAPH)
                .setRequired(true)
                .setMaxLength(BROADCAST_MAX_LENGTH)
                .build();
        PanelForms.open(
                event,
                BROADCAST_SAVE + id,
                "Message all games",
                Label.of("Posted in every game's main channel", message));
    }

    @ModalHandler(value = BROADCAST_SAVE, save = false)
    static void broadcast(ModalInteractionEvent event) {
        long id = PanelIds.key(event.getModalId(), BROADCAST_SAVE);
        Tournament tournament = TournamentService.getBean().find(id).orElseThrow();
        String text = "**Message from the " + tournament.getSeriesName() + " organizers:**\n"
                + PanelForms.value(event, "message");
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
        long id = PanelIds.key(buttonID, APPLY_SETUP);
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
        long id = PanelIds.key(buttonID, INVITE_MODAL);
        List<GuildMessageChannel> channels = TournamentService.getBean()
                .find(id)
                .map(TournamentInvites::signupChannelsOffHost)
                .orElse(List.of());
        List<ModalTopLevelComponent> parts = new ArrayList<>();
        parts.add(TextDisplay.of("Each post pings the players on that server who aren't on the host server yet."
                + (channels.isEmpty() ? "" : " A channel typed below replaces the pick.")));
        if (!channels.isEmpty()) parts.add(Label.of("Post in", inviteTargets(channels)));
        parts.add(PanelForms.field(
                channels.isEmpty() ? "Channel link, #mention or id" : "Or another channel: link, #mention or id",
                "channel",
                null,
                channels.isEmpty()));
        PanelForms.open(
                event, INVITE_SAVE + id, "Send the host server invite", parts.toArray(ModalTopLevelComponent[]::new));
    }

    private static StringSelectMenu inviteTargets(List<GuildMessageChannel> channels) {
        StringSelectMenu.Builder menu =
                StringSelectMenu.create(INVITE_TARGET).addOption(ALL_SIGNUP_CHANNELS_LABEL, ALL);
        channels.stream()
                .limit(StringSelectMenu.OPTIONS_MAX_AMOUNT - 1)
                .forEach(channel -> menu.addOption(
                        StringUtils.left(
                                "#" + channel.getName() + " ("
                                        + channel.getGuild().getName() + ")",
                                100),
                        channel.getId()));
        return menu.setRequiredRange(1, 1).setDefaultValues(ALL).build();
    }

    @ModalHandler(value = INVITE_SAVE, save = false)
    static void sendInvite(ModalInteractionEvent event) {
        long id = PanelIds.key(event.getModalId(), INVITE_SAVE);
        Tournament tournament = TournamentService.getBean().find(id).orElseThrow();
        String input = PanelForms.value(event, "channel");
        ModalMapping target = event.getValue(INVITE_TARGET);
        String picked = target == null
                ? ALL
                : target.getAsStringList().stream().findFirst().orElse(ALL);
        TournamentPanel.show(event, id, Tab.CREATE, inviteNotice(tournament, input, picked));
    }

    private static String inviteNotice(Tournament tournament, String input, String picked) {
        if (input.isEmpty()) {
            List<GuildMessageChannel> channels = TournamentInvites.signupChannelsOffHost(tournament).stream()
                    .filter(channel -> ALL.equals(picked) || channel.getId().equals(picked))
                    .toList();
            return channels.isEmpty()
                    ? "No sign-up post outside the host server to post in. Send invite… again with a channel link."
                    : TournamentInvites.send(tournament, channels);
        }
        GuildMessageChannel channel = TournamentInvites.resolveChannel(input);
        return channel == null
                ? "Couldn't find that channel. Paste a channel link (right click › Copy Link), #mention or id."
                : TournamentInvites.send(tournament, channel);
    }

    @ButtonHandler(value = INVITE_COPY, save = false)
    static void copyInvite(ButtonInteractionEvent event, String buttonID) {
        long id = PanelIds.key(buttonID, INVITE_COPY);
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
        long id = PanelIds.key(buttonID, RESULTS);
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
        TournamentPanel.show(event, PanelIds.key(buttonID, CREATE_ASK), Tab.CREATE, CONFIRM_CREATE, null);
    }

    @ButtonHandler(value = CREATE_GO, save = false)
    static void create(ButtonInteractionEvent event, String buttonID) {
        long id = PanelIds.key(buttonID, CREATE_GO);
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
        TournamentPanel.show(event, PanelIds.key(buttonID, ARCHIVE_ASK), Tab.CREATE, CONFIRM_ARCHIVE, null);
    }

    @ButtonHandler(value = ARCHIVE_GO, save = false)
    static void archive(ButtonInteractionEvent event, String buttonID) {
        long id = PanelIds.key(buttonID, ARCHIVE_GO);
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
        long id = PanelIds.key(buttonID, NEXT_ROUND_MODAL);
        int games = TournamentService.groups(TournamentService.getBean().entries(id))
                .size();
        PanelForms.open(
                event,
                NEXT_ROUND_SAVE + id,
                "Start the next round",
                PanelForms.field("Round name", "round", "Semifinals", true),
                TextDisplay.of("Players advancing: each finished game's winner first, then the best runners-up"
                        + " by VP. Leave it empty to start with an empty pool."),
                PanelForms.field("Players advancing", "advancing", String.valueOf(games), false));
    }

    @ModalHandler(value = NEXT_ROUND_SAVE, save = false)
    static void nextRound(ModalInteractionEvent event) {
        long id = PanelIds.key(event.getModalId(), NEXT_ROUND_SAVE);
        String round = StringUtils.left(PanelForms.value(event, "round"), 40);
        if (round.isBlank()) {
            TournamentPanel.show(event, id, Tab.CREATE, "The round needs a name.");
            return;
        }
        String advancingText = PanelForms.value(event, "advancing");
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
        service.bulkAdd(next.getId(), players, true, () -> false);
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
        long id = PanelIds.key(buttonID, WINNERS_MODAL);
        TournamentPanel.openNamesModal(event, WINNERS_SAVE + id, "Record tournament winners", "Winners");
    }

    @ModalHandler(value = WINNERS_SAVE, save = false)
    static void recordWinners(ModalInteractionEvent event) {
        long id = PanelIds.key(event.getModalId(), WINNERS_SAVE);
        Tournament tournament = TournamentService.getBean().find(id).orElseThrow();
        Resolution resolution = TournamentPanel.resolve(tournament, PanelForms.value(event, "names"));
        for (String userId : resolution.userIds()) {
            TourneyWinnerService.getBean().add(userId, TournamentDiscord.userName(userId), tournament.getSeriesName());
        }
        String notice = "Recorded " + resolution.userIds().size() + " winner(s) for **" + tournament.getSeriesName()
                + "**" + (resolution.userIds().isEmpty() ? "." : ": " + TournamentPanel.names(resolution.userIds()));
        if (!resolution.problems().isEmpty()) notice += "\n" + resolution.problems();
        TournamentPanel.show(event, id, Tab.CREATE, notice);
    }
}
