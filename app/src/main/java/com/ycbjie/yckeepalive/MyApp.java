package com.ycbjie.yckeepalive;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.ycbjie.live.alive.YcKeepAlive;
import com.ycbjie.live.config.ForegroundNotification;
import com.ycbjie.live.config.ForegroundNotificationClickListener;
import com.ycbjie.live.config.KeepLiveService;

public class MyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        initKeepLive();
    }

    /**
     * 初始化保活
     */
    private void initKeepLive() {
        //定义前台服务的默认样式。即标题、描述和图标
        ForegroundNotification notification = new ForegroundNotification("推送服务", "推送服务正在运行中...", R.mipmap.ic_launcher,
                //定义前台服务的通知点击事件
                new ForegroundNotificationClickListener() {
                    @Override
                    public void onNotificationClick(Context context, Intent intent) {
                        launchApp(getPackageName());
                    }
                })
                //要想不显示通知，可以设置为false，默认是false
                .setIsShow(false);
        //启动保活服务
        YcKeepAlive.startWork(this, YcKeepAlive.RunMode.ENERGY, notification,
                //你需要保活的服务，如socket连接、定时任务等，建议不用匿名内部类的方式在这里写
                new KeepLiveService() {
                    /**
                     * 运行中
                     * 由于服务可能会多次自动启动，该方法可能重复调用
                     */
                    @Override
                    public void onWorking() {
                        Log.e("xuexiang", "onWorking");
                    }
                    /**
                     * 服务终止
                     * 由于服务可能会被多次终止，该方法可能重复调用，需同onWorking配套使用，如注册和注销broadcast
                     */
                    @Override
                    public void onStop() {
                        Log.e("xuexiang", "onStop");
                    }
                }
        );
    }


    /**
     * 打开 App
     *
     * @param packageName 包名
     */
    public void launchApp(final String packageName) {
        if (isSpace(packageName)) {
            return;
        }
        this.startActivity(getLaunchAppIntent(packageName, true));
    }


    /**
     * 获取打开 App 的意图
     *
     * @param packageName 包名
     * @param isNewTask   是否开启新的任务栈
     * @return 打开 App 的意图
     */
    public Intent getLaunchAppIntent(final String packageName, final boolean isNewTask) {
        Intent intent = this.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            return null;
        }
        return getIntent(intent, isNewTask);
    }

    private Intent getIntent(final Intent intent, final boolean isNewTask) {
        return isNewTask ? intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) : intent;
    }

    private boolean isSpace(final String s) {
        if (s == null) {
            return true;
        }
        for (int i = 0, len = s.length(); i < len; ++i) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
