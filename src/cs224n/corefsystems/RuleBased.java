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
import cs224n.coref.Pronoun.Type;
import cs224n.coref.Sentence;
import cs224n.coref.Util;
import cs224n.ling.Tree;
import cs224n.util.Pair;

public class RuleBased implements CoreferenceSystem {

	private Document doc;
	private List<ClusteredMention> mentions;
	private Set<Entity> discoveredEntities;
	private Map<String, Set<String>> headWordMap;
	public static final String[] STOP_WORDS_ARRAY = {"i", "me", "my", "myself", "we", "our", "ours","ourselves", "you", "your", "yours",
		"yourself", "yourselves", "he", "him", "his", "himself", "she", "her", "hers",
		"herself", "it", "its", "itself", "they", "them", "their", "theirs", "themselves",
		"what", "which", "who", "whom", "this", "that", "these", "those", "am", "is", "are",
		"was", "were", "be", "been", "being", "have", "has", "had", "having", "do", "does",
		"did", "doing", "a", "an", "the", "and", "but", "if", "or", "because", "as", "until",
		"while", "of", "at", "by", "for", "with", "about", "against", "between", "into",
		"through", "during", "before", "after", "above", "below", "to", "from", "up", "down",
		"in", "out", "on", "off", "over", "under", "again", "further", "then", "once", "here",
		"there", "when", "where", "why", "how", "all", "any", "both", "each", "few", "more",
		"most", "other", "some", "such", "no", "nor", "not", "only", "own", "same", "so",
		"than", "too", "very", "s", "t", "can", "will", "just", "don", "should", "now"};
	public static final Set<String> STOP_WORDS = new HashSet<String>(Arrays.asList(STOP_WORDS_ARRAY));
	public static final String[] NUMBERS_ARRAY= {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
	public static final  Set<String> NUMBERS = new HashSet<String>(Arrays.asList(NUMBERS_ARRAY));

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
					if (!Pronoun.isSomePronoun(headWord) && !m.gloss().toLowerCase().equals("that"))
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
		//		predicateNominative();
		//		roleAppositive();
		acronym();
		strictHeadMatch();
		variantHeadMatch();
		properHeadMatch();
		trainedHeadMatch();
		flexHeadMatch();
		makeRestSingleton();
		handlePronoun();
		//		pronounSingleton(null);
		//		System.out.println(this.doc.prettyPrint(this.discoveredEntities));
		return mentions;
	}

	private void addToResult(Mention reference, Mention tobeAdded)
	{
		Entity entity = reference.getCorefferentWith();
		if(entity == null)
		{
			ClusteredMention newCluster = reference.markSingleton();
			entity = newCluster.entity;
			this.mentions.add(newCluster);
			this.discoveredEntities.add(newCluster.entity);
		}
		this.mentions.add(tobeAdded.markCoreferent(entity));
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

	private void simpleExactMatch()
	{
		for (Mention cm : doc.getMentions())
		{
			if (!Pronoun.isSomePronoun(cm.gloss()))
			{
				for (Mention m : doc.getMentions())
				{
					if(cm != m && m.getCorefferentWith() == null && cm.gloss().equals(m.gloss()))
					{
						addToResult(cm, m);
						break;
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
							addToResult(cm, m);
						}
					}
				}
			}
		}
	}

	private void predicateNominative()
	{
		List<Mention> mentionList = doc.getMentions();
		Iterator<Mention> iterMention = mentionList.iterator();
		Mention first = null;
		Mention second = null;
		if(iterMention.hasNext())
		{
			first = iterMention.next();
		}
		if(iterMention.hasNext())
		{
			second = iterMention.next();
		}
		while(iterMention.hasNext())
		{
			if(second.getCorefferentWith() == null && doc.indexOfSentence(first.sentence) == doc.indexOfSentence(second.sentence))
			{
				List<String> words = first.sentence.words;
				String middleString = "";
				for(int i = first.endIndexExclusive+1; i<second.beginIndexInclusive; i++)
				{
					middleString += words.get(i)+ " ";
				}

				if(middleString.trim().equals("is"))
				{
					addToResult(first, second);
					break;
				}
			}
			first = second;
			second = iterMention.next();
		}
	}

	private void roleAppositive()
	{
		List<Mention> mentionList = doc.getMentions();
		Iterator<Mention> iterMention = mentionList.iterator();
		Mention first = null;
		Mention second = null;
		if(iterMention.hasNext())
		{
			first = iterMention.next();
		}
		if(iterMention.hasNext())
		{
			second = iterMention.next();
		}
		while(iterMention.hasNext())
		{
			if(second.getCorefferentWith() == null && doc.indexOfSentence(first.sentence) == doc.indexOfSentence(second.sentence))
			{	
				if(first.endIndexExclusive == second.beginIndexInclusive)
				{
					if(second.headToken().nerTag().equals("PERSON") && Name.gender(first.gloss()) != Gender.NEUTRAL)
					{
						addToResult(first, second);
					}
				}
			}
			first = second;
			second = iterMention.next();
		}
	}

	private void acronym()
	{
		for (Mention cm : doc.getMentions())
		{
			if (!Pronoun.isSomePronoun(cm.gloss()) && cm.headToken().posTag().equals("NNP"))
			{
				List<String> words = cm.sentence.words;
				int indexBegin = cm.beginIndexInclusive;
				int indexEnd = cm.endIndexExclusive;
				String acronym = "";
				for(int i=indexBegin; i<indexEnd; i++)
				{
					String w = words.get(i).toLowerCase();
					if(!this.STOP_WORDS.contains(w))
					{
						String c = words.get(i).trim().substring(0, 1).toUpperCase();
						acronym += c;
					}
				}

				for (Mention m : doc.getMentions())
				{
					if(m.getCorefferentWith() == null && m.headToken().posTag().equals("NNP"))
					{
						String wm = m.gloss();
						if(wm.equals(acronym)||(wm.length() > 4 && wm.substring(4, wm.length()).equals(acronym)))
						{
							addToResult(cm, m);
							break;
						}
					}
				}
			}
		}
	}

	private boolean notIWithinI(Mention a, Mention b)
	{
		Tree<String> at = a.parse;
		Tree<String> bt = b.parse;
		if(at.getChildren() != null)
		{
			for(Tree<String> atc : at.getChildren())
			{
				if(b.equals(atc))
				{
					System.out.println("not i within i: "+a+", "+b);
					return true;
				}
			}
		}

		if(bt.getChildren() != null)
		{
			for(Tree<String> btc : bt.getChildren())
			{
				if(a.equals(btc))
				{
					System.out.println("not i within i: "+a+", "+b);
					return true;
				}
			}
		}
		return false;
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

							if(cmHeadWord.contains(mHeadWord) && containsAll && !this.notIWithinI(cm, m))
							{
								addToResult(cm, m);
							}
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
							if(cmHeadWord.equals(mHeadWord) && !this.notIWithinI(cm, m))
							{
								//								System.out.println("Variant Head Match: "+cm+", "+m);
								addToResult(cm, m);
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
							//							System.out.println("Trained Head Match: "+cm+", "+m);
							addToResult(cm, m);
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
				for (Mention m : doc.getMentions())
				{
					if(cm != m && m.getCorefferentWith() == null && !Pronoun.isSomePronoun(m.gloss()))
					{
						String mHeadWord = m.headWord();
						if(cmHeadWord.equals(mHeadWord) && cm.headToken().nerTag().equals(m.headToken().nerTag()) && cm.headToken().isPluralNoun() == m.headToken().isPluralNoun())
						{
							boolean hasNumber = false;
							for(String n : RuleBased.NUMBERS_ARRAY)
							{
								if(m.gloss().contains(n))
								{
									hasNumber = true;
									break;
								}
							}

							if(!hasNumber)
							{
								//								System.out.println("Proper Head Match: "+cm+", "+m);
								addToResult(cm, m);
							}
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
							if(cm.gloss().contains(mHeadWord) 
									&& Name.isName(cm.gloss()) && Name.isName(m.gloss()) 
									&& cm.headToken().nerTag() == m.headToken().nerTag())
							{
								addToResult(cm, m);
							}
						}
					}
				}
			}
		}
	}

	//	private void moreFlexHeadMatch()
	//	{
	//		for (Mention cm : doc.getMentions())
	//		{
	//			if (!Pronoun.isSomePronoun(cm.gloss()))
	//			{
	//				int cmIndex = doc.indexOfMention(cm);
	//				for (Mention m : doc.getMentions())
	//				{
	//					if(cm != m && m.getCorefferentWith() == null && !Pronoun.isSomePronoun(m.gloss()))
	//					{
	//						Set<String> cms = new HashSet<String>();
	//						cms.addAll(cm.gloss().split(" "));
	//						String mHeadWord = m.headWord();
	//						if(doc.indexOfMention(m) > cmIndex)
	//						{
	//							if(cm.gloss().contains(mHeadWord) && Name.isName(cm.gloss()) && Name.isName(m.gloss()) && cm.headToken().nerTag() == m.headToken().nerTag())
	//							{
	//								addToResult(cm, m);
	//							}
	//						}
	//					}
	//				}
	//			}
	//		}
	//	}

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

		this.moreStrictPronounMatch(nonPronouns);
		this.quotePronounMatch(nonPronouns);
		this.dialogePronounMatch(nonPronouns);
		this.strictPronounMatch(nonPronouns);
		this.posessivePronounMatch(nonPronouns);
		this.flexPronounMatch(nonPronouns);
		this.moreFlexPronounMatch(nonPronouns);
		this.mostFlexPronounMatch(nonPronouns);
		//		this.mostmostFlexPronounMatch(nonPronouns);
		//		this.pronounSingleton(nonPronouns);
		this.pronounSelfMatch();
	}

	private void moreStrictPronounMatch(List<Mention> nonPronouns)
	{
		for (Mention m : this.doc.getMentions())
		{
			if (m.getCorefferentWith() == null && Pronoun.isSomePronoun(m.gloss()))
			{
				for(Mention nonPronounMention : nonPronouns)
				{
					int nonPronounSentenceIndex = this.doc.indexOfSentence(nonPronounMention.sentence);
					int pronounSentenceIndex = this.doc.indexOfSentence(m.sentence);
					if(nonPronounSentenceIndex == pronounSentenceIndex)
					{
						Pronoun pronoun = Pronoun.getPronoun(m.headWord());
						if(pronoun == null)
						{
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
							}
						}
						else
						{
							// Not Person
							if((pronoun.gender == Gender.NEUTRAL) && token.isPluralNoun() == pronoun.plural)
							{
								this.mentions.add(m.markCoreferent(nonPronounMention.getCorefferentWith()));
								break;
							}
						}
					}
				}
			}
		}
	}

	private void quotePronounMatch(List<Mention> nonPronouns)
	{
		for (Mention m : this.doc.getMentions())
		{
			if (m.getCorefferentWith() == null && Pronoun.isSomePronoun(m.gloss()))
			{
				for(Mention nonPronounMention : this.doc.getMentions())
				{
					int nonPronounSentenceIndex = this.doc.indexOfSentence(nonPronounMention.sentence);
					int pronounSentenceIndex = this.doc.indexOfSentence(m.sentence);
					if(nonPronounSentenceIndex == pronounSentenceIndex)
					{
						Pronoun pronoun = Pronoun.getPronoun(m.headWord());
						if(pronoun == null)
						{
							break;
						}
						Sentence.Token token = nonPronounMention.headToken();
						String nerTag = token.nerTag();
						if (nerTag.equals("PERSON") && pronoun.speaker == Speaker.FIRST_PERSON 
								&& !token.isQuoted() && m.headToken().isQuoted())
						{
							//							System.out.println("Quoted: "+pronoun + " & " + nonPronounMention.gloss());
							//							System.out.println(m.sentence);
							this.addToResult(nonPronounMention, m);
							break;
						}
					}
				}
			}
		}
	}

	private void dialogePronounMatch(List<Mention> nonPronouns)
	{
		for (Mention prevMention : this.doc.getMentions())
		{
			if(Pronoun.isSomePronoun(prevMention.gloss()) && prevMention.headToken().isQuoted())
			{
				Pronoun prevPronoun = Pronoun.getPronoun(prevMention.headWord());
				if(prevPronoun == null)
				{
					break;
				}
				for(Mention currMention : this.doc.getMentions())
				{
					if (currMention.getCorefferentWith() == null && Pronoun.isSomePronoun(currMention.gloss()) && currMention.headToken().isQuoted())
					{
						int prevSentenceIndex = this.doc.indexOfSentence(prevMention.sentence);
						int currSentenceIndex = this.doc.indexOfSentence(currMention.sentence);
						if(currSentenceIndex - prevSentenceIndex == 1)
						{
							Pronoun currPronoun = Pronoun.getPronoun(currMention.headWord());
							if(currPronoun == null)
							{
								break;
							}

							//Determine if they are in the same quote
							String prevPart = "";
							for (int i = prevMention.endIndexExclusive; i<prevMention.sentence.length(); i++)
							{
								prevPart += prevMention.sentence.words.get(i) + " ";
							}

							String currPart = "";
							for (int i = 0; i<currMention.beginIndexInclusive; i++)
							{
								currPart += currMention.sentence.words.get(i) + " ";
							}

							String combinedPart = prevPart + currPart;

							if(combinedPart.contains("\""))
							{
								//Separate Sentence
								if((prevPronoun.type == currPronoun.type || (prevPronoun.type == Type.SUBJECTIVE && currPronoun.type == Type.OBJECTIVE) || (prevPronoun.type == Type.OBJECTIVE && currPronoun.type == Type.SUBJECTIVE)) && prevPronoun.plural == currPronoun.plural)
								{
									if(prevPronoun.speaker == Speaker.FIRST_PERSON && currPronoun.speaker == Speaker.SECOND_PERSON)
									{
										//										System.out.println("Dialoge: "+prevPronoun + " & " + currPronoun);
										//										System.out.println(prevMention.sentence);
										//										System.out.println(currMention.sentence);
										this.addToResult(prevMention, currMention);
										break;
									}
									else if(prevPronoun.speaker == Speaker.SECOND_PERSON && currPronoun.speaker == Speaker.FIRST_PERSON)
									{
										//										System.out.println("Dialoge: "+prevPronoun + " & " + currPronoun);
										//										System.out.println(prevMention.sentence);
										//										System.out.println(currMention.sentence);
										this.addToResult(prevMention, currMention);
										break;
									}
								}
							}
							//							System.out.println("Dialoge: "+prevPronoun + " & " + currPronoun);
							//							System.out.println("Combined: "+combinedPart);
							//							System.out.println(prevMention.sentence);
							//							System.out.println(currMention.sentence);
						}
					}
				}
			}
		}
	}

	private void strictPronounMatch(List<Mention> nonPronouns)
	{
		for (Mention m : this.doc.getMentions())
		{
			if (m.getCorefferentWith() == null && Pronoun.isSomePronoun(m.gloss()))
			{
				for(Mention nonPronounMention : nonPronouns)
				{
					Pronoun pronoun = Pronoun.getPronoun(m.headWord());
					if(pronoun == null)
					{
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
						}
					}
					else
					{
						// Not Person
						if((pronoun.gender == Gender.NEUTRAL) && token.isPluralNoun() == pronoun.plural)
						{
							this.mentions.add(m.markCoreferent(nonPronounMention.getCorefferentWith()));
							break;
						}
					}
				}
			}
		}
	}

	private void posessivePronounMatch(List<Mention> nonPronouns)
	{
		for (Mention m : this.doc.getMentions())
		{
			if (m.getCorefferentWith() == null && Pronoun.isSomePronoun(m.gloss()))
			{
				for(Mention nonPronounMention : nonPronouns)
				{
					Pronoun pronoun = Pronoun.getPronoun(m.headWord());
					if(pronoun == null)
					{
						break;
					}
					Sentence.Token token = nonPronounMention.headToken();
					String nerTag = token.nerTag();
					if (nerTag.equals("PERSON"))
					{
						String s = nonPronounMention.gloss();
						if(s.length() > 1 && s.substring(s.length()-2, s.length()).equals("'s"))
						{
							if(pronoun.type == Type.POSESSIVE_DETERMINER && pronoun.speaker == Speaker.THIRD_PERSON)
							{
								if(s.contains(" and ") && pronoun.plural)
								{
									//									System.out.println("Possive: " + s + " " + pronoun);
									this.mentions.add(m.markCoreferent(nonPronounMention.getCorefferentWith()));
									break;
								}
								else if(!s.contains(" and ") && !pronoun.plural)
								{
									//									System.out.println("Possive: " + s + " " + pronoun);
									this.mentions.add(m.markCoreferent(nonPronounMention.getCorefferentWith()));
									break;
								}
							}
						}

					}
					else
					{
						// Not Person
						if((pronoun.gender == Gender.NEUTRAL) && token.isPluralNoun() == pronoun.plural)
						{
							this.mentions.add(m.markCoreferent(nonPronounMention.getCorefferentWith()));
							break;
						}
					}
				}
			}
		}
	}

	private void flexPronounMatch(List<Mention> nonPronouns)
	{
		//Start process pronoun
		for (Mention m : this.doc.getMentions())
		{
			if (m.getCorefferentWith() == null && Pronoun.isSomePronoun(m.gloss()))
			{
				for(Mention nonPronounMention : nonPronouns)
				{
					Sentence.Token token = nonPronounMention.headToken();
					String nerTag = token.nerTag();
					Pronoun pronoun = Pronoun.getPronoun(m.headWord());
					if(pronoun == null)
					{
						if(!token.isPluralNoun())
						{
							this.mentions.add(m.markCoreferent(nonPronounMention.getCorefferentWith()));
						}
						break;
					}

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
						}
					}
					else
					{
						// Not Person
						if((pronoun.gender == Gender.NEUTRAL || pronoun.gender == Gender.EITHER) && token.isPluralNoun() == pronoun.plural)
						{
							this.mentions.add(m.markCoreferent(nonPronounMention.getCorefferentWith()));
							break;
						}
					}
				}
			}
		}
	}

	private void moreFlexPronounMatch(List<Mention> nonPronouns)
	{
		//Start process pronoun
		for (Mention m : this.doc.getMentions())
		{
			if (m.getCorefferentWith() == null && Pronoun.isSomePronoun(m.gloss()))
			{
				for(Mention nonPronounMention : nonPronouns)
				{
					Sentence.Token token = nonPronounMention.headToken();
					String nerTag = token.nerTag();

					Pronoun pronoun = Pronoun.getPronoun(m.headWord());
					if(pronoun == null)
					{
						if(!token.isPluralNoun())
						{
							this.mentions.add(m.markCoreferent(nonPronounMention.getCorefferentWith()));
						}
						break;
					}

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
					}
				}
			}
		}
	}

	private void mostFlexPronounMatch(List<Mention> nonPronouns)
	{
		//Start process pronoun
		for (Mention m : this.doc.getMentions())
		{
			if (m.getCorefferentWith() == null && Pronoun.isSomePronoun(m.gloss()))
			{
				for(Mention nonPronounMention : nonPronouns)
				{
					int nonPronounSentenceIndex = this.doc.indexOfSentence(nonPronounMention.sentence);
					int pronounSentenceIndex = this.doc.indexOfSentence(m.sentence);
					if(nonPronounSentenceIndex == pronounSentenceIndex)
					{
						Sentence.Token token = nonPronounMention.headToken();
						String nerTag = token.nerTag();

						Pronoun pronoun = Pronoun.getPronoun(m.headWord());
						if(pronoun == null)
						{
							if(!token.isPluralNoun())
							{
								this.mentions.add(m.markCoreferent(nonPronounMention.getCorefferentWith()));
							}
							break;
						}

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
							}
						}
						else
						{
							if(m.getCorefferentWith() == null)
								this.mentions.add(m.markCoreferent(nonPronounMention.getCorefferentWith()));
						}
					}
				}
			}
		}
	}

	private void mostmostFlexPronounMatch(List<Mention> nonPronouns)
	{
		//Start process pronoun
		for (Mention m : this.doc.getMentions())
		{
			if (m.getCorefferentWith() == null && Pronoun.isSomePronoun(m.gloss()))
			{
				for(Mention nonPronounMention : nonPronouns)
				{
					int nonPronounSentenceIndex = this.doc.indexOfSentence(nonPronounMention.sentence);
					int pronounSentenceIndex = this.doc.indexOfSentence(m.sentence);
					if(nonPronounSentenceIndex >= pronounSentenceIndex - 3 && nonPronounSentenceIndex <= pronounSentenceIndex)
					{
						this.mentions.add(m.markCoreferent(nonPronounMention.getCorefferentWith()));
						break;
					}
				}
			}
		}
	}

	private void pronounSelfMatch()
	{
		// Speaker and Number match pronoun
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

	private void pronounSingleton(List<Mention> nonPronouns)
	{
		for (Mention m:doc.getMentions())
		{
			if(m.getCorefferentWith() == null)
			{
				ClusteredMention newCluster = m.markSingleton();
				mentions.add(newCluster);
				//				System.out.println("A Pronoun that no one wants it: "+m.gloss());
				//				for(Mention mm : nonPronouns)
				//				{
				//					System.out.println(mm.gloss()+", "+mm.headToken().isPluralNoun());
				//				}
			}
		}
	}

}
