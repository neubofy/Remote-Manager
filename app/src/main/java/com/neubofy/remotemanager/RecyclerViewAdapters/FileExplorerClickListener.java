package com.neubofy.remotemanager.RecyclerViewAdapters;

import android.view.View;
import com.neubofy.remotemanager.Items.FileItem;

public interface FileExplorerClickListener {
    void onFileClicked(FileItem fileItem);
    void onDirectoryClicked(FileItem fileItem, int position);
    void onFilesSelected();
    void onFileDeselected();
    void onFileOptionsClicked(View view, FileItem fileItem);
    String[] getThumbnailServerParams();
}
