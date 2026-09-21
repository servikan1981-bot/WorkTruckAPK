package com.worktruck.catalog;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class SosActivity extends Activity {
    private static final int BLUE=Color.rgb(18,92,185), RED=Color.rgb(190,45,45), DARK=Color.rgb(8,39,79), BG=Color.rgb(244,247,250), MUTED=Color.rgb(101,113,125);
    private Spinner vehicle,problem;
    private EditText place,comment;
    private TextView diag;

    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(DARK);getWindow().setNavigationBarColor(Color.WHITE);build();}

    private void build(){
        LinearLayout root=col();root.setBackgroundColor(BG);root.addView(top("Машина встала · SOS"));
        ScrollView sv=new ScrollView(this);LinearLayout body=col();body.setPadding(dp(14),dp(14),dp(14),dp(26));
        LinearLayout hero=card();hero.setBackground(round(Color.rgb(255,238,238),18));hero.addView(tv("SOS",14,RED,true));hero.addView(tv("Помощь при поломке",24,DARK,true));hero.addView(tv("Соберите в одном сообщении автомобиль, проблему, место и последние данные диагностики. После подключения CRM SOS будет уходить напрямую ответственному менеджеру.",13,MUTED,false));body.addView(wrap(hero,0,0,0,12));

        vehicle=new Spinner(this);vehicle.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,vehicles()));body.addView(label("Автомобиль"));body.addView(vehicle,new LinearLayout.LayoutParams(-1,dp(52)));
        problem=new Spinner(this);problem.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Не заводится","Нет давления воздуха","Перегрев","Двигатель / тяга","КПП / сцепление","Тормоза","Электрика","ДТП / повреждение","Другое"}));body.addView(label("Что произошло"));body.addView(problem,new LinearLayout.LayoutParams(-1,dp(52)));
        place=input("Где находится автомобиль: трасса, км, город");body.addView(label("Местоположение"));body.addView(place,new LinearLayout.LayoutParams(-1,dp(52)));
        comment=input("Комментарий / симптомы");comment.setSingleLine(false);comment.setMinLines(2);body.addView(label("Подробности"));body.addView(comment,new LinearLayout.LayoutParams(-1,dp(78)));

        String last=getSharedPreferences("wt_diagnostics",MODE_PRIVATE).getString("last_report","");
        LinearLayout d=card();d.addView(tv("Последняя ELM327 диагностика",16,DARK,true));
        diag=tv(last.isEmpty()?"Диагностический отчёт пока не сохранён. Можно сначала открыть ELM327.":last,11,last.isEmpty()?MUTED:DARK,false);d.addView(diag);body.addView(wrap(d,0,12,0,10));

        Button send=button("СФОРМИРОВАТЬ И ОТПРАВИТЬ SOS",RED);send.setOnClickListener(v->share());body.addView(send,new LinearLayout.LayoutParams(-1,dp(54)));
        Button call=button("ПОЗВОНИТЬ В WORK TRUCK",BLUE);call.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:88005509638"))));body.addView(wrap(call,0,10,0,0));

        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void share(){
        String last=getSharedPreferences("wt_diagnostics",MODE_PRIVATE).getString("last_report","");
        StringBuilder s=new StringBuilder();
        s.append("SOS · Work Truck\n");
        s.append(new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date())).append("\n");
        s.append("Автомобиль: ").append(vehicle.getSelectedItem()).append("\n");
        s.append("Проблема: ").append(problem.getSelectedItem()).append("\n");
        s.append("Место: ").append(place.getText().toString().trim()).append("\n");
        s.append("Комментарий: ").append(comment.getText().toString().trim()).append("\n");
        if(!last.isEmpty())s.append("\nПоследняя диагностика:\n").append(last);
        Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_SUBJECT,"SOS Work Truck");i.putExtra(Intent.EXTRA_TEXT,s.toString());
        startActivity(Intent.createChooser(i,"Отправить SOS"));
    }

    private ArrayList<String> vehicles(){ArrayList<String> out=new ArrayList<String>();out.add("Scania (не выбрана)");String raw=getSharedPreferences("wt_client_hub",MODE_PRIVATE).getString("fleet","");for(String row:raw.split("\n")){if(row.trim().isEmpty())continue;String[] p=row.split("\\|",-1);String model=p.length>0?p[0]:"Scania",vin=p.length>1?p[1]:"";out.add((model.isEmpty()?"Scania":model)+(vin.isEmpty()?"":" · "+vin));}return out;}
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
