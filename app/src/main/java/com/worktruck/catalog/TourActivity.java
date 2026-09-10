package com.worktruck.catalog;

import android.app.Activity;
import android.content.pm.ActivityInfo;
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
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        enterImmersive();

        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        panorama=new PanoramaView();
        root.addView(panorama,new FrameLayout.LayoutParams(-1,-1));

        // Compact, more transparent title panel so the panorama remains visible.
        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(7),dp(5),dp(10),dp(5));
        top.setBackground(round(Color.argb(118,7,38,29),15));

        TextView back=tv("‹",32,Color.WHITE,true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->finish());
        top.addView(back,new LinearLayout.LayoutParams(dp(38),dp(44)));

        LinearLayout tb=new LinearLayout(this);
        tb.setOrientation(LinearLayout.VERTICAL);
        sceneCounter=tv("",9,Color.rgb(180,226,205),true);
        sceneTitle=tv("",17,Color.WHITE,true);
        sceneSub=tv("",10,Color.rgb(231,239,235),false);
        sceneTitle.setShadowLayer(4,0,1,Color.BLACK);
        sceneSub.setShadowLayer(3,0,1,Color.BLACK);
        tb.addView(sceneCounter);
        tb.addView(sceneTitle);
        tb.addView(sceneSub);
        top.addView(tb,new LinearLayout.LayoutParams(0,-2,1));

        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        tp.setMargins(dp(8),dp(7),dp(8),0);
        root.addView(top,tp);

        // Four-way panorama movement pad.
        GridLayout pad=new GridLayout(this);
        pad.setColumnCount(3);
        pad.setRowCount(3);
        pad.setPadding(dp(5),dp(5),dp(5),dp(5));
        pad.setBackground(round(Color.argb(105,0,0,0),18));

        addPadCell(pad,"",null);
        addPadCell(pad,"↑",()->panorama.nudge(0f,-0.13f));
        addPadCell(pad,"",null);
        addPadCell(pad,"←",()->panorama.nudge(-0.12f,0f));
        addPadCell(pad,"●",()->panorama.resetView());
        addPadCell(pad,"→",()->panorama.nudge(0.12f,0f));
        addPadCell(pad,"",null);
        addPadCell(pad,"↓",()->panorama.nudge(0f,0.13f));
        addPadCell(pad,"",null);

        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(174),dp(174),Gravity.RIGHT|Gravity.BOTTOM);
        pp.setMargins(0,0,dp(12),dp(86));
        root.addView(pad,pp);

        // Previous/next panorama points remain available separately from panning.
        LinearLayout nav=new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(6),dp(5),dp(6),dp(5));
        nav.setBackground(round(Color.argb(125,7,38,29),17));
        prev=button("← ТОЧКА");
        next=button("ТОЧКА →");
        prev.setOnClickListener(v->go(scene-1));
        next.setOnClickListener(v->go(scene+1));
        nav.addView(prev,new LinearLayout.LayoutParams(0,dp(45),1));
        TextView dot=tv("●",14,Color.WHITE,true);
        dot.setGravity(Gravity.CENTER);
        nav.addView(dot,new LinearLayout.LayoutParams(dp(42),dp(45)));
        nav.addView(next,new LinearLayout.LayoutParams(0,dp(45),1));
        FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        np.setMargins(dp(10),0,dp(10),dp(12));
        root.addView(nav,np);

        TextView help=tv("Свайп — обзор  •  двумя пальцами — масштаб",10,Color.WHITE,false);
        help.setGravity(Gravity.CENTER);
        help.setPadding(dp(10),dp(5),dp(10),dp(5));
        help.setBackground(round(Color.argb(100,0,0,0),11));
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,-2,Gravity.LEFT|Gravity.BOTTOM);
        hp.setMargins(dp(12),0,0,dp(89));
        root.addView(help,hp);

        setContentView(root);
        go(0);
    }

    private void addPadCell(GridLayout pad,String text,Runnable action){
        TextView b=tv(text,27,Color.WHITE,true);
        b.setGravity(Gravity.CENTER);
        if(action!=null){
            b.setBackground(round(Color.argb(175,8,91,58),16));
            b.setOnClickListener(v->action.run());
        }
        GridLayout.LayoutParams p=new GridLayout.LayoutParams();
        p.width=dp(50);p.height=dp(50);
        p.setMargins(dp(2),dp(2),dp(2),dp(2));
        pad.addView(b,p);
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
        prev.setAlpha(n>0?1f:.32f);
        next.setAlpha(n<titles.length-1?1f:.32f);
    }

    private class PanoramaView extends View{
        private Bitmap world;
        private float panX=0.5f,panY=0.5f,zoom=1.35f,lastX,lastY;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
        private final ScaleGestureDetector scale;

        PanoramaView(){
            super(TourActivity.this);
            setBackgroundColor(Color.BLACK);
            scale=new ScaleGestureDetector(TourActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector d){
                    zoom=Math.max(1.15f,Math.min(4.2f,zoom*d.getScaleFactor()));
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
            resetView();
        }

        void resetView(){
            panX=0.5f;
            panY=0.5f;
            zoom=1.35f;
            invalidate();
        }

        void nudge(float dx,float dy){
            panX+=dx/Math.max(1f,zoom*.8f);
            panY+=dy/Math.max(1f,zoom*.8f);
            clampPan();
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
            float maxTop=Math.max(0,world.getHeight()-cropH);
            float left=maxLeft*panX;
            float top=maxTop*panY;

            Rect src=new Rect((int)left,(int)top,(int)(left+cropW),(int)(top+cropH));
            RectF dst=new RectF(0,0,vw,vh);
            c.drawBitmap(world,src,dst,paint);
        }

        private void clampPan(){
            panX=Math.max(0f,Math.min(1f,panX));
            panY=Math.max(0f,Math.min(1f,panY));
        }

        @Override public boolean onTouchEvent(MotionEvent e){
            scale.onTouchEvent(e);
            if(e.getPointerCount()==1){
                if(e.getAction()==MotionEvent.ACTION_DOWN){
                    lastX=e.getX();
                    lastY=e.getY();
                    return true;
                }
                if(e.getAction()==MotionEvent.ACTION_MOVE){
                    float dx=e.getX()-lastX;
                    float dy=e.getY()-lastY;
                    panX-=dx/Math.max(1,getWidth())*.72f/zoom;
                    panY-=dy/Math.max(1,getHeight())*.72f/zoom;
                    clampPan();
                    lastX=e.getX();
                    lastY=e.getY();
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
        TextView t=new TextView(this);
        t.setText(x);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }

    private Button button(String x){
        Button b=new Button(this);
        b.setText(x);b.setTextColor(Color.WHITE);b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(round(Color.argb(205,8,91,58),14));
        return b;
    }

    private GradientDrawable round(int color,int r){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);g.setCornerRadius(dp(r));
        return g;
    }

    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
