package ti4.model;

import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.model.Source.ComponentSource;

public interface EmbeddableModel {
    ComponentSource getSource();

    MessageEmbed getRepresentationEmbed();

    boolean search(String searchString);

    default boolean search(String searchString, ComponentSource searchSource) {
        return (searchSource == null || (getSource() != null && getSource().equals(searchSource)))
                && (searchString == null || search(searchString));
    }

    String getAutoCompleteName();
}
