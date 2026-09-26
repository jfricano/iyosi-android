package com.orcasolutions.iyosi;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PlaceMapView extends View {
    interface Listener {
        void viewportChanged(String bbox);
        void placeTapped(String id);
        void pointChosen(double lat, double lon);
    }
    private static final int TILE = 256;
    private static final long CACHE_AGE_MS = 7L * 24 * 60 * 60 * 1000;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService tileWorkers = Executors.newFixedThreadPool(2);
    private final LruCache<String, Bitmap> memory = new LruCache<String, Bitmap>(64) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return Math.max(1, bitmap.getByteCount() / 65536); }
    };
    private final Set<String> inFlight = new HashSet<>();
    private final Map<String, Long> retryAfter = new HashMap<>();
    private final File disk;
    private final GestureDetector gestures;
    private final ScaleGestureDetector scale;
    private final List<JSONObject> places = new ArrayList<>();
    private Listener listener;
    private double lat = 14.5995, lon = 120.9842;
    private int zoom = 12;
    private boolean moved;
    private double chosenLat = Double.NaN, chosenLon = Double.NaN;
    private final Runnable viewportTask = () -> { if (listener != null) listener.viewportChanged(bbox()); };

    PlaceMapView(Context context) {
        super(context);
        setContentDescription("Map of Metro Manila. Swipe to pan, pinch to zoom, hold to choose a place pin. Places are also listed below.");
        disk = new File(context.getFilesDir(), "map-tiles"); disk.mkdirs();
        tileWorkers.execute(() -> {
            File[] old = disk.listFiles();
            if (old != null) for (File file : old)
                if (System.currentTimeMillis() - file.lastModified() > CACHE_AGE_MS) file.delete();
        });
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { moved = false; return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (moved) return true;
                if (zoom <= 13) {
                    float d = getResources().getDisplayMetrics().density;
                    for (List<JSONObject> group : groups().values()) {
                        if (group.size() < 2) continue;
                        double[] center = groupCenter(group);
                        if (Math.hypot(screenX(center[1]) - e.getX(), screenY(center[0]) - e.getY()) < 28 * d) {
                            moveTo(center[0], center[1], zoom + 2); return true;
                        }
                    }
                }
                JSONObject closest = null; double distance = 36 * getResources().getDisplayMetrics().density;
                for (JSONObject place : places) {
                    JSONObject point = place.optJSONObject("location"); if (point == null) continue;
                    double dx = screenX(point.optDouble("lon")) - e.getX();
                    double dy = screenY(point.optDouble("lat")) - e.getY();
                    double d = Math.hypot(dx, dy); if (d < distance) { distance = d; closest = place; }
                }
                if (closest != null && listener != null) listener.placeTapped(closest.optString("id"));
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                chosenLat = latitude((worldY(lat) + e.getY() - getHeight() / 2.0) / worldSize());
                chosenLon = longitude((worldX(lon) + e.getX() - getWidth() / 2.0) / worldSize());
                invalidate(); if (listener != null) listener.pointChosen(chosenLat, chosenLon);
            }
        });
        scale = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private float cumulative = 1;
            @Override public boolean onScale(ScaleGestureDetector detector) {
                cumulative *= detector.getScaleFactor();
                if (cumulative > 1.6f) { zoomBy(1); cumulative = 1; }
                if (cumulative < 0.625f) { zoomBy(-1); cumulative = 1; }
                return true;
            }
        });
    }
    void setListener(Listener listener) { this.listener = listener; scheduleViewport(); }
    void setPlaces(List<JSONObject> items) { places.clear(); places.addAll(items); invalidate(); }
    void moveTo(double latitude, double longitude, int newZoom) {
        lat = Math.max(-85, Math.min(85, latitude)); lon = Math.max(-180, Math.min(180, longitude));
        zoom = Math.max(minZoom(), Math.min(18, newZoom)); invalidate(); scheduleViewport();
    }
    void zoomBy(int delta) { moveTo(lat, lon, zoom + delta); }
    double centerLat() { return lat; }
    double centerLon() { return lon; }
    double chosenLat() { return Double.isNaN(chosenLat) ? lat : chosenLat; }
    double chosenLon() { return Double.isNaN(chosenLon) ? lon : chosenLon; }
    void clearChosen() { chosenLat = Double.NaN; chosenLon = Double.NaN; invalidate(); }
    private int minZoom() {
        for (int z = 1; z <= 18; z++) {
            double span = Math.max(getWidth(), getHeight()) / (TILE * Math.pow(2, z)) * 360;
            if (span <= 0.42) return z;
        }
        return 12;
    }
    private double worldSize() { return TILE * Math.pow(2, zoom); }
    private double worldX(double longitude) { return (longitude + 180) / 360 * worldSize(); }
    private double worldY(double latitude) {
        double radians = Math.toRadians(Math.max(-85, Math.min(85, latitude)));
        return (1 - Math.log(Math.tan(radians) + 1 / Math.cos(radians)) / Math.PI) / 2 * worldSize();
    }
    private double longitude(double x) { return x * 360 - 180; }
    private double latitude(double y) { return Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * y)))); }
    private float screenX(double longitude) { return (float) (getWidth() / 2.0 + worldX(longitude) - worldX(lon)); }
    private float screenY(double latitude) { return (float) (getHeight() / 2.0 + worldY(latitude) - worldY(lat)); }
    String bbox() {
        double halfW = Math.min(getWidth() / worldSize() * 180, 0.235);
        double halfH = Math.min(getHeight() / worldSize() * 180, 0.235);
        double west = Math.max(-180, lon - halfW), east = Math.min(180, lon + halfW);
        double north = latitude((worldY(lat) - getHeight() / 2.0) / worldSize());
        double south = latitude((worldY(lat) + getHeight() / 2.0) / worldSize());
        north = Math.min(north, lat + halfH); south = Math.max(south, lat - halfH);
        return String.format(java.util.Locale.US, "%.6f,%.6f,%.6f,%.6f", west, south, east, north);
    }
    private void scheduleViewport() { main.removeCallbacks(viewportTask); main.postDelayed(viewportTask, 350); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh); zoom = Math.max(zoom, minZoom()); scheduleViewport();
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        scale.onTouchEvent(event); gestures.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
                || event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) lastValid = false;
        if (event.getPointerCount() == 1 && !scale.isInProgress()) {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                // Movement is applied incrementally from the preceding pointer position.
                int count = event.getHistorySize();
                float prevX = count > 0 ? event.getHistoricalX(count - 1) : lastX;
                float prevY = count > 0 ? event.getHistoricalY(count - 1) : lastY;
                if (lastValid) { prevX = lastX; prevY = lastY; }
                double centerX = worldX(lon) - (event.getX() - prevX);
                double centerY = worldY(lat) - (event.getY() - prevY);
                lon = longitude(centerX / worldSize()); lat = latitude(centerY / worldSize());
                lat = Math.max(-85, Math.min(85, lat)); lon = Math.max(-180, Math.min(180, lon));
                moved = true; invalidate(); scheduleViewport();
            }
            lastX = event.getX(); lastY = event.getY(); lastValid = true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) lastValid = false;
        return true;
    }
    @Override protected void onDetachedFromWindow() {
        main.removeCallbacks(viewportTask); tileWorkers.shutdownNow(); super.onDetachedFromWindow();
    }
    private float lastX, lastY; private boolean lastValid;

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.rgb(233, 238, 232));
        double left = worldX(lon) - getWidth() / 2.0, top = worldY(lat) - getHeight() / 2.0;
        int x0 = (int) Math.floor(left / TILE), x1 = (int) Math.floor((left + getWidth()) / TILE);
        int y0 = (int) Math.floor(top / TILE), y1 = (int) Math.floor((top + getHeight()) / TILE);
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) {
            int wrapped = ((x % (1 << zoom)) + (1 << zoom)) % (1 << zoom);
            if (y < 0 || y >= (1 << zoom)) continue;
            String key = zoom + "-" + wrapped + "-" + y;
            int px = (int) Math.round(x * TILE - left), py = (int) Math.round(y * TILE - top);
            Bitmap tile = memory.get(key);
            if (tile == null) requestTile(key, wrapped, y);
            else { paint.setColor(Color.WHITE); canvas.drawBitmap(tile, null, new Rect(px, py, px + TILE, py + TILE), paint); }
        }
        drawPlaces(canvas);
        if (!Double.isNaN(chosenLat)) {
            paint.setColor(Color.MAGENTA); canvas.drawCircle(screenX(chosenLon), screenY(chosenLat), 12 * getResources().getDisplayMetrics().density, paint);
        }
    }

    private void drawPlaces(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density;
        if (zoom <= 13) {
            for (List<JSONObject> group : groups().values()) {
                if (group.size() == 1) { drawMarker(canvas, group.get(0), d); continue; }
                double[] center = groupCenter(group);
                float x = screenX(center[1]), y = screenY(center[0]);
                paint.setColor(Color.rgb(28, 52, 65)); canvas.drawCircle(x, y, 18 * d, paint);
                paint.setColor(Color.WHITE); paint.setTextSize(13 * d); paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(String.valueOf(group.size()), x, y + 4 * d, paint);
            }
            return;
        }
        for (JSONObject place : places) {
            JSONObject point = place.optJSONObject("location"); if (point == null) continue;
            float x = screenX(point.optDouble("lon")), y = screenY(point.optDouble("lat"));
            if (x < -30 || y < -30 || x > getWidth() + 30 || y > getHeight() + 30) continue;
            drawMarker(canvas, place, d);
        }
    }
    private Map<String, List<JSONObject>> groups() {
        float size = 48 * getResources().getDisplayMetrics().density;
        Map<String, List<JSONObject>> grouped = new HashMap<>();
        for (JSONObject place : places) {
            JSONObject point = place.optJSONObject("location"); if (point == null) continue;
            float x = screenX(point.optDouble("lon")), y = screenY(point.optDouble("lat"));
            if (x < -30 || y < -30 || x > getWidth() + 30 || y > getHeight() + 30) continue;
            String cell = (int) Math.floor(x / size) + ":" + (int) Math.floor(y / size);
            grouped.computeIfAbsent(cell, ignored -> new ArrayList<>()).add(place);
        }
        return grouped;
    }
    private double[] groupCenter(List<JSONObject> group) {
        double latitude = 0, longitude = 0;
        for (JSONObject item : group) {
            JSONObject point = item.optJSONObject("location");
            latitude += point.optDouble("lat"); longitude += point.optDouble("lon");
        }
        return new double[]{latitude / group.size(), longitude / group.size()};
    }
    private void drawMarker(Canvas canvas, JSONObject place, float d) {
            JSONObject point = place.optJSONObject("location"); if (point == null) return;
            float x = screenX(point.optDouble("lon")), y = screenY(point.optDouble("lat"));
            String kind = place.optString("kind"), status = place.optString("advisory_status");
            int color = status.equals("prohibited") ? Color.rgb(180, 38, 38)
                    : status.equals("disputed") ? Color.rgb(183, 105, 19)
                    : status.equals("verified_designated") ? Color.rgb(20, 113, 77)
                    : Color.rgb(44, 93, 154);
            paint.setColor(Color.WHITE); canvas.drawCircle(x, y, 14 * d, paint);
            paint.setColor(color);
            if (kind.equals("informal_outdoor")) canvas.drawCircle(x, y, 10 * d, paint);
            else if (kind.equals("venue_claim")) canvas.drawRect(x - 9 * d, y - 9 * d, x + 9 * d, y + 9 * d, paint);
            else {
                Path shape = new Path();
                if (kind.equals("public_designated")) {
                    shape.moveTo(x, y - 11 * d); shape.lineTo(x + 11 * d, y + 9 * d); shape.lineTo(x - 11 * d, y + 9 * d);
                } else {
                    shape.moveTo(x - 10 * d, y); shape.lineTo(x, y - 10 * d); shape.lineTo(x + 10 * d, y);
                    shape.lineTo(x, y + 10 * d);
                }
                shape.close(); canvas.drawPath(shape, paint);
            }
            if (status.equals("verified_designated")) {
                paint.setColor(Color.WHITE); paint.setTextSize(11 * d); paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("✓", x, y + 4 * d, paint);
            }
    }
    private void requestTile(String key, int x, int y) {
        synchronized (inFlight) {
            if (System.currentTimeMillis() < retryAfter.getOrDefault(key, 0L) || !inFlight.add(key)) return;
        }
        final int requestedZoom = zoom;
        tileWorkers.execute(() -> {
            try {
                File file = new File(disk, key + ".png");
                if (file.isFile() && System.currentTimeMillis() - file.lastModified() < CACHE_AGE_MS) {
                    Bitmap tile = BitmapFactory.decodeFile(file.getAbsolutePath());
                    if (tile != null) memory.put(key, tile);
                    else { file.delete(); synchronized (inFlight) { retryAfter.put(key, System.currentTimeMillis() + 30000); } }
                } else {
                    String url = BuildConfig.TILE_URL.replace("{z}", String.valueOf(requestedZoom))
                            .replace("{x}", String.valueOf(x)).replace("{y}", String.valueOf(y));
                    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setRequestProperty("User-Agent", "iYosi/0.1 (Android; com.orcasolutions.iyosi)");
                    connection.setConnectTimeout(7000); connection.setReadTimeout(7000);
                    try {
                        if (connection.getResponseCode() == 200) {
                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            byte[] buffer = new byte[8192]; int length;
                            try (java.io.InputStream input = connection.getInputStream()) {
                                while ((length = input.read(buffer)) != -1) stream.write(buffer, 0, length);
                            }
                            byte[] bytes = stream.toByteArray();
                            Bitmap tile = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            if (tile != null) {
                                try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); }
                                memory.put(key, tile);
                            } else synchronized (inFlight) { retryAfter.put(key, System.currentTimeMillis() + 30000); }
                        } else synchronized (inFlight) { retryAfter.put(key, System.currentTimeMillis() + 30000); }
                    } finally { connection.disconnect(); }
                }
            } catch (Exception ignored) {
                synchronized (inFlight) { retryAfter.put(key, System.currentTimeMillis() + 30000); }
            }
            finally { synchronized (inFlight) { inFlight.remove(key); } main.post(this::invalidate); }
        });
    }
}
