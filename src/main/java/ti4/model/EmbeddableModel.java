package ti4.model;

import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.model.Source.ComponentSource;

public interface EmbeddableModel {
    ComponentSource getSource();

    MessageEmbed getRepresentationEmbed();

    boolean search(String searchString);

    String getAutoCompleteName();

    default boolean search(String searchString, ComponentSource searchSource) {
        return (searchSource == null || (getSource() != null && getSource() == searchSource))
                && (searchString == null || search(searchString));
    }

    default boolean searchSource(ComponentSource searchSource) {
        return (searchSource == null || (getSource() != null && getSource() == searchSource));
    }
}
