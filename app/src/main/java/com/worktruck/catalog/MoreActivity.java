package com.worktruck.catalog;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MoreActivity extends Activity {
    private static final int GREEN=Color.rgb(18,92,185), DARK=Color.rgb(8,39,79), BG=Color.rgb(244,247,250), MUTED=Color.rgb(101,113,125);

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(Color.WHITE);
        build();
    }

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(10),dp(8),dp(14),dp(8));top.setBackgroundColor(Color.WHITE);
        TextView back=tv("‹",34,DARK,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));
        top.addView(tv("Ещё",22,DARK,true),new LinearLayout.LayoutParams(0,-2,1));root.addView(top);

        ScrollView sv=new ScrollView(this);LinearLayout body=col();body.setPadding(dp(14),dp(14),dp(14),dp(24));
        TextView intro=tv("Дополнительные возможности",23,DARK,true);body.addView(intro);
        TextView sub=tv("Сервис, журнал автомобиля, SOS, диагностика, подбор по VIN и сообщество.",13,MUTED,false);sub.setPadding(0,dp(5),0,dp(14));body.addView(sub);

        body.addView(section("АВТОМОБИЛЬ И СЕРВИС"));
        body.addView(sosMenu("SOS","Машина встала · SOS","Определить точку и отправить Сергею Аникинову",()->startActivity(new Intent(this,SosActivity.class))));
        body.addView(menu("SVC","Сервис Work Truck","Запись на сервис и статус ремонта",()->startActivity(new Intent(this,ServiceActivity.class))));
        body.addView(menu("LOG","Журнал автомобиля и ТО","Пробег, ремонты, обслуживание и напоминания",()->startActivity(new Intent(this,VehicleJournalActivity.class))));
        body.addView(menu("ERR","Справочник ошибок Scania","Поиск кодов, подсказки и переход к диагностике",()->startActivity(new Intent(this,ErrorGuideActivity.class))));
        body.addView(menu("VIN","Подбор по VIN · Scania Multi","Официальный каталог Scania + проверка наличия Work Truck",()->startActivity(new Intent(this,MultiLookupActivity.class))));
        body.addView(menu("OBD","Диагностика ELM327","Подключить Bluetooth-адаптер, считать ошибки и параметры",()->startActivity(new Intent(this,DiagnosticActivity.class))));

        body.addView(section("ЛИЧНЫЙ КАБИНЕТ"));
        body.addView(menu("ORD","Мои заявки","Сохранённые заявки на запчасти",()->openMain("orders")));
        body.addView(menu("PRO","Профиль","Данные клиента и информация Work Truck",()->openMain("profile")));

        body.addView(section("СООБЩЕСТВО И WORK TRUCK"));
        body.addView(menu("CHAT","Чат владельцев Scania","Общий чат, 5 серия, ремонт, запчасти и дорога",()->startActivity(new Intent(this,ScaniaChatActivity.class))));
        body.addView(menu("◎","Экскурсия по Work Truck","Панорамный тур по территории",()->startActivity(new Intent(this,TourActivity.class))));
        body.addView(menu("?","Игра · Угадай запчасть","10 вопросов и тестовый приз",()->startActivity(new Intent(this,GameActivity.class))));

        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void openMain(String screen){
        Intent i=new Intent(this,V53Activity.class);
        i.putExtra("screen",screen);
        startActivity(i);
    }

    private View sosMenu(String icon,String title,String sub,Runnable action){
        final int RED=Color.rgb(201,43,43);
        LinearLayout c=col();c.setPadding(dp(15),dp(13),dp(15),dp(13));
        GradientDrawable bg=round(Color.rgb(255,241,241),18);bg.setStroke(dp(2),RED);c.setBackground(bg);c.setOnClickListener(v->action.run());
        LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);
        TextView ic=tv(icon,16,Color.WHITE,true);ic.setGravity(Gravity.CENTER);ic.setBackground(round(RED,16));r.addView(ic,new LinearLayout.LayoutParams(dp(54),dp(50)));
        LinearLayout txt=col();txt.addView(tv(title,17,RED,true));TextView st=tv(sub,12,MUTED,false);st.setPadding(0,dp(3),0,0);txt.addView(st);r.addView(txt,new LinearLayout.LayoutParams(0,-2,1));
        TextView arr=tv("›",28,RED,true);arr.setGravity(Gravity.CENTER);r.addView(arr,new LinearLayout.LayoutParams(dp(28),dp(50)));c.addView(r);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(10));c.setLayoutParams(p);return c;
    }

    private View section(String title){
        TextView t=tv(title,12,MUTED,true);t.setPadding(dp(4),dp(12),0,dp(8));return t;
    }

    private View menu(String icon,String title,String sub,Runnable action){
        LinearLayout c=col();c.setPadding(dp(15),dp(13),dp(15),dp(13));c.setBackground(round(Color.WHITE,18));c.setOnClickListener(v->action.run());
        LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);
        TextView ic=tv(icon,18,GREEN,true);ic.setGravity(Gravity.CENTER);r.addView(ic,new LinearLayout.LayoutParams(dp(54),dp(50)));
        LinearLayout txt=col();txt.addView(tv(title,17,DARK,true));TextView s=tv(sub,12,MUTED,false);s.setPadding(0,dp(3),0,0);txt.addView(s);r.addView(txt,new LinearLayout.LayoutParams(0,-2,1));
        TextView arr=tv("›",28,GREEN,true);arr.setGravity(Gravity.CENTER);r.addView(arr,new LinearLayout.LayoutParams(dp(28),dp(50)));c.addView(r);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(10));c.setLayoutParams(p);return c;
    }

    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView tv(String x,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(x);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(int color,int r){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(r));g.setStroke(dp(1),Color.rgb(226,232,238));return g;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
