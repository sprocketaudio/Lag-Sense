package net.sprocketgames.lagsense;

/** Shared progression only. Timing and sampled contribution define their own independent domains. */
public final class PerformanceGradient {
    private static final int[] STOPS={0xff45e69b,0xffb9e65b,0xfff3d45b,0xfff59a45,0xffe94d58};
    public static int color(double fraction){
        if(!(fraction>0))return STOPS[0];if(fraction>=1)return STOPS[4];
        double position=fraction*4;int index=(int)position;return interpolate(STOPS[index],STOPS[index+1],position-index);
    }
    private static int interpolate(int a,int b,double fraction){
        int r=channel(a>>>16&255,b>>>16&255,fraction),g=channel(a>>>8&255,b>>>8&255,fraction),blue=channel(a&255,b&255,fraction);
        return 0xff000000|r<<16|g<<8|blue;
    }
    private static int channel(int a,int b,double fraction){return (int)Math.round(a+(b-a)*fraction);}
    private PerformanceGradient(){}
}
