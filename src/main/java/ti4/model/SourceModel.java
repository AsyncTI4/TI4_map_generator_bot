package ti4.model;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.model.Source.ComponentSource;

@Data
public class SourceModel implements ModelInterface, EmbeddableModel {
    
    private ComponentSource source; // unique identifiying name
    private String name; // Long fancy name
    private String canal; // Must be "official" or "community", must be non null
    private String subcanal; // Sub value of canal when canal = "community"
    private String credits; // Creator/Responsible.s
    private String description; // Content list (aggregated by component types)
    private List<String> data; // Links to rules, content, discussion/help channels, etc.

    public boolean isValid() {
        return source != null && name != null && canal != null;
    }

    @Override
    public String getAlias() {
        return getSource().toString();
    }

    @Override
    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(null);
    }
    public MessageEmbed getRepresentationEmbed(HashMap<String, Integer> occurrences) {
        EmbedBuilder eb = new EmbedBuilder();

        StringBuilder title = new StringBuilder();
        title.append(getSource().emoji()).append(" ").append(getName());
        eb.setTitle(title.toString());

        StringBuilder content = new StringBuilder();
        if (getDescription() != null) content.append("*").append(getDescription()).append("*\n\n");
        if (getData() != null) content.append("Links:\n").append(getDataFormatted()).append("\n");
        if (occurrences != null) content.append("Implementation: ").append(compTypeOccurrences(occurrences));
        eb.setDescription(content);

        StringBuilder footer = new StringBuilder();
        footer.append("Source: ").append(getSource()).append("    Type: ").append(getCanal());
        if(getSubcanal() != null) footer.append(" > ").append(getSubcanal());
        footer.append("\nCredits: ").append(getCredits());
        eb.setFooter(footer.toString());

        if (getCanal().equals("official")) {
            eb.setColor(Color.green);
        } else if (getCanal().equals("community")) {
            eb.setColor(Color.gray);
        } else {
            eb.setColor(Color.red);
        }

        return eb.build();
    }

    @Override
    public boolean search(String searchString) {
        return getName().toLowerCase().contains(searchString)
            || getSource().toString().toLowerCase().contains(searchString);
    }

    @Override
    public String getAutoCompleteName() {
        return getName();
    }

    public String getDataFormatted() {
        StringBuilder sb = new StringBuilder();
        for (String s : data) {
            sb.append("- ").append(s).append("\n");
        }
        return sb.toString();
    }

    public boolean isCanalOfficial() {
        boolean official = (getCanal().equals("official")) ? true : false;
        return official;
    }

    private String compTypeOccurrences(HashMap<String, Integer> occurrences){
        StringBuilder implementation = new StringBuilder("");
        for (Map.Entry<String, Integer> entry : occurrences.entrySet()) {
            if (entry.getValue() != 0) {
                if (!implementation.toString().equals("")) implementation.append(", ");
                implementation.append(entry.getKey()).append(" (").append(entry.getValue()).append(")");
            }
        }
        return implementation.toString();
    }

}
