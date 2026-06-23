package ti4.service.game;

import java.util.ArrayList;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.DeckModel;

@UtilityClass
public class SetDeckService {

    public static boolean setDeck(SlashCommandInteractionEvent event, Game game, String deckType, DeckModel deckModel) {
        if (deckModel == null) {
            return false;
        }
        boolean resetDeck = event.getOption("reset_deck", Boolean.FALSE, OptionMapping::getAsBoolean);
        return switch (deckType) {
            case Constants.AC_DECK -> {
                if (resetDeck) {
                    game.resetActionCardDeck(deckModel);
                    yield true;
                }
                yield game.validateAndSetActionCardDeck(event, deckModel);
            }
            case Constants.SO_DECK -> game.validateAndSetSecretObjectiveDeck(event, deckModel);
            case Constants.RELIC_DECK -> game.validateAndSetRelicDeck(deckModel);
            case Constants.AGENDA_DECK -> game.validateAndSetAgendaDeck(event, deckModel);
            case Constants.EVENT_DECK -> game.validateAndSetEventDeck(event, deckModel);
            case Constants.STAGE_1_PUBLIC_DECK -> {
                game.setPublicObjectives1(new ArrayList<>(deckModel.getNewShuffledDeck()));
                game.setStage1PublicDeckID(deckModel.getAlias());
                if (resetDeck) {
                    resetPeekableAndPeekedPublicObjectives(game, 1);
                }
                yield true;
            }
            case Constants.STAGE_2_PUBLIC_DECK -> {
                game.setPublicObjectives2(new ArrayList<>(deckModel.getNewShuffledDeck()));
                game.setStage2PublicDeckID(deckModel.getAlias());
                if (resetDeck) {
                    resetPeekableAndPeekedPublicObjectives(game, 2);
                }
                yield true;
            }
            case Constants.EXPLORATION_DECKS -> {
                game.setExploreDeck(new ArrayList<>(deckModel.getNewShuffledDeck()));
                game.setExplorationDeckID(deckModel.getAlias());
                yield true;
            }
            case Constants.TECHNOLOGY_DECK -> {
                game.setTechnologyDeckID(deckModel.getAlias());
                if (deckModel.getAlias().contains("absol")) {
                    upgradePlayersToAbsolTechs(game);
                }
                yield true;
            }
            default -> false;
        };
    }

    private static void upgradePlayersToAbsolTechs(Game game) {
        for (Player player : game.getRealPlayers()) {
            for (String tech : new ArrayList<>(player.getTechs())) {
                if (tech.contains("absol")) continue;
                String absolVariant = "absol_" + tech;
                if (Mapper.getTech(absolVariant) == null) continue;
                if (!player.hasTech(absolVariant)) {
                    player.addTech(absolVariant);
                }
                player.removeTech(tech);
            }
        }
    }

    private static void resetPeekableAndPeekedPublicObjectives(Game game, int stage) {
        if (stage == 1) {
            game.getPublicObjectives1Peeked().clear();
            game.getPublicObjectives1Peekable().clear();
            game.setUpPeekableObjectives(5, 1);
            game.getRevealedPublicObjectives()
                    .keySet()
                    .removeIf(Mapper.getPublicObjectivesStage1().keySet()::contains);
        } else {
            game.getPublicObjectives2Peeked().clear();
            game.getPublicObjectives2Peekable().clear();
            game.setUpPeekableObjectives(5, 2);
            game.getRevealedPublicObjectives()
                    .keySet()
                    .removeIf(Mapper.getPublicObjectivesStage2().keySet()::contains);
        }
    }
}
