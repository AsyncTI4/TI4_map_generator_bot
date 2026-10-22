package ti4.helpers.thirtyk;

import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.buttons.handlers.explore.thirtyk.ThirtyKAttachmentHandler;
import ti4.discord.interactions.buttons.handlers.explore.thirtyk.ThirtyKPlayAreaExploreHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;

@UtilityClass
public class ThirtyKExploreHooks {

    public static void onPlanetExhausted(
            GenericInteractionCreateEvent event, Game game, Player player, String planetID) {
        if (!ThirtyKCards.inUse(game)) return;
        if (game == null || player == null || planetID == null) return;
        ThirtyKAttachmentHandler.onPlanetExhausted(event, game, player, planetID);
    }

    public static List<Button> getStartOfTurnButtons(Player player) {
        if (!ThirtyKCards.inUse(player.getGame())) return List.of();
        return ThirtyKPlayAreaExploreHandler.getStartOfTurnButtons(player);
    }

    public static void afterPublicObjectiveScored(Game game, Player player, String objectiveID, int tgBefore) {
        if (!ThirtyKCards.inUse(game)) return;
        ThirtyKPlayAreaExploreHandler.offerRichHostages(game, player, objectiveID, player.getTg() < tgBefore);
    }

    public static int spaceCannonRollBonus(Game game, CombatRollType rollType, UnitModel unit, UnitHolder holder) {
        if (!ThirtyKCards.inUse(game)) return 0;
        if (rollType != CombatRollType.SpaceCannonOffence && rollType != CombatRollType.SpaceCannonDefence) return 0;
        return ThirtyKAttachmentHandler.spaceCannonBonus(unit, holder);
    }

    public static String spaceCannonBonusNote(UnitModel unit, int bonus) {
        if (bonus <= 0) return "";
        return "Overcharged targeting arrays add +" + bonus + " to the " + unit.getName()
                + " rolls (_Overcharged Targeting System_).\n";
    }

    public static int extraPlanetProduction(Game game, UnitHolder holder, int unitProduction) {
        if (!ThirtyKCards.inUse(game)) return 0;
        return ThirtyKAttachmentHandler.extraPlanetProduction(holder, unitProduction);
    }

    public static int productionDiscount(Game game, Map<String, Integer> producedUnits) {
        if (!ThirtyKCards.inUse(game)) return 0;
        return ThirtyKAttachmentHandler.productionDiscount(game, producedUnits);
    }

    public static void afterProduction(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!ThirtyKCards.inUse(game)) return;
        ThirtyKAttachmentHandler.afterProduction(event, game, player);
    }

    public static void afterLanding(Game game, Player player, Tile tile, String planetID, UnitType type, int amount) {
        if (!ThirtyKCards.inUse(game)) return;
        ThirtyKAttachmentHandler.afterLanding(game, player, tile, planetID, type, amount);
    }

    public static void beforeRetreatInto(Game game, Player player, Tile destination) {
        if (!ThirtyKCards.inUse(game)) return;
        ThirtyKAttachmentHandler.beforeRetreatInto(game, player, destination);
    }

    public static void afterSpaceCombatHits(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, String assignHitsType) {
        if (!ThirtyKCards.inUse(game)) return;
        ThirtyKPlayAreaExploreHandler.checkMacGuffianOutpost(event, game, player, tile, assignHitsType);
    }
}
