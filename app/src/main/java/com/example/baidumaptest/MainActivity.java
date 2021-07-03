package com.example.baidumaptest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.VersionInfo;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BaiduMapOptions;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.Text;
import com.baidu.mapapi.model.LatLng;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import android.os.*;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Date;
import java.text.SimpleDateFormat;

public class MainActivity extends AppCompatActivity {

    private TextView tv_postion;
    private LocationClient mLocationClient;
    private MapView mapView;
    private BaiduMap baiduMap;
    private boolean  isFirstLocate = true;
    private int cnt;
    private Socket socket;
    private ExecutorService mThreadPool;//线程池
    private OutputStream outputStream;
    private PowerManager.WakeLock wakeLock = null;
    private NotificationManager notificationManager;
    private String notificationId="serviced";
    private String notificationName="serviceName";

    public class MyLocationListener extends BDAbstractLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location) {
            //mapView 销毁后不在处理新接收的位置
            if (location == null || mapView == null){
                return;
            }
            MyLocationData locData = new MyLocationData.Builder()
                    .accuracy(location.getRadius())
                    // 此处设置开发者获取到的方向信息，顺时针0-360
                    .direction(location.getDirection()).latitude(location.getLatitude())
                    .longitude(location.getLongitude()).build();
            baiduMap.setMyLocationData(locData);
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_main);
        tv_postion = (TextView) findViewById(R.id.tv_position);
        mapView = (MapView) findViewById(R.id.map_bd);
        mThreadPool = Executors.newCachedThreadPool();
        baiduMap = mapView.getMap();
        baiduMap.setMyLocationEnabled(true);
        cnt=0;
        TimerTask task = new TimerTask(){
            public void run(){
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK|PowerManager.ON_AFTER_RELEASE,MainActivity.class.getName());
                if(wakeLock!=null){
                    wakeLock.acquire();
                }
            }
        };
        Timer timer=new Timer();
        timer.schedule(task,5000);
        initLocation();
        initBaiDuMap();
        List<String>  permissionList = new ArrayList<>();
        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.READ_PHONE_STATE);
        }
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if(!permissionList.isEmpty()){
            String[] permissions= permissionList.toArray(new String[permissionList.size()]);
            ActivityCompat.requestPermissions(MainActivity.this,permissions,1);
        }else{

            requestLocation();
        }

    }

    private void initBaiDuMap() {

    }

    private void requestLocation() {
        String CHANNEL_ONE_ID = "CHANNEL_ONE_ID";
        String CHANNEL_ONE_NAME= "CHANNEL_ONE_ID";
        Notification.Builder builder = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder = new Notification.Builder(MainActivity.this.getApplicationContext()).setChannelId(CHANNEL_ONE_ID);
        }
        NotificationChannel notificationChannel= null;
//进行8.0的判断
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationChannel= new NotificationChannel(CHANNEL_ONE_ID,
                    CHANNEL_ONE_NAME, NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.enableLights(true);
            notificationChannel.setShowBadge(true);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager= (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(notificationChannel);
        }
        Intent nfIntent = new Intent(MainActivity.this.getApplicationContext(),MainActivity.class);
        builder.setContentIntent(PendingIntent.getActivity(MainActivity.this, 0, nfIntent, 0)) // 设置PendingIntent
                .setContentTitle("正在进行后台定位") // 设置下拉列表里的标题
                .setSmallIcon(R.mipmap.ic_launcher) // 设置状态栏内的小图标
                .setContentText("后台定位通知") // 设置上下文内容
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis()); // 设置该通知发生的时间
        Notification notification = null;
        notification = builder.build();
        notification.defaults = Notification.DEFAULT_SOUND; //设置为默认的声音
        LocationClientOption  option = new LocationClientOption();
        option.setScanSpan(60000);
        //option.setIsNeedAddress(true);
        option.setNeedDeviceDirect(true);
        option.setOpenGps(true);
        mLocationClient.enableLocInForeground(1001,notification);
        mLocationClient.setLocOption(option);
        mLocationClient.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case 1:
                if(grantResults.length>0){
                    for (int result: grantResults
                    ) {
                        if(result !=PackageManager.PERMISSION_GRANTED){
                            Toast.makeText(this, "必须同意所有的权限才能使用本程序", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                    }
                    requestLocation();
                }else{
                    Toast.makeText(this, "发生了错误", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private void initLocation() {
        mLocationClient = new LocationClient(getApplicationContext());

        mLocationClient.registerLocationListener(new BDLocationListener() {
            @Override
            public void onReceiveLocation(BDLocation bdLocation) {
                StringBuilder  currentPosition =  new StringBuilder();
                currentPosition.append("纬度：").append(bdLocation.getLatitude()).append("\n");
                currentPosition.append("经度：").append(bdLocation.getLongitude()).append("\n");
                /*currentPosition.append("国家：").append(bdLocation.getCountry()).append("\n");
                currentPosition.append("省：").append(bdLocation.getProvince()).append("\n");
                currentPosition.append("市：").append(bdLocation.getCity()).append("\n");
                currentPosition.append("区：").append(bdLocation.getDistrict()).append("\n");
                currentPosition.append("街道：").append(bdLocation.getStreet()).append("\n");
                currentPosition.append("门牌号：").append(bdLocation.getStreetNumber()).append("\n");*/
                currentPosition.append("定位方式：");
                if(bdLocation.getLocType() == BDLocation.TypeGpsLocation){
                    currentPosition.append("GPS");
                }else if(bdLocation.getLocType() == BDLocation.TypeNetWorkLocation){
                    currentPosition.append("网络");
                }
                currentPosition.append(" "+cnt);
                cnt++;
               // String ss=Double.toString(bdLocation.getLatitude())+" "+Double.toString(bdLocation.getLongitude());

                if(bdLocation.getLocType() == BDLocation.TypeGpsLocation ||bdLocation.getLocType() == BDLocation.TypeNetWorkLocation){

                    if(isFirstLocate){
                        LatLng ll = new LatLng(bdLocation.getLatitude(),bdLocation.getLongitude());
                        MapStatus.Builder builder = new MapStatus.Builder();
                        builder.target(ll).zoom(18.0f);
                        baiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
                        isFirstLocate = false;
                    }
                    MyLocationData.Builder  locationBuilder = new MyLocationData.Builder();
                    locationBuilder.latitude(bdLocation.getLatitude());
                    locationBuilder.longitude(bdLocation.getLongitude());
                    MyLocationData locationData = locationBuilder.build();
                    baiduMap.setMyLocationData(locationData);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try{
                                socket = new Socket("8.140.20.196",8080);
                                System.out.println(socket.isConnected());
                            }catch (IOException e){
                                e.printStackTrace();
                            }
                            try {
                                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式

                                PrintWriter printWriter = new PrintWriter(socket.getOutputStream(),true);
                                printWriter.println(bdLocation.getLatitude()+" "+bdLocation.getLongitude()+" "+df.format(new Date()));
                            }
                            catch (Exception e)
                            {
                                Log.d("connect error", "run: ");
                            }
                        }
                    }).start();

                }
                tv_postion.setText(currentPosition);
            }
        });
        mLocationClient.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLocationClient.stop();
        mapView.onDestroy();
        baiduMap.setMyLocationEnabled(false);
        if(wakeLock!=null){
            wakeLock.release();
            wakeLock=null;
        }
        mLocationClient.disableLocInForeground(true);
    }
}
