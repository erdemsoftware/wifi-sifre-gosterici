import os
import sys
import subprocess
import shutil

JDK_BIN = r"C:\Program Files\Unity\Hub\Editor\2022.3.62f3\Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK\bin"
BUILD_TOOLS = r"C:\Program Files\Unity\Hub\Editor\2022.3.62f3\Editor\Data\PlaybackEngines\AndroidPlayer\SDK\build-tools\34.0.0"
ANDROID_JAR = r"C:\Program Files\Unity\Hub\Editor\2022.3.62f3\Editor\Data\PlaybackEngines\AndroidPlayer\SDK\platforms\android-34\android.jar"

AAPT2 = os.path.join(BUILD_TOOLS, "aapt2.exe")
D8 = os.path.join(BUILD_TOOLS, "d8.bat")
APKSIGNER = os.path.join(BUILD_TOOLS, "apksigner.bat")
ZIPALIGN = os.path.join(BUILD_TOOLS, "zipalign.exe")
JAVAC = os.path.join(JDK_BIN, "javac.exe")
KEYTOOL = os.path.join(JDK_BIN, "keytool.exe")

PROJECT_DIR = r"C:\Users\muham\.gemini\antigravity\scratch\wifi_app"
SRC_DIR = os.path.join(PROJECT_DIR, "src", "com", "wifi", "sifreler")
RES_DIR = os.path.join(PROJECT_DIR, "res")
BUILD_DIR = os.path.join(PROJECT_DIR, "build")
OBJ_DIR = os.path.join(BUILD_DIR, "obj")
BIN_DIR = os.path.join(BUILD_DIR, "bin")

for d in [SRC_DIR, os.path.join(RES_DIR, "values"), os.path.join(RES_DIR, "layout"), OBJ_DIR, BIN_DIR]:
    os.makedirs(d, exist_ok=True)

manifest = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.wifi.sifreler"
    android:versionCode="1"
    android:versionName="1.0">
    <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="34" />
    <application
        android:label="WiFi Şifreleri"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
'''
with open(os.path.join(PROJECT_DIR, "AndroidManifest.xml"), "w", encoding="utf-8") as f:
    f.write(manifest)

strings = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">WiFi Şifreleri</string>
</resources>
'''
with open(os.path.join(RES_DIR, "values", "strings.xml"), "w", encoding="utf-8") as f:
    f.write(strings)

activity_main = '''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#F5F5F5"
    android:padding="16dp">

    <TextView
        android:id="@+id/tvTitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="WiFi Şifreleri (Root)"
        android:textSize="22sp"
        android:textStyle="bold"
        android:textColor="#1976D2"
        android:gravity="center"
        android:layout_marginBottom="12dp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_marginBottom="12dp">

        <Button
            android:id="@+id/btnRefresh"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Yenile"
            android:backgroundTint="#1976D2"
            android:textColor="#FFFFFF" />

        <Space
            android:layout_width="8dp"
            android:layout_height="wrap_content" />

        <Button
            android:id="@+id/btnCopyAll"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Tümünü Kopyala"
            android:backgroundTint="#388E3C"
            android:textColor="#FFFFFF" />
    </LinearLayout>

    <TextView
        android:id="@+id/tvStatus"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Yükleniyor..."
        android:textSize="14sp"
        android:textColor="#555555"
        android:layout_marginBottom="8dp" />

    <ListView
        android:id="@+id/listView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:divider="#E0E0E0"
        android:dividerHeight="8dp" />
</LinearLayout>
'''
with open(os.path.join(RES_DIR, "layout", "activity_main.xml"), "w", encoding="utf-8") as f:
    f.write(activity_main)

item_wifi = '''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:background="#FFFFFF"
    android:padding="12dp"
    android:gravity="center_vertical">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/tvSsid"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="WiFi Adı"
            android:textSize="16sp"
            android:textStyle="bold"
            android:textColor="#212121" />

        <TextView
            android:id="@+id/tvPass"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Şifre"
            android:textSize="14sp"
            android:textColor="#D32F2F"
            android:layout_marginTop="4dp" />
    </LinearLayout>

    <Button
        android:id="@+id/btnCopy"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Kopyala"
        android:backgroundTint="#0288D1"
        android:textColor="#FFFFFF"
        android:textSize="12sp" />
</LinearLayout>
'''
with open(os.path.join(RES_DIR, "layout", "item_wifi.xml"), "w", encoding="utf-8") as f:
    f.write(item_wifi)

main_java = '''package com.wifi.sifreler;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    static class WifiItem {
        String ssid;
        String password;
        WifiItem(String ssid, String password) {
            this.ssid = ssid;
            this.password = password;
        }
    }

    private TextView tvStatus;
    private ListView listView;
    private Button btnRefresh, btnCopyAll;
    private List<WifiItem> wifiList = new ArrayList<>();
    private WifiAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = (TextView) findViewById(R.id.tvStatus);
        listView = (ListView) findViewById(R.id.listView);
        btnRefresh = (Button) findViewById(R.id.btnRefresh);
        btnCopyAll = (Button) findViewById(R.id.btnCopyAll);

        adapter = new WifiAdapter();
        listView.setAdapter(adapter);

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadWifiPasswords();
            }
        });

        btnCopyAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyAllToClipboard();
            }
        });

        loadWifiPasswords();
    }

    private void loadWifiPasswords() {
        tvStatus.setText("Root izni kontrol ediliyor...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Map<String, String> results = new LinkedHashMap<>();
                boolean rootSuccess = false;

                try {
                    Process process = Runtime.getRuntime().exec("su");
                    DataOutputStream os = new DataOutputStream(process.getOutputStream());
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                    os.writeBytes("cat /data/misc/apexdata/com.android.wifi/WifiConfigStore.xml /data/misc/wifi/WifiConfigStore.xml 2>/dev/null\\n");
                    os.writeBytes("exit\\n");
                    os.flush();

                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\\n");
                    }
                    process.waitFor();

                    String content = sb.toString();
                    if (!content.trim().isEmpty()) {
                        rootSuccess = true;
                        parseWifiXml(content, results);
                    }
                } catch (Exception e) {
                    rootSuccess = false;
                }

                final boolean finalRoot = rootSuccess;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        wifiList.clear();
                        if (!finalRoot || results.isEmpty()) {
                            tvStatus.setText("Root izni alınamadı veya kayıtlı ağ bulunamadı.");
                        } else {
                            for (Map.Entry<String, String> entry : results.entrySet()) {
                                wifiList.add(new WifiItem(entry.getKey(), entry.getValue()));
                            }
                            tvStatus.setText("Toplam " + wifiList.size() + " kayıtlı ağ bulundu.");
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void parseWifiXml(String xml, Map<String, String> outMap) {
        Pattern pattern = Pattern.compile("<Network>[\\\\s\\\\S]*?</Network>");
        Matcher matcher = pattern.matcher(xml);

        Pattern ssidPat = Pattern.compile("<string name=\\"SSID\\">(.*?)</string>");
        Pattern pskPat = Pattern.compile("<string name=\\"PreSharedKey\\">(.*?)</string>");

        while (matcher.find()) {
            String block = matcher.group();
            Matcher mSsid = ssidPat.matcher(block);
            Matcher mPsk = pskPat.matcher(block);

            if (mSsid.find()) {
                String ssid = mSsid.group(1).trim();
                if (ssid.startsWith("&quot;") && ssid.endsWith("&quot;")) {
                    ssid = ssid.substring(6, ssid.length() - 6);
                } else if (ssid.startsWith("\\"") && ssid.endsWith("\\"")) {
                    ssid = ssid.substring(1, ssid.length() - 1);
                }

                String psk = "(Şifresiz)";
                if (mPsk.find()) {
                    psk = mPsk.group(1).trim();
                    if (psk.startsWith("&quot;") && psk.endsWith("&quot;")) {
                        psk = psk.substring(6, psk.length() - 6);
                    } else if (psk.startsWith("\\"") && psk.endsWith("\\"")) {
                        psk = psk.substring(1, psk.length() - 1);
                    }
                }

                if (!ssid.isEmpty()) {
                    outMap.put(ssid, psk);
                }
            }
        }

        // Fallback for older formats
        if (outMap.isEmpty()) {
            Pattern networkOld = Pattern.compile("network=\\\\{[\\\\s\\\\S]*?\\\\}");
            Matcher mOld = networkOld.matcher(xml);
            Pattern sPat = Pattern.compile("ssid=\\"(.*?)\\"");
            Pattern pPat = Pattern.compile("psk=\\"(.*?)\\"");
            while (mOld.find()) {
                String blk = mOld.group();
                Matcher ms = sPat.matcher(blk);
                Matcher mp = pPat.matcher(blk);
                if (ms.find()) {
                    String s = ms.group(1);
                    String p = mp.find() ? mp.group(1) : "(Şifresiz)";
                    outMap.put(s, p);
                }
            }
        }
    }

    private void copyToClipboard(String text, String label) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("wifi_pass", text);
        cm.setPrimaryClip(clip);
        Toast.makeText(this, label + " kopyalandı!", Toast.LENGTH_SHORT).show();
    }

    private void copyAllToClipboard() {
        if (wifiList.isEmpty()) {
            Toast.makeText(this, "Kopyalanacak ağ yok", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (WifiItem item : wifiList) {
            sb.append(item.ssid).append(" : ").append(item.password).append("\\n");
        }
        copyToClipboard(sb.toString().trim(), "Tüm şifreler");
    }

    class WifiAdapter extends BaseAdapter {
        @Override
        public int getCount() { return wifiList.size(); }
        @Override
        public Object getItem(int pos) { return wifiList.get(pos); }
        @Override
        public long getItemId(int pos) { return pos; }
        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            if (cv == null) {
                cv = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_wifi, parent, false);
            }
            final WifiItem item = wifiList.get(pos);
            TextView tvSsid = (TextView) cv.findViewById(R.id.tvSsid);
            TextView tvPass = (TextView) cv.findViewById(R.id.tvPass);
            Button btnCopy = (Button) cv.findViewById(R.id.btnCopy);

            tvSsid.setText(item.ssid);
            tvPass.setText("Şifre: " + item.password);

            btnCopy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copyToClipboard(item.password, item.ssid);
                }
            });
            return cv;
        }
    }
}
'''
with open(os.path.join(SRC_DIR, "MainActivity.java"), "w", encoding="utf-8") as f:
    f.write(main_java)

print("Project files generated successfully.")
