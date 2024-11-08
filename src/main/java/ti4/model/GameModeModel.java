package ti4.model;

import java.util.List;

import lombok.Data;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.Player;

@Data
public class GameModeModel implements ModelInterface {
    private String id;
    private String name;
    private String description;

    private List<String> gameTagsToAdd;

    private List<String> factionSources; // faction sources available in the game (default = all)
    private List<String> tileSources; // tile sources available in the game (default = all)

    private String acDeckID;
    private String soDeckID;
    private String stage1PublicDeckID;
    private String stage2PublicDeckID;
    private String relicDeckID;
    private String agendaDeckID;
    private String explorationDeckID;
    private String technologyDeckID;
    private String scSetID;
    private String eventDeckID;

    private String addUnitsFromSource;
    private String swapInTechsFromSource;
    private String swapInUnitsFromSource;
    private String addLeadersFromSource;

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public String getAlias() {
        return getId();
    }

    public void apply(GenericInteractionCreateEvent event, Game game, boolean force) {
        game.validateAndSetActionCardDeck(event, Mapper.getDeck(getAcDeckID()));
        game.validateAndSetSecretObjectiveDeck(event, Mapper.getDeck(getSoDeckID()));
        game.validateAndSetPublicObjectivesStage1Deck(event, Mapper.getDeck(getStage1PublicDeckID()));
        game.validateAndSetPublicObjectivesStage2Deck(event, Mapper.getDeck(getStage2PublicDeckID()));
        game.validateAndSetAgendaDeck(event, Mapper.getDeck(getAgendaDeckID()));
        game.validateAndSetRelicDeck(event, Mapper.getDeck(getRelicDeckID()));
        game.validateAndSetExploreDeck(event, Mapper.getDeck(getExplorationDeckID()));
        game.swapOutVariantTechs();
        game.setTechnologyDeckID(getTechnologyDeckID());
        game.swapInVariantTechs();
        game.swapInVariantUnits(getSwapInUnitsFromSource());
    }

}
