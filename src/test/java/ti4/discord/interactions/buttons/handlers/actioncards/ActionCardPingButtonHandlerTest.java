package ti4.discord.interactions.buttons.handlers.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.TestGameHarness;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.service.fow.GMService;
import ti4.service.fow.PlanetTargetService;
import ti4.testUtils.BaseTi4Test;

/**
 * {@code pingPlanetTarget}/{@code pingSystemTarget}/{@code pingPlayerTarget} are also the shared
 * implementation behind the {@code /fow} slash commands (TargetPlanetPing/TargetSystemPing/TargetPlayerPing),
 * so most tests here call them directly rather than driving the button flow end to end.
 *
 * <p>Two real bugs were fixed in this handler shortly before these tests were written: the route-choice
 * button id used to embed the card title as a middle split field, which an underscore in the title would
 * have corrupted (and which was also unboundedly long, risking Discord's 100-char id cap); and non-public
 * pings used to notify players with mere <i>presence</i> in or next to the target system rather than players
 * who can <i>currently see</i> it. Both are pinned below.
 */
class ActionCardPingButtonHandlerTest extends BaseTi4Test {

    @BeforeEach
    void setUp() {
        JdaService.testingMode = true;
        JdaService.jda = mock(JDA.class);
    }

    private static TextChannel givePrivateChannel(Player player) {
        TextChannel channel = mock(TextChannel.class);
        String channelId = player.getFaction() + "-priv";
        player.setPrivateChannelID(channelId);
        when(JdaService.jda.getTextChannelById(channelId)).thenReturn(channel);
        return channel;
    }

    private static TextChannel giveMainChannel(Game game) {
        // Deep-stubbed: markActionCardWithPingType calls channel.addReactionById(...).queue(...) directly
        // (bypassing MessageHelper entirely), which a plain mock() would answer with null and NPE on.
        TextChannel channel = mock(TextChannel.class, RETURNS_DEEP_STUBS);
        game.setMainChannelID("main-chan");
        when(JdaService.jda.getTextChannelById("main-chan")).thenReturn(channel);
        return channel;
    }

    /**
     * An actor, EVERY other real player who also sees the same tile, and one real player who does not.
     * Every "seeing" peer must be accounted for (channel given or explicitly asserted about) - the
     * fixture is a real 5-player map, so more than one peer commonly shares vision of a tile.
     */
    private record VisibilityCase(Player actor, List<Player> visiblePeers, Player invisiblePeer, Tile tile) {}

    private static VisibilityCase findVisibilityCase(Game game) {
        for (Tile tile : game.getTileMap().values()) {
            String position = tile.getPosition();
            List<Player> seeing = game.getRealPlayers().stream()
                    .filter(p -> FoWHelper.getTilePositionsToShow(game, p).contains(position))
                    .toList();
            if (seeing.size() < 2) continue;
            List<Player> notSeeing = game.getRealPlayers().stream()
                    .filter(p -> !seeing.contains(p))
                    .toList();
            if (notSeeing.isEmpty()) continue;
            Player actor = seeing.get(0);
            List<Player> visiblePeers = seeing.subList(1, seeing.size());
            return new VisibilityCase(actor, visiblePeers, notSeeing.get(0), tile);
        }
        return null;
    }

    /** Like {@link VisibilityCase}, but anchored on a planet the actor actually owns. */
    private record PlanetVisibilityCase(Player actor, String planetId, List<Player> visiblePeers) {}

    private static PlanetVisibilityCase findOwnPlanetVisibilityCase(Game game) {
        for (Player actor : game.getRealPlayers()) {
            for (String planetId : actor.getPlanets()) {
                Tile tile = game.getTileFromPlanet(planetId);
                if (tile == null) continue;
                String position = tile.getPosition();
                List<Player> visiblePeers = game.getRealPlayers().stream()
                        .filter(p -> p != actor)
                        .filter(p -> FoWHelper.getTilePositionsToShow(game, p).contains(position))
                        .toList();
                if (!visiblePeers.isEmpty()) {
                    return new PlanetVisibilityCase(actor, planetId, visiblePeers);
                }
            }
        }
        return null;
    }

    // ---- public route: anonymised, main-channel-only -------------------------------------------

    @Test
    void pingPlanetTarget_publicRouteAnnouncesAnonymisedlyToMainChannelOnly() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            Player actor = game.getRealPlayers().getFirst();
            String planetId = actor.getPlanets().getFirst();
            TextChannel mainChannel = giveMainChannel(game);

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class)) {
                boolean sent = ActionCardPingButtonHandler.pingPlanetTarget(game, actor, planetId, true, "Sabotage");

                assertThat(sent).isTrue();
                mh.verify(() -> MessageHelper.sendMessageToChannel(
                        eq(mainChannel),
                        argThat(msg -> msg.contains("Someone")
                                && !msg.contains(actor.getFaction())
                                && !msg.contains(actor.getColor()))));
            }
        }
    }

    @Test
    void pingSystemTarget_publicRouteAnnouncesAnonymisedlyToMainChannelOnly() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            Player actor = game.getRealPlayers().getFirst();
            var visible = FoWHelper.getTilePositionsToShow(game, actor);
            assertThat(visible).as("actor should see at least one system").isNotEmpty();
            String position = visible.iterator().next();
            TextChannel mainChannel = giveMainChannel(game);

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class)) {
                boolean sent = ActionCardPingButtonHandler.pingSystemTarget(game, actor, position, true, "Sabotage");

                assertThat(sent).isTrue();
                mh.verify(() -> MessageHelper.sendMessageToChannel(
                        eq(mainChannel),
                        argThat(msg -> msg.contains("Someone")
                                && msg.contains(position)
                                && !msg.contains(actor.getFaction()))));
            }
        }
    }

    @Test
    void pingPlayerTarget_publicRouteAnnouncesAnonymisedlyToMainChannelOnly() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            Player actor = game.getRealPlayers().get(0);
            Player target = game.getRealPlayers().get(1);
            TextChannel mainChannel = giveMainChannel(game);

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class)) {
                boolean sent = ActionCardPingButtonHandler.pingPlayerTarget(
                        game, actor, target.getFaction(), true, "Sabotage");

                assertThat(sent).isTrue();
                // Only the ACTOR is anonymised - the target is meant to be named.
                mh.verify(() -> MessageHelper.sendMessageToChannel(
                        eq(mainChannel),
                        argThat(msg -> msg.contains("Someone")
                                && !msg.contains(actor.getFaction())
                                && msg.contains(target.getFactionNameOrColor()))));
            }
        }
    }

    // ---- non-public route: vision-based recipients (the key regression) ------------------------

    @Test
    void pingSystemTarget_nonPublicRoutePrivatelyNotifiesOnlyPlayersWithCurrentVisionOfTheSystem() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);

            VisibilityCase c = findVisibilityCase(game);
            assertThat(c)
                    .as("default test map should have a tile two players see and a third does not")
                    .isNotNull();

            TextChannel actorChannel = givePrivateChannel(c.actor());
            List<TextChannel> visibleChannels = c.visiblePeers().stream()
                    .map(ActionCardPingButtonHandlerTest::givePrivateChannel)
                    .toList();
            // c.invisiblePeer() deliberately has no private channel: if recipient selection ever
            // regressed back to presence-based, they would be notified, and privateChannelOrReport
            // would log a "no private channel" GM warning naming them - ruled out below.

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class);
                    MockedStatic<GMService> gm = mockStatic(GMService.class)) {
                boolean sent = ActionCardPingButtonHandler.pingSystemTarget(
                        game, c.actor(), c.tile().getPosition(), false, "");

                assertThat(sent).isTrue();
                for (TextChannel visibleChannel : visibleChannels) {
                    mh.verify(() -> MessageHelper.sendMessageToChannel(eq(visibleChannel), any()));
                }
                mh.verify(() -> MessageHelper.sendMessageToChannel(
                        eq(actorChannel),
                        argThat(msg -> c.visiblePeers().stream().noneMatch(p -> msg.contains(p.getFaction()))
                                && !msg.contains(c.invisiblePeer().getFaction()))));
                gm.verify(
                        () -> GMService.logActivity(
                                any(),
                                argThat(m -> m.contains(c.invisiblePeer().getRepresentationUnfogged())),
                                anyBoolean()),
                        never());
            }
        }
    }

    @Test
    void pingPlanetTarget_nonPublicRouteSharesTheSameVisionFilterAsSystemPing() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);

            PlanetVisibilityCase c = findOwnPlanetVisibilityCase(game);
            assertThat(c)
                    .as("default test map should have a player-owned planet a peer can currently see")
                    .isNotNull();

            givePrivateChannel(c.actor());
            List<TextChannel> visibleChannels = c.visiblePeers().stream()
                    .map(ActionCardPingButtonHandlerTest::givePrivateChannel)
                    .toList();

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class);
                    MockedStatic<GMService> gm = mockStatic(GMService.class)) {
                boolean sent = ActionCardPingButtonHandler.pingPlanetTarget(game, c.actor(), c.planetId(), false, "");

                assertThat(sent).isTrue();
                for (TextChannel visibleChannel : visibleChannels) {
                    mh.verify(() -> MessageHelper.sendMessageToChannel(eq(visibleChannel), any()));
                }
                gm.verify(() -> GMService.logActivity(any(), any(), anyBoolean()), never());
            }
        }
    }

    @Test
    void pingPlayerTarget_nonPublicRouteNotifiesOnlyTheTargetAndConfirmsToActorWithoutNamingAnyoneElse() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            Player actor = game.getRealPlayers().get(0);
            Player target = game.getRealPlayers().get(1);
            Player bystander = game.getRealPlayers().get(2);

            TextChannel actorChannel = givePrivateChannel(actor);
            TextChannel targetChannel = givePrivateChannel(target);
            TextChannel bystanderChannel = givePrivateChannel(bystander);

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class)) {
                boolean sent = ActionCardPingButtonHandler.pingPlayerTarget(
                        game, actor, target.getFaction(), false, "Sabotage");

                assertThat(sent).isTrue();
                mh.verify(() -> MessageHelper.sendMessageToChannel(eq(targetChannel), any()));
                mh.verify(() -> MessageHelper.sendMessageToChannel(
                        eq(actorChannel), argThat(msg -> msg.contains(target.getFactionNameOrColor()))));
                mh.verify(() -> MessageHelper.sendMessageToChannel(eq(bystanderChannel), any()), never());
            }
        }
    }

    // ---- missing private channel: the failure shape is asymmetric, not assumed ------------------

    @Test
    void pingPlayerTarget_missingTargetChannelFailsAndIsReportedToCaller() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            Player actor = game.getRealPlayers().get(0);
            Player target = game.getRealPlayers().get(1);
            // target deliberately left without a private channel.

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class);
                    MockedStatic<GMService> gm = mockStatic(GMService.class)) {
                boolean sent = ActionCardPingButtonHandler.pingPlayerTarget(
                        game, actor, target.getFaction(), false, "Sabotage");

                assertThat(sent).isFalse();
                gm.verify(() -> GMService.logActivity(
                        any(), argThat(m -> m.toLowerCase().contains("no private channel")), eq(true)));
                mh.verify(() -> MessageHelper.sendMessageToChannel(any(), any()), never());
            }
        }
    }

    @Test
    void pingSystemTarget_missingRecipientChannelIsLoggedButPingStillReportsSuccess() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);

            VisibilityCase c = findVisibilityCase(game);
            assertThat(c).isNotNull();
            givePrivateChannel(c.actor());
            // c.visiblePeers() deliberately left without private channels - possibly more than one,
            // so the GM log below is asserted at least once, not exactly once.

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class);
                    MockedStatic<GMService> gm = mockStatic(GMService.class)) {
                boolean sent = ActionCardPingButtonHandler.pingSystemTarget(
                        game, c.actor(), c.tile().getPosition(), false, "");

                // Surprising but real: routeSystem/routePlanet never fail the whole ping just because
                // one recipient has no private channel - only routePlayer's single-recipient path does
                // that (see the test above). A future change making this false too would be a
                // deliberate decision, not a silent break of this pin.
                assertThat(sent).isTrue();
                gm.verify(
                        () -> GMService.logActivity(
                                any(), argThat(m -> m.toLowerCase().contains("no private channel")), eq(true)),
                        atLeastOnce());
            }
        }
    }

    @Test
    void resolveRoute_reportsFizzleToTheUiWhenThePingItselfFails() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            Player actor = game.getRealPlayers().get(0);
            Player target = game.getRealPlayers().get(1);
            TextChannel actorChannel = givePrivateChannel(actor);
            // target deliberately left without a private channel, so the underlying ping fails.

            String msgId = "13243546";
            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class)) {
                ActionCardPingButtonHandler.pickType(
                        game, actor, Constants.AC_PING_PICK + "player_" + msgId + "_Sabotage");
            }

            ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class);
                    MockedStatic<GMService> gm = mockStatic(GMService.class)) {
                ActionCardPingButtonHandler.resolveRoute(
                        event,
                        game,
                        actor,
                        Constants.AC_PING_ROUTE + "local_player_" + msgId + "_" + target.getFaction());

                mh.verify(() -> MessageHelper.sendMessageToChannel(
                        eq(actorChannel), argThat(msg -> PlanetTargetService.messagePool().stream()
                                .anyMatch(msg::contains))));
            }
            verify(event.getHook().deleteOriginal()).queue(any(), any());
        }
    }

    // ---- route-id token fix: targetKey survives the split even with embedded underscores --------

    @Test
    void resolveRoute_targetKeySurvivesTheFourWaySplitEvenWithEmbeddedUnderscores() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game realGame = harness.load();
            realGame.setFowMode(true);
            Player actor = realGame.getRealPlayers().getFirst();
            givePrivateChannel(actor);
            Game game = spy(realGame);

            // "pi_arborec" is a real Project Pi faction id and legitimately contains an underscore. It
            // doesn't need to resolve to an actual player here - the point is that the full string
            // reaches getPlayerFromColorOrFaction as ONE argument, proving targetKey (the trailing
            // split("_", 4) field) survives regardless of embedded underscores. Before the fix that
            // moved the card title out of this id (replacing it with the numeric, underscore-free
            // token), an underscore in the OLD middle field (the title) would have shifted this
            // argument and truncated it at "pi".
            String buttonID = Constants.AC_PING_ROUTE + "public_player_87654321_pi_arborec";
            ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class)) {
                ActionCardPingButtonHandler.resolveRoute(event, game, actor, buttonID);
            }

            verify(game).getPlayerFromColorOrFaction("pi_arborec");
        }
    }

    // ---- full flow: pickType -> resolvePlanet -> resolveRoute, title round-trip included ---------

    @Test
    void fullFlow_pickTypeThroughResolvePlanetThroughResolveRouteEndToEndForANormalTarget() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            Player actor = game.getRealPlayers().getFirst();
            String planetId = actor.getPlanets().getFirst();
            TextChannel actorChannel = givePrivateChannel(actor);
            TextChannel mainChannel = giveMainChannel(game);

            // 18 digits: flowToken must keep only the last 8 ("44332211").
            String msgId = "998877665544332211";
            String title = "Sabotage";

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class)) {
                ActionCardPingButtonHandler.pickType(
                        game, actor, Constants.AC_PING_PICK + "planet_" + msgId + "_" + title);
            }
            // getStoredValueMap() holds raw, ESCAPED values (Game.getStoredValue unescapes on read) -
            // compare through the real accessor rather than the raw map, matching what peekCardTitle
            // itself does.
            assertThat(game.getStoredValueMap().keySet())
                    .as("pickType must store round|title keyed by the message id's last 8 digits")
                    .anySatisfy(key -> {
                        assertThat(key).endsWith("_44332211");
                        assertThat(game.getStoredValue(key)).isEqualTo(game.getRound() + "|" + title);
                    });

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class)) {
                ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
                ActionCardPingButtonHandler.resolvePlanet(
                        event, game, actor, Constants.AC_PING_PLANET + "_44332211_" + planetId);

                mh.verify(() -> MessageHelper.sendMessageToChannelWithButtons(
                        eq(actorChannel), any(), argThat(buttons -> buttons.size() == 2)));
            }

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class)) {
                ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
                ActionCardPingButtonHandler.resolveRoute(
                        event, game, actor, Constants.AC_PING_ROUTE + "public_planet_44332211_" + planetId);

                // The original, untruncated title must survive pickType's storage -> peekCardTitle's
                // retrieval -> targetingLine's "_<title>_" formatting.
                mh.verify(() -> MessageHelper.sendMessageToChannel(
                        eq(mainChannel), argThat(msg -> msg.contains("_" + title + "_"))));
            }
        }
    }

    @Test
    void peekCardTitle_returnsTheFullStoredTitleNotTheButtonIdTruncatedOne() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            Player actor = game.getRealPlayers().getFirst();
            String planetId = actor.getPlanets().getFirst();
            givePrivateChannel(actor);
            TextChannel mainChannel = giveMainChannel(game);

            // Longer than MAX_TITLE_LENGTH_IN_BUTTON_ID (30). sendPingPrompt would truncate this for
            // its OWN button id, but that cap only ever bounded sendPingPrompt's id length - pickType
            // stores whatever it is given, and peekCardTitle must hand the whole thing back untouched.
            String longTitle = "A Very Long Homebrew Action Card Title Indeed";
            assertThat(longTitle.length()).isGreaterThan(30);

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class)) {
                ActionCardPingButtonHandler.pickType(
                        game, actor, Constants.AC_PING_PICK + "planet_55555555_" + longTitle);
            }

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class)) {
                ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
                ActionCardPingButtonHandler.resolveRoute(
                        event, game, actor, Constants.AC_PING_ROUTE + "public_planet_55555555_" + planetId);

                mh.verify(() -> MessageHelper.sendMessageToChannel(
                        eq(mainChannel), argThat(msg -> msg.contains("_" + longTitle + "_"))));
            }
        }
    }

    // ---- forgetAbandonedFlows: cleanup is per-player and only for a stale (earlier) round --------

    @Test
    void forgetAbandonedFlows_isCurrentlyANoOpBecauseItReadsTheRawEscapedValueDirectly() {
        // KNOWN PRE-EXISTING BUG, not by design: Game.setStoredValue escapes its value ("|" becomes
        // "{pip}"), and Game.getStoredValue un-escapes on read - but forgetAbandonedFlows reads
        // entry.getValue() straight off the raw map, so it is searching escaped text for a literal "|"
        // that is never there. StringUtils.substringBefore then returns the whole (escaped) string,
        // StringUtils.isNumeric on that is always false, and no entry is ever judged stale. The result
        // is a small, harmless storage leak (one short string per player per ping-flow start, forever)
        // rather than a fog or correctness issue, so this pins the CURRENT behavior rather than the
        // intended one. If forgetAbandonedFlows is ever fixed to read through game.getStoredValue(key)
        // instead, this test should be rewritten to assert the stale entry actually gets swept.
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            Player actorA = game.getRealPlayers().get(0);

            ActionCardPingButtonHandler.pickType(game, actorA, Constants.AC_PING_PICK + "player_11111111_Old");
            game.setRound(game.getRound() + 1);
            ActionCardPingButtonHandler.pickType(game, actorA, Constants.AC_PING_PICK + "player_22222222_New");

            assertThat(game.getStoredValueMap().keySet())
                    .as("the earlier round's entry survives today - this is the bug, not the goal")
                    .anyMatch(k -> k.endsWith("_11111111"))
                    .anyMatch(k -> k.endsWith("_22222222"));
        }
    }
}
