package com.worktruck.catalog;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import android.text.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(8, 91, 58);
    private static final int DARK = Color.rgb(9, 44, 33);
    private static final int BG = Color.rgb(246, 248, 247);
    private static final int MUTED = Color.rgb(105, 116, 111);
    private Database database;
    private LinearLayout root;
    private int activeTab = 0;
    private final String[] warehouses = {"Все склады", "Нижний Новгород", "Москва", "Екатеринбург"};
    private final ArrayList<String> localOrders = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(DARK);
        database = new Database(this);
        try { database.open(); } catch(Exception e) { Toast.makeText(this, "Ошибка базы номенклатуры: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        loadOrders();
        showSplash();
    }

    private void showSplash() {
        LinearLayout splash = new LinearLayout(this);
        splash.setOrientation(LinearLayout.VERTICAL);
        splash.setGravity(Gravity.CENTER);
        splash.setPadding(dp(24), dp(24), dp(24), dp(24));
        splash.setBackgroundColor(DARK);
        TextView mark = tv("▰", 72, Color.WHITE, true); mark.setGravity(Gravity.CENTER);
        TextView title = tv("Ворк Трак", 38, Color.WHITE, true); title.setGravity(Gravity.CENTER);
        TextView sub = tv("ЗАПЧАСТИ ДЛЯ SCANIA\nИ БОЛЬШИХ ВОЗМОЖНОСТЕЙ", 15, Color.rgb(205,225,216), false); sub.setGravity(Gravity.CENTER); sub.setPadding(0,dp(10),0,0);
        TextView line = tv("Вместе дальше", 14, Color.WHITE, false); line.setGravity(Gravity.CENTER); line.setPadding(0,dp(70),0,0);
        splash.addView(mark); splash.addView(title); splash.addView(sub); splash.addView(line);
        setContentView(splash);
        new Handler(Looper.getMainLooper()).postDelayed(() -> showHome(), 1100);
    }

    private void prepareRoot(int tab) {
        activeTab = tab;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);
    }

    private void showHome() {
        prepareRoot(0);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = col(); body.setPadding(dp(16), dp(12), dp(16), dp(16));
        body.addView(homeHeader());
        EditText search = roundedEdit("Поиск запчасти по артикулу или названию");
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((v,id,event)->{ String q=search.getText().toString().trim(); if(!q.isEmpty()) showCatalog(q); return true; });
        body.addView(search, lp(-1, dp(54), 0, 8));
        body.addView(banner("НОВЫЕ ПОСТУПЛЕНИЯ", "Оригинальные и аналоговые запчасти для Scania", "Перейти в каталог", () -> showCatalog("")), lp(-1, dp(142), 0, 10));
        body.addView(banner("МАШИНЫ В РАЗБОР", "Проверенные Scania. Новые позиции каждую неделю", "Смотреть наличие", () -> showCatalog("SCANIA")), lp(-1, dp(118), 0, 14));
        body.addView(sectionTitle("Быстрые действия"));
        GridLayout grid = new GridLayout(this); grid.setColumnCount(2); grid.setUseDefaultMargins(false);
        grid.addView(actionCard("⌕", "Найти по артикулу", () -> showCatalog("")), gridLp());
        grid.addView(actionCard("VIN", "Отправить VIN", () -> showRequest("")), gridLp());
        grid.addView(actionCard("▣", "Загрузить фото", () -> showRequest("")), gridLp());
        grid.addView(actionCard("☎", "Связаться с менеджером", () -> dial("88005509638")), gridLp());
        body.addView(grid, lp(-1, -2, 0, 14));
        body.addView(sectionTitle("Популярные категории"));
        LinearLayout cats = row();
        cats.addView(categoryChip("Двигатель", "двигатель"), weightLp());
        cats.addView(categoryChip("КПП", "кпп"), weightLp());
        cats.addView(categoryChip("Подвеска", "подвеска"), weightLp());
        body.addView(cats);
        TextView stat = tv("В приложении: " + database.getProductCount() + " позиций с наличием", 13, MUTED, false); stat.setPadding(0,dp(18),0,dp(10)); body.addView(stat);
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1)); root.addView(bottomNav());
    }

    private View homeHeader() {
        LinearLayout h = row(); h.setGravity(Gravity.CENTER_VERTICAL); h.setPadding(0,dp(4),0,dp(10));
        TextView logo = tv("▰  Ворк Трак", 24, DARK, true); h.addView(logo, new LinearLayout.LayoutParams(0,-2,1));
        TextView city = tv("Москва ▾", 13, MUTED, false); h.addView(city);
        return h;
    }

    private void showCatalog(String initialQuery) {
        prepareRoot(1);
        LinearLayout header = col(); header.setPadding(dp(16),dp(12),dp(16),dp(10)); header.setBackgroundColor(Color.WHITE);
        LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL); TextView back=tv("‹",32,DARK,false); back.setGravity(Gravity.CENTER); back.setOnClickListener(v->showHome()); top.addView(back,lp(dp(36),dp(40),0,0)); TextView t=tv("Каталог запчастей",22,DARK,true); top.addView(t,new LinearLayout.LayoutParams(0,-2,1)); header.addView(top);
        EditText search = roundedEdit("Артикул, номер производителя или название"); search.setText(initialQuery); search.setSingleLine(true); search.setImeOptions(EditorInfo.IME_ACTION_SEARCH); header.addView(search,lp(-1,dp(52),0,8));
        Spinner wh = new Spinner(this); ArrayAdapter<String> wa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, warehouses); wh.setAdapter(wa); header.addView(wh,lp(-1,dp(48),0,0));
        root.addView(header);
        TextView count = tv("",13,MUTED,false); count.setPadding(dp(16),dp(8),dp(16),dp(6)); root.addView(count);
        ListView list = new ListView(this); list.setDividerHeight(0); list.setPadding(dp(8),0,dp(8),dp(6)); ProductAdapter adapter = new ProductAdapter(this); list.setAdapter(adapter); root.addView(list,new LinearLayout.LayoutParams(-1,0,1)); root.addView(bottomNav());
        Runnable doSearch = () -> {
            String q=search.getText().toString(); String w=String.valueOf(wh.getSelectedItem()); count.setText("Ищем...");
            new Thread(() -> { List<Product> data=database.search(q,w); runOnUiThread(() -> { adapter.set(data); count.setText("Найдено: "+data.size()+" · показываем до 150 позиций"); }); }).start();
        };
        search.setOnEditorActionListener((v,id,e)->{doSearch.run(); return true;});
        wh.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){ public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){doSearch.run();}});
        list.setOnItemClickListener((p,v,pos,id)->showProduct(adapter.items.get(pos)));
        doSearch.run();
    }

    private void showRequest(String article) {
        prepareRoot(2);
        ScrollView scroll=new ScrollView(this); LinearLayout body=col(); body.setPadding(dp(16),dp(14),dp(16),dp(24));
        body.addView(titleWithBack("Заявка на запчасти", ()->showHome()));
        TextView help=tv("Оставьте данные, и менеджер Ворк Трак подберёт нужные детали для вашего Scania.",14,MUTED,false); help.setPadding(0,0,0,dp(14)); body.addView(help);
        body.addView(label("VIN номер")); EditText vin=roundedEdit("Например: YS2R4X20005312345"); body.addView(vin,lp(-1,dp(54),0,12));
        body.addView(label("Артикул запчасти")); EditText art=roundedEdit("Например: 1861567"); art.setText(article); body.addView(art,lp(-1,dp(54),0,12));
        body.addView(label("Фото детали")); Button photo=new Button(this); photo.setText("▣  Добавить фото"); photo.setOnClickListener(v->{ Intent i=new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI); startActivityForResult(i,77); }); body.addView(photo,lp(-1,dp(58),0,12));
        body.addView(label("Комментарий")); EditText note=roundedEdit("Состояние, аналог, срочность и другие детали"); note.setMinLines(4); note.setGravity(Gravity.TOP); body.addView(note,lp(-1,dp(130),0,16));
        Button send=greenButton("ОТПРАВИТЬ ЗАЯВКУ"); body.addView(send,lp(-1,dp(56),0,8));
        send.setOnClickListener(v->{ String a=art.getText().toString().trim(); String vno=vin.getText().toString().trim(); String n=note.getText().toString().trim(); String id=new SimpleDateFormat("ddMMyy-HHmm",Locale.getDefault()).format(new Date()); String item="#"+id+"|"+(a.isEmpty()?"Без артикула":a)+"|В работе|"+(vno.isEmpty()?"VIN не указан":vno)+"|"+n; localOrders.add(0,item); saveOrders(); new AlertDialog.Builder(this).setTitle("Заявка принята").setMessage("Тестовая заявка сохранена в приложении. Она появится в разделе «Мои заявки».\n\nСледующий этап — подключение CRM/Telegram/API для реальной отправки менеджеру.").setPositiveButton("Мои заявки",(d,x)->showOrders()).setNegativeButton("На главную",(d,x)->showHome()).show(); });
        scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); root.addView(bottomNav());
    }

    private void showOrders() {
        prepareRoot(3);
        LinearLayout body=col(); body.setPadding(dp(16),dp(14),dp(16),dp(10)); body.addView(titleWithBack("Мои заявки",()->showHome()));
        if(localOrders.isEmpty()) { TextView empty=tv("Пока заявок нет.\nСоздайте первую заявку на запчасть — она появится здесь.",16,MUTED,false); empty.setGravity(Gravity.CENTER); body.addView(empty,new LinearLayout.LayoutParams(-1,0,1)); }
        else { ScrollView s=new ScrollView(this); LinearLayout cards=col(); for(String raw:localOrders){ String[] p=raw.split("\\|",-1); LinearLayout card=card(); card.addView(tv(p.length>0?p[0]:"Заявка",16,DARK,true)); TextView status=tv(p.length>2?p[2]:"В работе",12,Color.rgb(219,126,0),true); status.setPadding(0,dp(4),0,dp(6)); card.addView(status); card.addView(tv("Артикул: "+(p.length>1?p[1]:"—"),14,DARK,false)); card.addView(tv(p.length>3?p[3]:"",13,MUTED,false)); cards.addView(card,lp(-1,-2,0,10)); } s.addView(cards); body.addView(s,new LinearLayout.LayoutParams(-1,0,1)); }
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1)); root.addView(bottomNav());
    }

    private void showProfile() {
        prepareRoot(4);
        ScrollView scroll=new ScrollView(this); LinearLayout body=col(); body.setPadding(dp(16),dp(14),dp(16),dp(22)); body.addView(titleWithBack("Профиль",()->showHome()));
        LinearLayout person=card(); TextView avatar=tv("WT",24,Color.WHITE,true); avatar.setGravity(Gravity.CENTER); avatar.setBackgroundColor(GREEN); LinearLayout.LayoutParams avp=lp(dp(58),dp(58),0,0); person.addView(avatar,avp); TextView who=tv("Клиент Ворк Трак\nВаш личный кабинет",17,DARK,true); who.setPadding(dp(14),dp(6),0,0); person.addView(who); body.addView(person,lp(-1,-2,0,12));
        LinearLayout bonus=card(); bonus.setBackgroundColor(DARK); bonus.addView(tv("Бонусный баланс\n12 450 ₽",22,Color.WHITE,true)); body.addView(bonus,lp(-1,-2,0,12));
        body.addView(sectionTitle("Ваш менеджер")); LinearLayout mgr=card(); mgr.addView(tv("Алексей Сорокин\n+7 (916) 123-45-67",16,DARK,true),new LinearLayout.LayoutParams(0,-2,1)); Button call=greenButton("Позвонить"); call.setOnClickListener(v->dial("79161234567")); mgr.addView(call,lp(dp(110),dp(48),0,0)); body.addView(mgr,lp(-1,-2,0,12));
        body.addView(sectionTitle("Ваш филиал")); LinearLayout branch=card(); branch.addView(tv("Москва\nПн–Пт 9:00–18:00\n8 800 550-96-38",15,DARK,false)); body.addView(branch,lp(-1,-2,0,12));
        body.addView(sectionTitle("Статистика")); LinearLayout stats=row(); stats.addView(statBox(""+localOrders.size(),"Заявок"),weightLp()); stats.addView(statBox("94 851","Позиций"),weightLp()); body.addView(stats);
        scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); root.addView(bottomNav());
    }

    private View bottomNav() {
        LinearLayout nav=row(); nav.setBackgroundColor(Color.WHITE); nav.setPadding(dp(6),dp(5),dp(6),dp(5));
        nav.addView(navItem("⌂","Главная",0,()->showHome()),weightLp());
        nav.addView(navItem("⌕","Каталог",1,()->showCatalog("")),weightLp());
        nav.addView(navItem("▣","Заявка",2,()->showRequest("")),weightLp());
        nav.addView(navItem("☷","Заказы",3,()->showOrders()),weightLp());
        nav.addView(navItem("●","Профиль",4,()->showProfile()),weightLp());
        return nav;
    }

    private View navItem(String icon,String text,int index,Runnable r){ LinearLayout box=col(); box.setGravity(Gravity.CENTER); TextView i=tv(icon,18,index==activeTab?GREEN:MUTED,true); i.setGravity(Gravity.CENTER); TextView t=tv(text,11,index==activeTab?GREEN:MUTED,index==activeTab); t.setGravity(Gravity.CENTER); box.addView(i); box.addView(t); box.setOnClickListener(v->r.run()); box.setPadding(0,dp(3),0,dp(3)); return box; }
    private View titleWithBack(String text,Runnable r){ LinearLayout x=row(); x.setGravity(Gravity.CENTER_VERTICAL); x.setPadding(0,0,0,dp(14)); TextView b=tv("‹",32,DARK,false); b.setGravity(Gravity.CENTER); b.setOnClickListener(v->r.run()); x.addView(b,lp(dp(36),dp(40),0,0)); x.addView(tv(text,23,DARK,true)); return x; }
    private TextView sectionTitle(String s){ TextView v=tv(s,17,DARK,true); v.setPadding(0,dp(8),0,dp(8)); return v; }
    private TextView label(String s){ TextView v=tv(s,14,DARK,true); v.setPadding(0,0,0,dp(6)); return v; }
    private EditText roundedEdit(String hint){ EditText e=new EditText(this); e.setHint(hint); e.setTextSize(15); e.setSingleLine(true); e.setPadding(dp(14),0,dp(14),0); e.setBackgroundResource(android.R.drawable.edit_text); return e; }
    private Button greenButton(String text){ Button b=new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setBackgroundColor(GREEN); return b; }
    private LinearLayout banner(String title,String sub,String cta,Runnable run){ LinearLayout box=col(); box.setPadding(dp(16),dp(16),dp(16),dp(14)); box.setBackgroundColor(DARK); TextView t=tv(title,21,Color.WHITE,true); TextView s=tv(sub,13,Color.rgb(204,224,215),false); s.setPadding(0,dp(4),0,dp(8)); Button b=greenButton(cta+"  →"); b.setOnClickListener(v->run.run()); box.addView(t); box.addView(s); box.addView(b,lp(dp(190),dp(42),0,0)); return box; }
    private View actionCard(String icon,String text,Runnable r){ LinearLayout c=card(); c.setGravity(Gravity.CENTER_VERTICAL); TextView i=tv(icon,18,GREEN,true); i.setGravity(Gravity.CENTER); c.addView(i,lp(dp(42),dp(42),0,0)); TextView t=tv(text,13,DARK,true); t.setPadding(dp(6),0,0,0); c.addView(t,new LinearLayout.LayoutParams(0,-2,1)); c.setOnClickListener(v->r.run()); return c; }
    private View categoryChip(String text,String query){ TextView t=tv(text,13,DARK,true); t.setGravity(Gravity.CENTER); t.setPadding(dp(8),dp(12),dp(8),dp(12)); t.setBackgroundColor(Color.WHITE); t.setOnClickListener(v->showCatalog(query)); return t; }
    private View statBox(String n,String label){ LinearLayout b=card(); b.setGravity(Gravity.CENTER); TextView a=tv(n,21,GREEN,true); a.setGravity(Gravity.CENTER); TextView l=tv(label,12,MUTED,false); l.setGravity(Gravity.CENTER); b.addView(a); b.addView(l); return b; }
    private LinearLayout card(){ LinearLayout x=col(); x.setPadding(dp(14),dp(13),dp(14),dp(13)); x.setBackgroundColor(Color.WHITE); return x; }
    private GridLayout.LayoutParams gridLp(){ GridLayout.LayoutParams p=new GridLayout.LayoutParams(); p.width=0; p.height=dp(82); p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); p.setMargins(dp(3),dp(3),dp(3),dp(3)); return p; }
    private LinearLayout.LayoutParams weightLp(){ return new LinearLayout.LayoutParams(0,-2,1); }
    private LinearLayout.LayoutParams lp(int w,int h,int left,int top){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h); p.setMargins(left,top,0,0); return p; }
    private LinearLayout col(){ LinearLayout x=new LinearLayout(this); x.setOrientation(LinearLayout.VERTICAL); return x; }
    private LinearLayout row(){ LinearLayout x=new LinearLayout(this); x.setOrientation(LinearLayout.HORIZONTAL); return x; }
    private TextView tv(String text,int sp,int color,boolean bold){ TextView t=new TextView(this); t.setText(text); t.setTextSize(sp); t.setTextColor(color); if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t; }
    private void dial(String number){ try{ Intent i=new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"+number)); startActivity(i);}catch(Exception e){Toast.makeText(this,"Номер: "+number,Toast.LENGTH_SHORT).show();} }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private static String safe(String s){ return s==null||s.trim().isEmpty()?"—":s; }
    private void loadOrders(){ String raw=getSharedPreferences("wt",MODE_PRIVATE).getString("orders",""); if(!raw.isEmpty()) localOrders.addAll(Arrays.asList(raw.split("\\n§\\n"))); }
    private void saveOrders(){ getSharedPreferences("wt",MODE_PRIVATE).edit().putString("orders",android.text.TextUtils.join("\n§\n",localOrders)).apply(); }

    private void showProduct(Product p){ NumberFormat nf=NumberFormat.getNumberInstance(new Locale("ru","RU")); nf.setMaximumFractionDigits(0); String text="Артикул: "+safe(p.article)+"\nНомер производителя: "+safe(p.manufacturerNo)+"\nПроизводитель: "+safe(p.manufacturer)+"\nСерия: "+safe(p.series)+"\n\nЦена: "+nf.format(p.price)+" ₽\nНаличие: "+nf.format(p.qty)+" шт.\nСклад: "+safe(p.warehouse); new AlertDialog.Builder(this).setTitle(p.name).setMessage(text).setPositiveButton("Создать заявку",(d,x)->showRequest(p.article)).setNegativeButton("Закрыть",null).show(); }

    private class ProductAdapter extends BaseAdapter {
        private final Context c; private List<Product> items=new ArrayList<>(); ProductAdapter(Context c){this.c=c;} void set(List<Product>d){items=d;notifyDataSetChanged();}
        public int getCount(){return items.size();} public Object getItem(int p){return items.get(p);} public long getItemId(int p){return items.get(p).id;}
        public View getView(int pos,View convert,ViewGroup parent){ Product p=items.get(pos); LinearLayout box;if(convert==null){box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(12),dp(14),dp(12));box.setBackgroundColor(Color.WHITE); TextView n=new TextView(c);n.setId(1001);n.setTextSize(16);n.setTextColor(DARK);n.setTypeface(Typeface.DEFAULT,Typeface.BOLD);box.addView(n);TextView a=new TextView(c);a.setId(1002);a.setTextSize(12);a.setTextColor(MUTED);a.setPadding(0,dp(4),0,0);box.addView(a);TextView pr=new TextView(c);pr.setId(1003);pr.setTextSize(15);pr.setTextColor(GREEN);pr.setTypeface(Typeface.DEFAULT,Typeface.BOLD);pr.setPadding(0,dp(6),0,0);box.addView(pr);}else box=(LinearLayout)convert; ((TextView)box.findViewById(1001)).setText(p.name);((TextView)box.findViewById(1002)).setText("Арт. "+safe(p.article)+" · "+safe(p.warehouse)+" · "+safe(p.series));NumberFormat nf=NumberFormat.getNumberInstance(new Locale("ru","RU"));nf.setMaximumFractionDigits(0);((TextView)box.findViewById(1003)).setText(nf.format(p.price)+" ₽   ·   "+nf.format(p.qty)+" шт.");return box; }
    }
}
