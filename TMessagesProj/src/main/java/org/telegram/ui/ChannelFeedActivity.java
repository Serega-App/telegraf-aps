package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.collection.LongSparseArray;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yandex.mobile.ads.common.ImpressionData;

import com.yandex.mobile.ads.common.AdRequestError;
import com.yandex.mobile.ads.common.AdRequest;
import com.yandex.mobile.ads.common.AdError;
import com.yandex.mobile.ads.interstitial.InterstitialAd;
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener;
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener;
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

public class ChannelFeedActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate, NotificationCenter.NotificationCenterDelegate {

    private static final int POSTS_PER_CHANNEL = 5;
    private static final String YANDEX_FEED_INTERSTITIAL_AD_UNIT_ID = "R-M-19643161-4";

    private boolean hasMainTabs;
    private RecyclerListView listView;
    private FeedAdapter adapter;
    private int lastLoadIndex;
    private InterstitialAdLoader yandexFeedInterstitialAdLoader;
    private InterstitialAd yandexFeedInterstitialAd;
    private int yandexFeedInterstitialShowsLeft = 1;
    private boolean yandexFeedInterstitialRequested;
    private final ArrayList<MessageObject> feedMessages = new ArrayList<>();
    private final ArrayList<Object> feedRows = new ArrayList<>();
    private final HashSet<String> addedMessages = new HashSet<>();

    // ---- фильтр по каналам ----
    private boolean filterEnabled;
    private final HashSet<Long> selectedChannels = new HashSet<>();
    private static final int MENU_FILTER = 1000;
    private static final int MENU_FILTER_ALL = 1001;
    private static final int MENU_FILTER_PICK = 1002;

    private SharedPreferences filterPrefs(Context context) {
        return context.getSharedPreferences("channel_feed_filter", Context.MODE_PRIVATE);
    }

    private void loadFilterState(Context context) {
        SharedPreferences prefs = filterPrefs(context);
        filterEnabled = prefs.getBoolean("enabled_" + currentAccount, false);
        selectedChannels.clear();
        String stored = prefs.getString("channels_" + currentAccount, "");
        if (!TextUtils.isEmpty(stored)) {
            for (String part : stored.split(",")) {
                try {
                    selectedChannels.add(Long.parseLong(part));
                } catch (NumberFormatException ignore) {
                }
            }
        }
    }

    private void saveFilterState(Context context) {
        StringBuilder sb = new StringBuilder();
        for (Long id : selectedChannels) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        filterPrefs(context).edit()
                .putBoolean("enabled_" + currentAccount, filterEnabled)
                .putString("channels_" + currentAccount, sb.toString())
                .apply();
    }

    public ChannelFeedActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        if (arguments != null) {
            hasMainTabs = arguments.getBoolean("hasMainTabs", false);
        }

        loadFilterState(ApplicationLoader.applicationContext);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagesDidLoad);
        loadFeed();
        loadYandexFeedInterstitial();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        destroyYandexFeedInterstitial();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagesDidLoad);
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        yandexFeedInterstitialShowsLeft = 1;
        loadYandexFeedInterstitial();
        showYandexFeedInterstitialIfReady();
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle("Лента");
        actionBar.setBackButtonImage(0);

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem filterItem = menu.addItem(MENU_FILTER, R.drawable.ic_ab_other);
        filterItem.addSubItem(MENU_FILTER_ALL, "Все посты");
        filterItem.addSubItem(MENU_FILTER_PICK, "Выбрать каналы...");
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == MENU_FILTER_ALL) {
                    filterEnabled = false;
                    saveFilterState(context);
                    loadFeed();
                } else if (id == MENU_FILTER_PICK) {
                    showChannelPickerDialog(context);
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;

        // Фон как в обычном чате/канале: обои Telegram, если они уже есть в теме.
        Drawable wallpaper = Theme.getCachedWallpaperNonBlocking();
        if (wallpaper != null) {
            fragmentView.setBackground(wallpaper);
        } else {
            fragmentView.setBackgroundColor(getThemedColor(Theme.key_chat_wallpaper));
        }

        int bottomPadding = hasMainTabs
                ? AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS)
                : 0;
        frameLayout.setPadding(0, 0, 0, bottomPadding);

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setClipToPadding(false);
        // В этой вкладке actionBar лежит поверх fragmentView, поэтому первый
        // элемент списка частично уезжал под белую шапку "Лента" и выглядел
        // "кривым". Даём списку верхний safe-area как в экране чата.
        int topPadding = AndroidUtilities.statusBarHeight + AndroidUtilities.dp(64);
        listView.setPadding(0, topPadding, 0, AndroidUtilities.dp(42));
        listView.setVerticalScrollBarEnabled(true);

        adapter = new FeedAdapter(context);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || adapter == null) {
                return;
            }
            MessageObject message = adapter.getMessageAtRow(position);
            if (message == null) {
                return;
            }
            Bundle args = new Bundle();
            args.putLong("chat_id", -message.getDialogId());
            args.putInt("message_id", message.getId());
            presentFragment(new ChatActivity(args));
        });

        frameLayout.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT
        ));

        AndroidUtilities.runOnUIThread(() -> showYandexFeedInterstitialIfReady(), 500);


        return fragmentView;
    }

    /**
     * Берём уже загруженные Telegram посты из кэша диалогов.
     * Сеть не трогаем: вкладка открывается быстро и не ломается из-за loadMessages().
     */
    private void loadFeed() {
        feedMessages.clear();
        feedRows.clear();
        addedMessages.clear();
        lastLoadIndex = 0;

        MessagesController mc = getMessagesController();
        ArrayList<TLRPC.Dialog> dialogs = mc.getAllDialogs();
        LongSparseArray<ArrayList<MessageObject>> dialogMessages = mc.dialogMessage;

        if (dialogs == null || dialogMessages == null) {
            return;
        }

        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (dialog == null) {
                continue;
            }

            long dialogId = dialog.id;
            if (dialogId >= 0) {
                continue;
            }

            TLRPC.Chat chat = mc.getChat(-dialogId);
            if (chat == null || !ChatObject.isChannel(chat) || chat.megagroup || chat.left || chat.kicked) {
                continue;
            }

            if (filterEnabled && !selectedChannels.isEmpty() && !selectedChannels.contains(dialogId)) {
                continue; // канал не выбран в фильтре — пропускаем
            }

            ArrayList<MessageObject> messages = dialogMessages.get(dialogId);
            if (messages == null || messages.isEmpty()) {
                continue;
            }

            // ВАЖНО: здесь берём до 10 из уже имеющегося кэша.
            // Если Telegram держит в dialogMessage только 1 последнее сообщение канала,
            // то физически будет 1. Для настоящих 10 нужна отдельная догрузка истории.
            int count = Math.min(POSTS_PER_CHANNEL, messages.size());
            for (int j = 0; j < count; j++) {
                MessageObject message = messages.get(j);
                if (message == null || message.messageOwner == null) {
                    continue;
                }
                if (message.messageOwner instanceof TLRPC.TL_messageService) {
                    continue;
                }

                String key = message.getDialogId() + "_" + message.getId();
                if (addedMessages.contains(key)) {
                    continue;
                }
                addedMessages.add(key);
                feedMessages.add(message);
            }

            // Реально просим Telegram догрузить последние 10 постов этого канала.
            // Кэш остаётся на экране сразу, а ответы добавятся через messagesDidLoad.
            mc.loadMessages(
                    dialogId,
                    0,
                    false,
                    POSTS_PER_CHANNEL,
                    0,
                    0,
                    true,
                    0,
                    classGuid,
                    0,
                    0,
                    0,
                    0,
                    0,
                    lastLoadIndex++,
                    false
            );
        }

        Collections.sort(feedMessages, (a, b) -> Integer.compare(b.messageOwner.date, a.messageOwner.date));
        rebuildRows();

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * Список подписанных каналов (broadcast, не супергруппы, не покинутые) —
     * используется и в фильтре, и как источник для loadFeed().
     */
    private ArrayList<TLRPC.Chat> getSubscribedChannels() {
        ArrayList<TLRPC.Chat> result = new ArrayList<>();
        MessagesController mc = getMessagesController();
        ArrayList<TLRPC.Dialog> dialogs = mc.getAllDialogs();
        if (dialogs == null) {
            return result;
        }
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (dialog == null || dialog.id >= 0) {
                continue;
            }
            TLRPC.Chat chat = mc.getChat(-dialog.id);
            if (chat == null || !ChatObject.isChannel(chat) || chat.megagroup || chat.left || chat.kicked) {
                continue;
            }
            result.add(chat);
        }
        return result;
    }

    private void showChannelPickerDialog(Context context) {
        ArrayList<TLRPC.Chat> channels = getSubscribedChannels();
        if (channels.isEmpty()) {
            return;
        }

        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        scrollView.addView(container, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        // Копия текущего выбора, чтобы применить только по кнопке "Готово".
        // ВАЖНО: если фильтр сейчас выключен, это означает "показывать все".
        // Поэтому перед открытием списка надо явно положить ВСЕ каналы во
        // временный выбор. Иначе пользователь снимает галочки, но tempSelection
        // остаётся пустым, после "Готово" filterEnabled снова становится false,
        // и при следующем открытии опять отмечены все каналы.
        HashSet<Long> tempSelection = new HashSet<>(selectedChannels);
        if (!filterEnabled) {
            tempSelection.clear();
            for (TLRPC.Chat chat : channels) {
                if (chat != null) {
                    tempSelection.add(-chat.id);
                }
            }
        }

        for (TLRPC.Chat chat : channels) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));

            CheckBox checkBox = new CheckBox(context);
            long dialogId = -chat.id;
            checkBox.setChecked(tempSelection.contains(dialogId));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    tempSelection.add(dialogId);
                } else {
                    tempSelection.remove(dialogId);
                }
            });
            row.addView(checkBox, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView title = new TextView(context);
            title.setText(chat.title);
            title.setTextSize(16);
            title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            titleParams.leftMargin = AndroidUtilities.dp(8);
            row.addView(title, titleParams);

            container.addView(row);
        }

        new AlertDialog.Builder(context)
                .setTitle("Каналы в ленте")
                .setView(scrollView)
                .setPositiveButton("Готово", (dialog, which) -> {
                    selectedChannels.clear();
                    selectedChannels.addAll(tempSelection);
                    filterEnabled = !selectedChannels.isEmpty();
                    saveFilterState(context);
                    loadFeed();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void rebuildRows() {
        feedRows.clear();
        int lastDay = Integer.MIN_VALUE;

        for (int i = 0; i < feedMessages.size(); i++) {
            MessageObject message = feedMessages.get(i);
            if (message == null || message.messageOwner == null) {
                continue;
            }

            int day = getMessageDay(message);
            if (day != lastDay) {
                feedRows.add(new FeedDateRow(day, formatFeedDate(message.messageOwner.date)));
                lastDay = day;
            }

            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-message.getDialogId());
            if (chat != null) {
                feedRows.add(chat);
            }
            feedRows.add(message);
        }
    }

    private int getMessageDay(MessageObject message) {
        return message.messageOwner.date / 86400;
    }

    private String formatFeedDate(int date) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(date * 1000L);
        int day = calendar.get(java.util.Calendar.DAY_OF_MONTH);
        int month = calendar.get(java.util.Calendar.MONTH);
        return day + " " + getFeedMonthName(month);
    }

    private String getFeedMonthName(int month) {
        switch (month) {
            case java.util.Calendar.JANUARY:
                return "января";
            case java.util.Calendar.FEBRUARY:
                return "февраля";
            case java.util.Calendar.MARCH:
                return "марта";
            case java.util.Calendar.APRIL:
                return "апреля";
            case java.util.Calendar.MAY:
                return "мая";
            case java.util.Calendar.JUNE:
                return "июня";
            case java.util.Calendar.JULY:
                return "июля";
            case java.util.Calendar.AUGUST:
                return "августа";
            case java.util.Calendar.SEPTEMBER:
                return "сентября";
            case java.util.Calendar.OCTOBER:
                return "октября";
            case java.util.Calendar.NOVEMBER:
                return "ноября";
            case java.util.Calendar.DECEMBER:
                return "декабря";
        }
        return "";
    }

    private void addLoadedMessages(ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (int i = 0; i < messages.size(); i++) {
            MessageObject message = messages.get(i);
            if (message == null || message.messageOwner == null) {
                continue;
            }
            long dialogId = message.getDialogId();
            if (filterEnabled && !selectedChannels.isEmpty() && !selectedChannels.contains(dialogId)) {
                continue;
            }
            if (message.messageOwner instanceof TLRPC.TL_messageService) {
                continue;
            }
            if (getFeedMessagesCountForDialog(dialogId) >= POSTS_PER_CHANNEL) {
                continue;
            }
            String key = message.getDialogId() + "_" + message.getId();
            if (addedMessages.contains(key)) {
                continue;
            }
            addedMessages.add(key);
            feedMessages.add(message);
        }
        Collections.sort(feedMessages, (a, b) -> Integer.compare(b.messageOwner.date, a.messageOwner.date));
        rebuildRows();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private int getFeedMessagesCountForDialog(long dialogId) {
        int count = 0;
        for (int i = 0; i < feedMessages.size(); i++) {
            MessageObject message = feedMessages.get(i);
            if (message != null && message.getDialogId() == dialogId) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.messagesDidLoad || args == null || args.length < 11) {
            return;
        }
        int guid = (Integer) args[10];
        if (guid != classGuid) {
            return;
        }
        ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[2];
        addLoadedMessages(messages);
    }

    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        return true;
    }

    @Override
    public void onParentScrollToTop() {
        if (listView != null) {
            listView.smoothScrollToPosition(0);
        }
    }

    @Override
    public BlurredBackgroundSourceRenderNode getGlassSource() {
        return null;
    }

    private class FeedCellDelegate implements ChatMessageCell.ChatMessageCellDelegate {
        @Override
        public boolean canPerformActions() {
            return true;
        }

        @Override
        public boolean canPerformReply() {
            return true;
        }

        @Override
        public void didPressUrl(ChatMessageCell cell, CharacterStyle url, boolean longPress) {
            if (url != null && cell != null) {
                try {
                    Browser.openUrl(cell.getContext(), url.toString());
                } catch (Exception ignore) {
                }
            }
        }

        @Override
        public void didPressCommentButton(ChatMessageCell cell) {
            MessageObject message = cell.getMessageObject();
            if (message == null) {
                return;
            }
            Bundle args = new Bundle();
            args.putLong("chat_id", -message.getDialogId());
            args.putInt("message_id", message.getId());
            presentFragment(new ChatActivity(args));
        }

        @Override
        public void didPressSideButton(ChatMessageCell cell) {
            MessageObject message = cell.getMessageObject();
            if (message == null) {
                return;
            }
            Bundle args = new Bundle();
            args.putLong("chat_id", -message.getDialogId());
            args.putInt("message_id", message.getId());
            presentFragment(new ChatActivity(args));
        }

        @Override
        public void didPressImage(ChatMessageCell cell, float x, float y, boolean fullPreview) {
            MessageObject message = cell.getMessageObject();
            if (message == null) {
                return;
            }
            Bundle args = new Bundle();
            args.putLong("chat_id", -message.getDialogId());
            args.putInt("message_id", message.getId());
            presentFragment(new ChatActivity(args));
        }
    }

    private static class FeedDateRow {
        final int day;
        final String title;

        FeedDateRow(int day, String title) {
            this.day = day;
            this.title = title;
        }
    }

    private class FeedAdapter extends RecyclerListView.SelectionAdapter {
        private static final int VIEW_TYPE_HEADER = 0;
        private static final int VIEW_TYPE_MESSAGE = 1;
        private static final int VIEW_TYPE_DATE = 2;
        private final Context context;
        private final FeedCellDelegate cellDelegate = new FeedCellDelegate();

        FeedAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return getItemViewType(holder.getAdapterPosition()) == VIEW_TYPE_MESSAGE;
        }

        @Override
        public int getItemViewType(int position) {
            Object item = getItem(position);
            if (item instanceof FeedDateRow) {
                return VIEW_TYPE_DATE;
            }
            return item instanceof TLRPC.Chat ? VIEW_TYPE_HEADER : VIEW_TYPE_MESSAGE;
        }

        private Object getItem(int position) {
            if (position < 0 || position >= feedRows.size()) {
                return null;
            }
            return feedRows.get(position);
        }

        private MessageObject getMessageAtRow(int position) {
            Object item = getItem(position);
            return item instanceof MessageObject ? (MessageObject) item : null;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_DATE) {
                FeedDateView dateView = new FeedDateView(context);
                return new RecyclerListView.Holder(dateView);
            }
            if (viewType == VIEW_TYPE_HEADER) {
                FeedHeaderView header = new FeedHeaderView(context);
                return new RecyclerListView.Holder(header);
            }
            ChatMessageCell cell = new ChatMessageCell(context, currentAccount, true, null, null);
            // ВАЖНО для первого поста в RecyclerView: ChatMessageCell должен сразу
            // получить полноценную ширину родителя. Иначе первый bind иногда
            // проходит при некорректной/нулевой ширине, из-за чего ломается layout
            // preview/медиа у самого верхнего поста.
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
            ));
            cell.setResourcesProvider(null);
            cell.setDelegate(cellDelegate);
            cell.shouldCheckVisibleOnScreen = false;
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Object item = getItem(position);
            if (item == null) {
                return;
            }
            if (holder.itemView instanceof FeedDateView) {
                ((FeedDateView) holder.itemView).setDateRow((FeedDateRow) item);
                return;
            }
            if (holder.itemView instanceof FeedHeaderView) {
                ((FeedHeaderView) holder.itemView).setChat((TLRPC.Chat) item);
                return;
            }
            ChatMessageCell cell = (ChatMessageCell) holder.itemView;
            // Сбрасываем измерение перед новым сообщением. Особенно важно для
            // первого видимого поста: RecyclerView может переиспользовать cell или
            // дать первый layout-pass до финальной ширины списка.
            cell.requestLayout();
            cell.setMessageObject((MessageObject) item, null, false, false, false, false);
            cell.requestLayout();
        }

        @Override
        public int getItemCount() {
            return feedRows.size();
        }
    }


    private void loadYandexFeedInterstitial() {
        if (getContext() == null || yandexFeedInterstitialRequested || yandexFeedInterstitialShowsLeft <= 0) {
            return;
        }
        yandexFeedInterstitialRequested = true;
        yandexFeedInterstitialAdLoader = new InterstitialAdLoader(getContext());
        yandexFeedInterstitialAdLoader.loadAd(new AdRequest.Builder(YANDEX_FEED_INTERSTITIAL_AD_UNIT_ID).build(), new InterstitialAdLoadListener() {
            @Override
            public void onAdLoaded(InterstitialAd interstitialAd) {
                yandexFeedInterstitialAd = interstitialAd;
                yandexFeedInterstitialAd.setAdEventListener(new InterstitialAdEventListener() {
                    @Override public void onAdShown() { }
                    @Override public void onAdFailedToShow(AdError adError) { destroyYandexFeedInterstitial(); }
                    @Override public void onAdDismissed() {
                        destroyYandexFeedInterstitial();
                        yandexFeedInterstitialShowsLeft--;
                        if (yandexFeedInterstitialShowsLeft > 0) {
                            loadYandexFeedInterstitial();
                        }
                    }
                    @Override public void onAdClicked() { }
                    @Override public void onAdImpression(ImpressionData impressionData) { }
                });
                showYandexFeedInterstitialIfReady();
            }

            @Override
            public void onAdFailedToLoad(AdRequestError adRequestError) {
                destroyYandexFeedInterstitial();
            }
        });
    }

    private void showYandexFeedInterstitialIfReady() {
        if (yandexFeedInterstitialAd == null || getParentActivity() == null || yandexFeedInterstitialShowsLeft <= 0) {
            return;
        }
        yandexFeedInterstitialAd.show(getParentActivity());
    }

    private void destroyYandexFeedInterstitial() {
        if (yandexFeedInterstitialAd != null) {
            yandexFeedInterstitialAd.setAdEventListener(null);
            yandexFeedInterstitialAd = null;
        }
        yandexFeedInterstitialAdLoader = null;
        yandexFeedInterstitialRequested = false;
    }

    private class FeedDateView extends FrameLayout {
        private final TextView textView;

        FeedDateView(Context context) {
            super(context);
            setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
            setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));

            textView = new TextView(context);
            textView.setTextSize(13);
            textView.setTextColor(0xffffffff);
            textView.setGravity(Gravity.CENTER);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x66000000);
            bg.setCornerRadius(AndroidUtilities.dp(12));
            textView.setBackground(bg);

            addView(textView, LayoutHelper.createFrame(
                    LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER
            ));
        }

        void setDateRow(FeedDateRow row) {
            textView.setText(row != null ? row.title : "");
        }
    }

    private class FeedHeaderView extends FrameLayout {
        private final BackupImageView avatarView;
        private final AvatarDrawable avatarDrawable = new AvatarDrawable();
        private final TextView titleView;

        private final LinearLayout pillView;

        FeedHeaderView(Context context) {
            super(context);
            setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
            setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));

            pillView = new LinearLayout(context);
            pillView.setOrientation(LinearLayout.HORIZONTAL);
            pillView.setGravity(Gravity.CENTER);
            pillView.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(5), AndroidUtilities.dp(10), AndroidUtilities.dp(5));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xccffffff);
            bg.setCornerRadius(AndroidUtilities.dp(16));
            pillView.setBackground(bg);

            avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(AndroidUtilities.dp(14));
            pillView.addView(avatarView, new LinearLayout.LayoutParams(AndroidUtilities.dp(28), AndroidUtilities.dp(28)));

            titleView = new TextView(context);
            titleView.setTextSize(14);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setSingleLine(true);
            titleView.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.leftMargin = AndroidUtilities.dp(8);
            pillView.addView(titleView, params);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            lp.leftMargin = AndroidUtilities.dp(12);
            addView(pillView, lp);
        }

        void setChat(TLRPC.Chat chat) {
            if (chat != null) {
                titleView.setText(chat.title);
                avatarDrawable.setInfo(currentAccount, chat);
                avatarView.setForUserOrChat(chat, avatarDrawable);
            } else {
                titleView.setText("Канал");
                avatarView.setImageDrawable(null);
            }
        }
    }
}
