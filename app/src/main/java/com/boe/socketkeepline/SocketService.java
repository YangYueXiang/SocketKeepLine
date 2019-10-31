package com.boe.socketkeepline;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by yzq on 2017/9/26.
 * <p>
 * socket连接服务
 */
public class SocketService extends Service {

    /*socket*/
    private Socket socket;
    /*连接线程*/
    private Thread connectThread;
    private Timer timer = new Timer();
    private OutputStream outputStream;
    private SocketBinder sockerBinder = new SocketBinder();
    private TimerTask task;
    private SharedPreferences sharedPreferences;

    /*默认重连*/
    private boolean isReConnect = true;

    private Handler handler = new Handler(Looper.getMainLooper());


    @Override
    public IBinder onBind(Intent intent) {
        return sockerBinder;
    }


    public class SocketBinder extends Binder {

        /*返回SocketService 在需要的地方可以通过ServiceConnection获取到SocketService  */
        public SocketService getService() {
            return SocketService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        /*初始化socket*/
        initSocket();
        return super.onStartCommand(intent, flags, startId);
    }

    /*初始化socket*/
    private void initSocket() {
        if (socket == null && connectThread == null) {
            connectThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    socket = new Socket();
                    try {
                        /*超时时间为2秒*/
                        socket.connect(new InetSocketAddress("112.126.123.94", Integer.valueOf("8638")), 2000);
                        /*连接成功的话  发送心跳包*/
                        if (socket.isConnected()) {
                            /*因为Toast是要运行在主线程的  这里是子线程  所以需要到主线程哪里去显示toast*/
                            Log.i("tcp", "socket已连接");
                            /*发送连接成功的消息*/
                        //    EventBus.getDefault().postSticky(new ConstantEvent(9));
                            /*发送心跳数据*/
                            sendBeatData();
                            PrintWriter pw = new PrintWriter(socket.getOutputStream());
                            InputStream inputStream = socket.getInputStream();
                            byte[] buffer = new byte[1024];
                            int len = -1;
                            while ((len = inputStream.read(buffer)) != -1) {
                                String data = new String(buffer, 0, len);
                                Log.i("tcp", "收到服务器的数据----------------------------------:" + data);
                            }
                        } else {
                            Log.i("tcp", "连接超时");
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                        if (e instanceof SocketTimeoutException) {
                       /*     sharedPreferences = getSharedPreferences("sp",MODE_PRIVATE);
                            SharedPreferences.Editor editor= sharedPreferences.edit();
                            editor.putString("connected","");
                            editor.commit();*/
                            Log.i("tcp", "连接超时，正在重连");
                            releaseSocket();
                            stopSelf();

                        } else if (e instanceof NoRouteToHostException) {
                            Log.i("tcp", "该地址不存在，请检查");
                            stopSelf();

                        } else if (e instanceof ConnectException) {
                            /*重连*/
                            releaseSocket();
                            stopSelf();

                        }

                    }

                }
            });
            /*启动连接线程*/
            connectThread.start();
        }


    }

    /*发送数据*/
    public void sendOrder(final String msg) {
        if (socket != null && socket.isConnected()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        socket.getOutputStream().write(msg.getBytes());
                        socket.getOutputStream().flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } else {
            /*重连*/
            releaseSocket();
        }
    }

    /*定时发送数据*/
    private void sendBeatData() {
        if (timer == null) {
            timer = new Timer();
        }

        if (task == null) {
            task = new TimerTask() {
                @Override
                public void run() {
                    try {
                        outputStream = socket.getOutputStream();
                        /*这里的编码方式根据你的需求去改*/
                        outputStream.write(("test").getBytes("gbk"));
                        outputStream.flush();
                    } catch (Exception e) {
                        /*发送失败说明socket断开了或者出现了其他错误*/
                        Log.i("tcp", "断开连接，正在重连");
                        /*重连*/
                        releaseSocket();
                        e.printStackTrace();
                    }
                }
            };
        }

        timer.schedule(task, 0, 2000);
    }


    /*释放资源*/
    private void releaseSocket() {

        if (task != null) {
            task.cancel();
            task = null;
        }
        if (timer != null) {
            timer.purge();
            timer.cancel();
            timer = null;
        }

        if (outputStream != null) {
            try {
                outputStream.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
            outputStream = null;
        }

        if (socket != null) {
            try {
                socket.close();

            } catch (IOException e) {
            }
            socket = null;
        }

        if (connectThread != null) {
            connectThread = null;
        }

        /*重新初始化socket*/
        if (isReConnect) {
            initSocket();
        }

    }

    //重启App
    public void reStartApp() {
        Intent intent = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
        //与正常页面跳转一样可传递序列化数据,在Launch页面内获得
        intent.putExtra("REBOOT","reboot");
        PendingIntent restartIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);
        AlarmManager mgr = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, restartIntent);
        android.os.Process.killProcess(android.os.Process.myPid());
    }

}