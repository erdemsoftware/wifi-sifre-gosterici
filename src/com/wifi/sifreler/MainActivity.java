package com.wifi.sifreler;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
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

    private TextView tvStatus, tvCount;
    private EditText etSearch;
    private ListView listView;
    private Button btnRefresh, btnCopyAll, btnExport;
    private LinearLayout layoutGithub;

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
        layoutGithub = (LinearLayout) findViewById(R.id.layoutGithub);

        adapter = new WifiAdapter();
        listView.setAdapter(adapter);

        // Canlı Arama
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

        // GitHub Alanına Tıklama
        layoutGithub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/erdemsoftware/wifi-sifre-gosterici"));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Tarayıcı açılamadı", Toast.LENGTH_SHORT).show();
                }
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
        tvStatus.setText("Root (su) izni isteniyor, lütfen bekleyin...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Map<String, String> results = new LinkedHashMap<>();
                String debugMsg = "";

                // 1. Doğrudan su -c ile komut çalıştırma (Magisk için en güvenli yol)
                String cmd = "cat /data/misc/apexdata/com.android.wifi/WifiConfigStore.xml " +
                             "/data/misc/wifi/WifiConfigStore.xml " +
                             "/data/misc/wifi/wpa_supplicant.conf " +
                             "/data/vendor/wifi/wpa/wpa_supplicant.conf " +
                             "/data/wifi/bcm_supp.conf 2>/dev/null";

                String content = executeSuCommand(cmd);

                // Eğer bilinen yollar boş geldiyse find ile sistemdeki tüm wifi configleri bulup oku
                if (content.trim().isEmpty() || content.startsWith("ERROR:")) {
                    String fallbackCmd = "find /data/misc /data/vendor -name '*WifiConfigStore*.xml' -o -name '*wpa_supplicant*.conf' 2>/dev/null -exec cat {} +";
                    String fallbackContent = executeSuCommand(fallbackCmd);
                    if (!fallbackContent.trim().isEmpty() && !fallbackContent.startsWith("ERROR:")) {
                        content = fallbackContent;
                    }
                }

                if (content.startsWith("ERROR:")) {
                    debugMsg = "❌ " + content;
                } else if (content.trim().isEmpty()) {
                    debugMsg = "❌ Root izni verildi ancak Wi-Fi yapılandırma dosyası boş veya bulunamadı.";
                } else {
                    parseWifiContent(content, results);
                    if (results.isEmpty()) {
                        debugMsg = "❌ Dosya okundu (" + content.length() + " karakter) ancak Wi-Fi şifresi çözülemedi.";
                    } else {
                        debugMsg = "✔ Başarıyla yüklendi (" + results.size() + " ağ).";
                    }
                }

                final String finalStatus = debugMsg;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        allWifiList.clear();
                        for (Map.Entry<String, String> entry : results.entrySet()) {
                            allWifiList.add(new WifiItem(entry.getKey(), entry.getValue()));
                        }
                        tvStatus.setText(finalStatus);
                        filterList(etSearch.getText().toString());
                    }
                });
            }
        }).start();
    }

    private String executeSuCommand(String command) {
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), "UTF-8"));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            while ((line = errReader.readLine()) != null) {
                error.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode != 0 && output.length() == 0) {
                if (error.toString().toLowerCase().contains("denied") || error.toString().toLowerCase().contains("permission")) {
                    return "ERROR: Magisk/Root izni reddedildi. Lütfen Magisk uygulamasından izin verin.";
                }
                return "ERROR: Root komutu başarısız oldu (Hata kodu: " + exitCode + "). " + error.toString().trim();
            }
            return output.toString();
        } catch (Exception e) {
            return "ERROR: su komutu çalıştırılamadı. Magisk yüklü mü? (" + e.getMessage() + ")";
        }
    }

    private void parseWifiContent(String content, Map<String, String> outMap) {
        // YÖNTEM 1: Android 9-14 WifiConfigStore.xml blokları (<Network> veya <WifiConfiguration>)
        Pattern blockPattern = Pattern.compile("<(?:Network|WifiConfiguration)>([\\s\\S]*?)</(?:Network|WifiConfiguration)>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = blockPattern.matcher(content);

        Pattern ssidPat = Pattern.compile("<string name=\"SSID\">(.*?)</string>");
        Pattern pskPat = Pattern.compile("<string name=\"PreSharedKey\">(.*?)</string>");

        while (matcher.find()) {
            String block = matcher.group(1);
            Matcher mSsid = ssidPat.matcher(block);
            Matcher mPsk = pskPat.matcher(block);

            if (mSsid.find()) {
                String ssid = cleanString(mSsid.group(1));
                String psk = "(Şifresiz / Açık)";
                if (mPsk.find()) {
                    psk = cleanString(mPsk.group(1));
                }

                if (!ssid.isEmpty() && !outMap.containsKey(ssid)) {
                    outMap.put(ssid, psk);
                }
            }
        }

        // YÖNTEM 2: wpa_supplicant.conf blokları (network={...})
        Pattern wpaBlockPat = Pattern.compile("network=\\{([\\s\\S]*?)\\}");
        Matcher wpaMatcher = wpaBlockPat.matcher(content);
        Pattern wpaSsidPat = Pattern.compile("ssid=[\"']?(.*?)[\"']?\\s*$", Pattern.MULTILINE);
        Pattern wpaPskPat = Pattern.compile("psk=[\"']?(.*?)[\"']?\\s*$", Pattern.MULTILINE);

        while (wpaMatcher.find()) {
            String block = wpaMatcher.group(1);
            Matcher ms = wpaSsidPat.matcher(block);
            Matcher mp = wpaPskPat.matcher(block);

            if (ms.find()) {
                String ssid = cleanString(ms.group(1));
                String psk = "(Şifresiz / Açık)";
                if (mp.find()) {
                    psk = cleanString(mp.group(1));
                }
                if (!ssid.isEmpty() && !outMap.containsKey(ssid)) {
                    outMap.put(ssid, psk);
                }
            }
        }

        // YÖNTEM 3: Satır Bazlı Yedek Tarayıcı (Eğer özel ROM veya üretici XML yapısını değiştirdiyse)
        if (outMap.isEmpty()) {
            String[] lines = content.split("\n");
            String currentSsid = null;
            for (String line : lines) {
                if (line.contains("name=\"SSID\">")) {
                    Matcher m = ssidPat.matcher(line);
                    if (m.find()) {
                        currentSsid = cleanString(m.group(1));
                    }
                } else if (line.contains("name=\"PreSharedKey\">") && currentSsid != null) {
                    Matcher m = pskPat.matcher(line);
                    if (m.find()) {
                        String psk = cleanString(m.group(1));
                        outMap.put(currentSsid, psk);
                        currentSsid = null;
                    }
                } else if (line.contains("<null name=\"PreSharedKey\"") && currentSsid != null) {
                    outMap.put(currentSsid, "(Şifresiz / Açık)");
                    currentSsid = null;
                }
            }
        }
    }

    private String cleanString(String str) {
        if (str == null) return "";
        str = str.trim();
        if (str.startsWith("&quot;") && str.endsWith("&quot;")) {
            str = str.substring(6, str.length() - 6);
        }
        if (str.startsWith("\"") && str.endsWith("\"")) {
            str = str.substring(1, str.length() - 1);
        }
        return str.trim();
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
            sb.append("Ağ: ").append(item.ssid).append(" | Şifre: ").append(item.password).append("\n");
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
                StringBuilder sb = new StringBuilder();
                sb.append("=== KAYITLI WI-FI ŞİFRELERİ ===\n\n");
                for (WifiItem item : allWifiList) {
                    sb.append("Ağ Adı (SSID) : ").append(item.ssid).append("\n");
                    sb.append("Şifre         : ").append(item.password).append("\n");
                    sb.append("----------------------------------------\n");
                }

                String cmd = "echo '" + sb.toString().replace("'", "'\\''") + "' > " + targetPath;
                String res = executeSuCommand(cmd);
                final boolean success = !res.startsWith("ERROR:");

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
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
