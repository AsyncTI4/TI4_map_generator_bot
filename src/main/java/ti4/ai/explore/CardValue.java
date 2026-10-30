package ti4.ai.explore;

import java.util.List;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.PlanetStake;
import ti4.ai.eval.RelicValue;
import ti4.ai.eval.ValueScale;
import ti4.ai.legendary.LegendaryPlanets;
import ti4.ai.secrets.SecretDrawValue;
import ti4.ai.strategy.ReadyingValue;
import ti4.ai.trade.ComponentValues;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.model.PlanetModel;

@UtilityClass
public class CardValue {

    private static final Pattern EXTRA_COPY = Pattern.compile("extra\\d$");
    private static final Pattern CARD_NUMBER = Pattern.compile("\\d+$");
    private static final String PRE_FAB_ARCOLOGIES = "pfa";
    private static final String MIRAGE = "mirage";
    private static final int ABANDONED_WAREHOUSES_COMMODITIES = 2;
    private static final int FUNCTIONING_BASE_COMMODITIES = 1;

    public static String kind(String cardId) {
        return CARD_NUMBER.matcher(EXTRA_COPY.matcher(cardId).replaceAll("")).replaceAll("");
    }

    public static double of(String cardId, ExploreSite site) {
        Game game = site.game();
        Player seat = site.seat();
        return switch (kind(cardId)) {
            case "crf" -> ComponentValues.gainedFragment(seat, "CRF");
            case "irf" -> ComponentValues.gainedFragment(seat, "IRF");
            case "hrf" -> ComponentValues.gainedFragment(seat, "HRF");
            case "urf" -> ComponentValues.gainedFragment(seat, "URF");
            case "vfs" -> site.viaMechOrInfantry(ExploreGains.commandToken(game, seat));
            case "exp" -> site.viaMechOrInfantry(readyValue(site));
            case "cm" -> site.viaMechOrInfantry(ExploreGains.tradeGood(game, seat));
            case "frln" -> site.outlook().canFundFreelancers() ? ExploreValues.FREELANCERS : 0;
            case "mo" -> ExploreValues.MERCENARY_OUTFIT;
            case "lf" -> Math.max(mechOption(site), gainedCommodity(game, seat));
            case "fb" -> Math.max(actionCardOption(game, seat), gainedCommodity(game, seat));
            case "aw" -> Math.max(convertedWarehouses(game, seat), gainedWarehouses(game, seat));
            case "ms" -> Math.max(convertedMerchants(game, seat), replenishedMerchants(game, seat));
            case "lc" -> ExploreValues.actionCardsFromLostCrew(game, seat);
            case "dv" -> SecretDrawValue.of(seat.getGame(), seat);
            case "dw" -> RelicValue.draw(game, seat);
            case "ed" -> ExploreValues.ENIGMATIC_DEVICE;
            case "mirage" -> mirage();
            case "dmz" -> ExploreValues.DEMILITARIZED_ZONE - PlanetStake.structures(site.holder(), seat);
            case "ds" -> attachment(site, 2, 1);
            case "pw" -> attachment(site, 0, 2);
            case "ls" -> attachment(site, 1, 2);
            case "mw" -> attachment(site, 2, 0);
            case "rw" -> attachment(site, 1, 0);
            case "toe" -> attachment(site, 0, 1);
            case "warfare", "biotic", "cybernetic", "propulsion" -> researchFacility(site);
            default -> 0;
        };
    }

    public static double readyValue(ExploreSite site) {
        Player seat = site.seat();
        String planet = site.planet();
        boolean alreadyReady = seat.getPlanets().contains(planet)
                && !seat.getExhaustedPlanets().contains(planet);
        if (seat.hasTech(PRE_FAB_ARCOLOGIES) || alreadyReady) return 0;
        return ReadyingValue.bestSingle(site.game(), seat, List.of(planet))
                .map(ReadyingValue.Pick::value)
                .orElse(0.0);
    }

    public static boolean canPlaceMech(ExploreSite site) {
        return !site.game().isBaseGameMode()
                && !site.hasDemilitarizedZone()
                && ExploreValues.hasRoomForUnit(site.game(), site.seat(), UnitType.Mech)
                && ExploreValues.canPayCommodityOrTradeGood(site.seat());
    }

    public static double mechOption(ExploreSite site) {
        if (!canPlaceMech(site)) return 0;
        double garrison = site.infantry() + site.mechs() == 0 ? ExploreValues.MECH_GARRISON : 0;
        return ExploreValues.MECH + garrison - ExploreValues.commodityOrTradeGoodCost(site.game(), site.seat());
    }

    public static double actionCardOption(Game game, Player seat) {
        if (!ExploreValues.canPayCommodityOrTradeGood(seat)) return 0;
        return ExploreValues.drawnActionCards(game, seat, 1) - ExploreValues.commodityOrTradeGoodCost(game, seat);
    }

    public static double gainedCommodity(Game game, Player seat) {
        return ExploreValues.gainedCommodities(game, seat, FUNCTIONING_BASE_COMMODITIES);
    }

    public static double convertedWarehouses(Game game, Player seat) {
        return ExploreValues.convertedCommodities(game, seat, ABANDONED_WAREHOUSES_COMMODITIES);
    }

    public static double gainedWarehouses(Game game, Player seat) {
        return ExploreValues.gainedCommodities(game, seat, ABANDONED_WAREHOUSES_COMMODITIES);
    }

    public static double convertedMerchants(Game game, Player seat) {
        return ExploreValues.convertedCommodities(game, seat, seat.getCommodities());
    }

    public static double replenishedMerchants(Game game, Player seat) {
        return ExploreValues.gainedCommodities(game, seat, ExploreValues.commodityRoom(seat));
    }

    private static double mirage() {
        PlanetModel model = Mapper.getPlanet(MIRAGE);
        if (model == null) return 0;
        double value = ValueScale.spendable(model.getResources(), model.getInfluence());
        return model.isLegendary() ? value + LegendaryPlanets.holdingValue(MIRAGE) : value;
    }

    private static double attachment(ExploreSite site, int resources, int influence) {
        return attachment(resources, influence) + site.outlook().attachmentObjectiveGain(site.holder());
    }

    private static double attachment(int resources, int influence) {
        return ValueScale.spendable(resources, influence);
    }

    private static double researchFacility(ExploreSite site) {
        Planet planet = site.holder();
        double objectives = site.outlook().attachmentObjectiveGain(planet);
        if (planet.getTechSpecialities().isEmpty()) {
            return BoardView.TECH_SPECIALTY_VALUE + objectives + site.outlook().specialtyObjectiveGain(planet);
        }
        return attachment(1, 1) + objectives;
    }
}
