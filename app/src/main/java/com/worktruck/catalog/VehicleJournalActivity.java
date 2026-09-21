package com.worktruck.catalog;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class VehicleJournalActivity extends Activity {
    private static final int BLUE=Color.rgb(18,92,185), DARK=Color.rgb(8,39,79), BG=Color.rgb(244,247,250), MUTED=Color.rgb(101,113,125);
    private SharedPreferences prefs;
    private Spinner vehicle,type;
    private EditText mileage,nextMileage,note;
    private LinearLayout listBox;

    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(DARK);getWindow().setNavigationBarColor(Color.WHITE);prefs=getSharedPreferences("wt_vehicle_journal",MODE_PRIVATE);build();}

    private void build(){
        LinearLayout root=col();root.setBackgroundColor(BG);root.addView(top("Журнал автомобиля и ТО"));
        ScrollView sv=new ScrollView(this);LinearLayout body=col();body.setPadding(dp(14),dp(14),dp(14),dp(26));
        LinearLayout hero=card();hero.setBackground(round(Color.rgb(232,241,253),18));hero.addView(tv("МОЯ SCANIA",12,BLUE,true));hero.addView(tv("История обслуживания",23,DARK,true));hero.addView(tv("Записывайте пробег, ТО и ремонты. После CRM история покупок и работ сможет добавляться автоматически.",13,MUTED,false));body.addView(wrap(hero,0,0,0,12));

        vehicle=new Spinner(this);vehicle.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,vehicles()));body.addView(label("Автомобиль"));body.addView(vehicle,new LinearLayout.LayoutParams(-1,dp(52)));
        type=new Spinner(this);type.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"ТО","Замена масла","Фильтры","Тормоза","Двигатель","КПП / сцепление","Ходовая","Электрика","Другое"}));body.addView(label("Событие"));body.addView(type,new LinearLayout.LayoutParams(-1,dp(52)));
        mileage=input("Текущий пробег, км");mileage.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);body.addView(label("Пробег"));body.addView(mileage,new LinearLayout.LayoutParams(-1,dp(52)));
        nextMileage=input("Следующее ТО / проверка на пробеге, км");nextMileage.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);body.addView(label("Следующее напоминание"));body.addView(nextMileage,new LinearLayout.LayoutParams(-1,dp(52)));
        note=input("Что сделали / какие детали заменили");note.setSingleLine(false);note.setMinLines(2);body.addView(label("Комментарий"));body.addView(note,new LinearLayout.LayoutParams(-1,dp(78)));
        Button add=blueButton("+ ДОБАВИТЬ В ЖУРНАЛ");add.setOnClickListener(v->save());body.addView(wrap(add,0,12,0,16));

        body.addView(tv("История",20,DARK,true));listBox=col();listBox.setPadding(0,dp(8),0,0);body.addView(listBox);render();
        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void save(){
        if(mileage.getText().toString().trim().isEmpty()){mileage.setError("Введите пробег");return;}
        JSONArray a=load();JSONObject o=new JSONObject();try{
            o.put("vehicle",vehicle.getSelectedItem().toString());o.put("type",type.getSelectedItem().toString());o.put("mileage",mileage.getText().toString().trim());
            o.put("next",nextMileage.getText().toString().trim());o.put("note",note.getText().toString().trim());o.put("time",System.currentTimeMillis());a.put(o);
            prefs.edit().putString("entries",a.toString()).apply();
        }catch(Exception ignored){}
        mileage.setText("");nextMileage.setText("");note.setText("");render();Toast.makeText(this,"Запись добавлена",Toast.LENGTH_SHORT).show();
    }

    private void render(){
        listBox.removeAllViews();JSONArray a=load();
        if(a.length()==0){LinearLayout c=card();c.addView(tv("Журнал пока пуст",16,DARK,true));c.addView(tv("Добавьте первое ТО или ремонт.",12,MUTED,false));listBox.addView(c);return;}
        for(int i=a.length()-1;i>=0;i--)try{
            JSONObject o=a.getJSONObject(i);LinearLayout c=card();
            c.addView(tv(o.optString("type")+" · "+o.optString("mileage")+" км",16,DARK,true));
            c.addView(tv(o.optString("vehicle"),12,MUTED,false));
            String next=o.optString("next");if(!next.isEmpty()){TextView n=tv("Следующее напоминание: "+next+" км",12,BLUE,true);n.setPadding(0,dp(5),0,0);c.addView(n);}
            String note=o.optString("note");if(!note.isEmpty())c.addView(tv(note,12,MUTED,false));
            String d=new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).format(new Date(o.optLong("time")));c.addView(tv(d,10,MUTED,false));
            listBox.addView(wrap(c,0,0,0,8));
        }catch(Exception ignored){}
    }

    private JSONArray load(){try{return new JSONArray(prefs.getString("entries","[]"));}catch(Exception e){return new JSONArray();}}
    private ArrayList<String> vehicles(){ArrayList<String> out=new ArrayList<String>();out.add("Scania без привязки");String raw=getSharedPreferences("wt_client_hub",MODE_PRIVATE).getString("fleet","");for(String row:raw.split("\n")){if(row.trim().isEmpty())continue;String[] p=row.split("\\|",-1);String model=p.length>0?p[0]:"Scania",vin=p.length>1?p[1]:"";out.add((model.isEmpty()?"Scania":model)+(vin.isEmpty()?"":" · "+vin));}return out;}
    private View top(String s){LinearLayout t=new LinearLayout(this);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(8),dp(7),dp(10),dp(7));t.setBackgroundColor(Color.WHITE);TextView b=tv("‹",34,DARK,false);b.setGravity(Gravity.CENTER);b.setOnClickListener(v->finish());t.addView(b,new LinearLayout.LayoutParams(dp(44),dp(48)));t.addView(tv(s,21,DARK,true));return t;}
    private TextView label(String s){TextView t=tv(s,12,MUTED,true);t.setPadding(dp(2),dp(10),0,dp(4));return t;}
    private EditText input(String h){EditText e=new EditText(this);e.setHint(h);e.setTextSize(15);e.setPadding(dp(14),0,dp(14),0);e.setBackground(round(Color.WHITE,14));return e;}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){LinearLayout c=col();c.setPadding(dp(14),dp(13),dp(14),dp(13));c.setBackground(round(Color.WHITE,17));return c;}
    private View wrap(View v,int l,int t,int r,int b){LinearLayout x=col();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));x.addView(v,p);return x;}
    private Button blueButton(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(round(BLUE,14));return b;}
    private TextView tv(String s,int sp,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(c);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));g.setStroke(dp(1),Color.rgb(222,230,240));return g;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
