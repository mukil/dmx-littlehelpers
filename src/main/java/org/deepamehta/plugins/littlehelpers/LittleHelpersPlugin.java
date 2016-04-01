package org.deepamehta.plugins.littlehelpers;

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
import de.deepamehta.plugins.time.TimeService;
import de.deepamehta.plugins.workspaces.WorkspacesService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import javax.ws.rs.core.MediaType;
import org.codehaus.jettison.json.JSONArray;


/**
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @website http://github.com/mukil/dm4-littlehelpers
 * @version 0.2 - compatible with DM 4.7
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

    /** private final static String CHILD_URI = "dm4.core.child";
    private final static String PARENT_URI = "dm4.core.parent";
    private final static String AGGREGATION = "dm4.core.aggregation"; **/

    @Inject WorkspacesService wsService;
    @Inject TimeService timeService;



    // --
    // --- Stableviews Utility Service
    // --

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
        List<Topic> naives = dms.searchTopics(query + "*", null);
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
        return dms.searchTopics(query + "*", typeUri);
    }



    // --
    // --- Timeline Utility Service (Formerly eduZEN Notizen)
    // --

    /**
     * Fetches standard topics by time-range and time-value (created || modified).
     */
    @GET
    @Path("/by_time/{time_value}/{from}/{to}")
    @Produces("application/json")
    public String getStandardTopicsInTimeRange(@PathParam("time_value") String type, @PathParam("from") long from, @PathParam("to") long to) {
        JSONArray results = new JSONArray();
        try {
            // 1) Fetch all topics in either "created" or "modified"-timestamp timerange
            log.info("Fetching Topics in timerange from \"" + from + "\" to \"" + to + "\"");
            ArrayList<Topic> items_in_range = new ArrayList<Topic>(); // items of interest
            Collection<Topic> topics_in_range = null; // all topics
            if (type.equals("created")) {
                topics_in_range = timeService.getTopicsByCreationTime(from, to);
            } else if (type.equals("modified")) {
                topics_in_range = timeService.getTopicsByModificationTime(from, to);
            } else {
                return "Wrong parameter: set time_value to \"created\" or \"modified\"";
            }
            if (topics_in_range.isEmpty()) log.info("getStandardTopicsInTimeRange("+type+") got NO result.");
            Iterator<Topic> resultset = topics_in_range.iterator();
            while (resultset.hasNext()) {
                Topic in_question = resultset.next();
                if (in_question.getTypeUri().equals("dm4.notes.note")) {
                    items_in_range.add(in_question);
                } else if (in_question.getTypeUri().equals("dm4.files.file")) {
                    items_in_range.add(in_question);
                } else if (in_question.getTypeUri().equals("dm4.files.folder")) {
                    items_in_range.add(in_question);
                } else if (in_question.getTypeUri().equals("dm4.contacts.person")) {
                    items_in_range.add(in_question);
                } else if (in_question.getTypeUri().equals("dm4.contacts.institution")) {
                    items_in_range.add(in_question);
                } else if (in_question.getTypeUri().equals("dm4.webbrowser.web_resource")) {
                    items_in_range.add(in_question);
                } else {
                    // log.info("> Result \"" +in_question.getSimpleValue()+ "\" (" +in_question.getTypeUri()+ ")");
                }
            }
            log.info("> Fetched " +items_in_range.size()+ " elements (" + from + ", " + to + ")" + " by time");
            // 2) Sort all fetched items by their "created" or "modified" timestamps
            ArrayList<Topic> in_memory_resources = null;
            if (type.equals("created")) {
                in_memory_resources = getTopicListSortedByCreationTime(items_in_range);
            } else if (type.equals("modified")) {
                in_memory_resources = getTopicListSortedByModificationTime(items_in_range);
            }
            // 3) Prepare the notes page-results view-model (per type of interest)
            for (Topic item : in_memory_resources) {
                try {
                    item.loadChildTopics();
                    enrichTopicModelAboutCreationTimestamp(item);
                    enrichTopicModelAboutModificationTimestamp(item);
                    results.put(item.toJSON());
                } catch (RuntimeException rex) {
                    log.warning("Could not add fetched item to results, caused by: " + rex.getMessage());
                }
            }
        } catch (Exception e) { // e.g. a "RuntimeException" is thrown if the moodle-plugin is not installed
            throw new RuntimeException("something went wrong", e);
        }
        return results.toString();
    }

    /**
     * Getting composites of all standard topics in given timerange.
     */
    @GET
    @Path("/all/index/{from}/{to}")
    @Produces("application/json")
    public String getStandardIndexForTimeRange(@PathParam("from") long from, @PathParam("to") long to) {
        //
        JSONArray results = new JSONArray();
        try {
            // 1) Fetch Resultset of Resources
            log.info("Loadings all standard topic composites in timerange from \"" + from + "\" to \"" + to + "\"");
            ArrayList<Topic> all_in_range = new ArrayList<Topic>();
            Collection<Topic> topics_in_range = timeService.getTopicsByCreationTime(from, to);
            Iterator<Topic> resultset = topics_in_range.iterator();
            while (resultset.hasNext()) {
                Topic in_question = resultset.next();
                if (in_question.getTypeUri().equals("dm4.notes.note") ||
                    in_question.getTypeUri().equals("dm4.files.file") ||
                    in_question.getTypeUri().equals("dm4.files.folder") ||
                    in_question.getTypeUri().equals("dm4.contacts.person") ||
                    in_question.getTypeUri().equals("dm4.contacts.institution") ||
                    in_question.getTypeUri().equals("dm4.webbrowser.web_resource")) {
                    // log.info("> " +in_question.getSimpleValue()+ " of type \"" +in_question.getTypeUri()+ "\"");
                    all_in_range.add(in_question);
                }
            }
            log.info("> Fetched  " +all_in_range.size()+ " items (" + from + ", " + to + ")" + " by time");
            // 2) Sort and fetch resources
            // ArrayList<RelatedTopic> in_memory_resources = getResultSetSortedByCreationTime(all_resources);
            for (Topic item : all_in_range) { // 2) prepare resource items
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

    public ArrayList<Topic> getTopicListSortedByCreationTime (ArrayList<Topic> all) {
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

    public ArrayList<Topic> getTopicListSortedByModificationTime (ArrayList<Topic> all) {
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

    // --- Private Utility Methods

    private void enrichTopicModelAboutCreationTimestamp (Topic resource) {
        long created = timeService.getCreationTime(resource.getId());
        ChildTopicsModel resourceModel = resource.getChildTopics().getModel();
        resourceModel.put(PROP_URI_CREATED, created);
    }

    private void enrichTopicModelAboutModificationTimestamp (Topic resource) {
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
                    "dm4.core.parent", null, 0).getItems();
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
        TopicType topicType = dms.getTopicType(topic.getTypeUri());
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
