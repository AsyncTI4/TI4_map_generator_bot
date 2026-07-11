package ti4.service.combat;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.contest.replay.service.CombatReplayService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenPromissoryHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum.CrystellumAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum.CrystellumUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora.KaloraBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.vyserix.VyserixBreakthroughHandler;
import ti4.discord.interactions.commands.planet.PlanetExhaust;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.StringHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.fow.FOWCombatThreadMirroring;
import ti4.spring.context.SpringContext;

@UtilityClass
public class CombatRollPublication {
    public static void sendSpaceAssignHitsButtons(
            GenericInteractionCreateEvent event, Game game, Player opponent, Tile tile, int hits) {
        List<Button> buttons = new ArrayList<>();

        String plural = "hit" + (hits == 1 ? "" : "s");
        if (opponent.isDummy() || opponent.isNpc()) {
            String id = opponent.dummyPlayerSpoof() + "autoAssignSpaceHits_" + tile.getPosition() + "_" + hits;
            buttons.add(Buttons.green(id, "Auto-assign " + plural + " for Dummy"));

        } else {
            String assignID =
                    opponent.factionButtonChecker() + "autoAssignSpaceHits_" + tile.getPosition() + "_" + hits;
            buttons.add(Buttons.green(assignID, "Auto-assign " + plural));

            String manualID = "getDamageButtons_" + tile.getPosition() + "deleteThis_spacecombat";
            buttons.add(Buttons.red(manualID, "Manually Assign " + plural));

            String cancelID = opponent.factionButtonChecker() + "cancelSpaceHits_" + tile.getPosition() + "_" + hits;
            buttons.add(Buttons.gray(cancelID, "Cancel a Hit"));
        }

        String msg2 = opponent.getRepresentationNoPing() + ", you may automatically assign ";
        msg2 += (hits == 1 ? "the hit" : "hits") + ". ";
        msg2 += ButtonHelperModifyUnits.autoAssignSpaceCombatHits(opponent, game, tile, hits, event, true);
        if (opponent.hasRelic("metalivoidshielding")) {
            RelicModel relicModel = Mapper.getRelic("metalivoidshielding");
            msg2 += "\nReminder: You have the _" + relicModel.getName() + "_ relic,";
            msg2 += " you may SUSTAIN DAMAGE on one of your non-fighter ships instead of taking a hit.";
        }
        String combatRoundKey = "combatRoundTracker" + opponent.getFaction() + tile.getPosition() + "space";
        String combatRoundValue = game.getStoredValue(combatRoundKey);
        if (opponent.hasUnlockedBreakthrough("crystellumbt") && "1".equals(combatRoundValue)) {
            msg2 +=
                    "\nReminder: You have _Defensive Architecture_.\nFor each unit in the active system that is at capacity, you may give one other non-fighter ship in the same system SUSTAIN DAMAGE until the end of this combat. This is not tracked by the bot.";
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg2, buttons);
    }

    private static boolean isFoWPrivateChannelRoll(CombatRollPipelineState state) {
        return state.event.getMessageChannel().equals(state.player.getPrivateChannel());
    }

    static void publish(CombatRollPipelineState state) {
        MessageHelper.sendMessageToChannel(state.event.getMessageChannel(), "");
        AdjustedRollResult adjustedRoll = applyProximaBombardmentCancellation(state);
        String message = removeTrailingRollSeparator(adjustedRoll.message());
        message = appendAshenBombardmentReminder(state, message);
        MessageHelper.sendMessageToChannel(state.event.getMessageChannel(), message);
        boolean trackedCandidateRoll = mirrorCombatReplay(state, message);
        offerThalnosReroll(state, message);
        if (state.game.isFowMode()) {
            relayFogOfWarCombatResult(state, message);
            handleFogOfWarDummyCombatResult(state, adjustedRoll.hits());
        } else {
            reportSurprisingDiceRoll(state, message, trackedCandidateRoll);
            handlePublicCombatRoundResults(state, adjustedRoll.hits());
            handleAntiFighterBarrageResults(state, adjustedRoll.hits());
        }
        offerSpaceCannonHitAssignmentButtons(state, adjustedRoll.hits());
        offerVyserixMorayButtons(state, adjustedRoll.hits());
        handleBombardmentResults(state, adjustedRoll.hits());
        state.hits = adjustedRoll.hits();
    }

    private static String appendAshenBombardmentReminder(CombatRollPipelineState state, String message) {
        if (state.player.hasBreakthrough("ashenbt")) {
            return AshenBreakthroughHandler.appendBombardmentManualReminder(state.player, state.rollType, message);
        }
        return message;
    }

    private static AdjustedRollResult applyProximaBombardmentCancellation(CombatRollPipelineState state) {
        int hits = state.rollResult.totalHits();
        if (state.rollType != CombatRollType.bombardment
                || state.opponent == state.player
                || !state.opponent.hasTech("proxima")
                || hits < 1) {
            return new AdjustedRollResult(state.message, hits);
        }
        if (state.opponent.hasTech("tf-proxima")) {
            return new AdjustedRollResult(
                    state.message + "\n_Proxima Targeting VI_ canceled 1 hit automatically.", hits - 1);
        }
        if (state.bombardPlanet.isEmpty()) {
            return new AdjustedRollResult(state.message, hits);
        }
        UnitHolder planet = state.game.getUnitHolderFromPlanet(state.bombardPlanet);
        if (planet == null || planet.getGalvanizedUnitCount(state.player.getColorID()) < 1) {
            return new AdjustedRollResult(state.message, hits);
        }
        int adjustedHits = Math.max(0, hits - planet.getGalvanizedUnitCount(state.player.getColorID()));
        int canceledHits = hits - adjustedHits;
        String adjustedMessage = state.message + "\n_Proxima Targeting VI_ canceled " + canceledHits + " hit"
                + (canceledHits == 1 ? "" : "s") + " automatically.";
        return new AdjustedRollResult(adjustedMessage, adjustedHits);
    }

    private static String removeTrailingRollSeparator(String message) {
        return message.endsWith(";\n") ? message.substring(0, message.length() - 2) : message;
    }

    private static boolean mirrorCombatReplay(CombatRollPipelineState state, String message) {
        CombatReplayService combatReplayService = SpringContext.getBean(CombatReplayService.class);
        boolean trackedCandidateRoll = combatReplayService.isTrackedCandidateRoll(
                state.game, state.player, state.opponent, state.tile, state.rollType);
        combatReplayService.mirrorCombatRoll(
                state.game,
                state.player,
                state.opponent,
                state.tile,
                message,
                state.rollType,
                state.rollResult.whiff(),
                state.rollResult.slam(),
                state.payload);
        return trackedCandidateRoll;
    }

    private static void offerThalnosReroll(CombatRollPipelineState state, String message) {
        if (!message.contains("adding +1, at the risk of your")) {
            return;
        }
        Button thalnosButton = Buttons.green(
                "startThalnos_" + state.tile.getPosition() + "_" + state.unitHolderName,
                "Roll Thalnos",
                ExploreEmojis.Relic);
        Button decline = Buttons.gray("deleteButtons", "Decline");
        String thalnosMessage =
                "Use this button to roll for Thalnos.\n-# Note that if it matters, the dice were just rolled in the following format: (normal dice for unit 1)+(normal dice for unit 2)...etc...+(extra dice for unit 1)+(extra dice for unit 2)...etc.\n-# Sol and Letnev agents automatically are given as extra dice for unit 1.";
        MessageHelper.sendMessageToChannelWithButtons(
                state.event.getMessageChannel(), thalnosMessage, List.of(thalnosButton, decline));
    }

    private static void handleBombardmentResults(CombatRollPipelineState state, int hits) {
        if (state.rollType != CombatRollType.bombardment) return;

        offerAshenCommanderBombardmentButtons(state, hits);
        offerBombardmentHitAssignment(state, hits);
        offerMeteorSlingsInfantryReplacement(state, hits);
        offerKaloraBreakthroughInfantryCommit(state, hits);
        exhaustBombardedPlanetWithX89(state);
    }

    private static void offerAshenCommanderBombardmentButtons(CombatRollPipelineState state, int hits) {
        AshenLeadersHandler.offerCommanderBombardmentButtons(state.event, state.game, state.player, hits);
    }

    private static void offerBombardmentHitAssignment(CombatRollPipelineState state, int hits) {
        if (hits < 1) return;
        if (AshenLeadersHandler.offerHeroBombardmentAssignButtons(
                state.event, state.game, state.player, hits, state.bombardPlanet)) return;
        if (state.game.isFowMode()) return;

        List<Button> buttons = List.of(Buttons.red(
                "getDamageButtons_" + state.tile.getPosition() + "_bombardment",
                "Assign Hit" + (hits == 1 ? "" : "s")));
        for (Player target : state.game.getRealPlayersNNeutral()) {
            offerBombardmentHitAssignmentToTarget(state, target, hits, buttons);
        }
    }

    private static void offerBombardmentHitAssignmentToTarget(
            CombatRollPipelineState state, Player target, int hits, List<Button> buttons) {
        if (target == state.player || state.bombardPlanet.isEmpty()) return;
        if (!FoWHelper.playerHasUnitsOnPlanet(target, state.game.getUnitHolderFromPlanet(state.bombardPlanet))) return;

        if (target.isRealPlayer()) {
            sendBombardmentHitAssignmentToPlayer(state, target, hits, buttons);
        } else {
            sendBombardmentHitAssignmentForDummy(state, target, hits);
        }
    }

    private static void sendBombardmentHitAssignmentToPlayer(
            CombatRollPipelineState state, Player target, int hits, List<Button> buttons) {
        MessageHelper.sendMessageToChannelWithButtons(
                state.event.getMessageChannel(),
                target.getRepresentation() + ", please assign the BOMBARDMENT hit" + (hits == 1 ? "" : "s") + ".",
                buttons);
    }

    private static void sendBombardmentHitAssignmentForDummy(CombatRollPipelineState state, Player target, int hits) {
        List<Button> buttons = List.of(Buttons.green(
                target.dummyPlayerSpoof() + "autoAssignGroundHits_" + state.bombardPlanet + "_" + hits,
                "Auto-assign Hit" + (hits == 1 ? "" : "s") + " For Dummy"));
        MessageHelper.sendMessageToChannelWithButtons(
                state.event.getMessageChannel(),
                state.player.getRepresentation() + ", please assign the BOMBARDMENT hit" + (hits == 1 ? "" : "s")
                        + " for the dummy player.",
                buttons);
    }

    private static void offerMeteorSlingsInfantryReplacement(CombatRollPipelineState state, int hits) {
        if (hits < 1) return;
        if (!state.player.hasAbility("meteor_slings")
                && !state.player.getPromissoryNotes().containsKey("dspnkhra")) return;

        String planet = state.game.getStoredValue("bombardmentTarget" + state.player.getFaction());
        List<Button> buttons = List.of(
                Buttons.green(
                        state.player.factionButtonChecker() + "meteorSlings_" + planet,
                        "Infantry on " + Helper.getPlanetRepresentation(planet, state.game)),
                Buttons.red("deleteButtons", "Done"));
        String message = state.player.getRepresentation() + " you could potentially cancel "
                + (hits == 1 ? "the BOMBARDMENT hit" : "some BOMBARDMENT hits")
                + " to place infantry instead. Use these buttons to do so, and press done when done. The bot did not track how many hits you got. ";
        MessageHelper.sendMessageToChannelWithButtons(state.event.getMessageChannel(), message, buttons);
    }

    private static void offerKaloraBreakthroughInfantryCommit(CombatRollPipelineState state, int hits) {
        if (hits < 1 || !state.player.hasUnlockedBreakthrough("kalorabt")) return;
        KaloraBreakthroughHandler.offerCommitInfantryButton(
                state.event, state.game, state.player, state.tile, state.bombardPlanet);
    }

    private static void exhaustBombardedPlanetWithX89(CombatRollPipelineState state) {
        if (!state.player.hasTech("x89c4")) return;
        for (Player target : state.game.getRealPlayers()) {
            if (!target.hasPlanetReady(state.bombardPlanet)) continue;
            exhaustX89Target(state, target);
            return;
        }
    }

    private static void exhaustX89Target(CombatRollPipelineState state, Player target) {
        PlanetExhaust.doAction(target, state.bombardPlanet, state.game);
        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                target.getRepresentation() + ", your planet "
                        + Helper.getPlanetRepresentation(state.bombardPlanet, state.game) + " was exhausted when "
                        + (state.game.isFowMode() ? "another player" : state.player.getRepresentationNoPing())
                        + " bombarded it with _X-89 Bacterial Weapon ΩΩ_.");
    }

    private static void offerVyserixMorayButtons(CombatRollPipelineState state, int hits) {
        if (state.rollType == CombatRollType.AFB && state.player.hasUnlockedBreakthrough("vyserixbt")) {
            VyserixBreakthroughHandler.offerMoraySystemButtons(state.event, state.game, state.player, state.tile, hits);
        }
    }

    private static void offerSpaceCannonHitAssignmentButtons(CombatRollPipelineState state, int hits) {
        if ((!state.game.isFowMode() || isFoWPrivateChannelRoll(state))
                && state.rollType == CombatRollType.SpaceCannonOffence
                && hits > 0
                && state.opponent != state.player) {
            MessageChannel channel = isFoWPrivateChannelRoll(state)
                    ? state.opponent.getCorrectChannel()
                    : state.event.getMessageChannel();
            String msg = "\n" + state.opponent.getRepresentation(true, true, true, true) + " suffered "
                    + StringHelper.pluralize(hits, "hit") + " from SPACE CANNON against your ships.";
            MessageHelper.sendMessageToChannel(channel, msg);
            List<Button> buttons = new ArrayList<>();
            String factionChecker = "FFCC_" + state.opponent.getFaction() + "_";
            if (state.opponent.isDummy() || state.opponent.isNpc()) {
                buttons.add(Buttons.green(
                        state.opponent.dummyPlayerSpoof() + "autoAssignSpaceCannonOffenceHits_"
                                + state.tile.getPosition() + "_" + hits,
                        "Auto-assign Hit" + (hits == 1 ? "" : "s For Dummy")));
            } else {
                buttons.add(Buttons.green(
                        factionChecker + "autoAssignSpaceCannonOffenceHits_" + state.tile.getPosition() + "_" + hits,
                        "Auto-assign Hit" + (hits == 1 ? "" : "s")));
            }
            buttons.add(Buttons.red(
                    "getDamageButtons_" + state.tile.getPosition() + "deleteThis_pds",
                    "Manually Assign Hit" + (hits == 1 ? "" : "s")));
            buttons.add(Buttons.gray(
                    factionChecker + "cancelPdsOffenseHits_" + state.tile.getPosition() + "_" + hits, "Cancel a Hit"));
            String msg2 = state.opponent.getRepresentationNoPing() + ", you may automatically assign "
                    + (hits == 1 ? "the hit" : "hits") + "."
                    + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(
                            state.opponent, state.game, state.tile, hits, state.event, true, true);
            MessageHelper.sendMessageToChannelWithButtons(channel, msg2, buttons);
        }
    }

    private static void handlePublicCombatRoundResults(CombatRollPipelineState state, int hits) {
        if (state.rollType != CombatRollType.combatround || state.opponent == state.player) return;
        if (state.combatOnHolder instanceof Planet) {
            handleGroundCombatRoundResults(state, hits);
        } else {
            handleSpaceCombatRoundResults(state, hits);
        }
    }

    private static void handleGroundCombatRoundResults(CombatRollPipelineState state, int hits) {
        reportGroundCombatHits(state, hits);
        if (state.automated) {
            reportAutomatedValkyrieParticleWeaveHit(state, hits);
            return;
        }
        if (hits > 0) {
            offerGroundCombatHitAssignment(state, hits);
            offerValkyrieParticleWeaveHitAssignment(state, hits);
        } else {
            offerNextGroundCombatRound(state);
        }
    }

    private static void reportGroundCombatHits(CombatRollPipelineState state, int hits) {
        String message = "\n" + state.opponent.getRepresentation(true, true, true, true) + ", you suffered "
                + StringHelper.pluralize(hits, "hit") + " in round #" + state.playerRound + ".";
        MessageHelper.sendMessageToChannel(state.event.getMessageChannel(), message);
    }

    private static void offerGroundCombatHitAssignment(CombatRollPipelineState state, int hits) {
        List<Button> buttons = new ArrayList<>();
        if (state.playerRound > state.opponentRound) {
            String prefix = state.opponent.isDummy() || state.opponent.isNpc() ? state.opponent.dummyPlayerSpoof() : "";
            buttons.add(Buttons.blue(
                    prefix + "combatRoll_" + state.tile.getPosition() + "_" + state.combatOnHolder.getName(),
                    "Roll Dice " + (prefix.isEmpty() ? "" : "For Dummy ") + "for Combat Round #"
                            + (state.opponentRound + 1)));
        }
        if (state.opponent.isDummy() || state.opponent.isNpc()) {
            buttons.add(Buttons.green(
                    state.opponent.dummyPlayerSpoof() + "autoAssignGroundHits_" + state.combatOnHolder.getName() + "_"
                            + hits,
                    "Auto-assign Hit" + (hits == 1 ? "" : "s") + " For Dummy"));
        } else {
            buttons.add(Buttons.green(
                    state.opponent.factionButtonChecker() + "autoAssignGroundHits_" + state.combatOnHolder.getName()
                            + "_" + hits,
                    "Auto-assign Hit" + (hits == 1 ? "" : "s")));
            buttons.add(Buttons.red(
                    "getDamageButtons_" + state.tile.getPosition() + "deleteThis_groundcombat",
                    "Manually Assign Hit" + (hits == 1 ? "" : "s")));
            buttons.add(Buttons.gray(
                    state.opponent.factionButtonChecker() + "cancelGroundHits_" + state.tile.getPosition() + "_" + hits,
                    "Cancel a Hit"));
            AshenPromissoryHandler.addFromTheAshesButton(
                    buttons, state.game, state.opponent, state.player, state.tile, state.combatOnHolder, hits);
            if (state.opponent.hasUnit("crystellum_mech")) {
                CrystellumUnitHandler.offerRefractumButtonIfRelevant(
                        buttons, state.opponent, state.game, state.tile, state.combatOnHolder, hits);
            }
        }
        String message = state.opponent.getRepresentationUnfogged() + " you may autoassign "
                + StringHelper.pluralize(hits, "hit") + ".";
        MessageHelper.sendMessageToChannelWithButtons(state.event.getMessageChannel(), message, buttons);
    }

    private static void offerValkyrieParticleWeaveHitAssignment(CombatRollPipelineState state, int hits) {
        if (!state.opponent.hasTech("vpw")) return;
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                state.player.factionButtonChecker() + "autoAssignGroundHits_" + state.combatOnHolder.getName() + "_1",
                "Auto-assign Hit"));
        buttons.add(Buttons.red(
                "getDamageButtons_" + state.tile.getPosition() + "deleteThis_groundcombat", "Manually Assign Hit"));
        buttons.add(Buttons.gray(
                state.player.factionButtonChecker() + "cancelGroundHits_" + state.tile.getPosition() + "_1",
                "Cancel a Hit"));
        String message = state.player.getRepresentationUnfogged()
                + " you got hit by _Valkyrie Particle Weave_. You may autoassign 1 hit.";
        MessageHelper.sendMessageToChannelWithButtons(state.event.getMessageChannel(), message, buttons);
    }

    private static void offerNextGroundCombatRound(CombatRollPipelineState state) {
        if (state.playerRound <= state.opponentRound) return;
        String prefix = state.opponent.isDummy() || state.opponent.isNpc() ? state.opponent.dummyPlayerSpoof() : "";
        List<Button> buttons = List.of(Buttons.blue(
                prefix + "combatRoll_" + state.tile.getPosition() + "_" + state.combatOnHolder.getName(),
                "Roll Dice " + (prefix.isEmpty() ? "" : "For Dummy ") + "for Combat Round #"
                        + (state.opponentRound + 1)));
        String message = state.opponent.getRepresentationUnfogged() + " you may roll dice for Combat Round #"
                + (state.opponentRound + 1) + ".";
        MessageHelper.sendMessageToChannelWithButtons(state.event.getMessageChannel(), message, buttons);
    }

    private static void reportAutomatedValkyrieParticleWeaveHit(CombatRollPipelineState state, int hits) {
        if (!state.opponent.hasTech("vpw") || hits < 1) return;
        MessageHelper.sendMessageToChannel(
                state.event.getMessageChannel(),
                state.player.getRepresentation() + " suffered 1 hit due to _Valkyrie Particle Weave_.");
    }

    private static void handleSpaceCombatRoundResults(CombatRollPipelineState state, int hits) {
        List<Button> buttons = buildNextSpaceCombatRoundButtons(state);
        reportSpaceCombatHits(state, hits);
        if (hits > 0) sendSpaceCombatHitAssignment(state, hits, buttons);
        else offerNextSpaceCombatRound(state, buttons);
    }

    private static List<Button> buildNextSpaceCombatRoundButtons(CombatRollPipelineState state) {
        List<Button> buttons = new ArrayList<>();
        if (state.playerRound <= state.opponentRound) return buttons;
        String idPrefix = state.opponent.isDummy() || state.opponent.isNpc() ? state.opponent.dummyPlayerSpoof() : "";
        String labelPrefix =
                state.opponent.isDummy() || state.opponent.isNpc() ? "Roll Dice For Dummy For " : "Roll Dice For ";
        buttons.add(Buttons.blue(
                idPrefix + "combatRoll_" + state.tile.getPosition() + "_" + state.combatOnHolder.getName(),
                labelPrefix + "Combat Round #" + (state.opponentRound + 1)));
        return buttons;
    }

    private static void reportSpaceCombatHits(CombatRollPipelineState state, int hits) {
        String message = "\n" + state.opponent.getRepresentation(true, true, true, true) + ", you suffered "
                + StringHelper.pluralize(hits, "hit") + " in round #" + state.playerRound + ".";
        MessageHelper.sendMessageToChannel(state.event.getMessageChannel(), message);
    }

    private static void sendSpaceCombatHitAssignment(CombatRollPipelineState state, int hits, List<Button> buttons) {
        addSpaceCombatHitAssignmentButtons(state, hits, buttons);
        String message = buildSpaceCombatHitAssignmentMessage(state, hits);
        MessageHelper.sendMessageToChannelWithButtons(state.event.getMessageChannel(), message, buttons);
    }

    private static void addSpaceCombatHitAssignmentButtons(
            CombatRollPipelineState state, int hits, List<Button> buttons) {
        if (state.opponent.isDummy() || state.opponent.isNpc()) {
            buttons.add(Buttons.green(
                    state.opponent.dummyPlayerSpoof() + "autoAssignSpaceHits_" + state.tile.getPosition() + "_" + hits,
                    "Auto-assign Hit" + (hits == 1 ? "" : "s") + " For Dummy"));
            return;
        }
        String factionChecker = "FFCC_" + state.opponent.getFaction() + "_";
        buttons.add(Buttons.green(
                factionChecker + "autoAssignSpaceHits_" + state.tile.getPosition() + "_" + hits,
                "Auto-assign Hit" + (hits == 1 ? "" : "s")));
        buttons.add(Buttons.red(
                "getDamageButtons_" + state.tile.getPosition() + "deleteThis_spacecombat",
                "Manually Assign Hit" + (hits == 1 ? "" : "s")));
        buttons.add(Buttons.gray(
                factionChecker + "cancelSpaceHits_" + state.tile.getPosition() + "_" + hits, "Cancel a Hit"));
        if (state.opponent.hasAbility("refraction")) {
            CrystellumAbilityHandler.addRefractionButtonIfRelevant(
                    buttons, state.opponent, state.game, state.tile, hits);
        }
    }

    private static String buildSpaceCombatHitAssignmentMessage(CombatRollPipelineState state, int hits) {
        String message = state.opponent.getRepresentationNoPing() + ", you may automatically assign "
                + (hits == 1 ? "the hit" : "hits") + ". "
                + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(
                        state.opponent, state.game, state.tile, hits, state.event, true);
        if (state.opponent.hasRelic("metalivoidshielding")) {
            message += "\nReminder: You have the _"
                    + Mapper.getRelic("metalivoidshielding").getName()
                    + "_ relic, you may SUSTAIN DAMAGE on one of your non-fighter ships instead of taking a hit.";
        }
        if (state.opponent.hasUnlockedBreakthrough("crystellumbt") && state.playerRound == 1) {
            message +=
                    "\nReminder: You have _Defensive Architecture_.\nFor each unit in the active system that is at capacity, you may give one other non-fighter ship in the same system SUSTAIN DAMAGE until the end of this combat. This is not tracked by the bot.";
        }
        return message;
    }

    private static void offerNextSpaceCombatRound(CombatRollPipelineState state, List<Button> buttons) {
        if (state.playerRound <= state.opponentRound) return;
        String message = state.opponent.getRepresentationUnfogged() + " you may roll dice for Combat Round #"
                + (state.opponentRound + 1) + ".";
        MessageHelper.sendMessageToChannelWithButtons(state.event.getMessageChannel(), message, buttons);
    }

    private static void handleAntiFighterBarrageResults(CombatRollPipelineState state, int hits) {
        if (state.rollType != CombatRollType.AFB || hits < 1) return;
        String message = state.opponent.getRepresentation() + ", you may automatically assign "
                + (hits == 1 ? "the hit" : "hits") + " from AFB.";
        MessageHelper.sendMessageToChannel(
                state.event.getMessageChannel(), message, buildAntiFighterBarrageAssignmentButtons(state, hits));
    }

    private static List<Button> buildAntiFighterBarrageAssignmentButtons(CombatRollPipelineState state, int hits) {
        List<Button> buttons = new ArrayList<>();
        String label = "Auto-assign Hit" + (hits == 1 ? "" : "s");
        if (state.opponent.isNpc() || state.opponent.isDummy()) {
            buttons.add(Buttons.green(
                    state.opponent.dummyPlayerSpoof() + "autoAssignAFBHits_" + state.tile.getPosition() + "_" + hits,
                    label + " For Dummy"));
            return buttons;
        }
        buttons.add(Buttons.green(
                state.opponent.factionButtonChecker() + "autoAssignAFBHits_" + state.tile.getPosition() + "_" + hits,
                label));
        buttons.add(Buttons.red(
                state.opponent.factionButtonChecker() + "getDamageButtons_" + state.tile.getPosition() + "_afb",
                "Manually Assign Hit" + (hits == 1 ? "" : "s")));
        buttons.add(Buttons.gray(
                state.opponent.factionButtonChecker() + "cancelAFBHits_" + state.tile.getPosition() + "_" + hits,
                "Cancel a Hit"));
        return buttons;
    }

    private static void relayFogOfWarCombatResult(CombatRollPipelineState state, String message) {
        if (!isFoWPrivateChannelRoll(state)) return;
        if (state.rollType == CombatRollType.SpaceCannonOffence) {
            relayPrivateSpaceCannonResult(state, message);
        } else if (state.rollType == CombatRollType.bombardment) {
            remindPlayerToRelayPrivateBombardment(state);
        }
    }

    private static void relayPrivateSpaceCannonResult(CombatRollPipelineState state, String message) {
        MessageHelper.sendMessageToChannel(
                state.opponent.getCorrectChannel(),
                state.opponent.getRepresentationUnfogged() + " "
                        + FOWCombatThreadMirroring.parseCombatRollMessage(message, state.player));
        MessageHelper.sendMessageToChannel(
                state.player.getCorrectChannel(),
                "Roll result was sent to " + state.opponent.getRepresentationNoPing());
    }

    private static void remindPlayerToRelayPrivateBombardment(CombatRollPipelineState state) {
        MessageHelper.sendMessageToChannel(
                state.player.getCorrectChannel(),
                state.player.getRepresentationUnfogged()
                        + " This roll result is not automatically relayed. Please communicate the hits to the opponent manually.");
    }

    private static void handleFogOfWarDummyCombatResult(CombatRollPipelineState state, int hits) {
        if ((state.opponent.isDummy() || state.opponent.isNpc()) && hits > 0) {
            List<Button> buttons = new ArrayList<>();
            if (state.combatOnHolder instanceof Planet) {
                if (state.playerRound > state.opponentRound) {
                    buttons.add(Buttons.blue(
                            state.opponent.dummyPlayerSpoof() + "combatRoll_" + state.tile.getPosition() + "_"
                                    + state.combatOnHolder.getName(),
                            "Roll Dice For Dummy for Combat Round #" + (state.opponentRound + 1)));
                }
                buttons.add(Buttons.green(
                        state.opponent.dummyPlayerSpoof() + "autoAssignGroundHits_" + state.combatOnHolder.getName()
                                + "_" + hits,
                        "Auto-assign Hit" + (hits == 1 ? "" : "s") + " For Dummy"));
                String msg = state.opponent.getRepresentationUnfogged() + " you may autoassign "
                        + StringHelper.pluralize(hits, "hit") + ".";
                MessageHelper.sendMessageToChannelWithButtons(state.event.getMessageChannel(), msg, buttons);
            } else {
                String msg2 = state.opponent.getRepresentationNoPing() + ", you may automatically assign "
                        + (hits == 1 ? "the hit" : "hits") + ".";
                if (state.rollType == CombatRollType.AFB) {
                    buttons.add(Buttons.green(
                            state.opponent.dummyPlayerSpoof() + "autoAssignAFBHits_" + state.tile.getPosition() + "_"
                                    + hits,
                            "Auto-assign Hit" + (hits == 1 ? "" : "s For Dummy")));
                } else {
                    buttons.add(Buttons.green(
                            state.opponent.dummyPlayerSpoof() + "autoAssignSpaceHits_" + state.tile.getPosition() + "_"
                                    + hits,
                            "Auto-assign Hits For Dummy"));
                    msg2 += ButtonHelperModifyUnits.autoAssignSpaceCombatHits(
                            state.opponent, state.game, state.tile, hits, state.event, true);
                }
                MessageHelper.sendMessageToChannelWithButtons(state.event.getMessageChannel(), msg2, buttons);
            }
        }
    }

    private static void reportSurprisingDiceRoll(
            CombatRollPipelineState state, String message, boolean trackedCandidateRoll) {
        if (!trackedCandidateRoll && !"none".equals(state.game.getStoredValue("surprisingDiceRoll"))) {
            StringBuilder disaster;
            if ("hits".equals(state.game.getStoredValue("surprisingDiceRoll"))) {
                disaster = new StringBuilder(state.player.getRepresentation() + " has rolled grievously against "
                        + state.opponent.getRepresentation() + " in " + state.game.getName() + ".");
            } else {
                disaster = new StringBuilder(state.player.getRepresentation() + " has rolled dismally against "
                        + state.opponent.getRepresentation() + " in " + state.game.getName() + ".");
            }
            for (String line : message.split("\n")) {
                if (line.startsWith("> `") || line.startsWith("**Total hits")) {
                    disaster.append('\n').append(line);
                }
            }
            DisasterWatchHelper.sendMessageInDisasterWatch(state.game, disaster.toString());
        }
    }

    private record AdjustedRollResult(String message, int hits) {}
}
