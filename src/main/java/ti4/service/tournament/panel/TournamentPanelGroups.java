package ti4.service.tournament.panel;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.logging.BotLogger;
import ti4.service.tournament.TournamentAccess;
import ti4.service.tournament.TournamentDiscord;
import ti4.service.tournament.TournamentGameMasterAssigner;
import ti4.service.tournament.TournamentGrouper;
import ti4.service.tournament.TournamentGrouper.Entrant;
import ti4.service.tournament.TournamentHours;
import ti4.service.tournament.TournamentMemberResolver;
import ti4.service.tournament.TournamentMemberResolver.Resolution;
import ti4.service.tournament.TournamentRoster;
import ti4.service.tournament.panel.TournamentPanel.Context;
import ti4.service.tournament.panel.TournamentPanel.Tab;
import ti4.service.tournament.panel.TournamentPanel.View;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentEntry;
import ti4.spring.service.tournament.TournamentService;
import ti4.spring.service.tournament.TournamentStatus;

final class TournamentPanelGroups {

    private static final String GENERATE = "trnGenerate_";
    private static final String SWAP_MODAL = "trnSwap~MDL_";
    private static final String SWAP_SAVE = "trnSwapSave_";
    private static final String APPROVE = "trnApprove_";
    private static final String ROSTER = "trnRoster_";
    private static final String ASSIGN_GMS = "trnAssignGms_";
    private static final String SET_GM_MODAL = "trnSetGm~MDL_";
    private static final String SET_GM_SAVE = "trnSetGmSave_";
    private static final int GROUPS_PER_PAGE = 5;
    private static final SecureRandom SEEDS = new SecureRandom();

    private TournamentPanelGroups() {}

    static View view(Context context) {
        Tournament tournament = context.tournament();
        long id = context.id();
        Map<Integer, List<TournamentEntry>> groups = TournamentService.groups(context.entries());
        Map<Integer, String> gameMasters = TournamentService.gameMastersByGroup(tournament);
        List<ContainerChildComponent> body = new ArrayList<>();
        List<String> warnings = warnings(tournament, context.entries(), groups, gameMasters);
        int pageCount = 1;
        if (groups.isEmpty()) {
            body.add(TextDisplay.of("No groups yet. Press **Generate** to draw random groups"
                    + (tournament.isMatchActiveHours() ? " that favour shared active hours." : ".")));
        } else {
            List<List<Integer>> pages = ListUtils.partition(new ArrayList<>(groups.keySet()), GROUPS_PER_PAGE);
            pageCount = pages.size();
            int page = Math.min(context.page(), pageCount - 1);
            Map<String, Set<Integer>> hours = TournamentHours.activeHours(
                    context.entries().stream().map(TournamentEntry::getUserId).toList());
            List<String> blocks = new ArrayList<>();
            if (tournament.getStatus() == TournamentStatus.GROUPED) blocks.add("✅ **Groups approved**");
            for (int group : pages.get(page)) {
                blocks.add(groupBlock(tournament, group, groups.get(group), gameMasters.get(group), hours));
            }
            body.add(TextDisplay.of(String.join("\n\n", blocks)));
        }
        if (!warnings.isEmpty()) body.add(TextDisplay.of("⚠️ " + String.join("\n⚠️ ", warnings)));

        boolean editable = tournament.getStatus().allowsRosterChanges();
        List<ActionRow> controls = new ArrayList<>();
        controls.add(ActionRow.of(
                Buttons.blue(GENERATE + id, groups.isEmpty() ? "Generate" : "Reshuffle")
                        .withDisabled(!editable),
                Buttons.gray(SWAP_MODAL + id, "Swap two players").withDisabled(!editable || groups.isEmpty()),
                Buttons.green(APPROVE + id, "Approve")
                        .withDisabled(
                                !editable || groups.isEmpty() || tournament.getStatus() == TournamentStatus.GROUPED),
                Buttons.gray(ROSTER + id, "Copy roster").withDisabled(groups.isEmpty())));
        if (tournament.isFogMode()) {
            controls.add(ActionRow.of(
                    Buttons.blue(ASSIGN_GMS + id, "Assign GMs").withDisabled(!editable || groups.isEmpty()),
                    Buttons.gray(SET_GM_MODAL + id, "Set a GM").withDisabled(!editable || groups.isEmpty())));
        }
        return View.of(body, controls).withWarning(!warnings.isEmpty()).withPages(pageCount);
    }

    private static String groupBlock(
            Tournament tournament,
            int group,
            List<TournamentEntry> members,
            String gameMaster,
            Map<String, Set<Integer>> hours) {
        String game = members.getFirst().getGameName();
        String title = "**Group " + group + "** · " + members.size() + " players"
                + (tournament.isFogMode()
                        ? " · GM: " + (gameMaster == null ? "none" : TournamentDiscord.userName(gameMaster))
                        : "")
                + (game == null ? "" : " · " + game);
        List<String> userIds = members.stream().map(TournamentEntry::getUserId).toList();
        TournamentHours.Window window = TournamentHours.bestWindow(userIds.stream()
                .map(userId -> hours.getOrDefault(userId, Set.of()))
                .toList());
        return title + "\n" + TournamentPanel.names(userIds) + "\n-# "
                + TournamentHours.describe(window, members.size());
    }

    private static List<String> warnings(
            Tournament tournament,
            List<TournamentEntry> entries,
            Map<Integer, List<TournamentEntry>> groups,
            Map<Integer, String> gameMasters) {
        List<String> warnings = new ArrayList<>();
        if (groups.isEmpty()) return warnings;
        long unassigned = entries.stream()
                .filter(entry -> !entry.isWaitlisted() && entry.getGroupNumber() == null)
                .count();
        if (unassigned > 0)
            warnings.add(unassigned + " seated player(s) have no group. Reshuffle or add them by swapping.");
        int smallest = groups.values().stream().mapToInt(List::size).min().orElse(0);
        int largest = groups.values().stream().mapToInt(List::size).max().orElse(0);
        if (smallest != largest) warnings.add("Uneven groups: " + smallest + " to " + largest + " players.");
        else if (largest != tournament.getGroupSize())
            warnings.add("Groups have " + largest + " players, not " + tournament.getGroupSize() + ".");
        if (tournament.isFogMode()) {
            long withoutGm = groups.keySet().stream()
                    .filter(group -> !gameMasters.containsKey(group))
                    .count();
            if (withoutGm > 0) warnings.add(withoutGm + " group(s) have no GM.");
        }
        return warnings;
    }

    @ButtonHandler(value = GENERATE, save = false)
    static void generate(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(buttonID, GENERATE);
        TournamentService service = TournamentService.getBean();
        Tournament tournament = service.find(id).orElseThrow();
        if (!tournament.getStatus().allowsRosterChanges()) {
            TournamentPanel.show(event, id, Tab.GROUPS, "Games are already being created.");
            return;
        }
        List<TournamentEntry> seated = service.activeEntries(id);
        if (seated.isEmpty()) {
            TournamentPanel.show(event, id, Tab.GROUPS, "Nobody has signed up yet.");
            return;
        }
        Map<String, Set<Integer>> hours = TournamentHours.activeHours(
                seated.stream().map(TournamentEntry::getUserId).toList());
        List<Entrant> entrants = seated.stream()
                .map(entry -> new Entrant(entry.getUserId(), hours.getOrDefault(entry.getUserId(), Set.of())))
                .toList();
        long seed = SEEDS.nextLong();
        List<List<String>> drawn =
                TournamentGrouper.group(entrants, tournament.getGroupSize(), tournament.isMatchActiveHours(), seed);
        Map<String, Integer> groupByUser = new LinkedHashMap<>();
        for (int group = 0; group < drawn.size(); group++) {
            for (String userId : drawn.get(group)) {
                groupByUser.put(userId, group + 1);
            }
        }
        service.saveGroups(id, groupByUser, seed);
        TournamentPanel.show(
                event, id, Tab.GROUPS, "Drew " + drawn.size() + " groups. Not final until you press **Approve**.");
    }

    @ButtonHandler(value = SWAP_MODAL, save = false)
    static void openSwap(ButtonInteractionEvent event, String buttonID) {
        long id = TournamentPanel.idOf(buttonID, SWAP_MODAL);
        Modal modal = Modal.create(SWAP_SAVE + id, "Swap two players")
                .addComponents(
                        Label.of("First player", TournamentPanel.input("first", TextInputStyle.SHORT, null, true)),
                        Label.of("Second player", TournamentPanel.input("second", TextInputStyle.SHORT, null, true)))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(value = SWAP_SAVE, save = false)
    static void swap(ModalInteractionEvent event) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(event.getModalId(), SWAP_SAVE);
        Tournament tournament = TournamentService.getBean().find(id).orElseThrow();
        Resolution resolution = TournamentMemberResolver.resolve(
                TournamentPanel.value(event, "first") + "\n" + TournamentPanel.value(event, "second"),
                TournamentDiscord.lookupGuilds(tournament));
        if (resolution.userIds().size() != 2) {
            TournamentPanel.show(event, id, Tab.GROUPS, "Couldn't find exactly two players.\n" + resolution.problems());
            return;
        }
        String first = resolution.userIds().get(0);
        String second = resolution.userIds().get(1);
        String notice = TournamentService.getBean()
                .swap(id, first, second)
                .orElse("Swapped " + TournamentDiscord.userName(first) + " and " + TournamentDiscord.userName(second)
                        + ".");
        TournamentPanel.show(event, id, Tab.GROUPS, notice);
    }

    @ButtonHandler(value = APPROVE, save = false)
    static void approve(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(buttonID, APPROVE);
        TournamentService service = TournamentService.getBean();
        String error = service.approveGroups(id).orElse(null);
        if (error != null) {
            TournamentPanel.show(event, id, Tab.GROUPS, error);
            return;
        }
        Tournament tournament = service.find(id).orElseThrow();
        String notice = "Groups approved. Head to **Create** when you're ready.";
        if (tournament.isFogMode()
                && TournamentService.gameMastersByGroup(tournament).isEmpty()) {
            notice += "\n" + assignGameMasters(tournament);
        }
        TournamentPanel.show(event, id, Tab.GROUPS, notice);
    }

    @ButtonHandler(value = ASSIGN_GMS, save = false)
    static void assignGameMastersButton(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(buttonID, ASSIGN_GMS);
        Tournament tournament = TournamentService.getBean().find(id).orElseThrow();
        TournamentPanel.show(event, id, Tab.GROUPS, assignGameMasters(tournament));
    }

    private static String assignGameMasters(Tournament tournament) {
        TournamentService service = TournamentService.getBean();
        List<String> pool = service.gameMasterIds(tournament.getId());
        if (pool.isEmpty()) return "The GM pool is empty. Add GMs on the **Players** tab.";
        Map<Integer, List<String>> playersByGroup = new LinkedHashMap<>();
        TournamentService.groups(service.entries(tournament.getId()))
                .forEach((group, members) -> playersByGroup.put(
                        group, members.stream().map(TournamentEntry::getUserId).toList()));
        Map<Integer, String> assignments = TournamentGameMasterAssigner.assign(pool, playersByGroup);
        service.saveGameMastersByGroup(tournament.getId(), assignments);
        int missing = playersByGroup.size() - assignments.size();
        return "Assigned GMs to " + assignments.size() + " group(s)"
                + (missing > 0 ? "; " + missing + " group(s) only have GMs who play in them." : ".");
    }

    @ButtonHandler(value = SET_GM_MODAL, save = false)
    static void openSetGameMaster(ButtonInteractionEvent event, String buttonID) {
        long id = TournamentPanel.idOf(buttonID, SET_GM_MODAL);
        Modal modal = Modal.create(SET_GM_SAVE + id, "Set a GM")
                .addComponents(
                        Label.of("Group number", TournamentPanel.input("group", TextInputStyle.SHORT, null, true)),
                        Label.of("GM", TournamentPanel.input("gm", TextInputStyle.SHORT, null, true)))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(value = SET_GM_SAVE, save = false)
    static void setGameMaster(ModalInteractionEvent event) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(event.getModalId(), SET_GM_SAVE);
        TournamentService service = TournamentService.getBean();
        Tournament tournament = service.find(id).orElseThrow();
        Integer group = TournamentPanel.parseInt(TournamentPanel.value(event, "group"));
        Map<Integer, List<TournamentEntry>> groups = TournamentService.groups(service.entries(id));
        if (group == null || !groups.containsKey(group)) {
            TournamentPanel.show(event, id, Tab.GROUPS, "There is no group with that number.");
            return;
        }
        Resolution resolution = TournamentMemberResolver.resolve(
                TournamentPanel.value(event, "gm"), TournamentDiscord.lookupGuilds(tournament));
        if (resolution.userIds().size() != 1) {
            TournamentPanel.show(event, id, Tab.GROUPS, "Couldn't find exactly one GM.\n" + resolution.problems());
            return;
        }
        String gameMaster = resolution.userIds().getFirst();
        boolean playsInGroup =
                groups.get(group).stream().anyMatch(entry -> entry.getUserId().equals(gameMaster));
        if (playsInGroup) {
            TournamentPanel.show(
                    event, id, Tab.GROUPS, TournamentDiscord.userName(gameMaster) + " plays in group " + group + ".");
            return;
        }
        service.addGameMasters(id, List.of(gameMaster));
        Map<Integer, String> assignments = TournamentService.gameMastersByGroup(tournament);
        assignments.put(group, gameMaster);
        service.saveGameMastersByGroup(id, assignments);
        TournamentPanel.show(
                event, id, Tab.GROUPS, TournamentDiscord.userName(gameMaster) + " is the GM of group " + group + ".");
    }

    @ButtonHandler(value = ROSTER, save = false)
    static void roster(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(buttonID, ROSTER);
        TournamentService service = TournamentService.getBean();
        Tournament tournament = service.find(id).orElseThrow();
        TournamentRoster.post(tournament, service.entries(id), event.getMessageChannel());
    }
}
