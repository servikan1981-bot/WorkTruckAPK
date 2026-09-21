package com.worktruck.catalog;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class SosActivity extends Activity {
    private static final int BLUE=Color.rgb(18,92,185), RED=Color.rgb(190,45,45), DARK=Color.rgb(8,39,79), BG=Color.rgb(244,247,250), MUTED=Color.rgb(101,113,125);
    private static final int REQ_LOCATION=701;
    private static final String MANAGER_PHONE="79503512841";

    private Spinner vehicle,problem;
    private EditText place,comment;
    private TextView diag,locationStatus;
    private Button telegramBtn;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Handler handler=new Handler(Looper.getMainLooper());
    private boolean waitingLocation=false;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(Color.WHITE);
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        build();
    }

    private void build(){
        LinearLayout root=col();root.setBackgroundColor(BG);root.addView(top("Машина встала · SOS"));
        ScrollView sv=new ScrollView(this);LinearLayout body=col();body.setPadding(dp(14),dp(14),dp(14),dp(26));

        LinearLayout hero=card();hero.setBackground(round(Color.rgb(255,238,238),18));
        hero.addView(tv("SOS",14,RED,true));
        hero.addView(tv("Помощь при поломке",24,DARK,true));
        hero.addView(tv("При отправке SOS приложение определит текущую геопозицию и подготовит сообщение персональному менеджеру Сергею Аникинову в Telegram.",13,MUTED,false));
        body.addView(wrap(hero,0,0,0,12));

        LinearLayout manager=card();
        manager.addView(tv("Персональный менеджер",12,MUTED,true));
        manager.addView(tv("Сергей Аникинов",18,DARK,true));
        manager.addView(tv("8-950-351-28-41",15,BLUE,true));
        body.addView(wrap(manager,0,0,0,10));

        vehicle=new Spinner(this);
        vehicle.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,vehicles()));
        body.addView(label("Автомобиль"));body.addView(vehicle,new LinearLayout.LayoutParams(-1,dp(52)));

        problem=new Spinner(this);
        problem.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{
                "Не заводится","Нет давления воздуха","Перегрев","Двигатель / тяга","КПП / сцепление","Тормоза","Электрика","ДТП / повреждение","Другое"}));
        body.addView(label("Что произошло"));body.addView(problem,new LinearLayout.LayoutParams(-1,dp(52)));

        place=input("Дополнительно: трасса, км, ориентир");
        body.addView(label("Комментарий к месту"));body.addView(place,new LinearLayout.LayoutParams(-1,dp(52)));

        comment=input("Комментарий / симптомы");comment.setSingleLine(false);comment.setMinLines(2);
        body.addView(label("Подробности"));body.addView(comment,new LinearLayout.LayoutParams(-1,dp(78)));

        LinearLayout loc=card();
        loc.addView(tv("Геолокация SOS",16,DARK,true));
        locationStatus=tv("Точка определяется только после нажатия кнопки SOS.",12,MUTED,false);
        locationStatus.setPadding(0,dp(4),0,0);loc.addView(locationStatus);
        body.addView(wrap(loc,0,12,0,10));

        String last=getSharedPreferences("wt_diagnostics",MODE_PRIVATE).getString("last_report","");
        LinearLayout d=card();d.addView(tv("Последняя ELM327 диагностика",16,DARK,true));
        diag=tv(last.isEmpty()?"Диагностический отчёт пока не сохранён. Можно сначала открыть ELM327.":last,11,last.isEmpty()?MUTED:DARK,false);
        d.addView(diag);body.addView(wrap(d,0,0,0,10));

        telegramBtn=button("ОТПРАВИТЬ SOS С ТОЧКОЙ В TELEGRAM",RED);
        telegramBtn.setOnClickListener(v->startSos());
        body.addView(telegramBtn,new LinearLayout.LayoutParams(-1,dp(56)));

        Button call=button("ПОЗВОНИТЬ СЕРГЕЮ · 8-950-351-28-41",BLUE);
        call.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:+79503512841"))));
        body.addView(wrap(call,0,10,0,0));

        TextView privacy=tv("Геопозиция не отслеживается постоянно: приложение запрашивает текущую точку только при отправке SOS.",11,MUTED,false);
        privacy.setPadding(dp(2),dp(10),dp(2),0);body.addView(privacy);

        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void startSos(){
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);
            return;
        }
        locateAndOpenTelegram();
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_LOCATION){
            boolean ok=false;
            for(int r:grantResults)if(r==PackageManager.PERMISSION_GRANTED)ok=true;
            if(ok) locateAndOpenTelegram();
            else{
                locationStatus.setText("Доступ к геопозиции не разрешён. SOS можно отправить без точки.");
                openTelegram(null);
            }
        }
    }

    @SuppressWarnings("MissingPermission")
    private void locateAndOpenTelegram(){
        if(waitingLocation)return;
        waitingLocation=true;telegramBtn.setEnabled(false);
        locationStatus.setText("Определяем текущую точку…");

        Location best=getBestLastLocation();
        if(best!=null && System.currentTimeMillis()-best.getTime()<120000){
            finishLocation(best);return;
        }

        locationListener=new LocationListener(){
            @Override public void onLocationChanged(Location location){if(location!=null)finishLocation(location);}
            @Override public void onStatusChanged(String provider,int status,Bundle extras){}
            @Override public void onProviderEnabled(String provider){}
            @Override public void onProviderDisabled(String provider){}
        };

        boolean requested=false;
        try{
            if(locationManager!=null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,0,0,locationListener,Looper.getMainLooper());requested=true;
            }
        }catch(Throwable ignored){}
        try{
            if(locationManager!=null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,0,0,locationListener,Looper.getMainLooper());requested=true;
            }
        }catch(Throwable ignored){}

        final boolean any=requested;
        handler.postDelayed(()->{
            if(!waitingLocation)return;
            Location fallback=getBestLastLocation();
            stopLocationUpdates();
            if(fallback!=null){
                locationStatus.setText("Используем последнюю доступную точку.");
                openTelegram(fallback);
            }else{
                locationStatus.setText(any?"Не удалось быстро получить точку. SOS откроется без координат.":"Геолокация выключена. SOS откроется без координат.");
                openTelegram(null);
            }
        },9000);
    }

    @SuppressWarnings("MissingPermission")
    private Location getBestLastLocation(){
        if(locationManager==null)return null;
        Location best=null;
        try{
            for(String p:locationManager.getProviders(true)){
                Location l=locationManager.getLastKnownLocation(p);
                if(l!=null && (best==null || l.getTime()>best.getTime()))best=l;
            }
        }catch(Throwable ignored){}
        return best;
    }

    private void finishLocation(Location location){
        if(!waitingLocation)return;
        stopLocationUpdates();
        locationStatus.setText(String.format(Locale.US,"Точка получена: %.5f, %.5f",location.getLatitude(),location.getLongitude()));
        openTelegram(location);
    }

    private void stopLocationUpdates(){
        waitingLocation=false;telegramBtn.setEnabled(true);
        handler.removeCallbacksAndMessages(null);
        if(locationManager!=null && locationListener!=null){
            try{locationManager.removeUpdates(locationListener);}catch(Throwable ignored){}
        }
        locationListener=null;
    }

    private void openTelegram(Location loc){
        String last=getSharedPreferences("wt_diagnostics",MODE_PRIVATE).getString("last_report","");
        StringBuilder s=new StringBuilder();
        s.append("SOS · Work Truck\n");
        s.append(new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date())).append("\n");
        s.append("Менеджер: Сергей Аникинов\n");
        s.append("Автомобиль: ").append(vehicle.getSelectedItem()).append("\n");
        s.append("Проблема: ").append(problem.getSelectedItem()).append("\n");
        if(loc!=null){
            String coords=String.format(Locale.US,"%.6f,%.6f",loc.getLatitude(),loc.getLongitude());
            s.append("Геопозиция: https://maps.google.com/?q=").append(coords).append("\n");
            s.append("Координаты: ").append(coords).append("\n");
        }else s.append("Геопозиция: не получена\n");
        String p=place.getText().toString().trim();if(!p.isEmpty())s.append("Ориентир: ").append(p).append("\n");
        String c=comment.getText().toString().trim();if(!c.isEmpty())s.append("Комментарий: ").append(c).append("\n");
        if(!last.isEmpty())s.append("\nПоследняя диагностика:\n").append(last);

        String encoded=Uri.encode(s.toString());
        try{
            Intent tg=new Intent(Intent.ACTION_VIEW,Uri.parse("tg://resolve?phone="+MANAGER_PHONE+"&text="+encoded));
            startActivity(tg);
            locationStatus.setText("Telegram открыт. Проверьте сообщение и нажмите «Отправить».");
        }catch(Throwable e){
            try{
                startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://t.me/+"+MANAGER_PHONE+"?text="+encoded)));
                locationStatus.setText("Открыт Telegram. Проверьте сообщение и нажмите «Отправить».");
            }catch(Throwable x){
                Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/plain");share.putExtra(Intent.EXTRA_TEXT,s.toString());
                startActivity(Intent.createChooser(share,"Отправить SOS"));
            }
        }
    }

    private ArrayList<String> vehicles(){
        ArrayList<String> out=new ArrayList<String>();out.add("Scania (не выбрана)");
        String raw=getSharedPreferences("wt_client_hub",MODE_PRIVATE).getString("fleet","");
        for(String row:raw.split("\n")){
            if(row.trim().isEmpty())continue;
            String[] p=row.split("\\|",-1);String model=p.length>0?p[0]:"Scania",vin=p.length>1?p[1]:"";
            out.add((model.isEmpty()?"Scania":model)+(vin.isEmpty()?"":" · "+vin));
        }
        return out;
    }

    @Override protected void onDestroy(){stopLocationUpdates();super.onDestroy();}

    private View top(String s){LinearLayout t=new LinearLayout(this);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(8),dp(7),dp(10),dp(7));t.setBackgroundColor(Color.WHITE);TextView b=tv("‹",34,DARK,false);b.setGravity(Gravity.CENTER);b.setOnClickListener(v->finish());t.addView(b,new LinearLayout.LayoutParams(dp(44),dp(48)));t.addView(tv(s,22,DARK,true));return t;}
    private TextView label(String s){TextView t=tv(s,12,MUTED,true);t.setPadding(dp(2),dp(10),0,dp(4));return t;}
    private EditText input(String h){EditText e=new EditText(this);e.setHint(h);e.setTextSize(15);e.setPadding(dp(14),0,dp(14),0);e.setBackground(round(Color.WHITE,14));return e;}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){LinearLayout c=col();c.setPadding(dp(14),dp(13),dp(14),dp(13));c.setBackground(round(Color.WHITE,17));return c;}
    private View wrap(View v,int l,int t,int r,int b){LinearLayout x=col();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));x.addView(v,p);return x;}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(round(color,14));return b;}
    private TextView tv(String s,int sp,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(c);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));g.setStroke(dp(1),Color.rgb(222,230,240));return g;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
