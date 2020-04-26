package shigeki.work.musicalscalechecker;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;

public class MainActivity extends AppCompatActivity {

    final double maxSense = 0.3;
    double sense;

    SharedPreferences pref;
    final String sensePref = "sense";

    WaveView waveView;

    double[] noteFrequencies = {16.35, 17.32, 18.35, 19.45, 20.6, 21.83, 23.12, 24.5, 25.96, 27.5, 29.14, 30.87};
    String[] noteNames = {"C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"};
    String[] scaleNames = {"ド", "ド#", "レ", "レ#", "ミ", "ファ", "ファ#", "ソ", "ソ#", "ラ", "ラ#", "シ"};

    TextView bigNote;
    TextView noteEn;
    TextView noteJa;
    TextView freqText;
    TextView stopText;

    boolean isStopped = false;

    public final int REQUEST_CODE = 1000;

    private AdView mAdView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Android 6, API 23以上でパーミッションの確認
        if(Build.VERSION.SDK_INT >= 23) {
            String[] permissions = {
                    Manifest.permission.RECORD_AUDIO
            };
            checkPermission(permissions, REQUEST_CODE);
        }
    }

    public void checkPermission(final String[] permissions,final int request_code){
        ActivityCompat.requestPermissions(this, permissions, request_code);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    // パーミッションが許可された
                    start();
                } else {
                    // パーミッションが拒否された
                    new AlertDialog.Builder( this )
                            .setTitle("パーミッションエラー")
                            .setMessage("マイクの権限が必要です。設定->アプリ->音程チェッカーから、権限を付与してください。")
                            .setPositiveButton("OK" , new  DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // クリックしたときの処理
                                    finish();
                                }
                            })
                            .show();
                }
            }
        }
    }

    public void start(){

        pref = getSharedPreferences(sensePref, Context.MODE_PRIVATE);

        //startRecording();

        waveView = findViewById(R.id.waveView);
        setSeekbar();
        bigNote = findViewById(R.id.bigNote);
        noteEn = findViewById(R.id.noteEn);
        noteJa = findViewById(R.id.noteJa);
        freqText = findViewById(R.id.frequency);
        stopText = findViewById(R.id.stop);
        stopText.setVisibility(View.INVISIBLE);

        //メインスレッドでViewを更新する用
        final Handler handler = new Handler();

        //https://medium.com/@juniorbump/pitch-detection-in-android-using-tarsosdsp-a2dd4a3f04e9
        final int samplingRate = 44100;
        final int bufferSize = 4096;
        final int fftSize = bufferSize / 2;

        AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(samplingRate, bufferSize,0);

        PitchDetectionHandler pdh = new PitchDetectionHandler() {
            @Override
            public void handlePitch(PitchDetectionResult res, final AudioEvent event){
                final float pitchInHz = res.getPitch();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(event.getRMS() >= sense && !isStopped) {
                            //音量が閾値を超えている場合のみ
                            processPitch(pitchInHz);
                        }
                        //メインスレッドで更新
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if(!isStopped)
                                    waveView.update(event.getFloatBuffer());
                            }
                        });
                    }
                });
            }
        };
        AudioProcessor pitchProcessor = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, samplingRate, bufferSize, pdh);
        dispatcher.addAudioProcessor(pitchProcessor);

        Thread audioThread = new Thread(dispatcher, "Audio Thread");
        audioThread.start();


        //Admob
        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
            }
        });
        mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
    }

    public void setSeekbar() {
        int value = pref.getInt(sensePref, 0);
        setSenseText(value);
        sense = maxSense * value/100;
        waveView.setSense(sense);

        // SeekBar
        SeekBar seekBar = findViewById(R.id.seekBar);
        // 初期値
        seekBar.setProgress(value);
        // 最大値
        seekBar.setMax(100);
        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    final SharedPreferences.Editor editor = pref.edit();

                    //ツマミがドラッグされると呼ばれる
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        sense = maxSense * progress/100;
                        editor.putInt(sensePref, progress);
                        waveView.setSense(sense);
                        editor.commit();
                        setSenseText(progress);
                    }

                    //ツマミがタッチされた時に呼ばれる
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    //ツマミがリリースされた時に呼ばれる
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }

                });
    }

    public void setSenseText(int num){
        TextView textView = findViewById(R.id.senseText);
        textView.setText(String.format("マイク感度:%3d%%", num));
    }

    public void processPitch(float frequency) {
        if(frequency <= 0){
            bigNote.setText("--");
            noteJa.setText("--");
            noteEn.setText("--");
            freqText.setText("----Hz");
            return;
        }
        float freq = frequency;
        while(frequency > (float)(noteFrequencies[noteFrequencies.length- 1])){   //noteFrequenciesの値までオクターブを下げていく
            frequency /= 2.0;
        }
        while(frequency < (float)(noteFrequencies[0])) {   //noteFrequenciesの値までオクターブを上げる
            frequency *= 2.0;
        }

        double minDistance = 10000.0;  //間の距離
        int index = 0;

        for(int i=0; i<noteFrequencies.length; i++) {
            float distance = Math.abs((float)noteFrequencies[i] - frequency);   //各音程までの距離の絶対値
            if(distance < minDistance){ //一番小さい距離のものを記憶
                index = i;
                minDistance = distance;
            }
        }

        int octave = (int)(Math.log(freq / frequency));

        //low,mid,hiに対応(A基準のオクターブにする
        int octaveJa = octave;
        if(index < 9){
            octaveJa = octave-1; //Aより小さければ1つ下のオクターブとする
        }

        String prefix = "";
        if(octaveJa < 2){
            //1以下のオクターブでlow
            for(int i=0; i<=Math.abs(1-octaveJa); i++){
                prefix = prefix + "low";//1からの距離の回数lowをつける
            }
        }else if(octaveJa < 4){
            //3以下のオクターブでmid
            prefix = "mid" + (octaveJa-1);   //2,3のオクターブの時、-1するだけで良い
        }else{
            for(int i=0; i<=octaveJa-4; i++){
                prefix = prefix + "hi";
            }
        }


        String note = noteNames[index] + octave;
        String freqStr = String.format("%4d", (int)(freq));

        bigNote.setText(scaleNames[index]);
        noteJa.setText(prefix + noteNames[index]);
        noteEn.setText(note);
        freqText.setText(freqStr + "Hz");
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        if(stopText == null) return false;
        if(motionEvent.getAction() == MotionEvent.ACTION_DOWN){
            if(isStopped) {
                stopText.setVisibility(View.INVISIBLE);
                isStopped = false;
            }else{
                stopText.setVisibility(View.VISIBLE);
                isStopped = true;
            }
        }
        return true;
    }
}
