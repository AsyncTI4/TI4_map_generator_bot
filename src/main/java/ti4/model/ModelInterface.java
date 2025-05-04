package ti4.model;

public interface ModelInterface {
    boolean isValid();

    String getAlias();
    
    default String getID() {
        return getAlias();
    }
}
