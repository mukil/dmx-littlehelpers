package systems.dmx.littlehelpers;


import systems.dmx.littlehelpers.model.ListTopic;
import systems.dmx.littlehelpers.model.SearchResult;
import java.util.List;
import systems.dmx.core.Topic;

/**
 * A plugin-service helping with DeepaMehta 4 plugin development.
 *
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @website http://github.com/mukil/dm4-littlehelpers
 * @version 0.3 - compatible with DM 4.8
 */
public interface HelperService {

    List<SearchResult> getSuggestedSearchableUnits(String query);

    List<Topic> getTopicSuggestions(String query, String typeUri);

    List<Topic> findSearchableUnits(List<? extends Topic> topics);

    List<? extends Topic> getTopicListSortedByCreationTime(List<? extends Topic> list);

    List<? extends Topic> getTopicListSortedByModificationTime(List<? extends Topic> list);

    void enrichTopicModelAboutIconConfigURL(Topic item);

    void enrichTopicModelAboutCreationTimestamp(Topic item);

    void enrichTopicModelAboutModificationTimestamp(Topic item);

    void sortCompareToByChildTypeValue(List<? extends Topic> topics, final String childTypeUri);

    void sortCompareToBySimpleValue(List<? extends Topic> topics);

    List<ListTopic> getStandardTopicsInTimeRange(String modifiedOrCreated, long from, long to);

    String getTopicIndexForTimeRange(String modifiedOrCreated, long from, long to);

}
