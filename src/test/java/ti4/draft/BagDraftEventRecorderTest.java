package ti4.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.spring.service.gameevent.GameEventService;
import ti4.spring.service.gameevent.GameEventType;
import ti4.testUtils.BaseTi4Test;

class BagDraftEventRecorderTest extends BaseTi4Test {

    private MockedStatic<GameEventService> events;
    private Game game;
    private Player alice;
    private Player bob;

    @BeforeEach
    void setUp() {
        events = mockStatic(GameEventService.class);
        game = new Game();
        game.newGameSetup();
        game.setName("bag-draft-event-test");
        alice = seat("1", "alice", "sol", "blue");
        bob = seat("2", "bob", "hacan", "red");
        game.setBagDraft(new FrankenDraft(game));
    }

    @AfterEach
    void tearDown() {
        events.close();
    }

    private Player seat(String id, String name, String faction, String color) {
        Player player = game.addPlayer(id, name);
        player.setFaction(game, faction);
        player.setColor(color);
        player.setDraftHand(new DraftBag());
        return player;
    }

    private static DraftBag bagOf(DraftItem... items) {
        DraftBag bag = new DraftBag();
        bag.Contents.addAll(List.of(items));
        return bag;
    }

    private Map<String, Object> onlyPayload(String archetype, Player player) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        if (player == null) {
            events.verify(() -> GameEventService.commit(eq(game), eq(archetype), isNull(), payload.capture()));
        } else {
            events.verify(() -> GameEventService.commit(eq(game), eq(archetype), eq(player), payload.capture()));
        }
        return payload.getValue();
    }

    @Test
    void startedListsPlayersBagsAndChoices() {
        DraftItem tech = DraftItem.generate(DraftCategory.TECH, "lw2");
        DraftItem ability = DraftItem.generate(DraftCategory.ABILITY, "orbital_drop");
        DraftItem agent = DraftItem.generate(DraftCategory.AGENT, "hacanagent");
        alice.setCurrentDraftBag(bagOf(tech, ability));
        bob.setCurrentDraftBag(bagOf(agent));

        BagDraftEventRecorder.recordStarted(game);

        Map<String, Object> payload = onlyPayload(GameEventType.DRAFT_STARTED, null);
        assertThat(payload)
                .containsEntry("orchestrator", "FrankenDraft")
                .containsEntry("draftType", "franken")
                .containsEntry("players", List.of("alice", "bob"));
        assertThat(payload.get("bags"))
                .isEqualTo(Map.of(
                        "alice", List.of(tech.getAlias(), ability.getAlias()), "bob", List.of(agent.getAlias())));
        assertThat(payload.get("choices"))
                .isEqualTo(Map.of(
                        "TECH", List.of(tech.getAlias()),
                        "ABILITY", List.of(ability.getAlias()),
                        "AGENT", List.of(agent.getAlias())));
    }

    @Test
    void picksCommitOneEventPerItemForThePicker() {
        DraftItem tech = DraftItem.generate(DraftCategory.TECH, "lw2");
        DraftItem ability = DraftItem.generate(DraftCategory.ABILITY, "orbital_drop");
        List<DraftItem> queue = new ArrayList<>(List.of(tech, ability));

        BagDraftEventRecorder.recordPicks(game, bob, queue, "AUTO");
        // The recorder copies the list, so the caller clearing it afterwards cannot change what was recorded.
        queue.clear();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        events.verify(
                () -> GameEventService.commit(eq(game), eq(GameEventType.DRAFT_PICK), eq(bob), payload.capture()),
                times(2));
        assertThat(payload.getAllValues())
                .extracting(p -> p.get("choice"))
                .containsExactly(tech.getAlias(), ability.getAlias());
        assertThat(payload.getAllValues().getFirst())
                .containsEntry("userName", "bob")
                .containsEntry("draftable", "TECH")
                .containsEntry("source", "AUTO");
    }

    @Test
    void emptyPickListCommitsNothing() {
        BagDraftEventRecorder.recordPicks(game, alice, List.of(), "CONFIRM");

        events.verify(() -> GameEventService.commit(any(), any(), any(), any()), never());
    }

    @Test
    void pickDetailsAreMergedIntoThePayload() {
        DraftItem agent = DraftItem.generate(DraftCategory.AGENT, "hacanagent");

        BagDraftEventRecorder.recordPick(game, alice, agent, "BUTTON", Map.of("price", 3, "votesRemaining", 7));

        assertThat(onlyPayload(GameEventType.DRAFT_PICK, alice))
                .containsEntry("price", 3)
                .containsEntry("votesRemaining", 7)
                .containsEntry("choice", agent.getAlias())
                .containsEntry("source", "BUTTON");
    }

    @Test
    void endedGroupsEachHandByCategoryAndFiresOnce() {
        DraftItem tech = DraftItem.generate(DraftCategory.TECH, "lw2");
        DraftItem agent = DraftItem.generate(DraftCategory.AGENT, "hacanagent");
        alice.getDraftHand().Contents.add(tech);
        bob.getDraftHand().Contents.add(agent);

        BagDraftEventRecorder.recordEnded(game);
        // applyDraftBags can run again for the same draft (re-run command, stale confirm button).
        BagDraftEventRecorder.recordEnded(game);

        Map<String, Object> payload = onlyPayload(GameEventType.DRAFT_ENDED, null);
        assertThat(payload).containsEntry("orchestrator", "FrankenDraft");
        assertThat(payload.get("picks"))
                .isEqualTo(Map.of(
                        "alice", Map.of("TECH", List.of(tech.getAlias())),
                        "bob", Map.of("AGENT", List.of(agent.getAlias()))));
    }

    @Test
    void frankenDrazEndRecordsFactionPackagesBeforeTheyAreExpanded() {
        game.setBagDraft(new FrankenDrazDraft(game));
        DraftItem hacan = DraftItem.generate(DraftCategory.FACTION, "hacan");
        alice.getDraftHand().Contents.add(hacan);

        FrankenDrazDraft.expandFactionPackages(game);

        // The hand now holds Hacan's components, but the end event must match the FACTION pick that was made.
        assertThat(alice.getDraftHand().Contents).doesNotContain(hacan);
        Map<String, Object> payload = onlyPayload(GameEventType.DRAFT_ENDED, null);
        assertThat(payload).containsEntry("orchestrator", "FrankenDrazDraft");
        assertThat(payload.get("picks"))
                .isEqualTo(Map.of("alice", Map.of("FACTION", List.of(hacan.getAlias())), "bob", Map.of()));
    }

    @Test
    void auctionStartRecordsNominationOrderAndSettingsWithoutStaleBags() {
        game.setBagDraft(new AuctionFrankenDraft(game));
        // Leftover bag from an abandoned bag draft; an auction never deals bags, so it must not be reported.
        alice.setCurrentDraftBag(bagOf(DraftItem.generate(DraftCategory.TECH, "lw2")));

        BagDraftEventRecorder.recordAuctionStarted(game, List.of(bob, alice), Map.of("votes", 30, "bans", 1));

        Map<String, Object> payload = onlyPayload(GameEventType.DRAFT_STARTED, null);
        assertThat(payload)
                .containsEntry("orchestrator", "AuctionFrankenDraft")
                .containsEntry("players", List.of("bob", "alice"))
                .containsEntry("settings", Map.of("votes", 30, "bans", 1))
                .doesNotContainKeys("bags", "choices");
    }

    @Test
    void aRestartedDraftCanEndAgain() {
        alice.setCurrentDraftBag(new DraftBag());
        bob.setCurrentDraftBag(new DraftBag());

        BagDraftEventRecorder.recordStarted(game);
        BagDraftEventRecorder.recordEnded(game);
        BagDraftEventRecorder.recordStarted(game);
        BagDraftEventRecorder.recordEnded(game);

        events.verify(
                () -> GameEventService.commit(eq(game), eq(GameEventType.DRAFT_ENDED), isNull(), any()), times(2));
    }
}
