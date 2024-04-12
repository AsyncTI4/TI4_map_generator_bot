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
import java.util.StringTokenizer;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.Buttons;
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

    public static void resolveColonialRedTarget(Game activeGame, String buttonID, ButtonInteractionEvent event) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p2 == null)
            return;
        String planet = buttonID.split("_")[2];
        Tile tile = activeGame.getTileFromPlanet(planet);
        if (tile != null) {
            new AddUnits().unitParsing(event, p2.getColor(), tile, "1 inf " + planet, activeGame);
        }
        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
            "1 " + p2.getColor() + " infantry was added to " + planet);
        event.getMessage().delete().queue();
    }

    public static void resolveAgenda(Game activeGame, String buttonID, ButtonInteractionEvent event,
        MessageChannel actionsChannel) {
        actionsChannel = activeGame.getMainGameChannel();
        String winner = buttonID.substring(buttonID.indexOf("_") + 1);
        String agendaid = activeGame.getCurrentAgendaInfo().split("_")[2];
        int aID;
        if ("CL".equalsIgnoreCase(agendaid)) {
            String id2 = activeGame.revealAgenda(false);
            Map<String, Integer> discardAgendas = activeGame.getDiscardAgendas();
            AgendaModel agendaDetails = Mapper.getAgenda(id2);
            String agendaName = agendaDetails.getName();
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "# The hidden agenda was " + agendaName
                + "! You can find it added as a law or in the discard.");
            aID = discardAgendas.get(id2);
        } else {
            aID = Integer.parseInt(agendaid);
        }
        Map<String, Integer> discardAgendas = activeGame.getDiscardAgendas();
        String agID = "";
        List<Player> predictiveCheck = getLosingVoters(winner, activeGame);
        for (Player playerWL : predictiveCheck) {
            if (activeGame.getStoredValue("riskedPredictive").contains(playerWL.getFaction())
                && playerWL.hasTech("pi")) {
                playerWL.exhaustTech("pi");
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(playerWL, activeGame),
                    playerWL.getRepresentation()
                        + " predictive was exhausted since you voted the way that lost while using it");
            }
        }
        activeGame.setStoredValue("riskedPredictive", "");
        for (Map.Entry<String, Integer> agendas : discardAgendas.entrySet()) {
            if (agendas.getValue().equals(aID)) {
                agID = agendas.getKey();
                break;
            }
        }

        if (activeGame.getCurrentAgendaInfo().startsWith("Law")) {
            if (activeGame.getCurrentAgendaInfo().contains("Player")) {
                Player player2 = activeGame.getPlayerFromColorOrFaction(winner);
                if (player2 != null) {
                    activeGame.addLaw(aID, winner);
                }
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    "# Added Law with " + winner + " as the elected!");
                if ("censure".equalsIgnoreCase(agID) || "absool_censure".equalsIgnoreCase(agID)) {
                    StringBuilder message = new StringBuilder();
                    Integer poIndex = activeGame.addCustomPO("Political Censure", 1);
                    message.append("Custom PO 'Political Censure' has been added.\n");
                    activeGame.scorePublicObjective(player2.getUserID(), poIndex);
                    if (!activeGame.isFoWMode()) {
                        message.append(player2.getRepresentation()).append(" scored 'Political Censure'\n");
                    }
                    MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message.toString());
                    Helper.checkEndGame(activeGame, player2);
                }
                if ("warrant".equalsIgnoreCase(agID)) {
                    player2.setSearchWarrant();
                    activeGame.drawSecretObjective(player2.getUserID());
                    activeGame.drawSecretObjective(player2.getUserID());
                    if (player2.hasAbility("plausible_deniability")) {
                        activeGame.drawSecretObjective(player2.getUserID());
                    }
                    SOInfo.sendSecretObjectiveInfo(activeGame, player2, event);
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Drew elected 2 SOs and set their SO info as public");
                }
            } else {
                if ("for".equalsIgnoreCase(winner)) {
                    activeGame.addLaw(aID, null);
                    MessageHelper.sendMessageToChannel(event.getChannel(), activeGame.getPing() + " Added law to map!");
                }
                if ("regulations".equalsIgnoreCase(agID)) {
                    if ("for".equalsIgnoreCase(winner)) {
                        for (Player playerB : activeGame.getRealPlayers()) {
                            if (playerB.getFleetCC() > 4) {
                                playerB.setFleetCC(4);
                            }
                        }
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                            activeGame.getPing() + " Reduced people's fleets to 4 if they had more than that");
                    } else {
                        for (Player playerB : activeGame.getRealPlayers()) {
                            playerB.setFleetCC(playerB.getFleetCC() + 1);
                        }
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                            activeGame.getPing() + " Gave everyone 1 extra fleet CC");

                    }
                }
                if ("absol_checks".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        resolveAbsolAgainstChecksNBalances(activeGame);
                    }
                }
                if ("schematics".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        for (Player player : activeGame.getRealPlayers()) {
                            if (player.getTechs().contains("ws") || player.getTechs().contains("pws2")
                                || player.getTechs().contains("dsrohdws")) {
                                new DiscardACRandom().discardRandomAC(event, activeGame, player, player.getAc());
                            }
                        }
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                            "Discarded the ACs of those with warsun tech");
                    }
                }
                if ("defense_act".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        for (Player player : activeGame.getRealPlayers()) {
                            if (ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Pds)
                                .size() > 0) {
                                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                                    player.getRepresentation() + " remove a PDS", ButtonHelperModifyUnits
                                        .getRemoveThisTypeOfUnitButton(player, activeGame, "pds"));
                            }
                        }
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                            "Sent buttons for each person to remove a PDS");
                    }
                }
                if ("wormhole_recon".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        for (Tile tile : ButtonHelper.getAllWormholeTiles(activeGame)) {
                            for (Player player : activeGame.getRealPlayers()) {
                                if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                                    AddCC.addCC(event, player.getColor(), tile);
                                }
                            }
                        }
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                            "Added CCs to all tiles with wormholes and players ships");
                    }
                }
                if ("travel_ban".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        for (Tile tile : ButtonHelper.getAllWormholeTiles(activeGame)) {
                            for (Player player : activeGame.getRealPlayers()) {
                                for (String adjPos : FoWHelper.getAdjacentTilesAndNotThisTile(activeGame,
                                    tile.getPosition(), player, false)) {
                                    Tile tile2 = activeGame.getTileByPosition(adjPos);
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
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                            "Removed all PDS in or adjacent to a wormhole");
                    }
                }
                if ("shared_research".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        for (Player player : activeGame.getRealPlayers()) {
                            Tile tile = activeGame.getTile(AliasHandler.resolveTile(player.getFaction()));
                            if (player.hasAbility("mobile_command") && ButtonHelper
                                .getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Flagship).size() > 0) {
                                tile = ButtonHelper
                                    .getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Flagship).get(0);
                            }
                            if (tile == null) {
                                tile = ButtonHelper.getTileOfPlanetWithNoTrait(player, activeGame);
                            }
                            if (tile == null) {
                                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                                    "Could not find a HS, sorry bro");
                            }
                            AddCC.addCC(event, player.getColor(), tile);
                        }
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                            "Added player's CCs to their HS");
                    }
                }
                if ("conventions".equalsIgnoreCase(agID)) {
                    List<Player> winOrLose;
                    if (!"for".equalsIgnoreCase(winner)) {
                        winOrLose = getWinningVoters(winner, activeGame);
                        for (Player playerWL : winOrLose) {
                            new DiscardACRandom().discardRandomAC(event, activeGame, playerWL, playerWL.getAc());
                        }
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                            "Discarded the ACs of those who voted against");
                    }
                }
                if ("rep_govt".equalsIgnoreCase(agID)) {
                    List<Player> winOrLose;
                    if (!"for".equalsIgnoreCase(winner)) {
                        winOrLose = getWinningVoters(winner, activeGame);
                        for (Player playerWL : winOrLose) {
                            activeGame.setStoredValue("agendaRepGov",
                                activeGame.getStoredValue("agendaRepGov") + playerWL.getFaction());
                        }
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                            "Will exhaust cultural planets of all players who voted against at start of next strat phase");
                    }
                }
                if ("articles_war".equalsIgnoreCase(agID)) {
                    List<Player> winOrLose;
                    if (!"for".equalsIgnoreCase(winner)) {
                        winOrLose = getLosingVoters(winner, activeGame);
                        for (Player playerWL : winOrLose) {
                            playerWL.setTg(playerWL.getTg() + 3);
                            ButtonHelperAbilities.pillageCheck(playerWL, activeGame);
                            ButtonHelperAgents.resolveArtunoCheck(playerWL, activeGame, 3);
                        }
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                            "Added 3tg to those who voted for");
                    }
                }
                if ("nexus".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        Tile tile = activeGame.getTileFromPlanet("mr");
                        if (tile != null) {
                            String tokenFilename = Mapper.getTokenID("gamma");
                            tile.addToken(tokenFilename, Constants.SPACE);
                            MessageHelper.sendMessageToChannel(actionsChannel, "Added Gamma to Rex");
                        }
                    }
                }
                if ("sanctions".equalsIgnoreCase(agID)) {
                    if (!"for".equalsIgnoreCase(winner)) {
                        for (Player playerWL : activeGame.getRealPlayers()) {
                            new DiscardACRandom().discardRandomAC(event, activeGame, playerWL, 1);
                        }
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                            "Discarded 1 random AC of each player");
                    } else {
                        for (Player playerWL : activeGame.getRealPlayers()) {
                            ButtonHelper.checkACLimit(activeGame, event, playerWL);
                        }
                    }
                }
                if (activeGame.getCurrentAgendaInfo().contains("Secret")) {
                    activeGame.addLaw(aID, winner);
                    Player playerWithSO = null;

                    for (Map.Entry<String, Player> playerEntry : activeGame.getPlayers().entrySet()) {
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
                        MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found");
                        return;
                    }
                    if (winner.isEmpty()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), "Can make just Scored SO to Public");
                        return;
                    }
                    activeGame.addToSoToPoList(winner);
                    Integer poIndex = activeGame.addCustomPO(winner, 1);
                    activeGame.scorePublicObjective(playerWithSO.getUserID(), poIndex);

                    String sb = "**Public Objective added from Secret:**" + "\n" +
                        "(" + poIndex + ") " + "\n" +
                        Mapper.getSecretObjectivesJustNames().get(winner) + "\n";
                    MessageHelper.sendMessageToChannel(event.getChannel(), sb);

                    SOInfo.sendSecretObjectiveInfo(activeGame, playerWithSO, event);

                }
            }
            if (activeGame.getLaws().size() > 0) {
                for (Player player : activeGame.getRealPlayers()) {
                    if (player.getLeaderIDs().contains("edyncommander") && !player.hasLeaderUnlocked("edyncommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "edyn", event);
                    }
                }
            }
        } else {
            if (activeGame.getCurrentAgendaInfo().contains("Player")) {
                Player player2 = activeGame.getPlayerFromColorOrFaction(winner);
                if ("secret".equalsIgnoreCase(agID)) {
                    String message = "Drew Secret Objective for the elected player";
                    activeGame.drawSecretObjective(player2.getUserID());
                    if (player2.hasAbility("plausible_deniability")) {
                        activeGame.drawSecretObjective(player2.getUserID());
                        message = message + ". Drew a second SO due to plausible deniability";
                    }
                    SOInfo.sendSecretObjectiveInfo(activeGame, player2, event);
                    MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message);
                }
                if ("standardization".equalsIgnoreCase(agID)) {
                    player2.setTacticalCC(3);
                    player2.setStrategicCC(2);
                    player2.setFleetCC(3);
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Set " + ButtonHelper.getIdentOrColor(player2, activeGame) + " CCs to 3/3/2");
                }
                if ("minister_antiquities".equalsIgnoreCase(agID)) {
                    DrawRelic.drawRelicAndNotify(player2, event, activeGame);
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Drew relic for " + ButtonHelper.getIdentOrColor(player2, activeGame));
                }
                if ("execution".equalsIgnoreCase(agID)) {
                    String message = "Discarded elected player's ACs and marked them as unable to vote on the next agenda";
                    new DiscardACRandom().discardRandomAC(event, activeGame, player2, player2.getAc());
                    activeGame.setStoredValue("PublicExecution", player2.getFaction());
                    if (activeGame.getSpeaker().equalsIgnoreCase(player2.getUserID())) {
                        message = message + ". Also passed the speaker token";
                        boolean foundSpeaker = false;
                        boolean assignedSpeaker = false;
                        for (Player p4 : activeGame.getRealPlayers()) {
                            if (assignedSpeaker) {
                                break;
                            }
                            if (foundSpeaker) {
                                activeGame.setSpeaker(p4.getUserID());
                                assignedSpeaker = true;
                                break;
                            }
                            if (p4 == player2) {
                                foundSpeaker = true;
                            }
                        }
                        for (Player p4 : activeGame.getRealPlayers()) {
                            if (assignedSpeaker) {
                                break;
                            }
                            if (foundSpeaker) {
                                activeGame.setSpeaker(p4.getUserID());
                                assignedSpeaker = true;
                                break;
                            }
                            if (p4 == player2) {
                                foundSpeaker = true;
                            }
                        }
                    }
                    MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message);
                }
                if ("grant_reallocation".equalsIgnoreCase(agID)) {
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player2, activeGame),
                        player2.getRepresentation()
                            + " Use the button to get a tech. You will need to lose any fleet CC manually",
                        List.of(Buttons.GET_A_TECH));
                }

            } // "abolishment" || "absol_abolishment", "miscount" || "absol_miscount"
            if ("abolishment".equalsIgnoreCase(agID) || "absol_abolishment".equalsIgnoreCase(agID)) {
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                    "# Abolished the " + Mapper.getAgendaTitleNoCap(winner) + " law");
                activeGame.removeLaw(winner);
            }
            if ("redistribution".equalsIgnoreCase(agID)) {
                for (Player player : activeGame.getRealPlayers()) {
                    if (player.getPlanets().contains(winner.toLowerCase())) {
                        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(winner, activeGame);
                        int count = 0;
                        Player cabalMechOwner = Helper.getPlayerFromUnit(activeGame, "cabal_mech");
                        boolean cabalMech = cabalMechOwner != null
                            && uH.getUnitCount(UnitType.Mech, cabalMechOwner.getColor()) > 0
                            && !activeGame.getLaws().containsKey("articles_war");
                        Player cabalFSOwner = Helper.getPlayerFromUnit(activeGame, "cabal_flagship");
                        boolean cabalFS = cabalFSOwner != null && ButtonHelper.doesPlayerHaveFSHere("cabal_flagship",
                            cabalFSOwner, activeGame.getTileFromPlanet(winner));

                        if (uH.getUnitCount(UnitType.Mech, player.getColor()) > 0) {
                            if (player.hasTech("sar")) {
                                for (int x = 0; x < uH.getUnitCount(UnitType.Mech, player.getColor()); x++) {
                                    player.setTg(player.getTg() + 1);
                                    MessageHelper.sendMessageToChannel(
                                        ButtonHelper.getCorrectChannel(player, activeGame),
                                        player.getRepresentation() + " you gained 1tg (" + (player.getTg() - 1)
                                            + "->" + player.getTg()
                                            + ") from 1 of your mechs dying while you own Self-Assembly Routines. This is not an optional gain.");
                                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                                }
                                ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
                            }
                            if (cabalFS) {
                                ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabalFSOwner,
                                    uH.getUnitCount(UnitType.Mech, player.getColor()), "mech", event);
                            }
                            count = count + uH.getUnitCount(UnitType.Mech, player.getColor());
                            uH.removeUnit(Mapper.getUnitKey(AliasHandler.resolveUnit("mech"), player.getColorID()),
                                uH.getUnitCount(UnitType.Mech, player.getColor()));
                        }
                        if (uH.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
                            if ((player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2"))) {
                                ButtonHelperFactionSpecific.offerMahactInfButtons(player, activeGame);
                            }
                            if (player.hasInf2Tech()) {
                                ButtonHelper.resolveInfantryDeath(activeGame, player,
                                    uH.getUnitCount(UnitType.Infantry, player.getColor()));
                            }
                            if (cabalFS || cabalMech) {
                                ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabalFSOwner,
                                    uH.getUnitCount(UnitType.Infantry, player.getColor()), "infantry", event);
                            }
                            count = count + uH.getUnitCount(UnitType.Infantry, player.getColor());
                            uH.removeUnit(Mapper.getUnitKey(AliasHandler.resolveUnit("infantry"), player.getColorID()),
                                uH.getUnitCount(UnitType.Infantry, player.getColor()));
                        }
                        uH.removeAllUnits(player.getColor());
                        List<Button> buttons = new ArrayList<>();
                        for (Player player2 : getPlayersWithLeastPoints(activeGame)) {
                            if (activeGame.isFoWMode()) {
                                buttons.add(Button.success("colonialRedTarget_" + player2.getFaction() + "_" + winner,
                                    "" + player2.getColor()));
                            } else {
                                buttons.add(Button.success("colonialRedTarget_" + player2.getFaction() + "_" + winner,
                                    "" + player2.getFaction()));
                            }
                        }
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                            player.getRepresentation(true, true) + " choose who you want to get the planet",
                            buttons);

                        MessageHelper.sendMessageToChannel(actionsChannel,
                            "Removed all units and gave player the option of who to give the planet to");

                    }
                }

            }
            if ("disarmamament".equalsIgnoreCase(agID)) {
                for (Player player : activeGame.getRealPlayers()) {
                    if (player.getPlanets().contains(winner.toLowerCase())) {
                        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(winner, activeGame);
                        int count = 0;
                        Player cabalMechOwner = Helper.getPlayerFromUnit(activeGame, "cabal_mech");
                        boolean cabalMech = cabalMechOwner != null
                            && uH.getUnitCount(UnitType.Mech, cabalMechOwner.getColor()) > 0
                            && !activeGame.getLaws().containsKey("articles_war");
                        Player cabalFSOwner = Helper.getPlayerFromUnit(activeGame, "cabal_flagship");
                        boolean cabalFS = cabalFSOwner != null && ButtonHelper.doesPlayerHaveFSHere("cabal_flagship",
                            cabalFSOwner, activeGame.getTileFromPlanet(winner));

                        if (uH.getUnitCount(UnitType.Mech, player.getColor()) > 0) {
                            if (player.hasTech("sar")) {
                                for (int x = 0; x < uH.getUnitCount(UnitType.Mech, player.getColor()); x++) {
                                    player.setTg(player.getTg() + 1);
                                    MessageHelper.sendMessageToChannel(
                                        ButtonHelper.getCorrectChannel(player, activeGame),
                                        player.getRepresentation() + " you gained 1tg (" + (player.getTg() - 1)
                                            + "->" + player.getTg()
                                            + ") from 1 of your mechs dying while you own Self-Assembly Routines. This is not an optional gain.");
                                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                                }
                                ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
                            }
                            if (cabalFS) {
                                ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabalFSOwner,
                                    uH.getUnitCount(UnitType.Mech, player.getColor()), "mech", event);
                            }
                            count = count + uH.getUnitCount(UnitType.Mech, player.getColor());
                            uH.removeUnit(Mapper.getUnitKey(AliasHandler.resolveUnit("mech"), player.getColorID()),
                                uH.getUnitCount(UnitType.Mech, player.getColor()));
                        }
                        if (uH.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
                            if ((player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2"))) {
                                ButtonHelperFactionSpecific.offerMahactInfButtons(player, activeGame);
                            }
                            if (player.hasInf2Tech()) {
                                ButtonHelper.resolveInfantryDeath(activeGame, player,
                                    uH.getUnitCount(UnitType.Infantry, player.getColor()));
                            }
                            if (cabalFS || cabalMech) {
                                ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabalFSOwner,
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
                            ButtonHelperAgents.resolveArtunoCheck(player, activeGame, count);
                            ButtonHelperAbilities.pillageCheck(player, activeGame);
                        }
                        MessageHelper.sendMessageToChannel(actionsChannel,
                            "Removed all units and gave player appropriate amount of tg");

                    }
                }
            }
            if ("miscount".equalsIgnoreCase(agID) || "absol_miscount".equalsIgnoreCase(agID)) {
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                    "# Repealed the " + Mapper.getAgendaTitleNoCap(winner)
                        + " law and will now reveal it for the purposes of revoting. It is technically still in effect");
            }
            if ("cladenstine".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    for (Player player : activeGame.getRealPlayers()) {
                        String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";
                        Button loseTactic = Button.danger(finsFactionCheckerPrefix + "decrease_tactic_cc",
                            "Lose 1 Tactic CC");
                        Button loseFleet = Button.danger(finsFactionCheckerPrefix + "decrease_fleet_cc",
                            "Lose 1 Fleet CC");
                        Button loseStrat = Button.danger(finsFactionCheckerPrefix + "decrease_strategy_cc",
                            "Lose 1 Strategy CC");
                        Button DoneGainingCC = Button.danger(finsFactionCheckerPrefix + "deleteButtons",
                            "Done Losing CCs");
                        List<Button> buttons = List.of(loseTactic, loseFleet, loseStrat, DoneGainingCC);
                        String message2 = player.getRepresentation(true, true) + "! Your current CCs are "
                            + player.getCCRepresentation() + ". Use buttons to lose CCs";
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message2,
                            buttons);
                        activeGame.setStoredValue("originalCCsFor" + player.getFaction(),
                            player.getCCRepresentation());
                    }
                } else {
                    for (Player player : activeGame.getRealPlayers()) {
                        String message = player.getRepresentation() + " you lose a fleet CC";
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message);
                        player.setFleetCC(player.getFleetCC() - 1);
                        ButtonHelper.checkFleetInEveryTile(player, activeGame, event);
                    }
                }
            }
            if ("arms_reduction".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    for (Player player : activeGame.getRealPlayers()) {
                        if (ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "cruiser", false) > 4) {
                            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                                player.getRepresentation() + " remove excess cruisers", ButtonHelperModifyUnits
                                    .getRemoveThisTypeOfUnitButton(player, activeGame, "cruiser"));
                        }
                        if (ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "dreadnought", false) > 2) {
                            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                                player.getRepresentation() + " remove excess dreads", ButtonHelperModifyUnits
                                    .getRemoveThisTypeOfUnitButton(player, activeGame, "dreadnought"));
                        }
                    }
                    MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                        "Sent buttons for each person to remove excess dreads/cruisers");
                } else {
                    activeGame.setStoredValue("agendaArmsReduction", "true");
                    MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                        "# Will exhaust all tech skip planets at the start of next Strategy phase");

                }
            }
            if ("rearmament".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    for (Player player : activeGame.getRealPlayers()) {
                        String message = player.getRepresentation()
                            + " Use buttons to drop a mech on a Home System Planet";
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message,
                            Helper.getPlanetPlaceUnitButtons(player, activeGame, "mech",
                                "placeOneNDone_skipbuild"));
                    }
                } else {
                    for (Player player : activeGame.getRealPlayers()) {
                        for (Tile tile : activeGame.getTileMap().values()) {
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
                    MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "Removed all mechs");
                }

            }
            if ("wormhole_research".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    WormholeResearchFor.doResearch(event, activeGame);
                } else {
                    List<Player> players = getWinningVoters(winner, activeGame);
                    for (Player player : players) {
                        String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";
                        Button loseTactic = Button.danger(finsFactionCheckerPrefix + "decrease_tactic_cc",
                            "Lose 1 Tactic CC");
                        Button loseFleet = Button.danger(finsFactionCheckerPrefix + "decrease_fleet_cc",
                            "Lose 1 Fleet CC");
                        Button loseStrat = Button.danger(finsFactionCheckerPrefix + "decrease_strategy_cc",
                            "Lose 1 Strategy CC");
                        Button DoneGainingCC = Button.danger(finsFactionCheckerPrefix + "deleteButtons",
                            "Done Losing CCs");
                        List<Button> buttons = List.of(loseTactic, loseFleet, loseStrat, DoneGainingCC);
                        String message2 = player.getRepresentation(true, true) + "! Your current CCs are "
                            + player.getCCRepresentation() + ". Use buttons to lose CCs";
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message2,
                            buttons);
                        activeGame.setStoredValue("originalCCsFor" + player.getFaction(),
                            player.getCCRepresentation());
                    }
                }
            }
            if ("mutiny".equalsIgnoreCase(agID)) {
                List<Player> winOrLose;
                StringBuilder message = new StringBuilder();
                Integer poIndex;
                if ("for".equalsIgnoreCase(winner)) {
                    winOrLose = getWinningVoters(winner, activeGame);
                    poIndex = activeGame.addCustomPO("Mutiny", 1);

                } else {
                    winOrLose = getLosingVoters(winner, activeGame);
                    poIndex = activeGame.addCustomPO("Mutiny", -1);
                }
                message.append("Custom PO 'Mutiny' has been added.\n");
                for (Player playerWL : winOrLose) {
                    if (playerWL.getTotalVictoryPoints() < 1 && !"for".equalsIgnoreCase(winner)) {
                        continue;
                    }
                    activeGame.scorePublicObjective(playerWL.getUserID(), poIndex);
                    if (!activeGame.isFoWMode()) {
                        message.append(playerWL.getRepresentation()).append(" scored 'Mutiny'\n");
                    }
                    Helper.checkEndGame(activeGame, playerWL);
                    if (playerWL.getTotalVictoryPoints() >= activeGame.getVp()) {
                        break;
                    }

                }
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message.toString());
            }
            if ("constitution".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    List<String> laws = new ArrayList<>(activeGame.getLaws().keySet());
                    for (String law : laws) {
                        activeGame.removeLaw(law);
                    }
                    activeGame.setStoredValue("agendaConstitution", "true");
                    MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                        "# Removed all laws, will exhaust all home planets at the start of next Strategy phase");
                }
            }
            if ("artifact".equalsIgnoreCase(agID)) {
                TextChannel watchParty = watchPartyChannel(activeGame);
                String watchPartyPing = watchPartyPing(activeGame);
                if (watchParty != null && !activeGame.isFoWMode()) {
                    Tile tile = activeGame.getTileFromPlanet("mr");
                    if (tile != null) {
                        FileUpload systemWithContext = GenerateTile.getInstance().saveImage(activeGame, 1,
                            tile.getPosition(), event);
                        String message = "# Ixthian Artifact has resolved! " + watchPartyPing + "\n"
                            + getSummaryOfVotes(activeGame, true);
                        MessageHelper.sendMessageToChannel(watchParty, message);
                        MessageHelper.sendMessageWithFile(watchParty, systemWithContext,
                            "Surrounding Mecatol Rex In " + activeGame.getName(), false);
                    }
                }
                if ("for".equalsIgnoreCase(winner)) {
                    Button ixthianButton = Button.success("rollIxthian", "Roll Ixthian Artifact")
                        .withEmoji(Emoji.fromFormatted(Emojis.Mecatol));
                    String msg = activeGame.getPing() + "Click this button to roll ixthian artifact! 🥁";
                    MessageHelper.sendMessageToChannelWithButton(actionsChannel, msg, ixthianButton);
                } else {
                    MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                        "Against on Ixthian? Disgraceful");
                }
            }
            if ("seed_empire".equalsIgnoreCase(agID)) {
                List<Player> winOrLose;
                StringBuilder message = new StringBuilder();
                Integer poIndex;
                poIndex = activeGame.addCustomPO("Seed", 1);
                if ("for".equalsIgnoreCase(winner)) {
                    winOrLose = getPlayersWithMostPoints(activeGame);
                } else {
                    winOrLose = getPlayersWithLeastPoints(activeGame);

                }
                message.append("Custom PO 'Seed' has been added.\n");
                for (Player playerWL : winOrLose) {
                    activeGame.scorePublicObjective(playerWL.getUserID(), poIndex);
                    message.append(playerWL.getRepresentation()).append(" scored 'Seed'\n");
                    Helper.checkEndGame(activeGame, playerWL);
                    if (playerWL.getTotalVictoryPoints() >= activeGame.getVp()) {
                        break;
                    }
                }
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message.toString());
            }
            if ("plowshares".equalsIgnoreCase(agID)) {
                if ("for".equalsIgnoreCase(winner)) {
                    for (Player playerB : activeGame.getRealPlayers()) {
                        new SwordsToPlowsharesTGGain().doSwords(playerB, event, activeGame);
                    }
                } else {
                    for (Player playerB : activeGame.getRealPlayers()) {
                        new RiseOfMessiah().doRise(playerB, event, activeGame);
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
                    winOrLose = getLosingVoters(winner, activeGame);
                    for (Player playerWL : winOrLose) {
                        new DiscardACRandom().discardRandomAC(event, activeGame, playerWL, playerWL.getAc());
                    }
                    MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                        "Discarded the ACs of those who voted for");
                } else {
                    winOrLose = getWinningVoters(winner, activeGame);
                    for (Player playerWL : winOrLose) {
                        String message;
                        if (playerWL.hasAbility("autonetic_memory")) {
                            ButtonHelperAbilities.autoneticMemoryStep1(activeGame, playerWL, 2);
                            message = ButtonHelper.getIdent(playerWL) + " Triggered Autonetic Memory Option";
                        } else {
                            activeGame.drawActionCard(playerWL.getUserID());
                            activeGame.drawActionCard(playerWL.getUserID());
                            if (playerWL.hasAbility("scheming")) {
                                activeGame.drawActionCard(playerWL.getUserID());
                                ACInfo.sendActionCardInfo(activeGame, playerWL, event);
                                MessageHelper.sendMessageToChannelWithButtons(playerWL.getCardsInfoThread(),
                                    playerWL.getRepresentation(true, true) + " use buttons to discard",
                                    ACInfo.getDiscardActionCardButtons(activeGame, playerWL, false));
                            } else {
                                ACInfo.sendActionCardInfo(activeGame, playerWL, event);
                            }
                        }

                        if (playerWL.getLeaderIDs().contains("yssarilcommander")
                            && !playerWL.hasLeaderUnlocked("yssarilcommander")) {
                            ButtonHelper.commanderUnlockCheck(playerWL, activeGame, "yssaril", event);
                        }
                        ButtonHelper.checkACLimit(activeGame, event, playerWL);
                    }
                    MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                        "Drew 2 AC for each of the players who voted for");
                }
            }
            if ("economic_equality".equalsIgnoreCase(agID)) {
                int tg = 0;
                if ("for".equalsIgnoreCase(winner)) {
                    for (Player playerB : activeGame.getRealPlayers()) {
                        playerB.setTg(5);
                        ButtonHelperAgents.resolveArtunoCheck(playerB, activeGame, 5);
                        ButtonHelperAbilities.pillageCheck(playerB, activeGame);
                    }
                    tg = 5;
                } else {
                    for (Player playerB : activeGame.getRealPlayers()) {
                        playerB.setTg(0);
                    }
                }
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                    activeGame.getPing() + " Set everyone's tgs to " + tg);
            }
            if ("crisis".equalsIgnoreCase(agID)) {
                if (!activeGame.isHomeBrewSCMode()) {
                    List<Button> scButtons = new ArrayList<>();
                    switch (winner) {
                        case "1" -> {
                            scButtons.add(Button.success("leadershipGenerateCCButtons", "Gain CCs"));
                            scButtons.add(Button.danger("leadershipExhaust", "Exhaust Planets"));
                        }
                        case "2" -> {
                            scButtons.add(Button.success("diploRefresh2", "Ready 2 Planets"));
                        }
                        case "3" -> {
                            scButtons.add(Button.secondary("sc_ac_draw", "Draw 2 Action Cards")
                                .withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                        }
                        case "4" -> {
                            scButtons.add(Button.success("construction_spacedock", "Place A SD")
                                .withEmoji(Emoji.fromFormatted(Emojis.spacedock)));
                            scButtons.add(Button.success("construction_pds", "Place a PDS")
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
                        "You can use this button to resolve the secondary", scButtons);
                }
            }

            if (activeGame.getCurrentAgendaInfo().contains("Law")) {
                // Figure out law
            }
        }
        List<Player> riders = getWinningRiders(winner, activeGame, event);
        List<Player> voters = getWinningVoters(winner, activeGame);
        voters.addAll(riders);
        for (Player player : voters) {
            if (player.getLeaderIDs().contains("florzencommander") && !player.hasLeaderUnlocked("florzencommander")) {
                ButtonHelper.commanderUnlockCheck(player, activeGame, "florzen", event);
            }
        }
        String ridSum = "People had riders to resolve.";
        for (Player rid : riders) {
            String rep = rid.getRepresentation(true, true);
            String message;
            if (rid.hasAbility("future_sight")) {
                message = rep
                    + "You have a rider to resolve or you voted for the correct outcome. Either way a tg has been added to your total due to your future sight ability. ("
                    + rid.getTg() + "-->" + (rid.getTg() + 1) + ")";
                rid.setTg(rid.getTg() + 1);
                ButtonHelperAgents.resolveArtunoCheck(rid, activeGame, 1);
                ButtonHelperAbilities.pillageCheck(rid, activeGame);
            } else {
                message = rep + "You have a rider to resolve";
            }
            if (activeGame.isFoWMode()) {
                MessageHelper.sendPrivateMessageToPlayer(rid, activeGame, message);
            } else {
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message);
            }
        }
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "Sent pings to all those who ridered");
        } else if (riders.size() > 0) {
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), ridSum);
        }
        String resMes = "Resolving vote for " + StringUtils.capitalize(winner) + ".";
        String voteMessage = "Click the buttons for next steps after you're done resolving riders.";
        String agendaCount = activeGame.getStoredValue("agendaCount");
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
            if ("action_deck_2".equals(activeGame.getAcDeckID()) && aCount > 2) {
                String acd2Shenanigans = "This is the window for *Last Minute Deliberations* and *Data Archive*! " + activeGame.getPing();
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), acd2Shenanigans);
            }
        } else {
            activeGame.removeLaw(winner);
            activeGame.putAgendaBackIntoDeckOnTop(winner);
            RevealAgenda.revealAgenda(event, false, activeGame, activeGame.getMainGameChannel());
        }

        event.getMessage().delete().queue();
    }

    @Nullable
    private static String watchPartyPing(Game activeGame) {
        List<Role> roles = AsyncTI4DiscordBot.guildPrimary.getRolesByName("Ixthian Watch Party", true);
        if (!activeGame.isFoWMode() && roles.size() > 0) {
            return roles.get(0).getAsMention();
        }
        return null;
    }

    @Nullable
    private static TextChannel watchPartyChannel(Game activeGame) {
        List<TextChannel> channels = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("ixthian-watch-party", true);
        if (!activeGame.isFoWMode() && channels.size() > 0) {
            return channels.get(0);
        }
        return null;
    }

    private static void sleep() {
        try {
            TimeUnit.SECONDS.sleep(1);
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

    public static void offerEveryonePrepassOnShenanigans(Game activeGame) {
        for (Player player : activeGame.getRealPlayers()) {
            String msg = player.getRepresentation()
                + " you have the option to prepass on agenda shenanigans here. Agenda shenanigans are the action cards known as bribery, deadly plot, and the confounding/confusing legal texts. Feel free not to pre-pass, this is simply an optional way to resolve agendas faster";
            List<Button> buttons = new ArrayList<>();

            buttons.add(Button.success("resolvePreassignment_Pass On Shenanigans", "Pre-pass"));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        }
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

    public static void offerEveryonePreAbstain(Game activeGame) {
        for (Player player : activeGame.getRealPlayers()) {
            int[] voteInfo = getVoteTotal(player, activeGame);
            if (voteInfo[0] < 1) {
                continue;
            }
            String msg = ButtonHelper.getIdent(player)
                + " if you intend to abstain from voting on this agenda, you have the option to preset an abstain here. Feel free not to pre-abstain, this is simply an optional way to resolve agendas faster";
            List<Button> buttons = new ArrayList<>();
            if (player.hasAbility("future_sight")) {
                msg = msg + ". Reminder that you have future sight and may not want to abstain";
            }

            buttons.add(Button.success("resolvePreassignment_Abstain On Agenda", "Pre-abstain"));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        }
    }

    public static void rollIxthian(Game activeGame, boolean publish) {
        String activeGamePing = activeGame.getPing();
        if (publish) {
            TextChannel watchParty = watchPartyChannel(activeGame);
            String watchPartyPing = watchPartyPing(activeGame);
            Message watchPartyMsg = watchParty == null ? null
                : watchParty.sendMessage(drumroll(watchPartyPing, 0)).complete();
            MessageHelper.MessageFunction resolveIxthian = (msg) -> {
                int rand = 10 + ThreadLocalRandom.current().nextInt(5);
                if (ThreadLocalRandom.current().nextInt(10) == 0) {
                    // random chance for a super long wait
                    rand += ThreadLocalRandom.current().nextInt(45);
                }
                sleep();
                for (int i = 1; i <= rand; i++) {
                    msg.editMessage(drumroll(activeGamePing, i)).queue();
                    if (watchPartyMsg != null) {
                        watchPartyMsg.editMessage(drumroll(watchPartyPing, i)).queue();
                    }
                    sleep();
                }
                msg.delete().queue();
                if (watchPartyMsg != null) {
                    watchPartyMsg.delete().queue();
                }
                resolveIxthianRoll(activeGame, true);
            };
            MessageHelper.splitAndSentWithAction(drumroll(activeGamePing, 0), activeGame.getMainGameChannel(),
                resolveIxthian);
        } else {
            MessageHelper.MessageFunction resolveIxthian = (msg) -> {
                int rand = 10 + ThreadLocalRandom.current().nextInt(5);
                if (ThreadLocalRandom.current().nextInt(10) == 0) {
                    // random chance for a super long wait
                    rand += ThreadLocalRandom.current().nextInt(45);
                }
                sleep();
                for (int i = 1; i <= rand; i++) {
                    msg.editMessage(drumroll(activeGamePing, i)).queue();
                    sleep();
                }
                msg.delete().queue();
                resolveIxthianRoll(activeGame, false);
            };
            MessageHelper.splitAndSentWithAction(drumroll(activeGamePing, 0), activeGame.getMainGameChannel(),
                resolveIxthian);
        }

    }

    private static void resolveIxthianRoll(Game activeGame, boolean publish) {
        TextChannel watchParty = watchPartyChannel(activeGame);
        String watchPartyPing = watchPartyPing(activeGame);

        Die d1 = new Die(6);
        String msg = "# Rolled a " + d1.getResult() + " for Ixthian!";
        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), msg);
        if (watchParty != null && publish) {
            String watchMsg = watchPartyPing + " " + activeGame.getName() + " has finished rolling:\n" + msg;
            MessageHelper.sendMessageToChannel(watchParty, watchMsg);
        }
        if (d1.isSuccess() && !activeGame.isFoWMode()) {
            if (Helper.getPlayerFromAbility(activeGame, "propagation") != null) {
                Player player = Helper.getPlayerFromAbility(activeGame, "propagation");
                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                String message2 = player.getRepresentation() + "! Your current CCs are " + player.getCCRepresentation()
                    + ". Use buttons to gain CCs";
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                    message2, buttons);
                activeGame.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
            }
            MessageHelper.sendMessageToChannelWithButton(activeGame.getMainGameChannel(),
                "You can use the button to get your tech", Buttons.GET_A_TECH);
        } else if (!d1.isSuccess() && !activeGame.isFoWMode()) {
            Button modify = Button.secondary("getModifyTiles", "Modify Units");
            MessageHelper.sendMessageToChannelWithButton(activeGame.getMainGameChannel(),
                "Remove units on or adjacent to mecatol please", modify);
        }
    }

    public static void pingMissingPlayers(Game activeGame) {

        List<Player> missingPlayersWhens = ButtonHelper.getPlayersWhoHaventReacted(activeGame.getLatestWhenMsg(),
            activeGame);
        List<Player> missingPlayersAfters = ButtonHelper.getPlayersWhoHaventReacted(activeGame.getLatestAfterMsg(),
            activeGame);
        if (missingPlayersAfters.isEmpty() && missingPlayersWhens.isEmpty()) {
            return;
        }

        String messageWhens = " please indicate no whens";
        String messageAfters = " please indicate no afters";
        if (activeGame.isFoWMode()) {
            for (Player player : missingPlayersWhens) {
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(),
                    player.getRepresentation(true, true) + messageWhens);
            }
            for (Player player : missingPlayersAfters) {
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(),
                    player.getRepresentation(true, true) + messageAfters);
            }
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                "Sent reminder pings to players who have not yet reacted");

        } else {
            StringBuilder messageWhensBuilder = new StringBuilder(" please indicate no whens");
            for (Player player : missingPlayersWhens) {
                messageWhensBuilder.insert(0, player.getRepresentation(true, true));
            }
            messageWhens = messageWhensBuilder.toString();
            if (missingPlayersWhens.size() > 0) {
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), messageWhens);
            }

            StringBuilder messageAftersBuilder = new StringBuilder(" please indicate no afters");
            for (Player player : missingPlayersAfters) {
                messageAftersBuilder.insert(0, player.getRepresentation(true, true));
            }
            messageAfters = messageAftersBuilder.toString();
            if (missingPlayersAfters.size() > 0) {
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), messageAfters);
            }
        }
        Date newTime = new Date();
        activeGame.setLastActivePlayerPing(newTime);
    }

    public static void offerVoteAmounts(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player,
        String ident, String buttonLabel) {
        String outcome = buttonID.substring(buttonID.indexOf("_") + 1);
        String voteMessage = "Chose to vote for " + StringUtils.capitalize(outcome)
            + ". Click buttons for amount of votes";
        activeGame.setLatestOutcomeVotedFor(outcome);
        int maxVotes = getTotalVoteCount(activeGame, player);
        int minVotes = 1;
        if (player.hasAbility("zeal")) {
            minVotes = minVotes + activeGame.getRealPlayers().size();
        }

        if (activeGame.getLaws() != null && (activeGame.getLaws().containsKey("rep_govt")
            || activeGame.getLaws().containsKey("absol_government"))) {
            minVotes = 1;
            maxVotes = 1;
            if (activeGame.getLaws().containsKey("absol_government") && player.getPlanets().contains("mr")) {
                minVotes = 2;
                maxVotes = 2;
            }
        }
        if (maxVotes - minVotes > 20) {
            voteMessage = "Chose to vote for " + StringUtils.capitalize(outcome)
                + ". You have more votes than discord has buttons. Please further specify your desired vote count by clicking the button which contains your desired vote amount (or largest button).";
        }
        voteMessage = voteMessage + "\n" + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame);
        List<Button> voteActionRow = getVoteButtons(minVotes, maxVotes);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
        event.getMessage().delete().queue();
    }

    public static void exhaustPlanetsForVoting(String buttonID, ButtonInteractionEvent event, Game activeGame,
        Player player, String ident, String buttonLabel, String finsFactionCheckerPrefix) {
        String votes = buttonID.substring(buttonID.indexOf("_") + 1);
        String voteMessage = ButtonHelper.getIdent(player) + " Chose to vote " + votes + " votes for "
            + StringUtils.capitalize(activeGame.getLatestOutcomeVotedFor());
        List<Button> voteActionRow = getPlanetButtons(event, player, activeGame);
        int allVotes = getVoteTotal(player, activeGame)[0];
        Button exhausteverything = Button.danger("exhaust_everything_" + allVotes,
            "Exhaust everything (" + allVotes + ")");
        Button concludeExhausting = Button.danger(finsFactionCheckerPrefix + "resolveAgendaVote_" + votes,
            "Done exhausting planets.");
        Button OopsMistake = Button.success("refreshVotes_" + votes, "Ready planets");
        Button OopsMistake2 = Button.success("outcome_" + activeGame.getLatestOutcomeVotedFor(), "Change # of votes");
        voteActionRow.add(exhausteverything);
        voteActionRow.add(concludeExhausting);
        voteActionRow.add(OopsMistake);
        voteActionRow.add(OopsMistake2);
        String voteMessage2 = "Exhaust stuff";
        MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage2, voteActionRow);
        event.getMessage().delete().queue();
    }

    public static void exhaustPlanetsForVotingVersion2(String buttonID, ButtonInteractionEvent event, Game activeGame,
        Player player) {
        String outcome = buttonID.substring(buttonID.indexOf("_") + 1);
        String voteMessage = "Chose to vote for " + StringUtils.capitalize(outcome)
            + ". Click buttons to exhaust planets and use abilities for votes";
        activeGame.setLatestOutcomeVotedFor(outcome);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage,
            getPlanetButtonsVersion2(event, player, activeGame));
        event.getMessage().delete().queue();
    }

    public static void checkForAssigningGeneticRecombination(Game activeGame) {

        for (Player player : activeGame.getRealPlayers()) {
            activeGame.setStoredValue("Genetic Recombination " + player.getFaction(), "");
            if (player.hasTechReady("gr")) {
                String msg = player.getRepresentation()
                    + " you have the option to pre-assign the declaration of using genetic recombination on someone. When they are up to vote, it will ping them saying that you wish to use genetic recombination, and then it will be your job to clarify. Feel free to not preassign if you dont want to use it on this agenda";
                List<Button> buttons2 = new ArrayList<>();
                for (Player p2 : activeGame.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (!activeGame.isFoWMode()) {
                        buttons2.add(Button.secondary(
                            "resolvePreassignment_Genetic Recombination " + p2.getFaction() + "_"
                                + p2.getFaction(),
                            p2.getFaction()));
                    } else {
                        buttons2.add(Button.secondary(
                            "resolvePreassignment_Genetic Recombination " + p2.getFaction() + "_"
                                + p2.getFaction(),
                            p2.getColor()));
                    }
                }
                buttons2.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons2);
            }
        }
    }

    public static void exhaustStuffForVoting(String buttonID, ButtonInteractionEvent event, Game activeGame,
        Player player, String ident, String buttonLabel) {
        String planetName = StringUtils.substringAfter(buttonID, "_");
        String votes = StringUtils.substringBetween(buttonLabel, "(", ")");
        if (!buttonID.contains("argent") && !buttonID.contains("blood") && !buttonID.contains("predictive")
            && !buttonID.contains("everything")) {
            new PlanetExhaust().doAction(player, planetName, activeGame, false);
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
                    + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeGame);
            } else {
                int totalVotes = Integer.parseInt(
                    totalVotesSoFar.substring(totalVotesSoFar.indexOf(":") + 2, totalVotesSoFar.indexOf("\n")))
                    + Integer.parseInt(votes);
                totalVotesSoFar = totalVotesSoFar.substring(0, totalVotesSoFar.indexOf(":") + 2) + totalVotes
                    + totalVotesSoFar.substring(totalVotesSoFar.indexOf("\n"))
                    + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeGame);
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
                ButtonHelper.addReaction(event, true, false, "Exhausted all planets for " + votes + " votes", "");
            } else {
                ButtonHelper.addReaction(event, true, false, "Used ability for " + votes + " votes", "");
            }
        }
    }

    public static void resolvingAnAgendaVote(String buttonID, ButtonInteractionEvent event, Game activeGame,
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
                String outcome = activeGame.getLatestOutcomeVotedFor();
                if (activeGame.isFoWMode()) {
                    identifier = player.getColor();
                } else {
                    identifier = player.getFaction();
                }
                Map<String, String> outcomes = activeGame.getCurrentAgendaVotes();
                String existingData = outcomes.getOrDefault(outcome, "empty");
                int numV = Integer.parseInt(votes);
                int numVOrig = Integer.parseInt(Helper.buildSpentThingsMessageForVoting(player, activeGame, true));
                if (numV > numVOrig) {
                    player.addSpentThing("specialVotes_" + (numV - numVOrig));
                }
                if (activeGame.getLaws() != null && (activeGame.getLaws().containsKey("rep_govt")
                    || activeGame.getLaws().containsKey("absol_government"))) {
                } else {
                    if (player.ownsPromissoryNote("blood_pact")
                        || player.getPromissoryNotesInPlayArea().contains("blood_pact")) {
                        for (Player p2 : getWinningVoters(outcome, activeGame)) {
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
                activeGame.setCurrentAgendaVote(outcome, existingData);
                String msg = ButtonHelper.getIdent(player) + " Voted " + votes + " votes for "
                    + StringUtils.capitalize(outcome) + "!";
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    Helper.buildSpentThingsMessageForVoting(player, activeGame, false));
                event.getMessage().delete().queue();
            }

            String message = " up to vote! Resolve using buttons.";
            Button eraseandReVote = Button.danger("eraseMyVote", "Erase my vote & have me vote again");
            String revoteMsg = "You can press this button to revote if you made a mistake, ignore it otherwise";
            MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(), revoteMsg, eraseandReVote);
            Player nextInLine = getNextInLine(player, getVotingOrder(activeGame), activeGame);
            String realIdentity2 = nextInLine.getRepresentation(true, true);

            int[] voteInfo = getVoteTotal(nextInLine, activeGame);

            while ((voteInfo[0] < 1 && !nextInLine.getColor().equalsIgnoreCase(player.getColor()))
                || activeGame.getStoredValue("Abstain On Agenda").contains(nextInLine.getFaction())) {
                String skippedMessage = realIdentity2
                    + "You are being skipped because you cannot vote";
                if (activeGame.getStoredValue("Abstain On Agenda").contains(nextInLine.getFaction())) {
                    skippedMessage = realIdentity2
                        + "You are being skipped because you told the bot you wanted to preset an abstain";
                    activeGame.setStoredValue("Abstain On Agenda", activeGame
                        .getStoredValue("Abstain On Agenda").replace(nextInLine.getFaction(), ""));
                    nextInLine.resetSpentThings();
                }
                if (activeGame.isFoWMode()) {
                    MessageHelper.sendPrivateMessageToPlayer(nextInLine, activeGame, skippedMessage);
                } else {
                    MessageHelper.sendMessageToChannel(event.getChannel(), skippedMessage);
                }
                player = nextInLine;
                nextInLine = getNextInLine(nextInLine, getVotingOrder(activeGame), activeGame);
                realIdentity2 = nextInLine.getRepresentation(true, true);
                voteInfo = getVoteTotal(nextInLine, activeGame);
            }

            if (!nextInLine.getColor().equalsIgnoreCase(player.getColor())) {
                String realIdentity;
                realIdentity = nextInLine.getRepresentation(true, true);
                String pFaction = StringUtils.capitalize(nextInLine.getFaction());
                String finChecker = "FFCC_" + nextInLine.getFaction() + "_";
                Button Vote = Button.success(finChecker + "vote", pFaction + " Choose To Vote");
                Button Abstain = Button.danger(finChecker + "resolveAgendaVote_0", pFaction + " Choose To Abstain");
                Button ForcedAbstain = Button.secondary("forceAbstainForPlayer_" + nextInLine.getFaction(),
                    "(For Others) Abstain for this player");
                activeGame.updateActivePlayer(nextInLine);
                List<Button> buttons = List.of(Vote, Abstain, ForcedAbstain);
                if (activeGame.isFoWMode()) {
                    if (nextInLine.getPrivateChannel() != null) {
                        MessageHelper.sendMessageToChannel(nextInLine.getPrivateChannel(),
                            getSummaryOfVotes(activeGame, true) + "\n ");
                        MessageHelper.sendMessageToChannelWithButtons(nextInLine.getPrivateChannel(),
                            "\n " + realIdentity + message, buttons);
                        event.getChannel().sendMessage("Notified next in line").queue();
                    }
                } else {
                    message = getSummaryOfVotes(activeGame, true) + "\n \n " + realIdentity + message;
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                }
                ButtonHelperFactionSpecific.checkForGeneticRecombination(nextInLine, activeGame);
            } else {
                winner = getWinner(activeGame);
                if (!"".equalsIgnoreCase(winner) && !winner.contains("*")) {
                    resolveTime = true;
                } else {
                    Player speaker;
                    if (activeGame.getPlayer(activeGame.getSpeaker()) != null) {
                        speaker = activeGame.getPlayers().get(activeGame.getSpeaker());
                    } else {
                        speaker = activeGame.getRealPlayers().get(0);
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
                        tiedWinners = getAgendaButtons(null, activeGame, "resolveAgendaVote_outcomeTie*");
                    }
                    if (!tiedWinners.isEmpty()) {
                        MessageChannel channel = ButtonHelper.getCorrectChannel(speaker, activeGame);
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
            resolveTime(event, activeGame, winner);
        }
        if (!"0".equalsIgnoreCase(votes)) {
            event.getMessage().delete().queue();
        }
        GameSaveLoadManager.saveMap(activeGame, event);

    }

    public static void resolveTime(GenericInteractionCreateEvent event, Game activeGame, String winner) {
        if (winner == null) {
            winner = getWinner(activeGame);
        }
        String summary2 = getSummaryOfVotes(activeGame, true);
        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), summary2 + "\n \n");
        activeGame.setCurrentPhase("agendaEnd");
        activeGame.setActivePlayer(null);
        String resMessage3 = "Current winner is " + StringUtils.capitalize(winner) + ". " + activeGame.getPing();
        if (!"action_deck_2".equals(activeGame.getAcDeckID())) {
            handleShenanigans(event, activeGame, winner);
            resMessage3 += "When shenanigans have concluded, please confirm resolution or discard the result and manually resolve it yourselves.";
        }
        Button autoResolve = Button.primary("agendaResolution_" + winner, "Resolve with current winner");
        Button manualResolve = Button.danger("autoresolve_manual", "Resolve it Manually");
        List<Button> resolutions = List.of(autoResolve, manualResolve);
        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), resMessage3);
        MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), "Resolve", resolutions);
    }

    private static void handleShenanigans(GenericInteractionCreateEvent event, Game activeGame, String winner) {
        List<Player> losers = getLosers(winner, activeGame);
        String resMessage = "You can hold while people resolve shenanigans. If it is not an important agenda, you are encouraged to move on and float the shenanigans -- "
            + losers.size()
            + " players have the opportunity to play deadly plot.";
        if ((!activeGame.isACInDiscard("Bribery") || !activeGame.isACInDiscard("Deadly Plot"))
            && (losers.size() > 0 || activeGame.isAbsolMode())) {
            Button noDeadly = Button.primary("generic_button_id_1", "No Deadly Plot");
            Button noBribery = Button.primary("generic_button_id_2", "No Bribery");
            List<Button> deadlyActionRow = List.of(noBribery, noDeadly);
            MessageHelper.sendMessageToChannelWithPersistentReacts(activeGame.getMainGameChannel(), resMessage,
                activeGame, deadlyActionRow, "shenanigans");
            // MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(),
            // resMessage, deadlyActionRow);
            if (!activeGame.isFoWMode()) {
                StringBuilder loseMessage = new StringBuilder();
                for (Player los : losers) {
                    if (los != null) {
                        loseMessage.append(los.getRepresentation(true, true));
                    }
                }
                event.getMessageChannel().sendMessage(loseMessage + " Please respond to bribery/deadly plot window")
                    .queue();
            } else {
                MessageHelper.privatelyPingPlayerList(losers, activeGame,
                    "Please respond to bribery/deadly plot window");
            }
        } else {
            String messageShen = "Either both bribery and deadly plot were in the discard or noone could legally play them.";

            if (!activeGame.getCurrentAgendaInfo().contains("Elect Player") ||
                (activeGame.isACInDiscard("Confounding") && activeGame.isACInDiscard("Confusing"))) {
                messageShen = messageShen + " There are no shenanigans possible. Please resolve the agenda. ";
            }
            activeGame.getMainGameChannel().sendMessage(messageShen).queue();
        }
        if (activeGame.getCurrentAgendaInfo().contains("Elect Player")
            && (!activeGame.isACInDiscard("Confounding") || !activeGame.isACInDiscard("Confusing"))) {
            String resMessage2 = activeGame.getPing()
                + " please react to no confusing/confounding";
            Button noConfounding = Button.primary("generic_button_id_3", "Refuse Confounding Legal Text");
            Button noConfusing = Button.primary("genericReact4", "Refuse Confusing Legal Text");
            List<Button> buttons = List.of(noConfounding, noConfusing);
            // MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(),
            // resMessage2, buttons);
            MessageHelper.sendMessageToChannelWithPersistentReacts(activeGame.getMainGameChannel(), resMessage2,
                activeGame, buttons, "shenanigans");
        } else {
            if (activeGame.getCurrentAgendaInfo().contains("Elect Player")) {
                activeGame.getMainGameChannel()
                    .sendMessage("Both confounding and confusing are in the discard pile. ").queue();

            }
        }
    }

    public static void reverseRider(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player,
        String ident) {
        String choice = buttonID.substring(buttonID.indexOf("_") + 1);

        String voteMessage = " Chose to reverse the " + choice;
        if (activeGame.isFoWMode()) {
            voteMessage = player.getColor() + voteMessage;
        } else {
            voteMessage = ident + voteMessage;
        }
        Map<String, String> outcomes = activeGame.getCurrentAgendaVotes();
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
                activeGame.setCurrentAgendaVote(outcome, total);
            }

        }

        event.getChannel().sendMessage(voteMessage).queue();
        // event.getMessage().delete().queue();
    }

    public static void reverseAllRiders(ButtonInteractionEvent event, Game activeGame, Player player) {

        Map<String, String> outcomes = activeGame.getCurrentAgendaVotes();
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
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                            ButtonHelper.getIdent(player) + " erased " + onePiece.split("_")[1]);
                    }
                }
                String total = totalBuilder.toString();
                if (total.length() > 0 && total.charAt(0) == ';') {
                    total = total.substring(1);
                }
                activeGame.setCurrentAgendaVote(outcome, total);
            }
        }
    }

    public static void placeRider(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player,
        String ident) {
        String[] choiceParams = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.lastIndexOf("_")).split(";");
        // String choiceType = choiceParams[0];
        String choice = choiceParams[1];

        String rider = buttonID.substring(buttonID.lastIndexOf("_") + 1);
        String agendaDetails = activeGame.getCurrentAgendaInfo().split("_")[1];
        // if(activeMap)
        String cleanedChoice = choice;
        if (agendaDetails.contains("Planet") || agendaDetails.contains("planet")) {
            cleanedChoice = Helper.getPlanetRepresentation(choice, activeGame);
        }
        String voteMessage = "Chose to put a " + rider + " on " + StringUtils.capitalize(cleanedChoice);
        if (!activeGame.isFoWMode()) {
            voteMessage = ident + " " + voteMessage;
        }
        String identifier;
        if (activeGame.isFoWMode()) {
            identifier = player.getColor();
        } else {
            identifier = player.getFaction();
        }
        Map<String, String> outcomes = activeGame.getCurrentAgendaVotes();
        String existingData = outcomes.getOrDefault(choice, "empty");
        if ("empty".equalsIgnoreCase(existingData)) {
            existingData = identifier + "_" + rider;
        } else {
            existingData = existingData + ";" + identifier + "_" + rider;
        }
        activeGame.setCurrentAgendaVote(choice, existingData);

        MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
        String summary2 = getSummaryOfVotes(activeGame, true);
        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), summary2 + "\n \n");

        event.getMessage().delete().queue();
    }

    public static List<Button> getWhenButtons(Game activeGame) {
        Button playWhen = Button.danger("play_when", "Play When");
        Button noWhen = Button.primary("no_when", "No Whens (for now)")
            .withEmoji(Emoji.fromFormatted(Emojis.nowhens));
        Button noWhenPersistent = Button
            .primary("no_when_persistent", "No Whens (for this agenda)")
            .withEmoji(Emoji.fromFormatted(Emojis.nowhens));
        List<Button> whenButtons = new ArrayList<>(List.of(playWhen, noWhen, noWhenPersistent));
        Player quasher = Helper.getPlayerFromAbility(activeGame, "quash");
        if (quasher != null && quasher.getStrategicCC() > 0) {
            String finChecker = "FFCC_" + quasher.getFaction() + "_";
            Button quashButton = Button.danger(finChecker + "quash", "Quash Agenda")
                .withEmoji(Emoji.fromFormatted(Emojis.Xxcha));
            if (activeGame.isFoWMode()) {
                List<Button> quashButtons = new ArrayList<>(List.of(quashButton));
                MessageHelper.sendMessageToChannelWithButtons(quasher.getPrivateChannel(),
                    "Use Button To Quash If You Want", quashButtons);
            } else {
                whenButtons.add(quashButton);
            }
        }
        return whenButtons;
    }

    public static List<Button> getAfterButtons(Game activeGame) {
        List<Button> afterButtons = new ArrayList<>();
        Button playAfter = Button.danger("play_after_Non-AC Rider", "Play A Non-AC Rider");
        if (activeGame.isFoWMode()) {
            afterButtons.add(playAfter);
        }

        if (ButtonHelper.shouldKeleresRiderExist(activeGame) && !activeGame.isFoWMode()) {
            Button playKeleresAfter = Button.secondary("play_after_Keleres Rider", "Play Keleres Rider")
                .withEmoji(Emoji.fromFormatted(Emojis.Keleres));
            afterButtons.add(playKeleresAfter);
        }
        if (!activeGame.isFoWMode() && Helper.getDateDifference(activeGame.getCreationDate(),
            Helper.getDateRepresentation(1705824000011L)) < 0) {
            for (Player player : activeGame.getRealPlayers()) {
                String finChecker = "FFCC_" + player.getFaction() + "_";
                String planet = "tarrock";
                if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)) {
                    afterButtons
                        .add(Button.success(finChecker + "planetAbilityExhaust_" + planet, "Use Tarrock Ability")
                            .withEmoji(Emoji.fromFormatted(player.getFactionEmoji())));
                }
            }
        }
        if (activeGame.getPNOwner("dspnedyn") != null && !activeGame.isFoWMode()) {
            Button playKeleresAfter = Button.secondary("play_after_Edyn Rider", "Play Edyn PN Rider")
                .withEmoji(Emoji.fromFormatted(Emojis.edyn));
            afterButtons.add(playKeleresAfter);
        }
        if (activeGame.getPNOwner("dspnkyro") != null && !activeGame.isFoWMode()) {
            Button playKeleresAfter = Button.secondary("play_after_Kyro Rider", "Play Kyro PN Rider")
                .withEmoji(Emoji.fromFormatted(Emojis.kyro));
            afterButtons.add(playKeleresAfter);
        }
        if (Helper.getPlayerFromAbility(activeGame, "galactic_threat") != null && !activeGame.isFoWMode()) {
            Player nekroProbably = Helper.getPlayerFromAbility(activeGame, "galactic_threat");
            String finChecker = "FFCC_" + nekroProbably.getFaction() + "_";
            Button playNekroAfter = Button
                .secondary(finChecker + "play_after_Galactic Threat Rider", "Do Galactic Threat Rider")
                .withEmoji(Emoji.fromFormatted(Emojis.Nekro));
            afterButtons.add(playNekroAfter);
        } // conspirators
        if (Helper.getPlayerFromAbility(activeGame, "conspirators") != null && !activeGame.isFoWMode()) {
            Player nekroProbably = Helper.getPlayerFromAbility(activeGame, "conspirators");
            String finChecker = "FFCC_" + nekroProbably.getFaction() + "_";
            Button playNekroAfter = Button
                .secondary(finChecker + "play_after_Conspirators", "Use Conspirators")
                .withEmoji(Emoji.fromFormatted(Emojis.zealots));
            afterButtons.add(playNekroAfter);
        } // conspirators
        if (Helper.getPlayerFromUnlockedLeader(activeGame, "keleresheroodlynn") != null) {
            Player keleresX = Helper.getPlayerFromUnlockedLeader(activeGame, "keleresheroodlynn");
            String finChecker = "FFCC_" + keleresX.getFaction() + "_";
            Button playKeleresHero = Button.secondary(finChecker + "play_after_Keleres Xxcha Hero", "Play Keleres Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.Keleres));
            afterButtons.add(playKeleresHero);
        }
        if (Helper.getPlayerFromAbility(activeGame, "radiance") != null) {
            Player edyn = Helper.getPlayerFromAbility(activeGame, "radiance");
            String finChecker = "FFCC_" + edyn.getFaction() + "_";
            Button playKeleresHero = Button
                .secondary(finChecker + "play_after_Edyn Radiance Ability", "Use Edyn Radiance Ability")
                .withEmoji(Emoji.fromFormatted(Emojis.edyn));
            afterButtons.add(playKeleresHero);
        }

        for (Player p1 : activeGame.getRealPlayers()) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            if (p1.hasTechReady("dsedyng")) {
                Button playKeleresHero = Button
                    .secondary(finChecker + "play_after_Edyn Unity Algorithm", "Use Edyn Unity Algorithm Tech")
                    .withEmoji(Emoji.fromFormatted(Emojis.edyn));
                afterButtons.add(playKeleresHero);
            }
            if (activeGame.getCurrentAgendaInfo().contains("Player")
                && ButtonHelper.isPlayerElected(activeGame, p1, "committee")) {
                Button playKeleresHero = Button
                    .secondary(finChecker + "autoresolve_manualcommittee", "Use Committee Formation")
                    .withEmoji(Emoji.fromFormatted(Emojis.Agenda));
                afterButtons.add(playKeleresHero);
            }
        }

        Button noAfter = Button.primary("no_after", "No Afters (for now)")
            .withEmoji(Emoji.fromFormatted(Emojis.noafters));
        afterButtons.add(noAfter);
        Button noAfterPersistent = Button
            .primary("no_after_persistent", "No Afters (for this agenda)")
            .withEmoji(Emoji.fromFormatted(Emojis.noafters));
        afterButtons.add(noAfterPersistent);

        return afterButtons;
    }

    public static void ministerOfIndustryCheck(Player player, Game activeGame, Tile tile,
        GenericInteractionCreateEvent event) {
        if (ButtonHelper.isPlayerElected(activeGame, player, "minister_industry")) {
            String msg = player.getRepresentation(true, true)
                + "since you have minister of industry, you can build in tile "
                + tile.getRepresentationForButtons(activeGame, player) + ". You have "
                + Helper.getProductionValue(player, activeGame, tile, false) + " PRODUCTION Value in the system.";
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg,
                Helper.getPlaceUnitButtons(event, player, activeGame, tile, "ministerBuild", "place"));
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

    public static void startTheVoting(Game activeGame) {
        activeGame.setCurrentPhase("agendaVoting");
        if (activeGame.getCurrentAgendaInfo() != null) {
            String message = " up to vote! Resolve using buttons. \n \n" + getSummaryOfVotes(activeGame, true);

            Player nextInLine = null;
            try {
                nextInLine = getNextInLine(null, getVotingOrder(activeGame), activeGame);
            } catch (Exception e) {
                BotLogger.log("Could not find next in line", e);
            }
            if (nextInLine == null) {
                BotLogger.log("`AgendaHelper.startTheVoting` " + nextInLine + " is **null**");
                return;
            }
            String realIdentity = nextInLine.getRepresentation(true, true);
            int[] voteInfo = getVoteTotal(nextInLine, activeGame);
            int counter = 0;
            while ((voteInfo[0] < 1
                || activeGame.getStoredValue("Abstain On Agenda").contains(nextInLine.getFaction()))
                && counter < activeGame.getRealPlayers().size()) {
                String skippedMessage = realIdentity + "You are being skipped because the bot thinks you cant vote";
                if (activeGame.getStoredValue("Abstain On Agenda").contains(nextInLine.getFaction())) {
                    skippedMessage = realIdentity
                        + "You are being skipped because you told the bot you wanted to preset an abstain";
                    activeGame.setStoredValue("Abstain On Agenda", activeGame
                        .getStoredValue("Abstain On Agenda").replace(nextInLine.getFaction(), ""));
                    nextInLine.resetSpentThings();
                }
                if (activeGame.isFoWMode()) {
                    MessageHelper.sendPrivateMessageToPlayer(nextInLine, activeGame, skippedMessage);
                } else {
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(nextInLine, activeGame),
                        skippedMessage);
                }
                nextInLine = getNextInLine(nextInLine, getVotingOrder(activeGame), activeGame);
                realIdentity = nextInLine.getRepresentation(true, true);
                voteInfo = getVoteTotal(nextInLine, activeGame);
                counter = counter + 1;
            }

            String pFaction = StringUtils.capitalize(nextInLine.getFaction());
            message = realIdentity + message;
            String finChecker = "FFCC_" + nextInLine.getFaction() + "_";
            Button Vote = Button.success(finChecker + "vote", pFaction + " Choose To Vote");
            Button Abstain = Button.danger(finChecker + "resolveAgendaVote_0", pFaction + " Choose To Abstain");
            Button ForcedAbstain = Button.secondary("forceAbstainForPlayer_" + nextInLine.getFaction(),
                "(For Others) Abstain for this player");
            try {
                activeGame.updateActivePlayer(nextInLine);
            } catch (Exception e) {
                BotLogger.log("Could not update active player", e);
            }

            List<Button> buttons = List.of(Vote, Abstain, ForcedAbstain);
            if (activeGame.isFoWMode()) {
                if (nextInLine.getPrivateChannel() != null) {
                    MessageHelper.sendMessageToChannelWithButtons(nextInLine.getPrivateChannel(), message, buttons);
                    activeGame.getMainGameChannel().sendMessage("Voting started. Notified first in line").queue();
                }
            } else {
                MessageHelper.sendMessageToChannelWithButtons(
                    ButtonHelper.getCorrectChannel(nextInLine, activeGame), message, buttons);
            }
            ButtonHelperFactionSpecific.checkForGeneticRecombination(nextInLine, activeGame);
        } else {
            activeGame.getMainGameChannel().sendMessage("Cannot find voting info, sorry. Please resolve automatically")
                .queue();
        }
    }

    public static List<Button> getLawOutcomeButtons(Game activeGame, String rider, String prefix) {
        List<Button> lawButtons = new ArrayList<>();
        for (Map.Entry<String, Integer> law : activeGame.getLaws().entrySet()) {
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

    public static List<Button> getSecretOutcomeButtons(Game activeGame, String rider, String prefix) {
        List<Button> secretButtons = new ArrayList<>();
        for (Player player : activeGame.getPlayers().values()) {
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
        Game activeGame, String prefix, String rider) {
        List<Button> planetOutcomeButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanets());
        for (String planet : planets) {
            Button button;
            if (rider == null) {
                button = Button.secondary(prefix + "_" + planet, Helper.getPlanetRepresentation(planet, activeGame));
            } else {
                button = Button.secondary(prefix + "rider_planet;" + planet + "_" + rider,
                    Helper.getPlanetRepresentation(planet, activeGame));
            }
            planetOutcomeButtons.add(button);
        }
        return planetOutcomeButtons;
    }

    public static List<Button> getPlayerOutcomeButtons(Game activeGame, String rider, String prefix, String planetRes) {
        List<Button> playerOutcomeButtons = new ArrayList<>();

        for (Player player : activeGame.getPlayers().values()) {
            if (player.isRealPlayer()) {
                String faction = player.getFaction();
                if (faction != null && Mapper.isValidFaction(faction)) {
                    Button button;
                    if (!activeGame.isFoWMode() && !faction.contains("franken")) {
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
            }
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

    public static List<Player> getWinningRiders(String winner, Game activeGame, GenericInteractionCreateEvent event) {
        List<Player> winningRs = new ArrayList<>();
        Map<String, String> outcomes = activeGame.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            if (outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");
                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player winningR = activeGame.getPlayerFromColorOrFaction(faction.toLowerCase());
                    if (winningR != null && specificVote.contains("Sanction")) {
                        List<Player> loseFleetPlayers = getWinningVoters(winner, activeGame);
                        for (Player p2 : loseFleetPlayers) {
                            p2.setFleetCC(p2.getFleetCC() - 1);
                            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                                p2.getRepresentation()
                                    + " you lost 1 fleet CC due to voting the same way as a sanction");
                            ButtonHelper.checkFleetInEveryTile(p2, activeGame, event);
                        }
                    }
                    if (winningR != null && (specificVote.contains("Rider") || winningR.hasAbility("future_sight")
                        || specificVote.contains("Radiance") || specificVote.contains("Tarrock Ability"))) {

                        MessageChannel channel = ButtonHelper.getCorrectChannel(winningR, activeGame);
                        String identity = winningR.getRepresentation(true, true);
                        if (specificVote.contains("Galactic Threat Rider")) {
                            List<Player> voters = getWinningVoters(winner, activeGame);
                            List<String> potentialTech = new ArrayList<>();
                            for (Player techGiver : voters) {
                                potentialTech = ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(winningR,
                                    techGiver, potentialTech, activeGame);
                            }
                            MessageHelper.sendMessageToChannelWithButtons(channel,
                                identity + " resolve Galactic Threat Rider using the buttons",
                                ButtonHelperAbilities.getButtonsForPossibleTechForNekro(winningR, potentialTech,
                                    activeGame));
                        }
                        if (specificVote.contains("Technology Rider") && !winningR.hasAbility("propagation")) {

                            MessageHelper.sendMessageToChannelWithButtons(channel,
                                identity + " resolve Technology Rider by using the button to get a tech",
                                List.of(Buttons.GET_A_TECH));
                        }
                        if (specificVote.contains("Leadership Rider")
                            || (specificVote.contains("Technology Rider") && winningR.hasAbility("propagation"))) {
                            List<Button> buttons = ButtonHelper.getGainCCButtons(winningR);
                            String message = identity + "! Your current CCs are " + winningR.getCCRepresentation()
                                + ". Use buttons to gain CCs";
                            activeGame.setStoredValue("originalCCsFor" + winningR.getFaction(),
                                winningR.getCCRepresentation());
                            MessageHelper.sendMessageToChannel(channel,
                                identity + " resolve rider by using the button to get 3 command counters");
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Keleres Rider")) {
                            int currentTG = winningR.getTg();
                            winningR.setTg(currentTG + 2);
                            String message = "";
                            boolean scheming = winningR.hasAbility("scheming");
                            if (winningR.hasAbility("autonetic_memory")) {
                                ButtonHelperAbilities.autoneticMemoryStep1(activeGame, winningR, 1);
                            } else {
                                activeGame.drawActionCard(winningR.getUserID());

                                if (scheming) {
                                    activeGame.drawActionCard(winningR.getUserID());
                                }
                                ButtonHelper.checkACLimit(activeGame, event, winningR);
                                ACInfo.sendActionCardInfo(activeGame, winningR, event);
                            }

                            StringBuilder sb = new StringBuilder(identity);
                            sb.append("due to having a winning **Keleres Rider**, you have been given");
                            if (scheming) {
                                sb.append(" two ").append(Emojis.ActionCard).append(Emojis.ActionCard)
                                    .append(" Action Cards (Due to your **Scheming** ability)");
                            } else {
                                sb.append(" an ").append(Emojis.ActionCard).append(" Action Card");
                            }
                            sb.append(" and 2 ").append(Emojis.getTGorNomadCoinEmoji(activeGame))
                                .append(" trade goods (").append(currentTG).append(" -> ").append(winningR.getTg())
                                .append(")");
                            MessageHelper.sendMessageToChannel(channel, sb.toString());
                            ButtonHelperAbilities.pillageCheck(winningR, activeGame);
                            ButtonHelperAgents.resolveArtunoCheck(winningR, activeGame, 2);
                        }
                        if (specificVote.contains("Politics Rider")) {
                            int amount = 3;
                            if (winningR.hasAbility("scheming")) {
                                amount = 4;
                                activeGame.drawActionCard(winningR.getUserID());
                            }
                            if (winningR.hasAbility("autonetic_memory")) {
                                ButtonHelperAbilities.autoneticMemoryStep1(activeGame, winningR, 3);
                            } else {
                                activeGame.drawActionCard(winningR.getUserID());
                                activeGame.drawActionCard(winningR.getUserID());
                                activeGame.drawActionCard(winningR.getUserID());
                                ButtonHelper.checkACLimit(activeGame, event, winningR);
                                ACInfo.sendActionCardInfo(activeGame, winningR, event);
                            }

                            activeGame.setSpeaker(winningR.getUserID());
                            MessageHelper.sendMessageToChannel(channel,
                                identity + " due to having a winning **Politics Rider**, you have been given "
                                    + amount + " AC and the speaker token");
                        }
                        if (specificVote.contains("Diplomacy Rider")) {
                            String message = identity
                                + " You have a diplo rider to resolve. Click the name of the planet who's system you wish to diplo";
                            List<Button> buttons = Helper.getPlanetSystemDiploButtons(event, winningR, activeGame, true,
                                null);
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Construction Rider")) {
                            String message = identity
                                + " You have a construction rider to resolve. Click the name of the planet you wish to put your space dock on";
                            List<Button> buttons = Helper.getPlanetPlaceUnitButtons(winningR, activeGame, "sd",
                                "place");
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Warfare Rider")) {
                            String message = identity
                                + " You have a warfare rider to resolve. Select the system to put the dread";
                            List<Button> buttons = Helper.getTileWithShipsPlaceUnitButtons(winningR, activeGame,
                                "dreadnought", "placeOneNDone_skipbuild");
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Trade Rider")) {
                            int cTG = winningR.getTg();
                            winningR.setTg(cTG + 5);
                            MessageHelper.sendMessageToChannel(channel,
                                identity + " due to having a winning Trade Rider, you have been given 5 tg (" + cTG
                                    + "->" + winningR.getTg() + ")");
                            ButtonHelperAbilities.pillageCheck(winningR, activeGame);
                            ButtonHelperAgents.resolveArtunoCheck(winningR, activeGame, 5);
                        }
                        if (specificVote.contains("Radiance")) {
                            List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, winningR,
                                UnitType.Mech);
                            ButtonHelperFactionSpecific.resolveEdynAgendaStuffStep1(winningR, activeGame, tiles);
                        }
                        if (specificVote.contains("Tarrock Ability")) {
                            Player player = winningR;
                            String message = player.getFactionEmoji() + " Drew Secret Objective";
                            activeGame.drawSecretObjective(player.getUserID());
                            if (player.hasAbility("plausible_deniability")) {
                                activeGame.drawSecretObjective(player.getUserID());
                                message = message + ". Drew a second SO due to plausible deniability.";
                            }
                            SOInfo.sendSecretObjectiveInfo(activeGame, player, event);
                            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                                message);
                        }
                        if (specificVote.contains("Kyro Rider")) {
                            Player player = winningR;
                            String message = player.getRepresentation(true, true)
                                + " Click the names of the planet you wish to drop 3 infantry on";
                            List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, activeGame,
                                "3gf", "placeOneNDone_skipbuild"));
                            MessageHelper.sendMessageToChannelWithButtons(
                                ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
                        }
                        if (specificVote.contains("Edyn Rider")) {
                            List<Tile> tiles = new ArrayList<>();
                            for (Tile tile : activeGame.getTileMap().values()) {
                                if (FoWHelper.playerHasUnitsInSystem(winningR, tile)) {
                                    tiles.add(tile);
                                }
                            }
                            ButtonHelperFactionSpecific.resolveEdynAgendaStuffStep1(winningR, activeGame, tiles);
                        }
                        if (specificVote.contains("Imperial Rider")) {
                            String msg = identity + " due to having a winning Imperial Rider, you have scored a pt\n";
                            int poIndex;
                            poIndex = activeGame.addCustomPO("Imperial Rider", 1);
                            msg = msg + "Custom PO 'Imperial Rider' has been added.\n";
                            activeGame.scorePublicObjective(winningR.getUserID(), poIndex);
                            msg = msg + winningR.getRepresentation() + " scored 'Imperial Rider'\n";
                            MessageHelper.sendMessageToChannel(channel, msg);
                            Helper.checkEndGame(activeGame, winningR);

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

    public static List<Player> getRiders(Game activeGame) {
        List<Player> riders = new ArrayList<>();

        Map<String, String> outcomes = activeGame.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");

            while (vote_info.hasMoreTokens()) {
                String specificVote = vote_info.nextToken();
                String faction = specificVote.substring(0, specificVote.indexOf("_"));
                String vote = specificVote.substring(specificVote.indexOf("_") + 1);
                if (vote.contains("Rider") || vote.contains("Sanction")) {
                    Player rider = activeGame.getPlayerFromColorOrFaction(faction.toLowerCase());
                    if (rider != null) {
                        riders.add(rider);
                    }
                }

            }

        }
        return riders;
    }

    public static List<Player> getLosers(String winner, Game activeGame) {
        List<Player> losers = new ArrayList<>();
        Map<String, String> outcomes = activeGame.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            if (!outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");

                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player loser = activeGame.getPlayerFromColorOrFaction(faction.toLowerCase());
                    if (loser != null) {
                        if (!losers.contains(loser)) {
                            losers.add(loser);
                        }

                    }
                }
            }
        }
        return losers;
    }

    public static List<Player> getWinningVoters(String winner, Game activeGame) {
        List<Player> losers = new ArrayList<>();
        Map<String, String> outcomes = activeGame.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            if (outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");

                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player loser = activeGame.getPlayerFromColorOrFaction(faction.toLowerCase());
                    if (loser != null && !specificVote.contains("Rider") && !specificVote.contains("Sanction")) {
                        if (!losers.contains(loser)) {
                            losers.add(loser);
                        }

                    }
                }
            }
        }
        return losers;
    }

    public static List<Player> getLosingVoters(String winner, Game activeGame) {
        List<Player> losers = new ArrayList<>();
        Map<String, String> outcomes = activeGame.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            if (!outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");

                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player loser = activeGame.getPlayerFromColorOrFaction(faction.toLowerCase());
                    if (loser != null) {
                        if (!losers.contains(loser) && !specificVote.contains("Rider")
                            && !specificVote.contains("Sanction")) {
                            losers.add(loser);
                        }

                    }
                }
            }
        }
        return losers;
    }

    public static List<Player> getPlayersWithMostPoints(Game activeGame) {
        List<Player> losers = new ArrayList<>();
        int most = 0;
        for (Player p : activeGame.getRealPlayers()) {
            if (p.getTotalVictoryPoints() > most) {
                most = p.getTotalVictoryPoints();
            }
        }
        for (Player p : activeGame.getRealPlayers()) {
            if (p.getTotalVictoryPoints() == most) {
                losers.add(p);
            }
        }
        return losers;
    }

    public static List<Player> getPlayersWithLeastPoints(Game activeGame) {
        List<Player> losers = new ArrayList<>();
        int least = 20;
        for (Player p : activeGame.getRealPlayers()) {
            if (p.getTotalVictoryPoints() < least) {
                least = p.getTotalVictoryPoints();
            }
        }
        for (Player p : activeGame.getRealPlayers()) {
            if (p.getTotalVictoryPoints() == least) {
                losers.add(p);
            }
        }
        return losers;
    }

    public static int[] getVoteTotal(Player player, Game activeGame) {
        int hasXxchaAlliance = activeGame.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander") ? 1 : 0;
        int hasXxchaHero = player.hasLeaderUnlocked("xxchahero") ? 1 : 0;
        int voteCount = getTotalVoteCount(activeGame, player);

        // Check if Player only has additional votes but not any "normal" votes, if so,
        // they can't vote
        if (getVoteCountFromPlanets(activeGame, player) == 0) {
            voteCount = 0;
        }

        if (activeGame.getLaws() != null && (activeGame.getLaws().containsKey("rep_govt")
            || activeGame.getLaws().containsKey("absol_government"))) {
            voteCount = 1;
            if (activeGame.getLaws().containsKey("absol_government") && player.getPlanets().contains("mr")) {
                voteCount = 2;
            }
        }

        if ("nekro".equals(player.getFaction()) && hasXxchaAlliance == 0) {
            voteCount = 0;
        }
        List<Player> riders = getRiders(activeGame);
        if (riders.contains(player)) {
            if (hasXxchaAlliance == 0) {
                voteCount = 0;
            }
        }

        if (hasXxchaAlliance == 0
            && (activeGame.getStoredValue("AssassinatedReps").contains(player.getFaction())
                || activeGame.getStoredValue("PublicExecution").contains(player.getFaction()))) {
            voteCount = 0;
        }

        return new int[] { voteCount, hasXxchaHero, hasXxchaAlliance };
    }

    public static List<Player> getVotingOrder(Game activeGame) {
        List<Player> orderList = new ArrayList<>(activeGame.getPlayers().values().stream()
            .filter(Player::isRealPlayer)
            .toList());
        String speakerName = activeGame.getSpeaker();
        Optional<Player> optSpeaker = orderList.stream()
            .filter(player -> player.getUserID().equals(speakerName))
            .findFirst();

        if (optSpeaker.isPresent()) {
            int rotationDistance = orderList.size() - orderList.indexOf(optSpeaker.get()) - 1;
            Collections.rotate(orderList, rotationDistance);
        }
        if (activeGame.isReverseSpeakerOrder()) {
            Collections.reverse(orderList);
            if (optSpeaker.isPresent()) {
                int rotationDistance = orderList.size() - orderList.indexOf(optSpeaker.get()) - 1;
                Collections.rotate(orderList, rotationDistance);
            }
        }
        if (activeGame.getHackElectionStatus()) {
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
        String conspiratorsFaction = activeGame.getStoredValue("conspiratorsFaction");
        if (!conspiratorsFaction.isEmpty()) {
            Player rhodun = activeGame.getPlayerFromColorOrFaction(conspiratorsFaction);
            Optional<Player> speaker = orderList.stream()
                .filter(player -> player.getFaction() != null && activeGame.getSpeaker().equals(player.getUserID()))
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

    public static Player getNextInLine(Player player1, List<Player> votingOrder, Game activeGame) {
        boolean foundPlayer = false;
        if (player1 == null) {
            for (int x = 0; x < 6; x++) {
                if (x < votingOrder.size()) {
                    Player player = votingOrder.get(x);
                    if (player == null) {
                        BotLogger.log("`AgendaHelper.getNextInLine` Hit a null player in game " + activeGame.getName());
                        return null;
                    }

                    if (player.isRealPlayer()) {
                        return player;
                    } else {
                        BotLogger.log("`AgendaHelper.getNextInLine` Hit a notRealPlayer player in game "
                            + activeGame.getName() + " on player " + player.getUserName());
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

    public static List<Button> getPlanetButtons(GenericInteractionCreateEvent event, Player player, Game activeGame) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        int[] voteInfo = getVoteTotal(player, activeGame);
        Map<String, Planet> planetsInfo = activeGame.getPlanetsInfo();
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
            for (Player player_ : activeGame.getPlayers().values()) {
                if (player_.isRealPlayer())
                    numPlayers++;
            }
            Button button = Button.primary("exhaust_argent", "Special Argent Votes (" + numPlayers + ")")
                .withEmoji(Emoji.fromFormatted(Emojis.Argent));
            planetButtons.add(button);
        }
        if (player.hasTechReady("pi")) {
            Button button = Button.primary("exhaust_predictive", "Use Predictive Votes (3)")
                .withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
            planetButtons.add(button);
        }
        return planetButtons;
    }

    public static void checkForPoliticalSecret(Game activeGame) {
        for (Player player : activeGame.getRealPlayers()) {
            if (!player.getPromissoryNotes().containsKey(player.getColor() + "_ps")
                && player.getPromissoryNotesOwned().contains(player.getColor() + "_ps")) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation()
                    + " this is a reminder that you dont currently hold your political secret, and thus may want to wait until the holder indicates no whens before you do any afters");
            }
        }
    }

    public static void exhaustForVotes(ButtonInteractionEvent event, Player player, Game activeGame, String buttonID) {
        String thing = buttonID.replace("exhaustForVotes_", "");
        if (!thing.contains("hacan") && !thing.contains("kyro") && !thing.contains("allPlanets")) {
            player.addSpentThing(thing);
            if (thing.contains("planet_")) {
                String planet = thing.replace("planet_", "");
                player.exhaustPlanet(planet);
                String planetName = planet;
                UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planetName, activeGame);
                if (uH != null) {
                    if (uH.getTokenList().contains("attachment_arcane_citadel.png")) {
                        Tile tile = activeGame.getTileFromPlanet(planetName);
                        String msg = player.getRepresentation() + " added an infantry to " + planetName
                            + " due to the arcane citadel";
                        new AddUnits().unitParsing(event, player.getColor(), tile, "1 infantry " + planetName,
                            activeGame);
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
                    }
                }
            }
            if (thing.contains("dsghotg")) {
                player.exhaustTech("dsghotg");
            }
            if (thing.contains("predictive")) {
                activeGame.setStoredValue("riskedPredictive",
                    activeGame.getStoredValue("riskedPredictive") + player.getFaction());
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
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    player.getRepresentation() + " please remove 1 infantry to pay for commander",
                    ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, activeGame, "infantry"));
            }
            if (thing.contains("allPlanets")) {
                List<String> unexhaustedPs = new ArrayList<>();
                unexhaustedPs.addAll(player.getReadiedPlanets());
                for (String planet : unexhaustedPs) {
                    if (getSpecificPlanetsVoteWorth(player, activeGame, planet) > 0) {
                        player.addSpentThing("planet_" + planet);
                        player.exhaustPlanet(planet);
                    }
                    String planetName = planet;
                    UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planetName, activeGame);
                    if (uH != null) {
                        if (uH.getTokenList().contains("attachment_arcane_citadel.png")) {
                            Tile tile = activeGame.getTileFromPlanet(planetName);
                            String msg = player.getRepresentation() + " added an infantry to " + planetName
                                + " due to the arcane citadel";
                            new AddUnits().unitParsing(event, player.getColor(), tile, "1 infantry " + planetName,
                                activeGame);
                            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
                        }
                    }

                }
                ButtonHelper.deleteTheOneButton(event);
            }
        }
        String editedMessage = Helper.buildSpentThingsMessageForVoting(player, activeGame, false);
        editedMessage = getSummaryOfVotes(activeGame, true) + "\n\n" + editedMessage;
        event.getMessage().editMessage(editedMessage).queue();

    }

    public static int getSpecificPlanetsVoteWorth(Player player, Game activeGame, String planet) {
        int voteAmount = 0;
        Map<String, Planet> planetsInfo = activeGame.getPlanetsInfo();
        int[] voteInfo = getVoteTotal(player, activeGame);
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
        Game activeGame) {
        player.resetSpentThings();
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        int totalPlanetVotes = 0;
        for (String planet : planets) {
            int voteAmount = getSpecificPlanetsVoteWorth(player, activeGame, planet);
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
            for (Player player_ : activeGame.getPlayers().values()) {
                if (player_.isRealPlayer())
                    numPlayers++;
            }
            Button button = Button
                .primary("exhaustForVotes_zeal_" + numPlayers, "Special Argent Votes (" + numPlayers + ")")
                .withEmoji(Emoji.fromFormatted(Emojis.Argent));
            planetButtons.add(button);
        }
        if (player.hasTechReady("pi")) {
            Button button = Button.primary("exhaustForVotes_predictive_3", "Use Predictive Votes (3)")
                .withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
            planetButtons.add(button);
        }
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "hacancommander")) {
            Button button = Button.secondary("exhaustForVotes_hacanCommanderTg", "Spend a tg for 2 votes")
                .withEmoji(Emoji.fromFormatted(Emojis.Hacan));
            planetButtons.add(button);
        }
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "kyrocommander")) {
            Button button = Button.secondary("exhaustForVotes_kyrocommanderInf", "Kill infantry for 1 vote per kill")
                .withEmoji(Emoji.fromFormatted(Emojis.blex));
            planetButtons.add(button);
        }
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "augerscommander")) {
            int count = player.getTechs().size() / 2;
            Button button = Button
                .secondary("exhaustForVotes_augerscommander_" + count, "Use Augurs Commander Votes (" + count + ")")
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
                .secondary("exhaustForVotes_absolShard_" + shardVotes, "Use Absol Shard Votes (" + shardVotes + ")")
                .withEmoji(Emoji.fromFormatted(Emojis.Absol));
            planetButtons.add(button);
        }
        // Absol's Syncretone - +1 vote for each neighbour
        if (player.hasRelicReady("absol_syncretone")) {
            int count = player.getNeighbourCount();
            Button button = Button
                .secondary("exhaustForVotes_absolsyncretone_" + count, "Use Absol Syncretone Votes (" + count + ")")
                .withEmoji(Emoji.fromFormatted(Emojis.Absol));
            planetButtons.add(button);
        }

        // Ghoti Wayfarer Tech
        if (player.hasTechReady("dsghotg")) {
            int fleetCC = player.getFleetCC();
            Button button = Button
                .secondary("exhaustForVotes_dsghotg_" + fleetCC, "Use Ghoti Tech Votes (" + fleetCC + ")")
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

    public static void proceedToFinalizingVote(Game activeGame, Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        String votes = Helper.buildSpentThingsMessageForVoting(player, activeGame, true);
        String msg = Helper.buildSpentThingsMessageForVoting(player, activeGame, false) + "\n\n"
            + player.getRepresentation() + " you are currently voting " + votes
            + " votes. You can confirm this or you can modify this number if the bot missed something.";
        if (player.getPromissoryNotesInPlayArea().contains("blood_pact")) {
            msg = msg + " Any Blood Pact Votes will be automatically added";
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success(player.getFinsFactionCheckerPrefix() + "resolveAgendaVote_" + votes,
            "Vote " + votes + " votes"));
        buttons.add(Button.primary(player.getFinsFactionCheckerPrefix() + "distinguished_" + votes, "Modify Votes"));
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg, buttons);
    }

    public static void resolveAbsolAgainstChecksNBalances(Game activeGame) {
        StringBuilder message = new StringBuilder();
        // Integer poIndex = activeGame.addCustomPO("Points Scored Prior to Absol C&B
        // Wipe", 1);
        // message.append("Custom PO 'Points Scored Prior to Absol C&B Wipe' has been
        // added and people have scored it. \n");

        // activeGame.scorePublicObjective(playerWL.getUserID(), poIndex);
        for (Player player : activeGame.getRealPlayers()) {
            int currentPoints = player.getPublicVictoryPoints(false) + player.getSecretVictoryPoints();

            Integer poIndex = activeGame.addCustomPO(
                StringUtils.capitalize(player.getColor()) + " VP Scored Prior to Agenda Wipe", currentPoints);
            message.append("Custom PO '")
                .append(StringUtils.capitalize(player.getColor()
                    + " VP Scored Prior to Agenda Wipe' has been added and scored by that color, worth "
                    + currentPoints + " points. \n"));
            activeGame.scorePublicObjective(player.getUserID(), poIndex);
            Map<String, List<String>> playerScoredPublics = activeGame.getScoredPublicObjectives();
            for (Entry<String, List<String>> scoredPublic : playerScoredPublics.entrySet()) {
                if (Mapper.getPublicObjectivesStage1().containsKey(scoredPublic.getKey())
                    || Mapper.getPublicObjectivesStage2().containsKey(scoredPublic.getKey())) {
                    if (scoredPublic.getValue().contains(player.getUserID())) {
                        activeGame.unscorePublicObjective(player.getUserID(), scoredPublic.getKey());
                    }
                }
            }
            List<Integer> scoredSOs = new ArrayList<>(player.getSecretsScored().values());
            for (int soID : scoredSOs) {
                activeGame.unscoreAndShuffleSecretObjective(player.getUserID(), soID);
            }

        }
        message.append("All SOs have been returned to the deck and all POs scored have been cleared. \n");

        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message.toString());
    }

    public static List<Button> getPlanetRefreshButtons(GenericInteractionCreateEvent event, Player player,
        Game activeGame) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getExhaustedPlanets());
        for (String planet : planets) {
            String buttonID = "refresh_" + planet + "_" + player.getFaction();
            String buttonText = Helper.getPlanetRepresentation(planet, activeGame);
            Button button = Button.success(buttonID, buttonText);
            planetButtons.add(button);
        }

        return planetButtons;
    }

    public static void eraseVotesOfFaction(Game activeGame, String faction) {
        if (activeGame.getCurrentAgendaVotes().keySet().isEmpty()) {
            return;
        }
        Map<String, String> outcomes = new HashMap<>(activeGame.getCurrentAgendaVotes());
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
                activeGame.removeOutcomeAgendaVote(outcome);
            } else {
                activeGame.setCurrentAgendaVote(outcome, voteSumm);
            }
        }
    }

    public static String getWinner(Game activeGame) {
        StringBuilder winner = new StringBuilder();
        Map<String, String> outcomes = activeGame.getCurrentAgendaVotes();
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

    public static String getSummaryOfVotes(Game activeGame, boolean capitalize) {
        String summary;
        Map<String, String> outcomes = activeGame.getCurrentAgendaVotes();
        String agendaDetails = activeGame.getCurrentAgendaInfo();
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
            summary = "# Agenda Name: " + agendaName + "\nNo current riders or votes have been cast yet.";
        } else {
            StringBuilder summaryBuilder = new StringBuilder(
                "# Agenda Name: " + agendaName + "\nCurrent status of votes and outcomes is: \n");
            for (String outcome : outcomes.keySet()) {
                if (StringUtils.countMatches(activeGame.getCurrentAgendaInfo(), "_") > 1) {
                    agendaDetails = activeGame.getCurrentAgendaInfo().split("_")[1];
                } else {
                    agendaDetails = activeGame.getCurrentAgendaInfo();
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
                        Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
                        faction = Emojis.getFactionIconFromDiscord(faction);
                        if (p2 != null) {
                            faction = p2.getFactionEmoji();
                        }
                        if (activeGame.isFoWMode()) {
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

                    if (!activeGame.isFoWMode() && activeGame.getCurrentAgendaInfo().contains("Elect Player")) {
                        summaryBuilder.append(Emojis.getFactionIconFromDiscord(outcome.toLowerCase())).append(" ")
                            .append(outcome).append(": ").append(totalVotes).append(". (").append(outcomeSummary)
                            .append(")\n");

                    } else if (!activeGame.isHomeBrewSCMode()
                        && activeGame.getCurrentAgendaInfo().contains("Elect Strategy Card")) {
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

    public static void resolveMinisterOfWar(Game activeGame, Player player, ButtonInteractionEvent event) {
        ButtonHelper.deleteTheOneButton(event);
        boolean success = activeGame.removeLaw(activeGame.getLaws().get("minister_war"));
        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Minister of War Law removed");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Law ID not found");
        }
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, activeGame, event, "ministerOfWar");
        MessageChannel channel = ButtonHelper.getCorrectChannel(player, activeGame);
        MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
    }

    public static String getPlayerVoteText(Game activeGame, Player player) {
        StringBuilder sb = new StringBuilder();
        int voteCount = getVoteCountFromPlanets(activeGame, player);
        Map<String, Integer> additionalVotes = getAdditionalVotesFromOtherSources(activeGame, player);
        String additionalVotesText = getAdditionalVotesFromOtherSourcesText(additionalVotes);

        if (activeGame.isFoWMode()) {
            sb.append(" vote count: **???**");
            return sb.toString();
        } else if (player.hasAbility("galactic_threat")
            && !activeGame.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
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
        if (activeGame.getLaws().containsKey("rep_govt") || activeGame.getLaws().containsKey("absol_government")) {
            sb = new StringBuilder();
            if (activeGame.getLaws().containsKey("absol_government") && player.getPlanets().contains("mr")) {
                sb.append(" vote count (Rep Gov while controlling rex): **" + " ").append("2");
            } else {
                sb.append(" vote count (Rep Gov): **" + " ").append("1");
            }

        }
        return sb.toString();
    }

    public static int getTotalVoteCount(Game activeGame, Player player) {
        return getVoteCountFromPlanets(activeGame, player) + getAdditionalVotesFromOtherSources(activeGame, player)
            .values().stream().mapToInt(Integer::intValue).sum();
    }

    public static int getVoteCountFromPlanets(Game activeGame, Player player) {
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        Map<String, Planet> planetsInfo = activeGame.getPlanetsInfo();
        int baseResourceCount = planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(Planet.class::cast)
            .mapToInt(Planet::getResources).sum();
        int baseInfluenceCount = planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(Planet.class::cast)
            .mapToInt(Planet::getInfluence).sum();
        int voteCount = baseInfluenceCount; // default

        // NEKRO unless XXCHA ALLIANCE
        if (player.hasAbility("galactic_threat")
            && !activeGame.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
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
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
            int readyPlanetCount = planets.size();
            voteCount += readyPlanetCount;
        }

        // Olradin "Control" - +2 votes per cultural planet
        if (player.hasAbility("policy_the_people_control")) {
            List<String> cultPlanets = new ArrayList<>();
            for (String cplanet : planets) {
                Planet p = activeGame.getPlanetsInfo().get(cplanet);
                if (p == null) continue;
                if (p.getPlanetTypes().contains(Constants.CULTURAL)) {
                    cultPlanets.add(cplanet);
                }
            }
            voteCount += (cultPlanets.size() * 2);
        }
        UnitHolder p;
        for (String cplanet : planets) {
            p = ButtonHelper.getUnitHolderFromPlanetName(cplanet, activeGame);
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
    public static Map<String, Integer> getAdditionalVotesFromOtherSources(Game activeGame, Player player) {
        Map<String, Integer> additionalVotesAndSources = new LinkedHashMap<>();

        // Argent Zeal
        if (player.hasAbility("zeal")) {
            long playerCount = activeGame.getPlayers().values().stream().filter(Player::isRealPlayer).count();
            additionalVotesAndSources.put(Emojis.Argent + "Zeal", Math.toIntExact(playerCount));
        }

        // Blood Pact
        if (player.getPromissoryNotesInPlayArea().contains("blood_pact")) {
            additionalVotesAndSources.put(Emojis.Empyrean + Emojis.PN + "Blood Pact", 4);
        }

        // Predictive Intelligence
        if (player.hasTechReady("pi")) {
            additionalVotesAndSources.put(Emojis.CyberneticTech + "Predictive Intelligence", 3);
        }

        // Xxcha Alliance
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
            additionalVotesAndSources.put(Emojis.Xxcha + "Alliance has been counted for", 0);
        }

        // Hacan Alliance
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "hacancommander")) {
            additionalVotesAndSources.put(Emojis.Hacan + " Alliance not calculated", 0);
        }
        // Kyro Alliance
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "kyrocommander")) {
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
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "augerscommander")) {
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