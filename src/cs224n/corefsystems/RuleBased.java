package cs224n.corefsystems;

import java.util.*;

import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.Entity;
import cs224n.coref.Mention;
import cs224n.coref.Name;
import cs224n.coref.Pronoun;
import cs224n.coref.Sentence;
import cs224n.coref.Util;
import cs224n.util.Pair;

public class RuleBased implements CoreferenceSystem {

	private Document doc;
	private List<ClusteredMention> mentions;
	private Set<Entity> discoveredEntities;

	@Override
	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {

	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) 
	{
		this.doc = doc;
		this.mentions = new ArrayList<ClusteredMention>();
		this.discoveredEntities = new HashSet<Entity>();

		exactMatch();
		headMatch();
		makeRestSingleton();
		handlePronoun();
		return mentions;
	}

	private void makeRestSingleton()
	{
		for (Mention m : this.doc.getMentions())
		{
			if(!Pronoun.isSomePronoun(m.gloss()) && m.getCorefferentWith() == null)
			{
				ClusteredMention newCluster = m.markSingleton();
				mentions.add(newCluster);
			}
		}
	}
	
	private void handlePronoun()
	{
		Map<Integer, List<Mention>> nonPronounMap = new HashMap<Integer, List<Mention>>();
		
		for (Mention m : this.doc.getMentions())
		{
			if (!Pronoun.isSomePronoun(m.gloss()))
			{
				int sentenceIndex = this.doc.indexOfSentence(m.sentence);
				List<Mention> nonPronouns;
				if(!nonPronounMap.containsKey(sentenceIndex))
				{
					nonPronouns = new ArrayList<Mention>();
					nonPronounMap.put(sentenceIndex, nonPronouns);
				}
				else
				{
					nonPronouns = nonPronounMap.get(sentenceIndex);
				}
				nonPronouns.add(m);
			}
		}
		
		//Start process pronoun
		for (Mention m : this.doc.getMentions())
		{
			if (Pronoun.isSomePronoun(m.gloss()))
			{
				int sentenceIndex = this.doc.indexOfSentence(m.sentence);
				for(int i = sentenceIndex - 3; i<=sentenceIndex; i++) //TODO: Test if we do it backwards, will it perform better?
				{
					if(m.getCorefferentWith() == null && nonPronounMap.containsKey(i))
					{
						for(Mention nonPronounMention : nonPronounMap.get(i))
						{
							System.out.println(nonPronounMention+", "+m);
							Sentence.Token token = nonPronounMention.headToken();
							boolean isNoun = token.isNoun();
							boolean isPluralNoun = token.isPluralNoun();
							boolean isProperNoun = token.isProperNoun();
							boolean isQuoted = token.isQuoted();
							String lemma = token.lemma();
							String nerTag = token.nerTag();
							String posTag = token.posTag();
							String speaker = token.speaker();
							String word = token.word();
							//Check if m agree with nonPronounMention
							if(Name.isName(nonPronounMention.gloss()))
							{
								System.out.println("Name");
								System.out.println(Util.haveGenderAndAreSameGender(nonPronounMention, m));
								System.out.println(Util.haveNumberAndAreSameNumber(nonPronounMention, m));
							}
							else
							{
								System.out.println("Not name");
							}
						}
					}
				}
			}
		}
		
		//TODO Need to be removed!
		for (Mention m : this.doc.getMentions())
		{
			if(m.getCorefferentWith() == null)
			{
				ClusteredMention newCluster = m.markSingleton();
				mentions.add(newCluster);
			}
		}
	}

	private void headMatch()
	{
		for (Mention cm : doc.getMentions())
		{
			if (!Pronoun.isSomePronoun(cm.gloss()))
			{
				int cmIndex = doc.indexOfMention(cm);
				String cmHeadWord = cm.headWord();
				List<String> cmText = cm.text();
				for (Mention m : doc.getMentions())
				{
					if(cm != m && m.getCorefferentWith() == null && !Pronoun.isSomePronoun(m.gloss()))
					{
						String mHeadWord = m.headWord();
						if(doc.indexOfMention(m) > cmIndex)
						{
							if(cmHeadWord.equals(mHeadWord))
							{
								Entity cmEntity = cm.getCorefferentWith();
								if(cmEntity == null)
								{
									ClusteredMention newCluster = cm.markSingleton();
									cmEntity = newCluster.entity;
									this.mentions.add(newCluster);
									this.discoveredEntities.add(newCluster.entity);
								}
								this.mentions.add(m.markCoreferent(cmEntity));
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
									Entity cmEntity = cm.getCorefferentWith();
									if(cmEntity == null)
									{
										ClusteredMention newCluster = cm.markSingleton();
										cmEntity = newCluster.entity;
										this.mentions.add(newCluster);
										this.discoveredEntities.add(newCluster.entity);
									}
									this.mentions.add(m.markCoreferent(cmEntity));
								}
							}
						}
					}

				}
			}

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
			} 
			else if(singleMentions.containsKey(mentionString))
			{
				Mention previousMention = singleMentions.get(mentionString);
				ClusteredMention newCluster = previousMention.markSingleton();
				mentions.add(newCluster);
				clusters.put(mentionString,newCluster.entity);
				this.discoveredEntities.add(newCluster.entity);
				mentions.add(m.markCoreferent(newCluster));
				singleMentions.remove(mentionString);
			}
			else {
				if(!Pronoun.isSomePronoun(m.gloss()))
				{
					singleMentions.put(mentionString,m);
				}
			}
		}
	}

}
