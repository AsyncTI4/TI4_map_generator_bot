package ti4.service.relic;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.DiceHelper.Die;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.ExploreEmojis;

@UtilityClass
public class HeartOfIxthService {

    public String rep() {
        return Mapper.getRelic("heartofixth").getSimpleRepresentation(false);
    }

    public MessageEmbed embed() {
        return Mapper.getRelic("heartofixth").getRepresentationEmbed();
    }

    public boolean isHeartAvailable(Game game) {
        return getHeartOfIxthPlayer(game, false) != null;
    }

    public Player getHeartOfIxthPlayer(Game game, boolean includeExhausted) {
        for (Player p : game.getRealPlayers()) {
            if (includeExhausted && p.hasRelic("heartofixth")) return p;
            if (p.hasRelicReady("heartofixth")) return p;
        }
        return null;
    }

    public List<Button> makeHeartOfIxthButtons(Game game, Player rollingPlayer, Button good, Button bad, Die result) {
        if (rollingPlayer == null) return null;
        List<Button> buttons = new ArrayList<>();
        Player heart = getHeartOfIxthPlayer(game, false);

        boolean blockForFog = !rollingPlayer.is(heart) && game.isFowMode();
        // For now, not sure how to handle this in FoW unless the "player rolling" also has heart

        if (heart == null || !result.eligibleForHeart() || blockForFog) {
            if (result.isSuccess()) buttons.add(good);
            if (!result.isSuccess()) buttons.add(bad);
        } else {
            // add basic resolve buttons
            Button goodNoHeart = good.withCustomId(good.getCustomId() + "_noheart");
            Button badNoHeart = bad.withCustomId(bad.getCustomId() + "_noheart");
            if (result.isSuccess()) buttons.add(goodNoHeart);
            if (!result.isSuccess()) buttons.add(badNoHeart);

            // add heart resolve buttons
            Button goodHeart = good.withCustomId(good.getCustomId() + "_heart");
            Button badHeart = bad.withCustomId(bad.getCustomId() + "_heart");
            if (rollingPlayer.is(heart)) {
                goodHeart = goodHeart.withLabel(good.getLabel() + " [Exhaust Heart of Ixth]");
                badHeart = badHeart.withLabel(bad.getLabel() + " [Exhaust Heart of Ixth]");
            }
            if (result.eligibleForHeartPlus()) buttons.add(goodHeart);
            if (result.eligibleForHeartMinus()) buttons.add(badHeart);

            if (!rollingPlayer.is(heart)) {
                String idPre = heart.finChecker() + "exhaustHeartOfIxth_";
                if (result.eligibleForHeartPlus())
                    buttons.add(Buttons.blue(idPre + "plus", "Exhaust Heart of Ixth", ExploreEmojis.Relic));
                if (result.eligibleForHeartMinus())
                    buttons.add(Buttons.blue(idPre + "minus", "Exhaust Heart of Ixth", ExploreEmojis.Relic));
                buttons.add(Buttons.gray(
                        heart.finChecker() + "declineHeartOfIxth", "Decline Heart of Ixth", ExploreEmojis.Relic));

                if (game.getStoredValue("heartWarnedThisTurn").isBlank()) {
                    String warning = "### ATTENTION " + rollingPlayer.getRepresentation() + ":\n";
                    warning += heart.getRepresentation()
                            + " has access to _Heart of Ixth_ and is able to change the outcome of your die roll. ";
                    warning +=
                            "Check with this player before resolving to make sure you choose the appropriate result.";
                    game.setStoredValue("heartWarnedThisTurn", "y");
                    MessageHelper.sendMessageToChannel(rollingPlayer.getCorrectChannel(), warning);
                }
            }
        }
        return buttons;
    }

    public boolean waitForHeartToResolve(ButtonInteractionEvent event, Game game, Player player) {
        boolean wait = false;
        for (Button b : event.getMessage().getComponentTree().findAll(Button.class)) {
            if (b.getCustomId().contains("exhaustHeartOfIxth")) wait = true;
            if (b.getCustomId().contains("declineHeartOfIxth")) wait = true;
        }

        Player heart = getHeartOfIxthPlayer(game, false);
        if (wait && heart != null && !player.is(heart)) {
            String msg = heart.getRepresentation() + " still needs to decide on " + rep() + ".";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            return true;
        }

        return false;
    }

    private List<Button> getButtonsAfterUsingHeart(ButtonInteractionEvent event, boolean used) {
        List<Button> originalButtons = event.getMessage().getComponentTree().findAll(Button.class);
        List<Button> newButtons = new ArrayList<>();
        for (Button button : originalButtons) {
            // get rid of the exhaust/decline buttons
            if (button.getCustomId().contains("exhaustHeartOfIxth")) continue;
            if (button.getCustomId().contains("declineHeartOfIxth")) continue;

            // get rid of the "decline" outcomes if we used it
            if (used && button.getCustomId().endsWith("_noheart")) continue;
            // get rid of the "exhaust" outcomes if we didn't use it
            if (!used && button.getCustomId().endsWith("_heart")) continue;

            newButtons.add(button);
        }
        return newButtons;
    }

    @ButtonHandler("exhaustHeartOfIxth_minus")
    @ButtonHandler("exhaustHeartOfIxth_plus")
    private void exhaustHeartOfIxth(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        exhaustHeartOfIxth(game, player, buttonID.endsWith("_plus"));
        MessageHelper.editMessageButtons(event, getButtonsAfterUsingHeart(event, true));
    }

    public void exhaustHeartOfIxth(Game game, Player player, Boolean plus) {
        String msg = player.getRepresentation() + " exhausted " + rep();
        if (plus == null) {
            msg += ".";
        } else {
            msg += " to " + (plus ? "add one to" : "subtract one from") + " a die roll.";
        }
        player.addExhaustedRelic("heartofixth");
        MessageHelper.sendMessageToChannelWithEmbed(player.getCorrectChannel(), msg, embed());
    }

    @ButtonHandler("declineHeartOfIxth")
    private void declineHeartOfIxth(ButtonInteractionEvent event, Game game, Player player) {
        String msg = player.getRepresentation() + " declined to use " + rep() + " at this time.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        MessageHelper.editMessageButtons(event, getButtonsAfterUsingHeart(event, false));
    }
}
