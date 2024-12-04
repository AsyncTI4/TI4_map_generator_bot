package ti4.service.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.DeckModel;

@UtilityClass
public class SetDeckService {

    public static boolean setDeck(SlashCommandInteractionEvent event, Game game, String deckType, DeckModel deckModel) {
        if (Optional.ofNullable(deckModel).isEmpty()) {
            return false;
        }
        boolean resetDeck = event.getOption("reset_deck", false, OptionMapping::getAsBoolean);
        switch (deckType) {
            case Constants.AC_DECK -> {
                if (resetDeck) {
                    game.resetActionCardDeck(deckModel);
                    return true;
                }
                return game.validateAndSetActionCardDeck(event, deckModel);
            }
            case Constants.SO_DECK -> {
                return game.validateAndSetSecretObjectiveDeck(event, deckModel);
            }
            case Constants.STAGE_1_PUBLIC_DECK -> {
                game.setPublicObjectives1(new ArrayList<>(deckModel.getNewShuffledDeck()));
                game.setStage1PublicDeckID(deckModel.getAlias());
                return true;
            }
            case Constants.STAGE_2_PUBLIC_DECK -> {
                game.setPublicObjectives2(new ArrayList<>(deckModel.getNewShuffledDeck()));
                game.setStage2PublicDeckID(deckModel.getAlias());
                return true;
            }
            case Constants.RELIC_DECK -> {
                return game.validateAndSetRelicDeck(event, deckModel);
            }
            case Constants.AGENDA_DECK -> {
                return game.validateAndSetAgendaDeck(event, deckModel);
            }
            case Constants.EVENT_DECK -> {
                return game.validateAndSetEventDeck(event, deckModel);
            }
            case Constants.EXPLORATION_DECKS -> {
                game.setExploreDeck(new ArrayList<>(deckModel.getNewShuffledDeck()));
                game.setExplorationDeckID(deckModel.getAlias());
                return true;
            }
            case Constants.TECHNOLOGY_DECK -> {
                game.setTechnologyDeckID(deckModel.getAlias());
                if (deckModel.getAlias().contains("absol")) {
                    for (Player player : game.getRealPlayers()) {
                        List<String> techs = new ArrayList<>(player.getTechs());
                        for (String tech : techs) {
                            if (!tech.contains("absol") && Mapper.getTech("absol_" + tech) != null) {
                                if (!player.hasTech("absol_" + tech)) {
                                    player.addTech("absol_" + tech);
                                }
                                player.removeTech(tech);
                            }
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }
}
