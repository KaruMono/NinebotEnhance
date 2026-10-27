package dev.ichinomiya.ninebotenhance.core;

import java.util.*;

/** Monotonic-time animation queue. Exiting cards occupy a slot until fully outside the screen. */
public final class NotificationTimeline<T> {
    public static final int ENTER_MS = 440, EXIT_MS = 240, MOVE_MS = 340, STEP = 66;
    public static final int MIN_LIMIT=1,MAX_LIMIT=5,DEFAULT_LIMIT=3;
    public static int clampLimit(int limit){return Math.max(MIN_LIMIT,Math.min(MAX_LIMIT,limit));}
    public static final int MIN_SECONDS = 5, MAX_SECONDS = 30, DEFAULT_SECONDS = 15;
    // Keep the receipt strip compact on the 848px dashboard; users can still widen it in settings.
    public static final int MIN_WIDTH=180,MAX_WIDTH=600,DEFAULT_WIDTH=280;
    public static int requireWidth(int width){if(width<MIN_WIDTH||width>MAX_WIDTH)throw new IllegalArgumentException("通知宽度须为 180–600");return width;}
    public static int clampSeconds(int seconds) { return Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, seconds)); }
    public static int duration(int seconds) {
        if (seconds < MIN_SECONDS || seconds > MAX_SECONDS) throw new IllegalArgumentException("显示时长须为 5–30 秒");
        return seconds * 1000;
    }
    public static float ease(float t) { t = Math.max(0, Math.min(1, t)); return 1 - (1-t)*(1-t)*(1-t); }
    public static final class Entry<T> {
        public final String key; public final T data; public final long born, expires;
        public long exitAt = -1; private float from, target; private long moved;
        Entry(String key,T data,long now,int duration) { this.key=key;this.data=data;born=now;expires=now+duration;moved=now; }
        public float slot(long now) { return from+(target-from)*ease((now-moved)/(float)MOVE_MS); }
        public float enter(long now) { return ease((now-born)/(float)ENTER_MS); }
        public float exit(long now) { if(exitAt<0)return 0;float t=Math.max(0,Math.min(1,(now-exitAt)/(float)EXIT_MS));return t*t; }
    }
    private record Waiting<T>(String key,T data,int duration,long queued) {}
    private final ArrayList<Entry<T>> items = new ArrayList<>();
    private final ArrayDeque<Waiting<T>> pending = new ArrayDeque<>();
    private int limit=DEFAULT_LIMIT;
    /** Visible capacity; shrinking it retires the oldest cards beyond the new limit. Returns whether anything changed. */
    public boolean setLimit(int value,long now){int next=clampLimit(value);if(next==limit)return false;limit=next;tick(now);for(int i=items.size()-1;i>=limit;i--)if(items.get(i).exitAt<0)items.get(i).exitAt=now;return true;}
    public int limit(){return limit;}
    public void add(String key,T data,int milliseconds,long now) {
        if(milliseconds<1000||milliseconds>60000)throw new IllegalArgumentException("通知时长超出范围");
        // A source notification key may be reused by an app for each incoming message.
        // Each accepted post owns its card and lifetime; the key is only used for removal.
        tick(now);
        pending.addLast(new Waiting<>(key,data,milliseconds,now));while(pending.size()>16)pending.removeFirst();pump(now);
    }
    public void remove(String key,long now) { pending.removeIf(p->p.key.equals(key)); for(Entry<T> e:items)if(e.key.equals(key)&&e.exitAt<0)e.exitAt=now; }
    public void tick(long now) {
        for(Entry<T> e:items) if(e.exitAt<0&&now>=e.expires-EXIT_MS)e.exitAt=e.expires-EXIT_MS;
        if(items.removeIf(e->now>=e.expires||e.exitAt>=0&&now>=e.exitAt+EXIT_MS))layout(now);
        pump(now);
    }
    private void pump(long now) {
        pending.removeIf(p->now-p.queued>=p.duration);
        while(!pending.isEmpty()&&items.size()<limit){Waiting<T> p=pending.removeFirst();items.add(0,new Entry<>(p.key,p.data,now,p.duration));layout(now);}
        if(!pending.isEmpty()&&items.size()>=limit){Entry<T> oldest=items.get(items.size()-1);if(oldest.exitAt<0)oldest.exitAt=now;}
    }
    private void layout(long now) { for(int i=0;i<items.size();i++){Entry<T> e=items.get(i);float target=-i*STEP;if(target!=e.target){e.from=e.slot(now);e.target=target;e.moved=now;}} }
    public List<Entry<T>> entries(long now) { tick(now);return List.copyOf(items); }
    public boolean empty(long now) { tick(now);return items.isEmpty()&&pending.isEmpty(); }
    public void clear() { items.clear();pending.clear(); }
}
