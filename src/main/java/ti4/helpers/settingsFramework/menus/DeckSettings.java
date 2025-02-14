package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import ti4.helpers.settingsFramework.settings.ChoiceSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.model.DeckModel;
import ti4.model.StrategyCardSetModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;

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
    private ChoiceSetting<DeckModel> deckChoice(
            String id, String name, String defaultDeck, DeckModel.DeckType deckType, TI4Emoji emoji) {
        List<DeckModel> decks = Mapper.getDecks().values().stream()
                .filter(deck -> deck.getType() == deckType)
                .toList();

        ChoiceSetting<DeckModel> choice = new ChoiceSetting<>(id, name, defaultDeck);
        choice.setEmoji(emoji);
        choice.setShow(DeckModel::getName);
        choice.setAllValues(decks.stream().collect(Collectors.toMap(DeckModel::getAlias, x -> x)));
        return choice;
    }

    protected DeckSettings(JsonNode json, SettingsMenu parent) {
        super(
                "decks",
                "Card Decks",
                "Manually adjust which decks your game will use. This should be automatic, for the most part",
                parent);

        // Initialize deck settings to default values
        stage1 = deckChoice(
                "Stg1Deck",
                "Stage 1 Deck",
                "public_stage_1_objectives_pok",
                DeckModel.DeckType.PUBLIC_STAGE_1_OBJECTIVE,
                CardEmojis.Public1);

        stage2 = deckChoice(
                "Stg2Deck",
                "Stage 2 Deck",
                "public_stage_2_objectives_pok",
                DeckModel.DeckType.PUBLIC_STAGE_2_OBJECTIVE,
                CardEmojis.Public2);

        secrets = deckChoice(
                "SecretDeck",
                "Secrets Deck",
                "secret_objectives_pok",
                DeckModel.DeckType.SECRET_OBJECTIVE,
                CardEmojis.SecretObjective);

        actionCards = deckChoice(
                "ACs", "Action Card Deck", "action_cards_pok", DeckModel.DeckType.ACTION_CARD, CardEmojis.ActionCard);

        agendas = deckChoice("Agendas", "Agenda Deck", "agendas_pok", DeckModel.DeckType.AGENDA, CardEmojis.Agenda);

        techs = deckChoice(
                "Techs", "Technology Deck", "techs_pok", DeckModel.DeckType.TECHNOLOGY, TechEmojis.NonUnitTechSkip);

        relics = deckChoice("Relics", "Relic Deck", "relics_pok", DeckModel.DeckType.RELIC, ExploreEmojis.Relic);

        explores = deckChoice(
                "Explores", "Explore Decks", "explores_pok", DeckModel.DeckType.EXPLORE, ExploreEmojis.Frontier);

        // scenarios = deckChoice("Scenarios", "Scenario Deck", "scenario", null);

        // Initialize strat cards to default values
        stratCards = new ChoiceSetting<>("StratCards", "Strat Card Set", "pok");
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
            // scenarios.initialize(json.get("scenarios"));
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
        // ls.add(scenarios);
        return ls;
    }
}
