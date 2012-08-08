
package com.kmail.activity;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.kmail.R;
import com.kmail.Account;
import com.kmail.AccountStats;
import com.kmail.BaseAccount;
import com.kmail.FontSizes;
import com.kmail.K9;
import com.kmail.Preferences;
import com.kmail.SearchAccount;
import com.kmail.SearchSpecification;
import com.kmail.activity.misc.ExtendedAsyncTask;
import com.kmail.activity.misc.NonConfigurationInstance;
import com.kmail.activity.setup.AccountSettings;
import com.kmail.activity.setup.AccountSetupBasics;
import com.kmail.activity.setup.Prefs;
import com.kmail.controller.MessagingController;
import com.kmail.controller.MessagingListener;
import com.kmail.helper.SizeFormatter;
import com.kmail.mail.Flag;
import com.kmail.mail.ServerSettings;
import com.kmail.mail.Store;
import com.kmail.mail.Transport;
import com.kmail.mail.internet.MimeUtility;
import com.kmail.mail.store.StorageManager;
import com.kmail.mail.store.WebDavStore;
import com.kmail.preferences.SettingsExporter;
import com.kmail.preferences.SettingsImportExportException;
import com.kmail.preferences.SettingsImporter;
import com.kmail.preferences.SettingsImporter.AccountDescription;
import com.kmail.preferences.SettingsImporter.AccountDescriptionPair;
import com.kmail.preferences.SettingsImporter.ImportContents;
import com.kmail.preferences.SettingsImporter.ImportResults;
import com.kmail.view.ColorChip;


public class Accounts extends K9ListActivity implements OnItemClickListener, OnClickListener {

    /**
     * Immutable empty {@link BaseAccount} array
     */
    private static final BaseAccount[] EMPTY_BASE_ACCOUNT_ARRAY = new BaseAccount[0];

    /**
     * Immutable empty {@link Flag} array
     */
    private static final Flag[] EMPTY_FLAG_ARRAY = new Flag[0];

    /**
     * URL used to open Android Market application
     */
    private static final String ANDROID_MARKET_URL = "https://market.android.com/search?q=oi+file+manager&c=apps";

    /**
     * Number of special accounts ('Unified Inbox' and 'All Messages')
     */
    private static final int SPECIAL_ACCOUNTS_COUNT = 2;

    private static final int DIALOG_REMOVE_ACCOUNT = 1;
    private static final int DIALOG_CLEAR_ACCOUNT = 2;
    private static final int DIALOG_RECREATE_ACCOUNT = 3;
    private static final int DIALOG_NO_FILE_MANAGER = 4;

    private ConcurrentHashMap<String, AccountStats> accountStats = new ConcurrentHashMap<String, AccountStats>();

    private ConcurrentHashMap<BaseAccount, String> pendingWork = new ConcurrentHashMap<BaseAccount, String>();

    private BaseAccount mSelectedContextAccount;
    private int mUnreadMessageCount = 0;

    private AccountsHandler mHandler = new AccountsHandler();
    private AccountsAdapter mAdapter;
    private SearchAccount unreadAccount = null;
    private SearchAccount integratedInboxAccount = null;
    private FontSizes mFontSizes = K9.getFontSizes();

    /**
     * Contains information about objects that need to be retained on configuration changes.
     *
     * @see #onRetainNonConfigurationInstance()
     */
    private NonConfigurationInstance mNonConfigurationInstance;


    private static final int ACTIVITY_REQUEST_PICK_SETTINGS_FILE = 1;

    class AccountsHandler extends Handler {
        private void setViewTitle() {
            String dispString = mListener.formatHeader(Accounts.this, getString(R.string.accounts_title), mUnreadMessageCount, getTimeFormat());

            setTitle(dispString);
        }
        public void refreshTitle() {
            runOnUiThread(new Runnable() {
                public void run() {
                    setViewTitle();
                }
            });
        }

        public void dataChanged() {
            runOnUiThread(new Runnable() {
                public void run() {
                    if (mAdapter != null) {
                        mAdapter.notifyDataSetChanged();
                    }
                }
            });
        }

        public void workingAccount(final Account account, final int res) {
            runOnUiThread(new Runnable() {
                public void run() {
                    String toastText = getString(res, account.getDescription());

                    Toast toast = Toast.makeText(getApplication(), toastText, Toast.LENGTH_SHORT);
                    toast.show();
                }
            });
        }

        public void accountSizeChanged(final Account account, final long oldSize, final long newSize) {
            runOnUiThread(new Runnable() {
                public void run() {
                    AccountStats stats = accountStats.get(account.getUuid());
                    if (newSize != -1 && stats != null && K9.measureAccounts()) {
                        stats.size = newSize;
                    }
                    String toastText = getString(R.string.account_size_changed, account.getDescription(),
                                                 SizeFormatter.formatSize(getApplication(), oldSize), SizeFormatter.formatSize(getApplication(), newSize));

                    Toast toast = Toast.makeText(getApplication(), toastText, Toast.LENGTH_LONG);
                    toast.show();
                    if (mAdapter != null) {
                        mAdapter.notifyDataSetChanged();
                    }
                }
            });
        }

        public void progress(final boolean progress) {
            runOnUiThread(new Runnable() {
                public void run() {
                    setProgressBarIndeterminateVisibility(progress);
                }
            });
        }
        public void progress(final int progress) {
            runOnUiThread(new Runnable() {
                public void run() {
                    getWindow().setFeatureInt(Window.FEATURE_PROGRESS, progress);
                }
            });
        }
    }

    public void setProgress(boolean progress) {
        mHandler.progress(progress);
    }

    ActivityListener mListener = new ActivityListener() {
        @Override
        public void informUserOfStatus() {
            mHandler.refreshTitle();
        }

        @Override
        public void folderStatusChanged(Account account, String folderName, int unreadMessageCount) {
            try {
                AccountStats stats = account.getStats(Accounts.this);
                if (stats == null) {
                    Log.w(K9.LOG_TAG, "Unable to get account stats");
                } else {
                    accountStatusChanged(account, stats);
                }
            } catch (Exception e) {
                Log.e(K9.LOG_TAG, "Unable to get account stats", e);
            }
        }
        @Override
        public void accountStatusChanged(BaseAccount account, AccountStats stats) {
            AccountStats oldStats = accountStats.get(account.getUuid());
            int oldUnreadMessageCount = 0;
            if (oldStats != null) {
                oldUnreadMessageCount = oldStats.unreadMessageCount;
            }
            if (stats == null) {
                stats = new AccountStats(); // empty stats for unavailable accounts
                stats.available = false;
            }
            accountStats.put(account.getUuid(), stats);
            if (account instanceof Account) {
                mUnreadMessageCount += stats.unreadMessageCount - oldUnreadMessageCount;
            }
            mHandler.dataChanged();
            pendingWork.remove(account);

            if (pendingWork.isEmpty()) {
                mHandler.progress(Window.PROGRESS_END);
                mHandler.refreshTitle();
            } else {
                int level = (Window.PROGRESS_END / mAdapter.getCount()) * (mAdapter.getCount() - pendingWork.size()) ;
                mHandler.progress(level);
            }
        }

        @Override
        public void accountSizeChanged(Account account, long oldSize, long newSize) {
            mHandler.accountSizeChanged(account, oldSize, newSize);
        }

        @Override
        public void synchronizeMailboxFinished(
            Account account,
            String folder,
            int totalMessagesInMailbox,
        int numNewMessages) {
            MessagingController.getInstance(getApplication()).getAccountStats(Accounts.this, account, mListener);
            super.synchronizeMailboxFinished(account, folder, totalMessagesInMailbox, numNewMessages);

            mHandler.progress(false);

        }

        @Override
        public void synchronizeMailboxStarted(Account account, String folder) {
            super.synchronizeMailboxStarted(account, folder);
            mHandler.progress(true);
        }

        @Override
        public void synchronizeMailboxFailed(Account account, String folder,
        String message) {
            super.synchronizeMailboxFailed(account, folder, message);
            mHandler.progress(false);

        }

    };

    private static String ACCOUNT_STATS = "accountStats";
    private static String SELECTED_CONTEXT_ACCOUNT = "selectedContextAccount";

    public static final String EXTRA_STARTUP = "startup";


    public static void actionLaunch(Context context) {
        Intent intent = new Intent(context, Accounts.class);
        intent.putExtra(EXTRA_STARTUP, true);
        context.startActivity(intent);
    }

    public static void listAccounts(Context context) {
        Intent intent = new Intent(context, Accounts.class);
        intent.putExtra(EXTRA_STARTUP, false);
        context.startActivity(intent);
    }

    @Override
    public void onNewIntent(Intent intent) {
        Uri uri = intent.getData();
        Log.i(K9.LOG_TAG, "Accounts Activity got uri " + uri);
        if (uri != null) {
            ContentResolver contentResolver = getContentResolver();

            Log.i(K9.LOG_TAG, "Accounts Activity got content of type " + contentResolver.getType(uri));

            String contentType = contentResolver.getType(uri);
            if (MimeUtility.K9_SETTINGS_MIME_TYPE.equals(contentType)) {
                onImport(uri);
            }
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (!K9.isHideSpecialAccounts()) {
            createSpecialAccounts();
        }

        Account[] accounts = Preferences.getPreferences(this).getAccounts();
        Intent intent = getIntent();
        //onNewIntent(intent);

        boolean startup = intent.getBooleanExtra(EXTRA_STARTUP, true);
        if (startup && K9.startIntegratedInbox() && !K9.isHideSpecialAccounts()) {
            onOpenAccount(integratedInboxAccount);
            finish();
            return;
        } else if (startup && accounts.length == 1 && onOpenAccount(accounts[0])) {
            finish();
            return;
        }

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);

        setContentView(R.layout.accounts);
        ListView listView = getListView();
        listView.setOnItemClickListener(this);
        listView.setItemsCanFocus(false);
        listView.setEmptyView(findViewById(R.id.empty));
        listView.setScrollingCacheEnabled(false);
        findViewById(R.id.next).setOnClickListener(this);
        registerForContextMenu(listView);

        if (icicle != null && icicle.containsKey(SELECTED_CONTEXT_ACCOUNT)) {
            String accountUuid = icicle.getString("selectedContextAccount");
            mSelectedContextAccount = Preferences.getPreferences(this).getAccount(accountUuid);
        }

        restoreAccountStats(icicle);

        // Handle activity restarts because of a configuration change (e.g. rotating the screen)
        mNonConfigurationInstance = (NonConfigurationInstance) getLastNonConfigurationInstance();
        if (mNonConfigurationInstance != null) {
            mNonConfigurationInstance.restore(this);
        }
    }

    /**
     * Creates and initializes the special accounts ('Unified Inbox' and 'All Messages')
     */
    private void createSpecialAccounts() {
        integratedInboxAccount = SearchAccount.createUnifiedInboxAccount(this);
        unreadAccount = SearchAccount.createAllMessagesAccount(this);
    }

    @SuppressWarnings("unchecked")
    private void restoreAccountStats(Bundle icicle) {
        if (icicle != null) {
            Map<String, AccountStats> oldStats = (Map<String, AccountStats>)icicle.get(ACCOUNT_STATS);
            if (oldStats != null) {
                accountStats.putAll(oldStats);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSelectedContextAccount != null) {
            outState.putString(SELECTED_CONTEXT_ACCOUNT, mSelectedContextAccount.getUuid());
        }
        outState.putSerializable(ACCOUNT_STATS, accountStats);
    }

    private StorageManager.StorageListener storageListener = new StorageManager.StorageListener() {

        @Override
        public void onUnmount(String providerId) {
            refresh();
        }

        @Override
        public void onMount(String providerId) {
            refresh();
        }
    };

    @Override
    public void onResume() {
        super.onResume();

        refresh();
        MessagingController.getInstance(getApplication()).addListener(mListener);
        StorageManager.getInstance(getApplication()).addListener(storageListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        MessagingController.getInstance(getApplication()).removeListener(mListener);
        StorageManager.getInstance(getApplication()).removeListener(storageListener);
    }

    /**
     * Save the reference to a currently displayed dialog or a running AsyncTask (if available).
     */
    @Override
    public Object onRetainNonConfigurationInstance() {
        Object retain = null;
        if (mNonConfigurationInstance != null && mNonConfigurationInstance.retain()) {
            retain = mNonConfigurationInstance;
        }
        return retain;
    }

    private BaseAccount[] accounts = new BaseAccount[0];
    private enum ACCOUNT_LOCATION {
        TOP, MIDDLE, BOTTOM;
    }
    private EnumSet<ACCOUNT_LOCATION> accountLocation(BaseAccount account) {
        EnumSet<ACCOUNT_LOCATION> accountLocation = EnumSet.of(ACCOUNT_LOCATION.MIDDLE);
        if (accounts.length > 0) {
            if (accounts[0].equals(account)) {
                accountLocation.remove(ACCOUNT_LOCATION.MIDDLE);
                accountLocation.add(ACCOUNT_LOCATION.TOP);
            }
            if (accounts[accounts.length - 1].equals(account)) {
                accountLocation.remove(ACCOUNT_LOCATION.MIDDLE);
                accountLocation.add(ACCOUNT_LOCATION.BOTTOM);
            }
        }
        return accountLocation;
    }


    private void refresh() {
        accounts = Preferences.getPreferences(this).getAccounts();

        List<BaseAccount> newAccounts;
        if (!K9.isHideSpecialAccounts() && accounts.length > 0) {
            if (integratedInboxAccount == null || unreadAccount == null) {
                createSpecialAccounts();
            }

            newAccounts = new ArrayList<BaseAccount>(accounts.length +
                    SPECIAL_ACCOUNTS_COUNT);
            newAccounts.add(integratedInboxAccount);
            newAccounts.add(unreadAccount);
        } else {
            newAccounts = new ArrayList<BaseAccount>(accounts.length);
        }

        newAccounts.addAll(Arrays.asList(accounts));

        mAdapter = new AccountsAdapter(newAccounts.toArray(EMPTY_BASE_ACCOUNT_ARRAY));
        getListView().setAdapter(mAdapter);
        if (!newAccounts.isEmpty()) {
            mHandler.progress(Window.PROGRESS_START);
        }
        pendingWork.clear();

        for (BaseAccount account : newAccounts) {

            if (account instanceof Account) {
                pendingWork.put(account, "true");
                Account realAccount = (Account)account;
                MessagingController.getInstance(getApplication()).getAccountStats(Accounts.this, realAccount, mListener);
            } else if (K9.countSearchMessages() && account instanceof SearchAccount) {
                pendingWork.put(account, "true");
                final SearchAccount searchAccount = (SearchAccount)account;

                MessagingController.getInstance(getApplication()).searchLocalMessages(searchAccount, null, new MessagingListener() {
                    @Override
                    public void searchStats(AccountStats stats) {
                        mListener.accountStatusChanged(searchAccount, stats);
                    }
                });
            }
        }

    }

    private void onAddNewAccount() {
        AccountSetupBasics.actionNewAccount(this);
    }

    private void onEditAccount(Account account) {
        AccountSettings.actionSettings(this, account);
    }

    private void onEditPrefs() {
        Prefs.actionPrefs(this);
    }


    /*
     * This method is called with 'null' for the argument 'account' if
     * all accounts are to be checked. This is handled accordingly in
     * MessagingController.checkMail().
     */
    private void onCheckMail(Account account) {
        MessagingController.getInstance(getApplication()).checkMail(this, account, true, true, null);
        if (account == null) {
            MessagingController.getInstance(getApplication()).sendPendingMessages(null);
        } else {
            MessagingController.getInstance(getApplication()).sendPendingMessages(account, null);
        }

    }

    private void onClearCommands(Account account) {
        MessagingController.getInstance(getApplication()).clearAllPending(account);
    }

    private void onEmptyTrash(Account account) {
        MessagingController.getInstance(getApplication()).emptyTrash(account, null);
    }


    private void onCompose() {
        Account defaultAccount = Preferences.getPreferences(this).getDefaultAccount();
        if (defaultAccount != null) {
            MessageCompose.actionCompose(this, defaultAccount);
        } else {
            onAddNewAccount();
        }
    }

    /**
     * Show that account's inbox or folder-list
     * or return false if the account is not available.
     * @param account the account to open ({@link SearchAccount} or {@link Account})
     * @return false if unsuccessfull
     */
    private boolean onOpenAccount(BaseAccount account) {
        if (account instanceof SearchAccount) {
            SearchAccount searchAccount = (SearchAccount)account;
            MessageList.actionHandle(this, searchAccount.getDescription(), searchAccount);
        } else {
            Account realAccount = (Account)account;
            if (!realAccount.isEnabled()) {
                onActivateAccount(realAccount);
                return false;
            } else if (!realAccount.isAvailable(this)) {
                String toastText = getString(R.string.account_unavailable, account.getDescription());
                Toast toast = Toast.makeText(getApplication(), toastText, Toast.LENGTH_SHORT);
                toast.show();

                Log.i(K9.LOG_TAG, "refusing to open account that is not available");
                return false;
            }
            if (K9.FOLDER_NONE.equals(realAccount.getAutoExpandFolderName())) {
                FolderList.actionHandleAccount(this, realAccount);
            } else {
                MessageList.actionHandleFolder(this, realAccount, realAccount.getAutoExpandFolderName());
            }
        }
        return true;
    }

    private void onActivateAccount(Account account) {
        List<Account> disabledAccounts = new ArrayList<Account>();
        disabledAccounts.add(account);
        promptForServerPasswords(disabledAccounts);
    }

    /**
     * Ask the user to enter the server passwords for disabled accounts.
     *
     * @param disabledAccounts
     *         A non-empty list of {@link Account}s to ask the user for passwords. Never
     *         {@code null}.
     *         <p><strong>Note:</strong> Calling this method will modify the supplied list.</p>
     */
    private void promptForServerPasswords(final List<Account> disabledAccounts) {
        Account account = disabledAccounts.remove(0);
        PasswordPromptDialog dialog = new PasswordPromptDialog(account, disabledAccounts);
        setNonConfigurationInstance(dialog);
        dialog.show(this);
    }

    /**
     * Ask the user for the incoming/outgoing server passwords.
     */
    private static class PasswordPromptDialog implements NonConfigurationInstance, TextWatcher {
        private AlertDialog mDialog;
        private EditText mIncomingPasswordView;
        private EditText mOutgoingPasswordView;
        private CheckBox mUseIncomingView;

        private Account mAccount;
        private List<Account> mRemainingAccounts;
        private String mIncomingPassword;
        private String mOutgoingPassword;
        private boolean mUseIncoming;

        /**
         * Constructor
         *
         * @param account
         *         The {@link Account} to ask the server passwords for. Never {@code null}.
         * @param accounts
         *         The (possibly empty) list of remaining accounts to ask passwords for. Never
         *         {@code null}.
         */
        PasswordPromptDialog(Account account, List<Account> accounts) {
            mAccount = account;
            mRemainingAccounts = accounts;
        }

        @Override
        public void restore(Activity activity) {
            show((Accounts) activity, true);
        }

        @Override
        public boolean retain() {
            if (mDialog != null) {
                // Retain entered passwords and checkbox state
                mIncomingPassword = mIncomingPasswordView.getText().toString();
                if (mOutgoingPasswordView != null) {
                    mOutgoingPassword = mOutgoingPasswordView.getText().toString();
                    mUseIncoming = mUseIncomingView.isChecked();
                }

                // Dismiss dialog
                mDialog.dismiss();

                // Clear all references to UI objects
                mDialog = null;
                mIncomingPasswordView = null;
                mOutgoingPasswordView = null;
                mUseIncomingView = null;
                return true;
            }
            return false;
        }

        public void show(Accounts activity) {
            show(activity, false);
        }

        private void show(final Accounts activity, boolean restore) {
            ServerSettings incoming = Store.decodeStoreUri(mAccount.getStoreUri());
            ServerSettings outgoing = Transport.decodeTransportUri(mAccount.getTransportUri());

            // Don't ask for the password to the outgoing server for WebDAV accounts, because
            // incoming and outgoing servers are identical for this account type.
            boolean configureOutgoingServer = !WebDavStore.STORE_TYPE.equals(outgoing.type);

            // Create a ScrollView that will be used as container for the whole layout
            final ScrollView scrollView = new ScrollView(activity);

            // Create the dialog
            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(activity.getString(R.string.settings_import_activate_account_header));
            builder.setView(scrollView);
            builder.setPositiveButton(activity.getString(R.string.okay_action),
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String incomingPassword = mIncomingPasswordView.getText().toString();
                    String outgoingPassword = null;
                    if (mOutgoingPasswordView != null) {
                        outgoingPassword = (mUseIncomingView.isChecked()) ?
                                incomingPassword : mOutgoingPasswordView.getText().toString();
                    }

                    dialog.dismiss();

                    // Set the server passwords in the background
                    SetPasswordsAsyncTask asyncTask = new SetPasswordsAsyncTask(activity, mAccount,
                            incomingPassword, outgoingPassword, mRemainingAccounts);
                    activity.setNonConfigurationInstance(asyncTask);
                    asyncTask.execute();
                }
            });
            builder.setNegativeButton(activity.getString(R.string.cancel_action),
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    activity.setNonConfigurationInstance(null);
                }
            });
            mDialog = builder.create();

            // Use the dialog's layout inflater so its theme is used (and not the activity's theme).
            View layout = mDialog.getLayoutInflater().inflate(
                    R.layout.accounts_password_prompt, null);

            // Set the intro text that tells the user what to do
            TextView intro = (TextView) layout.findViewById(R.id.password_prompt_intro);
            String serverPasswords = activity.getResources().getQuantityString(
                    R.plurals.settings_import_server_passwords,
                    (configureOutgoingServer) ? 2 : 1);
            intro.setText(activity.getString(R.string.settings_import_activate_account_intro,
                    mAccount.getDescription(), serverPasswords));

            // Display the hostname of the incoming server
            TextView incomingText = (TextView) layout.findViewById(
                    R.id.password_prompt_incoming_server);
            incomingText.setText(activity.getString(R.string.settings_import_incoming_server,
                    incoming.host));

            mIncomingPasswordView = (EditText) layout.findViewById(R.id.incoming_server_password);
            mIncomingPasswordView.addTextChangedListener(this);

            if (configureOutgoingServer) {
                // Display the hostname of the outgoing server
                TextView outgoingText = (TextView) layout.findViewById(
                        R.id.password_prompt_outgoing_server);
                outgoingText.setText(activity.getString(R.string.settings_import_outgoing_server,
                        outgoing.host));

                mOutgoingPasswordView = (EditText) layout.findViewById(
                        R.id.outgoing_server_password);
                mOutgoingPasswordView.addTextChangedListener(this);

                mUseIncomingView = (CheckBox) layout.findViewById(
                        R.id.use_incoming_server_password);
                mUseIncomingView.setChecked(true);
                mUseIncomingView.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            mOutgoingPasswordView.setText(null);
                            mOutgoingPasswordView.setEnabled(false);
                        } else {
                            mOutgoingPasswordView.setText(mIncomingPasswordView.getText());
                            mOutgoingPasswordView.setEnabled(true);
                        }
                    }
                });
            } else {
                layout.findViewById(R.id.outgoing_server_prompt).setVisibility(View.GONE);
            }

            // Add the layout to the ScrollView
            scrollView.addView(layout);

            // Show the dialog
            mDialog.show();

            // Restore the contents of the password boxes and the checkbox (if the dialog was
            // retained during a configuration change).
            if (restore) {
                mIncomingPasswordView.setText(mIncomingPassword);
                if (configureOutgoingServer) {
                    mOutgoingPasswordView.setText(mOutgoingPassword);
                    mUseIncomingView.setChecked(mUseIncoming);
                }
            } else {
                // Trigger afterTextChanged() being called
                // Work around this bug: https://code.google.com/p/android/issues/detail?id=6360
                mIncomingPasswordView.setText(mIncomingPasswordView.getText());
            }
        }

        @Override
        public void afterTextChanged(Editable arg0) {
            boolean enable = false;
            // Is the password box for the incoming server password empty?
            if (mIncomingPasswordView.getText().length() > 0) {
                // Do we need to check the outgoing server password box?
                if (mOutgoingPasswordView == null) {
                    enable = true;
                }
                // If the checkbox to use the incoming server password is checked we need to make
                // sure that the password box for the outgoing server isn't empty.
                else if (mUseIncomingView.isChecked() ||
                        mOutgoingPasswordView.getText().length() > 0) {
                    enable = true;
                }
            }

            // Disable "OK" button if the user hasn't specified all necessary passwords.
            mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(enable);
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // Not used
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // Not used
        }
    }

    /**
     * Set the incoming/outgoing server password in the background.
     */
    private static class SetPasswordsAsyncTask extends ExtendedAsyncTask<Void, Void, Void> {
        private Account mAccount;
        private String mIncomingPassword;
        private String mOutgoingPassword;
        private List<Account> mRemainingAccounts;
        private Application mApplication;

        protected SetPasswordsAsyncTask(Activity activity, Account account,
                String incomingPassword, String outgoingPassword,
                List<Account> remainingAccounts) {
            super(activity);
            mAccount = account;
            mIncomingPassword = incomingPassword;
            mOutgoingPassword = outgoingPassword;
            mRemainingAccounts = remainingAccounts;
            mApplication = mActivity.getApplication();
        }

        @Override
        protected void showProgressDialog() {
            String title = mActivity.getString(R.string.settings_import_activate_account_header);
            int passwordCount = (mOutgoingPassword == null) ? 1 : 2;
            String message = mActivity.getResources().getQuantityString(
                    R.plurals.settings_import_setting_passwords, passwordCount);
            mProgressDialog = ProgressDialog.show(mActivity, title, message, true);
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                // Set incoming server password
                String storeUri = mAccount.getStoreUri();
                ServerSettings incoming = Store.decodeStoreUri(storeUri);
                ServerSettings newIncoming = incoming.newPassword(mIncomingPassword);
                String newStoreUri = Store.createStoreUri(newIncoming);
                mAccount.setStoreUri(newStoreUri);

                if (mOutgoingPassword != null) {
                    // Set outgoing server password
                    String transportUri = mAccount.getTransportUri();
                    ServerSettings outgoing = Transport.decodeTransportUri(transportUri);
                    ServerSettings newOutgoing = outgoing.newPassword(mOutgoingPassword);
                    String newTransportUri = Transport.createTransportUri(newOutgoing);
                    mAccount.setTransportUri(newTransportUri);
                }

                // Mark account as enabled
                mAccount.setEnabled(true);

                // Save the account settings
                mAccount.save(Preferences.getPreferences(mContext));

                // Start services if necessary
                K9.setServicesEnabled(mContext);

                // Get list of folders from remote server
                MessagingController.getInstance(mApplication).listFolders(mAccount, true, null);
            } catch (Exception e) {
                Log.e(K9.LOG_TAG, "Something went while setting account passwords", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            Accounts activity = (Accounts) mActivity;

            // Let the activity know that the background task is complete
            activity.setNonConfigurationInstance(null);

            activity.refresh();
            removeProgressDialog();

            if (mRemainingAccounts.size() > 0) {
                activity.promptForServerPasswords(mRemainingAccounts);
            }
        }
    }

    public void onClick(View view) {
        if (view.getId() == R.id.next) {
            onAddNewAccount();
        }
    }

    private void onDeleteAccount(Account account) {
        mSelectedContextAccount = account;
        showDialog(DIALOG_REMOVE_ACCOUNT);
    }

    @Override
    public Dialog onCreateDialog(int id) {
        // Android recreates our dialogs on configuration changes even when they have been
        // dismissed. Make sure we have all information necessary before creating a new dialog.
        switch (id) {
            case DIALOG_REMOVE_ACCOUNT: {
                if (mSelectedContextAccount == null) {
                    return null;
                }

                return ConfirmationDialog.create(this, id,
                        R.string.account_delete_dlg_title,
                        getString(R.string.account_delete_dlg_instructions_fmt,
                                mSelectedContextAccount.getDescription()),
                        R.string.okay_action,
                        R.string.cancel_action,
                        new Runnable() {
                            @Override
                            public void run() {
                                if (mSelectedContextAccount instanceof Account) {
                                    Account realAccount = (Account) mSelectedContextAccount;
                                    try {
                                        realAccount.getLocalStore().delete();
                                    } catch (Exception e) {
                                        // Ignore, this may lead to localStores on sd-cards that
                                        // are currently not inserted to be left
                                    }
                                    MessagingController.getInstance(getApplication())
                                            .notifyAccountCancel(Accounts.this, realAccount);
                                    Preferences.getPreferences(Accounts.this)
                                            .deleteAccount(realAccount);
                                    K9.setServicesEnabled(Accounts.this);
                                    refresh();
                                }
                            }
                        });
            }
            case DIALOG_CLEAR_ACCOUNT: {
                if (mSelectedContextAccount == null) {
                    return null;
                }

                return ConfirmationDialog.create(this, id,
                        R.string.account_clear_dlg_title,
                        getString(R.string.account_clear_dlg_instructions_fmt,
                                mSelectedContextAccount.getDescription()),
                        R.string.okay_action,
                        R.string.cancel_action,
                        new Runnable() {
                            @Override
                            public void run() {
                                if (mSelectedContextAccount instanceof Account) {
                                    Account realAccount = (Account) mSelectedContextAccount;
                                    mHandler.workingAccount(realAccount,
                                            R.string.clearing_account);
                                    MessagingController.getInstance(getApplication())
                                            .clear(realAccount, null);
                                }
                            }
                        });
            }
            case DIALOG_RECREATE_ACCOUNT: {
                if (mSelectedContextAccount == null) {
                    return null;
                }

                return ConfirmationDialog.create(this, id,
                        R.string.account_recreate_dlg_title,
                        getString(R.string.account_recreate_dlg_instructions_fmt,
                                mSelectedContextAccount.getDescription()),
                        R.string.okay_action,
                        R.string.cancel_action,
                        new Runnable() {
                            @Override
                            public void run() {
                                if (mSelectedContextAccount instanceof Account) {
                                    Account realAccount = (Account) mSelectedContextAccount;
                                    mHandler.workingAccount(realAccount,
                                            R.string.recreating_account);
                                    MessagingController.getInstance(getApplication())
                                            .recreate(realAccount, null);
                                }
                            }
                        });
            }
            case DIALOG_NO_FILE_MANAGER: {
                return ConfirmationDialog.create(this, id,
                        R.string.import_dialog_error_title,
                        getString(R.string.import_dialog_error_message),
                        R.string.open_market,
                        R.string.close,
                        new Runnable() {
                            @Override
                            public void run() {
                                Uri uri = Uri.parse(ANDROID_MARKET_URL);
                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                startActivity(intent);
                            }
                        });
            }
        }

        return super.onCreateDialog(id);
    }

    @Override
    public void onPrepareDialog(int id, Dialog d) {
        AlertDialog alert = (AlertDialog) d;
        switch (id) {
            case DIALOG_REMOVE_ACCOUNT: {
                alert.setMessage(getString(R.string.account_delete_dlg_instructions_fmt,
                        mSelectedContextAccount.getDescription()));
                break;
            }
            case DIALOG_CLEAR_ACCOUNT: {
                alert.setMessage(getString(R.string.account_clear_dlg_instructions_fmt,
                        mSelectedContextAccount.getDescription()));
                break;
            }
            case DIALOG_RECREATE_ACCOUNT: {
                alert.setMessage(getString(R.string.account_recreate_dlg_instructions_fmt,
                        mSelectedContextAccount.getDescription()));
                break;
            }
        }

        super.onPrepareDialog(id, d);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo)item.getMenuInfo();
        // submenus don't actually set the menuInfo, so the "advanced"
        // submenu wouldn't work.
        if (menuInfo != null) {
            mSelectedContextAccount = (BaseAccount)getListView().getItemAtPosition(menuInfo.position);
        }
        Account realAccount = null;
        if (mSelectedContextAccount instanceof Account) {
            realAccount = (Account)mSelectedContextAccount;
        }
        switch (item.getItemId()) {
        case R.id.delete_account:
            onDeleteAccount(realAccount);
            break;
        case R.id.edit_account:
            onEditAccount(realAccount);
            break;
        case R.id.open:
            onOpenAccount(mSelectedContextAccount);
            break;
        case R.id.activate:
            onActivateAccount(realAccount);
            break;
        case R.id.check_mail:
            onCheckMail(realAccount);
            break;
        case R.id.clear_pending:
            onClearCommands(realAccount);
            break;
        case R.id.empty_trash:
            onEmptyTrash(realAccount);
            break;
        case R.id.compact:
            onCompact(realAccount);
            break;
        case R.id.clear:
            onClear(realAccount);
            break;
        case R.id.recreate:
            onRecreate(realAccount);
            break;
        case R.id.export:
            onExport(false, realAccount);
            break;
        case R.id.move_up:
            onMove(realAccount, true);
            break;
        case R.id.move_down:
            onMove(realAccount, false);
            break;
        }
        return true;
    }



    private void onCompact(Account account) {
        mHandler.workingAccount(account, R.string.compacting_account);
        MessagingController.getInstance(getApplication()).compact(account, null);
    }

    private void onClear(Account account) {
        showDialog(DIALOG_CLEAR_ACCOUNT);

    }
    private void onRecreate(Account account) {
        showDialog(DIALOG_RECREATE_ACCOUNT);
    }
    private void onMove(final Account account, final boolean up) {
        MoveAccountAsyncTask asyncTask = new MoveAccountAsyncTask(this, account, up);
        setNonConfigurationInstance(asyncTask);
        asyncTask.execute();
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        BaseAccount account = (BaseAccount)parent.getItemAtPosition(position);
        onOpenAccount(account);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.add_new_account:
            onAddNewAccount();
            break;
        case R.id.edit_prefs:
            onEditPrefs();
            break;
        case R.id.check_mail:
            onCheckMail(null);
            break;
        case R.id.compose:
            onCompose();
            break;
        case R.id.about:
            onAbout();
            break;
        case R.id.search:
            onSearchRequested();
            break;
        case R.id.export_all:
            onExport(true, null);
            break;
        case R.id.import_settings:
            onImport();
            break;
        default:
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private static String[][] USED_LIBRARIES = new String[][] {
        new String[] {"jutf7", "http://jutf7.sourceforge.net/"},
        new String[] {"JZlib", "http://www.jcraft.com/jzlib/"},
        new String[] {"Commons IO", "http://commons.apache.org/io/"},
        new String[] {"Mime4j", "http://james.apache.org/mime4j/"},
        new String[] {"HtmlCleaner", "http://htmlcleaner.sourceforge.net/"},
    };

    private void onAbout() {
        String appName = getString(R.string.app_name);
        String year = "2012";
        WebView wv = new WebView(this);
        StringBuilder html = new StringBuilder()
        .append("<meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\" />")
        .append("<img src=\"file:///android_asset/icon.png\" alt=\"").append(appName).append("\"/>")
        .append("<h1>")
        .append(String.format(getString(R.string.about_title_fmt),
                              "<a href=\"" + getString(R.string.app_webpage_url)) + "\">")
        .append(appName)
        .append("</a>")
        .append("</h1><p>")
        .append(appName)
        .append(" ")
        .append(String.format(getString(R.string.debug_version_fmt), getVersionNumber()))
        .append("</p><p>")
        .append(String.format(getString(R.string.app_authors_fmt),
                              getString(R.string.app_authors)))
        .append("</p><p>")
        .append(String.format(getString(R.string.app_revision_fmt),
                              "<a href=\"" + getString(R.string.app_revision_url) + "\">" +
                              getString(R.string.app_revision_url) +
                              "</a>"))
        .append("</p><hr/><p>")
        .append(String.format(getString(R.string.app_copyright_fmt), year, year))
        .append("</p><hr/><p>")
        .append(getString(R.string.app_license))
        .append("</p><hr/><p>");

        StringBuilder libs = new StringBuilder().append("<ul>");
        for (String[] library : USED_LIBRARIES) {
            libs.append("<li><a href=\"").append(library[1]).append("\">").append(library[0]).append("</a></li>");
        }
        libs.append("</ul>");

        html.append(String.format(getString(R.string.app_libraries), libs.toString()))
        .append("</p><hr/><p>")
        .append(String.format(getString(R.string.app_emoji_icons),
                              "<div>TypePad \u7d75\u6587\u5b57\u30a2\u30a4\u30b3\u30f3\u753b\u50cf " +
                              "(<a href=\"http://typepad.jp/\">Six Apart Ltd</a>) / " +
                              "<a href=\"http://creativecommons.org/licenses/by/2.1/jp/\">CC BY 2.1</a></div>"))
        .append("</p><hr/><p>")
        .append(getString(R.string.app_htmlcleaner_license));


        wv.loadDataWithBaseURL("file:///android_res/drawable/", html.toString(), "text/html", "utf-8", null);
        new AlertDialog.Builder(this)
        .setView(wv)
        .setCancelable(true)
        .setPositiveButton(R.string.okay_action, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int c) {
                d.dismiss();
            }
        })
        .show();
    }

    /**
     * Get current version number.
     *
     * @return String version
     */
    private String getVersionNumber() {
        String version = "?";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            //Log.e(TAG, "Package name not found", e);
        }
        return version;
    }

    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.accounts_option, menu);
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.setHeaderTitle(R.string.accounts_context_menu_title);

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        BaseAccount account =  mAdapter.getItem(info.position);

        if ((account instanceof Account) && !((Account) account).isEnabled()) {
            getMenuInflater().inflate(R.menu.disabled_accounts_context, menu);
        } else {
            getMenuInflater().inflate(R.menu.accounts_context, menu);
        }

        if (account instanceof SearchAccount) {
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                if (item.getItemId() != R.id.open) {
                    item.setVisible(false);
                }
            }
        }
        else {
            EnumSet<ACCOUNT_LOCATION> accountLocation = accountLocation(account);
            if (accountLocation.contains(ACCOUNT_LOCATION.TOP)) {
                menu.findItem(R.id.move_up).setEnabled(false);
            }
            else {
                menu.findItem(R.id.move_up).setEnabled(true);
            }
            if (accountLocation.contains(ACCOUNT_LOCATION.BOTTOM)) {
                menu.findItem(R.id.move_down).setEnabled(false);
            }
            else {
                menu.findItem(R.id.move_down).setEnabled(true);
            }
        }
    }

    private void onImport() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(MimeUtility.K9_SETTINGS_MIME_TYPE);

        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> infos = packageManager.queryIntentActivities(i, 0);

        if (infos.size() > 0) {
            startActivityForResult(Intent.createChooser(i, null),
                    ACTIVITY_REQUEST_PICK_SETTINGS_FILE);
        } else {
            showDialog(DIALOG_NO_FILE_MANAGER);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(K9.LOG_TAG, "onActivityResult requestCode = " + requestCode + ", resultCode = " + resultCode + ", data = " + data);
        if (resultCode != RESULT_OK)
            return;
        if (data == null) {
            return;
        }
        switch (requestCode) {
        case ACTIVITY_REQUEST_PICK_SETTINGS_FILE:
            onImport(data.getData());
            break;
        }
    }

    private void onImport(Uri uri) {
        ListImportContentsAsyncTask asyncTask = new ListImportContentsAsyncTask(this, uri);
        setNonConfigurationInstance(asyncTask);
        asyncTask.execute();
    }


    private void showSimpleDialog(int headerRes, int messageRes, Object... args) {
        SimpleDialog dialog = new SimpleDialog(headerRes, messageRes, args);
        dialog.show(this);
        setNonConfigurationInstance(dialog);
    }

    /**
     * A simple dialog.
     */
    private static class SimpleDialog implements NonConfigurationInstance {
        private final int mHeaderRes;
        private final int mMessageRes;
        private Object[] mArguments;
        private Dialog mDialog;

        SimpleDialog(int headerRes, int messageRes, Object... args) {
            this.mHeaderRes = headerRes;
            this.mMessageRes = messageRes;
            this.mArguments = args;
        }

        @Override
        public void restore(Activity activity) {
            show((Accounts) activity);
        }

        @Override
        public boolean retain() {
            if (mDialog != null) {
                mDialog.dismiss();
                mDialog = null;
                return true;
            }
            return false;
        }

        public void show(final Accounts activity) {
            final String message = generateMessage(activity);

            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(mHeaderRes);
            builder.setMessage(message);
            builder.setPositiveButton(R.string.okay_action,
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    activity.setNonConfigurationInstance(null);
                    okayAction(activity);
                }
            });
            mDialog = builder.show();
        }

        /**
         * Returns the message the dialog should display.
         *
         * @param activity
         *         The {@code Activity} this dialog belongs to.
         *
         * @return The message the dialog should display
         */
        protected String generateMessage(Accounts activity) {
            return activity.getString(mMessageRes, mArguments);
        }

        /**
         * This method is called after the "OK" button was pressed.
         *
         * @param activity
         *         The {@code Activity} this dialog belongs to.
         */
        protected void okayAction(Accounts activity) {
            // Do nothing
        }
    }

    /**
     * Shows a dialog that displays how many accounts were successfully imported.
     *
     * @param importResults
     *         The {@link ImportResults} instance returned by the {@link SettingsImporter}.
     * @param filename
     *         The name of the settings file that was imported.
     */
    private void showAccountsImportedDialog(ImportResults importResults, String filename) {
        AccountsImportedDialog dialog = new AccountsImportedDialog(importResults, filename);
        dialog.show(this);
        setNonConfigurationInstance(dialog);
    }

    /**
     * A dialog that displays how many accounts were successfully imported.
     */
    private static class AccountsImportedDialog extends SimpleDialog {
        private ImportResults mImportResults;
        private String mFilename;

        AccountsImportedDialog(ImportResults importResults, String filename) {
            super(R.string.settings_import_success_header, R.string.settings_import_success);
            mImportResults = importResults;
            mFilename = filename;
        }

        @Override
        protected String generateMessage(Accounts activity) {
            //TODO: display names of imported accounts (name from file *and* possibly new name)

            int imported = mImportResults.importedAccounts.size();
            String accounts = activity.getResources().getQuantityString(
                    R.plurals.settings_import_success, imported, imported);
            return activity.getString(R.string.settings_import_success, accounts, mFilename);
        }

        @Override
        protected void okayAction(Accounts activity) {
            Context context = activity.getApplicationContext();
            Preferences preferences = Preferences.getPreferences(context);
            List<Account> disabledAccounts = new ArrayList<Account>();
            for (AccountDescriptionPair accountPair : mImportResults.importedAccounts) {
                Account account = preferences.getAccount(accountPair.imported.uuid);
                if (account != null && !account.isEnabled()) {
                    disabledAccounts.add(account);
                }
            }
            if (disabledAccounts.size() > 0) {
                activity.promptForServerPasswords(disabledAccounts);
            } else {
                activity.setNonConfigurationInstance(null);
            }
        }
    }

    /**
     * Display a dialog that lets the user select which accounts to import from the settings file.
     *
     * @param importContents
     *         The {@link ImportContents} instance returned by
     *         {@link SettingsImporter#getImportStreamContents(InputStream)}
     * @param uri
     *         The (content) URI of the settings file.
     */
    private void showImportSelectionDialog(ImportContents importContents, Uri uri) {
        ImportSelectionDialog dialog = new ImportSelectionDialog(importContents, uri);
        dialog.show(this);
        setNonConfigurationInstance(dialog);
    }

    /**
     * A dialog that lets the user select which accounts to import from the settings file.
     */
    private static class ImportSelectionDialog implements NonConfigurationInstance {
        private ImportContents mImportContents;
        private Uri mUri;
        private AlertDialog mDialog;
        private SparseBooleanArray mSelection;


        ImportSelectionDialog(ImportContents importContents, Uri uri) {
            mImportContents = importContents;
            mUri = uri;
        }

        @Override
        public void restore(Activity activity) {
            show((Accounts) activity, mSelection);
        }

        @Override
        public boolean retain() {
            if (mDialog != null) {
                // Save the selection state of each list item
                mSelection = mDialog.getListView().getCheckedItemPositions();

                mDialog.dismiss();
                mDialog = null;
                return true;
            }
            return false;
        }

        public void show(Accounts activity) {
            show(activity, null);
        }

        public void show(final Accounts activity, SparseBooleanArray selection) {
            List<String> contents = new ArrayList<String>();

            if (mImportContents.globalSettings) {
                contents.add(activity.getString(R.string.settings_import_global_settings));
            }

            for (AccountDescription account : mImportContents.accounts) {
                contents.add(account.name);
            }

            int count = contents.size();
            boolean[] checkedItems = new boolean[count];
            if (selection != null) {
                for (int i = 0; i < count; i++) {
                    checkedItems[i] = selection.get(i);
                }
            } else {
                for (int i = 0; i < count; i++) {
                    checkedItems[i] = true;
                }
            }

            //TODO: listview header: "Please select the settings you wish to import"
            //TODO: listview footer: "Select all" / "Select none" buttons?
            //TODO: listview footer: "Overwrite existing accounts?" checkbox

            OnMultiChoiceClickListener listener = new OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                    ((AlertDialog) dialog).getListView().setItemChecked(which, isChecked);
                }
            };

            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setMultiChoiceItems(contents.toArray(new String[0]), checkedItems, listener);
            builder.setTitle(activity.getString(R.string.settings_import_selection));
            builder.setInverseBackgroundForced(true);
            builder.setPositiveButton(R.string.okay_action,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ListView listView = ((AlertDialog) dialog).getListView();
                        SparseBooleanArray pos = listView.getCheckedItemPositions();

                        boolean includeGlobals = mImportContents.globalSettings ? pos.get(0) : false;
                        List<String> accountUuids = new ArrayList<String>();
                        int start = mImportContents.globalSettings ? 1 : 0;
                        for (int i = start, end = listView.getCount(); i < end; i++) {
                            if (pos.get(i)) {
                                accountUuids.add(mImportContents.accounts.get(i-start).uuid);
                            }
                        }

                        /*
                         * TODO: Think some more about this. Overwriting could change the store
                         * type. This requires some additional code in order to work smoothly
                         * while the app is running.
                         */
                        boolean overwrite = false;

                        dialog.dismiss();
                        activity.setNonConfigurationInstance(null);

                        ImportAsyncTask importAsyncTask = new ImportAsyncTask(activity,
                                includeGlobals, accountUuids, overwrite, mUri);
                        activity.setNonConfigurationInstance(importAsyncTask);
                        importAsyncTask.execute();
                    }
                });
            builder.setNegativeButton(R.string.cancel_action,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            activity.setNonConfigurationInstance(null);
                        }
                    });
            mDialog = builder.show();
        }
    }

    /**
     * Set the {@code NonConfigurationInstance} this activity should retain on configuration
     * changes.
     *
     * @param inst
     *         The {@link NonConfigurationInstance} that should be retained when
     *         {@link Accounts#onRetainNonConfigurationInstance()} is called.
     */
    private void setNonConfigurationInstance(NonConfigurationInstance inst) {
        mNonConfigurationInstance = inst;
    }

    class AccountsAdapter extends ArrayAdapter<BaseAccount> {
        public AccountsAdapter(BaseAccount[] accounts) {
            super(Accounts.this, 0, accounts);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final BaseAccount account = getItem(position);
            View view;
            if (convertView != null) {
                view = convertView;
            } else {
                view = getLayoutInflater().inflate(R.layout.accounts_item, parent, false);
            }
            AccountViewHolder holder = (AccountViewHolder) view.getTag();
            if (holder == null) {
                holder = new AccountViewHolder();
                holder.description = (TextView) view.findViewById(R.id.description);
                holder.email = (TextView) view.findViewById(R.id.email);
                holder.newMessageCount = (TextView) view.findViewById(R.id.new_message_count);
                holder.flaggedMessageCount = (TextView) view.findViewById(R.id.flagged_message_count);
                holder.activeIcons = (RelativeLayout) view.findViewById(R.id.active_icons);

                holder.chip = view.findViewById(R.id.chip);
                holder.folders = (ImageButton) view.findViewById(R.id.folders);
                holder.accountsItemLayout = (LinearLayout)view.findViewById(R.id.accounts_item_layout);

                view.setTag(holder);
            }
            AccountStats stats = accountStats.get(account.getUuid());

            if (stats != null && account instanceof Account && stats.size >= 0) {
                holder.email.setText(SizeFormatter.formatSize(Accounts.this, stats.size));
                holder.email.setVisibility(View.VISIBLE);
            } else {
                if (account.getEmail().equals(account.getDescription())) {
                    holder.email.setVisibility(View.GONE);
                } else {
                    holder.email.setVisibility(View.VISIBLE);
                    holder.email.setText(account.getEmail());
                }
            }

            String description = account.getDescription();
            if (description == null || description.length() == 0) {
                description = account.getEmail();
            }

            holder.description.setText(description);

            Integer unreadMessageCount = null;
            if (stats != null) {
                unreadMessageCount = stats.unreadMessageCount;
                holder.newMessageCount.setText(Integer.toString(unreadMessageCount));
                holder.newMessageCount.setVisibility(unreadMessageCount > 0 ? View.VISIBLE : View.GONE);

                holder.flaggedMessageCount.setText(Integer.toString(stats.flaggedMessageCount));
                holder.flaggedMessageCount.setVisibility(K9.messageListStars() && stats.flaggedMessageCount > 0 ? View.VISIBLE : View.GONE);

                holder.flaggedMessageCount.setOnClickListener(new AccountClickListener(account, SearchModifier.FLAGGED));
                holder.newMessageCount.setOnClickListener(new AccountClickListener(account, SearchModifier.UNREAD));

                view.getBackground().setAlpha(stats.available ? 0 : 127);

                holder.activeIcons.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        Toast toast = Toast.makeText(getApplication(), getString(R.string.tap_hint), Toast.LENGTH_SHORT);
                        toast.show();
                    }
                }
                                                     );

            } else {
                holder.newMessageCount.setVisibility(View.GONE);
                holder.flaggedMessageCount.setVisibility(View.GONE);
                view.getBackground().setAlpha(0);
            }
            if (account instanceof Account) {
                Account realAccount = (Account)account;

                holder.chip.setBackgroundDrawable(realAccount.generateColorChip().drawable());
                if (unreadMessageCount == null) {
                    holder.chip.getBackground().setAlpha(0);
                } else if (unreadMessageCount == 0) {
                    holder.chip.getBackground().setAlpha(127);
                } else {
                    holder.chip.getBackground().setAlpha(255);
                }

            } else {
                holder.chip.setBackgroundDrawable(new ColorChip(0xff999999).drawable());
            }


            holder.description.setTextSize(TypedValue.COMPLEX_UNIT_SP, mFontSizes.getAccountName());
            holder.email.setTextSize(TypedValue.COMPLEX_UNIT_SP, mFontSizes.getAccountDescription());

            if (K9.useCompactLayouts()) {
                holder.accountsItemLayout.setMinimumHeight(0);
            }
            if (account instanceof SearchAccount || K9.useCompactLayouts()) {

                holder.folders.setVisibility(View.GONE);
            } else {
                holder.folders.setVisibility(View.VISIBLE);
                holder.folders.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        FolderList.actionHandleAccount(Accounts.this, (Account)account);

                    }
                });
            }

            return view;
        }

        class AccountViewHolder {
            public TextView description;
            public TextView email;
            public TextView newMessageCount;
            public TextView flaggedMessageCount;
            public RelativeLayout activeIcons;
            public View chip;
            public ImageButton folders;
            public LinearLayout accountsItemLayout;
        }
    }
    private Flag[] combine(Flag[] set1, Flag[] set2) {
        if (set1 == null) {
            return set2;
        }
        if (set2 == null) {
            return set1;
        }
        Set<Flag> flags = new HashSet<Flag>();
        flags.addAll(Arrays.asList(set1));
        flags.addAll(Arrays.asList(set2));
        return flags.toArray(EMPTY_FLAG_ARRAY);
    }

    private class AccountClickListener implements OnClickListener {

        final BaseAccount account;
        final SearchModifier searchModifier;
        AccountClickListener(BaseAccount nAccount, SearchModifier nSearchModifier) {
            account = nAccount;
            searchModifier = nSearchModifier;
        }
        @Override
        public void onClick(View v) {
            String description = getString(R.string.search_title, account.getDescription(), getString(searchModifier.resId));
            if (account instanceof SearchAccount) {
                SearchAccount searchAccount = (SearchAccount)account;

                MessageList.actionHandle(Accounts.this,
                                         description, "", searchAccount.isIntegrate(),
                                         combine(searchAccount.getRequiredFlags(), searchModifier.requiredFlags),
                                         combine(searchAccount.getForbiddenFlags(), searchModifier.forbiddenFlags));
            } else {
                SearchSpecification searchSpec = new SearchSpecification() {
                    @Override
                    public String[] getAccountUuids() {
                        return new String[] { account.getUuid() };
                    }

                    @Override
                    public Flag[] getForbiddenFlags() {
                        return searchModifier.forbiddenFlags;
                    }

                    @Override
                    public String getQuery() {
                        return "";
                    }

                    @Override
                    public Flag[] getRequiredFlags() {
                        return searchModifier.requiredFlags;
                    }

                    @Override
                    public boolean isIntegrate() {
                        return false;
                    }

                    @Override
                    public String[] getFolderNames() {
                        return null;
                    }

                };
                MessageList.actionHandle(Accounts.this, description, searchSpec);
            }
        }

    }

    public void onExport(final boolean includeGlobals, final Account account) {

        // TODO, prompt to allow a user to choose which accounts to export
        Set<String> accountUuids = null;
        if (account != null) {
            accountUuids = new HashSet<String>();
            accountUuids.add(account.getUuid());
        }

        ExportAsyncTask asyncTask = new ExportAsyncTask(this, includeGlobals, accountUuids);
        setNonConfigurationInstance(asyncTask);
        asyncTask.execute();
    }

    /**
     * Handles exporting of global settings and/or accounts in a background thread.
     */
    private static class ExportAsyncTask extends ExtendedAsyncTask<Void, Void, Boolean> {
        private boolean mIncludeGlobals;
        private Set<String> mAccountUuids;
        private String mFileName;


        private ExportAsyncTask(Accounts activity, boolean includeGlobals,
                Set<String> accountUuids) {
            super(activity);
            mIncludeGlobals = includeGlobals;
            mAccountUuids = accountUuids;
        }

        @Override
        protected void showProgressDialog() {
            String title = mContext.getString(R.string.settings_export_dialog_title);
            String message = mContext.getString(R.string.settings_exporting);
            mProgressDialog = ProgressDialog.show(mActivity, title, message, true);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                mFileName = SettingsExporter.exportToFile(mContext, mIncludeGlobals,
                        mAccountUuids);
            } catch (SettingsImportExportException e) {
                Log.w(K9.LOG_TAG, "Exception during export", e);
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            Accounts activity = (Accounts) mActivity;

            // Let the activity know that the background task is complete
            activity.setNonConfigurationInstance(null);

            removeProgressDialog();

            if (success) {
                activity.showSimpleDialog(R.string.settings_export_success_header,
                        R.string.settings_export_success, mFileName);
            } else {
                //TODO: better error messages
                activity.showSimpleDialog(R.string.settings_export_failed_header,
                        R.string.settings_export_failure);
            }
        }
    }

    /**
     * Handles importing of global settings and/or accounts in a background thread.
     */
    private static class ImportAsyncTask extends ExtendedAsyncTask<Void, Void, Boolean> {
        private boolean mIncludeGlobals;
        private List<String> mAccountUuids;
        private boolean mOverwrite;
        private Uri mUri;
        private ImportResults mImportResults;

        private ImportAsyncTask(Accounts activity, boolean includeGlobals,
                List<String> accountUuids, boolean overwrite, Uri uri) {
            super(activity);
            mIncludeGlobals = includeGlobals;
            mAccountUuids = accountUuids;
            mOverwrite = overwrite;
            mUri = uri;
        }

        @Override
        protected void showProgressDialog() {
            String title = mContext.getString(R.string.settings_import_dialog_title);
            String message = mContext.getString(R.string.settings_importing);
            mProgressDialog = ProgressDialog.show(mActivity, title, message, true);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                InputStream is = mContext.getContentResolver().openInputStream(mUri);
                try {
                    mImportResults = SettingsImporter.importSettings(mContext, is,
                            mIncludeGlobals, mAccountUuids, mOverwrite);
                } finally {
                    try {
                        is.close();
                    } catch (IOException e) { /* Ignore */ }
                }
            } catch (SettingsImportExportException e) {
                Log.w(K9.LOG_TAG, "Exception during import", e);
                return false;
            } catch (FileNotFoundException e) {
                Log.w(K9.LOG_TAG, "Couldn't open import file", e);
                return false;
            } catch (Exception e) {
                Log.w(K9.LOG_TAG, "Unknown error", e);
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            Accounts activity = (Accounts) mActivity;

            // Let the activity know that the background task is complete
            activity.setNonConfigurationInstance(null);

            removeProgressDialog();

            String filename = mUri.getLastPathSegment();
            boolean globalSettings = mImportResults.globalSettings;
            int imported = mImportResults.importedAccounts.size();
            if (success && (globalSettings || imported > 0)) {
                if (imported == 0) {
                    activity.showSimpleDialog(R.string.settings_import_success_header,
                            R.string.settings_import_global_settings_success, filename);
                } else {
                    activity.showAccountsImportedDialog(mImportResults, filename);
                }

                activity.refresh();
            } else {
                //TODO: better error messages
                activity.showSimpleDialog(R.string.settings_import_failed_header,
                        R.string.settings_import_failure, filename);
            }
        }
    }

    private static class ListImportContentsAsyncTask extends ExtendedAsyncTask<Void, Void, Boolean> {
        private Uri mUri;
        private ImportContents mImportContents;

        private ListImportContentsAsyncTask(Accounts activity, Uri uri) {
            super(activity);

            mUri = uri;
        }

        @Override
        protected void showProgressDialog() {
            String title = mContext.getString(R.string.settings_import_dialog_title);
            String message = mContext.getString(R.string.settings_import_scanning_file);
            mProgressDialog = ProgressDialog.show(mActivity, title, message, true);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                ContentResolver resolver = mContext.getContentResolver();
                InputStream is = resolver.openInputStream(mUri);
                try {
                    mImportContents = SettingsImporter.getImportStreamContents(is);
                } finally {
                    try {
                        is.close();
                    } catch (IOException e) { /* Ignore */ }
                }
            } catch (SettingsImportExportException e) {
                Log.w(K9.LOG_TAG, "Exception during export", e);
                return false;
            }
            catch (FileNotFoundException e) {
                Log.w(K9.LOG_TAG, "Couldn't read content from URI " + mUri);
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            Accounts activity = (Accounts) mActivity;

            // Let the activity know that the background task is complete
            activity.setNonConfigurationInstance(null);

            removeProgressDialog();

            if (success) {
                activity.showImportSelectionDialog(mImportContents, mUri);
            } else {
                String filename = mUri.getLastPathSegment();
                //TODO: better error messages
                activity.showSimpleDialog(R.string.settings_import_failed_header,
                        R.string.settings_import_failure, filename);
            }
        }
    }

    private static class MoveAccountAsyncTask extends ExtendedAsyncTask<Void, Void, Void> {
        private Account mAccount;
        private boolean mUp;

        protected MoveAccountAsyncTask(Activity activity, Account account, boolean up) {
            super(activity);
            mAccount = account;
            mUp = up;
        }

        @Override
        protected void showProgressDialog() {
            String message = mActivity.getString(R.string.manage_accounts_moving_message);
            mProgressDialog = ProgressDialog.show(mActivity, null, message, true);
        }

        @Override
        protected Void doInBackground(Void... args) {
            mAccount.move(Preferences.getPreferences(mContext), mUp);
            return null;
        }

        @Override
        protected void onPostExecute(Void arg) {
            Accounts activity = (Accounts) mActivity;

            // Let the activity know that the background task is complete
            activity.setNonConfigurationInstance(null);

            activity.refresh();
            removeProgressDialog();
        }
    }
}
