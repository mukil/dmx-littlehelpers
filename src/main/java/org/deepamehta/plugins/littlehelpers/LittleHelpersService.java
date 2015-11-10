package org.deepamehta.plugins.littlehelpers;


import de.deepamehta.core.Topic;
import java.util.List;
import org.deepamehta.plugins.littlehelpers.SuggestionViewModel;

/**
 * A plugin-service helping with DeepaMehta 4 plugin development.
 *
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @website http://github.com/mukil/dm4-helpers
 * @version 0.1.0 - compatible with DM 4.5
 *
 */

public interface LittleHelpersService {

    List<SuggestionViewModel> getTopicSuggestions(String query);
    
    List<Topic> getTopicSuggestions(String query, String typeUri);

}
