package org.videolan.vlc.gui.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;

import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.util.FileUtils;


public class ExternalStorageDialog extends AppCompatDialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ViewCompat.setBackground(getActivity().getWindow().getDecorView(),
                ContextCompat.getDrawable(getActivity(), android.R.drawable.screen_background_dark_transparent));
        final String path = getArguments().getString(MediaParsingService.EXTRA_PATH);
        final String name = FileUtils.getFileNameFromPath(path);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final String message = String.format(VLCApplication.getAppResources().getString(R.string.ml_external_storage_msg), name);
        builder.setTitle(R.string.ml_external_storage_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Intent serviceInent = new Intent(MediaParsingService.ACTION_DISCOVER_DEVICE, null, getActivity(), MediaParsingService.class);
                        serviceInent.putExtra(MediaParsingService.EXTRA_PATH, path);
                        if (getActivity() != null) {
                            getActivity().startService(serviceInent);
                            getActivity().finish();
                        }
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext())
                                .edit()
                                .putBoolean("ignore_"+ name, true)
                                .apply();
                        if (getActivity() != null)
                            getActivity().finish();
                    }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        if (getActivity() != null)
            getActivity().finish();
    }
}
