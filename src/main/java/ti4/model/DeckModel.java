package ti4.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ti4.generator.Mapper;
import ti4.message.BotLogger;

public class DeckModel implements ModelInterface {

  private String alias;
  private String name;
  private String type;
  private String description;
  private List<String> cardIDs;

  public boolean isValid() {
        return alias != null
            && name != null
            && type != null
            && description != null
            && cardIDs != null
            && validateCardIDs();
    }

    public String getAlias() {
        return alias;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getNewDeck() {
        return new ArrayList<>(cardIDs);
    }

    public List<String> getNewShuffledDeck() {
        List<String> cardList = new ArrayList<>(cardIDs);
        Collections.shuffle(cardList);
        return cardList;
    }

    public int getCardCount() {
        return cardIDs.size();
    }

    private void setCardIDs(List<String> cardIDs) { // This method is for Jackson
      this.cardIDs = Collections.unmodifiableList(cardIDs);
    }

    private boolean validateCardIDs() {
        return switch (getType()) {
            case "technology" -> validateTechDeck();
            case "agenda", "event" -> validateAgendaDeck();
            case "action_card" -> validateActionCardDeck();
            case "secret_objective" -> validateSecretObjectiveDeck();
            case "public_stage_1_objective", "public_stage_2_objective" -> validatePublicObjectiveDecks();
            case "relic" -> validateRelicDeck();
            case "explore" -> validateExploreDecks();
            case "template" -> true;
            default -> false;
        };
    }

    private boolean validateTechDeck() {
        if (Mapper.getTechs().keySet().containsAll(cardIDs))
            return true;
        List<String> missingCardIDs = new ArrayList<>();
        for (String cardID : cardIDs) {
            if (!Mapper.getTechs().containsKey(cardID))
                missingCardIDs.add(cardID);
        }
        BotLogger.log("Deck **" + getName() + "** failed validation due to invalid card IDs: `" + missingCardIDs + "`");
        return false;
    }

    private boolean validateAgendaDeck() {
        if (Mapper.getAgendas().keySet().containsAll(cardIDs))
            return true;
        List<String> missingCardIDs = new ArrayList<>();
        for (String cardID : cardIDs) {
            if (!Mapper.getAgendas().containsKey(cardID))
                missingCardIDs.add(cardID);
        }
        BotLogger.log("Deck **" + getName() + "** failed validation due to invalid card IDs: `" + missingCardIDs + "`");
        return false;
    }

    private boolean validateActionCardDeck() {
        if (Mapper.getActionCards().keySet().containsAll(cardIDs))
            return true;
        List<String> missingCardIDs = new ArrayList<>();
        for (String cardID : cardIDs) {
            if (!Mapper.getActionCards().containsKey(cardID))
                missingCardIDs.add(cardID);
        }
        BotLogger.log("Deck **" + getName() + "** failed validation due to invalid card IDs: `" + missingCardIDs + "`");
        return false;
    }

    private boolean validateSecretObjectiveDeck() {
        if (Mapper.getSecretObjectives().keySet().containsAll(cardIDs))
            return true;
        List<String> missingCardIDs = new ArrayList<>();
        for (String cardID : cardIDs) {
            if (!Mapper.getSecretObjectives().containsKey(cardID))
                missingCardIDs.add(cardID);
        }
        BotLogger.log("Deck **" + getName() + "** failed validation due to invalid card IDs: `" + missingCardIDs + "`");
        return false;
    }

    private boolean validatePublicObjectiveDecks() {
        if (Mapper.getPublicObjectives().keySet().containsAll(cardIDs))
            return true;
        List<String> missingCardIDs = new ArrayList<>();
        for (String cardID : cardIDs) {
            if (!Mapper.getPublicObjectives().containsKey(cardID))
                missingCardIDs.add(cardID);
        }
        BotLogger.log("Deck **" + getName() + "** failed validation due to invalid card IDs: `" + missingCardIDs + "`");
        return false;
    }

    private boolean validateRelicDeck() {
        if (Mapper.getRelics().keySet().containsAll(cardIDs))
            return true;
        List<String> missingCardIDs = new ArrayList<>();
        for (String cardID : cardIDs) {
            if (!Mapper.getRelics().containsKey(cardID))
                missingCardIDs.add(cardID);
        }
        BotLogger.log("Deck **" + getName() + "** failed validation due to invalid card IDs: `" + missingCardIDs + "`");
        return false;
    }

    private boolean validateExploreDecks() {
        if (Mapper.getExplores().keySet().containsAll(cardIDs))
            return true;
        List<String> missingCardIDs = new ArrayList<>();
        for (String cardID : cardIDs) {
            if (!Mapper.getExplores().containsKey(cardID))
                missingCardIDs.add(cardID);
        }
        BotLogger.log("Deck **" + getName() + "** failed validation due to invalid card IDs: `" + missingCardIDs + "`");
        return false;
    }
}
