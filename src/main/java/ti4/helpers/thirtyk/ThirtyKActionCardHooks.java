package ti4.helpers.thirtyk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.actioncards.theodisi.TransitRiderLLButtonHandler;
import ti4.discord.interactions.buttons.handlers.actioncards.thirtyk.ThirtyKPoliticsAcButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.CombatModifierModel;
import ti4.model.Mapper;
import ti4.model.NamedCombatModifierModel;
import ti4.service.combat.CombatRollType;
import ti4.service.objectives.DrawSecretService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class ThirtyKActionCardHooks {

    static final String CONFLAGRATION = "conflagration30k";
    static final String PRESERVATION_ACT = "preservationact30k";
    static final String ANTIDISESTABLISHMENTARIANISM = "antidisestablishmentarianismjmsc";
    static final String FINE_PRINT = "hidewithinthefineprint30k";
    static final String OBJECTION_GAMBIT = "objectiongambit30k";

    public static void onRiderPlaced(Game game, Player player, String outcome, String rider) {
        if (!ThirtyKCards.inUse(game)) return;
        if (ThirtyKAgendaRiders.isCopyable(outcome, rider)) {
            offerRiderCopies(game, rider, outcome);
        }
        if (!ThirtyKAcState.playedRiderCard(game, player, rider)) return;
        if (ThirtyKAgendaRiders.PUBLIC_OUTCRY.equals(rider)) {
            String identifier = game.isFowMode() ? player.getColor() : player.getFaction();
            game.setCurrentAgendaVote(
                    outcome,
                    ThirtyKAgendaRiders.renameEntry(
                            game.getCurrentAgendaVotes().get(outcome),
                            identifier + "_" + ThirtyKAgendaRiders.PUBLIC_OUTCRY,
                            identifier + "_" + ThirtyKAgendaRiders.PUBLIC_OUTCRY_RIDER));
        }
        if (ThirtyKAgendaRiders.forbidsVoting(rider)) {
            TransitRiderLLButtonHandler.registerPrediction(game, player);
        }
        if (ThirtyKAgendaRiders.DUMMY_RIDER.equals(rider)) {
            ThirtyKAcState.setDummyRider(game, player, outcome);
        }
    }

    private static void offerRiderCopies(Game game, String rider, String outcome) {
        for (Player holder : game.getRealPlayers()) {
            offerPlay(
                    holder,
                    FINE_PRINT,
                    "a _" + rider + "_ was placed on \"" + outcome + "\". You may play _Hide Within the Fine Print_"
                            + " to follow it on the same outcome.");
            offerPlay(
                    holder,
                    OBJECTION_GAMBIT,
                    "a _" + rider + "_ was placed on \"" + outcome + "\". You may play _Objection Gambit_"
                            + " to follow it on the opposing outcome.");
        }
    }

    public static void onAgendaVotesReset(Game game) {
        if (!ThirtyKCards.inUse(game)) return;
        ThirtyKAcState.clearDummyRider(game);
        ThirtyKAcState.clearRiderCards(game);
    }

    public static void resolveAgendaPredictions(Game game, String winner, GenericInteractionCreateEvent event) {
        if (!ThirtyKCards.inUse(game)) return;
        for (ThirtyKAgendaRiders.RiderEntry entry : ThirtyKAgendaRiders.riderEntries(game.getCurrentAgendaVotes())) {
            Player predictor =
                    game.getPlayerFromColorOrFaction(entry.identifier().toLowerCase());
            if (predictor == null || !ThirtyKAcState.playedRiderCard(game, predictor, entry.rider())) continue;
            boolean correct = entry.outcome().equalsIgnoreCase(winner);
            if (ThirtyKAgendaRiders.EMBASSY_RIDER.equals(entry.rider()) && correct) {
                MessageHelper.sendMessageToChannel(
                        predictor.getCorrectChannel(),
                        predictor.getRepresentationUnfogged()
                                + ", your _Embassy Rider_ prediction was correct; grateful friends in the embassy"
                                + " slip you a secret objective.");
                DrawSecretService.drawSO(null, game, predictor);
            }
            if (ThirtyKAgendaRiders.DUMMY_RIDER.equals(entry.rider()) && correct) {
                ActionCardHelper.drawActionCards(predictor, 1);
                MessageHelper.sendMessageToChannel(
                        predictor.getCorrectChannel(),
                        predictor.getRepresentationUnfogged()
                                + ", your _Dummy Rider_ prediction was correct; the shell motion pays out 1 action"
                                + " card.");
            }
            if (ThirtyKAgendaRiders.DUMMY_RIDER.equals(entry.rider()) && !correct) {
                String card = ThirtyKAcState.takeWrongDummyRiderCard(game, predictor, entry.outcome());
                if (card != null) returnToHand(game, predictor, card);
            }
        }
        ThirtyKAcState.clearDummyRider(game);
        ThirtyKAcState.clearRiderCards(game);
    }

    private static void returnToHand(Game game, Player player, String baseId) {
        Optional<Map.Entry<String, Integer>> card = game.getDiscardActionCards().entrySet().stream()
                .filter(e -> baseId.equals(ThirtyKCards.baseId(e.getKey())))
                .findFirst();
        if (card.isEmpty()
                || !game.pickActionCard(player.getUserID(), card.get().getValue())) return;
        ActionCardHelper.sendActionCardInfo(game, player);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", the _Dummy Rider_ prediction failed, but the paperwork was never filed: _"
                        + Mapper.getActionCard(card.get().getKey()).getName() + "_ returns to your hand.");
    }

    public static void onPlanetControlChanged(Game game, Player newOwner, Player previousOwner, String planet) {
        if (!ThirtyKCards.inUse(game)) return;
        if (previousOwner == null || previousOwner == newOwner || !previousOwner.isRealPlayer()) return;
        if (!holdsActionCard(previousOwner, CONFLAGRATION)) return;
        ThirtyKAcState.setPlanetLoss(game, previousOwner, planet, newOwner);
        String planetName = Mapper.getPlanet(planet) == null
                ? planet
                : Mapper.getPlanet(planet).getName();
        offerPlay(
                previousOwner,
                CONFLAGRATION,
                "another player gained control of " + planetName
                        + ". You may play _Conflagration_ to destroy all but 1 of their infantry there.");
    }

    public static void onStrategyPhaseStart(GenericInteractionCreateEvent event, Game game) {
        if (!ThirtyKCards.inUse(game)) return;
        ThirtyKTurnState.clearActionPhase(game);
        for (Player player : game.getRealPlayers()) {
            int payout = ThirtyKAcState.collectCompoundInterest(game, player);
            if (payout <= 0) continue;
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", your _Compound Interest_ matures and the brokers pay out.");
            ButtonHelperStats.gainTGs(event, game, player, payout, false);
        }
    }

    public static void onTransactionAccepted(Game game, Player p1, Player p2) {
        if (!ThirtyKCards.inUse(game)) return;
        if (ThirtyKAcState.takeExtendedCampaigning(game, p1, p2)) {
            ThirtyKPoliticsAcButtonHandler.offerAgentReady(game, p2);
        }
        if (ThirtyKAcState.takeExtendedCampaigning(game, p2, p1)) {
            ThirtyKPoliticsAcButtonHandler.offerAgentReady(game, p1);
        }
    }

    // TODO: when both fleets die, the backed player may be paid before the other side assigns its lethal hits.
    public static void afterCombatHitsAssigned(Game game, Tile tile, String assignHitsType) {
        if (!ThirtyKCards.inUse(game)) return;
        if (tile == null || !"spacecombat".equalsIgnoreCase(assignHitsType)) return;
        List<String> remaining = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile).stream()
                .map(Player::getFaction)
                .toList();
        for (Player profiteer : game.getRealPlayers()) {
            ThirtyKAcState.WarProfiteering bet = ThirtyKAcState.warProfiteering(game, profiteer);
            if (bet == null || !bet.position().equals(tile.getPosition())) continue;
            ThirtyKAcState.ProfiteeringResult result =
                    ThirtyKAcState.profiteeringResult(bet.targetFaction(), remaining);
            if (result == ThirtyKAcState.ProfiteeringResult.PENDING) continue;
            ThirtyKAcState.clearWarProfiteering(game, profiteer);
            if (result == ThirtyKAcState.ProfiteeringResult.LOST) {
                MessageHelper.sendMessageToChannel(
                        profiteer.getCorrectChannel(),
                        profiteer.getRepresentationUnfogged()
                                + ", the side you bankrolled lost the space combat; your _War Profiteering_ bet is"
                                + " worthless.");
                continue;
            }
            MessageHelper.sendMessageToChannel(
                    profiteer.getCorrectChannel(),
                    profiteer.getRepresentationUnfogged()
                            + ", the side you bankrolled won the space combat; _War Profiteering_ pays out 4 trade"
                            + " goods.");
            ButtonHelperStats.gainTGs(null, game, profiteer, 4, false);
        }
    }

    public static void onGroundCombatStart(Game game, UnitHolder planet) {
        if (!ThirtyKCards.inUse(game) || planet == null) return;
        ThirtyKAcState.clearFinalStandOn(game, planet.getName());
    }

    public static List<NamedCombatModifierModel> finalStandExtraRolls(
            Game game, Player player, UnitHolder combatOnHolder, CombatRollType rollType) {
        if (!ThirtyKCards.inUse(game)) return List.of();
        if (rollType != CombatRollType.combatround || !(combatOnHolder instanceof Planet planet)) return List.of();
        if (!holdsFinalStand(game, player, planet)) return List.of();
        int dice = Math.max(0, planet.getInfluence());
        if (dice == 0) return List.of();
        CombatModifierModel modifier = new CombatModifierModel();
        modifier.setAlias("thirtyk_final_stand");
        modifier.setType(Constants.COMBAT_EXTRA_ROLLS);
        modifier.setValue(dice);
        modifier.setPersistenceType("ALWAYS");
        modifier.setScope("gf");
        modifier.setRelated(List.of());
        modifier.setForCombatAbility(CombatRollType.combatround);
        return List.of(new NamedCombatModifierModel(
                modifier, "_Final Stand_: +" + dice + " dice from the influence of the planet"));
    }

    static boolean holdsFinalStand(Game game, Player player, Planet planet) {
        if (!ThirtyKAcState.hasFinalStand(game, player, planet.getName())) return false;
        if (!player.getPlanets().contains(planet.getName())) return false;
        return planet.getUnitCount(UnitType.Infantry, player) == 1
                && ButtonHelper.getNumberOfGroundForces(player, planet) == 1;
    }

    public static void onFragmentsPurged(Game game, Player purger, List<String> fragments) {
        if (!ThirtyKCards.inUse(game)) return;
        if (fragments == null || fragments.isEmpty()) return;
        List<Player> holders = holdersOf(game, PRESERVATION_ACT).stream()
                .filter(holder -> holder != purger && !holder.isNeighboursWith(purger))
                .toList();
        if (holders.isEmpty()) return;
        ThirtyKAcState.setPurgedFragments(game, purger, fragments);
        String reason = game.isFowMode()
                ? "relic fragments are being purged. You may play _Preservation Act_ to gain up to 2 of them."
                : "a player you are not neighbors with purged " + fragments.size()
                        + " relic fragment(s). You may play _Preservation Act_ to gain up to 2 of them.";
        for (Player holder : holders) {
            offerPlay(holder, PRESERVATION_ACT, reason);
        }
    }

    public static void afterUnitsDestroyed(GenericInteractionCreateEvent event, Game game, List<RemovedUnit> units) {
        if (!ThirtyKCards.inUse(game)) return;
        List<Player> holders = holdersOf(game, ANTIDISESTABLISHMENTARIANISM);
        if (holders.isEmpty()) return;
        for (RemovedUnit unit : units) {
            UnitType type = unit.unitKey().unitType();
            if (type != UnitType.Pds && type != UnitType.Spacedock) continue;
            if (unit.uh() == null || "space".equalsIgnoreCase(unit.uh().getName())) continue;
            Player loser = game.getPlayerFromColorOrFaction(unit.unitKey().colorID());
            if (loser == null || !loser.isRealPlayer()) continue;
            String position = unit.tile().getPosition();
            ThirtyKAcState.setStructureLost(game, loser, position);
            offerAntidisestablishmentarianism(game, holders, position);
            return;
        }
    }

    // TODO: in fog the prompt itself still tells holders that a far-away structure was destroyed.
    private static void offerAntidisestablishmentarianism(Game game, List<Player> holders, String position) {
        String reason = game.isFowMode()
                ? "you may play _Antidisestablishmentarianism_ now."
                : "a structure was destroyed on a planet more than 5 hexes from your home system."
                        + " You may play _Antidisestablishmentarianism_.";
        for (Player holder : holders) {
            if (!ThirtyKDistance.isMoreThanFiveFromHome(game, holder, position)) continue;
            offerPlay(holder, ANTIDISESTABLISHMENTARIANISM, reason);
        }
    }

    static boolean holdsActionCard(Player player, String baseId) {
        return player.getActionCards().keySet().stream().anyMatch(card -> baseId.equals(ThirtyKCards.baseId(card)));
    }

    private static List<Player> holdersOf(Game game, String baseId) {
        return game.getRealPlayers().stream()
                .filter(player -> holdsActionCard(player, baseId))
                .toList();
    }

    private static void offerPlay(Player holder, String baseId, String reason) {
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> card : holder.getActionCards().entrySet()) {
            if (!baseId.equals(ThirtyKCards.baseId(card.getKey()))) continue;
            ActionCardModel model = Mapper.getActionCard(card.getKey());
            buttons.add(Buttons.green(
                    Constants.AC_PLAY_FROM_HAND + card.getValue(),
                    "Play " + (model == null ? card.getKey() : model.getName())));
            break;
        }
        if (buttons.isEmpty()) return;
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                holder.getCardsInfoThread(), holder.getRepresentationUnfogged() + ", " + reason, buttons);
    }
}
