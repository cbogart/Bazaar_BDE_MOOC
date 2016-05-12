package basilica2.myagent.listeners;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dadamson.words.ASentenceMatcher;
import dadamson.words.SynonymSentenceMatcher;
import edu.cmu.cs.lti.basilica2.core.Event;
import edu.cmu.cs.lti.project911.utils.log.Logger;
import edu.cmu.cs.lti.project911.utils.time.TimeoutReceiver;
import edu.cmu.cs.lti.project911.utils.time.Timer;
import basilica2.agents.components.InputCoordinator;
import basilica2.agents.events.MessageEvent;
import basilica2.agents.events.PresenceEvent;
import basilica2.agents.events.PromptEvent;
import basilica2.agents.listeners.BasilicaPreProcessor;
import basilica2.agents.listeners.MessageAnnotator;
import basilica2.social.events.DormantGroupEvent;
import basilica2.social.events.DormantStudentEvent;
import basilica2.socketchat.WebsocketChatClient;
import basilica2.tutor.events.DoTutoringEvent;

import org.apache.commons.lang3.StringUtils;
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import basilica2.myagent.Topic;
import basilica2.myagent.User;


public class Register implements BasilicaPreProcessor, TimeoutReceiver
{

	
	public Register() 
    {
    	
    	topicList = new ArrayList<Topic>();
    	userList = new ArrayList<User>();
    	lastConsolidation = 0;
    	contentful = false; //whether anything contentful has been said in the chat room
    	time_slot_no = 0;  // topic id
    	dormant_group = false; //if no one is speaking in the group
    	time_slot_messages = " "; // discussion done for a topic
		String dialogueConfigFile="dialogues/dialogues-example.xml";
    	loadconfiguration(dialogueConfigFile);
    	startTimer(); //start tracking chat room activity
	}    
    
	public ArrayList<Topic> topicList;	
	public ArrayList<User> userList;
	public int lastConsolidation;
    public static Timer timer;
    public InputCoordinator src;
    public Topic currentTopic; //topic being discussed currenlty
    public boolean contentful;
    public int time_slot_no;
    public boolean dormant_group;
    public String time_slot_messages; 
    
    private double similarity_threshold = .5; //similarity expected between question (topic) being asked and discussion being done by the group
    private int activity_prompt_pulse = 2; //checking group activity after every 2 minutes
	
    public void startTimer()
    {
    	timer = new Timer(activity_prompt_pulse * 60, this);
    	timer.start();
    }
    
	private void loadconfiguration(String f)
	{
		try
		{
			DOMParser parser = new DOMParser();
			parser.parse(f);
			Document dom = parser.getDocument();
			NodeList dialogsNodes = dom.getElementsByTagName("dialogs");
			if ((dialogsNodes != null) && (dialogsNodes.getLength() != 0))
			{
				Element conceptNode = (Element) dialogsNodes.item(0);
				NodeList conceptNodes = conceptNode.getElementsByTagName("dialog");
				if ((conceptNodes != null) && (conceptNodes.getLength() != 0))
				{
					for (int i = 0; i < conceptNodes.getLength(); i++)
					{
						Element conceptElement = (Element) conceptNodes.item(i);
						String conceptName = conceptElement.getAttribute("concept");// topic name
						String conceptDetailedName = conceptElement.getAttribute("description"); //brief summary for topic
						String pokeMessage = conceptElement.getAttribute("poke_message"); //poke message if nothing contentful has been said for a topic/question recently
						
						NodeList introNodes = conceptElement.getElementsByTagName("intro"); //prompt message (topic/question)
						String introText = "";
						if ((introNodes != null) && (introNodes.getLength() != 0))
						{
							Element introElement = (Element) introNodes.item(0);
							introText = introElement.getTextContent();
						}
						
						Topic topic = new Topic(conceptName, conceptDetailedName, introText, pokeMessage);
						topicList.add(topic);
					}
				}
			}
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}	    
	}
	
	

	public Topic IsInTopicList(String concept)
	{
		for (int i = 0; i < topicList.size(); i++)
		{
			if (topicList.get(i).name.equals(concept))
			{
				return topicList.get(i);
			}
		}
		
		return null;
	}
	
	public int IsInUserList(String id)
	{
		for (int i = 0; i < userList.size(); i++)
		{
			//change it to id later
			if (userList.get(i).id.equals(id))
			{
				return i;
			}
		}
		
		return -1;
	}
	
	public void incrementScore(int increment)
	{
		for (int i = 0; i < userList.size(); i++)
		{
			userList.get(i).score += increment;
		}
	}
	
	
	public void prompt_topic()
	{
		boolean topic_found = false;
		
		for (int i = 0; i < topicList.size(); i++)
		{
			Topic topic = topicList.get(i);
			if (topic.topic_prompted == null)
			{
				PromptEvent prompt = new PromptEvent(src, topic.intro_text , "TOPIC_PROMPT");
				src.queueNewEvent(prompt);
				currentTopic = topic;
				
				Date date= new Date();
				Timestamp currentTimestamp= new Timestamp(date.getTime());
				topic.topic_prompted = currentTimestamp;
				lastConsolidation++;
				topic_found = true;
				break;
			}
		}	
		
		if(!topic_found)
		{
			reset_topics();
			prompt_topic();
		}
	}
	
	
	public void reset_topics()
	{
		for (int i = 0; i < topicList.size(); i++)
		{
			Topic topic = topicList.get(i);
			topic.topic_prompted = null;
			topic.topic_discussed = null;
		}
	}
	/**
	 * @param source the InputCoordinator - to push new events to. (Modified events don't need to be re-pushed).
	 * @param event an incoming event which matches one of this preprocessor's advertised classes (see getPreprocessorEventClasses)
	 * 
	 * Preprocess an incoming event, by modifying this event or creating a new event in response. 
	 * All original and new events will be passed by the InputCoordinator to the second-stage Reactors ("BasilicaListener" instances).
	 */
	@Override
	public void preProcessEvent(InputCoordinator source, Event event)
	{
		src = source;
		if (event instanceof MessageEvent)
		{
			MessageEvent me = (MessageEvent)event;
			String message = me.getText();
			time_slot_messages = time_slot_messages + message + " ";
	    }
		else if ((event instanceof DormantGroupEvent) || 
				 (event instanceof DormantStudentEvent && userList.size() == 1))
		{
			System.out.println("Nothing happened since long.");
			dormant_group = true;
		}
		else if (event instanceof PresenceEvent)
		{
			PresenceEvent pe = (PresenceEvent) event;
			Boolean prompt_first_topic = false;
			if (!pe.getUsername().contains("Tutor") && !source.isAgentName(pe.getUsername()))
			{

				String username = pe.getUsername();
				String userid = pe.getUserId();
				if(userid == null)
					return;
				Date date= new Date();
				Timestamp currentTimestamp= new Timestamp(date.getTime());
				int userIndex = IsInUserList(userid);
				if (pe.getType().equals(PresenceEvent.PRESENT))
				{
					System.out.println("Someone present.");
					if(userIndex == -1)
					{
						String prompt_message = "Welcome, " + username + "\n";
						
						User newuser = new User(username, userid, currentTimestamp);
						userList.add(newuser);
						System.out.println("Someone joined with id = " + userid);
						
						String discussed_topics = discussedTopics();
						if(discussed_topics == null)
						{
							if(userList.size() < 2)
							{
								prompt_message = prompt_message + "Hi! I'm VirtualRyan, joining you for the last week of Big Data and Education. In this activity, we’ll reflect on the lectures from this week, and discuss some of the core ideas.\n";
								prompt_first_topic = true;
							}
							else
							{
								prompt_message = prompt_message + "We are just starting a discussion on some of the core ideas in the lectures from this week.\n";
							}
						}
						else
						{
							prompt_message = prompt_message + "We are discussing some of the core ideas in the lectures from this week.\n";
							prompt_message = prompt_message + "We have discussed  " + discussed_topics + "\n";
							
							if(lastConsolidation > 1 && userList.size() > 2)
							{
								lastConsolidation = 0;
								prompt_message = prompt_message + "Can someone provide a summary of our current discussion for  " + username + " ?\n";
								
							}
							if(lastConsolidation > 1 && userList.size() == 2)
							{
								lastConsolidation = 0;
								prompt_message = prompt_message + userList.get(0).name + ", can you provide a summary of our current discussion for " + username  + " ?\n";
								
							}
							else
							{
								prompt_message = prompt_message + "Currently we are discussing  " + currentTopic.detailed_name + "\n";
								prompt_message = prompt_message + "Please join in.";
							}
							
													
						}
						
						PromptEvent prompt = new PromptEvent(source, prompt_message , "INTRODUCTION");
						source.queueNewEvent(prompt);
						
						if(prompt_first_topic)
						{
							prompt_topic();
						}
					}
				}
				else if (pe.getType().equals(PresenceEvent.ABSENT))
				{
					System.out.println("Someone left");
					if(userIndex != -1)
					{
					    System.out.println("Someone left with id = " + userid);
						userList.remove(userIndex);
	     				checkOutdatedTopics();
					}
				}
			}
		}
	}
	
    public String discussedTopics()
    {
    	ArrayList<String> discussed_topics = new ArrayList<String>();
    	for (int i = 0; i < topicList.size(); i++)
		{
			Topic topic = topicList.get(i);
			if (
			    topic.topic_discussed != null 
			   )
				{
					discussed_topics.add(topic.detailed_name);
				}
		}
    	
    	return  discussed_topics.size() > 0 ? StringUtils.join(discussed_topics) : null;
    }
    
	public void checkOutdatedTopics()
	{
		Timestamp oldestStudent = oldestStudent();
		
		if(oldestStudent == null)
		{
			for (int i = 0; i < topicList.size(); i++)
			{
				topicList.get(i).topic_discussed = null;
				topicList.get(i).topic_prompted = null;
			}
		}
		else
		{
			for (int i = 0; i < topicList.size(); i++)
			{
				Topic topic = topicList.get(i);
				if ((topic.topic_discussed == null || topic.topic_discussed.before(oldestStudent)) &&
					(topic.topic_prompted  == null || topic.topic_prompted.before(oldestStudent))
				   )
				{
					topicList.get(i).topic_discussed = null;
					topicList.get(i).topic_prompted = null;		
				}
			}
		}
	}
	
	public Timestamp oldestStudent()
	{
		Timestamp minTimestamp = null;
		
		for (int i = 0; i < userList.size(); i++)
		{
			if (minTimestamp == null || userList.get(i).time_of_entry.before(minTimestamp))
			{
				minTimestamp = userList.get(i).time_of_entry;
			}
		}
		
		return minTimestamp;
	}
	
	public String oldestStudentName()
	{
		Timestamp minTimestamp = null;
		String name = "";
		
		for (int i = 0; i < userList.size(); i++)
		{
			if (minTimestamp == null || userList.get(i).time_of_entry.before(minTimestamp))
			{
				minTimestamp = userList.get(i).time_of_entry;
				name = userList.get(i).name;
			}
		}
		
		return name;
	}
	
	/**
	 * @return the classes of events that this Preprocessor cares about
	 */
	@Override
	public Class[] getPreprocessorEventClasses()
	{
		//only MessageEvents will be delivered to this watcher.
		return new Class[]{MessageEvent.class, DormantGroupEvent.class, PresenceEvent.class, DormantStudentEvent.class};
	}



	@Override
	public void log(String arg0, String arg1, String arg2) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void timedOut(String arg0) {
		
		// TODO Auto-generated method stub
		
		time_slot_no++;
		Date date= new Date();
		Timestamp currentTimestamp= new Timestamp(date.getTime());
		
		ASentenceMatcher matcher = new SynonymSentenceMatcher("stopwords.txt");
		
		double similarityScore = 0;
		try{
			similarityScore = matcher.getSentenceSimilarity(time_slot_messages, currentTopic.intro_text.trim());
		}
		catch(Exception e)
		{
			System.err.println("Caught Exception: " + e.getMessage());
		}
		
		if(similarityScore > similarity_threshold)
		{
			contentful = true;
		}
		System.out.println(similarityScore+"$"+time_slot_messages+"$"+currentTopic.intro_text.trim());
		
		time_slot_messages = " ";
		
		if(dormant_group || !contentful)
		{
			if(time_slot_no == 1)
			{
				String poke_message = currentTopic.poke_message;
				PromptEvent prompt = new PromptEvent(src, poke_message , "POKING");
				src.queueNewEvent(prompt);
			}
			else
			{
				time_slot_no = 0;
				currentTopic.topic_discussed = currentTimestamp;
				prompt_topic();
			}
			dormant_group = false;
		}
		else
		{
			contentful = false;
			if(time_slot_no == 5)//constant
			{
				time_slot_no = 0;
				currentTopic.topic_discussed = currentTimestamp;
				prompt_topic();
			}
		}
		startTimer();
	}
}
