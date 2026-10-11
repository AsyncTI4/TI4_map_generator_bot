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
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.collections4.CollectionUtils;
import ti4.contest.replay.core.CombatReplayTrackedEvent;
import ti4.contest.replay.service.CombatReplayService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.actioncards.ActionCardPingButtonHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.XinTechHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.oblivion.OblivionUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.arvaxi.ArvaxiLeaderHandler;
import ti4.discord.interactions.buttons.handlers.unit.monuments.MonumentsBRButtonHandler;
import ti4.discord.interactions.buttons.handlers.unit.monuments.MonumentsButtonHandler;
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
import ti4.model.Source.ComponentSource;
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
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.service.turn.StartTurnService;
import ti4.service.unit.AddUnitService;
import ti4.spring.context.SpringContext;
import ti4.spring.service.gameevent.GameEventDraft;
import ti4.spring.service.gameevent.GameEventService;
import ti4.spring.service.gameevent.GameEventType;
import ti4.spring.service.gameevent.GameSubEvent;

@UtilityClass
public class ActionCardHelper {

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

    /**
     * Whether action cards that were discarded rather than played must be hidden from {@code viewer}.
     * Only ever true in Fog of War games with the option turned on, and never for a GM.
     * The option is checked before the viewer, because {@link Player#isGM()} is a guild role lookup.
     */
    public static boolean hidesUnplayedDiscards(Game game, Player viewer) {
        if (!game.isFowMode() || !game.getFowOption(FOWOption.HIDE_AC_DISCARD)) return false;
        return viewer == null || !viewer.isGM();
    }

    /** Whether {@code viewer} is allowed to see, and interact with, a given card in the discard pile. */
    public static boolean isDiscardVisible(Game game, Player viewer, String acId) {
        return isDiscardVisible(game, hidesUnplayedDiscards(game, viewer), acId);
    }

    /**
     * @param hideUnplayed the result of {@link #hidesUnplayedDiscards}, hoisted out of the loop by callers that
     *                     check a whole pile so the GM role lookup only happens once.
     */
    public static boolean isDiscardVisible(Game game, boolean hideUnplayed, String acId) {
        return !hideUnplayed || game.getPlayedActionCards().contains(acId);
    }

    public static void sendActionCardInfo(Game game, Player player) {
        // AC INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, getActionCardInfo(game, player));
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
        game.getDiscardACStatus().forEach((acAlias, status) -> {
            if (status == ActionCardHelper.ACStatus.garbozia) {
                cards.put(acAlias, game.getDiscardActionCards().get(acAlias));
            }
        });
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
                if (MonumentsBRButtonHandler.hasArmageddonProjectSuperweapon(player.getGame(), player, id)) {
                    sb.append("\nCopied by Armageddon Project");
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
        List<GenericCardModel> plots = player.getPlotCards().entrySet().stream()
                .sorted(Comparator.comparingInt(Entry::getValue))
                .map(plot -> Mapper.getPlot(plot.getKey()))
                .toList();
        for (GenericCardModel plot : plots) {
            buttons.add(Buttons.green("addFactionTokenToPlot_" + plot.getAlias(), plot.getName()));
        }
        for (GenericCardModel plot : plots) {
            List<String> factions = player.getPuppetedFactionsForPlot(plot.getAlias());
            if (factions != null && !factions.isEmpty()) {
                buttons.add(
                        Buttons.red("removeFactionTokenFromPlot_" + plot.getAlias(), "Remove From " + plot.getName()));
            }
        }
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
                buttons.add(Buttons.green(id, "", p.getFactionEmojiOrColor()));
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
                        .append(actionCard.getRepresentationJustText((game)))
                        .append('\n');
                if (actionCard.getNotes() != null) {
                    // isTwilightKart is Deprecated. remove entire if-statement once isTwilightKart is removed
                    if (game != null && game.isTwilightKart() && "tf-starflare".equalsIgnoreCase(actionCard.getID())) {
                        continue;
                    }
                    sb.append("> -# [").append(actionCard.getNotes()).append("]\n");
                }
            }
        }
        return sb.toString();
    }

    private static List<Button> getPlayActionCardButtons(Game game, Player player) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> actionCards = player.getActionCards();

        boolean censured = isCensured(game, player);
        if (actionCards != null && !actionCards.isEmpty() && !censured) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String acName = Mapper.getActionCard(key).getName();
                acButtons.add(Buttons.red(
                        Constants.AC_PLAY_FROM_HAND + value, "(" + value + ") " + acName, CardEmojis.getACEmoji(game)));
            }
        }
        if (censured) {
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

    public static boolean isCensured(Game game, Player player) {
        return IsPlayerElectedService.isPlayerElected(game, player, "censure")
                || IsPlayerElectedService.isPlayerElected(game, player, "absol_censure");
    }

    private static boolean hasPrePlayCards(Player player) {
        List<String> prePlayable = List.of(
                "coup",
                "crisis",
                "tf-stasis",
                "extremeduress",
                "disgrace",
                "investments",
                "tf-reverse",
                "puppetsonastring",
                "tf-manipulate",
                "revolution",
                "deflection",
                "summit",
                "bounty_contracts",
                "tk-compose",
                "relitigate");
        List<String> actionCards = new ArrayList<>(player.getActionCards().keySet());
        if (player.hasPlanet("garbozia")) {
            actionCards.addAll(getGarboziaActionCards(player.getGame()).keySet());
        }
        return CollectionUtils.containsAny(prePlayable, actionCards);
    }

    public static List<Button> getActionPlayActionCardButtons(Player player) {
        if (isCensured(player.getGame(), player)) {
            return new ArrayList<>();
        }
        return getWindowedActionCardButtons(player, "action"::equalsIgnoreCase);
    }

    public static List<Button> getCombatActionCardButtons(Player player) {
        return getWindowedActionCardButtons(
                player, window -> window.contains("combat") || window.contains("roll") || window.contains("hit"));
    }

    private static List<Button> getWindowedActionCardButtons(Player player, Predicate<String> windowFilter) {
        List<Button> acButtons = new ArrayList<>();
        addPlayActionCardButtons(acButtons, player.getActionCards(), windowFilter, CardEmojis.getACEmoji(player));
        if (player.hasPlanet("garbozia")) {
            addPlayActionCardButtons(
                    acButtons, getGarboziaActionCards(player.getGame()), windowFilter, MiscEmojis.LegendaryPlanet);
        }
        return acButtons;
    }

    private static void addPlayActionCardButtons(
            List<Button> acButtons, Map<String, Integer> cards, Predicate<String> windowFilter, TI4Emoji emoji) {
        if (cards == null) return;
        for (Entry<String, Integer> ac : cards.entrySet()) {
            ActionCardModel actionCard = Mapper.getActionCard(ac.getKey());
            if (windowFilter.test(actionCard.getWindow())) {
                acButtons.add(Buttons.red(
                        Constants.AC_PLAY_FROM_HAND + ac.getValue(),
                        "(" + ac.getValue() + ") " + actionCard.getName(),
                        emoji));
            }
        }
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
        if (XinTechHandler.hasAstromanticCloakSteel(player)) {
            return acButtons;
        }
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
        StringBuilder message = new StringBuilder(player.getRepresentationNoPing() + " discarded "
                + StringHelper.pluralize(count, "random action card") + ".\n");
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

        String message = player.getRepresentation() + " drew " + StringHelper.pluralize(count, "action card") + ".";
        if (scheming && player.hasAbility("scheming")) {
            count++;
            message = player.getRepresentation() + " drew " + StringHelper.pluralize(count, "action card")
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
            if (activePlayer != null
                    && activePlayer != player
                    && (activePlayer.hasTech("tp") || activePlayer.hasTech("tf-crafty"))) {
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
                game.getPlayedActionCards().add(acID);
                if (!game.isFowMode()) {
                    fromGarbozia = true;
                }
            } else if (player.hasAbility("cybernetic_madness")) {
                game.purgedActionCard(player.getUserID(), acIndex, true);
                OblivionUnitHandler.doOblivionMechCheck(game, player);
            } else {
                game.discardActionCard(player.getUserID(), acIndex, true);
            }
        }
        recordTrackedActionCardPlay(game, player, actionCardTitle);
        if (!GameEventDraft.stage(
                game, new GameSubEvent.ActionCardPlayed(player.getFaction(), acID, actionCardTitle))) {
            GameEventService.commit(
                    game,
                    GameEventType.CARD_PLAY_ACTION_CARD,
                    player,
                    Map.of("cardId", acID, "cardName", actionCardTitle));
        }

        boolean hasUnyieldingWill = player.hasTech("baarvag");
        boolean actionCardIsCancelable = isActionCardCancelable(actionCard) && !twinned && !hasUnyieldingWill;

        String pingGame = actionCardIsCancelable ? game.getPing() + ", " : "";
        String message = pingGame + FoWHelper.actorOrAnon(game, player, "someone");
        message += fromGarbozia ? " purged " : " played ";
        message += "the action card _" + actionCardTitle + "_";
        message += fromGarbozia ? " using _Dok 'N Pic's Salvage Yard_." : ".";
        if (hasUnyieldingWill && isActionCardCancelable(actionCard) && !twinned) {
            message += " This card cannot be canceled due to _Unyielding Will_.";
        }

        List<Button> buttons = new ArrayList<>();

        boolean actionCardIsSabotageOrShatter = isSabotageOrShatter(acID);
        if (actionCardIsCancelable && !actionCardIsSabotageOrShatter) {
            Button sabotageButton = Buttons.red(
                    "sabotage_ac_" + actionCardTitle + "_" + player.getFaction(),
                    "Cancel Action Card With Sabotage",
                    MiscEmojis.Sabotage);
            buttons.add(sabotageButton);
        }

        MonumentsButtonHandler.gainKVDTradeGoods(game, player, actionCardTitle);

        if (actionCardIsCancelable) {
            for (Player empy : game.getRealPlayers()) {
                if (!empy.hasUnit("empyrean_mech")
                        || !ButtonHelperFactionSpecific.isNextToEmpyMechs(game, player, empy)
                        || ButtonHelper.isLawInPlay(game, "articles_war")) {
                    continue;
                }
                Button empyButton = Buttons.gray(
                        "sabotage_empy_" + actionCardTitle + "_" + player.getFaction(),
                        "Cancel " + actionCardTitle + " With Watcher",
                        UnitEmojis.mech);
                List<Button> empyButtons = new ArrayList<>();
                empyButtons.add(empyButton);
                Button refuse = Buttons.DELETE_BUTTONS;
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
                Button refuse = Buttons.DELETE_BUTTONS;
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
                    xxchaButtons.add(Buttons.DELETE_BUTTONS);
                    MessageHelper.sendMessageToChannelWithButtons(
                            player2.getCardsInfoThread(),
                            player2.getRepresentationUnfogged()
                                    + ", you have _Instinct Training_ readied and a command token available in your strategy pool."
                                    + " Use these buttons to decide whether to Sabo _" + actionCardTitle + "_.",
                            xxchaButtons);
                }
            }
        }
        Consumer<Message> pingPrompt = game.isFowMode()
                ? sentMessage ->
                        ActionCardPingButtonHandler.sendPingPrompt(player, sentMessage.getId(), actionCardTitle)
                : null;

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
            MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                    mainGameChannel, message, Collections.singletonList(acEmbed), null, pingPrompt);
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
                MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                        mainGameChannel, message, Collections.singletonList(acEmbed), null, pingPrompt);
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
                            mainGameChannel,
                            message,
                            game,
                            player,
                            Collections.singletonList(acEmbed),
                            buttons,
                            true,
                            pingPrompt);
                } else {
                    MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                            mainGameChannel, message, Collections.singletonList(acEmbed), null, pingPrompt);
                    StringBuilder noSabosMessage = new StringBuilder("> " + SabotageService.noSaboReason(game, player));
                    if (!game.isFowMode()) {
                        boolean instinctTraining = false, watcher = false, triune = false;
                        for (Player p : game.getRealPlayers()) {
                            if (p == player) continue;
                            if (!instinctTraining && p.hasTechReady("it")) {
                                noSabosMessage.append(
                                        "\n> A player may have access to **Instinct Training**, so watch out.");
                                instinctTraining = true;
                            }
                            if (!watcher && p.hasUnit("empyrean_mech")) {
                                noSabosMessage.append(
                                        "\n> A player may have access to a Watcher mech, so 𝓌𝒶𝓉𝒸𝒽 out.");
                                watcher = true;
                            }
                            if (!triune && p.hasUnit("tf-triune")) {
                                noSabosMessage.append(
                                        "\n> A player may have access to 3 Triune fighters, so watch out.");
                                triune = true;
                            }
                        }
                    }
                    MessageHelper.sendMessageToChannel(mainGameChannel, noSabosMessage.toString());
                }
            }

            MessageChannel channel2 = player.getCorrectChannel();
            String cancelReminder = actionCardIsCancelable ? "after checking for Sabos, " : "";
            // The body (everything after "<rep>, ") is kept separate so the main-channel sender can
            // omit the representation entirely under fog instead of stripping it back out afterwards.
            String introBody = cancelReminder + "please use buttons to resolve _" + actionCardTitle + "_.";
            String introMsg = player.getRepresentation() + ", " + introBody;

            List<Button> codedButtons = new ArrayList<>();
            if (automationID != null) {
                resolveAutomatedActionCard(game, player, automationID, buttonLabel, introBody, introMsg, codedButtons);
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
                    String riderName = actionCard.getSource() == ComponentSource.theodisi
                                    && "exploration_rider".equals(actionCard.getAutomationID())
                            ? "Exploration Rider (LL)"
                            : actionCardTitle;
                    List<Button> riderButtons = AgendaRiderHelper.getAgendaButtons(riderName, game, factionChecker);
                    MessageHelper.sendMessageToChannelWithFactionReact(
                            mainGameChannel,
                            (game.isFowMode() ? "P" : player.getRepresentation(false, true) + ", p")
                                    + "lease decide now which outcome you are predicting. If a Sabo occurs, it will automatically erase it. Reminder to also decide on other \"after\"s now.",
                            game,
                            player,
                            riderButtons);
                    erasePreVotes(game);
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
                    addGreenAndSend(player, codedButtons, "resolveInsiderInformation", buttonLabel, introMsg);
                }
                if ("smuggler_routes".equals(automationID)) {
                    addGreenAndSend(player, codedButtons, "moveShipToAdjacentSystemStep1", buttonLabel, introMsg);
                }
                if ("assassin".equals(automationID)) {
                    addGreenAndSend(
                            player,
                            codedButtons,
                            "resolveAssRepsStep1",
                            buttonLabel,
                            introMsg + declareReminder(player, "player"));
                    erasePreVotes(game);
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
                    buttons2.add(Buttons.DECLINE);
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
            ArvaxiLeaderHandler.postAgentOfferButtons(game, player, acID);
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

    public static boolean canPlayActionCards(Player player) {
        if (player.isNpc() || player.isDummy()) return false;

        return !player.getPlayableActionCards().isEmpty();
    }

    private static boolean isActionCardCancelable(ActionCardModel actionCard) {
        return !actionCard.getText().contains("cannot be canceled");
    }

    public static boolean cannotBeSabotaged(ActionCardModel actionCard) {
        return isSabotageOrShatter(actionCard.getAlias()) || !isActionCardCancelable(actionCard);
    }

    public static void serveManipulateInvestmentButtons(Game game, Player player) {
        List<Button> scButtons = getStrategyCardButtons(game, player, "increaseTGonSC_");
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
                reverseButtons.add(Buttons.DECLINE);
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
                twinningButtons.add(Buttons.DECLINE);
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(), msg.toString(), twinningButtons);
            }
        }
    }

    private record AutomatedResolve(String handlerId, String suffix) {}

    private static final String TARGET_REMINDER =
            " A reminder that you should declare which %s you are targeting now, before other players choose whether they will Sabo.";
    private static final String RESEARCH_REMINDER = " A reminder that since you are researching,"
            + " you need not declare what technology you will get until after other players have chosen whether they will Sabo.";

    private static AutomatedResolve plain(String handlerId) {
        return new AutomatedResolve(handlerId, "");
    }

    private static AutomatedResolve target(String handlerId, String noun) {
        return new AutomatedResolve(handlerId, String.format(TARGET_REMINDER, noun));
    }

    private static AutomatedResolve research(String handlerId) {
        return new AutomatedResolve(handlerId, RESEARCH_REMINDER);
    }

    private static final Map<String, AutomatedResolve> AUTOMATED_RESOLVES = Map.ofEntries(
            Map.entry("plagiarize", target("getPlagiarizeButtons", "technology")),
            Map.entry("mining_initiative", target("miningInitiative", "planet")),
            Map.entry("revolution", plain("willRevolution")),
            Map.entry("economic_initiative", plain("economicInitiative")),
            Map.entry("reveal_prototype", research("resolveResearch")),
            Map.entry("side_project", research("sideProject")),
            Map.entry("brutal_occupation", target("brutalOccupation", "planet")),
            Map.entry("stolen_prototype", target("resolveResearch", "destroyed unit")),
            Map.entry("reparations", target("resolveReparationsStep1", "planets")),
            Map.entry("distinguished", plain("resolveDistinguished")),
            Map.entry("uprising", target("resolveUprisingStep1", "planet")),
            Map.entry("tomb_raiders", plain("resolveTombRaiders")),
            Map.entry("artifact_hunters", plain("resolveArtifactHunters")),
            Map.entry("artifact_research", plain("resolveArtifactResearch")),
            Map.entry("bounty", target("resolveBounty", "destroyed unit")),
            Map.entry("heist", plain("resolveHeist")),
            Map.entry("mysterious_brokers", plain("resolveMysteriousBrokers")),
            Map.entry("survey", plain("resolveSurvey")),
            Map.entry("prisoners_of_war", plain("resolvePrisonersOfWar")),
            Map.entry("mobilization", plain("resolveMobilization")),
            Map.entry("project_rider", plain("resolveProjectRider")),
            Map.entry("innovation", plain("innovation")),
            Map.entry("liberation", target("resolveLiberation", "planet")),
            Map.entry("reconstruction", target("resolveReconstruction", "planet in the active system")),
            Map.entry("ubiquity", plain("ubiquity")),
            Map.entry("syndicate", plain("resolveSyndicate")),
            Map.entry("lost_treatise", plain("resolveLostTreatise")),
            Map.entry("opportunists", plain("resolveOpportunists")),
            Map.entry("arbitration", plain("resolveArbitration")),
            Map.entry("jamming", target("resolveSignalJammingStep1", "system and player")),
            Map.entry("spy", target("resolveSpyStep1", "player")),
            Map.entry("stability", plain("resolvePSStep1")),
            Map.entry("plague", target("resolvePlagueStep1", "planet")),
            Map.entry("micrometeoroid_storm", target("resolveMicrometeoroidStormStep1", "system")),
            Map.entry("cripple", target("resolveCrippleDefensesStep1", "planet")),
            Map.entry("impersonation", plain("resolveImpersonation")),
            Map.entry("abs", target("resolveABSStep1", "player")),
            Map.entry("salvage", plain("resolveSalvageStep1")),
            Map.entry("insub", target("resolveInsubStep1", "player")),
            Map.entry("f_deployment", target("resolveFrontline", "planet")),
            Map.entry("unexpected", target("resolveUnexpected", "system")),
            Map.entry("unexpected_strategy", target("resolveUnexpectedStrategy", "system")),
            Map.entry("data_archive", target("resolveDataArchive", "planets")),
            Map.entry("oracle", plain("resolveOracle")),
            Map.entry("intrigue", plain("resolveIntrigue")),
            Map.entry("ancient_trade_routes", target("resolveAncientTradeRoutes", "players")),
            Map.entry("armistice", target("resolveArmistice", "player")),
            Map.entry("decisive_victory", plain("resolveDecisiveVictory")),
            Map.entry("refabrication", target("resolveSisterShip", "destroyed ship")),
            Map.entry("boarding_party", target("resolveBoardingParty", "destroyed ship")),
            Map.entry("contingency", plain("resolveContingency")),
            Map.entry("reinforcements", plain("resolveReinforcements")),
            Map.entry("overtime", plain("resolveOvertime")),
            Map.entry("chain_reaction", plain("resolveChainReaction")),
            Map.entry("hostile_world", plain("resolveHostileWorld")),
            Map.entry("flawless_strategy", plain("resolveFlawlessStrategy")),
            Map.entry("arms_deal", target("resolveArmsDeal", "player")),
            Map.entry("betrayal", target("resolveBetrayal", "player")),
            Map.entry("espionage", target("resolveEspionage", "player")),
            Map.entry("rehashed_debates", plain("resolveRehashedDebates")),
            Map.entry("defectors", target("resolveDefectors", "player")),
            Map.entry("defense_installation", target("resolveDefenseInstallation", "planet")),
            Map.entry("cache", target("resolveCache", "planet")),
            Map.entry("compounding_interests", plain("resolveCompoundingInterests")),
            Map.entry("strategic_focus", plain("resolveStrategicFocus")),
            Map.entry("derelict_station", plain("resolveDerelictStation")),
            Map.entry("overthrow", plain("resolveOverthrow")),
            Map.entry("sacrifice", plain("resolveSacrifice")),
            Map.entry("scorched_earth", target("resolveScorchedEarth", "planet")),
            Map.entry("fractured_reality", plain("resolveFracturedReality")),
            Map.entry("propaganda_te", plain("resolvePropagandaTe")),
            Map.entry("truce", plain("resolveTruce")),
            Map.entry("settlers", plain("resolveSettlers")),
            Map.entry("settlements", plain("resolveSettlements")),
            Map.entry("privateers", plain("resolvePrivateers")),
            Map.entry("abduction", plain("resolveAbduction")),
            Map.entry("pivot", plain("resolvePivot")),
            Map.entry("mass_transference", plain("resolveMassTransference")),
            Map.entry("joint_research", plain("resolveJointResearch")),
            Map.entry("false_flag", plain("resolveFalseFlag")),
            Map.entry("ixthian_gift", plain("resolveIxthianGift")),
            Map.entry("liquidation", plain("resolveLiquidation")),
            Map.entry("public_outrage", plain("resolvePublicOutrage")),
            Map.entry("public_support", plain("resolvePublicSupport")),
            Map.entry("reengineer", plain("resolveReengineer")),
            Map.entry("field_test", plain("resolveFieldTest")),
            Map.entry("retrofit", plain("resolveRetrofit")),
            Map.entry("stasis_haul", plain("resolveStasisHaul")),
            Map.entry("freedom_fighters", plain("resolveFreedomFighters")),
            Map.entry("black_market_intel", plain("resolveBlackMarketIntel")),
            Map.entry("hidden_initiatives", plain("resolveHiddenInitiatives")),
            Map.entry("refugees", plain("resolveRefugees")),
            Map.entry("concord", plain("resolveConcord")),
            Map.entry("black_market_raid", plain("resolveBlackMarketRaid")),
            Map.entry("simulacrum", plain("resolveSimulacrum")),
            Map.entry("harness", plain("resolveHarness")),
            Map.entry("war_effort", target("resolveWarEffort", "system")),
            Map.entry("free_trade_network", plain("resolveFreeTrade")),
            Map.entry("preparation", plain("resolvePreparation")),
            Map.entry("summit", plain("resolveSummit")),
            Map.entry("classified_weapons", target("resolveClassifiedWeapons", "unit")),
            Map.entry("scuttle", target("startToScuttleAUnit_0", "ships")),
            Map.entry("lucky", target("startToLuckyShotAUnit_0", "ship")),
            Map.entry("refit", target("resolveRefitTroops", "infantry")),
            Map.entry("seize", target("resolveSeizeArtifactStep1", "player")),
            Map.entry("renegotiation", target("resolveDiplomaticPressureStep1", "player")),
            Map.entry("meltdown", target("resolveReactorMeltdownStep1", "space dock")),
            Map.entry("unstable", target("resolveUnstableStep1", "planet")),
            Map.entry("ghost_ship", target("resolveGhostShipStep1", "system")),
            Map.entry("covert_operation", plain("resolveCovertOperation")),
            Map.entry("corruption", plain("resolveCorruption")),
            Map.entry("seized_research", target("resolveSeizedResearch", "planet")),
            Map.entry("stranded_ship", target("strandedShipStep1", "system")),
            Map.entry("tactical", target("resolveTacticalBombardmentStep1", "system")),
            Map.entry("probe", target("resolveProbeStep1", "system")),
            Map.entry("rally", plain("resolveRally")),
            Map.entry("industrial_initiative", plain("industrialInitiative")),
            Map.entry("repeal", target("getRepealLawButtons", "law")),
            Map.entry("emergency_meeting", plain("resolveEmergencyMeeting")),
            Map.entry("f_researched", research("focusedResearch")),
            Map.entry("fsb", target("forwardSupplyBase", "player")),
            Map.entry("tf-engineer", plain("resolveEngineer")),
            Map.entry("tf-locust", plain("resolveLocust")),
            Map.entry("tf-create", plain("resolveCreate")),
            Map.entry("tf-unravel", plain("resolveUnravel")),
            Map.entry("tf-manifest", plain("resolveManifest")),
            Map.entry("tf-scarab", plain("resolveScarab")),
            Map.entry("tf-irradiate", plain("resolveIrradiate")),
            Map.entry("tf-ignis", plain("resolveIgnis")),
            Map.entry("tf-atomize", plain("resolveAtomize")),
            Map.entry("tf-starflare", plain("resolveStarFlare")),
            Map.entry("tf-converge", plain("resolveConverge")),
            Map.entry("tf-magnificence", plain("resolveMagnificence")),
            Map.entry("tf-genophage", plain("resolveGenophage")),
            Map.entry("tf-coerce", plain("resolveCoerce")),
            Map.entry("tf-thieve", plain("resolveThieve")),
            Map.entry("messiah", plain("riseOfAMessiah")),
            Map.entry("fire_team", plain("resolveFireTeam")),
            Map.entry("f_conscription", plain("fighterConscription")),
            Map.entry("unchart_space", plain("resolveUnchartedSpaceAC")),
            Map.entry("ancient_maps", plain("resolveAncientMapsAC")),
            Map.entry("dark_energy_spike", plain("resolveDarkEnergySpike")),
            Map.entry("overlooked_findings", plain("resolveOverlookedFindings")),
            Map.entry("mass_hypnosis", plain("resolveMassHypnosis")),
            Map.entry("mirror_shielding", plain("resolveMirrorShielding")),
            Map.entry("raised_morale", plain("resolveRaisedMorale")),
            Map.entry("relitigate", plain("resolveRelitigate")),
            Map.entry("extension_refit", plain("resolveExtensionRefit")),
            Map.entry("tactical_retreat", plain("resolveTacticalRetreat")),
            Map.entry("rigged_explosives", plain("resolveRiggedExplosives")),
            Map.entry("dedicated_study", plain("resolveDedicatedStudy")),
            Map.entry("borrowed_time", plain("resolveBorrowedTime")),
            Map.entry("political_marriage", plain("resolvePoliticalMarriage")),
            Map.entry("combat_initiative", plain("resolveCombatInitiative")),
            Map.entry("precision_targeting", plain("resolvePrecisionTargeting")),
            Map.entry("orbital_evacuation", plain("resolveOrbitalEvacuation")),
            Map.entry("administrative_exemption", plain("resolveAdministrativeExemption")),
            Map.entry("emergency_appropriations", plain("resolveEmergencyAppropriations")),
            Map.entry("wildlife_preservation", plain("resolveWildlifePreservation")),
            Map.entry("retrofitting", plain("resolveRetrofitting")),
            Map.entry("senate_gridlock", plain("resolveSenateGridlock")),
            Map.entry("archaeologist_coop", plain("resolveArchaeologistCoop")),
            Map.entry("collaborative_research", plain("resolveCollaborativeResearch")),
            Map.entry("shared_resources", plain("resolveSharedResources")),
            Map.entry("forward_assembly", plain("resolveForwardAssembly")),
            Map.entry("priority_requisition", plain("resolvePriorityRequisition")),
            Map.entry("ecological_survey", plain("resolveEcologicalSurvey")),
            Map.entry("enlistment", plain("resolveEnlistment")),
            Map.entry("deconstructed_militia", plain("resolveDeconstructedMilitia")),
            Map.entry("prototype_deployment", plain("resolvePrototypeDeployment")),
            Map.entry("courageous", plain("courageousStarter")),
            Map.entry("professional_archeologists", plain("resolveProfessionalArcheologists")),
            Map.entry("psionic_hammer", target("psionicHammerStep1", "player")),
            Map.entry("tf-transpose1", plain("resolveTranspose")),
            Map.entry("tf-transpose2", plain("resolveTranspose")),
            Map.entry(
                    "divert_funding",
                    target(
                            "getDivertFundingButtons",
                            "technology you are returning (but not which technology you are researching)")),
            Map.entry(
                    "infiltrate",
                    new AutomatedResolve(
                            "resolveInfiltrateStep1",
                            String.format(TARGET_REMINDER, "planet")
                                    + " Warning, this will not work if the player has already removed their structures.")),
            Map.entry("initiative_outpost", plain("resolveTheodisiOutpost_initiative_outpost")),
            Map.entry("exploration_outpost", plain("resolveTheodisiOutpost_exploration_outpost")),
            Map.entry("assembly_outpost", plain("resolveTheodisiOutpost_assembly_outpost")),
            Map.entry("market_outpost", plain("resolveTheodisiOutpost_market_outpost")));

    private static void erasePreVotes(Game game) {
        for (Player p2 : game.getRealPlayers()) {
            if (!game.getStoredValue("preVoting" + p2.getFaction()).isEmpty()) {
                AgendaWhensAftersHelper.erasePreVoteDueToAfterPlay(p2, game);
            }
        }
    }

    private static String declareReminder(Player player, String noun) {
        return player.getRepresentation()
                + ", a reminder that you should declare which " + noun
                + " you are targeting now, before other players choose whether they will Sabo.";
    }

    private static void addGreenAndSend(
            Player player, List<Button> codedButtons, String handlerId, String label, String message) {
        codedButtons.add(Buttons.green(player.factionButtonChecker() + handlerId, label));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, codedButtons);
    }

    private static void addGreenAndSendIfActiveSystem(
            Game game, Player player, List<Button> codedButtons, String handlerPrefix, String label, String message) {
        codedButtons.add(Buttons.green(player.factionButtonChecker() + handlerPrefix + game.getActiveSystem(), label));
        if (game.getActiveSystem().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "The active system is currently non-existent, so this card cannot be automated.");
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, codedButtons);
        }
    }

    private static List<Button> getStrategyCardButtons(Game game, Player player, String idPrefix) {
        List<Button> scButtons = new ArrayList<>();
        for (int sc : game.getSCList()) {
            TI4Emoji scEmoji = CardEmojis.getSCBackFromInteger(sc);
            String id = player.factionButtonChecker() + idPrefix + sc;
            if (scEmoji != CardEmojis.SCBackBlank) {
                scButtons.add(Buttons.gray(id, Helper.getSCName(sc, game), scEmoji));
            } else {
                scButtons.add(Buttons.gray(id, sc + " " + Helper.getSCName(sc, game)));
            }
        }
        return scButtons;
    }

    private static void resolveAutomatedActionCard(
            Game game,
            Player player,
            String automationID,
            String buttonLabel,
            String introBody,
            String introMsg,
            List<Button> codedButtons) {
        MessageChannel channel = player.getCorrectChannel();
        String fid = player.factionButtonChecker();
        AutomatedResolve simple = AUTOMATED_RESOLVES.get(automationID);
        if (simple != null) {
            addGreenAndSend(player, codedButtons, simple.handlerId(), buttonLabel, introMsg + simple.suffix());
        }
        switch (automationID) {
            case "investments" -> {
                serveManipulateInvestmentButtons(game, player);
                MessageHelper.sendMessageToChannelWithButtons(
                        channel,
                        player.getRepresentation()
                                + ", a reminder that you should declare how you are distributing the trade goods now, before other players choose whether they will Sabo.",
                        codedButtons);
            }
            case "deflection", "tf-tartarus" ->
                MessageHelper.sendMessageToChannelWithButtons(
                        channel,
                        player.getRepresentation()
                                + " Use buttons to choose which strategy card will be the target of your AC.",
                        getStrategyCardButtons(game, player, "deflectSC_"));
            case "arch_expedition" ->
                MessageHelper.sendMessageToChannelWithButtons(
                        channel,
                        player.getRepresentation()
                                + ", after checking for Sabos, please use buttons to explore a planet type thrice and gain any fragments.",
                        ButtonHelperActionCards.getArcExpButtons(game, player));
            case "planetary_rigs" -> {
                List<Button> acbuttons = ButtonHelperHeroes.getAttachmentSearchButtons(game, player);
                String msg = player.getRepresentation()
                        + (acbuttons.isEmpty()
                                ? ", there were no attachments found in the applicable exploration decks."
                                : ", after checking for Sabos, first declare what planet you mean to put an attachment on, then hit the button to resolve.");
                MessageHelper.sendMessageToChannelWithButtons(channel, msg, acbuttons);
            }
            case "war_machine" -> player.addSpentThing("warmachine");
            case "confounding" -> {
                codedButtons.add(Buttons.green("autoresolve_manual", buttonLabel));
                sendResolveMsgToMainChannel(introBody, codedButtons, player, game);
            }
            case "confusing" -> {
                codedButtons.add(Buttons.green("autoresolve_manual", buttonLabel));
                sendResolveMsgToMainChannel(
                        introBody + String.format(TARGET_REMINDER, "player"), codedButtons, player, game);
            }
            case "skilled_retreat" ->
                addGreenAndSend(
                        player,
                        codedButtons,
                        "retreat_" + game.getActiveSystem() + "_skilled",
                        buttonLabel,
                        introMsg
                                + "A reminder that you should declare which system you are retreating to now, before other players choose whether they will Sabo.");
            case "alliance_rider" ->
                addGreenAndSend(player, codedButtons, "allianceRiderRandomAlly", "Random Ally", introMsg);
            case "experimental" -> {
                codedButtons.add(Buttons.green(fid + "resolveEBSStep1_" + game.getActiveSystem(), buttonLabel));
                if (player.hasTechReady("gls")) {
                    codedButtons.add(Buttons.gray(
                            "exhaustTech_gls", "Exhaust Graviton Laser System", TechEmojis.CyberneticTech));
                }
                MessageHelper.sendMessageToChannelWithButtons(
                        channel,
                        introMsg
                                + (player.hasTechReady("gls")
                                        ? "\n-# Make sure to declare _Graviton Laser System_, should you so wish, before pressing this button."
                                        : ""),
                        codedButtons);
            }
            case "blitz" ->
                addGreenAndSend(player, codedButtons, "resolveBlitz_" + game.getActiveSystem(), buttonLabel, introMsg);
            case "shrapnel_turrets" ->
                addGreenAndSend(
                        player,
                        codedButtons,
                        "resolveShrapnelTurrets_" + game.getActiveSystem(),
                        buttonLabel,
                        introMsg + String.format(TARGET_REMINDER, "ships"));
            case "upgrade" ->
                addGreenAndSendIfActiveSystem(game, player, codedButtons, "resolveUpgrade_", buttonLabel, introMsg);
            case "emergency_repairs" ->
                addGreenAndSendIfActiveSystem(
                        game, player, codedButtons, "resolveEmergencyRepairs_", buttonLabel, introMsg);
            case "decoy" ->
                addGreenAndSendIfActiveSystem(
                        game,
                        player,
                        codedButtons,
                        "resolveDecoyOperationStep1_",
                        buttonLabel,
                        introMsg + String.format(TARGET_REMINDER, "ground forces"));
            case "parley" -> {
                codedButtons.add(Buttons.green(fid + "resolveParleyStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel, declareReminder(player, "planet (if multiple)"), codedButtons);
            }
            case "tf-pax" -> codedButtons.add(Buttons.green(fid + "resolveParleyStep1", buttonLabel));
            case "flank_speed" -> game.setStoredValue("flankspeedBoost", "1");
            case "diplo_pressure" ->
                addGreenAndSend(
                        player,
                        codedButtons,
                        "resolveDiplomaticPressureStep1",
                        buttonLabel,
                        "Please resolve _Diplomatic Pressure_ now. If any Sabo occurs, they will be able to ignore the buttons they are offered.");
            case "tf-rise" -> {
                codedButtons.add(Buttons.green(fid + "riseOfAMessiah", "1 Infantry On Every Planet"));
                addGreenAndSend(player, codedButtons, "fighterConscription", "1 Fighter With Every Ship", introMsg);
            }
            case "tf-manipulate" ->
                addGreenAndSend(player, codedButtons, "resolveManipulateTF", "Resolve Manipulate", introMsg);
            case "tf-mutate1", "tf-mutate2" -> {
                codedButtons.add(Buttons.red("discardSpliceCard_ability", "Discard 1 Ability"));
                codedButtons.add(Buttons.green("drawSingularNewSpliceCard_ability", "Draw 1 Ability"));
                codedButtons.add(Buttons.DONE_RESOLVING);
                MessageHelper.sendMessageToChannelWithButtons(channel, introMsg, codedButtons);
            }
            case "tf-evolve" -> {
                codedButtons.add(Buttons.green("drawSingularNewSpliceCard_units", "Draw 1 Unit Upgrade"));
                codedButtons.add(Buttons.green("drawSingularNewSpliceCard_genome", "Draw 1 Genome (Agent)"));
                codedButtons.add(Buttons.green("drawSingularNewSpliceCard_ability", "Draw 1 Ability"));
                codedButtons.add(Buttons.DONE_RESOLVING);
                MessageHelper.sendMessageToChannelWithButtons(channel, introMsg, codedButtons);
            }
            case "tf-helix" -> {
                codedButtons.add(Buttons.green(fid + "startSplice_7_all", "Initiate Ability Splice"));
                codedButtons.add(Buttons.gray(fid + "startSplice_2_all", "Initiate Genome (Agent) Splice"));
                codedButtons.add(Buttons.blue(fid + "startSplice_6_all", "Initiate Unit Upgrade Splice"));
                codedButtons.add(Buttons.DONE_RESOLVING);
                for (Player p : game.getRealPlayers()) {
                    ButtonHelperActionCards.checkForPlayingSpliceCards(game, p);
                }
                MessageHelper.sendMessageToChannelWithButtons(channel, introMsg, codedButtons);
            }
            case "tf-reverse" -> {
                codedButtons.add(Buttons.green("resolveReverseTF", buttonLabel + " (Anyone can press this)"));
                MessageHelper.sendMessageToChannelWithButtons(channel, introMsg, codedButtons);
            }
            case "tf-flux" -> {
                codedButtons.add(Buttons.green(fid + "resolveUnexpected", "Lift a Command Token"));
                addGreenAndSend(player, codedButtons, "resolveSignalJammingStep1", "Place a Command Token", introMsg);
            }
            case "tf-lash" ->
                MessageHelper.sendMessageToChannel(
                        channel,
                        introMsg
                                + "\nJust tell the opponent what unit to destroy. They can use the assign hits button to do so.");
            case "tf-reflect" -> {
                codedButtons.add(Buttons.green(
                        fid + "convertComms_1_stay", "Convert 1 Commodity to Trade Good", MiscEmojis.Wash));
                codedButtons.add(Buttons.blue(fid + "gainComms_1_stay", "Gain 1 Commodity", MiscEmojis.comm));
                codedButtons.add(Buttons.DONE_RESOLVING);
                MessageHelper.sendMessageToChannelWithButtons(channel, introMsg, codedButtons);
            }
            case "tf-elevate" -> addGreenAndSend(player, codedButtons, "drawParadigmAC", "Draw Paradigm", introMsg);
            case "veto" -> {
                codedButtons.add(Buttons.blue(fid + "resolveVeto", "Reveal next Agenda"));
                sendResolveMsgToMainChannel(introBody, codedButtons, player, game);
            }
            case "bribery" ->
                MessageHelper.sendMessageToChannelWithButtons(
                        channel,
                        player.getRepresentation()
                                + ", a reminder that you should declare how many trade goods you are spending now, before other players choose whether they will Sabo.",
                        codedButtons);
            case "silence_space" ->
                MessageHelper.sendMessageToChannelWithButtons(channel, declareReminder(player, "system"), codedButtons);
            case "direct_hit", "courageous" ->
                MessageHelper.sendMessageToChannel(channel, declareReminder(player, "ship (if multiple)"));
            case "ghost_squad" ->
                MessageHelper.sendMessageToChannelWithButtons(
                        channel, declareReminder(player, "ground forces"), codedButtons);
            case "monuments_festival", "monuments_rebel_bombing", "monuments_renovation" -> {
                if (game.isMonumentsMode()) {
                    addGreenAndSend(
                            player, codedButtons, "resolveMonumentsActionCard_" + automationID, buttonLabel, introMsg);
                }
            }
            default -> {}
        }
    }

    /**
     * Post a resolve prompt to the public main game channel. {@code body} is the message with the
     * acting player's representation NOT included; under fog the representation is omitted entirely
     * (body capitalized), otherwise it is prepended. Building the body without the identity — rather
     * than stripping it out after the fact — means the fog branch cannot leak the actor's identity.
     */
    private static void sendResolveMsgToMainChannel(String body, List<Button> buttons, Player player, Game game) {
        String message = game.isFowMode()
                ? org.apache.commons.lang3.StringUtils.capitalize(body)
                : player.getRepresentation() + ", " + body;
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), message, buttons);
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

    static void recordTrackedActionCardPlay(Game game, Player player, String actionCardName) {
        game.getGameStats().recordAcPlay(actionCardName, player);
    }

    private static String getGarboziaACIdentByAlias(Game game, Player player, String key) {
        boolean inGarbozia = game.getDiscardACStatus().get(key) == ACStatus.garbozia;
        return player.hasPlanet("garbozia") && inGarbozia ? key : null;
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

    public void sendRandomACPart2(GenericInteractionCreateEvent event, Game game, Player player, Player player2) {
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
                    game, event, player, player2, CardEmojis.getACEmoji(game) + " Action Card", null);
        }
        player.removeActionCard(actionCardsMap.get(acID));
        player2.setActionCard(acID);
        sendActionCardInfo(game, player2);
        ButtonHelper.checkACLimit(game, player2);
        sendActionCardInfo(game, player);
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                "# " + player.getRepresentation() + " you lost the action card _"
                        + Mapper.getActionCard(acID).getName() + "_.");
        MessageHelper.sendMessageToChannel(
                player2.getCardsInfoThread(),
                "# " + player2.getRepresentation() + " you gained the action card _"
                        + Mapper.getActionCard(acID).getName() + "_.");
    }

    public static void showAll(Player player, Player player2, Game game) {
        if (XinTechHandler.hasAstromanticCloakSteel(player) && player != player2) {
            MessageHelper.sendMessageToChannel(
                    player2.getCorrectChannel(),
                    player2.getRepresentation()
                            + ", you cannot look at " + player.getRepresentationNoPing() + "'s"
                            + " Action Cards because they have _Astromantic Cloak (Steel)!");
            return;
        }
        StringBuilder sb = new StringBuilder();
        StringBuilder sa = new StringBuilder();
        sa.append("Your action cards were shown to: ")
                .append(game.isFowMode() ? "Someone" : player2.getUserName())
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
        MessageHelper.sendMessageToPlayerCardsInfoThread(player2, sb.toString());
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
            sb.repeat(
                    Objects.requireNonNull(CardEmojis.ActionCard.toString()),
                    acEntryList.getValue().size());
            sb.append(" _").append(acEntryList.getKey()).append("_");
        }
        return sb.toString();
    }

    public static void pickACardFromDiscardStep1(Game game, Player player) {
        pickACardFromDiscardStep1(
                game,
                player,
                "pickFromDiscard_",
                player.getRepresentationUnfogged() + ", use buttons to retrieve an action card from the discard pile.");
    }

    private static void pickACardFromDiscardStep1(Game game, Player player, String buttonPrefix, String message) {
        pickACardFromDiscardStep1(game, player, buttonPrefix, message, player.getCardsInfoThread());
    }

    public static void pickACardFromDiscardStep1(
            Game game, Player player, String buttonPrefix, String message, MessageChannel channel) {
        List<Button> buttons = new ArrayList<>();
        boolean hideUnplayed = hidesUnplayedDiscards(game, player);
        for (String acStringID : game.getDiscardActionCards().keySet()) {
            if (!isDiscardActionCardPickable(game, hideUnplayed, acStringID)) {
                continue;
            }
            buttons.add(Buttons.green(
                    buttonPrefix + acStringID, Mapper.getActionCard(acStringID).getName()));
        }
        buttons.add(Buttons.DELETE_BUTTONS);
        if (buttons.size() > 25) {
            buttons.add(25, Buttons.red("deleteButtons_", "Delete These Buttons"));
        }
        if (buttons.size() > 50) {
            buttons.add(50, Buttons.red("deleteButtons_2", "Delete These Buttons"));
        }
        if (buttons.size() > 75) {
            buttons.add(75, Buttons.red("deleteButtons_3", "Delete These Buttons"));
        }
        MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
    }

    public static void pickACardFromDiscardStep2(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        ButtonHelper.deleteMessage(event);
        String acID = buttonID.replace("pickFromDiscard_", "");
        if (!isDiscardActionCardPickable(game, hidesUnplayedDiscards(game, player), acID)) {
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
            String message = player.getRepresentationUnfogged()
                    + ", please discard 1 action card due to your **Cybernetic Madness** ability.";
            if (game.isTwilightsFallMode()) {
                message +=
                        " If you picked this ability before it was edited to have the discard text, my advice is to ignore these buttons and just play it as you picked it.";
            }

            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(), message, getDiscardActionCardButtons(player, false));
        }
        ButtonHelper.checkACLimit(game, player);
    }

    public static void getActionCardFromDiscard(
            GenericInteractionCreateEvent event, Game game, Player player, int acIndex) {
        String acId = getDiscardedAcID(game, acIndex);

        if (acId == null || !isDiscardVisible(game, player, acId)) {
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

    private static boolean isDiscardActionCardPickable(Game game, boolean hideUnplayed, String acId) {
        ACStatus status = game.getDiscardACStatus().get(acId);
        return status == null && isDiscardVisible(game, hideUnplayed, acId);
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

        String msg = "Added " + StringHelper.pluralize(tilesAffected.size(), "fighter") + ".";
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
