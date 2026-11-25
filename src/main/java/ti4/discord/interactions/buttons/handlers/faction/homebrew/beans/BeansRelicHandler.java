package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.agenda.AgendaResolveButtonHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.oblivion.OblivionUnitHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AgendaRiderHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.TeHelperActionCards;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.info.RelicInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.map.TokenPlanetService;
import ti4.service.planet.AddPlanetService;
import ti4.service.player.PlayerStatsService;
import ti4.service.strategycard.PickStrategyCardService;
import ti4.service.strategycard.PlayStrategyCardService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class BeansRelicHandler {
    private static final String ASHEN_HOURGLASS = "ashenhourglass";
    private static final String USE_ASHEN_HOURGLASS = "beansUseAshenHourglass_";
    private static final String SELECT_ASHEN_HOURGLASS_UNIT = "beansSelectAshenHourglassUnit_";
    private static final String ASHEN_HOURGLASS_TARGET = "beansAshenHourglassTarget_";
    private static final String HAND_OF_THE_EMPEROR = "handoftheemperor";
    private static final String RESOLVE_HAND_OF_THE_EMPEROR = "beansResolveHandOfTheEmperor";
    private static final String HAND_OF_THE_EMPEROR_PLANET_OWNER = "beansHandOfTheEmperorPlanetOwner_";
    private static final String THE_SILENCE = "thesilence";
    private static final String USE_THE_SILENCE = "beansUseTheSilence";
    private static final String DECLINE_THE_SILENCE = "beansDeclineTheSilence";
    private static final String PICK_WITH_THE_SILENCE = "beansPickWithTheSilence_";
    private static final String HAMITS_TREASURE = "hamitstreasure";
    private static final String HAMITS_TREASURE_TRADE_GOODS = "beansHamitsTreasureTradeGoods_";
    private static final String HAMITS_TREASURE_STRATEGY_ROUND = "beansHamitsTreasureStrategyRound_";
    private static final String SPEND_HAMITS_TREASURE = "beansSpendHamitsTreasure_";
    private static final String HAMITS_TREASURE_SPENT = "hamitstreasure_";
    private static final String SHOW_HAMITS_TREASURE = "beansShowHamitsTreasure";
    private static final String WELLSPRING = "wellspring";
    private static final String LIBERTALIA = "libertalia";
    private static final String PLACE_LIBERTALIA = "beansPlaceLibertalia_";
    private static final String CHOOSE_RIVER_OF_PARADISE_PLANET = "beansChooseRiverOfParadisePlanet_";
    private static final String REPAIR_RIVER_OF_PARADISE = "beansRepairRiverOfParadise_";
    private static final String PLACE_RIVER_OF_PARADISE_INFANTRY = "beansPlaceRiverOfParadiseInfantry_";
    private static final String OUROBOROS = "ouroboros";
    private static final String OUROBOROS_FRAGMENTS = "beansOuroborosFragments_";
    private static final String CAPTURE_OUROBOROS_FRAGMENT = "beansCaptureOuroborosFragment_";
    private static final String DECLINE_OUROBOROS_FRAGMENT = "beansDeclineOuroborosFragment_";
    private static final String VIEW_OUROBOROS_FRAGMENTS = "beansViewOuroborosFragments";
    private static final String TEMPORAL_DISSIDENCE = "temporaldissidence";
    private static final String USE_TEMPORAL_DISSIDENCE = "beansUseTemporalDissidence";
    private static final String DECLINE_TEMPORAL_DISSIDENCE = "beansDeclineTemporalDissidence";
    private static final String SELECT_TEMPORAL_DISSIDENCE_PRIMARY = "beansTemporalDissidencePrimary_";

    public static void onRelicGained(Game game, Player player, String relicId) {
        if (HAMITS_TREASURE.equals(relicId)) {
            game.setStoredValue(hamitsTreasureTradeGoodsKey(player), "5");
            game.removeStoredValue(hamitsTreasureStrategyRoundKey(player));
        }
        if (WELLSPRING.equals(relicId)) {
            offerLibertaliaPlacement(game, player);
        }
    }

    private static void offerLibertaliaPlacement(Game game, Player player) {
        if (!player.hasRelic(WELLSPRING) || game.getTileFromPlanet(LIBERTALIA) != null) {
            return;
        }
        List<Button> buttons = ButtonHelper.getTilesWithPredicateForAction(
                player,
                game,
                PLACE_LIBERTALIA.substring(0, PLACE_LIBERTALIA.length() - 1),
                tile -> !tile.isHomeSystem(game) && FoWHelper.playerHasActualShipsInSystem(player, tile),
                false);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " gained _The Wellspring_, but has no system containing ships in which to place Libertalia.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a non-home system containing your ships for Libertalia.",
                buttons);
    }

    @ButtonHandler(PLACE_LIBERTALIA)
    public static void placeLibertalia(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.substring(PLACE_LIBERTALIA.length());
        Tile tile = game.getTileByPosition(position);
        if (!player.hasRelic(WELLSPRING)
                || game.getTileFromPlanet(LIBERTALIA) != null
                || tile == null
                || tile.isHomeSystem(game)
                || !FoWHelper.playerHasActualShipsInSystem(player, tile)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        String tokenId = Mapper.getTokenID(LIBERTALIA);
        if (tokenId == null) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        tile.getSpaceUnitHolder().addToken(tokenId);
        TokenPlanetService.addTokenPlanetToTile(game, tile, LIBERTALIA);
        game.clearPlanetsCache();
        AddPlanetService.addPlanet(player, LIBERTALIA, game, event, false);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed Libertalia in "
                        + tile.getRepresentationForButtons(game, player) + " and gained control of it.");
        ButtonHelper.deleteMessage(event);
    }

    public static void chooseRiverOfParadisePlanet(Game game, Player player) {
        if (!player.hasPlanet(LIBERTALIA)) {
            return;
        }
        List<Button> buttons = player.getExhaustedPlanets().stream()
                .filter(planet -> game.getTileFromPlanet(planet) != null)
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + CHOOSE_RIVER_OF_PARADISE_PLANET + planet,
                        Helper.getPlanetRepresentation(planet, game)))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a planet for _River of Paradise_.",
                buttons);
    }

    @ButtonHandler(CHOOSE_RIVER_OF_PARADISE_PLANET)
    public static void chooseRiverOfParadiseEffect(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.substring(CHOOSE_RIVER_OF_PARADISE_PLANET.length());
        if (!player.hasPlanet(LIBERTALIA) || !player.getPlanets().contains(planet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose _River of Paradise_'s effect.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + REPAIR_RIVER_OF_PARADISE + planet,
                                "Repair Damaged Mechs"),
                        Buttons.green(
                                player.factionButtonChecker() + PLACE_RIVER_OF_PARADISE_INFANTRY + planet,
                                "Place 2 Infantry")));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(REPAIR_RIVER_OF_PARADISE)
    public static void repairRiverOfParadiseMechs(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        resolveRiverOfParadise(event, game, player, buttonID.substring(REPAIR_RIVER_OF_PARADISE.length()), true);
    }

    @ButtonHandler(PLACE_RIVER_OF_PARADISE_INFANTRY)
    public static void placeRiverOfParadiseInfantry(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        resolveRiverOfParadise(
                event, game, player, buttonID.substring(PLACE_RIVER_OF_PARADISE_INFANTRY.length()), false);
    }

    private static void resolveRiverOfParadise(
            ButtonInteractionEvent event, Game game, Player player, String planet, boolean repairMechs) {
        Tile tile = game.getTileFromPlanet(planet);
        if (!player.hasPlanet(LIBERTALIA) || !player.getPlanets().contains(planet) || tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.exhaustPlanetAbility(LIBERTALIA);
        player.refreshPlanet(planet);
        if (repairMechs) {
            repairDamagedMechs(game, player);
        } else {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "2 infantry " + planet);
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " exhausted _River of Paradise_, readied "
                        + Helper.getPlanetRepresentation(planet, game) + ", and "
                        + (repairMechs ? "repaired all damaged mechs." : "placed 2 infantry there."));
        ButtonHelper.deleteMessage(event);
    }

    private static void repairDamagedMechs(Game game, Player player) {
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (UnitKey unitKey : holder.getUnitKeysForPlayer(player)) {
                    if (unitKey.unitType() == UnitType.Mech) {
                        holder.removeDamagedUnit(unitKey, holder.getUnitCount(unitKey));
                    }
                }
            }
        }
    }

    public static void onRelicPurged(Game game, Player player, String relicId) {
        if (HAMITS_TREASURE.equals(relicId)) {
            game.removeStoredValue(hamitsTreasureTradeGoodsKey(player));
            game.removeStoredValue(hamitsTreasureStrategyRoundKey(player));
        }
        if (OUROBOROS.equals(relicId)) {
            List<String> fragments = releaseOuroborosFragments(game, player);
            if (!fragments.isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentationNoPing() + " gained the relic fragments from _The Ouroboros_: "
                                + fragments.stream()
                                        .map(BeansRelicHandler::fragmentName)
                                        .collect(java.util.stream.Collectors.joining(", "))
                                + ".");
            }
        }
    }

    public static void addHamitsTreasureTradeGoodsAtStrategyPhaseStart(Game game) {
        for (Player player : game.getRealPlayers()) {
            if (!player.hasRelic(HAMITS_TREASURE)
                    || Integer.toString(game.getRound())
                            .equals(game.getStoredValue(hamitsTreasureStrategyRoundKey(player)))) {
                continue;
            }
            int tradeGoods = getHamitsTreasureTradeGoods(game, player) + 2;
            game.setStoredValue(hamitsTreasureTradeGoodsKey(player), Integer.toString(tradeGoods));
            game.setStoredValue(hamitsTreasureStrategyRoundKey(player), Integer.toString(game.getRound()));
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " placed 2 trade goods on _Hamit's Treasure_ (" + tradeGoods
                            + " total).");
        }
    }

    public static List<Button> getHamitsTreasurePaymentButtons(Game game, Player player, String paymentType) {
        if (!player.hasRelic(HAMITS_TREASURE)) {
            return List.of();
        }
        int tradeGoods = getHamitsTreasureTradeGoods(game, player);
        if (tradeGoods < 1) {
            return List.of();
        }
        List<Button> buttons = new ArrayList<>();
        for (int amount = 1; amount <= Math.min(3, tradeGoods); amount++) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + SPEND_HAMITS_TREASURE + amount + "_" + paymentType,
                    "Spend " + amount + " from Hamit's Treasure"));
        }
        return buttons;
    }

    public static Button getHamitsTreasureCardsInfoButton(Game game, Player player) {
        if (!player.hasRelic(HAMITS_TREASURE)) {
            return null;
        }
        return Buttons.gray(
                player.factionButtonChecker() + SHOW_HAMITS_TREASURE,
                "Hamit's Treasure Trade Goods",
                ExploreEmojis.Relic);
    }

    public static Button getOuroborosFragmentsCardsInfoButton(Player player) {
        if (!player.hasRelic(OUROBOROS)) {
            return null;
        }
        return Buttons.gray(
                player.factionButtonChecker() + VIEW_OUROBOROS_FRAGMENTS,
                "View Ouroboros Fragments",
                ExploreEmojis.Relic);
    }

    @ButtonHandler(VIEW_OUROBOROS_FRAGMENTS)
    public static void viewOuroborosFragments(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasRelic(OUROBOROS)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        List<String> fragments = getOuroborosFragments(game, player);
        String fragmentText = fragments.isEmpty()
                ? "There are no relic fragments on _The Ouroboros_."
                : "Relic fragments on _The Ouroboros_:\n- "
                        + fragments.stream()
                                .map(BeansRelicHandler::fragmentName)
                                .collect(java.util.stream.Collectors.joining("\n- "));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), fragmentText);
    }

    public static void offerOuroborosFragments(Game game, Player purgingPlayer, List<String> purgedFragments) {
        List<String> eligibleFragments = purgedFragments.stream()
                .filter(fragment -> !fragment.startsWith("stoneborn"))
                .toList();
        if (eligibleFragments.isEmpty()) {
            return;
        }
        for (Player owner : game.getRealPlayers()) {
            if (owner == purgingPlayer || !owner.hasRelic(OUROBOROS)) {
                continue;
            }
            List<Button> buttons = new ArrayList<>(eligibleFragments.stream()
                    .map(fragment -> Buttons.gray(
                            owner.factionButtonChecker() + CAPTURE_OUROBOROS_FRAGMENT + fragment,
                            "Place " + fragmentName(fragment) + " on Ouroboros"))
                    .toList());
            buttons.add(Buttons.red(owner.factionButtonChecker() + DECLINE_OUROBOROS_FRAGMENT, "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    owner.getCardsInfoThread(),
                    "Another player purged relic fragments. You may place 1 of those fragments on _The Ouroboros_.",
                    buttons);
        }
    }

    public static List<String> getOuroborosFragmentsInPlay(Game game) {
        return game.getRealPlayers().stream()
                .filter(player -> player.hasRelic(OUROBOROS))
                .flatMap(player -> getOuroborosFragments(game, player).stream())
                .toList();
    }

    @ButtonHandler(CAPTURE_OUROBOROS_FRAGMENT)
    public static void captureOuroborosFragment(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String fragment = buttonID.substring(CAPTURE_OUROBOROS_FRAGMENT.length());
        if (!player.hasRelic(OUROBOROS) || fragment.startsWith("stoneborn")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<String> fragments = new ArrayList<>(getOuroborosFragments(game, player));
        fragments.add(fragment);
        setOuroborosFragments(game, player, fragments);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DECLINE_OUROBOROS_FRAGMENT)
    public static void declineOuroborosFragment(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveOuroboros(Game game, Player player, ButtonInteractionEvent event) {
        List<String> fragments = releaseOuroborosFragments(game, player);
        RelicInfoService.sendRelicInfo(player);
        if (fragments.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "_The Ouroboros_ held no relic fragments.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " gained the relic fragments from _The Ouroboros_: "
                        + fragments.stream()
                                .map(BeansRelicHandler::fragmentName)
                                .collect(java.util.stream.Collectors.joining(", "))
                        + ".");
    }

    private static List<String> releaseOuroborosFragments(Game game, Player player) {
        List<String> fragments = getOuroborosFragments(game, player);
        for (String fragment : fragments) {
            player.addFragment(fragment);
        }
        game.setNumberOfPurgedFragments(Math.max(0, game.getNumberOfPurgedFragments() - fragments.size()));
        game.removeStoredValue(ouroborosFragmentsKey(player));
        return fragments;
    }

    public static void offerTemporalDissidence(Game game, Player player, int playedStrategyCard) {
        if (!player.hasRelic(TEMPORAL_DISSIDENCE)
                || TeHelperActionCards.getOverrulePrimaryButtons(game, player, false)
                        .isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation()
                        + ", you may purge _Temporal Dissidence_ to resolve the primary ability of a readied or unchosen strategy card instead.",
                List.of(
                        Buttons.red(player.factionButtonChecker() + USE_TEMPORAL_DISSIDENCE, "Use Temporal Dissidence"),
                        Buttons.gray(player.factionButtonChecker() + DECLINE_TEMPORAL_DISSIDENCE, "Decline")));
    }

    @ButtonHandler(USE_TEMPORAL_DISSIDENCE)
    public static void useTemporalDissidence(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasRelic(TEMPORAL_DISSIDENCE)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.removeRelic(TEMPORAL_DISSIDENCE);
        player.removeExhaustedRelic(TEMPORAL_DISSIDENCE);
        OblivionUnitHandler.doOblivionMechCheck(game, player);
        RelicInfoService.sendRelicInfo(player);
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : game.getMainGameChannel(),
                player.getRepresentationNoPing() + " purged _Temporal Dissidence_.");
        List<Button> buttons = TeHelperActionCards.getOverrulePrimaryButtons(game, player, false).stream()
                .map(button -> button.withCustomId(player.factionButtonChecker()
                        + SELECT_TEMPORAL_DISSIDENCE_PRIMARY
                        + getStrategyCardFromOverruleButton(button)))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "There are no eligible strategy cards for _Temporal Dissidence_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", choose the strategy card whose primary ability you will resolve with _Temporal Dissidence_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DECLINE_TEMPORAL_DISSIDENCE)
    public static void declineTemporalDissidence(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_TEMPORAL_DISSIDENCE_PRIMARY)
    public static void resolveTemporalDissidencePrimary(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int strategyCard = parsePositiveInt(buttonID.substring(SELECT_TEMPORAL_DISSIDENCE_PRIMARY.length()));
        boolean eligible = TeHelperActionCards.getOverrulePrimaryButtons(game, player, false).stream()
                .map(BeansRelicHandler::getStrategyCardFromOverruleButton)
                .anyMatch(sc -> sc == strategyCard);
        if (!eligible) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        PlayStrategyCardService.playSC(
                event,
                strategyCard,
                game,
                game.getMainGameChannel(),
                player,
                true,
                true,
                "resolved via Temporal Dissidence");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SHOW_HAMITS_TREASURE)
    public static void showHamitsTreasure(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasRelic(HAMITS_TREASURE)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "_Hamit's Treasure_ has " + getHamitsTreasureTradeGoods(game, player) + " trade goods on it.");
    }

    public static int getHamitsTreasureSpent(Player player) {
        return player.getSpentThingsThisWindow().stream()
                .filter(thing -> thing.startsWith(HAMITS_TREASURE_SPENT))
                .mapToInt(thing -> parsePositiveInt(thing.substring(HAMITS_TREASURE_SPENT.length())))
                .sum();
    }

    public static void resolveHamitsTreasure(Game game, Player player, ButtonInteractionEvent event) {
        int tradeGoods = getHamitsTreasureTradeGoods(game, player);
        game.removeStoredValue(hamitsTreasureTradeGoodsKey(player));
        game.removeStoredValue(hamitsTreasureStrategyRoundKey(player));
        int gained = tradeGoods * 2;
        String tradeGoodChange = player.gainTG(gained, true);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, gained);
        CommanderUnlockCheckService.checkPlayer(player, "hacan");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " gained " + gained + " trade goods from _Hamit's Treasure_ "
                        + tradeGoodChange + ".");
    }

    @ButtonHandler(SPEND_HAMITS_TREASURE)
    public static void spendHamitsTreasure(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(SPEND_HAMITS_TREASURE.length()).split("_", 2);
        int amount = values.length == 2 ? parsePositiveInt(values[0]) : 0;
        String paymentType = values.length == 2 ? values[1] : "both";
        if (amount < 1 || !player.hasRelic(HAMITS_TREASURE) || amount > getHamitsTreasureTradeGoods(game, player)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        game.setStoredValue(
                hamitsTreasureTradeGoodsKey(player),
                Integer.toString(getHamitsTreasureTradeGoods(game, player) - amount));
        player.addSpentThing(HAMITS_TREASURE_SPENT + amount);
        event.getMessage()
                .editMessage(Helper.buildSpentThingsMessage(player, game, paymentType))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static void addAshenHourglassCombatButton(
            List<Button> buttons, Game game, Player owner, Player opponent, Tile tile) {
        if (!owner.hasRelicReady(ASHEN_HOURGLASS)) {
            return;
        }
        buttons.add(Buttons.gray(
                owner.factionButtonChecker() + USE_ASHEN_HOURGLASS + opponent.getFaction() + "|" + tile.getPosition(),
                "Use Ashen Hourglass",
                ExploreEmojis.Relic));
    }

    @ButtonHandler(USE_ASHEN_HOURGLASS)
    public static void offerAshenHourglassUnitTypes(
            ButtonInteractionEvent event, Game game, Player owner, String buttonID) {
        if (!owner.hasRelicReady(ASHEN_HOURGLASS)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        owner.addExhaustedRelic(ASHEN_HOURGLASS);
        String[] payload = buttonID.substring(USE_ASHEN_HOURGLASS.length()).split("\\|", 2);
        Player opponent = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[1]) : null;
        if (opponent == null || tile == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons = tile.getSpaceUnitHolder().getUnitKeysForPlayer(opponent).stream()
                .filter(unit -> tile.getSpaceUnitHolder().getUnitCount(unit) > 0)
                .map(unit -> unit.unitType())
                .distinct()
                .map(unitType -> Buttons.gray(
                        owner.factionButtonChecker() + SELECT_ASHEN_HOURGLASS_UNIT + opponent.getFaction() + "|"
                                + tile.getPosition() + "|" + unitType.name(),
                        "Prevent " + unitType.humanReadableName() + " Combat",
                        unitType.getUnitTypeEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                game.isFowMode() ? owner.getCorrectChannel() : event.getMessageChannel(),
                owner.getRepresentationNoPing() + ", choose an opponent unit type for _The Ashen Hourglass_.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_ASHEN_HOURGLASS_UNIT)
    public static void resolveAshenHourglass(ButtonInteractionEvent event, Game game, Player owner, String buttonID) {
        String[] payload =
                buttonID.substring(SELECT_ASHEN_HOURGLASS_UNIT.length()).split("\\|", 3);
        Player opponent = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[1]) : null;
        UnitType unitType = payload.length == 3 ? unitType(payload[2]) : null;
        if (unitType == null
                || opponent == null
                || tile == null
                || tile.getSpaceUnitHolder().getUnitCount(unitType, opponent) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        int combatRound = getUpcomingCombatRound(game, opponent, tile);
        game.setStoredValue(targetKey(opponent), tile.getPosition() + "|" + combatRound + "|" + unitType.name());
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? owner.getCorrectChannel() : event.getMessageChannel(),
                owner.getRepresentationNoPing() + " exhausted _The Ashen Hourglass_; "
                        + opponent.getFactionEmojiOrColor() + "'s " + unitType.humanReadableName()
                        + " units will not roll combat dice this combat round.");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean isAshenHourglassUnitDisabled(Game game, Player player, Tile tile, String unitBaseType) {
        String[] state = game.getStoredValue(targetKey(player)).split("\\|", 3);
        if (state.length != 3
                || !tile.getPosition().equals(state[0])
                || !matchesAshenHourglassCombatRound(game, player, tile, state)) {
            return false;
        }
        return UnitType.valueOf(state[2]).plainName().equalsIgnoreCase(unitBaseType);
    }

    public static void clearAshenHourglassTargets(Game game) {
        game.getRealPlayers().forEach(player -> game.removeStoredValue(targetKey(player)));
    }

    private static boolean matchesAshenHourglassCombatRound(Game game, Player player, Tile tile, String[] state) {
        int storedRound;
        try {
            storedRound = Integer.parseInt(state[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        boolean matches = storedRound == getUpcomingCombatRound(game, player, tile);
        if (!matches) {
            game.setStoredValue(targetKey(player), "");
        }
        return matches;
    }

    private static int getUpcomingCombatRound(Game game, Player player, Tile tile) {
        String tracker =
                game.getStoredValue("combatRoundTracker" + player.getFaction() + tile.getPosition() + Constants.SPACE);
        if (tracker.isBlank()) {
            return 1;
        }
        if ("true".equalsIgnoreCase(game.getStoredValue("thalnosPlusOne"))) {
            return Integer.parseInt(tracker);
        }
        return Integer.parseInt(tracker) + 1;
    }

    public static boolean resolveHandOfEmperor(ButtonInteractionEvent event, Game game, Player player, String relicId) {
        if (!HAND_OF_THE_EMPEROR.equals(relicId) || !player.hasRelic(HAND_OF_THE_EMPEROR)) {
            return false;
        }
        String currentAgenda = game.getCurrentAgendaInfo();
        if (currentAgenda == null || currentAgenda.isBlank()) {
            return false;
        }
        player.removeRelic(HAND_OF_THE_EMPEROR);
        player.removeExhaustedRelic(HAND_OF_THE_EMPEROR);
        OblivionUnitHandler.doOblivionMechCheck(game, player);
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : game.getMainGameChannel(),
                player.getRepresentationNoPing()
                        + " purged the relic _"
                        + Mapper.getRelic(HAND_OF_THE_EMPEROR).getName()
                        + "_.\n> "
                        + Mapper.getRelic(HAND_OF_THE_EMPEROR).getText());
        RelicInfoService.sendRelicInfo(player);
        List<Button> buttons = handOfTheEmperorOutcomeButtons(game);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "There are no legal outcomes for _Hand of the Emperor_.");
            return true;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                game.isFowMode() ? player.getCorrectChannel() : game.getMainGameChannel(),
                player.getRepresentationNoPing() + ", choose the agenda outcome for _Hand of the Emperor_.",
                buttons);
        return true;
    }

    public static boolean offerTheSilenceAtStrategyPhaseStart(Game game) {
        List<Player> owners = game.getRealPlayers().stream()
                .filter(player -> player.hasRelic(THE_SILENCE))
                .toList();
        if (owners.isEmpty()) {
            return false;
        }
        for (Player owner : owners) {
            MessageHelper.sendMessageToChannelWithButtons(
                    owner.getCardsInfoThread(),
                    owner.getRepresentation() + ", you may use _The Silence_ before strategy card selection begins.",
                    List.of(
                            Buttons.red(owner.factionButtonChecker() + USE_THE_SILENCE, "Use The Silence"),
                            Buttons.gray(owner.factionButtonChecker() + DECLINE_THE_SILENCE, "Decline")));
        }
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    String.join(
                                    " and ",
                                    owners.stream()
                                            .map(Player::getRepresentationNoPing)
                                            .toList())
                            + " has _The Silence_. Please wait for them to decide whether to use it before choosing strategy cards.");
        }
        return true;
    }

    @ButtonHandler(USE_THE_SILENCE)
    public static void useTheSilence(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasRelic(THE_SILENCE)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.removeRelic(THE_SILENCE);
        player.removeExhaustedRelic(THE_SILENCE);
        OblivionUnitHandler.doOblivionMechCheck(game, player);
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : game.getMainGameChannel(),
                player.getRepresentationNoPing() + " purged _"
                        + Mapper.getRelic(THE_SILENCE).getName() + "_.");
        RelicInfoService.sendRelicInfo(player);
        List<Button> buttons = Helper.getRemainingSCButtons(game, player).stream()
                .map(button -> button.withCustomId(player.factionButtonChecker()
                        + PICK_WITH_THE_SILENCE
                        + button.getCustomId().substring(button.getCustomId().lastIndexOf('_') + 1)))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                game.isFowMode() ? player.getCorrectChannel() : game.getMainGameChannel(),
                player.getRepresentationNoPing() + ", choose the strategy card taken by _The Silence_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DECLINE_THE_SILENCE)
    public static void declineTheSilence(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PICK_WITH_THE_SILENCE)
    public static void pickWithTheSilence(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int strategyCard;
        try {
            strategyCard = Integer.parseInt(buttonID.substring(PICK_WITH_THE_SILENCE.length()));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!PlayerStatsService.secondHalfOfPickSC(event, game, player, strategyCard)) {
            return;
        }
        ButtonHelper.deleteMessage(event);
        resumeStrategyCardSelectionFromSpeaker(event, game);
    }

    private static void resumeStrategyCardSelectionFromSpeaker(ButtonInteractionEvent event, Game game) {
        List<Player> order = PickStrategyCardService.getSCPickOrder(game);
        Player speaker = game.getSpeaker();
        int speakerIndex = order.indexOf(speaker);
        if (speakerIndex < 0) {
            return;
        }
        Player beforeSpeaker = order.get((speakerIndex + order.size() - 1) % order.size());
        PickStrategyCardService.secondHalfOfSCPick(event, beforeSpeaker, game, 0);
    }

    @ButtonHandler(RESOLVE_HAND_OF_THE_EMPEROR)
    public static void resolveHandOfEmperorOutcome(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String winner = buttonID.substring(RESOLVE_HAND_OF_THE_EMPEROR.length() + 1);
        boolean validOutcome = handOfTheEmperorOutcomeButtons(game).stream()
                .map(Button::getCustomId)
                .anyMatch(id -> (RESOLVE_HAND_OF_THE_EMPEROR + "_" + winner).equals(id));
        if (!validOutcome) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String identifier = game.isFowMode() ? player.getColor() : player.getFaction();
        String existingVotes = game.getCurrentAgendaVotes().getOrDefault(winner, "empty");
        game.setCurrentAgendaVote(
                winner,
                "empty".equalsIgnoreCase(existingVotes) ? identifier + "_1" : existingVotes + ";" + identifier + "_1");
        game.setStoredValue("whensResolved", "Yes");
        game.setStoredValue("aftersResolved", "Yes");
        AgendaResolveButtonHandler.resolveAgenda(game, "agendaResolution_" + winner, event);
    }

    @ButtonHandler(HAND_OF_THE_EMPEROR_PLANET_OWNER)
    public static void offerHandOfEmperorPlanets(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player planetOwner =
                game.getPlayerFromColorOrFaction(buttonID.substring(HAND_OF_THE_EMPEROR_PLANET_OWNER.length()));
        if (planetOwner == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = planetOwner.getPlanets().stream()
                .filter(planet -> game.getTileFromPlanet(planet) != null)
                .map(planet -> Buttons.blue(
                        RESOLVE_HAND_OF_THE_EMPEROR + "_" + planet,
                        AgendaHelper.getAgendaOutcomeName(game, planet, true)))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                game.isFowMode() ? player.getCorrectChannel() : game.getMainGameChannel(),
                player.getRepresentationNoPing() + ", choose a planet for _Hand of the Emperor_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> handOfTheEmperorOutcomeButtons(Game game) {
        String currentAgenda = game.getCurrentAgendaInfo();
        if (currentAgenda == null || currentAgenda.isBlank() || !currentAgenda.contains("_")) {
            return List.of();
        }
        String agendaDetails = currentAgenda.split("_", 2)[1].toLowerCase();
        if (!agendaDetails.contains("planet")) {
            return AgendaRiderHelper.getAgendaButtons(null, game, RESOLVE_HAND_OF_THE_EMPEROR);
        }
        return game.getRealPlayers().stream()
                .map(player -> Buttons.blue(
                        HAND_OF_THE_EMPEROR_PLANET_OWNER + (game.isFowMode() ? player.getColor() : player.getFaction()),
                        game.isFowMode() ? player.getColor() : player.getFaction()))
                .toList();
    }

    private static UnitType unitType(String name) {
        try {
            return UnitType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String targetKey(Player player) {
        return ASHEN_HOURGLASS_TARGET + player.getFaction();
    }

    private static int getHamitsTreasureTradeGoods(Game game, Player player) {
        String key = hamitsTreasureTradeGoodsKey(player);
        String value = game.getStoredValue(key);
        if (value.isBlank()) {
            game.setStoredValue(key, "5");
            return 5;
        }
        return parsePositiveInt(value);
    }

    private static int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String hamitsTreasureTradeGoodsKey(Player player) {
        return HAMITS_TREASURE_TRADE_GOODS + player.getFaction();
    }

    private static String hamitsTreasureStrategyRoundKey(Player player) {
        return HAMITS_TREASURE_STRATEGY_ROUND + player.getFaction();
    }

    private static List<String> getOuroborosFragments(Game game, Player player) {
        String storedFragments = game.getStoredValue(ouroborosFragmentsKey(player));
        if (storedFragments.isBlank()) {
            return List.of();
        }
        return List.of(storedFragments.split("\\|"));
    }

    private static void setOuroborosFragments(Game game, Player player, List<String> fragments) {
        game.setStoredValue(ouroborosFragmentsKey(player), String.join("|", fragments));
    }

    private static String ouroborosFragmentsKey(Player player) {
        return OUROBOROS_FRAGMENTS + player.getFaction();
    }

    private static int getStrategyCardFromOverruleButton(Button button) {
        String buttonId = button.getCustomId();
        int start = buttonId.indexOf("winnuHero_") + "winnuHero_".length();
        int end = buttonId.indexOf('_', start);
        return Integer.parseInt(buttonId.substring(start, end));
    }

    private static String fragmentName(String fragment) {
        return Mapper.getExplore(fragment) == null
                ? fragment
                : Mapper.getExplore(fragment).getName();
    }
}
