package systems.dmx.littlehelpers.model;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import systems.dmx.accesscontrol.AccessControlService;
import systems.dmx.core.JSONEnabled;
import systems.dmx.core.RelatedTopic;
import systems.dmx.core.Topic;
import systems.dmx.core.model.ChildTopicsModel;
import systems.dmx.core.util.DMXUtils;
import static systems.dmx.tags.Constants.TAG;
import systems.dmx.workspaces.WorkspacesService;

/**
 * A data transfer object representing a topic with additionally context information,
 * the name of its creator and the current workspace (name, id) the topic resides in.
 * Very useful for presenting and when working with a list of DeepaMehta items.
 *
 * @author Malte Reißig <malte@dmx.berlin>
 */
public final class ListTopic implements JSONEnabled {
    
    JSONObject topic = new JSONObject();
    
    public ListTopic(Topic item, AccessControlService ac, WorkspacesService ws) {
        topic = item.toJSON();
        setUsername(ac.getCreator(item.getId()));
        setWorkspace(ws.getAssignedWorkspace(item.getId()));
    }

    public void includeTags(Topic item) {
        List<RelatedTopic> tags = item.getRelatedTopics(null, null, null, TAG);
        if (tags != null) {
            try {
                topic.put("tags", DMXUtils.toJSONArray(tags));
            } catch (JSONException ex) {
                Logger.getLogger(ListTopic.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
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
