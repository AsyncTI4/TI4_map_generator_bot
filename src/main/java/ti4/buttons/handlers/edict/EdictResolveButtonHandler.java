package ti4.buttons.handlers.edict;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.buttons.handlers.edict.resolver.EdictResolver;
import ti4.buttons.handlers.edict.resolver.TfArbitrateResolver;
import ti4.buttons.handlers.edict.resolver.TfAriseResolver;
import ti4.buttons.handlers.edict.resolver.TfArtificeResolver;
import ti4.buttons.handlers.edict.resolver.TfBlessResolver;
import ti4.buttons.handlers.edict.resolver.TfCensureResolver;
import ti4.buttons.handlers.edict.resolver.TfConveneResolver;
import ti4.buttons.handlers.edict.resolver.TfExecuteResolver;
import ti4.buttons.handlers.edict.resolver.TfForetellResolver;
import ti4.buttons.handlers.edict.resolver.TfLegacyOfIxthResolver;
import ti4.buttons.handlers.edict.resolver.TfSpliceResolver;
import ti4.buttons.handlers.edict.resolver.TkCatalyzeResolver;
import ti4.buttons.handlers.edict.resolver.TkEndorseResolver;
import ti4.buttons.handlers.edict.resolver.TkEnfiladeResolver;
import ti4.buttons.handlers.edict.resolver.TkSanctuaryResolver;
import ti4.buttons.handlers.edict.resolver.TkSpoilResolver;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.service.breakthrough.ValefarZService;

@UtilityClass
public class EdictResolveButtonHandler {
    private static final Map<String, EdictResolver> EDICT_HANDLERS = new LinkedHashMap<>();

    public Player getEdictResolver(Game game) {
        String edict = game.getStoredValue("currentEdict");
        return game.getPlayerFromColorOrFaction(game.getStoredValue("edictResolver-" + edict));
    }

    public boolean isEdictResolver(Player player) {
        Game game = player.getGame();
        String edict = game.getStoredValue("currentEdict");
        return game.getStoredValue("edictResolver-" + edict).equals(player.getFaction());
    }

    static {
        List<EdictResolver> resolvers = List.of(
                // Twilight's Fall
                new TfArbitrateResolver(), //
                new TfAriseResolver(), //
                new TfArtificeResolver(), //
                new TfBlessResolver(), //
                new TfCensureResolver(), //
                new TfConveneResolver(), //
                new TfExecuteResolver(), //
                new TfForetellResolver(), //
                new TfLegacyOfIxthResolver(), //
                new TfSpliceResolver(), //

                // Twilight Kart
                new TkCatalyzeResolver(), //
                new TkEndorseResolver(),
                new TkEnfiladeResolver(), //
                new TkSanctuaryResolver(), //
                new TkSpoilResolver()); //
        resolvers.forEach(handler -> EDICT_HANDLERS.put(handler.getEdict(), handler));
    }

    @ButtonHandler("resolveEdict_")
    private void resolveEdict(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String edict = buttonID.replace("resolveEdict_", "").replace("_orangetf", "");

        game.removeStoredValue("convenePlayers");
        game.setStoredValue("currentEdict", edict);
        game.setStoredValue("edictResolver-" + edict, player.getFaction());
        AgendaModel model = Mapper.getAgenda(edict);

        String message = player.getRepresentation() + " is resolving the _" + model.getName() + "_ edict.";
        MessageHelper.sendMessageToChannelWithEmbed(game.getMainGameChannel(), message, model.getRepresentationEmbed());

        EdictResolver handler = EDICT_HANDLERS.get(edict.toLowerCase());
        if (handler != null) {
            handler.handle(event, game, player);
        }
        ButtonHelper.deleteAllButtons(event);

        if (!game.getPhaseOfGame().contains("action")) {
            boolean orangeResolving = false;
            List<String> removeFromRemaining = new ArrayList<>();
            if (!buttonID.contains("orangetf")) {
                List<Player> airoShirRexPlayers = ValefarZService.getAllPlayersWithFlagships(game, "orangetf_flagship");
                for (Player aur : airoShirRexPlayers) {
                    if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, aur, "flagship", false) < 1) {
                        continue;
                    }

                    orangeResolving = true;
                    String msg = aur.getRepresentation()
                            + ", after resolving the edict, use this button to resolve an additional edict from your flagship.";

                    List<String> edicts = EdictPhaseHandler.getEdictDeck(game);
                    removeFromRemaining.forEach(edicts::remove);

                    String orangeEdict = edicts.getFirst();
                    AgendaModel edictModel = Mapper.getAgenda(orangeEdict);
                    String id = aur.finChecker() + "resolveEdict_" + orangeEdict + "_orangetf";
                    Button resolve = Buttons.green(id, "Resolve 1 Edict");

                    MessageHelper.sendMessageToChannelWithButton(aur.getCorrectChannel(), msg, resolve);
                    if ("Law".equals(edictModel.getType())) {
                        removeFromRemaining.add(orangeEdict);
                    }
                }
            }
            if (!orangeResolving) {
                String msg2 = player.getRepresentation()
                        + ", after resolving the edict, use this button to proceed to the strategy phase. This button will ready all cards, and ping the speaker.";
                Button proceed = Buttons.green("proceed_to_strategy", "Proceed to Strategy Phase");
                MessageHelper.sendMessageToChannelWithButton(event.getChannel(), msg2, proceed);
            } else if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(
                        game.getMainGameChannel(), "# Radiant Aur will be resolving a second edict after this one.");
            }
        }
    }
}
