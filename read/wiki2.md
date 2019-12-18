#### 进程保活目录介绍
- 01.经典的一像素保活
- 02.双进程守护
- 03.在后台播放一个无声的音频
- 04.利用账号同步机制拉活
- 05.使用JobService
- 06.将service设置为前台进程
- 07.第三方推送SDK唤醒



### 01.经典的一像素保活
- 据说这个是手Q的进程保活方案，基本思想，系统一般是不会杀死前台进程的。所以要使得进程常驻，我们只需要在锁屏的时候在本进程开启一个Activity，为了欺骗用户，让这个Activity的大小是1像素，并且透明无切换动画，在开屏幕的时候，把这个Activity关闭掉，所以这个就需要监听系统锁屏广播，如下。
    ```
    public class MainActivity extends AppCompatActivity {
     
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
       }
    }
    ```
- 如果直接启动一个Activity，当我们按下back键返回桌面的时候，oom_adj的值是8，上面已经提到过，这个进程在资源不够的情况下是容易被回收的。现在造一个一个像素的Activity。
    ```
    public class LiveActivity extends Activity {
     
        public static final String TAG = LiveActivity.class.getSimpleName();
     
        public static void actionToLiveActivity(Context pContext) {
            Intent intent = new Intent(pContext, LiveActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            pContext.startActivity(intent);
        }
     
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.activity_live);
     
            Window window = getWindow();
            //放在左上角
            window.setGravity(Gravity.START | Gravity.TOP);
            WindowManager.LayoutParams attributes = window.getAttributes();
            //宽高设计为1个像素
            attributes.width = 1;
            attributes.height = 1;
            //起始坐标
            attributes.x = 0;
            attributes.y = 0;
            window.setAttributes(attributes);
     
            ScreenManager.getInstance(this).setActivity(this);
        }
     
        @Override
        protected void onDestroy() {
            super.onDestroy();
            Log.d(TAG, "onDestroy");
        }
    }
    ```
- 为了做的更隐藏，最好设置一下这个Activity的主题，当然也无所谓了
    ```
    <style name="LiveStyle">
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowAnimationStyle">@null</item>
        <item name="android:windowNoTitle">true</item>
    </style>
    ```
- 在屏幕关闭的时候把LiveActivity启动起来，在开屏的时候把LiveActivity 关闭掉，所以要监听系统锁屏广播，以接口的形式通知MainActivity启动或者关闭LiveActivity。
    ```
    public class ScreenBroadcastListener {
     
        private Context mContext;
     
        private ScreenBroadcastReceiver mScreenReceiver;
     
        private ScreenStateListener mListener;
     
        public ScreenBroadcastListener(Context context) {
            mContext = context.getApplicationContext();
            mScreenReceiver = new ScreenBroadcastReceiver();
        }
     
        interface ScreenStateListener {
     
            void onScreenOn();
     
            void onScreenOff();
        }
     
        /**
         * screen状态广播接收者
         */
        private class ScreenBroadcastReceiver extends BroadcastReceiver {
            private String action = null;
     
            @Override
            public void onReceive(Context context, Intent intent) {
                action = intent.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(action)) { // 开屏
                    mListener.onScreenOn();
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) { // 锁屏
                    mListener.onScreenOff();
                }
            }
        }
     
        public void registerListener(ScreenStateListener listener) {
            mListener = listener;
            registerListener();
        }
     
        private void registerListener() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            mContext.registerReceiver(mScreenReceiver, filter);
        }
    }
    
    public class ScreenManager {
     
        private Context mContext;
     
        private WeakReference<Activity> mActivityWref;
     
        public static ScreenManager gDefualt;
     
        public static ScreenManager getInstance(Context pContext) {
            if (gDefualt == null) {
                gDefualt = new ScreenManager(pContext.getApplicationContext());
            }
            return gDefualt;
        }
        private ScreenManager(Context pContext) {
            this.mContext = pContext;
        }
     
        public void setActivity(Activity pActivity) {
            mActivityWref = new WeakReference<Activity>(pActivity);
        }
     
        public void startActivity() {
                LiveActivity.actionToLiveActivity(mContext);
        }
     
        public void finishActivity() {
            //结束掉LiveActivity
            if (mActivityWref != null) {
                Activity activity = mActivityWref.get();
                if (activity != null) {
                    activity.finish();
                }
            }
        }
    }
    ```
- 现在MainActivity改成如下
    ```
    public class MainActivity extends AppCompatActivity {
     
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            final ScreenManager screenManager = ScreenManager.getInstance(MainActivity.this);
            ScreenBroadcastListener listener = new ScreenBroadcastListener(this);
             listener.registerListener(new ScreenBroadcastListener.ScreenStateListener() {
                @Override
                public void onScreenOn() {
                    screenManager.finishActivity();
                }
     
                @Override
                public void onScreenOff() {
                    screenManager.startActivity();
                }
            });
        }
    }
    ```
- 按下back之后，进行锁屏，现在测试一下oom_adj的值，果然将进程的优先级提高了。但是还有一个问题，内存也是一个考虑的因素，内存越多会被最先kill掉，所以把上面的业务逻辑放到Service中，而Service是在另外一个 进程中，在MainActivity开启这个服务就行了，这样这个进程就更加的轻量，
    ```
    public class LiveService extends Service {
     
        public  static void toLiveService(Context pContext){
            Intent intent=new Intent(pContext,LiveService.class);
            pContext.startService(intent);
        }
     
        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
     
     
        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            //屏幕关闭的时候启动一个1像素的Activity，开屏的时候关闭Activity
            final ScreenManager screenManager = ScreenManager.getInstance(LiveService.this);
            ScreenBroadcastListener listener = new ScreenBroadcastListener(this);
            listener.registerListener(new ScreenBroadcastListener.ScreenStateListener() {
                @Override
                public void onScreenOn() {
                    screenManager.finishActivity();
                }
                @Override
                public void onScreenOff() {
                    screenManager.startActivity();
                }
            });
            return START_REDELIVER_INTENT;
        }
    }
    
    <service android:name=".LiveService"
        android:process=":live_service"/>
    ```
- 通过上面的操作，我们的应用就始终和前台进程是一样的优先级了，为了省电，系统检测到锁屏事件后一段时间内会杀死后台进程，如果采取这种方案，就可以避免了这个问题。但是还是有被杀掉的可能，所以我们还需要做双进程守护，关于双进程守护，比较适合的就是aidl的那种方式，但是这个不是完全的靠谱，原理是A进程死的时候，B还在活着，B可以将A进程拉起来，反之，B进程死的时候，A还活着，A可以将B拉起来。所以双进程守护的前提是，系统杀进程只能一个个的去杀，如果一次性杀两个，这种方法也是不OK的。
- 事实上，那么我们先来看看Android5.0以下的源码，ActivityManagerService是如何关闭在应用退出后清理内存的
    ```
    Process.killProcessQuiet(pid);  
    ```
- 应用退出后，ActivityManagerService就把主进程给杀死了，但是，在Android5.0以后，ActivityManagerService却是这样处理的：
    ```
    Process.killProcessQuiet(app.pid);  
    Process.killProcessGroup(app.info.uid, app.pid);  
    ```
- 在应用退出后，ActivityManagerService不仅把主进程给杀死，另外把主进程所属的进程组一并杀死，这样一来，由于子进程和主进程在同一进程组，子进程在做的事情，也就停止了。所以在Android5.0以后的手机应用在进程被杀死后，要采用其他方案。



### 05.使用JobService
- JobScheduler是作为进程死后复活的一种手段，native进程方式最大缺点是费电，Native进程费电的原因是感知主进程是否存活有两种实现方式，在 Native 进程中通过死循环或定时器，轮训判断主进程是否存活，当主进程不存活时进行拉活。
- 其次5.0以上系统不支持。 但是JobScheduler可以替代在Android5.0以上native进程方式，这种方式即使用户强制关闭，也能被拉起来，亲测可行。
    ```
    JobSheduler@TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public class MyJobService extends JobService {
        @Override
        public void onCreate() {
            super.onCreate();
            startJobSheduler();
        }
     
        public void startJobSheduler() {
            try {
                JobInfo.Builder builder = new JobInfo.Builder(1, new ComponentName(getPackageName(), MyJobService.class.getName()));
                builder.setPeriodic(5);
                builder.setPersisted(true);
                JobScheduler jobScheduler = (JobScheduler) this.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                jobScheduler.schedule(builder.build());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
     
        @Override
        public boolean onStartJob(JobParameters jobParameters) {
            return false;
        }
     
        @Override
        public boolean onStopJob(JobParameters jobParameters) {
            return false;
        }
    }
    ```




### 06.将service设置为前台进程
- 这种大部分人都了解，据说这个微信也用过的进程保活方案，移步微信Android客户端后台保活经验分享，这方案实际利用了Android前台service的漏洞。原理如下 
    - 对于 API level < 18 ：调用startForeground(ID， new Notification())，发送空的Notification ，图标则不会显示。 
    - 对于 API level >= 18：在需要提优先级的service A启动一个InnerService，两个服务同时startForeground，且绑定同样的 ID。Stop 掉InnerService ，这样通知栏图标即被移除。
    ```
    public class KeepLiveService extends Service {
     
        public static final int NOTIFICATION_ID=0x11;
     
        public KeepLiveService() {
        
        }
     
        @Override
        public IBinder onBind(Intent intent) {
            throw new UnsupportedOperationException("Not yet implemented");
        }
     
        @Override
        public void onCreate() {
            super.onCreate();
             //API 18以下，直接发送Notification并将其置为前台
            if (Build.VERSION.SDK_INT <Build.VERSION_CODES.JELLY_BEAN_MR2) {
                startForeground(NOTIFICATION_ID, new Notification());
            } else {
                //API 18以上，发送Notification并将其置为前台后，启动InnerService
                Notification.Builder builder = new Notification.Builder(this);
                builder.setSmallIcon(R.mipmap.ic_launcher);
                startForeground(NOTIFICATION_ID, builder.build());
                startService(new Intent(this, InnerService.class));
            }
        }
     
        public  class  InnerService extends Service{
            @Override
            public IBinder onBind(Intent intent) {
                return null;
            }
            @Override
            public void onCreate() {
                super.onCreate();
                //发送与KeepLiveService中ID相同的Notification，然后将其取消并取消自己的前台显示
                Notification.Builder builder = new Notification.Builder(this);
                builder.setSmallIcon(R.mipmap.ic_launcher);
                startForeground(NOTIFICATION_ID, builder.build());
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        stopForeground(true);
                        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                        manager.cancel(NOTIFICATION_ID);
                        stopSelf();
                    }
                },100);
     
            }
        }
    }
    ```
- 测试结果
    - 在没有采取前台服务之前，启动应用，oom_adj值是0，按下返回键之后，变成9（不同ROM可能不一样）
    - 在采取前台服务之后，启动应用，oom_adj值是0，按下返回键之后，变成2（不同ROM可能不一样），确实进程的优先级有所提高。 




### 07.第三方推送SDK唤醒
- 相互唤醒的意思就是，假如你手机里装了支付宝、淘宝、天猫、UC等阿里系的app，那么你打开任意一个阿里系的app后，有可能就顺便把其他阿里系的app给唤醒了。这个完全有可能的。
- 此外，开机，网络切换、拍照、拍视频时候，利用系统产生的广播也能唤醒app，不过Android N已经将这三种广播取消了。

 













