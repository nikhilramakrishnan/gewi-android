package com.nordicsemi.nrfUARTv2;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import co.mobiwise.library.RadioManager;

public class InfotainmentController {

    private Context mContext;
    private int station;
    protected RadioManager mRadioManager;
    private Station[] mStations;

    public InfotainmentController(@NonNull Context context) {
        mContext = context;
        mRadioManager = RadioManager.with(mContext);
        mStations = createStations();
        station = 0;
    }

    private Station[] createStations() {
        final String[] radioUrls = {
                "http://rockfm.rockfm.com.tr:9450",
                "https://www.sub.fm/listen.pls",
                "http://cast9.directhostingcenter.com:2199/tunein/soevwkmu.pls"};
        Station[] stations = new Station[radioUrls.length];
        for (int i = 0; i < radioUrls.length; i++) {
            stations[i] = new Station(radioUrls[i]);
        }
        return stations;
    }

    void connectRadio() {
        mRadioManager.connect();
    }

    void disconnectRadio() {
        mRadioManager.disconnect();
    }

    void startRadio() {
        mRadioManager.connect();
        mRadioManager.startRadio(mStations[station].url);
    }

    void stopRadio() {
        mRadioManager.stopRadio();
        mRadioManager.disconnect();
    }

    void nextRadioStation() {
        station++;
        if (station == 4) {
            station = 0;
        }
        startRadio();
    }

    void previousRadioStation() {
        station--;
        if (station == -1) {
            station = 3;
        }
        startRadio();
    }

    boolean isRadioPlaying() {
        return mRadioManager.isPlaying();
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
