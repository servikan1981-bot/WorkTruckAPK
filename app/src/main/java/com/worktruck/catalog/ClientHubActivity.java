package com.worktruck.catalog;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.util.*;

public class ClientHubActivity extends Activity {
    private static final int GREEN=Color.rgb(8,91,58), DARK=Color.rgb(7,38,29), BG=Color.rgb(244,247,245), MUTED=Color.rgb(101,113,107), BLUE=Color.rgb(10,57,115);
    private SharedPreferences prefs;
    private LinearLayout root;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(Color.WHITE);
        prefs=getSharedPreferences("wt_client_hub",MODE_PRIVATE);
        showHub();
    }

    private void base(String title,Runnable back){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(10),dp(8),dp(14),dp(8));top.setBackgroundColor(Color.WHITE);
        TextView b=tv("‹",34,DARK,false);b.setGravity(Gravity.CENTER);b.setOnClickListener(v->back.run());top.addView(b,new LinearLayout.LayoutParams(dp(44),dp(48)));
        top.addView(tv(title,22,DARK,true),new LinearLayout.LayoutParams(0,-2,1));root.addView(top);
        setContentView(root);
    }

    private void showHub(){
        base("Клиенту",this::finish);
        ScrollView sv=new ScrollView(this);LinearLayout body=col();body.setPadding(dp(14),dp(14),dp(14),dp(24));
        TextView intro=tv("Личный кабинет Work Truck",24,DARK,true);body.addView(intro);
        TextView sub=tv("Пять быстрых сервисов для постоянного клиента. Сейчас часть функций работает локально; после подключения ERP данные станут персональными и обновляемыми автоматически.",13,MUTED,false);sub.setPadding(0,dp(6),0,dp(14));body.addView(sub);
        body.addView(menuCard("🚛","Мой автопарк","Сохраните свои Scania: модель, VIN и госномер",this::showFleet));
        body.addView(menuCard("★","Бонусный кошелёк","Баланс, купоны и будущие начисления",this::showBonus));
        body.addView(menuCard("🔔","Сообщить о поступлении","Запомнить нужную деталь и не потерять её",this::showWatch));
        body.addView(menuCard("📦","Где мой заказ?","Быстрый просмотр статуса заявки или заказа",this::showOrderStatus));
        body.addView(menuCard("☎","Персональный менеджер","Один экран для звонка и сообщения",this::showManager));
        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    }

    private View menuCard(String icon,String title,String sub,Runnable r){
        LinearLayout card=card();card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setOnClickListener(v->r.run());
        TextView i=tv(icon,25,GREEN,false);i.setGravity(Gravity.CENTER);card.addView(i,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout t=col();TextView a=tv(title,17,DARK,true);TextView s=tv(sub,12,MUTED,false);s.setPadding(0,dp(3),0,0);t.addView(a);t.addView(s);card.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        TextView ar=tv("›",28,GREEN,true);ar.setGravity(Gravity.CENTER);card.addView(ar,new LinearLayout.LayoutParams(dp(30),dp(52)));
        return wrapMargin(card,0,0,0,10);
    }

    private void showFleet(){
        base("Мой автопарк",this::showHub);
        ScrollView sv=new ScrollView(this);LinearLayout body=col();body.setPadding(dp(14),dp(14),dp(14),dp(24));
        body.addView(tv("Ваши автомобили",21,DARK,true));
        TextView sub=tv("Добавьте Scania один раз — VIN и госномер останутся в приложении для будущих заявок.",13,MUTED,false);sub.setPadding(0,dp(5),0,dp(12));body.addView(sub);
        String raw=prefs.getString("fleet","");
        if(raw.trim().isEmpty()){
            LinearLayout empty=card();empty.addView(tv("Автомобили пока не добавлены",16,DARK,true));empty.addView(tv("Нажмите кнопку ниже и сохраните первую машину.",12,MUTED,false));body.addView(wrapMargin(empty,0,0,0,12));
        }else{
            String[] rows=raw.split("\\n");
            for(int idx=0;idx<rows.length;idx++){
                String row=rows[idx];if(row.trim().isEmpty())continue;
                String[] p=row.split("\\|",-1);
                String model=p.length>0?p[0]:"Scania", vin=p.length>1?p[1]:"", plate=p.length>2?p[2]:"";
                LinearLayout c=card();c.addView(tv(model.isEmpty()?"Scania":model,17,DARK,true));
                c.addView(tv("VIN: "+(vin.isEmpty()?"—":vin)+"\nГосномер: "+(plate.isEmpty()?"—":plate),13,MUTED,false));body.addView(wrapMargin(c,0,0,0,10));
            }
        }
        Button add=greenButton("+ ДОБАВИТЬ АВТОМОБИЛЬ");add.setOnClickListener(v->addVehicle());body.addView(add,new LinearLayout.LayoutParams(-1,dp(54)));
        if(!raw.trim().isEmpty()){
            Button clear=lightButton("Очистить автопарк");clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Очистить автопарк?").setMessage("Все сохранённые автомобили будут удалены с этого телефона.").setPositiveButton("Удалить",(d,w)->{prefs.edit().remove("fleet").apply();showFleet();}).setNegativeButton("Отмена",null).show());body.addView(wrapMargin(clear,0,10,0,0));
        }
        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    }

    private void addVehicle(){
        LinearLayout f=col();f.setPadding(dp(18),dp(6),dp(18),0);
        EditText model=input("Модель, например Scania R450");EditText vin=input("VIN / номер шасси");EditText plate=input("Госномер");f.addView(model);f.addView(vin);f.addView(plate);
        new AlertDialog.Builder(this).setTitle("Добавить автомобиль").setView(f).setPositiveButton("Сохранить",(d,w)->{
            String m=clean(model.getText().toString()), v=clean(vin.getText().toString()), p=clean(plate.getText().toString());
            if(m.isEmpty()&&v.isEmpty()&&p.isEmpty()){toast("Заполните хотя бы одно поле");return;}
            String raw=prefs.getString("fleet","");String row=m+"|"+v+"|"+p;String out=raw.trim().isEmpty()?row:raw+"\n"+row;prefs.edit().putString("fleet",out).apply();showFleet();
        }).setNegativeButton("Отмена",null).show();
    }

    private void showBonus(){
        base("Бонусный кошелёк",this::showHub);
        ScrollView sv=new ScrollView(this);LinearLayout body=col();body.setPadding(dp(14),dp(14),dp(14),dp(24));
        LinearLayout hero=card();hero.setBackground(round(GREEN,20));hero.addView(tv("БОНУСНЫЙ КОШЕЛЁК",12,Color.rgb(190,225,207),true));TextView bal=tv("0 бонусов",34,Color.WHITE,true);bal.setPadding(0,dp(4),0,dp(5));hero.addView(bal);hero.addView(tv("Баланс будет подгружаться из ERP после подключения клиентского кабинета.",12,Color.rgb(220,236,228),false));body.addView(wrapMargin(hero,0,0,0,12));
        SharedPreferences gp=getSharedPreferences("wt_game",MODE_PRIVATE);String coupon=gp.getString("coupon","");int disc=gp.getInt("discount",0);int best=gp.getInt("best_score",0);
        LinearLayout game=card();game.addView(tv("Игровой приз",17,DARK,true));
        if(coupon.isEmpty()) game.addView(tv("Пока нет купона. Сыграйте в «Угадай запчасть» на главной.",13,MUTED,false));
        else {game.addView(tv("Купон: "+coupon,16,GREEN,true));game.addView(tv("Тестовая скидка: "+disc+"% · лучший результат: "+best+"/10",13,MUTED,false));}
        body.addView(wrapMargin(game,0,0,0,12));
        LinearLayout info=card();info.addView(tv("После интеграции здесь будет",16,DARK,true));info.addView(tv("• текущий баланс бонусов\n• сколько можно списать\n• дата сгорания бонусов\n• история начислений и списаний",13,MUTED,false));body.addView(info);
        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    }

    private void showWatch(){
        base("Сообщить о поступлении",this::showHub);
        ScrollView sv=new ScrollView(this);LinearLayout body=col();body.setPadding(dp(14),dp(14),dp(14),dp(24));
        body.addView(tv("Лист ожидания",21,DARK,true));TextView sub=tv("Добавьте артикул или название детали. Сейчас список хранится на телефоне; после подключения ERP приложение сможет присылать уведомление при появлении остатка.",13,MUTED,false);sub.setPadding(0,dp(5),0,dp(12));body.addView(sub);
        String raw=prefs.getString("watch","");
        if(raw.trim().isEmpty()){LinearLayout e=card();e.addView(tv("Лист ожидания пуст",16,DARK,true));e.addView(tv("Добавьте первую нужную запчасть.",12,MUTED,false));body.addView(wrapMargin(e,0,0,0,12));}
        else for(String row:raw.split("\\n")){if(row.trim().isEmpty())continue;LinearLayout c=card();c.addView(tv(row,16,DARK,true));c.addView(tv("Ожидание поступления",12,GREEN,true));body.addView(wrapMargin(c,0,0,0,9));}
        Button add=greenButton("+ ДОБАВИТЬ ЗАПЧАСТЬ");add.setOnClickListener(v->addWatch());body.addView(add,new LinearLayout.LayoutParams(-1,dp(54)));
        if(!raw.trim().isEmpty()){Button clear=lightButton("Очистить лист ожидания");clear.setOnClickListener(v->{prefs.edit().remove("watch").apply();showWatch();});body.addView(wrapMargin(clear,0,10,0,0));}
        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    }

    private void addWatch(){
        EditText e=input("Артикул или название детали");e.setPadding(dp(16),0,dp(16),0);
        new AlertDialog.Builder(this).setTitle("Добавить в ожидание").setView(e).setPositiveButton("Добавить",(d,w)->{String x=clean(e.getText().toString());if(x.isEmpty()){toast("Введите артикул или название");return;}String raw=prefs.getString("watch","");prefs.edit().putString("watch",raw.trim().isEmpty()?x:raw+"\n"+x).apply();showWatch();}).setNegativeButton("Отмена",null).show();
    }

    private void showOrderStatus(){
        base("Где мой заказ?",this::showHub);
        ScrollView sv=new ScrollView(this);LinearLayout body=col();body.setPadding(dp(14),dp(14),dp(14),dp(24));
        body.addView(tv("Статус заказа",21,DARK,true));TextView sub=tv("Введите номер заявки или заказа. В этой тестовой версии отображается демонстрационный трек; после подключения ERP статус будет реальным.",13,MUTED,false);sub.setPadding(0,dp(5),0,dp(12));body.addView(sub);
        EditText order=input("Например: 19488");body.addView(order,new LinearLayout.LayoutParams(-1,dp(54)));
        Button find=greenButton("ПРОВЕРИТЬ СТАТУС");body.addView(wrapMargin(find,0,10,0,12));
        LinearLayout result=card();result.setVisibility(View.GONE);body.addView(result);
        find.setOnClickListener(v->{String n=clean(order.getText().toString());if(n.isEmpty()){toast("Введите номер заказа");return;}result.removeAllViews();result.addView(tv("Заказ №"+n,18,DARK,true));TextView badge=tv("● Заявка принята",14,GREEN,true);badge.setPadding(0,dp(7),0,dp(8));result.addView(badge);result.addView(tv("Заявка → Счёт → Оплата → Комплектуется → Готов → Отгружен",13,MUTED,false));TextView demo=tv("ТЕСТОВЫЙ РЕЖИМ",11,Color.rgb(170,100,0),true);demo.setPadding(0,dp(10),0,0);result.addView(demo);result.setVisibility(View.VISIBLE);});
        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    }

    private void showManager(){
        base("Персональный менеджер",this::showHub);
        ScrollView sv=new ScrollView(this);LinearLayout body=col();body.setPadding(dp(14),dp(14),dp(14),dp(24));
        LinearLayout c=card();
        TextView avatar=tv("WT",28,Color.WHITE,true);avatar.setGravity(Gravity.CENTER);avatar.setBackground(round(BLUE,40));c.addView(avatar,new LinearLayout.LayoutParams(dp(76),dp(76)));
        TextView name=tv("Ваш персональный менеджер",20,DARK,true);name.setPadding(0,dp(12),0,dp(4));c.addView(name);c.addView(tv("После авторизации ERP здесь появятся имя, фото и прямые контакты закреплённого менеджера.",13,MUTED,false));body.addView(wrapMargin(c,0,0,0,12));
        Button call=greenButton("ПОЗВОНИТЬ 8 800 550-96-38");call.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:88005509638"))));body.addView(call,new LinearLayout.LayoutParams(-1,dp(54)));
        Button msg=lightButton("Написать в WhatsApp");msg.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/78005509638")));}catch(Throwable e){toast("WhatsApp не найден");}});body.addView(wrapMargin(msg,0,10,0,0));
        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    }

    @Override public void onBackPressed(){showHub();}

    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){LinearLayout c=col();c.setPadding(dp(14),dp(13),dp(14),dp(13));c.setBackground(round(Color.WHITE,17));return c;}
    private View wrapMargin(View v,int l,int t,int r,int b){LinearLayout w=col();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));w.addView(v,p);return w;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(15);e.setSingleLine(true);e.setPadding(dp(14),0,dp(14),0);e.setBackground(round(Color.WHITE,14));return e;}
    private Button greenButton(String x){Button b=new Button(this);b.setText(x);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(round(GREEN,14));return b;}
    private Button lightButton(String x){Button b=new Button(this);b.setText(x);b.setTextColor(GREEN);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(round(Color.WHITE,14));return b;}
    private TextView tv(String x,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(x);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));g.setStroke(dp(1),color==Color.WHITE?Color.rgb(224,230,227):color);return g;}
    private String clean(String s){return s==null?"":s.replace("|"," ").replace("\n"," ").trim();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
