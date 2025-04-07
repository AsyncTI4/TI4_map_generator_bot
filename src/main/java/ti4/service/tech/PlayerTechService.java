package ti4.service.tech;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.commandcounter.RemoveCommandCounterService;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper;
import ti4.helpers.DiscordantStarsHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.StringHelper;
import ti4.helpers.Units;
import ti4.helpers.ignis_aurora.IgnisAuroraHelperTechs;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.TemporaryCombatModifierModel;
import ti4.model.metadata.TechSummariesMetadataManager;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.turn.EndTurnService;
import ti4.service.turn.StartTurnService;
import ti4.service.unit.AddUnitService;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
public class PlayerTechService {

    public static void addTech(GenericInteractionCreateEvent event, Game game, Player player, String techID) {
        player.addTech(techID);
        ButtonHelperCommanders.resolveNekroCommanderCheck(player, techID, game);
        String message = player.getRepresentation() + " added technology: " + Mapper.getTech(techID).getRepresentation(false) + ".";
        if ("iihq".equalsIgnoreCase(AliasHandler.resolveTech(techID))) {
            message += "\nAutomatically added the Custodia Vigilia planet.";
        }
        CommanderUnlockCheckService.checkPlayer(player, "mirveda", "jolnar", "nekro", "dihmohn");
        MessageHelper.sendMessageToEventChannel(event, message);
    }

    public static void removeTech(GenericInteractionCreateEvent event, Player player, String techID) {
        player.removeTech(techID);
        MessageHelper.sendMessageToEventChannel(event, player.getRepresentation(false, false) + " removed technology: " + Mapper.getTech(techID).getRepresentation(false) + ".");
    }

    public static void purgeTech(GenericInteractionCreateEvent event, Player player, String techID) {
        player.purgeTech(techID);
        MessageHelper.sendMessageToEventChannel(event, player.getRepresentation(false, false) + " purged technology: " + Mapper.getTech(techID).getRepresentation(false) + ".");
    }

    public static void refreshTech(GenericInteractionCreateEvent event, Player player, String techID) {
        player.refreshTech(techID);
        MessageHelper.sendMessageToEventChannel(event, player.getRepresentation(false, false) + " readied technology: " + Mapper.getTech(techID).getRepresentation(false) + ".");
    }

    public static void exhaustTechAndResolve(GenericInteractionCreateEvent event, Game game, Player player, String tech) {
        String pos = "";
        if (tech.contains("dskortg")) {
            pos = tech.split("_")[1];
            tech = "dskortg";
        }
        String inf = "";
        if (tech.contains("_inf") && tech.contains("absol_aida")) {
            tech = tech.replace("_inf", "");
            inf = "inf";
        }
        TechnologyModel techModel = Mapper.getTech(tech);
        if (!player.getTechs().contains(tech)) {
            boolean hasSub = false;
            for (String tech2 : player.getTechs()) {
                if (tech2.contains(tech)) {
                    hasSub = true;
                    break;
                }
            }
            if (!hasSub) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " does not have the tech known as " + techModel.getName());
                return;
            }
        }
        String exhaustMessage = player.getRepresentation(false, false) + " exhausted technology " + techModel.getRepresentation(false) + ".";
        if (game.isShowFullComponentTextEmbeds()) {
            MessageHelper.sendMessageToChannelWithEmbed(player.getCorrectChannel(), exhaustMessage, techModel.getRepresentationEmbed());
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), exhaustMessage);
        }

        player.exhaustTech(tech);

        // Handle Ignis Aurora Techs
        if (tech.startsWith("baldrick_")) {
            IgnisAuroraHelperTechs.handleExhaustIgnisAuroraTech(event, game, player, tech);
            return;
        }

        switch (tech) {
            case "bs" -> { // Bio-stims
                ButtonHelper.sendAllTechsNTechSkipPlanetsToReady(game, event, player, false);
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "gls" -> { // Graviton
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation()
                    + " exhausted _Graviton Laser System_. The auto-assign hits button for SPACE CANNON OFFENSE fire will now kill fighters last.");
                game.setStoredValue(player.getFaction() + "graviton", "true");
                deleteTheOneButtonIfButtonEvent(event);
            }

            case "dsbenty" -> {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                    + " exhausted _Merged Replicators_ to increase the PRODUCTION value of one of their units by 2, or to match the largest PRODUCTION value on the game board.");
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "dsceldr" -> {
                ButtonHelper.celdauriRedTech(player, game, event);
                deleteTheOneButtonIfButtonEvent(event);
            }

            case "dscymiy" -> {
                List<Tile> tiles = new ArrayList<>();
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.playerHasUnitsInSystem(player, tile) && !tile.isHomeSystem() && !tile.isMecatol()) {
                        tiles.add(tile);
                    }
                }
                ButtonHelperFactionSpecific.resolveEdynAgendaStuffStep1(player, game, tiles);
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "absol_bs" -> { // Bio-stims
                ButtonHelper.sendAllTechsNTechSkipPlanetsToReady(game, event, player, true);
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "absol_x89" -> { // Absol X-89
                ButtonHelper.sendAbsolX89NukeOptions(game, event, player);
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "absol_dxa" -> { // Dacxive
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    "Use buttons to drop 2 infantry on a planet",
                    Helper.getPlanetPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "absol_nm" -> { // Absol's Neural Motivator
                deleteIfButtonEvent(event);
                Button draw2ACButton = Buttons.gray(player.getFinsFactionCheckerPrefix() + "draw2 AC", "Draw 2 Action Cards", CardEmojis.ActionCard);
                MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), "", draw2ACButton);
                //sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "dskortg" -> {
                Tile tile = null;
                if (!pos.isEmpty()) {
                    tile = game.getTileByPosition(pos);
                } else if (!game.getActiveSystem().isEmpty()) {
                    tile = game.getTileByPosition(game.getActiveSystem());
                }
                if (tile != null) {
                    String tileRep = tile.getRepresentationForButtons(game, player);
                    String ident = player.getFactionEmoji();
                    String msg = ident + " removed command token from " + tileRep + ".";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                    RemoveCommandCounterService.fromTile(event, player.getColor(), tile, game);
                }
            }
            case "td", "absol_td" -> // Transit Diodes
                ButtonHelper.resolveTransitDiodesStep1(game, player);
            case "miltymod_hm" -> { // MiltyMod Hyper Metabolism (Gain a CC)
                Button gainCC = Buttons.green(player.getFinsFactionCheckerPrefix() + "gain_CCdeletethismessage", "Gain Command Tokens");
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    player.getFactionEmojiOrColor() + " use button to gain 1 command token.", List.of(gainCC));
            }
            case "absol_hm" -> { // MiltyMod Hyper Metabolism (Gain a CC)
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "gain_CCdeletethismessage", "Gain Command Tokens"));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getFactionEmojiOrColor()
                    + " use button to gain 1 command token.", buttons);
            }
            case "aida", "sar", "htp", "absol_aida" -> {
                if (event instanceof ButtonInteractionEvent buttonEvent) {
                    tech = tech.replace("absol_", "");
                    ButtonHelper.deleteTheOneButton(buttonEvent);
                    if (buttonEvent.getButton().getLabel().contains("(")) {
                        player.addSpentThing(tech + "_");
                    } else {
                        player.addSpentThing(tech);
                    }
                    if (inf.isEmpty()) {
                        inf = "res";
                    }
                    String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, inf);
                    buttonEvent.getMessage().editMessage(exhaustedMessage).queue();
                }
            }
            case "pi", "absol_pi" -> { // Predictive Intelligence
                deleteTheOneButtonIfButtonEvent(event);
                Button deleteButton = Buttons.red("FFCC_" + player.getFaction() + "_deleteButtons",
                    "Delete These Buttons");
                String message = player.getRepresentation(false, true) + " use buttons to redistribute";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message,
                    List.of(Buttons.REDISTRIBUTE_CCs, deleteButton));
            }
            case "dsvadeb" -> ButtonHelperFactionSpecific.resolveVadenTgForSpeed(player, event);
            case "mi" -> { // Mageon
                deleteIfButtonEvent(event);
                List<Button> buttons = new ArrayList<>();
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player || p2.getAc() == 0) {
                        continue;
                    }
                    if (game.isFowMode()) {
                        buttons.add(Buttons.gray(player.getFinsFactionCheckerPrefix() + "getACFrom_" + p2.getFaction(), p2.getColor()));
                    } else {
                        Button button = Buttons.gray(player.getFinsFactionCheckerPrefix() + "getACFrom_" + p2.getFaction(), " ");
                        String factionEmojiString = p2.getFactionEmoji();
                        button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                        buttons.add(button);
                    }
                }
                String message = player.getRepresentationUnfogged() + ", please choose who you wish to target with _Mageon Implants_.";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "dsuydag" -> {
                deleteIfButtonEvent(event);
                ActionCardHelper.doRise(player, event, game);
                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
                Button doneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
                buttons.add(doneExhausting);
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Click the names of the planets you wish to exhaust to pay the required " + player.getPlanetsAllianceMode().size() + " influence.", buttons);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "dsuydab" -> {
                game.setDominusOrb(true);
                ButtonHelper.deleteMessage(event);
                String message = "Choose a system to move from.";
                List<Button> systemButtons = ButtonHelper.getTilesToMoveFrom(player, game, event);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
            }
            case "dsaxisy" -> {
                deleteIfButtonEvent(event);
                List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, "sd",
                    "placeOneNDone_skipbuild");
                String message = player.getRepresentationUnfogged()
                    + " select the planet you would like to place or move a space dock to.";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "dskolly" -> {
                deleteIfButtonEvent(event);
                if (event instanceof ButtonInteractionEvent bevent) {
                    ButtonHelperActionCards.resolveSeizeArtifactStep1(player, game, bevent, "yes");
                }
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "dskolug" -> {
                deleteIfButtonEvent(event);
                String message = player.getRepresentationUnfogged() + " stalled using _Applied Biothermics_.";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "vtx", "absol_vtx" -> { // Vortex
                deleteIfButtonEvent(event);
                List<Button> buttons = ButtonHelperFactionSpecific.getUnitButtonsForVortex(player, game, event);
                String message = player.getRepresentationUnfogged() + ", please select which unit you would like to capture.";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "wg" -> { // Wormhole Generator
                deleteIfButtonEvent(event);
                List<Button> buttons = new ArrayList<>(ButtonHelperFactionSpecific.getCreussIFFTypeOptions());
                String message = player.getRepresentationUnfogged() + " select the type of wormhole you wish to drop.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "absol_wg" -> { // Absol's Wormhole Generator
                deleteIfButtonEvent(event);
                List<Button> buttons = new ArrayList<>(ButtonHelperFactionSpecific.getCreussIFFTypeOptions());
                String message = player.getRepresentationUnfogged() + " select the type of wormhole you wish to drop.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    message, buttons);
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    message, buttons);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "pm" -> { // Production Biomes
                deleteIfButtonEvent(event);
                ButtonHelperFactionSpecific.resolveProductionBiomesStep1(player, game, event);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "lgf" -> { // Lazax Gate Folding
                if (CollectionUtils.containsAny(player.getPlanetsAllianceMode(), Constants.MECATOLS)) {
                    deleteIfButtonEvent(event);
                    AddUnitService.addUnits(event, game.getMecatolTile(), game, player.getColor(), "inf mr");
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getFactionEmoji() + " added 1 infantry to Mecatol Rex using Laxax Gate Folding");
                    sendNextActionButtonsIfButtonEvent(event, game, player);
                } else {
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getRepresentation() + " You do not control Mecatol Rex");
                    player.refreshTech("lgf");
                }
            }
            case "det", "absol_det" -> {
                deleteIfButtonEvent(event);
                DiceHelper.Die d1 = new DiceHelper.Die(5);

                String message = player.getRepresentation() + " Rolled a " + d1.getResult() + " and will thus place a ";
                if (d1.getResult() > 4) {
                    message += "blue backed tile";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                    DiscordantStarsHelper.drawBlueBackTiles(event, game, player, 1);
                } else {
                    message += "red backed tile";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                    DiscordantStarsHelper.drawRedBackTiles(event, game, player, 1);
                }
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "sr", "absol_sar" -> { // Sling Relay or Absol Self Assembley Routines
                deleteIfButtonEvent(event);
                List<Button> buttons = new ArrayList<>();
                List<Tile> tiles = new ArrayList<>(ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, Units.UnitType.Spacedock, Units.UnitType.PlenaryOrbital));
                if (player.hasUnit("ghoti_flagship")) {
                    tiles.addAll(ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, Units.UnitType.Flagship));
                }
                List<String> pos2 = new ArrayList<>();
                for (Tile tile : tiles) {
                    if (!pos2.contains(tile.getPosition())) {
                        String buttonID = "produceOneUnitInTile_" + tile.getPosition() + "_sling";
                        Button tileButton = Buttons.green(buttonID,
                            tile.getRepresentationForButtons(game, player));
                        buttons.add(tileButton);
                        pos2.add(tile.getPosition());
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    "Select which tile you would like to produce a ship in ", buttons);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "dsdihmy" -> { // Impressment Programs
                List<Button> buttons = ButtonHelper.getButtonsToExploreReadiedPlanets(player, game);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select a planet to explore",
                    buttons);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            default -> MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "> This technology is not automated. Please resolve manually.");
        }
    }

    public static void checkAndApplyCombatMods(GenericInteractionCreateEvent event, Player player, String techID) {
        TemporaryCombatModifierModel possibleCombatMod = CombatTempModHelper.getPossibleTempModifier(Constants.TECH,
            techID, player.getNumberOfTurns());
        if (possibleCombatMod != null) {
            player.addNewTempCombatMod(possibleCombatMod);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Combat modifier will be applied next time you push the combat roll button.");
        }
    }

    private static void deleteIfButtonEvent(GenericInteractionCreateEvent event) {
        if (event instanceof ButtonInteractionEvent) {
            ((ButtonInteractionEvent) event).getMessage().delete().queue();
        }
    }

    private static void sendNextActionButtonsIfButtonEvent(GenericInteractionCreateEvent event, Game game, Player player) {
        if (event instanceof ButtonInteractionEvent) {
            ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
        }
    }

    public static void deleteTheOneButtonIfButtonEvent(GenericInteractionCreateEvent event) {
        if (event instanceof ButtonInteractionEvent) {
            ButtonHelper.deleteTheOneButton(event);
        }
    }

    public static void getTech(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String ident = player.getRepresentationNoPing();
        boolean isResearch = !buttonID.contains("__comp");
        boolean isStrat = !buttonID.contains("__comp");
        boolean paymentRequired = !buttonID.contains("__noPay");

        List<String> buttonIDComponents = Arrays.asList(buttonID.split("__"));
        buttonID = buttonIDComponents.getFirst();
        String paymentType = buttonIDComponents.size() > 1 ? buttonIDComponents.get(1) : "res";

        if (buttonIDComponents.contains("comp")) {
            isResearch = false;
            isStrat = false;
        }

        String techID = StringUtils.substringAfter(buttonID, "getTech_");
        techID = AliasHandler.resolveTech(techID);
        if (!Mapper.isValidTech(techID)) {
            BotLogger.warning(new BotLogger.LogMessageOrigin(event), "`ButtonHelper.getTech` Invalid TechID in 'getTech_' Button: " + techID);
            return;
        }
        TechnologyModel techM = Mapper.getTech(techID);
        StringBuilder message = new StringBuilder(ident).append(" acquired the technology ")
            .append(techM.getRepresentation(false)).append(".");

        if (techM.getRequirements().isPresent() && techM.getRequirements().get().length() > 1) {
            CommanderUnlockCheckService.checkPlayer(player, "zealots");
        }
        player.addTech(techID);
        if (techM.isUnitUpgrade()) {
            if (player.hasUnexhaustedLeader("mirvedaagent") && player.getStrategicCC() > 0) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.gray("exhaustAgent_mirvedaagent_" + player.getFaction(), "Use Mirveda Agent", FactionEmojis.mirveda));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentationUnfogged()
                    + " you may use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Logic Machina, the Mirveda"
                    + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, by spending 1 command token from your strategy pool"
                    + " to research a technology of the same color as a prerequisite of the unit upgrade you just got.",
                    buttons);
            }
            if (player.hasAbility("obsessive_designs") && paymentRequired
                && "action".equalsIgnoreCase(game.getPhaseOfGame())) {
                String msg = player.getRepresentation()
                    + " due to your **Obsessive Designs** ability, you may use the PRODUCTION ability of a space dock in your home system"
                    + " to build units of the type you just upgraded, reducing the total cost by 2.";
                String generalMsg = player.getFactionEmojiOrColor()
                    + " has an opportunity to use their **Obsessive Designs** ability to build " + techM.getName()
                    + " at home";
                List<Button> buttons;
                Tile tile = game.getTile(AliasHandler.resolveTile(player.getFaction()));
                if (player.hasAbility("mobile_command")
                    && !ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, Units.UnitType.Flagship).isEmpty()) {
                    tile = ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, Units.UnitType.Flagship).getFirst();
                }
                if (tile == null) {
                    tile = player.getHomeSystemTile();
                }
                if (tile == null) {
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Could not find your home system, sorry bro.");
                }
                buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "obsessivedesigns", "place");
                int val = Helper.getProductionValue(player, game, tile, true);
                String message2 = msg + ButtonHelper.getListOfStuffAvailableToSpend(player, game) + "\n"
                    + "You have " + val + " PRODUCTION value in this system.";
                if (val > 0 && game.playerHasLeaderUnlockedOrAlliance(player, "cabalcommander")) {
                    message2 = message2
                        + ". You also have the That Which Molds Flesh, the Vuil'raith commander,"
                        + " which allows you to produce 2 fighters/infantry that don't count towards the PRODUCTION limit";
                }
                if (val > 0 && ButtonHelper.isPlayerElected(game, player, "prophecy")) {
                    message2 += ". And reminder that you have _Prophecy of Ixth_ and should produce 2 fighters if you wish to keep it. Its removal is not automated.";
                }
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), generalMsg);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message2 + ".");
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Produce Units", buttons);
            }
        }

        if (player.hasUnexhaustedLeader("zealotsagent")) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.gray("exhaustAgent_zealotsagent_" + player.getFaction(), "Use Rhodun Agent", FactionEmojis.zealots));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                    + " you may use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Priestess Tuh, the Rhodun"
                    + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                    + " agent, to produce 1 ship at home or in a system where you have a technology specialty planet.",
                buttons);
        }

        if (isResearch) {
            ButtonHelperFactionSpecific.resolveResearchAgreementCheck(player, techID, game);
        }
        ButtonHelperCommanders.resolveNekroCommanderCheck(player, techID, game);
        if ("iihq".equalsIgnoreCase(techID)) {
            message.append("\n Automatically added the Custodia Vigilia planet");
        }
        if ("cm".equalsIgnoreCase(techID) && game.getActivePlayer() != null && game.getActivePlayerID().equalsIgnoreCase(player.getUserID()) && !player.getSCs().contains(7)) {
            if (!game.isFowMode()) {
                GameMessageManager
                    .remove(game.getName(), GameMessageType.TURN)
                    .ifPresent(messageId -> game.getMainGameChannel().deleteMessageById(messageId).queue());
            }
            String text = player.getRepresentationUnfogged() + ", it is now your turn (your "
                + StringHelper.ordinal(player.getInRoundTurnCount()) + " turn of round " + game.getRound() + ").";
            Player nextPlayer = EndTurnService.findNextUnpassedPlayer(game, player);
            if (nextPlayer != null && !game.isFowMode()) {
                if (nextPlayer == player) {
                    text += "\n-# All other players are passed; you will take consecutive turns until you pass, ending the action phase.";
                } else {
                    String ping = UserSettingsManager.get(nextPlayer.getUserID()).isPingOnNextTurn() ? nextPlayer.getRepresentationUnfogged() : nextPlayer.getRepresentationNoPing();
                    text += "\n-# " + ping + " will start their turn once you've ended yours.";
                }
            }
            String buttonText = "Use buttons to do your turn. ";
            if (game.getName().equalsIgnoreCase("pbd1000") || game.getName().equalsIgnoreCase("pbd100two")) {
                buttonText += "Your strategy card initiative number is " + player.getSCs().toArray()[0] + ".";
            }
            List<Button> buttons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), text);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), buttonText, buttons);
        }
        CommanderUnlockCheckService.checkPlayer(player, "jolnar", "nekro", "mirveda", "dihmohn");

        if (!isStrat || game.isComponentAction() || !"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message.toString());
        } else {
            ButtonHelper.sendMessageToRightStratThread(player, game, message.toString(), "technology");
            TechSummariesMetadataManager.addTech(game, player, techID, false);
        }
        if (paymentRequired) {
            payForTech(game, player, event, techID, paymentType);
        } else {
            if (player.hasLeader("zealotshero") && player.getLeader("zealotshero").get().isActive()) {
                if (game.getStoredValue("zealotsHeroTechs").isEmpty()) {
                    game.setStoredValue("zealotsHeroTechs", techID);
                } else {
                    game.setStoredValue("zealotsHeroTechs",
                        game.getStoredValue("zealotsHeroTechs") + "-" + techID);
                }
            }
        }
        if (player.hasUnit("augers_mech") && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech") < 4) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getFactionEmoji() + " has the opportunity to DEPLOY an Iledrith (Ilyxum mech) on a legendary planet or planet with a technology specialty.");
            String message2 = player.getRepresentationUnfogged() + ", please use buttons to drop 1 mech on a legendary planet or planet with a technology specialty.";
            List<Button> buttons2 = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game, "mech", "placeOneNDone_skipbuild"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message2, buttons2);
        }

        ButtonHelper.deleteMessage(event);
    }

    public static void payForTech(Game game, Player player, ButtonInteractionEvent event, String tech, final String payWith) {
        String trueIdentity = player.getRepresentationUnfogged();
        String message2 = trueIdentity + " Click the names of the planets you wish to exhaust. ";
        String payType = payWith != null ? payWith : "res";
        if (!payType.equals("res") && !payType.equals("inf") && !payType.equals("tgsonly")) {
            payType = "res";
        }
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, payType + "tech");
        TechnologyModel techM = Mapper.getTechs().get(AliasHandler.resolveTech(tech));
        if (techM.isUnitUpgrade() && player.hasTechReady("aida")) {
            Button aiDEVButton = Buttons.red("exhaustTech_aida", "Exhaust AI Development Algorithm");
            buttons.add(aiDEVButton);
        }
        if (techM.isUnitUpgrade() && player.hasTechReady("absol_aida")) {
            String inf = "";
            if (payType.equalsIgnoreCase("inf")) {
                inf = "_inf";
            }
            Button aiDEVButton = Buttons.red("exhaustTech_absol_aida" + inf, "Exhaust AI Development Algorithm");
            buttons.add(aiDEVButton);
        }
        if (!techM.isUnitUpgrade() && player.hasAbility("iconoclasm")) {

            for (int x = 1; x < player.getCrf() + 1; x++) {
                Button transact = Buttons.blue("purge_Frags_CRF_" + x, "Purge Cultural Fragments (" + x + ")", ExploreEmojis.CFrag);
                buttons.add(transact);
            }

            for (int x = 1; (x < player.getIrf() + 1 && x < 4); x++) {
                Button transact = Buttons.green("purge_Frags_IRF_" + x, "Purge Industrial Fragments (" + x + ")", ExploreEmojis.IFrag);
                buttons.add(transact);
            }

            for (int x = 1; (x < player.getHrf() + 1 && x < 4); x++) {
                Button transact = Buttons.red("purge_Frags_HRF_" + x, "Purge Hazardous Fragments (" + x + ")", ExploreEmojis.HFrag);
                buttons.add(transact);
            }

            for (int x = 1; x < player.getUrf() + 1; x++) {
                Button transact = Buttons.gray("purge_Frags_URF_" + x, "Purge Frontier Fragments (" + x + ")", ExploreEmojis.UFrag);
                buttons.add(transact);
            }

        }
        if (player.hasTechReady("is")) {
            Button inheritanceSystemsButton = Buttons.gray("exhaustTech_is", "Exhaust Inheritance Systems");
            buttons.add(inheritanceSystemsButton);
        }
        if (player.hasRelicReady("prophetstears")) {
            buttons.add(Buttons.red("prophetsTears_AC", "Exhaust Prophet's Tears for Action Card"));
            buttons.add(Buttons.red("prophetsTears_TechSkip", "Exhaust Prophet's Tears for Technology Prerequisite"));
        }
        if (player.hasExternalAccessToLeader("jolnaragent") || player.hasUnexhaustedLeader("jolnaragent")) {
            buttons.add(Buttons.gray("exhaustAgent_jolnaragent", "Use Jol-Nar Agent", FactionEmojis.Jolnar));
        }
        if (player.hasUnexhaustedLeader("veldyragent")) {
            buttons.add(Buttons.red("exhaustAgent_veldyragent_" + player.getFaction(), "Use Veldyr Agent", FactionEmojis.veldyr));
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "yincommander")) {
            buttons.add(Buttons.gray("yinCommanderStep1_", "Remove Infantry via Yin Commander", FactionEmojis.Yin));
        }
        buttons.add(Buttons.red("deleteButtons_technology", "Done Exhausting Planets"));
        if (!player.hasAbility("propagation")) {
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        }
        if (ButtonHelper.isLawInPlay(game, "revolution")) {
            MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(),
                player.getRepresentation() + ", due to the _Anti-Intellectual Revolution_ law, you now have to destroy a non-fighter ship (if you __researched__ the technology you just acquired).",
                Buttons.gray("getModifyTiles", "Modify Units"));
        }
    }
}
