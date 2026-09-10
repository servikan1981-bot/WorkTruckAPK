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
    private static final int GREEN=Color.rgb(8,91,58), DARK=Color.rgb(7,38,29), BG=Color.rgb(244,247,245), MUTED=Color.rgb(101,113,107);

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
        TextView sub=tv("Тур, игра и сервисные разделы собраны здесь, чтобы главный экран оставался простым.",13,MUTED,false);sub.setPadding(0,dp(5),0,dp(14));body.addView(sub);

        body.addView(menu("◎","Экскурсия по Work Truck","Панорамный тур по территории",()->startActivity(new Intent(this,TourActivity.class))));
        body.addView(menu("?","Игра · Угадай запчасть","10 вопросов и тестовый приз",()->startActivity(new Intent(this,GameActivity.class))));
        body.addView(menu("+","Создать заявку","Быстрый запрос на запчасть",()->openMain("request")));
        body.addView(menu("▦","Мои заказы","История заявок и заказов",()->openMain("orders")));
        body.addView(menu("●","Профиль","Профиль клиента и данные приложения",()->openMain("profile")));

        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void openMain(String screen){
        Intent i=new Intent(this,V53Activity.class);i.putExtra("screen",screen);startActivity(i);
    }

    private View menu(String icon,String title,String sub,Runnable action){
        LinearLayout c=col();c.setPadding(dp(15),dp(13),dp(15),dp(13));c.setBackground(round(Color.WHITE,18));c.setOnClickListener(v->action.run());
        LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);
        TextView ic=tv(icon,24,GREEN,true);ic.setGravity(Gravity.CENTER);r.addView(ic,new LinearLayout.LayoutParams(dp(50),dp(50)));
        LinearLayout txt=col();txt.addView(tv(title,17,DARK,true));TextView s=tv(sub,12,MUTED,false);s.setPadding(0,dp(3),0,0);txt.addView(s);r.addView(txt,new LinearLayout.LayoutParams(0,-2,1));
        TextView arr=tv("›",28,GREEN,true);arr.setGravity(Gravity.CENTER);r.addView(arr,new LinearLayout.LayoutParams(dp(28),dp(50)));c.addView(r);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(10));c.setLayoutParams(p);return c;
    }

    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView tv(String x,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(x);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(int color,int r){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(r));g.setStroke(dp(1),Color.rgb(226,232,229));return g;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
