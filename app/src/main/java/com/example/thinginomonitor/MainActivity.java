package com.example.thinginomonitor;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.net.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int BG=0xff10151c, PANEL=0xff202934, TEXT=0xfff1f5f9, MUTED=0xff9aaabd, ACCENT=0xff73b9f5;
    private static final String MJPEG="/x/ch0.mjpg", JPEG="/x/ch0.jpg";
    private final ExecutorService scans=Executors.newFixedThreadPool(24);
    private final ExecutorService coordinator=Executors.newSingleThreadExecutor();
    private final TreeMap<String,Camera> cameras=new TreeMap<>((a,b)->{try{return Integer.compare(Integer.parseInt(a.substring(a.lastIndexOf('.')+1)),Integer.parseInt(b.substring(b.lastIndexOf('.')+1)));}catch(Exception e){return a.compareTo(b);}});
    private LinearLayout root, tiles, footer; private TextView status, pageLabel;
    private int page=0; private volatile boolean scanning=false; private boolean foreground=true;
    private boolean polling=true, manual=false; private String manualSubnet="192.168.4"; private int scanGeneration=0;
    private final ArrayList<StreamView> visibleStreams=new ArrayList<>();
    private float touchX;
    static class Camera { final String ip; Camera(String ip){this.ip=ip;} }

    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);root.setPadding(dp(12),dp(16),dp(12),dp(10));setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header);
        LinearLayout heading=new LinearLayout(this);heading.setOrientation(1);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(text("THINGINO MONITOR",20,TEXT,true));status=text("Finding cameras…",12,MUTED,false);heading.addView(status);
        Button settings=button("⚙");settings.setContentDescription("Settings");settings.setOnClickListener(v->showSettings());header.addView(settings);
        Button refresh=button("⟳ Scan");refresh.setOnClickListener(v->scan());header.addView(refresh);
        tiles=new LinearLayout(this);tiles.setOrientation(1);root.addView(tiles,new LinearLayout.LayoutParams(-1,0,1));
        footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);root.addView(footer);
        loadSettings();render();scan();
    }
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(null,1);return t;}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setTextSize(12);b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);return b;}
    private void stopVisible(){for(StreamView s:visibleStreams)s.stop();visibleStreams.clear();}
    private void render(){stopVisible();tiles.removeAllViews();footer.removeAllViews();
        ArrayList<Camera> list=new ArrayList<>(cameras.values());int count=list.size(), pages=Math.max(1,(count+3)/4);page=Math.min(page,pages-1);
        status.setText((scanning?"Scanning · ":"")+count+" camera"+(count==1?"":"s")+" found");
        if(count==0){TextView empty=text(scanning?"Scanning your local network…":"No Thingino MJPEG cameras found\nTap Scan to try again",16,MUTED,false);empty.setGravity(Gravity.CENTER);tiles.addView(empty,new LinearLayout.LayoutParams(-1,-1));}
        else for(int row=0;row<2;row++){LinearLayout line=new LinearLayout(this);line.setOrientation(0);tiles.addView(line,new LinearLayout.LayoutParams(-1,0,1));
            for(int col=0;col<2;col++){int index=page*4+row*2+col;LinearLayout slot=new LinearLayout(this);slot.setOrientation(1);LinearLayout.LayoutParams slotParams=new LinearLayout.LayoutParams(0,-1,1);slotParams.setMargins(dp(3),dp(7),dp(3),dp(7));line.addView(slot,slotParams);
                if(index>=count)continue;Camera cam=list.get(index);slot.setBackgroundColor(PANEL);slot.setPadding(dp(5),dp(5),dp(5),dp(5));
                StreamView view=new StreamView(this,cam.ip,polling);visibleStreams.add(view);slot.addView(view,new LinearLayout.LayoutParams(-1,0,1));
                TextView name=text("Camera · "+cam.ip.substring(cam.ip.lastIndexOf('.')+1),13,TEXT,true);name.setPadding(dp(4),dp(7),0,0);slot.addView(name);
                TextView ip=text(cam.ip,11,MUTED,false);ip.setPadding(dp(4),0,0,dp(4));slot.addView(ip);
                LinearLayout actions=new LinearLayout(this);slot.addView(actions);
                Button play=button("▶ Play");actions.addView(play,new LinearLayout.LayoutParams(0,dp(45),1));
                play.setOnClickListener(v->{if(view.isPlaying()){view.stop();play.setText("▶ Play");}else{view.start();play.setText("Ⅱ Pause");}});
                Button web=button("WEBUI ↗");actions.addView(web,new LinearLayout.LayoutParams(0,dp(45),1));
                web.setOnClickListener(v->{stopVisible();openWeb(cam.ip);});
            }
        }
        Button prev=button("‹");prev.setEnabled(page>0);prev.setOnClickListener(v->{page--;render();});footer.addView(prev);
        pageLabel=text("Page "+(page+1)+" / "+pages,13,MUTED,false);pageLabel.setGravity(Gravity.CENTER);footer.addView(pageLabel,new LinearLayout.LayoutParams(0,-2,1));
        Button next=button("›");next.setEnabled(page<pages-1);next.setOnClickListener(v->{page++;render();});footer.addView(next);
    }
    private void openWeb(String ip){stopVisible();Uri uri=Uri.parse("http://"+ip+"/");Intent i=new Intent(Intent.ACTION_VIEW,uri);i.setPackage("com.android.chrome");try{startActivity(i);}catch(Exception e){i.setPackage(null);try{startActivity(i);}catch(Exception ignored){Toast.makeText(this,"No browser available",Toast.LENGTH_SHORT).show();}}}
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN)touchX=e.getX();else if(e.getAction()==MotionEvent.ACTION_UP){float dx=e.getX()-touchX;if(Math.abs(dx)>dp(110)&&e.getY()>dp(90)&&e.getY()<getResources().getDisplayMetrics().heightPixels-dp(85)){int total=(cameras.size()+3)/4;if(dx<0&&page<total-1){page++;render();return true;}if(dx>0&&page>0){page--;render();return true;}}}return super.dispatchTouchEvent(e);}
    private void loadSettings(){android.content.SharedPreferences p=getSharedPreferences("settings",MODE_PRIVATE);polling=p.getBoolean("polling",true);manual=p.getBoolean("manual",false);manualSubnet=p.getString("subnet","192.168.4");}
    private boolean validPrefix(String prefix){String[] parts=prefix.trim().split("\\.");if(parts.length!=3)return false;try{for(String part:parts){int v=Integer.parseInt(part);if(v<0||v>255)return false;}return true;}catch(Exception e){return false;}}
    private void showSettings(){
        LinearLayout panel=new LinearLayout(this);panel.setPadding(dp(20),dp(8),dp(20),0);panel.setOrientation(1);
        TextView label=text("Preview protocol",16,TEXT,true);panel.addView(label);
        RadioGroup modes=new RadioGroup(this);RadioButton poll=new RadioButton(this),mjpeg=new RadioButton(this);poll.setText("JPEG polling (2 FPS)");mjpeg.setText("MJPEG (continuous)");modes.addView(poll);modes.addView(mjpeg);(polling?poll:mjpeg).setChecked(true);panel.addView(modes);
        panel.addView(text("Network discovery",16,TEXT,true));RadioGroup networks=new RadioGroup(this);RadioButton auto=new RadioButton(this),custom=new RadioButton(this);auto.setText("Automatic (current Wi-Fi /24)");custom.setText("Manual subnet (/24)");networks.addView(auto);networks.addView(custom);(manual?custom:auto).setChecked(true);panel.addView(networks);
        EditText prefix=new EditText(this);prefix.setSingleLine(true);prefix.setText(manualSubnet);prefix.setHint("192.168.4");prefix.setEnabled(manual);panel.addView(prefix);networks.setOnCheckedChangeListener((g,id)->prefix.setEnabled(custom.isChecked()));
        new android.app.AlertDialog.Builder(this).setTitle("Thingino Monitor settings").setView(panel).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            boolean newManual=custom.isChecked();String newPrefix=prefix.getText().toString().trim();if(newManual&&!validPrefix(newPrefix)){Toast.makeText(this,"Invalid subnet; settings not changed",Toast.LENGTH_LONG).show();return;}
            boolean changedSubnet=manual!=newManual||(newManual&&!manualSubnet.equals(newPrefix));boolean changedMode=polling!=poll.isChecked();manual=newManual;manualSubnet=newPrefix;polling=poll.isChecked();getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("polling",polling).putBoolean("manual",manual).putString("subnet",manualSubnet).apply();
            if(changedSubnet){scanGeneration++;scanning=false;cameras.clear();page=0;render();scan();}else if(changedMode)render();
        }).show();
    }
    private String wifiPrefix(){try{
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);Network net=cm.getActiveNetwork();LinkProperties props=net==null?null:cm.getLinkProperties(net);if(props==null)return null;
        for(LinkAddress address:props.getLinkAddresses())if(address.getAddress() instanceof Inet4Address){byte[] a=address.getAddress().getAddress();return (a[0]&255)+"."+(a[1]&255)+"."+(a[2]&255);}
        }catch(Exception ignored){}return null;
    }
    private void scan(){if(scanning)return;scanning=true;final int generation=++scanGeneration;render();coordinator.execute(()->{
        String prefix=manual?manualSubnet:wifiPrefix();if(prefix==null||!validPrefix(prefix)){runOnUiThread(()->{if(generation==scanGeneration){scanning=false;status.setText("No Wi-Fi IPv4 subnet — select manual in settings");}});return;}
        ArrayList<Future<?>> jobs=new ArrayList<>();for(int n=1;n<255;n++){String ip=prefix+"."+n;jobs.add(scans.submit(()->probe(ip,generation)));}
        for(Future<?> f:jobs){try{f.get();}catch(Exception ignored){}}
        runOnUiThread(()->{if(generation==scanGeneration){scanning=false;render();}});
    });}
    private void probe(String ip,int generation){HttpURLConnection conn=null;try{
        conn=(HttpURLConnection)new URL("http://"+ip+"/").openConnection();conn.setConnectTimeout(1800);conn.setReadTimeout(1800);conn.setInstanceFollowRedirects(false);conn.setUseCaches(false);
        int status=conn.getResponseCode();if(status!=200)return;
        // Same proven WebUI signature test as Find-Thingino.ps1; cap at 32 KiB.
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=conn.getInputStream()){byte[] buf=new byte[4096];int n;while(bytes.size()<32768&&(n=in.read(buf,0,Math.min(buf.length,32768-bytes.size())))>0)bytes.write(buf,0,n);}
        String html=bytes.toString("UTF-8").toLowerCase(Locale.US);
        if(!(html.contains("thingino")||html.contains("prudynt")||html.contains("raptor")))return;
        runOnUiThread(()->{if(generation==scanGeneration&&!cameras.containsKey(ip)){cameras.put(ip,new Camera(ip));render();}});
        }catch(Exception ignored){}finally{if(conn!=null)conn.disconnect();}}
    @Override protected void onPause(){super.onPause();foreground=false;stopVisible();}
    @Override protected void onResume(){super.onResume();if(!foreground){foreground=true;render();scan();}}
    @Override protected void onDestroy(){stopVisible();scans.shutdownNow();coordinator.shutdownNow();super.onDestroy();}

    static class StreamView extends View {
        private final String ip;private final boolean polling;private volatile boolean running=false;private volatile Bitmap frame;private volatile String message="Paused";private volatile HttpURLConnection connection;private Thread worker;
        StreamView(Context c,String ip,boolean polling){super(c);this.ip=ip;this.polling=polling;setBackgroundColor(0xff111820);}
        boolean isPlaying(){return running;}
        synchronized void start(){if(running)return;running=true;message="Connecting…";invalidate();worker=new Thread(polling?this::pollFrames:this::stream,"preview-"+ip);worker.setDaemon(true);worker.start();}
        synchronized void stop(){running=false;HttpURLConnection c=connection;if(c!=null)c.disconnect();if(worker!=null)worker.interrupt();message="Paused";frame=null;postInvalidate();}
        private void pollFrames(){while(running){HttpURLConnection c=null;try{c=(HttpURLConnection)new URL("http://"+ip+JPEG).openConnection();connection=c;c.setConnectTimeout(2500);c.setReadTimeout(3500);c.setUseCaches(false);c.setRequestProperty("Cache-Control","no-cache");if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while(running&&(n=in.read(buf))!=-1&&out.size()<4000000)out.write(buf,0,n);byte[] data=out.toByteArray();Bitmap b=BitmapFactory.decodeByteArray(data,0,data.length);if(b!=null&&running){frame=b;message="";postInvalidate();}}}catch(Exception e){if(running){message="Preview unavailable";postInvalidate();}}finally{if(c!=null)c.disconnect();if(connection==c)connection=null;}if(running)try{Thread.sleep(500);}catch(InterruptedException ignored){break;}}}
        private void stream(){while(running){HttpURLConnection c=null;try{c=(HttpURLConnection)new URL("http://"+ip+MJPEG).openConnection();connection=c;c.setConnectTimeout(2500);c.setReadTimeout(4000);if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());
            try(InputStream in=new BufferedInputStream(c.getInputStream(),16384)){ByteArrayOutputStream jpg=new ByteArrayOutputStream(128*1024);int previous=-1,current;boolean collecting=false;while(running&&(current=in.read())!=-1){if(!collecting){if(previous==255&&current==216){collecting=true;jpg.reset();jpg.write(255);jpg.write(216);}}else{jpg.write(current);if(previous==255&&current==217){byte[] data=jpg.toByteArray();Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length);if(bitmap!=null&&running){frame=bitmap;message="";postInvalidate();}collecting=false;}if(jpg.size()>4_000_000){collecting=false;jpg.reset();}}previous=current;}}}
            catch(Exception e){if(running){message="Reconnecting…";postInvalidate();}}finally{if(c!=null)c.disconnect();if(connection==c)connection=null;}
            if(running)try{Thread.sleep(1500);}catch(InterruptedException ignored){}
        }}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);Bitmap b=frame;if(b!=null){float scale=Math.min(getWidth()/(float)b.getWidth(),getHeight()/(float)b.getHeight());float w=b.getWidth()*scale,h=b.getHeight()*scale;canvas.drawBitmap(b,null,new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2),null);}else{Paint p=new Paint(3);p.setColor(0xffaab7c7);p.setTextSize(Math.max(18,getWidth()/13f));String s=message;canvas.drawText(s,Math.max(8,(getWidth()-p.measureText(s))/2),getHeight()/2f,p);}}
    }
}
