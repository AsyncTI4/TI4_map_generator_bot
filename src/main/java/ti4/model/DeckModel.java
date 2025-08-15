package ti4.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.lang3.StringUtils;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;

public class DeckModel implements ModelInterface, EmbeddableModel {

    private String alias;
    private String name;
    private DeckType type;
    private String description;
    private List<String> cardIDs;
    private ComponentSource source;

    public enum DeckType {
        @JsonProperty("action_card")
        ACTION_CARD,

        @JsonProperty("agenda")
        AGENDA,

        @JsonProperty("event")
        EVENT,

        @JsonProperty("explore")
        EXPLORE,

        @JsonProperty("public_stage_1_objective")
        PUBLIC_STAGE_1_OBJECTIVE,

        @JsonProperty("public_stage_2_objective")
        PUBLIC_STAGE_2_OBJECTIVE,

        @JsonProperty("relic")
        RELIC,

        @JsonProperty("secret_objective")
        SECRET_OBJECTIVE,

        @JsonProperty("technology")
        TECHNOLOGY,

        @JsonEnumDefaultValue
        OTHER;

        public TI4Emoji deckEmoji() {
            return switch (this) {
                case ACTION_CARD -> CardEmojis.ActionCard;
                case AGENDA -> CardEmojis.Agenda;
                case EVENT -> null;
                case EXPLORE -> CardEmojis.FrontierCard;
                case PUBLIC_STAGE_1_OBJECTIVE -> CardEmojis.Public1;
                case PUBLIC_STAGE_2_OBJECTIVE -> CardEmojis.Public2;
                case RELIC -> CardEmojis.RelicCard;
                case SECRET_OBJECTIVE -> CardEmojis.SecretObjective;
                case TECHNOLOGY -> TechEmojis.NonUnitTechSkip;
                case OTHER -> null;
            };
        }

        public String typeName() {
            return switch (this) {
                case ACTION_CARD -> "Action Card Deck";
                case AGENDA -> "Agenda Deck";
                case EXPLORE -> "Exploration Deck";
                case PUBLIC_STAGE_1_OBJECTIVE -> "Stage 1 Deck";
                case PUBLIC_STAGE_2_OBJECTIVE -> "Stage 2 Deck";
                case RELIC -> "Relic Deck";
                case SECRET_OBJECTIVE -> "Secrets Deck";
                case TECHNOLOGY -> "Technology Deck";
                case EVENT -> "Ignis Aurora Event Deck";
                case OTHER -> "other deck";
            };
        }
    }

    public boolean isValid() {
        return alias != null
                && name != null
                && type != null
                && description != null
                && cardIDs != null
                && source != null;
    }

    public String getAlias() {
        return alias;
    }

    public String getName() {
        return name;
    }

    public DeckType getType() {
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

    protected void setCardIDs(List<String> cardIDs) { // This method is for Jackson
        this.cardIDs = Collections.unmodifiableList(cardIDs);
    }

    @Override
    public MessageEmbed getRepresentationEmbed() {
        EmbedBuilder eb = new EmbedBuilder();

        // TITLE
        String title = getTypeEmoji() + "__**" + name + "**__";
        eb.setTitle(title);

        // DESCRIPTION
        eb.setDescription(description);

        // // FIELDS
        // String cardList = getNewDeck().stream().collect(Collectors.joining("\n"));
        // if (cardList.length() <= 1024) {
        //     eb.addField("Card IDs:", cardList, true);
        // } else {
        //     while (true) {
        //         if (cardList.length() > 1024) {
        //             String firstCardList = StringUtils.left(StringUtils.substringBeforeLast(cardList, "\n"), 1024);
        //             eb.addField("Card IDs:", firstCardList, true);
        //             cardList = cardList.replace(firstCardList, "");
        //         } else {
        //             eb.addField("Card IDs:", cardList, true);
        //             break;
        //         }
        //     }
        // }

        // FOOTER
        eb.setFooter("ID: " + alias);

        eb.setColor(Color.BLACK);
        return eb.build();
    }

    @Override
    public boolean search(String searchString) {
        return alias.contains(searchString)
                || name.contains(searchString)
                || type.toString().contains(searchString)
                || description.contains(searchString);
    }

    @Override
    public String getAutoCompleteName() {
        return StringUtils.left(
                StringUtils.substringBefore("[" + type + "] " + name + " --> " + description, "\n"),
                100);
    }

    public ComponentSource getSource() {
        return source;
    }

    private String getTypeEmoji() {
        return switch (type) {
            case TECHNOLOGY -> TechEmojis.NonUnitTechSkip.toString();
            case AGENDA -> CardEmojis.Agenda.toString();
            case ACTION_CARD -> CardEmojis.ActionCard.toString();
            case PUBLIC_STAGE_1_OBJECTIVE -> CardEmojis.Public1.toString();
            case PUBLIC_STAGE_2_OBJECTIVE -> CardEmojis.Public2.toString();
            case SECRET_OBJECTIVE -> CardEmojis.SecretObjective.toString();
            case RELIC -> CardEmojis.RelicCard.toString();
            case EXPLORE ->
                CardEmojis.FrontierCard.toString()
                        + CardEmojis.CulturalCard
                        + CardEmojis.IndustrialCard
                        + CardEmojis.HazardousCard;
            default -> "";
        };
    }
}
