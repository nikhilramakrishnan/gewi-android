package com.nordicsemi.nrfUARTv2;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.widget.Toast;

import java.io.IOException;

import co.mobiwise.library.RadioListener;
import co.mobiwise.library.RadioManager;

class InfotainmentController {

    private Context mContext;
    private int station;
    private int currentSong;
    private RadioManager mRadioManager;
    private MediaPlayer mMediaPlayer;
    private Station[] mStations;
    private String[] mSongs;

    private boolean isRadioConnected = false;
    private boolean isMediaPlayerPrepared = false;
    private boolean isSongComplete = false;

    InfotainmentController(@NonNull Context context) {
        mContext = context;
        mRadioManager = RadioManager.with(mContext);
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.start();
                isMediaPlayerPrepared = true;
            }
        });
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                isSongComplete = true;
            }
        });
        mStations = createStations();
        mSongs = createSongs();
        station = 0;
    }

    private Station[] createStations() {
        final String[] radioUrls = {
                "https://www.sub.fm/listen.pls",
                "http://rockfm.rockfm.com.tr:9450",
                "http://cast9.directhostingcenter.com:2199/tunein/soevwkmu.pls"};
        Station[] stations = new Station[radioUrls.length];
        for (int i = 0; i < radioUrls.length; i++) {
            stations[i] = new Station(radioUrls[i]);
        }
        return stations;
    }

    private String[] createSongs() {
        return new String[] {
                "cd1.mp3",
                "cd2.mp3"
        };
    }

    private void prepareMediaPlayerFiles(@NonNull String fullyJustifiedFilename) {
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            AssetFileDescriptor descriptor = mContext.getAssets().openFd(fullyJustifiedFilename);
            mMediaPlayer.setDataSource(
                    descriptor.getFileDescriptor(),
                    descriptor.getStartOffset(),
                    descriptor.getLength());
            mMediaPlayer.prepare();
        } catch (Exception e) {
            Toast.makeText(mContext, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startRadio(boolean restart) {
        if (isRadioPlaying() && !restart) {
            return;
        }
        mRadioManager.startRadio(mStations[station].url);
    }

    void releaseMediaPlayer() {
        mMediaPlayer.release();
        isMediaPlayerPrepared = false;
    }

    void startMediaPlayer() {
        if (!isMediaPlayerPrepared) {
            prepareMediaPlayerFiles(mSongs[currentSong]);
            return;
        }
        mMediaPlayer.start();
    }

    void stopMediaPlayer() {
        mMediaPlayer.stop();
        isMediaPlayerPrepared = false;
    }

    void pauseMediaPlayer() {
        mMediaPlayer.pause();
    }

    void connectRadio() {
        if (isRadioPlaying()) {
            return;
        }
        mRadioManager.connect();
        isRadioConnected = true;
    }

    void disconnectRadio() {
        if (isRadioPlaying()) {
            stopRadio();
        }
        mRadioManager.disconnect();
        isRadioConnected = false;
    }

    void startRadio() {
        startRadio(false);
    }

    void stopRadio() {
        if (!isRadioPlaying()) {
            return;
        }
        mRadioManager.stopRadio();
    }

    void nextRadioStation() {
        station++;
        if (station == 3) {
            station = 0;
        }
        startRadio(true);
    }

    void previousRadioStation() {
        station--;
        if (station == -1) {
            station = 2;
        }
        startRadio(true);
    }

    void nextSong() {
        mMediaPlayer.reset();
        currentSong++;
        if (currentSong == 2) {
            currentSong = 0;
        }
        prepareMediaPlayerFiles(mSongs[currentSong]);
    }

    void previousSong() {
        mMediaPlayer.reset();
        currentSong--;
        if (currentSong == -1) {
            currentSong = 1;
        }
        prepareMediaPlayerFiles(mSongs[currentSong]);
    }

    boolean isRadioPlaying() {
        return isRadioConnected && mRadioManager.isPlaying();
    }

    boolean isMediaPlayerPrepared() {
        return isMediaPlayerPrepared;
    }

    private class Station {
        private String url;
        private String name;

        Station(String url) {
            this.url = url;
            this.name = Uri.parse(url).getHost();
        }
    }
}
