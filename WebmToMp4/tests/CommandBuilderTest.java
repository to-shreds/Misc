package com.toshreds.webmtomp4;
import java.util.Arrays;
import java.util.List;
public final class CommandBuilderTest{
    private static void check(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    public static void main(String[] args){
        String in="saf:123.webm",out="/tmp/my video.mp4";
        List<String> a=Arrays.asList(CommandBuilder.build(in,out,true,18));
        check(a.contains(in)&&a.contains(out),"paths");
        check(a.contains("libx264")&&a.contains("-qp")&&a.contains("0"),"lossless video");
        check(a.contains("alac")&&!a.contains("aac"),"lossless audio");
        check(!a.contains("yuv420p"),"no deliberate chroma downgrade");
        List<String> b=Arrays.asList(CommandBuilder.build(in,out,false,23));
        check(b.contains("-crf")&&b.contains("23"),"crf");
        check(b.contains("aac")&&b.contains("yuv420p"),"compatible codecs");
        try{CommandBuilder.build(in,out,false,51);throw new AssertionError("invalid CRF");}
        catch(IllegalArgumentException expected){}
        System.out.println("Conversion command tests passed.");
    }
}
