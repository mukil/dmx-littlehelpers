package systems.dmx.littlehelpers;

import systems.dmx.littlehelpers.model.ListTopic;
import systems.dmx.littlehelpers.model.SearchResult;
import java.util.logging.Logger;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import javax.ws.rs.core.MediaType;
import org.codehaus.jettison.json.JSONArray;
import systems.dmx.accesscontrol.AccessControlService;
import systems.dmx.core.QueryResult;
import systems.dmx.core.RelatedTopic;
import systems.dmx.core.Topic;
import systems.dmx.core.TopicType;
import systems.dmx.core.model.ChildTopicsModel;
import systems.dmx.core.osgi.PluginActivator;
import systems.dmx.core.service.Inject;
import systems.dmx.timestamps.TimestampsService;
import systems.dmx.workspaces.WorkspacesService;


/**
 * @author Malte Reißig <malte@dmx.berlin>
 * @website http://git.dmx.systems/dmx-plugins/dmx-littlehelpers
 * @version 0.5-SNAPSHOT - compatible with DMX 5.0-SNAPSHOT
 *
 */
@Path("/littlehelpers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class HelperPlugin extends PluginActivator implements HelperService {

    private Logger log = Logger.getLogger(getClass().getName());

    // --- DeepaMehta Time Plugin URIs

    private final static String PROP_URI_CREATED  = "dmx.time.created";
    private final static String PROP_URI_MODIFIED = "dmx.time.modified";

    // --- Hardcoded Type Cache (### Fixme: Lags updates of View Config Icon URL until te bundle is refreshed)
    private HashMap<String, TopicType> viewConfigTypeCache = new HashMap<String, TopicType>();

    private static final String SEARCH_OPTION_CREATED = "created";
    private static final String SEARCH_OPTION_MODIFIED = "modified";

    @Inject AccessControlService acService;
    @Inject WorkspacesService wsService;
    @Inject TimestampsService timeService;



    /** ----------------------------- Command Line Util Suggestion Search ----------------------------- **/

    @GET
    @Override
    @Path("/suggest/topics/{input}")
    public List<SearchResult> getSuggestedSearchableUnits(@PathParam("input") String query) {
        if(query == null || query.length() < 2) throw new IllegalArgumentException("To receive "
                + "suggestions, please provide at least two characters.");
        // ### Todo authorize request (maybe restrict to logged in users only)
        // fire three explicit searches: for topicmap name, usernames and note-titles
        List<Topic> searchResults = getTopicSuggestions(query, "dmx.topicmaps.name");
        searchResults.addAll(getTopicSuggestions(query, "dmx.notes.title"));
        searchResults.addAll(getTopicSuggestions(query, "dmx.accesscontrol.username"));
        // fire another global fulltext search // Note: As of 5.0 Beta-5, A Lucene Query is constructed by default in Core
        QueryResult queryResults = dmx.queryTopicsFulltext(query, null, false);
        if (queryResults != null) {
            log.info("Fulltext Search for \""+query+"*\" we found \"" + queryResults.topics.size() + "\" and in "
                    + "Topicmap Name, Notes Title and Username we found \"" + searchResults.size() + "\" topics");
            searchResults.addAll(queryResults.topics);
        }
        List<Topic> newResults = findSearchableUnits(searchResults);
        List<SearchResult> suggestions = new ArrayList<SearchResult>();
        for (Topic t : newResults) {
            SearchResult result = new SearchResult(t, wsService.getAssignedWorkspace(t.getId()));
            if (!suggestions.contains(result)) {
                log.fine("Suggesting \"" + t.getSimpleValue() + "\" topics (workspace=" +
                        wsService.getAssignedWorkspace(t.getId())+ ")");
                suggestions.add(result);
            }
        }
        log.info("Suggesting " + suggestions.size() + " searchable units for input \"" + query + "\"");
        return suggestions;
    }

    @GET
    @Override
    @Path("/suggest/topics/{input}/{typeUri}")
    public List<Topic> getTopicSuggestions(@PathParam("input") String query, 
            @PathParam("typeUri") String typeUri) {
        // Note: As of 5.0 Beta-5, A Lucene Query is constructed by default in Core
        return dmx.queryTopicsFulltext(query, typeUri, false).topics;
    }



    /** ------------------------------ Timerange Search Utils  --------------------------- **/

    /**
     * Fetches standard topics by time-range and time-value (created || modified).
     */
    @GET
    @Path("/by_time/{time_value}/{since}/{to}")
    @Produces("application/json")
    public List<ListTopic> getStandardTopicsInTimeRange(@PathParam("time_value") String type, @PathParam("since") long since,
        @PathParam("to") long to) {
        List<ListTopic> results = new ArrayList<ListTopic>();
        try {
            // 1) Fetch all topics in either "created" or "modified"-timestamp timerange
            log.info("Fetching Standard Topics (\"" + type + "\") since: " + new Date(since) + " and " + new Date(to));
            List<Topic> standardTopics = new ArrayList<Topic>(); // items of interest
            Collection<Topic> overallTopics = fetchAllTopicsInTimerange(type, since, to);
            if (overallTopics.isEmpty()) log.info("getStandardTopicsInTimeRange("+type+") got NO result.");
            Iterator<Topic> resultset = overallTopics.iterator();
            while (resultset.hasNext()) {
                Topic in_question = resultset.next();
                if (in_question.getTypeUri().equals("dmx.notes.note") ||
                    in_question.getTypeUri().equals("dmx.files.file") ||
                    in_question.getTypeUri().equals("dmx.files.folder") ||
                    in_question.getTypeUri().equals("dmx.contacts.person") ||
                    in_question.getTypeUri().equals("dmx.contacts.organization") ||
                    in_question.getTypeUri().equals("dmx.bookmarks.bookmark")) {
                    standardTopics.add(in_question);
                } else {
                    // log.info("> Result \"" +in_question.getSimpleValue()+ "\" (" +in_question.getTypeUri()+ ")");
                }
            }
            log.info("Topics " + type + " in timerange query found " + standardTopics.size() + " standard topics");
            // 2) Sort all fetched items by their "created" or "modified" timestamps
            List<Topic> in_memory_resources = null;
            if (type.equals(SEARCH_OPTION_CREATED)) {
                in_memory_resources = (List<Topic>) getTopicListSortedByCreationTime(standardTopics);
            } else if (type.equals(SEARCH_OPTION_MODIFIED)) {
                in_memory_resources = (List<Topic>) getTopicListSortedByModificationTime(standardTopics);
            }
            // 3) Prepare the notes page-results view-model (per type of interest)
            for (Topic item : in_memory_resources) {
                try {
                    item.loadChildTopics();
                    ListTopic viewTopic = prepareViewTopicItem(item);
                    results.add(viewTopic);
                } catch (RuntimeException rex) {
                    log.warning("Could not add fetched item to results, caused by: " + rex.getMessage());
                }
            }
        } catch (Exception e) { // e.g. a "RuntimeException" is thrown if the moodle-plugin is not installed
            throw new RuntimeException("something went wrong", e);
        }
        return results;
    }

    /**
     * Build a topic index (useful to display and facilitating visual time range queries) over most dm4 standard
     * topic types and a given timerange.
     */
    @GET
    @Path("/timeindex/{time_value}/{since}/{to}")
    @Produces("application/json")
    public String getTopicIndexForTimeRange(@PathParam("time_value") String type, @PathParam("since") long since,
        @PathParam("to") long to) {
        //
        JSONArray results = new JSONArray();
        try {
            log.info("Populating Topic Index (\"" + type + "\") since: " + new Date(since) + " and " + new Date(to));
            // 1) Fetch Resultset of Resources
            ArrayList<Topic> standardTopics = new ArrayList<Topic>();
            Collection<Topic> overallTopics = fetchAllTopicsInTimerange(type, since, to);
            Iterator<Topic> resultset = overallTopics.iterator();
            while (resultset.hasNext()) {
                Topic in_question = resultset.next();
                if (in_question.getTypeUri().equals("dmx.notes.note") ||
                    in_question.getTypeUri().equals("dmx.files.file") ||
                    in_question.getTypeUri().equals("dmx.files.folder") ||
                    in_question.getTypeUri().equals("dmx.contacts.person") ||
                    in_question.getTypeUri().equals("dmx.contacts.organization") ||
                    in_question.getTypeUri().equals("dmx.bookmarks.bookmark")) {
                    // log.info("> " +in_question.getSimpleValue()+ " of type \"" +in_question.getTypeUri()+ "\"");
                    standardTopics.add(in_question);
                }
            }
            log.info(type+" Topic Index for timerange query found " + standardTopics.size() + " standard topics (" + overallTopics.size() + " overall)");
            // 2) Sort and fetch resources
            // ArrayList<RelatedTopic> in_memory_resources = getResultSetSortedByCreationTime(all_resources);
            for (Topic item : standardTopics) { // 2) prepare resource items
                // 3) Prepare the notes page-results view-model
                enrichTopicModelAboutCreationTimestamp(item);
                enrichTopicModelAboutModificationTimestamp(item);
                results.put(item.toJSON());
            }
        } catch (Exception e) { // e.g. a "RuntimeException" is thrown if the moodle-plugin is not installed
            throw new RuntimeException("something went wrong", e);
        }
        return results.toString();
    }

    /** ---------------------------------- Sorting Utils ----------------------------------- **/

    @Override
    public List<? extends Topic> getTopicListSortedByCreationTime(List<? extends Topic> all) {
        Collections.sort(all, new Comparator<Topic>() {
            public int compare(Topic t1, Topic t2) {
                try {
                    Object one = t1.getProperty(PROP_URI_CREATED);
                    Object two = t2.getProperty(PROP_URI_CREATED);
                    if ( Long.parseLong(one.toString()) < Long.parseLong(two.toString()) ) return 1;
                    if ( Long.parseLong(one.toString()) > Long.parseLong(two.toString()) ) return -1;
                } catch (Exception nfe) {
                    log.warning("Error while accessing timestamp of Topic 1: " + t1.getId() + " Topic2: "
                            + t2.getId() + " nfe: " + nfe.getMessage());
                    return 0;
                }
                return 0;
            }
        });
        return all;
    }

    @Override
    public List<? extends Topic> getTopicListSortedByModificationTime(List<? extends Topic> all) {
        Collections.sort(all, new Comparator<Topic>() {
            public int compare(Topic t1, Topic t2) {
                try {
                    Object one = t1.getProperty(PROP_URI_MODIFIED);
                    Object two = t2.getProperty(PROP_URI_MODIFIED);
                    if ( Long.parseLong(one.toString()) < Long.parseLong(two.toString()) ) return 1;
                    if ( Long.parseLong(one.toString()) > Long.parseLong(two.toString()) ) return -1;
                } catch (Exception nfe) {
                    log.warning("Error while accessing timestamp of Topic 1: " + t1.getId() + " Topic2: "
                            + t2.getId() + " nfe: " + nfe.getMessage());
                    return 0;
                }
                return 0;
            }
        });
        return all;
    }

    @Override
    public void sortCompareToBySimpleValue(List<? extends Topic> topics) {
        Collections.sort(topics, new Comparator<Topic>() {
            public int compare(Topic t1, Topic t2) {
                String one = t1.getSimpleValue().toString();
                String two = t2.getSimpleValue().toString();
                return one.compareTo(two);
            }
        });
    }

    @Override
    public void sortCompareToByChildTypeValue(List<? extends Topic> topics, final String childTypeUri) {
        Collections.sort(topics, new Comparator<Topic>() {
            public int compare(Topic t1, Topic t2) {
                t1.loadChildTopics(childTypeUri);
                t2.loadChildTopics(childTypeUri);
                String one = t1.getChildTopics().getString(childTypeUri);
                String two = t2.getChildTopics().getString(childTypeUri);
                return one.compareTo(two);
            }
        });
    }

    /** -------------------------- Topic Presentation Utils (Timeline List Items) ---------------------------- **/

    @Override
    public void enrichTopicModelAboutIconConfigURL(Topic element) {
        TopicType topicType = null;
        if (viewConfigTypeCache.containsKey(element.getTypeUri())) {
            topicType = viewConfigTypeCache.get(element.getTypeUri());
        } else {
            topicType = dmx.getTopicType(element.getTypeUri());
            viewConfigTypeCache.put(element.getTypeUri(), topicType);
        }
        Object iconUrl = getViewConfig(topicType, "icon");
        if (iconUrl != null) {
            ChildTopicsModel resourceModel = element.getChildTopics().getModel();
            resourceModel.set("dmx.webclient.icon", iconUrl.toString());
        }
    }

    @Override
    public void enrichTopicModelAboutCreationTimestamp(Topic resource) {
        long created = timeService.getCreationTime(resource.getId());
        ChildTopicsModel resourceModel = resource.getChildTopics().getModel();
        resourceModel.set(PROP_URI_CREATED, created);
    }

    @Override
    public void enrichTopicModelAboutModificationTimestamp(Topic resource) {
        long created = timeService.getModificationTime(resource.getId());
        ChildTopicsModel resourceModel = resource.getChildTopics().getModel();
        resourceModel.set(PROP_URI_MODIFIED, created);
    }

    /** Taken from the WebclientPlugin.java by Jörg Richter */
    @Override
    public List<Topic> findSearchableUnits(List<? extends Topic> topics) {
        List<Topic> searchableUnits = new ArrayList<Topic>();
        for (Topic topic : topics) {
            if (searchableAsUnit(topic)) {
                searchableUnits.add(topic);
            } else {
                List<RelatedTopic> parentTopics = topic.getRelatedTopics(null, "dmx.core.child",
                    "dmx.core.parent", null);
                if (parentTopics.isEmpty()) {
                    searchableUnits.add(topic);
                } else {
                    searchableUnits.addAll(findSearchableUnits(parentTopics));
                }
            }
        }
        return searchableUnits;
    }



    // --- Private Utility Methods

    private boolean searchableAsUnit(Topic topic) {
        TopicType topicType = dmx.getTopicType(topic.getTypeUri());
        Boolean searchableAsUnit = (Boolean) getViewConfig(topicType, "searchable_as_unit");
        return searchableAsUnit != null ? searchableAsUnit.booleanValue() : false;  // default is false
    }

    private Collection<Topic> fetchAllTopicsInTimerange(String searchOption, long since, long to) {
        Collection<Topic> topics = null;
        if (searchOption.equals(SEARCH_OPTION_CREATED)) {
            topics = timeService.getTopicsByCreationTime(since, to);
            log.fine("> Queried " +topics.size()+ " elements CREATED since: " + new Date(since) + " and " + new Date(to));
        } else if (searchOption.equals(SEARCH_OPTION_MODIFIED)) {
            topics = timeService.getTopicsByModificationTime(since, to);
            log.fine("> Queried " +topics.size()+ " elements MODIFIED since: " + new Date(since) + " and " + new Date(to));
        } else {
            throw new RuntimeException("Invalid search parameter: set time_value either to \""
                    +SEARCH_OPTION_CREATED+"\" or \""+SEARCH_OPTION_MODIFIED+"\"");
        }
        return topics;
    }

    /**
     * Read out a view configuration setting.
     * <p>
     * Compare to client-side counterpart: function get_view_config() in webclient.js
     *
     * @param   topicType   The topic type whose view configuration is read out.
     * @param   setting     Last component of the setting URI, e.g. "icon".
     *
     * @return  The setting value, or <code>null</code> if there is no such setting
     */
    private Object getViewConfig(TopicType topicType, String setting) {
        return topicType.getViewConfigValue("dmx.webclient.view_config", "dmx.webclient." + setting);
    }

    private ListTopic prepareViewTopicItem(Topic item) {
        // enrich "childs" array of topic to transfer about some basics
        enrichTopicModelAboutCreationTimestamp(item);
        enrichTopicModelAboutModificationTimestamp(item);
        enrichTopicModelAboutIconConfigURL(item);
        ListTopic viewTopic = new ListTopic(item, acService, wsService);
        return viewTopic;
    }

}
