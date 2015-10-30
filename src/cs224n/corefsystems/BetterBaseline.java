package cs224n.corefsystems;

import java.util.*;

import cs224n.coref.*;
import cs224n.util.Pair;

public class BetterBaseline implements CoreferenceSystem {

	private Map<String, Set<String>> headWordMap;

	private void addToHeadWordMap(String word1, String word2)
	{
		if (headWordMap == null)
		{
			headWordMap = new HashMap<String, Set<String>>();
		}

		if (headWordMap.containsKey(word1))
		{
			headWordMap.get(word1).add(word2);
		}
		else
		{
			Set<String> temp = new HashSet<String>();
			temp.add(word2);
			headWordMap.put(word1, temp);
		}

		if (headWordMap.containsKey(word2))
		{
			headWordMap.get(word2).add(word1);
		}
		else
		{
			Set<String> temp = new HashSet<String>();
			temp.add(word1);
			headWordMap.put(word2, temp);
		}
	}

	@Override
	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {

		for (Pair<Document, List<Entity>> pair : trainingData)
		{
			for (Entity entity : pair.getSecond())
			{
				Set<String> currentSet = new HashSet<String>();
				for (Mention m : entity.mentions)
				{
					String headWord = m.headWord();
					if (!Pronoun.isSomePronoun(headWord))
					{
						currentSet.add(headWord);
					}
				}
				String[] temp = new String[currentSet.size()];
				currentSet.toArray(temp);
				for(int i=0; i<temp.length-1; i++)
				{
					for(int j=i+1; j<temp.length; j++)
					{
						this.addToHeadWordMap(temp[i], temp[j]);
					}
				}
			}
		}
	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) {

		List<ClusteredMention> mentions = new ArrayList<ClusteredMention>();
		Map<String,Entity> clusters = new HashMap<String,Entity>();

		for(Mention m : doc.getMentions())
		{
			String mentionString = m.gloss();
			if(clusters.containsKey(mentionString))
			{
				mentions.add(m.markCoreferent(clusters.get(mentionString)));
			} 
			else 
			{
				boolean isHeadWordMatch = false;
				String headWord = m.headWord();
				if(this.headWordMap.containsKey(headWord))
				{
					for(String coreferencedHeadWord : headWordMap.get(headWord))
					{
						if (clusters.containsKey(coreferencedHeadWord))
						{
							isHeadWordMatch = true;
							mentions.add(m.markCoreferent(clusters.get(coreferencedHeadWord)));
							break;
						}
					}
				}
				
				if (!isHeadWordMatch)
				{
					ClusteredMention newCluster = m.markSingleton();
					mentions.add(newCluster);
					clusters.put(mentionString,newCluster.entity);
				}
			}
		}

		return mentions;
	}

}
