package ti4.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import ti4.helpers.ResourceHelper;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TileEmojis;

@Data
public class TileModel implements ModelInterface, EmbeddableModel {

    public enum TileBack {
        GREEN,
        BLUE,
        RED,
        BLACK;

        @JsonCreator
        public static TileBack fromString(String value) {
            return value == null ? BLACK : valueOf(value.toUpperCase());
        }

        @JsonValue
        public String toValue() {
            return name().toLowerCase();
        }
    }

    private String id;
    private String name;
    private List<String> aliases;
    private String imagePath;
    private List<String> planets;

    @Nullable
    private ShipPositionModel.ShipPosition shipPositionsType;

    private List<Point> spaceTokenLocations;
    private Set<WormholeModel.Wormhole> wormholes;
    private @JsonProperty("isHyperlane") boolean hyperlane;
    private @JsonProperty("isAsteroidField") boolean asteroidField;
    private @JsonProperty("isSupernova") boolean supernova;
    private @JsonProperty("isNebula") boolean nebula;
    private @JsonProperty("isGravityRift") boolean gravityRift;
    private String imageURL;
    private ComponentSource source;
    private TileBack tileBack = TileBack.BLACK;

    @Override
    @JsonIgnore
    public boolean isValid() {
        return id != null && imagePath != null && source != null;
    }

    @JsonIgnore
    public String getNameNullSafe() {
        return Optional.ofNullable(name).orElse("");
    }

    public MessageEmbed getRepresentationEmbed(boolean includeAliases) {
        EmbedBuilder eb = new EmbedBuilder();

        // TITLE
        eb.setTitle(getEmbedTitle());

        StringBuilder sb = new StringBuilder();
        if (isEmpty()) sb.append(ExploreEmojis.Frontier);
        if (asteroidField) sb.append(MiscEmojis.Asteroids);
        if (supernova) sb.append(MiscEmojis.Supernova);
        if (nebula) sb.append(MiscEmojis.Nebula);
        if (gravityRift) sb.append(MiscEmojis.GravityRift);
        if (hasPlanets()) sb.append("\nPlanets: ").append(planets.toString());
        eb.setDescription(sb.toString());

        // Image
        TI4Emoji emoji = getEmoji();
        if (emoji != null && emoji.asEmoji() instanceof CustomEmoji customEmoji) {
            if (emoji.name().endsWith("Back") && !StringUtils.isEmpty(imagePath)) {
                eb.setThumbnail(
                        "https://github.com/AsyncTI4/TI4_map_generator_bot/blob/master/src/main/resources/tiles/"
                                + imagePath + "?raw=true");
            } else {
                eb.setThumbnail(customEmoji.getImageUrl());
            }
        }

        if (includeAliases) eb.setFooter("Aliases: " + aliases);
        return eb.build();
    }

    public String getEmbedTitle() {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(id).append(") ");
        if (getEmoji() != null) sb.append(getEmoji().emojiString()).append(" ");
        if (!getNameNullSafe().isEmpty())
            sb.append("__").append(getNameNullSafe()).append("__");
        return sb.toString();
    }

    @JsonIgnore
    public String getTilePath() {
        String tileName = Mapper.getTileID(id);
        return ResourceHelper.getInstance().getTileFile(tileName);
    }

    @JsonIgnore
    public boolean hasWormhole() {
        return wormholes != null && !wormholes.isEmpty();
    }

    @JsonIgnore
    private boolean hasPlanets() {
        return planets != null && !planets.isEmpty();
    }

    @JsonIgnore
    public int getNumPlanets() {
        return planets == null ? 0 : planets.size();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return !hasPlanets();
    }

    @JsonIgnore
    public boolean isAsteroidField() {
        return asteroidField;
    }

    @JsonIgnore
    public boolean isSupernova() {
        return supernova;
    }

    @JsonIgnore
    public boolean isNebula() {
        return nebula;
    }

    @JsonIgnore
    public boolean isGravityRift() {
        return gravityRift;
    }

    @JsonIgnore
    public boolean isAnomaly() {
        return asteroidField || gravityRift || nebula || supernova;
    }

    @JsonIgnore
    public String getAutoCompleteName() {
        StringBuilder sb = new StringBuilder();
        sb.append(id).append(" ");
        if (name != null) sb.append(name);
        return sb.toString();
    }

    @JsonIgnore
    public boolean search(String searchString) {
        return id.toLowerCase().contains(searchString)
                || getNameNullSafe().toLowerCase().contains(searchString)
                || (aliases != null
                        && aliases.stream().anyMatch(a -> a.toLowerCase().contains(searchString)));
    }

    @JsonIgnore
    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    @JsonIgnore
    public String getAlias() {
        return id;
    }

    @JsonIgnore
    @Nullable
    public TI4Emoji getEmoji() {
        return TileEmojis.getTileEmojiFromTileID(id);
    }
}
