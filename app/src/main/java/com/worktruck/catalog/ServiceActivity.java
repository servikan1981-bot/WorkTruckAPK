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
import java.text.*;
import java.util.*;

public class ServiceActivity extends Activity {
    private static final int BLUE=Color.rgb(18,92,185), DARK=Color.rgb(8,39,79), BG=Color.rgb(244,247,250), MUTED=Color.rgb(101,113,125);
    private SharedPreferences prefs;
    private LinearLayout listBox;
    private Spinner vehicle, reason;
    private EditText date, comment;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);getWindow().setStatusBarColor(DARK);getWindow().setNavigationBarColor(Color.WHITE);
        prefs=getSharedPreferences("wt_service",MODE_PRIVATE);build();
    }

    private void build(){
        LinearLayout root=col();root.setBackgroundColor(BG);
        root.addView(top("Сервис Work Truck"));
        ScrollView sv=new ScrollView(this);LinearLayout body=col();body.setPadding(dp(14),dp(14),dp(14),dp(26));

        LinearLayout hero=card();hero.setBackground(round(Color.rgb(232,241,253),18));
        hero.addView(tv("ЗАПИСЬ НА СЕРВИС",12,BLUE,true));
        hero.addView(tv("Запишите Scania на ремонт",23,DARK,true));
        hero.addView(tv("Сейчас запись сохраняется в приложении. После подключения CRM она будет уходить в сервис Work Truck, а статус ремонта обновляться автоматически.",13,MUTED,false));
        body.addView(wrap(hero,0,0,0,12));

        vehicle=new Spinner(this);vehicle.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,vehicles()));body.addView(label("Автомобиль"));body.addView(vehicle,new LinearLayout.LayoutParams(-1,dp(52)));
        reason=new Spinner(this);reason.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Диагностика","ТО","Двигатель","КПП / сцепление","Тормоза","Ходовая / рама","Электрика","Другое"}));body.addView(label("Причина обращения"));body.addView(reason,new LinearLayout.LayoutParams(-1,dp(52)));
        date=input("Желаемая дата");date.setFocusable(false);date.setOnClickListener(v->pickDate());body.addView(label("Дата"));body.addView(date,new LinearLayout.LayoutParams(-1,dp(52)));
        comment=input("Что случилось / комментарий");comment.setSingleLine(false);comment.setMinLines(2);body.addView(label("Комментарий"));body.addView(comment,new LinearLayout.LayoutParams(-1,dp(78)));

        Button book=blueButton("ЗАПИСАТЬСЯ НА СЕРВИС");book.setOnClickListener(v->saveBooking());body.addView(wrap(book,0,12,0,16));

        body.addView(tv("Мои обращения",20,DARK,true));
        TextView sub=tv("После интеграции статусы будут: подтверждена → машина принята → диагностика → ремонт → готова.",12,MUTED,false);sub.setPadding(0,dp(4),0,dp(8));body.addView(sub);
        listBox=col();body.addView(listBox);render();

        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void pickDate(){
        Calendar c=Calendar.getInstance();
        new DatePickerDialog(this,(v,y,m,d)->date.setText(String.format(Locale.getDefault(),"%02d.%02d.%04d",d,m+1,y)),
                c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void saveBooking(){
        String d=date.getText().toString().trim(), com=comment.getText().toString().trim();
        if(d.isEmpty()){date.setError("Выберите дату");return;}
        JSONArray a=load();JSONObject o=new JSONObject();
        try{
            o.put("id","S"+System.currentTimeMillis());
            o.put("vehicle",vehicle.getSelectedItem().toString());
            o.put("reason",reason.getSelectedItem().toString());
            o.put("date",d);o.put("comment",com);o.put("status","Заявка создана");o.put("created",System.currentTimeMillis());a.put(o);
            prefs.edit().putString("bookings",a.toString()).apply();
        }catch(Exception ignored){}
        comment.setText("");Toast.makeText(this,"Запись сохранена",Toast.LENGTH_SHORT).show();render();
    }

    private void render(){
        if(listBox==null)return;listBox.removeAllViews();JSONArray a=load();
        if(a.length()==0){LinearLayout c=card();c.addView(tv("Пока нет записей",16,DARK,true));c.addView(tv("Создайте первую запись на сервис.",12,MUTED,false));listBox.addView(c);return;}
        for(int i=a.length()-1;i>=0;i--){
            try{
                JSONObject o=a.getJSONObject(i);LinearLayout c=card();
                c.addView(tv(o.optString("vehicle","Scania"),17,DARK,true));
                c.addView(tv(o.optString("reason")+" · "+o.optString("date"),13,MUTED,false));
                TextView st=tv("● "+o.optString("status","Заявка создана"),13,BLUE,true);st.setPadding(0,dp(6),0,0);c.addView(st);
                String cm=o.optString("comment");if(!cm.isEmpty())c.addView(tv(cm,12,MUTED,false));
                listBox.addView(wrap(c,0,0,0,8));
            }catch(Exception ignored){}
        }
    }

    private JSONArray load(){try{return new JSONArray(prefs.getString("bookings","[]"));}catch(Exception e){return new JSONArray();}}
    private ArrayList<String> vehicles(){
        ArrayList<String> out=new ArrayList<String>();out.add("Выберите Scania");
        String raw=getSharedPreferences("wt_client_hub",MODE_PRIVATE).getString("fleet","");
        for(String row:raw.split("\n")){if(row.trim().isEmpty())continue;String[] p=row.split("\\|",-1);String model=p.length>0?p[0]:"Scania",vin=p.length>1?p[1]:"";out.add((model.isEmpty()?"Scania":model)+(vin.isEmpty()?"":" · "+vin));}
        return out;
    }

    private View top(String s){LinearLayout t=new LinearLayout(this);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(8),dp(7),dp(10),dp(7));t.setBackgroundColor(Color.WHITE);TextView b=tv("‹",34,DARK,false);b.setGravity(Gravity.CENTER);b.setOnClickListener(v->finish());t.addView(b,new LinearLayout.LayoutParams(dp(44),dp(48)));t.addView(tv(s,22,DARK,true));return t;}
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
