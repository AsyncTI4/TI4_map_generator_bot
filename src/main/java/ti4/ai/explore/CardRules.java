package ti4.ai.explore;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class CardRules {

    private static final String DECLINE = "decline_explore";
    private static final String UNDO_PREFIX = "ultimateUndo_";
    private static final String VOLATILE = "resolveVolatile";
    private static final String EXPEDITION = "resolveExpedition";
    private static final String CORE_MINE = "resolveCoreMine";
    private static final String LOCAL_FABRICATORS = "resolveLocalFab_";
    private static final String GAIN_ONE_COMMODITY = "gain_1_comms";
    private static final String COMMODITY_FOR_ACTION_CARD = "comm_for_AC";
    private static final String CONVERT_WAREHOUSES = "convert_2_comms";
    private static final String GAIN_WAREHOUSES = "gain_2_comms";
    private static final String CONVERT_MERCHANTS = "mallice_convert_comm";
    private static final String REPLENISH_MERCHANTS = "resolveHarness";
    private static final String ION_STORM = "addIonStorm_";
    private static final String MECH_SUFFIX = "Mech_";
    private static final String INFANTRY_SUFFIX = "Inf_";

    static Optional<AiDecision> next(AiTurnContext context, List<AiPrompt> prompts) {
        ExploreOutlook outlook = new ExploreOutlook(context.game(), context.seat());
        for (AiPrompt prompt : prompts) {
            if (!ExploreWindow.untouched(context, prompt)) continue;
            Optional<AiDecision> decision = decide(context, prompt, outlook);
            if (decision.isPresent()) return decision;
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> decide(AiTurnContext context, AiPrompt prompt, ExploreOutlook outlook) {
        if (prompt.firstEnabled(button -> button.isOwnedBy(context.faction())).isPresent()) {
            return merchantStation(context, prompt);
        }
        if (!ExploreWindow.isOwn(context, prompt)) return Optional.empty();
        return unitCard(context, prompt, outlook)
                .or(() -> commodityCard(context, prompt, outlook))
                .or(() -> ionStorm(context, prompt))
                .or(() -> onlyDeclineLeft(prompt));
    }

    private static Optional<AiDecision> onlyDeclineLeft(AiPrompt prompt) {
        boolean nothingElse = prompt.enabledButtons().stream()
                .allMatch(button ->
                        DECLINE.equals(button.handlerId()) || button.handlerId().startsWith(UNDO_PREFIX));
        if (!nothingElse) return Optional.empty();
        return prompt.enabledHandler(DECLINE)
                .map(decline -> AiDecision.press(prompt, decline, "decline an exploration card it cannot use"));
    }

    private static Optional<AiDecision> unitCard(AiTurnContext context, AiPrompt prompt, ExploreOutlook outlook) {
        if (has(prompt, VOLATILE)) {
            double token = ExploreValues.tokenValue(context.game(), context.seat());
            return viaUnit(context, prompt, outlook, VOLATILE, token);
        }
        if (has(prompt, EXPEDITION)) {
            return viaUnit(context, prompt, outlook, EXPEDITION, readyValue(context, prompt, outlook));
        }
        if (has(prompt, CORE_MINE)) return viaUnit(context, prompt, outlook, CORE_MINE, ExploreValues.TRADE_GOOD);
        return Optional.empty();
    }

    private static Optional<AiDecision> commodityCard(AiTurnContext context, AiPrompt prompt, ExploreOutlook outlook) {
        if (has(prompt, LOCAL_FABRICATORS)) return localFabricators(context, prompt, outlook);
        if (hasExactly(prompt, COMMODITY_FOR_ACTION_CARD)) return functioningBase(context, prompt);
        if (hasExactly(prompt, CONVERT_WAREHOUSES) || hasExactly(prompt, GAIN_WAREHOUSES)) {
            return abandonedWarehouses(context, prompt);
        }
        return Optional.empty();
    }

    private static boolean has(AiPrompt prompt, String handlerPrefix) {
        return prompt.firstEnabled(
                        button -> button.isUnowned() && button.handlerId().startsWith(handlerPrefix))
                .isPresent();
    }

    private static boolean hasExactly(AiPrompt prompt, String handler) {
        return prompt.firstEnabled(
                        button -> button.isUnowned() && button.handlerId().equals(handler))
                .isPresent();
    }

    private static Optional<AiDecision> viaUnit(
            AiTurnContext context, AiPrompt prompt, ExploreOutlook outlook, String family, double benefit) {
        Optional<PromptButton> mech = prompt.firstEnabled(
                button -> button.isUnowned() && button.handlerId().startsWith(family + MECH_SUFFIX));
        Optional<PromptButton> infantry = prompt.firstEnabled(
                button -> button.isUnowned() && button.handlerId().startsWith(family + INFANTRY_SUFFIX));
        Optional<String> planet =
                mech.or(() -> infantry).map(button -> StringUtils.substringAfter(button.handlerId(), "_"));
        Optional<ExploreSite> site = planet.flatMap(name -> siteOf(context, name, outlook));
        if (site.isEmpty()) return Optional.empty();
        if (mech.isPresent() && site.get().mechIsWorthIt(benefit)) {
            return Optional.of(AiDecision.press(prompt, mech.get(), "resolve the exploration card with a mech"));
        }
        if (infantry.isPresent() && site.get().infantryIsWorthIt(benefit)) {
            return Optional.of(
                    AiDecision.press(prompt, infantry.get(), "resolve the exploration card with an infantry"));
        }
        return prompt.enabledHandler(DECLINE)
                .map(decline ->
                        AiDecision.press(prompt, decline, "decline an exploration card that costs more than it gives"));
    }

    private static double readyValue(AiTurnContext context, AiPrompt prompt, ExploreOutlook outlook) {
        return prompt.firstEnabled(
                        button -> button.isUnowned() && button.handlerId().startsWith(EXPEDITION))
                .map(button -> StringUtils.substringAfter(button.handlerId(), "_"))
                .flatMap(planet -> siteOf(context, planet, outlook))
                .map(CardValue::readyValue)
                .orElse(0.0);
    }

    private static Optional<AiDecision> localFabricators(
            AiTurnContext context, AiPrompt prompt, ExploreOutlook outlook) {
        Optional<PromptButton> mech = prompt.firstEnabled(
                button -> button.isUnowned() && button.handlerId().startsWith(LOCAL_FABRICATORS));
        Optional<ExploreSite> site = mech.flatMap(
                button -> siteOf(context, StringUtils.removeStart(button.handlerId(), LOCAL_FABRICATORS), outlook));
        if (site.isEmpty()) return Optional.empty();
        double mechValue = CardValue.mechOption(site.get());
        double commodityValue = CardValue.gainedCommodity(context.game(), context.seat());
        if (mechValue > commodityValue && mechValue > 0) {
            return Optional.of(AiDecision.press(prompt, mech.get(), "place a mech with Local Fabricators"));
        }
        return press(prompt, GAIN_ONE_COMMODITY, "gain a commodity from Local Fabricators");
    }

    private static Optional<AiDecision> functioningBase(AiTurnContext context, AiPrompt prompt) {
        Game game = context.game();
        Player seat = context.seat();
        double actionCard = CardValue.actionCardOption(game, seat);
        double commodity = CardValue.gainedCommodity(game, seat);
        if (actionCard > commodity && actionCard > 0) {
            return prompt.enabledHandler(COMMODITY_FOR_ACTION_CARD)
                    .map(button -> AiDecision.press(prompt, button, "draw an action card with Functioning Base"));
        }
        return press(prompt, GAIN_ONE_COMMODITY, "gain a commodity from Functioning Base");
    }

    private static Optional<AiDecision> abandonedWarehouses(AiTurnContext context, AiPrompt prompt) {
        double convert = CardValue.convertedWarehouses(context.game(), context.seat());
        double gain = CardValue.gainedWarehouses(context.game(), context.seat());
        if (convert > gain && convert > 0) {
            return press(prompt, CONVERT_WAREHOUSES, "convert commodities with Abandoned Warehouses");
        }
        return press(prompt, GAIN_WAREHOUSES, "gain commodities from Abandoned Warehouses");
    }

    private static Optional<AiDecision> merchantStation(AiTurnContext context, AiPrompt prompt) {
        Optional<PromptButton> convert = prompt.firstEnabled(
                button -> button.isOwnedBy(context.faction()) && CONVERT_MERCHANTS.equals(button.handlerId()));
        Optional<PromptButton> replenish = prompt.firstEnabled(
                button -> button.isOwnedBy(context.faction()) && REPLENISH_MERCHANTS.equals(button.handlerId()));
        if (convert.isEmpty() || replenish.isEmpty()) return Optional.empty();
        double convertValue = CardValue.convertedMerchants(context.game(), context.seat());
        double replenishValue = CardValue.replenishedMerchants(context.game(), context.seat());
        if (convertValue > replenishValue && convertValue > 0) {
            return Optional.of(AiDecision.press(prompt, convert.get(), "convert commodities at the Merchant Station"));
        }
        return Optional.of(AiDecision.press(prompt, replenish.get(), "replenish commodities at the Merchant Station"));
    }

    private static Optional<AiDecision> ionStorm(AiTurnContext context, AiPrompt prompt) {
        Optional<PromptButton> any = prompt.firstEnabled(
                button -> button.isUnowned() && button.handlerId().startsWith(ION_STORM));
        if (any.isEmpty()) return Optional.empty();
        String position = StringUtils.substringAfterLast(any.get().handlerId(), "_");
        Tile storm = context.game().getTileByPosition(position);
        if (storm == null) return Optional.empty();
        String side = IonStormRules.sideFor(context.game(), context.seat(), storm);
        return prompt.enabledHandler(ION_STORM + side + "_" + position)
                .map(button -> AiDecision.press(prompt, button, "place the Ion Storm on its " + side + " side"));
    }

    private static Optional<AiDecision> press(AiPrompt prompt, String handler, String reason) {
        return prompt.enabledHandler(handler).map(button -> AiDecision.press(prompt, button, reason));
    }

    private static Optional<ExploreSite> siteOf(AiTurnContext context, String planet, ExploreOutlook outlook) {
        Game game = context.game();
        if (game.getTileFromPlanet(planet) == null || game.getUnitHolderFromPlanet(planet) == null) {
            return Optional.empty();
        }
        return Optional.of(ExploreSite.onBoard(game, context.seat(), planet, outlook));
    }
}
