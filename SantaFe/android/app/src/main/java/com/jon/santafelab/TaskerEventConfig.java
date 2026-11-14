package com.jon.santafelab;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.UUID;

/** Tasker event plugin: fired after a newer Hyundai observation indicates a transition. */
public final class TaskerEventConfig extends Activity {
    private static final String[] NAMES={"Any vehicle change","Doors locked","Doors unlocked","Engine on","Engine off"};
    private static final String[] EVENTS={"any","doors_locked","doors_unlocked","engine_on","engine_off"};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);
        TextView title=new TextView(this);title.setText("Santa Fe change event");title.setTextSize(20);root.addView(title);
        TextView info=new TextView(this);info.setText("Triggered only when a newer Hyundai vehicle observation reports a changed value. This is not a real-time Digital Key feed.");root.addView(info);
        Spinner spin=new Spinner(this);spin.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,NAMES));root.addView(spin);
        Bundle old=getIntent().getBundleExtra(TaskerSecurity.BUNDLE);
        if(old!=null){String event=old.getString("event","any");for(int i=0;i<EVENTS.length;i++)if(EVENTS[i].equals(event))spin.setSelection(i);}
        Button save=new Button(this);save.setText("Save event");root.addView(save);setContentView(root);
        save.setOnClickListener(v->{
            int i=spin.getSelectedItemPosition();if(i<0||i>=EVENTS.length)return;
            Bundle out=new Bundle();out.putString("event",EVENTS[i]);
            out.putString("id",old==null?UUID.randomUUID().toString():old.getString("id",UUID.randomUUID().toString()));
            Intent result=new Intent().putExtra(TaskerSecurity.BUNDLE,out).putExtra(TaskerSecurity.BLURB,NAMES[i]);
            setResult(RESULT_OK,result);finish();
        });
    }
}
