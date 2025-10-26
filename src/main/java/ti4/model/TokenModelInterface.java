package ti4.model;

// Interface mostly only needs to know how to display a token
public interface TokenModelInterface extends ModelInterface {
    String getFilePath();

    String getFileName();

    UnitHolderType getUnitHolderType();

    enum UnitHolderType {
        SPACE,
        PLANET;
    }

    default boolean allowedInSpace() {
        return getUnitHolderType() == UnitHolderType.SPACE;
    }

    default boolean allowedOnPlanet() {
        return getUnitHolderType() == UnitHolderType.PLANET;
    }
}
