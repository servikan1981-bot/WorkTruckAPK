package com.worktruck.catalog;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public class TourActivity extends Activity {
    private static final int DARK=Color.rgb(7,38,29), GREEN=Color.rgb(8,91,58), BG=Color.rgb(5,29,22);
    private PanoramaView panorama;
    private TextView sceneTitle;
    private TextView sceneSub;
    private Button prev, next;
    private int scene=0;
    private final String[] titles={"Точка 1 · Приёмка","Точка 2 · Склад запчастей","Точка 3 · Ремонтная зона"};
    private final String[] subs={"Осмотритесь и перейдите дальше","Стеллажи и зона хранения","Сервисная зона и рабочие посты"};

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(Color.BLACK);

        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(10),dp(7),dp(12),dp(7));top.setBackgroundColor(DARK);
        TextView back=tv("‹",34,Color.WHITE,true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(42),dp(50)));
        LinearLayout tb=new LinearLayout(this);tb.setOrientation(LinearLayout.VERTICAL);
        tb.addView(tv("Экскурсия по Work Truck",20,Color.WHITE,true));tb.addView(tv("ДЕМО · работает без интернета · двигайте пальцем",11,Color.rgb(190,211,202),false));
        top.addView(tb,new LinearLayout.LayoutParams(0,-2,1));root.addView(top,new LinearLayout.LayoutParams(-1,dp(66)));

        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(BG);
        panorama=new PanoramaView();stage.addView(panorama,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout hint=new LinearLayout(this);hint.setOrientation(LinearLayout.VERTICAL);hint.setPadding(dp(14),dp(10),dp(14),dp(10));hint.setBackground(round(Color.argb(220,7,38,29),16));
        sceneTitle=tv("",17,Color.WHITE,true);sceneSub=tv("",12,Color.rgb(197,222,211),false);hint.addView(sceneTitle);hint.addView(sceneSub);
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);hp.setMargins(dp(12),dp(12),dp(12),0);stage.addView(hint,hp);

        TextView gesture=tv("↔  тяните пальцем — обзор 360°    •    двумя пальцами — масштаб",11,Color.WHITE,false);gesture.setGravity(Gravity.CENTER);gesture.setPadding(dp(8),dp(7),dp(8),dp(7));gesture.setBackground(round(Color.argb(170,0,0,0),13));
        FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);gp.setMargins(0,0,0,dp(94));stage.addView(gesture,gp);

        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(dp(10),dp(8),dp(10),dp(10));nav.setBackground(round(Color.argb(235,7,38,29),18));
        prev=button("← НАЗАД");next=button("ВПЕРЁД →");
        prev.setOnClickListener(v->go(scene-1));next.setOnClickListener(v->go(scene+1));
        nav.addView(prev,new LinearLayout.LayoutParams(0,dp(56),1));
        TextView walk=tv("  ↑  ",26,Color.WHITE,true);walk.setGravity(Gravity.CENTER);nav.addView(walk,new LinearLayout.LayoutParams(dp(66),dp(56)));
        nav.addView(next,new LinearLayout.LayoutParams(0,dp(56),1));
        FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);np.setMargins(dp(10),0,dp(10),dp(12));stage.addView(nav,np);

        root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);go(0);
    }

    private void go(int n){
        if(n<0||n>=titles.length)return;scene=n;panorama.setScene(n);sceneTitle.setText(titles[n]);sceneSub.setText(subs[n]);prev.setEnabled(n>0);next.setEnabled(n<titles.length-1);prev.setAlpha(n>0?1f:.35f);next.setAlpha(n<titles.length-1?1f:.35f);
    }

    private class PanoramaView extends View {
        private Bitmap world;
        private float yaw=0f,pitch=0f,zoom=1f,lastX,lastY;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final ScaleGestureDetector scale;

        PanoramaView(){super(TourActivity.this);setBackgroundColor(BG);scale=new ScaleGestureDetector(TourActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){zoom*=d.getScaleFactor();zoom=Math.max(1f,Math.min(2.25f,zoom));invalidate();return true;}});}
        void setScene(int s){if(world!=null&&!world.isRecycled())world.recycle();world=buildWorld(s);yaw=s==0?8f:(s==1?205f:330f);pitch=0;zoom=1f;invalidate();}

        @Override protected void onDraw(Canvas c){super.onDraw(c);if(world==null)return;int vw=getWidth(),vh=getHeight();if(vw<=0||vh<=0)return;float srcW=world.getWidth()/zoom;float srcH=Math.min(world.getHeight(),srcW*((float)vh/vw));float cx=(yaw%360+360)%360/360f*world.getWidth();float cy=world.getHeight()/2f + pitch/70f*world.getHeight()/3f;float left=cx-srcW/2f,top=cy-srcH/2f;top=Math.max(0,Math.min(world.getHeight()-srcH,top));drawWrapped(c,left,top,srcW,srcH,new RectF(0,0,vw,vh));}

        private void drawWrapped(Canvas c,float left,float top,float sw,float sh,RectF dst){int W=world.getWidth();while(left<0)left+=W;while(left>=W)left-=W;if(left+sw<=W){c.drawBitmap(world,new RectF(left,top,left+sw,top+sh),dst,p);}else{float first=W-left;float ratio=first/sw;RectF d1=new RectF(dst.left,dst.top,dst.left+dst.width()*ratio,dst.bottom);RectF d2=new RectF(d1.right,dst.top,dst.right,dst.bottom);c.drawBitmap(world,new RectF(left,top,W,top+sh),d1,p);c.drawBitmap(world,new RectF(0,top,sw-first,top+sh),d2,p);}}

        @Override public boolean onTouchEvent(android.view.MotionEvent e){scale.onTouchEvent(e);if(e.getPointerCount()==1){if(e.getAction()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){float dx=e.getX()-lastX,dy=e.getY()-lastY;yaw-=dx/Math.max(1,getWidth())*150f/zoom;pitch+=dy/Math.max(1,getHeight())*60f/zoom;pitch=Math.max(-28f,Math.min(28f,pitch));lastX=e.getX();lastY=e.getY();invalidate();return true;}}return true;}
    }

    private Bitmap buildWorld(int s){int W=3072,H=1024;Bitmap b=Bitmap.createBitmap(W,H,Bitmap.Config.RGB_565);Canvas c=new Canvas(b);Paint q=new Paint(Paint.ANTI_ALIAS_FLAG);
        q.setShader(new LinearGradient(0,0,0,H,Color.rgb(48,62,58),Color.rgb(13,25,21),Shader.TileMode.CLAMP));c.drawRect(0,0,W,H,q);q.setShader(null);
        q.setColor(Color.rgb(204,207,197));c.drawRect(0,0,W,H*0.28f,q);q.setColor(Color.rgb(92,99,94));c.drawRect(0,H*.28f,W,H*.66f,q);q.setColor(Color.rgb(62,65,60));c.drawRect(0,H*.66f,W,H,q);
        for(int x=0;x<W;x+=260){q.setColor(Color.rgb(40,48,45));c.drawRect(x,H*.28f,x+10,H*.66f,q);q.setColor(Color.rgb(235,222,170));c.drawRect(x+55,75,x+195,95,q);}
        if(s==0)drawReception(c,q,W,H);else if(s==1)drawWarehouse(c,q,W,H);else drawWorkshop(c,q,W,H);
        q.setColor(Color.argb(80,0,0,0));c.drawRect(0,H*.645f,W,H*.67f,q);return b;}

    private void drawReception(Canvas c,Paint q,int W,int H){q.setColor(Color.rgb(19,70,51));c.drawRect(300,310,900,650,q);q.setColor(Color.WHITE);q.setTextSize(58);q.setTypeface(Typeface.DEFAULT_BOLD);c.drawText("WORK TRUCK",395,455,q);q.setTextSize(28);c.drawText("ПРИЁМКА",490,510,q);q.setColor(Color.rgb(45,48,47));c.drawRect(1240,430,1880,660,q);q.setColor(Color.rgb(224,224,218));c.drawRect(1290,460,1830,620,q);q.setColor(Color.rgb(25,75,58));c.drawRect(2120,355,2690,660,q);q.setColor(Color.WHITE);q.setTextSize(34);c.drawText("ВХОД НА СКЛАД  →",2210,520,q);drawTruck(c,q,20,620,.82f);drawTruck(c,q,2700,620,.82f);}
    private void drawWarehouse(Canvas c,Paint q,int W,int H){for(int x=80;x<W;x+=520){q.setColor(Color.rgb(116,79,45));c.drawRect(x,340,x+390,680,q);q.setColor(Color.rgb(170,124,68));for(int y=385;y<650;y+=72)c.drawRect(x+18,y,x+372,y+15,q);for(int bx=x+40;bx<x+350;bx+=95){q.setColor(Color.rgb(56+(bx%80),83,71));c.drawRect(bx,400,bx+70,445,q);c.drawRect(bx,475,bx+70,525,q);c.drawRect(bx,550,bx+70,605,q);}}q.setColor(Color.rgb(18,75,54));c.drawRect(1220,300,1850,370,q);q.setColor(Color.WHITE);q.setTextSize(36);q.setTypeface(Typeface.DEFAULT_BOLD);c.drawText("СКЛАД ЗАПЧАСТЕЙ",1360,350,q);drawTruck(c,q,2420,650,.7f);}
    private void drawWorkshop(Canvas c,Paint q,int W,int H){for(int x=220;x<W;x+=780){q.setColor(Color.rgb(32,87,65));c.drawRect(x,310,x+540,670,q);q.setColor(Color.rgb(210,215,210));c.drawRect(x+35,350,x+505,625,q);q.setColor(Color.rgb(22,27,26));c.drawRect(x+130,480,x+410,620,q);q.setColor(Color.rgb(8,91,58));c.drawRect(x+55,390,x+485,425,q);}q.setColor(Color.WHITE);q.setTextSize(34);q.setTypeface(Typeface.DEFAULT_BOLD);c.drawText("РЕМОНТНАЯ ЗОНА",1280,350,q);drawTruck(c,q,1050,690,1.05f);q.setColor(Color.rgb(210,181,55));c.drawRect(1880,690,2050,715,q);c.drawRect(2170,690,2340,715,q);}
    private void drawTruck(Canvas c,Paint q,float x,float y,float k){q.setColor(Color.rgb(210,210,205));c.drawRect(x,y-210*k,x+380*k,y,q);q.setColor(Color.rgb(27,76,58));c.drawRect(x+25*k,y-185*k,x+155*k,y-35*k,q);q.setColor(Color.rgb(43,61,66));c.drawRect(x+48*k,y-165*k,x+135*k,y-95*k,q);q.setColor(Color.rgb(32,32,32));c.drawCircle(x+90*k,y,42*k,q);c.drawCircle(x+300*k,y,42*k,q);q.setColor(Color.rgb(8,91,58));c.drawRect(x+160*k,y-180*k,x+360*k,y-145*k,q);}

    private TextView tv(String x,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(x);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Button button(String x){Button b=new Button(this);b.setText(x);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(round(GREEN,14));return b;}
    private GradientDrawable round(int color,int r){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(r));return g;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
