package com.jon.santafelab;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
public final class TaskerStateConfig extends Activity {
    static final String[] NAMES={"Doors locked","Doors unlocked","Engine on","Engine off"};
    static final String[] STATES={"doors_locked","doors_unlocked","engine_on","engine_off"};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);
        TextView title=new TextView(this);title.setText("Santa Fe observed state");title.setTextSize(20);root.addView(title);
        TextView info=new TextView(this);info.setText("This condition is Unknown if the vehicle's last observation is older than 30 minutes.");root.addView(info);
        Spinner spin=new Spinner(this);spin.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,NAMES));root.addView(spin);
        Bundle old=getIntent().getBundleExtra(TaskerSecurity.BUNDLE);
        if(old!=null){String state=old.getString("state","doors_locked");for(int i=0;i<STATES.length;i++)if(STATES[i].equals(state))spin.setSelection(i);}
        Button save=new Button(this);save.setText("Save state");root.addView(save);setContentView(root);
        save.setOnClickListener(v->{
            int i=spin.getSelectedItemPosition();if(i<0||i>=STATES.length)return;
            Bundle out=new Bundle();out.putString("state",STATES[i]);
            Intent result=new Intent().putExtra(TaskerSecurity.BUNDLE,out).putExtra(TaskerSecurity.BLURB,NAMES[i]);
            setResult(RESULT_OK,result);finish();
        });
    }
}
