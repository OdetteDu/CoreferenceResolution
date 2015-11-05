package cs224n.corefsystems;

import cs224n.coref.*;
import cs224n.util.Pair;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;
import edu.stanford.nlp.util.logging.StanfordRedwoodConfiguration;

import java.text.DecimalFormat;
import java.util.*;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public class ClassifierBased implements CoreferenceSystem {

	private static <E> Set<E> mkSet(E[] array){
		Set<E> rtn = new HashSet<E>();
		Collections.addAll(rtn, array);
		return rtn;
	}

	private static final Set<Object> ACTIVE_FEATURES = mkSet(new Object[]{

			/*
			 * TODO: Create a set of active features
			 */

			Feature.ExactMatch.class,
//			Feature.DistanceBetweenMention.class,
//			Feature.DistanceBetweenSentence.class,
			Feature.HeadWordMatch.class,
			Feature.SamePronoun.class,
//			//skeleton for how to create a pair feature
//			Pair.make(Feature.TestPronoun1.class, Feature.TestPronoun2.class),
			Pair.make(Feature.PosTag1.class, Feature.PosTag2.class),
			Feature.SameNumber.class,
////			Feature.SameGender.class,
//			Pair.make(Feature.NerTag1.class, Feature.NerTag2.class),
			Pair.make(Feature.Proper1.class, Feature.Proper2.class),
			Pair.make(Feature.Plural1.class, Feature.Plural2.class),
//			Feature.Length.class,
//			Feature.Common.class,
	});


	private LinearClassifier<Boolean,Feature> classifier;

	public ClassifierBased(){
		StanfordRedwoodConfiguration.setup();
		RedwoodConfiguration.current().collapseApproximate().apply();
	}
	
	private boolean tryParseFloat(String value) {  
	     try {  
	         Float.parseFloat(value);  
	         return true;  
	      } catch (NumberFormatException e) {  
	         return false;  
	      }  
	}
	
	private boolean isNumber(Mention m){
		List<String> words = m.text();
	    for(int i=0; i<words.size()-1; i++){
	      if (tryParseFloat(words.get(i))){
	    	  return true;
	      }
	    }
		
		return false;
	}
	
	private float getNumber(Mention m){
		List<String> words = m.text();
	    for(int i=0; i<words.size()-1; i++){
	      if (tryParseFloat(words.get(i))){
	    	  return Float.parseFloat(words.get(i));
	      }
	    }
	    return 0;
	
	}

	public FeatureExtractor<Pair<Mention,ClusteredMention>,Feature,Boolean> extractor = new FeatureExtractor<Pair<Mention, ClusteredMention>, Feature, Boolean>() {
		private <E> Feature feature(Class<E> clazz, Pair<Mention,ClusteredMention> input, Option<Double> count){
			
			//--Variables
			Mention onPrix = input.getFirst(); //the first mention (referred to as m_i in the handout)
			Mention candidate = input.getSecond().mention; //the second mention (referred to as m_j in the handout)
			Entity candidateCluster = input.getSecond().entity; //the cluster containing the second mention


			//--Features
			if(clazz.equals(Feature.ExactMatch.class)){
				//(exact string match)
				return new Feature.ExactMatch(onPrix.gloss().equals(candidate.gloss()));
			} else if(clazz.equals(Feature.DistanceBetweenMention.class)) {
				
				return new Feature.DistanceBetweenMention(Math.abs(onPrix.doc.indexOfMention(onPrix) - onPrix.doc.indexOfMention(candidate)) );
				
			} else if(clazz.equals(Feature.DistanceBetweenSentence.class)) {
				
				return new Feature.DistanceBetweenSentence(Math.abs(onPrix.doc.indexOfSentence(onPrix.sentence) - onPrix.doc.indexOfSentence(candidate.sentence) ));
				
			} else if(clazz.equals(Feature.HeadWordMatch.class)) {
				
				return new Feature.HeadWordMatch(onPrix.headWord().equals(candidate.headWord()));
				
			} else if(clazz.equals(Feature.TestPronoun1.class)) {
				
				return new Feature.TestPronoun1( Pronoun.isSomePronoun(onPrix.gloss()));
												
			} else if(clazz.equals(Feature.TestPronoun2.class)) {
				
				return new Feature.TestPronoun2(Pronoun.isSomePronoun(candidate.gloss()));
				
			} else if(clazz.equals(Feature.SamePronoun.class)) {
				boolean samePronoun = false;
				if (Pronoun.isSomePronoun(onPrix.gloss()) && Pronoun.isSomePronoun(candidate.gloss())){
					Pronoun firstPronoun = Pronoun.getPronoun(onPrix.gloss());
					Pronoun secondPronoun = Pronoun.getPronoun(candidate.gloss());
					if (firstPronoun != null && secondPronoun != null){
						if (firstPronoun.gender.isCompatible(secondPronoun.gender) && firstPronoun.plural == secondPronoun.plural && firstPronoun.speaker.equals(secondPronoun.speaker)){
							samePronoun = true;
						}
					}
				}
				
				return new Feature.SamePronoun(samePronoun);
				
			} else if(clazz.equals(Feature.PosTag1.class)) {
				
				return new Feature.PosTag1(onPrix.headToken().posTag());
				
			} else if(clazz.equals(Feature.PosTag2.class)) {
					
					return new Feature.PosTag2(candidate.headToken().posTag());
					
			}  else if(clazz.equals(Feature.NerTag1.class)) {
				
				return new Feature.NerTag1(onPrix.headToken().nerTag());
				
			} else if(clazz.equals(Feature.NerTag2.class)) {
					
					return new Feature.NerTag2(candidate.headToken().nerTag());
					
			} else if(clazz.equals(Feature.Proper1.class)) {
				
				return new Feature.Proper1(onPrix.headToken().isProperNoun());
				
			} else if(clazz.equals(Feature.Proper2.class)) {
					
					return new Feature.Proper2(candidate.headToken().isProperNoun());
					
			} else if(clazz.equals(Feature.Plural1.class)) {
				
				return new Feature.Plural1(onPrix.headToken().isPluralNoun());
				
			} else if(clazz.equals(Feature.Plural2.class)) {
					
					return new Feature.Plural2(candidate.headToken().isPluralNoun());
					
			} else if(clazz.equals(Feature.SameNumber.class)) {
				Pair<Boolean, Boolean> sameNumber =  Util.haveNumberAndAreSameNumber(onPrix, candidate);
				return new Feature.SameNumber(sameNumber.getSecond());
			} else if(clazz.equals(Feature.SameGender.class)) {
				Pair<Boolean, Boolean> sameNumber =  Util.haveGenderAndAreSameGender(onPrix, candidate);
				return new Feature.SameGender(sameNumber.getSecond());
			} else if(clazz.equals(Feature.Length.class)) {
				return new Feature.Length(onPrix.length() - candidate.length());
			} else if(clazz.equals(Feature.Common.class)) {
				String first = onPrix.headWord();
				String second = candidate.headWord();
				int common = 0;
				int i = 0;
				while (i < first.length() && i < second.length()){
					if (first.charAt(i) == second.charAt(i) ){
						common += 1;
					}
					i += 1;
				}
				return new Feature.Common(common);
			}
			
			else {
				throw new IllegalArgumentException("Unregistered feature: " + clazz);
			}
		}

		@SuppressWarnings({"unchecked"})
		@Override
		protected void fillFeatures(Pair<Mention, ClusteredMention> input, Counter<Feature> inFeatures, Boolean output, Counter<Feature> outFeatures) {
			//--Input Features
			for(Object o : ACTIVE_FEATURES){
				if(o instanceof Class){
					//(case: singleton feature)
					Option<Double> count = new Option<Double>(1.0);
					Feature feat = feature((Class) o, input, count);
					if(count.get() > 0.0){
						inFeatures.incrementCount(feat, count.get());
					}
				} else if(o instanceof Pair){
					//(case: pair of features)
					Pair<Class,Class> pair = (Pair<Class,Class>) o;
					Option<Double> countA = new Option<Double>(1.0);
					Option<Double> countB = new Option<Double>(1.0);
					Feature featA = feature(pair.getFirst(), input, countA);
					Feature featB = feature(pair.getSecond(), input, countB);
					if(countA.get() * countB.get() > 0.0){
						inFeatures.incrementCount(new Feature.PairFeature(featA, featB), countA.get() * countB.get());
					}
				}
			}

			//--Output Features
			if(output != null){
				outFeatures.incrementCount(new Feature.CoreferentIndicator(output), 1.0);
			}
		}

		@Override
		protected Feature concat(Feature a, Feature b) {
			return new Feature.PairFeature(a,b);
		}
	};

	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
		startTrack("Training");
		//--Variables
		RVFDataset<Boolean, Feature> dataset = new RVFDataset<Boolean, Feature>();
		LinearClassifierFactory<Boolean, Feature> fact = new LinearClassifierFactory<Boolean,Feature>();
		//--Feature Extraction
		startTrack("Feature Extraction");
		for(Pair<Document,List<Entity>> datum : trainingData){
			//(document variables)
			Document doc = datum.getFirst();
			List<Entity> goldClusters = datum.getSecond();
			List<Mention> mentions = doc.getMentions();
			Map<Mention,Entity> goldEntities = Entity.mentionToEntityMap(goldClusters);
			startTrack("Document " + doc.id);
			//(for each mention...)
			for(int i=0; i<mentions.size(); i++){
				//(get the mention and its cluster)
				Mention onPrix = mentions.get(i);
				Entity source = goldEntities.get(onPrix);
				if(source == null){ throw new IllegalArgumentException("Mention has no gold entity: " + onPrix); }
				//(for each previous mention...)
				int oldSize = dataset.size();
				for(int j=i-1; j>=0; j--){
					//(get previous mention and its cluster)
					Mention cand = mentions.get(j);
					Entity target = goldEntities.get(cand);
					if(target == null){ throw new IllegalArgumentException("Mention has no gold entity: " + cand); }
					//(extract features)
					Counter<Feature> feats = extractor.extractFeatures(Pair.make(onPrix, cand.markCoreferent(target)));
					//(add datum)
					dataset.add(new RVFDatum<Boolean, Feature>(feats, target == source));
					//(stop if
					if(target == source){ break; }
				}
				//logf("Mention %s (%d datums)", onPrix.toString(), dataset.size() - oldSize);
			}
			endTrack("Document " + doc.id);
		}
		endTrack("Feature Extraction");
		//--Train Classifier
		startTrack("Minimizer");
		this.classifier = fact.trainClassifier(dataset);
		endTrack("Minimizer");
		//--Dump Weights
		startTrack("Features");
		//(get labels to print)
		Set<Boolean> labels = new HashSet<Boolean>();
		labels.add(true);
		//(print features)
		for(Triple<Feature,Boolean,Double> featureInfo : this.classifier.getTopFeatures(labels, 0.0, true, 100, true)){
			Feature feature = featureInfo.first();
			Boolean label = featureInfo.second();
			Double magnitude = featureInfo.third();
			//log(FORCE,new DecimalFormat("0.000").format(magnitude) + " [" + label + "] " + feature);
		}
		end_Track("Features");
		endTrack("Training");
	}

	public List<ClusteredMention> runCoreference(Document doc) {
		//--Overhead
		startTrack("Testing " + doc.id);
		//(variables)
		List<ClusteredMention> rtn = new ArrayList<ClusteredMention>(doc.getMentions().size());
		List<Mention> mentions = doc.getMentions();
		int singletons = 0;
		//--Run Classifier
		for(int i=0; i<mentions.size(); i++){
			//(variables)
			Mention onPrix = mentions.get(i);
			int coreferentWith = -1;
			//(get mention it is coreferent with)
			for(int j=i-1; j>=0; j--){

				ClusteredMention cand = rtn.get(j);
				
				boolean coreferent = classifier.classOf(new RVFDatum<Boolean, Feature>(
						       extractor.extractFeatures(Pair.make(onPrix, cand))));
				
				if(coreferent){
					coreferentWith = j;
					break;
				}
			}

			if(coreferentWith < 0){
				singletons += 1;
				rtn.add(onPrix.markSingleton());
			} else {
				//log("Mention " + onPrix + " coreferent with " + mentions.get(coreferentWith));
				rtn.add(onPrix.markCoreferent(rtn.get(coreferentWith)));
			}
		}
		//log("" + singletons + " singletons");
		//--Return
		endTrack("Testing " + doc.id);
		return rtn;
	}

	private class Option<T> {
		private T obj;
		public Option(T obj){ this.obj = obj; }
		public Option(){};
		public T get(){ return obj; }
		public void set(T obj){ this.obj = obj; }
		public boolean exists(){ return obj != null; }
	}
}
