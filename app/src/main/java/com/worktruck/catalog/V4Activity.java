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
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

public class V4Activity extends Activity {
    private static final int GREEN = Color.rgb(8,91,58);
    private static final int DARK = Color.rgb(7,38,29);
    private static final int BG = Color.rgb(244,247,245);
    private static final int MUTED = Color.rgb(101,113,107);
    private static final int BLUE = Color.rgb(10,57,115);
    private Database database;
    private LinearLayout root;
    private int activeTab = 0;
    private boolean dbReady = false;
    private final String[] warehouses={"Все склады","Нижний Новгород","Москва","Екатеринбург"};
    private final ArrayList<String> localOrders=new ArrayList<>();
    private NumberFormat nf;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(Color.WHITE);
        nf=NumberFormat.getNumberInstance(new Locale("ru","RU"));
        nf.setMaximumFractionDigits(0);
        database=new Database(this);
        try { database.open(); dbReady=true; }
        catch(Exception e){ dbReady=false; }
        loadOrders();
        showSplash();
    }

    private void showSplash(){
        FrameLayout frame=new FrameLayout(this);
        frame.setBackgroundColor(DARK);
        ImageView photo=new ImageView(this); photo.setScaleType(ImageView.ScaleType.CENTER_CROP); photo.setAlpha(.23f);
        frame.addView(photo,new FrameLayout.LayoutParams(-1,-1));
        loadRemote(photo,"https://commons.wikimedia.org/wiki/Special:Redirect/file/Scania_truck_R_450%2C_FreshLinc.JPG?width=1200");
        LinearLayout box=col(); box.setGravity(Gravity.CENTER); box.setPadding(dp(28),dp(28),dp(28),dp(28));
        ImageView logo=new ImageView(this); logo.setImageResource(R.drawable.brand_logo); logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        GradientDrawable logoBg=roundRect(BLUE,28,0,0); logo.setBackground(logoBg); logo.setPadding(dp(24),dp(24),dp(24),dp(24));
        box.addView(logo,new LinearLayout.LayoutParams(dp(190),dp(190)));
        TextView title=tv("Ворк Трак",36,Color.WHITE,true); title.setGravity(Gravity.CENTER); title.setPadding(0,dp(18),0,0); box.addView(title);
        TextView sub=tv("ЗАПЧАСТИ ДЛЯ SCANIA\nИ БОЛЬШИХ ВОЗМОЖНОСТЕЙ",14,Color.rgb(216,230,224),false); sub.setGravity(Gravity.CENTER); sub.setPadding(0,dp(9),0,0); box.addView(sub);
        TextView tag=tv("Вместе дальше",13,Color.WHITE,false); tag.setGravity(Gravity.CENTER); tag.setPadding(0,dp(54),0,0); box.addView(tag);
        frame.addView(box,new FrameLayout.LayoutParams(-1,-1));
        setContentView(frame);
        new Handler(Looper.getMainLooper()).postDelayed(this::showHome,1250);
    }

    private void prepare(int tab){
        activeTab=tab;
        root=col(); root.setBackgroundColor(BG); setContentView(root);
    }

    private void showHome(){
        prepare(0);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout body=col(); body.setPadding(dp(14),dp(10),dp(14),dp(18));
        body.addView(header());
        EditText search=searchField("Поиск запчасти по артикулу или названию");
        search.setOnEditorActionListener((v,id,e)->{showCatalog(search.getText().toString().trim());return true;});
        body.addView(search,lp(-1,dp(52),0,8,0,0));

        body.addView(photoHero("Новые поступления","Оригинальные и аналоговые запчасти для Scania","Перейти в каталог",false,()->showCatalog("")),lp(-1,dp(180),0,12,0,0));
        body.addView(photoHero("Машины в разбор","Проверенные Scania. Новые позиции каждую неделю","Смотреть наличие",true,()->showCatalog("scania")),lp(-1,dp(145),0,10,0,0));

        body.addView(section("Быстрые действия"));
        GridLayout g=new GridLayout(this); g.setColumnCount(2);
        g.addView(action("⌕","Найти по артикулу",()->showCatalog("")),gridLp());
        g.addView(action("VIN","Отправить VIN",()->showRequest("")),gridLp());
        g.addView(action("▣","Загрузить фото",()->showRequest("")),gridLp());
        g.addView(action("☎","Связаться с менеджером",()->dial("88005509638")),gridLp());
        body.addView(g);

        body.addView(section("Популярные категории"));
        HorizontalScrollView hs=new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout cats=row(); cats.addView(cat("Двигатель","двигатель")); cats.addView(cat("Трансмиссия","трансмиссия")); cats.addView(cat("Подвеска","подвеска")); cats.addView(cat("Тормоза","тормоз"));
        hs.addView(cats); body.addView(hs);

        LinearLayout info=card();
        info.addView(tv(dbReady?"Каталог готов к работе":"Каталог загружается / база недоступна",15,dbReady?GREEN:Color.rgb(180,90,0),true));
        info.addView(tv(dbReady?("В базе: "+nf.format(database.getProductCount())+" позиций с наличием"):"Приложение не завершится с ошибкой: остальные разделы доступны.",13,MUTED,false));
        body.addView(info,lp(-1,-2,0,14,0,0));
        TextView credit=tv("Фото Scania: Wikimedia Commons, public domain",10,MUTED,false); credit.setGravity(Gravity.CENTER); credit.setPadding(0,dp(8),0,0); body.addView(credit);

        scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); root.addView(bottomNav());
    }

    private View header(){
        LinearLayout h=row(); h.setGravity(Gravity.CENTER_VERTICAL); h.setPadding(0,0,0,dp(5));
        ImageView logo=new ImageView(this); logo.setImageResource(R.drawable.brand_logo); logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE); logo.setBackground(roundRect(BLUE,14,0,0)); logo.setPadding(dp(8),dp(8),dp(8),dp(8));
        h.addView(logo,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout tx=col(); tx.setPadding(dp(10),0,0,0); tx.addView(tv("Ворк Трак",22,DARK,true)); tx.addView(tv("Москва  ▾",12,MUTED,false)); h.addView(tx,new LinearLayout.LayoutParams(0,-2,1));
        TextView b=tv("◌",18,GREEN,true); b.setGravity(Gravity.CENTER); h.addView(b,new LinearLayout.LayoutParams(dp(36),dp(36)));
        return h;
    }

    private View photoHero(String title,String sub,String cta,boolean second,Runnable click){
        FrameLayout f=new FrameLayout(this); f.setBackground(roundRect(second?Color.rgb(37,49,51):DARK,22,0,0)); f.setClipToOutline(true);
        ImageView img=new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP); img.setAlpha(second?.42f:.48f); f.addView(img,new FrameLayout.LayoutParams(-1,-1));
        loadRemote(img,second?"https://commons.wikimedia.org/wiki/Special:Redirect/file/Scania_R.jpg?width=1200":"https://commons.wikimedia.org/wiki/Special:Redirect/file/Scania_truck_R_450%2C_FreshLinc.JPG?width=1200");
        View shade=new View(this); shade.setBackgroundColor(Color.argb(105,0,25,18)); f.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content=col(); content.setPadding(dp(17),dp(15),dp(17),dp(14));
        content.addView(tv(title.toUpperCase(new Locale("ru")),21,Color.WHITE,true));
        TextView s=tv(sub,13,Color.rgb(222,233,228),false); s.setPadding(0,dp(4),0,dp(9)); content.addView(s);
        Button bt=greenButton(cta+"  →"); bt.setOnClickListener(v->click.run()); content.addView(bt,new LinearLayout.LayoutParams(dp(192),dp(42)));
        f.addView(content,new FrameLayout.LayoutParams(-1,-1)); f.setOnClickListener(v->click.run()); return f;
    }

    private void showCatalog(String initial){
        prepare(1);
        LinearLayout head=col(); head.setPadding(dp(14),dp(10),dp(14),dp(10)); head.setBackgroundColor(Color.WHITE);
        head.addView(titleBack("Каталог запчастей",this::showHome));
        EditText search=searchField("Артикул, номер производителя или название"); search.setText(initial); head.addView(search,lp(-1,dp(52),0,2,0,0));
        Spinner wh=new Spinner(this); ArrayAdapter<String> wa=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,warehouses); wh.setAdapter(wa); head.addView(wh,lp(-1,dp(46),0,8,0,0));
        root.addView(head);
        TextView count=tv("",13,MUTED,false); count.setPadding(dp(14),dp(8),dp(14),dp(8)); root.addView(count);
        ScrollView scroll=new ScrollView(this); LinearLayout results=col(); results.setPadding(dp(10),0,dp(10),dp(12)); scroll.addView(results); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); root.addView(bottomNav());

        final int[] serial={0};
        Runnable doSearch=()->{
            int token=++serial[0];
            String q=search.getText().toString().trim(); String w=String.valueOf(wh.getSelectedItem());
            results.removeAllViews(); count.setText("Поиск по каталогу…");
            if(!dbReady){
                count.setText("Каталог временно недоступен");
                TextView err=tv("База номенклатуры не открылась. Приложение продолжает работать без вылета. Установите полную сборку V4.",15,Color.rgb(150,65,30),false); err.setPadding(dp(14),dp(18),dp(14),dp(18)); results.addView(err); return;
            }
            new Thread(()->{
                try{
                    List<Product> data=database.search(q,w);
                    runOnUiThread(()->{
                        if(token!=serial[0] || isFinishing()) return;
                        count.setText("Найдено: "+data.size()+" · показываем до 150 позиций");
                        results.removeAllViews();
                        if(data.isEmpty()){
                            TextView empty=tv("Ничего не найдено. Попробуйте другой артикул или название.",15,MUTED,false); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(14),dp(28),dp(14),dp(28)); results.addView(empty); return;
                        }
                        for(Product p:data) results.addView(productCard(p),lp(-1,-2,0,0,0,8));
                    });
                }catch(Throwable ex){
                    runOnUiThread(()->{ if(token!=serial[0]) return; results.removeAllViews(); count.setText("Ошибка поиска"); TextView e=tv("Не удалось выполнить поиск, но приложение остаётся открытым. Попробуйте ещё раз.",15,Color.rgb(155,65,30),false); e.setPadding(dp(14),dp(20),dp(14),dp(20)); results.addView(e); });
                }
            }).start();
        };
        search.setOnEditorActionListener((v,id,e)->{doSearch.run();return true;});
        wh.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){ public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){doSearch.run();} });
        doSearch.run();
    }

    private View productCard(Product p){
        LinearLayout box=card();
        LinearLayout top=row(); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView nm=tv(safe(p.name),16,DARK,true); top.addView(nm,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=tv("›",24,GREEN,true); top.addView(arrow); box.addView(top);
        TextView meta=tv("Арт. "+safe(p.article)+"  ·  "+safe(p.warehouse)+"  ·  "+safe(p.series),12,MUTED,false); meta.setPadding(0,dp(5),0,0); box.addView(meta);
        TextView price=tv(nf.format(p.price)+" ₽   ·   "+nf.format(p.qty)+" шт.",15,GREEN,true); price.setPadding(0,dp(7),0,0); box.addView(price);
        box.setOnClickListener(v->showProduct(p)); return box;
    }

    private void showRequest(String article){
        prepare(2); ScrollView s=new ScrollView(this); LinearLayout b=col(); b.setPadding(dp(14),dp(12),dp(14),dp(24)); b.addView(titleBack("Заявка на запчасти",this::showHome));
        TextView h=tv("Оставьте данные, и мы подберём нужные детали для вашего Scania.",14,MUTED,false); h.setPadding(0,0,0,dp(13)); b.addView(h);
        b.addView(label("VIN номер")); EditText vin=searchField("Например: YS2R4X20005312345"); b.addView(vin,lp(-1,dp(52),0,0,0,12));
        b.addView(label("Артикул запчасти")); EditText art=searchField("Например: 1861567"); art.setText(article); b.addView(art,lp(-1,dp(52),0,0,0,12));
        b.addView(label("Фото детали")); Button ph=greenButton("▣  ДОБАВИТЬ ФОТО"); ph.setOnClickListener(v->{try{startActivityForResult(new Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI),77);}catch(Exception e){Toast.makeText(this,"Галерея недоступна",Toast.LENGTH_SHORT).show();}}); b.addView(ph,lp(-1,dp(52),0,0,0,12));
        b.addView(label("Комментарий")); EditText note=new EditText(this); note.setHint("Состояние, аналог, срочность и другие детали"); note.setTextSize(15); note.setMinLines(5); note.setGravity(Gravity.TOP); note.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE); note.setPadding(dp(12),dp(12),dp(12),dp(12)); note.setBackground(roundRect(Color.WHITE,15,Color.rgb(218,226,222),1)); b.addView(note,lp(-1,dp(132),0,0,0,15));
        Button send=greenButton("ОТПРАВИТЬ ЗАЯВКУ"); send.setOnClickListener(v->{String id=new SimpleDateFormat("ddMMyy-HHmm",Locale.getDefault()).format(new Date()); String a=art.getText().toString().trim(); String vi=vin.getText().toString().trim(); localOrders.add(0,"#"+id+"|"+(a.isEmpty()?"Без артикула":a)+"|В работе|"+(vi.isEmpty()?"VIN не указан":vi)+"|"+note.getText().toString().trim()); saveOrders(); new AlertDialog.Builder(this).setTitle("Заявка сохранена").setMessage("Заявка появилась в разделе «Мои заявки». Для коммерческого запуска следующим этапом подключается CRM/API.").setPositiveButton("Мои заявки",(d,x)->showOrders()).setNegativeButton("На главную",(d,x)->showHome()).show();}); b.addView(send,lp(-1,dp(55),0,0,0,0));
        s.addView(b); root.addView(s,new LinearLayout.LayoutParams(-1,0,1)); root.addView(bottomNav());
    }

    private void showOrders(){
        prepare(3); LinearLayout b=col(); b.setPadding(dp(14),dp(12),dp(14),dp(10)); b.addView(titleBack("Мои заявки",this::showHome));
        ScrollView sc=new ScrollView(this); LinearLayout list=col();
        if(localOrders.isEmpty()){TextView e=tv("Пока заявок нет\n\nСоздайте заявку на запчасть — она появится здесь.",16,MUTED,false); e.setGravity(Gravity.CENTER); e.setPadding(dp(10),dp(80),dp(10),0); list.addView(e);} else for(String raw:localOrders){String[] p=raw.split("\\|",-1); LinearLayout c=card(); LinearLayout t=row(); t.addView(tv(p[0],16,DARK,true),new LinearLayout.LayoutParams(0,-2,1)); TextView st=tv(p.length>2?p[2]:"В работе",12,Color.rgb(194,113,0),true); st.setBackground(roundRect(Color.rgb(255,241,214),12,0,0)); st.setPadding(dp(9),dp(5),dp(9),dp(5)); t.addView(st); c.addView(t); c.addView(tv("Артикул: "+(p.length>1?p[1]:"—"),14,DARK,false)); c.addView(tv(p.length>3?p[3]:"",12,MUTED,false)); list.addView(c,lp(-1,-2,0,0,0,8));}
        sc.addView(list); b.addView(sc,new LinearLayout.LayoutParams(-1,0,1)); root.addView(b,new LinearLayout.LayoutParams(-1,0,1)); root.addView(bottomNav());
    }

    private void showProfile(){
        prepare(4); ScrollView sc=new ScrollView(this); LinearLayout b=col(); b.setPadding(dp(14),dp(12),dp(14),dp(22)); b.addView(titleBack("Профиль",this::showHome));
        LinearLayout person=card(); LinearLayout r=row(); r.setGravity(Gravity.CENTER_VERTICAL); ImageView logo=new ImageView(this); logo.setImageResource(R.drawable.brand_logo); logo.setBackground(roundRect(BLUE,14,0,0)); logo.setPadding(dp(8),dp(8),dp(8),dp(8)); r.addView(logo,new LinearLayout.LayoutParams(dp(55),dp(55))); LinearLayout tx=col();tx.setPadding(dp(12),0,0,0);tx.addView(tv("Клиент Ворк Трак",17,DARK,true));tx.addView(tv("Личный кабинет",13,MUTED,false));r.addView(tx);person.addView(r);b.addView(person,lp(-1,-2,0,0,0,10));
        LinearLayout bonus=card(); bonus.setBackground(roundRect(GREEN,18,0,0)); bonus.addView(tv("Бонусный баланс",13,Color.rgb(215,235,226),false)); bonus.addView(tv("12 450 ₽",25,Color.WHITE,true)); b.addView(bonus,lp(-1,-2,0,0,0,12));
        b.addView(section("Ваш менеджер")); LinearLayout m=card(); m.addView(tv("Алексей Сорокин",16,DARK,true)); m.addView(tv("+7 (916) 123-45-67",13,MUTED,false)); Button call=greenButton("ПОЗВОНИТЬ"); call.setOnClickListener(v->dial("79161234567")); m.addView(call,lp(-1,dp(45),0,10,0,0)); b.addView(m);
        b.addView(section("Ваш филиал")); LinearLayout branch=card();branch.addView(tv("Москва",16,DARK,true));branch.addView(tv("Пн–Пт 9:00–18:00\n8 800 550-96-38",13,MUTED,false));b.addView(branch);
        sc.addView(b);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));root.addView(bottomNav());
    }

    private View bottomNav(){
        LinearLayout n=row(); n.setPadding(dp(5),dp(5),dp(5),dp(5)); n.setBackgroundColor(Color.WHITE);
        n.addView(nav("⌂","Главная",0,this::showHome),weight()); n.addView(nav("⌕","Каталог",1,()->showCatalog("")),weight()); n.addView(nav("▣","Заявка",2,()->showRequest("")),weight()); n.addView(nav("☰","Заказы",3,this::showOrders),weight()); n.addView(nav("●","Профиль",4,this::showProfile),weight()); return n;
    }

    private View nav(String ico,String txt,int idx,Runnable run){LinearLayout b=col();b.setGravity(Gravity.CENTER);TextView i=tv(ico,18,idx==activeTab?GREEN:MUTED,true);i.setGravity(Gravity.CENTER);TextView t=tv(txt,10,idx==activeTab?GREEN:MUTED,idx==activeTab);t.setGravity(Gravity.CENTER);b.addView(i);b.addView(t);b.setPadding(0,dp(3),0,dp(3));b.setOnClickListener(v->run.run());return b;}
    private View action(String ico,String txt,Runnable run){LinearLayout c=card();c.setGravity(Gravity.CENTER_VERTICAL);TextView i=tv(ico,17,GREEN,true);i.setGravity(Gravity.CENTER);i.setBackground(roundRect(Color.rgb(240,246,243),12,0,0));c.addView(i,new LinearLayout.LayoutParams(dp(42),dp(42)));TextView t=tv(txt,13,DARK,true);t.setPadding(dp(9),0,0,0);c.addView(t,new LinearLayout.LayoutParams(0,-2,1));c.setOnClickListener(v->run.run());return c;}
    private View cat(String txt,String q){TextView t=tv(txt,13,DARK,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(15),dp(11),dp(15),dp(11));t.setBackground(roundRect(Color.WHITE,14,Color.rgb(221,229,225),1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(0,0,dp(7),0);t.setLayoutParams(p);t.setOnClickListener(v->showCatalog(q));return t;}
    private View titleBack(String txt,Runnable r){LinearLayout x=row();x.setGravity(Gravity.CENTER_VERTICAL);x.setPadding(0,0,0,dp(10));TextView bk=tv("‹",32,DARK,false);bk.setGravity(Gravity.CENTER);bk.setOnClickListener(v->r.run());x.addView(bk,new LinearLayout.LayoutParams(dp(34),dp(40)));x.addView(tv(txt,22,DARK,true));return x;}
    private TextView section(String s){TextView t=tv(s,17,DARK,true);t.setPadding(0,dp(15),0,dp(9));return t;}
    private TextView label(String s){TextView t=tv(s,13,DARK,true);t.setPadding(0,0,0,dp(5));return t;}
    private EditText searchField(String hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setTextSize(14);e.setImeOptions(EditorInfo.IME_ACTION_SEARCH);e.setPadding(dp(13),0,dp(13),0);e.setBackground(roundRect(Color.WHITE,15,Color.rgb(216,225,221),1));return e;}
    private Button greenButton(String s){Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(roundRect(GREEN,13,0,0));return b;}
    private LinearLayout card(){LinearLayout c=col();c.setPadding(dp(13),dp(12),dp(13),dp(12));c.setBackground(roundRect(Color.WHITE,17,Color.rgb(226,233,230),1));return c;}
    private GridLayout.LayoutParams gridLp(){GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.height=dp(80);p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);p.setMargins(dp(3),dp(3),dp(3),dp(3));return p;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(l,t,r,b);return p;}
    private LinearLayout col(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);return x;}
    private LinearLayout row(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.HORIZONTAL);return x;}
    private TextView tv(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable roundRect(int fill,int radius,int stroke,int sw){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(sw>0)g.setStroke(dp(sw),stroke);return g;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private static String safe(String s){return s==null||s.trim().isEmpty()?"—":s;}
    private void dial(String n){try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+n)));}catch(Exception e){Toast.makeText(this,n,Toast.LENGTH_SHORT).show();}}
    private void loadOrders(){String raw=getSharedPreferences("wt4",MODE_PRIVATE).getString("orders","");if(!raw.isEmpty())localOrders.addAll(Arrays.asList(raw.split("\\n§\\n")));}
    private void saveOrders(){getSharedPreferences("wt4",MODE_PRIVATE).edit().putString("orders",android.text.TextUtils.join("\n§\n",localOrders)).apply();}
    private void showProduct(Product p){String m="Артикул: "+safe(p.article)+"\nНомер производителя: "+safe(p.manufacturerNo)+"\nПроизводитель: "+safe(p.manufacturer)+"\nСерия: "+safe(p.series)+"\n\nЦена: "+nf.format(p.price)+" ₽\nНаличие: "+nf.format(p.qty)+" шт.\nСклад: "+safe(p.warehouse);new AlertDialog.Builder(this).setTitle(safe(p.name)).setMessage(m).setPositiveButton("Создать заявку",(d,x)->showRequest(p.article)).setNegativeButton("Закрыть",null).show();}

    private void loadRemote(ImageView v,String url){
        new Thread(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(6000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","WorkTruckApp/4.0");try(InputStream in=c.getInputStream()){final Bitmap bm=BitmapFactory.decodeStream(in);if(bm!=null)runOnUiThread(()->{if(!isFinishing())v.setImageBitmap(bm);});}}catch(Throwable ignored){}}).start();
    }
}
