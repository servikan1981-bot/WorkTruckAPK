package com.worktruck.catalog;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public class TourActivity extends Activity {
    private static final int DARK=Color.rgb(7,38,29), GREEN=Color.rgb(8,91,58);
    private PanoramaView panorama;
    private TextView sceneTitle, sceneSub, sceneCounter;
    private Button prev,next;
    private int scene=0;
    private final int[] images={R.drawable.tour_01,R.drawable.tour_02,R.drawable.tour_03,R.drawable.tour_04};
    private final String[] titles={
        "Въезд и офис",
        "Центральная площадка",
        "Автомобили в разбор",
        "Площадка и складская зона"
    };
    private final String[] subs={
        "Комната ожидания, офис Work Truck и ряд автомобилей",
        "Основная территория, шасси, кабины и рабочие зоны",
        "Кабины Scania, разбор и складирование узлов",
        "Общий обзор площадки, офис и зоны хранения"
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        enterImmersive();

        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        panorama=new PanoramaView();
        root.addView(panorama,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8),dp(6),dp(12),dp(6));
        top.setBackground(round(Color.argb(205,7,38,29),16));

        TextView back=tv("‹",34,Color.WHITE,true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->finish());
        top.addView(back,new LinearLayout.LayoutParams(dp(42),dp(46)));

        LinearLayout tb=new LinearLayout(this);
        tb.setOrientation(LinearLayout.VERTICAL);
        sceneCounter=tv("",10,Color.rgb(159,210,186),true);
        sceneTitle=tv("",18,Color.WHITE,true);
        sceneSub=tv("",11,Color.rgb(210,228,220),false);
        tb.addView(sceneCounter);
        tb.addView(sceneTitle);
        tb.addView(sceneSub);
        top.addView(tb,new LinearLayout.LayoutParams(0,-2,1));
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        tp.setMargins(dp(10),dp(8),dp(10),0);
        root.addView(top,tp);

        LinearLayout nav=new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8),dp(6),dp(8),dp(6));
        nav.setBackground(round(Color.argb(220,7,38,29),18));
        prev=button("← НАЗАД");
        next=button("ВПЕРЁД →");
        prev.setOnClickListener(v->go(scene-1));
        next.setOnClickListener(v->go(scene+1));
        nav.addView(prev,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView dot=tv("●",18,Color.WHITE,true);
        dot.setGravity(Gravity.CENTER);
        nav.addView(dot,new LinearLayout.LayoutParams(dp(54),dp(48)));
        nav.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));
        FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(dp(470),-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        np.setMargins(0,0,0,dp(10));
        root.addView(nav,np);

        TextView help=tv("Тяните пальцем для обзора · двумя пальцами — масштаб",10,Color.WHITE,false);
        help.setGravity(Gravity.CENTER);
        help.setPadding(dp(12),dp(6),dp(12),dp(6));
        help.setBackground(round(Color.argb(165,0,0,0),12));
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        hp.setMargins(0,0,0,dp(73));
        root.addView(help,hp);

        setContentView(root);
        go(0);
    }

    private void enterImmersive(){
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus)enterImmersive();
    }

    private void go(int n){
        if(n<0||n>=titles.length)return;
        scene=n;
        panorama.setScene(n);
        sceneCounter.setText("ТОЧКА "+(n+1)+" ИЗ "+titles.length);
        sceneTitle.setText(titles[n]);
        sceneSub.setText(subs[n]);
        prev.setEnabled(n>0);
        next.setEnabled(n<titles.length-1);
        prev.setAlpha(n>0?1f:.35f);
        next.setAlpha(n<titles.length-1?1f:.35f);
    }

    private class PanoramaView extends View{
        private Bitmap world;
        private float pan=0.5f,zoom=1f,lastX;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
        private final ScaleGestureDetector scale;

        PanoramaView(){
            super(TourActivity.this);
            setBackgroundColor(Color.BLACK);
            scale=new ScaleGestureDetector(TourActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector d){
                    zoom=Math.max(1f,Math.min(3.2f,zoom*d.getScaleFactor()));
                    clampPan();
                    invalidate();
                    return true;
                }
            });
        }

        void setScene(int s){
            if(world!=null&&!world.isRecycled())world.recycle();
            BitmapFactory.Options o=new BitmapFactory.Options();
            o.inPreferredConfig=Bitmap.Config.ARGB_8888;
            o.inScaled=false;
            o.inDither=true;
            world=BitmapFactory.decodeResource(getResources(),images[s],o);
            pan=0.5f;
            zoom=1f;
            invalidate();
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            if(world==null)return;
            int vw=getWidth(),vh=getHeight();
            if(vw<=0||vh<=0)return;

            float viewRatio=(float)vw/vh;
            float cropH=world.getHeight()/zoom;
            float cropW=cropH*viewRatio;
            if(cropW>world.getWidth()){
                cropW=world.getWidth();
                cropH=cropW/viewRatio;
            }
            cropW=Math.max(1,Math.min(cropW,world.getWidth()));
            cropH=Math.max(1,Math.min(cropH,world.getHeight()));

            float maxLeft=Math.max(0,world.getWidth()-cropW);
            float left=maxLeft*pan;
            float top=Math.max(0,(world.getHeight()-cropH)/2f);
            RectF src=new RectF(left,top,left+cropW,top+cropH);
            RectF dst=new RectF(0,0,vw,vh);
            c.drawBitmap(world,src,dst,paint);
        }

        private void clampPan(){pan=Math.max(0f,Math.min(1f,pan));}

        @Override public boolean onTouchEvent(MotionEvent e){
            scale.onTouchEvent(e);
            if(e.getPointerCount()==1){
                if(e.getAction()==MotionEvent.ACTION_DOWN){lastX=e.getX();return true;}
                if(e.getAction()==MotionEvent.ACTION_MOVE){
                    float dx=e.getX()-lastX;
                    pan-=dx/Math.max(1,getWidth())*.72f/zoom;
                    clampPan();
                    lastX=e.getX();
                    invalidate();
                    return true;
                }
            }
            return true;
        }
    }

    @Override protected void onDestroy(){
        super.onDestroy();
        if(panorama!=null&&panorama.world!=null&&!panorama.world.isRecycled())panorama.world.recycle();
    }

    private TextView tv(String x,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(x);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private Button button(String x){
        Button b=new Button(this);b.setText(x);b.setTextColor(Color.WHITE);b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(round(GREEN,14));return b;
    }
    private GradientDrawable round(int color,int r){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(r));return g;
    }
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
