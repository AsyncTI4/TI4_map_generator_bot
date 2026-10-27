package ti4.discord.interactions.buttons.handlers.planet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.oblivion.OblivionTileHelper;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.RelicHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.AttachmentModel;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.model.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.service.objectives.DrawSecretService;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
public class IxthLegendaryButtonHandler {
    public static final String ALPERT = "alpert";
    public static final String IMPERIAL_SHIPYARD_SPENT_THING = "imperialshipyard";
    private static final String DESMOND = "desmond";
    private static final String EKO = "eko";
    private static final String ETHAN = "ethan";
    private static final String HORACE = "horace";
    private static final String JARRAH = "jarrah";
    private static final String KWON = "kwon";
    private static final String LAPIDUS = "lapidus";
    private static final String LLOYD = "lloyd";
    private static final String WIDMORE = "widmore";
    private static final String BACK_FACE_KEY = "ixthLegendaryBack_";
    private static final String PURGED_ABILITY_KEY = "ixthLegendaryPurged_";
    private static final String KWON_COMMANDER_KEY = "ixthKwonCommander";
    private static final String HORACE_ANOMALY_KEY = "ixthHoraceAnomaly_";
    private static final String WIDMORE_OFFERED_ROUND_KEY = "ixthWidmoreBackRound";
    private static final String EKO_PREFIX = "ekoFrontier_";
    private static final String HORACE_PREFIX = "horaceAnomaly_";
    private static final String WIDMORE_PREFIX = "widmoreGain_";
    private static final String LLOYD_INITIATIVE_PREFIX = "lloydInitiative_";
    private static final String ETHAN_SPECIALTY_PREFIX = "ethanSpecialty_";
    private static final String LAPIDUS_PAGE_PREFIX = "lapidusPage_";
    private static final String LAPIDUS_MESSAGE = "Choose a planet you control to explore with _Smugglers Network_:";
    private static final String ORBITAL_SLINGSHOT_TOKEN = "attachment_orbitalslingshot.png";
    private static final String GIFT_TARGET_PREFIX = "ixthGiftTarget_";
    private static final String GIFT_ACCEPT_PREFIX = "ixthGiftAccept_";
    private static final String TRADE_ROUTE_TOKEN = "attachment_traderoute.png";
    private static final String FINANCIAL_CENTER_TOKEN = "attachment_galacticfinancialcenter.png";
    private static final String INTERPLANETARY_HUB_TOKEN = "attachment_interplanetaryhub.png";
    private static final List<String> TECH_SPECIALTIES = List.of("biotic", "cybernetic", "propulsion", "warfare");
    private static final Set<ComponentSource> COMMANDER_SOURCES = Set.of(
            ComponentSource.base,
            ComponentSource.pok,
            ComponentSource.codex1,
            ComponentSource.codex2,
            ComponentSource.codex3,
            ComponentSource.codex4);

    public static List<Button> getComponentActionButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        if (player.hasPlanet(DESMOND) && !isAbilityPurged(game, DESMOND)) {
            buttons.add(
                    Buttons.green(player.factionButtonChecker() + "useDesmondLegendary", "Use Ancient Gashlai Ruins"));
        }
        if (player.hasPlanet(WIDMORE) && !isBackFace(game, WIDMORE)) {
            buttons.add(Buttons.green(player.factionButtonChecker() + "useWidmoreFront", "Use Tyrrel Syndicate"));
        }
        return buttons;
    }

    public static boolean isEndOfTurnAbilityUsable(Game game, Player player, String planet) {
        return switch (planet) {
            case LAPIDUS -> player.getPlanets().stream().anyMatch(name -> isExplorable(game, name));
            case LLOYD -> !getAvailableInitiatives(game).isEmpty();
            default -> true;
        };
    }

    public static boolean hasHoraceTarget(Game game, Player player) {
        return !getHoraceTargets(game, player).isEmpty();
    }

    public static void onPlanetGained(Game game, Player player, String planet) {
        if (game == null || !player.isRealPlayer()) {
            return;
        }
        if (KWON.equals(planet) && game.getStoredValue(KWON_COMMANDER_KEY).isEmpty()) {
            resolveKwonGate(game, player);
        }
        if (ETHAN.equals(planet) && !isBackFace(game, ETHAN)) {
            offerEthanSpecialty(game, player);
        }
    }

    public static void onPlanetSpent(Game game, Player player, String planet, String whatIsItFor) {
        String spendType = whatIsItFor.toLowerCase();
        boolean forResources =
                spendType.startsWith("res") || "both".equals(spendType) || "freelancers".equals(spendType);
        if (!ETHAN.equals(planet) || !forResources || !player.hasPlanet(ETHAN) || isBackFace(game, ETHAN)) {
            return;
        }
        flip(game, ETHAN);
        setEthanSpecialty(game, null);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " exhausted Ethan for resources, so _Metastability Event_ flipped to its back face.");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", _Metastability Event_: perform the secondary ability of any strategy card before this turn"
                        + " ends (no command token is spent). When the turn ends, all units in Ethan's system are"
                        + " destroyed and its tile is purged automatically.",
                ButtonHelperHeroes.getSecondaryButtons(game));
    }

    public static void onPlanetExhausted(Game game, Player player, String planet) {
        if (game != null && hasToken(game, planet, TRADE_ROUTE_TOKEN)) {
            offerGiftTargets(game, player, "commodity", "Established Trade Route");
        }
    }

    public static void onPlanetReadied(Game game, Player player, String planet) {
        if (game != null && player.hasPlanet(planet) && hasToken(game, planet, FINANCIAL_CENTER_TOKEN)) {
            offerGiftTargets(game, player, "tg", "Galactic Financial Center");
        }
    }

    public static void onSystemActivated(Game game, Player activePlayer) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null) {
            return;
        }
        for (Planet planet : activeSystem.getPlanetUnitHolders()) {
            if (!planet.getTokenList().contains(INTERPLANETARY_HUB_TOKEN)) {
                continue;
            }
            for (Player owner : game.getRealPlayers()) {
                if (!owner.hasPlanet(planet.getName())) {
                    continue;
                }
                String activator = game.isFowMode() ? "A player" : activePlayer.getRepresentationNoPing();
                MessageHelper.sendMessageToChannelWithButtons(
                        owner.getCorrectChannel(),
                        owner.getRepresentationUnfogged() + ", " + activator + " activated the system of "
                                + Helper.getPlanetRepresentation(planet.getName(), game)
                                + ". _Interplanetary Hub_: you may gain 1 action card.",
                        List.of(
                                Buttons.green(
                                        owner.factionButtonChecker() + GIFT_ACCEPT_PREFIX + "ac", "Gain 1 Action Card"),
                                Buttons.red("deleteButtons", "Decline")));
            }
        }
    }

    public static boolean hasKwonCommander(Game game, Player player, String leaderID) {
        return player.hasPlanet(KWON)
                && !leaderID.isEmpty()
                && leaderID.equalsIgnoreCase(game.getStoredValue(KWON_COMMANDER_KEY));
    }

    public static boolean ignoresHoraceAnomaly(Game game, Player player, Tile tile) {
        return game != null
                && player != null
                && tile != null
                && tile.getPosition().equalsIgnoreCase(game.getStoredValue(HORACE_ANOMALY_KEY + player.getFaction()));
    }

    public static void clearHoraceAnomaly(Game game, Player player) {
        game.removeStoredValue(HORACE_ANOMALY_KEY + player.getFaction());
    }

    public static void clearHoraceAnomalies(Game game) {
        game.getRealPlayers().forEach(player -> clearHoraceAnomaly(game, player));
    }

    public static void offerEkoSystems(Game game, Player player) {
        List<Button> buttons = getEkoButtons(game, player);
        if (buttons.isEmpty()) {
            player.refreshPlanetAbility(EKO);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + ", there is no system without a planet or frontier token for _Temporal Anomaly_."
                            + " The ability was not used.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a system for _Temporal Anomaly_:",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + EKO_PREFIX, 0));
    }

    @ButtonHandler(EKO_PREFIX)
    public static void placeEkoFrontier(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                getEkoButtons(game, player),
                player.getRepresentationNoPing() + ", choose a system for _Temporal Anomaly_:",
                player.factionButtonChecker() + EKO_PREFIX,
                buttonID)) {
            return;
        }
        Tile tile = game.getTileByPosition(buttonID.substring(EKO_PREFIX.length()));
        if (tile == null || !getEkoTargets(game, player).contains(tile)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        tile.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed a frontier token in "
                        + tile.getRepresentationForButtons(game, player) + " using _Temporal Anomaly_.");
    }

    public static void offerHoraceAnomalies(Game game, Player player) {
        List<Button> buttons = getHoraceButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " exhausted _Transgravity Conduit_, but sees no anomaly.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", choose the anomaly whose effects you ignore for this tactical action:",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + HORACE_PREFIX, 0));
    }

    @ButtonHandler(HORACE_PREFIX)
    public static void chooseHoraceAnomaly(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                getHoraceButtons(game, player),
                player.getRepresentationNoPing()
                        + ", choose the anomaly whose effects you ignore for this tactical action:",
                player.factionButtonChecker() + HORACE_PREFIX,
                buttonID)) {
            return;
        }
        Tile tile = game.getTileByPosition(buttonID.substring(HORACE_PREFIX.length()));
        if (tile == null || !getHoraceTargets(game, player).contains(tile)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        game.setStoredValue(HORACE_ANOMALY_KEY + player.getFaction(), tile.getPosition());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " ignores the anomaly effects of "
                        + tile.getRepresentationForButtons(game, player)
                        + " for this tactical action using _Transgravity Conduit_.");
    }

    public static void offerLapidusExplore(Game game, Player player) {
        List<Button> buttons = getLapidusButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " exhausted _Smugglers Network_, but has no planet to explore.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                LAPIDUS_MESSAGE,
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + LAPIDUS_PAGE_PREFIX, 0));
    }

    @ButtonHandler(LAPIDUS_PAGE_PREFIX)
    public static void pageLapidusExplore(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                getLapidusButtons(game, player),
                LAPIDUS_MESSAGE,
                player.factionButtonChecker() + LAPIDUS_PAGE_PREFIX,
                buttonID);
    }

    private static List<Button> getLapidusButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            if (isExplorable(game, planetName)) {
                buttons.addAll(ButtonHelper.getPlanetExplorationButtons(
                        game, game.getPlanetsInfo().get(planetName), player));
            }
        }
        return buttons;
    }

    private static boolean isExplorable(Game game, String planetName) {
        Planet planet = game.getPlanetsInfo().get(planetName);
        return planet != null && StringUtils.isNotBlank(planet.getOriginalPlanetType());
    }

    public static void offerLloydInitiatives(Game game, Player player) {
        List<Button> buttons = getAvailableInitiatives(game).stream()
                .map(initiative -> Buttons.green(
                        player.factionButtonChecker() + LLOYD_INITIATIVE_PREFIX + initiative.getId(),
                        initiative.getName()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " exhausted _New Elysium_, but no terraforming initiative is left.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a terraforming initiative for _New Elysium_:",
                buttons);
    }

    @ButtonHandler(LLOYD_INITIATIVE_PREFIX)
    public static void attachLloydInitiative(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        AttachmentModel initiative = Mapper.getAttachmentInfo(buttonID.substring(LLOYD_INITIATIVE_PREFIX.length()));
        Planet lloyd = game.getUnitHolderFromPlanet(LLOYD);
        boolean valid = initiative != null
                && lloyd != null
                && player.hasPlanet(LLOYD)
                && getAvailableInitiatives(game).contains(initiative);
        if (!valid) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        lloyd.addToken(initiative.getImagePath());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " attached _" + initiative.getName() + "_ to "
                        + Helper.getPlanetRepresentation(LLOYD, game) + " using _New Elysium_.",
                initiative.getRepresentationEmbed());
    }

    private static void offerGiftTargets(Game game, Player owner, String gift, String source) {
        List<Button> buttons = game.getRealPlayers().stream()
                .map(target -> FoWHelper.fogSafeTargetButton(
                        owner.factionButtonChecker() + GIFT_TARGET_PREFIX + gift + "_" + target.getFaction(),
                        "green",
                        target,
                        owner))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                owner.getCorrectChannel(),
                owner.getRepresentationUnfogged() + ", _" + source + "_: choose a player who may gain 1 "
                        + giftName(gift) + ".",
                buttons);
    }

    @ButtonHandler(GIFT_TARGET_PREFIX)
    public static void chooseGiftTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(GIFT_TARGET_PREFIX.length()).split("_", 2);
        Player target = parts.length < 2 ? null : game.getPlayerFromColorOrFaction(parts[1]);
        if (target == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        String gift = parts[0];
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " chose a player who may gain 1 " + giftName(gift) + ".");
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged() + ", you were chosen and may gain 1 " + giftName(gift) + ".",
                List.of(
                        Buttons.green(
                                target.factionButtonChecker() + GIFT_ACCEPT_PREFIX + gift, "Gain 1 " + giftName(gift)),
                        Buttons.red("deleteButtons", "Decline")));
    }

    @ButtonHandler(GIFT_ACCEPT_PREFIX)
    public static void acceptGift(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String gift = buttonID.substring(GIFT_ACCEPT_PREFIX.length());
        ButtonHelper.deleteMessage(event);
        switch (gift) {
            case "commodity" -> ButtonHelperStats.gainComms(event, game, player, 1, false);
            case "tg" -> ButtonHelperStats.gainTGs(event, game, player, 1, false);
            case "ac" -> {
                ActionCardHelper.drawActionCards(player, 1);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(), player.getRepresentationNoPing() + " drew 1 action card.");
            }
            default -> {}
        }
    }

    private static String giftName(String gift) {
        return switch (gift) {
            case "commodity" -> "commodity";
            case "tg" -> "trade good";
            default -> "action card";
        };
    }

    @ButtonHandler("useDesmondLegendary")
    public static void useDesmondLegendary(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasPlanet(DESMOND) || isAbilityPurged(game, DESMOND)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        game.setStoredValue(PURGED_ABILITY_KEY + DESMOND, "true");
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " purged the _Ancient Gashlai Ruins_ legendary ability card to draw 1 relic.",
                Mapper.getPlanet(DESMOND).getLegendaryEmbed());
        RelicHelper.drawRelicAndNotify(player, event, game);
        for (Player other : game.getRealPlayersExcludingThis(player)) {
            MessageHelper.sendMessageToChannelWithButton(
                    other.getCorrectChannel(),
                    other.getRepresentationUnfogged()
                            + ", _Ancient Gashlai Ruins_ was purged. You may draw 1 action card.",
                    Buttons.green(other.factionButtonChecker() + "desmondDrawActionCard", "Draw 1 Action Card"));
        }
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
    }

    @ButtonHandler("desmondDrawActionCard")
    public static void drawDesmondActionCard(ButtonInteractionEvent event, Player player) {
        ButtonHelper.deleteMessage(event);
        ActionCardHelper.drawActionCards(player, 1);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getRepresentationNoPing() + " drew 1 action card.");
    }

    @ButtonHandler("useWidmoreFront")
    public static void useWidmoreFront(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasPlanet(WIDMORE) || isBackFace(game, WIDMORE)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        ButtonHelper.deleteMessage(event);
        sendWidmoreChoice(player, false);
    }

    public static void offerWidmoreBack(Game game) {
        Optional<Player> owner = game.getRealPlayers().stream()
                .filter(player -> player.hasPlanet(WIDMORE))
                .findFirst();
        String round = String.valueOf(game.getRound());
        if (owner.isEmpty()
                || !isBackFace(game, WIDMORE)
                || round.equals(game.getStoredValue(WIDMORE_OFFERED_ROUND_KEY))) {
            return;
        }
        List<Player> candidates = new ArrayList<>(game.getRealPlayersExcludingThis(owner.get()));
        if (candidates.isEmpty()) {
            return;
        }
        game.setStoredValue(WIDMORE_OFFERED_ROUND_KEY, round);
        Collections.shuffle(candidates);
        sendWidmoreChoice(candidates.getFirst(), true);
    }

    private static void sendWidmoreChoice(Player player, boolean backFace) {
        String prefix = player.factionButtonChecker() + WIDMORE_PREFIX + (backFace ? "back_" : "front_");
        List<Button> buttons = new ArrayList<>(List.of(
                Buttons.green(prefix + "ac", "Gain 2 Action Cards"),
                Buttons.green(prefix + "so", "Gain 1 Secret Objective")));
        if (backFace) {
            buttons.add(Buttons.red(prefix + "decline", "Decline"));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", _Tyrrel Syndicate_" + (backFace ? " chose you at random" : "")
                        + ": gain 2 action cards or 1 secret objective.",
                buttons);
    }

    @ButtonHandler(WIDMORE_PREFIX)
    public static void gainWidmoreReward(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        boolean backFace = buttonID.startsWith(WIDMORE_PREFIX + "back_");
        ButtonHelper.deleteMessage(event);
        if (backFace != isBackFace(game, WIDMORE)) {
            return;
        }
        String reward = "nothing";
        if (buttonID.endsWith("_ac")) {
            ActionCardHelper.drawActionCards(player, 2);
            reward = "2 action cards";
        } else if (buttonID.endsWith("_so")) {
            DrawSecretService.drawSO(event, game, player);
            reward = "1 secret objective";
        }
        flip(game, WIDMORE);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " gained " + reward
                        + " from _Tyrrel Syndicate_, which is now on its " + (backFace ? "front" : "back") + " face.");
        if (!backFace) {
            ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
        }
    }

    private static void offerEthanSpecialty(Game game, Player player) {
        List<Button> buttons = TECH_SPECIALTIES.stream()
                .map(type -> Buttons.blue(
                        player.factionButtonChecker() + ETHAN_SPECIALTY_PREFIX + type, StringUtils.capitalize(type)))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", _Metastability Event_: choose the technology specialty Ethan has. You can change it with"
                        + " these buttons at any time while the card is face up.",
                buttons);
    }

    @ButtonHandler(ETHAN_SPECIALTY_PREFIX)
    public static void chooseEthanSpecialty(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String type = buttonID.substring(ETHAN_SPECIALTY_PREFIX.length());
        if (!player.hasPlanet(ETHAN) || isBackFace(game, ETHAN) || !TECH_SPECIALTIES.contains(type)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        setEthanSpecialty(game, type);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " set Ethan's technology specialty to " + type + ".");
    }

    private static void setEthanSpecialty(Game game, String type) {
        Planet ethan = game.getUnitHolderFromPlanet(ETHAN);
        if (ethan == null) {
            return;
        }
        for (String specialty : TECH_SPECIALTIES) {
            ethan.removeToken(ethanSpecialtyToken(specialty));
        }
        if (type != null) {
            ethan.addToken(ethanSpecialtyToken(type));
        }
    }

    private static String ethanSpecialtyToken(String type) {
        return "attachment_ethan" + type + ".png";
    }

    public static void resolveEthanAtEndOfTurn(GenericInteractionCreateEvent event, Game game, Player endingPlayer) {
        if (!isBackFace(game, ETHAN)) {
            return;
        }
        game.removeStoredValue(BACK_FACE_KEY + ETHAN);
        Tile tile = game.getTileFromPlanet(ETHAN);
        if (tile == null) {
            return;
        }
        Player owner = game.getRealPlayers().stream()
                .filter(p -> p.hasPlanet(ETHAN))
                .findFirst()
                .orElse(endingPlayer);
        List<Player> affected = game.getRealPlayersExcludingThis(owner).stream()
                .filter(p -> FoWHelper.playerHasUnitsInSystem(p, tile))
                .toList();
        DestroyUnitService.destroyAllUnitsInSystem(event, tile, game, false);
        List<String> planetNames =
                tile.getPlanetUnitHolders().stream().map(Planet::getName).toList();
        for (Player p : game.getRealPlayers()) {
            planetNames.forEach(p::removePlanet);
        }
        OblivionTileHelper.purgeTiles(game, List.of(tile.getTileID()));
        game.removeTile(tile.getPosition());
        MessageHelper.sendMessageToChannel(
                owner.getCorrectChannel(),
                owner.getRepresentationNoPing() + "'s _Metastability Event_ resolved at the end of the turn. All units"
                        + " in the system were destroyed and the tile was purged.");
        if (game.isFowMode()) {
            for (Player p : affected) {
                MessageHelper.sendMessageToChannel(
                        p.getCorrectChannel(),
                        p.getRepresentationUnfogged() + ", the system at " + tile.getPosition()
                                + " collapsed. All units there were destroyed and its tile was purged.");
            }
        }
    }

    private static void resolveKwonGate(Game game, Player player) {
        List<LeaderModel> commanders = getUnchosenCommanders(game);
        if (commanders.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "No unchosen faction commander is available for _Interstellar Gate_.");
            return;
        }
        Collections.shuffle(commanders);
        LeaderModel commander = commanders.getFirst();
        game.setStoredValue(KWON_COMMANDER_KEY, commander.getId());
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " gained Kwon. _Interstellar Gate_ entered play with the unlocked"
                        + " commander of a random unchosen faction attached. Whoever controls Kwon has this commander:",
                commander.getRepresentationEmbed());
    }

    @ButtonHandler("useAlpertCostReduction")
    public static void useAlpertCostReduction(ButtonInteractionEvent event, Game game, Player player) {
        player.addSpentThing(IMPERIAL_SHIPYARD_SPENT_THING);
        String message = Helper.buildSpentThingsMessage(player, game, "res");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
        event.getMessage().editMessage(message).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static boolean canUseImperialShipyard(Player player, Tile tile) {
        if (!ownsPlanetInTile(player, tile, ALPERT)) {
            return false;
        }
        return player.getCurrentProducedUnits().keySet().stream()
                .map(entry -> entry.split("_", 3))
                .filter(entry -> entry.length == 3 && entry[1].equals(tile.getPosition()))
                .map(entry -> player.getUnitFromUnitKey(
                        Mapper.getUnitKey(AliasHandler.resolveUnit(entry[0]), player.getColor())))
                .anyMatch(unit -> unit != null && Boolean.TRUE.equals(unit.getIsShip()));
    }

    public static String getOrbitalSlingshotReminder(Player player, Tile tile) {
        boolean controlsSlingshot = tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> player.hasPlanet(planet.getName())
                        && planet.getTokenList().contains(ORBITAL_SLINGSHOT_TOKEN));
        return controlsSlingshot
                ? " (_Orbital Slingshot_: you may apply +1 to the move value of 1 ship moving from or through this system)"
                : "";
    }

    public static boolean producesOnAlpert(Player player, UnitHolder holder) {
        return ALPERT.equals(holder.getName()) && player.hasPlanet(ALPERT);
    }

    public static int getCommoditiesModifier(String planet) {
        return switch (planet) {
            case JARRAH -> 2;
            case KWON -> -1;
            default -> 0;
        };
    }

    public static boolean ownsPlanetInTile(Player player, Tile tile, String planet) {
        return player.hasPlanet(planet)
                && tile != null
                && tile.getPlanetUnitHolders().stream().anyMatch(holder -> planet.equals(holder.getName()));
    }

    public static boolean isAbilityPurged(Game game, String planet) {
        return "true".equals(game.getStoredValue(PURGED_ABILITY_KEY + planet));
    }

    public static boolean isBackFace(Game game, String planet) {
        return "true".equals(game.getStoredValue(BACK_FACE_KEY + planet));
    }

    private static void flip(Game game, String planet) {
        if (isBackFace(game, planet)) {
            game.removeStoredValue(BACK_FACE_KEY + planet);
        } else {
            game.setStoredValue(BACK_FACE_KEY + planet, "true");
        }
    }

    private static boolean hasToken(Game game, String planet, String token) {
        Planet holder = game.getUnitHolderFromPlanet(planet);
        return holder != null && holder.getTokenList().contains(token);
    }

    private static List<Button> getEkoButtons(Game game, Player player) {
        return getEkoTargets(game, player).stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + EKO_PREFIX + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static List<Tile> getEkoTargets(Game game, Player player) {
        String frontier = Mapper.getTokenID(Constants.FRONTIER);
        Set<String> systemTileIds = new HashSet<>(Mapper.getFrontierTileIds());
        return visibleTiles(game, player).stream()
                .filter(tile -> tile.getPlanetUnitHolders().isEmpty())
                .filter(tile -> systemTileIds.contains(tile.getTileID()))
                .filter(tile -> !tile.getSpaceUnitHolder().getTokenList().contains(frontier))
                .toList();
    }

    private static List<Button> getHoraceButtons(Game game, Player player) {
        return getHoraceTargets(game, player).stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + HORACE_PREFIX + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static List<Tile> getHoraceTargets(Game game, Player player) {
        return visibleTiles(game, player).stream()
                .filter(tile -> tile.isAnomaly(game, player))
                .toList();
    }

    private static List<Tile> visibleTiles(Game game, Player player) {
        Set<String> visiblePositions = game.isFowMode() ? FoWHelper.getTilePositionsToShow(game, player) : null;
        return game.getTileMap().values().stream()
                .filter(tile -> visiblePositions == null || visiblePositions.contains(tile.getPosition()))
                .toList();
    }

    private static List<AttachmentModel> getAvailableInitiatives(Game game) {
        List<String> attached = game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .flatMap(planet -> planet.getTokenList().stream())
                .toList();
        return Mapper.getAttachments().values().stream()
                .filter(attachment -> attachment.getId().startsWith("lloyd_"))
                .filter(attachment -> !attached.contains(attachment.getImagePath()))
                .toList();
    }

    private static List<LeaderModel> getUnchosenCommanders(Game game) {
        Set<String> seatedFactions = new HashSet<>();
        Set<String> seatedLeaders = new HashSet<>();
        for (Player player : game.getPlayers().values()) {
            seatedFactions.add(player.getFaction());
            FactionModel faction = Mapper.getFaction(player.getFaction());
            if (faction != null && faction.getLeaders() != null) {
                seatedLeaders.addAll(faction.getLeaders());
            }
        }
        Map<String, LeaderModel> commanders = new LinkedHashMap<>();
        for (FactionModel faction : Mapper.getFactionsValues()) {
            boolean inPool = COMMANDER_SOURCES.contains(faction.getSource())
                    || (game.isThundersEdge() && faction.getSource() == ComponentSource.thunders_edge);
            if (!inPool || seatedFactions.contains(faction.getAlias()) || faction.getLeaders() == null) {
                continue;
            }
            faction.getLeaders().stream()
                    .filter(id -> id.endsWith("commander") && !seatedLeaders.contains(id))
                    .map(Mapper::getLeader)
                    .filter(Objects::nonNull)
                    .forEach(leader -> commanders.putIfAbsent(leader.getId(), leader));
        }
        return new ArrayList<>(commanders.values());
    }
}
