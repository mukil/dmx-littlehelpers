package org.deepamehta.littlehelpers.model;

import de.deepamehta.core.JSONEnabled;
import de.deepamehta.core.Topic;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;



/**
 * Representing a search result item for an auto-complete feature.
 * @author Malte Reißig (<a href="mailto:malte@mikromedia.de">Mail</a>)
 */
public class SearchResultItem implements JSONEnabled {
    
    Topic suggestion = null;
    Topic workspace = null;
    String workspaceMode = null;
    // String[] commands = null;
    
    public SearchResultItem (Topic topic, Topic workspace) {
        this.suggestion = topic;
        this.workspace = workspace;
        this.workspaceMode = workspace.loadChildTopics("dm4.workspaces.sharing_mode")
                .getChildTopics().getString("dm4.workspaces.sharing_mode");
    }
    
    public JSONObject toJSON() {
        try {
            return new JSONObject()
                .put("topic", suggestion.toJSON())
                .put("workspace", (workspace == null) ? "undefined" : workspace.toJSON())
                .put("workspace_mode", workspaceMode);
        } catch (JSONException ex) {
            Logger.getLogger(SearchResultItem.class.getName()).log(Level.SEVERE, null, ex);
        }
        return new JSONObject();
    }

}
