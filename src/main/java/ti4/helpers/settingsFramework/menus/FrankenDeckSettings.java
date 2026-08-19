package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.helpers.settingsFramework.settings.ChoiceSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.model.DeckModel;
import ti4.service.emoji.CardEmojis;
import tools.jackson.databind.JsonNode;

@Getter
@JsonIgnoreProperties("messageId")
class FrankenDeckSettings extends SettingsMenu {
    private final ChoiceSetting<DeckModel> actionCards;
    private final ChoiceSetting<DeckModel> relics;
    private final ChoiceSetting<DeckModel> explores;

    FrankenDeckSettings(Game game, JsonNode json, SettingsMenu parent) {
        super("frankenDecks", "Set Decks", "Choose the card decks used for this Franken draft.", parent);

        actionCards = deckChoice("ACs", "Action Cards", game.getAcDeckID(), DeckModel.DeckType.ACTION_CARD);
        relics = deckChoice("Relics", "Relics", game.getRelicDeckID(), DeckModel.DeckType.RELIC);
        explores = deckChoice("Explores", "Explores", game.getExplorationDeckID(), DeckModel.DeckType.EXPLORE);

        actionCards.setEmoji(CardEmojis.ActionCard);
        relics.setEmoji(CardEmojis.RelicCard);
        explores.setEmoji(CardEmojis.CulturalCard);

        if (json != null && json.has("deckSettings")) json = json.get("deckSettings");
        if (json != null
                && json.has("menuId")
                && "frankenDecks".equals(json.get("menuId").asString(""))) {
            actionCards.initialize(json.get("actionCards"));
            relics.initialize(json.get("relics"));
            explores.initialize(json.get("explores"));
        }
    }

    @Override
    protected List<SettingInterface> settings() {
        return List.of(actionCards, relics, explores);
    }

    @Override
    public String menuSummaryString(String lastSettingTouched) {
        if (parent instanceof FrankenSettings settings) {
            settings.persistSettings();
        }
        return super.menuSummaryString(lastSettingTouched);
    }

    void applyDecks(Game game, GenericInteractionCreateEvent event) {
        game.validateAndSetActionCardDeck(event, actionCards.getValue());
        game.validateAndSetRelicDeck(relics.getValue());
        game.validateAndSetExploreDeck(event, explores.getValue());
    }

    private static ChoiceSetting<DeckModel> deckChoice(
            String id, String name, String defaultDeck, DeckModel.DeckType deckType) {
        ChoiceSetting<DeckModel> choice = new ChoiceSetting<>(id, name, defaultDeck);
        choice.setAllValues(Mapper.getDecks().values().stream()
                .filter(deck -> deck.getType() == deckType)
                .collect(Collectors.toMap(DeckModel::getAlias, deck -> deck)));
        choice.setShow(DeckModel::getName);
        return choice;
    }
}
