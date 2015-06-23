package org.deepamehta.plugins.littlehelpers;

import java.util.logging.Logger;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;

import de.deepamehta.core.Topic;

import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.Inject;
import de.deepamehta.plugins.workspaces.service.WorkspacesService;
import java.util.ArrayList;
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
        List<Topic> results = getTopicSuggestions(query, "dm4.topicmaps.name");
        results.addAll(getTopicSuggestions(query, "dm4.notes.title"));
        results.addAll(getTopicSuggestions(query, "dm4.accesscontrol.username"));
        // 
        for (Topic t : results) {
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

}
