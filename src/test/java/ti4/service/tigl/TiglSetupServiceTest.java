package ti4.service.tigl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.TIGLHelper;
import ti4.testUtils.BaseTi4Test;

/**
 * The Standard Ladder ruleset, as published by the league: 6 players, both expansions and all codices, no events or
 * scenarios, no homebrew, 10/12/14 points, 5 stage-1, 5 stage-2, 3 secrets. Thunder's Edge is required rather than
 * forbidden - the non-Fractured ladder is reported upstream as "ThundersEdge" and every new game enables it.
 */
class TiglSetupServiceTest extends BaseTi4Test {

    private static Game standardGame() {
        Game game = new Game();
        game.setName("tigl-standard");
        game.newGameSetup();
        for (int i = 1; i <= 6; i++) {
            Player player = game.addPlayer("user" + i, "player" + i);
            player.setFaction("sol");
            player.setColor("blue");
        }
        game.setVp(10);
        game.setMaxSOCountPerPlayer(3);
        game.setPublicObjectives1Peekable(new ArrayList<>(List.of("a", "b", "c", "d", "e")));
        game.setPublicObjectives2Peekable(new ArrayList<>(List.of("f", "g", "h", "i", "j")));
        return game;
    }

    @Test
    void aCleanStandardGameHasNoViolations() {
        assertThat(TiglSetupService.validateStandardLadder(standardGame())).isEmpty();
    }

    // Regression pin: newGameSetup() turns Thunder's Edge on for every game, so treating TE as homebrew would have
    // fired this warning on essentially every standard-ladder game.
    @Test
    void aFreshGameIsNotFlaggedForThundersEdge() {
        assertThat(TiglSetupService.validateStandardLadder(standardGame()))
                .noneMatch(violation -> violation.contains("Thunder's Edge"));
    }

    @Test
    void thundersEdgeBeingOffIsAViolation() {
        Game game = standardGame();
        game.setThundersEdge(false);
        assertThat(TiglSetupService.validateStandardLadder(game))
                .anyMatch(violation -> violation.contains("Thunder's Edge is off"));
    }

    @Test
    void theWrongPlayerCountIsAViolation() {
        Game game = standardGame();
        Player seventh = game.addPlayer("user7", "player7");
        seventh.setFaction("hacan");
        seventh.setColor("red");
        assertThat(TiglSetupService.validateStandardLadder(game)).anyMatch(v -> v.startsWith("7 players"));
    }

    @Test
    void onlyTenTwelveAndFourteenPointsAreAllowed() {
        for (int vp : List.of(10, 12, 14)) {
            Game game = standardGame();
            game.setVp(vp);
            assertThat(TiglSetupService.validateStandardLadder(game)).isEmpty();
        }
        for (int vp : List.of(11, 13)) {
            Game game = standardGame();
            game.setVp(vp);
            assertThat(TiglSetupService.validateStandardLadder(game))
                    .anyMatch(v -> v.contains("Victory points are " + vp));
        }
    }

    @Test
    void prophecyOfKingsMustBeOn() {
        Game game = standardGame();
        game.setProphecyOfKings(false);
        assertThat(TiglSetupService.validateStandardLadder(game)).anyMatch(v -> v.contains("Prophecy of Kings is off"));
    }

    @Test
    void objectiveAndSecretCountsAreChecked() {
        Game game = standardGame();
        game.setMaxSOCountPerPlayer(4);
        game.setPublicObjectives1Peekable(new ArrayList<>(List.of("a", "b", "c", "d")));
        List<String> violations = TiglSetupService.validateStandardLadder(game);
        assertThat(violations).anyMatch(v -> v.contains("Secret objectives per player are 4"));
        assertThat(violations).anyMatch(v -> v.startsWith("4 stage 1 objectives"));
    }

    @Test
    void galacticEventsAreAViolation() {
        Game game = standardGame();
        game.setEventDeckID("events_pok");
        assertThat(TiglSetupService.validateStandardLadder(game)).anyMatch(v -> v.contains("Galactic Events"));
    }

    // The violation no longer enumerates modes - isNormalGame() is the project's own list, so a new mode is
    // covered without touching this service.
    @Test
    void homebrewContentIsAViolation() {
        Game game = standardGame();
        game.setDiscordantStarsMode(true);
        assertThat(TiglSetupService.validateStandardLadder(game)).anyMatch(v -> v.contains("homebrew"));
    }

    @Test
    void scenariosAreAViolation() {
        Game game = standardGame();
        game.setLiberationC4Mode(true);
        assertThat(TiglSetupService.validateStandardLadder(game)).anyMatch(v -> v.contains("scenario"));
    }

    // hasHomebrew() counts "fewer than 3 players" as homebrew, so a one-player test game used to be told only the
    // Fractured ladder applied. Player count does not block the Standard ladder - only the modes the
    // setCompetitiveTIGLGame setter refuses do.
    @Test
    void aSmallTestGameIsNotPushedOntoTheFracturedLadder() {
        Game game = new Game();
        game.newGameSetup();
        Player solo = game.addPlayer("user1", "player1");
        solo.setFaction("sol");
        solo.setColor("blue");

        assertThat(game.hasHomebrew()).isTrue();
        assertThat(game.hasStandardLadderIncompatibleMode()).isFalse();
    }

    @Test
    void modesThatTheSetterRefusesDoForceFractured() {
        Game game = standardGame();
        game.setDiscordantStarsMode(true);

        assertThat(game.hasStandardLadderIncompatibleMode()).isTrue();
    }

    // The prompt message keeps working buttons in the channel forever, so without this a winner could scroll back in
    // round 6 and press "Standard Ladder", or a loser could press "Remove TIGL". Lazik asked for exactly this to be
    // impossible. The command and the buttons share this one predicate so they cannot drift apart.
    @Test
    void theLadderLocksToBothelpersOnceTheGameHasStarted() {
        Game game = standardGame();

        game.setRound(1);
        assertThat(TiglSetupService.mayChangeLadder(game, false)).isTrue();
        assertThat(TiglSetupService.mayChangeLadder(game, true)).isTrue();

        game.setRound(2);
        assertThat(TiglSetupService.mayChangeLadder(game, false)).isFalse();
        assertThat(TiglSetupService.mayChangeLadder(game, true)).isTrue();

        game.setRound(6);
        assertThat(TiglSetupService.mayChangeLadder(game, false)).isFalse();
    }

    // Alliance and Community are refused by setCompetitiveTIGLGame unconditionally, so Fractured is not a way out of
    // them - telling the table to pick Fractured would be dead-end advice.
    @Test
    void allianceCannotBeTiglOnEitherLadder() {
        Game game = standardGame();
        game.setAllianceMode(true);

        assertThat(game.hasTiglIncompatibleMode()).isTrue();

        for (String choice : List.of(TiglSetupService.STANDARD, TiglSetupService.FRACTURED)) {
            Game attempt = standardGame();
            attempt.setAllianceMode(true);
            String response =
                    TiglSetupService.applyLadderChoice(attempt, choice).message();

            assertThat(response).contains("cannot be mixed with Alliance or Community mode");
            assertThat(response).doesNotContain("Choose **TIGL - Fractured Ladder**");
            assertThat(attempt.isCompetitiveTIGLGame()).isFalse();
        }
    }

    // Per the published Fractured ruleset: 6, 7 or 8 players - unlike Standard, which is exactly 6.
    @Test
    void theFracturedLadderAllowsSevenAndEightPlayers() {
        for (int extra = 1; extra <= 2; extra++) {
            int expectedCount = 6 + extra;
            Game game = standardGame();
            for (int i = 0; i < extra; i++) {
                Player added = game.addPlayer("extra" + i, "extra" + i);
                added.setFaction("hacan");
                added.setColor("red");
            }
            assertThat(TiglSetupService.validateLadder(game, true))
                    .as("%d players should be a legal Fractured game", expectedCount)
                    .isEmpty();
            // Standard is still exactly six.
            assertThat(TiglSetupService.validateLadder(game, false))
                    .anyMatch(v -> v.startsWith(expectedCount + " players"));
        }
    }

    @Test
    void theFracturedLadderStillRejectsCountsOutsideSixToEight() {
        Game nine = standardGame();
        for (int i = 0; i < 3; i++) {
            Player added = nine.addPlayer("extra" + i, "extra" + i);
            added.setFaction("hacan");
            added.setColor("red");
        }
        assertThat(TiglSetupService.validateLadder(nine, true)).anyMatch(v -> v.startsWith("9 players"));

        Game solo = new Game();
        solo.newGameSetup();
        Player only = solo.addPlayer("user1", "player1");
        only.setFaction("sol");
        only.setColor("blue");
        assertThat(TiglSetupService.validateLadder(solo, true)).anyMatch(v -> v.startsWith("1 players"));
    }

    // "10+ points" is a Fractured rule; Standard is restricted to exactly 10, 12 or 14.
    @Test
    void theFracturedLadderAllowsAnyPointTotalFromTenUpwards() {
        for (int vp : List.of(10, 11, 14, 20)) {
            Game game = standardGame();
            game.setVp(vp);
            assertThat(TiglSetupService.validateLadder(game, true))
                    .as("%d victory points should be a legal Fractured game", vp)
                    .isEmpty();
        }
        Game tooFew = standardGame();
        tooFew.setVp(8);
        assertThat(TiglSetupService.validateLadder(tooFew, true)).anyMatch(v -> v.contains("Victory points are 8"));
    }

    // Fractured tolerates the homebrew that Standard forbids, so those rules must not leak into its check.
    @Test
    void fracturedIsNotHeldToTheStandardLadderRuleset() {
        Game game = standardGame();
        game.setDiscordantStarsMode(true);
        game.setVp(11);

        assertThat(TiglSetupService.validateLadder(game, true)).isEmpty();
        assertThat(TiglSetupService.validateLadder(game, false)).isNotEmpty();
    }

    // validateLadder is called from enforceLadderRules to decide whether to remediate, and again to test whether
    // the other ladder would accept the game. If it mutated anything, that second speculative call would change
    // the game it was only meant to ask about.
    @Test
    void validateLadderDoesNotChangeTheGame() {
        Game game = standardGame();
        game.setDiscordantStarsMode(true);
        TIGLHelper.initializeTIGLGame(game, true);
        game.setMinimumTIGLRankAtGameStart(TIGLHelper.TIGLRank.COMMANDER);

        assertThat(TiglSetupService.validateLadder(game, false)).isNotEmpty();
        assertThat(TiglSetupService.validateLadder(game, true)).isEmpty();

        assertThat(game.isCompetitiveTIGLGame()).isTrue();
        assertThat(TIGLHelper.isFracturedTIGLGame(game)).isTrue();
        assertThat(game.getMinimumTIGLRankAtGameStart()).isEqualTo(TIGLHelper.TIGLRank.COMMANDER);
    }

    @Test
    void confirmingTheLadderClearsThePendingFlag() {
        Game game = standardGame();

        TiglSetupService.applyLadderChoice(game, TiglSetupService.STANDARD);

        assertThat(game.isCompetitiveTIGLGame()).isTrue();
    }

    @Test
    void removingTiglClearsTheFlagAndAnswersThePrompt() {
        Game game = standardGame();
        game.setCompetitiveTIGLGame(true);

        String response = TiglSetupService.applyLadderChoice(game, TiglSetupService.CASUAL)
                .message();

        assertThat(response).contains("TIGL flag has been removed");
        assertThat(game.isCompetitiveTIGLGame()).isFalse();
    }

    @Test
    void theNameCheckIsCaseInsensitive() {
        Game game = new Game();
        game.setCustomName("TIGL Sunday Showdown");
        assertThat(TiglSetupService.looksLikeTiglGame(game)).isTrue();

        game.setCustomName("a tigl rematch");
        assertThat(TiglSetupService.looksLikeTiglGame(game)).isTrue();

        game.setCustomName("Friday Casual");
        assertThat(TiglSetupService.looksLikeTiglGame(game)).isFalse();
    }

    // enforceLadderRules runs from /cards_so deal_to_all, which a bothelper can use at any point in a game. Without
    // the round guard, dealing a replacement secret in round 6 would re-run the setup ruleset and strip TIGL from a
    // game that is already being played - and nothing would put it back.
    @Test
    void enforcementDoesNotTouchAGameThatIsAlreadyUnderway() {
        Game game = standardGame();
        game.setVp(11);
        game.setCompetitiveTIGLGame(true);
        game.setRound(6);

        assertThat(TiglSetupService.validateLadder(game, false)).isNotEmpty();

        TiglSetupService.enforceLadderRules(game, null);

        assertThat(game.isCompetitiveTIGLGame()).isTrue();
    }

    // The expansion chooser turns Thunder's Edge off whenever it sets useOldPok, but SourceSettings' "ThundersEdge"
    // toggle turns TE back on WITHOUT clearing useOldPok. Such a game reaches RoundOneService with TE reading true,
    // and only there does roundOne swap the PoK decks back in and set TE false - too late for the ladder check.
    // Reading the stored value directly means the ladder check does not depend on when it runs.
    @Test
    void aGameStillOnOldProphecyOfKingsComponentsIsNotStandardLadder() {
        Game game = standardGame();
        game.setStoredValue("useOldPok", "true");

        assertThat(game.isThundersEdge()).isTrue();
        assertThat(TiglSetupService.validateStandardLadder(game))
                .anyMatch(v -> v.contains("old Prophecy of Kings components"));

        // Fractured tolerates it, so the game is remediated rather than stripped of TIGL.
        assertThat(TiglSetupService.validateLadder(game, true)).isEmpty();
    }

    // Only one of the two should fire, or the table gets told both that TE is off and that it is on-but-old.
    @Test
    void theTwoThundersEdgeViolationsAreMutuallyExclusive() {
        Game game = standardGame();
        game.setStoredValue("useOldPok", "true");
        game.setThundersEdge(false);

        List<String> violations = TiglSetupService.validateStandardLadder(game);
        assertThat(violations).anyMatch(v -> v.contains("Thunder's Edge is off"));
        assertThat(violations).noneMatch(v -> v.contains("old Prophecy of Kings components"));
    }

    // isProphecyOfKings() is initialised true, never set false anywhere in src/main/java, and never persisted - the
    // flag production actually writes when the table turns PoK off is baseGameMode (SourceSettings sets it as
    // !pok.isVal()). Checking only isProphecyOfKings() made this rule unreachable.
    @Test
    void turningProphecyOfKingsOffViaBaseGameModeIsAViolation() {
        Game game = standardGame();
        game.setBaseGameMode(true);

        assertThat(game.isProphecyOfKings()).isTrue();
        assertThat(TiglSetupService.validateStandardLadder(game)).anyMatch(v -> v.contains("Prophecy of Kings is off"));
    }

    // markAsTIGLGame used to clear the Fractured tag BEFORE calling the setter, so the setter then saw a
    // non-Fractured game, refused, and cascaded into clearTIGLSetupState() - nulling the game's minimum rank and
    // every player's rank-at-game-start. The caller only reported "choose Fractured instead".
    @Test
    void aRefusedStandardChoiceLeavesAnExistingFracturedGameIntact() {
        Game game = standardGame();
        game.setDiscordantStarsMode(true);
        TIGLHelper.initializeTIGLGame(game, true);

        assertThat(game.isCompetitiveTIGLGame()).isTrue();
        assertThat(TIGLHelper.isFracturedTIGLGame(game)).isTrue();
        game.setMinimumTIGLRankAtGameStart(TIGLHelper.TIGLRank.COMMANDER);

        String response = TiglSetupService.applyLadderChoice(game, TiglSetupService.STANDARD)
                .message();

        assertThat(response).contains("Fractured");
        assertThat(game.isCompetitiveTIGLGame()).isTrue();
        assertThat(TIGLHelper.isFracturedTIGLGame(game)).isTrue();
        assertThat(game.getMinimumTIGLRankAtGameStart()).isEqualTo(TIGLHelper.TIGLRank.COMMANDER);
    }

    // The prompt message stays live for the whole of round 1, and /tigl enable can answer it from elsewhere. Keying
    // the "consume the buttons" decision on tiglPrompt meant a REFUSED press deleted a prompt that an earlier answer
    // had already marked answered, leaving the table with no buttons and no valid choice made.
    @Test
    void aRefusedChoiceIsReportedAsNotAccepted() {
        Game game = standardGame();
        game.setDiscordantStarsMode(true);

        TiglSetupService.LadderChoice refused = TiglSetupService.applyLadderChoice(game, TiglSetupService.STANDARD);
        assertThat(refused.accepted()).isFalse();
        assertThat(refused.message()).contains("Fractured");

        TiglSetupService.LadderChoice accepted = TiglSetupService.applyLadderChoice(game, TiglSetupService.FRACTURED);
        assertThat(accepted.accepted()).isTrue();
    }

    @Test
    void allianceIsReportedAsNotAcceptedOnEitherLadder() {
        for (String choice : List.of(TiglSetupService.STANDARD, TiglSetupService.FRACTURED)) {
            Game game = standardGame();
            game.setAllianceMode(true);
            assertThat(TiglSetupService.applyLadderChoice(game, choice).accepted())
                    .as("alliance mode must not be accepted for %s", choice)
                    .isFalse();
        }
    }

    // Changing the victory point count with /game setup used to leave a Standard-ladder game reported to the league
    // under a ruleset it no longer met, because enforcement only ever ran at the round-one secret objective deal.
    // recheckLadder is deliberately NOT round-gated: a deliberate mid-game ruleset change must be caught.
    @Test
    void changingTheRulesetMidGameMovesTheGameToFractured() {
        Game game = standardGame();
        TIGLHelper.initializeTIGLGame(game, false);
        game.setRound(4);
        game.setVp(11);

        TiglSetupService.recheckLadder(game);

        assertThat(game.isCompetitiveTIGLGame()).isTrue();
        assertThat(TIGLHelper.isFracturedTIGLGame(game)).isTrue();
    }

    @Test
    void changingTheRulesetBeyondEitherLadderRemovesTigl() {
        Game game = standardGame();
        TIGLHelper.initializeTIGLGame(game, false);
        game.setRound(4);
        game.setVp(5);

        TiglSetupService.recheckLadder(game);

        assertThat(game.isCompetitiveTIGLGame()).isFalse();
    }

    // Before anyone has a faction, getRealPlayers() is empty, so a check would report "0 players" and strip TIGL from
    // every freshly created game. CreateGameService posts the ladder prompt at exactly that moment.
    @Test
    void nothingIsCheckedBeforeAnyoneIsSeated() {
        Game game = new Game();
        game.newGameSetup();
        game.setName("tigl-fresh");
        game.addPlayer("user1", "player1");
        TIGLHelper.markAsTIGLGame(game, false);

        assertThat(game.getRealPlayers()).isEmpty();

        TiglSetupService.recheckLadder(game);
        TiglSetupService.enforceLadderRules(game, null);

        assertThat(game.isCompetitiveTIGLGame()).isTrue();
    }
}
