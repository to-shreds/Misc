#!/usr/bin/env python3
"""Build a standalone Tasker project. Never copies the user's secret-bearing export."""
import copy, pathlib, time, uuid, xml.etree.ElementTree as E
ROOT=pathlib.Path(__file__).resolve().parents[1]
root=E.Element('TaskerData',sr='',dvi='1',tv='6.7.6-beta')
E.SubElement(root,'dmetric').text='1440.0,3120.0'
TASKS={};PROFILES=[];SCENES=[];stamp=str(int(time.time()*1000))
def text(p,k,v): x=E.SubElement(p,k);x.text=str(v);return x
def arg(p,n,v,integer=False):
    if integer:E.SubElement(p,'Int',sr='arg'+str(n),val=str(v))
    else:E.SubElement(p,'Str',sr='arg'+str(n),ve='3').text=str(v)
def action(code):
    # Native Tasker exports put the serialized reference first. Creating ve
    # before adding sr produced <Action ve="7" sr="act0"> in the broken export.
    a=E.Element('Action',sr='',ve='7');text(a,'code',code);return a
def java(code):
    a=action(474);arg(a,0,code);arg(a,1,'%sf_result');arg(a,2,1,True);return a
def call(name,p1='',p2=''):
    a=action(130);arg(a,0,name)
    z=E.SubElement(a,'Int',sr='arg1');text(z,'var','%priority')
    for n,v in [(2,p1),(3,p2),(4,''),(7,'')]:arg(a,n,v)
    for n,v in [(5,0),(6,0),(8,0),(9,0),(10,1)]:arg(a,n,v,True)
    return a
def task(name,acts):
    if not acts:raise ValueError('Task must contain at least one action: '+name)
    tid=24001+len(TASKS);t=E.SubElement(root,'Task',sr='task'+str(tid))
    for k,v in [('cdate',stamp),('edate',stamp),('id',tid),('nme',name),('pri',6),('rty',0)]:text(t,k,v)
    for i,a in enumerate(acts):a.set('sr','act'+str(i));t.append(a)
    TASKS[name]=tid;return tid
def show(name):
    a=action(47);arg(a,0,name)
    # Dialog display (2). A native Close button and navigation remain available.
    for n,v in [(1,2),(2,100),(3,100),(4,0),(5,0),(6,0),(7,1),(8,0),(9,0),(10,0)]:arg(a,n,v,True)
    return a
def destroy(name):a=action(49);text(a,'se','false');arg(a,0,name);return a
def condition(p,var,op,rhs='',sr='if'):
    cl=E.SubElement(p,'ConditionList',sr=sr);c=E.SubElement(cl,'Condition',sr='c0',ve='3')
    text(c,'lhs',var);text(c,'op',op);text(c,'rhs',rhs);return cl

task('SF Core',[java((ROOT/'tasker/core.java').read_text())])
task('SF Sync',[call('SF Core','sync')])
task('SF Pause All',[call('SF Core','pause')])
task('SF Forget Pairing',[call('SF Core','forget')])
task('SF Speak',[java((ROOT/'tasker/speak.java').read_text())])
pair=action(360);b=E.SubElement(pair,'Bundle',sr='arg0');E.SubElement(b,'Vals',sr='val')
for n,v in [(1,'Pair Tasker'),(2,'In the dashboard, open Settings and generate a Tasker pairing code.'),(3,''),(5,''),(8,'')]:arg(pair,n,v)
for n,v in [(4,180),(6,0),(7,0)]:arg(pair,n,v,True)
clear=action(549);arg(clear,0,'%input')
for n in [1,2,3]:arg(clear,n,0,True)
task('SF Pair Tasker',[pair,call('SF Core','pair','%input'),clear,call('SF Sync')])
for name,cmd in [('Lock','lock'),('Unlock','unlock'),('Start Climate','climate_start'),('Stop Climate','climate_stop'),('Lights','lights'),('Horn and Lights','horn_lights'),('Refresh Status','refresh'),('Wake and Refresh','force_refresh'),('Locate','locate')]:
    task('SF '+name,[call('SF Core','command','{"action":"'+cmd+'"}')])
# Advanced hooks accept a documented JSON event payload through Parameter 1.
task('SF Send Event',[call('SF Core','event','%par1')])
for name,kind in [('Car Connected','bluetooth_connected'),('Car Disconnected','bluetooth_disconnected'),('Home Enter','home_enter'),('Home Exit','home_exit'),('Power Connected','power_connected'),('Power Disconnected','power_disconnected'),('Android Auto Connected','android_auto_connected'),('Android Auto Disconnected','android_auto_disconnected'),('Alarm','alarm'),('NFC','nfc'),('Manual Event','manual')]:
    task('SF '+name,[call('SF Core','event','{"event":"'+kind+'"}'),call('SF Sync')])
task('SF Launch Termux',[java('''import android.content.Intent;
Intent i=context.getPackageManager().getLaunchIntentForPackage("com.termux");
if(i==null){tasker.showToast("Termux is not installed.");return "unavailable";}
i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(i);return "opened";''')])
task('SF Browser',[java('''import android.content.Intent;
import android.net.Uri;
Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("http://127.0.0.1:8293/"));
i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(i);return "opened";''')])
pages=[('Dashboard','dashboard'),('Controls','controls'),('Climate','climate'),('Automations','automations'),('Simulator','simulator'),('Settings','settings'),('Account','account'),('Diagnostics','diagnostics')]
allscenes=['SF '+n for n,_ in pages]+['SF Quick Controls','SF Offline']
close_actions=[destroy(n) for n in allscenes]
task('SF Close',close_actions)
# Network check contains no pairing token. Offline UI works even before setup.
probe='''import java.net.*;
tasker.setVariable("sf_online","0");
HttpURLConnection c=null;
try { c=(HttpURLConnection)new URL("http://127.0.0.1:8293/health").openConnection();
c.setConnectTimeout(1500);c.setReadTimeout(1500);c.setInstanceFollowRedirects(false);
if(c.getResponseCode()==200)tasker.setVariable("sf_online","1");
} catch(Exception e){} finally{if(c!=null)c.disconnect();}
return "checked";'''
for name,_ in pages:
    acts=[call('SF Close'),java(probe)]
    a=action(37);condition(a,'%sf_online',2,'1');acts+=[a,show('SF '+name),action(43),show('SF Offline'),action(38)]
    task('SF Open' if name=='Dashboard' else 'SF Open '+name,acts)
task('SF Quick Controls',[call('SF Close'),show('SF Quick Controls')])

def scene(name,w=1320,h=2600):
    s=E.SubElement(root,'Scene',sr='scene'+name);SCENES.append(name)
    for k,v in [('cdate',stamp),('edate',stamp),('heightLand',1300),('heightPort',h),('nme',name),('widthLand',2600),('widthPort',w)]:text(s,k,v)
    return s

def properties(s,name):
    p=E.SubElement(s,'PropertiesElement',sr='props')
    arg(p,0,1,True);arg(p,1,0,True);arg(p,2,'#FF101A2C');arg(p,3,0,True);arg(p,4,name);arg(p,5,'')
    E.SubElement(p,'Img',sr='arg6',ve='2');arg(p,7,'')

def label(s,index,name,value,geom,size=18):
    el=E.SubElement(s,'TextElement',sr='elements'+str(index),ve='3');text(el,'flags',4);text(el,'geom',geom)
    for n,v in [(0,name),(1,value),(4,'#FFF4F6FA'),(5,'')]:arg(el,n,v)
    for n,v in [(2,size),(3,100),(6,0),(7,0),(8,0)]:arg(el,n,v,True)

def button(s,index,name,value,target,geom):
    el=E.SubElement(s,'ButtonElement',sr='elements'+str(index),ve='3');text(el,'clickTask',TASKS[target]);text(el,'flags',4);text(el,'geom',geom)
    for n,v in [(0,name),(1,value),(4,'#FFF4F6FA'),(5,'')]:arg(el,n,v)
    for n,v in [(2,17),(3,100),(6,0)]:arg(el,n,v,True)
    E.SubElement(el,'Img',sr='arg7',ve='2')
for name,page in pages:
    s=scene('SF '+name)
    w=E.SubElement(s,'WebElement',sr='elements0',ve='2');text(w,'flags',4)
    text(w,'geom','0,0,1320,2470,0,0,2600,1170')
    arg(w,0,'Control Center');arg(w,1,0,True);arg(w,2,'http://127.0.0.1:8293/#'+page)
    # URI mode, self-handled links, phone/JavaScript access and popups enabled.
    # Only our loopback UI is configured. Never repoint this privileged element at an external page.
    for n in [3,4,5,6,7]:arg(w,n,1,True)
    button(s,1,'Close','Close','SF Close','1040,2480,260,100,2310,1180,260,100')
    button(s,2,'Sync','Sync','SF Sync','10,2480,260,100,10,1180,260,100')
    properties(s,'SF '+name)
s=scene('SF Quick Controls',1320,1150)
label(s,0,'Title','Santa Fe quick controls','40,30,1240,100,40,30,2300,100',24)
label(s,1,'Status','%SFStatus','40,135,1240,210,40,135,2300,150',16)
buttons=[('Dashboard','SF Open'),('Lock','SF Lock'),('Start climate','SF Start Climate'),('Stop climate','SF Stop Climate'),('Refresh status','SF Refresh Status'),('Pause all','SF Pause All'),('Settings','SF Open Settings'),('Close','SF Close')]
for i,(title,target) in enumerate(buttons):
    x=40+(i%2)*630;y=365+(i//2)*180
    button(s,i+2,'B'+str(i),title,target,f'{x},{y},590,150,{x},{y},590,150')
properties(s,'SF Quick Controls')
s=scene('SF Offline',1320,900)
label(s,0,'Title','Bluelink bridge is offline','40,40,1240,100,40,40,2300,100',24)
label(s,1,'Help','Open Termux and run sf-start. Leave its session running. Then tap Retry.\n\nNo Termux:Tasker plugin is needed. No command has been sent.','40,175,1240,340,40,175,2300,340',18)
for i,(title,target) in enumerate([('Open Termux','SF Launch Termux'),('Retry','SF Open'),('Close','SF Close')]):
    button(s,i+2,'B'+str(i),title,target,f'{40+i*420},570,390,160,{40+i*420},570,390,160')
properties(s,'SF Offline')

def profile(name,entry,exit=None):
    pid=25001+len(PROFILES);p=E.SubElement(root,'Profile',sr='prof'+str(pid),ve='2');PROFILES.append(pid)
    for k,v in [('cdate',stamp),('edate',stamp),('clp','true'),('id',pid),('mid0',TASKS[entry]),('nme',name)]:text(p,k,v)
    if exit:text(p,'mid1',TASKS[exit])
    return p

def guard(p,index,var='%SFEnabled',rhs='1'):
    s=E.SubElement(p,'State',sr='con'+str(index),ve='2');text(s,'code',165);condition(s,var,2,rhs)

p=profile('SF Local Heartbeat','SF Sync');t=E.SubElement(p,'Time',sr='con0')
for k,v in [('fh',-1),('fm',-1),('rep',2),('repval',2),('th',-1),('tm',-1)]:text(t,k,v)
guard(p,1)
for name,event in [('SF Monitor Started',307),('SF Phone Unlocked',1000)]:
    p=profile(name,'SF Sync');e=E.SubElement(p,'Event',sr='con0',ve='2');text(e,'code',event);text(e,'pri',0);guard(p,1)
p=profile('SF Car Bluetooth','SF Car Connected','SF Car Disconnected')
s=E.SubElement(p,'State',sr='con0',ve='2');text(s,'code',3);arg(s,0,'%SFBtName');arg(s,1,'')
guard(p,1);g=E.SubElement(p,'State',sr='con2',ve='2');text(g,'code',165);condition(g,'%SFBtName',12)
p=profile('SF Home WiFi','SF Home Enter','SF Home Exit')
s=E.SubElement(p,'State',sr='con0',ve='2');text(s,'code',160);arg(s,0,'%SFHomeWifi');arg(s,1,'');arg(s,2,'')
guard(p,1);g=E.SubElement(p,'State',sr='con2',ve='2');text(g,'code',165);condition(g,'%SFHomeWifi',12)
p=profile('SF External Event','SF Send Event')
e=E.SubElement(p,'Event',sr='con0',ve='2');text(e,'code',3050);text(e,'pri',0);arg(e,0,'%SFEvent');arg(e,1,'');arg(e,2,0,True)
# Entry task must read the external variable, not an unset parameter.
task('SF External Event Dispatch',[call('SF Core','event','%SFEvent'),call('SF Sync')]);p.find('mid0').text=str(TASKS['SF External Event Dispatch']);guard(p,1)
p=profile('SF Phone Power','SF Power Connected','SF Power Disconnected')
s=E.SubElement(p,'State',sr='con0',ve='2');text(s,'code',10);arg(s,0,0,True);guard(p,1)
p=profile('SF Phone Alarm','SF Alarm');e=E.SubElement(p,'Event',sr='con0',ve='2');text(e,'code',305);text(e,'pri',0);guard(p,1)
proj=E.SubElement(root,'Project',sr='proj0',ve='2')
for k,v in [('cdate',stamp),('id','cf1f54dc-cc4e-45b0-9bd0-9ca44f5c4631'),('name','Santa Fe Control Center'),('pids',','.join(map(str,PROFILES))),('psort','ActiveAlpha'),('scenes',','.join(SCENES)),('tids',','.join(map(str,TASKS.values())))]:text(proj,k,v)
# Match the user's native 6.7.6-beta export: scalar fields first, then children
# sorted lexicographically by serialized reference (arg1, arg10, arg2, ...).
# Keep the numeric references themselves intact because they define execution
# order. Well-formed generic XML alone did not establish Tasker compatibility.
def native_order(node):
    node[:]=sorted(node,key=lambda child:(1,child.get('sr')) if child.get('sr') is not None else (0,child.tag))
    for child in node:native_order(child)
native_order(root)
E.indent(root,space='\t')
out=ROOT/'tasker/Santa_Fe_Control_Center.prj.xml';E.ElementTree(root).write(out,encoding='utf-8',xml_declaration=False)
print(f'Generated {len(TASKS)} tasks, {len(PROFILES)} profiles, {len(SCENES)} scenes: {out.name}')
