package com.worktruck.catalog;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import android.widget.*;

import java.text.DecimalFormat;
import java.util.List;

public class MultiLookupActivity extends Activity {
    private static final int BLUE=Color.rgb(18,92,185), DARK=Color.rgb(8,39,79), BG=Color.rgb(244,247,250), MUTED=Color.rgb(101,113,125);
    private static final String SCANIA_PARTS_ONLINE="https://spp.scania.com/Site/CustomerLogin/Login.aspx";

    private LinearLayout root, webHolder, stockBox;
    private WebView web;
    private EditText chassisInput, partInput;
    private TextView webStatus, dbStatus;
    private Database database;
    private boolean dbReady=false;
    private final DecimalFormat money=new DecimalFormat("#,##0");

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(Color.WHITE);
        build();
        openDatabase();
    }

    private void build(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(7),dp(10),dp(7));top.setBackgroundColor(Color.WHITE);
        TextView back=tv("‹",34,DARK,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));
        LinearLayout ttl=col();ttl.addView(tv("Подбор по VIN · Scania Multi",20,DARK,true));ttl.addView(tv("Официальный каталог + наличие Work Truck",11,MUTED,false));top.addView(ttl,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);

        ScrollView sv=new ScrollView(this);LinearLayout body=col();body.setPadding(dp(14),dp(14),dp(14),dp(28));

        LinearLayout intro=card();intro.setBackground(round(Color.rgb(232,241,253),18));
        intro.addView(tv("1 · ПОДОБРАТЬ НОМЕР ДЕТАЛИ",12,BLUE,true));
        TextView h=tv("Scania Multi Parts Web",23,DARK,true);h.setPadding(0,dp(3),0,dp(4));intro.addView(h);
        intro.addView(tv("Введите chassis/VIN для удобства, затем откройте официальный каталог Scania. Доступ к каталогу может потребовать учётную запись Scania.",13,MUTED,false));
        body.addView(wrap(intro,0,0,0,10));

        chassisInput=new EditText(this);chassisInput.setHint("Chassis / VIN (например 7 цифр chassis)");chassisInput.setSingleLine(true);chassisInput.setTextSize(15);chassisInput.setPadding(dp(14),0,dp(14),0);chassisInput.setBackground(round(Color.WHITE,14));
        body.addView(chassisInput,new LinearLayout.LayoutParams(-1,dp(52)));

        LinearLayout buttons=new LinearLayout(this);buttons.setPadding(0,dp(8),0,0);
        Button open=blueButton("ОТКРЫТЬ SCANIA MULTI");open.setOnClickListener(v->openMulti());
        buttons.addView(open,new LinearLayout.LayoutParams(0,dp(52),1));
        Button external=lightButton("В БРАУЗЕРЕ");external.setOnClickListener(v->openExternal());
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,dp(52),0.56f);ep.setMargins(dp(8),0,0,0);buttons.addView(external,ep);
        body.addView(buttons);

        webStatus=tv("Каталог ещё не открыт.",12,MUTED,false);webStatus.setPadding(dp(2),dp(7),dp(2),dp(6));body.addView(webStatus);

        webHolder=col();
        body.addView(webHolder);

        LinearLayout check=card();check.setBackground(round(Color.WHITE,18));
        check.addView(tv("2 · ПРОВЕРИТЬ НАЛИЧИЕ У НАС",12,BLUE,true));
        TextView ch=tv("Нашли номер детали в Multi?",20,DARK,true);ch.setPadding(0,dp(4),0,dp(4));check.addView(ch);
        check.addView(tv("Введите оригинальный номер Scania или наш артикул. Поиск идёт по встроенной базе Work Truck.",13,MUTED,false));
        partInput=new EditText(this);partInput.setHint("Номер детали / артикул");partInput.setSingleLine(true);partInput.setTextSize(16);partInput.setPadding(dp(14),0,dp(14),0);partInput.setBackground(round(Color.rgb(244,247,250),14));
        check.addView(wrap(partInput,0,10,0,0));
        LinearLayout row=new LinearLayout(this);row.setPadding(0,dp(8),0,0);
        Button paste=lightButton("ВСТАВИТЬ");paste.setOnClickListener(v->pasteClipboard());row.addView(paste,new LinearLayout.LayoutParams(0,dp(50),0.42f));
        Button search=blueButton("ПРОВЕРИТЬ НАЛИЧИЕ");search.setOnClickListener(v->searchStock());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(50),1);sp.setMargins(dp(8),0,0,0);row.addView(search,sp);
        check.addView(row);
        dbStatus=tv("Загрузка локальной базы…",12,MUTED,false);dbStatus.setPadding(0,dp(7),0,0);check.addView(dbStatus);
        body.addView(wrap(check,0,14,0,8));

        stockBox=col();body.addView(stockBox);

        LinearLayout future=card();
        future.addView(tv("Следующий этап — полностью автоматический подбор",17,DARK,true));
        TextView ft=tv("Когда подключим Work Truck к вашему серверу Multi и CRM, этот же экран сможет отправлять chassis на сервер, получать применимые номера Scania и сразу показывать наши цены и остатки без ручного перехода между каталогом и наличием.",12,MUTED,false);ft.setPadding(0,dp(4),0,0);future.addView(ft);
        body.addView(wrap(future,0,10,0,0));

        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void openDatabase(){
        new Thread(()->{
            try{
                database=new Database(this);database.open();dbReady=true;
                runOnUiThread(()->dbStatus.setText("База Work Truck готова к поиску."));
            }catch(Exception e){
                runOnUiThread(()->dbStatus.setText("Не удалось открыть локальную базу. Перезапустите приложение."));
            }
        }).start();
    }

    private void openMulti(){
        if(web==null){
            web=new WebView(this);
            WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);s.setUserAgentString(s.getUserAgentString()+" WorkTruckApp/6.5");
            web.setWebViewClient(new WebViewClient(){
                @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap favicon){webStatus.setText("Загрузка Scania Parts Online…");}
                @Override public void onPageFinished(WebView v,String url){webStatus.setText("Scania Parts Online открыт. Авторизуйтесь и перейдите в Scania Multi Parts Web.");}
                @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError err){if(req.isForMainFrame())webStatus.setText("Не удалось открыть портал. Проверьте интернет или откройте его во внешнем браузере.");}
            });
            webHolder.addView(web,new LinearLayout.LayoutParams(-1,dp(540)));
        }
        web.loadUrl(SCANIA_PARTS_ONLINE);
    }

    private void openExternal(){
        try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(SCANIA_PARTS_ONLINE)));}catch(Exception e){Toast.makeText(this,"Не удалось открыть браузер",Toast.LENGTH_SHORT).show();}
    }

    private void pasteClipboard(){
        try{
            ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(cm!=null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount()>0){
                CharSequence x=cm.getPrimaryClip().getItemAt(0).coerceToText(this);if(x!=null)partInput.setText(x.toString().trim());
            }
        }catch(Exception ignored){}
    }

    private void searchStock(){
        final String q=partInput.getText().toString().trim();
        stockBox.removeAllViews();
        if(q.isEmpty()){partInput.setError("Введите номер детали");return;}
        if(!dbReady||database==null){dbStatus.setText("База ещё загружается. Попробуйте через несколько секунд.");return;}
        dbStatus.setText("Поиск "+q+"…");
        new Thread(()->{
            final List<Product> list=database.search(q,null);
            runOnUiThread(()->renderStock(q,list));
        }).start();
    }

    private void renderStock(String q,List<Product> list){
        stockBox.removeAllViews();
        if(list==null||list.isEmpty()){
            dbStatus.setText("По номеру "+q+" в текущих остатках ничего не найдено.");
            LinearLayout c=card();c.addView(tv("Нет в наличии",17,DARK,true));c.addView(tv("Создайте заявку — менеджер проверит замену, восстановленную или БУ деталь.",13,MUTED,false));stockBox.addView(c);return;
        }
        dbStatus.setText("Найдено позиций: "+list.size());
        int max=Math.min(list.size(),30);
        for(int i=0;i<max;i++){
            Product p=list.get(i);
            LinearLayout c=card();
            c.addView(tv(p.name,16,DARK,true));
            String nums="Артикул: "+safe(p.article)+(p.manufacturerNo==null||p.manufacturerNo.isEmpty()?"":"   OEM: "+p.manufacturerNo);
            TextView n=tv(nums,12,BLUE,true);n.setPadding(0,dp(4),0,0);c.addView(n);
            String line="Склад: "+safe(p.warehouse)+"   •   Остаток: "+qty(p.qty)+(p.price>0?"   •   "+money.format(p.price)+" ₽":"");
            c.addView(tv(line,12,MUTED,false));
            stockBox.addView(wrap(c,0,0,0,8));
        }
        if(list.size()>max)stockBox.addView(tv("Показаны первые "+max+" позиций. Уточните номер для более точного поиска.",12,MUTED,false));
    }

    private String safe(String s){return s==null||s.trim().isEmpty()?"—":s;}
    private String qty(double q){return q==Math.rint(q)?String.valueOf((long)q):String.valueOf(q);}

    @Override public void onBackPressed(){
        if(web!=null&&web.canGoBack()){web.goBack();return;}super.onBackPressed();
    }
    @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}

    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){LinearLayout c=col();c.setPadding(dp(14),dp(13),dp(14),dp(13));c.setBackground(round(Color.WHITE,17));return c;}
    private View wrap(View v,int l,int t,int r,int b){LinearLayout x=col();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));x.addView(v,p);return x;}
    private Button blueButton(String s){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setBackground(round(BLUE,14));return b;}
    private Button lightButton(String s){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setTextColor(DARK);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setBackground(round(Color.rgb(232,241,253),14));return b;}
    private TextView tv(String x,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(x);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(int color,int r){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(r));g.setStroke(dp(1),Color.rgb(222,230,240));return g;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
