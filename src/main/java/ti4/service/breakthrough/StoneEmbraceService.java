package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;
import ti4.service.button.ReactionService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.strategycard.StrategyCardMessageService;

@UtilityClass
public class StoneEmbraceService {

    public boolean canUseStoneEmbrace(Player khrask, Player primary, StrategyCardModel scModel) {
        if (scModel == null || scModel.usesAutomationForSCID("pok1leadership")) return false;
        if (!khrask.hasTech("dskhrag")) return false;
        if (khrask.getReadiedPlanets().isEmpty()) return false;
        return true;
    }

    public void serveStoneEmbraceButtons(Game game, Player player, int sc) {
        Player primary = game.getPlayerFromSC(sc);
        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
        if (!canUseStoneEmbrace(player, primary, scModel)) return;

        StringBuilder msg = new StringBuilder(player.getRepresentation())
                .append(" since you have ")
                .append("Stone's Embrace")
                .append(" you may exhaust a planet you control")
                .append(" instead of spending a command token to follow **")
                .append(scModel.getName())
                .append("**. If you wish to do so, please choose which planet to exhaust");

        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getReadiedPlanets()) {
            buttons.add(Buttons.green(
                    "stoneEmbraceFollow_" + sc + "_" + planet,
                    "Exhaust " + Helper.getPlanetRepresentation(planet, game)));
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS
                .withLabel("Decline Stone's Embrace")
                .withEmoji(FactionEmojis.khrask.asEmoji()));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg.toString(), buttons);
    }

    @ButtonHandler("stoneEmbraceFollow_")
    private void followWithMindsieve(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Integer sc = Integer.parseInt(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
        if (!player.getFollowedSCs().contains(sc))
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, sc, game, event);
        player.addFollowedSC(sc, event);
        StrategyCardMessageService.getStrategyCardMessage(game.getName(), game.getRound(), sc)
                .ifPresent(scMessage ->
                        ReactionService.addReaction(player, false, null, null, scMessage.messageId(), game));
        player.exhaustPlanet(planet);
        MessageChannel scChannel = ButtonHelper.getSCFollowChannel(game, player, sc);
        String msg = player.getRepresentationUnfogged() + " exhausted the planet of "
                + Helper.getPlanetRepresentation(planet, game) + " via Stone's embrace"
                + " to perform the secondary ability of **" + scModel.getName()
                + "** without spending a command token.";
        MessageHelper.sendMessageToChannel(scChannel, msg);
        ButtonHelper.deleteMessage(event);
    }
}
