package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Getter;
import ti4.helpers.settingsFramework.settings.ChoiceSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.DeckModel;
import ti4.model.StrategyCardSetModel;
import ti4.service.emoji.CardEmojis;

// This is a sub-menu
@Getter
@JsonIgnoreProperties({"messageId"})
public class DeckSettings extends SettingsMenu {
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    private final ChoiceSetting<DeckModel> stage1;
    private final ChoiceSetting<DeckModel> stage2;
    private final ChoiceSetting<DeckModel> secrets;
    private final ChoiceSetting<DeckModel> actionCards;
    private final ChoiceSetting<DeckModel> agendas;
    private final ChoiceSetting<DeckModel> techs;
    private final ChoiceSetting<DeckModel> relics;
    private final ChoiceSetting<DeckModel> explores;
    private final ChoiceSetting<StrategyCardSetModel> stratCards;

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Constructor & Initialization
    // ---------------------------------------------------------------------------------------------------------------------------------
    private ChoiceSetting<DeckModel> deckChoice(String id, String defaultDeck, DeckModel.DeckType deckType) {
        List<DeckModel> decks = Mapper.getDecks().values().stream()
                .filter(deck -> deck.getType() == deckType)
                .toList();

        ChoiceSetting<DeckModel> choice = new ChoiceSetting<>(id, deckType.typeName(), defaultDeck);
        choice.setEmoji(deckType.deckEmoji());
        choice.setShow(DeckModel::getName);
        choice.setAllValues(decks.stream().collect(Collectors.toMap(DeckModel::getAlias, x -> x)));
        return choice;
    }

    protected DeckSettings(JsonNode json, SettingsMenu parent, Optional<Game> game) {
        super(
                "decks",
                "Card Decks",
                "Manually adjust which decks your game will use. This should be automatic, for the most part",
                parent);

        // Get default deck IDs for this game
        String defaultStage1 = game.map(Game::getStage1PublicDeckID).orElse("public_stage_1_objectives_pok");
        String defaultStage2 = game.map(Game::getStage2PublicDeckID).orElse("public_stage_2_objectives_pok");
        String defaultSecret = game.map(Game::getSoDeckID).orElse("secret_objectives_pok");
        String defaultACdeck = game.map(Game::getAcDeckID).orElse("action_cards_pok");
        String defaultAgendas = game.map(Game::getAgendaDeckID).orElse("agendas_pok");
        String defaultTechs = game.map(Game::getTechnologyDeckID).orElse("techs_pok_c4");
        String defaultRelics = game.map(Game::getRelicDeckID).orElse("relics_pok");
        String defaultExplores = game.map(Game::getExplorationDeckID).orElse("explores_pok");
        String defaultSCs = game.map(Game::getScSetID).orElse("pok");

        // Initialize deck settings to default values
        stage1 = deckChoice("Stg1Deck", defaultStage1, DeckModel.DeckType.PUBLIC_STAGE_1_OBJECTIVE);
        stage2 = deckChoice("Stg2Deck", defaultStage2, DeckModel.DeckType.PUBLIC_STAGE_2_OBJECTIVE);
        secrets = deckChoice("SecretDeck", defaultSecret, DeckModel.DeckType.SECRET_OBJECTIVE);
        actionCards = deckChoice("ACs", defaultACdeck, DeckModel.DeckType.ACTION_CARD);
        agendas = deckChoice("Agendas", defaultAgendas, DeckModel.DeckType.AGENDA);
        techs = deckChoice("Techs", defaultTechs, DeckModel.DeckType.TECHNOLOGY);
        relics = deckChoice("Relics", defaultRelics, DeckModel.DeckType.RELIC);
        explores = deckChoice("Explores", defaultExplores, DeckModel.DeckType.EXPLORE);

        // Initialize strat cards to default values
        stratCards = new ChoiceSetting<>("StratCards", "Strat Card Set", defaultSCs);
        stratCards.setEmoji(CardEmojis.SCFrontBlank);
        stratCards.setAllValues(Mapper.getStrategyCardSets());
        stratCards.setShow(StrategyCardSetModel::getName);

        // Get the correct JSON node for initialization if applicable.
        // Add additional names here to support new generated JSON as needed.
        if (json != null && json.has("decks")) json = json.get("decks");

        // Verify this is the correct JSON node and continue initialization
        List<String> historicIDs = new ArrayList<>(List.of("decks"));
        if (json != null
                && json.has("menuId")
                && historicIDs.contains(json.get("menuId").asText(""))) {
            stage1.initialize(json.get("stage1"));
            stage2.initialize(json.get("stage2"));
            secrets.initialize(json.get("secrets"));
            actionCards.initialize(json.get("actionCards"));
            agendas.initialize(json.get("agendas"));
            techs.initialize(json.get("techs"));
            relics.initialize(json.get("relics"));
            explores.initialize(json.get("explores"));
            stratCards.initialize(json.get("stratCards"));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridden Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    @Override
    public List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<>();
        ls.add(stage1);
        ls.add(stage2);
        ls.add(secrets);
        ls.add(actionCards);
        ls.add(agendas);
        ls.add(techs);
        ls.add(relics);
        ls.add(explores);
        ls.add(stratCards);
        return ls;
    }
}
