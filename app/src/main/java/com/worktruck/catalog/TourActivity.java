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
    private TextView sceneTitle, sceneSub, sceneCounter;
    private Button prev,next;
    private int scene=0;
    private final int[] images={R.drawable.tour_01,R.drawable.tour_02,R.drawable.tour_03,R.drawable.tour_04};
    private final String[] titles={
        "Точка 1 · Въезд и офис",
        "Точка 2 · Центральная площадка",
        "Точка 3 · Зона автомобилей в разбор",
        "Точка 4 · Площадка и складская зона"
    };
    private final String[] subs={
        "Комната ожидания, офис Work Truck и ряд автомобилей",
        "Основная территория, шасси, кабины и рабочие зоны",
        "Кабины Scania, разбор и складирование узлов",
        "Общий обзор площадки, офис и зоны хранения"
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(Color.BLACK);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10),dp(7),dp(12),dp(7));
        top.setBackgroundColor(DARK);

        TextView back=tv("‹",34,Color.WHITE,true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->finish());
        top.addView(back,new LinearLayout.LayoutParams(dp(42),dp(50)));

        LinearLayout tb=new LinearLayout(this);
        tb.setOrientation(LinearLayout.VERTICAL);
        tb.addView(tv("Экскурсия по Work Truck",20,Color.WHITE,true));
        tb.addView(tv("4 реальные панорамы территории · работает без интернета",11,Color.rgb(190,211,202),false));
        top.addView(tb,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(66)));

        FrameLayout stage=new FrameLayout(this);
        stage.setBackgroundColor(BG);
        panorama=new PanoramaView();
        stage.addView(panorama,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout hint=new LinearLayout(this);
        hint.setOrientation(LinearLayout.VERTICAL);
        hint.setPadding(dp(14),dp(10),dp(14),dp(10));
        hint.setBackground(round(Color.argb(220,7,38,29),16));
        sceneCounter=tv("",11,Color.rgb(159,210,186),true);
        sceneTitle=tv("",17,Color.WHITE,true);
        sceneSub=tv("",12,Color.rgb(205,226,216),false);
        hint.addView(sceneCounter);
        hint.addView(sceneTitle);
        hint.addView(sceneSub);
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        hp.setMargins(dp(12),dp(12),dp(12),0);
        stage.addView(hint,hp);

        TextView gesture=tv("↔ тяните пальцем — панорама   •   двумя пальцами — масштаб",11,Color.WHITE,false);
        gesture.setGravity(Gravity.CENTER);
        gesture.setPadding(dp(8),dp(7),dp(8),dp(7));
        gesture.setBackground(round(Color.argb(185,0,0,0),13));
        FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        gp.setMargins(0,0,0,dp(94));
        stage.addView(gesture,gp);

        LinearLayout nav=new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10),dp(8),dp(10),dp(10));
        nav.setBackground(round(Color.argb(240,7,38,29),18));
        prev=button("← НАЗАД");
        next=button("ВПЕРЁД →");
        prev.setOnClickListener(v->go(scene-1));
        next.setOnClickListener(v->go(scene+1));
        nav.addView(prev,new LinearLayout.LayoutParams(0,dp(56),1));
        TextView walk=tv("●",22,Color.WHITE,true);
        walk.setGravity(Gravity.CENTER);
        nav.addView(walk,new LinearLayout.LayoutParams(dp(66),dp(56)));
        nav.addView(next,new LinearLayout.LayoutParams(0,dp(56),1));
        FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        np.setMargins(dp(10),0,dp(10),dp(12));
        stage.addView(nav,np);

        root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        go(0);
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
        private float pan=0f,zoom=1f,lastX,lastY;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
        private final ScaleGestureDetector scale;

        PanoramaView(){
            super(TourActivity.this);
            setBackgroundColor(Color.BLACK);
            scale=new ScaleGestureDetector(TourActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector d){
                    zoom=Math.max(1f,Math.min(2.8f,zoom*d.getScaleFactor()));
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

            float imageRatio=(float)world.getWidth()/world.getHeight();
            float viewRatio=(float)vw/vh;
            float cropW;
            float cropH;

            if(imageRatio>viewRatio){
                cropH=world.getHeight()/zoom;
                cropW=cropH*viewRatio;
            }else{
                cropW=world.getWidth()/zoom;
                cropH=cropW/viewRatio;
            }

            cropW=Math.min(cropW,world.getWidth());
            cropH=Math.min(cropH,world.getHeight());

            float maxLeft=Math.max(0,world.getWidth()-cropW);
            float left=maxLeft*pan;
            float top=Math.max(0,(world.getHeight()-cropH)/2f);

            Rect src=new Rect((int)left,(int)top,(int)(left+cropW),(int)(top+cropH));
            RectF dst=new RectF(0,0,vw,vh);
            c.drawBitmap(world,src,dst,paint);
        }

        private void clampPan(){pan=Math.max(0f,Math.min(1f,pan));}

        @Override public boolean onTouchEvent(MotionEvent e){
            scale.onTouchEvent(e);
            if(e.getPointerCount()==1){
                if(e.getAction()==MotionEvent.ACTION_DOWN){
                    lastX=e.getX();lastY=e.getY();return true;
                }
                if(e.getAction()==MotionEvent.ACTION_MOVE){
                    float dx=e.getX()-lastX;
                    pan-=dx/Math.max(1,getWidth())*.9f/zoom;
                    clampPan();
                    lastX=e.getX();lastY=e.getY();
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
        Button b=new Button(this);b.setText(x);b.setTextColor(Color.WHITE);b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(round(GREEN,14));return b;
    }
    private GradientDrawable round(int color,int r){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(r));return g;
    }
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
