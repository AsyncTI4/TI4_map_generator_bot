package ti4.discord.interactions.buttons.handlers.faction.homebrew.frmisc.xyrix;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.adjacency.Adjacency;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.model.TomeCardModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.info.KnownCardsService;
import ti4.service.info.KnownCardsService.KnownCardType;
import ti4.service.map.FractureService;
import ti4.service.tech.ListTechService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class XyrixTomeHandler {
    private static final String ABILITY = "tomebound_tutelage";
    private static final String OFFER_TOME = "xyrixOfferTome";
    private static final String CHOOSE_TOME = "xyrixChooseTome_";
    private static final String NEGOTIATION_PLANET = "xyrixNegotiationPlanet_";
    private static final String NEGOTIATION_USED = "xyrixNegotiationUsed_";
    private static final String NEGOTIATION_TARGET = "xyrixNegotiationTarget_";
    private static final String NEGOTIATION_SYSTEM = "xyrixNegotiationSystem_";
    private static final String NEGOTIATION_PLACED = "xyrixNegotiationPlaced_";
    private static final String INDUSTRIALIZATION_PDS = "xyrixIndustrializationPds_";
    private static final String INDUSTRIALIZATION_NORMAL_PDS = "xyrixIndustrializationNormalPds_";
    private static final String INDUSTRIALIZATION_USES = "xyrixIndustrializationUses_";
    private static final String INDUSTRIALIZATION_NORMAL_PENDING = "xyrixIndustrializationNormalPending_";
    private static final String INDUSTRIALIZATION_NORMAL_USED = "xyrixIndustrializationNormalUsed_";
    private static final String ADMINISTRATION = "xyrix_administration";
    private static final String NEGOTIATION = "xyrix_negotiation";
    private static final String INDUSTRIALIZATION = "xyrix_industrialization";
    private static final String MERCANTILISM = "xyrix_mercantilism";
    private static final String ENMITY = "xyrix_enmity";
    private static final String INTRIGUE = "xyrix_intrigue";
    private static final String EXPERIMENTATION = "xyrix_experimentation";
    private static final String VICTORY = "xyrix_victory";
    private static final String ENMITY_SYSTEM = "xyrixEnmitySystem_";
    private static final String INTRIGUE_DISCARD = "xyrixIntrigueDiscard_";
    private static final String INTRIGUE_DONE = "xyrixIntrigueDone";
    private static final String INTRIGUE_DISCARDS_REMAINING = "xyrixIntrigueDiscardsRemaining_";
    private static final String INTRIGUE_GIVEN = "xyrixIntrigueGiven_";
    private static final String INTRIGUE_TARGET = "xyrixIntrigueTarget_";
    private static final String INTRIGUE_GIVE = "xyrixIntrigueGive_";
    private static final String INDUSTRIALIZATION_PLANET = "xyrixIndustrializationPlanet_";
    private static final String EXPERIMENTATION_PURGE = "xyrixExperimentationPurge_";

    public static void initialize(Player player) {
        if (player.isTomeCardsInitialized()) {
            return;
        }
        int identifier = 1;
        for (String tomeId : Mapper.getTomeCards().keySet().stream().sorted().toList()) {
            player.setTomeCard(tomeId, identifier++);
        }
        player.setTomeCardsInitialized(true);
    }

    public static void sendTomeInfo(Game game, Player player) {
        if (!player.hasAbility(ABILITY)) {
            return;
        }
        initialize(player);
        List<TomeCardModel> tomes = new ArrayList<>();
        StringBuilder message = new StringBuilder("### __Tome Cards__\n");
        if (!player.getTomeCardInPlay().isBlank()) {
            TomeCardModel activeTome = Mapper.getTomeCard(player.getTomeCardInPlay());
            if (activeTome != null) {
                tomes.add(activeTome);
                message.append("**In play:** ")
                        .append(activeTome.getNameRepresentation())
                        .append('\n');
            }
        }
        if (player.getTomeCards().isEmpty()) {
            message.append("> None remaining");
        } else {
            player.getTomeCards().entrySet().stream()
                    .sorted(Comparator.comparingInt(java.util.Map.Entry::getValue))
                    .map(java.util.Map.Entry::getKey)
                    .map(Mapper::getTomeCard)
                    .filter(java.util.Objects::nonNull)
                    .forEach(tomes::add);
        }
        if (tomes.isEmpty()) {
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, message.toString());
            return;
        }
        List<MessageEmbed> embeds =
                tomes.stream().map(TomeCardModel::getRepresentationEmbed).toList();
        MessageHelper.sendMessageToChannelWithEmbeds(player.getCardsInfoThread(), message.toString(), embeds);
    }

    public static void offerEndOfStrategyTome(Game game, Player player) {
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            return;
        }
        purgeActiveTome(game, player);
        if (!player.hasAbility(ABILITY)) {
            return;
        }
        initialize(player);
        List<Button> buttons = player.getTomeCards().entrySet().stream()
                .sorted(Comparator.comparingInt(java.util.Map.Entry::getValue))
                .map(entry -> {
                    TomeCardModel tome = Mapper.getTomeCard(entry.getKey());
                    return tome == null
                            ? null
                            : Buttons.green(
                                    player.factionButtonChecker() + CHOOSE_TOME + entry.getValue(),
                                    tome.getName(),
                                    FactionEmojis.xyrix);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        if (buttons.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", choose 1 Tome Card to place face up and resolve.",
                buttons);
    }

    public static Button getTomeCardsInfoButton(Game game, Player player) {
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())
                || !player.hasAbility(ABILITY)
                || !player.getTomeCardInPlay().isBlank()
                || player.getTomeCards().isEmpty()) {
            return null;
        }
        return Buttons.green(player.factionButtonChecker() + OFFER_TOME, "Choose Tome Card", FactionEmojis.xyrix);
    }

    @ButtonHandler(OFFER_TOME)
    public static void offerTomeFromCardsInfo(ButtonInteractionEvent event, Game game, Player player) {
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())
                || !player.getTomeCardInPlay().isBlank()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        offerEndOfStrategyTome(game, player);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(CHOOSE_TOME)
    public static void chooseTome(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())
                || !player.hasAbility(ABILITY)
                || !player.getTomeCardInPlay().isBlank()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        int identifier;
        try {
            identifier = Integer.parseInt(buttonID.substring(CHOOSE_TOME.length()));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String tomeId = player.removeTomeCard(identifier);
        TomeCardModel tome = Mapper.getTomeCard(tomeId);
        if (tome == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.setTomeCardInPlay(tomeId);
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed " + tome.getNameRepresentation()
                        + " face up and is resolving it.",
                tome.getRepresentationEmbed());
        resolveTome(game, player, tome);
        ButtonHelper.deleteMessage(event);
    }

    private static void resolveTome(Game game, Player player, TomeCardModel tome) {
        if (game.getRound() == 1 && ADMINISTRATION.equals(tome.getAlias())) {
            reduceCommandTokenLimit(game, player);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + ", _Tome of Administration_ purged 1 command token from your reinforcements. Gain 2 command tokens, allocating them with these buttons.",
                    ButtonHelper.getGainCCButtons(player));
            return;
        }
        if (game.getRound() == 1 && MERCANTILISM.equals(tome.getAlias())) {
            player.setTg(player.getTg() + 2);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " gained 2 trade goods with **Tome of Mercantilism** and may transact with non-neighbors this round.");
            return;
        }
        switch (tome.getAlias()) {
            case ENMITY -> offerEnmityModifyUnits(game, player);
            case INTRIGUE -> resolveIntrigue(game, player);
            case INDUSTRIALIZATION -> {
                if (game.getRound() == 1) {
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentationNoPing()
                                    + " may use **Tome of Industrialization** up to twice this round when exploring a planet.");
                } else {
                    offerIndustrializationPlanet(game, player);
                }
            }
            case MERCANTILISM -> resolveMercantilism(game, player);
            case EXPERIMENTATION -> resolveExperimentation(game, player);
            case VICTORY -> resolveVictory(game, player);
            case ADMINISTRATION -> resolveAdministration(game, player);
            case NEGOTIATION -> resolveNegotiation(game, player);
            default ->
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentationNoPing() + " must resolve **" + tome.getName() + "**: "
                                + (game.getRound() == 1 ? tome.getFirstRoundText() : tome.getText()));
        }
    }

    private static void resolveAdministration(Game game, Player player) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing()
                        + ", spend 1 command token and 1 influence for **Tome of Administration**, then use these buttons to add 2 command tokens to your reinforcements.",
                ButtonHelper.getGainCCButtons(player));
    }

    private static void resolveNegotiation(Game game, Player player) {
        List<Button> buttons = game.getRealPlayersExcludingThis(player).stream()
                .filter(target -> !"placed"
                        .equals(game.getStoredValue(
                                NEGOTIATION_PLACED + player.getFaction() + "_" + target.getFaction())))
                .map(target -> Buttons.green(
                        player.factionButtonChecker() + NEGOTIATION_TARGET + target.getFaction(),
                        "Place " + target.getRepresentationNoPing() + " command token"))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing()
                        + ", place 1 command token from each other player's reinforcements with **Tome of Negotiation**.",
                buttons);
    }

    @ButtonHandler(NEGOTIATION_TARGET)
    public static void chooseNegotiationTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(NEGOTIATION_TARGET.length()));
        if (!hasActiveTome(player, NEGOTIATION) || target == null || target == player) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getNegotiationSystems(game, player).stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + NEGOTIATION_SYSTEM + target.getFaction() + "|"
                                + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing() + ", choose the system for " + target.getRepresentationNoPing()
                        + "'s command token.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(NEGOTIATION_SYSTEM)
    public static void placeNegotiationToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(NEGOTIATION_SYSTEM.length()).split("\\|", 2);
        Player target = parts.length == 2 ? game.getPlayerFromColorOrFaction(parts[0]) : null;
        Tile tile = parts.length == 2 ? game.getTileByPosition(parts[1]) : null;
        if (!hasActiveTome(player, NEGOTIATION)
                || target == null
                || target == player
                || "placed"
                        .equals(game.getStoredValue(
                                NEGOTIATION_PLACED + player.getFaction() + "_" + target.getFaction()))
                || tile == null
                || !getNegotiationSystems(game, player).contains(tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        CommandCounterHelper.addCC(event, target, tile, false);
        game.setStoredValue(NEGOTIATION_PLACED + player.getFaction() + "_" + target.getFaction(), "placed");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed " + target.getRepresentationNoPing()
                        + "'s command token in " + tile.getRepresentationForButtons(game, player)
                        + " with **Tome of Negotiation**.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Tile> getNegotiationSystems(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.isHomeSystem(game) && !tile.isMecatol(game))
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .map(planet -> planet.getName())
                        .anyMatch(player::hasPlanet))
                .toList();
    }

    private static void resolveIntrigue(Game game, Player player) {
        if (game.getRound() == 1) {
            ActionCardHelper.drawActionCards(player, 4);
            game.setStoredValue(INTRIGUE_DISCARDS_REMAINING + player.getFaction(), "2");
            List<Button> targetButtons = game.getRealPlayersExcludingThis(player).stream()
                    .map(target -> Buttons.green(
                            player.factionButtonChecker() + INTRIGUE_TARGET + target.getFaction(),
                            "Give 1 action card to " + target.getRepresentationNoPing()))
                    .toList();
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    player.getRepresentationNoPing()
                            + " drew 4 action cards with **Tome of Intrigue**. Choose another player to give 1 action card.",
                    targetButtons);
            sendIntrigueDiscardButtons(player, 2, false);
            return;
        }
        ActionCardHelper.drawActionCards(player, 1);
        game.setStoredValue(INTRIGUE_DISCARDS_REMAINING + player.getFaction(), "3");
        sendIntrigueDiscardButtons(player, 3, true);
    }

    @ButtonHandler(INTRIGUE_TARGET)
    public static void chooseIntrigueTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(INTRIGUE_TARGET.length()));
        if (game.getRound() != 1 || !hasActiveTome(player, INTRIGUE) || target == null || target == player) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = player.getActionCards().entrySet().stream()
                .map(entry -> Buttons.red(
                        player.factionButtonChecker() + INTRIGUE_GIVE + target.getFaction() + "|" + entry.getValue(),
                        "Give " + Mapper.getActionCard(entry.getKey()).getName()))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing() + ", choose 1 action card to give to "
                        + target.getRepresentationNoPing() + " for **Tome of Intrigue**.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(INTRIGUE_GIVE)
    public static void giveIntrigueActionCard(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(INTRIGUE_GIVE.length()).split("\\|", 2);
        Player target = parts.length == 2 ? game.getPlayerFromColorOrFaction(parts[0]) : null;
        try {
            int cardId = parts.length == 2 ? Integer.parseInt(parts[1]) : -1;
            String actionCard = player.getActionCards().entrySet().stream()
                    .filter(entry -> entry.getValue().equals(cardId))
                    .map(java.util.Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            if (game.getRound() != 1
                    || !hasActiveTome(player, INTRIGUE)
                    || !game.getStoredValue(INTRIGUE_GIVEN + player.getFaction())
                            .isEmpty()
                    || target == null
                    || actionCard == null) {
                ButtonHelper.deleteMessage(event);
                return;
            }
            player.removeActionCard(cardId);
            target.setActionCard(actionCard);
            game.setStoredValue(INTRIGUE_GIVEN + player.getFaction(), "given");
            KnownCardsService.recordCardPassed(player, target, KnownCardType.ACTION_CARD, actionCard);
            ActionCardHelper.sendActionCardInfo(game, player);
            ActionCardHelper.sendActionCardInfo(game, target);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " gave 1 action card to " + target.getRepresentationNoPing()
                            + " with **Tome of Intrigue**.");
            ButtonHelper.deleteMessage(event);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
        }
    }

    private static void sendIntrigueDiscardButtons(Player player, int remaining, boolean redraw) {
        List<Button> buttons = player.getActionCards().entrySet().stream()
                .map(entry -> Buttons.red(
                        player.factionButtonChecker() + INTRIGUE_DISCARD + remaining + "|" + redraw + "|"
                                + entry.getValue(),
                        "Discard " + Mapper.getActionCard(entry.getKey()).getName()))
                .toList();
        if (redraw) {
            buttons = new ArrayList<>(buttons);
            buttons.add(Buttons.red(player.factionButtonChecker() + INTRIGUE_DONE, "Done Discarding"));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing() + ", discard " + remaining + " action card"
                        + (remaining == 1 ? "" : "s") + " for **Tome of Intrigue**.",
                buttons);
    }

    @ButtonHandler(INTRIGUE_DISCARD)
    public static void discardForIntrigue(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(INTRIGUE_DISCARD.length()).split("\\|", 3);
        if (!hasActiveTome(player, INTRIGUE) || parts.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        try {
            int remaining = Integer.parseInt(parts[0]);
            boolean redraw = Boolean.parseBoolean(parts[1]);
            int cardId = Integer.parseInt(parts[2]);
            if (redraw == (game.getRound() == 1)
                    || remaining < 1
                    || remaining != getIntrigueDiscardsRemaining(game, player)
                    || !game.discardActionCard(player.getUserID(), cardId)) {
                ButtonHelper.deleteMessage(event);
                return;
            }
            if (redraw) {
                ActionCardHelper.drawActionCards(player, 1);
            }
            game.setStoredValue(INTRIGUE_DISCARDS_REMAINING + player.getFaction(), Integer.toString(remaining - 1));
            ButtonHelper.deleteMessage(event);
            if (remaining > 1) {
                sendIntrigueDiscardButtons(player, remaining - 1, redraw);
            }
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler(INTRIGUE_DONE)
    public static void finishIntrigueDiscards(ButtonInteractionEvent event, Game game, Player player) {
        if (game.getRound() == 1 || !hasActiveTome(player, INTRIGUE)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.removeStoredValue(INTRIGUE_DISCARDS_REMAINING + player.getFaction());
        ButtonHelper.deleteMessage(event);
    }

    private static void offerEnmityModifyUnits(Game game, Player player) {
        Button modify = Buttons.gray("getModifyTiles", "Modify Units");
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", use the modify-units menu to place up to 3 units with a combined cost of 3 or less for **Tome of Enmity**.",
                modify);
    }

    @ButtonHandler(ENMITY_SYSTEM)
    public static void chooseEnmitySystem(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(ENMITY_SYSTEM.length()).split("\\|", 2);
        if (!hasActiveTome(player, ENMITY) || parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        boolean firstRound = "first".equals(parts[0]);
        Tile tile = game.getTileByPosition(parts[1]);
        if (firstRound != (game.getRound() == 1)
                || tile == null
                || !(firstRound ? getEnmityFirstRoundTiles(game, player) : getEnmityProductionTiles(game, player))
                        .contains(tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = Helper.getPlaceUnitButtons(
                event, player, game, tile, "xyrixEnmity", firstRound ? "placeOneNDone_dontskip" : "place");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + (firstRound
                                ? ", place up to 3 units with a combined cost of 3 or less using **Tome of Enmity**."
                                : ", produce up to 4 units using **Tome of Enmity**. Reduce their combined cost by 2 when paying."),
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Tile> getEnmityFirstRoundTiles(Game game, Player player) {
        Tile home = player.getHomeSystemTile();
        if (home == null) {
            return List.of();
        }
        return game.getTileMap().values().stream()
                .filter(tile -> tile == home
                        || Adjacency.of(game, player)
                                .forEffects()
                                .from(home.getPosition())
                                .contains(tile.getPosition()))
                .toList();
    }

    private static List<Tile> getEnmityProductionTiles(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasShipsInSystem(player, tile))
                .toList();
    }

    private static void offerIndustrializationPlanet(Game game, Player player) {
        List<Button> buttons = player.getPlanets().stream()
                .filter(planet -> !player.getExhaustedPlanets().contains(planet))
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + INDUSTRIALIZATION_PLANET + planet,
                        Helper.getPlanetRepresentation(planet, game)))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " has no ready planets to exhaust for **Tome of Industrialization**.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", choose a planet to exhaust for **Tome of Industrialization**. You will then place 1 structure on it and 1 PDS on a planet you control.",
                buttons);
    }

    @ButtonHandler(INDUSTRIALIZATION_PLANET)
    public static void exhaustForIndustrialization(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.substring(INDUSTRIALIZATION_PLANET.length());
        if (game.getRound() == 1
                || !hasActiveTome(player, INDUSTRIALIZATION)
                || !game.getStoredValue(INDUSTRIALIZATION_NORMAL_USED + player.getFaction())
                        .isEmpty()
                || !player.getPlanets().contains(planet)
                || player.getExhaustedPlanets().contains(planet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.exhaustPlanet(planet);
        List<Button> buttons = player.getPlanets().stream()
                .map(target -> Buttons.green(
                        player.factionButtonChecker() + INDUSTRIALIZATION_NORMAL_PDS + target,
                        "Place PDS on " + Helper.getPlanetRepresentation(target, game)))
                .toList();
        game.setStoredValue(INDUSTRIALIZATION_NORMAL_PENDING + player.getFaction(), "pending");
        game.setStoredValue(INDUSTRIALIZATION_NORMAL_USED + player.getFaction(), "used");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " exhausted " + Helper.getPlanetRepresentation(planet, game)
                        + " with **Tome of Industrialization**. Place 1 structure there manually, then choose a planet for 1 PDS.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    private static void resolveMercantilism(Game game, Player player) {
        int neighborCount = player.getAbilityNeighbours().size();
        player.setCommoditiesBase(player.getCommoditiesBase() + 1);
        player.setTg(player.getTg() + neighborCount);
        for (Player neighbor : player.getAbilityNeighbours()) {
            neighbor.setTg(neighbor.getTg() + 1);
            MessageHelper.sendMessageToChannel(
                    neighbor.getCorrectChannel(),
                    neighbor.getRepresentationNoPing() + " gained 1 trade good from " + player.getRepresentationNoPing()
                            + " resolving **Tome of Mercantilism**.");
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " increased their commodity value by 1 and gained " + neighborCount
                        + " trade good" + (neighborCount == 1 ? "" : "s") + " with **Tome of Mercantilism**.");
    }

    private static void resolveExperimentation(Game game, Player player) {
        List<TechnologyModel> technologies = getExperimentationTechnologies(game, player);
        if (technologies.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " has no eligible technologies for **Tome of Experimentation**.");
            return;
        }
        List<Button> buttons = ListTechService.getTechButtons(
                new ArrayList<>(technologies), player, game.getRound() == 1 ? "free" : "normal");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + (game.getRound() == 1
                                ? ", research 1 non-faction technology, then choose a technology to purge."
                                : ", spend 4 resources to research a technology whose color you own 3 other technologies in."),
                buttons);
        if (game.getRound() == 1) {
            offerExperimentationPurge(game, player);
        }
    }

    private static List<TechnologyModel> getExperimentationTechnologies(Game game, Player player) {
        return Mapper.getTechs().values().stream()
                .filter(technology -> !technology.isFactionTech())
                .filter(technology -> game.getTechnologyDeck().contains(technology.getAlias()))
                .filter(technology -> !player.hasTech(technology.getAlias()))
                .filter(technology -> ListTechService.isTechResearchable(technology, player))
                .filter(technology -> game.getRound() == 1 || ownsThreeOtherTechnologiesOfColor(player, technology))
                .toList();
    }

    private static boolean ownsThreeOtherTechnologiesOfColor(Player player, TechnologyModel technology) {
        return player.getTechs().stream()
                        .map(Mapper::getTech)
                        .filter(java.util.Objects::nonNull)
                        .filter(other -> !other.getAlias().equals(technology.getAlias()))
                        .filter(other -> other.getFirstType() == technology.getFirstType())
                        .count()
                >= 3;
    }

    private static void offerExperimentationPurge(Game game, Player player) {
        List<Button> buttons = player.getTechs().stream()
                .map(Mapper::getTech)
                .filter(java.util.Objects::nonNull)
                .map(technology -> Buttons.red(
                        player.factionButtonChecker() + EXPERIMENTATION_PURGE + technology.getAlias(),
                        "Purge " + technology.getName()))
                .toList();
        if (!buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + ", after researching, purge 1 technology for **Tome of Experimentation**.",
                    buttons);
        }
    }

    @ButtonHandler(EXPERIMENTATION_PURGE)
    public static void purgeExperimentationTechnology(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String technologyId = buttonID.substring(EXPERIMENTATION_PURGE.length());
        if (game.getRound() != 1 || !hasActiveTome(player, EXPERIMENTATION) || !player.hasTech(technologyId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.purgeTech(technologyId);
        TechnologyModel technology = Mapper.getTech(technologyId);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " purged "
                        + (technology == null ? technologyId : technology.getNameRepresentation())
                        + " with **Tome of Experimentation**.");
        ButtonHelper.deleteMessage(event);
    }

    private static void resolveVictory(Game game, Player player) {
        if (game.getRound() == 1) {
            BreakthroughCommandHelper.unlockAllBreakthroughs(game, player);
            FractureService.enterPlayOrExplain(null, game, player, null);
            return;
        }
        game.drawSecretObjective(player.getUserID());
        List<Button> buttons =
                new ArrayList<>(SecretObjectiveHelper.getSODiscardButtonsWithSuffix(player, "tomevictory"));
        if (!buttons.isEmpty()) {
            buttons.add(Buttons.DONE_RED_DELETE_BUTTONS);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    player.getRepresentationNoPing() + ", discard all but 1 secret objective for **Tome of Victory**.",
                    buttons);
        }
    }

    public static boolean hasActiveTome(Player player, String tomeId) {
        return player.hasAbility(ABILITY) && tomeId.equals(player.getTomeCardInPlay());
    }

    public static boolean mayTransactWithNonNeighbor(Game game, Player player) {
        return game.getRound() == 1 && hasActiveTome(player, MERCANTILISM);
    }

    public static void addIndustrializationExploreButton(
            List<Button> buttons, Game game, Player player, String planetName) {
        if (game.getRound() != 1
                || !hasActiveTome(player, INDUSTRIALIZATION)
                || getIndustrializationUses(game, player) >= 2
                || !player.getPlanets().contains(planetName)
                || buttons.stream()
                        .anyMatch(button -> button.getCustomId().endsWith(INDUSTRIALIZATION_PDS + planetName))) {
            return;
        }
        buttons.add(Buttons.gray(
                player.factionButtonChecker() + INDUSTRIALIZATION_PDS + planetName,
                "Place PDS on " + Helper.getPlanetRepresentation(planetName, game) + " Instead",
                FactionEmojis.xyrix));
    }

    @ButtonHandler(INDUSTRIALIZATION_PDS)
    public static void placeIndustrializationPds(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring(INDUSTRIALIZATION_PDS.length());
        if (game.getRound() != 1
                || !hasActiveTome(player, INDUSTRIALIZATION)
                || getIndustrializationUses(game, player) >= 2
                || !player.getPlanets().contains(planetName)
                || game.getTileFromPlanet(planetName) == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(
                event, game.getTileFromPlanet(planetName), game, player.getColor(), "1 pds " + planetName);
        game.setStoredValue(
                INDUSTRIALIZATION_USES + player.getFaction(),
                Integer.toString(getIndustrializationUses(game, player) + 1));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed 1 PDS on "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " instead of exploring it with **Tome of Industrialization**.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(INDUSTRIALIZATION_NORMAL_PDS)
    public static void placeNormalIndustrializationPds(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring(INDUSTRIALIZATION_NORMAL_PDS.length());
        if (game.getRound() == 1
                || !hasActiveTome(player, INDUSTRIALIZATION)
                || game.getStoredValue(INDUSTRIALIZATION_NORMAL_PENDING + player.getFaction())
                        .isEmpty()
                || !player.getPlanets().contains(planetName)
                || game.getTileFromPlanet(planetName) == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(
                event, game.getTileFromPlanet(planetName), game, player.getColor(), "1 pds " + planetName);
        game.removeStoredValue(INDUSTRIALIZATION_NORMAL_PENDING + player.getFaction());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed 1 PDS on "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " with **Tome of Industrialization**.");
        ButtonHelper.deleteMessage(event);
    }

    public static Button getNegotiationEndTurnButton(Game game, Player player) {
        if (game.getRound() != 1
                || !hasActiveTome(player, NEGOTIATION)
                || !game.getStoredValue(NEGOTIATION_USED + player.getFaction()).isEmpty()
                || getNegotiationPlanets(game, player).isEmpty()) {
            return null;
        }
        return Buttons.green(
                player.factionButtonChecker() + NEGOTIATION_PLANET, "Use Tome of Negotiation", FactionEmojis.xyrix);
    }

    @ButtonHandler(NEGOTIATION_PLANET)
    public static void chooseNegotiationPlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring(NEGOTIATION_PLANET.length());
        if (game.getRound() != 1
                || !hasActiveTome(player, NEGOTIATION)
                || !game.getStoredValue(NEGOTIATION_USED + player.getFaction()).isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (planetName.isEmpty()) {
            List<Button> buttons = getNegotiationPlanets(game, player).stream()
                    .map(planet -> Buttons.green(
                            player.factionButtonChecker() + NEGOTIATION_PLANET + planet,
                            Helper.getPlanetRepresentation(planet, game)))
                    .toList();
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + ", choose a planet for **Tome of Negotiation**.",
                    buttons);
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        if (!getNegotiationPlanets(game, player).contains(planetName)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile tile = game.getTileFromPlanet(planetName);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planetName);
        player.addPlanet(planetName);
        game.setStoredValue(NEGOTIATION_USED + player.getFaction(), "used");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " gained control of "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " and placed 1 infantry with **Tome of Negotiation**.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<String> getNegotiationPlanets(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> Adjacency.of(game, player).forEffects().either(tile.getPosition()).stream()
                        .map(game::getTileByPosition)
                        .filter(java.util.Objects::nonNull)
                        .flatMap(adjacent -> adjacent.getPlanetUnitHolders().stream())
                        .map(planet -> planet.getName())
                        .anyMatch(player.getPlanets()::contains))
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .map(planet -> planet.getName())
                .filter(planet -> !"mecatol".equalsIgnoreCase(planet))
                .filter(planet -> game.getPlanetOwner(planet) == null)
                .filter(planet -> Mapper.getPlanet(planet) != null)
                .sorted()
                .toList();
    }

    private static void purgeActiveTome(Game game, Player player) {
        String activeTome = player.getTomeCardInPlay();
        if (activeTome.isBlank()) {
            return;
        }
        player.setTomeCardInPlay("");
        game.removeStoredValue(NEGOTIATION_USED + player.getFaction());
        game.removeStoredValue(INDUSTRIALIZATION_USES + player.getFaction());
        game.removeStoredValue(INDUSTRIALIZATION_NORMAL_PENDING + player.getFaction());
        game.removeStoredValue(INDUSTRIALIZATION_NORMAL_USED + player.getFaction());
        game.removeStoredValue(INTRIGUE_DISCARDS_REMAINING + player.getFaction());
        game.removeStoredValue(INTRIGUE_GIVEN + player.getFaction());
        for (Player target : game.getRealPlayersExcludingThis(player)) {
            game.removeStoredValue(NEGOTIATION_PLACED + player.getFaction() + "_" + target.getFaction());
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " purged "
                        + Mapper.getTomeCard(activeTome).getNameRepresentation() + ".");
    }

    private static int getIndustrializationUses(Game game, Player player) {
        try {
            return Integer.parseInt(game.getStoredValue(INDUSTRIALIZATION_USES + player.getFaction()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int getIntrigueDiscardsRemaining(Game game, Player player) {
        try {
            return Integer.parseInt(game.getStoredValue(INTRIGUE_DISCARDS_REMAINING + player.getFaction()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void reduceCommandTokenLimit(Game game, Player player) {
        int relicBonus = player.hasRelic("endurance_steroids") ? 2 : 0;
        int reducedBaseLimit = player.getCommandTokenLimit() - relicBonus - 1;
        game.setStoredValue("ccLimit" + player.getColor(), Integer.toString(reducedBaseLimit));
    }
}
