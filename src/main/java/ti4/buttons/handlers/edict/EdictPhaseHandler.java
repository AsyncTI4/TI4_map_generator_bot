package ti4.buttons.handlers.edict;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.DeckModel;

@UtilityClass
public class EdictPhaseHandler {

    public List<String> getEdictDeck(Game game) {
        DeckModel deck = Mapper.getDeck(game.getAgendaDeckID());
        if (!deck.getSource().isTwilightFallish()) return null;

        List<String> edicts = deck.getNewShuffledDeck();
        edicts.removeIf(edict -> ButtonHelper.isLawInPlay(game, edict));
        return edicts;
    }

    @ButtonHandler("edictPhase")
    public static void edictPhase(GenericInteractionCreateEvent event, Game game) {
        game.setPhaseOfGame("agenda");
        List<String> edicts = getEdictDeck(game);
        List<Button> buttons = new ArrayList<>();
        List<MessageEmbed> embeds = new ArrayList<>();
        Player tyrant = game.getTyrant();
        if (tyrant == null) {
            Button proceedToStrategyPhase = Buttons.green("proceed_to_strategy", "Proceed to Strategy Phase");
            MessageHelper.sendMessageToChannelWithButton(
                    event.getMessageChannel(),
                    "There is no Tyrant, and so there can be no Edict Phase.",
                    proceedToStrategyPhase);
            return;
        }
        for (int x = 0; x < 3; x++) {
            AgendaModel edict = Mapper.getAgenda(edicts.get(x));
            buttons.add(Buttons.green(
                    tyrant.getFinsFactionCheckerPrefix() + "resolveEdict_" + edicts.get(x), edict.getName()));
            embeds.add(edict.getRepresentationEmbed());
        }
        String msg = tyrant.getRepresentation()
                + " as Tyrant, you should now choose which of the 3 edicts you wish to resolve.";
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(tyrant.getCorrectChannel(), msg, embeds, buttons);
        ButtonHelper.deleteMessage(event);
    }
}
