package cs224n.coref;

import cs224n.util.Pair;

import java.util.Set;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public interface Feature {

  //-----------------------------------------------------------
  // TEMPLATE FEATURE TEMPLATES
  //-----------------------------------------------------------
  public static class PairFeature implements Feature {
    public final Pair<Feature,Feature> content;
    public PairFeature(Feature a, Feature b){ this.content = Pair.make(a, b); }
    public String toString(){ return content.toString(); }
    public boolean equals(Object o){ return o instanceof PairFeature && ((PairFeature) o).content.equals(content); }
    public int hashCode(){ return content.hashCode(); }
  }

  public static abstract class Indicator implements Feature {
    public final boolean value;
    public Indicator(boolean value){ this.value = value; }
    public boolean equals(Object o){ return o instanceof Indicator && o.getClass().equals(this.getClass()) && ((Indicator) o).value == value; }
    public int hashCode(){ 
    	return this.getClass().hashCode() ^ Boolean.valueOf(value).hashCode(); }
    public String toString(){ 
    	return this.getClass().getSimpleName() + "(" + value + ")"; }
  }

  public static abstract class IntIndicator implements Feature {
    public final int value;
    public IntIndicator(int value){ this.value = value; }
    public boolean equals(Object o){ return o instanceof IntIndicator && o.getClass().equals(this.getClass()) && ((IntIndicator) o).value == value; }
    public int hashCode(){ 
    	return this.getClass().hashCode() ^ value; 
    }
    public String toString(){ return this.getClass().getSimpleName() + "(" + value + ")"; }
  }

  public static abstract class BucketIndicator implements Feature {
    public final int bucket;
    public final int numBuckets;
    public BucketIndicator(int value, int max, int numBuckets){
      this.numBuckets = numBuckets;
      bucket = value * numBuckets / max;
      if(bucket < 0 || bucket >= numBuckets){ throw new IllegalStateException("Bucket out of range: " + value + " max="+max+" numbuckets="+numBuckets); }
    }
    public boolean equals(Object o){ return o instanceof BucketIndicator && o.getClass().equals(this.getClass()) && ((BucketIndicator) o).bucket == bucket; }
    public int hashCode(){ return this.getClass().hashCode() ^ bucket; }
    public String toString(){ return this.getClass().getSimpleName() + "(" + bucket + "/" + numBuckets + ")"; }
  }

  public static abstract class Placeholder implements Feature {
    public Placeholder(){ }
    public boolean equals(Object o){ return o instanceof Placeholder && o.getClass().equals(this.getClass()); }
    public int hashCode(){ return this.getClass().hashCode(); }
    public String toString(){ return this.getClass().getSimpleName(); }
  }

  public static abstract class StringIndicator implements Feature {
    public final String str;
    public StringIndicator(String str){ this.str = str; }
    public boolean equals(Object o){ return o instanceof StringIndicator && o.getClass().equals(this.getClass()) && ((StringIndicator) o).str.equals(this.str); }
    public int hashCode(){ return this.getClass().hashCode() ^ str.hashCode(); }
    public String toString(){ return this.getClass().getSimpleName() + "(" + str + ")"; }
  }

  public static abstract class SetIndicator implements Feature {
    public final Set<String> set;
    public SetIndicator(Set<String> set){ this.set = set; }
    public boolean equals(Object o){ return o instanceof SetIndicator && o.getClass().equals(this.getClass()) && ((SetIndicator) o).set.equals(this.set); }
    public int hashCode(){ return this.getClass().hashCode() ^ set.hashCode(); }
    public String toString(){
      StringBuilder b = new StringBuilder();
      b.append(this.getClass().getSimpleName());
      b.append("( ");
      for(String s : set){
        b.append(s).append(" ");
      }
      b.append(")");
      return b.toString();
    }
  }
  
  /*
   * TODO: If necessary, add new feature types
   */

  //-----------------------------------------------------------
  // REAL FEATURE TEMPLATES
  //-----------------------------------------------------------

  public static class CoreferentIndicator extends Indicator {
    public CoreferentIndicator(boolean coreferent){ super(coreferent); }
  }

  public static class ExactMatch extends Indicator {
    public ExactMatch(boolean exactMatch){ super(exactMatch); }
  }
  
  public static class HeadWordMatch extends Indicator {
	    public HeadWordMatch(boolean headWordMatch){ super(headWordMatch); }
	  }
  public static class TestPronoun1 extends Indicator {
	    public TestPronoun1(boolean testPronoun){ super(testPronoun); }
	  }
  
  public static class TestPronoun2 extends Indicator {
	    public TestPronoun2(boolean testPronoun){ super(testPronoun); }
	  }
  
  public static class PosTag1 extends StringIndicator {
	    public PosTag1(String posTag){ super(posTag); }
	  }

public static class PosTag2 extends StringIndicator {
	    public PosTag2(String posTag){ super(posTag); }
	  }

public static class NerTag1 extends StringIndicator {
    public NerTag1(String nerTag){ super(nerTag); }
  }

public static class NerTag2 extends StringIndicator {
    public NerTag2(String nerTag){ super(nerTag); }
  }

public static class Proper1 extends Indicator {
    public Proper1(boolean speakerTag){ super(speakerTag); }
  }

public static class Proper2 extends Indicator {
    public Proper2(boolean speakerTag){ super(speakerTag); }
  }

public static class Plural1 extends Indicator {
    public Plural1(boolean speakerTag){ super(speakerTag); }
  }

public static class Plural2 extends Indicator {
    public Plural2(boolean speakerTag){ super(speakerTag); }
  }

public static class Appositive extends Indicator {
    public Appositive(boolean speakerTag){ super(speakerTag); }
  }

public static class Length extends IntIndicator {
    public Length(int length){ super(length); }
  }

public static class Common extends IntIndicator {
    public Common(int length){ super(length); }
  }



  
  public static class SamePronoun extends Indicator {
	    public SamePronoun(boolean samePronoun){ super(samePronoun); }
	  }

  public static class SameNumber extends Indicator {
	    public SameNumber(boolean sameNumber){ super(sameNumber); }
	  }
  
  public static class SameGender extends Indicator {
	    public SameGender(boolean sameNumber){ super(sameNumber); }
	  }

  public static class DistanceBetweenMention extends IntIndicator {
	    public DistanceBetweenMention(int distanceBetweenMention){ super(distanceBetweenMention); }
	  }
  public static class DistanceBetweenSentence extends IntIndicator {
	    public DistanceBetweenSentence(int distanceBetweenSentence){ super(distanceBetweenSentence); }
	  }
  /*
   * TODO: Add values to the indicators here.
   */

}
