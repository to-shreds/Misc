'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.join(__dirname,'..');
const read=(name)=>fs.readFileSync(path.join(root,name),'utf8');
const provider=read('android/app/src/main/java/com/jon/santafelab/SantaFeWidgetProvider.java');
const jobs=read('android/app/src/main/java/com/jon/santafelab/StatusJob.java');
const activity=read('android/app/src/main/java/com/jon/santafelab/MainActivity.java');
const manifest=read('android/app/src/main/AndroidManifest.xml');
const build=read('android/build.sh');

test('every widget has a background tap target and all explicitly bound controls',()=>{
  for(const size of ['compact','controls','dashboard']){
    const xml=read('android/app/src/main/res/layout/widget_'+size+'.xml');
    assert.match(xml,/<LinearLayout android:id="@\+id\/widget_root"/);
    for(const id of ['w_open','w_refresh'])assert.ok(xml.includes('android:id="@+id/'+id+'"'));
    if(size!=='compact') for(const id of ['w_lock','w_unlock','w_start','w_stop'])
      assert.ok(xml.includes('android:id="@+id/'+id+'"'));
  }
  for(const id of ['widget_root','w_open','w_refresh','w_lock','w_unlock','w_start','w_stop'])
    assert.ok(provider.includes('R.id.'+id));
  assert.match(provider,/setOnClickPendingIntent\(R\.id\.widget_root,dashboard\)/);
});
test('the app creates immutable instance-specific Android PendingIntents',()=>{
  assert.match(provider,/code\(int widgetId,int operation\)/);
  assert.match(provider,/santafe-widget:\/\/open\//);
  assert.match(provider,/santafe-widget:\/\/refresh\//);
  assert.match(provider,/PendingIntent\.getActivity\(/);
  assert.match(provider,/PendingIntent\.getBroadcast\(/);
  assert.ok((provider.match(/PendingIntent\.FLAG_IMMUTABLE/g)||[]).length>=2);
  assert.match(provider,/if\(!"dashboard"\.equals\(operation\)\)i\.putExtra\("santa_fe_action",operation\)/);
  assert.match(provider,/OPEN_"\+operation\.toUpperCase/);
});
test('widget rebinds on lifecycle changes even without optional network monitoring',()=>{
  for(const name of ['onUpdate','onEnabled','onDisabled','onRestored','onAppWidgetOptionsChanged'])
    assert.ok(provider.includes('void '+name+'('),name);
  assert.ok(provider.includes('Intent.ACTION_MY_PACKAGE_REPLACED'));
  assert.ok(provider.includes('Intent.ACTION_BOOT_COMPLETED'));
  assert.ok(manifest.includes('android.intent.action.MY_PACKAGE_REPLACED'));
  assert.ok(manifest.includes('android.intent.action.BOOT_COMPLETED'));
  assert.ok(activity.includes('SantaFeWidgetProvider.redrawIfInstalled(getApplicationContext())'));
  assert.match(jobs,/WIDGET_REPAIR=92343/);
  assert.match(jobs,/setPeriodic\(FIFTEEN_MINUTES\)\.setPersisted\(true\)/);
  assert.match(jobs,/if\(params\.getJobId\(\)==WIDGET_REPAIR\)/);
  assert.match(jobs,/SantaFeWidgetProvider\.redrawAll\(getApplicationContext\(\)\)/);
  assert.match(jobs,/if\(!enabled\)\{s\.cancel\(WIDGET_REPAIR\);return;\}/);
});
test('manual refresh shows feedback but cannot send a vehicle command',()=>{
  assert.match(provider,/StatusJob\.manualRefreshRequested\(c\)/);
  assert.match(provider,/StatusJob\.enqueue\(c\)/);
  assert.match(jobs,/Refreshing…/);
  assert.match(jobs,/Refresh failed/);
  assert.match(jobs,/con\.setRequestMethod\("GET"\)/);
  assert.doesNotMatch(jobs,/setRequestMethod\("POST"\)|api\.telematics\.hyundaiusa\.com/);
  assert.match(build,/SF_VERSION_CODE:-10/);
  assert.match(build,/SF_VERSION_NAME:-1\.0\.3/);
});
