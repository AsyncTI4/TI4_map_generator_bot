package ti4.service.tech;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.commands.units.AddUnits;
import ti4.commands2.commandcounter.RemoveCommandCounterService;
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
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.ignis_aurora.IgnisAuroraHelperTechs;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.TemporaryCombatModifierModel;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.turn.StartTurnService;

@UtilityClass
public class PlayerTechService {

    public static void addTech(GenericInteractionCreateEvent event, Game game, Player player, String techID) {
        player.addTech(techID);
        ButtonHelperCommanders.resolveNekroCommanderCheck(player, techID, game);
        String message = player.getRepresentation() + " added tech: " + Mapper.getTech(techID).getRepresentation(false);
        if ("iihq".equalsIgnoreCase(AliasHandler.resolveTech(techID))) {
            message = message + "\n Automatically added the Custodia Vigilia planet";
        }
        CommanderUnlockCheckService.checkPlayer(player, "mirveda", "jolnar", "nekro", "dihmohn");
        MessageHelper.sendMessageToEventChannel(event, message);
    }

    public static void removeTech(GenericInteractionCreateEvent event, Player player, String techID) {
        player.removeTech(techID);
        MessageHelper.sendMessageToEventChannel(event, player.getRepresentation() + " removed tech: " + Mapper.getTech(techID).getRepresentation(false));
    }

    public static void refreshTech(GenericInteractionCreateEvent event, Player player, String techID) {
        player.refreshTech(techID);
        MessageHelper.sendMessageToEventChannel(event, player.getRepresentation() + " readied tech: " + Mapper.getTech(techID).getRepresentation(false));
    }

    public static void exhaustTechAndResolve(GenericInteractionCreateEvent event, Game game, Player player, String tech) {
        String pos = "";
        if (tech.contains("dskortg")) {
            pos = tech.split("_")[1];
            tech = "dskortg";
        }
        TechnologyModel techModel = Mapper.getTech(tech);
        String exhaustMessage = player.getRepresentation() + " exhausted tech " + techModel.getRepresentation(false);
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
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " exhausted Graviton Laser System. The auto assign hit buttons for PDS fire will now kill fighters last");
                game.setStoredValue(player.getFaction() + "graviton", "true");
                deleteTheOneButtonIfButtonEvent(event);
            }

            case "dsbenty" -> {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " exhausted Merged Replicators to increase the production value of one of their units by 2, or to match the largest value on the board");
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
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Use buttons to drop 2 infantry on a planet",
                    Helper.getPlanetPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "absol_nm" -> { // Absol's Neural Motivator
                deleteIfButtonEvent(event);
                Button draw2ACButton = Buttons.gray(player.getFinsFactionCheckerPrefix() + "draw2 AC", "Draw 2 Action Cards", Emojis.ActionCard);
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
                    String msg = ident + " removed CC from " + tileRep;
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                    RemoveCommandCounterService.removeCC(event, player.getColor(), tile, game);
                }
            }
            case "td", "absol_td" -> { // Transit Diodes
                ButtonHelper.resolveTransitDiodesStep1(game, player);
            }
            case "miltymod_hm" -> { // MiltyMod Hyper Metabolism (Gain a CC)
                Button gainCC = Buttons.green(player.getFinsFactionCheckerPrefix() + "gain_CC", "Gain CC");
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    player.getFactionEmojiOrColor() + " use button to gain 1 CC:", List.of(gainCC));
            }
            case "absol_hm" -> { // MiltyMod Hyper Metabolism (Gain a CC)
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "gain_CC", "Gain CC"));
                if (player.getStrategicCC() > 0) {
                    for (Leader leader : player.getLeaders()) {
                        if (leader.isExhausted() && leader.getId().contains("agent")) {
                            buttons.add(Buttons.blue(
                                player.getFinsFactionCheckerPrefix() + "spendStratNReadyAgent_" + leader.getId(),
                                "Ready " + leader.getId()));
                        }
                    }
                    for (String relic : player.getExhaustedRelics()) {
                        if ("titanprototype".equalsIgnoreCase(relic) || "absol_jr".equalsIgnoreCase(relic)) {
                            buttons.add(Buttons.blue(
                                player.getFinsFactionCheckerPrefix() + "spendStratNReadyAgent_" + relic,
                                "Ready JR"));
                        }
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getFactionEmojiOrColor()
                    + " use button to gain 1 CC or spend 1 strat CC to ready your agent", buttons);
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
                    String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, "res");
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
            case "dsvadeb" -> {
                ButtonHelperFactionSpecific.resolveVadenTgForSpeed(player, event);
            }
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
                String message = player.getRepresentationUnfogged() + " Select who you would like to Mageon.";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                sendNextActionButtonsIfButtonEvent(event, game, player);
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
                String message = player.getRepresentationUnfogged() + " stalled using the Applied Biothermics tech.";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "vtx", "absol_vtx" -> { // Vortex
                deleteIfButtonEvent(event);
                List<Button> buttons = ButtonHelperFactionSpecific.getUnitButtonsForVortex(player, game, event);
                String message = player.getRepresentationUnfogged() + " Select what unit you would like to capture";
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
                    new AddUnits().unitParsing(event, player.getColor(), game.getMecatolTile(), "inf mr", game);
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
                    message = message + "blue backed tile";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                    DiscordantStarsHelper.drawBlueBackTiles(event, game, player, 1);
                } else {
                    message = message + "red backed tile";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                    DiscordantStarsHelper.drawRedBackTiles(event, game, player, 1);
                }
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "sr", "absol_sar" -> { // Sling Relay or Absol Self Assembley Routines
                deleteIfButtonEvent(event);
                List<Button> buttons = new ArrayList<>();
                List<Tile> tiles = new ArrayList<>(ButtonHelper.getTilesOfPlayersSpecificUnits(game, player,
                    Units.UnitType.Spacedock, Units.UnitType.CabalSpacedock, Units.UnitType.PlenaryOrbital));
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
            default -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "> This tech is not automated. Please resolve manually.");
            }
        }
    }

    public static void checkAndApplyCombatMods(GenericInteractionCreateEvent event, Player player, String techID) {
        TemporaryCombatModifierModel possibleCombatMod = CombatTempModHelper.getPossibleTempModifier(Constants.TECH,
            techID, player.getNumberTurns());
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
        String ident = player.getFactionEmoji();
        boolean paymentRequired = !buttonID.contains("__noPay");
        final String[] buttonIDComponents = buttonID.split("__");
        buttonID = buttonIDComponents[0];
        final String paymentType = buttonIDComponents.length > 1 ? buttonIDComponents[1] : "res";

        String techID = StringUtils.substringAfter(buttonID, "getTech_");
        techID = AliasHandler.resolveTech(techID);
        if (!Mapper.isValidTech(techID)) {
            BotLogger.log(event, "`ButtonHelper.getTech` Invalid TechID in 'getTech_' Button: " + techID);
            return;
        }
        TechnologyModel techM = Mapper.getTech(techID);
        StringBuilder message = new StringBuilder(ident).append(" acquired the technology: ")
            .append(techM.getRepresentation(false));

        if (techM.getRequirements().isPresent() && techM.getRequirements().get().length() > 1) {
            CommanderUnlockCheckService.checkPlayer(player, "zealots");
        }
        player.addTech(techID);
        if (techM.isUnitUpgrade()) {
            if (player.hasUnexhaustedLeader("mirvedaagent") && player.getStrategicCC() > 0) {
                List<Button> buttons = new ArrayList<>();
                Button mirvedaButton = Buttons.gray("exhaustAgent_mirvedaagent_" + player.getFaction(), "Use Mirveda Agent", Emojis.mirveda);
                buttons.add(mirvedaButton);
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentationUnfogged()
                        + " you may use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Logic Machina, the Mirveda"
                        + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, to spend 1 CC and research a tech of the same color as a prerequisite of the tech you just got.",
                    buttons);
            }
            if (player.hasAbility("obsessive_designs") && paymentRequired
                && "action".equalsIgnoreCase(game.getPhaseOfGame())) {
                String msg = player.getRepresentation()
                    + " due to your obsessive designs ability, you may use your space dock at home PRODUCTION ability to build units of the type you just upgraded, reducing the total cost by 2.";
                String generalMsg = player.getFactionEmojiOrColor()
                    + " has an opportunity to use their obsessive designs ability to build " + techM.getName()
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
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Could not find a HS, sorry bro");
                }
                buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "obsessivedesigns", "place");
                int val = Helper.getProductionValue(player, game, tile, true);
                String message2 = msg + ButtonHelper.getListOfStuffAvailableToSpend(player, game) + "\n"
                    + "You have " + val + " PRODUCTION value in this system";
                if (val > 0 && game.playerHasLeaderUnlockedOrAlliance(player, "cabalcommander")) {
                    message2 = message2
                        + ". You also have the That Which Molds Flesh, the Vuil'raith commander, which allows you to produce 2 fighters/infantry that don't count towards production limit";
                }
                if (val > 0 && ButtonHelper.isPlayerElected(game, player, "prophecy")) {
                    message2 = message2 + "Reminder that you have Prophecy of Ixth and should produce 2 fighters if you want to keep it. Its removal is not automated";
                }
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), generalMsg);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message2);
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Produce Units", buttons);
            }
        }

        if (player.hasUnexhaustedLeader("zealotsagent")) {
            List<Button> buttons = new ArrayList<>();
            Button zealotsButton = Buttons.gray("exhaustAgent_zealotsagent_" + player.getFaction(), "Use Zealots Agent", Emojis.zealots);
            buttons.add(zealotsButton);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                    + " you may use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Priestess Tuh, the Rhodun"
                    + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, to produce 1 ship at home or in a system where you have a tech skip planet.",
                buttons);
        }

        ButtonHelperFactionSpecific.resolveResearchAgreementCheck(player, techID, game);
        ButtonHelperCommanders.resolveNekroCommanderCheck(player, techID, game);
        if ("iihq".equalsIgnoreCase(techID)) {
            message.append("\n Automatically added the Custodia Vigilia planet");
        }
        if ("cm".equalsIgnoreCase(techID) && game.getActivePlayer() != null
            && game.getActivePlayerID().equalsIgnoreCase(player.getUserID()) && !player.getSCs().contains(7)) {
            if (!game.isFowMode()) {
                try {
                    if (game.getLatestTransactionMsg() != null && !game.getLatestTransactionMsg().isEmpty()) {
                        game.getMainGameChannel().deleteMessageById(game.getLatestTransactionMsg()).queue();
                        game.setLatestTransactionMsg("");
                    }
                } catch (Exception e) {
                    // Block of code to handle errors
                }
            }
            String text = player.getRepresentationUnfogged() + " UP NEXT";
            String buttonText = "Use buttons to do your turn. ";
            if (game.getName().equalsIgnoreCase("pbd1000") || game.getName().equalsIgnoreCase("pbd100two")) {
                buttonText = buttonText + "Your SC number is #" + player.getSCs().toArray()[0];
            }
            List<Button> buttons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), text);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), buttonText, buttons);
        }
        CommanderUnlockCheckService.checkPlayer(player, "jolnar", "nekro", "mirveda", "dihmohn");

        if (game.isComponentAction() || !"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message.toString());
        } else {
            ButtonHelper.sendMessageToRightStratThread(player, game, message.toString(), "technology");
            String key = "TechForRound" + game.getRound() + player.getFaction();
            if (game.getStoredValue(key).isEmpty()) {
                game.setStoredValue(key, techID);
            } else {
                game.setStoredValue(key, game.getStoredValue(key) + "." + techID);
            }
            postTechSummary(game);
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
                player.getFactionEmoji() + " has the opportunity to deploy an Augur mech on a legendary planet or planet with a tech skip");
            String message2 = player.getRepresentationUnfogged() + " Use buttons to drop 1 mech on a legendary planet or planet with a tech skip";
            List<Button> buttons2 = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game, "mech", "placeOneNDone_skipbuild"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message2, buttons2);
        }

        ButtonHelper.deleteMessage(event);
    }

    public static void postTechSummary(Game game) {
        if (game.isFowMode() || game.getTableTalkChannel() == null
            || !game.getStoredValue("TechSummaryRound" + game.getRound()).isEmpty() || game.isHomebrewSCMode()) {
            return;
        }
        StringBuilder msg = new StringBuilder("**__Tech Summary For Round " + game.getRound() + "__**\n");
        for (Player player : game.getRealPlayers()) {
            if (!player.hasFollowedSC(7)) {
                return;
            }
            String key = "TechForRound" + game.getRound() + player.getFaction();
            msg.append(player.getFactionEmoji()).append(":");
            String key2 = "RAForRound" + game.getRound() + player.getFaction();
            if (!game.getStoredValue(key2).isEmpty()) {
                msg.append("(From RA: ");
                if (game.getStoredValue(key2).contains(".")) {
                    for (String tech : game.getStoredValue(key2).split("\\.")) {
                        msg.append(" ").append(Mapper.getTech(tech).getNameRepresentation());
                    }

                } else {
                    msg.append(" ").append(Mapper.getTech(game.getStoredValue(key2)).getNameRepresentation());
                }
                msg.append(")");
            }
            if (!game.getStoredValue(key).isEmpty()) {
                if (game.getStoredValue(key).contains(".")) {
                    String tech1 = StringUtils.substringBefore(game.getStoredValue(key), ".");
                    String tech2 = StringUtils.substringAfter(game.getStoredValue(key), ".");
                    msg.append(" ").append(Mapper.getTech(tech1).getNameRepresentation());
                    for (String tech2Plus : tech2.split("\\.")) {
                        msg.append("and ").append(Mapper.getTech(tech2Plus).getNameRepresentation());
                    }

                } else {
                    msg.append(" ").append(Mapper.getTech(game.getStoredValue(key)).getNameRepresentation());
                }
                msg.append("\n");
            } else {
                msg.append(" Did not follow for tech\n");
            }
        }
        String key2 = "TechForRound" + game.getRound() + "Counter";
        if (game.getStoredValue(key2).equalsIgnoreCase("0")) {
            MessageHelper.sendMessageToChannel(game.getTableTalkChannel(), msg.toString());
            game.setStoredValue("TechSummaryRound" + game.getRound(), "yes");
        } else {
            if (game.getStoredValue(key2).isEmpty()) {
                game.setStoredValue(key2, "6");
            }
        }
    }

    /**
     * Generate buttons to pay for tech.
     * @param game
     * @param player
     * @param event
     * @param tech
     * @param payWith Possible values: {@code ["res", "inf"]}
     */
    public static void payForTech(Game game, Player player, ButtonInteractionEvent event, String tech, final String payWith) {
        String trueIdentity = player.getRepresentationUnfogged();
        String message2 = trueIdentity + " Click the names of the planets you wish to exhaust. ";
        String payType = payWith != null ? payWith : "res";
        if (!payType.equals("res") && !payType.equals("inf")) {
            payType = "res";
        }
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, payType + "tech");
        TechnologyModel techM = Mapper.getTechs().get(AliasHandler.resolveTech(tech));
        if (techM.isUnitUpgrade() && player.hasTechReady("aida")) {
            Button aiDEVButton = Buttons.red("exhaustTech_aida", "Exhaust AI Development Algorithm");
            buttons.add(aiDEVButton);
        }
        if (techM.isUnitUpgrade() && player.hasTechReady("absol_aida")) {
            Button aiDEVButton = Buttons.red("exhaustTech_absol_aida", "Exhaust AI Development Algorithm");
            buttons.add(aiDEVButton);
        }
        if (!techM.isUnitUpgrade() && player.hasAbility("iconoclasm")) {

            for (int x = 1; x < player.getCrf() + 1; x++) {
                Button transact = Buttons.blue("purge_Frags_CRF_" + x, "Purge Cultural Fragments (" + x + ")", Emojis.CFrag);
                buttons.add(transact);
            }

            for (int x = 1; (x < player.getIrf() + 1 && x < 4); x++) {
                Button transact = Buttons.green("purge_Frags_IRF_" + x, "Purge Industrial Fragments (" + x + ")", Emojis.IFrag);
                buttons.add(transact);
            }

            for (int x = 1; (x < player.getHrf() + 1 && x < 4); x++) {
                Button transact = Buttons.red("purge_Frags_HRF_" + x, "Purge Hazardous Fragments (" + x + ")", Emojis.HFrag);
                buttons.add(transact);
            }

            for (int x = 1; x < player.getUrf() + 1; x++) {
                Button transact = Buttons.gray("purge_Frags_URF_" + x, "Purge Frontier Fragments (" + x + ")", Emojis.UFrag);
                buttons.add(transact);
            }

        }
        if (player.hasTechReady("is")) {
            Button inheritanceSystemsButton = Buttons.gray("exhaustTech_is", "Exhaust Inheritance Systems");
            buttons.add(inheritanceSystemsButton);
        }
        if (player.hasRelicReady("prophetstears")) {
            Button pT1 = Buttons.red("prophetsTears_AC", "Exhaust Prophets Tears for AC");
            buttons.add(pT1);
            Button pT2 = Buttons.red("prophetsTears_TechSkip", "Exhaust Prophets Tears for Tech Skip");
            buttons.add(pT2);
        }
        if (player.hasExternalAccessToLeader("jolnaragent") || player.hasUnexhaustedLeader("jolnaragent")) {
            Button pT2 = Buttons.gray("exhaustAgent_jolnaragent", "Use Jol-Nar Agent", Emojis.Jolnar);
            buttons.add(pT2);
        }
        if (player.hasUnexhaustedLeader("veldyragent")) {
            Button winnuButton = Buttons.red("exhaustAgent_veldyragent_" + player.getFaction(), "Use Veldyr Agent", Emojis.veldyr);
            buttons.add(winnuButton);
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "yincommander")) {
            Button pT2 = Buttons.gray("yinCommanderStep1_", "Remove infantry via Yin Commander", Emojis.Yin);
            buttons.add(pT2);
        }
        Button doneExhausting = Buttons.red("deleteButtons_technology", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        if (!player.hasAbility("propagation")) {
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        }
        if (ButtonHelper.isLawInPlay(game, "revolution")) {
            MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(),
                player.getRepresentation()
                    + " Due to the Anti-Intellectual Revolution law, you now have to kill a non-fighter ship if you researched the tech you just acquired",
                Buttons.gray("getModifyTiles", "Modify Units"));
        }
    }
}
