package com.worktruck.catalog;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.text.*;
import java.util.*;

public class V5Activity extends Activity {
    private static final int GREEN=Color.rgb(8,91,58), DARK=Color.rgb(7,38,29), BG=Color.rgb(244,247,245), MUTED=Color.rgb(101,113,107), BLUE=Color.rgb(10,57,115);
    private final String[] warehouses={"Все склады","Нижний Новгород","Москва","Екатеринбург"};
    private Database database; private LinearLayout root; private boolean dbReady=false; private int activeTab=0; private NumberFormat nf;
    private final ArrayList<String> localOrders=new ArrayList<>();

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); getWindow().setStatusBarColor(DARK); getWindow().setNavigationBarColor(Color.WHITE);
        nf=NumberFormat.getNumberInstance(new Locale("ru","RU")); nf.setMaximumFractionDigits(0);
        database=new Database(this); try{database.open();dbReady=true;}catch(Exception e){dbReady=false;}
        loadOrders(); showSplash();
    }

    private void showSplash(){
        FrameLayout f=new FrameLayout(this); f.setBackgroundColor(DARK);
        LinearLayout c=col(); c.setGravity(Gravity.CENTER); c.setPadding(dp(28),dp(28),dp(28),dp(28));
        ImageView logo=new ImageView(this); logo.setImageResource(R.drawable.brand_logo); logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE); logo.setBackground(roundRect(BLUE,28,0,0)); logo.setPadding(dp(22),dp(22),dp(22),dp(22));
        c.addView(logo,new LinearLayout.LayoutParams(dp(180),dp(180)));
        TextView t=tv("Ворк Трак",36,Color.WHITE,true); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(18),0,0); c.addView(t);
        TextView s=tv("КАТАЛОГ ЗАПЧАСТЕЙ SCANIA",14,Color.rgb(216,230,224),false); s.setGravity(Gravity.CENTER); s.setPadding(0,dp(8),0,0); c.addView(s);
        f.addView(c,new FrameLayout.LayoutParams(-1,-1)); setContentView(f);
        new Handler(Looper.getMainLooper()).postDelayed(this::showHome,900);
    }

    private void prepare(int tab){activeTab=tab; root=col();root.setBackgroundColor(BG);setContentView(root);}

    private void showHome(){
        prepare(0); ScrollView sv=new ScrollView(this); LinearLayout b=col(); b.setPadding(dp(14),dp(10),dp(14),dp(18));
        b.addView(header());
        EditText search=searchField("Поиск по артикулу, номеру или названию"); search.setOnEditorActionListener((v,id,e)->{showProducts(null,search.getText().toString().trim());return true;}); b.addView(search,lp(-1,dp(52),0,8,0,0));
        b.addView(hero("КАТАЛОГ ПО УЗЛАМ","Вся номенклатура разложена по основным системам Scania","Открыть каталоги",this::showCategories),lp(-1,dp(145),0,12,0,0));
        b.addView(section("Каталоги")); b.addView(categoryGrid(false));
        b.addView(section("Быстрые действия")); GridLayout g=new GridLayout(this);g.setColumnCount(2);g.addView(action("⌕","Найти запчасть",()->showProducts(null,"")),gridLp());g.addView(action("VIN","Отправить VIN",()->showRequest("")),gridLp());g.addView(action("▣","Загрузить фото",()->showRequest("")),gridLp());g.addView(action("☎","Позвонить",()->dial("88005509638")),gridLp());b.addView(g);
        LinearLayout info=card(); info.addView(tv(dbReady?"Каталог готов":"База недоступна",15,dbReady?GREEN:Color.rgb(170,80,0),true)); info.addView(tv(dbReady?("В наличии: "+nf.format(database.getProductCount())+" позиций"):"Переустановите полную сборку приложения.",13,MUTED,false)); b.addView(info,lp(-1,-2,0,14,0,0));
        sv.addView(b); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1)); root.addView(bottomNav());
    }

    private void showCategories(){
        prepare(1); ScrollView sv=new ScrollView(this); LinearLayout b=col(); b.setPadding(dp(14),dp(10),dp(14),dp(18)); b.addView(titleBack("Каталог по узлам",this::showHome));
        TextView sub=tv("Выберите систему автомобиля. Внутри каталога можно искать по артикулу и фильтровать по складу.",14,MUTED,false); sub.setPadding(0,0,0,dp(12)); b.addView(sub); b.addView(categoryGrid(true));
        sv.addView(b); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1)); root.addView(bottomNav());
    }

    private View categoryGrid(boolean showCounts){
        GridLayout g=new GridLayout(this); g.setColumnCount(2); Map<String,Integer> counts=dbReady?database.getCategoryCounts():new LinkedHashMap<>();
        for(String cat:Database.CATEGORIES){ int n=counts.containsKey(cat)?counts.get(cat):0; String icon=categoryIcon(cat); String label=showCounts?(cat+"\n"+nf.format(n)+" поз."):cat; g.addView(categoryCard(icon,label,()->showProducts(cat,"")),gridCatLp()); }
        return g;
    }

    private String categoryIcon(String c){
        if(c.equals("Двигатель"))return "⚙"; if(c.startsWith("КПП"))return "⇄"; if(c.startsWith("Шасси"))return "▱"; if(c.startsWith("Кабина"))return "▰"; if(c.equals("Крепёж"))return "●"; if(c.equals("Электрика"))return "ϟ"; if(c.startsWith("Тормоз"))return "◎"; if(c.startsWith("Топлив"))return "◈"; if(c.startsWith("Охлаж"))return "❄"; return "•••";
    }

    private void showProducts(String category,String initial){
        prepare(1); LinearLayout head=col(); head.setPadding(dp(14),dp(10),dp(14),dp(10)); head.setBackgroundColor(Color.WHITE);
        head.addView(titleBack(category==null?"Все запчасти":category,category==null?this::showCategories:this::showCategories));
        EditText search=searchField(category==null?"Артикул, номер производителя или название":"Поиск внутри каталога"); search.setText(initial); head.addView(search,lp(-1,dp(52),0,2,0,0));
        Spinner wh=new Spinner(this); wh.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,warehouses)); head.addView(wh,lp(-1,dp(46),0,8,0,0)); root.addView(head);
        TextView count=tv("",13,MUTED,false); count.setPadding(dp(14),dp(8),dp(14),dp(8)); root.addView(count);
        ScrollView sv=new ScrollView(this); LinearLayout results=col(); results.setPadding(dp(10),0,dp(10),dp(12)); sv.addView(results); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1)); root.addView(bottomNav());
        final int[] serial={0}; Runnable run=()->{int token=++serial[0];String q=search.getText().toString().trim(),w=String.valueOf(wh.getSelectedItem());results.removeAllViews();count.setText("Поиск…"); if(!dbReady){count.setText("Каталог недоступен");return;} new Thread(()->{try{List<Product> data=database.searchCategory(category,q,w);runOnUiThread(()->{if(token!=serial[0]||isFinishing())return;count.setText((category==null?"Найдено: ":"В каталоге: ")+data.size()+" · до 150 позиций");results.removeAllViews();if(data.isEmpty()){TextView e=tv("Ничего не найдено.",15,MUTED,false);e.setGravity(Gravity.CENTER);e.setPadding(dp(12),dp(30),dp(12),dp(30));results.addView(e);}else for(Product p:data)results.addView(productCard(p),lp(-1,-2,0,0,0,8));});}catch(Throwable ex){runOnUiThread(()->count.setText("Ошибка поиска"));}}).start();};
        search.setOnEditorActionListener((v,id,e)->{run.run();return true;}); wh.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){run.run();}}); run.run();
    }

    private View productCard(Product p){LinearLayout box=card();LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);top.addView(tv(safe(p.name),16,DARK,true),new LinearLayout.LayoutParams(0,-2,1));top.addView(tv("›",24,GREEN,true));box.addView(top);TextView m=tv("Арт. "+safe(p.article)+" · "+safe(p.warehouse)+" · "+safe(p.series),12,MUTED,false);m.setPadding(0,dp(5),0,0);box.addView(m);TextView pr=tv(nf.format(p.price)+" ₽ · "+nf.format(p.qty)+" шт.",15,GREEN,true);pr.setPadding(0,dp(7),0,0);box.addView(pr);box.setOnClickListener(v->showProduct(p));return box;}

    private void showRequest(String article){prepare(2);ScrollView s=new ScrollView(this);LinearLayout b=col();b.setPadding(dp(14),dp(12),dp(14),dp(24));b.addView(titleBack("Заявка на запчасти",this::showHome));b.addView(label("VIN номер"));EditText vin=searchField("YS2R4X20005312345");b.addView(vin,lp(-1,dp(52),0,0,0,12));b.addView(label("Артикул"));EditText art=searchField("Артикул запчасти");art.setText(article);b.addView(art,lp(-1,dp(52),0,0,0,12));b.addView(label("Комментарий"));EditText note=new EditText(this);note.setHint("Состояние, аналог, срочность");note.setTextSize(15);note.setMinLines(5);note.setGravity(Gravity.TOP);note.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);note.setPadding(dp(12),dp(12),dp(12),dp(12));note.setBackground(roundRect(Color.WHITE,15,Color.rgb(218,226,222),1));b.addView(note,lp(-1,dp(132),0,0,0,15));Button send=greenButton("ОТПРАВИТЬ ЗАЯВКУ");send.setOnClickListener(v->{String id=new SimpleDateFormat("ddMMyy-HHmm",Locale.getDefault()).format(new Date());localOrders.add(0,"#"+id+"|"+(art.getText().toString().trim().isEmpty()?"Без артикула":art.getText().toString().trim())+"|В работе|"+(vin.getText().toString().trim().isEmpty()?"VIN не указан":vin.getText().toString().trim())+"|"+note.getText().toString().trim());saveOrders();new AlertDialog.Builder(this).setTitle("Заявка сохранена").setMessage("Заявка добавлена в раздел «Мои заявки».").setPositiveButton("Открыть",(d,x)->showOrders()).setNegativeButton("Главная",(d,x)->showHome()).show();});b.addView(send,lp(-1,dp(56),0,0,0,0));s.addView(b);root.addView(s,new LinearLayout.LayoutParams(-1,0,1));root.addView(bottomNav());}

    private void showOrders(){prepare(3);LinearLayout b=col();b.setPadding(dp(14),dp(12),dp(14),dp(10));b.addView(titleBack("Мои заявки",this::showHome));ScrollView s=new ScrollView(this);LinearLayout list=col();if(localOrders.isEmpty()){TextView e=tv("Пока заявок нет",17,MUTED,false);e.setGravity(Gravity.CENTER);e.setPadding(0,dp(40),0,0);list.addView(e);}else for(String raw:localOrders){String[] p=raw.split("\\|",-1);LinearLayout c=card();c.addView(tv(p[0],16,DARK,true));c.addView(tv("Артикул: "+(p.length>1?p[1]:"—"),14,DARK,false));TextView st=tv(p.length>2?p[2]:"В работе",12,Color.rgb(194,111,0),true);st.setPadding(0,dp(5),0,0);c.addView(st);list.addView(c,lp(-1,-2,0,0,0,8));}s.addView(list);b.addView(s,new LinearLayout.LayoutParams(-1,0,1));root.addView(b,new LinearLayout.LayoutParams(-1,0,1));root.addView(bottomNav());}

    private void showProfile(){prepare(4);ScrollView s=new ScrollView(this);LinearLayout b=col();b.setPadding(dp(14),dp(12),dp(14),dp(20));b.addView(titleBack("Профиль",this::showHome));LinearLayout bonus=card();bonus.setBackground(roundRect(DARK,18,0,0));bonus.addView(tv("Бонусный баланс",14,Color.WHITE,false));bonus.addView(tv("12 450 ₽",26,Color.WHITE,true));b.addView(bonus);b.addView(section("Ворк Трак"));LinearLayout c=card();c.addView(tv("Москва · Нижний Новгород · Екатеринбург",14,DARK,true));c.addView(tv("8 800 550-96-38",14,GREEN,true));b.addView(c);s.addView(b);root.addView(s,new LinearLayout.LayoutParams(-1,0,1));root.addView(bottomNav());}

    private View header(){LinearLayout h=row();h.setGravity(Gravity.CENTER_VERTICAL);ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.brand_logo);logo.setBackground(roundRect(BLUE,14,0,0));logo.setPadding(dp(8),dp(8),dp(8),dp(8));h.addView(logo,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout tx=col();tx.setPadding(dp(10),0,0,0);tx.addView(tv("Ворк Трак",22,DARK,true));tx.addView(tv("Каталог запчастей Scania",12,MUTED,false));h.addView(tx);return h;}
    private View hero(String title,String sub,String cta,Runnable click){LinearLayout b=col();b.setPadding(dp(17),dp(15),dp(17),dp(14));b.setBackground(roundRect(DARK,22,0,0));b.addView(tv(title,21,Color.WHITE,true));TextView s=tv(sub,13,Color.rgb(222,233,228),false);s.setPadding(0,dp(4),0,dp(9));b.addView(s);Button bt=greenButton(cta+" →");bt.setOnClickListener(v->click.run());b.addView(bt,new LinearLayout.LayoutParams(dp(190),dp(42)));return b;}
    private View categoryCard(String icon,String text,Runnable r){LinearLayout c=card();c.setGravity(Gravity.CENTER);TextView i=tv(icon,24,GREEN,true);i.setGravity(Gravity.CENTER);TextView t=tv(text,13,DARK,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(4),dp(7),dp(4),0);c.addView(i);c.addView(t);c.setOnClickListener(v->r.run());return c;}
    private View action(String icon,String text,Runnable r){LinearLayout c=card();c.setGravity(Gravity.CENTER_VERTICAL);TextView i=tv(icon,18,GREEN,true);i.setGravity(Gravity.CENTER);c.addView(i,new LinearLayout.LayoutParams(dp(38),dp(38)));TextView t=tv(text,13,DARK,true);t.setPadding(dp(8),0,0,0);c.addView(t,new LinearLayout.LayoutParams(0,-2,1));c.setOnClickListener(v->r.run());return c;}
    private View bottomNav(){LinearLayout n=row();n.setPadding(dp(4),dp(5),dp(4),dp(5));n.setBackgroundColor(Color.WHITE);n.addView(nav("⌂","Главная",0,this::showHome),weight());n.addView(nav("☷","Каталоги",1,this::showCategories),weight());n.addView(nav("▣","Заявка",2,()->showRequest("")),weight());n.addView(nav("≡","Заказы",3,this::showOrders),weight());n.addView(nav("●","Профиль",4,this::showProfile),weight());return n;}
    private View nav(String icon,String text,int tab,Runnable r){LinearLayout b=col();b.setGravity(Gravity.CENTER);TextView i=tv(icon,17,tab==activeTab?GREEN:MUTED,true);i.setGravity(Gravity.CENTER);TextView t=tv(text,10,tab==activeTab?GREEN:MUTED,tab==activeTab);t.setGravity(Gravity.CENTER);b.addView(i);b.addView(t);b.setOnClickListener(v->r.run());return b;}
    private View titleBack(String t,Runnable r){LinearLayout x=row();x.setGravity(Gravity.CENTER_VERTICAL);x.setPadding(0,0,0,dp(10));TextView back=tv("‹",32,DARK,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->r.run());x.addView(back,new LinearLayout.LayoutParams(dp(34),dp(40)));x.addView(tv(t,22,DARK,true));return x;}
    private void showProduct(Product p){new AlertDialog.Builder(this).setTitle(safe(p.name)).setMessage("Артикул: "+safe(p.article)+"\nНомер производителя: "+safe(p.manufacturerNo)+"\nПроизводитель: "+safe(p.manufacturer)+"\nСерия: "+safe(p.series)+"\n\nЦена: "+nf.format(p.price)+" ₽\nНаличие: "+nf.format(p.qty)+" шт.\nСклад: "+safe(p.warehouse)).setPositiveButton("Создать заявку",(d,x)->showRequest(p.article)).setNegativeButton("Закрыть",null).show();}
    private EditText searchField(String h){EditText e=new EditText(this);e.setHint(h);e.setTextSize(15);e.setSingleLine(true);e.setImeOptions(EditorInfo.IME_ACTION_SEARCH);e.setPadding(dp(13),0,dp(13),0);e.setBackground(roundRect(Color.WHITE,15,Color.rgb(218,226,222),1));return e;}
    private Button greenButton(String t){Button b=new Button(this);b.setText(t);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(roundRect(GREEN,13,0,0));return b;}
    private TextView section(String s){TextView v=tv(s,17,DARK,true);v.setPadding(0,dp(16),0,dp(9));return v;} private TextView label(String s){TextView v=tv(s,14,DARK,true);v.setPadding(0,0,0,dp(6));return v;}
    private LinearLayout card(){LinearLayout x=col();x.setPadding(dp(13),dp(12),dp(13),dp(12));x.setBackground(roundRect(Color.WHITE,17,Color.rgb(226,232,229),1));return x;}
    private GridLayout.LayoutParams gridLp(){GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.height=dp(76);p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);p.setMargins(dp(3),dp(3),dp(3),dp(3));return p;} private GridLayout.LayoutParams gridCatLp(){GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.height=dp(106);p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);p.setMargins(dp(4),dp(4),dp(4),dp(4));return p;}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(l,t,r,b);return p;} private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);} private LinearLayout col(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);return x;} private LinearLayout row(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.HORIZONTAL);return x;}
    private TextView tv(String s,int sp,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(c);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;} private GradientDrawable roundRect(int color,int radius,int stroke,int sw){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));if(sw>0)g.setStroke(dp(sw),stroke);return g;} private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);} private static String safe(String s){return s==null||s.trim().isEmpty()?"—":s;}
    private void dial(String n){try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+n)));}catch(Exception e){Toast.makeText(this,n,Toast.LENGTH_SHORT).show();}} private void loadOrders(){String raw=getSharedPreferences("wt",MODE_PRIVATE).getString("orders","");if(!raw.isEmpty())localOrders.addAll(Arrays.asList(raw.split("\\n§\\n")));} private void saveOrders(){getSharedPreferences("wt",MODE_PRIVATE).edit().putString("orders",android.text.TextUtils.join("\n§\n",localOrders)).apply();}
}
