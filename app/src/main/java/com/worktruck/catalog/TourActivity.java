package com.worktruck.catalog;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

public class TourActivity extends Activity {
    private static final int DARK = Color.rgb(7,38,29);
    private static final int GREEN = Color.rgb(8,91,58);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(Color.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(7), dp(12), dp(7));
        top.setBackgroundColor(DARK);

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(34);
        back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(48)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Экскурсия по Work Truck");
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        titleBox.addView(title);
        TextView demo = new TextView(this);
        demo.setText("ДЕМО · 360° · двигайте изображение пальцем");
        demo.setTextSize(11);
        demo.setTextColor(Color.rgb(190,211,202));
        titleBox.addView(demo);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(62)));

        WebView web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.setBackgroundColor(Color.BLACK);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>" +
                "<link rel='stylesheet' href='https://cdn.jsdelivr.net/npm/pannellum@2.5.6/build/pannellum.css'>" +
                "<script src='https://cdn.jsdelivr.net/npm/pannellum@2.5.6/build/pannellum.js'></script>" +
                "<style>html,body,#pan{width:100%;height:100%;margin:0;background:#071f18;overflow:hidden}.badge{position:fixed;left:12px;bottom:14px;z-index:9;background:rgba(7,38,29,.88);color:#fff;padding:9px 12px;border-radius:12px;font:13px sans-serif}.badge b{color:#87d6b5}</style>" +
                "</head><body><div id='pan'></div><div class='badge'><b>ДЕМО-МАРШРУТ</b><br>Нажимайте стрелки для перехода между зонами</div>" +
                "<script>pannellum.viewer('pan',{default:{firstScene:'garage',sceneFadeDuration:700,autoLoad:true,showControls:true},scenes:{" +
                "garage:{title:'Точка 1 · Гараж / приёмка',type:'equirectangular',panorama:'https://commons.wikimedia.org/wiki/Special:Redirect/file/Garage_%E2%80%93_Panorama_%28Greg_Zaal_via_Poly_Haven%29.jpg?width=3840',hotSpots:[{pitch:-8,yaw:38,type:'scene',text:'Перейти в мастерскую',sceneId:'workshop'}]}," +
                "workshop:{title:'Точка 2 · Мастерская',type:'equirectangular',panorama:'https://commons.wikimedia.org/wiki/Special:Redirect/file/Small_workshop_-_Panorama_%28Poly_haven%29.jpg?width=3840',hotSpots:[{pitch:-7,yaw:-32,type:'scene',text:'Назад в гараж',sceneId:'garage'},{pitch:-6,yaw:44,type:'scene',text:'Перейти в большую ремзону',sceneId:'industrial'}]}," +
                "industrial:{title:'Точка 3 · Большая ремзона',type:'equirectangular',panorama:'https://commons.wikimedia.org/wiki/Special:Redirect/file/Aircraft_workshop_01_%E2%80%93_Panorama_%28Oliksiy_Yakovlyev_via_Poly_Haven%29.jpg?width=3840',hotSpots:[{pitch:-8,yaw:-40,type:'scene',text:'Вернуться в мастерскую',sceneId:'workshop'}]}" +
                "}});</script></body></html>";

        web.loadDataWithBaseURL("https://cdn.jsdelivr.net/", html, "text/html", "UTF-8", null);
    }

    private int dp(int x) {
        return (int)(x * getResources().getDisplayMetrics().density + .5f);
    }
}
