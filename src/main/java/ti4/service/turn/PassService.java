package ti4.service.turn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.migo.MigoAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.SarcosaHeroHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.UydaiUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.verydith.VerydithAbilitiesHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.tyris.TyrisBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.planet.NiflheimLegendaryButtonHandler;
import ti4.discord.interactions.buttons.handlers.unit.monuments.MonumentsButtonHandler;
import ti4.discord.interactions.buttons.handlers.unit.monuments.MonumentsDSButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.DiscordantStarsHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.StatusHelper;
import ti4.helpers.Units.UnitType;
import ti4.helpers.omega_phase.PriorityTrackHelper;
import ti4.helpers.thirtyk.ThirtyKSecretHooks;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.game.MonumentsService;
import ti4.service.leader.agent.modules.homebrew.theodisi.RevenantArcanumAgent;
import ti4.service.unit.AddUnitService;
import ti4.spring.service.gameevent.GameEventService;
import ti4.spring.service.gameevent.GameEventType;
import ti4.spring.service.gameevent.TurnActionLog;

@UtilityClass
public class PassService {

    public static void passPlayerForRound(
            GenericInteractionCreateEvent event, Game game, Player player, boolean autoPass) {
        if (game.isFowMode() && !player.isActivePlayer() && !autoPass) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "You are not the active player. If you need to, you can force pass with `/player stats passed:y`.");
            return;
        }
        UydaiUnitHandler.offerDeathCommandosPlacement(event, game, player);

        player.setPassed(true);
        MigoAbilityHandler.offerCerebralArchiveResearch(game, player);
        ThirtyKSecretHooks.onPassed(game, player);
        GameEventService.commit(game, GameEventType.TURN, player, TurnActionLog.turnPayload(game, true));
        ButtonHelperFactionSpecific.offerDeathBinding(game, player);
        SarcosaHeroHandler.offerPassAbility(game, player);
        NiflheimLegendaryButtonHandler.offerHvergelmirsHaze(event, game, player);
        VerydithAbilitiesHandler.getMandateButtons(event, player, game);
        RevenantArcanumAgent.offerAfterPass(game, player);
        if (game.playerHasLeaderUnlockedOrAlliance(player, "olradincommander")) {
            ButtonHelperCommanders.olradinCommanderStep1(player, game);
        }
        ButtonHelperActionCards.checkForPlayingBountyContracts(game, player);
        ButtonHelperActionCards.checkForPlayingStrategicFocus(game, player);
        if (player.getPlanets().contains("petra")
                && !player.getExhaustedPlanetsAbilities().contains("petra")) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "planetAbilityExhaust_petra",
                    "Use " + Mapper.getPlanet("petra").getLegendaryAbilityName()));
            buttons.add(Buttons.DECLINE_DELETE_BUTTONS);

            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", you may exhaust _Petra_ to explore 1 planet you control.",
                    buttons);
        }
        game.setStoredValue(
                "currentActionSummary" + player.getFaction(),
                game.getStoredValue("currentActionSummary" + player.getFaction()) + " Passed.");

        String text = player.getRepresentation(true, false) + " has passed" + (autoPass ? " (preset)." : ".");

        if (autoPass
                && player.getPlanets().contains("ordinian")
                && !player.getExhaustedPlanetsAbilities().contains("ordinian")) {
            text += "\n# They did not use the Ordinian ability. If this was a mistake, let them do so now.";
        }

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), text);
        if (player.hasTech("absol_aida")) {
            String msg = player.getRepresentation()
                    + " since you have _AI Development Algorithm_, you may research 1 unit upgrade now for 6 influence.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            if (!player.hasAbility("propagation")) {
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                                + " you may use the button to get your unit upgrade technology.",
                        Collections.singletonList(Buttons.GET_A_UNIT_TECH_WITH_INF));
            } else {
                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                String message2 = player.getRepresentation()
                        + ", you would research a unit upgrade technology, but because of **Propagation**, you instead gain 3 command tokens."
                        + " Your current command tokens are " + player.getCCRepresentation()
                        + ". Use buttons to gain command tokens.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons);
                game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
            }
        }
        if (player.hasAbility("deliberate_action")
                && (player.getTacticalCC() == 0 || player.getStrategicCC() == 0 || player.getFleetCC() == 0)) {
            String msg = player.getRepresentation() + ", since you have the **Deliberate Action** ability,"
                    + " and passed while one of your command pools contained no tokens, you may gain 1 command token to that pool.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            String message2 = player.getRepresentationUnfogged() + "! Your current command tokens are "
                    + player.getCCRepresentation() + ". Use buttons to gain a command token.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons);
        }
        if (player.hasTech("lgf_br")) {
            String mecatol = game.mecatols().stream()
                    .filter(planet -> "mr".equals(planet) || "mrte".equals(planet))
                    .findFirst()
                    .orElse(null);
            UnitHolder planetHolder = mecatol == null ? null : game.getUnitHolderFromPlanet(mecatol);
            boolean canCoexist = planetHolder != null
                    && game.getRealPlayers().stream()
                            .filter(otherPlayer -> !otherPlayer.equals(player))
                            .anyMatch(otherPlayer -> FoWHelper.playerHasUnitsOnPlanet(otherPlayer, planetHolder));
            if (canCoexist && mecatol != null) {
                String priorCoexistenceFlag = game.getStoredValue("coexistFlag");
                game.setStoredValue("coexistFlag", "yes");
                try {
                    int infantryBefore = planetHolder.getUnitCount(UnitType.Infantry, player.getColor());
                    AddUnitService.addUnits(
                            event, game.getTileFromPlanet(mecatol), game, player.getColor(), "2 infantry " + mecatol);
                    if (planetHolder.getUnitCount(UnitType.Infantry, player.getColor()) == infantryBefore + 2) {
                        MessageHelper.sendMessageToChannel(
                                player.getCorrectChannel(),
                                player.getRepresentation()
                                        + " placed 2 infantry into coexistence on Mecatol Rex using _Lazax Gate Folding_.");
                    } else {
                        MessageHelper.sendMessageToChannel(
                                player.getCorrectChannel(),
                                player.getRepresentation() + " could not place infantry using _Lazax Gate Folding_.");
                    }
                } finally {
                    if (priorCoexistenceFlag.isEmpty()) {
                        game.removeStoredValue("coexistFlag");
                    } else {
                        game.setStoredValue("coexistFlag", priorCoexistenceFlag);
                    }
                }
            } else {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation()
                                + " could not place infantry using _Lazax Gate Folding_ because no other player's units are on Mecatol Rex.");
            }
        }
        // if (player.hasAbility("bestow")) {
        //     String msg = player.getRepresentation() + ", since you have the _Bestow_ Honor card,"
        //             + " you can allow your neighbors to each gain 2 commodities with the below button. If you do, for
        // each one that does you will gain 1 commodity.";
        //     MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        //     List<Button> buttons = new ArrayList<>();
        //     buttons.add(Buttons.green("startBestow", "Allow Neighbors To Gain 2 Commodities"));
        //     buttons.add(Buttons.DECLINE_DELETE_BUTTONS);
        //     MessageHelper.sendMessageToChannelWithButtons(
        //             player.getCorrectChannel(), "Use buttons to resolve.", buttons);
        // }
        if (player.hasTech("dskolug")) {
            int oldComm = player.getCommodities();
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (p2.isPassed()) {
                    player.setCommodities(Math.min(player.getCommoditiesTotal(), player.getCommodities() + 1));
                }
            }
            if (player.getCommodities() > oldComm) {
                String msg = player.getRepresentation()
                        + " since you have _Applied Biothermics_, you gained 1 commodity for each passed player"
                        + " (commodities went from " + oldComm + " -> " + player.getCommodities() + ").";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
        }
        if (game.isMonumentsMode() && MonumentsService.isMonumentOnBoard(game, player, "arborec_monument")) {
            List<Button> buttons = MonumentsButtonHandler.getArborecMonumentPlacementButtons(game, player);
            if (!buttons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentation()
                                + ", choose a non-Mecatol planet in or adjacent to your Monument's system "
                                + "to place 1 infantry with **Flaah Orbitals**. If it contains another player's units, "
                                + "the infantry will enter coexistence.",
                        buttons);
            }
        }
        MonumentsDSButtonHandler.offerKolumeMonumentButton(game, player);

        if (game.hasAnyPriorityTrackMode()) {
            PriorityTrackHelper.AssignPlayerToPriority(game, player, null);
        }

        DiscordantStarsHelper.checkKjalengardMechs(event, player, game);

        if ("yes".equals(game.getStoredValue("autoProveEndurance_" + player.getFaction()))) {
            for (Map.Entry<String, Integer> so : player.getSecrets().entrySet()) {
                if ("pe".equals(so.getKey())) {
                    boolean wonGame = SecretObjectiveHelper.scoreSO(
                            event,
                            game,
                            player,
                            so.getValue(),
                            (game.isFowMode() ? player.getPrivateChannel() : game.getMainGameChannel()));
                    if (wonGame) {
                        return;
                    }

                    break;
                }
            }
        }

        Player tyris =
                game.getPlayersFromBreakthrough("tyrisbt").stream().findFirst().orElse(null);
        if (tyris != null && tyris.hasUnlockedBreakthrough("tyrisbt")) {
            TyrisBreakthroughHandler.handlePlayerPassed(game, tyris);
        }
        EndTurnService.pingNextPlayer(event, game, player, true);
        if (!autoPass) {
            StatusHelper.offerPreScoringButtons(game, player);
        }
        ButtonHelper.updateMap(
                game,
                event,
                "End of Turn (PASS) " + player.getInRoundTurnCount() + ", Round " + game.getRound() + " for "
                        + player.getFactionEmoji());
    }
}
