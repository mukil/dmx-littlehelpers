package org.deepamehta.plugins.littlehelpers;


import de.deepamehta.core.Topic;
import java.util.List;

/**
 * A plugin-service helping with DeepaMehta 4 plugin development.
 *
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @website http://github.com/mukil/dm4-littlehelpers
 * @version 0.3 - compatible with DM 4.8
 */
public interface LittleHelpersService {

    List<SuggestionViewModel> getTopicSuggestions(String query);

    List<Topic> getTopicSuggestions(String query, String typeUri);

    List<? extends Topic> getTopicListSortedByCreationTime(List<? extends Topic> list);

    List<? extends Topic> getTopicListSortedByModificationTime(List<? extends Topic> list);

    void sortAlphabeticalDescendingByChild(List<? extends Topic> topics, final String childTypeUri);

    List<? extends Topic> sortAlphabeticalDescending(List<? extends Topic> topics);

    List<ViewTopic> getStandardTopicsInTimeRange(String modifiedOrCreated, long from, long to);

    String getTopicIndexForTimeRange(String modifiedOrCreated, long from, long to);

}
