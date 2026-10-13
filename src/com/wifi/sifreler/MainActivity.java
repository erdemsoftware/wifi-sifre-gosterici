package com.wifi.sifreler;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
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
        int networkId = -1;
        boolean isOpen = false;
        boolean isSelected = false;

        WifiItem(String ssid, String password, int networkId, boolean isOpen) {
            this.ssid = ssid;
            this.password = password;
            this.networkId = networkId;
            this.isOpen = isOpen;
        }
    }

    private TextView tvStatus, tvCount;
    private EditText etSearch;
    private CheckBox cbSelectAll;
    private ListView listView;
    private Button btnRefresh, btnCopyAll, btnExport, btnDeleteSelected, btnClearOpen;
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
        cbSelectAll = (CheckBox) findViewById(R.id.cbSelectAll);
        listView = (ListView) findViewById(R.id.listView);
        btnRefresh = (Button) findViewById(R.id.btnRefresh);
        btnCopyAll = (Button) findViewById(R.id.btnCopyAll);
        btnExport = (Button) findViewById(R.id.btnExport);
        btnDeleteSelected = (Button) findViewById(R.id.btnDeleteSelected);
        btnClearOpen = (Button) findViewById(R.id.btnClearOpen);
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

        // Tümünü Seç / Kaldır
        cbSelectAll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                for (WifiItem item : filteredList) {
                    item.isSelected = isChecked;
                }
                adapter.notifyDataSetChanged();
            }
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

        // Seçilenleri Sil
        btnDeleteSelected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final List<WifiItem> toDelete = new ArrayList<>();
                for (WifiItem item : filteredList) {
                    if (item.isSelected) {
                        toDelete.add(item);
                    }
                }
                if (toDelete.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Lütfen silmek istediğiniz ağları seçin.", Toast.LENGTH_SHORT).show();
                    return;
                }
                confirmAndDelete(toDelete, toDelete.size() + " adet seçili Wi-Fi ağını silmek istediğinize emin misiniz?");
            }
        });

        // Şifresizleri Temizle
        btnClearOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final List<WifiItem> toDelete = new ArrayList<>();
                for (WifiItem item : allWifiList) {
                    if (item.isOpen) {
                        toDelete.add(item);
                    }
                }
                if (toDelete.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Kayıtlı şifresiz (açık) ağ bulunamadı.", Toast.LENGTH_SHORT).show();
                    return;
                }
                confirmAndDelete(toDelete, "Toplam " + toDelete.size() + " adet şifresiz/açık ağ bulundu. Bu ağların tümü sistemden silinsin mi?");
            }
        });

        // GitHub Linki
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
        tvStatus.setText("Root izni kontrol ediliyor...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Map<String, WifiItem> results = new LinkedHashMap<>();
                String debugMsg = "";

                // 1. WifiConfigStore ve wpa_supplicant içeriklerini oku
                String cmd = "cat /data/misc/apexdata/com.android.wifi/WifiConfigStore.xml " +
                             "/data/misc/wifi/WifiConfigStore.xml " +
                             "/data/misc/wifi/wpa_supplicant.conf " +
                             "/data/vendor/wifi/wpa/wpa_supplicant.conf " +
                             "/data/wifi/bcm_supp.conf 2>/dev/null";

                String content = executeSuCommand(cmd);

                if (content.trim().isEmpty() || content.startsWith("ERROR:")) {
                    String fallbackCmd = "find /data/misc /data/vendor -name '*WifiConfigStore*.xml' -o -name '*wpa_supplicant*.conf' 2>/dev/null -exec cat {} +";
                    String fallbackContent = executeSuCommand(fallbackCmd);
                    if (!fallbackContent.trim().isEmpty() && !fallbackContent.startsWith("ERROR:")) {
                        content = fallbackContent;
                    }
                }

                // 2. cmd wifi list-networks ile Network ID haritasını al (Silme işlemleri için)
                Map<String, Integer> idMap = new HashMap<>();
                String listCmdOut = executeSuCommand("cmd wifi list-networks 2>/dev/null");
                parseCmdWifiList(listCmdOut, idMap);

                if (content.startsWith("ERROR:")) {
                    debugMsg = "❌ " + content;
                } else if (content.trim().isEmpty()) {
                    debugMsg = "❌ Root izni verildi ancak Wi-Fi dosyaları boş veya bulunamadı.";
                } else {
                    parseWifiContent(content, idMap, results);
                    if (results.isEmpty()) {
                        debugMsg = "❌ Dosya okundu ancak Wi-Fi şifresi çözülemedi.";
                    } else {
                        debugMsg = "✔ Başarıyla yüklendi (" + results.size() + " ağ).";
                    }
                }

                final String finalStatus = debugMsg;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        allWifiList.clear();
                        allWifiList.addAll(results.values());
                        tvStatus.setText(finalStatus);
                        cbSelectAll.setChecked(false);
                        filterList(etSearch.getText().toString());
                    }
                });
            }
        }).start();
    }

    private void parseCmdWifiList(String output, Map<String, Integer> idMap) {
        if (output == null || output.trim().isEmpty() || output.startsWith("ERROR:")) return;
        String[] lines = output.split("\n");
        Pattern pat = Pattern.compile("^\\s*(\\d+)\\s+[\"']?(.*?)[\"']?\\s+", Pattern.MULTILINE);
        for (String line : lines) {
            Matcher m = pat.matcher(line);
            if (m.find()) {
                try {
                    int id = Integer.parseInt(m.group(1));
                    String ssid = cleanString(m.group(2));
                    if (!ssid.isEmpty()) {
                        idMap.put(ssid, id);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void parseWifiContent(String content, Map<String, Integer> idMap, Map<String, WifiItem> outMap) {
        // YÖNTEM 1: WifiConfigStore blokları
        Pattern blockPattern = Pattern.compile("<(?:Network|WifiConfiguration)>([\\s\\S]*?)</(?:Network|WifiConfiguration)>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = blockPattern.matcher(content);

        Pattern ssidPat = Pattern.compile("<string name=\"SSID\">(.*?)</string>");
        Pattern pskPat = Pattern.compile("<string name=\"PreSharedKey\">(.*?)</string>");
        Pattern netIdPat = Pattern.compile("<int name=\"NetworkId\" value=\"(\\d+)\" />", Pattern.CASE_INSENSITIVE);

        while (matcher.find()) {
            String block = matcher.group(1);
            Matcher mSsid = ssidPat.matcher(block);
            Matcher mPsk = pskPat.matcher(block);
            Matcher mId = netIdPat.matcher(block);

            if (mSsid.find()) {
                String ssid = cleanString(mSsid.group(1));
                String psk = "(Şifresiz / Açık)";
                boolean isOpen = true;
                if (mPsk.find()) {
                    psk = cleanString(mPsk.group(1));
                    isOpen = false;
                }

                int netId = -1;
                if (mId.find()) {
                    try { netId = Integer.parseInt(mId.group(1)); } catch (Exception ignored) {}
                }
                if (netId == -1 && idMap.containsKey(ssid)) {
                    netId = idMap.get(ssid);
                }

                if (!ssid.isEmpty() && !outMap.containsKey(ssid)) {
                    outMap.put(ssid, new WifiItem(ssid, psk, netId, isOpen));
                }
            }
        }

        // YÖNTEM 2: wpa_supplicant blokları
        Pattern wpaBlockPat = Pattern.compile("network=\\{([\\s\\S]*?)\\}");
        Matcher wpaMatcher = wpaBlockPat.matcher(content);
        Pattern wpaSsidPat = Pattern.compile("ssid=[\"']?(.*?)[\"']?\\s*$", Pattern.MULTILINE);
        Pattern wpaPskPat = Pattern.compile("psk=[\"']?(.*?)[\"']?\\s*$", Pattern.MULTILINE);
        Pattern wpaIdPat = Pattern.compile("id=(\\d+)", Pattern.MULTILINE);

        while (wpaMatcher.find()) {
            String block = wpaMatcher.group(1);
            Matcher ms = wpaSsidPat.matcher(block);
            Matcher mp = wpaPskPat.matcher(block);
            Matcher mi = wpaIdPat.matcher(block);

            if (ms.find()) {
                String ssid = cleanString(ms.group(1));
                String psk = "(Şifresiz / Açık)";
                boolean isOpen = true;
                if (mp.find()) {
                    psk = cleanString(mp.group(1));
                    isOpen = false;
                }
                int netId = -1;
                if (mi.find()) {
                    try { netId = Integer.parseInt(mi.group(1)); } catch (Exception ignored) {}
                }
                if (netId == -1 && idMap.containsKey(ssid)) {
                    netId = idMap.get(ssid);
                }

                if (!ssid.isEmpty() && !outMap.containsKey(ssid)) {
                    outMap.put(ssid, new WifiItem(ssid, psk, netId, isOpen));
                }
            }
        }

        // YÖNTEM 3: Satır Bazlı Yedek
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
                        int netId = idMap.containsKey(currentSsid) ? idMap.get(currentSsid) : -1;
                        outMap.put(currentSsid, new WifiItem(currentSsid, psk, netId, false));
                        currentSsid = null;
                    }
                } else if (line.contains("<null name=\"PreSharedKey\"") && currentSsid != null) {
                    int netId = idMap.containsKey(currentSsid) ? idMap.get(currentSsid) : -1;
                    outMap.put(currentSsid, new WifiItem(currentSsid, "(Şifresiz / Açık)", netId, true));
                    currentSsid = null;
                }
            }
        }
    }

    private void confirmAndDelete(final List<WifiItem> itemsToDelete, String message) {
        new AlertDialog.Builder(this)
            .setTitle("Ağları Sil")
            .setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Evet, Sil", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    executeDelete(itemsToDelete);
                }
            })
            .setNegativeButton("İptal", null)
            .show();
    }

    private void executeDelete(final List<WifiItem> itemsToDelete) {
        tvStatus.setText("Ağlar siliniyor...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                StringBuilder deleteCommands = new StringBuilder();
                for (WifiItem item : itemsToDelete) {
                    if (item.networkId >= 0) {
                        deleteCommands.append("cmd wifi forget-network ").append(item.networkId).append(" 2>/dev/null; ");
                        deleteCommands.append("wpa_cli remove_network ").append(item.networkId).append(" 2>/dev/null; ");
                    }
                }
                deleteCommands.append("wpa_cli save_config 2>/dev/null; ");

                executeSuCommand(deleteCommands.toString());

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, itemsToDelete.size() + " adet ağ silindi! 🗑️", Toast.LENGTH_SHORT).show();
                        loadWifiPasswords();
                    }
                });
            }
        }).start();
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
                return "ERROR: Root komutu başarısız oldu (Hata: " + exitCode + "). " + error.toString().trim();
            }
            return output.toString();
        } catch (Exception e) {
            return "ERROR: su komutu çalıştırılamadı (" + e.getMessage() + ")";
        }
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
            CheckBox cbSelect = (CheckBox) cv.findViewById(R.id.cbSelect);
            TextView tvSsid = (TextView) cv.findViewById(R.id.tvSsid);
            TextView tvPass = (TextView) cv.findViewById(R.id.tvPass);
            Button btnCopy = (Button) cv.findViewById(R.id.btnCopy);
            Button btnDelete = (Button) cv.findViewById(R.id.btnDelete);

            tvSsid.setText(item.ssid);
            tvPass.setText(item.password);

            // Şifresiz ağ ise yeşil yap
            if (item.isOpen) {
                tvPass.setTextColor(0xFF2E7D32);
            } else {
                tvPass.setTextColor(0xFFC62828);
            }

            cbSelect.setOnCheckedChangeListener(null);
            cbSelect.setChecked(item.isSelected);
            cbSelect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    item.isSelected = isChecked;
                }
            });

            btnCopy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copyToClipboard(item.password, item.ssid);
                }
            });

            btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<WifiItem> singleList = new ArrayList<>();
                    singleList.add(item);
                    confirmAndDelete(singleList, "'" + item.ssid + "' ağını cihazdan silmek istediğinize emin misiniz?");
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
