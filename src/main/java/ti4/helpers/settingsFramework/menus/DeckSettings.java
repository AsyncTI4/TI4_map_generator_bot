package ti4.helpers.settingsFramework.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.helpers.settingsFramework.settings.*;
import ti4.map.Game;
import ti4.model.DeckModel;
import ti4.model.StrategyCardSetModel;

// This is a sub-menu
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeckSettings extends SettingsMenu {
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    private ChoiceSetting<DeckModel> stage1, stage2, secrets, actionCards, agendas, techs, relics, explores;//, events;
    private ChoiceSetting<StrategyCardSetModel> stratCards;

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridden Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    @Override
    public void finishInitialization(Game game, SettingsMenu parent) {
        // Required "Static" Attributes
        this.menuId = "decks";
        this.menuName = "Card Decks";
        this.description = "Manually adjust which decks your game will use. This should be automatic, for the most part";
        this.parent = null;

        if (parent instanceof GameSettings gameSettings && gameSettings.getParent() instanceof MiltySettings milty) {
            // TODO: Jazz need to think about how to handle source settings
            // I don't think it makes sense to restrict these based on the SourceSettings menu
            // because folks like to do weird stuff and I don't want to have to support every
            // little homebrew manually in these settings menus
            milty.getSourceSettings();
        }

        // Initialize
        stage1 = new ChoiceSetting<>("Stg1Deck", "Stage 1 Deck", "public_stage_1_objectives_pok", stage1);
        stage2 = new ChoiceSetting<>("Stg2Deck", "Stage 2 Deck", "public_stage_2_objectives_pok", stage2);
        secrets = new ChoiceSetting<>("SecretDeck", "Secrets Deck", "secret_objectives_pok", secrets);
        actionCards = new ChoiceSetting<>("ACs", "Action Card Deck", "action_cards_pok", actionCards);
        agendas = new ChoiceSetting<>("Agendas", "Agenda Deck", "agendas_pok", agendas);
        techs = new ChoiceSetting<>("Techs", "Technology Deck", "techs_pok", techs);
        relics = new ChoiceSetting<>("Relics", "Relic Deck", "relics_pok", relics);
        explores = new ChoiceSetting<>("Explores", "Explore Decks", "explores_pok", explores);
        //events = new ChoiceSetting<>("Events", "Event Deck", "events_pok", events);
        stratCards = new ChoiceSetting<>("StratCards", "Strat Card Set", "pok", stratCards);

        // Emojis
        stage1.setEmoji(Emojis.Public1);
        stage2.setEmoji(Emojis.Public2);
        secrets.setEmoji(Emojis.SecretObjective);
        actionCards.setEmoji(Emojis.ActionCard);
        agendas.setEmoji(Emojis.Agenda);
        techs.setEmoji(Emojis.NonUnitTechSkip);
        relics.setEmoji(Emojis.Relic);
        explores.setEmoji(Emojis.Frontier);
        //events.setEmoji(null);
        stratCards.setEmoji(Emojis.SC1);

        // Setup deck options
        List<DeckModel> allDecks = new ArrayList<>(Mapper.getDecks().values());
        Map<String, List<DeckModel>> decksByType = allDecks.stream().collect(Collectors.groupingBy(DeckModel::getType));
        List<DeckModel> nil = new ArrayList<>();
        Collector<DeckModel, ?, Map<String, DeckModel>> collector = Collectors.toMap(DeckModel::getAlias, x -> x);
        stage1.setAllValues(decksByType.getOrDefault("public_stage_1_objective", nil).stream().collect(collector));
        stage2.setAllValues(decksByType.getOrDefault("public_stage_2_objective", nil).stream().collect(collector));
        secrets.setAllValues(decksByType.getOrDefault("secret_objective", nil).stream().collect(collector));
        actionCards.setAllValues(decksByType.getOrDefault("action_card", nil).stream().collect(collector));
        agendas.setAllValues(decksByType.getOrDefault("agenda", nil).stream().collect(collector));
        techs.setAllValues(decksByType.getOrDefault("technology", nil).stream().collect(collector));
        relics.setAllValues(decksByType.getOrDefault("relic", nil).stream().collect(collector));
        explores.setAllValues(decksByType.getOrDefault("explore", nil).stream().collect(collector));
        //events.setAllValues(decksByType.getOrDefault("event", nil).stream().collect(collector));
        stratCards.setAllValues(Mapper.getStrategyCardSets()); // strat cards use a different model

        // shows
        stage1.setShow(DeckModel::getName);
        stage2.setShow(DeckModel::getName);
        secrets.setShow(DeckModel::getName);
        actionCards.setShow(DeckModel::getName);
        agendas.setShow(DeckModel::getName);
        techs.setShow(DeckModel::getName);
        relics.setShow(DeckModel::getName);
        explores.setShow(DeckModel::getName);
        //events.setShow(DeckModel::getName);
        stratCards.setShow(StrategyCardSetModel::getName);

        super.finishInitialization(game, parent);
    }

    @Override
    public List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<SettingInterface>();
        ls.add(stage1);
        ls.add(stage2);
        ls.add(secrets);
        ls.add(actionCards);
        ls.add(agendas);
        ls.add(techs);
        ls.add(relics);
        ls.add(explores);
        ls.add(stratCards);
        //ls.add(events);
        return ls;
    }
}
