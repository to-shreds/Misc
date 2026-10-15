package com.toshreds.webmtomp4;
import java.util.Arrays;
import java.util.List;

public final class CommandBuilderTest{
    private static void check(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    private static String valueAfter(List<String> args,String key){
        int i=args.indexOf(key);
        if(i<0||i+1>=args.size())throw new AssertionError("Missing "+key);
        return args.get(i+1);
    }
    public static void main(String[] args){
        String in="saf:123.webm",out="saf:456.mp4";
        List<String> lossless=Arrays.asList(CommandBuilder.build(in,out,true,18));
        check(in.equals(valueAfter(lossless,"-i")),"SAF input stays intact");
        check(out.equals(lossless.get(lossless.size()-1)),"SAF output stays intact");
        check(lossless.contains("libx264")&&"0".equals(valueAfter(lossless,"-qp")),"lossless x264");
        check("alac".equals(valueAfter(lossless,"-c:a")),"lossless audio");
        check(!lossless.contains("yuv420p"),"no forced chroma downgrade");
        check("0:a:0?".equals(lossless.get(lossless.indexOf("-map")+3)),"optional first audio");
        check("mp4".equals(valueAfter(lossless,"-f")),"explicit MP4 muxer");
        check(valueAfter(lossless,"-movflags").contains("empty_moov"),"streamable nonseekable output");
        check(!lossless.contains("+faststart"),"no seeking or duplicated temp files");
        List<String> crf=Arrays.asList(CommandBuilder.build(in,out,false,23));
        check("23".equals(valueAfter(crf,"-crf")),"CRF respected");
        check("aac".equals(valueAfter(crf,"-c:a"))&&crf.contains("yuv420p"),"compatible codecs");
        try{CommandBuilder.build(in,out,false,51);throw new AssertionError("invalid CRF");}
        catch(IllegalArgumentException expected){}
        System.out.println("Conversion command tests passed.");
    }
}
