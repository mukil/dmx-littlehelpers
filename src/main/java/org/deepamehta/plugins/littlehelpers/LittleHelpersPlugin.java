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

import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.Inject;
import de.deepamehta.plugins.workspaces.service.WorkspacesService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.ws.rs.core.MediaType;
import org.deepamehta.plugins.littlehelpers.service.LittleHelpersService;


/**
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @website http://github.com/mukil/dm4-helpers
 * @version 0.1.0 - compatible with DeepaMehta 4.5
 *
 */

@Path("/helpers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LittleHelpersPlugin extends PluginActivator implements LittleHelpersService {

    private Logger log = Logger.getLogger(getClass().getName());

    // --- DeepaMehta Standard URIs

    /** private final static String CHILD_URI = "dm4.core.child";
    private final static String PARENT_URI = "dm4.core.parent";
    private final static String AGGREGATION = "dm4.core.aggregation"; **/

    @Inject
    WorkspacesService wsService;
    
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
    // --- Helper Methods taken from the WebclientPlugin.java by Jörg Richter
    // --

    private List<Topic> findSearchableUnits(List<? extends Topic> topics) {
        List<Topic> searchableUnits = new ArrayList();
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
