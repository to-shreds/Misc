package com.toshreds.webmtomp4;

import java.util.ArrayList;
import java.util.List;

/** FFmpeg argv built without shell-escaping hazards. Pure Java for fast unit tests. */
public final class CommandBuilder {
    private CommandBuilder(){}
    public static String[] build(String input,String output,boolean lossless,int crf){
        if(input==null||input.isEmpty()||output==null||output.isEmpty())
            throw new IllegalArgumentException("Missing input or output.");
        if(!lossless&&(crf<1||crf>35))
            throw new IllegalArgumentException("CRF must be 1 through 35.");
        List<String> a=new ArrayList<>();
        add(a,"-hide_banner","-y","-nostdin","-i",input,
            "-map","0:v:0","-map","0:a:0?","-map_metadata","0",
            "-sn","-dn","-c:v","libx264","-preset","veryfast");
        if(lossless){
            // Mathematically lossless H.264 + ALAC; no forced chroma subsampling.
            add(a,"-qp","0","-c:a","alac");
        }else{
            add(a,"-crf",Integer.toString(crf),"-pix_fmt","yuv420p",
                "-c:a","aac","-b:a","192k");
        }
        // SAF write descriptors may be non-seekable. Fragmented MP4 avoids
        // writing a giant second copy in temporary storage, even for 3GB inputs.
        add(a,"-movflags","+frag_keyframe+empty_moov+default_base_moof",
            "-f","mp4",output);
        return a.toArray(new String[0]);
    }
    private static void add(List<String> list,String...words){
        for(String w:words)list.add(w);
    }
}
