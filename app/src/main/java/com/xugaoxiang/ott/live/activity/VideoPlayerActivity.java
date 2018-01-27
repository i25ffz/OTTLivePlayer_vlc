package com.xugaoxiang.ott.live.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.xugaoxiang.ott.live.R;
import com.xugaoxiang.ott.live.adapter.ProgramAdapter;
import com.xugaoxiang.ott.live.bean.LiveBean;
import com.xugaoxiang.ott.live.utils.NetWorkUtils;
import com.xugaoxiang.ott.live.utils.PreUtils;
import com.xugaoxiang.ott.live.view.RotaProgressBar;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import butterknife.Bind;
import butterknife.ButterKnife;


public class VideoPlayerActivity extends Activity {
    private static final String TAG = VideoPlayerActivity.class.getName();

    public static LiveBean liveBean;
    @Bind(R.id.lv_program)
    ListView lvProgram;
    @Bind(R.id.tv_program_number)
    TextView tvProgramNumber;
    @Bind(R.id.tv_program_name)
    TextView tvProgramName;
    @Bind(R.id.tv_system_time)
    TextView tvSystemTime;
    @Bind(R.id.rl_display)
    RelativeLayout rlDisplay;
    @Bind(R.id.surfaceview)
    SurfaceView surfaceview;
    @Bind(R.id.pb_loading)
    RotaProgressBar pbLoading;
    @Bind(R.id.ll_program_list)
    LinearLayout llProgramList;
    @Bind(R.id.tv_cache)
    TextView tvCache;

    private final static int CODE_SHOWLOADING = 1;

    private final static int CODE_STOP_SHOWLOADING = 2;

    private final static int CODE_GONE_PROGRAMINFO = 3;

    private final static int CODE_NET_STATE = 4;

    private final static int CODE_HIDE_BLACK = 5;

    private final static String PROGRAM_KEY = "lastProIndex";
    @Bind(R.id.tv_net_state)
    TextView tvNetState;
    @Bind(R.id.tv_black)
    TextView tvBlack;
    private SurfaceHolder surfaceHolder;
    private LibVLC libvlc = null;
    private MediaPlayer mediaPlayer = null;
    private IVLCVout ivlcVout;
    private Media media;

    private int programIndex = 0;
    private int currentListItemID = 0;

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CODE_SHOWLOADING:
                    showLoading();
                    handler.sendEmptyMessageDelayed(CODE_SHOWLOADING, 1000);
                    break;

                case CODE_STOP_SHOWLOADING:
                    hideLoading();
                    handler.removeMessages(CODE_SHOWLOADING);
                    break;

                case CODE_GONE_PROGRAMINFO:
                    rlDisplay.setVisibility(View.INVISIBLE);
                    break;

                case CODE_NET_STATE:
                    tvNetState.setVisibility(View.INVISIBLE);
                    break;

                case CODE_HIDE_BLACK:
                    tvBlack.setVisibility(View.INVISIBLE);
                    break;
            }
        }
    };

    private TranslateAnimation animIn;
    private TranslateAnimation exitAnim;
    private NetworkReceiver networkReceiver;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live);
        ButterKnife.bind(this);

        initData();
        initPlayer();

        showProgramInfo();

        //1 初始化  手势识别器
        gestureDetector = new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                //判断竖直方向移动的大小
                if(Math.abs(e1.getRawY() - e2.getRawY()) > 100){
                    //Toast.makeText(getApplicationContext(), "动作不合法", 0).show();
                    return true;
                }
                if(Math.abs(velocityX) < 150){
                    //Toast.makeText(getApplicationContext(), "移动的太慢", 0).show();
                    return true;
                }

                if((e1.getRawX() - e2.getRawX()) > 200){
                    hidePlaylist();
                    return true;
                }

                if((e2.getRawX() - e1.getRawX()) > 200){
                    showPlaylist();
                    //消费掉当前事件  不让当前事件继续向下传递
                    return true;
                }
                return super.onFling(e1, e2, velocityX, velocityY);
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //2.让手势识别器生效
        gestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }
    
    @Override
    protected void onStop() {
        super.onStop();

        mediaPlayer.stop();
        mediaPlayer.getVLCVout().detachViews();
    }

    private void registerNetReceiver() {
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        networkReceiver = new NetworkReceiver();
        registerReceiver(networkReceiver, filter);
    }

    private void initData() {
        setAdapter();

        programIndex = PreUtils.getInt(VideoPlayerActivity.this, PROGRAM_KEY, 0);
        if ((programIndex >= liveBean.getData().size()) || (programIndex < 0)) {
            programIndex = 0;
        }
    }

    private void initPlayer() {
        ArrayList<String> options = new ArrayList<>();
        options.add("--aout=opensles");
        options.add("--audio-time-stretch");
        options.add("-vvv");
        libvlc = new LibVLC(VideoPlayerActivity.this, options);
        surfaceHolder = surfaceview.getHolder();
        surfaceHolder.setKeepScreenOn(true);
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });

        mediaPlayer = new MediaPlayer(libvlc);
        mediaPlayer.setEventListener(new MediaPlayer.EventListener() {
            @Override
            public void onEvent(MediaPlayer.Event event) {
                switch (event.type) {
                    case MediaPlayer.Event.Buffering:
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.pause();
                        }

                        if (event.getBuffering() >= 100.0f) {
                            hideLoading();
                            Log.i(TAG, "onEvent: buffer success...");
                            handler.sendEmptyMessageDelayed(CODE_HIDE_BLACK, 500);
                            handler.sendEmptyMessageDelayed(CODE_GONE_PROGRAMINFO, 5000);

                            handler.sendEmptyMessageDelayed(CODE_HIDE_BLACK, 500);
                            handler.sendEmptyMessageDelayed(CODE_GONE_PROGRAMINFO, 5000);

                            mediaPlayer.play();
                        } else {
                            showLoading();
                            tvCache.setText("缓冲: " + Math.floor(event.getBuffering()) + "%");
                        }

                        break;

                    case MediaPlayer.Event.Playing:
                        Log.i(TAG, "onEvent: playing...");
                        break;

                    case MediaPlayer.Event.EncounteredError:
                        Log.i(TAG, "onEvent: error...");
                        hideLoading();
                        mediaPlayer.stop();
                        Toast.makeText(VideoPlayerActivity.this, "播放出错！", Toast.LENGTH_LONG).show();
                        break;
                }
            }
        });

        media = new Media(libvlc, Uri.parse(liveBean.getData().get(programIndex).getUrl()));
        mediaPlayer.setMedia(media);

        ivlcVout = mediaPlayer.getVLCVout();
        ivlcVout.setVideoView(surfaceview);
        ivlcVout.attachViews();
        ivlcVout.addCallback(new IVLCVout.Callback() {
            @Override
            public void onSurfacesCreated(IVLCVout vlcVout) {
                int sw = getWindow().getDecorView().getWidth();
                int sh = getWindow().getDecorView().getHeight();

                if (sw * sh == 0) {
                    Log.e(TAG, "Invalid surface size");
                    return;
                }

                mediaPlayer.getVLCVout().setWindowSize(sw, sh);
                mediaPlayer.setAspectRatio("16:9");
                mediaPlayer.setScale(0);
            }

            @Override
            public void onSurfacesDestroyed(IVLCVout vlcVout) {

            }
        });

        mediaPlayer.play();
    }

    private void setAdapter() {
        if (liveBean != null) {
            ProgramAdapter programAdapter = new ProgramAdapter(VideoPlayerActivity.this);
            lvProgram.setAdapter(programAdapter);
        } else {
            Toast.makeText(this, "打开播放列表失败，请检查网络！", Toast.LENGTH_SHORT).show();
        }

        lvProgram.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (programIndex != position) {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                    }

                    programIndex = position;
                    ivlcVout.detachViews();
                    play(position);
                }

            }
        });

        lvProgram.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentListItemID = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    public static void openLive(Context context, LiveBean liveBean) {
        VideoPlayerActivity.liveBean = liveBean;
        context.startActivity(new Intent(context, VideoPlayerActivity.class));
    }

    private void play(int position) {
        Uri parse = Uri.parse(liveBean.getData().get(position).getUrl());
        media = new Media(libvlc, parse);
        mediaPlayer.setMedia(media);
        ivlcVout.setVideoView(surfaceview);
        ivlcVout.attachViews();
        mediaPlayer.play();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (event.getRepeatCount() != 0) {
                    break;
                }

                showPlaylist();
                break;

            case KeyEvent.KEYCODE_DPAD_UP:
                if (currentListItemID == 0) {
                    lvProgram.setSelection(liveBean.getData().size() - 1);
                }

                if (llProgramList.getVisibility() == View.VISIBLE) {
                    return false;
                }
                previous();
                showProgramInfo();
                break;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (currentListItemID == liveBean.getData().size() - 1) {
                    lvProgram.setSelection(0);
                }

                if (llProgramList.getVisibility() == View.VISIBLE) {
                    return false;
                }
                next();
                showProgramInfo();
                break;

            case KeyEvent.KEYCODE_MENU:
                showProgramInfo();
                handler.sendEmptyMessageDelayed(CODE_GONE_PROGRAMINFO, 4000);
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {

            case KeyEvent.KEYCODE_DPAD_UP:
                if (llProgramList.getVisibility() == View.VISIBLE) {
                    return false;
                }
                changeChannel();
                break;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (llProgramList.getVisibility() == View.VISIBLE) {
                    return false;
                }
                changeChannel();
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void showProgramInfo() {
        rlDisplay.setVisibility(View.VISIBLE);
        tvProgramName.setText(liveBean.getData().get(programIndex).getName());
        tvProgramNumber.setText(liveBean.getData().get(programIndex).getNum());
        tvSystemTime.setText(getDtate());
    }

    @Override
    public void onBackPressed() {
        if (llProgramList.getVisibility() == View.VISIBLE) {
            exitProgramList();
        } else {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
            }

            finish();
        }
    }

    private void exitProgramList() {
        if (exitAnim == null) {
            exitAnim = new TranslateAnimation(0f, -llProgramList.getWidth(), 0f, 0f);
            exitAnim.setDuration(500);
        }
        exitAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                llProgramList.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        llProgramList.startAnimation(exitAnim);
    }

    public void hidePlaylist() {
        if (llProgramList.getVisibility() == View.VISIBLE) {
            exitProgramList();
        }
    }

    public void showPlaylist() {
        if (llProgramList.getVisibility() == View.VISIBLE) {
            if (currentListItemID == programIndex) {
                return;
            }
        }

        if (animIn == null) {
            animIn = new TranslateAnimation(-llProgramList.getWidth(), 0f, 0f, 0f);
            animIn.setDuration(500);
        }
        animIn.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                llProgramList.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animation animation) {

            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        llProgramList.startAnimation(animIn);
        lvProgram.setSelection(programIndex);
    }

    public String getDtate() {
        SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy/MM/dd    HH:mm");
        return sDateFormat.format(new Date());
    }

    private void showLoading() {
        if (pbLoading.getVisibility() == View.INVISIBLE) {
            pbLoading.setVisibility(View.VISIBLE);
            tvCache.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoading() {
        if (pbLoading.getVisibility() == View.VISIBLE) {
            pbLoading.setVisibility(View.INVISIBLE);
            tvCache.setVisibility(View.GONE);
        }
    }

    private void changeChannel() {
        ivlcVout.detachViews();
        play(programIndex);
    }

    private void next() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }

        tvBlack.setVisibility(View.VISIBLE);
        programIndex++;
        if (programIndex >= liveBean.getData().size()) {
            programIndex = 0;
        }

        handler.removeMessages(CODE_GONE_PROGRAMINFO);
    }

    private void previous() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }

        tvBlack.setVisibility(View.VISIBLE);
        programIndex--;
        if (programIndex < 0) {
            programIndex = liveBean.getData().size() - 1;
        }

        handler.removeMessages(CODE_GONE_PROGRAMINFO);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (networkReceiver == null) {
            registerNetReceiver();
        }

        mediaPlayer.play();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            ivlcVout.detachViews();
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        ivlcVout.setVideoView(surfaceview);
        ivlcVout.attachViews();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        PreUtils.setInt(VideoPlayerActivity.this, PROGRAM_KEY, programIndex);

        if (networkReceiver != null) {
            unregisterReceiver(networkReceiver);
        }

        if (mediaPlayer != null) {
            mediaPlayer.release();
            ivlcVout.detachViews();
            libvlc.release();
        }

        finish();
    }

    public void showNetworkInfo(String text) {
        if (tvNetState.getVisibility() == View.INVISIBLE) {
            tvNetState.setVisibility(View.VISIBLE);
        }

        tvNetState.setText(text);
    }

    public void hideNetworkInfo() {
        if (tvNetState.getVisibility() == View.VISIBLE) {
            tvNetState.setVisibility(View.INVISIBLE);
        }

        tvNetState.setText("");
    }

    class NetworkReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!NetWorkUtils.getNetState(context)) {
                showNetworkInfo("网络已断开!");
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }
            } else {
                hideNetworkInfo();
                if (!mediaPlayer.isPlaying()) {
                    mediaPlayer.play();
                }
            }
        }
    }
}
