package org.deepamehta.plugins.littlehelpers;

import de.deepamehta.core.JSONEnabled;
import de.deepamehta.core.Topic;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class SuggestionViewModel implements JSONEnabled {
    
    Topic suggestion = null;
    Topic workspace = null;
    String workspaceMode = null;
    // String[] commands = null;
    
    public SuggestionViewModel (Topic topic, Topic workspace) {
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
            Logger.getLogger(SuggestionViewModel.class.getName()).log(Level.SEVERE, null, ex);
        }
        return new JSONObject();
    }

}
