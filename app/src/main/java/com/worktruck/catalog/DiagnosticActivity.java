package com.worktruck.catalog;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiagnosticActivity extends Activity {
    private static final int BLUE=Color.rgb(18,92,185), DARK=Color.rgb(8,39,79), BG=Color.rgb(244,247,250), MUTED=Color.rgb(101,113,125);
    private static final int REQ_BT=401;
    private static final UUID SPP_UUID=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter adapter;
    private BluetoothSocket socket;
    private InputStream in;
    private OutputStream out;
    private final ExecutorService io=Executors.newSingleThreadExecutor();

    private LinearLayout deviceBox, resultBox;
    private TextView status, live;
    private Button readBtn, clearBtn, shareBtn;
    private final StringBuilder report=new StringBuilder();

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(Color.WHITE);
        BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        adapter=bm==null?null:bm.getAdapter();
        build();
        ensurePermissionsAndList();
    }

    private void build(){
        LinearLayout root=col();root.setBackgroundColor(BG);

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(10),dp(8),dp(14),dp(8));top.setBackgroundColor(Color.WHITE);
        TextView back=tv("‹",34,DARK,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));
        top.addView(tv("Диагностика ELM327",22,DARK,true),new LinearLayout.LayoutParams(0,-2,1));root.addView(top);

        ScrollView sv=new ScrollView(this);LinearLayout body=col();body.setPadding(dp(14),dp(14),dp(14),dp(30));

        LinearLayout intro=card();
        intro.setBackground(round(Color.rgb(232,241,253),18));
        intro.addView(tv("БЫСТРАЯ ДИАГНОСТИКА",12,BLUE,true));
        TextView h=tv("Подключите ELM327",24,DARK,true);h.setPadding(0,dp(4),0,dp(5));intro.addView(h);
        intro.addView(tv("Чтение стандартных OBD-II ошибок и основных параметров. Для полной диагностики блоков Scania потребуется профессиональный интерфейс.",13,MUTED,false));
        body.addView(wrap(intro,0,0,0,12));

        status=tv("Проверка Bluetooth…",14,DARK,true);status.setPadding(dp(2),0,dp(2),dp(8));body.addView(status);
        Button refresh=blueButton("ПОКАЗАТЬ СПАРЕННЫЕ ELM327");refresh.setOnClickListener(v->ensurePermissionsAndList());body.addView(refresh,new LinearLayout.LayoutParams(-1,dp(52)));

        deviceBox=col();deviceBox.setPadding(0,dp(10),0,0);body.addView(deviceBox);

        LinearLayout actions=card();TextView t=tv("Диагностика",18,DARK,true);actions.addView(t);
        readBtn=blueButton("СЧИТАТЬ ОШИБКИ И ПАРАМЕТРЫ");readBtn.setEnabled(false);readBtn.setOnClickListener(v->readDiagnostics());actions.addView(wrap(readBtn,0,10,0,0));
        clearBtn=lightButton("Стереть ошибки");clearBtn.setEnabled(false);clearBtn.setOnClickListener(v->confirmClear());actions.addView(wrap(clearBtn,0,8,0,0));
        shareBtn=lightButton("Отправить отчёт");shareBtn.setEnabled(false);shareBtn.setOnClickListener(v->shareReport());actions.addView(wrap(shareBtn,0,8,0,0));
        body.addView(wrap(actions,0,12,0,12));

        live=tv("После подключения здесь появятся обороты, температура, напряжение и нагрузка двигателя.",13,MUTED,false);body.addView(live);

        resultBox=col();resultBox.setPadding(0,dp(12),0,0);body.addView(resultBox);

        LinearLayout warn=card();
        warn.addView(tv("Важно",16,DARK,true));
        warn.addView(tv("ELM327 читает только те блоки и параметры, которые доступны по стандартному OBD-II. Ошибка в приложении не является окончательным диагнозом. Не стирайте коды до фиксации причины неисправности.",12,MUTED,false));
        body.addView(wrap(warn,0,14,0,0));

        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void ensurePermissionsAndList(){
        if(Build.VERSION.SDK_INT>=31){
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ_BT);return;
            }
        } else if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ_BT);return;
        }
        listPaired();
    }

    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);
        if(r==REQ_BT) listPaired();
    }

    @SuppressWarnings("MissingPermission")
    private void listPaired(){
        deviceBox.removeAllViews();
        if(adapter==null){status.setText("Bluetooth на этом телефоне недоступен");return;}
        if(!adapter.isEnabled()){
            status.setText("Включите Bluetooth и вернитесь в приложение");
            Button b=lightButton("ОТКРЫТЬ НАСТРОЙКИ BLUETOOTH");
            b.setOnClickListener(v->startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)));
            deviceBox.addView(b,new LinearLayout.LayoutParams(-1,dp(50)));return;
        }
        Set<BluetoothDevice> paired=adapter.getBondedDevices();
        status.setText("Выберите ELM327 из уже спаренных устройств");
        if(paired==null||paired.isEmpty()){
            TextView e=tv("Спаренных Bluetooth-устройств нет. Сначала подключите ELM327 в настройках телефона. Частые PIN: 1234 или 0000.",13,MUTED,false);
            deviceBox.addView(wrapText(e));
            Button settings=lightButton("ОТКРЫТЬ BLUETOOTH");
            settings.setOnClickListener(v->startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)));
            deviceBox.addView(wrap(settings,0,8,0,0));return;
        }
        ArrayList<BluetoothDevice> list=new ArrayList<>(paired);
        Collections.sort(list,(a,b)->safeName(a).compareToIgnoreCase(safeName(b)));
        for(BluetoothDevice d:list){
            String name=safeName(d);String mac=d.getAddress();
            LinearLayout c=card();c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout txt=col();txt.addView(tv(name,16,DARK,true));txt.addView(tv(mac,12,MUTED,false));c.addView(txt,new LinearLayout.LayoutParams(0,-2,1));
            TextView go=tv("›",30,BLUE,true);go.setGravity(Gravity.CENTER);c.addView(go,new LinearLayout.LayoutParams(dp(34),dp(50)));
            c.setOnClickListener(v->connect(d));deviceBox.addView(wrap(c,0,0,0,8));
        }
    }

    @SuppressWarnings("MissingPermission")
    private String safeName(BluetoothDevice d){try{String n=d.getName();return n==null?"Bluetooth-устройство":n;}catch(Throwable e){return "Bluetooth-устройство";}}

    @SuppressWarnings("MissingPermission")
    private void connect(BluetoothDevice device){
        status.setText("Подключение к "+safeName(device)+"…");
        readBtn.setEnabled(false);clearBtn.setEnabled(false);shareBtn.setEnabled(false);
        io.execute(()->{
            closeSocket();
            try{
                adapter.cancelDiscovery();
                socket=device.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();in=socket.getInputStream();out=socket.getOutputStream();
                elm("ATZ",2500);elm("ATE0",1000);elm("ATL0",1000);elm("ATS0",1000);elm("ATH0",1000);elm("ATSP0",1800);
                String proto=elm("ATDP",1200);
                runOnUiThread(()->{
                    status.setText("Подключено: "+safeName(device)+" · "+cleanResponse(proto));
                    readBtn.setEnabled(true);clearBtn.setEnabled(true);
                    live.setText("ELM327 готов. Нажмите «Считать ошибки и параметры».");
                });
            }catch(Throwable e){
                runOnUiThread(()->{status.setText("Не удалось подключиться. Проверьте, что ELM327 включён и не занят другим приложением.");live.setText(shortErr(e));});
                closeSocket();
            }
        });
    }

    private void readDiagnostics(){
        readBtn.setEnabled(false);resultBox.removeAllViews();live.setText("Чтение данных…");
        io.execute(()->{
            try{
                String voltage=cleanResponse(elm("ATRV",1200));
                String rpm=parsePid(elm("010C",1600),0x0C);
                String coolant=parsePid(elm("0105",1600),0x05);
                String load=parsePid(elm("0104",1600),0x04);
                String vin=parseVin(elm("0902",2200));
                String rawDtc=elm("03",2200);
                List<String> dtcs=parseDtcs(rawDtc);

                StringBuilder r=new StringBuilder();
                r.append("Work Truck · диагностика ELM327\n");
                r.append(new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date())).append("\n\n");
                if(!vin.isEmpty())r.append("VIN: ").append(vin).append("\n");
                r.append("Напряжение: ").append(voltage).append("\n");
                r.append("Обороты: ").append(rpm).append("\n");
                r.append("Температура ОЖ: ").append(coolant).append("\n");
                r.append("Нагрузка двигателя: ").append(load).append("\n\n");
                if(dtcs.isEmpty())r.append("Стандартные DTC: не обнаружены\n");
                else{r.append("Ошибки:\n");for(String d:dtcs)r.append("• ").append(d).append(" — ").append(dtcHint(d)).append("\n");}
                report.setLength(0);report.append(r);

                runOnUiThread(()->showResults(vin,voltage,rpm,coolant,load,dtcs));
            }catch(Throwable e){
                runOnUiThread(()->{live.setText("Ошибка чтения: "+shortErr(e));readBtn.setEnabled(true);});
            }
        });
    }

    private void showResults(String vin,String voltage,String rpm,String coolant,String load,List<String> dtcs){
        live.setText("Данные получены");
        resultBox.removeAllViews();

        LinearLayout values=card();values.addView(tv("Основные параметры",18,DARK,true));
        values.addView(tv((vin.isEmpty()?"VIN: недоступен":"VIN: "+vin)+"\nНапряжение: "+voltage+"\nОбороты: "+rpm+"\nТемпература ОЖ: "+coolant+"\nНагрузка двигателя: "+load,14,DARK,false));
        resultBox.addView(wrap(values,0,0,0,10));

        LinearLayout codes=card();codes.addView(tv("Ошибки OBD-II",18,DARK,true));
        if(dtcs.isEmpty()) codes.addView(tv("Стандартные ошибки не обнаружены.",14,BLUE,true));
        else for(String d:dtcs){
            TextView x=tv(d+" · "+dtcHint(d),14,DARK,true);x.setPadding(0,dp(7),0,dp(4));codes.addView(x);
        }
        resultBox.addView(wrap(codes,0,0,0,10));
        shareBtn.setEnabled(true);readBtn.setEnabled(true);
    }

    private void confirmClear(){
        new AlertDialog.Builder(this)
            .setTitle("Стереть ошибки?")
            .setMessage("Коды неисправностей и часть диагностических данных могут быть удалены из блока. Это не устраняет причину поломки. Сначала сохраните отчёт.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Стереть",(d,w)->clearDtcs()).show();
    }

    private void clearDtcs(){
        clearBtn.setEnabled(false);
        io.execute(()->{
            try{
                String ans=cleanResponse(elm("04",2200));
                runOnUiThread(()->{Toast.makeText(this,"Команда отправлена: "+ans,Toast.LENGTH_LONG).show();clearBtn.setEnabled(true);});
            }catch(Throwable e){runOnUiThread(()->{Toast.makeText(this,"Не удалось стереть ошибки",Toast.LENGTH_LONG).show();clearBtn.setEnabled(true);});}
        });
    }

    private synchronized String elm(String command,long timeout) throws Exception{
        if(socket==null||!socket.isConnected()||out==null||in==null)throw new IOException("Нет соединения с ELM327");
        while(in.available()>0)in.read();
        out.write((command+"\r").getBytes(StandardCharsets.US_ASCII));out.flush();
        long end=System.currentTimeMillis()+timeout;StringBuilder sb=new StringBuilder();byte[] buf=new byte[512];
        while(System.currentTimeMillis()<end){
            int avail=in.available();
            if(avail>0){
                int n=in.read(buf,0,Math.min(avail,buf.length));
                if(n>0){String s=new String(buf,0,n,StandardCharsets.US_ASCII);sb.append(s);if(sb.indexOf(">")>=0)break;}
            }else Thread.sleep(35);
        }
        String res=sb.toString();
        if(res.trim().isEmpty())throw new IOException("ELM327 не ответил");
        return res;
    }

    private String cleanResponse(String s){
        if(s==null)return "—";
        return s.replace(">","").replace("\r"," ").replace("\n"," ").replace("SEARCHING...","").replaceAll("\s+"," ").trim();
    }

    private String parsePid(String raw,int pid){
        String hex=hexOnly(raw);
        String marker=String.format(Locale.US,"41%02X",pid);
        int i=hex.indexOf(marker);if(i<0)return "недоступно";
        try{
            if(pid==0x0C && i+8<=hex.length()){int a=Integer.parseInt(hex.substring(i+4,i+6),16),b=Integer.parseInt(hex.substring(i+6,i+8),16);return ((a*256+b)/4)+" об/мин";}
            if(pid==0x05 && i+6<=hex.length()){int a=Integer.parseInt(hex.substring(i+4,i+6),16);return (a-40)+" °C";}
            if(pid==0x04 && i+6<=hex.length()){int a=Integer.parseInt(hex.substring(i+4,i+6),16);return Math.round(a*100f/255f)+" %";}
        }catch(Throwable ignored){}
        return "недоступно";
    }

    private String parseVin(String raw){
        try{
            String h=hexOnly(raw);StringBuilder vin=new StringBuilder();int pos=0;
            while((pos=h.indexOf("4902",pos))>=0){
                int start=pos+6; // skip 49 02 frameIndex
                int end=Math.min(h.length(),start+28);
                for(int i=start;i+2<=end;i+=2){int v=Integer.parseInt(h.substring(i,i+2),16);if(v>=32&&v<=126)vin.append((char)v);}
                pos=end;
            }
            String v=vin.toString().replaceAll("[^A-HJ-NPR-Z0-9]","");
            return v.length()>=11?(v.length()>17?v.substring(0,17):v):"";
        }catch(Throwable e){return "";}
    }

    private List<String> parseDtcs(String raw){
        ArrayList<String> out=new ArrayList<>();String h=hexOnly(raw);int idx=h.indexOf("43");if(idx<0)return out;
        String data=h.substring(idx+2);
        for(int i=0;i+4<=data.length();i+=4){
            try{
                int a=Integer.parseInt(data.substring(i,i+2),16),b=Integer.parseInt(data.substring(i+2,i+4),16);
                if(a==0&&b==0)continue;
                char[] family={'P','C','B','U'};char f=family[(a>>6)&3];
                int d1=(a>>4)&3,d2=a&15,d3=(b>>4)&15,d4=b&15;
                String code=""+f+d1+Integer.toHexString(d2).toUpperCase(Locale.US)+Integer.toHexString(d3).toUpperCase(Locale.US)+Integer.toHexString(d4).toUpperCase(Locale.US);
                if(!out.contains(code))out.add(code);
            }catch(Throwable ignored){}
        }
        return out;
    }

    private String dtcHint(String c){
        if(c==null)return "требуется уточнение";
        if(c.startsWith("P01")||c.startsWith("P02"))return "топливо / воздух / двигатель";
        if(c.startsWith("P03"))return "зажигание / пропуски / двигатель";
        if(c.startsWith("P04"))return "экология / EGR / выхлоп";
        if(c.startsWith("P05"))return "скорость / холостой ход / системы управления";
        if(c.startsWith("P07")||c.startsWith("P08"))return "трансмиссия";
        if(c.startsWith("C"))return "шасси";
        if(c.startsWith("B"))return "кузов / кабина";
        if(c.startsWith("U"))return "связь между блоками";
        return "стандартный OBD-II код";
    }

    private String hexOnly(String s){return s==null?"":s.toUpperCase(Locale.US).replaceAll("[^0-9A-F]","");}

    private void shareReport(){
        if(report.length()==0)return;
        Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_SUBJECT,"Диагностика Work Truck");i.putExtra(Intent.EXTRA_TEXT,report.toString());
        startActivity(Intent.createChooser(i,"Отправить диагностический отчёт"));
    }

    private String shortErr(Throwable e){String s=e==null?"":e.getMessage();return s==null||s.isEmpty()?"проверьте соединение":s;}

    private void closeSocket(){
        try{if(in!=null)in.close();}catch(Throwable ignored){}try{if(out!=null)out.close();}catch(Throwable ignored){}try{if(socket!=null)socket.close();}catch(Throwable ignored){}
        in=null;out=null;socket=null;
    }

    @Override protected void onDestroy(){closeSocket();io.shutdownNow();super.onDestroy();}

    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){LinearLayout c=col();c.setPadding(dp(14),dp(13),dp(14),dp(13));c.setBackground(round(Color.WHITE,17));return c;}
    private View wrap(View v,int l,int t,int r,int b){LinearLayout x=col();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));x.addView(v,p);return x;}
    private View wrapText(View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(8));v.setLayoutParams(p);return v;}
    private Button blueButton(String s){Button b=new Button(this);b.setText(s);b.setTextSize(14);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setBackground(round(BLUE,14));return b;}
    private Button lightButton(String s){Button b=new Button(this);b.setText(s);b.setTextSize(14);b.setTextColor(DARK);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setBackground(round(Color.rgb(232,241,253),14));return b;}
    private TextView tv(String x,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(x);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(int color,int r){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(r));g.setStroke(dp(1),Color.rgb(222,230,240));return g;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
