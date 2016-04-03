package org.deepamehta.plugins.littlehelpers;


import de.deepamehta.core.Topic;
import java.util.ArrayList;
import java.util.List;

/**
 * A plugin-service helping with DeepaMehta 4 plugin development.
 *
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @website http://github.com/mukil/dm4-littlehelpers
 * @version 0.2 - compatible with DM 4.7
 */
public interface LittleHelpersService {

    List<SuggestionViewModel> getTopicSuggestions(String query);

    List<Topic> getTopicSuggestions(String query, String typeUri);

    ArrayList<Topic> getTopicListSortedByCreationTime(ArrayList<Topic> list);

    ArrayList<Topic> getTopicListSortedByModificationTime(ArrayList<Topic> list);

    String getStandardTopicsInTimeRange(String modifiedOrCreated, long from, long to);

    String getTopicIndexForTimeRange(String modifiedOrCreated, long from, long to);

}
