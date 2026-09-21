package com.worktruck.catalog;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

public class ScaniaChatActivity extends Activity {
    private static final int BLUE=Color.rgb(18,92,185), DARK=Color.rgb(8,39,79), BG=Color.rgb(244,247,250), MUTED=Color.rgb(101,113,125);
    private static final String PREF="wt_scania_chat";
    private static final String USER_KEY="chat_nickname";
    private static final String[] CHANNELS={"Общий чат","5 серия Scania","Ремонт и диагностика","Запчасти и разборка","Дорога и рейсы"};
    private static final String[] DESCRIPTIONS={
            "Общение владельцев Scania",
            "Опыт эксплуатации и особенности 5 серии",
            "Ошибки, симптомы, ремонт и сервис",
            "Подбор, взаимозаменяемость и поиск деталей",
            "Маршруты, стоянки и дорожные вопросы"
    };

    private LinearLayout root, messages;
    private ScrollView scroll;
    private TextView title, subtitle;
    private EditText input;
    private SharedPreferences prefs;
    private int channel=0;
    private String nickname;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(Color.WHITE);
        prefs=getSharedPreferences(PREF,MODE_PRIVATE);
        nickname=prefs.getString(USER_KEY,"");
        if(nickname.trim().isEmpty()) askName(false);
        build();
    }

    private void build(){
        root=col();root.setBackgroundColor(BG);

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(7),dp(8),dp(7));top.setBackgroundColor(Color.WHITE);
        TextView back=tv("‹",34,DARK,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));

        LinearLayout names=col();
        title=tv("Чат владельцев Scania",20,DARK,true);
        subtitle=tv(CHANNELS[channel],12,MUTED,false);
        names.addView(title);names.addView(subtitle);
        names.setOnClickListener(v->showChannels());
        top.addView(names,new LinearLayout.LayoutParams(0,-2,1));

        TextView profile=tv("☺",25,BLUE,true);profile.setGravity(Gravity.CENTER);profile.setOnClickListener(v->showProfile());
        top.addView(profile,new LinearLayout.LayoutParams(dp(45),dp(48)));
        TextView menu=tv("⋮",28,DARK,true);menu.setGravity(Gravity.CENTER);menu.setOnClickListener(v->showOptions(menu));
        top.addView(menu,new LinearLayout.LayoutParams(dp(40),dp(48)));
        root.addView(top);

        LinearLayout channelBar=new LinearLayout(this);channelBar.setGravity(Gravity.CENTER_VERTICAL);channelBar.setPadding(dp(12),dp(7),dp(12),dp(7));channelBar.setBackgroundColor(Color.rgb(232,241,253));
        TextView channelBtn=tv("☰  Выбрать тему",13,BLUE,true);channelBtn.setOnClickListener(v->showChannels());channelBar.addView(channelBtn,new LinearLayout.LayoutParams(0,-2,1));
        TextView members=tv("клиентский чат",12,MUTED,false);channelBar.addView(members);
        root.addView(channelBar);

        scroll=new ScrollView(this);scroll.setFillViewport(true);
        messages=col();messages.setPadding(dp(12),dp(10),dp(12),dp(12));scroll.addView(messages);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout composer=new LinearLayout(this);composer.setGravity(Gravity.BOTTOM|Gravity.CENTER_VERTICAL);composer.setPadding(dp(8),dp(7),dp(8),dp(8));composer.setBackgroundColor(Color.WHITE);
        TextView plus=tv("+",28,BLUE,true);plus.setGravity(Gravity.CENTER);plus.setOnClickListener(v->showAttachmentInfo());composer.addView(plus,new LinearLayout.LayoutParams(dp(44),dp(50)));
        input=new EditText(this);input.setHint("Сообщение…");input.setTextSize(15);input.setTextColor(DARK);input.setHintTextColor(Color.rgb(145,155,165));input.setMaxLines(4);input.setPadding(dp(14),0,dp(12),0);input.setBackground(round(Color.rgb(244,247,250),22));composer.addView(input,new LinearLayout.LayoutParams(0,dp(50),1));
        TextView send=tv("➤",25,Color.WHITE,true);send.setGravity(Gravity.CENTER);send.setBackground(round(BLUE,22));send.setOnClickListener(v->send());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(50),dp(50));sp.setMargins(dp(8),0,0,0);composer.addView(send,sp);
        root.addView(composer);

        setContentView(root);
        ensureDemo();
        render();
    }

    private void showChannels(){
        AlertDialog.Builder b=new AlertDialog.Builder(this);
        b.setTitle("Темы чата");
        b.setSingleChoiceItems(CHANNELS,channel,(d,which)->{
            channel=which;subtitle.setText(CHANNELS[channel]);d.dismiss();render();
        });
        b.show();
    }

    private void showProfile(){
        LinearLayout box=col();box.setPadding(dp(20),dp(8),dp(20),0);
        TextView info=tv("Сейчас профиль хранится на телефоне. После подключения CRM имя, компания и список автомобилей будут подставляться из карточки клиента.",13,MUTED,false);
        box.addView(info);
        new AlertDialog.Builder(this)
                .setTitle(nickname.trim().isEmpty()?"Профиль участника":nickname)
                .setView(box)
                .setNeutralButton("Изменить имя",(d,w)->askName(true))
                .setPositiveButton("ОК",null).show();
    }

    private void askName(boolean forced){
        final EditText e=new EditText(this);e.setHint("Например: Сергей · Scania R440");e.setSingleLine(true);
        if(!nickname.isEmpty())e.setText(nickname);
        AlertDialog dlg=new AlertDialog.Builder(this)
                .setTitle("Как показывать вас в чате?")
                .setMessage("Позже имя будет автоматически подтягиваться из профиля клиента Work Truck.")
                .setView(e)
                .setNegativeButton(forced?"Отмена":"Позже",null)
                .setPositiveButton("Сохранить",null).create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String n=e.getText().toString().trim();
            if(n.length()<2){e.setError("Введите имя");return;}
            nickname=n;prefs.edit().putString(USER_KEY,n).apply();dlg.dismiss();
        }));
        dlg.show();
    }

    private void showOptions(View anchor){
        PopupMenu p=new PopupMenu(this,anchor);
        p.getMenu().add("Правила сообщества");
        p.getMenu().add("О будущем подключении CRM");
        p.setOnMenuItemClickListener(i->{
            if(i.getTitle().toString().startsWith("Правила"))showRules();else showBackendInfo();
            return true;
        });p.show();
    }

    private void showRules(){
        new AlertDialog.Builder(this).setTitle("Правила чата")
                .setMessage("1. Общаемся уважительно.\n\n2. Не публикуем персональные данные других людей.\n\n3. Запрещены спам и мошеннические предложения.\n\n4. Технические советы — это опыт участников, а не официальный диагноз.\n\n5. После запуска серверной версии появятся жалобы, блокировки и модерация.")
                .setPositiveButton("Понятно",null).show();
    }

    private void showBackendInfo(){
        new AlertDialog.Builder(this).setTitle("Чат готов к подключению")
                .setMessage("Сейчас сообщения тестово хранятся только на этом телефоне. При подключении CRM интерфейс останется тем же. Сервер будет отвечать за авторизацию клиента, список комнат, историю сообщений, онлайн-доставку, изображения, push-уведомления, жалобы и блокировки.")
                .setPositiveButton("ОК",null).show();
    }

    private void showAttachmentInfo(){
        new AlertDialog.Builder(this).setTitle("Фото и файлы")
                .setMessage("Интерфейс вложений предусмотрен. Отправку фото/видео включим вместе с сервером чата, чтобы файлы корректно хранились и были доступны всем участникам.")
                .setPositiveButton("ОК",null).show();
    }

    private void send(){
        String text=input.getText().toString().trim();
        if(text.isEmpty())return;
        if(nickname.trim().isEmpty()){askName(false);Toast.makeText(this,"Сначала укажите имя участника",Toast.LENGTH_SHORT).show();return;}
        JSONArray arr=load(channel);
        JSONObject o=new JSONObject();
        try{
            o.put("author",nickname);o.put("text",text);o.put("time",System.currentTimeMillis());o.put("mine",true);
            arr.put(o);save(channel,arr);
        }catch(Exception ignored){}
        input.setText("");hideKeyboard();render();scroll.postDelayed(()->scroll.fullScroll(View.FOCUS_DOWN),80);
    }

    private void render(){
        messages.removeAllViews();
        TextView topic=tv(CHANNELS[channel],18,DARK,true);topic.setPadding(dp(4),dp(2),0,0);messages.addView(topic);
        TextView desc=tv(DESCRIPTIONS[channel],12,MUTED,false);desc.setPadding(dp(4),0,0,dp(12));messages.addView(desc);

        JSONArray arr=load(channel);
        for(int i=0;i<arr.length();i++){
            try{
                JSONObject o=arr.getJSONObject(i);
                messages.addView(message(o.optString("author"),o.optString("text"),o.optLong("time"),o.optBoolean("mine")));
            }catch(Exception ignored){}
        }
        scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }

    private View message(String author,String text,long time,boolean mine){
        LinearLayout row=new LinearLayout(this);row.setGravity(mine?Gravity.RIGHT:Gravity.LEFT);
        LinearLayout bubble=col();bubble.setPadding(dp(12),dp(8),dp(12),dp(8));bubble.setBackground(round(mine?Color.rgb(220,235,255):Color.WHITE,15));
        TextView a=tv(author,12,mine?BLUE:DARK,true);bubble.addView(a);
        TextView m=tv(text,15,DARK,false);m.setPadding(0,dp(2),0,dp(4));bubble.addView(m);
        String tm=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(time));
        TextView t=tv(tm,10,MUTED,false);t.setGravity(Gravity.RIGHT);bubble.addView(t);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.setMargins(mine?dp(45):0,0,mine?0:dp(45),dp(8));row.addView(bubble,bp);
        return row;
    }

    private void ensureDemo(){
        if(prefs.getBoolean("demo_seeded",false))return;
        String[][] demos={
                {"Алексей · R450","Всем привет! Кто недавно менял компрессор на DC13?","Дмитрий · G440","Сначала проверь разгрузочный клапан и утечки — у меня проблема была не в компрессоре."},
                {"Михаил · R420","На пятой серии кто сталкивался с вибрацией после 80 км/ч?","Илья · R440","У меня оказался подвесной и крестовина кардана."},
                {"Николай · R500","Ошибка по EGR появляется под нагрузкой. Начну с диагностики.","Сергей · сервис","Сохрани коды до сброса, это сильно помогает при поиске причины."},
                {"Владимир · P420","Ищу информацию по взаимозаменяемости задних ступиц.","Андрей · R420","Лучше сверять по VIN и номеру узла, визуально они очень похожи."},
                {"Данил · R440","На М7 сегодня большой поток, кто идёт на Нижний?","Евгений · G400","Шёл утром — плотнее обычного, но без серьёзных задержек."}
        };
        for(int c=0;c<CHANNELS.length;c++){
            JSONArray a=new JSONArray();
            try{
                JSONObject x=new JSONObject();x.put("author",demos[c][0]);x.put("text",demos[c][1]);x.put("time",System.currentTimeMillis()-3600000);x.put("mine",false);a.put(x);
                JSONObject y=new JSONObject();y.put("author",demos[c][2]);y.put("text",demos[c][3]);y.put("time",System.currentTimeMillis()-2700000);y.put("mine",false);a.put(y);
            }catch(Exception ignored){}
            save(c,a);
        }
        prefs.edit().putBoolean("demo_seeded",true).apply();
    }

    private JSONArray load(int c){
        String raw=prefs.getString("channel_"+c,"[]");
        try{return new JSONArray(raw);}catch(Exception e){return new JSONArray();}
    }
    private void save(int c,JSONArray a){prefs.edit().putString("channel_"+c,a.toString()).apply();}

    private void hideKeyboard(){try{((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);}catch(Exception ignored){}}

    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView tv(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(int color,int r){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(r));g.setStroke(dp(1),Color.rgb(224,231,240));return g;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
