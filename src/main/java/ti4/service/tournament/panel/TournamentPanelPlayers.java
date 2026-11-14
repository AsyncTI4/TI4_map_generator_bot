package ti4.service.tournament.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.logging.BotLogger;
import ti4.service.tournament.TournamentAccess;
import ti4.service.tournament.TournamentDiscord;
import ti4.service.tournament.TournamentHours;
import ti4.service.tournament.TournamentHours.Profile;
import ti4.service.tournament.TournamentHours.Source;
import ti4.service.tournament.TournamentMemberResolver;
import ti4.service.tournament.TournamentMemberResolver.Resolution;
import ti4.service.tournament.TournamentRoles;
import ti4.service.tournament.TournamentSignupPosts;
import ti4.service.tournament.panel.TournamentPanel.Context;
import ti4.service.tournament.panel.TournamentPanel.Tab;
import ti4.service.tournament.panel.TournamentPanel.View;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentEntry;
import ti4.spring.service.tournament.TournamentService;
import ti4.spring.service.tournament.TournamentService.BulkAddResult;
import ti4.spring.service.tournament.TournamentService.LeaveResult;
import ti4.spring.service.tournament.TournamentSignupPost;

final class TournamentPanelPlayers {

    private static final String BULK_ADD_MODAL = "trnBulkAdd~MDL_";
    private static final String BULK_ADD_SAVE = "trnBulkAddSave_";
    private static final String REMOVE_MODAL = "trnRemove~MDL_";
    private static final String REMOVE_SAVE = "trnRemoveSave_";
    private static final String PROMOTE = "trnPromote_";
    private static final String SYNC_ROLES = "trnSyncRoles_";
    private static final String GM_ADD_MODAL = "trnGmAdd~MDL_";
    private static final String GM_ADD_SAVE = "trnGmAddSave_";
    private static final String GM_REMOVE_MODAL = "trnGmRemove~MDL_";
    private static final String GM_REMOVE_SAVE = "trnGmRemoveSave_";
    private static final String POST_REMOVE = "trnPostRemove_";
    private static final String NAMES_PLACEHOLDER = "One per line: @mention, user id or name";
    private static final int LINES_PER_PAGE = 20;
    private static final int MAX_LIST_LENGTH = 450;

    private TournamentPanelPlayers() {}

    static View view(Context context) {
        Tournament tournament = context.tournament();
        long id = context.id();
        List<String> lines = rosterLines(tournament, context.entries());
        List<List<String>> pages = lines.isEmpty()
                ? List.of(List.of("Nobody has signed up yet."))
                : ListUtils.partition(lines, LINES_PER_PAGE);
        int page = Math.min(context.page(), pages.size() - 1);

        List<ContainerChildComponent> body = new ArrayList<>();
        body.add(TextDisplay.of(String.join("\n", pages.get(page))));
        TournamentService service = TournamentService.getBean();
        List<TournamentSignupPost> posts = service.signupPosts(id);
        body.add(TextDisplay.of(StringUtils.abbreviate(
                "**Sign-up posts:** "
                        + (posts.isEmpty()
                                ? "none. Use `/tournament post_signup` to add one."
                                : String.join(
                                        ", ",
                                        posts.stream()
                                                .map(TournamentSignupPosts::describe)
                                                .toList())),
                MAX_LIST_LENGTH)));
        if (tournament.isFogMode()) {
            List<String> gameMasters = service.gameMasterIds(id);
            body.add(TextDisplay.of(StringUtils.abbreviate(
                    "**GM pool (" + gameMasters.size() + "):** "
                            + (gameMasters.isEmpty()
                                    ? "empty. Add GMs before approving groups."
                                    : TournamentPanel.names(gameMasters)),
                    MAX_LIST_LENGTH)));
        }

        boolean editable = tournament.getStatus().allowsRosterChanges();
        boolean replacing = TournamentPanel.canEditGroupsWithoutGame(tournament);
        List<ActionRow> controls = new ArrayList<>();
        controls.add(ActionRow.of(
                Buttons.green(BULK_ADD_MODAL + id, "Bulk add").withDisabled(!editable && !replacing),
                Buttons.red(REMOVE_MODAL + id, "Remove").withDisabled(!editable && !replacing),
                Buttons.blue(PROMOTE + id, "Promote from waitlist").withDisabled(!editable),
                Buttons.gray(SYNC_ROLES + id, "Sync roles")));
        if (tournament.isFogMode()) {
            controls.add(ActionRow.of(
                    Buttons.green(GM_ADD_MODAL + id, "Add GMs"), Buttons.red(GM_REMOVE_MODAL + id, "Remove GM")));
        }
        if (!posts.isEmpty()) {
            controls.add(ActionRow.of(postSelect(id, posts)));
        }
        return View.of(body, controls).withPages(pages.size());
    }

    private static List<String> rosterLines(Tournament tournament, List<TournamentEntry> entries) {
        List<TournamentEntry> seated =
                entries.stream().filter(entry -> !entry.isWaitlisted()).toList();
        List<TournamentEntry> waitlist =
                entries.stream().filter(TournamentEntry::isWaitlisted).toList();
        Map<String, Profile> hours = TournamentHours.profiles(
                entries.stream().map(TournamentEntry::getUserId).toList());
        List<String> lines = new ArrayList<>();
        if (!seated.isEmpty()) lines.add("**Seated (" + seated.size() + "/" + tournament.getTargetSize() + ")**");
        for (int i = 0; i < seated.size(); i++) {
            lines.add(line(i + 1, seated.get(i), hours));
        }
        if (!waitlist.isEmpty()) lines.add("**Waitlist (" + waitlist.size() + ")**");
        for (int i = 0; i < waitlist.size(); i++) {
            lines.add(line(i + 1, waitlist.get(i), hours));
        }
        return lines;
    }

    private static String line(int number, TournamentEntry entry, Map<String, Profile> hours) {
        String hourInfo = hours.getOrDefault(entry.getUserId(), new Profile(Set.of(), Source.NONE))
                .describe();
        return number + ". " + TournamentDiscord.userName(entry.getUserId()) + " · " + hourInfo
                + (entry.isAddedByAdmin() ? " · added by admin" : "");
    }

    private static StringSelectMenu postSelect(long id, List<TournamentSignupPost> posts) {
        List<SelectOption> options = posts.stream()
                .limit(25)
                .map(post -> SelectOption.of(
                        StringUtils.left(TournamentSignupPosts.describe(post), 100), String.valueOf(post.getId())))
                .toList();
        return StringSelectMenu.create(POST_REMOVE + id)
                .setPlaceholder("Delete a sign-up post")
                .addOptions(options)
                .setRequiredRange(1, 1)
                .build();
    }

    private static Resolution resolve(long id, String text) {
        Tournament tournament = TournamentService.getBean().find(id).orElseThrow();
        return TournamentMemberResolver.resolve(text, TournamentDiscord.lookupGuilds(tournament));
    }

    @ButtonHandler(value = BULK_ADD_MODAL, save = false)
    static void openBulkAdd(ButtonInteractionEvent event, String buttonID) {
        long id = TournamentPanel.idOf(buttonID, BULK_ADD_MODAL);
        event.replyModal(TournamentPanel.namesModal(
                        BULK_ADD_SAVE + id, "Bulk add players", "Players", NAMES_PLACEHOLDER))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(value = BULK_ADD_SAVE, save = false)
    static void bulkAdd(ModalInteractionEvent event) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(event.getModalId(), BULK_ADD_SAVE);
        Resolution resolution = resolve(id, TournamentPanel.value(event, "names"));
        TournamentService service = TournamentService.getBean();
        BulkAddResult result = service.bulkAdd(id, resolution.userIds(), TournamentPanel.creationRunning(id));
        if (result.refusal() != null) {
            TournamentPanel.show(event, id, Tab.PLAYERS, result.refusal());
            return;
        }
        Tournament tournament = service.find(id).orElseThrow();
        result.added().forEach(userId -> TournamentRoles.give(tournament, userId));
        result.promoted().forEach(userId -> TournamentSignupPosts.announcePromotion(tournament, userId));
        TournamentSignupPosts.scheduleRefresh(id);
        List<String> notice = new ArrayList<>();
        notice.add("Added " + result.added().size()
                + (result.added().isEmpty() ? "" : ": " + TournamentPanel.names(result.added())));
        if (!result.promoted().isEmpty())
            notice.add("Moved off the waitlist: " + TournamentPanel.names(result.promoted()));
        if (!result.waitlisted().isEmpty())
            notice.add("Put on the waitlist because games are being created: "
                    + TournamentPanel.names(result.waitlisted())
                    + ". Remove a player from a group without a game and the first waitlisted player takes the seat.");
        if (!result.alreadyIn().isEmpty()) notice.add("Already seated: " + TournamentPanel.names(result.alreadyIn()));
        if (!resolution.problems().isEmpty()) notice.add(resolution.problems());
        TournamentPanel.show(event, id, Tab.PLAYERS, String.join("\n", notice));
    }

    @ButtonHandler(value = REMOVE_MODAL, save = false)
    static void openRemove(ButtonInteractionEvent event, String buttonID) {
        long id = TournamentPanel.idOf(buttonID, REMOVE_MODAL);
        event.replyModal(TournamentPanel.namesModal(REMOVE_SAVE + id, "Remove players", "Players", NAMES_PLACEHOLDER))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(value = REMOVE_SAVE, save = false)
    static void remove(ModalInteractionEvent event) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(event.getModalId(), REMOVE_SAVE);
        Resolution resolution = resolve(id, TournamentPanel.value(event, "names"));
        TournamentService service = TournamentService.getBean();
        Tournament tournament = service.find(id).orElseThrow();
        List<String> removed = new ArrayList<>();
        List<String> promoted = new ArrayList<>();
        List<String> notice = new ArrayList<>();
        for (String userId : resolution.userIds()) {
            LeaveResult result = service.removeByAdmin(id, userId, TournamentPanel.creationRunning(id));
            if (result.refusal() != null) {
                notice.add(result.refusal());
                break;
            }
            if (!result.removed()) continue;
            removed.add(userId);
            TournamentRoles.take(tournament, userId);
            if (result.promotedUserId() != null) {
                promoted.add(result.promotedUserId());
                TournamentSignupPosts.announcePromotion(tournament, result.promotedUserId());
            }
        }
        TournamentSignupPosts.scheduleRefresh(id);
        notice.addFirst("Removed " + removed.size() + (removed.isEmpty() ? "" : ": " + TournamentPanel.names(removed)));
        if (!promoted.isEmpty()) notice.add("Moved off the waitlist: " + TournamentPanel.names(promoted));
        if (!resolution.problems().isEmpty()) notice.add(resolution.problems());
        TournamentPanel.show(event, id, Tab.PLAYERS, String.join("\n", notice));
    }

    @ButtonHandler(value = PROMOTE, save = false)
    static void promote(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(buttonID, PROMOTE);
        TournamentService service = TournamentService.getBean();
        String promoted = service.promoteNext(id).orElse(null);
        if (promoted != null) {
            service.find(id).ifPresent(tournament -> TournamentSignupPosts.announcePromotion(tournament, promoted));
            TournamentSignupPosts.scheduleRefresh(id);
        }
        TournamentPanel.show(
                event,
                id,
                Tab.PLAYERS,
                promoted == null
                        ? "The waitlist is empty."
                        : "Moved " + TournamentDiscord.userName(promoted) + " off the waitlist.");
    }

    @ButtonHandler(value = SYNC_ROLES, save = false)
    static void syncRoles(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(buttonID, SYNC_ROLES);
        TournamentService service = TournamentService.getBean();
        Tournament tournament = service.find(id).orElseThrow();
        if (TournamentRoles.role(tournament) == null) {
            String roleId = TournamentRoles.ensureRole(tournament);
            tournament = service.update(id, pool -> pool.setParticipantRoleId(roleId))
                    .orElseThrow();
        }
        int given = TournamentRoles.sync(tournament, service.entries(id));
        TournamentPanel.show(
                event,
                id,
                Tab.PLAYERS,
                "Checked the participant role for " + given + " seated player(s) on the host server.");
    }

    @ButtonHandler(value = GM_ADD_MODAL, save = false)
    static void openGameMasterAdd(ButtonInteractionEvent event, String buttonID) {
        long id = TournamentPanel.idOf(buttonID, GM_ADD_MODAL);
        event.replyModal(TournamentPanel.namesModal(GM_ADD_SAVE + id, "Add GMs", "GMs", NAMES_PLACEHOLDER))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(value = GM_ADD_SAVE, save = false)
    static void addGameMasters(ModalInteractionEvent event) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(event.getModalId(), GM_ADD_SAVE);
        Resolution resolution = resolve(id, TournamentPanel.value(event, "names"));
        List<String> added = TournamentService.getBean().addGameMasters(id, resolution.userIds());
        String notice =
                "Added " + added.size() + " GM(s)" + (added.isEmpty() ? "." : ": " + TournamentPanel.names(added));
        if (!resolution.problems().isEmpty()) notice += "\n" + resolution.problems();
        TournamentPanel.show(event, id, Tab.PLAYERS, notice);
    }

    @ButtonHandler(value = GM_REMOVE_MODAL, save = false)
    static void openGameMasterRemove(ButtonInteractionEvent event, String buttonID) {
        long id = TournamentPanel.idOf(buttonID, GM_REMOVE_MODAL);
        event.replyModal(TournamentPanel.namesModal(GM_REMOVE_SAVE + id, "Remove GMs", "GMs", NAMES_PLACEHOLDER))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(value = GM_REMOVE_SAVE, save = false)
    static void removeGameMasters(ModalInteractionEvent event) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(event.getModalId(), GM_REMOVE_SAVE);
        Resolution resolution = resolve(id, TournamentPanel.value(event, "names"));
        TournamentService service = TournamentService.getBean();
        List<String> removed = resolution.userIds().stream()
                .filter(userId -> service.removeGameMaster(id, userId))
                .toList();
        String notice =
                "Removed " + removed.size() + " GM(s). Assignments already made stay until you assign GMs again.";
        if (!resolution.problems().isEmpty()) notice += "\n" + resolution.problems();
        TournamentPanel.show(event, id, Tab.PLAYERS, notice);
    }

    @SelectionHandler(value = POST_REMOVE, save = false)
    static void removePost(StringSelectInteractionEvent event) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(event.getComponentId(), POST_REMOVE);
        long postId = Long.parseLong(event.getValues().getFirst());
        TournamentService.getBean().signupPosts(id).stream()
                .filter(post -> post.getId() == postId)
                .findFirst()
                .ifPresent(TournamentSignupPosts::delete);
        TournamentPanel.show(event, id, Tab.PLAYERS, "Deleted the sign-up post.");
    }
}
