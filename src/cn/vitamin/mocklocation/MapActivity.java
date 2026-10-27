package cn.vitamin.mocklocation;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class MapActivity extends Activity {
    private WebView map;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.WHITE);
        TextView help = new TextView(this);
        help.setText("点击地图选点，可拖动和缩放；地图需要联网。");
        help.setTextSize(15);
        help.setPadding(18, 12, 18, 12);
        layout.addView(help);
        map = new WebView(this);
        map.getSettings().setJavaScriptEnabled(true);
        map.getSettings().setAllowFileAccess(false);
        map.getSettings().setAllowContentAccess(false);
        map.getSettings().setDomStorageEnabled(false);
        map.getSettings().setUserAgentString(map.getSettings().getUserAgentString() + " FixedLocationRCPro2/1.1");
        map.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
        });
        layout.addView(map, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout buttons = new LinearLayout(this);
        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setOnClickListener(v -> finish());
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1));
        Button select = new Button(this);
        select.setText("使用此位置");
        select.setOnClickListener(v -> map.evaluateJavascript("JSON.stringify(tileLoaded ? [selected.lat,selected.lng] : null)", value -> {
            try {
                JSONArray point = new JSONArray(new org.json.JSONTokener(value).nextValue().toString());
                double lat = point.getDouble(0), lon = point.getDouble(1);
                if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) throw new IllegalArgumentException();
                setResult(RESULT_OK, new Intent().putExtra("lat", lat).putExtra("lon", lon));
                finish();
            } catch (Exception e) { Toast.makeText(this, "地图尚未加载，请检查网络后重试", Toast.LENGTH_LONG).show(); }
        }));
        buttons.addView(select, new LinearLayout.LayoutParams(0, -2, 1));
        layout.addView(buttons);
        setContentView(layout);
        try {
            double lat = getIntent().getDoubleExtra("lat", 22.281214);
            double lon = getIntent().getDoubleExtra("lon", 114.128642);
            lat = Math.max(-85, Math.min(85, lat));
            lon = Math.max(-180, Math.min(180, lon));
            String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'>"
                    + "<meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; img-src https://a.tile.openstreetmap.fr data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'\">"
                    + "<style>" + asset("leaflet.css") + "html,body,#map{height:100%;margin:0}.leaflet-control-attribution{font-size:11px!important}</style>"
                    + "</head><body><div id='map'></div><script>" + asset("leaflet.js") + "</script><script>"
                    + String.format(Locale.US, "var selected=L.latLng(%.7f,%.7f);", lat, lon)
                    + "var map=L.map('map',{worldCopyJump:true}).setView(selected,16);"
                    + "var tileLoaded=false;var tiles=L.tileLayer('https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors · OSM France'}).addTo(map);"
                    + "tiles.on('tileload',function(){tileLoaded=true;});"
                    + "var marker=L.circleMarker(selected,{radius:9,color:'#fff',weight:3,fillColor:'#2468df',fillOpacity:1}).addTo(map);"
                    + "map.on('click',function(e){selected=e.latlng.wrap();marker.setLatLng(selected);});"
                    + "</script></body></html>";
            map.loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "UTF-8", null);
        } catch (Exception e) { Toast.makeText(this, "无法打开地图", Toast.LENGTH_LONG).show(); finish(); }
    }

    private String asset(String name) throws Exception {
        try (InputStream input = getAssets().open(name); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) bytes.write(buffer, 0, length);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Override protected void onDestroy() {
        if (map != null) map.destroy();
        super.onDestroy();
    }
}
