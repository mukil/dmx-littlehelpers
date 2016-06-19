package org.deepamehta.plugins.littlehelpers;

import de.deepamehta.accesscontrol.AccessControlService;
import de.deepamehta.core.RelatedTopic;
import java.util.logging.Logger;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;

import de.deepamehta.core.Topic;
import de.deepamehta.core.TopicType;
import de.deepamehta.core.model.ChildTopicsModel;

import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.Inject;
import de.deepamehta.time.TimeService;
import de.deepamehta.workspaces.WorkspacesService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import javax.ws.rs.core.MediaType;
import org.codehaus.jettison.json.JSONArray;


/**
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @website http://github.com/mukil/dm4-littlehelpers
 * @version 0.3-SNAPSHOT - compatible with DM 4.8
 *
 */
@Path("/helpers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LittleHelpersPlugin extends PluginActivator implements LittleHelpersService {

    private Logger log = Logger.getLogger(getClass().getName());

    // --- DeepaMehta Standard URIs

    private final static String PROP_URI_CREATED  = "dm4.time.created";
    private final static String PROP_URI_MODIFIED = "dm4.time.modified";

    // --- Custom Type Cache
    private HashMap<String, TopicType> viewConfigTypeCache = new HashMap<String, TopicType>();

    /** private final static String CHILD_URI = "dm4.core.child";
    private final static String PARENT_URI = "dm4.core.parent";
    private final static String AGGREGATION = "dm4.core.aggregation"; **/

    @Inject AccessControlService acService;
    @Inject WorkspacesService wsService;
    @Inject TimeService timeService;



    // --- Stableviews Utility Service

    @GET
    @Override
    @Path("/suggest/topics/{input}")
    public List<SuggestionViewModel> getTopicSuggestions(@PathParam("input") String query) {
        if(query == null || query.length() < 2) throw new IllegalArgumentException("To receive "
                + "suggestions, please provide at least two characters.");
        List<SuggestionViewModel> suggestions = new ArrayList<SuggestionViewModel>();
        // three explicit search for topicmap name, usernames and note-titles ### add IndexMode.FULLTEXT_KEY ?
        List<Topic> results = getTopicSuggestions(query, "dm4.topicmaps.name");
        results.addAll(getTopicSuggestions(query, "dm4.notes.title"));
        results.addAll(getTopicSuggestions(query, "dm4.accesscontrol.username"));
        // append the results of a generic fulltext search
        List<Topic> naives = dm4.searchTopics(query + "*", null);
        if (naives != null) {
            log.info("Naive search " + naives.size() + " length");
            results.addAll(naives);
        }
        // 
        log.info("> Checking for searchable units.. in " + results.size() );
        List<Topic> new_results = findSearchableUnits(results);
        for (Topic t : new_results) {
            log.fine("Suggesting \"" + t.getSimpleValue() + "\" topics (workspace=" + wsService.getAssignedWorkspace(t.getId())+ ")");
            suggestions.add(new SuggestionViewModel(t, wsService.getAssignedWorkspace(t.getId())));
        }
        log.info("Suggesting " + suggestions.size() + " topics for input: " + query);
        return suggestions;
    }

    @GET
    @Override
    @Path("/suggest/topics/{input}/{typeUri}")
    public List<Topic> getTopicSuggestions(@PathParam("input") String query, 
            @PathParam("typeUri") String typeUri) {
        return dm4.searchTopics(query + "*", typeUri);
    }



    // --- Timeline Utility Service (Formerly eduZEN Notizen)

    /**
     * Fetches standard topics by time-range and time-value (created || modified).
     */
    @GET
    @Path("/by_time/{time_value}/{since}/{to}")
    @Produces("application/json")
    public List<ViewTopic> getStandardTopicsInTimeRange(@PathParam("time_value") String type, @PathParam("since") long since,
        @PathParam("to") long to) {
        List<ViewTopic> results = new ArrayList<ViewTopic>();
        try {
            // 1) Fetch all topics in either "created" or "modified"-timestamp timerange
            log.info("Fetching Standard Topics (\"" + type + "\") since: " + new Date(since) + " and " + new Date(to));
            List<Topic> standardTopics = new ArrayList<Topic>(); // items of interest
            Collection<Topic> overallTopics = fetchAllTopicsInTimerange(type, since, to);
            if (overallTopics.isEmpty()) log.info("getStandardTopicsInTimeRange("+type+") got NO result.");
            Iterator<Topic> resultset = overallTopics.iterator();
            while (resultset.hasNext()) {
                Topic in_question = resultset.next();
                if (in_question.getTypeUri().equals("dm4.notes.note") ||
                    in_question.getTypeUri().equals("dm4.files.file") ||
                    in_question.getTypeUri().equals("dm4.files.folder") ||
                    in_question.getTypeUri().equals("dm4.contacts.person") ||
                    in_question.getTypeUri().equals("dm4.contacts.institution") ||
                    in_question.getTypeUri().equals("dm4.webbrowser.web_resource")) {
                    standardTopics.add(in_question);
                } else {
                    // log.info("> Result \"" +in_question.getSimpleValue()+ "\" (" +in_question.getTypeUri()+ ")");
                }
            }
            log.info("Topics " + type + " in timerange query found " + standardTopics.size() + " standard topics");
            // 2) Sort all fetched items by their "created" or "modified" timestamps
            List<Topic> in_memory_resources = null;
            if (type.equals("created")) {
                in_memory_resources = (List<Topic>) getTopicListSortedByCreationTime(standardTopics);
            } else if (type.equals("modified")) {
                in_memory_resources = (List<Topic>) getTopicListSortedByModificationTime(standardTopics);
            }
            // 3) Prepare the notes page-results view-model (per type of interest)
            for (Topic item : in_memory_resources) {
                try {
                    item.loadChildTopics();
                    enrichTopicModelAboutCreationTimestamp(item);
                    enrichTopicModelAboutModificationTimestamp(item);
                    enrichTopicModelAboutIconConfigURL(item);
                    ViewTopic viewTopic = new ViewTopic();
                    viewTopic.setTopicViewModel(item.toJSON());
                    String username = acService.getCreator(item.getId());
                    if (username != null) viewTopic.setUsername(username);
                    Topic workspace = wsService.getAssignedWorkspace(item.getId());
                    if (workspace != null) viewTopic.setWorkspaceId(workspace.getId());
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
     * Getting composites of all standard topics in given timerange.
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
                if (in_question.getTypeUri().equals("dm4.notes.note") ||
                    in_question.getTypeUri().equals("dm4.files.file") ||
                    in_question.getTypeUri().equals("dm4.files.folder") ||
                    in_question.getTypeUri().equals("dm4.contacts.person") ||
                    in_question.getTypeUri().equals("dm4.contacts.institution") ||
                    in_question.getTypeUri().equals("dm4.webbrowser.web_resource")) {
                    // log.info("> " +in_question.getSimpleValue()+ " of type \"" +in_question.getTypeUri()+ "\"");
                    standardTopics.add(in_question);
                }
            }
            log.info(type+" Topic Index for timerange query found " + standardTopics.size() + " standard topics (" + overallTopics.size() + " overall)");
            // 2) Sort and fetch resources
            // ArrayList<RelatedTopic> in_memory_resources = getResultSetSortedByCreationTime(all_resources);
            for (Topic item : standardTopics) { // 2) prepare resource items
                // 3) Prepare the notes page-results view-model
                item.loadChildTopics();
                enrichTopicModelAboutCreationTimestamp(item);
                enrichTopicModelAboutModificationTimestamp(item);
                results.put(item.toJSON());
            }
        } catch (Exception e) { // e.g. a "RuntimeException" is thrown if the moodle-plugin is not installed
            throw new RuntimeException("something went wrong", e);
        }
        return results.toString();
    }

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
    public List<? extends Topic> sortAlphabeticalDescending(List<? extends Topic> topics) {
        Collections.sort(topics, new Comparator<Topic>() {
            public int compare(Topic t1, Topic t2) {
                String one = t1.getSimpleValue().toString();
                String two = t2.getSimpleValue().toString();
                return one.compareTo(two);
            }
        });
        return topics;
    }

    @Override
    public void sortAlphabeticalDescendingByChild(List<? extends Topic> topics, final String childTypeUri) {
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
    
    // --- Private Utility Methods

    private Collection<Topic> fetchAllTopicsInTimerange(String type, long since, long to) {
        Collection<Topic> topics = null;
        if (type.equals("created")) {
            topics = timeService.getTopicsByCreationTime(since, to);
            log.fine("> Queried " +topics.size()+ " elements CREATED since: " + new Date(since) + " and " + new Date(to));
        } else if (type.equals("modified")) {
            topics = timeService.getTopicsByModificationTime(since, to);
            log.fine("> Queried " +topics.size()+ " elements MODIFIED since: " + new Date(since) + " and " + new Date(to));
        } else {
            throw new RuntimeException("Wrong parameter: set time_value either to \"created\" or \"modified\"");
        }
        return topics;
    }

    private void enrichTopicModelAboutIconConfigURL(Topic element) {
        TopicType topicType = null;
        if (viewConfigTypeCache.containsKey(element.getTypeUri())) {
            topicType = viewConfigTypeCache.get(element.getTypeUri());
        } else {
            topicType = dm4.getTopicType(element.getTypeUri());
            viewConfigTypeCache.put(element.getTypeUri(), topicType);
        }
        Object iconUrl = getViewConfig(topicType, "icon");
        if (iconUrl != null) {
            ChildTopicsModel resourceModel = element.getChildTopics().getModel();
            resourceModel.put("dm4.webclient.icon", iconUrl.toString());
        }
    }

    private void enrichTopicModelAboutCreationTimestamp(Topic resource) {
        long created = timeService.getCreationTime(resource.getId());
        ChildTopicsModel resourceModel = resource.getChildTopics().getModel();
        resourceModel.put(PROP_URI_CREATED, created);
    }

    private void enrichTopicModelAboutModificationTimestamp(Topic resource) {
        long created = timeService.getModificationTime(resource.getId());
        ChildTopicsModel resourceModel = resource.getChildTopics().getModel();
        resourceModel.put(PROP_URI_MODIFIED, created);
    }

    /** Taken from the WebclientPlugin.java by Jörg Richter */
    private List<Topic> findSearchableUnits(List<? extends Topic> topics) {
        List<Topic> searchableUnits = new ArrayList<Topic>();
        for (Topic topic : topics) {
            if (searchableAsUnit(topic)) {
                searchableUnits.add(topic);
            } else {
                List<RelatedTopic> parentTopics = topic.getRelatedTopics((String) null, "dm4.core.child",
                    "dm4.core.parent", null);
                if (parentTopics.isEmpty()) {
                    searchableUnits.add(topic);
                } else {
                    searchableUnits.addAll(findSearchableUnits(parentTopics));
                }
            }
        }
        return searchableUnits;
    }

    private boolean searchableAsUnit(Topic topic) {
        TopicType topicType = dm4.getTopicType(topic.getTypeUri());
        Boolean searchableAsUnit = (Boolean) getViewConfig(topicType, "searchable_as_unit");
        return searchableAsUnit != null ? searchableAsUnit.booleanValue() : false;  // default is false
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
        return topicType.getViewConfig("dm4.webclient.view_config", "dm4.webclient." + setting);
    }

}
