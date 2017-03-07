package com.nordicsemi.nrfUARTv2;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import co.mobiwise.library.RadioManager;

public class InfotainmentController {

    private int station;

    private RadioManager mRadioManager;
    private Station[] mStations;
    private Context mContext;

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

    public void connectRadio() {
        mRadioManager.connect();
    }

    public void disconnectRadio() {
        mRadioManager.disconnect();
    }

    public void startRadio() {
        mRadioManager.startRadio("http://cast9.directhostingcenter.com:2199/tunein/soevwkmu.pls");
    }

    public void stopRadio() {
        mRadioManager.stopRadio();
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
