package ti4.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.awt.Point;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import ti4.helpers.Units;

@JsonTypeName("space")
public class Space extends UnitHolder {

    // Colors a fog-vision token here reveals the system to; null = everyone. Kept beside the token
    // (not on Tile) so it travels wherever the space holder's tokens are copied: tile flips and
    // replacements, moves, swaps. Allocated only when a restricted grant is set.
    @JsonIgnore
    @Nullable
    private Set<String> fowVisionGrant;

    public Space(String name, Point holderCenterPosition) {
        super(name, holderCenterPosition);
    }

    @JsonCreator
    public Space(
            @JsonProperty("name") String name,
            @JsonProperty("holderCenterPosition") Point holderCenterPosition,
            @JsonProperty("unitsByState") Map<Units.UnitKey, List<Integer>> unitsByState,
            @JsonProperty("ccList") Set<String> ccList,
            @JsonProperty("controlList") Set<String> controlList,
            @JsonProperty("tokenList") Set<String> tokenList) {
        super(name, holderCenterPosition, unitsByState, ccList, controlList, tokenList);
    }

    public String getRepresentation(Game game) {
        return "Space";
    }

    /** Read-only view of the fog-vision recipients; empty means everyone. */
    @JsonIgnore
    public Set<String> getFowVisionGrant() {
        return fowVisionGrant == null ? Collections.emptySet() : Collections.unmodifiableSet(fowVisionGrant);
    }

    /** Replaces the fog-vision recipients; null or empty means everyone (and frees the set). */
    public void setFowVisionGrant(@Nullable Collection<String> colors) {
        fowVisionGrant = colors == null || colors.isEmpty() ? null : new LinkedHashSet<>(colors);
    }

    @Override
    public void inheritEverythingFrom(UnitHolder other) {
        super.inheritEverythingFrom(other);
        if (other instanceof Space otherSpace && otherSpace.fowVisionGrant != null) {
            setFowVisionGrant(otherSpace.fowVisionGrant);
        }
    }
}
