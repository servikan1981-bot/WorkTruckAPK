package com.worktruck.catalog;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GameActivity extends Activity {
    private static final int GREEN=Color.rgb(8,91,58), DARK=Color.rgb(7,38,29), BG=Color.rgb(244,247,245), MUTED=Color.rgb(101,113,107);

    private final String[] clues={
        "Эта деталь сжимает воздух и подаёт его в пневмосистему грузовика.",
        "Этот узел передаёт крутящий момент от двигателя к коробке передач и позволяет плавно тронуться.",
        "Эта деталь охлаждает жидкость системы охлаждения набегающим потоком воздуха.",
        "Этот электрический агрегат заряжает аккумуляторы при работающем двигателе.",
        "Эта деталь распыляет топливо непосредственно в цилиндр двигателя.",
        "Этот узел помогает замедлять грузовик без постоянного использования рабочих тормозов.",
        "Эта деталь соединяет коробку передач с ведущим мостом и передаёт вращение.",
        "Этот элемент подвески использует сжатый воздух и поддерживает высоту автомобиля.",
        "Этот прибор измеряет параметры работы автомобиля и передаёт сигнал блоку управления.",
        "Этот узел увеличивает количество воздуха, поступающего в двигатель, используя энергию выхлопных газов."
    };

    private final String[][] options={
        {"Компрессор","Интеркулер","Редуктор","Стартер"},
        {"Сцепление","Радиатор","Суппорт","Генератор"},
        {"Радиатор","Маховик","Форсунка","Кардан"},
        {"Генератор","Стартер","Турбина","Компрессор"},
        {"Форсунка","Датчик ABS","Термостат","Амортизатор"},
        {"Ретардер","Стабилизатор","Сцепление","Интеркулер"},
        {"Карданный вал","Распредвал","Полуось кабины","Радиатор"},
        {"Пневмоподушка","Тормозной диск","Фара","Кронштейн"},
        {"Датчик","Шатун","Рессора","Глушитель"},
        {"Турбина","Стартер","Суппорт","ТНВД"}
    };

    private final int[] correct={0,0,0,0,0,0,0,0,0,0};
    private int question=0, score=0;
    private TextView progress, scoreView, clue;
    private final Button[] answerButtons=new Button[4];

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(Color.WHITE);
        build();
        showQuestion();
    }

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(16),dp(14),dp(16),dp(18));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=tv("‹",34,DARK,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(42),dp(46)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.addView(tv("Угадай запчасть",23,DARK,true));titles.addView(tv("Мини-игра Work Truck",12,MUTED,false));top.addView(titles,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);

        LinearLayout status=card();status.setOrientation(LinearLayout.HORIZONTAL);status.setGravity(Gravity.CENTER_VERTICAL);
        progress=tv("",13,MUTED,true);scoreView=tv("",13,GREEN,true);scoreView.setGravity(Gravity.RIGHT);
        status.addView(progress,new LinearLayout.LayoutParams(0,-2,1));status.addView(scoreView,new LinearLayout.LayoutParams(0,-2,1));root.addView(status,margin(-1,-2,0,10,0,12));

        LinearLayout q=card();
        TextView badge=tv("ЧТО ЭТО ЗА ДЕТАЛЬ?",12,GREEN,true);badge.setPadding(0,0,0,dp(10));q.addView(badge);
        clue=tv("",19,DARK,true);clue.setMinHeight(dp(120));clue.setGravity(Gravity.CENTER_VERTICAL);q.addView(clue,new LinearLayout.LayoutParams(-1,0,1));root.addView(q,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout answers=new LinearLayout(this);answers.setOrientation(LinearLayout.VERTICAL);answers.setPadding(0,dp(12),0,0);
        for(int i=0;i<4;i++){
            final int idx=i;
            Button btn=new Button(this);btn.setAllCaps(false);btn.setTextSize(16);btn.setTextColor(DARK);btn.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);btn.setPadding(dp(16),0,dp(12),0);btn.setBackground(round(Color.WHITE,15));btn.setOnClickListener(v->answer(idx));answerButtons[i]=btn;answers.addView(btn,margin(-1,dp(58),0,0,0,8));
        }
        root.addView(answers);

        TextView note=tv("Демо-призы: 5–7 правильных — скидка 1%, 8–9 — 2%, 10 из 10 — 3%. До подключения ERP купон тестовый.",11,MUTED,false);note.setGravity(Gravity.CENTER);note.setPadding(dp(6),dp(5),dp(6),0);root.addView(note);
        setContentView(root);
    }

    private void showQuestion(){
        progress.setText("Вопрос "+(question+1)+" из "+clues.length);
        scoreView.setText("Счёт: "+score);
        clue.setText(clues[question]);
        for(int i=0;i<4;i++){answerButtons[i].setText(options[question][i]);answerButtons[i].setEnabled(true);answerButtons[i].setAlpha(1f);}
    }

    private void answer(int selected){
        boolean ok=selected==correct[question];
        if(ok)score++;
        for(Button b:answerButtons)b.setEnabled(false);
        String msg=ok?"Верно!":"Правильный ответ: "+options[question][correct[question]];
        new AlertDialog.Builder(this)
            .setTitle(ok?"✓ Верно":"Не совсем")
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton(question==clues.length-1?"Результат":"Дальше",(d,w)->{
                question++;
                if(question>=clues.length)finishGame(); else showQuestion();
            }).show();
    }

    private void finishGame(){
        int discount=score==10?3:(score>=8?2:(score>=5?1:0));
        String prize;
        if(discount>0){
            String code="WT-"+new SimpleDateFormat("ddMMHHmm",Locale.getDefault()).format(new Date())+"-"+discount;
            getSharedPreferences("wt_game",MODE_PRIVATE).edit().putInt("best_score",Math.max(score,getSharedPreferences("wt_game",MODE_PRIVATE).getInt("best_score",0))).putInt("discount",discount).putString("coupon",code).apply();
            prize="Тестовый купон: "+code+"\nСкидка: "+discount+"%";
        }else{
            prize="До призовой зоны не хватило совсем немного. Попробуйте ещё раз.";
        }
        new AlertDialog.Builder(this)
            .setTitle("Результат: "+score+" из "+clues.length)
            .setMessage(prize+"\n\nПосле подключения ERP такие призы можно будет подтверждать автоматически и привязывать к клиенту.")
            .setCancelable(false)
            .setPositiveButton("Сыграть ещё",(d,w)->{question=0;score=0;showQuestion();})
            .setNegativeButton("В приложение",(d,w)->finish())
            .show();
    }

    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(13),dp(14),dp(13));c.setBackground(round(Color.WHITE,17));return c;}
    private TextView tv(String x,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(x);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));g.setStroke(dp(1),color==Color.WHITE?Color.rgb(224,230,227):color);return g;}
    private LinearLayout.LayoutParams margin(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
