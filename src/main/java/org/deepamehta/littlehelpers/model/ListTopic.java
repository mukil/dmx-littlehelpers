package org.deepamehta.littlehelpers.model;

import de.deepamehta.accesscontrol.AccessControlService;
import de.deepamehta.core.JSONEnabled;
import de.deepamehta.core.Topic;
import de.deepamehta.workspaces.WorkspacesService;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * A data transfer object representing a topic with additionally context information,
 * the name of its creator and the current workspace (name, id) the topic resides in.
 * Very useful for presenting and when working with a list of DeepaMehta items.
 *
 * @author Malte Reißig
 */
public final class ListTopic implements JSONEnabled {
    
    JSONObject topic = new JSONObject();
    
    public ListTopic(Topic item, AccessControlService ac, WorkspacesService ws) {
        topic = item.toJSON();
        setUsername(ac.getCreator(item.getId()));
        setWorkspace(ws.getAssignedWorkspace(item.getId()));
    }

    public void setUsername(String username) {
        try {
            topic.put("creator", username);
        } catch (JSONException ex) {
            Logger.getLogger(ListTopic.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void setWorkspace(Topic workspace) {
        try {
            topic.put("workspace", workspace.getSimpleValue().toString());
            topic.put("workspace_id", workspace.getId());
        } catch (JSONException ex) {
            Logger.getLogger(ListTopic.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public JSONObject toJSON() {
        return topic;
    }

}
