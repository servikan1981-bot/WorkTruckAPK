package com.worktruck.catalog;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;

public class ErrorGuideActivity extends Activity {
    private static final int BLUE=Color.rgb(18,92,185), DARK=Color.rgb(8,39,79), BG=Color.rgb(244,247,250), MUTED=Color.rgb(101,113,125);
    private EditText query;private LinearLayout box;

    private static final String[][] DATA={
        {"P0100","Расход воздуха","Цепь датчика массового расхода воздуха. Проверить разъём, проводку, подсос воздуха и сам датчик."},
        {"P0101","Расход воздуха","Сигнал MAF вне ожидаемого диапазона. Проверить загрязнение датчика, фильтр, герметичность впуска."},
        {"P0115","Температура ОЖ","Цепь датчика температуры охлаждающей жидкости. Проверить датчик, разъём и проводку."},
        {"P0201","Форсунка","Неисправность цепи управления форсункой цилиндра. Нужна проверка проводки и форсунки."},
        {"P0299","Наддув","Недостаточное давление наддува. Возможны утечки, управление турбиной, актуатор, интеркулер."},
        {"P0300","Двигатель","Обнаружены пропуски/нестабильное сгорание. Нужна дальнейшая диагностика топлива и механики."},
        {"P0401","EGR","Недостаточный поток EGR. Проверить клапан, каналы, датчики и загрязнение системы."},
        {"P0402","EGR","Избыточный поток EGR. Проверить клапан, управление и датчики."},
        {"P0560","Питание","Ненормальное напряжение системы. Проверить аккумуляторы, генератор, массы и соединения."},
        {"P0700","Трансмиссия","Блок трансмиссии сообщил о неисправности. Нужны дополнительные коды из блока КПП."},
        {"P2002","Сажевый фильтр","Эффективность DPF ниже порога. Проверить дифференциальное давление, регенерацию и датчики."},
        {"P2453","DPF","Датчик дифференциального давления DPF: диапазон/характеристика."},
        {"U0100","CAN / связь","Потеря связи с блоком управления двигателем. Проверить питание блоков и CAN-шину."},
        {"C0035","Шасси / ABS","Цепь датчика скорости колеса. Проверить датчик, проводку и задающее кольцо."}
    };

    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(DARK);getWindow().setNavigationBarColor(Color.WHITE);build();}

    private void build(){
        LinearLayout root=col();root.setBackgroundColor(BG);root.addView(top("Справочник ошибок Scania"));
        ScrollView sv=new ScrollView(this);LinearLayout body=col();body.setPadding(dp(14),dp(14),dp(14),dp(26));
        LinearLayout hero=card();hero.setBackground(round(Color.rgb(232,241,253),18));hero.addView(tv("КОДЫ И СИМПТОМЫ",12,BLUE,true));hero.addView(tv("Что означает ошибка?",23,DARK,true));hero.addView(tv("Локальный справочник содержит распространённые стандартные OBD-II коды. Фирменные коды блоков Scania будут расширяться после подключения серверного справочника.",13,MUTED,false));body.addView(wrap(hero,0,0,0,12));

        query=new EditText(this);query.setHint("Введите код или слово: P0401, EGR, наддув…");query.setSingleLine(true);query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);query.setPadding(dp(14),0,dp(14),0);query.setBackground(round(Color.WHITE,14));body.addView(query,new LinearLayout.LayoutParams(-1,dp(54)));
        Button search=blueButton("НАЙТИ ОШИБКУ");search.setOnClickListener(v->render(query.getText().toString()));query.setOnEditorActionListener((v,a,e)->{render(query.getText().toString());return true;});body.addView(wrap(search,0,8,0,12));

        LinearLayout links=new LinearLayout(this);
        Button obd=lightButton("ОТКРЫТЬ ELM327");obd.setOnClickListener(v->startActivity(new Intent(this,DiagnosticActivity.class)));links.addView(obd,new LinearLayout.LayoutParams(0,dp(50),1));
        Button vin=lightButton("ПОДБОР ПО VIN");vin.setOnClickListener(v->startActivity(new Intent(this,MultiLookupActivity.class)));LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(0,dp(50),1);vp.setMargins(dp(8),0,0,0);links.addView(vin,vp);body.addView(links);

        box=col();box.setPadding(0,dp(12),0,0);body.addView(box);render("");
        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void render(String q){
        box.removeAllViews();String s=q==null?"":q.trim().toUpperCase(Locale.US);int count=0;
        for(String[] d:DATA){
            String all=(d[0]+" "+d[1]+" "+d[2]).toUpperCase(Locale.US);
            if(s.isEmpty()||all.contains(s)){LinearLayout c=card();c.addView(tv(d[0]+" · "+d[1],17,DARK,true));TextView x=tv(d[2],13,MUTED,false);x.setPadding(0,dp(5),0,0);c.addView(x);box.addView(wrap(c,0,0,0,8));count++;}
        }
        if(count==0){
            LinearLayout c=card();c.addView(tv("Код "+s+" пока не найден",17,DARK,true));
            c.addView(tv(generic(s),13,MUTED,false));box.addView(c);
        }
    }

    private String generic(String c){
        if(c.startsWith("P0")||c.startsWith("P1")||c.startsWith("P2"))return "Это код силового агрегата. Считайте полный набор кодов через ELM327 и не стирайте их до диагностики.";
        if(c.startsWith("C"))return "Код относится к шасси/ходовой системе. Для Scania может потребоваться расширенная диагностика блока.";
        if(c.startsWith("B"))return "Код относится к кузову/кабине. Нужна расшифровка конкретного блока.";
        if(c.startsWith("U"))return "Код связи между электронными блоками. Проверьте питание, массы и CAN-сеть.";
        return "Проверьте точное написание кода. Для фирменных кодов Scania используйте профессиональную диагностику или передайте код специалисту Work Truck.";
    }

    private View top(String s){LinearLayout t=new LinearLayout(this);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(8),dp(7),dp(10),dp(7));t.setBackgroundColor(Color.WHITE);TextView b=tv("‹",34,DARK,false);b.setGravity(Gravity.CENTER);b.setOnClickListener(v->finish());t.addView(b,new LinearLayout.LayoutParams(dp(44),dp(48)));t.addView(tv(s,21,DARK,true));return t;}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){LinearLayout c=col();c.setPadding(dp(14),dp(13),dp(14),dp(13));c.setBackground(round(Color.WHITE,17));return c;}
    private View wrap(View v,int l,int t,int r,int b){LinearLayout x=col();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));x.addView(v,p);return x;}
    private Button blueButton(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(round(BLUE,14));return b;}
    private Button lightButton(String s){Button b=new Button(this);b.setText(s);b.setTextColor(DARK);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(round(Color.WHITE,14));return b;}
    private TextView tv(String s,int sp,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(c);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));g.setStroke(dp(1),Color.rgb(222,230,240));return g;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
