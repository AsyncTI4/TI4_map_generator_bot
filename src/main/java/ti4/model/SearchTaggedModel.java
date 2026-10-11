package ti4.model;

import java.util.List;

public interface SearchTaggedModel extends EmbeddableModel {
    String getAlias();

    String getName();

    List<String> getSearchTags();

    @Override
    default boolean search(String searchString) {
        return getAlias().toLowerCase().contains(searchString)
                || getName().toLowerCase().contains(searchString)
                || getSearchTags().contains(searchString);
    }
}
