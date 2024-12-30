package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands2.CommandHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.GenericCardModel;
import ti4.model.PlanetModel;
import ti4.model.TemporaryCombatModifierModel;
import ti4.model.UnitModel;
import ti4.model.metadata.AutoPingMetadataManager;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.turn.StartTurnService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class ActionCardHelper {

    public static void sendActionCardInfo(Game game, Player player) {
        // AC INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, getActionCardInfo(game, player));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "_ _\nClick a button below to play an Action Card", getPlayActionCardButtons(game, player));

        sendTrapCardInfo(game, player);
    }

    private static void sendTrapCardInfo(Game game, Player player) {
        if (player.hasAbility("cunning") || player.hasAbility("subterfuge")) { // Lih-zo trap abilities
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, getTrapCardInfo(player));
        }
    }

    private static String getTrapCardInfo(Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("_ _\n");
        sb.append("**Trap Cards:**").append("\n");
        int index = 1;
        Map<String, Integer> trapCards = player.getTrapCards();
        Map<String, String> trapCardsPlanets = player.getTrapCardsPlanets();
        if (trapCards != null) {
            if (trapCards.isEmpty()) {
                sb.append("> None");
            } else {
                for (Map.Entry<String, Integer> trapCard : trapCards.entrySet()) {
                    Integer value = trapCard.getValue();
                    sb.append("`").append(index).append(".").append(Helper.leftpad("(" + value, 4)).append(")`");
                    sb.append(getTrapCardRepresentation(trapCard.getKey(), trapCardsPlanets));
                    index++;
                }
            }
        }
        return sb.toString();
    }

    public static String getTrapCardRepresentation(String trapID, Map<String, String> trapCardsPlanets) {
        StringBuilder sb = new StringBuilder();
        GenericCardModel trap = Mapper.getTrap(trapID);
        String planet = trapCardsPlanets.get(trapID);

        sb.append(trap.getRepresentation());
        if (planet != null) {
            Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
            String representation = planetRepresentations.get(planet);
            if (representation == null) {
                representation = planet;
            }
            sb.append(" **__Planet: ").append(representation).append("**__");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String getActionCardInfo(Game game, Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("_ _\n");

        // ACTION CARDS
        sb.append("**Action Cards (").append(player.getAc()).append("/").append(ButtonHelper.getACLimit(game, player)).append("):**").append("\n");
        int index = 1;

        Map<String, Integer> actionCards = player.getActionCards();
        if (actionCards == null || actionCards.isEmpty()) {
            sb.append("> None");
            return sb.toString();
        }

        for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
            Integer value = ac.getValue();
            ActionCardModel actionCard = Mapper.getActionCard(ac.getKey());

            sb.append("`").append(index).append(".").append(Helper.leftpad("(" + value, 4)).append(")`");
            if (actionCard == null) {
                sb.append("Something broke here");
            } else {
                sb.append(actionCard.getRepresentation());
            }

            index++;
        }

        return sb.toString();
    }

    private static List<Button> getPlayActionCardButtons(Game game, Player player) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> actionCards = player.getActionCards();

        if (actionCards != null && !actionCards.isEmpty()
                && !ButtonHelper.isPlayerElected(game, player, "censure")
                && !ButtonHelper.isPlayerElected(game, player, "absol_censure")) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCard(key).getName();
                if (ac_name != null) {
                    acButtons.add(Buttons.red(Constants.AC_PLAY_FROM_HAND + value, "(" + value + ") " + ac_name, CardEmojis.ActionCard));
                }
            }
        }
        if (ButtonHelper.isPlayerElected(game, player, "censure") || ButtonHelper.isPlayerElected(game, player, "absol_censure")) {
            acButtons.add(Buttons.blue("getDiscardButtonsACs", "Discard an Action Card (You Are Politically Censured)"));
        } else {
            acButtons.add(Buttons.blue("getDiscardButtonsACs", "Discard an Action Card"));
        }
        if (actionCards != null && !actionCards.isEmpty() && !ButtonHelper.isPlayerElected(game, player, "censure")
                && (actionCards.containsKey("coup") || actionCards.containsKey("disgrace") || actionCards.containsKey("special_session")
                    || actionCards.containsKey("investments") || actionCards.containsKey("last_minute_deliberation") || actionCards.containsKey("revolution")
                    || actionCards.containsKey("deflection") || actionCards.containsKey("summit"))) {
            acButtons.add(Buttons.gray("checkForAllACAssignments", "Pre-Assign Action Cards"));
        }

        return acButtons;
    }

    public static List<Button> getActionPlayActionCardButtons(Player player) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null && !actionCards.isEmpty()) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCard(key).getName();
                ActionCardModel actionCard = Mapper.getActionCard(key);
                String actionCardWindow = actionCard.getWindow();
                if (ac_name != null && "Action".equalsIgnoreCase(actionCardWindow)) {
                    acButtons.add(Buttons.red(Constants.AC_PLAY_FROM_HAND + value, "(" + value + ") " + ac_name, CardEmojis.ActionCard));
                }
            }
        }
        return acButtons;
    }

    public static void sendDiscardActionCardButtons(Player player, boolean doingAction) {
        List<Button> buttons = getDiscardActionCardButtons(player, doingAction);
        String msg = player.getRepresentationUnfogged() + " use buttons to discard";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void sendDiscardAndDrawActionCardButtons(Player player) {
        List<Button> buttons = getDiscardActionCardButtonsWithSuffix(player, "redraw");
        String msg = player.getRepresentationUnfogged() + " use buttons to discard. A new action card will be automatically drawn afterwards.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static List<Button> getDiscardActionCardButtons(Player player, boolean doingAction) {
        return getDiscardActionCardButtonsWithSuffix(player, doingAction ? "stall" : "");
    }

    public static List<Button> getDiscardActionCardButtonsWithSuffix(Player player, String suffix) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> actionCards = player.getActionCards();

        if (actionCards != null && !actionCards.isEmpty()) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCard(key).getName();
                if (ac_name != null) {
                    acButtons.add(Buttons.blue("ac_discard_from_hand_" + value + suffix, "(" + value + ") " + ac_name, CardEmojis.ActionCard));
                }
            }
        }
        return acButtons;
    }

    public static List<Button> getToBeStolenActionCardButtons(Player player) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null && !actionCards.isEmpty()) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCard(key).getName();
                if (ac_name != null) {
                    acButtons.add(Buttons.red("takeAC_" + value + "_" + player.getFaction(), ac_name, CardEmojis.ActionCard));
                }
            }
        }
        return acButtons;
    }

    @ButtonHandler("refreshACInfo")
    public static void sendActionCardInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + CommandHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, headerText);
        sendActionCardInfo(game, player);
    }

    public static void discardAC(GenericInteractionCreateEvent event, Game game, Player player, int acNumericalID) {
        String acID = null;
        for (Map.Entry<String, Integer> ac : player.getActionCards().entrySet()) {
            if (ac.getValue().equals(acNumericalID)) {
                acID = ac.getKey();
            }
        }

        if (acID == null || !game.discardActionCard(player.getUserID(), acNumericalID)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No such Action Card ID found, please retry: " + acID);
            return;
        }
        String message = player.getRepresentationNoPing() + " discarded Action Card: " + Mapper.getActionCard(acID).getRepresentationJustName();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        sendActionCardInfo(game, player);
    }

    public void discardRandomAC(GenericInteractionCreateEvent event, Game game, Player player, int count) {
        if (count < 1) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Player: ").append(player.getUserName()).append(" - ");
        sb.append("Discarded Action Card:").append("\n");
        while (count > 0 && !player.getActionCards().isEmpty()) {
            Map<String, Integer> actionCards_ = player.getActionCards();
            List<String> cards_ = new ArrayList<>(actionCards_.keySet());
            Collections.shuffle(cards_);
            String acID = cards_.getFirst();
            boolean removed = game.discardActionCard(player.getUserID(), actionCards_.get(acID));
            if (!removed) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No such Action Cards found, please retry");
                return;
            }
            sb.append(Mapper.getActionCard(acID).getRepresentation()).append("\n");
            count--;
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
        sendActionCardInfo(game, player);
    }

    public static void drawActionCards(Game game, Player player, int count, boolean resolveAbilities) {
        if (count > 10) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "You probably shouldn't need to ever draw more than 10 cards, double check what you're doing please.");
            return;
        }
        String message = player.getRepresentation() + " drew " + count + " action card" + (count == 1 ? "" : "s") + ".";
        if (resolveAbilities && player.hasAbility("scheming")) {
            count++;
            message = player.getRepresentation() + " drew " + count + " action card" + (count == 1 ? "" : "s") + " (including one extra because of **Scheming**).";
        }
        if (resolveAbilities && player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, count);
            return;
        }
        game.drawActionCard(player.getUserID(), count);

        sendActionCardInfo(game, player);
        ButtonHelper.checkACLimit(game, player);
        if (resolveAbilities && player.hasAbility("scheming")) sendDiscardActionCardButtons(player, false);
        CommanderUnlockCheckService.checkPlayer(player, "yssaril");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
    }

    public static String resolveActionCard(GenericInteractionCreateEvent event, Game game, Player player, String acID, int acIndex, MessageChannel channel) {
        MessageChannel mainGameChannel = game.getMainGameChannel() == null ? channel : game.getMainGameChannel();
        ActionCardModel actionCard = Mapper.getActionCard(acID);
        String actionCardTitle = actionCard.getName();
        String actionCardWindow = actionCard.getWindow();

        String activePlayerID = game.getActivePlayerID();
        if (player.isPassed() && activePlayerID != null) {
            Player activePlayer = game.getPlayer(activePlayerID);
            if (activePlayer != null && activePlayer.hasTech("tp")) {
                return "You are passed and the active player owns _Transparasteel Plating_, preventing you from playing action cards. As such, the action card command has been cancelled.";
            }
        }
        if ("Action".equalsIgnoreCase(actionCardWindow) && game.getPlayer(activePlayerID) != player) {
            return "You are trying to play an action card with a component action, and the game does not think you are the active player."
            + " You may fix this with `/player turn_start`. Until then, you are #denied.";
        }
        if (ButtonHelper.isPlayerOverLimit(game, player)) {
            return player.getRepresentationUnfogged()
                + " The bot thinks you are over the limit and thus will not allow you to play action cards at this time."
                + " You may discard the action cards and manually resolve if you need to.";
        }

        if (player.hasAbility("cybernetic_madness")) {
            game.purgedActionCard(player.getUserID(), acIndex);
        } else {
            game.discardActionCard(player.getUserID(), acIndex);
        }

        String message = game.getPing() + ", " + (game.isFowMode() ? "someone" : player.getRepresentation())
            + " played the action card _" + actionCardTitle + "_.";

        List<Button> buttons = new ArrayList<>();
        Button sabotageButton = Buttons.red("sabotage_ac_" + actionCardTitle, "Cancel Action Card With Sabotage", MiscEmojis.Sabotage);
        buttons.add(sabotageButton);
        Player empy = Helper.getPlayerFromUnit(game, "empyrean_mech");
        if (empy != null && ButtonHelperFactionSpecific.isNextToEmpyMechs(game, player, empy) && !ButtonHelper.isLawInPlay(game, "articles_war")) {
            Button empyButton = Buttons.gray("sabotage_empy_" + actionCardTitle, "Cancel " + actionCardTitle + " With Watcher", UnitEmojis.mech);
            List<Button> empyButtons = new ArrayList<>();
            empyButtons.add(empyButton);
            Button refuse = Buttons.red("deleteButtons", "Delete These Buttons");
            empyButtons.add(refuse);
            MessageHelper.sendMessageToChannelWithButtons(empy.getCardsInfoThread(),
                empy.getRepresentationUnfogged()
                    + "You have one or more mechs adjacent to some units of the player who played _" + actionCardTitle + "_. Use buttons to decide whether to Sabo this action card.",
                empyButtons);
        }
        String instinctTrainingID = "it";
        for (Player player2 : game.getPlayers().values()) {
            if (!player.equals(player2) && player2.hasTechReady(instinctTrainingID) && player2.getStrategicCC() > 0) {
                List<Button> xxchaButtons = new ArrayList<>();
                xxchaButtons.add(Buttons.gray("sabotage_xxcha_" + actionCardTitle, "Cancel " + actionCardTitle + " With Instinct Training", FactionEmojis.Xxcha));
                xxchaButtons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(player2.getCardsInfoThread(), player2.getRepresentationUnfogged()
                    + ", you have _Instinct Training_ readied and a command token available in your strategy pool."
                    + " Use buttons to decide whether to Sabo _" + actionCardTitle + "_.", xxchaButtons);
            }

        }
        MessageEmbed acEmbed = actionCard.getRepresentationEmbed();
        Button noSabotageButton = Buttons.blue("no_sabotage", "No Sabotage", MiscEmojis.NoSabo);
        buttons.add(noSabotageButton);
        buttons.add(Buttons.gray(player.getFinsFactionCheckerPrefix() + "moveAlongAfterAllHaveReactedToAC_" + actionCardTitle, "Pause Timer While Waiting For Sabo"));
        if (acID.contains("sabo")) {
            MessageHelper.sendMessageToChannelWithEmbed(mainGameChannel, message, acEmbed);
        } else {
            String buttonLabel = "Resolve " + actionCardTitle;

            if (Helper.isSaboAllowed(game, player)) {
                MessageHelper.sendMessageToChannelWithEmbedsAndFactionReact(mainGameChannel, message, game, player, Collections.singletonList(acEmbed), buttons, true);
            } else {
                MessageHelper.sendMessageToChannelWithEmbed(mainGameChannel, message, acEmbed);
                StringBuilder noSabosMessage = new StringBuilder("> " + Helper.noSaboReason(game, player));
                boolean it = false, watcher = false;
                for (Player p : game.getRealPlayers()) {
                    if (p == player) continue;
                    if (game.isFowMode() || (!it && p.hasTechReady("it"))) {
                        noSabosMessage.append("\n> A player may have access to _Instinct Training_, so watch out.");
                        it = true;
                    }
                    if (game.isFowMode() || (!watcher && Helper.getPlayerFromUnit(game, "empyrean_mech") != null)) {
                        noSabosMessage.append("\n> A player may have access to a Watcher (Empyrean mech), so 𝓌𝒶𝓉𝒸𝒽 out.");
                        watcher = true;
                    }
                }
                MessageHelper.sendMessageToChannel(mainGameChannel, noSabosMessage.toString());
            }
            MessageChannel channel2 = player.getCorrectChannel();
            if (actionCardTitle.contains("Manipulate Investments")) {
                List<Button> scButtons = new ArrayList<>();
                for (int sc : game.getSCList()) {
                    Button button;
                    TI4Emoji scEmoji = CardEmojis.getSCBackFromInteger(sc);
                    if (scEmoji != CardEmojis.SCBackBlank) {
                        button = Buttons.gray(player.finChecker() + "increaseTGonSC_" + sc, Helper.getSCName(sc, game), scEmoji);
                    } else {
                        button = Buttons.gray(player.finChecker() + "deflectSC_" + sc, sc + " " + Helper.getSCName(sc, game));
                    }
                    scButtons.add(button);
                }
                scButtons.add(Buttons.red("deleteButtons", "Done Adding Trade Goods"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, player.getRepresentation()
                    + ", please use buttons to increase trade goods on strategy cards. Each button press adds 1 trade good.", scButtons);
            }
            if (actionCardTitle.contains("Deflection")) {
                List<Button> scButtons = new ArrayList<>();
                for (int sc : game.getSCList()) {
                    TI4Emoji scEmoji = CardEmojis.getSCBackFromInteger(sc);
                    Button button;
                    if (scEmoji != CardEmojis.SCBackBlank) {
                        button = Buttons.gray(player.finChecker() + "deflectSC_" + sc, Helper.getSCName(sc, game), scEmoji);
                    } else {
                        button = Buttons.gray(player.finChecker() + "deflectSC_" + sc, sc + " " + Helper.getSCName(sc, game));
                    }
                    scButtons.add(button);
                }
                MessageHelper.sendMessageToChannelWithButtons(channel2,
                    player.getRepresentation() + " Use buttons to choose which strategy card will be _Deflect_'d.",
                    scButtons);
            }

            if (actionCardTitle.contains("Archaeological Expedition")) {
                List<Button> scButtons = ButtonHelperActionCards.getArcExpButtons(game, player);
                MessageHelper.sendMessageToChannelWithButtons(channel2, player.getRepresentation() + " After checking for Sabos, use buttons to explore a planet type thrice and gain any fragments.", scButtons);
            }

            if (actionCardTitle.contains("Planetary Rigs")) {
                List<Button> acbuttons = ButtonHelperHeroes.getAttachmentSearchButtons(game, player);
                String msg = player.getRepresentation() + " After checking for Sabos, first declare what planet you mean to put an attachment on, then hit the button to resolve.";
                if (acbuttons.isEmpty()) {
                    msg = player.getRepresentation() + " there were no attachments found in the applicable exploration decks.";
                }
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, acbuttons);
            }

            String codedMsg = player.getRepresentation() + " After checking for Sabos, use buttons to resolve."
                + " Reminder that all card targets (besides technology research) should be declared now, before people decide on Sabos."
                + " Resolve " + actionCardTitle + ".";

            List<Button> codedButtons = new ArrayList<>();
            if (actionCardTitle.contains("Plagiarize")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "getPlagiarizeButtons", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Mining Initiative")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "miningInitiative", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Revolution")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "willRevolution", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Last Minute Deliberation")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "lastMinuteDeliberation", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Special Session")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveVeto", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("War Machine")) {
                player.addSpentThing("warmachine");
            }

            if (actionCardTitle.contains("Economic Initiative")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "economicInitiative", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Confounding Legal Text")) {
                codedButtons.add(Buttons.green("autoresolve_manual", buttonLabel));
                sendResolveMsgToMainChannel(codedMsg, codedButtons, player, game);
            }
            if (actionCardTitle.contains("Confusing Legal Text")) {
                codedButtons.add(Buttons.green("autoresolve_manual", buttonLabel));
                sendResolveMsgToMainChannel(codedMsg, codedButtons, player, game);
            }

            if (actionCardTitle.contains("Reveal Prototype")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveResearch", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Spatial Collapse")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "spatialCollapseStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Side Project")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "sideProject", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Brutal Occupation")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "brutalOccupation", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Stolen Prototype")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveResearch", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Skilled Retreat")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "retreat_" + game.getActiveSystem() + "_skilled", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Reparations")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveReparationsStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Distinguished Councilor")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveDistinguished", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Uprising")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveUprisingStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Tomb Raiders")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveTombRaiders", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Technological Breakthrough")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "technologicalBreakthrough", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Assassinate Representative")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveAssRepsStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Signal Jamming")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveSignalJammingStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Spy")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveSpyStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Political Stability")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolvePSStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Plague")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolvePlagueStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Experimental Battlestation")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveEBSStep1_" + game.getActiveSystem(), buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Blitz")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveBlitz_" + game.getActiveSystem(), buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Shrapnel Turrets")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveShrapnelTurrets_" + game.getActiveSystem(), buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Micrometeoroid Storm")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveMicrometeoroidStormStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Upgrade")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveUpgrade_" + game.getActiveSystem(), buttonLabel));
                if (game.getActiveSystem().isEmpty()) {
                    MessageHelper.sendMessageToChannel(channel2, "The active system is currently non-existant, so this card cannot be automated");
                } else {
                    MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
                }
            }
            if (actionCardTitle.contains("Infiltrate")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveInfiltrateStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg + ". Warning, this will not work if the player has already removed their structures", codedButtons);
            }
            if (actionCardTitle.contains("Emergency Repairs")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveEmergencyRepairs_" + game.getActiveSystem(), buttonLabel));
                if (game.getActiveSystem().isEmpty()) {
                    MessageHelper.sendMessageToChannel(channel2, "The active system is currently non-existant, so this card cannot be automated");
                } else {
                    MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
                }
            }
            if (actionCardTitle.contains("Insider Information")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveInsiderInformation", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Cripple Defenses")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveCrippleDefensesStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Impersonation")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveImpersonation", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Ancient Burial Sites")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveABSStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Salvage")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveSalvageStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Insubordination")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveInsubStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Frontline Deployment")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveFrontline", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Unexpected Action")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveUnexpected", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Data Archive")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveDataArchive", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Ancient Trade Routes")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveAncientTradeRoutes", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Flank Speed")) {
                game.setStoredValue("flankspeedBoost", "1");
            }
            if (actionCardTitle.contains("Sister Ship")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveSisterShip", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Boarding Party")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveBoardingParty", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCard.getAlias().equals("mercenary_contract")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveMercenaryContract", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Chain Reaction")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveChainReaction", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Rendezvous Point")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveRendezvousPoint", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Flawless Strategy")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveFlawlessStrategy", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Arms Deal")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveArmsDeal", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Defense Installation")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveDefenseInstallation", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Harness Energy")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveHarness", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("War Effort")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveWarEffort", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Free Trade Initiative")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveFreeTrade", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Preparation")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolvePreparation", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Summit")) {
                codedButtons.add(Buttons.green("resolveSummit", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Scuttle")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "startToScuttleAUnit_0", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Lucky Shot")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "startToLuckyShotAUnit_0", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Refit Troops")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveRefitTroops", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Seize Artifact")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveSeizeArtifactStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Diplomatic Pressure")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveDiplomaticPressureStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Renegotiation")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveDiplomaticPressureStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Decoy Operation")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveDecoyOperationStep1_" + game.getActiveSystem(), buttonLabel));
                if (game.getActiveSystem().isEmpty()) {
                    MessageHelper.sendMessageToChannel(channel2, "The active system is currently non-existant, so this card cannot be automated");
                } else {
                    MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
                }
            }
            if (actionCardTitle.contains("Reactor Meltdown")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveReactorMeltdownStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Unstable Planet")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveUnstableStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Ghost Ship")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveGhostShipStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Stranded Ship")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "strandedShipStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Tactical Bombardment")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveTacticalBombardmentStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Exploration Probe")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveProbeStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Rally")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveRally", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Industrial Initiative")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "industrialInitiative", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Repeal Law")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "getRepealLawButtons", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Divert Funding")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "getDivertFundingButtons", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Emergency Meeting")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveEmergencyMeeting", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Focused Research")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "focusedResearch", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Forward Supply Base")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "forwardSupplyBase", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Rise of a Messiah")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "riseOfAMessiah", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Veto")) {
                codedButtons.add(
                    Buttons.blue(player.getFinsFactionCheckerPrefix() + "resolveVeto", "Reveal next Agenda"));
                sendResolveMsgToMainChannel(codedMsg, codedButtons, player, game);
            }

            if (actionCardTitle.contains("Fighter Conscription")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "fighterConscription", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            TemporaryCombatModifierModel combatModAC = CombatTempModHelper.getPossibleTempModifier(Constants.AC, actionCard.getAlias(), player.getNumberTurns());
            if (combatModAC != null) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "applytempcombatmod__" + Constants.AC + "__" + actionCard.getAlias(), "Resolve " + actionCard.getName()));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardWindow.contains("After an agenda is revealed")) {
                List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no afters again.", game, afterButtons, "after");
                AutoPingMetadataManager.delayPing(game.getName());

                String finChecker = "FFCC_" + player.getFaction() + "_";
                if (actionCard.getText().toLowerCase().contains("predict aloud")) {
                    List<Button> riderButtons = AgendaHelper.getAgendaButtons(actionCardTitle, game, finChecker);
                    MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, "Please select your prediction target.", game, player, riderButtons);
                }
                if (actionCardTitle.contains("Hack Election")) {
                    game.setHasHackElectionBeenPlayed(true);
                    Button setHack = Buttons.red("hack_election", "Set the voting order as normal");
                    List<Button> hackButtons = List.of(setHack);
                    MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, "Voting order reversed. Please hit this button if _Hack Election_ is Sabo'd.", game, player, hackButtons);
                }

            }
            if (actionCardWindow.contains("When an agenda is revealed") && !actionCardTitle.contains("Veto")) {
                AutoPingMetadataManager.delayPing(game.getName());
                List<Button> whenButtons = AgendaHelper.getWhenButtons(game);
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no whens again.", game, whenButtons, "when");
                List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel,
                    "Please indicate no afters again.", game, afterButtons, "after");
            }
            if ("Action".equalsIgnoreCase(actionCardWindow)) {
                game.setJustPlayedComponentAC(true);
                List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
                MessageHelper.sendMessageToChannelWithButtons(channel2, "Use buttons to end turn or do another action.", systemButtons);
                if (player.getLeaderIDs().contains("kelerescommander") && !player.hasLeaderUnlocked("kelerescommander")) {
                    String message2 = player.getRepresentationUnfogged() + " you may unleash Suffi An, your commander, by paying 1 trade good (if the action card isn't Sabo'd).";
                    List<Button> buttons2 = new ArrayList<>();
                    buttons2.add(Buttons.green("pay1tgforKeleres", "Pay 1 Trade Good to Unleash Suffi An", LeaderEmojis.KeleresAgent));
                    buttons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(channel2, message2, buttons2);
                }
                serveReverseEngineerButtons(game, player, List.of(acID));
            }
        }

        // Fog of war ping
        if (game.isFowMode()) {
            String fowMessage = player.getRepresentation() + " played an action card: _" + actionCardTitle + "_.";
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, fowMessage);
            MessageHelper.sendPrivateMessageToPlayer(player, game, "Played action card: _" + actionCardTitle + "_.");
        }
        if (player.hasUnexhaustedLeader("cymiaeagent") && player.getStrategicCC() > 0) {
            Button cymiaeButton = Buttons.gray("exhaustAgent_cymiaeagent_" + player.getFaction(), "Use Cymiae Agent", FactionEmojis.cymiae);
            MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(), player.getRepresentationUnfogged() 
                + ", you may use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Skhot Unit X-12, the Cymiae" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, to draw action card.", cymiaeButton);
        }

        sendActionCardInfo(game, player);
        return null;
    }

    public static void serveReverseEngineerButtons(Game game, Player discardingPlayer, List<String> actionCards) {
        for (Player player : game.getRealPlayers()) {
            if (player == discardingPlayer) continue;
            if (ButtonHelper.isPlayerElected(game, player, "censure")) continue;
            if (ButtonHelper.isPlayerElected(game, player, "absol_censure")) continue;

            String reverseEngineerID = "reverse_engineer";
            if (player.getActionCards().containsKey(reverseEngineerID)) {
                StringBuilder msg = new StringBuilder(player.getRepresentationUnfogged() + " you can use _Reverse Engineer_ on ");
                if (actionCards.size() > 1) msg.append("one of the following cards:");

                List<Button> reverseButtons = new ArrayList<>();
                String reversePrefix = Constants.AC_PLAY_FROM_HAND + player.getActionCards().get(reverseEngineerID) + "_reverse_";

                for (String acID : actionCards) {
                    ActionCardModel model = Mapper.getActionCard(acID);
                    if (!model.getWindow().toLowerCase().startsWith("action")) {
                        continue;
                    }

                    String id = reversePrefix + model.getName();
                    String label = "Reverse Engineer " + model.getName();
                    reverseButtons.add(Buttons.green(id, label, CardEmojis.ActionCard));
                    if (actionCards.size() == 1) msg.append(model.getName()).append(".");
                }

                if (!reverseButtons.isEmpty()) {
                    reverseButtons.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg.toString(), reverseButtons);
                }
            }
        }
    }

    private static void sendResolveMsgToMainChannel(String message, List<Button> buttons, Player player, Game game) {
        if (game.isFowMode()) {
            message = message.replace(player.getRepresentation(), "");
        }
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), message, buttons);
    }

    public static String playAC(GenericInteractionCreateEvent event, Game game, Player player, String value, MessageChannel channel) {
        String acID = null;
        int acIndex = -1;
        try {
            acIndex = Integer.parseInt(value);
            for (Map.Entry<String, Integer> so : player.getActionCards().entrySet()) {
                if (so.getValue().equals(acIndex)) {
                    acID = so.getKey();
                }
            }
        } catch (Exception e) {
            boolean foundSimilarName = false;
            String cardName = "";
            for (Map.Entry<String, Integer> ac : player.getActionCards().entrySet()) {
                String actionCardName = Mapper.getActionCard(ac.getKey()).getName();
                if (actionCardName != null) {
                    actionCardName = actionCardName.toLowerCase();
                    if (actionCardName.contains(value)) {
                        if (foundSimilarName && !cardName.equals(actionCardName)) {
                            return "Multiple cards with similar name founds, please use ID";
                        }
                        acID = ac.getKey();
                        acIndex = ac.getValue();
                        foundSimilarName = true;
                        cardName = actionCardName;
                    }
                }
            }
        }
        if (acID == null) {
            return "No such Action Card ID found, please retry";
        }
        return resolveActionCard(event, game, player, acID, acIndex, channel);
    }

    public static void sendActionCard(GenericInteractionCreateEvent event, Game game, Player player, Player p2, String acID) {
        Integer handIndex = player.getActionCards().get(acID);
        ButtonHelper.checkACLimit(game, p2);
        if (acID == null || handIndex == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find action card in your hand.");
            return;
        }
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find other player.");
            return;
        }

        player.removeActionCard(handIndex);
        p2.setActionCard(acID);
        sendActionCardInfo(game, player);
        sendActionCardInfo(game, p2);
    }

    public void sendRandomACPart2(GenericInteractionCreateEvent event, Game game, Player player, Player player_) {
        Map<String, Integer> actionCardsMap = player.getActionCards();
        List<String> actionCards = new ArrayList<>(actionCardsMap.keySet());
        if (actionCards.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No Action Cards in hand");
        }
        Collections.shuffle(actionCards);
        String acID = actionCards.getFirst();
        // FoW specific pinging
        if (game.isFowMode()) {
            FoWHelper.pingPlayersTransaction(game, event, player, player_, CardEmojis.ActionCard + " Action Card", null);
        }
        player.removeActionCard(actionCardsMap.get(acID));
        player_.setActionCard(acID);
        sendActionCardInfo(game, player_);
        ButtonHelper.checkACLimit(game, player_);
        sendActionCardInfo(game, player);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "# " + player.getRepresentation() + " you lost the action card _" + Mapper.getActionCard(acID).getName() + "_.");
        MessageHelper.sendMessageToChannel(player_.getCardsInfoThread(), "# " + player_.getRepresentation() + " you gained the action card _" + Mapper.getActionCard(acID).getName() +"_.");
    }

    public static void showAll(Player player, Player player_, Game game) {
        StringBuilder sb = new StringBuilder();
        StringBuilder sa = new StringBuilder();
        sa.append("You have shown your cards to player: ").append(player_.getUserName()).append("\n");
        sa.append("Your cards were presented in the order below. You may reference the number listed when discussing the cards:\n");
        sb.append("Game: ").append(game.getName()).append("\n");
        sb.append("Player: ").append(player.getUserName()).append("\n");
        sb.append("Showed Action Cards, they were also presented the cards in the order you see them so you may reference the number when talking to them:").append("\n");
        List<String> actionCards = new ArrayList<>(player.getActionCards().keySet());
        Collections.shuffle(actionCards);
        int index = 1;
        for (String id : actionCards) {
            sa.append(index).append(". ").append(Mapper.getActionCard(id).getRepresentation()).append("\n");
            sb.append(index).append(". ").append(Mapper.getActionCard(id).getRepresentation()).append("\n");
            index++;
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, sa.toString());
        MessageHelper.sendMessageToPlayerCardsInfoThread(player_, sb.toString());
    }

    public static String actionCardListCondensedNoIds(List<String> discards, String title) {
        StringBuilder sb = new StringBuilder();
        if (title != null) sb.append("**__").append(title).append(":__**");
        Map<String, List<String>> cardsByName = discards.stream()
            .collect(Collectors.groupingBy(ac -> Mapper.getActionCard(ac).getName()));
        int index = 1;

        List<Map.Entry<String, List<String>>> displayOrder = new ArrayList<>(cardsByName.entrySet());
        displayOrder.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, List<String>> acEntryList : displayOrder) {
            sb.append("\n").append(index).append(". ");
            sb.append(CardEmojis.ActionCard.toString().repeat(acEntryList.getValue().size()));
            sb.append(" **").append(acEntryList.getKey()).append("**");
            // sb.append(Mapper.getActionCard()
            index++;
        }
        return sb.toString();
    }

    public static void pickACardFromDiscardStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String acStringID : game.getDiscardActionCards().keySet()) {
            buttons.add(Buttons.green("pickFromDiscard_" + acStringID, Mapper.getActionCard(acStringID).getName()));
        }
        buttons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
        if (buttons.size() > 25) {
            buttons.add(25, Buttons.red("deleteButtons_", "Delete These Buttons"));
        }
        if (buttons.size() > 50) {
            buttons.add(50, Buttons.red("deleteButtons_2", "Delete These Buttons"));
        }
        if (buttons.size() > 75) {
            buttons.add(75, Buttons.red("deleteButtons_3", "Delete These Buttons"));
        }
        String msg = player.getRepresentationUnfogged() + ", use buttons to grab an action card from the discard pile.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void pickACardFromDiscardStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        ButtonHelper.deleteMessage(event);
        String acID = buttonID.replace("pickFromDiscard_", "");
        boolean picked = game.pickActionCard(player.getUserID(), game.getDiscardActionCards().get(acID));
        if (!picked) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
            return;
        }
        String msg2 = player.getRepresentationUnfogged() + " grabbed " + Mapper.getActionCard(acID).getName() + " from the discard";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);

        ActionCardHelper.sendActionCardInfo(game, player, event);
        if (player.hasAbility("autonetic_memory")) {
            String message;
            if (player.hasRelic("codex") || player.hasRelic("absol_codex")) {
                message = player.getRepresentationUnfogged() + ", if you did not just use _The Codex_ to get that action card,"
                    + " please discard 1 action card due to your **Cybernetic Madness** ability.";
            } else {
                message = player.getRepresentationUnfogged() + ", please discard 1 action card due to your **Cybernetic Madness** ability.";
            }
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, ActionCardHelper.getDiscardActionCardButtons(player, false));
        }
        ButtonHelper.checkACLimit(game, player);
    }

    public static void getActionCardFromDiscard(GenericInteractionCreateEvent event, Game game, Player player, int acIndex) {
        String acId = null;
        for (Map.Entry<String, Integer> ac : game.getDiscardActionCards().entrySet()) {
            if (ac.getValue().equals(acIndex)) {
                acId = ac.getKey();
            }
        }

        if (acId == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No such Action Card ID found, please retry");
            return;
        }
        boolean picked = game.pickActionCard(player.getUserID(), acIndex);
        if (!picked) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No such Action Card ID found, please retry");
            return;
        }
        String sb = "Game: " + game.getName() + " " +
            "Player: " + player.getUserName() + "\n" +
            "Picked card from Discards: " +
            Mapper.getActionCard(acId).getRepresentation() + "\n";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb);

        ActionCardHelper.sendActionCardInfo(game, player);
    }

    @ButtonHandler("riseOfAMessiah")
    public static void riseOfAMessiah(ButtonInteractionEvent event, Player player, Game game) {
        ActionCardHelper.doRise(player, event, game);
        ButtonHelper.deleteMessage(event);
    }

    public static void doRise(Player player, GenericInteractionCreateEvent event, Game game) {
        List<String> planets = player.getPlanetsAllianceMode();
        StringBuilder sb = new StringBuilder();
        sb.append(player.getRepresentationNoPing()).append(" added one ").append(UnitEmojis.infantry).append(" to each of: ");
        int count = 0;
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (planets.contains(unitHolder.getName())) {
                    Set<String> tokenList = unitHolder.getTokenList();
                    boolean ignorePlanet = false;
                    for (String token : tokenList) {
                        if (token.contains("dmz") || token.contains(Constants.WORLD_DESTROYED_PNG) || token.contains("arcane_shield")) {
                            ignorePlanet = true;
                            break;
                        }
                    }
                    if (ignorePlanet) {
                        continue;
                    }
                    AddUnitService.addUnits(event, tile, game, player.getColor(), "inf " + unitHolder.getName());
                    PlanetModel planetModel = Mapper.getPlanet(unitHolder.getName());
                    if (planetModel != null) {
                        sb.append("\n> ").append(Helper.getPlanetRepresentationPlusEmoji(unitHolder.getName()));
                        count++;
                    }
                }
            }
        }
        if (count == 0) {
            sb = new StringBuilder(player.getRepresentationNoPing()).append(" did not have any planets which could receive +1 infantry");
        } else if (count > 5) {
            sb.append("\n> Total of ").append(count);
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
    }

    @ButtonHandler("fighterConscription")
    public static void fighterConscription(ButtonInteractionEvent event, Player player, Game game) {
        ActionCardHelper.doFfCon(event, player, game);
        ButtonHelper.deleteMessage(event);
    }

    public static void doFfCon(GenericInteractionCreateEvent event, Player player, Game game) {
        String colorID = Mapper.getColorID(player.getColor());

        List<Tile> tilesAffected = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            boolean hasSD = false;
            boolean hasCap = false;
            boolean blockaded = false;
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                // player has a space dock in the system
                int numSd = unitHolder.getUnitCount(Units.UnitType.Spacedock, colorID);
                numSd += unitHolder.getUnitCount(Units.UnitType.CabalSpacedock, colorID);
                numSd += unitHolder.getUnitCount(Units.UnitType.PlenaryOrbital, colorID);
                if (numSd > 0) {
                    hasSD = true;
                }

                // Check if space area contains capacity units or another player's units
                if ("space".equals(unitHolder.getName())) {
                    Map<Units.UnitKey, Integer> units = unitHolder.getUnits();
                    for (Map.Entry<Units.UnitKey, Integer> unit : units.entrySet()) {
                        Units.UnitKey unitKey = unit.getKey();

                        Integer quantity = unit.getValue();

                        if (player.unitBelongsToPlayer(unitKey) && quantity != null && quantity > 0) {
                            UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                            if (unitModel == null) continue;
                            if (unitModel.getCapacityValue() > 0) {
                                hasCap = true;
                            }
                        } else if (quantity != null && quantity > 0) {
                            blockaded = true;
                            break;
                        }
                    }
                }

                if (blockaded || hasCap) {
                    break;
                }
            }

            if (!blockaded && (hasCap || hasSD)) {
                AddUnitService.addUnits(event, tile, game, player.getColor(), "ff");
                tilesAffected.add(tile);
            }
        }

        String msg = "Added " + tilesAffected.size() + " fighter" + (tilesAffected.size() == 1 ? "" : "s") + ".";
        if (!tilesAffected.isEmpty()) {
            msg += " Please check fleet size and capacity in each of the systems: ";
        }
        boolean first = true;
        StringBuilder msgBuilder = new StringBuilder(msg);
        for (Tile tile : tilesAffected) {
            if (first) {
                msgBuilder.append("\n> **").append(tile.getPosition()).append("**");
                first = false;
            } else {
                msgBuilder.append(", **").append(tile.getPosition()).append("**");
            }
        }
        msg = msgBuilder.toString();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }
}
