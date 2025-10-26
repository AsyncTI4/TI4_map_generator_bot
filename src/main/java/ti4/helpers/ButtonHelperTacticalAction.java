package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.commands.tokens.AddTokenCommand;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.TeHelperGeneral;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.breakthrough.EidolonMaximumService;
import ti4.service.breakthrough.VoidTetherService;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.fow.FOWPlusService;
import ti4.service.fow.LoreService;
import ti4.service.fow.RiftSetModeService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.tactical.TacticalActionService;
import ti4.service.turn.StartTurnService;
import ti4.service.unit.CheckUnitContainmentService;
import ti4.settings.users.UserSettingsManager;

public class ButtonHelperTacticalAction {

    @ButtonHandler("doneWithTacticalAction")
    public static void concludeTacticalAction(Player player, Game game, ButtonInteractionEvent event) {
        if (!game.isL1Hero() && !FOWPlusService.isVoid(game, game.getActiveSystem())) {
            RiftSetModeService.concludeTacticalAction(player, game, event);
            ButtonHelper.exploreDET(player, game, event);
            ButtonHelperFactionSpecific.cleanCavUp(game, event);
            if (player.hasAbility("cunning")) {
                List<Button> trapButtons = new ArrayList<>();
                for (UnitHolder uH : game.getTileByPosition(game.getActiveSystem())
                        .getUnitHolders()
                        .values()) {
                    if (uH instanceof Planet) {
                        String planet = uH.getName();
                        trapButtons.add(
                                Buttons.gray("setTrapStep3_" + planet, Helper.getPlanetRepresentation(planet, game)));
                    }
                }
                trapButtons.add(Buttons.red("deleteButtons", "Decline"));
                String msg =
                        player.getRepresentationUnfogged() + " you can use the buttons to place a trap on a planet";
                if (trapButtons.size() > 1) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, trapButtons);
                }
            }
            if (player.hasUnexhaustedLeader("celdauriagent")) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.gray(
                        "exhaustAgent_celdauriagent_" + player.getFaction(),
                        "Use Celdauri Agent",
                        FactionEmojis.celdauri));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                                + " you may use "
                                + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                                + "George Nobin, the Celdauri"
                                + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                                + " agent to place 1 space dock for 2 trade goods or 2 commodities",
                        buttons);
            }
        }

        if (!game.isAbsolMode()
                && player.getRelics().contains("emphidia")
                && !player.getExhaustedRelics().contains("emphidia")) {
            String message = player.getRepresentation()
                    + ", you may use the button to explore a planet using _The Crown of Emphidia_.";
            List<Button> systemButtons2 = new ArrayList<>();
            systemButtons2.add(Buttons.green("crownofemphidiaexplore", "Use Crown of Emphidia To Explore"));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
        }
        if (game.isNaaluAgent()) {
            player = game.getPlayer(game.getActivePlayerID());
        }
        if (game.isWarfareAction()) {
            Button redistro = Buttons.blue(
                    player.finChecker() + "redistributeCCButtons_deleteThisButton", "Redistribute Command Tokens");
            String warfareDone = player.getRepresentationUnfogged()
                    + " your Warfare action is finished, you can redistribute your command tokens again.";
            MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(), warfareDone, redistro);
        }

        resetStoredValuesForTacticalAction(game);
        game.removeStoredValue("producedUnitCostFor" + player.getFaction());
        String message = player.getRepresentationUnfogged() + ", use buttons to end turn, or do another action.";
        List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
        MessageChannel channel = event.getMessageChannel();
        if (game.isFowMode()) {
            LoreService.showSystemLore(player, game, game.getActiveSystem());
            channel = player.getPrivateChannel();
        }
        MessageHelper.sendMessageToChannelWithButtons(channel, message, systemButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("tacticalActionBuild_")
    public static void buildWithTacticalAction(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.replace("tacticalActionBuild_", "");
        List<Button> buttons =
                Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), "tacticalAction", "place");
        String message = player.getRepresentation() + " Use the buttons to produce units. ";
        String message3 = "You have "
                + Helper.getProductionValue(player, game, game.getTileByPosition(pos), false)
                + " PRODUCTION value in this system.\n";
        if (Helper.getProductionValue(player, game, game.getTileByPosition(pos), false) > 0
                && game.playerHasLeaderUnlockedOrAlliance(player, "cabalcommander")) {
            message3 = message3
                    + "You also have That Which Molds Flesh, the Vuil'raith commander, which allows you to produce 2 "
                    + UnitEmojis.fighter + "/" + UnitEmojis.infantry + " that don't count towards PRODUCTION limit.\n";
        }
        if (Helper.getProductionValue(player, game, game.getTileByPosition(pos), false) > 0
                && IsPlayerElectedService.isPlayerElected(game, player, "prophecy")) {
            message3 +=
                    "Reminder that you have _Prophecy of Ixth_ and should produce at least 2 fighters if you wish to keep it. Its removal is not automated.\n";
        }
        MessageHelper.sendMessageToChannel(
                event.getChannel(), message3 + ButtonHelper.getListOfStuffAvailableToSpend(player, game, true));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveAfterMovementEffects(
            ButtonInteractionEvent event, Game game, Player player, Tile tile, boolean unitsWereMoved) {
        if (player != game.getActivePlayer()
                && player.hasAbility("hired_guns")
                && !game.getStoredValue("hiredGunsInPlay").isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getFactionEmoji()
                            + " moved the ships to the active system. If a combat is underway,"
                            + " press refresh picture to see the ships. \nWhen the active player rolls dice or assigns hits, they should be able to use these hired ships. "
                            + "\nWhen the player concludes the tactical action, these ships will automatically be replaced with the active players.");
            return;
        }
        if (unitsWereMoved && tile.getPlanetUnitHolders().isEmpty() && player.hasUnexhaustedLeader("empyreanagent")) {
            List<Button> empyButtons = new ArrayList<>();
            empyButtons.add(Buttons.gray("exhaustAgent_empyreanagent", "Use Empyrean Agent", FactionEmojis.Empyrean));
            empyButtons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged() + ", use button to exhaust "
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "Acamar, the Empyrean" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                            + " agent.",
                    empyButtons);
        }
        if (unitsWereMoved
                && (tile.getUnitHolders().size() == 1)
                && player.getPlanets().contains("ghoti")) {
            player.setCommodities(player.getCommodities() + 1);
            String msg = player.getRepresentation()
                    + " gained 1 commodity due to the legendary ability of Ghoti. Your commodities are now "
                    + player.getCommodities() + ".";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        }
        boolean flagshipMoved = game.getTacticalActionDisplacement().values().stream()
                .anyMatch(m -> m.containsKey(Units.getUnitKey(UnitType.Flagship, player.getColor())));
        if (unitsWereMoved && flagshipMoved && player.hasUnit("dihmohn_flagship")) {
            Button produce = Buttons.blue("dihmohnfs_" + game.getActiveSystem(), "Produce 2 Units");
            String msg = player.getRepresentation()
                    + ", the Maximus (Dih-Mohn Flagship) moved into the active system, so you may produce up to 2 units with a combined cost of 4 or less.";
            MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(), msg, produce);
        }
        EidolonMaximumService.sendEidolonMaximumFlipButtons(game, player);
        if (unitsWereMoved) {
            CommanderUnlockCheckService.checkPlayer(player, "nivyn", "ghoti", "zelian", "gledge", "mortheus");
            CommanderUnlockCheckService.checkAllPlayersInGame(game, "empyrean");
            for (Player nonActivePlayer : game.getRealPlayers()) {
                if (player == nonActivePlayer) {
                    continue;
                }
                if (nonActivePlayer.hasTech("vw") && FoWHelper.playerHasUnitsInSystem(nonActivePlayer, tile)) {

                    if (game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(
                                nonActivePlayer.getCorrectChannel(),
                                nonActivePlayer.getRepresentation() + ", your _Voidwatch_ has been triggered.");
                    }
                    List<Button> stuffToTransButtons =
                            ButtonHelper.getForcedPNSendButtons(game, nonActivePlayer, player);
                    String message2 = player.getRepresentationUnfogged()
                            + ", you have triggered _Voidwatch_. Please choose the promissory note you wish to send.";
                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCardsInfoThread(), message2, stuffToTransButtons);
                    if (game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(
                                player.getCorrectChannel(),
                                player.getRepresentation()
                                        + ", you owe a promissory note to the player with units here.");
                    } else {
                        MessageHelper.sendMessageToChannel(
                                player.getCorrectChannel(),
                                player.getRepresentation()
                                        + ", you owe a promissory note to " + nonActivePlayer.getRepresentation()
                                        + " from triggering _Voidwatch_.");
                    }
                }
            }
            ButtonHelper.resolveEmpyCommanderCheck(player, game, tile, event);
            ButtonHelper.sendEBSWarning(player, game, tile.getPosition());
            ButtonHelper.checkForIonStorm(tile, player);
            TeHelperGeneral.addStationsToPlayArea(event, game, tile);
            ButtonHelperFactionSpecific.checkForStymie(game, player, tile);
            ButtonHelper.checkFleetInEveryTile(player, game);
            if (!game.isFowMode()) {
                ButtonHelper.updateMap(game, event, "Post Movement For " + player.getFactionEmoji());
            }
        }
    }

    public static void tacticalActionSpaceCannonOffenceStep(
            Game game, Player player, List<Player> playersWithPds2, Tile tile) {
        if (game.isFowMode()) {
            String title = "### Space Cannon Offence " + UnitEmojis.pds + "\n";
            if (playersWithPds2.size() > 1 || !playersWithPds2.contains(player)) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        title
                                + "There " + (playersWithPds2.size() == 1 ? "is " : "are ") + playersWithPds2.size()
                                + " player" + (playersWithPds2.size() == 1 ? "" : "s")
                                + " with Space Cannon Offence coverage in this system.\n"
                                + "Please resolve those before continuing, or float the window if irrelevant.");
            }
            List<Button> spaceCannonButtons = StartCombatService.getSpaceCannonButtons(game, player, tile);
            spaceCannonButtons.add(
                    Buttons.red("declinePDS_" + tile.getTileID() + "_" + player.getFaction(), "Decline PDS"));
            for (Player playerWithPds : playersWithPds2) {
                MessageHelper.sendMessageToChannelWithButtons(
                        playerWithPds.getCorrectChannel(),
                        title + playerWithPds.getRepresentationUnfogged() + ", you have SPACE CANNON coverage in "
                                + tile.getRepresentation() + ", use buttons to resolve:",
                        spaceCannonButtons);
            }
        } else {
            StartCombatService.sendSpaceCannonButtonsToThread(player.getCorrectChannel(), game, player, tile);
        }
    }

    @ButtonHandler("tacticalAction")
    public static void selectRingThatActiveSystemIsIn(Player player, Game game, ButtonInteractionEvent event) {
        if (!player.isActivePlayer() && game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "## " + player.getFactionEmoji() + " is not the active player.");
            return;
        }
        if (player.getTacticalCC() < 1) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getFactionEmoji() + " does not have any command tokens in their tactic pool.");
            return;
        }
        resetStoredValuesForTacticalAction(game);
        game.removeStoredValue("fortuneSeekers");
        beginTacticalAction(game, player);
    }

    public static void resetStoredValuesForTacticalAction(Game game) {
        game.setNaaluAgent(false);
        game.setWarfareAction(false);
        game.setL1Hero(false);
        game.removeStoredValue("violatedSystems");
        game.removeStoredValue("vaylerianHeroActive");
        game.removeStoredValue("tnelisCommanderTracker");
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue("ASN" + player.getFaction());
        }
        game.removeStoredValue("planetsTakenThisRound");
        game.removeStoredValue("hiredGunsInPlay");
        game.removeStoredValue("allianceModeSimultaneousAction");
        game.removeStoredValue("absolLux");
        game.removeStoredValue("mentakHero");
        game.removeStoredValue("ghostagent_active");

        game.resetCurrentMovedUnitsFrom1TacticalAction();
        game.getTacticalActionDisplacement().clear();
    }

    public static void beginTacticalAction(Game game, Player player) {
        boolean prefersDistanceBasedTacticalActions =
                UserSettingsManager.get(player.getUserID()).isPrefersDistanceBasedTacticalActions();
        if (!game.isFowMode() && game.getRingCount() < 5 && prefersDistanceBasedTacticalActions) {
            alternateWayOfOfferingTiles(player, game);
        } else {
            String message =
                    "Doing a tactical action. Please choose the ring of the map that the system you wish to activate is located in.";
            if (!game.isFowMode()) {
                message +=
                        " Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Mecatol Rex. The Wormhole Nexus is in the corner.";
            }
            List<Button> ringButtons = ButtonHelper.getPossibleRings(player, game);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, ringButtons);
        }
    }

    private static void alternateWayOfOfferingTiles(Player player, Game game) {
        Map<String, Integer> distances =
                CheckDistanceHelper.getTileDistancesRelativeToAllYourUnlockedTiles(game, player);
        List<String> initialOffering =
                new ArrayList<>(CheckDistanceHelper.getAllTilesACertainDistanceAway(game, player, distances, 0));
        int maxDistance = 0;
        List<Button> buttons = new ArrayList<>();
        String message =
                "Doing a tactical action. Please choose the system you wish to activate. Right now showing tiles ";
        if (initialOffering.size()
                        + CheckDistanceHelper.getAllTilesACertainDistanceAway(game, player, distances, 1)
                                .size()
                < 6) {
            initialOffering.addAll(CheckDistanceHelper.getAllTilesACertainDistanceAway(game, player, distances, 1));
            maxDistance = 1;
            message += "0-1 tiles away.";
        } else {
            message += "0 tiles away.";
        }
        for (String pos : initialOffering) {
            Tile tile = game.getTileByPosition(pos);
            if (ButtonHelper.canActivateTile(game, player, tile)) {
                buttons.add(Buttons.green(
                        "ringTile_" + pos, tile.getRepresentationForButtons(game, player), tile.getTileEmoji(player)));
            }
        }
        buttons.add(Buttons.gray(
                "getTilesThisFarAway_" + (maxDistance + 1), "Get Tiles " + (maxDistance + 1) + " Spaces Away"));
        if (Constants.prisonerOneId.equals(player.getUserID()))
            buttons.addAll(ButtonHelper.getPossibleRings(player, game)); // TODO: Add option for this
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("getTilesThisFarAway_")
    public static void getTilesThisFarAway(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        int desiredDistance = Integer.parseInt(buttonID.split("_")[1]);
        Map<String, Integer> distances =
                CheckDistanceHelper.getTileDistancesRelativeToAllYourUnlockedTiles(game, player);
        List<Button> buttons = new ArrayList<>();
        if (desiredDistance > 0) {
            buttons.add(Buttons.gray(
                    "getTilesThisFarAway_" + (desiredDistance - 1),
                    "Get Tiles " + (desiredDistance - 1) + " Spaces Away"));
        }
        for (String pos :
                CheckDistanceHelper.getAllTilesACertainDistanceAway(game, player, distances, desiredDistance)) {
            Tile tile = game.getTileByPosition(pos);
            String tileRepresentation = tile.getRepresentationForButtons(game, player);
            if (ButtonHelper.canActivateTile(game, player, tile)) {
                buttons.add(Buttons.green("ringTile_" + pos, tileRepresentation, tile.getTileEmoji(player)));
            }
        }
        buttons.add(Buttons.gray(
                "getTilesThisFarAway_" + (desiredDistance + 1), "Get Tiles " + (desiredDistance + 1) + " Spaces Away"));

        String message = "Doing a tactical action. Please choose the system you wish to activate.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("ringTile_")
    public static void selectActiveSystem(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.replace("ringTile_", "");
        game.setActiveSystem(pos);
        game.setStoredValue("possiblyUsedRift", "");
        game.setStoredValue("lastActiveSystem", pos);
        List<Button> systemButtons = TacticalActionService.getTilesToMoveFrom(player, game, event);
        Tile tile = game.getTileByPosition(pos);
        if (FOWPlusService.isVoid(game, pos)) {
            tile = FOWPlusService.voidTile(pos);
        }
        StringBuilder message = new StringBuilder(player.getRepresentationUnfogged() + " activated "
                + tile.getRepresentationForButtons(game, player) + ".");

        if (!game.isFowMode()) {
            for (Player player_ : game.getRealPlayers()) {
                if (!game.isL1Hero()
                        && !player.getFaction().equalsIgnoreCase(player_.getFaction())
                        && !player_.isPlayerMemberOfAlliance(player)
                        && FoWHelper.playerHasUnitsInSystem(player_, tile)) {
                    message.append("\n").append(player_.getRepresentation()).append(" has units in the system.");
                }
            }
            for (UnitHolder planet : tile.getPlanetUnitHolders()) {
                if (player.getPlanets().contains(planet.getName())) {
                    continue;
                }
                for (String attachment : planet.getTokenList()) {
                    if (attachment.contains("sigma_weirdway")) {
                        message.append("\nSystem contains the Weirdway; applying -1 to the move value of your ships.");
                        break;
                    }
                }
            }
        } else {
            for (Player player_ : game.getRealPlayers()) {
                if (player_ == player
                        || !FoWHelper.getTilePositionsToShow(game, player_).contains(pos)) {
                    continue;
                }
                String playerMessage = player_.getRepresentationUnfogged() + " - System "
                        + tile.getRepresentationForButtons(game, player_)
                        + " has been activated by " + player.getFactionEmojiOrColor() + ".";
                MessageHelper.sendPrivateMessageToPlayer(player_, game, playerMessage);
            }
            ButtonHelper.resolveOnActivationEnemyAbilities(game, tile, player, false, event);
        }
        game.setStoredValue(
                "currentActionSummary" + player.getFaction(),
                game.getStoredValue("currentActionSummary" + player.getFaction()) + " Activated "
                        + tile.getRepresentationForButtons(game, player) + ".");
        if (game.playerHasLeaderUnlockedOrAlliance(player, "celdauricommander")
                && CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, UnitType.Spacedock)
                        .contains(tile)) {
            List<Button> buttons = new ArrayList<>();
            Button getCommButton = Buttons.blue("gain_1_comms", "Gain 1 Commodity", MiscEmojis.comm);
            buttons.add(getCommButton);
            String msg = player.getRepresentation()
                    + " you have Henry Storcher, the Celdauri Commander, and activated a system with your space dock. Please use the button to get a commodity.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        }

        List<Player> playersWithPds2 = ButtonHelper.tileHasPDS2Cover(player, game, pos);
        if (!game.isFowMode() && !playersWithPds2.isEmpty() && !game.isL1Hero()) {
            List<String> mentions = new ArrayList<>();
            for (Player playerWithPds : playersWithPds2) {
                if (playerWithPds == player) {
                    continue;
                }
                mentions.add(playerWithPds.getRepresentation());
            }
            if (!mentions.isEmpty()) {
                message.append("\n")
                        .append(player.getRepresentationUnfogged())
                        .append(" the activated system is in range of SPACE CANNON units owned by ")
                        .append(String.join(", ", mentions))
                        .append(".");
            }
        }

        if (tile.getPlanetUnitHolders().isEmpty()
                && ButtonHelper.doesPlayerHaveFSHere("mortheus_flagship", player, tile)
                && !tile.getUnitHolders().get("space").getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER))) {
            String msg = player.getRepresentationUnfogged()
                    + " automatically added 1 frontier token to the system due to the Particle Sieve (the Mortheus flagship).";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            AddTokenCommand.addToken(event, tile, Constants.FRONTIER, game);
        }

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message.toString());

        List<Button> button3 = ButtonHelperAgents.getL1Z1XAgentButtons(game, tile, player);
        if (player.hasUnexhaustedLeader("l1z1xagent") && !button3.isEmpty() && !game.isL1Hero()) {
            String msg = player.getRepresentationUnfogged() + ", you can use buttons to resolve "
                    + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                    + "I48S, the L1Z1Z" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                    + " agent, if you so wish.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, button3);
        }

        List<Button> button4 = ButtonHelperAgents.getTFAwakenButtons(game, tile, player);
        if (player.hasTech("tf-awaken") && !button3.isEmpty() && !game.isL1Hero()) {
            String msg = player.getRepresentationUnfogged()
                    + ", you can use these buttons to change an infantry into a PDS using awaken.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, button4);
        }

        if (tile.isAnomaly() && player.getActionCards().containsKey("harness")) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentation() + ", you activated an anomaly, and so could now play _Harness Energy_.");
        }

        if (FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)
                && player.getActionCards().containsKey("rally")) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentation()
                            + ", you activated another players ships, and so could now play _Rally_.");
        }

        List<Button> button2 = ButtonHelper.scanlinkResolution(player, tile, game);
        if ((player.hasTech("sdn") || player.hasTech("absol_sdn") || player.hasTech("wavelength"))
                && !button2.isEmpty()
                && !game.isL1Hero()) {
                    if(game.isTwilightsFallMode()){
                        MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", Please resolve a wavelength explore.",
                    button2);
                    }else{
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", Please resolve _Scanlink Drone Network_.",
                    button2);
                    }
        }
        if (!game.isL1Hero()) {
            // All players get to use Magen
            Tile activeSystem = tile;
            for (Player magenPlayer : game.getPlayers().values()) {
                boolean has =
                        activeSystem.containsPlayersUnitsWithModelCondition(magenPlayer, UnitModel::getIsStructure);
                if (magenPlayer.hasAbility("byssus")) {
                    for (UnitHolder planet : activeSystem.getPlanetUnitHolders()) {
                        if (planet.getUnitCount(UnitType.Mech, magenPlayer) > 0) {
                            has = true;
                        }
                    }
                }
                for (UnitHolder p : activeSystem.getPlanetUnitHolders()) {
                    for (String token : p.getTokenList()) {
                        if (magenPlayer.getPlanets().contains(p.getName()) && token.contains("superweapon")) {
                            has = true;
                            break;
                        }
                    }
                }
                if (!has || !magenPlayer.hasTech("md")) continue;

                String id = magenPlayer.finChecker() + "useMagenDefense_" + activeSystem.getPosition();
                Button useMagen = Buttons.red(id, "Use Magen Defense Grid", TechEmojis.WarfareTech);
                String magenMsg = magenPlayer.getRepresentation()
                        + " you can, and must, use _Magen Defense Grid_ to place an infantry with each of your structures in the active system.";
                MessageHelper.sendMessageToChannelWithButton(magenPlayer.getCorrectChannel(), magenMsg, useMagen);
            }
        }
        if (player.hasAbility("awaken")
                || player.hasUnit("titans_flagship")
                || player.hasUnit("sigma_ul_flagship_1")
                || player.hasUnit("sigma_ul_flagship_2")) {
            ButtonHelper.resolveTitanShenanigansOnActivation(player, game, tile, event);
        }
        if (player.hasAbility("plague_reservoir") && player.hasTech("dxa")) {
            for (Planet planetUH : tile.getPlanetUnitHolders()) {
                String planet = planetUH.getName();
                if (player.getPlanetsAllianceMode().contains(planetUH.getName())) {
                    String msg10 = player.getRepresentationUnfogged()
                            + " when you get to the invasion step of the tactical action, you may have an opportunity to use _Dacxive Animators_ on "
                            + Helper.getPlanetRepresentation(planet, game)
                            + ". Only use this on one planet, per the **Plague Reservoir** ability.";
                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCorrectChannel(), msg10, ButtonHelper.getDacxiveButtons(planet, player));
                }
            }
        }

        if (player.hasAbility("plague_reservoir") && player.hasTech("dsvaylr")) {
            for (Planet planetUH : tile.getPlanetUnitHolders()) {
                String planet = planetUH.getName();
                if (player.getPlanetsAllianceMode().contains(planetUH.getName())) {
                    String msg10 = player.getRepresentationUnfogged()
                            + " when you get to the invasion step of the tactical action, you may have an opportunity to use _Scavenger Exos_ on "
                            + Helper.getPlanetRepresentation(planet, game)
                            + ". Only use this on one planet, per the **Plague Reservoir** ability.";
                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCorrectChannel(), msg10, ButtonHelper.getScavengerExosButtons(player));
                    break;
                }
            }
        }

        // Send buttons to move
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please choose the first system you wish to move from.",
                systemButtons);

        // Resolve other abilities
        if (player.hasAbility("recycled_materials")) {
            List<Button> buttons = ButtonHelperFactionSpecific.getRohDhnaRecycleButtons(game, tile, player);
            if (!buttons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(), "Please choose which unit to recycle.", buttons);
            }
        }
        if (player.hasRelic("absol_plenaryorbital")
                && !tile.isHomeSystem(game)
                && !tile.isMecatol()
                && !player.hasUnit("plenaryorbital")) {
            List<Button> buttons4 = ButtonHelper.getAbsolOrbitalButtons(game, tile, player);
            if (!buttons4.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(), "You can place down the _Plenary Orbital_.", buttons4);
            }
        }
        if (VoidTetherService.meetsCriteria(game, player, tile)) {
            VoidTetherService.postInitialButtons(game, player, tile);
        }

        if (!game.isFowMode()) {
            if (!game.isL1Hero()) {
                ButtonHelper.resolveOnActivationEnemyAbilities(game, tile, player, false, event);
            }
        }
        game.removeStoredValue("crucibleBoost");
        game.removeStoredValue("flankspeedBoost");
        game.removeStoredValue("baldrickGDboost");
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getButtonsForAllUnitsInSystem(Player player, Game game, Tile tile, String moveOrRemove) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        List<UnitType> movableFromPlanets = new ArrayList<>(List.of(UnitType.Infantry, UnitType.Mech));
        if (player.hasTech("ffac2")) {
            movableFromPlanets.add(UnitType.Spacedock);
        }
        boolean remove = "remove".equalsIgnoreCase(moveOrRemove);

        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        for (Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String planetName = planetRepresentations.get(name);
            if (planetName == null) {
                planetName = name;
            }
            UnitHolder unitHolder = entry.getValue();
            for (UnitKey unitKey : unitHolder.getUnitKeys()) {
                if (!player.unitBelongsToPlayer(unitKey)) {
                    boolean belongsToUnlockedAlly = false;
                    UnitType uT = unitKey.getUnitType();
                    if (uT == UnitType.Infantry || uT == UnitType.Fighter || uT == UnitType.Mech) {
                        for (Player p2 : game.getRealPlayers()) {
                            if (p2.unitBelongsToPlayer(unitKey)
                                    && player.getAllianceMembers().contains(p2.getFaction())
                                    && !tile.hasPlayerCC(p2)) {
                                belongsToUnlockedAlly = true;
                            }
                        }
                    }
                    if (!belongsToUnlockedAlly) {
                        continue;
                    }
                }
                if (unitHolder instanceof Planet && !(movableFromPlanets.contains(unitKey.getUnitType()))) continue;

                List<Integer> states = unitHolder.getUnitsByState().get(unitKey);
                for (UnitState state : UnitState.values()) {
                    int amt = states.get(state.ordinal());
                    for (int x = 1; x <= Math.min(2, amt); x++) {
                        Button move = ButtonHelper.buildMoveUnitButton(
                                player, tile, unitHolder, state, unitKey, x, false, remove);
                        buttons.add(move);
                    }
                }
            }
        }

        if ("Remove".equalsIgnoreCase(moveOrRemove)) {
            buttons.add(Buttons.gray(
                    finChecker + "unitTacticalRemove_" + tile.getPosition() + "_removeAllShips", "Remove All Ships"));
            buttons.add(Buttons.gray(
                    finChecker + "unitTacticalRemove_" + tile.getPosition() + "_removeAll", "Remove All Units"));
            buttons.add(Buttons.blue(finChecker + "doneRemoving", "Done removing units"));
            return buttons;
        } else {
            if (game.playerHasLeaderUnlockedOrAlliance(player, "tneliscommander")
                    && game.getStoredValue("tnelisCommanderTracker").isEmpty())
                buttons.add(Buttons.blue(
                        "declareUse_Tnelis Commander_" + tile.getPosition(),
                        "Use Tnelis Commander",
                        FactionEmojis.tnelis));

            buttons.add(
                    Buttons.gray(finChecker + "unitTacticalMove_" + tile.getPosition() + "_moveAll", "Move All Units"));
            buttons.add(Buttons.blue(
                    finChecker + "doneWithOneSystem_" + tile.getPosition(), "Done Moving Units From This System"));
        }

        Map<String, Map<UnitKey, List<Integer>>> displacedUnits = game.getTacticalActionDisplacement();
        for (UnitHolder uh : tile.getUnitHolders().values()) {
            String uhKey = tile.getPosition() + "-" + uh.getName();
            if (!displacedUnits.containsKey(uhKey)) continue;

            Map<UnitKey, List<Integer>> unitsMovedFromUnitHolder = displacedUnits.get(uhKey);
            for (Entry<UnitKey, List<Integer>> entry : unitsMovedFromUnitHolder.entrySet()) {
                List<Integer> states = entry.getValue();
                for (UnitState state : UnitState.values()) {
                    int amt = states.get(state.ordinal());
                    for (int x = 1; x <= Math.min(2, amt); x++) {
                        Button reverse = ButtonHelper.buildMoveUnitButton(
                                player, tile, uh, state, entry.getKey(), x, true, false);
                        buttons.add(reverse);
                    }
                }
            }
        }
        if (!displacedUnits.isEmpty()) {
            Button validTile2 =
                    Buttons.green(finChecker + "unitTacticalMove_" + tile.getPosition() + "_reverseAll", "Undo All");
            buttons.add(validTile2);
        }
        return buttons;
    }
}
