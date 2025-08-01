package ti4.buttons.handlers.agenda;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RelicHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.omega_phase.VoiceOfTheCouncilHelper;
import ti4.image.Mapper;
import ti4.image.TileGenerator;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.objectives.RevealPublicObjectiveService;
import ti4.service.strategycard.PlayStrategyCardService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
class AgendaResolveButtonHandler {

    @ButtonHandler("agendaResolution_")
    public static void resolveAgenda(Game game, String buttonID, ButtonInteractionEvent event) {
        MessageChannel actionsChannel = game.getMainGameChannel();
        String winner = buttonID.substring(buttonID.indexOf("_") + 1);
        String agendaid = game.getCurrentAgendaInfo().split("_")[2];
        if (game.getStoredValue("agendaRes" + game.getRound() + game.getDiscardAgendas().size()).equalsIgnoreCase(winner + agendaid)) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Double press suspected, stopping resolution here.");
            return;
        }
        game.setStoredValue("agendaRes" + game.getRound() + game.getDiscardAgendas().size(), winner + agendaid);
        int aID;
        if ("CL".equalsIgnoreCase(agendaid)) {
            String id2 = game.revealAgenda(false);
            Map<String, Integer> discardAgendas = game.getDiscardAgendas();
            AgendaModel agendaDetails = Mapper.getAgenda(id2);
            String agendaName = agendaDetails.getName();
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "# The hidden agenda was " + agendaName
                + "! You can find it added as a law or in the discard.");
            MessageHelper.sendMessageToChannelWithEmbed(game.getMainGameChannel(), "Hidden Agenda", agendaDetails.getRepresentationEmbed());
            aID = discardAgendas.get(id2);
        } else {
            aID = Integer.parseInt(agendaid);
        }
        Map<String, Integer> discardAgendas = game.getDiscardAgendas();
        String agID = "";
        List<Player> predictiveCheck = AgendaHelper.getLosingVoters(winner, game);
        AgendaHelper.atokeraCommanderUnlockCheck(game);
        for (Player playerWL : predictiveCheck) {
            if (game.getStoredValue("riskedPredictive").contains(playerWL.getFaction())
                && playerWL.hasTech("pi")) {
                playerWL.exhaustTech("pi");
                MessageHelper.sendMessageToChannel(playerWL.getCorrectChannel(),
                    playerWL.getRepresentation()
                        + " _Predictive Intelligence_ was exhausted since you voted for a losing outcome while using it.");
            }
        }
        game.setStoredValue("riskedPredictive", "");
        for (Map.Entry<String, Integer> agendas : discardAgendas.entrySet()) {
            if (agendas.getValue().equals(aID)) {
                agID = agendas.getKey();
                break;
            }
        }

        if (game.getCurrentAgendaInfo().startsWith("Law")) {
            if (game.getCurrentAgendaInfo().contains("Player")) {
                Player player2 = game.getPlayerFromColorOrFaction(winner);
                if (player2 != null) {
                    game.addLaw(aID, winner);
                }
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    "# Added Law with " + winner + " as the elected!");
                if ("censure".equalsIgnoreCase(agID) || "absol_censure".equalsIgnoreCase(agID)) {
                    StringBuilder message = new StringBuilder();
                    Integer poIndex = game.addCustomPO("Political Censure", 1);
                    message.append("Custom objective _Political Censure_ has been added.\n");
                    game.scorePublicObjective(player2.getUserID(), poIndex);
                    if (!game.isFowMode()) {
                        message.append(player2.getRepresentation()).append(" scored _Political Censure_.\n");
                    }
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message.toString());
                    Helper.checkEndGame(game, player2);
                }
                if (Constants.VOICE_OF_THE_COUNCIL_ID.equalsIgnoreCase(agID)) {
                    VoiceOfTheCouncilHelper.ElectVoiceOfTheCouncil(game, player2);
                }
                if ("warrant".equalsIgnoreCase(agID)) {
                    player2.flipSearchWarrant();
                    game.drawSecretObjective(player2.getUserID());
                    game.drawSecretObjective(player2.getUserID());
                    if (player2.hasAbility("plausible_deniability")) {
                        game.drawSecretObjective(player2.getUserID());
                    }
                    SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player2, event);
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        (game.isFowMode() ? "The elected player" : player2.getRepresentation()) + " has drawn 2 secret objectives, and their secret objective info is now public.");
                }
            } else {
                if ("for".equalsIgnoreCase(winner)) {
                    game.addLaw(aID, null);
                    MessageHelper.sendMessageToChannel(event.getChannel(), game.getPing() + " Added law to map!");
                } else {
                    if ("checks".equalsIgnoreCase(agID)) {
                        game.setStoredValue("agendaChecksNBalancesAgainst", "true");
                    }
                    if ("revolution".equalsIgnoreCase(agID)) {
                        game.setStoredValue("agendaRevolution", "true");
                    }
                }

                if ("regulations".equalsIgnoreCase(agID)) {
                    if ("for".equalsIgnoreCase(winner)) {
                        for (Player playerB : game.getRealPlayers()) {
                            if (playerB.getFleetCC() > 4) {
                                playerB.setFleetCC(4);
                                ButtonHelper.checkFleetInEveryTile(playerB, game);
                            }
                            if (playerB.hasAbility("imperia")) {
                                if (playerB.getFleetCC() + playerB.getMahactCC().size() > 4) {
                                    int min = Math.max(0, 4 - playerB.getMahactCC().size());
                                    playerB.setFleetCC(min);
                                    ButtonHelper.checkFleetInEveryTile(playerB, game);
                                }
                            }
                        }

                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            game.getPing() + ", all player that had more than 4 command tokens in their fleet pools have had the excess removed.");
                    } else {
                        for (Player playerB : game.getRealPlayers()) {
                            playerB.setFleetCC(playerB.getFleetCC() + 1);
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            game.getPing() + ", all players have had 1 command token added to their respective fleet pools.");

                    }
                }
                if ("absol_checks".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        AgendaHelper.resolveAbsolAgainstChecksNBalances(game);
                    }
                }
                if ("schematics".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        for (Player player : game.getRealPlayers()) {
                            if (player.getTechs().contains("ws") || player.getTechs().contains("pws2")
                                || player.getTechs().contains("dsrohdws")) {
                                ActionCardHelper.discardRandomAC(event, game, player, player.getAc());
                            }
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            "Discarded the action cards of those that own the war sun technology.");
                    }
                }
                if ("defense_act".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        for (Player player : game.getRealPlayers()) {
                            if (!ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Pds).isEmpty()) {
                                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                                    player.getRepresentation() + " remove 1 PDS", ButtonHelperModifyUnits
                                        .getRemoveThisTypeOfUnitButton(player, game, "pds"));
                            }
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            "Sent buttons for each player to remove 1 PDS.");
                    }
                }
                if ("wormhole_recon".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        for (Tile tile : ButtonHelper.getAllWormholeTiles(game)) {
                            for (Player player : game.getRealPlayers()) {
                                if (!FoWHelper.playerHasShipsInSystem(player, tile))
                                    continue;
                                CommandCounterHelper.addCC(event, player, tile);
                            }
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Each system with a wormhole and a player's ships have had 1 of that player's command tokens placed in that system.");
                    }
                }
                if ("travel_ban".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        Set<String> wormholesAndAdj = new HashSet<>();
                        for (Tile tile : ButtonHelper.getAllWormholeTiles(game)) {
                            wormholesAndAdj.add(tile.getPosition());
                            wormholesAndAdj.addAll(FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), null, false));
                        }
                        for (String pos : wormholesAndAdj) {
                            Tile t = game.getTileByPosition(pos);
                            for (Player p : game.getRealPlayersNNeutral()) {
                                UnitKey pds = Units.getUnitKey(UnitType.Pds, p.getColorID());
                                for (UnitHolder uh : t.getUnitHolders().values()) {
                                    int num = uh.getUnitCount(pds);
                                    if (num > 0) DestroyUnitService.destroyUnit(event, t, game, pds, num, uh, false);
                                }
                            }
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Removed all PDS in or adjacent to a wormhole");
                    }
                }
                if ("shared_research".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        for (Player player : game.getRealPlayers()) {
                            Tile tile = player.getHomeSystemTile();
                            if (tile != null) {
                                CommandCounterHelper.addCC(event, player, tile);
                            }
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            "A command token from each player has been placed in their home system.");
                    }
                }
                if ("conventions".equalsIgnoreCase(agID)) {
                    List<Player> winOrLose;
                    if (!"for".equalsIgnoreCase(winner)) {
                        winOrLose = AgendaHelper.getWinningVoters(winner, game);
                        for (Player playerWL : winOrLose) {
                            ActionCardHelper.discardRandomAC(event, game, playerWL, playerWL.getAc());
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            "Discarded the action cards of those who voted \"Against\".");
                    }
                }
                if ("rep_govt".equalsIgnoreCase(agID)) {
                    List<Player> winOrLose;
                    if (!"for".equalsIgnoreCase(winner)) {
                        winOrLose = AgendaHelper.getWinningVoters(winner, game);
                        for (Player playerWL : winOrLose) {
                            game.setStoredValue("agendaRepGov",
                                game.getStoredValue("agendaRepGov") + playerWL.getFaction());
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            "Will exhaust cultural planets of all players who voted \"Against\" at start of next Strategy Phase.");
                    }
                }
                if ("articles_war".equalsIgnoreCase(agID)) {
                    List<Player> winOrLose;
                    if (!"for".equalsIgnoreCase(winner)) {
                        winOrLose = AgendaHelper.getLosingVoters(winner, game);
                        for (Player playerWL : winOrLose) {
                            playerWL.setTg(playerWL.getTg() + 3);
                            ButtonHelperAbilities.pillageCheck(playerWL, game);
                            ButtonHelperAgents.resolveArtunoCheck(playerWL, 3);
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            "Given 3 trade goods to those who voted \"For\".");
                    }
                }
                if ("nexus".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        Tile tile = game.getMecatolTile();
                        if (tile != null) {
                            String tokenFilename = Mapper.getTokenID("gamma");
                            tile.addToken(tokenFilename, Constants.SPACE);
                            MessageHelper.sendMessageToChannel(actionsChannel, "Added a gamma wormhole to the Mecatol Rex system.");
                        }
                    }
                }
                if ("sanctions".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        for (Player playerWL : game.getRealPlayers()) {
                            ActionCardHelper.discardRandomAC(event, game, playerWL, 1);
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            "Discarded 1 random action card from each player's hand.");
                    } else {
                        for (Player playerWL : game.getRealPlayers()) {
                            ButtonHelper.checkACLimit(game, playerWL);
                        }
                    }
                }
                if (game.getCurrentAgendaInfo().contains("Secret")) {
                    game.addLaw(aID, Mapper.getSecretObjectivesJustNames().get(winner));
                    Player playerWithSO = null;
                    int soID = 0;
                    for (Map.Entry<String, Player> playerEntry : game.getPlayers().entrySet()) {
                        Player player_ = playerEntry.getValue();
                        Map<String, Integer> secretsScored = new LinkedHashMap<>(
                            player_.getSecretsScored());
                        for (Map.Entry<String, Integer> soEntry : secretsScored.entrySet()) {
                            if (soEntry.getKey().equals(winner)) {
                                playerWithSO = player_;
                                soID = soEntry.getValue();
                                break;
                            }
                        }
                    }

                    if (playerWithSO == null) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found");
                        return;
                    }
                    if (winner.isEmpty()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), "Can make only scored secret objective to public objective.");
                        return;
                    }
                    game.addToSoToPoList(winner);
                    playerWithSO.removeSecretScored(soID);
                    Integer poIndex = game.addCustomPO(Mapper.getSecretObjectivesJustNames().get(winner), 1);
                    game.scorePublicObjective(playerWithSO.getUserID(), poIndex);

                    String sb = "_" + Mapper.getSecretObjectivesJustNames().get(winner) + "_ has been made in to a public objective (" + poIndex + ").";
                    if (!game.isFowMode()) {
                        sb += "\n-# " + playerWithSO.getRepresentationUnfogged() + " has been marked as having scored this, and it no longer counts towards their secret objective limit.";
                    }
                    MessageHelper.sendMessageToChannel(event.getChannel(), sb);

                    SecretObjectiveInfoService.sendSecretObjectiveInfo(game, playerWithSO, event);

                }
            }
            if (!game.getLaws().isEmpty()) {
                CommanderUnlockCheckService.checkAllPlayersInGame(game, "edyn");
            }
        } else {
            if (game.getCurrentAgendaInfo().contains("Player")) {
                Player player2 = game.getPlayerFromColorOrFaction(winner);
                if ("secret".equalsIgnoreCase(agID)) {
                    String message;
                    if (game.isFowMode()) {
                        message = "The elected player drew a secret objective.";
                    } else {
                        message = player2.getRepresentation() + " drew a secret objective as the elected player.";
                    }
                    game.drawSecretObjective(player2.getUserID());
                    if (player2.hasAbility("plausible_deniability")) {
                        game.drawSecretObjective(player2.getUserID());
                        message += " They drew a second secret objective due to **Plausible Deniability**.";
                    }
                    SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player2, event);
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
                }
                if ("standardization".equalsIgnoreCase(agID)) {
                    player2.setTacticalCC(3);
                    player2.setStrategicCC(2);
                    int amount = Math.clamp(3 - player2.getMahactCC().size(), 0, 3);
                    player2.setFleetCC(amount);
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Set " + player2.getFactionEmojiOrColor() + " command sheet to 3/" + player2.getFleetCC() + "/2.");
                    ButtonHelper.checkFleetInEveryTile(player2, game);
                }
                if ("minister_antiquities".equalsIgnoreCase(agID)) {
                    RelicHelper.drawRelicAndNotify(player2, event, game);
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Drew relic for " + player2.getFactionEmojiOrColor());
                }
                if ("execution".equalsIgnoreCase(agID)) {
                    String message = "Discarded the elected player's action cards and marked them as unable to vote on the next agenda.";
                    ActionCardHelper.discardRandomAC(event, game, player2, player2.getAc());
                    game.setStoredValue("PublicExecution", player2.getFaction());
                    if (game.getSpeakerUserID().equalsIgnoreCase(player2.getUserID())) {
                        boolean foundSpeaker = false;
                        for (Player p4 : game.getRealPlayers()) {
                            if (foundSpeaker) {
                                game.setSpeakerUserID(p4.getUserID());
                                message += " Also passed the Speaker token to " + p4.getRepresentation() + ".";
                                break;
                            }
                            if (p4 == player2) {
                                foundSpeaker = true;
                            }
                        }
                    }
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
                }
                if ("grant_reallocation".equalsIgnoreCase(agID)) {
                    MessageHelper.sendMessageToChannelWithButtons(player2.getCorrectChannel(),
                        player2.getRepresentation()
                            + " Use the button to get a technology. You will need to remove any command tokens from your fleet pool manually.",
                        List.of(Buttons.GET_A_TECH));
                }

            } // "abolishment" || "absol_abolishment", "miscount" || "absol_miscount"
            if ("abolishment".equalsIgnoreCase(agID) || "absol_abolishment".equalsIgnoreCase(agID)) {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                    "# Abolished the " + Mapper.getAgendaTitleNoCap(winner) + " law");
                game.removeLaw(winner);
            }
            if ("redistribution".equalsIgnoreCase(agID)) {
                for (Player player : game.getRealPlayers()) {
                    if (player.getPlanets().contains(winner.toLowerCase())) {
                        Planet uH = ButtonHelper.getUnitHolderFromPlanetName(winner, game);
                        Tile tile = game.getTileFromPlanet(winner);
                        if (tile != null) {
                            DestroyUnitService.destroyAllUnits(event, tile, game, uH, false);
                        }

                        boolean containsDMZ = uH.getTokenList().stream().anyMatch(token -> token.contains("dmz"));
                        if (containsDMZ) {
                            String dmzString = "Because " + Helper.getPlanetRepresentation(winner, game) + " is the _Demilitarized Zone_,";
                            dmzString += " there is no point in choosing a player to place an infantry.";
                            MessageHelper.sendMessageToChannel(actionsChannel, dmzString);
                            continue;
                        }
                        if (AgendaHelper.getPlayersWithLeastPoints(game).size() == 1) {
                            Player p2 = AgendaHelper.getPlayersWithLeastPoints(game).getFirst();
                            if (tile != null) {
                                AddUnitService.addUnits(event, tile, game, p2.getColor(), "1 inf " + winner);
                            }
                            String resolveStr = "1 " + p2.getColor() + " infantry was added to " + Helper.getPlanetRepresentation(winner, game) + " automatically.";
                            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), resolveStr);
                            continue;
                        }
                        List<Button> buttons = new ArrayList<>();
                        for (Player player2 : AgendaHelper.getPlayersWithLeastPoints(game)) {
                            if (game.isFowMode()) {
                                buttons.add(Buttons.green("colonialRedTarget_" + player2.getFaction() + "_" + winner, player2.getColor()));
                            } else {
                                buttons.add(Buttons.green("colonialRedTarget_" + player2.getFaction() + "_" + winner, player2.getFaction()));
                            }
                        }

                        String msg = player.getRepresentationUnfogged() + ", please choose who you wish to place an infantry on";
                        msg += ", and thus gain control of " + Helper.getPlanetRepresentation(winner, game) + ".";
                        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
                        if (game.isFowMode()) {
                            MessageHelper.sendMessageToChannel(actionsChannel, "Removed all units and gave player who owns the planet the option of who to give it to.");
                        }
                    }
                }

            }
            if ("disarmamament".equalsIgnoreCase(agID)) {
                for (Player player : game.getRealPlayers()) {
                    if (player.getPlanets().contains(winner.toLowerCase())) {
                        Tile tile = game.getTileFromPlanet(winner);
                        Planet uH = ButtonHelper.getUnitHolderFromPlanetName(winner, game);
                        int count = 0;
                        for (UnitKey uk : uH.getUnitsByStateForPlayer(player).keySet()) {
                            if (player.getUnitFromUnitKey(uk).getIsGroundForce()) {
                                int amt = uH.getUnitCount(uk);
                                count += amt;
                                DestroyUnitService.destroyUnit(event, tile, game, uk, amt, uH, false);
                            }
                        }
                        if (count > 0) {
                            player.setTg(player.getTg() + count);
                            ButtonHelperAgents.resolveArtunoCheck(player, count);
                            ButtonHelperAbilities.pillageCheck(player, game);
                        }
                        MessageHelper.sendMessageToChannel(actionsChannel, "Removed all ground forces and gave the player the appropriate amount of trade goods.");
                    }
                }
            }
            if ("miscount".equalsIgnoreCase(agID) || "absol_miscount".equalsIgnoreCase(agID)) {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                    "# Repealed the " + Mapper.getAgendaTitleNoCap(winner)
                        + " law and will now reveal it for the purposes of revoting. It is technically still in effect");
            }
            if ("cladenstine".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    for (Player player : game.getRealPlayers()) {
                        String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";
                        Button loseTactic = Buttons.red(finsFactionCheckerPrefix + "decrease_tactic_cc",
                            "Lose 1 Tactic Token");
                        Button loseFleet = Buttons.red(finsFactionCheckerPrefix + "decrease_fleet_cc",
                            "Lose 1 Fleet Token");
                        Button loseStrat = Buttons.red(finsFactionCheckerPrefix + "decrease_strategy_cc",
                            "Lose 1 Strategy Token");
                        Button DoneGainingCC = Buttons.red(finsFactionCheckerPrefix + "deleteButtons",
                            "Done Losing Command Tokens");
                        List<Button> buttons = List.of(loseTactic, loseFleet, loseStrat, DoneGainingCC);
                        String message2 = player.getRepresentationUnfogged() + ", your current command tokens are "
                            + player.getCCRepresentation() + ". Use buttons to lose command tokens.";
                        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons);
                        game.setStoredValue("originalCCsFor" + player.getFaction(),
                            player.getCCRepresentation());
                    }
                } else {
                    for (Player player : game.getRealPlayers()) {
                        String message = player.getRepresentation() + ", you've lost a command token from your fleet pool.";
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                        player.setFleetCC(player.getFleetCC() - 1);
                        ButtonHelper.checkFleetInEveryTile(player, game);
                    }
                }
            }
            if ("arms_reduction".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    for (Player player : game.getRealPlayers()) {
                        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "cruiser", false) > 4) {
                            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                                player.getRepresentation() + " remove excess cruisers", ButtonHelperModifyUnits
                                    .getRemoveThisTypeOfUnitButton(player, game, "cruiser"));
                        }
                        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "dreadnought", false) > 2) {
                            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                                player.getRepresentation() + " remove excess dreadnoughts", ButtonHelperModifyUnits
                                    .getRemoveThisTypeOfUnitButton(player, game, "dreadnought"));
                        }
                    }
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                        "Sent buttons for each player to remove excess dreadnoughts and cruisers.");
                } else {
                    game.setStoredValue("agendaArmsReduction", "true");
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                        "# Will exhaust all planets with a technology specialty  at the start of next Strategy Phase.");

                }
            }
            if ("rearmament".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    for (Player player : game.getRealPlayers()) {
                        String message = player.getRepresentation()
                            + " Use buttons to drop 1 mech on a home system planet.";
                        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
                            Helper.getHSPlanetPlaceUnitButtons(player, game, "mech",
                                "placeOneNDone_skipbuild"));
                    }
                } else {
                    for (Player player : game.getRealPlayers()) {
                        for (Tile tile : game.getTileMap().values()) {
                            for (UnitHolder capChecker : tile.getUnitHolders().values()) {
                                int count = capChecker.getUnitCount(UnitType.Mech, player.getColor());
                                if (count > 0) {
                                    String colorID = Mapper.getColorID(player.getColor());
                                    UnitKey mechKey = Mapper.getUnitKey(AliasHandler.resolveUnit("mech"), colorID);
                                    UnitKey infKey = Mapper.getUnitKey(AliasHandler.resolveUnit("inf"), colorID);
                                    capChecker.removeUnit(mechKey, count);
                                    capChecker.addUnit(infKey, count);
                                }
                            }
                        }
                    }
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Removed all mechs.");
                }
            }
            if ("wormhole_research".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    AgendaHelper.doResearch(event, game);
                } else {
                    List<Player> players = AgendaHelper.getWinningVoters(winner, game);
                    for (Player player : players) {
                        String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";
                        Button loseTactic = Buttons.red(finsFactionCheckerPrefix + "decrease_tactic_cc", "Lose 1 Tactic Token");
                        Button loseFleet = Buttons.red(finsFactionCheckerPrefix + "decrease_fleet_cc", "Lose 1 Fleet Token");
                        Button loseStrat = Buttons.red(finsFactionCheckerPrefix + "decrease_strategy_cc", "Lose 1 Strategy Token");
                        Button done = Buttons.red(finsFactionCheckerPrefix + "deleteButtons", "Done Losing Command Tokens");
                        List<Button> buttons = List.of(loseTactic, loseFleet, loseStrat, done);
                        String message2 = player.getRepresentationUnfogged() + ", your current command tokens are "
                            + player.getCCRepresentation() + ". Use buttons to lose command tokens.";
                        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons);
                        game.setStoredValue("originalCCsFor" + player.getFaction(),
                            player.getCCRepresentation());
                    }
                }
            }
            if ("mutiny".equalsIgnoreCase(agID)) {
                List<Player> winOrLose;
                StringBuilder message = new StringBuilder();
                Integer poIndex;
                if ("for".equalsIgnoreCase(winner)) {
                    winOrLose = AgendaHelper.getWinningVoters(winner, game);
                    poIndex = game.addCustomPO("Mutiny", 1);

                } else {
                    winOrLose = AgendaHelper.getLosingVoters(winner, game);
                    poIndex = game.addCustomPO("Mutiny", -1);
                }
                message.append("Custom objective _Mutiny_ has been added.\n");
                for (Player playerWL : winOrLose) {
                    if (playerWL.getTotalVictoryPoints() < 1 && !"for".equalsIgnoreCase(winner)) {
                        continue;
                    }
                    game.scorePublicObjective(playerWL.getUserID(), poIndex);
                    if (!game.isFowMode()) {
                        message.append(playerWL.getRepresentation()).append(" scored _Mutiny_.\n");
                    }
                    Helper.checkEndGame(game, playerWL);
                    if (playerWL.getTotalVictoryPoints() >= game.getVp()) {
                        break;
                    }

                }
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message.toString());
            }
            if ("constitution".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    List<String> laws = new ArrayList<>(game.getLaws().keySet());
                    for (String law : laws) {
                        game.removeLaw(law);
                    }
                    game.setStoredValue("agendaConstitution", "true");
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                        "# Removed all laws, will exhaust all home planets at the start of next Strategy Phase.");
                }
            }
            if ("absol_constitution".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    List<String> laws = new ArrayList<>(game.getLaws().keySet());
                    for (String law : laws) {
                        game.removeLaw(law);
                    }
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                        "# Removed all laws");
                    int counter = 40;
                    boolean lawFound = false;
                    ArrayList<String> discardedAgendas = new ArrayList<>();
                    while (counter > 0 && !lawFound) {
                        counter--;
                        String id2 = game.revealAgenda(false);
                        AgendaModel agendaDetails = Mapper.getAgenda(id2);
                        if (agendaDetails.getType().equalsIgnoreCase("law")) {
                            lawFound = true;
                            game.putAgendaBackIntoDeckOnTop(id2);
                            AgendaHelper.revealAgenda(event, false, game, game.getMainGameChannel());
                            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                                "Shuffled the found agendas back in");
                            for (String id3 : discardedAgendas) {
                                game.putAgendaBackIntoDeckOnTop(id3);
                            }
                            game.shuffleAgendas();
                        } else {
                            discardedAgendas.add(id2);
                            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                                "Found the non-law agenda: " + agendaDetails.getName());
                        }
                    }

                }
            }
            if ("absol_artifact".equalsIgnoreCase(agID) || "artifact".equalsIgnoreCase(agID) || "little_omega_artifact".equalsIgnoreCase(agID)) {
                TextChannel watchParty = AgendaHelper.watchPartyChannel(game);
                String watchPartyPing = AgendaHelper.watchPartyPing(game);
                if (watchParty != null && !game.isFowMode()) {
                    Tile tile = game.getMecatolTile();
                    if (tile != null) {
                        FileUpload systemWithContext = new TileGenerator(game, event, null, 1, tile.getPosition()).createFileUpload();
                        String message = "# Ixthian Artifact has resolved! " + watchPartyPing + "\n"
                            + AgendaHelper.getSummaryOfVotes(game, true);
                        MessageHelper.sendMessageToChannel(watchParty, message);
                        MessageHelper.sendMessageWithFile(watchParty, systemWithContext,
                            "Surrounding Mecatol Rex In " + game.getName(), false);
                    }
                }
                if ("for".equalsIgnoreCase(winner)) {
                    Button ixthianButton = Buttons.green("rollIxthian", "Roll Ixthian Artifact", PlanetEmojis.Mecatol);
                    String msg = game.getPing() + "Click this button to roll for _Ixthian Artifact_! 🥁";
                    MessageHelper.sendMessageToChannelWithButton(actionsChannel, msg, ixthianButton);
                } else {
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                        "Against on _Ixthian Artifact_‽ Disgraceful.");
                    if ("absol_artifact".equalsIgnoreCase(agID)) {
                        Integer poIndex = game.addCustomPO("Ixthian Rex Point", 1);
                        StringBuilder message = new StringBuilder();
                        message.append("Custom objective _Ixthian Rex Point_ has been added.\n");
                        for (Player playerWL : game.getRealPlayers()) {
                            if (playerWL.getPlanets().contains("mr")) {
                                game.scorePublicObjective(playerWL.getUserID(), poIndex);
                                message.append(playerWL.getRepresentation()).append(" scored _Ixthian Rex Point_.\n");
                                Helper.checkEndGame(game, playerWL);
                            }
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message.toString());
                    }
                }
            }
            if ("seed_empire".equalsIgnoreCase(agID)) {
                List<Player> winOrLose;
                Integer poIndex;
                poIndex = game.addCustomPO("Seed of an Empire", 1);
                if ("for".equalsIgnoreCase(winner)) {
                    winOrLose = AgendaHelper.getPlayersWithMostPoints(game);
                } else {
                    winOrLose = AgendaHelper.getPlayersWithLeastPoints(game);

                }
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Custom objective _Seed of an Empire_ has been added.");
                for (Player playerWL : winOrLose) {
                    game.scorePublicObjective(playerWL.getUserID(), poIndex);
                    MessageHelper.sendMessageToChannel(playerWL.getCorrectChannel(), playerWL.getRepresentation() + " scored _Seed of an Empire_.");
                    Helper.checkEndGame(game, playerWL);
                    if (playerWL.getTotalVictoryPoints() >= game.getVp()) {
                        break;
                    }
                }
            }
            if ("absol_seeds".equalsIgnoreCase(agID)) {
                List<Player> winOrLose;
                Integer poIndex;
                poIndex = game.addCustomPO("Seed of an Empire", 1);
                if ("for".equalsIgnoreCase(winner)) {
                    winOrLose = AgendaHelper.getPlayersWithMostPoints(game);
                } else {
                    winOrLose = AgendaHelper.getPlayersWithLeastPoints(game);

                }
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Custom public objective _Seed of an Empire_ has been added.");
                if (winOrLose.size() == 1) {
                    Player playerWL = winOrLose.getFirst();
                    game.scorePublicObjective(playerWL.getUserID(), poIndex);
                    MessageHelper.sendMessageToChannel(playerWL.getCorrectChannel(), playerWL.getRepresentation() + " scored _Seed of an Empire_.");
                    Helper.checkEndGame(game, playerWL);
                    if ("for".equalsIgnoreCase(winner)) {
                        game.setSpeakerUserID(playerWL.getUserID());
                        MessageHelper.sendMessageToChannel(playerWL.getCorrectChannel(), playerWL.getRepresentation() + " was made speaker and so must give each other player that voted \"for\" a promissory note.");
                        for (Player p2 : AgendaHelper.getWinningVoters(winner, game)) {
                            if (p2 != playerWL) {
                                MessageHelper.sendMessageToChannelWithButtons(playerWL.getCardsInfoThread(), "You owe " + p2.getRepresentation() +
                                    "a promissory note.", ButtonHelper.getForcedPNSendButtons(game, p2, playerWL));
                            }
                        }
                    } else {
                        ActionCardHelper.drawActionCards(game, playerWL, aID, true);
                        playerWL.setFleetCC(playerWL.getFleetCC() + 1);
                        playerWL.setTacticalCC(playerWL.getTacticalCC() + 1);
                        playerWL.setStrategicCC(playerWL.getStrategicCC() + 1);
                        MessageHelper.sendMessageToChannel(playerWL.getCorrectChannel(), playerWL.getRepresentation() + " drew some action cards and has had a command token placed in each command pool.");
                    }

                }
            }
            if ("plowshares".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    for (Player playerB : game.getRealPlayers()) {
                        AgendaHelper.doSwords(playerB, event, game);
                    }
                } else {
                    for (Player playerB : game.getRealPlayers()) {
                        ActionCardHelper.doRise(playerB, event, game);
                    }
                }
            }
            if ("incentive".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    RevealPublicObjectiveService.revealS1(game, event, actionsChannel);
                } else {
                    RevealPublicObjectiveService.revealS2(game, event, actionsChannel);
                }
            }
            if ("unconventional".equalsIgnoreCase(agID)) {
                List<Player> winOrLose;
                if (!"for".equalsIgnoreCase(winner)) {
                    winOrLose = AgendaHelper.getLosingVoters(winner, game);
                    for (Player playerWL : winOrLose) {
                        ActionCardHelper.discardRandomAC(event, game, playerWL, playerWL.getAc());
                    }
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                        "Discarded the action cards of those who voted \"for\".");
                } else {
                    winOrLose = AgendaHelper.getWinningVoters(winner, game);
                    for (Player playerWL : winOrLose) {
                        if (playerWL.hasAbility("autonetic_memory")) {
                            ButtonHelperAbilities.autoneticMemoryStep1(game, playerWL, 2);
                        } else {
                            game.drawActionCard(playerWL.getUserID());
                            game.drawActionCard(playerWL.getUserID());
                            if (playerWL.hasAbility("scheming")) {
                                game.drawActionCard(playerWL.getUserID());
                                ActionCardHelper.sendActionCardInfo(game, playerWL, event);
                                MessageHelper.sendMessageToChannelWithButtons(playerWL.getCardsInfoThread(),
                                    playerWL.getRepresentationUnfogged() + " use buttons to discard",
                                    ActionCardHelper.getDiscardActionCardButtons(playerWL, false));
                            } else {
                                ActionCardHelper.sendActionCardInfo(game, playerWL, event);
                            }
                        }
                        CommanderUnlockCheckService.checkPlayer(playerWL, "yssaril");
                        ButtonHelper.checkACLimit(game, playerWL);
                    }
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                        "Drew 2 action cards for each of the players who voted \"for\".");
                }
            }
            if ("absol_measures".equalsIgnoreCase(agID)) {
                List<Player> winOrLose;
                if ("for".equalsIgnoreCase(winner)) {
                    winOrLose = AgendaHelper.getWinningVoters(winner, game);
                    for (Player playerWL : winOrLose) {
                        if (playerWL.hasAbility("autonetic_memory")) {
                            ButtonHelperAbilities.autoneticMemoryStep1(game, playerWL, 2);
                        } else {
                            game.drawActionCard(playerWL.getUserID());
                            game.drawActionCard(playerWL.getUserID());
                            if (playerWL.hasAbility("scheming")) {
                                game.drawActionCard(playerWL.getUserID());
                                ActionCardHelper.sendActionCardInfo(game, playerWL, event);
                                MessageHelper.sendMessageToChannelWithButtons(playerWL.getCardsInfoThread(),
                                    playerWL.getRepresentationUnfogged() + " use buttons to discard",
                                    ActionCardHelper.getDiscardActionCardButtons(playerWL, false));
                            } else {
                                ActionCardHelper.sendActionCardInfo(game, playerWL, event);
                            }
                        }

                        CommanderUnlockCheckService.checkPlayer(playerWL, "yssaril");
                        ButtonHelper.checkACLimit(game, playerWL);
                    }
                    for (Player p2 : AgendaHelper.getLosingVoters(winner, game)) {
                        p2.setStrategicCC(p2.getStrategicCC());
                    }
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                        "Drew 2 action cards for each of the players who voted \"for\""
                            + " and placed 1 command token in the strategy pool of each player that voted \"against\".");
                }
            }
            if ("economic_equality".equalsIgnoreCase(agID)) {
                int finalTG = "for".equalsIgnoreCase(winner) ? 5 : 0;
                int maxLoss = 12;
                List<Player> comrades = new ArrayList<>();
                for (Player playerB : game.getRealPlayers()) {
                    if (playerB.getTg() > maxLoss) {
                        maxLoss = playerB.getTg();
                        comrades = new ArrayList<>();
                        comrades.add(playerB);
                    } else if (playerB.getTg() == maxLoss) {
                        comrades.add(playerB);
                    }
                    playerB.setTg(finalTG);
                    if (finalTG > 0) {
                        ButtonHelperAgents.resolveArtunoCheck(playerB, finalTG);
                        ButtonHelperAbilities.pillageCheck(playerB, game);
                    }
                }
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), game.getPing() + ", all players' trade goods have been set to " + finalTG + ".");
                if (!comrades.isEmpty())
                {
                    for (Player playerB : comrades)
                    {
                        DisasterWatchHelper.sendMessageInDisasterWatch(game,
                            "The Galactic Council of " + game.getName() + " have generously volunteered " + playerB.getRepresentation() + " to donate "
                                + maxLoss + " trade goods to the less economically fortunate citizens of the galaxy.");
                    }
                    DisasterWatchHelper.sendMessageInDisasterWatch(game, MiscEmojis.tg(maxLoss));
                }
            }
            if ("crisis".equalsIgnoreCase(agID)) {
                if (!game.isHomebrewSCMode()) {
                    List<Button> scButtons = new ArrayList<>();
                    switch (winner) {
                        case "1" -> scButtons.add(Buttons.green("leadershipGenerateCCButtons", "Spend & Gain Command Tokens"));
                        case "2" -> scButtons.add(Buttons.green("diploRefresh2", "Ready 2 Planets"));
                        case "3" -> scButtons.add(Buttons.gray("sc_ac_draw", "Draw 2 Action Cards", CardEmojis.ActionCard));
                        case "4" -> {
                            scButtons.add(Buttons.green("construction_spacedock", "Place 1 Space Dock", UnitEmojis.spacedock));
                            scButtons.add(Buttons.green("construction_pds", "Place 1 PDS", UnitEmojis.pds));
                        }
                        case "5" -> scButtons.add(Buttons.gray("sc_refresh", "Replenish Commodities", MiscEmojis.comm));
                        case "6" -> scButtons.add(Buttons.green("warfareBuild", "Build At Home"));
                        case "7" -> {
                            scButtons.add(Buttons.GET_A_TECH);
                            if (Helper.getPlayerFromAbility(game, "propagation") != null) {
                                scButtons.add(Buttons.green("leadershipGenerateCCButtons", "Gain 3 Command Tokens (for Nekro)"));
                            }
                        }
                        case "8" -> {
                            PlayStrategyCardService.handleSOQueueing(game, false);
                            scButtons.add(Buttons.gray("sc_draw_so", "Draw Secret Objective", CardEmojis.SecretObjective));
                        }
                    }
                    scButtons.add(Buttons.blue("sc_no_follow_" + winner, "Not Following"));
                    MessageHelper.sendMessageToChannelWithButtons(actionsChannel,
                        "You may use these button to resolve the secondary ability of **" + Helper.getSCName(Integer.parseInt(winner), game) + "**.", scButtons);
                }
            }
        }
        List<Player> riders = AgendaHelper.getWinningRiders(winner, game, event);
        List<Player> voters = AgendaHelper.getWinningVoters(winner, game);
        for (Player voter : voters) {
            if (voter.hasTech("dskyrog")) {
                MessageHelper.sendMessageToChannel(voter.getCorrectChannel(), voter.getFactionEmoji() + " gets to drop 2 infantry on a planet due to _Indoctrination Team_.");
                List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(voter, game, "2gf", "placeOneNDone_skipbuild"));
                MessageHelper.sendMessageToChannelWithButtons(voter.getCorrectChannel(), "Please use buttons to drop 2 infantry on a planet.", buttons);
            }
        }
        voters.addAll(riders);
        for (Player player : voters) {
            CommanderUnlockCheckService.checkPlayer(player, "florzen");
        }
        String ridSum = "People had Riders to resolve.";
        Player machinations = null;
        for (Player rid : riders) {
            String rep = rid.getRepresentationUnfogged();
            String message;
            if (rid.hasAbility("future_sight")) {
                message = rep
                    + " you have a Rider to resolve or you voted for the correct outcome. Either way a trade good has been added to your total due to your **Future Sight** ability "
                    + rid.gainTG(1, true) + ".";
                ButtonHelperAgents.resolveArtunoCheck(rid, 1);
                for (Player player2 : game.getRealPlayers()) {
                    if (player2.getPromissoryNotesInPlayArea().contains("sigma_machinations")) {
                        machinations = player2;
                    }
                }
            } else {
                message = rep + " you have a Rider to resolve.";

            }
            if (rid.hasTech("dsatokcr") && ButtonHelper.getNumberOfUnitsOnTheBoard(game, rid, "cruiser", true) < 8) {
                MessageHelper.sendMessageToChannel(rid.getCorrectChannel(), rid.getFactionEmoji() + " may DEPLOY 1 cruiser to a system that contains their ships.");
                List<Button> buttons = new ArrayList<>(Helper.getTileWithShipsPlaceUnitButtons(rid, game, "cruiser", "placeOneNDone_skipbuild"));
                MessageHelper.sendMessageToChannelWithButtons(rid.getCorrectChannel(), "Use buttons to DEPLOY 1 cruiser to a system that contains your ships.", buttons);
            }
            if (game.isFowMode()) {
                MessageHelper.sendPrivateMessageToPlayer(rid, game, message);
                if (machinations != null) {
                    MessageHelper.sendPrivateMessageToPlayer(machinations, game, machinations.getRepresentationUnfogged()
                        + ", you've gained a trade good from _Machinations_ " + machinations.gainTG(1, true) + ".");
                }
            } else {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
                if (machinations != null) {
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), machinations.getRepresentationUnfogged()
                        + ", you've also gained a trade good from _Machinations_ " + machinations.gainTG(1, true) + ".");
                }
            }
        }

        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Sent pings to all those who Rider'd.");
        } else if (!riders.isEmpty()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), ridSum);
        }
        String resMes = "Resolving vote for \"" + StringUtils.capitalize(winner) + "\".";
        if (game.getCurrentAgendaInfo().contains("Elect Strategy Card")) {
            resMes = "Resolving vote for \"**" + Helper.getSCName(Integer.parseInt(winner), game) + "**\".";
        }
        String voteMessage = "Click the buttons for next steps after you're done resolving Riders.";
        String agendaCount = game.getStoredValue("agendaCount");
        int aCount;
        if (agendaCount.isEmpty()) {
            aCount = 1;
        } else {
            aCount = Integer.parseInt(agendaCount) + 1;
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue("flip_agenda", "Flip Agenda #" + aCount));

        if (!game.isOmegaPhaseMode()) {
            buttons.add(Buttons.green("proceed_to_strategy", "Proceed to Strategy Phase (will run agenda cleanup and ping speaker)"));
        } else {
            Button proceedToScoring;
            Button electVoiceOfTheCouncil;
            String currentVotC = game.getLawsInfo().get(Constants.VOICE_OF_THE_COUNCIL_ID);
            boolean mustElectVoice = currentVotC == null || currentVotC.isEmpty();
            String electVoiceText = "Elect Voice of the Council";
            if (mustElectVoice) {
                electVoiceText += " (required before scoring)";
            } else {
                electVoiceText += " (optional)";
            }
            if (aCount < 3) {
                electVoiceOfTheCouncil = Buttons.gray("elect_voice_of_the_council", electVoiceText);
                proceedToScoring = Buttons.gray("proceed_to_scoring", "Proceed to scoring objectives");
            } else {
                electVoiceOfTheCouncil = Buttons.green("elect_voice_of_the_council", electVoiceText);
                if (mustElectVoice) {
                    proceedToScoring = Buttons.gray("proceed_to_scoring", "Proceed to Scoring Objectives");
                } else {
                    String speakerUserID = game.getSpeakerUserID();
                    Player speaker = game.getPlayer(speakerUserID);
                    if (speaker != null) {
                        MessageChannel speakerCardsInfoThread = speaker.getCardsInfoThread();
                        MessageHelper.sendMessageToChannel(speakerCardsInfoThread, "These are the current votes available for the _Voice of the Council_ vote.");
                        AgendaHelper.listVoteCount(game, speakerCardsInfoThread);
                    }
                    proceedToScoring = Buttons.green("proceed_to_scoring", "Proceed to Scoring Objectives");
                }
            }
            if (aCount == 3) {
                String previousElectee = game.getLawsInfo().get(Constants.VOICE_OF_THE_COUNCIL_ID);
                voteMessage += " The bot believes this is the third agenda, which in Omega Phase means you"
                    + (previousElectee == null ? "'ll" : " might") + " vote on the _Voice of the Council_.";
                if (previousElectee == null) {
                    voteMessage += " Since no player currently has the _Voice of the Council_, it must be voted on once before proceeding to scoring.";
                } else {
                    Player previousPlayer = game.getPlayerFromColorOrFaction(previousElectee);
                    voteMessage += " Since somebody (specifically, " + previousPlayer.getRepresentationNoPing()
                        + ") currently has the _Voice of the Council_, the Speaker chooses whether to vote on it this round or not.";
                }
                voteMessage += " If this is not actually the third agenda yet, please remember this when that agenda is reached.";
            }
            buttons.add(electVoiceOfTheCouncil);
            buttons.add(proceedToScoring);
        }

        if (!"miscount".equalsIgnoreCase(agID) && !"absol_miscount".equalsIgnoreCase(agID))

        {
            MessageHelper.sendMessageToChannel(event.getChannel(), resMes);
            if (!game.getPhaseOfGame().equalsIgnoreCase("action")) {
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, buttons);
            }
        } else {
            game.removeLaw(winner);
            game.putAgendaBackIntoDeckOnTop(winner);
            AgendaHelper.revealAgenda(event, false, game, game.getMainGameChannel());
        }

        ButtonHelper.deleteMessage(event);
    }
}
