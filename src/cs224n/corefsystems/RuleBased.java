package cs224n.corefsystems;

import java.util.*;

import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.Entity;
import cs224n.coref.Mention;
import cs224n.coref.Pronoun;
import cs224n.util.Pair;

public class RuleBased implements CoreferenceSystem {
	
	private Document doc;
	private List<ClusteredMention> mentions;
	private List<Mention> unReferencedMentions;
	private Map<Integer, ClusteredMention> entityMap;
	private Set<Entity> discoveredEntities;

	@Override
	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {

	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) 
	{
		this.doc = doc;
		this.mentions = new ArrayList<ClusteredMention>();
		this.unReferencedMentions = new ArrayList<Mention>();
		this.entityMap = new HashMap<Integer, ClusteredMention>();
		this.discoveredEntities = new HashSet<Entity>();
		
		exactMatch();
		headMatch();
		makeRestSingleton();
		return mentions;
	}
	
	private void makeRestSingleton()
	{
		for (Mention m : unReferencedMentions)
		{
			 ClusteredMention newCluster = m.markSingleton();
		     mentions.add(newCluster);
		}
	}
	
	private void headMatch()
	{
		System.out.println(doc.prettyPrint(this.discoveredEntities));
		List<Mention> tobeRemovedMentions = new ArrayList<Mention>();
		for (int e : this.entityMap.keySet())
		{
			ClusteredMention cm = this.entityMap.get(e);
			int cmIndex = doc.indexOfMention(cm.mention);
			String cmHeadWord = cm.mention.headWord();
			int count = 0;
			for (Mention m : unReferencedMentions)
			{
				if(!Pronoun.isSomePronoun(m.gloss()) && doc.indexOfMention(m) > cmIndex)
				{
					String mHeadWord = m.headWord();
					if(cmHeadWord.contains(mHeadWord))
					{
						System.out.println(count);
						mentions.add(m.markCoreferent(cm));
					    tobeRemovedMentions.add(m);
					}
				}
				count ++;
			}
			this.unReferencedMentions.removeAll(tobeRemovedMentions);
		}
		
		
	}
	
	private void exactMatch()
	{
		Map<String,Entity> clusters = new HashMap<String,Entity>();
		Map<String,Mention> singleMentions = new HashMap<String, Mention>();
		for(Mention m : doc.getMentions()){
			String mentionString = m.gloss();
			if(clusters.containsKey(mentionString)){
				mentions.add(m.markCoreferent(clusters.get(mentionString)));
				unReferencedMentions.remove(m);
			} 
			else if(singleMentions.containsKey(mentionString))
			{
				Mention previousMention = singleMentions.get(mentionString);
				ClusteredMention newCluster = previousMention.markSingleton();
			    mentions.add(newCluster);
			    clusters.put(mentionString,newCluster.entity);
			    this.entityMap.put(newCluster.entity.uniqueID, newCluster);
			    mentions.add(m.markCoreferent(newCluster));
			    singleMentions.remove(mentionString);
			    unReferencedMentions.remove(previousMention);
			}
			else {
				unReferencedMentions.add(m);
				if (!Pronoun.isSomePronoun(m.gloss()))
				{
					singleMentions.put(mentionString,m);
				}
			}
		}
		System.out.println("Finished exact match!");
	}

}
