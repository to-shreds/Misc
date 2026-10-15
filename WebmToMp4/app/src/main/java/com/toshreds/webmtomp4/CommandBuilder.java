package com.toshreds.webmtomp4;

import java.util.ArrayList;
import java.util.List;

public final class CommandBuilder {
    private CommandBuilder(){}
    /** Constructs argv directly so paths containing spaces and quotes are safe. */
    public static String[] build(String input,String output,boolean lossless,int crf){
        if(input==null||input.isEmpty()||output==null||output.isEmpty())
            throw new IllegalArgumentException("Missing input or output.");
        if(!lossless&&(crf<1||crf>35))
            throw new IllegalArgumentException("CRF must be 1–35.");
        List<String> a=new ArrayList<>();
        add(a,"-hide_banner","-y","-nostdin","-i",input,
            "-map","0:v:0","-map","0:a?","-map_metadata","0","-sn","-dn",
            "-c:v","libx264","-preset","veryfast");
        if(lossless){
            // QP=0 uses lossless H.264. Do not force a chroma conversion.
            add(a,"-qp","0","-c:a","alac");
        }else{
            add(a,"-crf",Integer.toString(crf),"-pix_fmt","yuv420p",
                "-c:a","aac","-b:a","192k");
        }
        add(a,"-movflags","+faststart",output);
        return a.toArray(new String[0]);
    }
    private static void add(List<String> list,String...words){
        for(String w:words)list.add(w);
    }
}
