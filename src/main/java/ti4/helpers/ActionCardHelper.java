package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import ti4.contest.replay.core.CombatReplayTrackedEvent;
import ti4.contest.replay.service.CombatReplayService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.agenda.VoteButtonHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.arvaxi.ArvaxiAgentButtonHandler;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.thundersedge.TeHelperActionCards;
import ti4.helpers.twilight_kart.TkHelperActionCards;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.GenericCardModel;
import ti4.model.PlanetModel;
import ti4.model.TemporaryCombatModifierModel;
import ti4.model.UnitModel;
import ti4.model.metadata.AutoPingMetadataManager;
import ti4.service.actioncard.SabotageService;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.breakthrough.DeepgloomService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.turn.StartTurnService;
import ti4.service.unit.AddUnitService;
import ti4.spring.context.SpringContext;

@UtilityClass
public class ActionCardHelper {

    private static final String PINNED_AC_INFO_MESSAGE_ID = "pinned_ac_info_message_id";

    private static final Set<String> WAR_MACHINE_IDS = Set.of(
            "war_machine1",
            "war_machine2",
            "war_machine3",
            "war_machine4",
            "war_machine_ds",
            "war_machine1_acd2",
            "war_machine2_acd2",
            "war_machine3_acd2",
            "war_machine4_acd2");
    private static final Set<String> MORALE_BOOST_IDS = Set.of("mb1", "mb2", "mb3", "mb4", "morale_boost_ds");
    private static final Set<String> SHIELDS_HOLDING_IDS = Set.of("sh1", "sh2", "sh3", "sh4", "shields_holding_ds");

    public enum ACStatus {
        ralnelbt,
        garbozia,
        purged
    }

    public static void sendActionCardInfo(Game game, Player player) {
        // AC INFO
        MessageHelper.sendMessageToPlayerCardsInfoThreadAndPin(
                game, player, PINNED_AC_INFO_MESSAGE_ID, getActionCardInfo(game, player));
        Map<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null && !actionCards.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    "Click a button below to play an action card.",
                    getPlayActionCardButtons(game, player));
        }

        if (game.isWildWildGalaxyMode()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    "This is a reminder that in Wild, Wild Galaxy mode, each of the four-of-a-type action cards are modified in some way. The text you see may not be entirely accurate as a result.");
        }

        sendTrapCardInfo(player);
        sendPlotCardInfo(game, player);
        sendGarboziaInfo(game, player);
    }

    private static void sendGarboziaInfo(Game game, Player player) {
        if (player.hasPlanet("garbozia")) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(), getGarboziaInfo(game), getPurgeGarboziaActionCardButtons(game));
        }
    }

    private static String getGarboziaInfo(Game game) {
        StringBuilder sb = new StringBuilder();
        // ACTION CARDS
        sb.append("### ")
                .append(MiscEmojis.LegendaryPlanet)
                .append(" _Dok 'N Pic's Salvage Yard_ Action Cards:")
                .append('\n');

        Map<String, Integer> actionCards = game.getDiscardActionCards();
        if (actionCards == null || actionCards.isEmpty()) {
            sb.append("> None");
            return sb.toString();
        }

        int index = 1;
        for (Entry<String, Integer> ac : actionCards.entrySet()) {
            ACStatus status = game.getDiscardACStatus().getOrDefault(ac.getKey(), null);
            if (status != ACStatus.garbozia) continue;

            Integer value = ac.getValue();
            ActionCardModel actionCard = Mapper.getActionCard(ac.getKey());
            sb.append('`')
                    .append(index)
                    .append(".")
                    .append(Helper.leftpad("(" + value, 4))
                    .append(")`")
                    .append(actionCard.getRepresentation(game));
            index++;
        }
        return sb.toString();
    }

    public static Map<String, Integer> getGarboziaActionCards(Game game) {
        Map<String, Integer> cards = new HashMap<>();
        for (Entry<String, ACStatus> discard : game.getDiscardACStatus().entrySet()) {
            if (discard.getValue() != ACStatus.garbozia) continue;
            Integer ident = game.getDiscardActionCards().get(discard.getKey());
            cards.put(discard.getKey(), ident);
        }
        return cards;
    }

    private static List<Button> getPurgeGarboziaActionCardButtons(Game game) {
        List<Button> buttons = new ArrayList<>();
        for (Entry<String, Integer> card : getGarboziaActionCards(game).entrySet()) {
            String key = card.getKey();
            Integer ident = card.getValue();
            ACStatus status = game.getDiscardACStatus().get(key);
            if (status == ACStatus.garbozia) {
                String id = Constants.AC_PLAY_FROM_HAND + ident;
                String label = Mapper.getActionCard(card.getKey()).getName();
                buttons.add(Buttons.red(id, label, CardEmojis.getACEmoji(game)));
            }
        }
        return buttons;
    }

    private static void sendTrapCardInfo(Player player) {
        if (player.hasAbility("cunning") || player.hasAbility("subterfuge")) { // Lih-zo trap abilities
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, getTrapCardInfo(player));
        }
        if (player.hasAbility("classified_developments")) {
            String[] superWeapons = {
                "superweaponavailyn", "superweaponcaled", "superweaponglatison", "superweapongrom", "superweaponmors"
            };

            StringBuilder sb = new StringBuilder();
            sb.append("Info on your superweapons are as follows:\n");

            for (String id : superWeapons) {
                sb.append(Mapper.getRelic(id).getSimpleRepresentation());

                var location =
                        ButtonHelperAbilities.getLocationOfSuperweapon(player.getGame(), id.replace("superweapon", ""));
                if (location != null) {
                    sb.append("\nLOCATION: ").append(location.getRepresentationForButtons());
                }
                sb.append('\n');
            }

            MessageHelper.sendMessageToPlayerCardsInfoThread(player, sb.toString());
        }
    }

    private static String getTrapCardInfo(Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append('\n');
        sb.append("Trap Cards:").append('\n');
        int index = 1;
        Map<String, Integer> trapCards = player.getTrapCards();
        Map<String, String> trapCardsPlanets = player.getTrapCardsPlanets();
        if (trapCards != null) {
            if (trapCards.isEmpty()) {
                sb.append("> None");
            } else {
                for (Map.Entry<String, Integer> trapCard : trapCards.entrySet()) {
                    Integer value = trapCard.getValue();
                    sb.append(index)
                            .append(". ")
                            .append(Helper.leftpad("(" + value, 4))
                            .append(")`");
                    index++;
                    sb.append(getTrapCardRepresentation(trapCard.getKey(), trapCardsPlanets));
                }
            }
        }
        return sb.toString();
    }

    private static String getTrapCardRepresentation(String trapID, Map<String, String> trapCardsPlanets) {
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
            sb.append("\n> Planet: ").append(representation);
        }
        sb.append('\n');
        return sb.toString();
    }

    public static void sendPlotCardInfo(Game game, Player player) {
        if (player.hasAbility("plotsplots")
                || player.hasAbility("bladesorchestra")
                || player.hasAbility("puppetsoftheblade")) { // firmament/obsidian plot abilities
            List<Button> buttons = getPlotCardButtons(player);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(), getPlotCardInfo(game, player), buttons);
        }
    }

    private static List<Button> getPlotCardButtons(Player player) {
        boolean hasManageAbility = player.hasAbility("plotsplots");
        if (player.hasLeader("firmamenthero")) hasManageAbility = true;
        if (!hasManageAbility && player.getPlotCards().isEmpty()) return new ArrayList<>();

        List<Button> buttons = new ArrayList<>();
        Set<Entry<String, Integer>> plotCards = player.getPlotCards().entrySet();
        plotCards.stream()
                .map(plot -> Map.entry(plot.getValue(), Mapper.getPlot(plot.getKey())))
                .sorted(Comparator.comparingInt(Entry::getKey))
                .forEachOrdered(plotEntry -> {
                    GenericCardModel plot = plotEntry.getValue();
                    String buttonID = "addFactionTokenToPlot_" + plot.getAlias();
                    String buttonText = plot.getName();
                    buttons.add(Buttons.green(buttonID, buttonText));
                });
        plotCards.stream()
                .map(plot -> Map.entry(plot.getValue(), Mapper.getPlot(plot.getKey())))
                .sorted(Comparator.comparingInt(Entry::getKey))
                .forEachOrdered(plotEntry -> {
                    GenericCardModel plot = plotEntry.getValue();
                    String buttonID = "removeFactionTokenFromPlot_" + plot.getAlias();
                    String buttonText = "Remove From " + plot.getName();
                    List<String> factions = player.getPuppetedFactionsForPlot(plot.getAlias());
                    if (factions != null && !factions.isEmpty()) {
                        buttons.add(Buttons.red(buttonID, buttonText));
                    }
                });
        if (player.hasAbility("plotsplots")) {
            buttons.add(Buttons.blue("scoreOtherPlayersSecrets", "Score Other Players' Secrets (Max 5)"));
        }
        return buttons;
    }

    public static List<Button> getFactionButtonsForPlot(Game game, Player player, String plotID, String prefix) {
        List<Button> buttons = new ArrayList<>();
        List<String> factions = player.getPuppetedFactionsForPlot(plotID);
        game.getRealPlayers().forEach(p -> {
            boolean valid = factions == null || !factions.contains(p.getFaction());
            if (prefix.startsWith("remove")) valid = factions != null && factions.contains(p.getFaction());
            if (valid) {
                String id = prefix + plotID + "_" + p.getFaction();
                buttons.add(Buttons.gray(id, "", p.getFactionEmojiOrColor()));
            }
        });
        buttons.add(Buttons.DONE_DELETE_BUTTONS);
        return buttons;
    }

    private static String getPlotCardInfo(Game game, Player player) {
        StringBuilder sb = new StringBuilder("### __Plot Cards:__\n");
        player.getPlotCards().entrySet().stream()
                .map(plot -> Map.entry(plot.getValue(), Mapper.getPlot(plot.getKey())))
                .sorted(Comparator.comparingInt(Entry::getKey))
                .forEachOrdered(plotEntry -> {
                    GenericCardModel plot = plotEntry.getValue();
                    Integer value = plotEntry.getKey();
                    List<String> factions = player.getPuppetedFactionsForPlot(plot.getAlias());
                    sb.append('`').append(Helper.leftpad("(" + value, 3)).append(")`");
                    sb.append(getPlotCardRepresentation(game, plot, factions));
                });
        return sb.toString();
    }

    private static String getPlotCardRepresentation(Game game, GenericCardModel plot, List<String> factions) {
        StringBuilder sb = new StringBuilder(plot.getRepresentation()).append('\n');
        if (factions != null) {
            List<String> factionEmojis = factions.stream()
                    .map(game::getPlayerFromColorOrFaction)
                    .filter(Objects::nonNull)
                    .map(Player::fogSafeEmoji)
                    .toList();
            sb.append("> ").append(String.join(", ", factionEmojis)).append('\n');
        }
        return sb.toString();
    }

    private static String getActionCardInfo(Game game, Player player) {
        StringBuilder sb = new StringBuilder();

        // ACTION CARDS
        sb.append("__Action Cards__ (")
                .append(player.getAcCount())
                .append('/')
                .append(ButtonHelper.getACLimit(game, player))
                .append("):")
                .append('\n');
        int index = 1;

        Map<String, Integer> actionCards = player.getActionCards();
        if (actionCards == null || actionCards.isEmpty()) {
            sb.append("> None");
            return sb.toString();
        }

        for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
            ActionCardModel actionCard = Mapper.getActionCard(ac.getKey());
            sb.append(index).append("\\. ");
            index++;

            if (actionCard == null) {
                sb.append("Something broke here");
            } else {
                sb.append(CardEmojis.getACEmoji(game))
                        .append(actionCard.isWild(game) ? CardEmojis.Event : "")
                        .append(" _")
                        .append(actionCard.getName())
                        .append("_ `(")
                        .append(Helper.leftpad("" + ac.getValue(), 3))
                        .append(")`\n> ")
                        .append(actionCard.hasWildText(game) ? actionCard.getWildWildWindow() : actionCard.getWindow())
                        .append(": ")
                        .append(actionCard.hasWildText(game) ? actionCard.getWildWildText() : actionCard.getText())
                        .append('\n');
                if (actionCard.getNotes() != null) {
                    sb.append("> -# [").append(actionCard.getNotes()).append("]\n");
                }
            }
        }
        return sb.toString();
    }

    private static List<Button> getPlayActionCardButtons(Game game, Player player) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> actionCards = player.getActionCards();

        if (actionCards != null
                && !actionCards.isEmpty()
                && !IsPlayerElectedService.isPlayerElected(game, player, "censure")
                && !IsPlayerElectedService.isPlayerElected(game, player, "absol_censure")) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String acName = Mapper.getActionCard(key).getName();
                acButtons.add(Buttons.red(
                        Constants.AC_PLAY_FROM_HAND + value, "(" + value + ") " + acName, CardEmojis.getACEmoji(game)));
            }
        }
        if (IsPlayerElectedService.isPlayerElected(game, player, "censure")
                || IsPlayerElectedService.isPlayerElected(game, player, "absol_censure")) {
            acButtons.add(
                    Buttons.blue("getDiscardButtonsACs", "Discard an Action Card (You Are Politically Censured)"));
        } else {
            acButtons.add(Buttons.blue("getDiscardButtonsACs", "Discard an Action Card"));
        }
        if (actionCards != null
                && !actionCards.isEmpty()
                && !IsPlayerElectedService.isPlayerElected(game, player, "censure")
                && hasPrePlayCards(player)) {
            acButtons.add(Buttons.gray("checkForAllACAssignments", "Pre-Assign Action Cards"));
        }

        return acButtons;
    }

    private static boolean hasPrePlayCards(Player player) {
        List<String> prePlayable = List.of(
                "coup",
                "crisis",
                "tf-stasis",
                "extremeduress",
                "disgrace",
                "special_session",
                "investments",
                "tf-reverse",
                "puppetsonastring",
                "tf-manipulate",
                "revolution",
                "deflection",
                "summit",
                "bounty_contracts",
                "tk-compose");
        List<String> actionCards = new ArrayList<>(player.getActionCards().keySet());
        if (player.hasPlanet("garbozia")) {
            actionCards.addAll(getGarboziaActionCards(player.getGame()).keySet());
        }
        return CollectionUtils.containsAny(prePlayable, actionCards);
    }

    private static List<Button> getGarboziaComponentActionCards(Player player) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> garboziaCards = getGarboziaActionCards(player.getGame());
        if (player.hasPlanet("garbozia") && !garboziaCards.isEmpty()) {
            for (Entry<String, Integer> ac : garboziaCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String acName = Mapper.getActionCard(key).getName();
                ActionCardModel actionCard = Mapper.getActionCard(key);
                String actionCardWindow = actionCard.getWindow();
                if ("action".equalsIgnoreCase(actionCardWindow)) {
                    acButtons.add(Buttons.red(
                            Constants.AC_PLAY_FROM_HAND + value,
                            "(" + value + ") " + acName,
                            MiscEmojis.LegendaryPlanet));
                }
            }
        }
        return acButtons;
    }

    public static List<Button> getActionPlayActionCardButtons(Player player) {
        List<Button> acButtons = new ArrayList<>();
        if (IsPlayerElectedService.isPlayerElected(player.getGame(), player, "censure")
                || IsPlayerElectedService.isPlayerElected(player.getGame(), player, "absol_censure")) {
            return acButtons;
        }
        Map<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null && !actionCards.isEmpty()) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String acName = Mapper.getActionCard(key).getName();
                ActionCardModel actionCard = Mapper.getActionCard(key);
                String actionCardWindow = actionCard.getWindow();
                if ("action".equalsIgnoreCase(actionCardWindow)) {
                    acButtons.add(Buttons.red(
                            Constants.AC_PLAY_FROM_HAND + value,
                            "(" + value + ") " + acName,
                            CardEmojis.getACEmoji(player)));
                }
            }
        }
        acButtons.addAll(getGarboziaComponentActionCards(player));
        return acButtons;
    }

    private static List<Button> getGarboziaCombatActionCards(Player player) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> garboziaCards = getGarboziaActionCards(player.getGame());
        if (player.hasPlanet("garbozia") && !garboziaCards.isEmpty()) {
            for (Entry<String, Integer> ac : garboziaCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCard(key).getName();
                ActionCardModel actionCard = Mapper.getActionCard(key);
                String actionCardWindow = actionCard.getWindow();
                if (actionCardWindow.contains("combat")
                        || actionCardWindow.contains("roll")
                        || actionCardWindow.contains("hit")) {
                    acButtons.add(Buttons.red(
                            Constants.AC_PLAY_FROM_HAND + value,
                            "(" + value + ") " + ac_name,
                            MiscEmojis.LegendaryPlanet));
                }
            }
        }
        return acButtons;
    }

    public static List<Button> getCombatActionCardButtons(Player player) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null && !actionCards.isEmpty()) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String acName = Mapper.getActionCard(key).getName();
                ActionCardModel actionCard = Mapper.getActionCard(key);
                String actionCardWindow = actionCard.getWindow();
                if (actionCardWindow.contains("combat")
                        || actionCardWindow.contains("roll")
                        || actionCardWindow.contains("hit")) {
                    acButtons.add(Buttons.red(
                            Constants.AC_PLAY_FROM_HAND + value,
                            "(" + value + ") " + acName,
                            CardEmojis.getACEmoji(player)));
                }
            }
        }
        acButtons.addAll(getGarboziaCombatActionCards(player));
        return acButtons;
    }

    public static void sendACDiscardButtons(Player player) {
        List<Button> buttons = getDiscardActionCardButtons(player, false);
        String msg = player.getRepresentationUnfogged() + ", discard 1 action card.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    private static void sendSchemingDiscardButtons(Player player) {
        List<Button> buttons = getDiscardActionCardButtons(player, false);
        String msg = player.getRepresentationUnfogged() + ", discard an action card due to **Scheming**.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void sendDiscardAndDrawActionCardButtons(Player player) {
        List<Button> buttons = getDiscardActionCardButtonsWithSuffix(player, "redraw");
        String msg = player.getRepresentationUnfogged()
                + " use buttons to discard. A new action card will be automatically drawn afterwards.";
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
                String acName = Mapper.getActionCard(key).getName();
                acButtons.add(Buttons.blue(
                        "ac_discard_from_hand_" + value + suffix,
                        "(" + value + ") " + acName,
                        CardEmojis.getACEmoji(player)));
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
                String acName = Mapper.getActionCard(key).getName();
                acButtons.add(Buttons.red(
                        "takeAC_" + value + "_" + player.getFaction(), acName, CardEmojis.getACEmoji(player)));
            }
        }
        return acButtons;
    }

    @ButtonHandler(value = "refreshACInfo", save = false)
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
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "No such action card ID found, please retry: " + acID);
            return;
        }
        String message = player.getRepresentationNoPing() + " discarded the action card _"
                + Mapper.getActionCard(acID).getName() + "_.\n"
                + Mapper.getActionCard(acID).getRepresentation(game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        sendActionCardInfo(game, player);
    }

    public void discardRandomAC(GenericInteractionCreateEvent event, Game game, Player player, int count) {
        if (count < 1) {
            return;
        }
        StringBuilder message = new StringBuilder(player.getRepresentationNoPing() + " discarded " + count
                + " random action card" + (count == 1 ? "" : "s") + ".\n");
        while (count > 0 && !player.getActionCards().isEmpty()) {
            Map<String, Integer> actionCards_ = player.getActionCards();
            List<String> cards_ = new ArrayList<>(actionCards_.keySet());
            Collections.shuffle(cards_);
            String acID = cards_.getFirst();
            boolean removed = game.discardActionCard(player.getUserID(), actionCards_.get(acID));
            if (!removed) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(), "No such action card with id `" + acID + "` found, please retry.");
                return;
            }
            message.append(Mapper.getActionCard(acID).getRepresentation(game));
            count--;
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message.toString());
        sendActionCardInfo(game, player);
    }

    public static void drawActionCards(Player player, int count) {
        drawActionCards(player.getGame(), player, count, true, true, false);
    }

    public static void drawActionCardsNoAutonetic(Player player, int count) {
        drawActionCards(player.getGame(), player, count, true, false, false);
    }

    public static void drawActionCardsNoAbilities(Player player, int count) {
        drawActionCards(player.getGame(), player, count, false, false, false);
    }

    public static void drawActionCardsSilent(Player player, int count) {
        drawActionCards(player.getGame(), player, count, true, true, true);
    }

    private static void drawActionCards(
            Game game, Player player, int count, boolean scheming, boolean autonetic, boolean silent) {
        if (count > 10) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "You probably shouldn't need to ever draw more than 10 cards, double check what you're doing please.");
            return;
        }
        if (autonetic && player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(player, count);
            return;
        }

        String message = player.getRepresentation() + " drew " + count + " action card" + (count == 1 ? "" : "s") + ".";
        if (scheming && player.hasAbility("scheming")) {
            count++;
            message = player.getRepresentation() + " drew " + count + " action card" + (count == 1 ? "" : "s")
                    + " (including one extra because of **Scheming**).";
        }
        game.drawActionCard(player.getUserID(), count);

        sendActionCardInfo(game, player);
        ButtonHelper.checkACLimit(game, player);
        if (scheming && player.hasAbility("scheming")) {
            DeepgloomService.spendOneDebt(game, player, "scheming");
            sendSchemingDiscardButtons(player);
        }
        CommanderUnlockCheckService.checkPlayer(player, "yssaril");

        if (!silent) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        }
    }

    public static String resolveActionCard(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            String acID,
            int acIndex,
            MessageChannel channel) {
        MessageChannel mainGameChannel = game.getMainGameChannel() == null ? channel : game.getMainGameChannel();
        ActionCardModel actionCard = Mapper.getActionCard(acID);
        String actionCardTitle = actionCard.getName();
        String actionCardWindow = actionCard.getWindow();
        boolean twinned = (acIndex == -99);

        String activePlayerID = game.getActivePlayerID();
        if (player.isPassed() && activePlayerID != null) {
            Player activePlayer = game.getPlayer(activePlayerID);
            if (activePlayer != null && (activePlayer.hasTech("tp") || activePlayer.hasTech("tf-crafty"))) {
                return "You are passed and the active player owns _Transparasteel Plating_, preventing you from playing action cards.";
            }
        }

        if ("blackmarketdealing".equals(acID)
                && game.getPhaseOfGame().toLowerCase().contains("agenda")
                && game.isHiddenAgendaMode()
                && game.getStoredValue("executiveOrder").isEmpty()) {
            return "You cannot make transactions during the agenda phase in Hidden Agenda mode. Cancelling this action card automatically";
        }

        if ("Action".equalsIgnoreCase(actionCardWindow)
                && game.getPlayer(activePlayerID) != player
                && !game.isTwilightsFallMode()) {
            return "You are trying to play an action card with a component action, and the game does not think you are the active player."
                    + " You may fix this with `/player turn_start`. Until then, you are #denied.";
        }
        if (ButtonHelper.isPlayerOverLimit(game, player)) {
            return player.getRepresentationUnfogged()
                    + " The bot thinks you are over the limit and thus will not allow you to play action cards at this time."
                    + " You may discard the action cards and manually resolve if you need to.";
        }
        if (game.isStellarAtomicsMode()
                && "agenda".equalsIgnoreCase(actionCard.getPhase())
                && game.getRevealedPublicObjectives().get("Stellar Atomics") != null) {
            if (!game.getScoredPublicObjectives().get("Stellar Atomics").contains(player.getUserID())) {
                return player.getRepresentationUnfogged() + ", the bot thinks you have committed some light war crimes,"
                        + " thus you no longer have your token on the _Stellar Atomics_ event card, and therefore cannot play action cards during the Agenda Phase.";
            }
        }
        // // Async leniency: reveal-window agenda cards may still be played anywhere before outcome resolution.
        // if (actionCard.getPlayTiming().isDuringAgendaReveal() && !AgendaHelper.isPreResolutionAgendaPhase(game)) {
        //     return "This action card can only be played during the agenda phase before outcome resolution.";
        // }

        CryypterHelper.checkForAssigningYssarilEnvoy(event, game, player, acID);
        if (game.isWildWildGalaxyMode() && actionCard.getName().toLowerCase().contains("morale boost")) {
            game.setStoredValue("wildMB" + player.getFaction(), "yes");
        }

        game.setStoredValue(
                "currentActionSummary" + player.getFaction(),
                game.getStoredValue("currentActionSummary" + player.getFaction()) + " Played the _" + actionCardTitle
                        + "_ action card.");

        boolean fromGarbozia = false;
        if (!twinned) {
            if (player.hasPlanet("garbozia")
                    && game.getDiscardACStatus().getOrDefault(acID, null) == ACStatus.garbozia) {
                game.getDiscardACStatus().put(acID, ACStatus.purged);
                if (!game.isFowMode()) {
                    fromGarbozia = true;
                }
            } else if (player.hasAbility("cybernetic_madness")) {
                game.purgedActionCard(player.getUserID(), acIndex);
            } else {
                game.discardActionCard(player.getUserID(), acIndex);
            }
        }

        boolean actionCardIsCancelable = isActionCardCancelable(actionCard) && !twinned;

        String pingGame = actionCardIsCancelable ? game.getPing() + ", " : "";
        String message = pingGame + (game.isFowMode() ? "someone" : player.getRepresentation());
        message += fromGarbozia ? " purged " : " played ";
        message += "the action card _" + actionCardTitle + "_";
        message += fromGarbozia ? " using _Dok 'N Pic's Salvage Yard_." : ".";

        List<Button> buttons = new ArrayList<>();

        boolean actionCardIsSabotageOrShatter = isSabotageOrShatter(acID);
        if (actionCardIsCancelable && !actionCardIsSabotageOrShatter) {
            Button sabotageButton = Buttons.red(
                    "sabotage_ac_" + actionCardTitle + "_" + player.getFaction(),
                    "Cancel Action Card With Sabotage",
                    MiscEmojis.Sabotage);
            buttons.add(sabotageButton);
        }

        if (actionCardIsCancelable) {
            Player empy = Helper.getPlayerFromUnit(game, "empyrean_mech");
            if (empy != null
                    && ButtonHelperFactionSpecific.isNextToEmpyMechs(game, player, empy)
                    && !ButtonHelper.isLawInPlay(game, "articles_war")) {
                Button empyButton = Buttons.gray(
                        "sabotage_empy_" + actionCardTitle + "_" + player.getFaction(),
                        "Cancel " + actionCardTitle + " With Watcher",
                        UnitEmojis.mech);
                List<Button> empyButtons = new ArrayList<>();
                empyButtons.add(empyButton);
                Button refuse = Buttons.red("deleteButtons", "Delete These Buttons");
                empyButtons.add(refuse);
                MessageHelper.sendMessageToChannelWithButtons(
                        empy.getCardsInfoThread(),
                        empy.getRepresentationUnfogged()
                                + "You have one or more mechs adjacent to some units of the player who played _"
                                + actionCardTitle + "_. Use these buttons to decide whether to Sabo this action card.",
                        empyButtons);
            }
            Player tfTriune = Helper.getPlayerFromUnit(game, "tf-triune");
            if (tfTriune != null && ButtonHelperFactionSpecific.isNextToTriunes(game, player, tfTriune)) {
                Button tfButton = Buttons.gray(
                        "sabotage_tf_" + actionCardTitle + "_" + player.getFaction(),
                        "Cancel " + actionCardTitle + " With Triunes",
                        UnitEmojis.fighter);
                List<Button> tfButtons = new ArrayList<>();
                tfButtons.add(tfButton);
                Button refuse = Buttons.red("deleteButtons", "Delete These Buttons");
                tfButtons.add(refuse);
                MessageHelper.sendMessageToChannelWithButtons(
                        tfTriune.getCardsInfoThread(),
                        tfTriune.getRepresentationUnfogged()
                                + "You have at least 3 Triune fighters adjacent to some units of the player who played _"
                                + actionCardTitle
                                + "_. Use these buttons to decide whether to cancel this action card.",
                        tfButtons);
            }
            String instinctTrainingID = "it";
            for (Player player2 : game.getPlayers().values()) {
                if (!player.equals(player2)
                        && player2.hasTechReady(instinctTrainingID)
                        && player2.getStrategicCC() > 0) {
                    List<Button> xxchaButtons = new ArrayList<>();
                    xxchaButtons.add(Buttons.gray(
                            "sabotage_xxcha_" + actionCardTitle + "_" + player.getFaction(),
                            "Cancel " + actionCardTitle + " With Instinct Training",
                            FactionEmojis.Xxcha));
                    xxchaButtons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
                    MessageHelper.sendMessageToChannelWithButtons(
                            player2.getCardsInfoThread(),
                            player2.getRepresentationUnfogged()
                                    + ", you have _Instinct Training_ readied and a command token available in your strategy pool."
                                    + " Use these buttons to decide whether to Sabo _" + actionCardTitle + "_.",
                            xxchaButtons);
                }
            }
        }
        MessageEmbed acEmbed = actionCard.getRepresentationEmbed(false, true, game);
        if (!game.isFowMode() && event instanceof ButtonInteractionEvent bEvent) {
            if (bEvent.getChannel().getName().toLowerCase().contains("-vs-")) {
                MessageHelper.sendMessageToChannelWithEmbed(bEvent.getChannel(), message, acEmbed);
            }
        }
        SpringContext.getBean(CombatReplayService.class)
                .mirrorActionCardPlayed(
                        game,
                        player,
                        actionCard.getAlias(),
                        player.getCorrectChannel().getName(),
                        getCombatReplayTrackedEvent(actionCard));

        if (actionCardIsSabotageOrShatter) {
            MessageHelper.sendMessageToChannelWithEmbed(mainGameChannel, message, acEmbed);
            if (game.isWildWildGalaxyMode()) {
                Button codex1 = Buttons.green("codexCardPick_1", "Card #1");
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentation()
                                + ", please pick up the Sabo'd card from the discard, per the _Wild, Wild Galaxy_ galactic event.",
                        List.of(codex1));
            }
        } else {
            String buttonLabel = "Resolve " + actionCardTitle;
            String automationID = actionCard.getAutomationID();

            if (!actionCardIsCancelable) {
                MessageHelper.sendMessageToChannelWithEmbed(mainGameChannel, message, acEmbed);
            } else {
                if (SabotageService.isSaboAllowed(game, player)) {
                    String cancelName = "Sabotage";
                    if (game.isTwilightsFallMode()) {
                        cancelName = "Shatter";
                    }
                    buttons.add(Buttons.blue("no_sabotage", "No " + cancelName, MiscEmojis.NoSabo));
                    buttons.add(Buttons.gray(
                            player.factionButtonChecker() + "moveAlongAfterAllHaveReactedToAC_" + actionCardTitle,
                            "Pause Timer While Waiting For " + cancelName));
                    MessageHelper.sendMessageToChannelWithEmbedsAndFactionReact(
                            mainGameChannel, message, game, player, Collections.singletonList(acEmbed), buttons, true);
                } else {
                    MessageHelper.sendMessageToChannelWithEmbed(mainGameChannel, message, acEmbed);
                    StringBuilder noSabosMessage = new StringBuilder("> " + SabotageService.noSaboReason(game, player));
                    boolean it = false, watcher = false, triune = false;
                    for (Player p : game.getRealPlayers()) {
                        if (p == player) continue;
                        if (!it && (game.isFowMode() || p.hasTechReady("it"))) {
                            noSabosMessage.append(
                                    "\n> A player may have access to **Instinct Training**, so watch out.");
                            it = true;
                        }
                        if (!watcher && (game.isFowMode() || p.hasUnit("empyrean_mech"))) {
                            noSabosMessage.append("\n> A player may have access to a Watcher mech, so 𝓌𝒶𝓉𝒸𝒽 out.");
                            watcher = true;
                        }
                        if (!triune && (game.isFowMode() || p.hasUnit("tf-triune"))) {
                            noSabosMessage.append("\n> A player may have access to 3 Triune fighters, so watch out.");
                            triune = true;
                        }
                    }
                    MessageHelper.sendMessageToChannel(mainGameChannel, noSabosMessage.toString());
                }
            }

            MessageChannel channel2 = player.getCorrectChannel();
            if ("investments".equals(automationID)) {
                serveManipulateInvestmentButtons(game, player);
            }
            if ("deflection".equals(automationID) || "tf-tartarus".equals(automationID)) {
                List<Button> scButtons = new ArrayList<>();
                for (int sc : game.getSCList()) {
                    TI4Emoji scEmoji = CardEmojis.getSCBackFromInteger(sc);
                    Button button;
                    if (scEmoji != CardEmojis.SCBackBlank) {
                        button = Buttons.gray(
                                player.factionButtonChecker() + "deflectSC_" + sc, Helper.getSCName(sc, game), scEmoji);
                    } else {
                        button = Buttons.gray(
                                player.factionButtonChecker() + "deflectSC_" + sc,
                                sc + " " + Helper.getSCName(sc, game));
                    }
                    scButtons.add(button);
                }
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2,
                        player.getRepresentation()
                                + " Use buttons to choose which strategy card will be the target of your AC.",
                        scButtons);
            }

            if ("arch_expedition".equals(automationID)) {
                List<Button> scButtons = ButtonHelperActionCards.getArcExpButtons(game, player);
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2,
                        player.getRepresentation()
                                + ", after checking for Sabos, please use buttons to explore a planet type thrice and gain any fragments.",
                        scButtons);
            }

            if ("planetary_rigs".equals(automationID)) {
                List<Button> acbuttons = ButtonHelperHeroes.getAttachmentSearchButtons(game, player);
                String msg = player.getRepresentation()
                        + ", after checking for Sabos, first declare what planet you mean to put an attachment on, then hit the button to resolve.";
                if (acbuttons.isEmpty()) {
                    msg = player.getRepresentation()
                            + ", there were no attachments found in the applicable exploration decks.";
                }
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, acbuttons);
            }
            String cancelReminder = actionCardIsCancelable ? ", after checking for Sabos" : "";
            String introMsg = player.getRepresentation() + cancelReminder + ", please use buttons to resolve _"
                    + actionCardTitle + "_.";
            String targetMsg =
                    " A reminder that you should declare which %s you are targeting now, before other players choose whether they will Sabo.";

            List<Button> codedButtons = new ArrayList<>();
            if ("plagiarize".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "getPlagiarizeButtons", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "technology"), codedButtons);
            }

            if ("mining_initiative".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "miningInitiative", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "planet"), codedButtons);
            }

            if ("revolution".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "willRevolution", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("professional_archeologists".equals(automationID)) {
                codedButtons.add(
                        Buttons.green(player.factionButtonChecker() + "resolveProfessionalArcheologists", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("special_session".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveVeto", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("war_machine".equals(automationID)) {
                player.addSpentThing("warmachine");
            }

            if ("economic_initiative".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "economicInitiative", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("confounding".equals(automationID)) {
                codedButtons.add(Buttons.green("autoresolve_manual", buttonLabel));
                sendResolveMsgToMainChannel(introMsg, codedButtons, player, game);
            }

            if ("confusing".equals(automationID)) {
                codedButtons.add(Buttons.green("autoresolve_manual", buttonLabel));
                sendResolveMsgToMainChannel(introMsg + String.format(targetMsg, "player"), codedButtons, player, game);
            }

            if ("reveal_prototype".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveResearch", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2,
                        introMsg + " A reminder that since you are researching,"
                                + " you need not declare what technology you will get until after other players have chosen whether they will Sabo.",
                        codedButtons);
            }

            if ("side_project".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "sideProject", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2,
                        introMsg + " A reminder that since you are researching,"
                                + " you need not declare what technology you will get until after other players have chosen whether they will Sabo.",
                        codedButtons);
            }

            if ("brutal_occupation".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "brutalOccupation", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "planet"), codedButtons);
            }

            if ("stolen_prototype".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveResearch", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "destroyed unit"), codedButtons);
            }

            if ("skilled_retreat".equals(automationID)) {
                codedButtons.add(Buttons.green(
                        player.factionButtonChecker() + "retreat_" + game.getActiveSystem() + "_skilled", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2,
                        introMsg
                                + "A reminder that you should declare which system you are retreating to now, before other players choose whether they will Sabo.",
                        codedButtons);
            }

            if ("reparations".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveReparationsStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "planets"), codedButtons);
            }

            if ("distinguished".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveDistinguished", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("uprising".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveUprisingStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "planet"), codedButtons);
            }

            if ("tomb_raiders".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveTombRaiders", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("alliance_rider".equals(automationID)) {
                codedButtons.add(
                        Buttons.green(player.factionButtonChecker() + "allianceRiderRandomAlly", "Random Ally"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("innovation".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "innovation", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("ubiquity".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "ubiquity", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("lost_treatise".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveLostTreatise", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("opportunists".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveOpportunists", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("jamming".equals(automationID)) {
                codedButtons.add(
                        Buttons.green(player.factionButtonChecker() + "resolveSignalJammingStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "system and player"), codedButtons);
            }

            if ("spy".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveSpyStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "player"), codedButtons);
            }

            if ("stability".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolvePSStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("plague".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolvePlagueStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "planet"), codedButtons);
            }

            if ("experimental".equals(automationID)) {
                codedButtons.add(Buttons.green(
                        player.factionButtonChecker() + "resolveEBSStep1_" + game.getActiveSystem(), buttonLabel));
                if (player.hasTechReady("gls")) {
                    codedButtons.add(Buttons.gray(
                            "exhaustTech_gls", "Exhaust Graviton Laser System", TechEmojis.CyberneticTech));
                }
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2,
                        introMsg
                                + (player.hasTechReady("gls")
                                        ? "\n-# Make sure to declare _Graviton Laser System_, should you so wish, before pressing this button."
                                        : ""),
                        codedButtons);
            }

            if ("blitz".equals(automationID)) {
                codedButtons.add(Buttons.green(
                        player.factionButtonChecker() + "resolveBlitz_" + game.getActiveSystem(), buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("shrapnel_turrets".equals(automationID)) {
                codedButtons.add(Buttons.green(
                        player.factionButtonChecker() + "resolveShrapnelTurrets_" + game.getActiveSystem(),
                        buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "ships"), codedButtons);
            }

            if ("micrometeoroid_storm".equals(automationID)) {
                codedButtons.add(
                        Buttons.green(player.factionButtonChecker() + "resolveMicrometeoroidStormStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "system"), codedButtons);
            }

            if ("upgrade".equals(automationID)) {
                codedButtons.add(Buttons.green(
                        player.factionButtonChecker() + "resolveUpgrade_" + game.getActiveSystem(), buttonLabel));
                if (game.getActiveSystem().isEmpty()) {
                    MessageHelper.sendMessageToChannel(
                            channel2, "The active system is currently non-existent, so this card cannot be automated.");
                } else {
                    MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
                }
            }

            if ("infiltrate".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveInfiltrateStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2,
                        introMsg + String.format(targetMsg, "planet")
                                + " Warning, this will not work if the player has already removed their structures.",
                        codedButtons);
            }

            if ("emergency_repairs".equals(automationID)) {
                codedButtons.add(Buttons.green(
                        player.factionButtonChecker() + "resolveEmergencyRepairs_" + game.getActiveSystem(),
                        buttonLabel));
                if (game.getActiveSystem().isEmpty()) {
                    MessageHelper.sendMessageToChannel(
                            channel2, "The active system is currently non-existent, so this card cannot be automated.");
                } else {
                    MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
                }
            }

            if ("cripple".equals(automationID)) {
                codedButtons.add(
                        Buttons.green(player.factionButtonChecker() + "resolveCrippleDefensesStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "planet"), codedButtons);
            }

            if ("impersonation".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveImpersonation", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("abs".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveABSStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "player"), codedButtons);
            }

            if ("salvage".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveSalvageStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("insub".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveInsubStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "player"), codedButtons);
            }
            if ("parley".equals(automationID) || "tf-pax".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveParleyStep1", buttonLabel));
            }

            if ("f_deployment".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveFrontline", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "planet"), codedButtons);
            }

            if ("unexpected".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveUnexpected", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "system"), codedButtons);
            }

            if ("data_archive".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveDataArchive", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "planets"), codedButtons);
            }

            if ("oracle".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveOracle", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("intrigue".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveIntrigue", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("ancient_trade_routes".equals(automationID)) {
                codedButtons.add(
                        Buttons.green(player.factionButtonChecker() + "resolveAncientTradeRoutes", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "players"), codedButtons);
            }

            if ("flank_speed".equals(automationID)) {
                game.setStoredValue("flankspeedBoost", "1");
            }

            if ("refabrication".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveSisterShip", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "destroyed ship"), codedButtons);
            }

            if ("boarding_party".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveBoardingParty", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "destroyed ship"), codedButtons);
            }

            if ("rapid_fulfillment".equals(automationID) || "contingency".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveRapidFulfillment", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("chain_reaction".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveChainReaction", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("flawless_strategy".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveFlawlessStrategy", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("arms_deal".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveArmsDeal", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "player"), codedButtons);
            }

            if ("defense_installation".equals(automationID)) {
                codedButtons.add(
                        Buttons.green(player.factionButtonChecker() + "resolveDefenseInstallation", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "planet"), codedButtons);
            }

            if ("cache".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveCache", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "planet"), codedButtons);
            }

            if ("simulacrum".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveSimulacrum", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("harness".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveHarness", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("war_effort".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveWarEffort", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "system"), codedButtons);
            }

            if ("free_trade_network".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveFreeTrade", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("preparation".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolvePreparation", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("summit".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveSummit", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("compounding_interests".equals(automationID)) {
                codedButtons.add(
                        Buttons.green(player.factionButtonChecker() + "resolveCompoundingInterests", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("scuttle".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "startToScuttleAUnit_0", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "ships"), codedButtons);
            }

            if ("lucky".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "startToLuckyShotAUnit_0", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "ship"), codedButtons);
            }

            if ("refit".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveRefitTroops", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "infantry"), codedButtons);
            }

            if ("seize".equals(automationID)) {
                codedButtons.add(
                        Buttons.green(player.factionButtonChecker() + "resolveSeizeArtifactStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "player"), codedButtons);
            }

            if ("diplo_pressure".equals(automationID)) {
                codedButtons.add(
                        Buttons.green(player.factionButtonChecker() + "resolveDiplomaticPressureStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2,
                        "Please resolve _Diplomatic Pressure_ now. If any Sabo occurs, they will be able to ignore the buttons they are offered.",
                        codedButtons);
            }

            if ("renegotiation".equals(automationID)) {
                codedButtons.add(
                        Buttons.green(player.factionButtonChecker() + "resolveDiplomaticPressureStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "player"), codedButtons);
            }

            if ("decoy".equals(automationID)) {
                codedButtons.add(Buttons.green(
                        player.factionButtonChecker() + "resolveDecoyOperationStep1_" + game.getActiveSystem(),
                        buttonLabel));
                if (game.getActiveSystem().isEmpty()) {
                    MessageHelper.sendMessageToChannel(
                            channel2, "The active system is currently non-existent, so this card cannot be automated.");
                } else {
                    MessageHelper.sendMessageToChannelWithButtons(
                            channel2, introMsg + String.format(targetMsg, "ground forces"), codedButtons);
                }
            }

            if ("meltdown".equals(automationID)) {
                codedButtons.add(
                        Buttons.green(player.factionButtonChecker() + "resolveReactorMeltdownStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "space dock"), codedButtons);
            }

            if ("unstable".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveUnstableStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "planet"), codedButtons);
            }

            if ("ghost_ship".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveGhostShipStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "system"), codedButtons);
            }

            if ("stranded_ship".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "strandedShipStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "system"), codedButtons);
            }

            if ("tactical".equals(automationID)) {
                codedButtons.add(
                        Buttons.green(player.factionButtonChecker() + "resolveTacticalBombardmentStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "system"), codedButtons);
            }

            if ("probe".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveProbeStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "system"), codedButtons);
            }

            if ("rally".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveRally", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("industrial_initiative".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "industrialInitiative", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("repeal".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "getRepealLawButtons", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "law"), codedButtons);
            }

            if ("divert_funding".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "getDivertFundingButtons", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2,
                        introMsg
                                + String.format(
                                        targetMsg,
                                        "technology you are returning (but not which technology you are researching)"),
                        codedButtons);
            }

            if ("emergency_meeting".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveEmergencyMeeting", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("f_researched".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "focusedResearch", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2,
                        introMsg + " A reminder that since you are researching,"
                                + " you need not declare what technology you will get until after other players have chosen whether they will Sabo.",
                        codedButtons);
            }

            if ("fsb".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "forwardSupplyBase", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "player"), codedButtons);
            }

            if ("tf-rise".equals(automationID)) {
                codedButtons.add(
                        Buttons.green(player.factionButtonChecker() + "riseOfAMessiah", "1 Infantry On Every Planet"));
                codedButtons.add(Buttons.green(
                        player.factionButtonChecker() + "fighterConscription", "1 Fighter With Every Ship"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-manipulate".equals(automationID)) {
                codedButtons.add(
                        Buttons.green(player.factionButtonChecker() + "resolveManipulateTF", "Resolve Manipulate"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-mutate1".equals(automationID) || "tf-mutate2".equals(automationID)) {
                codedButtons.add(Buttons.red("discardSpliceCard_ability", "Discard 1 Ability"));
                codedButtons.add(Buttons.green("drawSingularNewSpliceCard_ability", "Draw 1 Ability"));
                codedButtons.add(Buttons.gray("deleteButtons", "Done Resolving"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-evolve".equals(automationID)) {
                codedButtons.add(Buttons.green("drawSingularNewSpliceCard_units", "Draw 1 Unit Upgrade"));
                codedButtons.add(Buttons.green("drawSingularNewSpliceCard_genome", "Draw 1 Genome (Agent)"));
                codedButtons.add(Buttons.green("drawSingularNewSpliceCard_ability", "Draw 1 Ability"));
                codedButtons.add(Buttons.gray("deleteButtons", "Done Resolving"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-helix".equals(automationID)) {
                codedButtons.add(
                        Buttons.green(player.factionButtonChecker() + "startSplice_7_all", "Initiate Ability Splice"));
                codedButtons.add(Buttons.gray(
                        player.factionButtonChecker() + "startSplice_2_all", "Initiate Genome (Agent) Splice"));
                codedButtons.add(Buttons.blue(
                        player.factionButtonChecker() + "startSplice_6_all", "Initiate Unit Upgrade Splice"));
                codedButtons.add(Buttons.gray("deleteButtons", "Done Resolving"));
                for (Player p : game.getRealPlayers()) {
                    ButtonHelperActionCards.checkForPlayingSpliceCards(game, p);
                }

                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("tf-reverse".equals(automationID)) {
                codedButtons.add(Buttons.green("resolveReverseTF", buttonLabel + " (Anyone can press this)"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("tf-engineer".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveEngineer", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("tf-locust".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveLocust", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-create".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveCreate", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-unravel".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveUnravel", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-manifest".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveManifest", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-scarab".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveScarab", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-irradiate".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveIrradiate", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-ignis".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveIgnis", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-atomize".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveAtomize", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-flux".equals(automationID)) {
                codedButtons.add(
                        Buttons.green(player.factionButtonChecker() + "resolveUnexpected", "Lift a Command Token"));
                codedButtons.add(Buttons.green(
                        player.factionButtonChecker() + "resolveSignalJammingStep1", "Place a Command Token"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-starflare".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveStarFlare", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-converge".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveConverge", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("tf-lash".equals(automationID)) {
                MessageHelper.sendMessageToChannel(
                        channel2,
                        introMsg
                                + "\nJust tell the opponent what unit to destroy. They can use the assign hits button to do so.");
            }

            if ("tf-magnificence".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveMagnificence", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("tf-genophage".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveGenophage", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-coerce".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveCoerce", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-transpose1".equals(automationID) || "tf-transpose2".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveTranspose", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-reflect".equals(automationID)) {
                codedButtons.add(Buttons.green(
                        player.factionButtonChecker() + "convertComms_1_stay",
                        "Convert 1 Commodity to Trade Good",
                        MiscEmojis.Wash));
                codedButtons.add(Buttons.blue(
                        player.factionButtonChecker() + "gainComms_1_stay", "Gain 1 Commodity", MiscEmojis.comm));
                codedButtons.add(Buttons.gray("deleteButtons", "Done Resolving"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-thieve".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveThieve", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("tf-elevate".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "drawParadigmAC", "Draw Paradigm"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("messiah".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "riseOfAMessiah", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("courageous".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "courageousStarter", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }
            if ("veto".equals(automationID)) {
                codedButtons.add(Buttons.blue(player.factionButtonChecker() + "resolveVeto", "Reveal next Agenda"));
                sendResolveMsgToMainChannel(introMsg, codedButtons, player, game);
            }

            if ("f_conscription".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "fighterConscription", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if ("bribery".equals(automationID)) {
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2,
                        player.getRepresentation()
                                + ", a reminder that you should declare how many trade goods you are spending now, before other players choose whether they will Sabo.",
                        codedButtons);
            }

            if ("investments".equals(automationID)) {
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2,
                        player.getRepresentation()
                                + ", a reminder that you should declare how you are distributing the trade goods now, before other players choose whether they will Sabo.",
                        codedButtons);
            }

            if ("psionic_hammer".equals(automationID)) {
                codedButtons.add(Buttons.green(player.factionButtonChecker() + "psionicHammerStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, introMsg + String.format(targetMsg, "player"), codedButtons);
            }

            targetMsg = player.getRepresentation()
                    + ", a reminder that you should declare which %s you are targeting now, before other players choose whether they will Sabo.";

            if ("silence_space".equals(automationID)) {
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, String.format(targetMsg, "system"), codedButtons);
            }

            if ("direct_hit".equals(automationID) || "courageous".equals(automationID)) {
                MessageHelper.sendMessageToChannel(channel2, String.format(targetMsg, "ship (if multiple)"));
            }

            if ("parley".equals(automationID)) {
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, String.format(targetMsg, "planet (if multiple)"), codedButtons);
            }

            if ("ghost_squad".equals(automationID)) {
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, String.format(targetMsg, "ground forces"), codedButtons);
            }
            TeHelperActionCards.resolveTeActionCard(actionCard, player, introMsg);
            TkHelperActionCards.resolveTkActionCard(actionCard, player, introMsg);

            TemporaryCombatModifierModel combatModAC = CombatTempModHelper.getPossibleTempModifier(
                    Constants.AC, actionCard.getAlias(), player.getNumberOfTurns());
            if (combatModAC != null) {
                codedButtons.add(Buttons.green(
                        player.factionButtonChecker() + "applytempcombatmod__" + Constants.AC + "__"
                                + actionCard.getAlias(),
                        "Resolve " + actionCard.getName()));
                MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
            }

            if (actionCardWindow.contains("After an agenda is revealed")
                    || actionCardWindow.contains("After the first agenda of this agenda phase is revealed")) {
                AutoPingMetadataManager.delayPing(game.getName());

                String factionChecker = player.factionButtonChecker();
                if (actionCard.getText().toLowerCase().contains("predict aloud")) {
                    List<Button> riderButtons = AgendaHelper.getAgendaButtons(actionCardTitle, game, factionChecker);
                    MessageHelper.sendMessageToChannelWithFactionReact(
                            mainGameChannel,
                            (game.isFowMode() ? "P" : player.getRepresentation(false, true) + ", p")
                                    + "lease decide now which outcome you are predicting. If a Sabo occurs, it will automatically erase it. Reminder to also decide on other \"after\"s now.",
                            game,
                            player,
                            riderButtons);
                    for (Player p2 : game.getRealPlayers()) {
                        if (!game.getStoredValue("preVoting" + p2.getFaction()).isEmpty()) {
                            VoteButtonHandler.erasePreVoteDueToAfterPlay(p2, game);
                        }
                    }
                }
                if ("hack".equals(automationID)) {
                    game.setHasHackElectionBeenPlayed(true);
                    game.setStoredValue("hackElectionFaction", player.getFaction());
                    Button resetHack = Buttons.red("hack_election", "Set the Voting Order as Normal");
                    List<Button> hackButtons = List.of(resetHack);
                    MessageHelper.sendMessageToChannelWithFactionReact(
                            mainGameChannel,
                            "The election hacker will now vote last."
                                    + " Please hit this button if _Hack Election_ is Sabo'd.",
                            game,
                            player,
                            hackButtons);
                }
                if ("insider".equals(automationID)) {
                    codedButtons.add(
                            Buttons.green(player.factionButtonChecker() + "resolveInsiderInformation", buttonLabel));
                    MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
                }
                if ("smuggler_routes".equals(automationID)) {
                    codedButtons.add(Buttons.green(
                            player.factionButtonChecker() + "moveShipToAdjacentSystemStep1", buttonLabel));
                    MessageHelper.sendMessageToChannelWithButtons(channel2, introMsg, codedButtons);
                }
                if ("assassin".equals(automationID)) {
                    codedButtons.add(Buttons.green(player.factionButtonChecker() + "resolveAssRepsStep1", buttonLabel));
                    MessageHelper.sendMessageToChannelWithButtons(
                            channel2, introMsg + String.format(targetMsg, "player"), codedButtons);
                    for (Player p2 : game.getRealPlayers()) {
                        if (!game.getStoredValue("preVoting" + p2.getFaction()).isEmpty()) {
                            VoteButtonHandler.erasePreVoteDueToAfterPlay(p2, game);
                        }
                    }
                }
            }
            if (actionCardWindow.contains("When an agenda is revealed") && !actionCardTitle.contains("Veto")) {
                AutoPingMetadataManager.delayPing(game.getName());
            }

            if ("Action".equalsIgnoreCase(actionCardWindow) && game.getPlayer(activePlayerID) == player) {
                game.setJustPlayedComponentAC(true);
                List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
                MessageHelper.sendMessageToChannelWithButtons(
                        channel2, "Use buttons to end turn or do another action.", systemButtons);
                if (player.getLeaderIDs().contains("kelerescommander")
                        && !player.hasLeaderUnlocked("kelerescommander")) {
                    String message2 = player.getRepresentationUnfogged()
                            + " you may unleash Suffi An, your commander, by paying 1 trade good (if the action card isn't Sabo'd).";
                    List<Button> buttons2 = new ArrayList<>();
                    buttons2.add(Buttons.green(
                            "pay1tgforKeleres", "Pay 1 Trade Good to Unleash Suffi An", LeaderEmojis.KeleresCommander));
                    buttons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(channel2, message2, buttons2);
                }
                serveReverseEngineerButtons(game, player, List.of(acID));
                serveTwinningButtons(game, player, List.of(acID));
            } else {
                if (player.hasAbility("matters_of_state")) {
                    String message2 = player.getRepresentationUnfogged()
                            + " if the AC is not sabod, please gain or flip 1 balance token.";
                    List<Button> buttons2 = ButtonHelper.getBalanceButtons(player);
                    MessageHelper.sendMessageToChannelWithButtons(channel2, message2, buttons2);
                }
            }
            ArvaxiAgentButtonHandler.postInitialButtons(game, player, acID);
        }

        // Fog of war ping
        if (game.isFowMode()) {
            String fowMessage = player.getRepresentation() + " played an action card " + CardEmojis.getACEmoji(game)
                    + ": _" + actionCardTitle + "_.";
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, fowMessage);
            MessageHelper.sendPrivateMessageToPlayer(
                    player, game, "Played action card " + CardEmojis.getACEmoji(game) + ": _" + actionCardTitle + "_.");
        }
        if (player.hasUnexhaustedLeader("cymiaeagent") && player.getStrategicCC() > 0) {
            Button cymiaeButton = Buttons.gray(
                    "exhaustAgent_cymiaeagent_" + player.getFaction(), "Use Cymiae Agent", FactionEmojis.cymiae);
            MessageHelper.sendMessageToChannelWithButton(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you may use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "Skhot Unit X-12, the Cymiae"
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                            + " agent, to draw action card.",
                    cymiaeButton);
        }

        sendActionCardInfo(game, player);
        return null;
    }

    public static boolean isSabotageOrShatter(String acID) {
        return acID.contains("sabo") || acID.contains("shatter");
    }

    private static boolean isActionCardCancelable(ActionCardModel actionCard) {
        return !actionCard.getText().contains("cannot be canceled");
    }

    public static void serveManipulateInvestmentButtons(Game game, Player player) {
        List<Button> scButtons = new ArrayList<>();
        for (int sc : game.getSCList()) {
            Button button;
            TI4Emoji scEmoji = CardEmojis.getSCBackFromInteger(sc);

            String id = player.factionButtonChecker() + "increaseTGonSC_" + sc;
            if (scEmoji != CardEmojis.SCBackBlank) {
                button = Buttons.gray(id, Helper.getSCName(sc, game), scEmoji);
            } else {
                button = Buttons.gray(id, sc + " " + Helper.getSCName(sc, game));
            }
            scButtons.add(button);
        }
        scButtons.add(Buttons.red("deleteButtons", "Done Adding Trade Goods"));

        String msg = player.getRepresentation() + ", please use buttons to increase trade goods";
        msg += " on strategy cards. Each button press adds 1 trade good.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, scButtons);
    }

    public static void serveReverseEngineerButtons(Game game, Player discardingPlayer, List<String> actionCards) {
        for (Player player : game.getRealPlayers()) {
            if (player == discardingPlayer) continue;

            String reverseEngineerID = "reverse_engineer";
            if (!player.getPlayableActionCards().contains(reverseEngineerID)) continue;

            StringBuilder msg =
                    new StringBuilder(player.getRepresentationUnfogged() + " you can use _Reverse Engineer_ on ");
            if (actionCards.size() > 1) msg.append("one of the following cards:");

            List<String> notInDiscard = new ArrayList<>();
            List<Button> reverseButtons = new ArrayList<>();

            Integer reverseEngineerValue = player.getActionCards().get(reverseEngineerID);
            if (reverseEngineerValue == null) {
                reverseEngineerValue = game.getDiscardActionCards().get(reverseEngineerID);
            }
            String reversePrefix = Constants.AC_PLAY_FROM_HAND + reverseEngineerValue + "_reverse_";

            Set<String> cardNames = new HashSet<>();
            for (String acID : actionCards) {
                ActionCardModel model = Mapper.getActionCard(acID);
                if (!model.getWindow().toLowerCase().startsWith("action")) {
                    continue;
                }
                ACStatus discardStatus = game.getDiscardACStatus().get(acID);
                if (discardStatus != null) {
                    notInDiscard.add(acID);
                    continue;
                }
                cardNames.add(model.getName());
            }
            for (String name : cardNames) {
                String id = reversePrefix + name;
                String label = "Reverse Engineer " + name;
                reverseButtons.add(Buttons.green(id, label, CardEmojis.getACEmoji(game)));
                if (actionCards.size() == 1) msg.append(name).append(".");
            }

            if (!reverseButtons.isEmpty()) {
                reverseButtons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(), msg.toString(), reverseButtons);
            }
            if (!notInDiscard.isEmpty()) {
                String error =
                        "The action cards were not placed in the discard pile: " + String.join(", ", notInDiscard);
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), error);
            }
        }
    }

    private static void serveTwinningButtons(Game game, Player discardingPlayer, List<String> actionCards) {
        for (Player player : game.getRealPlayers()) {
            if (player == discardingPlayer) continue;

            String twinningId = "tf-twinning";
            if (!player.getPlayableActionCards().contains(twinningId)) continue;

            List<Button> twinningButtons = new ArrayList<>();

            Integer twinningValue = player.getActionCards().get(twinningId);
            if (twinningValue == null) {
                twinningValue = game.getDiscardActionCards().get(twinningId);
            }
            String twinningPrefix = Constants.AC_PLAY_FROM_HAND + twinningValue + "_twinning_";

            String lastCardName = null;
            for (String acID : actionCards) {
                ActionCardModel model = Mapper.getActionCard(acID);
                if (!model.getWindow().toLowerCase().startsWith("action")) {
                    continue;
                }

                String id = twinningPrefix + model.getName();
                String label = "Twin " + model.getName();
                lastCardName = model.getName();
                twinningButtons.add(Buttons.green(id, label, CardEmojis.getACEmoji(game)));
            }

            if (!twinningButtons.isEmpty()) {
                StringBuilder msg =
                        new StringBuilder(player.getRepresentationUnfogged() + ", you may use _Twinning_ on ");
                if (twinningButtons.size() > 1) {
                    msg.append("one of the following cards.");
                } else {
                    msg.append("_").append(lastCardName).append("_.");
                }
                twinningButtons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(), msg.toString(), twinningButtons);
            }
        }
    }

    private static void sendResolveMsgToMainChannel(String message, List<Button> buttons, Player player, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(
                game.getMainGameChannel(), removeRepresentationIfFOW(message, player, game), buttons);
    }

    private static String removeRepresentationIfFOW(String message, Player player, Game game) {
        return game.isFowMode()
                ? StringUtils.capitalize(
                        message.replace(player.getRepresentation() + ",", "").trim())
                : message;
    }

    public static String playAC(
            GenericInteractionCreateEvent event, Game game, Player player, String value, MessageChannel channel) {
        String acID = null;
        int acIndex = -1;
        try {
            acIndex = Integer.parseInt(value);
            for (Map.Entry<String, Integer> ac : player.getActionCards().entrySet()) {
                if (ac.getValue().equals(acIndex)) {
                    acID = ac.getKey();
                }
            }
            if (acID == null) {
                acID = getGarboziaACIdentByNumber(game, player, acIndex);
            }
        } catch (Exception e) {
            boolean foundSimilarName = false;
            String cardName = "";
            if ((acID = getGarboziaACIdentByAlias(game, player, value)) != null) {
                acIndex = game.getDiscardActionCards().get(acID);
            } else {
                for (Map.Entry<String, Integer> ac : player.getActionCards().entrySet()) {
                    String actionCardName = Mapper.getActionCard(ac.getKey()).getName();
                    if (actionCardName != null) {
                        actionCardName = actionCardName.toLowerCase();
                        if (actionCardName.contains(value) || ac.getKey().equalsIgnoreCase(value)) {
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
        }
        if (acID == null) {
            return "No such Action Card ID found, please retry";
        }
        return resolveActionCard(event, game, player, acID, acIndex, channel);
    }

    private static String getGarboziaACIdentByAlias(Game game, Player player, String key) {
        if (player.hasPlanet("garbozia")) {
            for (Entry<String, ACStatus> entry : game.getDiscardACStatus().entrySet()) {
                if (entry.getValue() != ACStatus.garbozia) continue;
                if (entry.getKey().equals(key)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private static String getGarboziaACIdentByNumber(Game game, Player player, int key) {
        if (player.hasPlanet("garbozia")) {
            for (Entry<String, ACStatus> entry : game.getDiscardACStatus().entrySet()) {
                if (entry.getValue() != ACStatus.garbozia) continue;
                if (game.getDiscardActionCards().get(entry.getKey()).equals(key)) return entry.getKey();
            }
        }
        return null;
    }

    public static void sendActionCard(
            GenericInteractionCreateEvent event, Game game, Player player, Player p2, String acID) {
        Integer handIndex = player.getActionCards().get(acID);
        ButtonHelper.checkACLimit(game, p2);
        if (acID == null || handIndex == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find action card in your hand.");
            return;
        }

        player.removeActionCard(handIndex);
        p2.setActionCard(acID);
        ButtonHelper.checkACLimit(game, p2);
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
            FoWHelper.pingPlayersTransaction(
                    game, event, player, player_, CardEmojis.getACEmoji(game) + " Action Card", null);
        }
        player.removeActionCard(actionCardsMap.get(acID));
        player_.setActionCard(acID);
        sendActionCardInfo(game, player_);
        ButtonHelper.checkACLimit(game, player_);
        sendActionCardInfo(game, player);
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                "# " + player.getRepresentation() + " you lost the action card _"
                        + Mapper.getActionCard(acID).getName() + "_.");
        MessageHelper.sendMessageToChannel(
                player_.getCardsInfoThread(),
                "# " + player_.getRepresentation() + " you gained the action card _"
                        + Mapper.getActionCard(acID).getName() + "_.");
    }

    public static void showAll(Player player, Player player_, Game game) {
        StringBuilder sb = new StringBuilder();
        StringBuilder sa = new StringBuilder();
        sa.append("Your action cards were shown to: ")
                .append(game.isFowMode() ? "Someone" : player_.getUserName())
                .append('\n');
        sa.append(
                "Action cards were presented in the order below. You may reference the number listed when discussing the cards:\n");
        sb.append("Game: ").append(game.getName()).append('\n');
        sb.append("Player: ")
                .append(game.isFowMode() ? player.getColor() : player.getUserName())
                .append('\n');
        sb.append(
                        "Showed Action Cards, they were also presented the cards in the order you see them so you may reference the number when talking to them:")
                .append('\n');
        List<String> actionCards = new ArrayList<>(player.getActionCards().keySet());
        Collections.shuffle(actionCards);
        int index = 1;
        for (String id : actionCards) {
            sa.append(index)
                    .append("\\. ")
                    .append(Mapper.getActionCard(id).getRepresentation(game))
                    .append('\n');
            sb.append(index)
                    .append("\\. ")
                    .append(Mapper.getActionCard(id).getRepresentation(game))
                    .append('\n');
            index++;
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, sa.toString());
        MessageHelper.sendMessageToPlayerCardsInfoThread(player_, sb.toString());
    }

    public static String actionCardListCondensedNoIds(List<String> discards, String title) {
        StringBuilder sb = new StringBuilder();
        if (title != null) sb.append("__").append(title).append("__:");
        Map<String, List<String>> cardsByName = discards.stream()
                .collect(Collectors.groupingBy(ac -> Mapper.getActionCard(ac).getName()));
        int index = 1;

        List<Map.Entry<String, List<String>>> displayOrder = new ArrayList<>(cardsByName.entrySet());
        displayOrder.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, List<String>> acEntryList : displayOrder) {
            sb.append('\n').append(index).append("\\. ");
            index++;
            sb.append(CardEmojis.ActionCard.toString()
                    .repeat(acEntryList.getValue().size()));
            sb.append(" _").append(acEntryList.getKey()).append("_");
        }
        return sb.toString();
    }

    public static void pickACardFromDiscardStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String acStringID : game.getDiscardActionCards().keySet()) {
            if (!isDiscardActionCardPickable(game, acStringID)) {
                continue;
            }
            buttons.add(Buttons.green(
                    "pickFromDiscard_" + acStringID,
                    Mapper.getActionCard(acStringID).getName()));
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
        String msg =
                player.getRepresentationUnfogged() + ", use buttons to retrieve an action card from the discard pile.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void pickACardFromDiscardStep2(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        ButtonHelper.deleteMessage(event);
        String acID = buttonID.replace("pickFromDiscard_", "");
        if (!isDiscardActionCardPickable(game, acID)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
            return;
        }
        boolean picked = game.pickActionCard(
                player.getUserID(), game.getDiscardActionCards().get(acID));
        if (!picked) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
            return;
        }
        String msg2 = player.getRepresentationUnfogged() + " retrieved _"
                + Mapper.getActionCard(acID).getName() + "_ from the action card discard pile.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);

        sendActionCardInfo(game, player, event);
        if (player.hasAbility("autonetic_memory")) {
            String message;
            if (player.hasRelic("codex") || player.hasRelic("absol_codex")) {
                message = player.getRepresentationUnfogged()
                        + ", if you did not just use _The Codex_ to get that action card,"
                        + " please discard 1 action card due to your **Cybernetic Madness** ability.";
            } else {
                message = player.getRepresentationUnfogged()
                        + ", please discard 1 action card due to your **Cybernetic Madness** ability.";
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(), message, getDiscardActionCardButtons(player, false));
        }
        ButtonHelper.checkACLimit(game, player);
    }

    public static void getActionCardFromDiscard(
            GenericInteractionCreateEvent event, Game game, Player player, int acIndex) {
        String acId = getDiscardedAcID(game, acIndex);

        if (acId == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No such Action Card ID found, please retry");
            return;
        }
        boolean picked = game.pickActionCard(player.getUserID(), acIndex);
        if (!picked) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No such Action Card ID found, please retry");
            return;
        }
        String sb = "Game: " + game.getName() + " " + "Player: "
                + player.getUserName() + "\n" + "Picked card from Discards: "
                + Mapper.getActionCard(acId).getRepresentation(game)
                + "\n";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb);

        sendActionCardInfo(game, player);
    }

    public static String getDiscardedAcID(Game game, int acIndex) {
        return game.getDiscardActionCards().entrySet().stream()
                .filter(e -> e.getValue().equals(acIndex))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private static boolean isDiscardActionCardPickable(Game game, String acId) {
        ACStatus status = game.getDiscardACStatus().get(acId);
        return status == null;
    }

    @ButtonHandler("riseOfAMessiah")
    public static void riseOfAMessiah(ButtonInteractionEvent event, Player player, Game game) {
        doRise(player, event, game);
        ButtonHelper.deleteMessage(event);
    }

    public static void doRise(Player player, GenericInteractionCreateEvent event, Game game) {
        List<String> planets = player.getPlanetsAllianceMode();
        StringBuilder sb = new StringBuilder();
        sb.append(player.getRepresentationNoPing())
                .append(" added one ")
                .append(UnitEmojis.infantry)
                .append(" to each of: ");
        int count = 0;
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getPlanetUnitHolders()) {
                if (planets.contains(unitHolder.getName())) {
                    Set<String> tokenList = unitHolder.getTokenList();
                    boolean ignorePlanet = false;
                    for (String token : tokenList) {
                        if (token.contains("dmz")
                                || token.contains(Constants.WORLD_DESTROYED)
                                || token.contains("arcane_shield")) {
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
            sb = new StringBuilder(player.getRepresentationNoPing())
                    .append(" did not have any planets which could receive +1 infantry");
        } else if (count > 5) {
            sb.append("\n> Total of ").append(count);
        }
        CommanderUnlockCheckService.checkPlayer(player, "obsidian", "arborec");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
    }

    @ButtonHandler("fighterConscription")
    public static void fighterConscription(ButtonInteractionEvent event, Player player, Game game) {
        doFfCon(event, player, game);
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
            if (game.isTwilightsFallMode()) {
                if (FoWHelper.playerHasActualShipsInSystem(player, tile) || (!blockaded && hasSD)) {
                    AddUnitService.addUnits(event, tile, game, player.getColor(), "ff");
                    tilesAffected.add(tile);
                }
            } else {
                if (!blockaded && (hasCap || hasSD)) {
                    AddUnitService.addUnits(event, tile, game, player.getColor(), "ff");
                    tilesAffected.add(tile);
                }
            }
        }

        String msg = "Added " + tilesAffected.size() + " fighter" + (tilesAffected.size() == 1 ? "" : "s") + ".";
        if (!tilesAffected.isEmpty()) {
            msg += " Please check fleet size and capacity in each of the systems: ";
            ButtonHelper.checkFleetInEveryTile(player, game);
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

    static boolean playerHasWarMachine(Player player) {
        return player.getPlayableActionCards().stream().anyMatch(WAR_MACHINE_IDS::contains);
    }

    private CombatReplayTrackedEvent getCombatReplayTrackedEvent(ActionCardModel actionCard) {
        if (actionCard == null || actionCard.getAlias() == null) return CombatReplayTrackedEvent.NONE;
        String alias = actionCard.getAlias();
        String automationId = actionCard.getAutomationID();
        if (MORALE_BOOST_IDS.contains(alias)) return CombatReplayTrackedEvent.MORALE_BOOST;
        if (SHIELDS_HOLDING_IDS.contains(alias)) return CombatReplayTrackedEvent.SHIELDS_HOLDING;
        if ("direct_hit".equalsIgnoreCase(automationId)) return CombatReplayTrackedEvent.DIRECT_HIT;
        if ("f_prototype".equalsIgnoreCase(automationId)) return CombatReplayTrackedEvent.FIGHTER_PROTOTYPE;
        if ("Rout".equalsIgnoreCase(actionCard.getName()) || alias.startsWith("rout")) {
            return CombatReplayTrackedEvent.ROUT;
        }
        return CombatReplayTrackedEvent.NONE;
    }
}
