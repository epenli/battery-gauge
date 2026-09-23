package local.battery.gauge;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Reading> found = new LinkedHashMap<>();
    private String selected;
    private BluetoothLeScanner scanner;
    private boolean scanning, visible;
    private TextView status, value, detail, identity, hint;
    private ProgressBar progress;
    private final int teal = Color.rgb(0, 119, 107);
    private static class Reading {
        int percent, rssi; long seen;
        Reading(int p, int r, long t) { percent=p; rssi=r; seen=t; }
    }
    private final ScanCallback callback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult result) { receive(result); }
        @Override public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result: results) receive(result);
        }
        @Override public void onScanFailed(int error) {
            runOnUiThread(() -> { scanning=false; status.setText("扫描暂时不可用");
                hint.setText("请稍后点击重新扫描（错误 " + error + "）"); });
        }
    };
    private final Runnable ticker = new Runnable() {
        @Override public void run() { refresh(); if (visible) handler.postDelayed(this, 1000); }
    };
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView text(String s, int size, int color) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0,dp(8),0,dp(8)); return t;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        selected=getPreferences(0).getString("battery", "HBB8903302475");
        getWindow().setStatusBarColor(Color.rgb(245,248,247));
        getWindow().setNavigationBarColor(Color.rgb(245,248,247));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        LinearLayout page=new LinearLayout(this); page.setOrientation(1);
        page.setPadding(dp(26),dp(32),dp(26),dp(24)); page.setBackgroundColor(Color.rgb(245,248,247));
        TextView title=text("电池电量",30,Color.rgb(23,45,42)); title.setTypeface(null,Typeface.BOLD); page.addView(title);
        page.addView(text("靠近电池，即可查看最新电量",15,Color.DKGRAY));
        LinearLayout card=new LinearLayout(this); card.setOrientation(1); card.setPadding(dp(22),dp(24),dp(22),dp(24));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(22)); card.setBackground(bg);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.topMargin=dp(24); cp.bottomMargin=dp(20); page.addView(card,cp);
        status=text("等待电池信号",16,teal); card.addView(status);
        value=text("—",78,Color.rgb(23,45,42)); value.setTypeface(null,Typeface.BOLD); card.addView(value);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(teal)); card.addView(progress,new LinearLayout.LayoutParams(-1,dp(10)));
        identity=text("电池 " + selected.substring(3),15,Color.DKGRAY); card.addView(identity);
        detail=text("尚未收到数据",14,Color.GRAY); card.addView(detail);
        hint=text("扫描只在页面打开时进行。",14,Color.DKGRAY); page.addView(hint);
        Button retry=new Button(this); retry.setText("重新扫描"); retry.setOnClickListener(v -> { stopScan(); startScan(); }); page.addView(retry);
        Button choose=new Button(this); choose.setText("选择附近电池"); choose.setOnClickListener(v -> chooseBattery()); page.addView(choose);
        Button bluetooth=new Button(this); bluetooth.setText("蓝牙与定位设置"); bluetooth.setOnClickListener(v ->
            new AlertDialog.Builder(this).setItems(new String[]{"蓝牙设置","定位设置"},(d,n)->startActivity(new Intent(n==0?Settings.ACTION_BLUETOOTH_SETTINGS:Settings.ACTION_LOCATION_SOURCE_SETTINGS))).show()); page.addView(bluetooth);
        page.addView(text("本地读取 · 无需配对\n仅验证过当前电池的广播格式；不代表所有小哈电池均兼容。",12,Color.GRAY));
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(page); setContentView(scroll);
    }
    @Override public void onResume() { super.onResume(); visible=true; startScan(); handler.post(ticker); }
    @Override public void onPause() { visible=false; handler.removeCallbacks(ticker); stopScan(); super.onPause(); }
    private void startScan() {
        if (scanning || !visible) return;
        String[] permissions = Build.VERSION.SDK_INT >= 31
            ? new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT}
            : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        for (String p:permissions) if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED) {
            status.setText("需要扫描权限"); hint.setText("Android 10 需要定位权限才能扫描附近蓝牙设备；本应用不获取定位坐标。");
            requestPermissions(permissions,1); return;
        }
        if(Build.VERSION.SDK_INT < 31 && !((LocationManager)getSystemService(LOCATION_SERVICE)).isLocationEnabled()) {
            status.setText("请开启定位服务"); hint.setText("这是此 Android 版本扫描蓝牙的系统要求。"); return;
        }
        BluetoothAdapter adapter=((BluetoothManager)getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        if(adapter==null || !adapter.isEnabled()) { status.setText("请开启蓝牙"); return; }
        scanner=adapter.getBluetoothLeScanner();
        if(scanner==null) { status.setText("蓝牙尚未就绪"); return; }
        try {
            scanning=true; scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback);
            hint.setText("保持手机靠近电池，电量随收到的广播更新。"); refresh();
        } catch (RuntimeException e) { scanning=false; status.setText("无法开始扫描"); hint.setText("请检查蓝牙与扫描权限后重试。"); }
    }
    private void stopScan() {
        if(scanning && scanner!=null) try {scanner.stopScan(callback);} catch (RuntimeException ignored) {}
        scanning=false;
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(results.length>0) {
            for(int result:results) if(result!=PackageManager.PERMISSION_GRANTED) {
                status.setText("扫描权限未开启"); hint.setText("允许权限后，点击重新扫描。"); return;
            }
            startScan();
        }
    }
    private void receive(ScanResult result) {
        ScanRecord record=result.getScanRecord();
        BatteryName battery=BatteryName.parse(record==null ? null : record.getDeviceName());
        if(battery==null) return;
        long seen=result.getTimestampNanos()/1000000L;
        runOnUiThread(() -> {
            Reading previous=found.get(battery.id);
            if(previous==null || seen>=previous.seen) found.put(battery.id,new Reading(battery.percent,result.getRssi(),seen));
            refresh();
        });
    }
    private void refresh() {
        Reading r=found.get(selected);
        identity.setText("电池 " + selected.substring(3));
        if(r==null) { if(scanning) status.setText("正在寻找电池…"); value.setText("—"); progress.setProgress(0); detail.setText("尚未收到数据"); return; }
        long age=Math.max(0,(SystemClock.elapsedRealtime()-r.seen)/1000);
        boolean fresh=scanning && age<=15;
        if(scanning) status.setText(fresh?"附近 · 实时电量":"信号中断 · 上次读数");
        value.setText(r.percent+"%"); value.setAlpha(fresh?1f:0.45f); progress.setProgress(r.percent);
        detail.setText("更新于 " + age + " 秒前  ·  信号 " + r.rssi + " dBm");
    }
    private void chooseBattery() {
        String[] ids=found.keySet().toArray(new String[0]);
        if(ids.length==0) {new AlertDialog.Builder(this).setMessage("尚未发现兼容电池，请靠近电池后重新扫描。").setPositiveButton("知道了",null).show();return;}
        String[] labels=new String[ids.length];
        for(int n=0;n<ids.length;n++)labels[n]=ids[n].substring(3)+" · "+found.get(ids[n]).percent+"%";
        new AlertDialog.Builder(this).setTitle("选择电池").setItems(labels,(d,n)->{
            selected=ids[n];getPreferences(0).edit().putString("battery",selected).apply();refresh();
        }).show();
    }
}
