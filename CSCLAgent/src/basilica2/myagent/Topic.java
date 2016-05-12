package basilica2.myagent;

import java.sql.Timestamp;

public class Topic {
	
    public String name;
    public String detailed_name;
    public String intro_text;
    public String poke_message;
	public Timestamp topic_detected;
    public Timestamp topic_requested;
    public Timestamp topic_prompted;
    public Timestamp topic_discussed;
    
    public Topic(String topicName, String topicDetailedName, String introText, String pokeMessage) {
    	name = topicName;
    	detailed_name = topicDetailedName;
    	intro_text = introText;
    	poke_message = pokeMessage;
        topic_detected = null;
        topic_requested = null;
        topic_prompted = null;
        topic_discussed = null;
    }
}