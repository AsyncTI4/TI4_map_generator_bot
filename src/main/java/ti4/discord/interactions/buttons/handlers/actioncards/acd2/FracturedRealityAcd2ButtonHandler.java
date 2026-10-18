package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper.Die;
import ti4.message.MessageHelper;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.DiceEmojis;

@UtilityClass
public class FracturedRealityAcd2ButtonHandler {

    private static final String PENDING_KEY = "fracturedReality";
    private static final String CARD = "_Fractured Reality_";

    @ButtonHandler("resolveFracturedReality")
    public static void resolveFracturedReality(Player player, Game game, ButtonInteractionEvent event) {
        String prefix = player.factionButtonChecker() + "fracturedRealityFor_";
        List<Button> buttons = new ArrayList<>();
        for (CombatRollType rollType : CombatRollType.values()) {
            buttons.add(Buttons.red(prefix + rollType.name(), describeCombatRoll(rollType)));
        }
        buttons.add(Buttons.gray(player.factionButtonChecker() + "fracturedRealityRollNow", "Other Die Roll"));
        buttons.add(Buttons.red("deleteButtons", "Cancel"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the roll you are using " + CARD + " on.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("fracturedRealityFor_")
    public static void chooseRoll(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String rollKind = buttonID.replace("fracturedRealityFor_", "");
        game.setStoredValue(PENDING_KEY + player.getFaction(), rollKind);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), describePendingRoll(player, rollKind));
        ButtonHelper.deleteMessage(event);
    }

    private static String describePendingRoll(Player player, String rollKind) {
        return player.getRepresentationNoPing() + " is using " + CARD + " on their next "
                + describeCombatRoll(CombatRollType.valueOf(rollKind)).toUpperCase()
                + " roll this turn. One die for their best unit will be rolled as 2 dice, and neither result will be"
                + " cancelled.";
    }

    private static String describeCombatRoll(CombatRollType rollType) {
        return switch (rollType) {
            case combatround -> "Combat Roll";
            case AFB -> "Anti-Fighter Barrage";
            case bombardment -> "Bombardment";
            case SpaceCannonOffence -> "Space Cannon Offense";
            case SpaceCannonDefence -> "Space Cannon Defense";
        };
    }

    public static List<NamedCombatModifierModel> consumeCombatExtraRoll(
            Game game, Player player, CombatRollType rollType) {
        String key = PENDING_KEY + player.getFaction();
        if (!rollType.name().equals(game.getStoredValue(key))) {
            return List.of();
        }
        game.removeStoredValue(key);

        CombatModifierModel modifier = new CombatModifierModel();
        modifier.setAlias("fractured_reality");
        modifier.setType(Constants.COMBAT_EXTRA_ROLLS);
        modifier.setValue(1);
        modifier.setPersistenceType("ALWAYS");
        modifier.setScope("_best_");
        modifier.setRelated(List.of());
        modifier.setForCombatAbility(rollType);
        return List.of(new NamedCombatModifierModel(
                modifier, CARD + ": 1 die is rolled as 2 dice, and neither result is cancelled"));
    }

    public static void clearPendingRolls(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(PENDING_KEY + player.getFaction());
        }
    }

    @ButtonHandler("fracturedRealityRollNow")
    public static void rollNow(Player player, ButtonInteractionEvent event) {
        int first = new Die(0).getResult();
        int second = new Die(0).getResult();
        String idPrefix = player.factionButtonChecker() + "fracturedRealityKeep_" + first + "_" + second + "_";
        List<Button> buttons = List.of(
                Buttons.green(idPrefix + "first", "Keep " + first + ", Cancel " + second),
                Buttons.green(idPrefix + "second", "Keep " + second + ", Cancel " + first),
                Buttons.blue(idPrefix + "both", "Keep Both"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " rolled " + DiceEmojis.getGrayDieEmoji(first) + " "
                        + DiceEmojis.getGrayDieEmoji(second) + " with " + CARD
                        + ". Choose which result to cancel, if any, then resolve the roll with the result you keep.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("fracturedRealityKeep_")
    public static void keepOtherResult(Player player, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("fracturedRealityKeep_", "").split("_");
        String first = DiceEmojis.getGrayDieEmoji(Integer.parseInt(parts[0]));
        String second = DiceEmojis.getGrayDieEmoji(Integer.parseInt(parts[1]));
        String message =
                switch (parts[2]) {
                    case "first" -> "kept " + first + " and cancelled " + second;
                    case "second" -> "kept " + second + " and cancelled " + first;
                    default -> "kept both " + first + " and " + second;
                };
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getRepresentationNoPing() + " " + message + " with " + CARD + ".");
        ButtonHelper.deleteMessage(event);
    }
}
