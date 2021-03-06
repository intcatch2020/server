package com.platypus.android.server.gui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.platypus.android.server.R;
import com.platypus.crw.FunctionObserver;

import java.text.DecimalFormat;

/**
 * View that allows users to read and set gains for the vehicle server.
 *
 * @author pkv
 */
public class GainView extends VehicleGuiView {
    private static final String TAG = GainView.class.getSimpleName();
    private static final DecimalFormat GAIN_FORMAT = new DecimalFormat("#.#####");

    final Spinner mAxis;
    final TypedArray mAxisValues;
    final EditText mGainP;
    final EditText mGainI;
    final EditText mGainD;
    final ImageButton mRefresh;
    final ImageButton mSave;
    final Handler mHandler = new Handler();

    public GainView(Context context, AttributeSet attrs) {
        super(context, attrs);

        View view = View.inflate(context, R.layout.view_gain, null);
        mAxis = (Spinner) view.findViewById(R.id.axis_spinner);
        mGainP = (EditText) view.findViewById(R.id.p_gain);
        mGainI = (EditText) view.findViewById(R.id.i_gain);
        mGainD = (EditText) view.findViewById(R.id.d_gain);
        mRefresh = (ImageButton) view.findViewById(R.id.refresh_button);
        mSave = (ImageButton) view.findViewById(R.id.save_button);

        mAxisValues = getResources().obtainTypedArray(R.array.gain_axis_values);

        // Clear color filters on save button if gains are changed.
        mGainP.addTextChangedListener(mClearSaveButton);
        mGainI.addTextChangedListener(mClearSaveButton);
        mGainD.addTextChangedListener(mClearSaveButton);

        // Clear focus when done editing.
        mGainP.setOnEditorActionListener(mClearFocus);
        mGainI.setOnEditorActionListener(mClearFocus);
        mGainD.setOnEditorActionListener(mClearFocus);

        // Refresh values when axis is changed.
        mAxis.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                clear();
            }
        });

        // Get the current values from the server.
        mRefresh.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });

        // Set the current values on the server.
        mSave.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });

        this.addView(view);
    }

    /**
     * Helper function to reset all PID values to blanks.
     */
    public void clear() {
        // Reset all PIDs to blank.
        mGainP.setText("");
        mGainI.setText("");
        mGainD.setText("");
        mRefresh.setEnabled(true);
    }

    /**
     * Helper function to trigger a refresh of the PID values from the server.
     */
    public void refresh() {
        int axis = mAxisValues.getInt(mAxis.getSelectedItemPosition(), -1);
        new GetGains(axis).execute();
        mSave.clearColorFilter();
        mRefresh.setEnabled(false);
    }

    /**
     * Helper function to save the current PID values to the server.
     */
    public void save() {
        try {
            int axis = mAxisValues.getInt(mAxis.getSelectedItemPosition(), -1);
            double p = Double.parseDouble(mGainP.getText().toString());
            double i = Double.parseDouble(mGainI.getText().toString());
            double d = Double.parseDouble(mGainD.getText().toString());

            new SetGains(axis).execute(p, i, d);
            mSave.setEnabled(false);
        } catch (NumberFormatException e) {
            mSave.setColorFilter(Color.YELLOW);
            Log.e(TAG, "Unable to parse gains.");
        }
    }

    /**
     * Clears focus from a PID text box when user clicks "done".
     */
    final TextView.OnEditorActionListener mClearFocus = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                // Hide virtual keyboard when finished editing.
                InputMethodManager imm =
                        (InputMethodManager)getContext().getSystemService(
                                Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                v.clearFocus();
            }
            return false;
        }
    };

    /**
     * Reset the save button if the user changes gains.
     */
    final TextWatcher mClearSaveButton = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // Do nothing.
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // Do nothing.
        }

        @Override
        public void afterTextChanged(Editable s) {
            mSave.clearColorFilter();
        }
    };

    /**
     * Task that queries the server for gains in the background.
     */
    class GetGains extends AsyncTask<Void, Void, Void> {
        final int mAxis;

        public GetGains(int axis) {
            mAxis = axis;
        }

        @Override
        protected Void doInBackground(Void... args) {
            synchronized (mServerLock) {
                if (mServer == null) {
                    Log.w(TAG, "Unable to get gains while disconnected.");
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mRefresh.setColorFilter(Color.YELLOW);
                            clear();
                        }
                    });
                    return null;
                }

                mServer.getGains(mAxis, new FunctionObserver<double[]>() {
                    @Override
                    public void completed(final double[] gains) {
                        if (gains.length != 3) {
                            Log.w(TAG, "Got unexpected number of gain values " + gains.length +
                                    "for axis " + mAxis + ".");
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mRefresh.setColorFilter(Color.YELLOW);
                                    clear();
                                }
                            });
                        }

                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mRefresh.clearColorFilter();
                                mGainP.setText(GAIN_FORMAT.format(gains[0]));
                                mGainI.setText(GAIN_FORMAT.format(gains[1]));
                                mGainD.setText(GAIN_FORMAT.format(gains[2]));
                                mRefresh.setEnabled(true);
                            }
                        });
                    }

                    @Override
                    public void failed(FunctionError functionError) {
                        Log.w(TAG, "Failed to retrieve server gains: " + functionError);
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mRefresh.setColorFilter(Color.YELLOW);
                                clear();
                            }
                        });
                    }
                });
            }

            return null;
        }
    }

    /**
     * Task that send the new gains to the server in the background.
     */
    class SetGains extends AsyncTask<Double, Void, Void> {
        final int mAxis;

        public SetGains(int axis) {
            mAxis = axis;
        }

        @Override
        protected Void doInBackground(Double... gain_args) {
            // Un-box gains from Double[] to double[].
            double[] gains = new double[gain_args.length];
            for (int i = 0; i < gains.length; ++i)
                gains[i] = gain_args[i];

            synchronized (mServerLock) {
                if (mServer == null) {
                    Log.w(TAG, "Unable to set gains while disconnected.");
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mSave.setColorFilter(Color.YELLOW);
                            mSave.setEnabled(true);
                        }
                    });
                    return null;
                }

                mServer.setGains(mAxis, gains, new FunctionObserver<Void>() {
                    @Override
                    public void completed(Void aVoid) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mSave.setColorFilter(Color.GREEN);
                                mSave.setEnabled(true);
                            }
                        });
                    }

                    @Override
                    public void failed(FunctionError functionError) {
                        Log.w(TAG, "Failed to set server gains: " + functionError);
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mSave.setColorFilter(Color.YELLOW);
                                mSave.setEnabled(true);
                            }
                        });
                    }
                });
            }

            return null;
        }
    }
}
