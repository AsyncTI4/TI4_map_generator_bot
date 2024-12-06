package ti4.model;

import java.awt.*;
import java.util.List;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.model.Source.ComponentSource;

@Data
public class SourceModel implements ModelInterface, EmbeddableModel {
    
    private ComponentSource source; // unique identifiying name
    private String name; // Long fancy name
    private String canal; // Must be "official" or "community"
    private String subcanal; // Sub value of canal when canal = "community"
    private String credits; // Creator/Responsible
    private List<String> data; // Links to rules, content, discussion channels, etc.

    public boolean isValid() {
        return source != null && name != null && canal != null;
    }

    @Override
    public String getAlias() {
        return getSource().toString();
    }

    @Override
    public MessageEmbed getRepresentationEmbed() {

        EmbedBuilder eb = new EmbedBuilder();

        StringBuilder title = new StringBuilder();
        title.append(getSource().emoji()).append(" ").append(getName());
        eb.setTitle(title.toString());

        StringBuilder description = new StringBuilder();
        description.append("Content:\n").append(getDataFormatted());
        description.append("Implementation:\n").append("WIP");
        eb.setDescription(description);

        StringBuilder footer = new StringBuilder();
        footer.append("Source: ").append(getSource()).append("    Type: ").append(getCanal());
        if(subcanal != null) footer.append(" > ").append(getSubcanal());
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
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAutoCompleteName'");
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

}
