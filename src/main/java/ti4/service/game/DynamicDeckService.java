package ti4.service.game;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.image.Mapper;
import ti4.model.DeckModel;

@UtilityClass
public class DynamicDeckService {

    public static boolean applyEnabledSourceDecks(Game game, GenericInteractionCreateEvent event) {
        BlueReverieExtrasService.synchronize(game);
        if (game.isDynamicDeckOverride()) return true;

        DeckModel actionCards = Mapper.getDeck(getActionCardDeckID(game));
        if (!game.validateAndSetActionCardDeck(event, actionCards)) return false;

        DeckModel relics = Mapper.getDeck(Mapper.getDynamicRelicDeckID(
                game.isUnchartedSpaceStuff(), game.isAbsolMode(), game.isBlueReverieExtras()));
        if (!game.validateAndSetRelicDeck(relics)) return false;

        DeckModel explores = Mapper.getDeck(
                Mapper.getDynamicExploreDeckID(game.isUnchartedSpaceStuff(), game.isBlueReverieExtras()));
        if (!game.validateAndSetExploreDeck(event, explores)) return false;

        DeckModel technologies = Mapper.getDeck(Mapper.getDynamicTechnologyDeckID(
                game.isDiscordantStarsMode(),
                game.isUnchartedSpaceStuff(),
                game.isAbsolMode(),
                game.isBlueReverieExtras()));
        return game.validateAndSetTechnologyDeck(technologies);
    }

    private static String getActionCardDeckID(Game game) {
        if (!game.isAcd2()) {
            return Mapper.getDynamicActionCardDeckID(game.isUnchartedSpaceStuff(), game.isBlueReverieExtras());
        }
        if (!game.isThundersEdge()) return game.isProphecyOfKings() ? "action_deck_2_pok" : "action_deck_2";
        return game.isProphecyOfKings() ? "action_deck_2_pok_te" : "action_deck_2_te";
    }
}
