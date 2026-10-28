package ti4.discord.interactions.buttons.handlers.explore.thirtyk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.FoWHelper;
import ti4.helpers.thirtyk.ThirtyKCards;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.PublicObjectiveModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.tech.ListTechService;

@UtilityClass
public class ThirtyKPlayAreaExploreHandler {

    public static final String RICH_HOSTAGES = "richhostages30k";
    public static final String MACGUFFIAN_OUTPOST = "macguffianoutpostjmsc";

    private static final String USE_CARD = "thirtyKPlayAreaUse_";
    private static final String PURGE_FOR_TG = "thirtyKPlayAreaPurge_";
    private static final String RESEARCH = "thirtyKPlayAreaResearch_";
    private static final String GAIN_TECH = "thirtyKPlayAreaGainTech_";
    private static final String RICH_HOSTAGES_PURGE = "thirtyKRichHostagesPurge_";
    private static final String REFUND = "r";
    private static final String NO_REFUND = "n";

    private static final Map<String, PlayAreaCard> START_OF_TURN_CARDS = Map.of(
            "subjectivityequasion30k", PlayAreaCard.research(TechnologyType.PROPULSION),
            "destructiveelements30k", PlayAreaCard.research(TechnologyType.WARFARE),
            "ancientbiologicalspecimens30k", PlayAreaCard.research(TechnologyType.BIOTIC),
            "hyperalgorithms30k", PlayAreaCard.research(TechnologyType.CYBERNETIC),
            "ancientexcavators30k", PlayAreaCard.gain("sdn", 2),
            "dauntingweaponsschematics30k", PlayAreaCard.gain("dn2", 5));

    private record PlayAreaCard(int purgeTradeGoods, int cost, TechnologyType researchType, String techToGain) {
        static PlayAreaCard research(TechnologyType type) {
            return new PlayAreaCard(3, 5, type, null);
        }

        static PlayAreaCard gain(String techID, int cost) {
            return new PlayAreaCard(1, cost, null, techID);
        }
    }

    public static void moveToPlayArea(GenericInteractionCreateEvent event, Game game, Player player, String drawnID) {
        game.purgeExplore(drawnID);
        String baseId = ThirtyKCards.baseId(drawnID);
        player.addRelic(baseId);
        MessageHelper.sendMessageToEventChannel(
                event,
                "The find is crated, catalogued and kept close: _"
                        + Mapper.getRelic(baseId).getName() + "_ now sits face up in your play area.");
    }

    public static List<Button> getStartOfTurnButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String cardID : START_OF_TURN_CARDS.keySet()) {
            if (!player.hasRelic(cardID)) continue;
            RelicModel card = Mapper.getRelic(cardID);
            buttons.add(Buttons.green(player.factionButtonChecker() + USE_CARD + cardID, "Use " + card.getName()));
        }
        return buttons;
    }

    @ButtonHandler(USE_CARD)
    public static void useCard(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String cardID = buttonID.substring(USE_CARD.length());
        PlayAreaCard card = START_OF_TURN_CARDS.get(cardID);
        if (card == null || !player.hasRelic(cardID)) return;
        String name = Mapper.getRelic(cardID).getName();
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + PURGE_FOR_TG + cardID,
                "Purge For " + card.purgeTradeGoods() + " Trade Good" + (card.purgeTradeGoods() == 1 ? "" : "s")));
        if (card.techToGain() == null) {
            buttons.add(Buttons.blue(
                    player.factionButtonChecker() + RESEARCH + cardID,
                    "Spend " + card.cost() + " Resources To Research"));
        } else if (!player.hasTech(techToGain(player, card))) {
            buttons.add(Buttons.blue(
                    player.factionButtonChecker() + GAIN_TECH + cardID,
                    "Spend " + card.cost() + " Resources For "
                            + Mapper.getTech(techToGain(player, card)).getName()));
        }
        buttons.add(ThirtyKExploreSupport.decline(player));
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), player.getRepresentation() + ", choose how to use _" + name + "_.", buttons);
    }

    @ButtonHandler(PURGE_FOR_TG)
    public static void purgeForTradeGoods(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String cardID = buttonID.substring(PURGE_FOR_TG.length());
        PlayAreaCard card = START_OF_TURN_CARDS.get(cardID);
        if (card == null || !player.hasRelic(cardID)) return;
        purgeCard(event, player, cardID);
        ButtonHelperStats.gainTGs(event, game, player, card.purgeTradeGoods(), false);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(RESEARCH)
    public static void research(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String cardID = buttonID.substring(RESEARCH.length());
        PlayAreaCard card = START_OF_TURN_CARDS.get(cardID);
        if (card == null || card.researchType() == null || !player.hasRelic(cardID)) return;
        List<TechnologyModel> techs =
                ListTechService.getAllTechOfAType(game, card.researchType().toString(), player, false, true);
        ButtonHelper.deleteMessage(event);
        if (techs.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You meet the prerequisites for no technology of that colour.");
            return;
        }
        purgeCard(event, player, cardID);
        sendResourceSpendButtons(
                event, game, player, card.cost(), Mapper.getRelic(cardID).getName());
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose the technology to research.",
                ListTechService.getTechButtons(techs, player, "nopay"));
    }

    @ButtonHandler(GAIN_TECH)
    public static void gainTech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String cardID = buttonID.substring(GAIN_TECH.length());
        PlayAreaCard card = START_OF_TURN_CARDS.get(cardID);
        if (card == null || card.techToGain() == null || !player.hasRelic(cardID)) return;
        ButtonHelper.deleteMessage(event);
        purgeCard(event, player, cardID);
        sendResourceSpendButtons(
                event, game, player, card.cost(), Mapper.getRelic(cardID).getName());
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", gain the technology once you have paid.",
                List.of(Buttons.green(
                        player.factionButtonChecker() + "getTech_" + techToGain(player, card) + "__noPay__comp",
                        "Gain " + Mapper.getTech(techToGain(player, card)).getName())));
    }

    private static String techToGain(Player player, PlayAreaCard card) {
        TechnologyModel generic = Mapper.getTech(card.techToGain());
        if (generic == null || !generic.isUnitUpgrade()) return card.techToGain();
        List<String> factionTechs = new ArrayList<>(player.getNotResearchedFactionTechs());
        factionTechs.addAll(player.getTechs());
        return factionTechs.stream()
                .map(Mapper::getTech)
                .filter(tech -> tech != null
                        && tech.isUnitUpgrade()
                        && card.techToGain()
                                .equalsIgnoreCase(tech.getBaseUpgrade().orElse("")))
                .map(TechnologyModel::getAlias)
                .findFirst()
                .orElse(card.techToGain());
    }

    private static void purgeCard(GenericInteractionCreateEvent event, Player player, String cardID) {
        player.removeRelic(cardID);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " puts the last of _"
                        + Mapper.getRelic(cardID).getName() + "_ to use, and the card is purged.");
    }

    private static void sendResourceSpendButtons(
            GenericInteractionCreateEvent event, Game game, Player player, int cost, String cardName) {
        player.resetSpentThings();
        List<Button> buttons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, "res"));
        buttons.add(Buttons.DONE_EXHAUSTING_PLANETS_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please spend " + cost + " resources for _" + cardName + "_.",
                buttons);
    }

    public static void offerRichHostages(Game game, Player player, String objectiveID, boolean tradeGoodsSpent) {
        if (!player.hasRelic(RICH_HOSTAGES)) return;
        PublicObjectiveModel objective = Mapper.getPublicObjective(objectiveID);
        if (objective == null || objective.getPoints() == null || objective.getPoints() != 1) return;
        if (objective.getText() == null || !objective.getText().toLowerCase().contains("spend")) return;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", you may purge _Rich Hostages_ to reduce the cost of _"
                        + objective.getName() + "_ by 3.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker()
                                        + RICH_HOSTAGES_PURGE
                                        + (tradeGoodsSpent ? REFUND : NO_REFUND)
                                        + objectiveID,
                                "Purge Rich Hostages (-3 Cost)"),
                        ThirtyKExploreSupport.decline(player)));
    }

    @ButtonHandler(RICH_HOSTAGES_PURGE)
    public static void richHostagesPurge(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasRelic(RICH_HOSTAGES)) return;
        String payload = buttonID.substring(RICH_HOSTAGES_PURGE.length());
        boolean encoded = (payload.startsWith(REFUND) || payload.startsWith(NO_REFUND))
                && Mapper.getPublicObjective(payload.substring(1)) != null;
        boolean refund = encoded && payload.startsWith(REFUND);
        String objectiveID = encoded ? payload.substring(1) : payload;
        player.removeRelic(RICH_HOSTAGES);
        PublicObjectiveModel objective = Mapper.getPublicObjective(objectiveID);
        String name = objective == null ? objectiveID : objective.getName();
        if (refund) {
            ButtonHelperStats.gainTGs(event, game, player, 3, false);
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "The hostages go home for a handsome fee: " + player.getRepresentationNoPing()
                        + " purges _Rich Hostages_ and _" + name
                        + "_ costs 3 less (ready anything you over-spent).");
        ButtonHelper.deleteMessage(event);
    }

    public static void checkMacGuffianOutpost(
            GenericInteractionCreateEvent event, Game game, Player loser, Tile tile, String assignHitsType) {
        if (tile == null || loser == null || !"spacecombat".equalsIgnoreCase(assignHitsType)) return;
        if (!loser.hasRelic(MACGUFFIAN_OUTPOST) || FoWHelper.playerHasActualShipsInSystem(loser, tile)) return;
        List<Player> remaining = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile).stream()
                .filter(other -> other != loser && FoWHelper.playerHasActualShipsInSystem(other, tile))
                .toList();
        if (remaining.size() != 1) return;
        String key = "thirtyKMacGuffian" + loser.getFaction();
        String marker = game.getRound() + "_" + tile.getPosition() + "_" + game.getActivePlayerID();
        if (marker.equals(game.getStoredValue(key))) return;
        game.setStoredValue(key, marker);
        Player winner = remaining.getFirst();
        ButtonHelperStats.gainTGs(event, game, loser, 2, false);
        if (winner.isNeutral()) {
            MessageHelper.sendMessageToChannel(
                    loser.getCorrectChannel(),
                    "Your crews salvage the wreckage of the _MacGuffin Outpost_ convoy: "
                            + loser.getRepresentationNoPing() + " gains 2 trade goods. The neutral victors gain"
                            + " nothing.");
            return;
        }
        ButtonHelperStats.gainTGs(event, game, winner, 2, false);
        FoWHelper.notifyPlayerAndAffectedInFog(
                game,
                loser,
                "Victor and vanquished alike pick through the wreckage of the _MacGuffin Outpost_ convoy: "
                        + loser.getRepresentationNoPing() + " and their opponent each gain 2 trade goods.",
                winner,
                winner.getRepresentationUnfogged()
                        + ", your salvage crews find a share of your opponent's _MacGuffin Outpost_ cargo: you gain 2"
                        + " trade goods.");
    }
}
