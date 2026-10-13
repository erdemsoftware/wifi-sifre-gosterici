package com.wifi.sifreler;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    private TextView tvTitle, tvStatus, tvCount;
    private EditText etSearch;
    private ListView listView;
    private Button btnRefresh, btnCopyAll, btnExport;

    private List<WifiItem> allWifiList = new ArrayList<>();
    private List<WifiItem> filteredList = new ArrayList<>();
    private WifiAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvCount = (TextView) findViewById(R.id.tvCount);
        etSearch = (EditText) findViewById(R.id.etSearch);
        listView = (ListView) findViewById(R.id.listView);
        btnRefresh = (Button) findViewById(R.id.btnRefresh);
        btnCopyAll = (Button) findViewById(R.id.btnCopyAll);
        btnExport = (Button) findViewById(R.id.btnExport);

        adapter = new WifiAdapter();
        listView.setAdapter(adapter);

        // Canlı Arama Filtresi
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterList(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

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

        btnExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportToFile();
            }
        });

        loadWifiPasswords();
    }

    private void filterList(String query) {
        filteredList.clear();
        String q = query.trim().toLowerCase(Locale.getDefault());
        if (q.isEmpty()) {
            filteredList.addAll(allWifiList);
        } else {
            for (WifiItem item : allWifiList) {
                if (item.ssid.toLowerCase(Locale.getDefault()).contains(q) ||
                    item.password.toLowerCase(Locale.getDefault()).contains(q)) {
                    filteredList.add(item);
                }
            }
        }
        tvCount.setText(filteredList.size() + " / " + allWifiList.size() + " Ağ");
        adapter.notifyDataSetChanged();
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
                        allWifiList.clear();
                        if (!finalRoot || results.isEmpty()) {
                            tvStatus.setText("❌ Root izni alınamadı veya kayıtlı ağ bulunamadı.");
                            tvCount.setText("0 Ağ");
                        } else {
                            for (Map.Entry<String, String> entry : results.entrySet()) {
                                allWifiList.add(new WifiItem(entry.getKey(), entry.getValue()));
                            }
                            tvStatus.setText("✔ Başarıyla yüklendi.");
                        }
                        filterList(etSearch.getText().toString());
                    }
                });
            }
        }).start();
    }

    private void parseWifiXml(String xml, Map<String, String> outMap) {
        Pattern pattern = Pattern.compile("<Network>[\\s\\S]*?</Network>");
        Matcher matcher = pattern.matcher(xml);

        Pattern ssidPat = Pattern.compile("<string name=\"SSID\">(.*?)</string>");
        Pattern pskPat = Pattern.compile("<string name=\"PreSharedKey\">(.*?)</string>");

        while (matcher.find()) {
            String block = matcher.group();
            Matcher mSsid = ssidPat.matcher(block);
            Matcher mPsk = pskPat.matcher(block);

            if (mSsid.find()) {
                String ssid = mSsid.group(1).trim();
                if (ssid.startsWith("&quot;") && ssid.endsWith("&quot;")) {
                    ssid = ssid.substring(6, ssid.length() - 6);
                } else if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid = ssid.substring(1, ssid.length() - 1);
                }

                String psk = "(Şifresiz)";
                if (mPsk.find()) {
                    psk = mPsk.group(1).trim();
                    if (psk.startsWith("&quot;") && psk.endsWith("&quot;")) {
                        psk = psk.substring(6, psk.length() - 6);
                    } else if (psk.startsWith("\"") && psk.endsWith("\"")) {
                        psk = psk.substring(1, psk.length() - 1);
                    }
                }

                if (!ssid.isEmpty()) {
                    outMap.put(ssid, psk);
                }
            }
        }

        // Eski Android biçimleri için fallback
        if (outMap.isEmpty()) {
            Pattern networkOld = Pattern.compile("network=\\{[\\s\\S]*?\\}");
            Matcher mOld = networkOld.matcher(xml);
            Pattern sPat = Pattern.compile("ssid=\"(.*?)\"");
            Pattern pPat = Pattern.compile("psk=\"(.*?)\"");
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
        Toast.makeText(this, label + " kopyalandı! 📋", Toast.LENGTH_SHORT).show();
    }

    private void copyAllToClipboard() {
        if (allWifiList.isEmpty()) {
            Toast.makeText(this, "Kopyalanacak ağ yok", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (WifiItem item : allWifiList) {
            sb.append("SSID: ").append(item.ssid).append(" | Şifre: ").append(item.password).append("\\n");
        }
        copyToClipboard(sb.toString().trim(), "Tüm Wi-Fi Şifreleri");
    }

    private void exportToFile() {
        if (allWifiList.isEmpty()) {
            Toast.makeText(this, "Kaydedilecek ağ bulunamadı", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String targetPath = "/sdcard/Download/wifi_sifreleri.txt";
                boolean success = false;
                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("=== KAYITLI WI-FI ŞİFRELERİ ===\\n\\n");
                    for (WifiItem item : allWifiList) {
                        sb.append("Ağ Adı (SSID) : ").append(item.ssid).append("\\n");
                        sb.append("Şifre         : ").append(item.password).append("\\n");
                        sb.append("----------------------------------------\\n");
                    }

                    Process p = Runtime.getRuntime().exec("su");
                    DataOutputStream os = new DataOutputStream(p.getOutputStream());
                    // Base64 veya doğrudan echo ile yaz
                    String content = sb.toString().replace("'", "'\\\\''");
                    os.writeBytes("echo '" + content + "' > " + targetPath + "\\n");
                    os.writeBytes("exit\\n");
                    os.flush();
                    p.waitFor();
                    success = true;
                } catch (Exception e) {
                    success = false;
                }

                final boolean finalSuccess = success;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (finalSuccess) {
                            Toast.makeText(MainActivity.this, "Kaydedildi: İndirilenler/wifi_sifreleri.txt 📁", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Dosya kaydedilemedi!", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    class WifiAdapter extends BaseAdapter {
        @Override
        public int getCount() { return filteredList.size(); }
        @Override
        public Object getItem(int pos) { return filteredList.get(pos); }
        @Override
        public long getItemId(int pos) { return pos; }
        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            if (cv == null) {
                cv = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_wifi, parent, false);
            }
            final WifiItem item = filteredList.get(pos);
            TextView tvSsid = (TextView) cv.findViewById(R.id.tvSsid);
            TextView tvPass = (TextView) cv.findViewById(R.id.tvPass);
            Button btnCopy = (Button) cv.findViewById(R.id.btnCopy);

            tvSsid.setText(item.ssid);
            tvPass.setText(item.password);

            btnCopy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copyToClipboard(item.password, item.ssid);
                }
            });

            cv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copyToClipboard(item.password, item.ssid);
                }
            });

            return cv;
        }
    }
}
