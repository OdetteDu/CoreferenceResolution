package cs224n.corefsystems;

import java.util.*;

import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.Entity;
import cs224n.coref.Gender;
import cs224n.coref.Mention;
import cs224n.coref.Name;
import cs224n.coref.Pronoun;
import cs224n.coref.Pronoun.Speaker;
import cs224n.coref.Sentence;
import cs224n.coref.Util;
import cs224n.util.Pair;

public class RuleBased implements CoreferenceSystem {

	private Document doc;
	private List<ClusteredMention> mentions;
	private Set<Entity> discoveredEntities;
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
	public List<ClusteredMention> runCoreference(Document doc) 
	{
		this.doc = doc;
		this.mentions = new ArrayList<ClusteredMention>();
		this.discoveredEntities = new HashSet<Entity>();

		exactMatch();
		relaxedStringMatch();
		strictHeadMatch();
		variantHeadMatch();
		properHeadMatch();
		trainedHeadMatch();
		flexHeadMatch();
		makeRestSingleton();
		handlePronoun();
		return mentions;
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

	private void pronounExactMatch()
	{
		Map<String,Entity> clusters = new HashMap<String,Entity>();
		Map<String,Mention> singleMentions = new HashMap<String, Mention>();
		for(Mention m : doc.getMentions()){
			if(m.getCorefferentWith() == null)
			{
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
					if(Pronoun.isSomePronoun(m.gloss()))
					{
						singleMentions.put(mentionString,m);
					}
				}
			}
		}
	}

	private void strictHeadMatch()
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
							boolean containsAll = true;
							for(String s : m.text())
							{
								if(!cmText.contains(s))
								{
									containsAll = false;
									break;
								}
							}
							if(cmHeadWord.contains(mHeadWord) && containsAll)
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

	private void relaxedStringMatch()
	{
		for (Mention cm : doc.getMentions())
		{
			if (!Pronoun.isSomePronoun(cm.gloss()))
			{
				List<String> cmWords = cm.sentence.words;
				String cmDroppedString = "";
				for(int i=cm.beginIndexInclusive; i<=cm.headWordIndex; i++)
				{
					cmDroppedString += cmWords.get(i) + " ";
				}
				
//				System.out.println("Mention: "+cm.gloss()+" Head: "+cm.headWord());
//				System.out.println("Dropped: "+droppedString);
//				System.out.println(cm.beginIndexInclusive+", "+cm.headWordIndex+", "+cm.endIndexExclusive);
//				System.out.println(cm.sentence.toString());
				for (Mention m : doc.getMentions())
				{
					if(cm != m && m.getCorefferentWith() == null && !Pronoun.isSomePronoun(m.gloss()))
					{
						List<String> mWords = m.sentence.words;
						String mDroppedString = "";
						for(int i=m.beginIndexInclusive; i<=m.headWordIndex; i++)
						{
							mDroppedString += mWords.get(i) + " ";
						}
						
						if(cmDroppedString.equals(mDroppedString))
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

	private void variantHeadMatch()
	{
		for (Mention cm : doc.getMentions())
		{
			if (!Pronoun.isSomePronoun(cm.gloss()))
			{
				int cmIndex = doc.indexOfMention(cm);
				String cmHeadWord = cm.headWord();
				for (Mention m : doc.getMentions())
				{
					if(cm != m && m.getCorefferentWith() == null && !Pronoun.isSomePronoun(m.gloss()))
					{
						String mHeadWord = m.headWord();
						if(doc.indexOfMention(m) > cmIndex)
						{
							if(cmHeadWord.contains(mHeadWord))
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

	private void trainedHeadMatch() {
		for (Mention cm : doc.getMentions())
		{
			if (!Pronoun.isSomePronoun(cm.gloss()))
			{
				String cmHeadWord = cm.headWord();
				for (Mention m : doc.getMentions())
				{
					if(cm != m && m.getCorefferentWith() == null && !Pronoun.isSomePronoun(m.gloss()))
					{
						String headWord = m.headWord();
						if(this.headWordMap.containsKey(headWord) && headWordMap.get(headWord).contains(cmHeadWord) && doc.indexOfMention(m) > doc.indexOfMention(cm))
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

	private void properHeadMatch()
	{
		for (Mention cm : doc.getMentions())
		{
			if (!Pronoun.isSomePronoun(cm.gloss()))
			{
				String cmHeadWord = cm.headWord();
				int cmIndex = doc.indexOfMention(cm);
				for (Mention m : doc.getMentions())
				{
					if(cm != m && m.getCorefferentWith() == null && !Pronoun.isSomePronoun(m.gloss()))
					{
						String mHeadWord = m.headWord();
						if(doc.indexOfMention(m) > cmIndex && cmHeadWord.contains(mHeadWord) && cm.headToken().nerTag().equals(m.headToken().nerTag()) && cm.headToken().isPluralNoun() == m.headToken().isPluralNoun())
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

	private void flexHeadMatch()
	{
		for (Mention cm : doc.getMentions())
		{
			if (!Pronoun.isSomePronoun(cm.gloss()))
			{
				int cmIndex = doc.indexOfMention(cm);
				for (Mention m : doc.getMentions())
				{
					if(cm != m && m.getCorefferentWith() == null && !Pronoun.isSomePronoun(m.gloss()))
					{
						String mHeadWord = m.headWord();
						if(doc.indexOfMention(m) > cmIndex)
						{
							if(cm.gloss().contains(mHeadWord) && Name.isName(cm.gloss()) && Name.isName(m.gloss()) && cm.headToken().nerTag() == m.headToken().nerTag())
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
		List<Mention> nonPronouns = new ArrayList<Mention>();

		for (Mention m : this.doc.getMentions())
		{
			if (!Pronoun.isSomePronoun(m.gloss()))
			{
				nonPronouns.add(m);
			}
		}

		//Start process pronoun
		for (Mention m : this.doc.getMentions())
		{
			if (Pronoun.isSomePronoun(m.gloss()))
			{
				for(Mention nonPronounMention : nonPronouns)
				{
					//							System.out.println(nonPronounMention+", "+m);
					Pronoun pronoun = Pronoun.getPronoun(m.headWord());
					if(pronoun == null)
					{
						//TODO, if pronoun = one, it is null, need to handle this case
						System.out.println(m+", "+m.headWord());
						break;
					}
					Sentence.Token token = nonPronounMention.headToken();
					String nerTag = token.nerTag();
					if (nerTag.equals("PERSON"))
					{
						// Person
						Pair<Boolean, Boolean> haveNumberAndSameNumber = Util.haveNumberAndAreSameNumber(nonPronounMention, m);
						if(haveNumberAndSameNumber.getFirst() && haveNumberAndSameNumber.getSecond())
						{
							Pair<Boolean, Boolean> haveGenderAndSameGender = Util.haveGenderAndAreSameGender(nonPronounMention, m);
							if((haveGenderAndSameGender.getFirst() && haveGenderAndSameGender.getSecond()))
							{
								this.mentions.add(m.markCoreferent(nonPronounMention.getCorefferentWith()));
								break;
							}
							else if(pronoun.gender != Gender.NEUTRAL)
							{
								this.mentions.add(m.markCoreferent(nonPronounMention.getCorefferentWith()));
								break;
							}
							else
							{
								//										System.out.println("Not have gender: "+nonPronounMention.gloss() + ": "+pronoun);
							}
						}
						else
						{
							//									System.out.println("Plural: "+pronoun+": "+pronoun.plural);
							//									System.out.println("Plural: "+nonPronounMention+": "+token.isPluralNoun());
						}

					}
					else
					{
						// Not Person
						if(token.isPluralNoun() == pronoun.plural)
						{
							this.mentions.add(m.markCoreferent(nonPronounMention.getCorefferentWith()));
							break;
						}
						else
						{
							//TODO
						}
					}
				}
			}
		}

		for (Mention m : this.doc.getMentions())
		{
			Entity[] singleEntities = new Entity[4];
			Entity[] doubleEntities = new Entity[3];
			if(m.getCorefferentWith() == null)
			{
				Pronoun pronoun = Pronoun.getPronoun(m.headWord());
				if(pronoun == null)
				{
					if(singleEntities[3] == null)
					{
						ClusteredMention newCluster = m.markSingleton();
						mentions.add(newCluster);
						singleEntities[3] = newCluster.entity;
					}
					else
					{
						this.mentions.add(m.markCoreferent(singleEntities[3]));
					}
				}
				else
				{
					if(pronoun.plural)
					{
						int index;
						if(pronoun.speaker == Speaker.FIRST_PERSON)
						{
							index = 0;
						}
						else if(pronoun.speaker == Speaker.SECOND_PERSON)
						{
							index = 1;
						}
						else
						{
							index = 2;
						}

						if(doubleEntities[index] == null)
						{
							ClusteredMention newCluster = m.markSingleton();
							mentions.add(newCluster);
							doubleEntities[index] = newCluster.entity;
						}
						else
						{
							this.mentions.add(m.markCoreferent(doubleEntities[index]));
						}
					}
					else
					{
						int index;
						if(pronoun.speaker == Speaker.FIRST_PERSON)
						{
							index = 0;
						}
						else if(pronoun.speaker == Speaker.SECOND_PERSON)
						{
							index = 1;
						}
						else
						{
							index = 2;
						}

						if(singleEntities[index] == null)
						{
							ClusteredMention newCluster = m.markSingleton();
							mentions.add(newCluster);
							singleEntities[index] = newCluster.entity;
						}
						else
						{
							this.mentions.add(m.markCoreferent(singleEntities[index]));
						}
					}
				}
			}
		}

		for (Mention m:doc.getMentions())
		{
			if(m.getCorefferentWith() == null)
			{
				ClusteredMention newCluster = m.markSingleton();
				mentions.add(newCluster);
				System.out.println("A Pronoun that no one wants it: "+m.gloss());
				for(Mention mm : nonPronouns)
				{
					System.out.println(mm.gloss()+", "+mm.headToken().isPluralNoun());
				}
			}
		}
	}

}
