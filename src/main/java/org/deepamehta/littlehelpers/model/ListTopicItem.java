package org.deepamehta.littlehelpers.model;

import de.deepamehta.core.JSONEnabled;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * A data transfer object representing a topic with additionally containing the name of
 * its creator and the context (current workspace id) the topic is assigned to.
 *
 * @author Malte Reißig (<a href="mailto:malte@mikromedia.de">Mail</a>)
 */
public class ListTopicItem implements JSONEnabled {
    
    JSONObject topic = new JSONObject();
    
    /** Note: Call after setTopciViewModel() */
    public void setUsername(String username) {
        try {
            topic.put("creator", username);
        } catch (JSONException ex) {
            Logger.getLogger(ListTopicItem.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /** Note: Call after setTopciViewModel() */
    public void setWorkspaceId(long workspaceId) {
        try {
            topic.put("workspace", workspaceId);
        } catch (JSONException ex) {
            Logger.getLogger(ListTopicItem.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void setTopicViewModel(JSONObject object) {
        topic = object;
    }

    @Override
    public JSONObject toJSON() {
        return topic;
    }
}
