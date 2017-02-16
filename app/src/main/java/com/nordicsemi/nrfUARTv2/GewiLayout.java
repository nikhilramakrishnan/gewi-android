package com.nordicsemi.nrfUARTv2;

import android.content.Context;
import android.graphics.Color;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

/**
 * Created by nramakri on 2/13/2017.
 */

public class GewiLayout extends RelativeLayout {
    public GewiLayout(Context context) {
        super(context);
        setup();
    }

    public GewiLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    public GewiLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }

    private void setup() {
        setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.tertiary_text_light));

    }

    public void setColors(MainActivity.Modes modes) {
        if (modes == null) {
            setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.tertiary_text_light));
            return;
        }
        switch (modes.getIconType()) {
            case "fan":
                setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.holo_green_light));
                break;
            case "temperature":
                setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.holo_red_light));
                break;
            case "radio":
                setBackgroundColor(ContextCompat.getColor(getContext(), R.color.blue_translucent));
                break;
            case "volume":
                setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.holo_orange_light));
                break;
            default:
                setBackgroundColor(ContextCompat.getColor(getContext(), R.color.yellow_translucent));
                break;
        }
    }
}
