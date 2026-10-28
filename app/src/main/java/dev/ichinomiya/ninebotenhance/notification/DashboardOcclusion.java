package dev.ichinomiya.ninebotenhance.notification;

import android.graphics.*;

/**
 * Preview-only mock of the instrument card the vehicle dashboard paints over the top right of the received frame
 * (gear, speed unit, power, range and battery). Drawn above the HUD in the local simulation so the phone shows what
 * the rider will not see; never part of the frames sent to the vehicle. The rectangle was read off a dashboard photo
 * of the 848 x 480 calibration grid: the card covers roughly x 640-842 and y 44-254.
 */
public final class DashboardOcclusion {
    public static final RectF INSTRUMENT=new RectF(dev.ichinomiya.ninebotenhance.core.SidebarLayout.INSTRUMENT.left(),dev.ichinomiya.ninebotenhance.core.SidebarLayout.INSTRUMENT.top(),dev.ichinomiya.ninebotenhance.core.SidebarLayout.INSTRUMENT.right(),dev.ichinomiya.ninebotenhance.core.SidebarLayout.INSTRUMENT.bottom());
    private static final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private DashboardOcclusion(){}
    /** Same fit as the HUD: the 848 x 480 reference frame scaled into the given size, anchored bottom right. */
    public static void draw(Canvas canvas,int width,int height){draw(canvas,width,height,false);}
    /** With hillHold the dashboard's own "拧动油门解除坡道驻车" toast is mocked at the lower right as well. */
    public static void draw(Canvas canvas,int width,int height,boolean hillHold){draw(canvas,width,height,hillHold,dev.ichinomiya.ninebotenhance.core.SidebarLayout.DEFAULT_OCCLUSIONS);}
    /** Every dashboard occlusion (reference frame) is mocked as a card; the first one carries the instrument mock-up. */
    public static synchronized void draw(Canvas canvas,int width,int height,boolean hillHold,java.util.List<dev.ichinomiya.ninebotenhance.core.SidebarLayout.Box> occlusions){
        if(width<=0||height<=0)return;int save=canvas.save();
        try{
            canvas.clipRect(0,0,width,height);dev.ichinomiya.ninebotenhance.core.SidebarLayout.Fit fit=dev.ichinomiya.ninebotenhance.core.SidebarLayout.fit(width,height);canvas.translate(fit.dx(),fit.dy());canvas.scale(fit.scale(),fit.scale());
            boolean first=true;
            for(dev.ichinomiya.ninebotenhance.core.SidebarLayout.Box b:occlusions==null?dev.ichinomiya.ninebotenhance.core.SidebarLayout.DEFAULT_OCCLUSIONS:occlusions){
                RectF r=new RectF(b.left(),b.top(),b.right(),b.bottom());
                if(first&&r.width()>=150&&r.height()>=200)instrument(canvas,r);else card(canvas,r);
                first=false;
            }
            if(hillHold)hillHoldToast(canvas);
        }finally{canvas.restoreToCount(save);}
    }
    private static void card(Canvas c,RectF r){
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xf01d2124);c.drawRoundRect(r,16,16,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(0x55ffffff);c.drawRoundRect(r,16,16,paint);paint.setStyle(Paint.Style.FILL);
    }
    private static void hillHoldToast(Canvas c){
        dev.ichinomiya.ninebotenhance.core.SidebarLayout.Box b=dev.ichinomiya.ninebotenhance.core.SidebarLayout.HILL_HOLD_TOAST;
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xf01d2124);c.drawRoundRect(b.left(),b.top(),b.right()+16,b.bottom(),14,14,paint);
        paint.setColor(0xff2f7de1);c.drawRoundRect(b.left()+22,b.top()+27,b.left()+56,b.top()+63,6,6,paint);
        font(26,true);centered(c,"H",b.left()+39,b.top()+55,0xfff2f4f5);
        font(17,false);left(c,"拧动油门解除坡道驻车",b.left()+70,b.top()+52,0xfff2f4f5);
    }
    private static void instrument(Canvas c,RectF r){
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xf01d2124);c.drawRoundRect(r,16,16,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(0x55ffffff);c.drawRoundRect(r,16,16,paint);paint.setStyle(Paint.Style.FILL);
        font(86,true);centered(c,"P",r.centerX(),r.top+82,0xfff2f4f5);
        font(20,true);centered(c,"C",r.left+44,r.top+122,0xff3f8fe8);
        font(17,false);centered(c,"km/h",r.right-48,r.top+122,0xff8b949b);
        paint.setColor(0x33ffffff);c.drawRect(r.left+12,r.top+131,r.right-12,r.top+132,paint);
        font(22,true);left(c,"0.0",r.left+16,r.top+158,0xfff2f4f5);font(12,false);left(c,"kW",r.left+54,r.top+158,0xff8b949b);
        font(22,true);left(c,"94",r.left+118,r.top+158,0xfff2f4f5);font(12,false);left(c,"km",r.left+148,r.top+158,0xff8b949b);
        paint.setColor(0xff3b4148);c.drawRoundRect(r.left+16,r.top+168,r.left+92,r.top+171,2,2,paint);
        paint.setColor(0xffe2e6e9);c.drawRoundRect(r.left+52,r.top+164,r.left+55,r.top+175,1,1,paint);
        paint.setColor(0xff8a7cf0);c.drawRoundRect(r.left+16,r.top+179,r.left+92,r.top+183,2,2,paint);
        font(11,true);centered(c,"NOS",r.left+54,r.top+199,0xffd7dce0);
        // Do not paint the dashboard's green battery block over the navigation preview.
        font(17,true);centered(c,"86%",r.left+150,r.top+187,0xfff2f4f5);
    }
    private static void font(float size,boolean bold){text.setTextSize(size);text.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));}
    private static void centered(Canvas c,String s,float centerX,float baseline,int color){text.setColor(color);text.setTextAlign(Paint.Align.CENTER);c.drawText(s,centerX,baseline,text);text.setTextAlign(Paint.Align.LEFT);}
    private static void left(Canvas c,String s,float x,float baseline,int color){text.setColor(color);c.drawText(s,x,baseline,text);}
}
