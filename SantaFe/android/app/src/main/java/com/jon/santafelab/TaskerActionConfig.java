package com.jon.santafelab;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

/** Native Locale/Tasker action editor, no Hyundai secrets in returned bundles. */
public final class TaskerActionConfig extends Activity {
    private static final String[] NAMES={"Read cached status","Lock doors","Unlock doors","Remote start: 72°F","Remote start: 62°F","Remote start: 81°F","Stop remote start"};
    private static final String[] ACTIONS={"status","lock","unlock","normal","cool","warm","stop"};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setPadding(24,24,24,24);root.setOrientation(LinearLayout.VERTICAL);
        TextView title=new TextView(this);title.setText("Santa Fe Tasker action");title.setTextSize(20);root.addView(title);
        TextView info=new TextView(this);
        info.setText("Cached status checks run in the background. Vehicle commands open a confirmation notification; remote starts also require confirming the car is outdoors.");
        root.addView(info);
        Spinner choices=new Spinner(this);choices.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,NAMES));
        root.addView(choices);
        Bundle old=getIntent().getBundleExtra(TaskerSecurity.BUNDLE);
        if(old!=null){String action=old.getString("action","status");for(int i=0;i<ACTIONS.length;i++)if(ACTIONS[i].equals(action))choices.setSelection(i);}
        Button save=new Button(this);save.setText("Save Tasker action");root.addView(save);setContentView(root);
        save.setOnClickListener(v->{
            int i=choices.getSelectedItemPosition();if(i<0||i>=ACTIONS.length)return;
            Bundle out=new Bundle();out.putString("action",ACTIONS[i]);out.putString("auth",TaskerSecurity.token(this));
            Intent data=new Intent();data.putExtra(TaskerSecurity.BUNDLE,out);data.putExtra(TaskerSecurity.BLURB,NAMES[i]);
            setResult(RESULT_OK,data);finish();
        });
    }
}
