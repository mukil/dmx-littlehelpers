package systems.dmx.littlehelpers;

import java.net.URI;
import java.net.URISyntaxException;
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
import javax.ws.rs.core.Response;
import org.codehaus.jettison.json.JSONArray;
import systems.dmx.accesscontrol.AccessControlService;
import systems.dmx.core.Assoc;
import static systems.dmx.core.Constants.CHILD;
import static systems.dmx.core.Constants.PARENT;
import systems.dmx.core.Topic;
import systems.dmx.core.TopicType;
import systems.dmx.core.model.ChildTopicsModel;
import systems.dmx.core.model.SimpleValue;
import systems.dmx.core.osgi.PluginActivator;
import systems.dmx.core.service.Inject;
import systems.dmx.core.service.TopicResult;
import systems.dmx.core.service.Transactional;
import systems.dmx.core.util.DMXUtils;
import systems.dmx.timestamps.TimestampsService;
import systems.dmx.topicmaps.TopicmapsService;
import systems.dmx.workspaces.WorkspacesService;
import static systems.dmx.timestamps.Constants.CREATED;
import static systems.dmx.timestamps.Constants.MODIFIED;
import static systems.dmx.topicmaps.Constants.PAN_X;
import static systems.dmx.topicmaps.Constants.PAN_Y;
import static systems.dmx.topicmaps.Constants.TOPICMAP;
import static systems.dmx.topicmaps.Constants.TOPICMAP_TYPE_URI;
import static systems.dmx.topicmaps.Constants.VISIBILITY;
import static systems.dmx.topicmaps.Constants.X;
import static systems.dmx.topicmaps.Constants.Y;
import static systems.dmx.topicmaps.Constants.ZOOM;
import static systems.dmx.workspaces.Constants.WORKSPACE;
import static systems.dmx.workspaces.Constants.WORKSPACE_NAME;
import static systems.dmx.datetime.Constants.DATE;
import static systems.dmx.datetime.Constants.DAY;
import static systems.dmx.datetime.Constants.MONTH;
import static systems.dmx.datetime.Constants.YEAR;

/**
 * @author Malte Reißig <malte@dmx.berlin>
 * @website http://git.dmx.systems/dmx-plugins/dmx-littlehelpers
 * @version 0.5-SNAPSHOT - compatible with DMX 5.1
 */
@Path("/littlehelpers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class HelperPlugin extends PluginActivator implements HelperService {

    private Logger log = Logger.getLogger(getClass().getName());

    // --- DMX Time Plugin URIs

    private final static String WEBCLIENT_ICON_URI = "dmx.webclient.icon";
    public static final String WEBCLIENT_SLUG = "/systems.dmx.webclient/#";

    // --- Hardcoded Type Cache (### Fixme: Lags updates of View Config Icon URL until bundle is refreshed)
    private HashMap<String, TopicType> viewConfigTypeCache = new HashMap<String, TopicType>();

    private static final String SEARCH_OPTION_CREATED = "created";
    private static final String SEARCH_OPTION_MODIFIED = "modified";

    @Inject AccessControlService acl;
    @Inject WorkspacesService workspaces;
    @Inject TimestampsService timestamps;
    @Inject TopicmapsService topicmaps;



    @Override
    public List<TopicType> getTopicTypesConfiguredForCreateMenu() {
        List<TopicType> allTypes = dmx.getAllTopicTypes();
        List<TopicType> result = new ArrayList<>();
        for (TopicType type : allTypes) {
            if (getViewConfig(type, "add_to_create_menu").equals(true)) {
                result.add(type);
            }
        }
        // Color: Background color
        // Type Icon: Font Color, <div class="fa">UTF-8-String</div>
        // Font-Awesome 4.7
        return result;
    }

    @Override
    public List<Topic> getTopicmapsByMaptype(String mapTypeUri) {
        List<Topic> allMaps = dmx.getTopicsByType(TOPICMAP);
        List<Topic> result = new ArrayList<>();
        for (Topic t : allMaps) {
            // Topicmap map = topicmaps.getTopicmap(t.getId(), false);
            String topicmapType = t.getChildTopics().getString(TOPICMAP_TYPE_URI);
            if (mapTypeUri.equals(topicmapType)) {
                result.add(t);
            }
        }
        return result;
    }



    @Override
    public Topic getFirstWorkspaceByName(String name) {
        Topic realWs = null;
        Topic ws = dmx.getTopicByValue(WORKSPACE_NAME, new SimpleValue(name));
        if (ws == null) {
            // double check if it *really* does not exist yet
            List<Topic> existingWs = dmx.getTopicsByType(WORKSPACE);
            for (Topic topic : existingWs) {
                if (topic.getSimpleValue().toString().equals(name)) {
                    return topic;
                }
            }
        } else {
            realWs = ws.getRelatedTopic(null, CHILD, PARENT, WORKSPACE);
            return realWs;
        }
        return realWs;
    }

    
    
    /** ----------------------------- Reveal Topic in Topicmap Endpoint ----------------------------- **/

    @GET
    @Path("/open-in-map/{topicmapId}/{topicId}")
    @Produces(MediaType.TEXT_HTML)
    @Transactional
    public Response showTopicInTopicmap(@PathParam("topicmapId") long topicmapId, @PathParam("topicId") long topicId) throws URISyntaxException {
        if (topicmapId != -1) {
            Topic map = dmx.getTopic(topicmapId);
            if (map.getTypeUri().equals(TOPICMAP)) {
                // Translate Topicmap so Topic is in current viewport
                setTopicPositionInBrowserViewport(topicmapId, topicId);
                return Response.seeOther(new URI(WEBCLIENT_SLUG + "/topicmap/" + topicmapId + "/topic/" + topicId)).build();
            }
        }
        return Response.seeOther(new URI(WEBCLIENT_SLUG + "/topicmap/" + topicmapId)).build();
    }

    @Transactional
    private void setTopicPositionInBrowserViewport(long topicmapId, long topicId) {
        int x, y, mapX, mapY;
        Topic topicmap = dmx.getTopic(topicmapId);
        double zoom = (Double) topicmap.getProperty(ZOOM);
        Assoc tc = topicmaps.getTopicMapcontext(topicmapId, topicId);
        if (tc == null) { // Topic is NOT part of the map
            mapX = (Integer) topicmap.getProperty(PAN_X);
            mapY = (Integer) topicmap.getProperty(PAN_Y);
            topicmaps.addTopicToTopicmap(topicmap.getId(), topicId, mapX + 300, mapY + 300, true);
        } else { // Topic IS part of the map
            x = (Integer) tc.getProperty(X);
            y = (Integer) tc.getProperty(Y);
            boolean visibility = (Boolean) tc.getProperty(VISIBILITY);
            if (!visibility) {
                tc.setProperty(VISIBILITY, true, false);
            }
            if (acl.getUsername() != null) {
                topicmaps.setTopicmapViewport(topicmapId, 300 - x, 200 - y, zoom);
            }
        }
    }

    @GET
    @Path("/topicmaps/{mapTypeUri}")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public String getTopicmaps(@PathParam("mapTypeUri") String mapTypeUri) throws URISyntaxException {
        return DMXUtils.toJSONArray(getTopicmapsByMaptype(mapTypeUri)).toString();
    }



    /** ----------------------------- Create Date/Time Date Topic ----------------------------------- **/

    public ChildTopicsModel setDateTopic(ChildTopicsModel cm, Date date, String assocTypeUri) {
        // Create new child topicsmodel for date topic
        ChildTopicsModel child = mf.newChildTopicsModel();
        child.set(DAY, date.getDate());
        child.set(MONTH, date.getMonth() + 1);
        child.set(YEAR, date.getYear() + 1900);
        // set date on childTopicsModel
        cm.set(DATE + "#" + assocTypeUri, mf.newTopicModel(DATE, child));
        return cm;
    }



    /** ----------------------------- Filter list of topics by topicmapId ------------------------ **/
    
    // @Override
    public List<Topic> applyTopicmapFilter(List<Topic> searchResults, long topicmapId) {
        List<Topic> filteredResults = new ArrayList<Topic>();
        if (topicmapId > 0 && !searchResults.isEmpty()) {
            log.info("> Filter list of " + searchResults.size() + " uniqueResults about topicmap: " + topicmapId);
            for (Topic searchResult : searchResults) {
                if (isTopicVisibleInTopicmap(topicmapId, searchResult.getId())) {
                    filteredResults.add(searchResult);
                }
            }
        }
        return filteredResults;
    }

    // @Override
    public boolean isTopicVisibleInTopicmap(long topicmapId, long topicId) {
        Assoc mapContext = topicmaps.getTopicMapcontext(topicmapId, topicId);
        return (mapContext == null) ? false : (Boolean) mapContext.getProperty(VISIBILITY);
    }


    /** ----------------------------- Command Line Util Suggestion Search ----------------------------- **/

    @GET
    @Override
    @Path("/suggest/topics/{input}")
    public List<SearchResult> getSuggestedSearchResults(@PathParam("input") String query) {
        if(query == null || query.length() < 2) throw new IllegalArgumentException("To receive "
                + "suggestions, please provide at least two characters.");
        // ### Todo authorize request (maybe restrict to logged in users only)
        // fire three explicit searches: for topicmap name, usernames and note-titles
        List<Topic> searchResults = getTopicSuggestions(query, "dmx.topicmaps.name");
        searchResults.addAll(getTopicSuggestions(query, "dmx.notes.title"));
        searchResults.addAll(getTopicSuggestions(query, "dmx.accesscontrol.username"));
        // fire another global fulltext search // Note: As of 5.0 Beta-5, A Lucene Query is constructed by default in Core
        TopicResult queryResults = dmx.queryTopicsFulltext(query, null, false);
        if (queryResults != null) {
            log.info("Fulltext Search for \""+query+"\" we found \"" + queryResults.topics.size() + "\" and in "
                    + "Topicmap Name, Notes Title and Username we found \"" + searchResults.size() + "\" topics");
            searchResults.addAll(queryResults.topics);
        }
        List<SearchResult> suggestions = new ArrayList<SearchResult>();
        for (Topic t : searchResults) {
            SearchResult result = new SearchResult(t, workspaces.getAssignedWorkspace(t.getId()));
            if (!suggestions.contains(result)) {
                log.fine("Suggesting \"" + t.getSimpleValue() + "\" topics (workspace=" +
                        workspaces.getAssignedWorkspace(t.getId())+ ")");
                suggestions.add(result);
            }
        }
        log.info("Suggesting " + suggestions.size() + " search results for input \"" + query + "\"");
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
    public List<ListTopic> getTopicsInTimeRange(@PathParam("time_value") String type, @PathParam("since") long since,
        @PathParam("to") long to) {
        List<ListTopic> results = new ArrayList<ListTopic>();
        try {
            // 1) Fetch all topics in either "created" or "modified"-timestamp timerange
            log.info("Fetching Standard Topics (\"" + type + "\") since: " + new Date(since) + " and " + new Date(to));
            List<Topic> standardTopics = new ArrayList<Topic>(); // items of interest
            Collection<Topic> overallTopics = fetchAllTopicsInTimerange(type, since, to);
            if (overallTopics.isEmpty()) log.info("getStandardTopicsInTimeRange("+type+") got NO result.");
            // Todo: Load all Entity Types (with add_to_create_menu=true) to generically filter resultset
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
                in_memory_resources = (List<Topic>) getTopicsDescendingByCreationTime(standardTopics);
            } else if (type.equals(SEARCH_OPTION_MODIFIED)) {
                in_memory_resources = (List<Topic>) getTopicsDescendingByModificationTime(standardTopics);
            }
            // 3) Prepare the notes page-results view-mode
            for (Topic item : in_memory_resources) {
                try {
                    item.loadChildTopics();
                    ListTopic viewTopic = buildListTopic(item);
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
    public String getIndexForTimeRange(@PathParam("time_value") String type, @PathParam("since") long since,
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
    public List<? extends Topic> getTopicsDescendingByCreationTime(List<? extends Topic> all) {
        Collections.sort(all, new Comparator<Topic>() {
            public int compare(Topic t1, Topic t2) {
                try {
                    Object one = t1.getProperty(CREATED);
                    Object two = t2.getProperty(CREATED);
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
    public List<? extends Topic> getTopicsDescendingByModificationTime(List<? extends Topic> all) {
        Collections.sort(all, new Comparator<Topic>() {
            public int compare(Topic t1, Topic t2) {
                try {
                    Object one = t1.getProperty(MODIFIED);
                    Object two = t2.getProperty(MODIFIED);
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
    public void compareToBySimpleValue(List<? extends Topic> topics) {
        Collections.sort(topics, new Comparator<Topic>() {
            public int compare(Topic t1, Topic t2) {
                String one = t1.getSimpleValue().toString();
                String two = t2.getSimpleValue().toString();
                return one.compareTo(two);
            }
        });
    }

    @Override
    public void compareToByChildTypeValue(List<? extends Topic> topics, final String childTypeUri) {
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
            resourceModel.set(WEBCLIENT_ICON_URI, iconUrl.toString());
        }
    }

    @Override
    public void enrichTopicModelAboutCreationTimestamp(Topic resource) {
        long created = timestamps.getCreationTime(resource.getId());
        ChildTopicsModel resourceModel = resource.getChildTopics().getModel();
        resourceModel.set(CREATED, created);
    }

    @Override
    public void enrichTopicModelAboutModificationTimestamp(Topic resource) {
        long created = timestamps.getModificationTime(resource.getId());
        ChildTopicsModel resourceModel = resource.getChildTopics().getModel();
        resourceModel.set(MODIFIED, created);
    }

    // --- Private Utility Methods

    private Collection<Topic> fetchAllTopicsInTimerange(String searchOption, long since, long to) {
        Collection<Topic> topics = null;
        if (searchOption.equals(SEARCH_OPTION_CREATED)) {
            topics = timestamps.getTopicsByCreationTime(since, to);
            log.fine("> Queried " +topics.size()+ " elements CREATED since: " + new Date(since) + " and " + new Date(to));
        } else if (searchOption.equals(SEARCH_OPTION_MODIFIED)) {
            topics = timestamps.getTopicsByModificationTime(since, to);
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

    private ListTopic buildListTopic(Topic item) {
        // enrich "childs" array of topic to transfer about some basics
        enrichTopicModelAboutCreationTimestamp(item);
        enrichTopicModelAboutModificationTimestamp(item);
        enrichTopicModelAboutIconConfigURL(item);
        ListTopic viewTopic = new ListTopic(item, acl, workspaces);
        return viewTopic;
    }

}
