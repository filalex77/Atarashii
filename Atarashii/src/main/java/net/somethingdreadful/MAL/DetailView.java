package net.somethingdreadful.MAL;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import net.somethingdreadful.MAL.account.AccountService;
import net.somethingdreadful.MAL.adapters.DetailViewPagerAdapter;
import net.somethingdreadful.MAL.api.APIHelper;
import net.somethingdreadful.MAL.api.BaseModels.AnimeManga.Anime;
import net.somethingdreadful.MAL.api.BaseModels.AnimeManga.GenericRecord;
import net.somethingdreadful.MAL.api.BaseModels.AnimeManga.Manga;
import net.somethingdreadful.MAL.api.MALApi;
import net.somethingdreadful.MAL.api.MALApi.ListType;
import net.somethingdreadful.MAL.detailView.DetailViewDetails;
import net.somethingdreadful.MAL.detailView.DetailViewPersonal;
import net.somethingdreadful.MAL.detailView.DetailViewRecs;
import net.somethingdreadful.MAL.detailView.DetailViewReviews;
import net.somethingdreadful.MAL.dialog.ChooseDialogFragment;
import net.somethingdreadful.MAL.dialog.DatePickerDialogFragment;
import net.somethingdreadful.MAL.dialog.InputDialogFragment;
import net.somethingdreadful.MAL.dialog.ListDialogFragment;
import net.somethingdreadful.MAL.dialog.NumberPickerDialogFragment;
import net.somethingdreadful.MAL.tasks.NetworkTask;
import net.somethingdreadful.MAL.tasks.TaskJob;
import net.somethingdreadful.MAL.tasks.WriteDetailTask;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import lombok.Getter;

public class DetailView extends AppCompatActivity implements Serializable, NetworkTask.NetworkTaskListener, SwipeRefreshLayout.OnRefreshListener, NumberPickerDialogFragment.onUpdateClickListener, ListDialogFragment.onUpdateClickListener, ViewPager.OnPageChangeListener, ChooseDialogFragment.onClickListener, InputDialogFragment.onClickListener, DatePickerDialogFragment.onDateSetListener {
    public ListType type;
    public Anime animeRecord;
    public Manga mangaRecord;
    private DetailViewDetails details;
    private DetailViewPersonal personal;
    public DetailViewReviews reviews;
    public DetailViewRecs recommendations;
    @Getter private DetailViewPagerAdapter PageAdapter;
    private int recordID;
    private Menu menu;
    private Context context;
    private boolean coverImageLoaded = false;


    @BindView(R.id.coverImage) ImageView coverImage;
    @BindView(R.id.bannerImage) ImageView bannerImage;
    @Getter @BindView(R.id.collapsingToolbarLayout) CollapsingToolbarLayout collapsingToolbarLayout;
    @Getter @BindView(R.id.pager) ViewPager viewPager;
    @BindView(R.id.actionbar) Toolbar toolbar;
    @BindView(R.id.appBar) AppBarLayout appBarLayout;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        Theme.setTheme(this, R.layout.activity_detailview, true);
        PageAdapter = (DetailViewPagerAdapter) Theme.setActionBar(this, new DetailViewPagerAdapter(getFragmentManager(), this));
        ButterKnife.bind(this);

        viewPager.addOnPageChangeListener(this);
        context = getApplicationContext();
        type = (ListType) getIntent().getSerializableExtra("recordType");
        recordID = getIntent().getIntExtra("recordID", -1);

        if (state != null) {
            animeRecord = (Anime) state.getSerializable("anime");
            mangaRecord = (Manga) state.getSerializable("manga");
        }

        if (isEmpty())
            getRecord(false);
        else
            setText();
    }

    /**
     * Disable animation when back is pressed due a bug
     */
    @Override
    public void onBackPressed() {
        finish();
    }

    /**
     * Set text in all fragments
     */
    private void setText() {
        try {
            collapsingToolbarLayout.setTitle(type == ListType.ANIME ? animeRecord.getTitle() : mangaRecord.getTitle());
            if (!coverImageLoaded)
                setToolbarImages();
            PageAdapter.hidePersonal(!isAdded());
            if (details != null && !isEmpty())
                details.setText();
            if (personal != null && !isEmpty())
                personal.setText();
            if (reviews != null && !isEmpty())
                reviews.setText();
            if (recommendations != null && !isEmpty())
                recommendations.setText();
            if (!isEmpty()) setupBeam();
        } catch (Exception e) {
            AppLog.log(Log.ERROR, "Atarashii", "DetailView.setText(): " + e.getMessage());
            if (!(e instanceof IllegalStateException))
                AppLog.logException(e);
        }
        setMenu();
    }

    public String nullCheck(String string) {
        return isEmpty(string) ? getString(R.string.unknown) : string;
    }

    private boolean isEmpty(String string) {
        return ((string == null || string.equals("") || string.equals("0-00-00")));
    }

    public String nullCheck(int number) {
        return (number == 0 ? "?" : String.valueOf(number));
    }

    public String getDate(String string) {
        return (isEmpty(string) ? getString(R.string.unknown) : DateTools.parseDate(string, false));
    }

    /**
     * Checks if the records are null to prevent nullpointerexceptions
     */
    private boolean isEmpty() {
        return animeRecord == null && mangaRecord == null;
    }

    /**
     * Checks if this record is in our list
     */
    public boolean isAdded() {
        return !isEmpty() && (ListType.ANIME.equals(type) ? animeRecord.getWatchedStatus() != null : mangaRecord.getReadStatus() != null);
    }

    /**
     * Set refreshing on all SwipeRefreshViews
     */
    private void setRefreshing(Boolean show) {
        if (details != null) {
            details.swipeRefresh.setRefreshing(show);
            details.swipeRefresh.setEnabled(!show);
        }
        if (personal != null) {
            personal.swipeRefresh.setRefreshing(show);
            personal.swipeRefresh.setEnabled(!show);
        }
    }

    private void showRemoveDialog() {
        ChooseDialogFragment lcdf = new ChooseDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putString("title", getString(R.string.dialog_title_remove));
        bundle.putString("message", getString(R.string.dialog_message_remove));
        bundle.putString("positive", getString(R.string.dialog_label_remove));
        lcdf.setArguments(bundle);
        lcdf.setCallback(this);
        lcdf.show(getFragmentManager(), "fragment_LogoutConfirmationDialog");
    }

    /**
     * Show the dialog with the tag
     */
    public void showDialog(String tag, DialogFragment dialog) {
        FragmentManager fm = getFragmentManager();
        dialog.show(fm, "fragment_" + tag);
    }

    /**
     * Show the dialog with the tag
     */
    public void showDialog(String tag, DialogFragment dialog, Bundle args) {
        FragmentManager fm = getFragmentManager();
        dialog.setArguments(args);
        dialog.show(fm, "fragment_" + tag);
    }

    /**
     * Episode picker dialog
     */
    @Override
    public void onUpdated(int number, int id) {
        switch (id) {
            case R.id.progress1:
                animeRecord.setWatchedEpisodes(number);
                break;
            case R.id.scorePanel:
                if (isAnime())
                    animeRecord.setScore(number);
                else
                    mangaRecord.setScore(number);
                break;
            case R.id.priorityPanel:
                if (isAnime())
                    animeRecord.setPriority(number);
                else
                    mangaRecord.setPriority(number);
                break;
            case R.id.storagePanel:
                animeRecord.setStorage(number);
                break;
            case R.id.capacityPanel:
                animeRecord.setStorageValue(number);
                break;
            case R.id.rewatchPriorityPanel:
                if (isAnime())
                    animeRecord.setRewatchValue(number);
                else
                    mangaRecord.setRereadValue(number);
                break;
            case R.id.countPanel:
                if (isAnime())
                    animeRecord.setRewatchCount(number);
                else
                    mangaRecord.setRereadCount(number);
                break;
        }
        setText();
    }

    public boolean isAnime() {
        return type.equals(MALApi.ListType.ANIME);
    }

    /**
     * Set the right menu items.
     */
    public void setMenu() {
        if (menu != null) {
            if (isAdded()) {
                menu.findItem(R.id.action_Remove).setVisible(!isEmpty() && APIHelper.isNetworkAvailable(this));
                menu.findItem(R.id.action_addToList).setVisible(false);
            } else {
                menu.findItem(R.id.action_Remove).setVisible(false);
                menu.findItem(R.id.action_addToList).setVisible(!isEmpty());
            }
            menu.findItem(R.id.action_Share).setVisible(!isEmpty());
            menu.findItem(R.id.action_ViewMALPage).setVisible(!isEmpty());
        }
    }

    /**
     * Add record to list
     */
    private void addToList() {
        if (!isEmpty()) {
            if (type.equals(ListType.ANIME)) {
                animeRecord.setCreateFlag();
                // If the anime hasn't aired mark is planned
                if (!animeRecord.getStatus().equalsIgnoreCase("not yet aired"))
                    animeRecord.setWatchedStatus(PrefManager.getAddList());
                else
                    animeRecord.setWatchedStatus(GenericRecord.STATUS_PLANTOWATCH);
            } else {
                mangaRecord.setCreateFlag();
                mangaRecord.setReadStatus(PrefManager.getAddList());
            }
            PageAdapter.hidePersonal(false);
            setText();
        }
    }

    /**
     * Open the share dialog
     */
    private void Share() {
        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        sharingIntent.putExtra(Intent.EXTRA_TEXT, makeShareText());
        startActivity(Intent.createChooser(sharingIntent, "Share via"));
    }

    /**
     * Make the share text for the share dialog
     */
    private String makeShareText() {
        String shareText = PrefManager.getCustomShareText();
        shareText = shareText.replace("$title;", toolbar.getTitle());
        if (AccountService.isMAL())
            shareText = shareText.replace("$link;", "https://myanimelist.net/" + type.toString().toLowerCase(Locale.US) + "/" + String.valueOf(recordID));
        else
            shareText = shareText.replace("$link;", "http://anilist.co/" + type.toString().toLowerCase(Locale.US) + "/" + String.valueOf(recordID));
        shareText = shareText + getResources().getString(R.string.customShareText_fromAtarashii);
        return shareText;
    }

    /**
     * Check if  the record contains all the details.
     * <p/>
     * Without this function the fragments will call setText while it isn't loaded.
     * This will cause a nullpointerexception.
     */
    public boolean isDone() {
        return (!isEmpty()) && (type.equals(ListType.ANIME) ? animeRecord.getSynopsis() != null : mangaRecord.getSynopsis() != null);
    }

    /**
     * Get the records (Anime/Manga)
     * <p/>
     * Try to fetch them from the Database first to get reading/watching details.
     * If the record doesn't contains a synopsis this method will get it.
     */
    private void getRecord(boolean forceUpdate) {
        setRefreshing(true);
        Bundle data = new Bundle();
        data.putInt("recordID", recordID);
        new NetworkTask(TaskJob.GETDETAILS, type, this, data, this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, String.valueOf(forceUpdate));
    }

    public void onStatusDialogDismissed(String currentStatus) {
        if (type.equals(ListType.ANIME)) {
            animeRecord.setWatchedStatus(currentStatus);
        } else {
            mangaRecord.setReadStatus(currentStatus);
        }
        setText();
    }

    public void onMangaDialogDismissed(int value, int value2) {
        mangaRecord.setChaptersRead(value);
        mangaRecord.setVolumesRead(value2);

        setText();
        setMenu();
    }

    @Override
    protected void onSaveInstanceState(Bundle State) {
        super.onSaveInstanceState(State);
        State.putSerializable("anime", animeRecord);
        State.putSerializable("manga", mangaRecord);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_detail_view, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        this.menu = menu;
        setMenu();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.action_Share:
                Share();
                break;
            case R.id.action_Remove:
                showRemoveDialog();
                break;
            case R.id.action_addToList:
                addToList();
                break;
            case R.id.action_viewTopic:
                if (APIHelper.isNetworkAvailable(context)) {
                    Intent forumActivity = new Intent(this, ForumActivity.class);
                    forumActivity.putExtra("id", recordID);
                    forumActivity.putExtra("listType", type);
                    startActivity(forumActivity);
                } else {
                    Theme.Snackbar(this, R.string.toast_error_noConnectivity);
                }
                break;
            case R.id.action_ViewMALPage:
                Uri malurl;
                if (AccountService.isMAL())
                    malurl = Uri.parse("https://myanimelist.net/" + type.toString().toLowerCase(Locale.US) + "/" + recordID + "/");
                else
                    malurl = Uri.parse("http://anilist.co/" + type.toString().toLowerCase(Locale.US) + "/" + recordID + "/");
                startActivity(new Intent(Intent.ACTION_VIEW, malurl));
                break;
            case R.id.action_copy:
                if (animeRecord != null || mangaRecord != null) {
                    android.content.ClipboardManager clipBoard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clipData = android.content.ClipData.newPlainText("Atarashii", type == ListType.ANIME ? animeRecord.getTitle() : mangaRecord.getTitle());
                    clipBoard.setPrimaryClip(clipData);
                    Theme.Snackbar(this, R.string.toast_info_Copied);
                } else {
                    Theme.Snackbar(this, R.string.toast_info_hold_on);
                }
                break;
        }
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (animeRecord == null && mangaRecord == null)
            return; // nothing to do
        try {
            if (type.equals(ListType.ANIME)) {
                if (animeRecord.isDirty() && !animeRecord.getDeleteFlag())
                    new WriteDetailTask(type, this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, animeRecord);
                else if (animeRecord.getDeleteFlag())
                    new WriteDetailTask(type, this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, animeRecord);
            } else if (type.equals(ListType.MANGA)) {
                if (mangaRecord.isDirty() && !mangaRecord.getDeleteFlag())
                    new WriteDetailTask(type, this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mangaRecord);
                else if (mangaRecord.getDeleteFlag())
                    new WriteDetailTask(type, this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mangaRecord);
            }
        } catch (Exception e) {
            AppLog.log(Log.ERROR, "Atarashii", "DetailView.onPause(): " + e.getMessage());
            AppLog.logException(e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // received Android Beam?
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction()))
            processIntent();
    }

    private void processIntent() {
        Parcelable[] rawMsgs = getIntent().getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        // only one message sent during the beam
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        String message = new String(msg.getRecords()[0].getPayload());
        String[] splitmessage = message.split(":", 2);
        if (splitmessage.length == 2) {
            try {
                type = ListType.valueOf(splitmessage[0].toUpperCase(Locale.US));
                recordID = Integer.parseInt(splitmessage[1]);
                getRecord(false);
            } catch (NumberFormatException e) {
                AppLog.logException(e);
                finish();
            }
        }
    }

    private void setupBeam() {
        try {
            // setup beam functionality (if NFC is available)
            NfcAdapter mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (mNfcAdapter == null) {
                AppLog.log(Log.INFO, "Atarashii", "DetailView.setupBeam(): NFC not available");
            } else {
                // Register NFC callback
                String message_str = type.toString() + ":" + String.valueOf(recordID);
                NdefMessage message = new NdefMessage(new NdefRecord[]{
                        new NdefRecord(
                                NdefRecord.TNF_MIME_MEDIA,
                                "application/net.somethingdreadful.MAL".getBytes(Charset.forName("US-ASCII")),
                                new byte[0], message_str.getBytes(Charset.forName("US-ASCII"))),
                        NdefRecord.createApplicationRecord(getPackageName())
                });
                mNfcAdapter.setNdefPushMessage(message, this);
            }
        } catch (Exception e) {
            AppLog.log(Log.ERROR, "Atarashii", "DetailView.setupBeam(): " + e.getMessage());
            AppLog.logException(e);
        }
    }

    @SuppressWarnings("unchecked") // Don't panic, we handle possible class cast exceptions
    @Override
    public void onNetworkTaskFinished(Object result, TaskJob job, ListType type) {
        try {
            if (type == ListType.ANIME)
                animeRecord = (Anime) result;
            else
                mangaRecord = (Manga) result;
            setRefreshing(false);

            setText();
        } catch (ClassCastException e) {
            AppLog.log(Log.ERROR, "Atarashii", "DetailView.onNetworkTaskFinished(): " + result.getClass().toString());
            AppLog.logException(e);
            Theme.Snackbar(this, R.string.toast_error_DetailsError);
        }
    }

    /**
     * Set the banner image and the cover image.
     */
    public void setToolbarImages() {
        try {
            GenericRecord record = (type == ListType.ANIME ? animeRecord : mangaRecord);
            final String imageUrl = record.getImageUrl();
            final Activity activity = this;
            Picasso.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.cover_loading)
                    .into(new Target() {
                        @Override
                        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                            coverImage.setImageBitmap(bitmap);
                            coverImageLoaded = true;
                        }

                        @Override
                        public void onBitmapFailed(Drawable errorDrawable) {
                            try {
                                Picasso.with(activity)
                                        .load(imageUrl.replace("l.jpg", ".jpg"))
                                        .error(R.drawable.cover_error)
                                        .placeholder(R.drawable.cover_loading)
                                        .into(coverImage);
                            } catch (Exception e) {
                                AppLog.log(Log.ERROR, "Atarashii", "DetailViewGeneral.setText(): " + e.getMessage());
                            }
                        }

                        @Override
                        public void onPrepareLoad(Drawable placeHolderDrawable) {
                            Drawable drawable = ContextCompat.getDrawable(activity, R.drawable.cover_loading);
                            coverImage.setImageDrawable(drawable);
                        }
                    });

            if (record.getBannerUrl() != null && !record.getBannerUrl().equals("")) {
                Picasso.with(this)
                        .load(record.getBannerUrl())
                        .into(bannerImage);
            } else {
                Picasso.with(this)
                        .load(record.getImageUrl())
                        .error(R.drawable.atarashii_background)
                        .into(bannerImage);
            }
        } catch (Exception e) {
            AppLog.logException(e);
        }
    }

    @Override
    public void onNetworkTaskError(TaskJob job) {
        Theme.Snackbar(this, R.string.toast_error_DetailsError);
    }

    /**
     * Set the fragment to future use
     */
    public void setDetails(DetailViewDetails details) {
        this.details = details;
    }

    public void setPersonal(DetailViewPersonal personal) {
        this.personal = personal;
    }

    public void setReviews(DetailViewReviews reviews) {
        this.reviews = reviews;
    }

    public void setRecommendations(DetailViewRecs recommendations) {
        this.recommendations = recommendations;
    }

    @Override
    public void onRefresh() {
        getRecord(true);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        if (reviews != null && PageAdapter.getPageTitle(position).equals(getString(R.string.tab_name_reviews)) && !isEmpty() && reviews.page == 0) {
            reviews.getRecords(1);
        } else if (recommendations != null && recommendations.record != null && PageAdapter.getPageTitle(position).equals(getString(R.string.tab_name_recommendations)) && !isEmpty() && recommendations.record.size() == 0) {
            recommendations.getRecords();
        }
    }

    @Override
    public void onPageSelected(int position) {
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    /**
     * Used for the Remove dialog
     */
    @Override
    public void onPositiveButtonClicked() {
        if (type.equals(ListType.ANIME))
            animeRecord.setDeleteFlag();
        else
            mangaRecord.setDeleteFlag();
        finish();
    }

    @Override
    public void onPosInputButtonClicked(String text, int id) {
        switch (id) {
            case R.id.tagsPanel:
                if (isAnime())
                    animeRecord.setPersonalTags(text);
                else
                    mangaRecord.setPersonalTags(text);
                break;
            case R.id.commentspanel:
                if (isAnime())
                    animeRecord.setNotes(text);
                else
                    mangaRecord.setNotes(text);
                break;
            case R.id.scorePanel:
                if (isAnime())
                    animeRecord.setScore(Theme.getRawScore(text));
                else
                    mangaRecord.setScore(Theme.getRawScore(text));
                break;
        }
        setText();
    }

    @Override
    public void onNegInputButtonClicked(int id) {
        onPosInputButtonClicked("", id);
    }

    @Override
    public void onDateSet(Boolean startDate, int year, int month, int day) {
        String monthString = String.valueOf(month);
        if (monthString.length() == 1)
            monthString = "0" + monthString;

        String dayString = String.valueOf(day);
        if (dayString.length() == 1)
            dayString = "0" + dayString;
        if (type.equals(ListType.ANIME)) {
            if (startDate)
                animeRecord.setWatchingStart(String.valueOf(year) + "-" + monthString + "-" + dayString);
            else
                animeRecord.setWatchingEnd(String.valueOf(year) + "-" + monthString + "-" + dayString);
        } else {
            if (startDate)
                mangaRecord.setReadingStart(String.valueOf(year) + "-" + monthString + "-" + dayString);
            else
                mangaRecord.setReadingEnd(String.valueOf(year) + "-" + monthString + "-" + dayString);
        }
        setText();
    }

    public static void createDV(Activity activity, View view, int id, ListType listType, String username) {
        Intent startDetails = new Intent(activity, DetailView.class);
        startDetails.putExtra("recordID", id);
        startDetails.putExtra("recordType", listType);
        startDetails.putExtra("username", username);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(activity, view, "coverImage");
            activity.startActivity(startDetails, options.toBundle());
        } else {
            activity.startActivity(startDetails);
        }
    }
}
