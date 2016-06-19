package org.deepamehta.littlehelpers;

import de.deepamehta.core.JSONEnabled;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * A JSON DTO to return a topic with the name of its creator and its context (workspace) id.
 *
 * @author Malte Reißig (<a href="mailto:malte@mikromedia.de">Mail</a>)
 */
public class ViewTopic implements JSONEnabled {
    
    JSONObject topic = new JSONObject();
    
    /** Note: Call after setTopciViewModel() */
    public void setUsername(String username) {
        try {
            topic.put("creator", username);
        } catch (JSONException ex) {
            Logger.getLogger(ViewTopic.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /** Note: Call after setTopciViewModel() */
    public void setWorkspaceId(long workspaceId) {
        try {
            topic.put("workspace", workspaceId);
        } catch (JSONException ex) {
            Logger.getLogger(ViewTopic.class.getName()).log(Level.SEVERE, null, ex);
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
