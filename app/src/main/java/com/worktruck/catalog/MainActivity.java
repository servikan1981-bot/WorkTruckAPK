package com.worktruck.catalog;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.text.NumberFormat;
import java.util.*;

public class MainActivity extends Activity {
    static class P { String a,n,w,s; double p,q; P(String a,String n,double p,double q,String w,String s){this.a=a;this.n=n;this.p=p;this.q=q;this.w=w;this.s=s;} }
    private final ArrayList<P> all=new ArrayList<>();
    private LinearLayout results; private EditText search; private TextView counter;

    @Override public void onCreate(Bundle b){ super.onCreate(b); seed(); setContentView(build()); render(""); }
    private void seed(){
        all.add(new P("104409","ACL (система центральной смазки) в сборе без насоса",49613,1,"Нижний Новгород","5 СЕРИЯ"));
        all.add(new P("2281363","DAU Блок прерывания трансляции E58",9000,1,"Нижний Новгород","5 СЕРИЯ"));
        all.add(new P("1010","AVITO",1000,1,"","5 СЕРИЯ"));
        all.add(new P("1763365","Кронштейн Scania",12500,1,"Москва","5 СЕРИЯ"));
        all.add(new P("1898287","Патрубок Scania",3900,1,"Екатеринбург","5 СЕРИЯ"));
    }
    private View build(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(247,249,248));
        LinearLayout head=new LinearLayout(this); head.setOrientation(LinearLayout.VERTICAL); head.setPadding(dp(20),dp(22),dp(20),dp(18)); head.setBackgroundColor(Color.rgb(11,43,32));
        TextView t=new TextView(this); t.setText("Ворк Трак"); t.setTextColor(Color.WHITE); t.setTextSize(28); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); head.addView(t);
        TextView s=new TextView(this); s.setText("Запчасти Scania • тестовая версия"); s.setTextColor(Color.rgb(190,220,207)); s.setTextSize(14); head.addView(s); root.addView(head);
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16),dp(16),dp(16),dp(8));
        search=new EditText(this); search.setHint("Артикул или название"); search.setSingleLine(true); search.setImeOptions(EditorInfo.IME_ACTION_SEARCH); c.addView(search,new LinearLayout.LayoutParams(-1,dp(54)));
        Button b=new Button(this); b.setText("НАЙТИ ЗАПЧАСТЬ"); b.setTextColor(Color.WHITE); b.setBackgroundColor(Color.rgb(12,107,67)); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(52));bp.topMargin=dp(8);c.addView(b,bp);
        counter=new TextView(this); counter.setPadding(0,dp(10),0,0); c.addView(counter); root.addView(c);
        ScrollView sv=new ScrollView(this); results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);results.setPadding(dp(12),0,dp(12),dp(16));sv.addView(results);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        b.setOnClickListener(v->render(search.getText().toString())); search.setOnEditorActionListener((v,id,e)->{render(search.getText().toString());return true;}); return root;
    }
    private void render(String q){ results.removeAllViews(); String x=q.trim().toLowerCase(new Locale("ru")); int n=0; for(P p:all){ if(!x.isEmpty() && !(p.a.toLowerCase().contains(x)||p.n.toLowerCase().contains(x))) continue; n++; TextView v=new TextView(this); v.setText(p.n+"\nАрт. "+p.a+" • "+p.w+" • "+p.s+"\n"+money(p.p)+" ₽ • "+((int)p.q)+" шт."); v.setTextSize(16);v.setTextColor(Color.rgb(24,34,30));v.setPadding(dp(16),dp(14),dp(16),dp(14));v.setBackgroundColor(Color.WHITE);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));results.addView(v,lp); v.setOnClickListener(z->new AlertDialog.Builder(this).setTitle(p.n).setMessage("Артикул: "+p.a+"\nЦена: "+money(p.p)+" ₽\nНаличие: "+((int)p.q)+" шт.\nСклад: "+p.w+"\nСерия: "+p.s).setNegativeButton("Закрыть",null).setPositiveButton("Создать заявку",(d,k)->Toast.makeText(this,"Тестовая заявка создана",Toast.LENGTH_LONG).show()).show()); }
        counter.setText("Найдено: "+n+" • тестовая выборка из вашей номенклатуры");
    }
    private String money(double d){ NumberFormat f=NumberFormat.getNumberInstance(new Locale("ru","RU"));f.setMaximumFractionDigits(0);return f.format(d); }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
}
