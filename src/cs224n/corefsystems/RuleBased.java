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
//		System.out.println(doc.prettyPrint(this.discoveredEntities));
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
//		System.out.println(doc.prettyPrint(this.discoveredEntities));
		List<Mention> tobeRemovedMentions = new ArrayList<Mention>();
		List<ClusteredMention> tobeAddedMentions = new ArrayList<ClusteredMention>();
		for (ClusteredMention cm : this.mentions)
		{
			int cmIndex = doc.indexOfMention(cm.mention);
			String cmHeadWord = cm.mention.headWord();
			List<String> cmText = cm.mention.text();
			for (Mention m : unReferencedMentions)
			{
				String mHeadWord = m.headWord();
				if(doc.indexOfMention(m) > cmIndex)
				{
					if(cmHeadWord.equals(mHeadWord))
					{
						tobeAddedMentions.add(m.markCoreferent(cm));
					    tobeRemovedMentions.add(m);
					}
					else
					{
						boolean containsAll = true;
						for(String s : m.text())
						{
							if(!cmText.contains(s))
							{
								containsAll = false;
								break;
							}
						}
						if (containsAll)
						{
							tobeAddedMentions.add(m.markCoreferent(cm));
						    tobeRemovedMentions.add(m);
						}
					}
				}
			}
			this.unReferencedMentions.removeAll(tobeRemovedMentions);
			tobeRemovedMentions = new ArrayList<Mention>();
		}
		this.mentions.addAll(tobeAddedMentions);

		Map<Mention, ClusteredMention> addedMentionMap = new HashMap<Mention, ClusteredMention>();
		for (Mention cm : this.unReferencedMentions)
		{
			if(cm.getCorefferentWith() != null)
			{
				continue;
			}
			int cmIndex = doc.indexOfMention(cm);
			String cmHeadWord = cm.headWord();
			List<String> cmText = cm.text();
			for (Mention m : unReferencedMentions)
			{
				if(cm == m || m.getCorefferentWith() != null)
				{
					continue;
				}
				String mHeadWord = m.headWord();
				if(doc.indexOfMention(m) > cmIndex)
				{
					if(cmHeadWord.equals(mHeadWord))
					{
						if(addedMentionMap.containsKey(cm))
						{
							this.mentions.add(m.markCoreferent(addedMentionMap.get(cm)));
							tobeRemovedMentions.add(m);
						}
						else
						{
							ClusteredMention newCluster = cm.markSingleton();
						    this.mentions.add(newCluster);
						    addedMentionMap.put(cm,newCluster);
						    this.entityMap.put(newCluster.entity.uniqueID, newCluster);
						    this.discoveredEntities.add(newCluster.entity);
						    tobeRemovedMentions.add(cm);
						    
						    this.mentions.add(m.markCoreferent(newCluster));
						    tobeRemovedMentions.add(m);
						}
					}
					else
					{
						boolean containsAll = true;
						for(String s : m.text())
						{
							if(!cmText.contains(s))
							{
								containsAll = false;
								break;
							}
						}
						if (containsAll)
						{
							if(addedMentionMap.containsKey(cm))
							{
								this.mentions.add(m.markCoreferent(addedMentionMap.get(cm)));
								tobeRemovedMentions.add(m);
							}
							else
							{
								ClusteredMention newCluster = cm.markSingleton();
							    this.mentions.add(newCluster);
							    addedMentionMap.put(cm,newCluster);
							    this.entityMap.put(newCluster.entity.uniqueID, newCluster);
							    this.discoveredEntities.add(newCluster.entity);
							    tobeRemovedMentions.add(cm);
							    
							    this.mentions.add(m.markCoreferent(newCluster));
							    tobeRemovedMentions.add(m);
							}
						}
					}
				}
			}
		}
		this.unReferencedMentions.removeAll(tobeRemovedMentions);
		tobeRemovedMentions = new ArrayList<Mention>();
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
			    this.discoveredEntities.add(newCluster.entity);
			    mentions.add(m.markCoreferent(newCluster));
			    singleMentions.remove(mentionString);
			    unReferencedMentions.remove(previousMention);
			}
			else {
				unReferencedMentions.add(m);
				singleMentions.put(mentionString,m);
			}
		}
	}

}
