package ca.pkay.rcloneexplorer.Fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ca.pkay.rcloneexplorer.Items.RemoteItem;
import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.Rclone;
import ca.pkay.rcloneexplorer.RecyclerViewAdapters.ShareRemotesRecyclerViewAdapter;
import jp.wasabeef.recyclerview.animators.LandingAnimator;

public class ShareRemotesFragment extends Fragment {

    private List<RemoteItem> remotes;
    private OnRemoteClickListener remoteClickListener;
    private Context context;

    public interface OnRemoteClickListener {
        void onRemoteClick(RemoteItem remote);
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ShareRemotesFragment() {
    }

    @SuppressWarnings("unused")
    public static ShareRemotesFragment newInstance() {
        return new ShareRemotesFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getContext() == null) {
            return;
        }

        ((FragmentActivity) context).setTitle(getString(R.string.remotes_toolbar_title));
        Rclone rclone = new Rclone(getContext());
        List<RemoteItem> allRemotes = rclone.getRemotes();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        Set<String> hiddenRemotesIds = sharedPreferences.getStringSet(getString(R.string.shared_preferences_hidden_remotes), new HashSet<>());
        remotes = new ArrayList<>();
        for (RemoteItem remote : allRemotes) {
            if (!hiddenRemotesIds.contains(remote.getName())) {
                remotes.add(remote);
            }
        }
        RemoteItem.prepareDisplay(getContext(), remotes);
        Collections.sort(remotes);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_share_remotes_list, container, false);

        final Context context = view.getContext();
        RecyclerView recyclerView =  view.findViewById(R.id.share_remotes_list);
        recyclerView.setItemAnimator(new LandingAnimator());
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        ShareRemotesRecyclerViewAdapter recyclerViewAdapter = new ShareRemotesRecyclerViewAdapter(remotes, remoteClickListener);
        recyclerView.setAdapter(recyclerViewAdapter);

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
        if (context instanceof OnRemoteClickListener) {
            remoteClickListener = (OnRemoteClickListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement OnRemoteClickListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        context = null;
        remoteClickListener = null;
    }
}
