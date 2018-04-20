package com.biblepaywallet.presenter.activities;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.transition.TransitionManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.biblepaywallet.BiblePayApp;
import com.biblepaywallet.R;
import com.biblepaywallet.presenter.activities.util.BRActivity;
import com.biblepaywallet.presenter.customviews.BRDialogView;
import com.biblepaywallet.presenter.interfaces.BROnSignalCompletion;
import com.biblepaywallet.tools.animation.BRAnimator;
import com.biblepaywallet.tools.animation.BRDialog;
import com.biblepaywallet.tools.animation.SpringAnimator;
import com.biblepaywallet.tools.crypto.CryptoHelper;
import com.biblepaywallet.tools.manager.BRApiManager;
import com.biblepaywallet.tools.manager.BRReportsManager;
import com.biblepaywallet.tools.manager.BRSharedPrefs;
import com.biblepaywallet.tools.security.SmartValidator;
import com.biblepaywallet.tools.threads.executor.BRExecutor;
import com.biblepaywallet.tools.util.Bip39Reader;
import com.biblepaywallet.tools.util.TypesConverter;
import com.biblepaywallet.tools.util.Utils;
import com.biblepaywallet.wallet.WalletsMaster;
import com.biblepaywallet.wallet.abstracts.BaseWalletManager;

import org.junit.experimental.theories.internal.ParameterizedAssertionError;

import java.util.Locale;
import java.util.Random;


public class AssociateBOINCActivity extends BRActivity {
    private static final String TAG = AssociateBOINCActivity.class.getName();
    private Button submit;
    private EditText wordEditFirst;
    private EditText wordEditSecond;
    private TextView wordTextFirst;
    private TextView wordTextSecond;
    private ImageView checkMark1;
    private ImageView checkMark2;
    private SparseArray<String> sparseArrayWords = new SparseArray<>();
    public static boolean appVisible = false;
    private static AssociateBOINCActivity app;
    private ConstraintLayout constraintLayout;
    private ConstraintSet applyConstraintSet = new ConstraintSet();
    private ConstraintSet resetConstraintSet = new ConstraintSet();

    public static AssociateBOINCActivity getApp() {
        return app;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_associate_boinc);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        submit = (Button) findViewById(R.id.button_submit);
        wordEditFirst = (EditText) findViewById(R.id.word_edittext_first);
        wordEditSecond = (EditText) findViewById(R.id.word_edittext_second);
        wordTextFirst = (TextView) findViewById(R.id.word_number_first);
        wordTextSecond = (TextView) findViewById(R.id.word_number_second);

        checkMark1 = (ImageView) findViewById(R.id.check_mark_1);
        checkMark2 = (ImageView) findViewById(R.id.check_mark_2);

//        wordEditFirst.setOnFocusChangeListener(new FocusListener());
//        wordEditSecond.setOnFocusChangeListener(new FocusListener());

        wordEditFirst.addTextChangedListener(new BRTextWatcher());
        wordEditSecond.addTextChangedListener(new BRTextWatcher());

        constraintLayout = (ConstraintLayout) findViewById(R.id.constraintLayout);
        resetConstraintSet.clone(constraintLayout);
        applyConstraintSet.clone(constraintLayout);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {

                TransitionManager.beginDelayedTransition(constraintLayout);
                applyConstraintSet.setMargin(R.id.word_number_first, ConstraintSet.TOP, 8);
                applyConstraintSet.setMargin(R.id.line1, ConstraintSet.TOP, 16);
                applyConstraintSet.setMargin(R.id.line2, ConstraintSet.TOP, 16);
                applyConstraintSet.setMargin(R.id.word_number_second, ConstraintSet.TOP, 8);
                applyConstraintSet.applyTo(constraintLayout);


            }
        }, 500);


        wordEditSecond.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.submit || id == EditorInfo.IME_NULL) {
                    submit.performClick();
                    return true;
                }
                return false;
            }
        });


        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!BRAnimator.isClickAllowed()) return;

                BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
                    @Override
                    public void run() {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(app, app.getString(R.string.Biblepay_ConnectingBOINC), Toast.LENGTH_LONG).show();
                            }
                        }, 1000);
                    }
                });

                try {
                    String ErrorMsg = "";
                    String Email = wordEditFirst.getText().toString();
                    String Password = wordEditSecond.getText().toString();
                    byte[] md5 = CryptoHelper.md5((Email + Password).getBytes());
                    String PasswordHash = Utils.bytesToHex(md5);

                    // get BOINC authenticator
                    String urlAuth = String.format("https://%s/rosetta/lookup_account.php?email_addr=%s&passwd_hash=%s&get_opaque_auth=1", BiblePayApp.HOST_BOINC, Email, PasswordHash);
                    String xmlAuth = BRApiManager.urlGET(app, urlAuth);
                    String Authenticator = getTagValue(xmlAuth, "authenticator");
                    if (Authenticator=="")  {
                        ErrorMsg = getTagValue(xmlAuth, "error_msg");
                        throw new Exception("Authenticator: "+ErrorMsg);
                    }

                    // get BOINC researcher ID
                    String urlRID = String.format("https://%s/rosetta/am_get_info.php?account_key=%s", BiblePayApp.HOST_BOINC, Authenticator);
                    String xmlRID = BRApiManager.urlGET(app, urlRID);
                    String rID = getTagValue(xmlRID, "id");
                    if (rID=="")  {
                        ErrorMsg = getTagValue(xmlAuth, "error_msg");
                        throw new Exception("Researcher ID: "+ErrorMsg);
                    }

                       // get hexCode and CPID
                    String[] arr = GetBoincResearcherHexCodeAndCPID(rID);
                    String hexKey = arr[0];
                    String CPID = arr[1];

                    if ( CPID != "" ) {
                        Utils.hideKeyboard(AssociateBOINCActivity.this);
                        BRSharedPrefs.putPoDCCPID(AssociateBOINCActivity.this, CPID);

                        final Context ctx = BiblePayApp.getBreadContext();
                        final BaseWalletManager wallet = WalletsMaster.getInstance(ctx).getCurrentWallet(ctx);

                        String mReceiveAddress = BRSharedPrefs.getReceiveAddress(ctx, wallet.getIso(ctx));
                        String decorated = wallet.decorateAddress(ctx, mReceiveAddress);

                        // set BOINC Address
                        String urlSetAddr = String.format("https://%s/rosetta/am_set_info.php?account_key=%s&url=%s", BiblePayApp.HOST_BOINC, Authenticator, decorated);
                        String xmlSetAddr = BRApiManager.urlGET(app, urlSetAddr);
                        String hexCode = getTagValue(xmlRID, "url");

                        String[] arr2 = GetBoincResearcherHexCodeAndCPID(rID);
                        String hexKey2 = arr[0];
                        if ( hexKey!=hexKey2 )  {
                            throw new Exception("BOINC address: unable to set.");
                        }

                        // create burn TX (run as forced mode)
                        String Data = CPID + ";" + hexKey + ";" + decorated + ";" + rID + ";;1" ;       // unbanked always
                        BRSharedPrefs.putLastAdvertisedDCC(AssociateBOINCActivity.this, wallet.getPeerManager().getLastBlockHeight() );     // not needed in "forced" mode anyway but just for the future...

                        wallet.getPeerManager().getLastBlockHeight()
                        // Advertise to blockchain


                    } else {
                       ErrorMsg = getTagValue(xmlAuth, "error_msg");
                        throw new Exception("CPID: "+ErrorMsg);
                    }
                }
                catch (Exception e)
                {
                    BRAnimator.showBreadSignal(AssociateBOINCActivity.this, getString(R.string.Biblepay_Associate), getString(R.string.Biblepay_AssociateError)+": "+e.getMessage(), R.drawable.ic_fingerprint_error, new BROnSignalCompletion() {
                        @Override
                        public void onComplete() {
                            BRAnimator.startBreadActivity(AssociateBOINCActivity.this, false);
                            overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
                            finishAffinity();
                        }
                    });

                }
            }
        });
        String cleanPhrase = null;

        cleanPhrase = getIntent().getExtras() == null ? null : getIntent().getStringExtra("phrase");

        if (Utils.isNullOrEmpty(cleanPhrase)) {
            throw new RuntimeException(TAG + ": cleanPhrase is null");
        }

        String wordArray[] = cleanPhrase.split(" ");

        if (wordArray.length == 12 && cleanPhrase.charAt(cleanPhrase.length() - 1) == '\0') {
            BRDialog.showCustomDialog(this, getString(R.string.JailbreakWarnings_title),
                    getString(R.string.Alert_keystore_generic_android), getString(R.string.Button_ok), null, new BRDialogView.BROnClickListener() {
                        @Override
                        public void onClick(BRDialogView brDialogView) {
                            brDialogView.dismissWithAnimation();
                        }
                    }, null, null, 0);
            BRReportsManager.reportBug(new IllegalArgumentException("Paper Key error, please contact support at breadwallet.com"), false);
        } else {
            randomWordsSetUp(wordArray);

        }

    }

    public static String[] GetBoincResearcherHexCodeAndCPID(String rID )
    {
        String urlCPID = String.format("https://%s/rosetta/show_user.php?userid=%s&format=xml", BiblePayApp.HOST_BOINC, rID);
        String xmlCPID = BRApiManager.urlGET(app, urlCPID);
        String hexKey = getTagValue(xmlCPID, "url");
        hexKey.replace("http://", "").replace("https://", "");
        String CPID = getTagValue(xmlCPID, "url");

        String [] ret = new String[2];
        ret[0] = hexKey;
        ret[1] = CPID;
        return ret;
    }

    public static String getTagValue(String xml, String tagName){
        return xml.split("<"+tagName+">")[1].split("</"+tagName+">")[0];
    }

    @Override
    protected void onResume() {
        super.onResume();
        appVisible = true;
        app = this;
    }

    @Override
    protected void onPause() {
        super.onPause();
        appVisible = false;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.enter_from_left, R.anim.exit_to_right);
    }

    private void randomWordsSetUp(String[] words) {
        final Random random = new Random();
        int n = random.nextInt(10) + 1;

        sparseArrayWords.append(n, words[n]);

        while (sparseArrayWords.get(n) != null) {
            n = random.nextInt(10) + 1;
        }

        sparseArrayWords.append(n, words[n]);

        wordTextFirst.setText(String.format(Locale.getDefault(), getString(R.string.ConfirmPaperPhrase_word), (sparseArrayWords.keyAt(0) + 1)));
        wordTextSecond.setText(String.format(Locale.getDefault(), getString(R.string.ConfirmPaperPhrase_word), (sparseArrayWords.keyAt(1) + 1)));

    }

    private boolean isWordCorrect(boolean first) {
        if (first) {
            return (wordEditFirst.getText().toString()!="");
        } else {
            return (wordEditSecond.getText().toString()!="");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
    }

//    private class FocusListener implements View.OnFocusChangeListener {
//
//        @Override
//        public void onFocusChange(View v, boolean hasFocus) {
//            if (!hasFocus) {
//                validateWord((EditText) v);
//            } else {
//                ((EditText) v).setTextColor(getColor(R.color.light_gray));
//            }
//        }
//    }
/*
    private void validateWord(EditText view) {
        String word = view.getText().toString();
        boolean valid = SmartValidator.isWordValid(this, word);
        view.setTextColor(getColor(valid ? R.color.light_gray : R.color.red_text));
//        if (!valid)
//            SpringAnimator.failShakeAnimation(this, view);
        if (isWordCorrect(true)) {
            checkMark1.setVisibility(View.VISIBLE);
        } else {
            checkMark1.setVisibility(View.INVISIBLE);
        }

        if (isWordCorrect(false)) {
            checkMark2.setVisibility(View.VISIBLE);
        } else {
            checkMark2.setVisibility(View.INVISIBLE);
        }
    }
*/
    private class BRTextWatcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
//            validateWord(wordEditFirst);
//            validateWord(wordEditSecond);

        }

        @Override
        public void afterTextChanged(Editable s) {

        }
    }


}
