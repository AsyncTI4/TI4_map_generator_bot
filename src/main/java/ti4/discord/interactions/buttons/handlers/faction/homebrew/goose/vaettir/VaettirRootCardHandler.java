package ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.vaettir;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AgendaSummaryHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.TemporaryCombatModifierModel;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class VaettirRootCardHandler {
    private static final String CULTURAL_ROOT_CARD = "cultural_root_card";
    private static final String INDUSTRIAL_ROOT_CARD = "industrial_root_card";
    private static final String HAZARDOUS_ROOT_CARD = "hazardous_root_card";
    private static final String HAZARDOUS_ROOT_CARD_MODIFIER = "vaettir_root_card";
    private static final String USE_CULTURAL_ROOT_CARD = "useCulturalRootCard";
    private static final String USE_INDUSTRIAL_ROOT_CARD = "useIndustrialRootCard_";
    private static final String USE_HAZARDOUS_ROOT_CARD = "useHazardousRootCard";
    private static final String INDUSTRIAL_ROOT_PRODUCTION = "vaettirIndustrialRootProduction_";

    public static void offerCulturalRootCard(ButtonInteractionEvent event, Game game, Player player, String planetId) {
        if (event == null
                || !hasReadyRootCard(player, CULTURAL_ROOT_CARD)
                || !planetHasRootToken(game, planetId, "cultural")) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", you may exhaust _Cultural Root Card_.",
                List.of(Buttons.green(
                        player.factionButtonChecker() + USE_CULTURAL_ROOT_CARD,
                        "Use Cultural Root Card",
                        FactionEmojis.vaettir)));
    }

    @ButtonHandler(USE_CULTURAL_ROOT_CARD)
    public static void useCulturalRootCard(ButtonInteractionEvent event, Game game, Player player) {
        if (!hasReadyRootCard(player, CULTURAL_ROOT_CARD)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        int rootCount = getControlledRootTokenCount(game, player, "cultural");
        if (rootCount < 1) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        player.addExhaustedAbility(CULTURAL_ROOT_CARD);
        int votes = rootCount * 2;
        player.addSpentThing("culturalRoot_" + votes);
        String message = Helper.buildSpentThingsMessageForVoting(player, game, false);
        if (!game.isFowMode()) {
            message = AgendaSummaryHelper.getSummaryOfVotes(game, true) + "\n\n" + message;
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " exhausted _Cultural Root Card_ to cast " + votes
                        + " additional votes.\n\n" + message);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
    }

    public static void addIndustrialRootCardButton(List<Button> buttons, Game game, Player player, Tile tile) {
        if (!hasReadyRootCard(player, INDUSTRIAL_ROOT_CARD)
                || tile == null
                || !hasControlledRootToken(tile, player, "industrial")
                || Helper.getProductionValue(player, game, tile, false) < 1) {
            return;
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker() + USE_INDUSTRIAL_ROOT_CARD + tile.getPosition(),
                "Use Industrial Root Card",
                FactionEmojis.vaettir));
    }

    @ButtonHandler(USE_INDUSTRIAL_ROOT_CARD)
    public static void useIndustrialRootCard(ButtonInteractionEvent event, Game game, Player player, String buttonId) {
        Tile tile = game.getTileByPosition(buttonId.substring(USE_INDUSTRIAL_ROOT_CARD.length()));
        if (!hasReadyRootCard(player, INDUSTRIAL_ROOT_CARD)
                || tile == null
                || !hasControlledRootToken(tile, player, "industrial")) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        player.addExhaustedAbility(INDUSTRIAL_ROOT_CARD);
        game.setStoredValue(INDUSTRIAL_ROOT_PRODUCTION + player.getFaction(), tile.getPosition());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " exhausted _Industrial Root Card_. Their combined production cost is reduced by "
                        + getControlledRootTokenCount(game, player, "industrial") + ".");
        ButtonHelper.deleteTheOneButton(event);
    }

    public static int getIndustrialRootProductionDiscount(Game game, Player player) {
        String productionPosition = game.getStoredValue(INDUSTRIAL_ROOT_PRODUCTION + player.getFaction());
        return !productionPosition.isBlank()
                        && player.getCurrentProducedUnits().keySet().stream()
                                .anyMatch(unit -> unit.split("_", 3).length > 1
                                        && productionPosition.equals(unit.split("_", 3)[1]))
                ? getControlledRootTokenCount(game, player, "industrial")
                : 0;
    }

    public static int applyIndustrialRootProductionDiscount(Game game, Player player, int cost) {
        if (getIndustrialRootProductionDiscount(game, player) > 0) {
            game.removeStoredValue(INDUSTRIAL_ROOT_PRODUCTION + player.getFaction());
        }
        return cost;
    }

    public static void clearIndustrialRootProduction(Game game, Player player) {
        game.removeStoredValue(INDUSTRIAL_ROOT_PRODUCTION + player.getFaction());
    }

    public static void addHazardousRootCardButton(List<Button> buttons, Game game, Player player, Tile tile) {
        if (!hasReadyRootCard(player, HAZARDOUS_ROOT_CARD)
                || tile == null
                || !hasRootToken(tile, "hazardous")
                || getControlledRootTokenCount(game, player, "hazardous") < 1) {
            return;
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker() + USE_HAZARDOUS_ROOT_CARD,
                "Use Hazardous Root Card",
                FactionEmojis.vaettir));
    }

    @ButtonHandler(USE_HAZARDOUS_ROOT_CARD)
    public static void useHazardousRootCard(ButtonInteractionEvent event, Game game, Player player) {
        if (!hasReadyRootCard(player, HAZARDOUS_ROOT_CARD)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        TemporaryCombatModifierModel modifier = CombatTempModHelper.getPossibleTempModifier(
                HAZARDOUS_ROOT_CARD_MODIFIER, HAZARDOUS_ROOT_CARD, player.getNumberOfTurns());
        if (modifier == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        player.addExhaustedAbility(HAZARDOUS_ROOT_CARD);
        player.addNewTempCombatMod(modifier);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " exhausted _Hazardous Root Card_. It applies +"
                        + getControlledRootTokenCount(game, player, "hazardous")
                        + " to their combat rolls this combat round.");
        ButtonHelper.deleteTheOneButton(event);
    }

    public static int getControlledRootTokenCount(Game game, Player player, String rootType) {
        return (int) player.getPlanets().stream()
                .map(game::getUnitHolderFromPlanet)
                .filter(planet ->
                        planet != null && planet.getTokenList().contains("attachment_root_" + rootType + ".png"))
                .count();
    }

    public static void clearHazardousRootCardModifiers(Game game) {
        for (Player player : game.getRealPlayers()) {
            player.getNewTempCombatModifiers().removeIf(VaettirRootCardHandler::isHazardousRootCardModifier);
            player.getTempCombatModifiers().removeIf(VaettirRootCardHandler::isHazardousRootCardModifier);
        }
    }

    private static boolean hasReadyRootCard(Player player, String rootCard) {
        return player.hasAbility(rootCard) && !player.getExhaustedAbilities().contains(rootCard);
    }

    private static boolean planetHasRootToken(Game game, String planetId, String rootType) {
        var planet = game.getUnitHolderFromPlanet(planetId);
        return planet != null && planet.getTokenList().contains("attachment_root_" + rootType + ".png");
    }

    private static boolean hasControlledRootToken(Tile tile, Player player, String rootType) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> player.getPlanets().contains(planet.getName())
                        && planet.getTokenList().contains("attachment_root_" + rootType + ".png"));
    }

    private static boolean hasRootToken(Tile tile, String rootType) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> planet.getTokenList().contains("attachment_root_" + rootType + ".png"));
    }

    private static boolean isHazardousRootCardModifier(TemporaryCombatModifierModel modifier) {
        return HAZARDOUS_ROOT_CARD_MODIFIER.equals(modifier.getRelatedType())
                && HAZARDOUS_ROOT_CARD.equals(modifier.getRelatedID());
    }
}
