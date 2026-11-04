package audio.sprocket.lagsense;

import java.util.*;

final class GradientChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void run(){
        int[] stops={0xff45e69b,0xffb9e65b,0xfff3d45b,0xfff59a45,0xffe94d58};
        for(int i=0;i<5;i++){
            double position=i/4.0;check(PerformanceGradient.color(position)==stops[i],"Gradient passes through each exact requested palette anchor");
            check(TimingSeverity.color(TimingSeverity.legendMicros(position))==stops[i],"Legend uses the actual shared log timing scale");
        }
        check(TimingSeverity.MIN_MICROS==0&&TimingSeverity.MAX_MICROS==10000,"One explicit fixed timing scale, independent of category/capture");
        for(double value:new double[]{-1,-Double.MAX_VALUE,Double.NEGATIVE_INFINITY,Double.NaN,0})check(TimingSeverity.color(value)==stops[0]&&TimingSeverity.position(value)==0,"Non-positive/non-measurement values safely select the low endpoint");
        for(double value:new double[]{10000,10001,Double.MAX_VALUE,Double.POSITIVE_INFINITY})check(TimingSeverity.color(value)==stops[4]&&TimingSeverity.position(value)==1,"Values above the scale safely clamp without overflow");
        double previous=-1;double previousProgress=-1;
        var colours=new HashSet<Integer>();
        for(int i=0;i<=100000;i++){
            double value=10000*i/100000.0,position=TimingSeverity.position(value);int color=TimingSeverity.color(value);
            check(position>=previous,"Larger µs/t never moves backwards on the severity scale");check(color==TimingSeverity.color(value),"Timing colours are deterministic");
            check((color>>>24)==255,"Gradient preserves existing opaque RGB so renderers retain their transparency behaviour");
            // Project rounded RGB back along its current palette segment; quantisation cannot reverse progression.
            double segment=position*4;int index=Math.min(3,(int)segment);double dot=0,length=0;
            for(int shift:new int[]{16,8,0}){int a=stops[index]>>>shift&255,b=stops[index+1]>>>shift&255,c=color>>>shift&255;dot+=(c-a)*(b-a);length+=(b-a)*(b-a);}
            double progress=index+dot/length;check(progress+1e-10>=previousProgress,"Displayed gradient colours progress monotonically after RGB quantisation");
            previous=position;previousProgress=progress;colours.add(color);
        }
        check(colours.size()>256,"Timing output is a continuous interpolated progression, not four discrete bands");
        for(double oldBoundary:new double[]{10,100,1000,5000,10000}){
            int a=TimingSeverity.color(oldBoundary-.0001),b=TimingSeverity.color(oldBoundary+.0001);
            check(distance(a,b)<=1,"No abrupt jumps at previous individual/group/event thresholds");
        }
        var entries=List.of(new Report.Entry("Entity","a:creeper","minecraft:overworld",1,64,1,2300000,100),new Report.Entry("Entity","a:creeper","minecraft:overworld",2,64,1,7500000,100));
        var report=new Report(1,1,100,1,true,false,entries).view(true,List.of());var targets=ProfileResults.targets(report,new ProfileResults.Filter("All","All",false,"",0,0,0),false);
        var group=ProfileResults.groups(targets,false).getFirst();var mod=ProfileResults.mods(targets,false,true).getFirst();var chunk=report.chunks().getFirst();var investigation=new MapTargets(report,null).chunk(chunk.dimension(),chunk.x(),chunk.z());
        check(group.micros(100)==98&&mod.micros(100)==98&&chunk.micros(100)==98&&investigation.nanos()/1000.0/100==98,"Type/mod/category/chunk aggregates retain the full sum of measured timings");
        check(TimingSeverity.color(group.micros(100))==TimingSeverity.color(mod.micros(100))&&TimingSeverity.color(chunk.micros(100))==TimingSeverity.color(98),"All aggregate levels use the same measured-cost scale");
        var otherCapture=new Report(2,2,200,1,true,false,List.of(new Report.Entry("Block","b:other","another:dimension",100,0,100,19600000,1),new Report.Entry("Block","b:expensive","another:dimension",0,0,0,500000000,1)));
        check(TimingSeverity.color(otherCapture.entries().getFirst().micros(otherCapture.ticks()))==TimingSeverity.color(98),"Unrelated expensive targets, capture duration and category cannot change a timing colour");
        for(long samples:new long[]{0,1,10,50,100})check(SampleContribution.color(samples,100)==PerformanceGradient.color(samples/100.0),"CPU contribution colours use sample ratios rather than measured timings");
        check(SampleContribution.fraction(50,100)==.5&&SampleContribution.fraction(500,1000)==.5&&SampleContribution.color(50,100)==SampleContribution.color(500,1000),"Identical sampled shares keep identical colours across sample counts");
        check(SampleContribution.color(50,100)!=TimingSeverity.color(50),"50% sampled activity is not equated with 50 µs/t");
        check(SampleContribution.color(-1,100)==stops[0]&&SampleContribution.color(1,0)==stops[0]&&SampleContribution.color(101,100)==stops[4]&&SampleContribution.color(Long.MAX_VALUE,Long.MAX_VALUE)==stops[4],"Invalid/overflowing contribution inputs are clamped safely");
        System.out.println("Gradient checks passed (100,001 monotonic timings, exact palette, smooth old boundaries, clamping, shared aggregate scale, capture independence and separate CPU contribution domain).");
    }
    private static int distance(int a,int b){int result=0;for(int shift:new int[]{16,8,0})result=Math.max(result,Math.abs((a>>>shift&255)-(b>>>shift&255)));return result;}
}
