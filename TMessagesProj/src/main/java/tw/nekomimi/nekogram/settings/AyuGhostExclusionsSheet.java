package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.radolyn.ayugram.utils.AyuGhostPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/**
 * Picker for the Ghost Mode per-chat exceptions: a chat that is checked keeps sending (or keeps
 * blocking) read or typing receipts even when the global Ghost Mode switch says otherwise.
 */
public class AyuGhostExclusionsSheet extends BottomSheet {

    private final boolean typing;
    private final Runnable onChanged;
    private final int currentAccount = UserConfig.selectedAccount;

    private final ArrayList<TLRPC.Dialog> allDialogs = new ArrayList<>();
    private final ArrayList<TLRPC.Dialog> shown = new ArrayList<>();
    private final Adapter adapter;

    public AyuGhostExclusionsSheet(Context context, boolean typing, Runnable onChanged) {
        super(context, false, null);
        this.typing = typing;
        this.onChanged = onChanged;
        setApplyBottomPadding(false);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        title.setText(getString(typing ? R.string.AyuGhostExclusionsTyping : R.string.AyuGhostExclusionsRead));
        title.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(16), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
        container.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        EditText search = new EditText(context);
        search.setHint(getString(R.string.Search));
        search.setSingleLine();
        search.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        search.setBackground(null);
        search.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        search.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint, resourcesProvider));
        search.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(8), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filter(s == null ? "" : s.toString());
            }
        });
        container.addView(search, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        RecyclerListView list = new RecyclerListView(context, resourcesProvider);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setAdapter(adapter = new Adapter());
        list.setPadding(0, 0, 0, AndroidUtilities.dp(8));
        container.addView(list, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        containerView = container;
        loadDialogs();
        filter("");
    }

    private void loadDialogs() {
        allDialogs.clear();
        ArrayList<TLRPC.Dialog> dialogs = MessagesController.getInstance(currentAccount).getDialogs(0);
        if (dialogs != null) {
            allDialogs.addAll(dialogs);
        }
    }

    private void filter(String query) {
        shown.clear();
        final String lower = query == null ? "" : query.trim().toLowerCase();
        for (TLRPC.Dialog dialog : allDialogs) {
            CharSequence name = title(dialog.id);
            if (name == null || name.length() == 0) {
                continue;
            }
            if (!TextUtils.isEmpty(lower) && !name.toString().toLowerCase().contains(lower)) {
                continue;
            }
            shown.add(dialog);
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private CharSequence title(long dialogId) {
        if (DialogObject.isEncryptedDialog(dialogId)) {
            return null;
        }
        if (dialogId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            return user != null ? UserObject.getUserName(user) : null;
        }
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        return chat != null ? chat.title : null;
    }

    private boolean isAllowed(long dialogId) {
        int type = typing ? AyuGhostPreferences.getTypingException(dialogId) : AyuGhostPreferences.getReadException(dialogId);
        return type == AyuGhostPreferences.TYPE_FORCE_ALLOW;
    }

    private void toggle(long dialogId) {
        int next = isAllowed(dialogId) ? AyuGhostPreferences.TYPE_DEFAULT : AyuGhostPreferences.TYPE_FORCE_ALLOW;
        if (typing) {
            AyuGhostPreferences.setTypingException(dialogId, next);
        } else {
            AyuGhostPreferences.setReadException(dialogId, next);
        }
        if (onChanged != null) {
            onChanged.run();
        }
    }

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? 1 : 0;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = viewType == 1
                    ? new TextInfoPrivacyCell(getContext())
                    : new TextCheckCell(getContext(), resourcesProvider);
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position == 0) {
                ((TextInfoPrivacyCell) holder.itemView).setText(getString(R.string.AyuGhostExclusionsInfo));
                return;
            }
            TLRPC.Dialog dialog = shown.get(position - 1);
            TextCheckCell cell = (TextCheckCell) holder.itemView;
            cell.setTextAndCheck(title(dialog.id), isAllowed(dialog.id), position - 1 != shown.size() - 1);
            cell.setOnClickListener(v -> {
                toggle(dialog.id);
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() {
            return shown.size() + 1;
        }
    }
}
