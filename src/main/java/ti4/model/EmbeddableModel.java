package ti4.model;

import net.dv8tion.jda.api.entities.MessageEmbed;

public interface EmbeddableModel {
    MessageEmbed getRepresentationEmbed();
    boolean search(String searchString);
    String getAutoCompleteName();
}
