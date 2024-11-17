package ti4.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import ti4.helpers.Emojis;
import ti4.model.Source.ComponentSource;

public class DeckModel implements ModelInterface, EmbeddableModel {

    private String alias;
    private String name;
    private DeckType type;
    private String description;
    private List<String> cardIDs;
    private ComponentSource source;

    public enum DeckType {
        ACTION_CARD,
        AGENDA,
        EVENT,
        EXPLORE,
        PUBLIC_STAGE_1_OBJECTIVE,
        PUBLIC_STAGE_2_OBJECTIVE,
        RELIC,
        SECRET_OBJECTIVE,
        TECHNOLOGY,
        @JsonEnumDefaultValue OTHER
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

        //TITLE
        String title = getTypeEmoji() +
            "__**" + getName() + "**__";
        eb.setTitle(title);

        //DESCRIPTION
        eb.setDescription(getDescription());

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

        //FOOTER
        eb.setFooter("ID: " + getAlias());

        eb.setColor(Color.BLACK);
        return eb.build();
    }

    @Override
    public boolean search(String searchString) {
        return getAlias().contains(searchString) || getName().contains(searchString) || getType().toString().contains(searchString) || getDescription().contains(searchString);
    }

    @Override
    public String getAutoCompleteName() {
        return StringUtils.left(StringUtils.substringBefore("[" + getType() + "] " + getName() + " --> " + getDescription(), "\n"), 100);
    }

    public ComponentSource getSource() {
        return source;
    }

    private String getTypeEmoji() {
        return switch (getType()) {
            case TECHNOLOGY -> Emojis.NonUnitTechSkip;
            case AGENDA -> Emojis.Agenda;
            case EVENT -> "";
            case ACTION_CARD -> Emojis.ActionCard;
            case PUBLIC_STAGE_1_OBJECTIVE -> Emojis.Public1;
            case PUBLIC_STAGE_2_OBJECTIVE -> Emojis.Public2;
            case SECRET_OBJECTIVE -> Emojis.SecretObjective;
            case RELIC -> Emojis.RelicCard;
            case EXPLORE -> Emojis.FrontierCard + Emojis.CulturalCard + Emojis.IndustrialCard + Emojis.HazardousCard;
            default -> "";
        };
    }
}
