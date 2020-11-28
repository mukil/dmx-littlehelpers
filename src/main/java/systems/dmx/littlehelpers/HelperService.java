package systems.dmx.littlehelpers;


import systems.dmx.littlehelpers.model.ListTopic;
import systems.dmx.littlehelpers.model.SearchResult;
import java.util.List;
import systems.dmx.core.Topic;
import systems.dmx.core.TopicType;

/**
 * A plugin-service covering generic utilities in DMX plugin development.
 *
 * @author Malte Reißig (<malte@dmx.berlin>)
 * @website http://git.dmx.systems/dmx-plugins/dmx-littlehelpers
 * @version 0.5-SNAPSHOT - compatible with DMX 5.1
 */
public interface HelperService {

    /** 
     * Get a workspace topic with the given name.
     */
    Topic getWorkspaceByName(String name);



    /** 
     * Load list of topic types added to the "Create Menu"
     **/
    List<TopicType> getTopicTypesConfiguredForCreateMenu();



    /** 
     * Fulltext Query with Custom Resultset destined for CLI Usage
     * Todo: Reconceptualize.
     */
    List<SearchResult> getSuggestedSearchResults(String query);

    /** 
     * Fulltext Query with Custom Resultset destined for CLI Usage
     * Todo: Reconceptualize.
     */
    List<Topic> getTopicSuggestions(String query, String typeUri);
    


    /** 
     * Sorts a DMX topic collection descending by creation time.
     */
    List<? extends Topic> getTopicsDescendingByCreationTime(List<? extends Topic> list);

    /** 
     * Sorts a DMX topic collection descending by modification time.
     */
    List<? extends Topic> getTopicsDescendingByModificationTime(List<? extends Topic> list);

    void compareToByChildTypeValue(List<? extends Topic> topics, final String childTypeUri);

    void compareToBySimpleValue(List<? extends Topic> topics);


    
    /**
     * Timerange Query with Customized Result Type *ListTopic*
     */
    List<ListTopic> getStandardTopicsInTimeRange(String modifiedOrCreated, long from, long to);

    /**
     * Builds up a JSON string for a visual index of topics contained in the timerange.
     */
    String getTopicIndexForTimeRange(String modifiedOrCreated, long from, long to);



    /**
     * Enriching the underlying TopicModel before transfer.
     */
    void enrichTopicModelAboutIconConfigURL(Topic item);

    /**
     * Enriches the underlying TopicModel before transfer.
     */
    void enrichTopicModelAboutCreationTimestamp(Topic item);

    /**
     * Enriches the underlying TopicModel before transfer.
     */
    void enrichTopicModelAboutModificationTimestamp(Topic item);



}
