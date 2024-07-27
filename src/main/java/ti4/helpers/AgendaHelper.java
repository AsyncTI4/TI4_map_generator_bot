package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.Buttons;
import ti4.commands.agenda.ListVoteCount;
import ti4.commands.agenda.RevealAgenda;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.DiscardACRandom;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.explore.DrawRelic;
import ti4.commands.planet.PlanetExhaust;
import ti4.commands.special.RiseOfMessiah;
import ti4.commands.special.SwordsToPlowsharesTGGain;
import ti4.commands.special.WormholeResearchFor;
import ti4.commands.status.RevealStage1;
import ti4.commands.status.RevealStage2;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddUnits;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.AgendaModel;
import ti4.model.PlanetModel;
import ti4.model.TechnologyModel;

public class AgendaHelper {

    public static void resolveColonialRedTarget(Game game, String buttonID, ButtonInteractionEvent event) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p2 == null)
            return;
        String planet = buttonID.split("_")[2];
        Tile tile = game.getTileFromPlanet(planet);
        if (tile != null) {
            new AddUnits().unitParsing(event, p2.getColor(), tile, "1 inf " + planet, game);
        }
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
            "1 " + p2.getColor() + " infantry was added to " + planet);
        event.getMessage().delete().queue();
    }

    public static void resolveAgenda(Game game, String buttonID, ButtonInteractionEvent event,
        MessageChannel actionsChannel) {
        actionsChannel = game.getMainGameChannel();
        String winner = buttonID.substring(buttonID.indexOf("_") + 1);
        String agendaid = game.getCurrentAgendaInfo().split("_")[2];
        int aID;
        if ("CL".equalsIgnoreCase(agendaid)) {
            String id2 = game.revealAgenda(false);
            Map<String, Integer> discardAgendas = game.getDiscardAgendas();
            AgendaModel agendaDetails = Mapper.getAgenda(id2);
            String agendaName = agendaDetails.getName();
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "# The hidden agenda was " + agendaName
                + "! You can find it added as a law or in the discard.");
            aID = discardAgendas.get(id2);
        } else {
            aID = Integer.parseInt(agendaid);
        }
        Map<String, Integer> discardAgendas = game.getDiscardAgendas();
        String agID = "";
        List<Player> predictiveCheck = getLosingVoters(winner, game);
        for (Player playerWL : predictiveCheck) {
            if (game.getStoredValue("riskedPredictive").contains(playerWL.getFaction())
                && playerWL.hasTech("pi")) {
                playerWL.exhaustTech("pi");
                MessageHelper.sendMessageToChannel(playerWL.getCorrectChannel(),
                    playerWL.getRepresentation()
                        + " Predictive Intelligence was exhausted since you voted the way that lost while using it.");
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
                    message.append("Custom public objective \"Political Censure\" has been added.\n");
                    game.scorePublicObjective(player2.getUserID(), poIndex);
                    if (!game.isFowMode()) {
                        message.append(player2.getRepresentation()).append(" scored \"Political Censure\".\n");
                    }
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message.toString());
                    Helper.checkEndGame(game, player2);
                }
                if ("warrant".equalsIgnoreCase(agID)) {
                    player2.setSearchWarrant();
                    game.drawSecretObjective(player2.getUserID());
                    game.drawSecretObjective(player2.getUserID());
                    if (player2.hasAbility("plausible_deniability")) {
                        game.drawSecretObjective(player2.getUserID());
                    }
                    SOInfo.sendSecretObjectiveInfo(game, player2, event);
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "The elected player has drawn 2 secret objectives, and their secret objective cards are now public.");
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
                                ButtonHelper.checkFleetInEveryTile(playerB, game, event);
                            }
                            if (playerB.hasAbility("imperia")) {
                                if (playerB.getFleetCC() + playerB.getMahactCC().size() > 4) {
                                    int min = Math.max(0, 4 - playerB.getMahactCC().size());
                                    playerB.setFleetCC(min);
                                    ButtonHelper.checkFleetInEveryTile(playerB, game, event);
                                }
                            }
                        }

                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            game.getPing() + " All players with more than 4 command tokens in their fleet pools have had the excess removed.");
                    } else {
                        for (Player playerB : game.getRealPlayers()) {
                            playerB.setFleetCC(playerB.getFleetCC() + 1);
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            game.getPing() + " Placed 1 command token in each player's fleet pool.");

                    }
                }
                if ("absol_checks".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        resolveAbsolAgainstChecksNBalances(game);
                    }
                }
                if ("schematics".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        for (Player player : game.getRealPlayers()) {
                            if (player.getTechs().contains("ws") || player.getTechs().contains("pws2")
                                || player.getTechs().contains("dsrohdws")) {
                                new DiscardACRandom().discardRandomAC(event, game, player, player.getAc());
                            }
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            "Discarded the action cards of players with the war sun technology.");
                    }
                }
                if ("defense_act".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        for (Player player : game.getRealPlayers()) {
                            if (ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Pds)
                                .size() > 0) {
                                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
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
                                if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                                    AddCC.addCC(event, player.getColor(), tile);
                                }
                            }
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            "Placed command tokens for each player in to systems that contained their ships and a wormhole.");
                    }
                }
                if ("travel_ban".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        for (Tile tile : ButtonHelper.getAllWormholeTiles(game)) {
                            for (Player player : game.getRealPlayers()) {
                                for (String adjPos : FoWHelper.getAdjacentTilesAndNotThisTile(game,
                                    tile.getPosition(), player, false)) {
                                    Tile tile2 = game.getTileByPosition(adjPos);
                                    for (UnitHolder uH : tile2.getUnitHolders().values()) {
                                        if (uH.getUnitCount(UnitType.Pds, player.getColor()) > 0) {
                                            uH.removeUnit(
                                                Mapper.getUnitKey(AliasHandler.resolveUnit("pds"),
                                                    player.getColorID()),
                                                uH.getUnitCount(UnitType.Pds, player.getColor()));
                                        }
                                    }
                                }
                                for (UnitHolder uH : tile.getUnitHolders().values()) {
                                    if (uH.getUnitCount(UnitType.Pds, player.getColor()) > 0) {
                                        uH.removeUnit(
                                            Mapper.getUnitKey(AliasHandler.resolveUnit("pds"), player.getColorID()),
                                            uH.getUnitCount(UnitType.Pds, player.getColor()));
                                    }
                                }
                            }
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            "Removed all PDS in or adjacent to a wormhole");
                    }
                }
                if ("shared_research".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        for (Player player : game.getRealPlayers()) {
                            Tile tile = player.getHomeSystemTile();
                            if (tile != null) {
                                AddCC.addCC(event, player.getColor(), tile);
                            }
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            "Placed 1 command token for each player into their home system.");
                    }
                }
                if ("conventions".equalsIgnoreCase(agID)) {
                    List<Player> winOrLose;
                    if (!"for".equalsIgnoreCase(winner)) {
                        winOrLose = getWinningVoters(winner, game);
                        for (Player playerWL : winOrLose) {
                            new DiscardACRandom().discardRandomAC(event, game, playerWL, playerWL.getAc());
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            "Discarded the action cards of players who voted \"Against\".");
                    }
                }
                if ("rep_govt".equalsIgnoreCase(agID)) {
                    List<Player> winOrLose;
                    if (!"for".equalsIgnoreCase(winner)) {
                        winOrLose = getWinningVoters(winner, game);
                        for (Player playerWL : winOrLose) {
                            game.setStoredValue("agendaRepGov",
                                game.getStoredValue("agendaRepGov") + playerWL.getFaction());
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            "Will exhaust cultural planets of all players who voted \"Against\" at start of next strategy phase.");
                    }
                }
                if ("articles_war".equalsIgnoreCase(agID)) {
                    List<Player> winOrLose;
                    if (!"for".equalsIgnoreCase(winner)) {
                        winOrLose = getLosingVoters(winner, game);
                        for (Player playerWL : winOrLose) {
                            playerWL.setTg(playerWL.getTg() + 3);
                            ButtonHelperAbilities.pillageCheck(playerWL, game);
                            ButtonHelperAgents.resolveArtunoCheck(playerWL, game, 3);
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            "Added 3 trade goods to those who voted \"For\".");
                    }
                }
                if ("nexus".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        Tile tile = game.getMecatolTile();
                        if (tile != null) {
                            String tokenFilename = Mapper.getTokenID("gamma");
                            tile.addToken(tokenFilename, Constants.SPACE);
                            MessageHelper.sendMessageToChannel(actionsChannel, "Added gamma wormhole to the Mecatol Rex system.");
                        }
                    }
                }
                if ("sanctions".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        for (Player playerWL : game.getRealPlayers()) {
                            new DiscardACRandom().discardRandomAC(event, game, playerWL, 1);
                        }
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            "Discarded 1 random action card from each player.");
                    } else {
                        for (Player playerWL : game.getRealPlayers()) {
                            ButtonHelper.checkACLimit(game, event, playerWL);
                        }
                    }
                }
                if (game.getCurrentAgendaInfo().contains("Secret")) {
                    game.addLaw(aID, Mapper.getSecretObjectivesJustNames().get(winner));
                    Player playerWithSO = null;

                    for (Map.Entry<String, Player> playerEntry : game.getPlayers().entrySet()) {
                        Player player_ = playerEntry.getValue();
                        Map<String, Integer> secretsScored = new LinkedHashMap<>(
                            player_.getSecretsScored());
                        for (Map.Entry<String, Integer> soEntry : secretsScored.entrySet()) {
                            if (soEntry.getKey().equals(winner)) {
                                playerWithSO = player_;
                                break;
                            }
                        }
                    }

                    if (playerWithSO == null) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found.");
                        return;
                    }
                    if (winner.isEmpty()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), "Can only make scored secret objective into public objective.");
                        return;
                    }
                    game.addToSoToPoList(winner);
                    Integer poIndex = game.addCustomPO(winner, 1);
                    game.scorePublicObjective(playerWithSO.getUserID(), poIndex);

                    String sb = "**Public Objective added from Secret:**" + "\n" +
                        "(" + poIndex + ") " + "\n" +
                        Mapper.getSecretObjectivesJustNames().get(winner) + "\n";
                    MessageHelper.sendMessageToChannel(event.getChannel(), sb);

                    SOInfo.sendSecretObjectiveInfo(game, playerWithSO, event);

                }
            }
            if (game.getLaws().size() > 0) {
                for (Player player : game.getRealPlayers()) {
                    if (player.getLeaderIDs().contains("edyncommander") && !player.hasLeaderUnlocked("edyncommander")) {
                        ButtonHelper.commanderUnlockCheck(player, game, "edyn", event);
                    }
                }
            }
        } else {
            if (game.getCurrentAgendaInfo().contains("Player")) {
                Player player2 = game.getPlayerFromColorOrFaction(winner);
                if ("secret".equalsIgnoreCase(agID)) {
                    String message = "Drew Secret Objective for the elected player.";
                    game.drawSecretObjective(player2.getUserID());
                    if (player2.hasAbility("plausible_deniability")) {
                        game.drawSecretObjective(player2.getUserID());
                        message = message + " Drew a second secret objective due to Plausible Deniability.";
                    }
                    SOInfo.sendSecretObjectiveInfo(game, player2, event);
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
                }
                if ("standardization".equalsIgnoreCase(agID)) {
                    player2.setTacticalCC(3);
                    player2.setStrategicCC(2);
                    player2.setFleetCC(3);
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Set " + ButtonHelper.getIdentOrColor(player2, game) + " command tokens to 3/3/2");
                    ButtonHelper.checkFleetInEveryTile(player2, game, event);
                }
                if ("minister_antiquities".equalsIgnoreCase(agID)) {
                    DrawRelic.drawRelicAndNotify(player2, event, game);
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Drew relic for " + ButtonHelper.getIdentOrColor(player2, game));
                }
                if ("execution".equalsIgnoreCase(agID)) {
                    String message = "Discarded the elected player's action card and marked them as unable to vote on the next agenda.";
                    new DiscardACRandom().discardRandomAC(event, game, player2, player2.getAc());
                    game.setStoredValue("PublicExecution", player2.getFaction());
                    if (game.getSpeaker().equalsIgnoreCase(player2.getUserID())) {
                        message = message + " Also passed the speaker token.";
                        boolean foundSpeaker = false;
                        boolean assignedSpeaker = false;
                        for (Player p4 : game.getRealPlayers()) {
                            if (assignedSpeaker) {
                                break;
                            }
                            if (foundSpeaker) {
                                game.setSpeaker(p4.getUserID());
                                assignedSpeaker = true;
                                break;
                            }
                            if (p4 == player2) {
                                foundSpeaker = true;
                            }
                        }
                        for (Player p4 : game.getRealPlayers()) {
                            if (assignedSpeaker) {
                                break;
                            }
                            if (foundSpeaker) {
                                game.setSpeaker(p4.getUserID());
                                assignedSpeaker = true;
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
                            + " Use the button to gain a technology. You will need to removed tokens from your fleet pool manually.",
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
                        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(winner, game);
                        int count = 0;
                        Player cabalMechOwner = Helper.getPlayerFromUnit(game, "cabal_mech");
                        boolean cabalMech = cabalMechOwner != null
                            && uH.getUnitCount(UnitType.Mech, cabalMechOwner.getColor()) > 0
                            && !game.getLaws().containsKey("articles_war");
                        Player cabalFSOwner = Helper.getPlayerFromUnit(game, "cabal_flagship");
                        boolean cabalFS = cabalFSOwner != null && ButtonHelper.doesPlayerHaveFSHere("cabal_flagship",
                            cabalFSOwner, game.getTileFromPlanet(winner));

                        if (uH.getUnitCount(UnitType.Mech, player.getColor()) > 0) {
                            if (player.hasTech("sar")) {
                                for (int x = 0; x < uH.getUnitCount(UnitType.Mech, player.getColor()); x++) {
                                    player.setTg(player.getTg() + 1);
                                    MessageHelper.sendMessageToChannel(
                                        player.getCorrectChannel(),
                                        player.getRepresentation() + " you gained 1 trade good (" + (player.getTg() - 1)
                                            + "->" + player.getTg()
                                            + ") from 1 of your mechs dying while you own Self-Assembly Routines. This is not an optional gain.");
                                    ButtonHelperAbilities.pillageCheck(player, game);
                                }
                                ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                            }
                            if (cabalFS) {
                                ButtonHelperFactionSpecific.cabalEatsUnit(player, game, cabalFSOwner,
                                    uH.getUnitCount(UnitType.Mech, player.getColor()), "mech", event);
                            }
                            count = count + uH.getUnitCount(UnitType.Mech, player.getColor());
                            uH.removeUnit(Mapper.getUnitKey(AliasHandler.resolveUnit("mech"), player.getColorID()),
                                uH.getUnitCount(UnitType.Mech, player.getColor()));
                        }
                        if (uH.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
                            if ((player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2"))) {
                                ButtonHelperFactionSpecific.offerMahactInfButtons(player, game);
                            }
                            if (player.hasInf2Tech()) {
                                ButtonHelper.resolveInfantryDeath(game, player,
                                    uH.getUnitCount(UnitType.Infantry, player.getColor()));
                            }
                            if (cabalFS || cabalMech) {
                                ButtonHelperFactionSpecific.cabalEatsUnit(player, game, cabalFSOwner,
                                    uH.getUnitCount(UnitType.Infantry, player.getColor()), "infantry", event);
                            }
                            count = count + uH.getUnitCount(UnitType.Infantry, player.getColor());
                            uH.removeUnit(Mapper.getUnitKey(AliasHandler.resolveUnit("infantry"), player.getColorID()),
                                uH.getUnitCount(UnitType.Infantry, player.getColor()));
                        }
                        uH.removeAllUnits(player.getColor());
                        List<Button> buttons = new ArrayList<>();
                        for (Player player2 : getPlayersWithLeastPoints(game)) {
                            if (game.isFowMode()) {
                                buttons.add(Button.success("colonialRedTarget_" + player2.getFaction() + "_" + winner,
                                    "" + player2.getColor()));
                            } else {
                                buttons.add(Button.success("colonialRedTarget_" + player2.getFaction() + "_" + winner,
                                    "" + player2.getFaction()));
                            }
                        }
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                            player.getRepresentation(true, true) + " choose who you wish to get the planet",
                            buttons);

                        MessageHelper.sendMessageToChannel(actionsChannel,
                            "Removed all units and gave player the option of who to give the planet to");

                    }
                }

            }
            if ("disarmamament".equalsIgnoreCase(agID)) {
                for (Player player : game.getRealPlayers()) {
                    if (player.getPlanets().contains(winner.toLowerCase())) {
                        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(winner, game);
                        int count = 0;
                        Player cabalMechOwner = Helper.getPlayerFromUnit(game, "cabal_mech");
                        boolean cabalMech = cabalMechOwner != null
                            && uH.getUnitCount(UnitType.Mech, cabalMechOwner.getColor()) > 0
                            && !game.getLaws().containsKey("articles_war");
                        Player cabalFSOwner = Helper.getPlayerFromUnit(game, "cabal_flagship");
                        boolean cabalFS = cabalFSOwner != null && ButtonHelper.doesPlayerHaveFSHere("cabal_flagship",
                            cabalFSOwner, game.getTileFromPlanet(winner));

                        if (uH.getUnitCount(UnitType.Mech, player.getColor()) > 0) {
                            if (player.hasTech("sar")) {
                                for (int x = 0; x < uH.getUnitCount(UnitType.Mech, player.getColor()); x++) {
                                    player.setTg(player.getTg() + 1);
                                    MessageHelper.sendMessageToChannel(
                                        player.getCorrectChannel(),
                                        player.getRepresentation() + " you gained 1 trade good (" + (player.getTg() - 1)
                                            + "->" + player.getTg()
                                            + ") from 1 of your mechs dying while you own Self-Assembly Routines. This is not an optional gain.");
                                    ButtonHelperAbilities.pillageCheck(player, game);
                                }
                                ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                            }
                            if (cabalFS) {
                                ButtonHelperFactionSpecific.cabalEatsUnit(player, game, cabalFSOwner,
                                    uH.getUnitCount(UnitType.Mech, player.getColor()), "mech", event);
                            }
                            count = count + uH.getUnitCount(UnitType.Mech, player.getColor());
                            uH.removeUnit(Mapper.getUnitKey(AliasHandler.resolveUnit("mech"), player.getColorID()),
                                uH.getUnitCount(UnitType.Mech, player.getColor()));
                        }
                        if (uH.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
                            if ((player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2"))) {
                                ButtonHelperFactionSpecific.offerMahactInfButtons(player, game);
                            }
                            if (player.hasInf2Tech()) {
                                ButtonHelper.resolveInfantryDeath(game, player,
                                    uH.getUnitCount(UnitType.Infantry, player.getColor()));
                            }
                            if (cabalFS || cabalMech) {
                                ButtonHelperFactionSpecific.cabalEatsUnit(player, game, cabalFSOwner,
                                    uH.getUnitCount(UnitType.Infantry, player.getColor()), "infantry", event);
                            }
                            count = count + uH.getUnitCount(UnitType.Infantry, player.getColor());
                            uH.removeUnit(Mapper.getUnitKey(AliasHandler.resolveUnit("infantry"), player.getColorID()),
                                uH.getUnitCount(UnitType.Infantry, player.getColor()));
                        }
                        if (player.ownsUnit("titans_pds") || player.ownsUnit("titans_pds2")) {
                            if (uH.getUnitCount(UnitType.Pds, player.getColor()) > 0) {
                                count = count + uH.getUnitCount(UnitType.Pds, player.getColor());
                                uH.removeUnit(Mapper.getUnitKey(AliasHandler.resolveUnit("pds"), player.getColorID()),
                                    uH.getUnitCount(UnitType.Pds, player.getColor()));
                            }
                        }
                        if (count > 0) {
                            player.setTg(player.getTg() + count);
                            ButtonHelperAgents.resolveArtunoCheck(player, game, count);
                            ButtonHelperAbilities.pillageCheck(player, game);
                        }
                        MessageHelper.sendMessageToChannel(actionsChannel,
                            "Removed all units and gave player appropriate amount of trade goods.");

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
                        Button loseTactic = Button.danger(finsFactionCheckerPrefix + "decrease_tactic_cc",
                            "Lose 1 Tactic Token");
                        Button loseFleet = Button.danger(finsFactionCheckerPrefix + "decrease_fleet_cc",
                            "Lose 1 Fleet Token");
                        Button loseStrat = Button.danger(finsFactionCheckerPrefix + "decrease_strategy_cc",
                            "Lose 1 Strategy Token");
                        Button DoneGainingCC = Button.danger(finsFactionCheckerPrefix + "deleteButtons",
                            "Done Losing Command Tokens");
                        List<Button> buttons = List.of(loseTactic, loseFleet, loseStrat, DoneGainingCC);
                        String message2 = player.getRepresentation(true, true) + "! Your current command tokens are "
                            + player.getCCRepresentation() + ". Use buttons to lose command tokens.";
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message2,
                            buttons);
                        game.setStoredValue("originalCCsFor" + player.getFaction(),
                            player.getCCRepresentation());
                    }
                } else {
                    for (Player player : game.getRealPlayers()) {
                        String message = player.getRepresentation() + " you lost 1 command token from your fleet pool.";
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                        player.setFleetCC(player.getFleetCC() - 1);
                        ButtonHelper.checkFleetInEveryTile(player, game, event);
                    }
                }
            }
            if ("arms_reduction".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    for (Player player : game.getRealPlayers()) {
                        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "cruiser", false) > 4) {
                            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                                player.getRepresentation() + " remove excess cruisers", ButtonHelperModifyUnits
                                    .getRemoveThisTypeOfUnitButton(player, game, "cruiser"));
                        }
                        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "dreadnought", false) > 2) {
                            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                                player.getRepresentation() + " remove excess dreadnoughts", ButtonHelperModifyUnits
                                    .getRemoveThisTypeOfUnitButton(player, game, "dreadnought"));
                        }
                    }
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                        "Sent buttons for each player to remove excess dreadnoughts and cruisers.");
                } else {
                    game.setStoredValue("agendaArmsReduction", "true");
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                        "# Will exhaust all technology skip planets at the start of next Strategy phase.");

                }
            }
            if ("rearmament".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    for (Player player : game.getRealPlayers()) {
                        String message = player.getRepresentation()
                            + " Use buttons to drop 1 mech on a Home System Planet";
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message,
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
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Removed all mechs");
                }

            }
            if ("wormhole_research".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    WormholeResearchFor.doResearch(event, game);
                } else {
                    List<Player> players = getWinningVoters(winner, game);
                    for (Player player : players) {
                        String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";
                        Button loseTactic = Button.danger(finsFactionCheckerPrefix + "decrease_tactic_cc",
                            "Lose 1 Tactic Token");
                        Button loseFleet = Button.danger(finsFactionCheckerPrefix + "decrease_fleet_cc",
                            "Lose 1 Fleet Token");
                        Button loseStrat = Button.danger(finsFactionCheckerPrefix + "decrease_strategy_cc",
                            "Lose 1 Strategy Token");
                        Button DoneGainingCC = Button.danger(finsFactionCheckerPrefix + "deleteButtons",
                            "Done Losing Command Tokens");
                        List<Button> buttons = List.of(loseTactic, loseFleet, loseStrat, DoneGainingCC);
                        String message2 = player.getRepresentation(true, true) + "! Your current command tokens are "
                            + player.getCCRepresentation() + ". Use buttons to lose command tokens.";
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message2,
                            buttons);
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
                    winOrLose = getWinningVoters(winner, game);
                    poIndex = game.addCustomPO("Mutiny", 1);

                } else {
                    winOrLose = getLosingVoters(winner, game);
                    poIndex = game.addCustomPO("Mutiny", -1);
                }
                message.append("Custom public objective Mutiny has been added.\n");
                for (Player playerWL : winOrLose) {
                    if (playerWL.getTotalVictoryPoints() < 1 && !"for".equalsIgnoreCase(winner)) {
                        continue;
                    }
                    game.scorePublicObjective(playerWL.getUserID(), poIndex);
                    if (!game.isFowMode()) {
                        message.append(playerWL.getRepresentation()).append(" scored \"Mutiny\"\n");
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
                        "# Removed all laws, will exhaust all home planets at the start of next Strategy phase");
                }
            }
            if ("artifact".equalsIgnoreCase(agID)) {
                TextChannel watchParty = watchPartyChannel(game);
                String watchPartyPing = watchPartyPing(game);
                if (watchParty != null && !game.isFowMode()) {
                    Tile tile = game.getMecatolTile();
                    if (tile != null) {
                        FileUpload systemWithContext = GenerateTile.getInstance().saveImage(game, 1,
                            tile.getPosition(), event);
                        String message = "# Ixthian Artifact has resolved! " + watchPartyPing + "\n"
                            + getSummaryOfVotes(game, true);
                        MessageHelper.sendMessageToChannel(watchParty, message);
                        MessageHelper.sendMessageWithFile(watchParty, systemWithContext,
                            "Surrounding Mecatol Rex In " + game.getName(), false);
                    }
                }
                if ("for".equalsIgnoreCase(winner)) {
                    Button ixthianButton = Button.success("rollIxthian", "Roll Ixthian Artifact")
                        .withEmoji(Emoji.fromFormatted(Emojis.Mecatol));
                    String msg = game.getPing() + "Click this button to roll Ixthian Artifact! 🥁";
                    MessageHelper.sendMessageToChannelWithButton(actionsChannel, msg, ixthianButton);
                } else {
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                        "Against on Ixthian? Disgraceful");
                }
            }
            if ("seed_empire".equalsIgnoreCase(agID)) {
                List<Player> winOrLose;
                StringBuilder message = new StringBuilder();
                Integer poIndex;
                poIndex = game.addCustomPO("Seed", 1);
                if ("for".equalsIgnoreCase(winner)) {
                    winOrLose = getPlayersWithMostPoints(game);
                } else {
                    winOrLose = getPlayersWithLeastPoints(game);

                }
                message.append("Custom public objective Seed Of An Empire has been added.\n");
                for (Player playerWL : winOrLose) {
                    game.scorePublicObjective(playerWL.getUserID(), poIndex);
                    message.append(playerWL.getRepresentation()).append(" scored Seed Of An Empire\n");
                    Helper.checkEndGame(game, playerWL);
                    if (playerWL.getTotalVictoryPoints() >= game.getVp()) {
                        break;
                    }
                }
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message.toString());
            }
            if ("plowshares".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    for (Player playerB : game.getRealPlayers()) {
                        new SwordsToPlowsharesTGGain().doSwords(playerB, event, game);
                    }
                } else {
                    for (Player playerB : game.getRealPlayers()) {
                        new RiseOfMessiah().doRise(playerB, event, game);
                    }
                }
            }
            if ("incentive".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    new RevealStage1().revealS1(event, actionsChannel);
                } else {
                    new RevealStage2().revealS2(event, actionsChannel);
                }
            }
            if ("unconventional".equalsIgnoreCase(agID)) {
                List<Player> winOrLose;
                if (!"for".equalsIgnoreCase(winner)) {
                    winOrLose = getLosingVoters(winner, game);
                    for (Player playerWL : winOrLose) {
                        new DiscardACRandom().discardRandomAC(event, game, playerWL, playerWL.getAc());
                    }
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                        "Discarded the action cards of those who voted \"For\".");
                } else {
                    winOrLose = getWinningVoters(winner, game);
                    for (Player playerWL : winOrLose) {
                        String message;
                        if (playerWL.hasAbility("autonetic_memory")) {
                            ButtonHelperAbilities.autoneticMemoryStep1(game, playerWL, 2);
                            message = playerWL.getFactionEmoji() + " Triggered Autonetic Memory Option";
                        } else {
                            game.drawActionCard(playerWL.getUserID());
                            game.drawActionCard(playerWL.getUserID());
                            if (playerWL.hasAbility("scheming")) {
                                game.drawActionCard(playerWL.getUserID());
                                ACInfo.sendActionCardInfo(game, playerWL, event);
                                MessageHelper.sendMessageToChannelWithButtons(playerWL.getCardsInfoThread(),
                                    playerWL.getRepresentation(true, true) + " use buttons to discard",
                                    ACInfo.getDiscardActionCardButtons(game, playerWL, false));
                            } else {
                                ACInfo.sendActionCardInfo(game, playerWL, event);
                            }
                        }

                        if (playerWL.getLeaderIDs().contains("yssarilcommander")
                            && !playerWL.hasLeaderUnlocked("yssarilcommander")) {
                            ButtonHelper.commanderUnlockCheck(playerWL, game, "yssaril", event);
                        }
                        ButtonHelper.checkACLimit(game, event, playerWL);
                    }
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                        "Dealt 2 action cards to each player who voted \"For\".");
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
                        ButtonHelperAgents.resolveArtunoCheck(playerB, game, finalTG);
                        ButtonHelperAbilities.pillageCheck(playerB, game);
                    }
                }
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                    game.getPing() + " Set all players' trade goods to " + finalTG);
                if (AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("disaster-watch-party", true).size() > 0 && !game.isFowMode()) {
                    TextChannel watchPary = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("disaster-watch-party", true).get(0);
                    for (Player playerB : comrades) {
                        MessageHelper.sendMessageToChannel(watchPary,
                            "The Galactic Council of " + game.getName() + " have generously volunteered " + playerB.getRepresentation() + " to donate " + maxLoss + "trade goods to the less economically fortunate citizens of the galaxy.");
                    }
                }
            }
            if ("crisis".equalsIgnoreCase(agID)) {
                if (!game.isHomebrewSCMode()) {
                    List<Button> scButtons = new ArrayList<>();
                    switch (winner) {
                        case "1" -> {
                            scButtons.add(Button.success("leadershipGenerateCCButtons", "Spend And Gain Command Tokens"));
                            //scButtons.add(Button.danger("leadershipExhaust", "Exhaust Planets"));
                        }
                        case "2" -> {
                            scButtons.add(Button.success("diploRefresh2", "Ready 2 Planets"));
                        }
                        case "3" -> {
                            scButtons.add(Button.secondary("sc_ac_draw", "Draw 2 Action Cards")
                                .withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                        }
                        case "4" -> {
                            scButtons.add(Button.success("construction_spacedock", "Place 1 space dock")
                                .withEmoji(Emoji.fromFormatted(Emojis.spacedock)));
                            scButtons.add(Button.success("construction_pds", "Place 1 PDS")
                                .withEmoji(Emoji.fromFormatted(Emojis.pds)));
                        }
                        case "5" -> {
                            scButtons.add(Button.secondary("sc_refresh", "Replenish Commodities")
                                .withEmoji(Emoji.fromFormatted(Emojis.comm)));
                        }
                        case "6" -> {
                            scButtons.add(Button.success("warfareBuild", "Build At Home"));
                        }
                        case "7" -> {
                            scButtons.add(Buttons.GET_A_TECH);
                        }
                        case "8" -> {
                            scButtons.add(Button.secondary("non_sc_draw_so", "Draw Secret Objective")
                                .withEmoji(Emoji.fromFormatted(Emojis.SecretObjective)));
                        }
                    }
                    MessageHelper.sendMessageToChannel(actionsChannel,
                        "You may use this button to resolve the secondary.", scButtons);
                }
            }

            if (game.getCurrentAgendaInfo().contains("Law")) {
                // Figure out law
            }
        }
        List<Player> riders = getWinningRiders(winner, game, event);
        List<Player> voters = getWinningVoters(winner, game);
        for (Player voter : voters) {
            if (voter.hasTech("dskyrog")) {
                MessageHelper.sendMessageToChannel(voter.getCorrectChannel(), voter.getFactionEmoji() + " gets to drop 2 infantry on a planet due to Kyro green technology.");
                List<Button> buttons = new ArrayList<>();
                buttons.addAll(Helper.getPlanetPlaceUnitButtons(voter, game, "2gf", "placeOneNDone_skipbuild"));
                MessageHelper.sendMessageToChannel(voter.getCorrectChannel(), "Use buttons to drop 2 infantry on a planet", buttons);
            }
        }
        voters.addAll(riders);
        for (Player player : voters) {
            if (player.getLeaderIDs().contains("florzencommander") && !player.hasLeaderUnlocked("florzencommander")) {
                ButtonHelper.commanderUnlockCheck(player, game, "florzen", event);
            }
        }
        String ridSum = "People had Riders to resolve.";
        for (Player rid : riders) {
            String rep = rid.getRepresentation(true, true);
            String message;
            if (rid.hasAbility("future_sight")) {
                message = rep
                    + "You have a Rider to resolve or you voted for the correct outcome. Either way 1 trade good has been added to your total due to your Future Sight ability. ("
                    + rid.getTg() + "-->" + (rid.getTg() + 1) + ")";
                rid.setTg(rid.getTg() + 1);
                ButtonHelperAgents.resolveArtunoCheck(rid, game, 1);
                ButtonHelperAbilities.pillageCheck(rid, game);
            } else {
                message = rep + "You have a Rider to resolve.";
            }
            if (game.isFowMode()) {
                MessageHelper.sendPrivateMessageToPlayer(rid, game, message);
            } else {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
            }
        }

        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Sent pings to all those who Rider'd.");
        } else if (riders.size() > 0) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), ridSum);
        }
        String resMes = "Resolving vote for " + StringUtils.capitalize(winner) + ".";
        String voteMessage = "Click the buttons for next steps after you're done resolving Riders.";
        String agendaCount = game.getStoredValue("agendaCount");
        int aCount;
        if (agendaCount.isEmpty()) {
            aCount = 1;
        } else {
            aCount = Integer.parseInt(agendaCount) + 1;
        }
        Button flipNextAgenda = Button.primary("flip_agenda", "Flip Agenda #" + aCount);
        Button proceedToStrategyPhase = Button.success("proceed_to_strategy",
            "Proceed to Strategy Phase (will run agenda cleanup and ping speaker)");
        List<Button> resActionRow = List.of(flipNextAgenda, proceedToStrategyPhase);
        if (!"miscount".equalsIgnoreCase(agID) && !"absol_miscount".equalsIgnoreCase(agID)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), resMes);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, resActionRow);
            if ("action_deck_2".equals(game.getAcDeckID()) && aCount > 2) {
                String acd2Shenanigans = "This is the window for *Last Minute Deliberations* and *Data Archive*! " + game.getPing();
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), acd2Shenanigans);
            }
        } else {
            game.removeLaw(winner);
            game.putAgendaBackIntoDeckOnTop(winner);
            RevealAgenda.revealAgenda(event, false, game, game.getMainGameChannel());
        }

        event.getMessage().delete().queue();
    }

    @Nullable
    private static String watchPartyPing(Game game) {
        List<Role> roles = AsyncTI4DiscordBot.guildPrimary.getRolesByName("Ixthian Watch Party", true);
        if (!game.isFowMode() && roles.size() > 0) {
            return roles.get(0).getAsMention();
        }
        return null;
    }

    @Nullable
    private static TextChannel watchPartyChannel(Game game) {
        List<TextChannel> channels = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("ixthian-watch-party", true);
        if (!game.isFowMode() && channels.size() > 0) {
            return channels.get(0);
        }
        return null;
    }

    private static void sleep() {
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (Exception ignored) {
        }
    }

    private static String drumroll(String ping, int drums) {
        StringBuilder sb = new StringBuilder();
        if (ping != null) {
            sb.append(ping).append("\n");
        }
        sb.append("# Drumroll please.... ").append(Emojis.RollDice).append("\n");
        sb.append("# 🥁").append(" 🥁".repeat(drums));
        return sb.toString();
    }

    public static void offerEveryonePrepassOnShenanigans(Game game) {
        if (game.islandMode()) return;
        for (Player player : game.getRealPlayers()) {
            if (playerDoesNotHaveShenanigans(player)) {
                continue;
            }
            String msg = player.getRepresentation()
                + " you have the option to prepass on agenda shenanigans here. Agenda shenanigans are the action cards known as Bribery, Deadly Plot, and the Confounding/Confusing Legal Texts."
                + " Feel free not to pre-pass, this is simply an optional way to resolve agendas faster.";
            List<Button> buttons = new ArrayList<>();

            buttons.add(Button.success("resolvePreassignment_Pass On Shenanigans", "Pre-pass"));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        }
    }

    private static boolean playerDoesNotHaveShenanigans(Player player) {
        Set<String> shenanigans = Set.of("deadly_plot", "bribery", "confounding", "confusing");
        return player.getActionCards().keySet().stream()
            .noneMatch(shenanigans::contains);
    }

    public static boolean doesPlayerHaveAnyWhensOrAfters(Player player) {
        if (!player.doesPlayerAutoPassOnWhensAfters()) {
            return true;
        }
        if (player.hasAbility("quash") || player.ownsPromissoryNote("rider")
            || player.getPromissoryNotes().containsKey("riderm")
            || player.hasAbility("radiance") || player.hasAbility("galactic_threat")
            || player.hasAbility("conspirators")
            || player.ownsPromissoryNote("riderx")
            || player.ownsPromissoryNote("riderm") || player.ownsPromissoryNote("ridera")) {
            return true;
        }
        for (String acID : player.getActionCards().keySet()) {
            ActionCardModel actionCard = Mapper.getActionCard(acID);
            String actionCardWindow = actionCard.getWindow();
            if (actionCardWindow.contains("When an agenda is revealed")
                || actionCardWindow.contains("After an agenda is revealed")) {
                return true;
            }
        }
        for (String pnID : player.getPromissoryNotes().keySet()) {
            if (player.ownsPromissoryNote(pnID)) {
                continue;
            }
            if (pnID.endsWith("_ps") && !pnID.contains("absol_")) {
                return true;
            }
        }
        return false;
    }

    public static void offerEveryonePreAbstain(Game game) {
        for (Player player : game.getRealPlayers()) {
            int[] voteInfo = getVoteTotal(player, game);
            if (voteInfo[0] < 1) {
                continue;
            }
            String msg = player.getFactionEmoji()
                + " if you intend to abstain from voting on this agenda, you have the option to preset an abstain here. Feel free not to pre-abstain, this is simply an optional way to resolve agendas faster";
            List<Button> buttons = new ArrayList<>();
            if (player.hasAbility("future_sight")) {
                msg = msg + ". Reminder that you have Future Sight and may not wish to abstain.";
            }

            buttons.add(Button.success("resolvePreassignment_Abstain On Agenda", "Pre-abstain"));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        }
    }

    public static void rollIxthian(Game game, boolean publish) {
        String activeGamePing = game.getPing();
        TextChannel watchParty = watchPartyChannel(game);
        String watchPartyPing = watchPartyPing(game);
        Message watchPartyMsg = publish && watchParty != null ? watchParty.sendMessage(drumroll(watchPartyPing, 0)).complete() : null;

        MessageHelper.MessageFunction resolveIxthian = (msg) -> {
            int rand = 4 + ThreadLocalRandom.current().nextInt(4);
            if (ThreadLocalRandom.current().nextInt(5) == 0) { // random chance for an extra long wait
                rand += 8 + ThreadLocalRandom.current().nextInt(14);
            }

            // Sleep will sleep for 2 seconds now, many quick edits is bad for rate limit
            sleep();
            for (int i = 1; i <= rand; i++) {
                msg.editMessage(drumroll(activeGamePing, i)).queue(Consumers.nop(), BotLogger::catchRestError);
                if (publish && watchPartyMsg != null) {
                    watchPartyMsg.editMessage(drumroll(watchPartyPing, i)).queue(Consumers.nop(), BotLogger::catchRestError);
                }
                sleep();
            }
            msg.delete().queue(Consumers.nop(), BotLogger::catchRestError);
            if (publish && watchPartyMsg != null) {
                watchPartyMsg.delete().queue(Consumers.nop(), BotLogger::catchRestError);
            }
            resolveIxthianRoll(game, publish);
        };
        MessageHelper.splitAndSentWithAction(drumroll(activeGamePing, 0), game.getMainGameChannel(), resolveIxthian);
    }

    private static void resolveIxthianRoll(Game game, boolean publish) {
        TextChannel watchParty = watchPartyChannel(game);
        String watchPartyPing = watchPartyPing(game);

        Die d1 = new Die(6);
        String msg = "# Rolled a " + d1.getResult() + " for Ixthian!";
        if (d1.isSuccess()) {
            msg += Emojis.Propulsion3 + " " + Emojis.Biotic3 + " " + Emojis.Cybernetic3 + " " + Emojis.Warfare3;
        } else {
            msg += "💥 💥 💥 💥";
        }
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
        if (watchParty != null && publish) {
            String watchMsg = watchPartyPing + " " + game.getName() + " has finished rolling:\n" + msg;
            MessageHelper.sendMessageToChannel(watchParty, watchMsg);
        }
        if (d1.isSuccess() && !game.isFowMode()) {
            if (Helper.getPlayerFromAbility(game, "propagation") != null) {
                Player player = Helper.getPlayerFromAbility(game, "propagation");
                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                String message2 = player.getRepresentation() + "! Your current command tokens are " + player.getCCRepresentation()
                    + ". Use buttons to gain command tokens.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    message2, buttons);
                game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
            }
            MessageHelper.sendMessageToChannelWithButton(game.getMainGameChannel(),
                "You may use the button to research your technologies.", Buttons.GET_A_TECH);
        } else if (!d1.isSuccess() && !game.isFowMode()) {
            Button modify = Button.secondary("getModifyTiles", "Modify Units");
            MessageHelper.sendMessageToChannelWithButton(game.getMainGameChannel(),
                "Remove units on or adjacent to Mecatol Rex, please.", modify);
        }
    }

    public static void pingAboutDebt(Game game) {
        for (Player player : game.getRealPlayers()) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player || (player.getTg() + player.getCommodities()) < 0 || p2.hasAbility("binding_debts") || p2.hasAbility("fine_print") || p2.getDebtTokenCount(player.getColor()) < 1) {
                    continue;
                }
                String msg = player.getRepresentation() + " This is a reminder that you owe debt to " + ButtonHelper.getIdentOrColor(p2, game) + " and now could be a good time to pay it (or get it cleared if it was paid already).";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
        }
    }

    public static void pingMissingPlayers(Game game) {

        List<Player> missingPlayersWhens = ButtonHelper.getPlayersWhoHaventReacted(game.getLatestWhenMsg(),
            game);
        List<Player> missingPlayersAfters = ButtonHelper.getPlayersWhoHaventReacted(game.getLatestAfterMsg(),
            game);
        if (missingPlayersAfters.isEmpty() && missingPlayersWhens.isEmpty()) {
            return;
        }

        String messageWhens = " please indicate \"No Whens\".";
        String messageAfters = " please indicate \"No Afters\".";
        if (game.isFowMode()) {
            for (Player player : missingPlayersWhens) {
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(),
                    player.getRepresentation(true, true) + messageWhens);
            }
            for (Player player : missingPlayersAfters) {
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(),
                    player.getRepresentation(true, true) + messageAfters);
            }
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                "Sent reminder pings to players who have not yet reacted.");

        } else {
            StringBuilder messageWhensBuilder = new StringBuilder(" please indicate \"No Whens\".");
            for (Player player : missingPlayersWhens) {
                messageWhensBuilder.insert(0, player.getRepresentation(true, true));
            }
            messageWhens = messageWhensBuilder.toString();
            if (missingPlayersWhens.size() > 0) {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), messageWhens);
            }

            StringBuilder messageAftersBuilder = new StringBuilder(" please indicate \"No Afters\".");
            for (Player player : missingPlayersAfters) {
                messageAftersBuilder.insert(0, player.getRepresentation(true, true));
            }
            messageAfters = messageAftersBuilder.toString();
            if (missingPlayersAfters.size() > 0) {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), messageAfters);
            }
        }
        Date newTime = new Date();
        game.setLastActivePlayerPing(newTime);
    }

    public static void offerVoteAmounts(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident, String buttonLabel) {
        String outcome = buttonID.substring(buttonID.indexOf("_") + 1);
        String voteMessage = "Chose to vote for " + StringUtils.capitalize(outcome)
            + ". Click buttons for amount of votes";
        game.setLatestOutcomeVotedFor(outcome);
        int maxVotes = getTotalVoteCount(game, player);
        int minVotes = 1;
        if (player.hasAbility("zeal")) {
            minVotes = minVotes + game.getRealPlayers().size();
        }

        if (game.getLaws() != null && (game.getLaws().containsKey("rep_govt")
            || game.getLaws().containsKey("absol_government"))) {
            minVotes = 1;
            maxVotes = 1;
            if (game.getLaws().containsKey("absol_government") && player.controlsMecatol(true)) {
                minVotes = 2;
                maxVotes = 2;
            }
        }
        if (maxVotes - minVotes > 20) {
            voteMessage = "Chose to vote for " + StringUtils.capitalize(outcome)
                + ". You have more votes than discord has buttons. Please further specify your desired vote count by clicking the button which contains your desired vote amount (or largest button).";
        }
        voteMessage = voteMessage + "\n" + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        List<Button> voteActionRow = getVoteButtons(minVotes, maxVotes);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
        event.getMessage().delete().queue();
    }

    public static void exhaustPlanetsForVoting(String buttonID, ButtonInteractionEvent event, Game game,
        Player player, String ident, String buttonLabel, String finsFactionCheckerPrefix) {
        String votes = buttonID.substring(buttonID.indexOf("_") + 1);
        String voteMessage = player.getFactionEmoji() + " Chose to vote " + votes + " vote" + (votes.equals("1") ? "" : "s") + " for "
            + StringUtils.capitalize(game.getLatestOutcomeVotedFor());
        List<Button> voteActionRow = getPlanetButtons(event, player, game);
        int allVotes = getVoteTotal(player, game)[0];
        Button exhausteverything = Button.danger("exhaust_everything_" + allVotes,
            "Exhaust everything (" + allVotes + ")");
        Button concludeExhausting = Button.danger(finsFactionCheckerPrefix + "resolveAgendaVote_" + votes,
            "Done exhausting planets.");
        Button OopsMistake = Button.success("refreshVotes_" + votes, "Ready planets");
        Button OopsMistake2 = Button.success("outcome_" + game.getLatestOutcomeVotedFor(), "Change # of votes");
        voteActionRow.add(exhausteverything);
        voteActionRow.add(concludeExhausting);
        voteActionRow.add(OopsMistake);
        voteActionRow.add(OopsMistake2);
        String voteMessage2 = "Exhaust stuff";
        MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage2, voteActionRow);
        event.getMessage().delete().queue();
    }

    public static void exhaustPlanetsForVotingVersion2(String buttonID, ButtonInteractionEvent event, Game game,
        Player player) {
        String outcome = buttonID.substring(buttonID.indexOf("_") + 1);
        String voteMessage = "Chose to vote for " + StringUtils.capitalize(outcome)
            + ". Click buttons to exhaust planets and use abilities for votes";
        game.setLatestOutcomeVotedFor(outcome);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage,
            getPlanetButtonsVersion2(event, player, game));
        event.getMessage().delete().queue();
    }

    public static void checkForAssigningGeneticRecombination(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.setStoredValue("Genetic Recombination " + player.getFaction(), "");
            if (player.hasTechReady("gr")) {
                String msg = player.getRepresentation()
                    + " you have the option to pre-assign the declaration of using Genetic Recombination on someone."
                    + " When they are up to vote, it will ping them saying that you wish to use Genetic Recombination, and then it will be your job to clarify."
                    + " Feel free to not preassign if you don't wish to use it on this agenda.";
                List<Button> buttons2 = new ArrayList<>();
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (!game.isFowMode()) {
                        buttons2.add(Button.secondary(
                            "resolvePreassignment_Genetic Recombination " + player.getFaction() + "_"
                                + p2.getFaction(),
                            p2.getFaction()));
                    } else {
                        buttons2.add(Button.secondary(
                            "resolvePreassignment_Genetic Recombination " + player.getFaction() + "_"
                                + p2.getFaction(),
                            p2.getColor()));
                    }
                }
                buttons2.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons2);
            }
        }
    }

    public static void exhaustStuffForVoting(String buttonID, ButtonInteractionEvent event, Game game,
        Player player, String ident, String buttonLabel) {
        String planetName = StringUtils.substringAfter(buttonID, "_");
        String votes = StringUtils.substringBetween(buttonLabel, "(", ")");
        if (!buttonID.contains("argent") && !buttonID.contains("blood") && !buttonID.contains("predictive")
            && !buttonID.contains("everything")) {
            new PlanetExhaust().doAction(player, planetName, game, false);
        }
        if (buttonID.contains("everything")) {
            for (String planet : player.getPlanets()) {
                player.exhaustPlanet(planet);
            }
        }
        List<ActionRow> actionRow2 = new ArrayList<>();
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            int buttonIndex = buttonRow.indexOf(event.getButton());
            if (buttonIndex > -1) {
                buttonRow.remove(buttonIndex);
            }
            if (buttonRow.size() > 0) {
                actionRow2.add(ActionRow.of(buttonRow));
            }
        }
        String totalVotesSoFar = event.getMessage().getContentRaw();
        if (!buttonID.contains("argent") && !buttonID.contains("blood") && !buttonID.contains("predictive")
            && !buttonID.contains("everything")) {

            if ("Exhaust stuff".equalsIgnoreCase(totalVotesSoFar)) {
                totalVotesSoFar = "Total votes exhausted so far: " + votes + "\n Planets exhausted so far are: "
                    + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
            } else {
                int totalVotes = Integer.parseInt(
                    totalVotesSoFar.substring(totalVotesSoFar.indexOf(":") + 2, totalVotesSoFar.indexOf("\n")))
                    + Integer.parseInt(votes);
                totalVotesSoFar = totalVotesSoFar.substring(0, totalVotesSoFar.indexOf(":") + 2) + totalVotes
                    + totalVotesSoFar.substring(totalVotesSoFar.indexOf("\n"))
                    + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
            }
            if (actionRow2.size() > 0) {
                event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
            }
            // addReaction(event, true, false,"Exhausted
            // "+Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName,
            // activeMap) + " as "+ votes + " votes", "");
        } else {
            if ("Exhaust stuff".equalsIgnoreCase(totalVotesSoFar)) {
                totalVotesSoFar = "Total votes exhausted so far: " + votes
                    + "\n Planets exhausted so far are: all planets";
            } else {
                int totalVotes = Integer.parseInt(
                    totalVotesSoFar.substring(totalVotesSoFar.indexOf(":") + 2, totalVotesSoFar.indexOf("\n")))
                    + Integer.parseInt(votes);
                totalVotesSoFar = totalVotesSoFar.substring(0, totalVotesSoFar.indexOf(":") + 2) + totalVotes
                    + totalVotesSoFar.substring(totalVotesSoFar.indexOf("\n"));
            }
            if (actionRow2.size() > 0) {
                event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
            }
            if (buttonID.contains("everything")) {
                // addReaction(event, true, false,"Exhausted
                // "+Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName,
                // activeMap) + " as "+ votes + " votes", "");
                ButtonHelper.addReaction(event, true, false, "Exhausted all planets for " + votes + " vote" + (votes.equals("1") ? "" : "s"), "");
            } else {
                ButtonHelper.addReaction(event, true, false, "Used ability for " + votes + " vote" + (votes.equals("1") ? "" : "s"), "");
            }
        }
    }

    public static void resolvingAnAgendaVote(String buttonID, ButtonInteractionEvent event, Game game,
        Player player) {
        boolean resolveTime = false;
        String winner = "";
        String votes = buttonID.substring(buttonID.lastIndexOf("_") + 1);
        if (!buttonID.contains("outcomeTie*")) {
            if ("0".equalsIgnoreCase(votes)) {

                String pfaction2 = null;
                if (player != null) {
                    pfaction2 = player.getFaction();
                }
                if (pfaction2 != null) {
                    player.resetSpentThings();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        player.getRepresentation() + " Abstained");
                    event.getMessage().delete().queue();
                }

            } else {
                String identifier;
                String outcome = game.getLatestOutcomeVotedFor();
                if (game.isFowMode()) {
                    identifier = player.getColor();
                } else {
                    identifier = player.getFaction();
                }
                Map<String, String> outcomes = game.getCurrentAgendaVotes();
                String existingData = outcomes.getOrDefault(outcome, "empty");
                int numV = Integer.parseInt(votes);
                int numVOrig = Integer.parseInt(Helper.buildSpentThingsMessageForVoting(player, game, true));
                if (numV > numVOrig) {
                    player.addSpentThing("specialVotes_" + (numV - numVOrig));
                }
                if (game.getLaws() != null && (game.getLaws().containsKey("rep_govt")
                    || game.getLaws().containsKey("absol_government"))) {
                } else {
                    if (player.ownsPromissoryNote("blood_pact")
                        || player.getPromissoryNotesInPlayArea().contains("blood_pact")) {
                        for (Player p2 : getWinningVoters(outcome, game)) {
                            if (p2 == player) {
                                continue;
                            }
                            if (p2.ownsPromissoryNote("blood_pact")
                                || p2.getPromissoryNotesInPlayArea().contains("blood_pact")) {
                                player.addSpentThing("bloodPact_" + 4);
                                votes = (Integer.parseInt(votes) + 4) + "";
                                break;
                            }
                        }
                    }
                }
                if ("empty".equalsIgnoreCase(existingData)) {
                    existingData = identifier + "_" + votes;
                } else {
                    existingData = existingData + ";" + identifier + "_" + votes;
                }
                game.setCurrentAgendaVote(outcome, existingData);
                String msg = player.getFactionEmoji() + " Voted " + votes + " vote" + (votes.equals("1") ? "" : "s") + " for "
                    + StringUtils.capitalize(outcome) + "!";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    Helper.buildSpentThingsMessageForVoting(player, game, false));
                event.getMessage().delete().queue();
            }

            String message = " up to vote! Resolve using buttons.";
            Button eraseandReVote = Button.danger("eraseMyVote", "Erase My Vote And Have Me Vote Again");
            String revoteMsg = "You may press this button to revote, if you made a mistake. Ignore it otherwise.";
            MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(), revoteMsg, eraseandReVote);
            Player nextInLine = getNextInLine(player, getVotingOrder(game), game);
            String realIdentity2 = nextInLine.getRepresentation(true, true);

            int[] voteInfo = getVoteTotal(nextInLine, game);

            while ((voteInfo[0] < 1 && !nextInLine.getColor().equalsIgnoreCase(player.getColor()))
                || game.getStoredValue("Abstain On Agenda").contains(nextInLine.getFaction())) {
                String skippedMessage = nextInLine.getRepresentation(true, false)
                    + "You are being skipped because you cannot vote";
                if (game.getStoredValue("Abstain On Agenda").contains(nextInLine.getFaction())) {
                    skippedMessage = realIdentity2
                        + "You are being skipped because you told the bot you wanted to preset an abstain";
                    game.setStoredValue("Abstain On Agenda", game
                        .getStoredValue("Abstain On Agenda").replace(nextInLine.getFaction(), ""));
                    nextInLine.resetSpentThings();
                }
                if (game.isFowMode()) {
                    MessageHelper.sendPrivateMessageToPlayer(nextInLine, game, skippedMessage);
                } else {
                    MessageHelper.sendMessageToChannel(event.getChannel(), skippedMessage);
                }
                player = nextInLine;
                nextInLine = getNextInLine(nextInLine, getVotingOrder(game), game);
                realIdentity2 = nextInLine.getRepresentation(true, true);
                voteInfo = getVoteTotal(nextInLine, game);
            }

            if (!nextInLine.getColor().equalsIgnoreCase(player.getColor())) {
                String realIdentity;
                realIdentity = nextInLine.getRepresentation(true, true);
                String pFaction = nextInLine.getFlexibleDisplayName();
                String finChecker = "FFCC_" + nextInLine.getFaction() + "_";
                Button Vote = Button.success(finChecker + "vote", pFaction + " Choose To Vote");
                Button Abstain;
                if (nextInLine.hasAbility("future_sight")) {
                    Abstain = Button.danger(finChecker + "resolveAgendaVote_0", pFaction + " Choose To Abstain (You Have Future Sight)");
                } else {
                    Abstain = Button.danger(finChecker + "resolveAgendaVote_0", pFaction + " Choose To Abstain");
                }
                Button ForcedAbstain = Button.secondary("forceAbstainForPlayer_" + nextInLine.getFaction(),
                    "(For Others) Abstain for this player");
                game.updateActivePlayer(nextInLine);
                List<Button> buttons = List.of(Vote, Abstain, ForcedAbstain);
                if (game.isFowMode()) {
                    if (nextInLine.getPrivateChannel() != null) {
                        MessageHelper.sendMessageToChannel(nextInLine.getPrivateChannel(),
                            getSummaryOfVotes(game, true) + "\n ");
                        MessageHelper.sendMessageToChannelWithButtons(nextInLine.getPrivateChannel(),
                            "\n " + realIdentity + message, buttons);
                        event.getChannel().sendMessage("Notified next in line").queue();
                    }
                } else {
                    message = getSummaryOfVotes(game, true) + "\n \n " + realIdentity + message;
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                }
                ButtonHelperFactionSpecific.checkForGeneticRecombination(nextInLine, game);
            } else {
                winner = getWinner(game);
                if (!"".equalsIgnoreCase(winner) && !winner.contains("*")) {
                    resolveTime = true;
                } else {
                    Player speaker;
                    if (game.getPlayer(game.getSpeaker()) != null) {
                        speaker = game.getPlayers().get(game.getSpeaker());
                    } else {
                        speaker = game.getRealPlayers().get(0);
                    }
                    List<Button> tiedWinners = new ArrayList<>();
                    if (!"".equalsIgnoreCase(winner)) {
                        StringTokenizer winnerInfo = new StringTokenizer(winner, "*");
                        while (winnerInfo.hasMoreTokens()) {
                            String tiedWinner = winnerInfo.nextToken();
                            Button button = Button.primary("resolveAgendaVote_outcomeTie* " + tiedWinner, tiedWinner);
                            tiedWinners.add(button);
                        }
                    } else {
                        tiedWinners = getAgendaButtons(null, game, "resolveAgendaVote_outcomeTie*");
                    }
                    if (!tiedWinners.isEmpty()) {
                        MessageChannel channel = speaker.getCorrectChannel();
                        MessageHelper.sendMessageToChannelWithButtons(channel,
                            speaker.getRepresentation(true, true) + " please decide the winner.", tiedWinners);
                    }
                }
            }
        } else {
            resolveTime = true;
            winner = buttonID.substring(buttonID.lastIndexOf("*") + 2);
        }
        if (resolveTime) {
            resolveTime(event, game, winner);
        }
        if (!"0".equalsIgnoreCase(votes)) {
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        }
        GameSaveLoadManager.saveMap(game, event);

    }

    public static void resolveTime(GenericInteractionCreateEvent event, Game game, String winner) {
        if (winner == null) {
            winner = getWinner(game);
        }
        String summary2 = getSummaryOfVotes(game, true);
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), summary2 + "\n \n");
        game.setPhaseOfGame("agendaEnd");
        game.setActivePlayerID(null);
        StringBuilder message = new StringBuilder();
        message.append(game.getPing()).append("\n");
        message.append("### Current winner is ").append(StringUtils.capitalize(winner)).append("\n");
        if (!"action_deck_2".equals(game.getAcDeckID())) {
            handleShenanigans(event, game, winner);
            message.append("When shenanigans have concluded, please confirm resolution or discard the result and manually resolve it yourselves.");
        }
        Button autoResolve = Button.primary("agendaResolution_" + winner, "Resolve with Current Winner");
        Button manualResolve = Button.danger("autoresolve_manual", "Resolve it Manually");
        List<Button> resolutions = List.of(autoResolve, manualResolve);
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), message.toString(), resolutions);
    }

    private static void handleShenanigans(GenericInteractionCreateEvent event, Game game, String winner) {
        List<Player> losers = getLosers(winner, game);
        boolean shenanigans = false;
        if (game.islandMode()) return;

        if ((!game.isACInDiscard("Bribery") || !game.isACInDiscard("Deadly Plot")) && (losers.size() > 0 || game.isAbsolMode())) {
            StringBuilder message = new StringBuilder("You may hold while people resolve shenanigans. If it is not an important agenda, you are encouraged to move on and float the shenanigans.\n");
            Button noDeadly = Button.primary("generic_button_id_1", "No Deadly Plot");
            Button noBribery = Button.primary("generic_button_id_2", "No Bribery");
            List<Button> deadlyActionRow = List.of(noBribery, noDeadly);
            if (!game.isFowMode()) {
                if (!game.isACInDiscard("Deadly Plot")) {
                    message.append("The following players (" + losers.size() + ") have the opportunity to play " + Emojis.ActionCard + "Deadly Plot:\n");
                }
                for (Player loser : losers) {
                    message.append("> ").append(loser.getRepresentation(true, true)).append("\n");
                }
                message.append("Please confirm you will not be playing Bribery or Deadly Plot");
            } else {
                message.append(losers.size() + " players have the opportunity to play " + Emojis.ActionCard + "Deadly Plot.\n");
                MessageHelper.privatelyPingPlayerList(losers, game, "Please respond to Bribery/Deadly Plot window");
            }
            MessageHelper.sendMessageToChannelWithPersistentReacts(game.getMainGameChannel(), message.toString(), game, deadlyActionRow, "shenanigans");
            shenanigans = true;
        } else {
            String message = "Either both Bribery and Deadly Plot were in the discard or no player could legally play them.";
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
        }

        // Confounding & Confusing Legal Text
        if (game.getCurrentAgendaInfo().contains("Elect Player")) {
            if (!game.isACInDiscard("Confounding") || !game.isACInDiscard("Confusing")) {
                String message = game.getPing() + " please confirm no Confusing/Confounding Legal Texts.";
                Button noConfounding = Button.primary("generic_button_id_3", "Refuse Confounding Legal Text");
                Button noConfusing = Button.primary("genericReact4", "Refuse Confusing Legal Text");
                List<Button> buttons = List.of(noConfounding, noConfusing);
                MessageHelper.sendMessageToChannelWithPersistentReacts(game.getMainGameChannel(), message, game, buttons, "shenanigans");
                shenanigans = true;
            } else {
                String message = "Both *Confounding Legal Text* and *Confusing Legal Text* are in the discard pile.\nThere are no shenanigans possible. Please resolve the agenda.";
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
            }
        }

        if (!shenanigans) {
            String message = "There are no shenanigans possible. Please resolve the agenda.";
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
        }
    }

    public static void reverseRider(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident) {
        String choice = buttonID.substring(buttonID.indexOf("_") + 1);

        String voteMessage = " Chose to reverse the " + choice;
        if (game.isFowMode()) {
            voteMessage = player.getColor() + voteMessage;
        } else {
            voteMessage = ident + voteMessage;
        }
        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        for (String outcome : outcomes.keySet()) {
            String existingData = outcomes.getOrDefault(outcome, "empty");
            if (existingData == null || "empty".equalsIgnoreCase(existingData) || "".equalsIgnoreCase(existingData)) {
            } else {
                String[] votingInfo = existingData.split(";");
                StringBuilder totalBuilder = new StringBuilder();
                for (String onePiece : votingInfo) {
                    if (!onePiece.contains(choice)) {
                        totalBuilder.append(";").append(onePiece);
                    }
                }
                String total = totalBuilder.toString();
                if (total.length() > 0 && total.charAt(0) == ';') {
                    total = total.substring(1);
                }
                game.setCurrentAgendaVote(outcome, total);
            }

        }

        event.getChannel().sendMessage(voteMessage).queue();
        // event.getMessage().delete().queue();
    }

    public static void reverseAllRiders(ButtonInteractionEvent event, Game game, Player player) {

        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        for (String outcome : outcomes.keySet()) {
            String existingData = outcomes.getOrDefault(outcome, "empty");
            if (existingData == null || "empty".equalsIgnoreCase(existingData) || "".equalsIgnoreCase(existingData)) {
            } else {
                String[] votingInfo = existingData.split(";");
                StringBuilder totalBuilder = new StringBuilder();
                for (String onePiece : votingInfo) {
                    String identifier = onePiece.split("_")[0];
                    if (!identifier.equalsIgnoreCase(player.getFaction())
                        && !identifier.equalsIgnoreCase(player.getColor())) {
                        totalBuilder.append(";").append(onePiece);
                    } else {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                            player.getFactionEmoji() + " erased " + onePiece.split("_")[1]);
                    }
                }
                String total = totalBuilder.toString();
                if (total.length() > 0 && total.charAt(0) == ';') {
                    total = total.substring(1);
                }
                game.setCurrentAgendaVote(outcome, total);
            }
        }
    }

    public static void placeRider(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident) {
        String[] choiceParams = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.lastIndexOf("_")).split(";");
        // String choiceType = choiceParams[0];
        String choice = choiceParams[1];

        String rider = buttonID.substring(buttonID.lastIndexOf("_") + 1);
        String agendaDetails = game.getCurrentAgendaInfo().split("_")[1];
        // if(activeMap)
        String cleanedChoice = choice;
        if (agendaDetails.contains("Planet") || agendaDetails.contains("planet")) {
            cleanedChoice = Helper.getPlanetRepresentation(choice, game);
        }
        String voteMessage = "Chose to put a " + rider + " on " + StringUtils.capitalize(cleanedChoice);
        if (!game.isFowMode()) {
            voteMessage = ident + " " + voteMessage;
        }
        String identifier;
        if (game.isFowMode()) {
            identifier = player.getColor();
        } else {
            identifier = player.getFaction();
        }
        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        String existingData = outcomes.getOrDefault(choice, "empty");
        if ("empty".equalsIgnoreCase(existingData)) {
            existingData = identifier + "_" + rider;
        } else {
            existingData = existingData + ";" + identifier + "_" + rider;
        }
        game.setCurrentAgendaVote(choice, existingData);

        MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
        String summary2 = getSummaryOfVotes(game, true);
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), summary2 + "\n \n");

        event.getMessage().delete().queue();
    }

    public static List<Button> getWhenButtons(Game game) {
        Button playWhen = Button.danger("play_when", "Play When");
        Button noWhen = Button.primary("no_when", "No Whens For Now")
            .withEmoji(Emoji.fromFormatted(Emojis.nowhens));
        Button noWhenPersistent = Button
            .primary("no_when_persistent", "No Whens For This Agenda")
            .withEmoji(Emoji.fromFormatted(Emojis.nowhens));
        List<Button> whenButtons = new ArrayList<>(List.of(playWhen, noWhen, noWhenPersistent));
        Player quasher = Helper.getPlayerFromAbility(game, "quash");
        if (quasher != null && quasher.getStrategicCC() > 0) {
            String finChecker = "FFCC_" + quasher.getFaction() + "_";
            Button quashButton = Button.danger(finChecker + "quash", "Quash Agenda")
                .withEmoji(Emoji.fromFormatted(Emojis.Xxcha));
            if (game.isFowMode()) {
                List<Button> quashButtons = new ArrayList<>(List.of(quashButton));
                MessageHelper.sendMessageToChannelWithButtons(quasher.getPrivateChannel(),
                    "Use Button To Quash If You Want", quashButtons);
            } else {
                whenButtons.add(quashButton);
            }
        }
        return whenButtons;
    }

    public static List<Button> getAfterButtons(Game game) {
        List<Button> afterButtons = new ArrayList<>();
        Button playAfter = Button.danger("play_after_Non-AC Rider", "Play A Non-Action Card Rider");
        if (game.isFowMode()) {
            afterButtons.add(playAfter);
        }

        if (ButtonHelper.shouldKeleresRiderExist(game) && !game.isFowMode()) {
            Button playKeleresAfter = Button.secondary("play_after_Keleres Rider", "Play Keleres Rider")
                .withEmoji(Emoji.fromFormatted(Emojis.Keleres));
            afterButtons.add(playKeleresAfter);
        }
        if (!game.isFowMode() && Helper.getDateDifference(game.getCreationDate(),
            Helper.getDateRepresentation(1705824000011L)) < 0) {
            for (Player player : game.getRealPlayers()) {
                String finChecker = "FFCC_" + player.getFaction() + "_";
                String planet = "tarrock";
                if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)) {
                    afterButtons
                        .add(Button.success(finChecker + "planetAbilityExhaust_" + planet, "Use Tarrock Ability")
                            .withEmoji(Emoji.fromFormatted(player.getFactionEmoji())));
                }
            }
        }
        if (game.getPNOwner("dspnedyn") != null && !game.isFowMode()) {
            Button playKeleresAfter = Button.secondary("play_after_Edyn Rider", "Play Edyn Rider Promissory Note")
                .withEmoji(Emoji.fromFormatted(Emojis.edyn));
            afterButtons.add(playKeleresAfter);
        }
        if (game.getPNOwner("dspnkyro") != null && !game.isFowMode()) {
            Button playKeleresAfter = Button.secondary("play_after_Kyro Rider", "Play Kyro Rider Promissory Note")
                .withEmoji(Emoji.fromFormatted(Emojis.kyro));
            afterButtons.add(playKeleresAfter);
        }
        if (Helper.getPlayerFromAbility(game, "galactic_threat") != null) {
            Player nekroProbably = Helper.getPlayerFromAbility(game, "galactic_threat");
            String finChecker = "FFCC_" + nekroProbably.getFaction() + "_";
            Button playNekroAfter = Button
                .secondary(finChecker + "play_after_Galactic Threat Rider", "Do Galactic Threat Prediction")
                .withEmoji(Emoji.fromFormatted(Emojis.Nekro));
            afterButtons.add(playNekroAfter);
        } // conspirators
        if (Helper.getPlayerFromAbility(game, "conspirators") != null && !game.isFowMode()) {
            Player nekroProbably = Helper.getPlayerFromAbility(game, "conspirators");
            String finChecker = "FFCC_" + nekroProbably.getFaction() + "_";
            Button playNekroAfter = Button
                .secondary(finChecker + "play_after_Conspirators", "Use Conspirators To Vote Last")
                .withEmoji(Emoji.fromFormatted(Emojis.zealots));
            afterButtons.add(playNekroAfter);
        } // conspirators
        if (Helper.getPlayerFromUnlockedLeader(game, "keleresheroodlynn") != null) {
            Player keleresX = Helper.getPlayerFromUnlockedLeader(game, "keleresheroodlynn");
            String finChecker = "FFCC_" + keleresX.getFaction() + "_";
            Button playKeleresHero = Button.secondary(finChecker + "play_after_Keleres Xxcha Hero", "Play Keleres (Xxcha) Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.Keleres));
            afterButtons.add(playKeleresHero);
        }
        if (Helper.getPlayerFromAbility(game, "radiance") != null) {
            Player edyn = Helper.getPlayerFromAbility(game, "radiance");
            String finChecker = "FFCC_" + edyn.getFaction() + "_";
            Button playKeleresHero = Button
                .secondary(finChecker + "play_after_Edyn Radiance Ability", "Use Edyn Radiance Ability")
                .withEmoji(Emoji.fromFormatted(Emojis.edyn));
            afterButtons.add(playKeleresHero);
        }

        for (Player p1 : game.getRealPlayers()) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            if (p1.hasTechReady("dsedyng")) {
                Button playKeleresHero = Button
                    .secondary(finChecker + "play_after_Edyn Unity Algorithm", "Use Edyn Unity Algorithm Technology")
                    .withEmoji(Emoji.fromFormatted(Emojis.edyn));
                afterButtons.add(playKeleresHero);
            }
            if (game.getCurrentAgendaInfo().contains("Player")
                && ButtonHelper.isPlayerElected(game, p1, "committee")) {
                Button playKeleresHero = Button
                    .secondary(finChecker + "autoresolve_manualcommittee", "Use Committee Formation")
                    .withEmoji(Emoji.fromFormatted(Emojis.Agenda));
                afterButtons.add(playKeleresHero);
            }
        }

        Button noAfter = Button.primary("no_after", "No Afters For Now")
            .withEmoji(Emoji.fromFormatted(Emojis.noafters));
        afterButtons.add(noAfter);
        Button noAfterPersistent = Button
            .primary("no_after_persistent", "No Afters For This Agenda")
            .withEmoji(Emoji.fromFormatted(Emojis.noafters));
        afterButtons.add(noAfterPersistent);

        return afterButtons;
    }

    public static void ministerOfIndustryCheck(Player player, Game game, Tile tile,
        GenericInteractionCreateEvent event) {
        if (ButtonHelper.isPlayerElected(game, player, "minister_industry")) {
            String msg = player.getRepresentation(true, true)
                + "since you have Minister of Industry, you may build in tile "
                + tile.getRepresentationForButtons(game, player) + ". You have "
                + Helper.getProductionValue(player, game, tile, false) + " PRODUCTION Value in the system.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg,
                Helper.getPlaceUnitButtons(event, player, game, tile, "ministerBuild", "place"));
        }
    }

    public static List<Button> getVoteButtons(int minVote, int voteTotal) {
        List<Button> voteButtons = new ArrayList<>();
        if (minVote < 0) {
            minVote = 0;
        }
        if (voteTotal - minVote > 20) {
            for (int x = 10; x < voteTotal + 10; x += 10) {
                int y = x - 9;
                int z = x;
                if (x > voteTotal) {
                    z = voteTotal;
                    y = z - 9;
                }
                Button button = Button.secondary("fixerVotes_" + z, y + "-" + z);
                voteButtons.add(button);
            }
        } else {
            for (int x = minVote; x < voteTotal + 1; x++) {
                Button button = Button.secondary("votes_" + x, "" + x);
                voteButtons.add(button);
            }
            Button button = Button.danger("distinguished_" + voteTotal, "Increase Votes");
            voteButtons.add(button);
        }
        return voteButtons;
    }

    public static List<Button> getVoteButtonsVersion2(int minVote, int voteTotal) {
        List<Button> voteButtons = new ArrayList<>();
        if (minVote < 0) {
            minVote = 0;
        }
        for (int x = minVote; x < voteTotal + 1; x++) {
            Button button = Button.secondary("resolveAgendaVote_" + x, "" + x);
            voteButtons.add(button);
        }
        Button button = Button.danger("distinguished_" + voteTotal, "Increase Votes");
        voteButtons.add(button);
        return voteButtons;
    }

    public static List<Button> getForAgainstOutcomeButtons(String rider, String prefix) {
        List<Button> voteButtons = new ArrayList<>();
        Button button;
        Button button2;
        if (rider == null) {
            button = Button.secondary(prefix + "_for", "For");
            button2 = Button.danger(prefix + "_against", "Against");
        } else {
            button = Button.primary("rider_fa;for_" + rider, "For");
            button2 = Button.danger("rider_fa;against_" + rider, "Against");
        }
        voteButtons.add(button);
        voteButtons.add(button2);
        return voteButtons;
    }

    public static void startTheVoting(Game game) {
        game.setPhaseOfGame("agendaVoting");
        if (game.getCurrentAgendaInfo() != null) {
            String message = " up to vote! Resolve using buttons. \n \n" + getSummaryOfVotes(game, true);

            Player nextInLine = null;
            try {
                nextInLine = getNextInLine(null, getVotingOrder(game), game);
            } catch (Exception e) {
                BotLogger.log("Could not find next in line", e);
            }
            if (nextInLine == null) {
                BotLogger.log("`AgendaHelper.startTheVoting` " + nextInLine + " is **null**");
                return;
            }
            String realIdentity = nextInLine.getRepresentation(true, true);
            int[] voteInfo = getVoteTotal(nextInLine, game);
            int counter = 0;
            while ((voteInfo[0] < 1
                || game.getStoredValue("Abstain On Agenda").contains(nextInLine.getFaction()))
                && counter < game.getRealPlayers().size()) {
                String skippedMessage = nextInLine.getRepresentation(true, false) + "You are being skipped because the bot thinks you can't vote.";
                if (game.getStoredValue("Abstain On Agenda").contains(nextInLine.getFaction())) {
                    skippedMessage = realIdentity
                        + "You are being skipped because you told the bot you wanted to preset an abstain";
                    game.setStoredValue("Abstain On Agenda", game
                        .getStoredValue("Abstain On Agenda").replace(nextInLine.getFaction(), ""));
                    nextInLine.resetSpentThings();
                }
                if (game.isFowMode()) {
                    MessageHelper.sendPrivateMessageToPlayer(nextInLine, game, skippedMessage);
                } else {
                    MessageHelper.sendMessageToChannel(nextInLine.getCorrectChannel(), skippedMessage);
                }
                nextInLine = getNextInLine(nextInLine, getVotingOrder(game), game);
                realIdentity = nextInLine.getRepresentation(true, true);
                voteInfo = getVoteTotal(nextInLine, game);
                counter = counter + 1;
            }

            String pFaction = StringUtils.capitalize(nextInLine.getFaction());
            message = realIdentity + message;
            String finChecker = "FFCC_" + nextInLine.getFaction() + "_";
            Button Vote = Button.success(finChecker + "vote", pFaction + " Choose To Vote");
            Button Abstain;
            if (nextInLine.hasAbility("future_sight")) {
                Abstain = Button.danger(finChecker + "resolveAgendaVote_0", pFaction + " Choose To Abstain (You Have Future Sight)");
            } else {
                Abstain = Button.danger(finChecker + "resolveAgendaVote_0", pFaction + " Choose To Abstain");
            }
            Button ForcedAbstain = Button.secondary("forceAbstainForPlayer_" + nextInLine.getFaction(),
                "(For Others) Abstain for this player");
            try {
                game.updateActivePlayer(nextInLine);
            } catch (Exception e) {
                BotLogger.log("Could not update active player", e);
            }

            List<Button> buttons = List.of(Vote, Abstain, ForcedAbstain);
            if (game.isFowMode()) {
                if (nextInLine.getPrivateChannel() != null) {
                    MessageHelper.sendMessageToChannelWithButtons(nextInLine.getPrivateChannel(), message, buttons);
                    game.getMainGameChannel().sendMessage("Voting started. Notified first in line").queue();
                }
            } else {
                MessageHelper.sendMessageToChannelWithButtons(nextInLine.getCorrectChannel(), message, buttons);
            }
            ButtonHelperFactionSpecific.checkForGeneticRecombination(nextInLine, game);
        } else {
            game.getMainGameChannel().sendMessage("Cannot find voting info, sorry. Please resolve automatically")
                .queue();
        }
    }

    public static List<Button> getLawOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> lawButtons = new ArrayList<>();
        for (Map.Entry<String, Integer> law : game.getLaws().entrySet()) {
            String lawName = Mapper.getAgendaTitleNoCap(law.getKey());
            Button button;
            if (rider == null) {
                button = Button.secondary(prefix + "_" + law.getKey(), lawName);
            } else {
                button = Button.secondary(prefix + "rider_law;" + law.getKey() + "_" + rider, lawName);
            }
            lawButtons.add(button);
        }
        return lawButtons;
    }

    public static List<Button> getSecretOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> secretButtons = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            for (Map.Entry<String, Integer> so : player.getSecretsScored().entrySet()) {
                Button button;
                String soName = Mapper.getSecretObjectivesJustNames().get(so.getKey());
                if (rider == null) {

                    button = Button.secondary(prefix + "_" + so.getKey(), soName);
                } else {
                    button = Button.secondary(prefix + "rider_so;" + so.getKey() + "_" + rider, soName);
                }
                secretButtons.add(button);
            }
        }
        return secretButtons;
    }

    public static List<Button> getUnitUpgradeOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> buttons = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            for (TechnologyModel tech : Helper.getAllNonFactionUnitUpgradeTech(game, player)) {
                Button button;
                if (rider == null) {
                    button = Button.secondary(prefix + "_" + tech.getAlias(), tech.getName());
                } else {
                    button = Button.secondary(prefix + "rider_so;" + tech.getAlias() + "_" + rider, tech.getName());
                }
                buttons.add(button);
            }
        }
        return buttons;
    }

    public static List<Button> getUnitOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> buttons = new ArrayList<>();
        for (TechnologyModel tech : Helper.getAllNonFactionUnitUpgradeTech(game)) {
            Button button;
            if (rider == null) {
                button = Button.secondary(prefix + "_" + tech.getAlias(), tech.getName());
            } else {
                button = Button.secondary(prefix + "rider_so;" + tech.getAlias() + "_" + rider, tech.getName());
            }
            buttons.add(button);
        }
        return buttons;
    }

    public static List<Button> getStrategyOutcomeButtons(String rider, String prefix) {
        List<Button> strategyButtons = new ArrayList<>();
        for (int x = 1; x < 9; x++) {
            Button button;
            if (rider == null) {
                Emoji scEmoji = Emoji.fromFormatted(Emojis.getSCBackEmojiFromInteger(x));
                if (scEmoji.getName().contains("SC") && scEmoji.getName().contains("Back")) {
                    button = Button.secondary(prefix + "_" + x, " ").withEmoji(scEmoji);
                } else {
                    button = Button.secondary(prefix + "_" + x, x + "");
                }

            } else {
                button = Button.secondary(prefix + "rider_sc;" + x + "_" + rider, x + "");
            }
            strategyButtons.add(button);
        }
        return strategyButtons;
    }

    public static List<Button> getPlanetOutcomeButtons(GenericInteractionCreateEvent event, Player player,
        Game game, String prefix, String rider) {
        List<Button> planetOutcomeButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanets());
        for (String planet : planets) {
            Button button;
            if (rider == null) {
                button = Button.secondary(prefix + "_" + planet, Helper.getPlanetRepresentation(planet, game));
            } else {
                button = Button.secondary(prefix + "rider_planet;" + planet + "_" + rider,
                    Helper.getPlanetRepresentation(planet, game));
            }
            planetOutcomeButtons.add(button);
        }
        return planetOutcomeButtons;
    }

    public static List<Button> getPlayerOutcomeButtons(Game game, String rider, String prefix, String planetRes) {
        List<Button> playerOutcomeButtons = new ArrayList<>();

        for (Player player : game.getRealPlayers()) {
            String faction = player.getFaction();
            Button button;
            if (!game.isFowMode() && !faction.contains("franken")) {
                if (rider != null) {
                    if (planetRes != null) {
                        button = Button.secondary(planetRes + "_" + faction + "_" + rider, " ");
                    } else {
                        button = Button.secondary(prefix + "rider_player;" + faction + "_" + rider, " ");
                    }
                } else {
                    button = Button.secondary(prefix + "_" + faction, " ");
                }
                String factionEmojiString = player.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
            } else {
                if (rider != null) {
                    if (planetRes != null) {
                        button = Button.secondary(planetRes + "_" + player.getColor() + "_" + rider, " ");
                    } else {
                        button = Button.secondary(prefix + "rider_player;" + player.getColor() + "_" + rider,
                            player.getColor());
                    }
                } else {
                    button = Button.secondary(prefix + "_" + player.getColor(), player.getColor());
                }
            }
            playerOutcomeButtons.add(button);
        }
        return playerOutcomeButtons;
    }

    public static List<Button> getAgendaButtons(String ridername, Game game, String prefix) {
        String agendaDetails = game.getCurrentAgendaInfo().split("_")[1];
        List<Button> outcomeActionRow;
        if (agendaDetails.contains("For")) {
            outcomeActionRow = getForAgainstOutcomeButtons(ridername, prefix);
        } else if (agendaDetails.contains("Player") || agendaDetails.contains("player")) {
            outcomeActionRow = getPlayerOutcomeButtons(game, ridername, prefix, null);
        } else if (agendaDetails.contains("Planet") || agendaDetails.contains("planet")) {
            if (ridername == null) {
                outcomeActionRow = getPlayerOutcomeButtons(game, null, "tiedPlanets_" + prefix, "planetRider");
            } else {
                outcomeActionRow = getPlayerOutcomeButtons(game, ridername, prefix, "planetRider");
            }
        } else if (agendaDetails.contains("Secret") || agendaDetails.contains("secret")) {
            outcomeActionRow = getSecretOutcomeButtons(game, ridername, prefix);
        } else if (agendaDetails.contains("Strategy") || agendaDetails.contains("strategy")) {
            outcomeActionRow = getStrategyOutcomeButtons(ridername, prefix);
        } else if (agendaDetails.contains("unit upgrade")) {
            outcomeActionRow = getUnitUpgradeOutcomeButtons(game, ridername, prefix);
        } else if (agendaDetails.contains("Unit") || agendaDetails.contains("unit")) {
            outcomeActionRow = getUnitOutcomeButtons(game, ridername, prefix);
        } else {
            outcomeActionRow = getLawOutcomeButtons(game, ridername, prefix);
        }

        return outcomeActionRow;

    }

    public static List<Player> getWinningRiders(String winner, Game game, GenericInteractionCreateEvent event) {
        List<Player> winningRs = new ArrayList<>();
        Map<String, String> outcomes = game.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            StringTokenizer vote_info2 = new StringTokenizer(outcomes.get(outcome), ";");
            while (vote_info2.hasMoreTokens()) {
                String specificVote = vote_info2.nextToken();
                String faction = specificVote.substring(0, specificVote.indexOf("_"));
                Player keleres = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                Player player = keleres;
                if (keleres != null && specificVote.contains("Keleres Xxcha Hero")) {
                    int size = getLosingVoters(outcome, game).size();
                    String message = keleres.getRepresentation()
                        + " You have Odlynn Myrr, the Keleres (Xxcha) Hero, to resolve."
                        + " There were " + size + " players who voted for a different outcome, so you get " + size + " trade good" + (size == 1 ? "s" : "") + " and " + size + " command token" + (size == 1 ? "s" : "") + ".";
                    MessageHelper.sendMessageToChannel(keleres.getCorrectChannel(), message);
                    if (size > 0) {
                        player.setTg(player.getTg() + size);
                        String msg2 = "Gained 3" + Emojis.getTGorNomadCoinEmoji(game) + " (" + (player.getTg() - size)
                            + " -> **" + player.getTg() + "**) ";
                        ButtonHelperAbilities.pillageCheck(player, game);
                        ButtonHelperAgents.resolveArtunoCheck(player, game, size);
                        MessageHelper.sendMessageToChannel(keleres.getCorrectChannel(), msg2);
                        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                        String trueIdentity = player.getRepresentation(true, true);
                        String msg3 = trueIdentity + "! Your current command tokens are " + player.getCCRepresentation()
                            + ". Use buttons to gain command tokens.";
                        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                        MessageHelper.sendMessageToChannelWithButtons(keleres.getCorrectChannel(), msg3, buttons);
                    }

                }
            }
            if (outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");
                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player winningR = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                    if (winningR != null && specificVote.contains("Sanction")) {
                        List<Player> loseFleetPlayers = getWinningVoters(winner, game);
                        for (Player p2 : loseFleetPlayers) {
                            p2.setFleetCC(p2.getFleetCC() - 1);
                            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                                p2.getRepresentation()
                                    + " you lost 1 command token from your fleet pool due to voting for the outcome that was Sanction'd.");
                            ButtonHelper.checkFleetInEveryTile(p2, game, event);
                        }
                    }
                    if (winningR != null && specificVote.contains("Corporate Lobbying")) {
                        List<Player> loseFleetPlayers = getWinningVoters(winner, game);
                        for (Player p2 : loseFleetPlayers) {
                            p2.setTg(p2.getTg() + 2);
                            ButtonHelperAgents.resolveArtunoCheck(p2, game, 2);
                            ButtonHelperAbilities.pillageCheck(p2, game);
                            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                                p2.getRepresentation()
                                    + ", the Corporate Lobbyists have \"gifted\" you gained 2 trade goods, completely coincidental to you voting for the outcome they desired.");
                            ButtonHelper.checkFleetInEveryTile(p2, game, event);
                        }
                    }
                    if (winningR != null && (specificVote.contains("Rider") || winningR.hasAbility("future_sight")
                        || specificVote.contains("Radiance") || specificVote.contains("Tarrock Ability"))) {

                        MessageChannel channel = winningR.getCorrectChannel();
                        String identity = winningR.getRepresentation(true, true);
                        if (specificVote.contains("Galactic Threat Rider")) {
                            List<Player> voters = getWinningVoters(winner, game);
                            List<String> potentialTech = new ArrayList<>();
                            for (Player techGiver : voters) {
                                potentialTech = ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(winningR,
                                    techGiver, potentialTech, game);
                            }
                            MessageHelper.sendMessageToChannelWithButtons(channel,
                                identity + " resolve Galactic Threat using the buttons.",
                                ButtonHelperAbilities.getButtonsForPossibleTechForNekro(winningR, potentialTech,
                                    game));
                        }
                        if (specificVote.contains("Technology Rider") && !winningR.hasAbility("propagation")) {

                            MessageHelper.sendMessageToChannelWithButtons(channel,
                                identity + " resolve Technology Rider by using the button to research a technology.",
                                List.of(Buttons.GET_A_TECH));
                        }
                        if (specificVote.contains("Schematics Rider")) {

                            MessageHelper.sendMessageToChannelWithButtons(channel,
                                identity + " resolve Schematics Rider by using the button to gain the pre-selected technology.",
                                List.of(Buttons.GET_A_TECH));
                        }
                        if (specificVote.contains("Leadership Rider")
                            || (specificVote.contains("Technology Rider") && winningR.hasAbility("propagation"))) {
                            List<Button> buttons = ButtonHelper.getGainCCButtons(winningR);
                            String message = identity + "! Your current command tokens are " + winningR.getCCRepresentation()
                                + ". Use buttons to gain command tokens.";
                            game.setStoredValue("originalCCsFor" + winningR.getFaction(),
                                winningR.getCCRepresentation());
                            MessageHelper.sendMessageToChannel(channel,
                                identity + " resolve Leadership Rider by using the button to get 3 command tokens,");
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Keleres Rider")) {
                            int currentTG = winningR.getTg();
                            winningR.setTg(currentTG + 2);
                            String message = "";
                            boolean scheming = winningR.hasAbility("scheming");
                            if (winningR.hasAbility("autonetic_memory")) {
                                ButtonHelperAbilities.autoneticMemoryStep1(game, winningR, 1);
                            } else {
                                game.drawActionCard(winningR.getUserID());

                                if (scheming) {
                                    game.drawActionCard(winningR.getUserID());
                                    MessageHelper.sendMessageToChannelWithButtons(winningR.getCardsInfoThread(),
                                        winningR.getRepresentation(true, true) + " use buttons to discard",
                                        ACInfo.getDiscardActionCardButtons(game, winningR, false));
                                }
                                ButtonHelper.checkACLimit(game, event, winningR);
                                ACInfo.sendActionCardInfo(game, winningR, event);
                            }

                            StringBuilder sb = new StringBuilder(identity);
                            sb.append("due to having a winning **Keleres Rider**, you have been given");
                            if (scheming) {
                                sb.append(" 2 ").append(Emojis.ActionCard).append(Emojis.ActionCard)
                                    .append(" action cards (due to your **Scheming** ability; discard buttons sent to thread)");
                            } else {
                                sb.append(" 1 ").append(Emojis.ActionCard).append(" action card");
                            }
                            sb.append(" and 2 ").append(Emojis.getTGorNomadCoinEmoji(game))
                                .append(" trade goods (").append(currentTG).append(" -> ").append(winningR.getTg())
                                .append(")");
                            MessageHelper.sendMessageToChannel(channel, sb.toString());
                            ButtonHelperAbilities.pillageCheck(winningR, game);
                            ButtonHelperAgents.resolveArtunoCheck(winningR, game, 2);
                        }
                        if (specificVote.contains("Politics Rider")) {
                            int amount = 3;

                            if (winningR.hasAbility("autonetic_memory")) {
                                ButtonHelperAbilities.autoneticMemoryStep1(game, winningR, 3);
                            } else {
                                game.drawActionCard(winningR.getUserID());
                                game.drawActionCard(winningR.getUserID());
                                game.drawActionCard(winningR.getUserID());
                                ButtonHelper.checkACLimit(game, event, winningR);
                                ACInfo.sendActionCardInfo(game, winningR, event);
                            }
                            if (winningR.hasAbility("scheming")) {
                                amount = 4;
                                game.drawActionCard(winningR.getUserID());
                                MessageHelper.sendMessageToChannelWithButtons(winningR.getCardsInfoThread(),
                                    winningR.getRepresentation(true, true) + " use buttons to discard.",
                                    ACInfo.getDiscardActionCardButtons(game, winningR, false));
                            }

                            game.setSpeaker(winningR.getUserID());
                            MessageHelper.sendMessageToChannel(channel,
                                identity + " due to having a winning **Politics Rider**, you have been given "
                                    + amount + " action cards and the speaker token.");
                        }
                        if (specificVote.contains("Diplomacy Rider")) {
                            String message = identity
                                + " You have a Diplomacy Rider to resolve. Click the name of the planet in the system you wish lock down with this Rider.";
                            List<Button> buttons = Helper.getPlanetSystemDiploButtons(event, winningR, game, true,
                                null);
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Construction Rider")) {
                            String message = identity
                                + " You have a Construction Rider to resolve. Click the name of the planet you wish to put your space dock on.";
                            List<Button> buttons = Helper.getPlanetPlaceUnitButtons(winningR, game, "sd",
                                "place");
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Warfare Rider")) {
                            String message = identity
                                + " You have a Warfare Rider to resolve. Select the system where you wish to put the dreadnought.";
                            List<Button> buttons = Helper.getTileWithShipsPlaceUnitButtons(winningR, game,
                                "dreadnought", "placeOneNDone_skipbuild");
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Armament Rider")) {
                            String message = identity
                                + " You have a Armament Rider to resolve. Select the system in which you wish to produce 2 units each with cost 4 or less.";

                            List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, winningR, UnitType.Spacedock,
                                UnitType.CabalSpacedock);
                            List<Button> buttons = new ArrayList<>();
                            for (Tile tile : tiles) {
                                Button starTile = Button.success("umbatTile_" + tile.getPosition(),
                                    tile.getRepresentationForButtons(game, winningR));
                                buttons.add(starTile);
                            }
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }

                        if (specificVote.contains("Trade Rider")) {
                            int cTG = winningR.getTg();
                            winningR.setTg(cTG + 5);
                            MessageHelper.sendMessageToChannel(channel,
                                identity + " due to having a winning Trade Rider, you have been given 5 trade goods (" + cTG
                                    + "->" + winningR.getTg() + ")");
                            ButtonHelperAbilities.pillageCheck(winningR, game);
                            ButtonHelperAgents.resolveArtunoCheck(winningR, game, 5);
                        }
                        if (specificVote.contains("Relic Rider")) {
                            MessageHelper.sendMessageToChannel(channel,
                                identity + " due to having a winning Relic Rider, you have gained a relic");
                            DrawRelic.drawRelicAndNotify(winningR, event, game);
                        }
                        if (specificVote.contains("Radiance")) {
                            List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, winningR,
                                UnitType.Mech);
                            ButtonHelperFactionSpecific.resolveEdynAgendaStuffStep1(winningR, game, tiles);
                        }
                        if (specificVote.contains("Tarrock Ability")) {
                            Player player = winningR;
                            String message = player.getFactionEmoji() + " Drew Secret Objective.";
                            game.drawSecretObjective(player.getUserID());
                            if (player.hasAbility("plausible_deniability")) {
                                game.drawSecretObjective(player.getUserID());
                                message = message + " Drew a second secret objective due to Plausible Deniability.";
                            }
                            SOInfo.sendSecretObjectiveInfo(game, player, event);
                            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                        }
                        if (specificVote.contains("Kyro Rider")) {
                            Player player = winningR;
                            String message = player.getRepresentation(true, true)
                                + " Click the names of the planet you wish to drop 3 infantry on";
                            List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game,
                                "3gf", "placeOneNDone_skipbuild"));
                            MessageHelper.sendMessageToChannelWithButtons(
                                player.getCorrectChannel(), message, buttons);
                        }
                        if (specificVote.contains("Edyn Rider")) {
                            List<Tile> tiles = new ArrayList<>();
                            for (Tile tile : game.getTileMap().values()) {
                                if (FoWHelper.playerHasUnitsInSystem(winningR, tile)) {
                                    tiles.add(tile);
                                }
                            }
                            ButtonHelperFactionSpecific.resolveEdynAgendaStuffStep1(winningR, game, tiles);
                        }
                        if (specificVote.contains(Constants.IMPERIAL_RIDER)) {
                            String msg = identity + " due to having a winning Imperial Rider, you have scored a victory point.\n";
                            int poIndex;
                            poIndex = game.addCustomPO(Constants.IMPERIAL_RIDER, 1);
                            msg = msg + "Custom public objective Imperial Rider has been added.\n";
                            game.scorePublicObjective(winningR.getUserID(), poIndex);
                            msg = msg + winningR.getRepresentation() + " scored Imperial Rider\n";
                            MessageHelper.sendMessageToChannel(channel, msg);
                            Helper.checkEndGame(game, winningR);

                        }
                        if (!winningRs.contains(winningR)) {
                            winningRs.add(winningR);
                        }

                    }

                }
            }
        }
        return winningRs;
    }

    public static List<Player> getRiders(Game game) {
        List<Player> riders = new ArrayList<>();

        Map<String, String> outcomes = game.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");

            while (vote_info.hasMoreTokens()) {
                String specificVote = vote_info.nextToken();
                String faction = specificVote.substring(0, specificVote.indexOf("_"));
                String vote = specificVote.substring(specificVote.indexOf("_") + 1);
                if (vote.contains("Rider") || vote.contains("Sanction")) {
                    Player rider = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                    if (rider != null) {
                        riders.add(rider);
                    }
                }

            }

        }
        return riders;
    }

    public static List<Player> getLosers(String winner, Game game) {
        List<Player> losers = new ArrayList<>();
        Map<String, String> outcomes = game.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            if (!outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");
                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player loser = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                    if (loser != null && !losers.contains(loser)) {
                        losers.add(loser);
                    }
                }
            }
        }
        return losers;
    }

    public static List<Player> getWinningVoters(String winner, Game game) {
        List<Player> losers = new ArrayList<>();
        Map<String, String> outcomes = game.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            if (outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");

                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player loser = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                    if (loser != null && !specificVote.contains("Rider") && !specificVote.contains("Sanction") && !specificVote.contains("Ability")) {
                        if (!losers.contains(loser)) {
                            losers.add(loser);
                        }

                    }
                }
            }
        }
        return losers;
    }

    public static List<Player> getLosingVoters(String winner, Game game) {
        List<Player> losers = new ArrayList<>();
        Map<String, String> outcomes = game.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            if (!outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");

                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player loser = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                    if (loser != null) {
                        if (!losers.contains(loser) && !specificVote.contains("Rider")
                            && !specificVote.contains("Sanction") && !specificVote.contains("Ability")) {
                            losers.add(loser);
                        }

                    }
                }
            }
        }
        return losers;
    }

    public static List<Player> getPlayersWithMostPoints(Game game) {
        List<Player> losers = new ArrayList<>();
        int most = 0;
        for (Player p : game.getRealPlayers()) {
            if (p.getTotalVictoryPoints() > most) {
                most = p.getTotalVictoryPoints();
            }
        }
        for (Player p : game.getRealPlayers()) {
            if (p.getTotalVictoryPoints() == most) {
                losers.add(p);
            }
        }
        return losers;
    }

    public static List<Player> getPlayersWithLeastPoints(Game game) {
        List<Player> losers = new ArrayList<>();
        int least = 20;
        for (Player p : game.getRealPlayers()) {
            if (p.getTotalVictoryPoints() < least) {
                least = p.getTotalVictoryPoints();
            }
        }
        for (Player p : game.getRealPlayers()) {
            if (p.getTotalVictoryPoints() == least) {
                losers.add(p);
            }
        }
        return losers;
    }

    public static int[] getVoteTotal(Player player, Game game) {
        int hasXxchaAlliance = game.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander") ? 1 : 0;
        int hasXxchaHero = player.hasLeaderUnlocked("xxchahero") ? 1 : 0;
        int voteCount = getTotalVoteCount(game, player);

        // Check if Player only has additional votes but not any "normal" votes, if so,
        // they can't vote
        if (getVoteCountFromPlanets(game, player) == 0) {
            voteCount = 0;
        }

        if (game.getLaws() != null && (game.getLaws().containsKey("rep_govt")
            || game.getLaws().containsKey("absol_government"))) {
            voteCount = 1;
            if (game.getLaws().containsKey("absol_government") && player.controlsMecatol(false)) {
                voteCount = 2;
            }
        }

        if ("nekro".equals(player.getFaction()) && hasXxchaAlliance == 0) {
            voteCount = 0;
        }
        List<Player> riders = getRiders(game);
        if (riders.contains(player)) {
            if (hasXxchaAlliance == 0) {
                voteCount = 0;
            }
        }

        if (hasXxchaAlliance == 0
            && (game.getStoredValue("AssassinatedReps").contains(player.getFaction())
                || game.getStoredValue("PublicExecution").contains(player.getFaction()))) {
            voteCount = 0;
        }

        return new int[] { voteCount, hasXxchaHero, hasXxchaAlliance };
    }

    public static List<Player> getVotingOrder(Game game) {
        List<Player> orderList = new ArrayList<>(game.getPlayers().values().stream()
            .filter(Player::isRealPlayer)
            .toList());
        String speakerName = game.getSpeaker();
        Optional<Player> optSpeaker = orderList.stream()
            .filter(player -> player.getUserID().equals(speakerName))
            .findFirst();

        if (optSpeaker.isPresent()) {
            int rotationDistance = orderList.size() - orderList.indexOf(optSpeaker.get()) - 1;
            Collections.rotate(orderList, rotationDistance);
        }
        if (game.isReverseSpeakerOrder()) {
            Collections.reverse(orderList);
            if (optSpeaker.isPresent()) {
                int rotationDistance = orderList.size() - orderList.indexOf(optSpeaker.get()) - 1;
                Collections.rotate(orderList, rotationDistance);
            }
        }
        if (game.isHasHackElectionBeenPlayed()) {
            Collections.reverse(orderList);
            if (optSpeaker.isPresent()) {
                int rotationDistance = orderList.size() - orderList.indexOf(optSpeaker.get()) - 1;
                Collections.rotate(orderList, rotationDistance);
            }
        }

        // Check if Argent Flight is in the game - if it is, put it at the front of the
        // vote list.
        Optional<Player> argentPlayer = orderList.stream()
            .filter(player -> player.getFaction() != null && player.hasAbility("zeal")).findFirst();
        if (argentPlayer.isPresent()) {
            orderList.remove(argentPlayer.orElse(null));
            orderList.add(0, argentPlayer.get());
        }
        String conspiratorsFaction = game.getStoredValue("conspiratorsFaction");
        if (!conspiratorsFaction.isEmpty()) {
            Player rhodun = game.getPlayerFromColorOrFaction(conspiratorsFaction);
            Optional<Player> speaker = orderList.stream()
                .filter(player -> player.getFaction() != null && game.getSpeaker().equals(player.getUserID()))
                .findFirst();
            if (speaker.isPresent() && rhodun != null) {
                orderList.remove(rhodun);
                orderList.add(orderList.indexOf(speaker.get()) + 1, rhodun);
            }
        }

        // Check if Player has Edyn Mandate faction tech - if it is, put it at the end
        // of the vote list.
        Optional<Player> edynPlayer = orderList.stream()
            .filter(player -> player.getFaction() != null && player.hasTech("dsedyny")).findFirst();
        if (edynPlayer.isPresent()) {
            orderList.remove(edynPlayer.orElse(null));
            orderList.add(edynPlayer.get());
        }
        return orderList;
    }

    public static Player getNextInLine(Player player1, List<Player> votingOrder, Game game) {
        boolean foundPlayer = false;
        if (player1 == null) {
            for (int x = 0; x < 6; x++) {
                if (x < votingOrder.size()) {
                    Player player = votingOrder.get(x);
                    if (player == null) {
                        BotLogger.log("`AgendaHelper.getNextInLine` Hit a null player in game " + game.getName());
                        return null;
                    }

                    if (player.isRealPlayer()) {
                        return player;
                    } else {
                        BotLogger.log("`AgendaHelper.getNextInLine` Hit a notRealPlayer player in game "
                            + game.getName() + " on player " + player.getUserName());
                    }
                }
            }
            return null;

        }
        for (Player player2 : votingOrder) {
            if (player2 == null || player2.isDummy()) {
                continue;
            }
            if (foundPlayer && player2.isRealPlayer()) {
                return player2;
            }
            if (player1.getColor().equalsIgnoreCase(player2.getColor())) {
                foundPlayer = true;
            }
        }

        return player1;
    }

    public static List<Button> getPlanetButtons(GenericInteractionCreateEvent event, Player player, Game game) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        int[] voteInfo = getVoteTotal(player, game);
        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        for (String planet : planets) {
            PlanetModel planetModel = Mapper.getPlanet(planet);
            int voteAmount = 0;
            Planet p = planetsInfo.get(planet);
            if (p == null) {
                continue;
            }
            voteAmount += p.getInfluence();
            if (voteInfo[2] != 0) {
                voteAmount += 1;
            }
            if (voteInfo[1] != 0) {
                voteAmount += p.getResources();
            }
            String planetNameProper = planet;
            if (planetModel.getName() != null) {
                planetNameProper = planetModel.getName();
            } else {
                BotLogger.log(
                    event.getChannel().getAsMention() + " TEMP BOTLOG: A bad PlanetModel was found for planet: "
                        + planet + " - using the planet id instead of the model name");
            }

            if (voteAmount != 0) {
                Emoji emoji = Emoji.fromFormatted(Emojis.getPlanetEmoji(planet));
                Button button = Button.secondary("exhaust_" + planet, planetNameProper + " (" + voteAmount + ")");
                button = button.withEmoji(emoji);
                planetButtons.add(button);
            }
        }
        if (player.hasAbility("zeal")) {
            int numPlayers = 0;
            for (Player player_ : game.getPlayers().values()) {
                if (player_.isRealPlayer())
                    numPlayers++;
            }
            Button button = Button.primary("exhaust_argent", "Special Argent Votes (" + numPlayers + ")")
                .withEmoji(Emoji.fromFormatted(Emojis.Argent));
            planetButtons.add(button);
        }
        if (player.hasTechReady("pi") || player.hasTechReady("absol_pi")) {
            Button button = Button.primary("exhaust_predictive", "Use Predictive Intelligence Votes (3)")
                .withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
            planetButtons.add(button);
        }
        return planetButtons;
    }

    public static void checkForPoliticalSecret(Game game) {
        for (Player player : game.getRealPlayers()) {
            if (!player.getPromissoryNotes().containsKey(player.getColor() + "_ps")
                && player.getPromissoryNotesOwned().contains(player.getColor() + "_ps")) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation()
                    + " this is a reminder that you don't currently hold your Political Secret, and thus may wish to wait until the holder indicates \"No Whens\" before you do any \"after\"s.");
            }
        }
    }

    public static void exhaustForVotes(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String thing = buttonID.replace("exhaustForVotes_", "");
        if (!thing.contains("hacan") && !thing.contains("kyro") && !thing.contains("allPlanets")) {
            player.addSpentThing(thing);
            if (thing.contains("planet_")) {
                String planet = thing.replace("planet_", "");
                player.exhaustPlanet(planet);
                String planetName = planet;
                UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
                if (uH != null) {
                    if (uH.getTokenList().contains("attachment_arcane_citadel.png")) {
                        Tile tile = game.getTileFromPlanet(planetName);
                        String msg = player.getRepresentation() + " added 1 infantry to " + planetName
                            + " due to the Arcane Citadel.";
                        new AddUnits().unitParsing(event, player.getColor(), tile, "1 infantry " + planetName, game);
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                    }
                }
            }
            if (thing.contains("dsghotg")) {
                player.exhaustTech("dsghotg");
            }
            if (thing.contains("predictive")) {
                game.setStoredValue("riskedPredictive",
                    game.getStoredValue("riskedPredictive") + player.getFaction());
            }
            ButtonHelper.deleteTheOneButton(event);
        } else {
            if (thing.contains("hacan")) {
                player.setTg(player.getTg() - 1);
                player.increaseTgsSpentThisWindow(1);
                if (player.getTg() < 1) {
                    ButtonHelper.deleteTheOneButton(event);
                }
            }
            if (thing.contains("kyro")) {
                player.increaseInfantrySpentThisWindow(1);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getRepresentation() + " please remove 1 infantry to pay for Silas Deriga, the Kyro commander.",
                    ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "infantry"));
            }
            if (thing.contains("allPlanets")) {
                List<String> unexhaustedPs = new ArrayList<>();
                unexhaustedPs.addAll(player.getReadiedPlanets());
                for (String planet : unexhaustedPs) {
                    if (getSpecificPlanetsVoteWorth(player, game, planet) > 0) {
                        player.addSpentThing("planet_" + planet);
                        player.exhaustPlanet(planet);
                    }
                    String planetName = planet;
                    UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
                    if (uH != null) {
                        if (uH.getTokenList().contains("attachment_arcane_citadel.png")) {
                            Tile tile = game.getTileFromPlanet(planetName);
                            String msg = player.getRepresentation() + " added 1 infantry to " + planetName
                                + " due to the Arcane Citadel";
                            new AddUnits().unitParsing(event, player.getColor(), tile, "1 infantry " + planetName, game);
                            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                        }
                    }

                }
                ButtonHelper.deleteTheOneButton(event);
            }
        }
        String editedMessage = Helper.buildSpentThingsMessageForVoting(player, game, false);
        editedMessage = getSummaryOfVotes(game, true) + "\n\n" + editedMessage;
        event.getMessage().editMessage(editedMessage).queue();

    }

    public static int getSpecificPlanetsVoteWorth(Player player, Game game, String planet) {
        int voteAmount = 0;
        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        int[] voteInfo = getVoteTotal(player, game);
        Planet p = planetsInfo.get(planet);
        if (p == null) {
            if (planet.contains("custodia") || planet.contains("ghoti")) {
                return 3;
            }
        }
        voteAmount += p.getInfluence();
        if (voteInfo[1] != 0) {
            voteAmount += p.getResources();
        }
        if (player.hasAbility("lithoids")) {
            voteAmount = p.getResources();
        }

        if (player.hasAbility("biophobic")) {
            voteAmount = 1;
        }

        if (voteInfo[2] != 0) {
            voteAmount += 1;
        }
        if (player.hasAbility("policy_the_people_control")) {
            if (p.getPlanetTypes().contains(Constants.CULTURAL)) {
                voteAmount += 2;
            }
        }
        for (String attachment : p.getTokenList()) {
            if (attachment.contains("council_preserve")) {
                voteAmount += 5;
            }
        }

        return voteAmount;

    }

    public static List<Button> getPlanetButtonsVersion2(GenericInteractionCreateEvent event, Player player,
        Game game) {
        player.resetSpentThings();
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        int totalPlanetVotes = 0;
        for (String planet : planets) {
            int voteAmount = getSpecificPlanetsVoteWorth(player, game, planet);
            String planetNameProper = planet;
            PlanetModel planetModel = Mapper.getPlanet(planet);
            if (planetModel.getName() != null) {
                planetNameProper = planetModel.getName();
            } else {
                BotLogger.log(
                    event.getChannel().getAsMention() + " TEMP BOTLOG: A bad PlanetModel was found for planet: "
                        + planet + " - using the planet id instead of the model name");
            }
            if (voteAmount != 0) {
                Emoji emoji = Emoji.fromFormatted(Emojis.getPlanetEmoji(planet));
                Button button = Button.secondary("exhaustForVotes_planet_" + planet,
                    planetNameProper + " (" + voteAmount + ")");
                button = button.withEmoji(emoji);
                planetButtons.add(button);
            }
            totalPlanetVotes = totalPlanetVotes + voteAmount;
        }
        if (player.hasAbility("zeal")) {
            int numPlayers = 0;
            for (Player player_ : game.getPlayers().values()) {
                if (player_.isRealPlayer())
                    numPlayers++;
            }
            Button button = Button
                .primary("exhaustForVotes_zeal_" + numPlayers, "Special Argent Votes (" + numPlayers + ")")
                .withEmoji(Emoji.fromFormatted(Emojis.Argent));
            planetButtons.add(button);
        }
        if (player.hasTechReady("pi") || player.hasTechReady("absol_pi")) {
            Button button = Button.primary("exhaustForVotes_predictive_3", "Use Predictive Intelligence For 3 Votes")
                .withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
            planetButtons.add(button);
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "hacancommander")) {
            Button button = Button.secondary("exhaustForVotes_hacanCommanderTg", "Spend Trade Goods For 2 Votes Each")
                .withEmoji(Emoji.fromFormatted(Emojis.Hacan));
            planetButtons.add(button);
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "kyrocommander")) {
            Button button = Button.secondary("exhaustForVotes_kyrocommanderInf", "Kill Infantry For 1 Vote Each")
                .withEmoji(Emoji.fromFormatted(Emojis.blex));
            planetButtons.add(button);
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "augerscommander")) {
            int count = player.getTechs().size() / 2;
            Button button = Button
                .secondary("exhaustForVotes_augerscommander_" + count, "Use Augurs Commander For " + count + " Vote" + (count == 1 ? "" : "s"))
                .withEmoji(Emoji.fromFormatted(Emojis.augers));
            planetButtons.add(button);
        }
        if (CollectionUtils.containsAny(player.getRelics(),
            List.of("absol_shardofthethrone1", "absol_shardofthethrone2", "absol_shardofthethrone3"))) {
            int count = player.getRelics().stream().filter(s -> s.contains("absol_shardofthethrone")).toList().size(); // +2
                                                                                                                       // votes
                                                                                                                       // per
                                                                                                                       // Absol
                                                                                                                       // shard
            int shardVotes = 2 * count;
            Button button = Button
                .secondary("exhaustForVotes_absolShard_" + shardVotes, "Use Shard of the Throne Votes (" + shardVotes + ")")
                .withEmoji(Emoji.fromFormatted(Emojis.Absol));
            planetButtons.add(button);
        }
        // Absol's Syncretone - +1 vote for each neighbour
        if (player.hasRelicReady("absol_syncretone")) {
            int count = player.getNeighbourCount();
            Button button = Button
                .secondary("exhaustForVotes_absolsyncretone_" + count, "Use Syncretone Votes (" + count + ")")
                .withEmoji(Emoji.fromFormatted(Emojis.Absol));
            planetButtons.add(button);
        }

        // Ghoti Wayfarer Tech
        if (player.hasTechReady("dsghotg")) {
            int fleetCC = player.getFleetCC();
            Button button = Button
                .secondary("exhaustForVotes_dsghotg_" + fleetCC, "Use Ghoti Technology Votes (" + fleetCC + ")")
                .withEmoji(Emoji.fromFormatted(Emojis.ghoti));
            planetButtons.add(button);
        }
        Button button = Button.secondary("exhaustForVotes_allPlanets_" + totalPlanetVotes,
            "Exhaust All Voting Planets (" + totalPlanetVotes + ")");
        planetButtons.add(button);
        planetButtons.add(Button.danger(player.getFinsFactionCheckerPrefix() + "proceedToFinalizingVote",
            "Done exhausting planets."));
        return planetButtons;
    }

    public static void refreshAgenda(Game game, ButtonInteractionEvent event) {
        String agendaDetails = game.getCurrentAgendaInfo();
        String agendaID = "CL";
        if (StringUtils.countMatches(agendaDetails, "_") > 2) {
            if (StringUtils.countMatches(agendaDetails, "_") > 3) {
                agendaID = StringUtils.substringAfter(agendaDetails, agendaDetails.split("_")[2] + "_");
            } else {
                agendaID = agendaDetails.split("_")[3];
            }
        }
        AgendaModel agendaModel = Mapper.getAgenda(agendaID);
        MessageEmbed agendaEmbed = agendaModel.getRepresentationEmbed();

        String revealMessage = "Refreshed Agenda";
        MessageHelper.sendMessageToChannelWithEmbed(game.getMainGameChannel(), revealMessage, agendaEmbed);
        List<Button> proceedButtons = new ArrayList<>();
        String msg = "Buttons for various things";

        ListVoteCount.turnOrder(event, game, game.getMainGameChannel());

        proceedButtons.add(Button.danger("proceedToVoting", "Skip Waiting And Start The Voting For Everyone"));
        proceedButtons.add(Button.primary("transaction", "Transaction"));
        proceedButtons.add(Button.danger("eraseMyVote", "Erase My Vote And Have Me Vote Again"));
        proceedButtons.add(Button.danger("eraseMyRiders", "Erase My Riders"));
        proceedButtons.add(Button.secondary("refreshAgenda", "Refresh Agenda"));

        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), msg, proceedButtons);
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), getSummaryOfVotes(game, true));
    }

    public static void proceedToFinalizingVote(Game game, Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        String votes = Helper.buildSpentThingsMessageForVoting(player, game, true);
        String msg = Helper.buildSpentThingsMessageForVoting(player, game, false) + "\n\n"
            + player.getRepresentation() + " you are currently voting " + votes
            + " vote" + (votes.equals("1") ? "" : "s") + ". You may confirm this or you may modify this number if the bot missed something.";
        if (player.getPromissoryNotesInPlayArea().contains("blood_pact")) {
            msg = msg + " Any Blood Pact votes will be automatically added.";
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success(player.getFinsFactionCheckerPrefix() + "resolveAgendaVote_" + votes,
            "Vote " + votes + " vote" + (votes.equals("1") ? "" : "s")));
        buttons.add(Button.primary(player.getFinsFactionCheckerPrefix() + "distinguished_" + votes, "Modify Votes"));
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
    }

    public static void resolveAbsolAgainstChecksNBalances(Game game) {
        StringBuilder message = new StringBuilder();
        // Integer poIndex = game.addCustomPO("Points Scored Prior to Absol C&B
        // Wipe", 1);
        // message.append("Custom PO 'Points Scored Prior to Absol C&B Wipe' has been
        // added and people have scored it. \n");

        // game.scorePublicObjective(playerWL.getUserID(), poIndex);
        for (Player player : game.getRealPlayers()) {
            int currentPoints = player.getPublicVictoryPoints(false) + player.getSecretVictoryPoints();

            Integer poIndex = game.addCustomPO(
                StringUtils.capitalize(player.getColor()) + " Pre-C&B Wipe Objectives", currentPoints);
            message.append(StringUtils.capitalize(player.getColor()
                    + " Pre-C&B Wipe Objectives has been added as a custom public objective, and has been scored, worth "
                    + currentPoints + " victory points. \n"));
            game.scorePublicObjective(player.getUserID(), poIndex);
            Map<String, List<String>> playerScoredPublics = game.getScoredPublicObjectives();
            for (Entry<String, List<String>> scoredPublic : playerScoredPublics.entrySet()) {
                if (Mapper.getPublicObjectivesStage1().containsKey(scoredPublic.getKey())
                    || Mapper.getPublicObjectivesStage2().containsKey(scoredPublic.getKey())) {
                    if (scoredPublic.getValue().contains(player.getUserID())) {
                        game.unscorePublicObjective(player.getUserID(), scoredPublic.getKey());
                    }
                }
            }
            List<Integer> scoredSOs = new ArrayList<>(player.getSecretsScored().values());
            for (int soID : scoredSOs) {
                game.unscoreAndShuffleSecretObjective(player.getUserID(), soID);
            }

        }
        message.append("All secret objectives have been returned to the deck and all public objectives scored have been cleared.\n");

        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message.toString());
    }

    public static List<Button> getPlanetRefreshButtons(GenericInteractionCreateEvent event, Player player,
        Game game) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getExhaustedPlanets());
        for (String planet : planets) {
            String buttonID = "refresh_" + planet + "_" + player.getFaction();
            String buttonText = Helper.getPlanetRepresentation(planet, game);
            Button button = Button.success(buttonID, buttonText);
            planetButtons.add(button);
        }

        return planetButtons;
    }

    public static void eraseVotesOfFaction(Game game, String faction) {
        if (game.getCurrentAgendaVotes().keySet().isEmpty()) {
            return;
        }
        Map<String, String> outcomes = new HashMap<>(game.getCurrentAgendaVotes());
        String voteSumm;

        for (String outcome : outcomes.keySet()) {
            voteSumm = "";
            StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");

            StringBuilder voteSummBuilder = new StringBuilder(voteSumm);
            while (vote_info.hasMoreTokens()) {
                String specificVote = vote_info.nextToken();
                String faction2 = specificVote.substring(0, specificVote.indexOf("_"));
                String vote = specificVote.substring(specificVote.indexOf("_") + 1);
                if (vote.contains("Rider") || vote.contains("Sanction") || vote.contains("Radiance")
                    || vote.contains("Unity Algorithm") || vote.contains("Tarrock") || vote.contains("Hero")) {
                    voteSummBuilder.append(";").append(specificVote);
                } else if (!faction2.equals(faction)) {
                    voteSummBuilder.append(";").append(specificVote);
                }
            }
            voteSumm = voteSummBuilder.toString();
            if ("".equalsIgnoreCase(voteSumm)) {
                game.removeOutcomeAgendaVote(outcome);
            } else {
                game.setCurrentAgendaVote(outcome, voteSumm);
            }
        }
    }

    public static String getWinner(Game game) {
        StringBuilder winner = new StringBuilder();
        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        int currentHighest = -1;
        for (String outcome : outcomes.keySet()) {
            int totalVotes = 0;
            StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");
            while (vote_info.hasMoreTokens()) {
                String specificVote = vote_info.nextToken();
                String vote = specificVote.split("_")[1];
                if (NumberUtils.isDigits(vote)) {
                    totalVotes += Integer.parseInt(vote);
                }
            }
            int votes = totalVotes;
            if (votes >= currentHighest && votes != 0) {
                if (votes == currentHighest) {
                    winner.append("*").append(outcome);
                } else {
                    currentHighest = votes;
                    winner = new StringBuilder(outcome);
                }
            }
        }
        return winner.toString();
    }

    public static String getSummaryOfVotes(Game game, boolean capitalize) {
        String summary;
        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        String agendaDetails = game.getCurrentAgendaInfo();
        String agendaName;
        if (StringUtils.countMatches(agendaDetails, "_") > 2)
            if (StringUtils.countMatches(agendaDetails, "_") > 3) {
                agendaName = Mapper.getAgendaTitleNoCap(
                    StringUtils.substringAfter(agendaDetails, agendaDetails.split("_")[2] + "_"));
            } else {
                agendaName = Mapper.getAgendaTitleNoCap(agendaDetails.split("_")[3]);
            }
        else {
            agendaName = "Not Currently Tracked";
        }

        if (outcomes.keySet().isEmpty()) {
            summary = "# Agenda Name: " + agendaName + "\nNo current Riders or votes have been cast yet.";
        } else {
            StringBuilder summaryBuilder = new StringBuilder(
                "# Agenda Name: " + agendaName + "\nCurrent status of votes and outcomes is: \n");
            for (String outcome : outcomes.keySet()) {
                if (StringUtils.countMatches(game.getCurrentAgendaInfo(), "_") > 1) {
                    agendaDetails = game.getCurrentAgendaInfo().split("_")[1];
                } else {
                    agendaDetails = game.getCurrentAgendaInfo();
                }

                int totalVotes = 0;
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");
                String outcomeSummary;
                if (agendaDetails.contains("Secret") || agendaDetails.contains("secret")) {
                    outcome = Mapper.getSecretObjectivesJustNames().get(outcome);
                } else if (agendaDetails.contains("Elect Law") || agendaDetails.contains("elect law")) {
                    outcome = Mapper.getAgendaTitleNoCap(outcome);
                } else if (agendaDetails.toLowerCase().contains("unit upgrade")) {
                    outcome = Mapper.getTech(outcome).getName();
                } else if (capitalize) {
                    outcome = StringUtils.capitalize(outcome);
                }
                StringBuilder outcomeSummaryBuilder = new StringBuilder();
                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    if (capitalize) {
                        Player p2 = game.getPlayerFromColorOrFaction(faction);
                        faction = Emojis.getFactionIconFromDiscord(faction);
                        if (p2 != null) {
                            faction = p2.getFactionEmoji();
                        }
                        if (game.isFowMode()) {
                            faction = "Someone";
                        }
                        String vote = specificVote.substring(specificVote.indexOf("_") + 1);
                        if (NumberUtils.isDigits(vote)) {
                            totalVotes += Integer.parseInt(vote);
                        }
                        outcomeSummaryBuilder.append(faction).append("-").append(vote).append(", ");
                    } else {
                        String vote = specificVote.substring(specificVote.indexOf("_") + 1);
                        if (NumberUtils.isDigits(vote)) {
                            totalVotes += Integer.parseInt(vote);
                            outcomeSummaryBuilder.append(faction).append(" voted ").append(vote).append(" votes. ");
                        } else {
                            outcomeSummaryBuilder.append(faction).append(" cast a ").append(vote).append(". ");
                        }

                    }

                }
                outcomeSummary = outcomeSummaryBuilder.toString();
                if (capitalize) {
                    if (outcomeSummary.length() > 2) {
                        outcomeSummary = outcomeSummary.substring(0, outcomeSummary.length() - 2);
                    }

                    if (!game.isFowMode() && game.getCurrentAgendaInfo().contains("Elect Player")) {
                        summaryBuilder.append(Emojis.getFactionIconFromDiscord(outcome.toLowerCase())).append(" ")
                            .append(outcome).append(": ").append(totalVotes).append(". (").append(outcomeSummary)
                            .append(")\n");

                    } else if (!game.isHomebrewSCMode()
                        && game.getCurrentAgendaInfo().contains("Elect Strategy Card")) {
                        summaryBuilder.append(Emojis.getSCEmojiFromInteger(Integer.parseInt(outcome))).append(" ")
                            .append(outcome).append(": ").append(totalVotes).append(". (").append(outcomeSummary)
                            .append(")\n");
                    } else {
                        summaryBuilder.append(outcome).append(": ").append(totalVotes).append(". (")
                            .append(outcomeSummary).append(")\n");

                    }
                } else {
                    summaryBuilder.append(outcome).append(": Total votes ").append(totalVotes).append(". ")
                        .append(outcomeSummary).append("\n");
                }

            }
            summary = summaryBuilder.toString();
        }
        return summary;
    }

    public static void resolveMinisterOfWar(Game game, Player player, ButtonInteractionEvent event) {
        ButtonHelper.deleteTheOneButton(event);
        boolean success = game.removeLaw(game.getLaws().get("minister_war"));
        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Minister of War Law removed");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Law ID not found");
        }
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "ministerOfWar");
        MessageChannel channel = player.getCorrectChannel();
        MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
    }

    public static String getPlayerVoteText(Game game, Player player) {
        StringBuilder sb = new StringBuilder();
        int voteCount = getVoteCountFromPlanets(game, player);
        Map<String, Integer> additionalVotes = getAdditionalVotesFromOtherSources(game, player);
        String additionalVotesText = getAdditionalVotesFromOtherSourcesText(additionalVotes);

        if (game.isFowMode()) {
            sb.append(" vote count: **???**");
            return sb.toString();
        } else if (player.hasAbility("galactic_threat")
            && !game.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
            sb.append(" NOT VOTING (Galactic Threat)");
            return sb.toString();
        } else if (player.hasLeaderUnlocked("xxchahero")) {
            sb.append(" vote count: **" + Emojis.ResInf + " ").append(voteCount);
        } else if (player.hasAbility("lithoids")) { // Vote with planet resources, not influence
            sb.append(" vote count: **" + Emojis.resources + " ").append(voteCount);
        } else if (player.hasAbility("biophobic")) {
            sb.append(" vote count: **" + Emojis.SemLor + " ").append(voteCount);
        } else {
            sb.append(" vote count: **" + Emojis.influence + " ").append(voteCount);
        }
        if (!additionalVotesText.isEmpty()) {
            int additionalVoteCount = additionalVotes.values().stream().mapToInt(Integer::intValue).sum();
            if (additionalVoteCount > 0) {
                sb.append(" + ").append(additionalVoteCount).append("** additional votes from: ");
            } else {
                sb.append("**");
            }
            sb.append("  ").append(additionalVotesText);
        } else
            sb.append("**");
        if (game.getLaws().containsKey("rep_govt") || game.getLaws().containsKey("absol_government")) {
            sb = new StringBuilder();
            if (game.getLaws().containsKey("absol_government") && player.controlsMecatol(false)) {
                sb.append(" vote count (Representative Government while controlling Mecatol Rex): **2**");
            } else {
                sb.append(" vote count (Representative Government): **1**");
            }

        }
        return sb.toString();
    }

    public static int getTotalVoteCount(Game game, Player player) {
        return getVoteCountFromPlanets(game, player) + getAdditionalVotesFromOtherSources(game, player)
            .values().stream().mapToInt(Integer::intValue).sum();
    }

    public static int getVoteCountFromPlanets(Game game, Player player) {
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        int baseResourceCount = planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(Planet.class::cast)
            .mapToInt(Planet::getResources).sum();
        int baseInfluenceCount = planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(Planet.class::cast)
            .mapToInt(Planet::getInfluence).sum();
        int voteCount = baseInfluenceCount; // default

        // NEKRO unless XXCHA ALLIANCE
        if (player.hasAbility("galactic_threat")
            && !game.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
            return 0;
        }

        // KHRASK
        if (player.hasAbility("lithoids")) { // Vote with planet resources, not influence
            voteCount = baseResourceCount;
        }

        // ZELIAN PURIFIER BIOPHOBIC ABILITY - 1 planet = 1 vote
        if (player.hasAbility("biophobic")) {
            voteCount = planets.size();
        }

        // XXCHA
        if (player.hasLeaderUnlocked("xxchahero")) {
            voteCount = baseResourceCount + baseInfluenceCount;
        }

        // Xxcha Alliance - +1 vote for each planet
        if (game.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
            int readyPlanetCount = planets.size();
            voteCount += readyPlanetCount;
        }

        // Olradin "Control" - +2 votes per cultural planet
        if (player.hasAbility("policy_the_people_control")) {
            List<String> cultPlanets = new ArrayList<>();
            for (String cplanet : planets) {
                Planet p = game.getPlanetsInfo().get(cplanet);
                if (p == null) continue;
                if (p.getPlanetTypes().contains(Constants.CULTURAL)) {
                    cultPlanets.add(cplanet);
                }
            }
            voteCount += (cultPlanets.size() * 2);
        }
        UnitHolder p;
        for (String cplanet : planets) {
            p = ButtonHelper.getUnitHolderFromPlanetName(cplanet, game);
            if (p == null)
                continue;
            for (String attachment : p.getTokenList()) {
                if (attachment.contains("council_preserve")) {
                    voteCount += 5;
                }
            }
        }

        return voteCount;
    }

    public static String getAdditionalVotesFromOtherSourcesText(Map<String, Integer> additionalVotes) {
        StringBuilder sb = new StringBuilder();
        for (Entry<String, Integer> entry : additionalVotes.entrySet()) {
            if (entry.getValue() > 0) {
                sb.append("(+").append(entry.getValue()).append(" for ").append(entry.getKey()).append(")");
            } else {
                sb.append("(").append(entry.getKey()).append(")");
            }
        }
        return sb.toString();
    }

    /**
     * @return (K, V) -> K = additionalVotes / V = text explanation of votes
     */
    public static Map<String, Integer> getAdditionalVotesFromOtherSources(Game game, Player player) {
        Map<String, Integer> additionalVotesAndSources = new LinkedHashMap<>();

        if (getVoteCountFromPlanets(game, player) == 0) {
            return additionalVotesAndSources;
        }
        // Argent Zeal
        if (player.hasAbility("zeal")) {
            long playerCount = game.getPlayers().values().stream().filter(Player::isRealPlayer).count();
            additionalVotesAndSources.put(Emojis.Argent + "Zeal", Math.toIntExact(playerCount));
        }

        // Blood Pact
        if (player.getPromissoryNotesInPlayArea().contains("blood_pact")) {
            additionalVotesAndSources.put(Emojis.Empyrean + Emojis.PN + "Blood Pact", 4);
        }

        // Predictive Intelligence
        if (player.hasTechReady("pi") || player.hasTechReady("absol_pi")) {
            additionalVotesAndSources.put(Emojis.CyberneticTech + "Predictive Intelligence", 3);
        }

        // Xxcha Alliance
        if (game.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
            additionalVotesAndSources.put(Emojis.Xxcha + "Alliance has been counted for", 0);
        }

        // Hacan Alliance
        if (game.playerHasLeaderUnlockedOrAlliance(player, "hacancommander")) {
            additionalVotesAndSources.put(Emojis.Hacan + " Alliance not calculated", 0);
        }
        // Kyro Alliance
        if (game.playerHasLeaderUnlockedOrAlliance(player, "kyrocommander")) {
            additionalVotesAndSources.put(Emojis.blex + " Alliance not calculated", 0);
        }

        // Absol Shard of the Throne
        if (CollectionUtils.containsAny(player.getRelics(),
            List.of("absol_shardofthethrone1", "absol_shardofthethrone2", "absol_shardofthethrone3"))) {
            int count = player.getRelics().stream().filter(s -> s.contains("absol_shardofthethrone")).toList().size(); // +2
                                                                                                                       // votes
                                                                                                                       // per
                                                                                                                       // Absol
                                                                                                                       // shard
            int shardVotes = 2 * count;
            additionalVotesAndSources.put("(" + count + "x)" + Emojis.Relic + "Shard of the Throne" + Emojis.Absol,
                shardVotes);
        }

        // Absol's Syncretone - +1 vote for each neighbour
        if (player.hasRelicReady("absol_syncretone")) {
            int count = player.getNeighbourCount();
            additionalVotesAndSources.put(Emojis.Relic + "Syncretone", count);
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "augerscommander")) {
            int count = player.getTechs().size() / 2;
            additionalVotesAndSources.put(Emojis.augers + "Augers Commander", count);
        }

        // Ghoti Wayfarer Tech
        if (player.hasTechReady("dsghotg")) {
            int fleetCC = player.getFleetCC();
            additionalVotesAndSources.put(Emojis.BioticTech + "Exhaust Networked Command", fleetCC);
        }

        return additionalVotesAndSources;
    }

    public static EmbedBuilder buildAgendaEmbed(AgendaModel agenda) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(agenda.getSource().emoji() + " " + agenda.getName());

        StringBuilder desc = new StringBuilder("**").append(agenda.getType()).append(":** *").append(agenda.getTarget())
            .append("*\n");
        desc.append("> ").append(agenda.getText1().replace("For:", "**For:**")).append("\n");
        desc.append("> ").append(agenda.getText2().replace("Against:", "**Against:**"));
        eb.setDescription(desc.toString());
        eb.setFooter(agenda.footnote());

        return eb;
    }
}
